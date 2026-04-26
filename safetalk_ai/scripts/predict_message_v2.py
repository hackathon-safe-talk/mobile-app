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

def predict_messages_v2():
    vectorizer_path = base_dir / "models" / "tfidf_vectorizer_v2.pkl"
    model_path = base_dir / "models" / "safetalk_message_classifier_v2.pkl"
    
    if not vectorizer_path.exists() or not model_path.exists():
        print("V2 Model artifacts not found!")
        return
        
    vectorizer = joblib.load(vectorizer_path)
    model = joblib.load(model_path)
    
    classes = list(model.classes_)
    try:
        scam_idx = classes.index('scam')
        ham_idx = classes.index('ham')
    except ValueError:
        print("Error: Model classes do not match 'ham'/'scam'.")
        return

    test_messages = [
        "Tabriklaymiz siz 10 million so'm yutdingiz linkni bosing",
        "Kartangiz bloklandi darhol tasdiqlang",
        "Bugun dars nechida boshlanadi",
        "Telegram premium sovg'a olish uchun botga kiring",
        "Onam aytdi uyga non opkel",
        "Guruhga yangi video tashlandi 18 plus apk yuklab oling",
        "To'lovingiz amalga oshmadi iltimos havola orqali tasdiqlang",
        "Ertaga soat 9 da uchrashamiz"
    ]
    
    print("=== SafeTalk Model V2 Predictions ===\n")
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
    predict_messages_v2()
