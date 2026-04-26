import pandas as pd
import random
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def generate_hard_cases_v8():
    output_dir = base_dir / "data" / "expansions"
    hard_cases_path = output_dir / "hard_cases_v8.csv"
    output_dir.mkdir(parents=True, exist_ok=True)
    
    data = []

    # 1. HARD NEGATIVES (SAFE messages with risky words)
    # Focus: login, havola, kod, to'lov, tasdiqlash
    hard_negatives = [
        {"text": "Hurmatli mijoz, ClickEvolution ilovasiga xush kelibsiz! Parolingizni hech kimga ayta ko'rmang.", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "security_info"},
        {"text": "Sizning Humo kartangizdan 50,000 so'm yechildi. Tafsilotlar: http://bank.uz/stats", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "transaction,link"},
        {"text": "Loginni tasdiqlash uchun SMS kodi: 7721. Uni begonalarga bermang.", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "otp,security"},
        {"text": "Beeline: To'lovingiz qabul qilindi. Balans: 12,500 so'm. Rahmat!", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "payment,service"},
        {"text": "Yandex Go: Haydovchi yetib keldi. Mashina raqami: 01A123BA.", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "service"},
        {"text": "UzPost: Sizning posilkangiz aloqa bo'limiga yetib keldi. Olib ketishingiz mumkin.", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "delivery"},
        {"text": "Tahlil natijalaringiz tayyor. Ularni shaxsiy kabinetingizda ko'ring: http://med.uz/results", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "medical,link"},
        {"text": "Sizning arizangiz ko'rib chiqildi. Statusni tekshirish: http://my.gov.uz", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "gov,link"},
        {"text": "Hurmatli foydalanuvchi, xizmat ko'rsatish shartlari o'zgardi. Batafsil: http://payme.uz/terms", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "legal,link"},
        {"text": "Bugun soat 21:00 da texnik ishlar olib boriladi, tizimda uzilishlar bo'lishi mumkin.", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "technical"},
        {"text": "To'lov muvaffaqiyatli amalga oshirildi. Kvitansiya: http://click.uz/r/12345", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "receipt,link"},
        {"text": "Shaxsiy hisobingizga kirish uchun vaqtinchalik kod: 0982. Hech kimga bermang.", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "otp,security"},
        {"text": "Karta ma'lumotlarini yangilash uchun ofisimizga tashrif buyuring.", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "offline_request"},
        {"text": "Tasdiqlash kodi: 111222. SafeTalk sizdan hech qachon kod so'ramaydi.", "risk_label": "SAFE", "intent_label": "INFO", "language": "uz", "tags": "otp_warning"},
    ]
    
    # 2. HARD SUSPICIOUS (Bait/Promo without direct malicious action)
    hard_suspicious = [
        {"text": "Ajoyib imkoniyat! Bugunoq ro'yxatdan o'ting va sovg'alarga ega bo'ling.", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "promo,bait"},
        {"text": "Siz uchun maxsus taklif bor! Batafsil ma'lumot olish uchun kiring.", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "promo,bait"},
        {"text": "Profilingizni yangilang va barcha funksiyalardan foydalaning.", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "account_update,bait"},
        {"text": "Aksiyada qatnashing va qimmatbaho sovrinlarni yutib oling!", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "promo,bait"},
        {"text": "Maxsus chegirmalar faqat siz uchun! Hoziroq ko'rib chiqing.", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "promo,urgency"},
        {"text": "Sizga kutilmagan sovg'a tayyorlangan. Uni olishga shoshiling!", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "bait,urgency"},
        {"text": "Telegram Premium yutib oldingiz deb o'ylaysizmi? Tekshirib ko'ring.", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "bait,curiosity"},
        {"text": "Bonus olish uchun to'g'ri vaqt keldi. Imkoniyatni boy bermang.", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "bait,promo"},
        {"text": "Hisobingizni faollashtiring, aks holda funksiyalar ishlamasligi mumkin.", "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "uz", "tags": "account,pressure"},
    ]

    # 3. DANGEROUS DIVERSITY (100+ unique patterns)
    dangerous_data = []
    
    phishing_templates = [
        "Xavfsizlik xizmati: {bank} kartangizdan shubhali to'lov aniqlandi. Bloklash: {link}",
        "{bank} to'lov tizimi: Sizning hisobingiz vaqtincha cheklangan. Tasdiqlang: {link}",
        "Bank-online: Sizga {amount} so'm keshbek yozildi. Karta ma'lumotlarini to'ldiring: {link}",
        "Telegram Admin: Profilingiz qoidabuzarlik uchun o'chirilishi mumkin. Kodni kiriting: {link}",
        "Sizga bepul Telegram Premium yuborildi. Faollashtirish uchun botga kiring: @{bot}",
        "Yangi {app} ilovasini o'rnating va {amount} so'm bonus oling: {link}",
        "Darhol to'lov qilinmasa, hisobingizga jarima yoziladi! To'lov: {link}",
        "Akkauntingiz buzildi! Darhol parolni almashtiring: {link}",
        "Sizga {amount} so'm mukofot yozildi. Olish uchun ssilkaga kiring: {link}",
        "Profilingiz {reason} uchun bloklandi. Tiklash uchun: {link}",
        "Diqqat! {bank} kartangizdan pul yechilmoqda. Bekor qilish: {link}",
        "Sizning {app} profilingiz shikoyatga uchradi. Kodni yuboring: {code}",
        "Bepul {gift} yutib oldingiz! Hoziroq bosing: {link}",
        "{authority} ogohlantiradi: IP manzilingiz bloklandi. To'lov: {link}",
        "Yangi {app} versiyasini yuklang va bonus oling: {link}",
        "Siz tanlandingiz! {job} bilan oyiga {salary} topishni boshlang: {link}"
    ]
    
    amounts = ["250,000", "500,000", "1,000,000", "2,500,000", "5,000,000", "8,000,000"]
    links = ["http://gift-uz.click", "http://verify-bank.xyz", "http://scam-pay.top", "http://check-user.online", "http://pay-fix.biz", "http://bit.ly/uz-scam", "http://t.me/secure_bot"]
    reasons = ["qoidabuzarlik", "shubhali harakatlar", "xavfsizlik testi", "shikoyatlar", "noqonuniy kirish"]
    banks = ["UzCard", "Humo", "IPAK Yo'li", "Asaka Bank", "NBU", "TBC Bank"]
    apps = ["Telegram", "WhatsApp", "Click", "Payme", "Apelsin"]
    authorities = ["MXX", "DXX", "Politsiya", "Soliq qo'mitasi", "Markaziy Bank"]
    jobs = ["Online ish", "Masofaviy ish", "Uyda o'tirib ishlash"]
    salaries = ["500$", "1000$", "2000$", "5000$"]
    gifts = ["iPhone 15", "Malibu", "Kvartira", "Noutbuk"]

    # Generate 150+ diverse dangerous messages
    for i in range(150):
        template = random.choice(phishing_templates)
        text = template.format(
            bank=random.choice(banks),
            amount=random.choice(amounts),
            link=random.choice(links),
            bot="secure_uz_bot",
            app=random.choice(apps),
            reason=random.choice(reasons),
            code=str(random.randint(10000, 99999)),
            gift=random.choice(gifts),
            authority=random.choice(authorities),
            job=random.choice(jobs),
            salary=random.choice(salaries)
        )
        dangerous_data.append({"text": text, "risk_label": "DANGEROUS", "intent_label": "REQUEST", "language": "uz", "tags": "phishing_gen"})

    # Combine data
    all_data = hard_negatives + hard_suspicious + dangerous_data
    df = pd.DataFrame(all_data).drop_duplicates(subset=['text'])
    
    # Save to CSV
    df.to_csv(hard_cases_path, index=False, encoding='utf-8')
    print(f"Generated {len(df)} unique hard cases for Stage 5 at {hard_cases_path}")
    print("\nDistribution:")
    print(df['risk_label'].value_counts())

if __name__ == "__main__":
    generate_hard_cases_v8()
