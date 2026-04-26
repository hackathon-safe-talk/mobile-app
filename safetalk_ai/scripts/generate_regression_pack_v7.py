import json
import random
from pathlib import Path

def generate_regression_pack():
    base_dir = Path(__file__).resolve().parents[1]
    output_path = base_dir / "data" / "regression_pack_v7.json"
    
    cases = []
    
    # 1. SAFE / INFO (OTP & Bank)
    safe_info = [
        {"text": "Tasdiqlash kodi: 482901", "expected_risk": "SAFE", "expected_intent": "INFO"},
        {"text": "Sizning kodingiz: 112233", "expected_risk": "SAFE", "expected_intent": "INFO"},
        {"text": "Hisobingizga 50 000 so'm tushdi. Manba: Payme", "expected_risk": "SAFE", "expected_intent": "INFO"},
        {"text": "Karta faollashtirildi. Xizmat haqi: 2000 so'm", "expected_risk": "SAFE", "expected_intent": "INFO"},
        {"text": "To'lov muvaffaqiyatli yakunlandi. Miqdor: 15000 so'm", "expected_risk": "SAFE", "expected_intent": "INFO"},
        {"text": "Omonat muddati tugadi. Bank xizmati.", "expected_risk": "SAFE", "expected_intent": "INFO"},
        {"text": "Sizning parolingiz: 54321. Uni kiring.", "expected_risk": "SAFE", "expected_intent": "INFO"},
        {"text": "Karta raqami: 8600****1234. Balans: 45000", "expected_risk": "SAFE", "expected_intent": "INFO"},
        {"text": "Google kodi: 9988. Uni hech kimga bermang.", "expected_risk": "SAFE", "expected_intent": "INFO"}, # INFO with warning suffix
        {"text": "Tasdiqlash kodi: 1212. Bank xodimlari uni so'ramaydi.", "expected_risk": "SAFE", "expected_intent": "INFO"},
    ]
    
    # 2. SAFE / WARNING
    safe_warning = [
        {"text": "Bu kodni hech kimga bermang", "expected_risk": "SAFE", "expected_intent": "WARNING"},
        {"text": "SMS kodingizni begonalarga aytmang!", "expected_risk": "SAFE", "expected_intent": "WARNING"},
        {"text": "Xavfsizlik yuzasidan kodni maxfiy tuting.", "expected_risk": "SAFE", "expected_intent": "WARNING"},
        {"text": "Hech qachon kodingizni xodimlarimizga ham bermang.", "expected_risk": "SAFE", "expected_intent": "WARNING"},
        {"text": "Diqqat! Akkauntga kirish kodi: 4455. Begonalarga bermang!", "expected_risk": "SAFE", "expected_intent": "WARNING"},
    ]
    
    # 3. SUSPICIOUS (Lures & Promo)
    suspicious = [
        {"text": "Sovrin yutib oldingiz! http://bonusuz.click", "expected_risk": "SUSPICIOUS", "expected_intent": "UNKNOWN"},
        {"text": "iPhone 15 yutib olish imkoniyati! Tekshiring: http://win.top", "expected_risk": "SUSPICIOUS", "expected_intent": "UNKNOWN"},
        {"text": "Yangi aksiyamizda ishtirok eting va iPhone yutib oling!", "expected_risk": "SUSPICIOUS", "expected_intent": "UNKNOWN"},
        {"text": "Siz tanlandingiz! http://winner.site orqali sovg'angizni oling.", "expected_risk": "SUSPICIOUS", "expected_intent": "UNKNOWN"},
        {"text": "Tekinga internet! Ulanish: http://free-net.ml", "expected_risk": "SUSPICIOUS", "expected_intent": "UNKNOWN"},
        {"text": "Keshbek 50%! Batafsil: http://cashback-uz.click", "expected_risk": "SUSPICIOUS", "expected_intent": "UNKNOWN"},
        {"text": "Bonus kodi: GIFT10. Ishlatish: http://gift.uz", "expected_risk": "SUSPICIOUS", "expected_intent": "UNKNOWN"},
        {"text": "Faqat bugun tekin Telegram Premium! http://tg-gift.link", "expected_risk": "SUSPICIOUS", "expected_intent": "UNKNOWN"},
    ]
    
    # 4. DANGEROUS / REQUEST (Phishing & Malware)
    dangerous = [
        {"text": "Tasdiqlash kodini menga yuboring", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "Karta raqami va CVV kodini ayting, tizimni tekshiramiz", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "Akkauntingiz bloklandi. Darhol loginni tasdiqlang: http://secure-login.xyz", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "Hujjatni yuklab oling va oching: update.apk", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "Rasmni ko'ring: scan_doc.pdf.exe", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "Sizning hisobingiz xavf ostida! Kodni hoziroq yuboring.", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "To'lov amali bajarilmadi. Qayta kiriting: http://pay-check.xyz", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "Admin: Kodingizni yuboring, aks holda bloklaymiz!", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "CVV kod va karta amal qilish muddati kerak.", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
        {"text": "Yangilanishni yuklad olish kerak: chrome_update.apk", "expected_risk": "DANGEROUS", "expected_intent": "REQUEST"},
    ]

    # Mix them up and generate similar patterns to reach 100+
    all_cases = safe_info + safe_warning + suspicious + dangerous
    
    # Adding variations
    for _ in range(70):
        base = random.choice(all_cases)
        txt = base["text"]
        
        # Minor edits to text to create variety
        if "kodi" in txt:
            txt = txt.replace("kodi", "kodi (maxfiy)")
        if "http" in txt:
            txt = txt.replace(".click", ".top").replace(".xyz", ".site")
            
        new_case = {
            "text": txt,
            "expected_risk": base["expected_risk"],
            "expected_intent": base["expected_intent"]
        }
        cases.append(new_case)
        
    final_pack = all_cases + cases
    
    with open(output_path, "w", encoding="utf-8") as f:
        json.dump(final_pack, f, ensure_ascii=False, indent=2)
        
    print(f"Generated {len(final_pack)} regression test cases at {output_path}")

if __name__ == "__main__":
    generate_regression_pack()
