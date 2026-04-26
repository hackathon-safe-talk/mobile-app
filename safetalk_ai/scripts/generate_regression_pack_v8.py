import pandas as pd
import json
from pathlib import Path
import joblib

base_dir = Path(__file__).resolve().parents[1]

def generate_regression_pack_v8():
    model_path = base_dir / "models" / "safetalk_message_classifier_v8.pkl"
    vectorizer_path = base_dir / "models" / "tfidf_vectorizer_v8.pkl"
    output_path = base_dir / "data" / "regression_pack_v8.json"
    
    if not model_path.exists() or not vectorizer_path.exists():
        print("Error: Models not found.")
        return

    model = joblib.load(model_path)
    vectorizer = joblib.load(vectorizer_path)

    # Curated regression cases
    test_cases = [
        # HARD NEGATIVES (Expected: SAFE)
        {"text": "Loginni tasdiqlash uchun SMS kodi: 1234. Uni hech kimga bermang.", "expected": "SAFE", "category": "hard_negative"},
        {"text": "Sizning Humo kartangizdan 50,000 so'm yechildi. http://bank.uz", "expected": "SAFE", "category": "hard_negative"},
        {"text": "Yandex Go: Haydovchi yetib keldi. Mashina: 01A777AA", "expected": "SAFE", "category": "service"},
        
        # SUSPICIOUS (Expected: SUSPICIOUS)
        {"text": "Ajoyib imkoniyat! Bugunoq ro'yxatdan o'ting.", "expected": "SUSPICIOUS", "category": "promo"},
        {"text": "Profilingizni yangilang va bonus oling.", "expected": "SUSPICIOUS", "category": "bait"},
        
        # DANGEROUS (Expected: DANGEROUS)
        {"text": "Hisobingiz bloklandi! Darhol login qiling: http://verify.click", "expected": "DANGEROUS", "category": "phishing"},
        {"text": "Sizga 1,000,000 so'm yordam puli berildi. Karta raqamingizni kiriting: http://uz-scam.top", "expected": "DANGEROUS", "category": "finance_scam"},
        {"text": "Yangi ClickEvolution ilovasini o'rnating: http://click.apk", "expected": "DANGEROUS", "category": "malware"},
    ]

    results = []
    print("Running regression test pack...")
    
    # Needs the same preprocessing as training
    from preprocess_text_v8 import preprocess_v8

    for case in test_cases:
        clean = preprocess_v8(case['text'])
        feat = vectorizer.transform([clean])
        pred = model.predict(feat)[0]
        probs = model.predict_proba(feat)[0]
        
        # Mapping labels to indices for probability report
        labels = model.classes_
        prob_dict = {label: float(prob) for label, prob in zip(labels, probs)}
        
        results.append({
            "text": case['text'],
            "expected": case['expected'],
            "predicted": pred,
            "status": "PASS" if pred == case['expected'] else "FAIL",
            "probabilities": prob_dict,
            "category": case['category']
        })

    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(results, f, indent=4, ensure_ascii=False)

    print(f"Regression pack results saved to {output_path}")
    
    passed = sum(1 for r in results if r['status'] == "PASS")
    print(f"Summary: {passed}/{len(results)} Passed")

if __name__ == "__main__":
    generate_regression_pack_v8()
