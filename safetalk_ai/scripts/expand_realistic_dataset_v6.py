import pandas as pd
import random
from pathlib import Path

# Resolve base directory
base_dir = Path(__file__).resolve().parents[1]

def generate_realistic_v6():
    print("=== SafeTalk Realistic Dataset Expansion V6 (Multilingual) ===")
    
    # Random modifiers to ensure uniqueness
    modifiers = ["", ".", "!", "..", "...", " :)", " 👍", " OK", " ?", "!!", " )", " (", " -"]
    
    # --- 1. UZBEK SCAM (500) ---
    uz_scam_templates = [
        "Sizning kartangiz bloklandi. Tasdiqlash uchun: {link}",
        "Yutuq sohibi bo'ldingiz! {amount} so'm yutdingiz. {link}",
        "Hisobingizdan {amount} yechildi. Bekor qilish {link}",
        "Click: To'lov xatosi. Kartani qayta bog'lang {link}",
        "Telegram Premium free! Botga kiring {link}",
        "Sizga yangi transfer keldi {amount}. Qabul qiling {link}",
        "Davlat subsidiyasi uchun ariza qoldiring {link}",
        "MTS: Hisobingizda kutilmagan faollik. {link}"
    ]
    
    # --- 2. RUSSIAN SCAM (500) ---
    ru_scam_templates = [
        "Ваша карта заблокирована. Подтвердите по ссылке: {link}",
        "Вы выиграли {amount} рублей! Получите приз: {link}",
        "Вход в личный кабинет с нового устройства. Это не вы? {link}",
        "Списание {amount} прошло успешно. Отменить: {link}",
        "Ваш аккаунт Telegram будет удален через 24 часа. Подтвердите {link}",
        "Госуслуги: Вам начислена выплата {amount}. {link}",
        "Оплата заказа №{id} не прошла. Повторите: {link}",
        "Инвестиции в газ! Доход от {amount} в неделю: {link}"
    ]
    
    # --- 3. MIXED SCAM (Uzbek + Russian + English) (500) ---
    mixed_scam_templates = [
        "Vasha karta bloklandi. Tasdiqlash uchun linkni bosing: {link}",
        "Telegram premium sovg'a claim qiling: {link}",
        "Hisobingizdan {amount} som spisanie. Agar bu siz bo'lmasangiz cancel qiling: {link}",
        "Click payment failed. Tasdiqlash uchun linkga o'ting: {link}",
        "U vas noviy yutuq! {amount} yutib oldingiz. Claim your prize: {link}",
        "Sizga {amount} som perevod keldi. Podtverdite po ssilke: {link}",
        "Your account is blocked! Podtverdite svoyu lichnost: {link}",
        "Sizning limitiz ispolzovan. Uvelichit limit: {link}"
    ]
    
    # --- 4. REALISTIC HAM (500) ---
    ham_templates = [
        "Bugun uchrashamizmi?",
        "Pulni tashladim tekshirib ko'r.",
        "Segodnya uchrashuv soat nechida?",
        "Qayerdasan hozir?",
        "Ertaga dars {time} da boshlanadi.",
        "Uyga qaytayotganda {item} olib kel.",
        "Privet, kak dela?",
        "Menga {amount} tashlab ber, ertaga qaytaraman.",
        "Xatni jo'natdingmi?",
        "Uchrashuv bekor qilindi.",
        "Ona, men keldim.",
        "Yaxshimisiz? Sog'liqlar qalay?",
        "Vazifani jo'natvor iltimos.",
        "Zavtra v {time} vstrechaemsya.",
        "Kechikaman biroz.",
        "Yo'ldaman hozir boraman.",
        "Oshga kelasizmi?",
        "Rahmat kattakon!",
        "Ishlar bilan charchamayapsizmi?",
        "Ertaga soat {time} da kutaman."
    ]

    items = ["non", "sut", "go'sht", "meva", "sabzavot", "shakar", "choy"]
    amounts = [f"{random.randint(50, 900)} 000" for _ in range(100)]
    times = ["9:00", "14:30", "18:00", "20:00", "obid", "kechga", "10:00", "12:00"]
    links = ["http://uz-confirm.com/", "https://t.me/prize_bot/", "http://click-safety.uz/", "https://bit.ly/claim-", "http://pay-check.net/"]
    ids = [str(random.randint(1000, 9999)) for _ in range(50)]

    def generate_batch(templates, label, lang_tag, count):
        batch = []
        seen = set()
        max_attempts = count * 20
        attempts = 0
        while len(batch) < count and attempts < max_attempts:
            attempts += 1
            t = random.choice(templates)
            msg = t.format(
                amount=random.choice(amounts),
                time=random.choice(times),
                link=random.choice(links) + str(random.randint(100, 999999)),
                id=random.choice(ids),
                item=random.choice(items)
            )
            # Add random modifier to ensure high uniqueness
            msg += random.choice(modifiers)
            
            clean_msg = msg.strip()
            if clean_msg not in seen:
                batch.append({"text": clean_msg, "source": random.choice(["sms", "telegram"]), "label": label, "lang": lang_tag})
                seen.add(clean_msg)
        return batch

    all_data = []
    all_data.extend(generate_batch(uz_scam_templates, "scam", "uz", 500))
    all_data.extend(generate_batch(ru_scam_templates, "scam", "ru", 500))
    all_data.extend(generate_batch(mixed_scam_templates, "scam", "mixed", 500))
    all_data.extend(generate_batch(ham_templates, "ham", "ham_various", 500))

    df = pd.DataFrame(all_data)
    
    # Final Formatting
    df = df.drop_duplicates(subset=['text'])
    df['text'] = df['text'].str.strip()
    df = df[df['text'] != ""]

    # Save
    out_dir = base_dir / "data" / "expansions"
    out_dir.mkdir(parents=True, exist_ok=True)
    out_path = out_dir / "realistic_v6_messages.csv"
    
    df[['text', 'source', 'label']].to_csv(out_path, index=False, encoding='utf-8')
    
    print(f"\nTotal rows generated: {len(df)}")
    print("\nRows by label:")
    print(df['label'].value_counts().to_string())
    print("\nRows by language tag (internal):")
    print(df['lang'].value_counts().to_string())
    
    print(f"\nDataset saved to {out_path}")
    print("\n--- Examples ---")
    for _, row in df.sample(min(10, len(df))).iterrows():
        print(f"[{row['label'].upper()}] {row['text'][:80]}...")

if __name__ == "__main__":
    generate_realistic_v6()
