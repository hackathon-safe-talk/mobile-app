import pandas as pd
from pathlib import Path
import random

base_dir = Path(__file__).resolve().parents[1]

def generate_phishing():
    output_dir = base_dir / "data" / "phishing_expansion"
    output_dir.mkdir(parents=True, exist_ok=True)
    out_path = output_dir / "uz_financial_scams.csv"
    
    data = []
    
    # bank_phishing - 80
    bank_phishing = [
        f"Hurmatli mijoz, {bank} kartangizda shubhali tranzaksiya aniqlangan. Parolni yangilash uchun bosing: {link}"
        for bank in ['HUMO', 'Uzcard', 'Xalq Banki', 'NBU', 'Kapitalbank', 'Agrobank', 'Ipoteka Bank', 'SQB', 'Aloqabank', 'Asakabank']
        for link in ['https://bank-uz.com', 'bit.ly/uz-bank', 'http://tasdiq-bank.uz', 'https://uzcard-update.uz', 'http://security-uz.com', 'https://humomp.uz', 'https://xavfsizlik-bot.uz', 'bit.ly/bank-auth']
    ][:80]
    
    # payment_failure - 60
    payment_failure = [
        f"{pay_sys} to'lovi amalga oshmadi. Hisobni tasdiqlash uchun kirish talab qilinadi: {link}"
        for pay_sys in ['Click.uz', 'Payme.uz', 'Uzum Pay', 'Oson', 'Paynet', 'Apelsin']
        for link in ['https://click-auth.uz', 'http://payme-error.com', 'bit.ly/tolov-xato', 'https://uzum-tasdiq.uz', 'http://paynet-verify.com', 'https://oson-tolov.uz', 'https://click-evolution.uz', 'bit.ly/pay-fail', 'http://uz-pay-support.com', 'https://payme-help.uz']
    ][:60]
    
    # account_verification - 40
    account_verification = []
    for i in range(40):
        link = random.choice(['http://tasdiqlash-uz.com', 'https://verify-account.uz', 'bit.ly/auth-uz', 'http://xavfsizlik.uz', 'https://profil-tasdiq.com'])
        account_verification.append(f"Diqqat! Akkauntingiz xavf ostida ID-{i+1000}. Shaxsiy ma'lumotlarni tasdiqlash uchun kiring: {link}/{i}")
        
    # card_blocked - 40
    card_blocked = []
    for i in range(40):
        link = random.choice(['http://uzcard-block.uz', 'https://humo-unlock.com', 'bit.ly/karta-ochish', 'http://banck-uz.com'])
        card_blocked.append(f"Diqqat! Bank kartangiz xavfsizlik sababli vaqtincha bloklandi (Kod: {random.randint(100, 999)}). Tasdiqlash uchun havolaga kiring: {link}?id={i}")
        
    # fake_transfer - 40
    fake_transfer = []
    for i in range(40):
        link = random.choice(['http://bekor-qilish.uz', 'https://uz-bank-cancel.com', 'bit.ly/refund-uz', 'http://tolov-bekor.uz'])
        amount = random.choice(['980,000', '1,500,000', '4,200,000', '500,000', '3,000,000'])
        fake_transfer.append(f"Sizning hisobingizdan {amount} so'm yechib olindi (Ref: {random.randint(1000, 9999)}). Bekor qilish uchun quyidagi link orqali tasdiqlang: {link}/{i}")
        
    # loan_offer - 20
    loan_offer = []
    for i in range(20):
        link = random.choice(['http://kredit-uz.com', 'https://qarz-olish.uz', 'bit.ly/tez-kredit', 'http://agrobank-kredit.com'])
        amount = random.choice(['20,000,000', '15,000,000', '50,000,000', '10,000,000'])
        loan_offer.append(f"Sizga {amount} so'm mikrokredit tasdiqlandi! Pulni kartaga tushirish uchun: {link}?id={i}")
        
    # government_payment - 10
    government_payment = []
    for i in range(10):
        link = random.choice(['http://subsidiya-uz.com', 'https://davlat-yordam.uz', 'bit.ly/yordam-pul'])
        amount = random.choice(['2,000,000', '1,200,000', '3,500,000'])
        government_payment.append(f"Sizga {amount} so'm davlat subsidiyasi ajratildi (Status: {i}). Arizani tasdiqlash uchun kirish: {link}/{i}")
        
    # delivery_payment - 10
    delivery_payment = []
    for i in range(10):
        link = random.choice(['http://bojxona-uz.com', 'https://uzpost-tolov.uz', 'http://dostavka-uz.com'])
        delivery_payment.append(f"Sizning pochtangiz posilkasi bojxonada to'xtatildi. Boj to'lovini amalga oshiring (ID-{i}): {link}/{i}")
        
    def add_rows(messages, cat):
        for msg in messages:
            data.append({"text": msg, "label": "scam", "category": cat, "notes": "generated financial phishing"})
            
    add_rows(bank_phishing, "bank_phishing")
    add_rows(payment_failure, "payment_failure")
    add_rows(account_verification, "account_verification")
    add_rows(card_blocked, "card_blocked")
    add_rows(fake_transfer, "fake_transfer")
    add_rows(loan_offer, "loan_offer")
    add_rows(government_payment, "government_payment")
    add_rows(delivery_payment, "delivery_payment")
    
    df = pd.DataFrame(data)
    
    # Clean and dedup
    df = df[df['text'].str.strip() != ""]
    df['text'] = df['text'].str.strip()
    df = df.drop_duplicates(subset=['text'], keep='first')
    
    df.to_csv(out_path, index=False, encoding='utf-8')
    
    print("\n=== Phishing Dataset Generation ===")
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
    generate_phishing()
