import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix, classification_report
import joblib
from pathlib import Path
from datetime import datetime

base_dir = Path(__file__).resolve().parents[1]

def train_model_v5():
    input_path = base_dir / "data" / "processed" / "messages_cleaned_v5.csv"
    
    if not input_path.exists():
        print(f"Error: Preprocessed dataset V5 not found at {input_path}")
        return

    print(f"Loading cleaned dataset: {input_path}")
    df = pd.read_csv(input_path)
    
    # Final safety check on cleaned text
    df = df.dropna(subset=['clean_text'])
    df = df[df['clean_text'].str.strip() != ""]
    
    print(f"Total rows for training: {len(df)}")
    
    # Feature Extraction with increased max_features for 10K dataset
    print("Extracting TF-IDF features (max_features=7000)...")
    vectorizer = TfidfVectorizer(
        ngram_range=(1, 2),
        max_features=7000,
        min_df=1
    )
    
    X = vectorizer.fit_transform(df['clean_text'])
    y = df['label']
    
    # Split
    print("Splitting data into train/test (80/20)...")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    # Train
    print("Training Logistic Regression model...")
    model = LogisticRegression(max_iter=1000)
    model.fit(X_train, y_train)
    
    # Evaluate
    print("Evaluating V5 model...")
    y_pred = model.predict(X_test)
    
    # Metrics
    scam_label = 'scam'
    accuracy = accuracy_score(y_test, y_pred)
    precision = precision_score(y_test, y_pred, pos_label=scam_label)
    recall = recall_score(y_test, y_pred, pos_label=scam_label)
    f1 = f1_score(y_test, y_pred, pos_label=scam_label)
    conf_matrix = confusion_matrix(y_test, y_pred)
    class_report = classification_report(y_test, y_pred)
    
    print(f"\nAccuracy: {accuracy:.4f}")
    print(f"Scam Recall: {recall:.4f}")
    
    # Save artifacts
    models_dir = base_dir / "models"
    models_dir.mkdir(parents=True, exist_ok=True)
    
    vec_path = models_dir / "tfidf_vectorizer_v5.pkl"
    clf_path = models_dir / "safetalk_message_classifier_v5.pkl"
    
    joblib.dump(vectorizer, vec_path)
    joblib.dump(model, clf_path)
    print(f"Models saved: {vec_path}, {clf_path}")
    
    # Save Report
    outputs_dir = base_dir / "outputs"
    outputs_dir.mkdir(parents=True, exist_ok=True)
    report_path = outputs_dir / "model_report_v5.txt"
    
    # Attempt to load V4 metrics for comparison if possible (simplistic mention)
    report_content = f"""=== SafeTalk Model Evaluation Summary V5 ===
Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

Dataset: {input_path.name}
Total rows used: {len(df)}
Train set size: {X_train.shape[0]}
Test set size: {X_test.shape[0]}

=== Metrics ===
Accuracy: {accuracy:.4f}
Scam Precision: {precision:.4f}
Scam Recall: {recall:.4f}
F1-Score (Scam): {f1:.4f}

Confusion Matrix:
{conf_matrix}

Classification Report:
{class_report}

=== V4 vs V5 Benchmarks ===
(Refer to model_report_v4.txt for exact precision)
V5 targets 10,000 unified rows with 7,000 TF-IDF features.
The recall for financial and security alerts should show measurable stability due to bulk auto-scaling.
"""
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report_content)
    
    print(f"Report saved to: {report_path}")

if __name__ == "__main__":
    train_model_v5()
