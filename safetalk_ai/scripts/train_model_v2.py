import pandas as pd
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, precision_score, recall_score, f1_score, confusion_matrix, classification_report
import joblib
from pathlib import Path
from datetime import datetime

base_dir = Path(__file__).resolve().parents[1]

def train_v2():
    input_path = base_dir / "data" / "processed" / "messages_cleaned_v2.csv"
    
    if not input_path.exists():
        print(f"Error: Could not find {input_path}")
        return
        
    print(f"Loading dataset from: {input_path}")
    df = pd.read_csv(input_path)
    
    required_cols = ['text', 'source', 'label', 'clean_text']
    for col in required_cols:
        if col not in df.columns:
            print(f"Error: Missing required column '{col}'")
            return
            
    # Drop rows with empty clean_text just in case
    df = df.dropna(subset=['clean_text'])
    df = df[df['clean_text'].str.strip() != ""]
    
    print(f"Total rows: {len(df)}")
    
    print("Extracting TF-IDF features...")
    vectorizer = TfidfVectorizer(
        ngram_range=(1, 2),
        max_features=5000,
        min_df=1
    )
    
    X = vectorizer.fit_transform(df['clean_text'])
    y = df['label']
    
    print("Splitting dataset into train/test...")
    X_train, X_test, y_train, y_test = train_test_split(
        X, y, test_size=0.2, random_state=42, stratify=y
    )
    
    print(f"Train set size: {X_train.shape[0]}")
    print(f"Test set size: {X_test.shape[0]}")
    
    print("Training Logistic Regression model...")
    model = LogisticRegression(max_iter=1000)
    model.fit(X_train, y_train)
    
    print("Evaluating model...")
    y_pred = model.predict(X_test)
    
    # Calculate metrics
    scam_label = 'scam'
    accuracy = accuracy_score(y_test, y_pred)
    # Get indices for scam class dynamically if needed, or rely on pos_label
    # Since classes are 'ham' and 'scam', we specify pos_label='scam'
    precision = precision_score(y_test, y_pred, pos_label=scam_label)
    recall = recall_score(y_test, y_pred, pos_label=scam_label)
    f1 = f1_score(y_test, y_pred, pos_label=scam_label)
    conf_matrix = confusion_matrix(y_test, y_pred)
    class_report = classification_report(y_test, y_pred)
    
    print(f"\nAccuracy: {accuracy:.4f}")
    print(f"Scam Precision: {precision:.4f}")
    print(f"Scam Recall: {recall:.4f}")
    print(f"F1-Score: {f1:.4f}")
    print("\nConfusion Matrix:")
    print(conf_matrix)
    print("\nClassification Report:")
    print(class_report)
    
    # Save artifacts
    models_dir = base_dir / "models"
    models_dir.mkdir(parents=True, exist_ok=True)
    
    vectorizer_path = models_dir / "tfidf_vectorizer_v2.pkl"
    model_path = models_dir / "safetalk_message_classifier_v2.pkl"
    
    joblib.dump(vectorizer, vectorizer_path)
    joblib.dump(model, model_path)
    
    print(f"\nSaved vectorizer to: {vectorizer_path}")
    print(f"Saved model to: {model_path}")
    
    # Generate output report
    outputs_dir = base_dir / "outputs"
    outputs_dir.mkdir(parents=True, exist_ok=True)
    report_path = outputs_dir / "model_report_v2.txt"
    
    # Create the report content
    report_content = f"""=== SafeTalk Model Evaluation Summary V2 ===
Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}

Dataset: {input_path.name}
Total rows used: {len(df)}

=== Model Configuration ===
Algorithm: LogisticRegression(max_iter=1000)
Feature Extraction: TfidfVectorizer(ngram_range=(1, 2), max_features=5000, min_df=1)
Train/Test Split: 80/20 (Stratified)

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
The model was retrained on the newly expanded Uzbek dataset.
Compare these metrics against v1 offline logs to confirm performance improvements in the target language.
"""
    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report_content)
        
    print(f"\nSaved evaluation summary to: {report_path}")

if __name__ == "__main__":
    train_v2()
