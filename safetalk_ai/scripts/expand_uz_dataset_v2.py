import pandas as pd
from pathlib import Path
import random

base_dir = Path(__file__).resolve().parents[1]

def generate_datasets():
    print("=== SafeTalk Data Expansion V5 ===")
    
    # 1. Generate roughly 1500 HAM combinations
    ham_templates = [
        "Qayerdasan hozir?",
        "Ertaga dars soat nechida boshlanadi?",
        "Uyga qaytayotganda {item} olib kel.",
        "Pulni tashlab yubordim tekshirib ko'r.",
        "Bugun uchrashamizmi?",
        "Salom, ishlar qale?",
        "Nima qilyapsan tuzukmisan?",
        "Bugun {time} da ko'rishamiz.",
        "Vazifani qildingmi?",
        "Telegramdan yozaman hozir.",
        "Telefonni ko'tar!",
        "Qachon kelasiz?",
        "Aka, ishlar qanday?",
        "Menga {amount} tashlab tura olasanmi?",
        "Yaxshimisiz, uydagilar qalay?",
        "Kechikaman biroz {time} da boraman.",
        "{item} narxi qancha ekan hozir?",
        "Bugungi majlis {time} ga surildi.",
        "Xatni pochtadan jo'natdim.",
        "O'qish qachon tugaydi?",
        "Rahmat sizga katta!",
        "Iltimos, qayta qo'ng'iroq qiling.",
        "Men hozir ishdamon.",
        "Bayramingiz bilan tabriklayman!",
        "Charchamang, oq yo'l!"
    ]
    
    items = ['non', 'sut', 'go\'sht', 'tuxum', 'kartoshka', 'piyoz', 'suv', 'meva', 'shirinlik', 'choy', 'shakar', 'kitob', 'daftar', 'ruchka', 'qogoz']
    times = [f"{h}:{m:02d}" for h in range(8, 22) for m in [0, 15, 30, 45]] + ['ertalab', 'kechqurun', 'tushdan keyin']
    amounts = [f"{random.randint(10, 500)}0,000 so'm" for _ in range(50)]
    
    hams = []
    # Make sure we get distinct strings
    ham_set = set()
    while len(ham_set) < 1600:
        t = random.choice(ham_templates)
        if '{item}' in t: t = t.replace('{item}', random.choice(items))
        if '{time}' in t: t = t.replace('{time}', random.choice(times))
        if '{amount}' in t: t = t.replace('{amount}', random.choice(amounts))
        
        # Add slight modifications to ensure uniqueness
        t += random.choice(["", "?", "!", ".", " :)", "...", " tezroq"])
        ham_set.add(t.strip())
        
    hams = list(ham_set)[:1500]
    
    # 2. Generate roughly 1500 SCAM combinations
    scam_templates = [
        "Tabriklaymiz siz {amount} yutib oldingiz. Pulni olish uchun havolaga kiring: {link}",
        "Sizga {amount} pul o'tkazmasi keldi. Qabul qilish uchun linkni bosing: {link}",
        "Telegram premium sovg'a olish uchun botga kiring: {link}",
        "Hisobingizdan shubhali tranzaksiya aniqlandi. Tasdiqlash uchun havolaga o'ting: {link}",
        "Yangi video yuklandi 18+ apk yuklab oling: {link}",
        "Diqqat! Akkauntingiz xavf ostida. Tasdiqlash uchun kiring: {link}",
        "Kriptovalyuta sarmoya qilib kuniga {amount} toping: {link}",
        "Pochtangiz bojxonada to'xtatildi. To'lov qiling: {link}",
        "Sizga davlat subsidiyasi ajratildi. Olish uchun: {link}",
        "Click to'lovi amalga oshmadi. Tasdiqlash: {link}",
        "Kartangiz bloklandi. Qayta tiklash uchun malumotlarni kiriting: {link}",
        "NBU: Hisobingizdan {amount} yechib olindi! Bekor qilish: {link}",
        "Uzcard xavfsizlik botiga ulaning va bonus oling: {link}",
        "Davlat tomonidan {amount} kompensatsiya ajratildi. Havola orqali tekshiring: {link}",
        "Shifrdan nusxa olindi. Parolni yangilang: {link}",
        "Uyda o'tirib kuniga 50$ ishlashni xohlaysizmi? Botga kiring: {link}"
    ]
    
    links = ['http://bit.ly/uz-', 'https://uz-yutuq.com/p=', 'http://bonus-uz.com/id/', 'https://t.me/premium_uz_bot?ref=', 'http://18plus-video.apk/q=', 'https://click-auth.uz/code=', 'http://pochta-uz.com/track=']
    
    scams = []
    scam_set = set()
    i = 0
    while len(scam_set) < 1600:
        t = random.choice(scam_templates)
        if '{amount}' in t: t = t.replace('{amount}', random.choice(amounts))
        if '{link}' in t: t = t.replace('{link}', random.choice(links) + str(random.randint(1000, 9999999)))
        scam_set.add(t.strip())
        i += 1
        
    scams = list(scam_set)[:1500]
    
    # Compile
    data = []
    for h in hams:
        data.append({"text": h, "source": random.choice(['sms', 'telegram']), "label": "ham"})
    for s in scams:
        data.append({"text": s, "source": random.choice(['sms', 'telegram']), "label": "scam"})
        
    df_new = pd.DataFrame(data)
    
    # Format rules
    df_new = df_new[df_new['text'].str.strip() != ""]
    df_new['text'] = df_new['text'].str.strip()
    df_new = df_new.drop_duplicates(subset=['text', 'source', 'label'], keep='first')
    
    # Print metrics
    print(f"Synthesized New Rows: {len(df_new)}")
    print(df_new['label'].value_counts())
    
    # Save standalone
    uzbek_exp_dir = base_dir / "data" / "uzbek_expansion"
    uzbek_exp_dir.mkdir(parents=True, exist_ok=True)
    out_v2 = uzbek_exp_dir / "uz_dataset_v2.csv"
    df_new.to_csv(out_v2, index=False, encoding='utf-8')
    print(f"\nSaved uz_dataset_v2.csv to {out_v2}")
    
    # Merge with V4
    v4_path = base_dir / "data" / "final" / "safetalk_unified_dataset_v4.csv"
    
    if not v4_path.exists():
        print("V4 Source dataset not found!")
        return
        
    df_v4 = pd.read_csv(v4_path)
    initial_v4_size = len(df_v4)
    
    df_v5 = pd.concat([df_v4, df_new], ignore_index=True)
    
    # Global cleanup
    df_v5 = df_v5.dropna(subset=['text', 'label'])
    df_v5 = df_v5[df_v5['text'].str.strip() != ""]
    df_v5 = df_v5[df_v5['label'].isin(['ham', 'scam'])]
    df_v5 = df_v5.drop_duplicates(subset=['text', 'source', 'label'], keep='first')
    
    out_v5 = base_dir / "data" / "final" / "safetalk_unified_dataset_v5.csv"
    df_v5.to_csv(out_v5, index=False, encoding='utf-8')
    
    print(f"\n=== Dataset V5 Auto-Scaling Merge ===")
    print(f"Total rows in V5 dataset: {len(df_v5)}")
    print("\nRows by label:")
    print(df_v5['label'].value_counts().to_string())
    print("\nRows by source:")
    print(df_v5['source'].value_counts().to_string())
    print(f"\nNew Rows Inserted: {len(df_v5) - initial_v4_size}")
    
    print("\n--- Example New Rows (Top 10) ---")
    for _, row in df_new.head(10).iterrows():
        print(f"[{row['label'].upper()}] {row['text'][:80]}...")
        
    print(f"\nSaved v5 target to: {out_v5}")

if __name__ == "__main__":
    generate_datasets()
