import pandas as pd
import numpy as np
import joblib
from pathlib import Path
from datetime import datetime
from sklearn.feature_extraction.text import TfidfVectorizer
from sklearn.model_selection import train_test_split
from sklearn.linear_model import LogisticRegression
from sklearn.metrics import accuracy_score, classification_report, confusion_matrix

base_dir = Path(__file__).resolve().parents[1]

def main():
    dataset_path = base_dir / "data" / "processed" / "messages_cleaned.csv"
    models_dir = base_dir / "models"
    outputs_dir = base_dir / "outputs"
    
    models_dir.mkdir(parents=True, exist_ok=True)
    outputs_dir.mkdir(parents=True, exist_ok=True)

    print(f"Loading dataset from: {dataset_path}")
    if not dataset_path.exists():
        print("Dataset not found!")
        return

    df = pd.read_csv(dataset_path)
    
    # Validate required columns
    required_columns = ['text', 'source', 'label', 'clean_text']
    for col in required_columns:
        if col not in df.columns:
            raise ValueError(f"Missing required column: {col}")
            
    # Drop any remaining nulls in clean_text just in case
    df = df.dropna(subset=['clean_text', 'label'])
    total_rows = len(df)
    
    print(f"Total rows: {total_rows}")
    
    X = df['clean_text']
    y = df['label']
    
    # Prepare features
    print("Extracting TF-IDF features...")
    vectorizer = TfidfVectorizer(ngram_range=(1, 2), max_features=5000, min_df=1)
    X_tfidf = vectorizer.fit_transform(X)
    
    # Split dataset
    print("Splitting dataset into train/test...")
    X_train, X_test, y_train, y_test = train_test_split(
        X_tfidf, y, test_size=0.2, random_state=42, stratify=y
    )
    
    print(f"Train set size: {X_train.shape[0]}")
    print(f"Test set size: {X_test.shape[0]}")
    print("\nTrain Label Distribution:")
    print(y_train.value_counts())
    print("\nTest Label Distribution:")
    print(y_test.value_counts())
    
    # Train model
    print("\nTraining Logistic Regression model...")
    model = LogisticRegression(max_iter=1000)
    model.fit(X_train, y_train)
    
    # Evaluate model
    print("Evaluating model...")
    y_pred = model.predict(X_test)
    
    acc = accuracy_score(y_test, y_pred)
    clf_report = classification_report(y_test, y_pred)
    conf_matrix = confusion_matrix(y_test, y_pred)
    
    print(f"\nAccuracy: {acc:.4f}")
    print("\nClassification Report:")
    print(clf_report)
    print("Confusion Matrix:")
    print(conf_matrix)
    
    # Feature Importance Analysis
    feature_names = np.array(vectorizer.get_feature_names_out())
    # LogisticRegression coef_ is (1, n_features) for binary classification
    # Assuming 'ham' is negative (0) and 'scam' is positive (1)
    # Let's check model.classes_ to be sure
    classes = model.classes_
    print(f"Model classes: {classes}")
    
    coefs = model.coef_[0]
    
    # Sort indices
    sorted_idx = np.argsort(coefs)
    
    # If classes[1] is 'scam', then high positive coefs predict 'scam'
    # If classes[0] is 'scam', then high negative coefs predict 'scam'
    if classes[1] == 'scam':
        scam_idx = sorted_idx[-20:][::-1] # top 20 positive
        ham_idx = sorted_idx[:20]        # top 20 negative
    else:
        scam_idx = sorted_idx[:20]       # top 20 negative
        ham_idx = sorted_idx[-20:][::-1] # top 20 positive
        
    print("\n=== TOP 20 SCAM-INDICATING FEATURES ===")
    for i in scam_idx:
        print(f"{feature_names[i]}: {coefs[i]:.4f}")
        
    print("\n=== TOP 20 HAM-INDICATING FEATURES ===")
    for i in ham_idx:
        print(f"{feature_names[i]}: {coefs[i]:.4f}")

    # Save trained artifacts
    vectorizer_path = models_dir / "tfidf_vectorizer.pkl"
    model_path = models_dir / "safetalk_message_classifier.pkl"
    
    joblib.dump(vectorizer, vectorizer_path)
    joblib.dump(model, model_path)
    print(f"\nSaved vectorizer to: {vectorizer_path}")
    print(f"Saved model to: {model_path}")
    
    # Create evaluation summary file
    report_path = outputs_dir / "model_report.txt"
    timestamp = datetime.now().strftime("%Y-%m-%d %H:%M:%S")
    
    report_content = f"""=== SafeTalk ML Model Evaluation Report ===
Timestamp: {timestamp}

Dataset Path used: {dataset_path}
Total Rows: {total_rows}

TF-IDF Settings:
- ngram_range: (1, 2)
- max_features: 5000
- min_df: 1

Train/Test Split Settings:
- test_size: 0.2
- random_state: 42
- stratify: labels

Model Type: LogisticRegression(max_iter=1000)

--- PERFORMANCE ---
Accuracy: {acc:.4f}

Classification Report:
{clf_report}

Confusion Matrix:
{conf_matrix}
"""

    with open(report_path, "w", encoding="utf-8") as f:
        f.write(report_content)
        
    print(f"\nSaved evaluation summary to: {report_path}")

if __name__ == "__main__":
    main()
