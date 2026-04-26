# Mocking the final SafeTalk Android logic (V9 + Diagnostic Fixes)

SIGNALS = {
    "urgency_pressure": 20,
    "promo_pressure": 20,
    "account_update_pressure": 25,
    "vague_action_request": 15,
    "curiosity_lure": 20,
    "phishing_login": 40,
    "login_intent": 25,
    "urgency_intent": 10
}

URGENCY_KEYWORDS = ["aks holda", "vaqt tugayapti", "cheklanadi"]
PROMO_KEYWORDS = ["foydali", "imkoniyat", "foydali imkoniyat"]
ACCOUNT_UPDATE_KEYWORDS = ["yangilanish", "yangilang"]
VAGUE_ACTION_KEYWORDS = ["tasdiqlash", "tasdiqlanish", "aniqlashtirish"]
CURIOSITY_KEYWORDS = ["tavsiya etiladi", "saqlab qolish uchun"]

def detect_signals(text):
    text = text.lower()
    detected = []
    if any(k in text for k in URGENCY_KEYWORDS): detected.append("urgency_pressure")
    if any(k in text for k in PROMO_KEYWORDS): detected.append("promo_pressure")
    if any(k in text for k in ACCOUNT_UPDATE_KEYWORDS): detected.append("account_update_pressure")
    if any(k in text for k in VAGUE_ACTION_KEYWORDS): detected.append("vague_action_request")
    if any(k in text for k in CURIOSITY_KEYWORDS): detected.append("curiosity_lure")
    
    # Intent mocks
    if "yangilang" in text: detected.append("login_intent")
    if "aks holda" in text: detected.append("urgency_intent")
    return detected

def fuse(rule_score, pattern_floor, dng_prob, sus_prob):
    ml_score = (dng_prob * 100 + sus_prob * 50)
    if ml_score > 100: ml_score = 100
    
    fused_risk = max(float(rule_score), float(pattern_floor))
    
    has_hard_threat = (dng_prob > 0.5) or (pattern_floor >= 70)
    
    # synergy promotion
    if sus_prob > 0.5 and not has_hard_threat and fused_risk < 40:
        fused_risk = 40
        
    # ML boost
    if dng_prob >= 0.9:
        if fused_risk < 90: fused_risk = 90
    elif dng_prob >= 0.5 or sus_prob >= 0.7:
        if ml_score > fused_risk:
            fused_risk = max(fused_risk, fused_risk * 0.4 + ml_score * 0.6)
            
    # Soft cap
    if not has_hard_threat and fused_risk > 60:
        fused_risk = 60
        
    return fused_risk

def evaluate_case(text, dng, sus):
    signals = detect_signals(text)
    rule_score = sum(SIGNALS.get(s, 0) for s in signals)
    
    # Mock PatternEngine
    soft_signals = ["account_update_pressure", "urgency_pressure", "promo_pressure", "vague_action_request"]
    soft_count = sum(1 for s in soft_signals if s in signals)
    strong_pressure = any(k in text for k in ["aks holda", "vaqt tugayapti"])
    
    pattern_floor = 0
    if soft_count >= 2 or strong_pressure:
        pattern_floor = 40
        if soft_count >= 2: pattern_floor += 10
        
    # Floor can be 80 if it was the OLD logic, but here we cap it in RiskFusion anyway.
    # We follow the NEW pattern engine which excludes login_intent from hasHardThreat.
    
    final_risk = fuse(rule_score, pattern_floor, dng, sus)
    return final_risk, signals

def run_validation():
    cases = [
        ("Profilingizni yangilang, aks holda ayrim imkoniyatlar vaqtincha cheklanadi", 0.051, 0.651, "XAVFLI (80)"),
        ("Akkauntingiz xavfsizligi uchun ma’lumotlarni qayta tasdiqlash tavsiya etiladi", 0.340, 0.571, "XAVFSIZ (< 30)"),
        ("Siz uchun foydali imkoniyat mavjud, lekin uni saqlab qolish uchun ma’lumotlarni aniqlashtirish kerak", 0.174, 0.759, "XAVFSIZ (< 30)")
    ]
    
    print(f"{'Case':<50} | {'Signals':<30} | {'Before':<15} | {'After':<10}")
    print("-" * 115)
    
    for text, dng, sus, before in cases:
        after_risk, signals = evaluate_case(text, dng, sus)
        after_label = "XAVFLI" if after_risk >= 70 else "SHUBHALI" if after_risk >= 40 else "XAVFSIZ"
        print(f"{text[:48]:<50} | {str(signals)[:28]:<30} | {before:<15} | {after_label} ({int(after_risk)})")

if __name__ == "__main__":
    run_validation()
