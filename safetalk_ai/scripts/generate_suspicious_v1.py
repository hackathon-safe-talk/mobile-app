"""
SafeTalk Synthetic SUSPICIOUS Dataset Generator V1
====================================================
Generates 3,000+ high-quality SUSPICIOUS-class messages across
UZ (60%), EN (30%), RU (10%) with natural variation, typos,
and stylistic noise.

Output: data/generated/suspicious_synthetic_v1.csv
Schema: text | clean_text | label | language | source | category

Author: SafeTalk AI Team
"""

import csv
import random
import re
import os
from pathlib import Path
from datetime import datetime
from collections import Counter

# ─────────────────────────────────────────────────────────────
# Configuration
# ─────────────────────────────────────────────────────────────

SEED = 42
random.seed(SEED)

BASE_DIR = Path(__file__).resolve().parents[1]
OUTPUT_DIR = BASE_DIR / "data" / "generated"
OUTPUT_FILE = OUTPUT_DIR / "suspicious_synthetic_v1.csv"

TARGET_TOTAL = 3600  # Divisible for 60/30/10 split
TARGET_UZ = int(TARGET_TOTAL * 0.60)   # 2160
TARGET_EN = int(TARGET_TOTAL * 0.30)   # 1080
TARGET_RU = int(TARGET_TOTAL * 0.10)   # 360


# ─────────────────────────────────────────────────────────────
# Text Preprocessing (aligned with build_unified_v10.py)
# ─────────────────────────────────────────────────────────────

_URL_RE = re.compile(r'(https?://\S+)', re.I)
_EXTS = ['.apk', '.exe', '.scr', '.msi', '.bat', '.zip', '.rar']
_NOISE_RE = re.compile(r"[^\w\s\./:']")
_MULTI_EXCL = re.compile(r'!!+')
_WS = re.compile(r'\s+')


def clean_text(text: str) -> str:
    if not isinstance(text, str) or not text.strip():
        return ""
    t = text.lower()
    t = t.replace("\u2018", "'").replace("\u2019", "'").replace("`", "'").replace("\u02bc", "'")
    t = t.replace("o\u2018", "o'").replace("o\u2019", "o'").replace("o`", "o'")
    t = t.replace("g\u2018", "g'").replace("g\u2019", "g'").replace("g`", "g'")
    t = _URL_RE.sub(r' \1 ', t)
    for ext in _EXTS:
        if ext in t:
            t = t.replace(ext, f' {ext} ')
    t = _MULTI_EXCL.sub(' ! ', t)
    t = _NOISE_RE.sub(' ', t)
    t = _WS.sub(' ', t).strip()
    return t


# ─────────────────────────────────────────────────────────────
# Noise Injection Utilities
# ─────────────────────────────────────────────────────────────

def maybe_typo(word: str, prob: float = 0.08) -> str:
    """Randomly introduce a character-level typo."""
    if len(word) < 4 or random.random() > prob:
        return word
    idx = random.randint(1, len(word) - 2)
    op = random.choice(["swap", "drop", "double"])
    chars = list(word)
    if op == "swap" and idx + 1 < len(chars):
        chars[idx], chars[idx + 1] = chars[idx + 1], chars[idx]
    elif op == "drop":
        chars.pop(idx)
    elif op == "double":
        chars.insert(idx, chars[idx])
    return "".join(chars)


def inject_noise(text: str) -> str:
    """Apply light natural noise: typos, casing, punctuation variation."""
    words = text.split()
    words = [maybe_typo(w) for w in words]

    # Random casing variation (10% chance)
    if random.random() < 0.10:
        words[0] = words[0].capitalize()
    if random.random() < 0.05:
        text = " ".join(words).upper()
        return text

    text = " ".join(words)

    # Trailing punctuation variation
    r = random.random()
    if r < 0.15:
        text = text.rstrip(".!?") + "..."
    elif r < 0.25:
        text = text.rstrip(".!?") + "!"
    elif r < 0.30:
        text = text.rstrip(".!?") + ".."

    return text


def pick(*options):
    """Pick a random option from the list."""
    return random.choice(options)


def maybe(text: str, prob: float = 0.5) -> str:
    """Return text with given probability, else empty string."""
    return text if random.random() < prob else ""


# ─────────────────────────────────────────────────────────────
# Template Pools — UZBEK (UZ)
# ─────────────────────────────────────────────────────────────

def gen_uz_soft_urgency() -> list:
    templates = [
        "Hisobingizni {time} ichida tasdiqlang",
        "Akkauntingiz tekshiruvdan o'tishi kerak",
        "Hisobingizni yangilash talab qilinmoqda",
        "Iltimos, ma'lumotlaringizni {time} ichida tasdiqlang",
        "Profilingiz faollashtirish kutmoqda",
        "Akkauntingiz xavfsizlik tekshiruvidan o'tishi lozim",
        "Ma'lumotlaringizni yangilash uchun tizimga kiring",
        "Hisobingiz vaqtincha cheklangan, tasdiqlang",
        "Tizim hisobingizni tekshirish so'ramoqda",
        "Profilingiz {time} ichida tasdiqlanishi kerak",
        "Hisobingizni faollashtirish uchun kirish talab etiladi",
        "Ma'lumotlaringiz eskirgan, yangilash kerak",
        "Foydalanuvchi sifatida ma'lumotlaringizni tasdiqlang",
        "Akkauntingiz xavfsizlik sababli tekshirilmoqda",
        "Hisobingiz holati o'zgardi, qayta tasdiqlang",
        "Ma'lumotlaringizni {time} da yangilang",
        "Profilingizda o'zgarishlar aniqlandi",
        "Hisobingiz uchun yangi xavfsizlik talabi mavjud",
        "Tizimga kirish uchun hisobni qayta faollashtiring",
        "Xavfsizlik sababli akkauntingizni tekshiring",
    ]
    times = ["24 soat", "48 soat", "bugun", "ertaga", "3 kun"]
    results = []
    for t in templates:
        for _ in range(3):
            msg = t.format(time=random.choice(times))
            results.append(inject_noise(msg))
    return results


def gen_uz_curiosity_hooks() -> list:
    templates = [
        "Sizga yangi xabar keldi, tekshiring",
        "Sizga bildirishnoma bor",
        "Akkountingizga yangi harakat qayd qilindi",
        "Siz uchun yangilik bor, tasdiqlang",
        "Profilingizga kimdir kirgan bo'lishi mumkin",
        "So'ngi faoliyatingiz bo'yicha eslatma",
        "Hisobingizda yangi bildirishnoma kutmoqda",
        "Kutilmagan kirish aniqlandi, tekshiring",
        "Yangi qurilmadan kirish amalga oshirildi",
        "Hisobingiz faoliyatida o'zgarish mavjud",
        "Kimdir sizning akkauntingizga kirmoqchi bo'ldi",
        "Hisobingizga noma'lum qurilmadan ulanish bo'ldi",
        "Oxirgi faoliyat bo'yicha ogohlantiris",
        "Sizning profilingiz ko'rildi",
        "Hisobingizda {n} ta yangi xabar bor",
        "Sizga {name} dan xabar keldi",
        "Ma'lumotlaringiz bo'yicha yangilik mavjud",
        "Yangi kirish urinishi qayd qilindi",
        "Hisobingiz holati yangilandi",
        "Profilingiz bo'yicha muhim bildirishnoma",
    ]
    names = ["Admin", "Tizim", "Xavfsizlik", "Texnik yordam", "Moderator"]
    results = []
    for t in templates:
        for _ in range(3):
            msg = t.format(n=random.randint(1, 9), name=random.choice(names))
            results.append(inject_noise(msg))
    return results


def gen_uz_promotional() -> list:
    templates = [
        "Siz maxsus chegirma olishingiz mumkin",
        "Cheklangan vaqt ichida bonus olish imkoniyati",
        "Akkauntingiz uchun {pct}% chegirmaga haqingiz bor",
        "Maxsus taklif sizni kutmoqda",
        "Bugun ro'yxatdan o'ting va bonus oling",
        "Foydalanuvchilarga maxsus imkoniyat taqdim etilmoqda",
        "Sizga {amount} so'm cashback berilishi mumkin",
        "Premium obuna uchun maxsus chegirma",
        "Siz tanlab olingan foydalanuvchilardansiz",
        "VIP status olish imkoniyati mavjud",
        "Hisobingiz uchun maxsus bonus tayyorlandi",
        "Chegirma muddati {time} da tugaydi",
        "Siz sovg'a olish uchun ro'yxatdan o'tishingiz mumkin",
        "Bugungi aksiya: barcha foydalanuvchilarga bonus",
        "Hisobingizga bonus qo'shilishi kutilmoqda",
        "Maxsus taklifdan foydalaning, vaqt cheklangan",
        "Siz uchun eksklyuziv imkoniyat tayyor",
        "Profilingiz uchun maxsus chegirma faollashtirilishi mumkin",
        "Siz {amount} so'm miqdorida mukofot olishingiz mumkin",
        "Bugungi taklif: maxsus bonus sizni kutmoqda",
    ]
    results = []
    for t in templates:
        for _ in range(3):
            msg = t.format(
                pct=random.choice([10, 15, 20, 25, 30, 50]),
                amount=random.choice(["5,000", "10,000", "15,000", "25,000", "50,000"]),
                time=random.choice(["bugun", "ertaga", "3 kun"])
            )
            results.append(inject_noise(msg))
    return results


def gen_uz_authority() -> list:
    templates = [
        "Xavfsizlik xizmati: hisobingizni tekshiring",
        "Tizim administratori xabari: yangilanish talab qilinadi",
        "Bank xavfsizlik bo'limi: {action}",
        "Texnik xizmat xabari: tizim yangilanishi rejalashtirilgan",
        "Hisobingiz xavfsizligi uchun {action}",
        "Administratsiya: profilingizni yangilang",
        "Xavfsizlik markazi: hisobingiz tekshirilmoqda",
        "Tizim ma'muriyati: parolingizni yangilash tavsiya etiladi",
        "Bank xizmati: kartangiz holati tekshirilmoqda",
        "Qo'llab-quvvatlash xizmati: ma'lumotlaringizni tasdiqlang",
        "Moderator xabari: akkauntingiz ko'rib chiqilmoqda",
        "Xavfsizlik tizimi: shubhali faoliyat aniqlandi",
        "Texnik yordam: tizimga qayta kiring",
        "Administrator: hisobingiz vaqtincha to'xtatildi",
        "Bank: kartangiz faoliyati tekshirilmoqda",
        "Tizim xavfsizligi: {action}",
        "Xizmat ko'rsatish markazi: hisobingizni tasdiqlang",
        "Texnik bo'lim: tizim yangilanishi amalga oshirilmoqda",
        "Xavfsizlik: oxirgi kirishlar tekshirilmoqda",
        "Ma'muriyat: akkauntingiz holati yangilandi",
    ]
    actions = [
        "ma'lumotlaringizni tekshiring",
        "akkauntingizni yangilang",
        "parolingizni o'zgartiring",
        "kartangiz ma'lumotlarini tasdiqlang",
        "kirishlarni tekshiring",
        "hisobingizni tasdiqlang",
    ]
    results = []
    for t in templates:
        for _ in range(3):
            msg = t.format(action=random.choice(actions))
            results.append(inject_noise(msg))
    return results


def gen_uz_mixed_language() -> list:
    """Mixed UZ-EN / UZ-RU messages — very common in Uzbekistan."""
    templates = [
        "Hisobingizni verify qiling",
        "Account ni tasdiqlash kerak",
        "Security update qiling iltimos",
        "Password ni o'zgartiring please",
        "Sizning account {status}",
        "Login qilib tekshirib ko'ring",
        "Profile ni update qilish kerak",
        "Hisobingiz temporarily blocked",
        "Confirm qiling akkauntingizni",
        "New notification bor sizga",
        "Kartangiz expired bo'ldi",
        "OTP kodni verify qiling",
        "Hisobingiz uchun new offer bor",
        "Please hisobingizni check qiling",
        "Username yoki password xato",
        "Account reactivation kerak",
        "Sizning balance tekshirilmoqda",
        "Message pending bor sizda",
        "Billing issue aniqlandi hisobingizda",
        "Contact support iltimos, muammo bor",
        "Verify your kartangizni please",
        "New device dan login bo'ldi",
        "Secure your hisobingizni",
        "Update required profilingiz uchun",
        "Payment pending kutilmoqda",
        "Sizga cashback offer bor",
        "Discount olish uchun register qiling",
        "Bonus activation kerak, tezroq",
        "Subscription expire bo'lmoqda",
        "Sizning order status yangilandi",
        "Akkauntingiz review qilinmoqda",
        "Sizga urgent xabar keldi",
        "Iltimos login qiling va confirm qiling",
        "Transaction failed bo'ldi, tekshiring",
        "Yangi security policy accept qiling",
    ]
    statuses = ["pending", "under review", "temporarily suspended", "restricted"]
    results = []
    for t in templates:
        for _ in range(4):
            msg = t.format(status=random.choice(statuses))
            results.append(inject_noise(msg))
    return results


def gen_uz_social_engineering() -> list:
    """Subtle social engineering patterns common in UZ context."""
    templates = [
        "Salom, men {role}dan. Hisobingiz haqida gaplashishimiz kerak",
        "Assalomu alaykum, {org} dan qo'ng'iroq bo'ladi, javob bering",
        "Sizning buyurtmangiz bo'yicha muammo bor, aloqaga chiqing",
        "Hurmatli mijoz, {org} sizga xabar yubormoqda",
        "Tizimda sizning nomingizga ro'yxatdan o'tilgan, sizmisiz?",
        "Xurmatli foydalanuvchi, hisobingiz bo'yicha savol bor",
        "Salom, sizning raqamingiz {org} da ro'yxatdan o'tgan",
        "Iltimos, {org} bilan bog'laning, muhim xabar bor",
        "Sizning ma'lumotlaringiz bo'yicha tushuntirish kerak",
        "Buyurtmangiz holati o'zgardi, tekshiring",
        "Sizga {org} tomonidan maxsus taklif yuborildi",
        "Do'stingiz sizga sovg'a yubordi, tasdiqlang",
        "Guruhda sizni eslatishdi, tekshiring",
        "Sizning nomingizga ariza tushdi",
        "Hurmatli mijoz, akkauntingiz bo'yicha aniqlik kiritish kerak",
        "Kimdir sizni kontakt sifatida ko'rsatdi",
        "Sizning profilingiz ko'rildi, kim ekanligini bilasizmi?",
        "Sizga yaqin odamdan xabar bor, oching",
        "Tanishingiz sizni tavsiya qildi, tekshiring",
        "Do'stingiz akkauntingizga kirishga harakat qildi",
    ]
    roles = ["bank xodimi", "texnik mutaxassis", "xavfsizlik bo'limi", "operator"]
    orgs = ["Payme", "Click", "Humo", "UzCard", "Beeline", "Ucell", "Uzmobile", "Kapitalbank"]
    results = []
    for t in templates:
        for _ in range(3):
            msg = t.format(role=random.choice(roles), org=random.choice(orgs))
            results.append(inject_noise(msg))
    return results


def gen_uz_financial_gray() -> list:
    """Financial messages that are suspicious but not outright scam."""
    templates = [
        "Hisobingizga {amount} so'm tushdi, tekshiring",
        "Kartangiz bo'yicha operatsiya amalga oshirildi",
        "To'lov holati: kutilmoqda. Tasdiqlang",
        "Hisobingizdan {amount} so'm yechildi, taniyasizmi?",
        "Kartangiz bilan yangi qurilmadan operatsiya qilindi",
        "Oxirgi tranzaksiyani tasdiqlang iltimos",
        "Hisobingizda shubhali operatsiya kuzatildi",
        "To'lovingiz qayta ishlash jarayonida",
        "Kartangiz limitiga yaqinlashmoqdasiz",
        "Hisobingiz balansi {amount} so'mdan past",
        "Yangi to'lov so'rovi qabul qilindi",
        "Kartangiz holati yangilandi, tekshiring",
        "Avtoplatej {time} da amalga oshiriladi",
        "To'lov rad etildi, qayta urinib ko'ring",
        "Hisobingiz muddati tugash arafasida",
        "Kartangiz boshqa qurilmaga ulandi",
        "Tranzaksiya tasdiqlash kutilmoqda",
        "To'lov amalga oshirish uchun tasdiqlash kerak",
        "Hisobingizdan noma'lum manzilga pul o'tkazildi",
        "Balans tekshiruvi: hisobingizga kiring",
    ]
    results = []
    for t in templates:
        for _ in range(3):
            msg = t.format(
                amount=random.choice(["5,000", "15,000", "50,000", "120,000", "300,000", "500,000"]),
                time=random.choice(["bugun", "ertaga", "soat 18:00 da"])
            )
            results.append(inject_noise(msg))
    return results


# ─────────────────────────────────────────────────────────────
# Template Pools — ENGLISH (EN)
# ─────────────────────────────────────────────────────────────

def gen_en_soft_urgency() -> list:
    templates = [
        "Please verify your account within {time}",
        "Action needed: confirm your identity",
        "Your account requires immediate attention",
        "Verify your email to keep your account active",
        "Please update your information to avoid disruption",
        "Your session will expire soon, please re-authenticate",
        "Account verification required within {time}",
        "Confirm your identity to continue using the service",
        "Important: update your profile information",
        "Your account is pending review, please verify",
        "Please complete the verification process",
        "Re-authenticate to maintain access",
        "Your credentials need to be updated",
        "Immediate action recommended for your account",
        "Time-sensitive: confirm your account details",
        "Your account settings need attention",
        "Please review and update your security info",
        "Verification pending for your recent activity",
        "Your access may be limited without verification",
        "Please confirm the recent changes to your account",
    ]
    times = ["24 hours", "48 hours", "the next 3 days", "today"]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t.format(time=random.choice(times))))
    return results


def gen_en_curiosity_hooks() -> list:
    templates = [
        "You have a pending notification",
        "Check your account status now",
        "Someone viewed your profile",
        "A new device signed into your account",
        "You have {n} unread messages",
        "Your recent activity has been flagged",
        "An update is available for your account",
        "Someone mentioned you in a group",
        "A new login attempt was detected",
        "Your account activity summary is ready",
        "New connection request pending",
        "You received a document from {name}",
        "Action taken on your behalf, please review",
        "Unusual sign-in activity on your account",
        "You were tagged in a conversation",
        "Your order status has changed",
        "A friend sent you a gift, check it out",
        "You've been selected for a survey",
        "New features available for your account",
        "Your weekly report is ready",
    ]
    names = ["Admin", "Support", "System", "HR", "Finance"]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t.format(n=random.randint(1, 15), name=random.choice(names))))
    return results


def gen_en_promotional() -> list:
    templates = [
        "You may qualify for a special offer",
        "Limited-time access available for premium features",
        "Exclusive deal for selected users",
        "You've been chosen for a {pct}% discount",
        "Special promotion ending soon",
        "Upgrade your account with a limited offer",
        "New rewards available, check your eligibility",
        "Your loyalty points are about to expire",
        "Complete your profile for a bonus reward",
        "Members-only discount available now",
        "You're eligible for a cashback offer",
        "New seasonal offer waiting for you",
        "Flash sale: premium access at reduced price",
        "Your subscription renewal comes with a bonus",
        "Refer a friend and earn {amount}",
        "Claim your welcome bonus today",
        "Special savings for valued customers",
        "Early access to new features",
        "You qualify for priority support upgrade",
        "Bonus points expiring in {time}",
    ]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t.format(
                pct=random.choice([10, 15, 20, 25, 30, 40, 50]),
                amount=random.choice(["$5", "$10", "$15", "$25"]),
                time=random.choice(["24 hours", "3 days", "this week"])
            )))
    return results


def gen_en_authority() -> list:
    templates = [
        "Security notice: please review your account",
        "IT Department: password change recommended",
        "Customer service: your ticket requires a response",
        "Account review: suspicious activity detected",
        "Compliance team: documentation update needed",
        "System administrator: maintenance scheduled",
        "Security team: new login policy in effect",
        "Support: your case has been escalated",
        "Billing: invoice discrepancy found",
        "Network alert: connectivity changes detected",
        "Service update: migration in progress",
        "Admin: your permissions have been modified",
        "Fraud prevention: verify recent transactions",
        "Audit team: records need your confirmation",
        "HR Department: benefits enrollment deadline approaching",
        "Legal: privacy policy update requires acknowledgment",
        "IT Security: two-factor authentication update",
        "System: your API key is about to expire",
        "Technical team: service disruption expected",
        "Management: new policy requires your agreement",
    ]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t))
    return results


def gen_en_social_engineering() -> list:
    templates = [
        "Hey, this is {name}. Can you call me back on this number?",
        "I found something interesting about you online",
        "Are you the {name} who applied for the position?",
        "We met at the conference, remember me?",
        "Your resume was forwarded to me, let's talk",
        "Quick question about the document you shared",
        "Can you confirm if this email is yours?",
        "I'm reaching out regarding your application",
        "You were recommended by a mutual friend",
        "Hi, I think we have a mutual connection",
        "Following up on our conversation earlier",
        "Did you send me a file? I received something",
        "Your colleague asked me to reach out to you",
        "I noticed your profile matches our criteria",
        "Do you still need help with the project?",
        "Your contact was listed in our directory",
        "Confirming if you registered for the event",
        "We have an opportunity that might interest you",
        "Your name came up during the meeting",
        "Hi, is this the right number for {name}?",
    ]
    names = ["Alex", "David", "Sara", "Mike", "John", "Alice", "Robert"]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t.format(name=random.choice(names))))
    return results


# ─────────────────────────────────────────────────────────────
# Template Pools — RUSSIAN (RU)
# ─────────────────────────────────────────────────────────────

def gen_ru_soft_urgency() -> list:
    templates = [
        "Пожалуйста, подтвердите свой аккаунт в течение {time}",
        "Требуется обновление данных вашего профиля",
        "Ваш аккаунт ожидает верификации",
        "Необходимо подтвердить вашу личность",
        "Ваша учетная запись требует обновления",
        "Пожалуйста, обновите данные для продолжения работы",
        "Срочно: подтвердите изменения в аккаунте",
        "Ваш профиль нуждается в актуализации",
        "Подтвердите данные для восстановления доступа",
        "Ваша сессия истекает, войдите снова",
        "Необходима повторная авторизация",
        "Обновите информацию о безопасности",
        "Ваш аккаунт ограничен, требуется подтверждение",
        "Сроки верификации истекают {time}",
        "Пожалуйста, проверьте настройки безопасности",
        "Обязательное обновление данных профиля",
        "Ваша учетная запись временно ограничена",
        "Подтвердите актуальность вашего номера телефона",
        "Ваш пароль рекомендуется сменить",
        "Завершите настройку безопасности аккаунта",
    ]
    times = ["24 часов", "48 часов", "сегодня", "3 дней"]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t.format(time=random.choice(times))))
    return results


def gen_ru_curiosity_hooks() -> list:
    templates = [
        "У вас есть непрочитанное уведомление",
        "Кто-то просматривал ваш профиль",
        "Новый вход в ваш аккаунт с другого устройства",
        "Обнаружена подозрительная активность в аккаунте",
        "Вам поступило сообщение от {name}",
        "Изменение статуса вашего заказа",
        "У вас {n} новых уведомлений",
        "Ваш еженедельный отчет готов",
        "Запрос на подключение ожидает ответа",
        "Кто-то упомянул вас в обсуждении",
        "Новый запрос авторизации зафиксирован",
        "Ваш аккаунт был активирован на новом устройстве",
        "Получен документ на ваше имя",
        "Изменения в вашем профиле зафиксированы",
        "Поступил новый отклик на вашу публикацию",
        "Ваш запрос обрабатывается",
        "Обнаружен новый вход с неизвестного IP",
        "Ваша заявка получила ответ",
        "Уведомление о смене пароля",
        "У вас есть ожидающие действия",
    ]
    names = ["Администратор", "Поддержка", "Система", "Модератор"]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t.format(n=random.randint(1, 12), name=random.choice(names))))
    return results


def gen_ru_authority() -> list:
    templates = [
        "Служба безопасности: проверьте вашу учетную запись",
        "Техническая поддержка: требуется обновление",
        "Администратор: ваши права доступа изменены",
        "Банк: проверка операций по вашей карте",
        "Отдел безопасности: обнаружена подозрительная активность",
        "Служба поддержки: ваш запрос требует ответа",
        "Финансовый отдел: несоответствие в платежах",
        "Системный администратор: плановое обслуживание",
        "Модератор: ваш аккаунт на проверке",
        "Оператор связи: изменения в тарифном плане",
        "Банк: верификация вашей карты необходима",
        "IT-отдел: смените пароль в целях безопасности",
        "Управление: новая политика безопасности",
        "Юридический отдел: требуется ваше согласие",
        "Техподдержка: ваша заявка обрабатывается",
    ]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t))
    return results


def gen_ru_social_engineering() -> list:
    templates = [
        "Здравствуйте, это {name}. Нам нужно обсудить ваш аккаунт",
        "Вас порекомендовал знакомый, свяжитесь с нами",
        "Мы нашли информацию на ваше имя, подтвердите",
        "Ваш номер указан в нашей базе, это вы?",
        "Перезвоните по поводу вашего запроса",
        "Коллега просил передать вам информацию",
        "Ваше резюме было отправлено нам, давайте обсудим",
        "Уточните, вы регистрировались на нашем сервисе?",
        "Вас упоминали в разговоре, уточните детали",
        "Мы получили заявку от вашего имени",
        "Ваш контакт был передан нам партнером",
        "Вам передали документ, проверьте пожалуйста",
        "Ваше имя фигурирует в списке участников",
        "Нам нужно уточнить данные по вашему обращению",
        "Здравствуйте, подтвердите получение сообщения",
    ]
    names = ["Анна", "Дмитрий", "Сергей", "Ольга", "Алексей"]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t.format(name=random.choice(names))))
    return results


def gen_ru_financial() -> list:
    templates = [
        "На вашу карту поступил перевод, подтвердите",
        "Операция по карте ожидает подтверждения",
        "Обнаружена транзакция на {amount} рублей",
        "Ваш баланс был обновлен, проверьте",
        "Автоплатеж запланирован на {time}",
        "Платеж отклонен, попробуйте снова",
        "Списание с карты: {amount} руб. Это вы?",
        "Ваша карта привязана к новому устройству",
        "Лимит карты скоро будет достигнут",
        "Новый запрос на перевод средств",
        "Чек по операции доступен для скачивания",
        "Подозрительная операция на {amount} руб",
        "Обновите платежные данные",
        "Ваша подписка требует продления оплаты",
        "Возврат средств ожидает подтверждения",
    ]
    results = []
    for t in templates:
        for _ in range(2):
            results.append(inject_noise(t.format(
                amount=random.choice(["500", "1,500", "5,000", "15,000", "50,000"]),
                time=random.choice(["сегодня", "завтра", "через 24 часа"])
            )))
    return results


# ─────────────────────────────────────────────────────────────
# Assembly & Deduplication
# ─────────────────────────────────────────────────────────────

def generate_for_language(lang: str, target: int) -> list:
    """Generate messages for a single language, trimmed/padded to target."""
    if lang == "uz":
        pools = [
            ("soft_urgency", gen_uz_soft_urgency()),
            ("curiosity_hooks", gen_uz_curiosity_hooks()),
            ("promotional", gen_uz_promotional()),
            ("authority", gen_uz_authority()),
            ("mixed_language", gen_uz_mixed_language()),
            ("social_engineering", gen_uz_social_engineering()),
            ("financial_gray", gen_uz_financial_gray()),
        ]
    elif lang == "en":
        pools = [
            ("soft_urgency", gen_en_soft_urgency()),
            ("curiosity_hooks", gen_en_curiosity_hooks()),
            ("promotional", gen_en_promotional()),
            ("authority", gen_en_authority()),
            ("social_engineering", gen_en_social_engineering()),
        ]
    elif lang == "ru":
        pools = [
            ("soft_urgency", gen_ru_soft_urgency()),
            ("curiosity_hooks", gen_ru_curiosity_hooks()),
            ("authority", gen_ru_authority()),
            ("social_engineering", gen_ru_social_engineering()),
            ("financial", gen_ru_financial()),
        ]
    else:
        return []

    # Flatten with category tags
    all_msgs = []
    for cat, msgs in pools:
        for m in msgs:
            all_msgs.append((m, cat))

    # Deduplicate within language (case-insensitive)
    seen = set()
    unique = []
    for msg, cat in all_msgs:
        key = msg.strip().lower()
        if key and key not in seen:
            seen.add(key)
            unique.append((msg, cat))

    random.shuffle(unique)

    # If under target, generate more variations by re-noising
    if len(unique) < target:
        original_pool = list(unique)
        attempts = 0
        while len(unique) < target and attempts < target * 5:
            base_msg, cat = random.choice(original_pool)
            variant = inject_noise(base_msg)
            key = variant.strip().lower()
            if key not in seen:
                seen.add(key)
                unique.append((variant, cat))
            attempts += 1

    # Trim if over target
    unique = unique[:target]

    return [(msg, lang, cat) for msg, cat in unique]


def build_dataset() -> list:
    """Build full dataset across all languages."""
    print(f"  Generating UZ messages (target: {TARGET_UZ})...")
    uz = generate_for_language("uz", TARGET_UZ)
    print(f"    Generated: {len(uz)}")

    print(f"  Generating EN messages (target: {TARGET_EN})...")
    en = generate_for_language("en", TARGET_EN)
    print(f"    Generated: {len(en)}")

    print(f"  Generating RU messages (target: {TARGET_RU})...")
    ru = generate_for_language("ru", TARGET_RU)
    print(f"    Generated: {len(ru)}")

    all_rows = uz + en + ru
    random.shuffle(all_rows)
    return all_rows


# ─────────────────────────────────────────────────────────────
# Main Pipeline
# ─────────────────────────────────────────────────────────────

def main():
    print("=" * 60)
    print("SafeTalk Synthetic SUSPICIOUS Dataset Generator V1")
    print(f"Timestamp: {datetime.now().strftime('%Y-%m-%d %H:%M:%S')}")
    print("=" * 60)

    # Generate
    print("\n[1/4] Generating messages...")
    rows = build_dataset()

    # Global dedup
    print("\n[2/4] Global deduplication...")
    seen = set()
    unique_rows = []
    for msg, lang, cat in rows:
        key = msg.strip().lower()
        if key not in seen:
            seen.add(key)
            unique_rows.append((msg, lang, cat))
    dupes = len(rows) - len(unique_rows)
    print(f"  Removed {dupes} cross-language duplicates")

    # Build final records with clean_text
    print("\n[3/4] Generating clean_text and final records...")
    final_records = []
    for msg, lang, cat in unique_rows:
        ct = clean_text(msg)
        if ct:  # Skip if cleaning produces empty
            final_records.append({
                "text": msg,
                "clean_text": ct,
                "label": "SUSPICIOUS",
                "language": lang,
                "source": "synthetic_suspicious_v1",
                "category": cat,
            })

    # Final dedup on clean_text
    seen_clean = set()
    deduped = []
    for rec in final_records:
        if rec["clean_text"] not in seen_clean:
            seen_clean.add(rec["clean_text"])
            deduped.append(rec)
    clean_dupes = len(final_records) - len(deduped)
    if clean_dupes:
        print(f"  Removed {clean_dupes} clean_text duplicates")
    final_records = deduped

    # Save
    print("\n[4/4] Saving output...")
    OUTPUT_DIR.mkdir(parents=True, exist_ok=True)
    with open(OUTPUT_FILE, "w", encoding="utf-8", newline="") as f:
        writer = csv.DictWriter(f, fieldnames=["text", "clean_text", "label", "language", "source", "category"])
        writer.writeheader()
        writer.writerows(final_records)

    size_kb = OUTPUT_FILE.stat().st_size / 1024
    print(f"  Saved: {OUTPUT_FILE}")
    print(f"  Size:  {size_kb:.0f} KB")

    # ── Report ──
    print("\n" + "=" * 60)
    print("GENERATION REPORT")
    print("=" * 60)

    total = len(final_records)
    print(f"\n  Total generated: {total}")

    # Language dist
    lang_counts = Counter(r["language"] for r in final_records)
    print(f"\n  Language Distribution:")
    for lang in ["uz", "en", "ru"]:
        count = lang_counts.get(lang, 0)
        pct = count / total * 100 if total else 0
        bar = "#" * int(pct / 2)
        print(f"    {lang.upper():4s}: {count:5,} ({pct:5.1f}%) {bar}")

    # Category dist
    cat_counts = Counter(r["category"] for r in final_records)
    print(f"\n  Category Distribution:")
    for cat, count in cat_counts.most_common():
        pct = count / total * 100
        print(f"    {cat:25s}: {count:4,} ({pct:4.1f}%)")

    # Samples
    print(f"\n  Sample Messages:")
    samples = random.sample(final_records, min(10, len(final_records)))
    for i, s in enumerate(samples, 1):
        lang_tag = s["language"].upper()
        cat_tag = s["category"]
        text_preview = s["text"][:80] + ("..." if len(s["text"]) > 80 else "")
        print(f"    {i:2d}. [{lang_tag}] [{cat_tag}] {text_preview}")

    print(f"\n{'=' * 60}")
    print(f"Output: {OUTPUT_FILE}")
    print(f"{'=' * 60}")


if __name__ == "__main__":
    main()
