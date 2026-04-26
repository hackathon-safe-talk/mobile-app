import joblib
import re
from pathlib import Path

# Resolve base directory
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

def get_risk_band(scam_prob):
    # Risk bands:
    # Safe = 0% – 19.99%
    # Suspicious = 20% – 54.99%
    # Dangerous = 55% – 100%
    prob_pct = scam_prob * 100
    if prob_pct <= 19.99:
        return "Safe"
    elif prob_pct <= 54.99:
        return "Suspicious"
    else:
        return "Dangerous"

def predict_v6():
    vectorizer_path = base_dir / "models" / "tfidf_vectorizer_v6.pkl"
    model_path = base_dir / "models" / "safetalk_message_classifier_v6.pkl"
    
    if not vectorizer_path.exists() or not model_path.exists():
        print("Model V6 artifacts not found!")
        return
        
    vectorizer = joblib.load(vectorizer_path)
    model = joblib.load(model_path)
    
    classes = list(model.classes_)
    scam_idx = classes.index('scam')
    ham_idx = classes.index('ham')

    test_messages = [
        "Kartangiz bloklandi darhol tasdiqlang",
        "Hisobingizdan 900000 so'm yechildi",
        "Agar bu siz bo'lmasangiz tasdiqlang",
        "Telegram premium sovg'a olish uchun bosing",
        "To'lov amalga oshmadi havola orqali tasdiqlang",
        "Vasha karta bloklandi. Tasdiqlash uchun linkni bosing",
        "Hisobingizdan 120000 som spisanie. Agar bu siz bo'lmasangiz cancel qiling",
        "Bugun dars nechida boshlanadi",
        "Onam aytdi non opkel",
        "Segodnya uchrashuv soat nechida"
    ]
    
    print("=== SafeTalk Model V6 Predictions (Multilingual) ===\n")
    for msg in test_messages:
        cleaned_msg = clean_text(msg)
        features = vectorizer.transform([cleaned_msg])
        
        prediction = model.predict(features)[0]
        probabilities = model.predict_proba(features)[0]
        
        scam_prob = probabilities[scam_idx]
        ham_prob = probabilities[ham_idx]
        risk_band = get_risk_band(scam_prob)
        
        print(f"Original:   {msg}")
        print(f"Cleaned:    {cleaned_msg}")
        print(f"Prediction: {prediction}")
        print(f"Scam prob:  {scam_prob:.4f}")
        print(f"Ham prob:   {ham_prob:.4f}")
        print(f"Risk Band:  {risk_band}")
        print("-" * 50)

if __name__ == "__main__":
    predict_v6()
