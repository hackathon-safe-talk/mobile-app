import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix, classification_report
import joblib
from pathlib import Path
from datetime import datetime

# Resolve base directory
base_dir = Path(__file__).resolve().parents[1]

def train_v7():
    input_path = base_dir / "data" / "processed" / "master_semantic_dataset_v7_cleaned.csv"
    
    if not input_path.exists():
        print(f"Error: Cleaned dataset V7 not found at {input_path}")
        return

    print(f"Loading dataset: {input_path}")
    df = pd.read_csv(input_path)
    
    # Feature Extraction
    print("Extracting TF-IDF features...")
    vectorizer = TfidfVectorizer(
        ngram_range=(1, 2),
        max_features=10000,
        min_df=1
    )
    
    X = vectorizer.fit_transform(df['clean_text'].astype(str))
    y = df['risk_label'] # Training on RISK levels
    
    # Train/Test Split
    print("Splitting data (80/20)...")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.20, random_state=42, stratify=y
    )
    
    # Model Training
    print("Training Logistic Regression...")
    model = LogisticRegression(max_iter=1000)
    model.fit(X_train, y_train)
    
    # Evaluation
    print("Evaluating model...")
    y_pred = model.predict(X_test)
    
    # Global Metrics
    accuracy = accuracy_score(y_test, y_pred)
    conf_matrix = confusion_matrix(y_test, y_pred, labels=['SAFE', 'SUSPICIOUS', 'DANGEROUS'])
    class_report = classification_report(y_test, y_pred)
    
    print(f"\nTraining set size: {X_train.shape[0]}")
    print(f"Test set size: {X_test.shape[0]}")
    print(f"Accuracy: {accuracy:.4f}")
    
    # Save Artifacts
    models_dir = base_dir / "models"
    models_dir.mkdir(parents=True, exist_ok=True)
    
    vec_path = models_dir / "tfidf_vectorizer_v7.pkl"
    clf_path = models_dir / "safetalk_message_classifier_v7.pkl"
    
    joblib.dump(vectorizer, vec_path)
    joblib.dump(model, clf_path)
    print(f"\nModels saved: {vec_path}, {clf_path}")
    
    # Report Generation
    outputs_dir = base_dir / "outputs"
    outputs_dir.mkdir(parents=True, exist_ok=True)
    report_path = outputs_dir / "model_report_v7_semantic.txt"
    
    report_content = f"""=== SafeTalk Semantic Model Evaluation Summary V7 ===
Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

Dataset: {input_path.name}
Total rows used: {len(df)}
Classes: SAFE, SUSPICIOUS, DANGEROUS

=== Metrics ===
Accuracy: {accuracy:.4f}

Confusion Matrix (Labels: SAFE, SUSPICIOUS, DANGEROUS):
{conf_matrix}

Level-wise breakdown:
{class_report}

Uzbek Priority Check:
The dataset includes focused seeds for Uzbek OTP, warnings, and solicitation scams.

Next steps: ONNX conversion for Android runtime integration.
"""
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report_content)
    
    print(f"Report saved to: {report_path}")

if __name__ == "__main__":
    train_v7()
