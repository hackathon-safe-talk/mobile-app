import pandas as pd
from pathlib import Path

base_dir = Path(__file__).resolve().parents[1]

def generate_uzbek_enriched():
    output_dir = base_dir / "data" / "uzbek_expansion"
    sms_path = output_dir / "sms_uz_enriched.csv"
    telegram_path = output_dir / "telegram_uz_enriched.csv"
    
    output_dir.mkdir(parents=True, exist_ok=True)
    
    # ----------------
    # SMS UZ ENRICHED
    # ----------------
    sms_data = []
    
    # SMS Ham Examples (40+)
    sms_ham_texts = [
        "Aka qayerdasiz?", "Assalomu alaykum ustoz, dars qachon boshlanadi?", 
        "Pulni tashlab yubordim, tekshirib ko'ring", "Tezroq kela qoling kutib qoldik",
        "Ertaga soat 9da ofisda ko'rishamiz", "Sms keldiyoq o'qib javob yozing",
        "Toshkentga yetib keldik, hammasi joyida", "Bugun ob-havo qanaqa bo'larkan?",
        "Ona, non bilan sut opkelaman", "Dam olish kuningiz maroqli o'tsin",
        "Dokumentlarni telegramdan tashlab yuborin", "Mashinani qayerga qoydingiz",
        "Xayrli tong, ishlar qalay", "Yaxshi dam oldingizmi?",
        "Juma ayyomingiz muborak", "O'rtoq, qarzni qachon qaytarasan?",
        "Keyingi hafta tug'ilgan kunga boramizmi?", "Bozordan nima oliy?",
        "O'g'lim, uydamisan?", "Biznikiga mehmonga kelinglar bugun",
        "Rahmat kattakon", "Qo'ng'iroq qilaman hozir", "Ishlarim chiqib qoldi bora olmayman",
        "Imtihon qachon ekan?", "Tabriklayman, yutuqlaringiz bardavom bo'lsin",
        "Kecha nega telefonni ko'tarmading?", "Akam kelyaptilar, kutib ololasanmi?",
        "Moshinani yuvdirib kel", "Men chiqdim, 10 minutda boraman",
        "Bugun dars bo'lmaydi, o'qituvchi kasal ekan", "Kartaga pul tushdimi?",
        "Ha tushdi raxmat", "Yo'q hali tushmadi, bank dasturi qotyapti",
        "Bayramingiz bilan tabriklayman", "Ovqat tayyor, kelaveringlar",
        "Kompterni o'chirib qo'ying", "Parolni eslolmayapman, yordam bering",
        "Maktabga qachon borish kerak?", "Chiptalarni otdim, ko'rib chiqing",
        "Ertaga xabar olaman"
    ]
    
    for text in sms_ham_texts:
        sms_data.append({"text": text, "label": "ham", "origin": "adapted", "notes": "daily chat"})
        
    # SMS Scam Examples (40+)
    sms_scam_texts = [
        "Tabriklaymiz! Siz 10,000,000 so'm yutib oldingiz. Pulni olish uchun shu ssilkaga kiring: http://fake-link.uz",
        "Diqqat! Plastik kartangiz bloklandi. Qayta tiklash uchun shaxsiy ma'lumotlaringizni kiriting: bit.ly/uzcard-block",
        "Sizga 500,000 so'm pul o'tkazmasi keldi. Qabul qilish uchun quyidagi havolani bosing: humo-pay.uz",
        "Hurmatli mijoz, to'lovingiz amalga oshmadi. Iltimos kartangiz balansini tekshiring va havolaga kiring",
        "Sizning hisobingizdan 1,500,000 so'm yechib olindi! Bekor qilish uchun darhol bosing: uzbank-cancel.com",
        "Shoshilinch! Sizga davlat tomonidan subsidiya ajratildi. Bepul pulni olish uchun ro'yxatdan o'ting",
        "Ushbu raqamga qo'ng'iroq qiling va Malibu avtomobilini yutib oling! 88002986",
        "Siz tanlandingiz! Oyiga 5000$ topish siri. Telegram guruhimizga qo'shiling: t.me/scamgroup",
        "Click orqali tolov tasdiqlanmadi, profilingiz bloklanadi. Havolaga kiring: click-uzb.com",
        "Aksiya! Barcha tovarlarga 90% chegirma. Faqat 1 soat qoldi, ssilkaga o'ting",
        "UzMobile: Sizning raqamingiz g'olib bo'ldi. Sovrinni olish uchun SMS dagi kodni 1122 ga yuboring",
        "Kredit tasdiqlandi! 20,000,000 so'mni kartangizga olish uchun shu ssilkaga bosing",
        "Saylov tugadi, sizga davlatdan mukofot puli yozildi! Tasdiqlash uchun havola...",
        "Iltimos ovoz berib yuboring, qizim konkursda qatnashyapti: fake-ovoz.uz",
        "PayMe: Sizning parolingiz o'zgartirildi. Agar siz o'zgartirmagan bo'lsangiz, havolaga kiring",
        "Diqqat! Politsiya sizni jarimaga tortdi. To'lovni onlayn amalga oshiring: jarima-tolov.uz.com",
        "Karta ma'lumotlaringiz xavf ostida! Himoyalash uchun PIN kodingizni kiriting",
        "Salom, men chet eldan yozibapman. Sizga katta meros qoldi, faqat 100 dollar komissiya tolang",
        "Bitcoin narxi tushib ketdi! Hozir 10$ kiritsangiz ertaga 1000$ qilib olasiz. Kirish:",
        "DXX ogohlantiradi! Sizning profilingiz buzilgan. Uni tiklash uchun shu botga start bosing",
        "Siz lotereya g'olibisiz. Yutuq kodini adminlarga yuboring va mukofotni oling",
        "Farzandingiz maktabda muammoga uchradi, jarima to'lashingiz kerak. Havola: maktab-jarima.uz",
        "Sizning Telegram profilingiz 24 soat ichida o'chiriladi. Bekor qilish uchun bosing",
        "Bepul internet! Beeline mijozlari uchun 100 GB bepul internet. Olish uchun: bit.ly/beeline-free",
        "Assalomu alaykum, onam kasalxonada, 50,000 ming som qarz berib turing kartaga",
        "Sizning IP manzilingiz bloklandi. Blokdan chiqarish uchun to'lov qiling",
        "Aziz fuqaro, bu soliq qo'mitasi. Qarzingizni shu havola orqali tolang",
        "Garantiyali yutuq! 1xbet da 100% yutadigan sxema topdim, kanalga o'ting",
        "Viza yutib oldingiz! AQShga Green Card tasdiqlandi. Registratsiya puli: 50$",
        "Uyga yetkazib berish xizmati: Sizning pochtangiz keldi, lekin manzil xato. To'g'rilash uchun kiring",
        "Olx.uz: Xaridor sizning tovaringizni sotib oldi. Pulni olish uchun karta raqamingizni kiriting",
        "Ushbu SMS ni 10 ta odamga yuboring va sizga baxt kulib boqadi (va balansingizga pul tushadi)",
        "Internet paketingiz tugadi. Lekin aksiya asosida bepul paketni faollashtirishingiz mumkin: fake-paket.uz",
        "Assalom, ishimizga 10 kishi kerak. Kuniga 200,000 so'm. O'rganish bepul, faqat ssilkaga bosing",
        "Click Evolution: Tizim yangilanmoqda. Kartangizni qayta ulab qo'ying",
        "Sizning avtomobilingiz qidiruvga berilgan. Yo'l harakati xavfsizligi xizmati: havola.uz",
        "Elektr ta'minoti: Qarzdorlik uchun chiroq o'chiriladi. Onlayn to'lash: uzen-scam.uz",
        "Qonuniy yordam! Qarzni kechish dasturiga yoziling: botga kiring",
        "Super daromad! Faqat telefon orqali ish. 1 kunda 50 dollar! Havolani bosing",
        "Sizning pochtangiz posilkasi bojxonada to'xtatildi. Boj to'lovini amalga oshiring: boj-online.uz"
    ]
    
    for text in sms_scam_texts:
        sms_data.append({"text": text, "label": "scam", "origin": "adapted", "notes": "phishing / block / financial / promo"})
        

    # ----------------
    # TELEGRAM UZ ENRICHED
    # ----------------
    tel_data = []
    
    # Telegram Ham Examples (40+)
    tel_ham_texts = [
        "Juma ayyomi muborak qadrdonlarim!", "Guruhga yangi a'zolarni qo'shmanglar iltimos",
        "Faylni yuklab oldim, rahmat", "Zoom linkini tashlab yuborasizmi?",
        "Ertaga praktika nechchida boshlanar ekan?", "Guruhimiz qizlarini 8-mart bilan tabriklayman",
        "Kimda konspekt bor? Tashlab yuboringlar", "Dars qachon tugaydi o'zi?",
        "Men bugun borolmayman, ruxsat olganman", "Stadionda soat 7 da yig'ilamiz",
        "Forma kiyish shartmi ertaga?", "Uydagilar bilan maslahatlashib aytaman",
        "Bugungi video yozuvi kanalga tashlanadimi?", "Kim pythonni zo'r biladi? Savol bor edi",
        "Assalomu alaykum ustoz, uy vazifasini tekshirib bera olasizmi?", "Yusuf qayerdasan, aloqaga chiq",
        "Ovozingiz eshitilmayapti, mikrofonni yoqing", "Bot qotib qoldi shekilli, ishlamayapti",
        "Rasmga tushib guruhga tashlab qo'yinglar", "Ertangi majlis qoldirildi",
        "Ob havo sovuq bo'lyapti, issiq kiyinib oling", "Kanaldagi yangiliklarni o'qidingizmi?",
        "Javobingiz uchun katta rahmat!", "Tushunarli, xop", "Aka lokatsiya tashlang",
        "Oq yo'l, yaxshi yetib boring", "Mana shu ssilkadan kirib registratsiya qilinarkan",
        "Kim ertaga Toshkentdan vodiyga ketyapti? Pochta bor edi", "Yangi kino chiqibdi, ko'rdinglarmi?",
        "Qanday yordam bera olaman?", "Iltimos, shaxsiyga yozmang guruhda hal qilamiz",
        "Ovozlarga ishonib bo'lmaydi hozirgi paytda", "Abituriyentlar diqqatiga: qabul boshlandi",
        "Uyga vazifa 5-mashq", "Kichkina qizcham tug'ildi, duo qilib qo'yinglar!",
        "Stol band qilingan, 6 kishilik", "Buyurtmangiz tayyor, olib ketishingiz mumkin",
        "Yandexdan jo'natvordim, chiqib olin", "To'lovni payme dan qilsam bo'ladimi?",
        "Aloqadamisiz? Bitta savol bor edi"
    ]
    
    for text in tel_ham_texts:
        tel_data.append({"text": text, "label": "ham", "origin": "adapted", "notes": "telegram group / work / general"})
        
    # Telegram Scam Examples (40+)
    tel_scam_texts = [
        "Sizga bepul Telegram Premium sovg'a qilishdi! Olish uchun linkni bosing: t.me/premium-fake-bot",
        "Qizim rasm chizish tanlovida qatnashyapti, iltimos shu ssilkaga kirib ovoz bering",
        "Telegram profilingiz shikoyatlar tufayli bloklanmoqda. Tasdiqlash uchun botga shaxsiy raqamingizni yuboring",
        "Iltimos ovoz berib yuboring, bitta ovoz qoldi yutishiga",
        "Assalomu alaykum, onam reanimatsiyaga tushib qoldi. Iltimos kartaga ozgina bo'lsa ham yordam bering",
        "Faqat bizning kanalda 10,000 marta aylantirilgan 1xbet sxemasi! Hozir 100 ming som tikib 5 million yuting",
        "Kriptovalyutaga sarmoya kiriting va har kuni 20% foyda oling! Yozing @scammer_crypto",
        "Siz kanalimizning 10,000-chi a'zosiga aylandingiz! Sizga 50 dollar mukofot. Karta raqamingizni tashlang",
        "Telegram Stars bepul oling! Faqat botga start bosing va 3 ta do'stingizga yuboring",
        "Bu xabarni 10 ta gruppaga yuboring va sizning balansingizga 10.000 so'm tushadi",
        "Sizning nomingizdan noqonuniy harakatlar sodir etildi. MXX tomonidan jinoiy ish ochilgan. Batafsil: havola",
        "Tezda pul ishlashni xohlaysizmi? Kuniga 2-3 soat vaqt ajrating, oyiga 1000$ daromad",
        "Erotik videolar guruhiga qo'shiling. VIP kirish faqat bugun bepul: t.me/fake-adult-channel",
        "Klik ilovasidan ro'yxatdan o'ting va 50 ming so'm bonus oling. Referal link orqali kiring",
        "Avtodomdan Cobalt yutib oling! Faqat botga azo boling va raqamingizni tasdiqlang",
        "Bu rasmni ochmang! Rasm ko'rinishida virus tarqalyapti (shu faylni ozingiz oching: image.apk)",
        "Notcoin bot yangilandi! Endi 1 kunda 10 million coin ishlash mumkin. Avtoklikerni yuklab oling",
        "Shu botga kirsangiz kim sizni nomerizni qanaqa nom bilan saqlaganini ko'rasiz!",
        "Xotin-qizlar uchun davlatdan moddiy yordam tarqatilyapti. Formani to'ldiring: moddiy-yordam.xyz",
        "Prezident qarori bilan barcha talabalarga 500,000 so'mdan bepul yordam puli tarqatilmoqda. Link:",
        "Haj safariga bepul yo'llanma yutib oling. Musulmon birodarlarimiz uchun maxsus aksiya!",
        "Diqqat! Telegram pullik bo'lmoqda. Bepul versiyada qolish uchun bu xabarni 5 kishiga yuboring",
        "Kartangizdan 50 ming yechildi (Click/Payme). Bekor qilish uchun: click-uz.support.com",
        "Adminlar diqqatiga! Kanalga narutka kerakmi? 1000 obunachi 5000 so'm, yozing",
        "Ismoilov Daler 2 yoshda. Operatsiyaga 50.000$ kerak. Iltimos ehson qiling...",
        "Qonuniy qarzdan qutulishni xohlaysizmi? Biz bankrotlik protsedurasini qilib beramiz",
        "Shu guruhdan chiqib keting, ular firibgar! Haqiqiy pul beradigan kanal mana budir:",
        "Bepul V-Bucks, UC va olmoslar! PUBG, FreeFire o'yinchilari uchun: free-uc.xyz",
        "Xayrli kun. Sizning maoshi kammi? Men oyiga 5000$ topaman. Qanday? Shu kanalga kiring",
        "Shikoyat markazi: Sizning ustingizdan shikoyat tushdi. O'qish uchun botga kiring",
        "Salom, men chet ellik harbiyman, Afg'onistonda xizmat qilyapman. Menga ishonchli odam kerak Pul jo'natish uchun",
        "Hammasi bepul! Siz hech narsa yo'qotmaysiz. Ssilkaga kiring va ko'ring!",
        "TikTok da like bosish orqali pul ishlash. Kuniga 300 ming so'm! Menejerga yozing",
        "Bankdan xabar: Bugun sizning kredit limitingiz 50 millionga oshirildi. Tasdiqlang:",
        "Dars tayyorlash jonizga tegdimi? Bizning chatgpt botimiz hammasini ishlab beradi. Bot: @fake-gpt",
        "Olx.uz yordam xizmati: Tovaringizni sotib oldim, kurerni kutib olish uchun pulni tasdiqlang",
        "Aziz ustozlar, ta'lim vazirligidan yangi buyruq. PDF faylni yuklab o'qib chiqing: virus.pdf",
        "Kanalimni sotaman, 100k padpischik bor, aktiv zor. Narxi 20$",
        "Sizning Telegram akkauntingiz boshqa qurilmadan ochildi! Bloklash uchun Havolaga bosing",
        "Ehtiyot bo'ling! Bu havola xavfli bo'lishi mumkin. Xavfsiz ko'rish uchun maxsus vpN ishlating: vpn-fake.apk"
    ]
    
    for text in tel_scam_texts:
        tel_data.append({"text": text, "label": "scam", "origin": "adapted", "notes": "phishing / promo / voice vote / appeal"})
    
    
    # Write files
    df_sms = pd.DataFrame(sms_data)
    df_tel = pd.DataFrame(tel_data)
    
    df_sms.to_csv(sms_path, index=False, encoding='utf-8')
    df_tel.to_csv(telegram_path, index=False, encoding='utf-8')
    
    print("=== Uzbek Dataset Enrichment Report ===")
    print(f"1. Added rows to sms_uz_enriched.csv: {len(df_sms)}")
    print(f"2. Added rows to telegram_uz_enriched.csv: {len(df_tel)}")
    print("\n3. Label Distribution (SMS):")
    print(df_sms['label'].value_counts().to_string())
    print("   Label Distribution (Telegram):")
    print(df_tel['label'].value_counts().to_string())
    
    print("\n4. 10 Sample Uzbek Scam Rows (mixed):")
    scam_samples = pd.concat([df_sms[df_sms['label'] == 'scam'].head(5), df_tel[df_tel['label'] == 'scam'].head(5)])
    for idx, row in scam_samples.iterrows():
        print(f"- [Scam] {row['text'][:80]}...")
        
    print("\n5. 10 Sample Uzbek Ham Rows (mixed):")
    ham_samples = pd.concat([df_sms[df_sms['label'] == 'ham'].head(5), df_tel[df_tel['label'] == 'ham'].head(5)])
    for idx, row in ham_samples.iterrows():
        print(f"- [Ham]  {row['text'][:80]}...")
        
    print(f"\n6. Output files created at:\n- {sms_path}\n- {telegram_path}")

if __name__ == "__main__":
    generate_uzbek_enriched()
