"""
SafeTalk V11 End-to-End Pipeline
================================
1. Merges V10 dataset with the new Semantic Context dataset
2. Cleans, deduplicates, and balances labels and languages
3. Trains Logistic Regression & LightGBM
4. Selects the best performing model
5. Exports to ONNX mapping
"""

import os
import re
import json
import joblib
import pandas as pd
import numpy as np
from pathlib import Path
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.linear_model import LogisticRegression
from sklearn.preprocessing import MaxAbsScaler
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix
from sklearn.model_selection import train_test_split
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
# CONFIGURATION
# ─────────────────────────────────────────────────────────────

RANDOM_SEED = 42
np.random.seed(RANDOM_SEED)

BASE_DIR = Path(__file__).resolve().parents[1]
V10_FILE = BASE_DIR / "data" / "final" / "safetalk_unified_v10_ready.csv"
EXPANSION_FILE = BASE_DIR / "data" / "expansions" / "safetalk_semantic_context_v1.csv"
OUT_MODELS_DIR = BASE_DIR / "models" / "v11"

# Target distributions
LABEL_TARGETS = {'SAFE': 0.44, 'SUSPICIOUS': 0.28, 'DANGEROUS': 0.28}
LANG_TARGETS = {'uz': 0.50, 'en': 0.30, 'ru': 0.20}

LABEL_MAP = {'SAFE': 0, 'SUSPICIOUS': 1, 'DANGEROUS': 2}
REVERSE_LABEL_MAP = {0: 'SAFE', 1: 'SUSPICIOUS', 2: 'DANGEROUS'}

TFIDF_OPTS = {
    'ngram_range': (1, 2),
    'max_features': 15000,
    'token_pattern': r'(?u)\b\w\w+\b|\w+[.][a-z]+'
}

# V10 Baseline (Hardcoded from previous run)
V10_DANG_RECALL = 0.9261
V10_MACRO_F1 = 0.9610

# ─────────────────────────────────────────────────────────────
# STEP 2: PREPROCESSING LOGIC
# ─────────────────────────────────────────────────────────────

URL_PATTERN = re.compile(r'(https?://\S+)', re.IGNORECASE)
EXTENSIONS = [".apk", ".exe", ".scr", ".msi", ".bat", ".zip", ".rar"]
NOISE_PATTERN = re.compile(r"[^\w\s\./:']")
WHITESPACE_PATTERN = re.compile(r"\s+")
MULTI_EXCL_PATTERN = re.compile(r"!!+")

def clean_text(text):
    if pd.isna(text):
        return ""
    
    cleaned = str(text).lower()
    
    # Apostrophes
    cleaned = cleaned.replace("‘", "'").replace("’", "'").replace("`", "'").replace("ʼ", "'")
    cleaned = cleaned.replace("o‘", "o'").replace("o’", "o'").replace("o`", "o'")
    cleaned = cleaned.replace("g‘", "g'").replace("g’", "g'").replace("g`", "g'")
    
    # Protect URLs and Extensions
    cleaned = URL_PATTERN.sub(r' \1 ', cleaned)
    for ext in EXTENSIONS:
        if ext in cleaned:
            cleaned = cleaned.replace(ext, f" {ext} ")
            
    # Remove excessive exclamation
    cleaned = MULTI_EXCL_PATTERN.sub(" ! ", cleaned)
            
    # Noise and Whitespace
    cleaned = NOISE_PATTERN.sub(" ", cleaned)
    cleaned = WHITESPACE_PATTERN.sub(" ", cleaned).strip()
    return cleaned

# ─────────────────────────────────────────────────────────────
# STEP 1-4: LOAD, STANDARDIZE, MERGE, DEDUPLICATE
# ─────────────────────────────────────────────────────────────

def load_and_merge():
    print("\n[STEP 1-4] Loading, Merging & Deduplicating Datasets...")
    
    df_v10 = pd.read_csv(V10_FILE)
    df_exp = pd.read_csv(EXPANSION_FILE)
    
    print(f"  V10 Original rows: {len(df_v10):,}")
    print(f"  Semantic Expansion rows: {len(df_exp):,}")
    
    # Standardize V10
    if 'source' not in df_v10.columns:
        df_v10['source'] = 'v10_original'
        
    # Standardize Expansion
    df_exp['source'] = 'semantic_context_v1'
    df_exp['clean_text'] = df_exp['text'].apply(clean_text)
    
    # Ensure columns match
    cols = ['text', 'clean_text', 'label', 'language', 'source']
    
    df_merged = pd.concat([df_v10[cols], df_exp[cols]], ignore_index=True)
    df_merged['clean_text'] = df_merged['clean_text'].fillna("").astype(str)
    
    before_len = len(df_merged)
    df_merged = df_merged.drop_duplicates(subset=['clean_text'], keep='first').reset_index(drop=True)
    after_len = len(df_merged)
    
    print(f"  Merged Rows: {before_len:,}")
    print(f"  Duplicates Removed: {before_len - after_len:,}")
    print(f"  Final Merged Dataset Size: {after_len:,}")
    
    return df_merged

# ─────────────────────────────────────────────────────────────
# STEP 5 & 6: BALANCING
# ─────────────────────────────────────────────────────────────

def balance_dataset(df):
    print("\n[STEP 5 & 6] Rebalancing Labels & Languages...")
    initial_len = len(df)
    
    target_total = initial_len # we try to keep max possible without oversampling initially
    
    # Stratify by language first
    lang_dfs = []
    for lang, ratio in LANG_TARGETS.items():
        lang_df = df[df['language'].str.lower() == lang]
        target_size = int(initial_len * ratio)
        if len(lang_df) > target_size:
            lang_dfs.append(lang_df.sample(n=target_size, random_state=RANDOM_SEED))
        else:
            lang_dfs.append(lang_df)
            
    df_lang_balanced = pd.concat(lang_dfs)
    
    # Now balance labels based on LABEL_TARGETS downsampling
    label_dfs = []
    current_total = len(df_lang_balanced)
    
    for lbl, ratio in LABEL_TARGETS.items():
        lbl_df = df_lang_balanced[df_lang_balanced['label'] == lbl]
        target_size = int(current_total * ratio)
        
        if len(lbl_df) > target_size:
            label_dfs.append(lbl_df.sample(n=target_size, random_state=RANDOM_SEED))
        else:
            label_dfs.append(lbl_df) # Don't oversample safely
            
    df_balanced = pd.concat(label_dfs).sample(frac=1.0, random_state=RANDOM_SEED).reset_index(drop=True)
    
    print(f"  Rows after balancing: {len(df_balanced):,} (Initial: {initial_len:,})")
    
    # Label Dist
    print("  Label Distribution:")
    counts = df_balanced['label'].value_counts()
    for lbl, count in counts.items():
        print(f"    {lbl}: {count} ({count/len(df_balanced):.1%})")
        
    # Lang Dist
    print("  Language Distribution:")
    lc = df_balanced['language'].value_counts()
    for l, count in lc.items():
        print(f"    {l}: {count} ({count/len(df_balanced):.1%})")
        
    return df_balanced

# ─────────────────────────────────────────────────────────────
# STEP 7 & 8: SPLIT & ENGINEER FEATURES
# ─────────────────────────────────────────────────────────────

def create_splits_and_features(df):
    print("\n[STEP 7] Splitting 80/10/10...")
    
    # Label encoding target
    y_full = df['label'].map(LABEL_MAP).values
    
    valid_mask = ~np.isnan(y_full)
    df = df[valid_mask].reset_index(drop=True)
    y_full = y_full[valid_mask].astype(int)
    
    # Split
    X_train_df, X_temp_df, y_train, y_temp = train_test_split(
        df, y_full, test_size=0.20, stratify=df['label'], random_state=RANDOM_SEED
    )
    
    X_valid_df, X_test_df, y_valid, y_test = train_test_split(
        X_temp_df, y_temp, test_size=0.50, stratify=X_temp_df['label'], random_state=RANDOM_SEED
    )
    
    print(f"  Train: {len(X_train_df):,} | Valid: {len(X_valid_df):,} | Test: {len(X_test_df):,}")
    
    print("\n[STEP 8] Feature Engineering...")
    # Add structured features into df natively to compute
    for subset in [X_train_df, X_valid_df, X_test_df]:
        subset['length'] = subset['text'].astype(str).str.len()
        url_pat = re.compile(r'(https?://\S+|www\.\S+|\b[\w-]+\.(com|org|net|uz|ru|xyz|top|click|online|site|store)\b)', re.I)
        subset['has_url'] = subset['text'].astype(str).apply(lambda x: 1.0 if pd.notnull(url_pat.search(x)) else 0.0)
        subset['has_number'] = subset['text'].astype(str).str.contains(r'\d').astype(float)
        
    # TF-IDF
    vectorizer = TfidfVectorizer(**TFIDF_OPTS)
    T_train = vectorizer.fit_transform(X_train_df['clean_text'])
    T_valid = vectorizer.transform(X_valid_df['clean_text'])
    T_test = vectorizer.transform(X_test_df['clean_text'])
    
    # Structured Extraction
    def get_struct(in_df):
        return in_df[['length', 'has_url', 'has_number']].values

    S_train = get_struct(X_train_df)
    S_valid = get_struct(X_valid_df)
    S_test = get_struct(X_test_df)
    
    scaler = MaxAbsScaler()
    S_train_scaled = scaler.fit_transform(S_train)
    S_valid_scaled = scaler.transform(S_valid)
    S_test_scaled = scaler.transform(S_test)
    
    # Compile
    X_train = hstack([T_train, csr_matrix(S_train_scaled)]).tocsr()
    X_valid = hstack([T_valid, csr_matrix(S_valid_scaled)]).tocsr()
    X_test = hstack([T_test, csr_matrix(S_test_scaled)]).tocsr()
    
    feature_count = X_train.shape[1]
    print(f"  TF-IDF + Structured Features = {feature_count} columns")
    
    return X_train, y_train, X_valid, y_valid, X_test, y_test, vectorizer, scaler, feature_count

# ─────────────────────────────────────────────────────────────
# STEP 9-11: TRAIN, EVAL & SELECT
# ─────────────────────────────────────────────────────────────

def evaluate(name, y_true, y_pred, y_prob):
    acc = accuracy_score(y_true, y_pred)
    rep = classification_report(y_true, y_pred, target_names=['SAFE', 'SUSPICIOUS', 'DANGEROUS'], output_dict=True)
    cm = confusion_matrix(y_true, y_pred)
    
    dang_recall = rep['DANGEROUS']['recall']
    safe_fp = cm[0, 2] # SAFE misclassified as DANGEROUS
    macro_f1 = rep['macro avg']['f1-score']
    
    # Custom scoring
    score = (dang_recall * 100) - (safe_fp * 0.1) + (macro_f1 * 50)
    
    return {
        'name': name,
        'acc': acc,
        'dang_recall': dang_recall,
        'safe_fp': safe_fp,
        'macro_f1': macro_f1,
        'score': score,
        'report': rep,
        'cm': cm
    }

def train_and_select(X_train, y_train, X_valid, y_valid, X_test, y_test):
    print("\n[STEP 9 & 10] Training and Evaluating...")
    models = []
    
    # LR
    lr = LogisticRegression(solver='lbfgs', C=10.0, class_weight='balanced', max_iter=1000, random_state=RANDOM_SEED)
    lr.fit(X_train, y_train)
    lr_pred = lr.predict(X_test)
    lr_prob = lr.predict_proba(X_test)
    m1 = evaluate('LogisticRegression', y_test, lr_pred, lr_prob)
    m1['model'] = lr
    models.append(m1)
    
    # LightGBM
    lgbm = lgb.LGBMClassifier(objective='multiclass', num_class=3, learning_rate=0.05, num_leaves=31, class_weight='balanced', random_state=RANDOM_SEED, n_estimators=150)
    lgbm.fit(X_train, y_train, eval_set=[(X_valid, y_valid)], callbacks=[lgb.early_stopping(stopping_rounds=15, verbose=False)])
    lgb_pred = lgbm.predict(X_test)
    lgb_prob = lgbm.predict_proba(X_test)
    m2 = evaluate('LightGBM', y_test, lgb_pred, lgb_prob)
    m2['model'] = lgbm
    models.append(m2)
    
    # Model Selection
    print("\n[STEP 11] Model Selection Scores:")
    best_model_meta = None
    best_score = -9999
    
    for m in models:
        print(f"  {m['name']:18s} | Score: {m['score']:.2f} | Dang.Recall: {m['dang_recall']:.3f} | FP: {m['safe_fp']} | F1: {m['macro_f1']:.3f}")
        if m['score'] > best_score:
            best_score = m['score']
            best_model_meta = m
            
    print(f"\n  ⭐ WINNER: {best_model_meta['name']} ⭐")
    return best_model_meta

# ─────────────────────────────────────────────────────────────
# STEP 12 & 13: SAVE AND EXPORT
# ─────────────────────────────────────────────────────────────

def export_model(meta, vectorizer, scaler, feature_count):
    print("\n[STEP 12 & 13] Saving and ONNX Export...")
    OUT_MODELS_DIR.mkdir(parents=True, exist_ok=True)
    
    model = meta['model']
    joblib.dump(model, OUT_MODELS_DIR / "best_model.pkl")
    joblib.dump(vectorizer, OUT_MODELS_DIR / "tfidf_vectorizer.pkl")
    joblib.dump(scaler, OUT_MODELS_DIR / "structured_scaler.pkl")
    
    with open(OUT_MODELS_DIR / "label_encoder.json", "w") as f:
        json.dump(LABEL_MAP, f, indent=4)
        
    # ONNX Export (zipmap=True explicit)
    initial_type = [('float_input', FloatTensorType([None, feature_count]))]
    onnx_path = OUT_MODELS_DIR / "safetalk_classifier_v11.onnx"
    
    try:
        if meta['name'] == 'LogisticRegression':
            model.classes_ = np.array(['SAFE', 'SUSPICIOUS', 'DANGEROUS'], dtype=object)
            options = {id(model): {'zipmap': True}}
            onx = convert_sklearn(model, initial_types=initial_type, options=options, target_opset=12)
        elif meta['name'] == 'LightGBM':
            model.classes_ = np.array(['SAFE', 'SUSPICIOUS', 'DANGEROUS'], dtype=object)
            options = {id(model): {'zipmap': True}}
            onx = convert_lightgbm(model, initial_types=initial_type, options=options, target_opset=12)
            
        with open(onnx_path, "wb") as f:
            f.write(onx.SerializeToString())
            
        print(f"  V11 ONNX and logic successfully saved.")
    except Exception as e:
        print(f"  [ERROR] ONNX Export failed: {e}")

# ─────────────────────────────────────────────────────────────
# STEP 14: SANITY TESTS
# ─────────────────────────────────────────────────────────────

def run_tests(meta, vectorizer, scaler):
    print("\n[STEP 14] Sanity Tests (Inference Native Mode)...")
    model = meta['model']
    
    tests = [
        ("We are discussing normal family business.", "SAFE"),
        ("Have you checked out this new crypto doubling investment pool?", "SUSPICIOUS"),
        ("URGENT: Your bank account is locked! Hackers are inside! Click to fix: http://scam.ru", "DANGEROUS")
    ]
    
    for text, expected in tests:
        c_text = clean_text(text)
        length = np.array([[len(text)]], dtype=float)
        has_url = np.array([[1.0 if "http" in text else 0.0]], dtype=float)
        has_num = np.array([[1.0 if any(c.isdigit() for c in text) else 0.0]], dtype=float)
        s_feat = np.hstack([length, has_url, has_num])
        
        t_feat = vectorizer.transform([c_text])
        s_scaled = scaler.transform(s_feat)
        X_in = hstack([t_feat, csr_matrix(s_scaled)]).tocsr()
        
        prob = model.predict_proba(X_in)[0]
        idx = np.argmax(prob)
        # model.classes_ was overridden during onnx export to strings
        pred = model.classes_[idx] if model.classes_.dtype == object else REVERSE_LABEL_MAP[idx]
        
        print(f"  Input   : '{text}'")
        print(f"  Expected: {expected} | Pred: {pred}")
        probs = f"SAFE: {prob[0]:.3f} | SUSP: {prob[1]:.3f} | DANG: {prob[2]:.3f}"
        print(f"  Probs   : {probs}\n")

# ─────────────────────────────────────────────────────────────
# STEP 15: FINAL REPORT
# ─────────────────────────────────────────────────────────────

def print_report(df, best_meta, total_original):
    print("==================================================")
    print("           SAFETALK V11 FINAL REPORT              ")
    print("==================================================")
    
    print("\n[1. Dataset Statistics]")
    print(f"  Total Rows: {len(df):,} (Cleaned from {total_original:,})")
    
    print("  Labels:")
    for l, c in df['label'].value_counts().items():
        print(f"    {l}: {c/len(df):.1%}")
        
    print("  Languages:")
    for l, c in df['language'].value_counts().items():
        print(f"    {l}: {c/len(df):.1%}")
        
    print("\n[2. Model Performance (V11 Winner)]")
    print(f"  Name: {best_meta['name']}")
    print(f"  Accuracy: {best_meta['acc']:.4f}")
    
    rep = best_meta['report']
    print("  Per Class Metrics:")
    for cls in ['SAFE', 'SUSPICIOUS', 'DANGEROUS']:
        p = rep[cls]['precision']
        r = rep[cls]['recall']
        f = rep[cls]['f1-score']
        print(f"    {cls:12s} - P: {p:.4f} | R: {r:.4f} | F1: {f:.4f}")
        
    cm = best_meta['cm']
    print("\n  Confusion Matrix (True \ Pred):")
    print("                 SAFE   SUSPICIOUS  DANGEROUS")
    print(f"  SAFE       {cm[0,0]:7d}  {cm[0,1]:10d}  {cm[0,2]:9d}")
    print(f"  SUSPICIOUS {cm[1,0]:7d}  {cm[1,1]:10d}  {cm[1,2]:9d}")
    print(f"  DANGEROUS  {cm[2,0]:7d}  {cm[2,1]:10d}  {cm[2,2]:9d}")
    
    print("\n[3. Improvements vs V10 Baseline]")
    dang_diff = best_meta['dang_recall'] - V10_DANG_RECALL
    f1_diff = best_meta['macro_f1'] - V10_MACRO_F1
    
    print(f"  V10 Dang Recall: {V10_DANG_RECALL:.4f} -> V11: {best_meta['dang_recall']:.4f} ({dang_diff:+.4f})")
    print(f"  V10 Macro F1   : {V10_MACRO_F1:.4f} -> V11: {best_meta['macro_f1']:.4f} ({f1_diff:+.4f})")
    print("==================================================")

def main():
    df_merged = load_and_merge()
    original_len = len(df_merged)
    df_bal = balance_dataset(df_merged)
    X_train, y_train, X_v, y_v, X_t, y_t, vec, scl, f_count = create_splits_and_features(df_bal)
    
    best_meta = train_and_select(X_train, y_train, X_v, y_v, X_t, y_t)
    export_model(best_meta, vec, scl, f_count)
    run_tests(best_meta, vec, scl)
    print_report(df_bal, best_meta, original_len)

if __name__ == "__main__":
    main()
