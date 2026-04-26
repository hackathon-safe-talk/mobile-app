import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import classification_report, confusion_matrix, accuracy_score
import joblib
from pathlib import Path
from datetime import datetime

base_dir = Path(__file__).resolve().parents[1]

def run_experiment(df, ngram_range, class_weight):
    print(f"\nExperiment: ngrams={ngram_range}, class_weight={class_weight}")
    
    vectorizer = TfidfVectorizer(
        ngram_range=ngram_range,
        max_features=15000,
        min_df=1,
        token_pattern=r'(?u)\b\w\w+\b|\w+[.][a-z]+'
    )
    
    X = vectorizer.fit_transform(df['clean_text'].astype(str))
    y = df['risk_label']
    
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42, stratify=y
    )
    
    model = LogisticRegression(
        max_iter=1000,
        C=10.0,
        class_weight=class_weight,
        solver='lbfgs'
    )
    model.fit(X_train, y_train)
    
    y_pred = model.predict(X_test)
    
    # Custom Score: Priority on DANGEROUS Recall and SAFE->DANGEROUS FP Reduction
    report = classification_report(y_test, y_pred, output_dict=True)
    conf = confusion_matrix(y_test, y_pred, labels=['SAFE', 'SUSPICIOUS', 'DANGEROUS'])
    
    # conf[0][2] is SAFE predicted as DANGEROUS
    safe_to_dang_fp = conf[0][2]
    dang_recall = report['DANGEROUS']['recall']
    
    print(f"  Accuracy: {accuracy_score(y_test, y_pred):.4f}")
    print(f"  DANGEROUS Recall: {dang_recall:.4f}")
    print(f"  SAFE -> DANGEROUS Errors: {safe_to_dang_fp}")
    
    return {
        "model": model, 
        "vectorizer": vectorizer, 
        "dang_recall": dang_recall, 
        "safe_to_dang_fp": safe_to_dang_fp,
        "report": report,
        "conf": conf,
        "params": {"ngram_range": ngram_range, "class_weight": class_weight}
    }

def train_v9():
    input_path = base_dir / "data" / "processed" / "master_semantic_dataset_v9_cleaned.csv"
    if not input_path.exists():
        print(f"Error: Dataset not found at {input_path}")
        return

    df = pd.read_csv(input_path)
    
    experiments = [
        ((1, 2), 'balanced'),
        ((1, 3), 'balanced'),
        ((1, 2), None),
        ((1, 3), None)
    ]
    
    results = []
    for ngrads, weight in experiments:
        res = run_experiment(df, ngrads, weight)
        results.append(res)
    
    # Selection Logic:
    # 1. Best DANGEROUS Recall
    # 2. Tie-break: Lowest SAFE -> DANGEROUS Errors
    # 3. Tie-break: Overall F1
    
    best_res = sorted(results, key=lambda x: (x['dang_recall'], -x['safe_to_dang_fp']), reverse=True)[0]
    
    print("\n--- BEST CONFIGURATION SELECTED ---")
    print(f"NGrams: {best_res['params']['ngram_range']}")
    print(f"Class Weight: {best_res['params']['class_weight']}")
    print(f"DANGEROUS Recall: {best_res['dang_recall']:.4f}")
    print(f"SAFE -> DANGEROUS Errors: {best_res['safe_to_dang_fp']}")
    
    # Save best artifacts
    models_dir = base_dir / "models"
    models_dir.mkdir(parents=True, exist_ok=True)
    
    joblib.dump(best_res['vectorizer'], models_dir / "tfidf_vectorizer_v9.pkl")
    joblib.dump(best_res['model'], models_dir / "safetalk_message_classifier_v9.pkl")
    
    # Report
    outputs_dir = base_dir / "outputs"
    outputs_dir.mkdir(parents=True, exist_ok=True)
    report_path = outputs_dir / "model_report_v9_semantic.txt"
    
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(f"=== SafeTalk Semantic Model Evaluation Summary V9 ===\n")
        f.write(f"Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}\n")
        f.write(f"Best Params: {best_res['params']}\n")
        f.write(f"Accuracy: {accuracy_score(df['risk_label'], best_res['model'].predict(best_res['vectorizer'].transform(df['clean_text'].astype(str)))):.4f}\n")
        f.write("\nClassification Report (Test Set):\n")
        for label, metrics in best_res['report'].items():
            f.write(f"{label}: {metrics}\n")
        f.write("\nConfusion Matrix (SAFE, SUSPICIOUS, DANGEROUS):\n")
        f.write(str(best_res['conf']))
        
    print(f"V9 models and report saved.")

if __name__ == "__main__":
    train_v9()
