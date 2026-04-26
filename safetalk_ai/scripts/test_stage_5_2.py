import re

# Mocking the constants and logic from Android implementation
SAFE_MAX = 39
SUSPICIOUS_MIN = 40
SUSPICIOUS_MAX = 69
DANGEROUS_MIN = 70

SIGNALS = {
    "urgency_pressure": 20,
    "promo_pressure": 20,
    "account_update_pressure": 25,
    "vague_action_request": 15,
    "phishing_login": 40,
    "otp_request": 45,
    "financial_data_request": 25,
    "card_phishing_bait": 35,
    "suspicious_link": 30
}

URGENCY_KEYWORDS = ["darhol", "zudlik bilan", "aks holda", "vaqt tugayapti", "cheklanadi"]
PROMO_KEYWORDS = ["maxsus taklif", "foydali taklif", "imkoniyatni boy bermang", "siz uchun maxsus"]
ACCOUNT_UPDATE_KEYWORDS = ["profilingizni yangilang", "hisobingizni tasdiqlang", "hisobingiz bo'yicha yangilanish", "akkauntni tasdiqlang", "yangilanish mavjud", "hisobingiz bo'yicha"]
VAGUE_ACTION_KEYWORDS = ["tekshirib chiqing", "yangilang", "tasdiqlang", "ko'rib chiqing"]
STRONG_PRESSURE_PHRASES = ["aks holda", "vaqt tugayapti", "cheklanadi"]

def detect_signals(text):
    text = text.lower()
    detected = []
    if any(k in text for k in URGENCY_KEYWORDS): detected.append("urgency_pressure")
    if any(k in text for k in PROMO_KEYWORDS): detected.append("promo_pressure")
    if any(k in text for k in ACCOUNT_UPDATE_KEYWORDS): detected.append("account_update_pressure")
    if any(k in text for k in VAGUE_ACTION_KEYWORDS): detected.append("vague_action_request")
    if "login" in text: detected.append("phishing_login")
    if "http" in text: detected.append("suspicious_link")
    return detected

def simulate_pattern_engine(text, signals):
    text = text.lower()
    has_link = "http" in text
    has_apk = ".apk" in text
    
    # Hard Threat Check
    has_hard_threat = (has_link or has_apk or "phishing_login" in signals or 
                       "otp_request" in signals or "financial_data_request" in signals)
    
    # Soft Manipulation Detection
    soft_signals = ["account_update_pressure", "urgency_pressure", "promo_pressure", "vague_action_request"]
    detected_soft_count = sum(1 for s in soft_signals if s in signals)
    
    # Strong Pressure Check
    has_strong_pressure = any(p in text for p in STRONG_PRESSURE_PHRASES)
    
    highest_floor = 0
    
    # Base signal score
    base_score = sum(SIGNALS.get(s, 0) for s in signals)
    
    # Floor/Cap Logic
    floor_applied = False
    cap_applied = False
    
    if not has_hard_threat:
        if detected_soft_count >= 2 or has_strong_pressure:
            highest_floor = max(highest_floor, 40)
            floor_applied = True
            if detected_soft_count >= 2:
                highest_floor += 10 # Bonus
        
        if highest_floor > 60:
            highest_floor = 60
            cap_applied = True
    else:
        # Hard threat rules (simplified)
        if has_link: highest_floor = max(highest_floor, 70)
        if has_apk: highest_floor = max(highest_floor, 80)

    final_risk = max(base_score, highest_floor)
    
    # Apply soft cap to final risk if no hard threat
    if not has_hard_threat and final_risk > 60:
        final_risk = 60
        cap_applied = True
        
    return final_risk, floor_applied, cap_applied, has_hard_threat

def run_tests():
    test_cases = [
        "Sizning tasdiqlash kodingiz 123456",
        "MyGov: Arizangiz qabul qilindi. Holatini portal orqali kuzatishingiz mumkin.",
        "Profilingizni yangilang, aks holda imkoniyatlar vaqtincha cheklanadi",
        "Hisobingiz bo'yicha muhim yangilanish mavjud. Tekshirib chiqing",
        "Tabriklaymiz! Siz uchun maxsus taklif bor",
        "Login qiling: http://fake-site.com",
        "Karta ma'lumotlarini kiriting: http://secure-pay.xyz",
        "Yangi ilovani yuklab o'rnating: http://update.apk"
    ]
    
    print(f"{'Case':<70} | {'Score':<5} | {'Level':<10} | {'Floor':<5} | {'Cap':<5} | {'Hard'}")
    print("-" * 115)
    
    for msg in test_cases:
        signals = detect_signals(msg)
        risk, floor, cap, hard = simulate_pattern_engine(msg, signals)
        level = "XAVFLI" if risk >= 70 else "SHUBHALI" if risk >= 40 else "XAVFSIZ"
        print(f"{msg[:68]:<70} | {risk:<5} | {level:<10} | {str(floor):<5} | {str(cap):<5} | {str(hard)}")

if __name__ == "__main__":
    run_tests()
