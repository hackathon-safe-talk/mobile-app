import joblib
import re
from pathlib import Path
import numpy as np

base_dir = Path(__file__).resolve().parents[1]

def clean_text(text):
    if not isinstance(text, str): return ""
    text = text.lower()
    text = re.sub(r'https?://\S+|www\.\S+', ' ', text)
    text = re.sub(r'[^\w\s]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def test_model(v_suffix):
    vectorizer_path = base_dir / "models" / f"tfidf_vectorizer_{v_suffix}.pkl"
    model_path = base_dir / "models" / f"safetalk_message_classifier_{v_suffix}.pkl"
    
    if not vectorizer_path.exists() or not model_path.exists():
        print(f"Model {v_suffix} artifacts not found!")
        return
        
    vectorizer = joblib.load(vectorizer_path)
    model = joblib.load(model_path)
    
    test_messages = [
        "Hisobingiz bloklandi! Darhol login qiling: http://fake-link.com",
        "Humo: Sizning kodingiz 1234. Uni hech kimga bermang."
    ]
    
    print(f"=== Testing {v_suffix} Model Behavior ===")
    for msg in test_messages:
        cleaned_msg = clean_text(msg)
        features = vectorizer.transform([cleaned_msg])
        
        prediction = model.predict(features)[0]
        probabilities = model.predict_proba(features)[0]
        
        print(f"Original:   {msg}")
        print(f"Prediction: {prediction}")
        print(f"Probabilities: {dict(zip(model.classes_, probabilities))}")
        print("-" * 30)

if __name__ == "__main__":
    test_model("v8")
    test_model("v9")
