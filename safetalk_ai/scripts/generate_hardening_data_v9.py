import pandas as pd
import random
import re
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def inject_noise(text, intensity=0.3):
    """Applies noise (typos, punctuation, emoji, mixed scripts) to a subset of text."""
    if random.random() > intensity:
        return text
    
    # Typos (swap adjacent letters)
    if len(text) > 5 and random.random() < 0.3:
        idx = random.randint(0, len(text) - 2)
        text = text[:idx] + text[idx+1] + text[idx] + text[idx+2:]
    
    # Punctuation spam
    if random.random() < 0.3:
        text += "!!!" if random.random() < 0.5 else "???"
        
    # Emoji usage
    emojis = ["⚠️", "🚨", "✅", "💰", "📱", "🎁"]
    if random.random() < 0.3:
        text = random.choice(emojis) + " " + text
        
    # Mixed scripts (basic: O vs 0, a vs @)
    if random.random() < 0.2:
        text = text.replace('o', '0').replace('a', '@').replace('i', '1')
        
    return text

def generate_v9_hardening():
    output_dir = base_dir / "data" / "expansions"
    v9_hardening_path = output_dir / "hardening_data_v9.csv"
    output_dir.mkdir(parents=True, exist_ok=True)
    
    data = []

    # --- TASK 1: PHISHING LOGIN (50-100 unique) ---
    phishing_login_templates = [
        # Uzbek
        "Xavfsizlik xizmati: {bank} profilingiz bloklangan. Qayta tiklash: {link}/login",
        "Diqqat! Akkauntingizga begonalar kirdi. Loginni tasdiqlang: {link}",
        "{bank} bank: Shaxsingizni tasdiqlamasangiz kartangiz o'chiriladi. Kirish: {link}",
        "Sizning {app} parolingiz o'zgartirildi. Agar bu siz bo'lmasangiz: {link}",
        "Login va parol xato kiritildi. Profilingizni himoya qiling: {link}",
        "MXX: Noqonuniy harakat aniqlandi. Shaxsingizni botda tasdiqlang: {link}",
        # Russian
        "Аккаунт {app} заблокирован из-за подозрительной активности. Подтвердите вход: {link}",
        "Служба безопасности {bank}: Ваша карта ограничена. Пройдите авторизацию: {link}",
        "Внимание! Кто-то вошел в ваш аккаунт. Если это не вы, перейдите по ссылке: {link}",
        "Ваш пароль истекает. Обновите данные в личном кабинете: {link}",
        # English
        "Security Alert: Your {app} account is locked. Please log in to verify: {link}",
        "Someone tried to log into your {bank} account. Confirm identity: {link}",
        "Your access is restricted. Sign in to recover: {link}/auth",
        # Mixed
        "Внимание! Hisobingiz bloklandi. Akkauntingizni login orqali tasdiqlang: {link}",
        "Security alert: {app}ga begona kirdi. Please verify login immediately: {link}",
    ]
    
    banks = ["UzCard", "Humo", "NBU", "TBC", "Ipak Yo'li", "SberBank", "Tinkoff"]
    apps = ["Telegram", "WhatsApp", "Click", "Payme", "Google", "Apple ID"]
    links = ["http://verify-secure.click", "http://login-uz.xyz", "http://auth-check.top", "http://secure-entry.online", "http://link-fix.bz"]
    
    for i in range(80):
        template = random.choice(phishing_login_templates)
        text = template.format(
            bank=random.choice(banks),
            app=random.choice(apps),
            link=random.choice(links)
        )
        # Apply noise to 25% of samples
        text = inject_noise(text, intensity=0.25)
        data.append({"text": text, "risk_label": "DANGEROUS", "intent_label": "REQUEST", "language": "mixed", "tags": "phishing_login"})

    # --- TASK 2: SAFE HARD NEGATIVES (60-100 samples) ---
    safe_hard_negatives = [
        # OTP / Codes
        "Loginni tasdiqlash uchun SMS kodi: {code}. Uni hech kimga bermang.",
        "Sizning vaqtinchalik parolingiz: {code}. ClickEvolution xodimlariga ham aytmang.",
        "Humo: {code} - bu sizning to'lov kodingiz. Faqat kassa xodimiga ayting.",
        # Login Alerts
        "Sizning Apple ID hisobingizga yangi qurilmadan kirildi. Agar bu siz bo'lsangiz, amal qilmang.",
        "Microsoft: Hisobingizga muvaffaqiyatli kirildi. IP: 213.230.12.11.",
        # Payment Notifications
        "To'lov muvaffaqiyatli amalga oshirildi. Miqdor: {amount} so'm. Rahmat!",
        "Karta {card_end} yordamida {service} xizmati uchun {amount} so'm yechildi.",
        "Sizning Humo kartangizga {amount} so'm tushum keldi. Balans: {total} so'm.",
        # Legitimate Links / Bank Messages
        "Hurmatli mijoz, bankimiz xizmatlari yaxshilandi. Batafsil: https://{bank_site}.uz",
        "Tahlil natijalari tayyor. Tibbiy kabinetingizga kiring: http://med.uz/results",
        "Sizning arizangiz ko'rib chiqilmoqda. Statusni my.gov.uz orqali kuzating.",
    ]
    
    bank_sites = ["nbu", "tbc", "ipakyoli", "uzpsb"]
    services = ["Paynet", "UzAuto Motor", "Milliy", "Spotify"]
    
    for i in range(85):
        template = random.choice(safe_hard_negatives)
        text = template.format(
            code=random.randint(1000, 999999),
            amount=random.randint(5000, 10000000),
            card_end=f"*{random.randint(1000, 9999)}",
            service=random.choice(services),
            total=random.randint(10000, 50000000),
            bank_site=random.choice(bank_sites)
        )
        text = inject_noise(text, intensity=0.25)
        data.append({"text": text, "risk_label": "SAFE", "intent_label": "INFO", "language": "mixed", "tags": "hard_negative"})

    # --- TASK 3: RU + MIXED LANGUAGE EXPANSION (200-300 samples) ---
    # We want a balance across classes for RU/MIXED
    
    # RU/MIXED DANGEROUS
    ru_mixed_dangerous = [
        "Вам начислена компенсация {amount} руб. Получить на карту: {link}",
        "Срочно! Ваша карта заблокирована. Лимиты превышены. Снимите блок: {link}",
        "Вы выиграли iPhone 15! Доставка бесплатно, оплатите только страховку: {link}",
        "Tezda pul kerakmi? Kuniga 100$ topish siri mana bu kanalda: @scam_crypto",
        "Assalomu alaykum, sarmoya kiriting va har kuni 20% foyda oling! @invest_ru",
        "Твой аккаунт Telegram взломан! Срочно перейди по ссылке чтобы не потерять данные: {link}",
        "Davlat tomonidan moddiy yordam tarqatilyapti. Formani to'ldiring: http://subsidiya.xyz",
    ]
    
    # RU/MIXED SUSPICIOUS
    ru_mixed_suspicious = [
        "Спеши забрать свой бонус! Акция действует до конца дня.",
        "У вас новое сообщение в личном кабинете. Посмотрите пока не удалили.",
        "Ajoyib taklif! Faqat bugun bizning kanalda chegirmalar: t.me/promo_kanal",
        "Хочешь заработать? Подписывайся на наш закрытый чат по ставкам.",
        "Sizga maxsus sovg'a yuborildi. Uni qachon olib ketasiz?",
        "Обновите приложение до последней версии чтобы получить все функции.",
    ]
    
    # RU/MIXED SAFE
    ru_mixed_safe = [
        "Заказ №{code} передан курьеру. Ожидайте звонка.",
        "Ваша запись на прием подтверждена. Ждем вас в 10:00.",
        "Bugun dars bo'lmaydi, hamma uyda qolsin.",
        "Rahmat kattakon, pul tushdi.",
        "Привет, как дела? У меня все хорошо.",
        "Пополнил баланс на {amount} сум. Спасибо!",
        "Ertaga soat 9da ofisda ko'rishamiz. Kechikmang.",
    ]

    for i in range(80):
        t = random.choice(ru_mixed_dangerous)
        text = t.format(amount=random.randint(1000, 100000), link=random.choice(links))
        data.append({"text": inject_noise(text, 0.25), "risk_label": "DANGEROUS", "intent_label": "REQUEST", "language": "mixed", "tags": "ru_mixed_expansion"})

    for i in range(80):
        t = random.choice(ru_mixed_suspicious)
        data.append({"text": inject_noise(t, 0.25), "risk_label": "SUSPICIOUS", "intent_label": "INFO", "language": "mixed", "tags": "ru_mixed_expansion"})

    for i in range(90):
        t = random.choice(ru_mixed_safe)
        text = t.format(code=random.randint(100, 999), amount=random.randint(1000, 50000))
        data.append({"text": inject_noise(text, 0.25), "risk_label": "SAFE", "intent_label": "INFO", "language": "mixed", "tags": "ru_mixed_expansion"})

    # Combine and save
    df = pd.DataFrame(data).drop_duplicates(subset=['text'])
    df.to_csv(v9_hardening_path, index=False, encoding='utf-8')
    
    print(f"Generated {len(df)} hardened samples for Stage 5.1 at {v9_hardening_path}")
    print("\nDistribution:")
    print(df['risk_label'].value_counts())
    print("\nSample DANGEROUS Login Phishing:")
    print(df[df['tags'] == 'phishing_login'].head(5)['text'].to_list())

if __name__ == "__main__":
    generate_v9_hardening()
