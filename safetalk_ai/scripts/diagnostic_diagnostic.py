import joblib
import json
import os
import re

# Simulation of the ML pipeline in Python
def clean_text(text):
    if not text: return ""
    text = text.lower()
    text = re.sub(r"(https?://\S+)", r" \1 ", text)
    for ext in [".apk", ".exe", ".scr", ".msi", ".bat", ".zip", ".rar"]:
        text = text.replace(ext, f" {ext} ")
    text = re.sub(r"[^\w\s\./:]", " ", text)
    text = re.sub(r"\s+", " ", text).strip()
    return text

def run_diagnostic():
    model_path = "d:/SafeTalk/safetalk_ai/models/safetalk_message_classifier_v9.pkl"
    vectorizer_path = "d:/SafeTalk/safetalk_ai/models/tfidf_vectorizer_v9.pkl"
    
    if not os.path.exists(model_path) or not os.path.exists(vectorizer_path):
        print("Model or vectorizer not found!")
        return

    model = joblib.load(model_path)
    vectorizer = joblib.load(vectorizer_path)
    
    test_cases = [
        "Profilingizni yangilang, aks holda ayrim imkoniyatlar vaqtincha cheklanadi",
        "Akkauntingiz xavfsizligi uchun ma’lumotlarni qayta tasdiqlash tavsiya etiladi",
        "Siz uchun foydali imkoniyat mavjud, lekin uni saqlab qolish uchun ma’lumotlarni aniqlashtirish kerak"
    ]
    
    print(f"{'Case':<80} | {'DNG':<5} | {'SUS':<5} | {'SAFE':<5}")
    print("-" * 105)
    
    for msg in test_cases:
        cleaned = clean_text(msg)
        features = vectorizer.transform([cleaned])
        probs = model.predict_proba(features)[0]
        classes = model.classes_
        
        prob_dict = dict(zip(classes, probs))
        dng = prob_dict.get('DANGEROUS', 0)
        sus = prob_dict.get('SUSPICIOUS', 0.0)
        safe = prob_dict.get('SAFE', 0)
        
        print(f"{msg[:78]:<80} | {dng:.3f} | {sus:.3f} | {safe:.3f}")

if __name__ == "__main__":
    run_diagnostic()
