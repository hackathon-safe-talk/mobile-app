import random
import pandas as pd
from pathlib import Path

def generate_uzbek_data(count=5000):
    data = []
    
    # Templates from ScamSemanticPolicy
    safe_info_templates = [
        "Tasdiqlash kodi: {code}",
        "Sizning kodingiz: {code}",
        "Hisobingizga {amount} so'm kelib tushdi. Manba: {sender}",
        "Balansingiz: {amount} so'm. To'lov muvaffaqiyatli.",
        "O'tkazma qabul qilindi: {amount} so'm. Humo/Uzcard",
        "Karta faollashtirildi. Kod: {code}",
        "Xizmat haqi: {amount} so'm. {bank} xizmati.",
        "Muvaffaqiyatli to'lov! Miqdor: {amount} so'm. {source}"
    ]
    
    safe_warning_templates = [
        "Bu kodni hech kimga bermang, xodimlarimiz uni so'ramaydi. Kod: {code}",
        "SMS kodingizni begonalarga bermang. Xavfsizlik xizmati.",
        "Kodingiz: {code}. Uni hech qachon begona shaxslarga aytmang!",
        "Diqqat! Kodni faqat bank ilovasiga kiriting. Uni begonalarga ishonib topshirmang.",
        "Xavfsizlik yuzasidan kodni maxfiy tuting. Bank xodimlariga ham bermang."
    ]
    
    suspicious_request_templates = [
        "Sizga {amount} so'm bonus berildi! Olish uchun: {url}",
        "Sovrin yutdingiz! {url} orqali profilingizni tasdiqlang",
        "Tabriklaymiz! Siz iPhone yutib oldingiz. Batafsil: {url}",
        "Yangi aksiya! 1 000 000 so'm yutib oling: {url}",
        "Sizning raqamingiz g'olib bo'ldi! Sovg'ani olish: {url}"
    ]
    
    dangerous_request_templates = [
        "Tasdiqlash kodini menga yuboring, aks holda bloklanadi",
        "Karta raqami va CVV kodini ayting, tizimni tekshiramiz",
        "Kodni hoziroq yozib yuboring, pul o'tkazish kerak",
        "Akkauntingiz bloklandi. Darhol loginni tasdiqlang: {dan_url}",
        "Hujjatni yuklab oling va oching: {filename}.apk",
        "Rasmni ko'ring: {filename}.jpg.apk",
        "Sizning hisobingiz xavf ostida! Kodni yuboring.",
        "Karta ma'lumotlarini kiriting: {dan_url}",
        "To'lovni tasdiqlash uchun CVV kodni ayting."
    ]
    
    # Variables
    banks = ["Payme", "Click", "Uzcard", "Humo", "Hamkorbank", "Ipak Yo'li"]
    sources = ["Terminal", "Payme", "Click", "Uzum"]
    urls = ["http://bonusuz.click", "http://prize-win.top", "http://lucky-draw.net", "http://promo-gift.ml"]
    dan_urls = ["http://secure-login.xyz", "http://bank-verify.click", "http://pay-check.top", "http://unblock-card.io"]
    filenames = ["document", "photo", "invoice", "update", "video", "scan"]
    
    # Classes: 30% SAFE, 30% SUSPICIOUS, 40% DANGEROUS (to boost small class)
    for _ in range(count):
        r = random.random()
        if r < 0.3:
            # SAFE
            if random.random() < 0.7:
                # INFO
                text = random.choice(safe_info_templates).format(
                    code=random.randint(1000, 999999),
                    amount=random.randint(1000, 500000),
                    sender=random.choice(sources),
                    bank=random.choice(banks),
                    source=random.choice(sources)
                )
                data.append([text, "SAFE", "INFO", "otp" if "kodi" in text else "transaction_notice", "uz", "aug"])
            else:
                # WARNING
                text = random.choice(safe_warning_templates).format(
                    code=random.randint(1000, 999999)
                )
                data.append([text, "SAFE", "WARNING", "otp_warning", "uz", "aug"])
        elif r < 0.6:
            # SUSPICIOUS
            text = random.choice(suspicious_request_templates).format(
                amount=random.randint(10000, 1000000),
                url=random.choice(urls)
            )
            data.append([text, "SUSPICIOUS", "UNKNOWN", "prize_bait,link", "uz", "aug"])
        else:
            # DANGEROUS
            text = random.choice(dangerous_request_templates).format(
                dan_url=random.choice(dan_urls),
                filename=random.choice(filenames)
            )
            tag = "phishing"
            if "apk" in text: tag = "malware,dangerous_file"
            if "cvv" in text or "karta" in text: tag = "card_data,phishing"
            if "kod" in text: tag = "otp_request,phishing"
            
            data.append([text, "DANGEROUS", "REQUEST", tag, "uz", "aug"])
            
    df_aug = pd.DataFrame(data, columns=["text", "risk_label", "intent_label", "tags", "language", "source"])
    return df_aug

if __name__ == "__main__":
    base_dir = Path(__file__).resolve().parents[1]
    seeds_path = base_dir / "data" / "uzbek_policy_seeds.csv"
    
    # Load original seeds
    seeds = pd.read_csv(seeds_path)
    
    # Augment
    aug_df = generate_uzbek_data(5000)
    
    # Combine
    final_seeds = pd.concat([seeds, aug_df], ignore_index=True)
    
    final_seeds.to_csv(seeds_path, index=False)
    print(f"Uzbek seeds expanded to {len(final_seeds)} samples.")
