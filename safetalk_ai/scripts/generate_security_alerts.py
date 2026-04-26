import pandas as pd
from pathlib import Path
import random

base_dir = Path(__file__).resolve().parents[1]

def generate_security_alerts():
    output_dir = base_dir / "data" / "security_alert_expansion"
    output_dir.mkdir(parents=True, exist_ok=True)
    out_path = output_dir / "uz_security_alert_scams.csv"
    
    data = []
    
    # Categories:
    # 1. suspicious bank transactions
    # 2. money withdrawal alerts
    # 3. unauthorized login
    # 4. security verification
    # 5. account freeze warnings

    # suspicious_transaction - 50
    suspicious_transaction = []
    for i in range(50):
        bank = random.choice(['HUMO', 'Uzcard', 'Xalq Banki', 'NBU', 'Kapitalbank', 'Agrobank'])
        link = random.choice(['https://bank-uz.com', 'bit.ly/bank-auth', 'http://tasdiq-bank.uz/sms', 'http://security-uz.com/alert'])
        suspicious_transaction.append(f"Sizning {bank} kartangiz orqali shubhali tranzaksiya amalga oshirildi. Bekor qilish uchun bosing: {link}?id={i}")

    # money_withdrawal - 50
    money_withdrawal = []
    for i in range(50):
        amount = random.choice(['980000', '1500000', '420000', '50000', '3200000'])
        link = random.choice(['http://bekor-uz.com', 'https://uz-bank-cancel.com/verify', 'bit.ly/refund-uz'])
        money_withdrawal.append(f"Hisobingizdan {amount} so'm yechib olindi (Kod: {random.randint(100, 999)}). Agar bu siz bo'lmasangiz darhol tasdiqlang: {link}/{i}")

    # unauthorized_login - 50
    unauthorized_login = []
    device = ['iPhone 13', 'Samsung Galaxy', 'Windows PC', 'MacBook', 'Redmi Note']
    locations = ['Tashkent', 'Samarkand', 'Moscow', 'Almaty']
    for i in range(50):
        d = random.choice(device)
        loc = random.choice(locations)
        link = random.choice(['http://xavfsizlik.uz/tasdiq', 'https://profil-tasdiq.com/sms', 'bit.ly/xavf-uz'])
        unauthorized_login.append(f"Yangi qurilmadan ({d}, {loc}) hisobingizga kirish aniqlandi. Xavfsizlik uchun tasdiqlash talab qilinadi: {link}?id={i}")

    # security_verification - 50
    security_verification = []
    for i in range(50):
        system = random.choice(['Payme', 'Click', 'Uzum', 'DavlatXizmatlari', 'Telegram'])
        link = random.choice(['https://click-auth.uz/code', 'http://payme-error.com/verify', 'https://uzum-tasdiq.uz'])
        security_verification.append(f"{system}: Xavfsizlik tizimi yangilandi. Profilingizni himoyalash uchun qayta verifikatsiyadan o'ting: {link}/{i}")

    # account_freeze - 50
    account_freeze = []
    for i in range(50):
        bank = random.choice(['Asakabank', 'NBU', 'Kapitalbank', 'Hamkorbank', 'IpakYuli'])
        link = random.choice(['http://uzcard-block.uz/unlock', 'https://humo-unlock.com/kod', 'bit.ly/banck-uz'])
        account_freeze.append(f"Diqqat! {bank} hisobingiz xavfsizlik maqsadida muzlatildi. Qayta tiklash uchun shaxsiy ma'lumotlaringizni kiriting: {link}?user={i}")

    def add_rows(messages, cat):
        for msg in messages:
            data.append({"text": msg, "label": "scam", "category": cat})
            
    add_rows(suspicious_transaction, "suspicious_transaction")
    add_rows(money_withdrawal, "money_withdrawal")
    add_rows(unauthorized_login, "unauthorized_login")
    add_rows(security_verification, "security_verification")
    add_rows(account_freeze, "account_freeze")
    
    df = pd.DataFrame(data)
    
    # Clean and dedup
    df = df[df['text'].str.strip() != ""]
    df['text'] = df['text'].str.strip()
    df = df.drop_duplicates(subset=['text'], keep='first')
    
    df.to_csv(out_path, index=False, encoding='utf-8')
    
    print("\n=== Security Alert Dataset Generation ===")
    print(f"Total generated rows: {len(df)}")
    print("\nRows per category:")
    print(df['category'].value_counts().to_string())
    
    print("\n=== Examples per Category ===")
    for cat in df['category'].unique():
        print(f"\n[{cat}]")
        samples = df[df['category'] == cat].head(5)
        for _, row in samples.iterrows():
            print(f"- {row['text']}")

if __name__ == "__main__":
    generate_security_alerts()
