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

def test_v9_smoke():
    vectorizer_path = base_dir / "models" / "tfidf_vectorizer_v9.pkl"
    model_path = base_dir / "models" / "safetalk_message_classifier_v9.pkl"
    
    if not vectorizer_path.exists() or not model_path.exists():
        print("V9 artifacts not found!")
        return
        
    vectorizer = joblib.load(vectorizer_path)
    model = joblib.load(model_path)
    
    test_messages = [
        # SAFE
        "Humo: Sizning tasdiqlash kodingiz 482911. Kodni hech kimga bermang.",
        "Uzum Bank: Kartangizdan 54 000 so'm yechildi. Agar bu amal sizga tegishli bo'lmasa, rasmiy ilova orqali tekshiring.",
        # SUSPICIOUS
        "Tabriklaymiz! Siz uchun maxsus sovg'a tayyor. Batafsil ma'lumotni ko'rish uchun havolaga o'ting.",
        "Profilingizni yangilab qo'ying, aks holda ayrim imkoniyatlar vaqtincha cheklanadi.",
        # DANGEROUS
        "Hisobingiz bloklandi! Darhol login qiling va ma'lumotlaringizni tasdiqlang: http://security-verify-uz.com",
        "Yangi xavfsizlik ilovasini yuklab o'rnating, aks holda bankingizga kirish vaqtincha to'xtatiladi: http://protect-app.apk"
    ]
    
    print("=== SafeTalk V9 Smoke Test Results ===\n")
    for i, msg in enumerate(test_messages, 1):
        cleaned_msg = clean_text(msg)
        features = vectorizer.transform([cleaned_msg])
        
        prediction = model.predict(features)[0]
        probabilities = model.predict_proba(features)[0]
        prob_dict = dict(zip(model.classes_, [float(p) for p in probabilities]))
        
        print(f"Test {i}: {msg}")
        print(f"Prediction: {prediction}")
        print(f"Confidence: {prob_dict[prediction]:.4f}")
        print(f"Full Probs: {prob_dict}")
        print("-" * 30)

if __name__ == "__main__":
    test_v9_smoke()
