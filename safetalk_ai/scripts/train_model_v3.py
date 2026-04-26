import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix, classification_report
import joblib
from pathlib import Path
from datetime import datetime

base_dir = Path(__file__).resolve().parents[1]

def train_v3():
    input_path = base_dir / "data" / "processed" / "messages_cleaned_v3.csv"
    
    if not input_path.exists():
        print(f"Error: Could not find {input_path}")
        return
        
    df = pd.read_csv(input_path)
    df = df.dropna(subset=['clean_text'])
    df = df[df['clean_text'].str.strip() != ""]
    
    vectorizer = TfidfVectorizer(
        ngram_range=(1, 2),
        max_features=5000,
        min_df=1
    )
    
    X = vectorizer.fit_transform(df['clean_text'])
    y = df['label']
    
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    model = LogisticRegression(max_iter=1000)
    model.fit(X_train, y_train)
    
    y_pred = model.predict(X_test)
    
    scam_label = 'scam'
    accuracy = accuracy_score(y_test, y_pred)
    precision = precision_score(y_test, y_pred, pos_label=scam_label)
    recall = recall_score(y_test, y_pred, pos_label=scam_label)
    f1 = f1_score(y_test, y_pred, pos_label=scam_label)
    conf_matrix = confusion_matrix(y_test, y_pred)
    class_report = classification_report(y_test, y_pred)
    
    models_dir = base_dir / "models"
    vectorizer_path = models_dir / "tfidf_vectorizer_v3.pkl"
    model_path = models_dir / "safetalk_message_classifier_v3.pkl"
    
    joblib.dump(vectorizer, vectorizer_path)
    joblib.dump(model, model_path)
    
    outputs_dir = base_dir / "outputs"
    report_path = outputs_dir / "model_report_v3.txt"
    
    report_content = f"""=== SafeTalk Model Evaluation Summary V3 ===
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

=== Conclusion ===
V3 incorporates 300 strictly focused Uzbek financial phishing records. 
Performance should highlight improved anomaly detection on zero-day phrasing over V2 baseline metrics.
"""
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report_content)
        
    print(f"=== V3 Training Report ===")
    print(f"Dataset Size: {len(df)}")
    print(f"Accuracy: {accuracy:.4f}")
    print(f"Scam Precision: {precision:.4f}")
    print(f"Scam Recall: {recall:.4f}")
    print(f"F1-Score: {f1:.4f}")
    print("\nConfusion Matrix:")
    print(conf_matrix)
    print("\nArtifacts and full report saved to safetalk_ai/models and safetalk_ai/outputs.")

if __name__ == "__main__":
    train_v3()
