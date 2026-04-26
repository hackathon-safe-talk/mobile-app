import joblib
import re
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def clean_text(text):
    if not isinstance(text, str): return ""
    text = text.lower()
    text = re.sub(r'https?://\S+|www\.\S+', ' ', text)
    text = re.sub(r'\b[A-Za-z0-9._%+-]+@[A-Za-z0-9.-]+\.[A-Z|a-z]{2,}\b', ' ', text)
    text = re.sub(r'\+?\d[\d\-\s()]{6,}\d', ' ', text)
    text = re.sub(r'[^\w\s]', ' ', text)
    text = re.sub(r'\s+', ' ', text).strip()
    return text

def predict_messages_v3():
    vectorizer_path = base_dir / "models" / "tfidf_vectorizer_v3.pkl"
    model_path = base_dir / "models" / "safetalk_message_classifier_v3.pkl"
    
    vectorizer = joblib.load(vectorizer_path)
    model = joblib.load(model_path)
    
    classes = list(model.classes_)
    scam_idx = classes.index('scam')
    ham_idx = classes.index('ham')

    test_messages = [
        "Kartangiz bloklandi darhol tasdiqlang",
        "Hisobingizdan 900000 so'm yechildi",
        "To'lov amalga oshmadi havola orqali tasdiqlang",
        "Telegram premium sovg'a olish uchun bosing",
        "Bugun dars nechida boshlanadi",
        "Onam aytdi non opkel"
    ]
    
    print("=== SafeTalk Model V3 Predictions ===\n")
    for msg in test_messages:
        cleaned_msg = clean_text(msg)
        features = vectorizer.transform([cleaned_msg])
        
        prediction = model.predict(features)[0]
        probabilities = model.predict_proba(features)[0]
        
        scam_prob = probabilities[scam_idx]
        ham_prob = probabilities[ham_idx]
        
        print(f"Original: {msg}")
        print(f"Cleaned:  {cleaned_msg}")
        print(f"Prediction: {prediction}")
        print(f"Scam probability: {scam_prob:.2f}")
        print(f"Ham probability:  {ham_prob:.2f}")
        print("-" * 40)

if __name__ == "__main__":
    predict_messages_v3()
