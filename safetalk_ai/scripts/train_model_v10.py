"""
SafeTalk V10 Training Pipeline
==============================
Production-grade ML training script for the SafeTalk V10 API.
Trains and compares Logistic Regression and LightGBM models.
Selects the best model based on DANGEROUS class recall and F1-score,
and exports the result to ONNX.

Author: SafeTalk AI Team
"""

import os
import json
import joblib
import pandas as pd
import numpy as np
from pathlib import Path
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import MaxAbsScaler
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
from scipy.sparse import hstack, csr_matrix
import lightgbm as lgb
from skl2onnx import convert_sklearn
from skl2onnx.common.data_types import FloatTensorType

try:
    from onnxmltools.convert import convert_lightgbm
    HAS_ONNXMLTOOLS = True
except ImportError:
    HAS_ONNXMLTOOLS = False

# ─────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────

RANDOM_SEED = 42
np.random.seed(RANDOM_SEED)

BASE_DIR = Path(__file__).resolve().parents[1]
DATA_DIR = BASE_DIR / "data" / "final" / "v10_ready"
MODELS_DIR = BASE_DIR / "models"

TRAIN_PATH = DATA_DIR / "train.csv"
VALID_PATH = DATA_DIR / "validation.csv"
TEST_PATH = DATA_DIR / "test.csv"

# Label mapping
LABEL_MAP = {
    'SAFE': 0,
    'SUSPICIOUS': 1,
    'DANGEROUS': 2
}
REVERSE_LABEL_MAP = {v: k for k, v in LABEL_MAP.items()}

# TF-IDF Configuration
TFIDF_KWARGS = {
    'ngram_range': (1, 2),
    'max_features': 15000,
    'token_pattern': r'(?u)\b\w\w+\b|\w+[.][a-z]+'
}

# ─────────────────────────────────────────────────────────────
# Pipeline Step 2 & 3: Load Data & Encode Labels
# ─────────────────────────────────────────────────────────────

def load_and_encode(filepath: Path) -> tuple[pd.DataFrame, np.ndarray, np.ndarray]:
    """Loads dataset and encodes labels."""
    df = pd.read_csv(filepath)
    # Ensure text columns are string and fill NaNs
    df['clean_text'] = df['clean_text'].fillna("").astype(str)
    
    # Map labels
    y = df['label'].map(LABEL_MAP).values
    
    # Drop rows where label mapping failed (if any)
    valid_mask = ~np.isnan(y)
    df = df[valid_mask].reset_index(drop=True)
    y = y[valid_mask].astype(int)
    
    return df, y

# ─────────────────────────────────────────────────────────────
# Pipeline Step 4: Feature Engineering
# ─────────────────────────────────────────────────────────────

def build_features(train_df: pd.DataFrame, valid_df: pd.DataFrame, test_df: pd.DataFrame):
     print("\n[STEP 4] Feature Engineering...")
     
     # TF-IDF on clean_text
     vectorizer = TfidfVectorizer(**TFIDF_KWARGS)
     X_train_text = vectorizer.fit_transform(train_df['clean_text'])
     X_valid_text = vectorizer.transform(valid_df['clean_text'])
     X_test_text = vectorizer.transform(test_df['clean_text'])
     
     print(f"  TF-IDF Features created: {X_train_text.shape[1]}")
     
     # Structured Features
     def extract_structured(df: pd.DataFrame) -> np.ndarray:
         length = df['length'].values.reshape(-1, 1).astype(float)
         has_url = df['has_url'].astype(float).values.reshape(-1, 1)
         has_num = df['has_number'].astype(float).values.reshape(-1, 1)
         return np.hstack([length, has_url, has_num])
         
     S_train = extract_structured(train_df)
     S_valid = extract_structured(valid_df)
     S_test = extract_structured(test_df)
     
     # Scale structured features (MaxAbsScaler preserves sparsity structurally if needed)
     scaler = MaxAbsScaler()
     S_train_scaled = scaler.fit_transform(S_train)
     S_valid_scaled = scaler.transform(S_valid)
     S_test_scaled = scaler.transform(S_test)
     
     # Combine features using sparse hstack
     X_train = hstack([X_train_text, csr_matrix(S_train_scaled)]).tocsr()
     X_valid = hstack([X_valid_text, csr_matrix(S_valid_scaled)]).tocsr()
     X_test = hstack([X_test_text, csr_matrix(S_test_scaled)]).tocsr()
     
     print(f"  Combined Feature Shape: {X_train.shape[1]} (TF-IDF + {S_train.shape[1]} structured)")
     
     return (X_train, X_valid, X_test), vectorizer, scaler

# ─────────────────────────────────────────────────────────────
# Pipeline Step 5 & 6 & 7: Train & Evaluate
# ─────────────────────────────────────────────────────────────

def evaluate_model(name: str, y_true: np.ndarray, y_pred: np.ndarray, y_prob: np.ndarray):
    """Prints evaluation metrics and returns target optimization scores."""
    print(f"\n═══ {name} EVALUATION ═══")
    
    acc = accuracy_score(y_true, y_pred)
    print(f"  Accuracy: {acc:.4f}")
    
    report = classification_report(y_true, y_pred, target_names=['SAFE', 'SUSPICIOUS', 'DANGEROUS'], output_dict=True)
    print("\n  Class Metrics:")
    for cls in ['SAFE', 'SUSPICIOUS', 'DANGEROUS']:
        p = report[cls]['precision']
        r = report[cls]['recall']
        f = report[cls]['f1-score']
        print(f"    {cls:12s} - Precision: {p:.4f} | Recall: {r:.4f} | F1: {f:.4f}")
        
    cm = confusion_matrix(y_true, y_pred)
    print("\n  Confusion Matrix (True \\ Pred):")
    print("                 SAFE   SUSPICIOUS  DANGEROUS")
    print(f"  SAFE       {cm[0,0]:7d}  {cm[0,1]:10d}  {cm[0,2]:9d}")
    print(f"  SUSPICIOUS {cm[1,0]:7d}  {cm[1,1]:10d}  {cm[1,2]:9d}")
    print(f"  DANGEROUS  {cm[2,0]:7d}  {cm[2,1]:10d}  {cm[2,2]:9d}")
    
    # Critical metrics for model selection
    dang_recall = report['DANGEROUS']['recall']
    safe_to_dang_fp = cm[0, 2] # True SAFE, Pred DANGEROUS
    avg_f1 = report['macro avg']['f1-score']
    
    return dang_recall, safe_to_dang_fp, avg_f1


def train_models(X_train, y_train, X_valid, y_valid, X_test, y_test):
    print("\n[STEP 5 & 6] Training Models...")
    
    models = {}
    
    # Model 1: Logistic Regression
    print("  Training Logistic Regression...")
    lr = LogisticRegression(
        solver='lbfgs',
        C=10.0,
        class_weight='balanced',
        max_iter=1000,
        random_state=RANDOM_SEED
    )
    lr.fit(X_train, y_train)
    lr_pred = lr.predict(X_test)
    lr_prob = lr.predict_proba(X_test)
    models['LogisticRegression'] = {
        'model': lr,
        'metrics': evaluate_model('Logistic Regression', y_test, lr_pred, lr_prob)
    }
    
    # Model 2: LightGBM
    print("\n  Training LightGBM...")
    lgbm = lgb.LGBMClassifier(
        objective='multiclass',
        num_class=3,
        learning_rate=0.05,
        num_leaves=31,
        class_weight='balanced',
        random_state=RANDOM_SEED,
        n_estimators=150
    )
    lgbm.fit(X_train, y_train, eval_set=[(X_valid, y_valid)], callbacks=[lgb.early_stopping(stopping_rounds=15)])
    lgb_pred = lgbm.predict(X_test)
    lgb_prob = lgbm.predict_proba(X_test)
    models['LightGBM'] = {
        'model': lgbm,
        'metrics': evaluate_model('LightGBM', y_test, lgb_pred, lgb_prob)
    }
    
    return models

# ─────────────────────────────────────────────────────────────
# Pipeline Step 8: Model Selection
# ─────────────────────────────────────────────────────────────

def select_best_model(models: dict):
    print("\n[STEP 8] Model Selection...")
    
    best_name = None
    best_score = -1
    best_model = None
    
    for name, data in models.items():
        dang_recall, safe_to_dang_fp, f1 = data['metrics']
        
        # Scoring function: Prioritize DANGEROUS recall heavily, penalize false positives, reward F1
        # Normalization heuristics
        score = (dang_recall * 100) - (safe_to_dang_fp * 0.1) + (f1 * 50)
        
        print(f"  {name:20s} | Dang. Recall: {dang_recall:.3f} | Safe->Dang FP: {safe_to_dang_fp:3d} | Overall F1: {f1:.3f} -> Score: {score:.2f}")
        
        if score > best_score:
            best_score = score
            best_name = name
            best_model = data['model']
            
    print(f"\n  ⭐ Selected Best Model: {best_name} ⭐")
    return best_model, best_name

# ─────────────────────────────────────────────────────────────
# Pipeline Step 9 & 10: Saving & Exporting
# ─────────────────────────────────────────────────────────────

def save_and_export(model, model_name: str, vectorizer, scaler, n_features: int):
    print("\n[STEP 9 & 10] Saving and ONNX Export...")
    MODELS_DIR.mkdir(parents=True, exist_ok=True)
    
    # 1. Save standard artifacts
    joblib.dump(model, MODELS_DIR / "best_model.pkl")
    joblib.dump(vectorizer, MODELS_DIR / "tfidf_vectorizer.pkl")
    joblib.dump(scaler, MODELS_DIR / "structured_scaler.pkl")
    
    with open(MODELS_DIR / "label_encoder.json", "w") as f:
        json.dump(LABEL_MAP, f, indent=4)
        
    print(f"  Saved artifacts (.pkl, .json) to {MODELS_DIR.name}/")
    
    # 2. Export ONNX
    # Because MLInferenceModule expects Map<String, Float> representing class probabilities natively
    # supported by scikit-learn ONNX export, we map class indices back to strings if possible,
    # or just export the shape explicitly.
    onnx_path = MODELS_DIR / "safetalk_classifier_v10.onnx"
    initial_type = [('float_input', FloatTensorType([None, n_features]))]
    
    try:
        if "LogisticRegression" in model_name:
            # Change classes to strings so ONNX outputs mapping with 'SAFE', 'SUSPICIOUS', 'DANGEROUS' keys
            # to match Android `Map<String, Float>` decoding pattern without index guessing.
            model.classes_ = np.array(['SAFE', 'SUSPICIOUS', 'DANGEROUS'], dtype=object)
            options = {id(model): {'zipmap': True}}
            onx = convert_sklearn(model, initial_types=initial_type, options=options, target_opset=12)
            
        elif "LightGBM" in model_name:
            if not HAS_ONNXMLTOOLS:
                print("  [WARN] onnxmltools not installed. Cannot export LightGBM to ONNX natively.")
                return
            model.classes_ = np.array(['SAFE', 'SUSPICIOUS', 'DANGEROUS'], dtype=object)
            options = {id(model): {'zipmap': True}}
            onx = convert_lightgbm(model, initial_types=initial_type, options=options, target_opset=12)
            
        else:
            print("  Unknown model type for ONNX export.")
            return

        with open(onnx_path, "wb") as f:
            f.write(onx.SerializeToString())
        print(f"  Exported ONNX model to: {onnx_path.name}")
        
    except Exception as e:
        print(f"  [ERROR] ONNX Export failed: {e}")

# ─────────────────────────────────────────────────────────────
# Pipeline Step 11: Inference Test
# ─────────────────────────────────────────────────────────────

def test_inference(model, vectorizer, scaler):
    print("\n[STEP 11] Test Inference Cases...")
    
    cases = [
        ("How are you today, friend?", "SAFE"),
        ("Your password expires today. Please login at http://secure-update.com", "SUSPICIOUS"),
        ("URGENT: Your account has been suspended! Click http://pay-now.xyz to unlock!!", "DANGEROUS")
    ]
    
    for text, expected in cases:
        # Pseudo extraction (in reality you'd pipe text through preprocessor)
        length = np.array([[len(text)]], dtype=float)
        has_url = np.array([[1.0 if "http" in text else 0.0]], dtype=float)
        has_num = np.array([[1.0 if any(c.isdigit() for c in text) else 0.0]], dtype=float)
        s_feat = np.hstack([length, has_url, has_num])
        s_scaled = scaler.transform(s_feat)
        
        t_feat = vectorizer.transform([text.lower()])
        x_in = hstack([t_feat, csr_matrix(s_scaled)]).tocsr()
        
        prob = model.predict_proba(x_in)[0]
        # model.classes_ was overridden to string arrays right before ONNX save
        if model.classes_.dtype == object:
            idx = np.argmax(prob)
            pred = model.classes_[idx]
        else:
            idx = np.argmax(prob)
            pred = REVERSE_LABEL_MAP[idx]
        
        print(f"\n  Text: '{text}'")
        print(f"  Expected: {expected} | Predicted: {pred}")
        print(f"  Probs  : SAFE={prob[0]:.3f}, SUSPICIOUS={prob[1]:.3f}, DANGEROUS={prob[2]:.3f}")


def main():
    print("==================================================")
    print(" SafeTalk V10 API - ML Training Pipeline")
    print("==================================================")
    
    # 2 & 3. Load Datasets
    print("\n[STEP 2 & 3] Loading Datasets...")
    train_df, y_train = load_and_encode(TRAIN_PATH)
    valid_df, y_valid = load_and_encode(VALID_PATH)
    test_df, y_test = load_and_encode(TEST_PATH)
    print(f"  Train: {len(train_df)} | Valid: {len(valid_df)} | Test: {len(test_df)}")
    
    # 4. Feature Engineering
    (X_train, X_valid, X_test), vectorizer, scaler = build_features(train_df, valid_df, test_df)
    n_features = X_train.shape[1]
    
    # 5 & 6. Train Models
    models = train_models(X_train, y_train, X_valid, y_valid, X_test, y_test)
    
    # 8. Model Selection
    best_model, best_name = select_best_model(models)
    
    # 9 & 10. Save and Export
    save_and_export(best_model, best_name, vectorizer, scaler, n_features)
    
    # 11. Test Model Inference
    test_inference(best_model, vectorizer, scaler)
    
    print("\n✅ V10 Model Pipeline Completed Successfully.\n")

if __name__ == "__main__":
    main()
