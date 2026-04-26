import sys
import re
import joblib
from pathlib import Path

# Resolve base_dir dynamically relative to script location
base_dir = Path(__file__).resolve().parents[1]

def clean_text(text):
    """
    Identical string cleaning logic as used in preprocess_text.py
    during the original dataset construction.
    """
    if not isinstance(text, str):
        return ""
    
    # convert text to lowercase
    text = text.lower()
    
    # remove URLs (http, https, www links)
    text = re.sub(r'https?://\S+|www\.\S+', ' ', text)
    
    # remove email addresses
    text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b', ' ', text)
    
    # remove phone numbers
    text = re.sub(r'\+?\d[\d\-\s()]{6,}\d', ' ', text)
    
    # remove punctuation and emojis
    text = re.sub(r'[^\w\s]', ' ', text)
    
    # remove extra whitespace
    text = re.sub(r'\s+', ' ', text).strip()
    
    return text

def predict_messages(messages):
    vectorizer_path = base_dir / "models" / "tfidf_vectorizer.pkl"
    model_path = base_dir / "models" / "safetalk_message_classifier.pkl"
    
    if not vectorizer_path.exists() or not model_path.exists():
        print("Model artifacts not found! Please run train_model.py first.")
        return
        
    # Load model files
    vectorizer = joblib.load(vectorizer_path)
    model = joblib.load(model_path)
    
    # model.classes_ usually returns ['ham', 'scam']
    # We will dynamically find which index is which
    classes = list(model.classes_)
    try:
        scam_idx = classes.index('scam')
        ham_idx = classes.index('ham')
    except ValueError:
        print(f"Error: Model classes {classes} do not match 'ham'/'scam'.")
        return

    print("=== SafeTalk Model Predictions ===\n")
    for msg in messages:
        # Clean the message
        cleaned_msg = clean_text(msg)
        
        # Convert message to TF-IDF features
        features = vectorizer.transform([cleaned_msg])
        
        # Run prediction
        prediction = model.predict(features)[0]
        probabilities = model.predict_proba(features)[0]
        
        scam_prob = probabilities[scam_idx]
        ham_prob = probabilities[ham_idx]
        
        # Print results
        print(f"Original: {msg}")
        print(f"Cleaned:  {cleaned_msg}")
        print(f"Prediction: {prediction}")
        print(f"Scam probability: {scam_prob:.2f}")
        print(f"Ham probability:  {ham_prob:.2f}")
        print("-" * 40)

if __name__ == "__main__":
    # Add multiple test messages
    examples = [
        "Free entry win prize now",
        "Salom qalaysan bugun nima qilyapsan",
        "Claim your reward now",
        "Bugun uchrashamizmi",
        "You won 1000 dollars click link"
    ]
    
    predict_messages(examples)
