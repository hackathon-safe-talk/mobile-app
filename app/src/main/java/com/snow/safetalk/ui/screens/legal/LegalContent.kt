package com.snow.safetalk.ui.screens.legal

object LegalContent {

    /**
     * Privacy Policy text structured for easy reading in Jetpack Compose
     */
    fun getPrivacyPolicy(isUzbek: Boolean): String {
        return if (isUzbek) {
            """
                **Maxfiylik Siyosati**
                So'nggi yangilanish: Mart 2026

                SafeTalk dasturiga xush kelibsiz. Biz sizning maxfiyligingizni himoya qilishga qat'iy ishonamiz. Ushbu Maxfiylik siyosati ilovamizdan foydalanganingizda ma'lumotlaringiz qanday boshqarilishini tushuntiradi. **SafeTalk birinchi navbatda maxfiylikka asoslangan, oflayn xavfsizlik ilovasi hisoblanadi.**

                **1. Biz qanday ma'lumotlarga kiramiz va nima uchun?**
                Xavfsizlik tahlilini taqdim etish uchun SafeTalk quyidagi ma'lumotlarga faqat qurilma ichida xavflarni aniqlash uchun kiradi:
                • **SMS xabarlar:** Kiruvchi xabarlarni zararli havolalar, fishing va firibgarlik holatlariga tekshirish uchun.
                • **Bildirishnomalar:** Telegram kabi ilovalardagi xabarlarni xavfsizlikka tahdidlarni aniqlash maqsadida kuzatish uchun.
                • **URL va fayl nomlari:** Sizga yuborilgan zararli namunalarni aniqlash uchun.

                **2. Qurilmada qayta ishlash**
                Sizning xabarlaringiz, bildirishnomalaringiz va havolalaringiz tahlili **to'liq sizning qurilmangizda** oflayn sun'iy intellekt va qoidalar mexanizmi yordamida amalga oshiriladi.
                **Xabar mazmuni tashqi serverlarga yuborilmaydi. Barcha asosiy tahlillar qurilmangizda amalga oshiriladi.**

                **3. Ma'lumotlarni almashish**
                Ma'lumotlaringizni o'z serverlarimizga yubormaganimiz sababli, biz sizning shaxsiy ma'lumotlaringiz yoki xabarlaringiz mazmunini uchinchi shaxslarga sotmaymiz, almashtirmaymiz yoki bera olmaymiz.

                **4. Ma'lumotlar xavfsizligi**
                Biz faqat tahlil tarixi va statistik xavf ballari kabi minimal ma'lumotlarni qurilmangizda saqlaymiz. Bu sizga o'tgan tahdidlarni ko'rib chiqish imkonini berish uchun amalga oshiriladi va u butunlay sizning nazoratingizda. Bu tarixni istalgan vaqtda o'chirishingiz mumkin.

                **5. Ilova cheklovlari**
                SafeTalk sizni himoya qilish uchun ilg'or algoritmlardan foydalansa-da, xavfsizlik doim ehtimolliklarga bog'liq. **SafeTalk 100% aniqlik bilan xavfni aniqlash kafolatini bermaydi.** Biz ba'zan xavfsiz havolalarni shubhali deyishimiz (yolg'on ijobiy) yoki yangi tahdidlarni o'tkazib yuborishimiz mumkin (yolg'on salbiy). Har doim o'z xulosangizga tayaning.

                **6. Ruxsatlarni tushuntirish**
                SafeTalk ishlashi uchun zarur bo'lgan ruxsatlarni (masalan, SMS va Bildirishnomalarga kirish) aniq so'raydi. Ushbu ruxsatlarni tizim sozlamalaridan istalgan vaqtda bekor qilishingiz mumkin, ammo bu himoya funksiyalarini o'chirib qo'yadi.
                
                **7. Bolalar maxfiyligi**
                SafeTalk 13 yoshdan kichik bo'lgan bolalardan ongli ravishda shaxsiy ma'lumot yig'maydi. Hech qanday ma'lumot qurilmadan chiqmagani uchun, barcha ma'lumotlar faqat telefonda mahalliy qoladi.

                **8. Siyosatdagi o'zgarishlar**
                Biz Maxfiylik siyosatimizni vaqti-vaqti bilan yangilab turishimiz mumkin. Har qanday o'zgarishlar haqida ilova ichida e'lon qilish orqali sizni xabardor qilamiz.
            """.trimIndent()
        } else {
            """
                **Privacy Policy**
                Last Updated: March 2026

                Welcome to SafeTalk. We are committed to protecting your privacy. This Privacy Policy explains how SafeTalk handles your information when you use our mobile application. **SafeTalk is designed to be a privacy-first, offline security application.**

                **1. Data We Access and Why**
                To provide security analysis, SafeTalk requires access to the following data purely for on-device threat detection:
                • **SMS Messages:** To scan incoming text messages for malicious links, phishing attempts, and scam patterns.
                • **Notifications:** To monitor messages from apps like Telegram for potential security threats.
                • **URLs and File Names:** To detect known malicious patterns in links or files shared with you.

                **2. On-Device Processing**
                All analysis of your messages, notifications, and links is conducted **entirely on your device** using our offline rule-based and AI security engine.
                **Your personal data, messages, or notifications are NEVER sent to any remote server.**

                **3. Data Sharing and Third Parties**
                Because we do not transmit your data to our servers, we do not—and mathematically cannot—sell, trade, or otherwise transfer your personally identifiable information or message contents to outside parties.

                **4. Data Security & Storage**
                We store minimal data, such as analysis history and statistical risk scores, locally in an encrypted format on your device. This data is kept strictly to allow you to review past threats and is under your complete control. You can clear this history at any time from the app settings.

                **5. Application Limitations**
                While SafeTalk employs advanced algorithms to protect you, cybersecurity relies on probability. **SafeTalk does not guarantee 100% detection accuracy.** We may occasionally flag safe links (false positives) or miss new, unknown threats (false negatives). Always exercise your own judgment.

                **6. Permissions Explanation**
                SafeTalk explicitly asks for permissions (such as SMS Read/Receive and Notification Listener Access) required for the app to function. You have full control over these permissions and can revoke them at any time in your device's System Settings, though doing so will disable the associated protection features.
                
                **7. Children's Privacy**
                SafeTalk does not knowingly collect any personally identifiable information from children under the age of 13. Since no data leaves the device, children's data remains entirely local to their phone.

                **8. Changes to Policy**
                We may update our Privacy Policy from time to time. We will notify you of any changes by posting the new Privacy Policy within the app.
            """.trimIndent()
        }
    }

    /**
     * Terms of Service text structured for easy reading in Jetpack Compose
     */
    fun getTermsOfService(isUzbek: Boolean): String {
        return if (isUzbek) {
            """
                **Foydalanish Shartlari**
                So'nggi yangilanish: Mart 2026

                **1. Shartlarni qabul qilish**
                SafeTalk ilovasini yuklab olish yoki undan foydalanish orqali siz ushbu Foydalanish shartlariga rozilik bildirasiz. Agar shartlarga rozi bo'lmasangiz, iltimos, ilovadan foydalanmang.

                **2. Faqat maslahat beruvchi vosita**
                SafeTalk qat'iy ravishda **maslahat beruvchi xavfsizlik vositasi** sifatida loyihalashtirilgan. Uning maqsadi ehtimoliy fishing, firibgarlik va zararli havolalarni aniqlashga yordam berishdir. Biroq, u ehtimolli sun'iy intellekt va oldindan o'rnatilgan qoidalarga tayanadi.

                **SafeTalk 100% aniqlikni kafolatlamaydi.** Ilova ba'zan xavfsiz narsalarni xavfli deb topishi (yolg'on ijobiy) yoki asl xavflarni o'tkazib yuborishi (yolg'on salbiy) mumkin. Biror havolani ochish, ilovani o'rnatish yoki xabarga javob berish bo'yicha yakuniy qaror butunlay sizning zimmangizda.

                **3. Foydalanuvchi javobgarligi**
                SafeTalk ogohlantirishlari asosida yoki ogohlantirish yo'qligiga qarab qabul qiladigan qarorlaringiz uchun o'zingiz to'liq javobgar ekanligingizni tan olasiz. Ilova tahlilidan qat'i nazar, raqamli xabarlar bilan ishlashda doimo ehtiyotkor bo'lishingiz va aql bilan ish tutishingizga rozilik bildirasiz.

                **4. Kafolatlardan voz kechish**
                SafeTalk "BORICHA" va "MAVJUD HOLATDA" tamoyillari asosida taqdim etiladi. Biz dastrturning uzluksiz ishlashi, aniqligi yoki xatosiz ekanligiga hech qanday kafolat bermaymiz. Biz SafeTalk sizning talablaringizga to'liq mos kelishiga yoki mutlaqo xatosiz ishlashiga kafolat bermaymiz.

                **5. Javobgarlikni cheklash**
                Hech qanday holatda SafeTalk dasturchilari yoki egalari sizning ilovadan foydalanishingiz natijasida tarmoqdagi harakatlaringiz, qurilmangizning zararlanishi, firibgarlardan ko'rilgan moliyaviy zararlar yoki boshqa har qanday to'g'ridan-to'g'ri bo'lmagan yo'qotishlar uchun javobgar bo'lmaydi. Bu hatto ogohlantirilgan holatlarga ham tegishli.

                **6. Teskari muhandislik va Noto'g'ri foydalanish**
                SafeTalk ilovasini, uning on-device modellarini o'zgartirmaslikka, kodini teskari muhandislik yordamida ochmaslikka rozi bo'lasiz.

                **7. Shartlarni o'zgartirish**
                Biz istalgan vaqtda ushbu Shartlarni o'zgartirish huquqini o'zida saqlab qolamiz. Yirik o'zgarishlar haqida ilova ichida xabar beramiz.
            """.trimIndent()
        } else {
            """
                **Terms of Service**
                Last Updated: March 2026

                **1. Acceptance of Terms**
                By downloading, accessing, or using SafeTalk, you agree to be bound by these Terms of Service. If you do not agree to these terms, please do not use the application.

                **2. Advisory Tool Only**
                SafeTalk is designed exclusively as an **advisory security tool**. Its purpose is to assist you in identifying potential phishing attempts, scams, and malicious links. However, it is fundamentally reliant on probabilistic machine learning models and predetermined rulesets.

                **SafeTalk does not guarantee 100% accuracy.** The application may produce false positives (flagging benign content as dangerous) or false negatives (failing to identify actual threats). The final decision to click a link, install an application, or respond to a message rests entirely with you.

                **3. User Responsibility**
                You acknowledge that you are solely responsible for your actions and decisions based upon warnings or the lack thereof provided by SafeTalk. You agree to use caution and common sense when interacting with digital communications, regardless of the application's analysis.

                **4. Disclaimer of Warranties**
                SafeTalk is provided strictly on an "AS IS" and "AS AVAILABLE" basis. We disclaim all warranties of any kind, whether express or implied. We make no warranty that SafeTalk will meet your requirements or that the service will be uninterrupted, timely, secure, or completely error-free.

                **5. Limitation of Liability**
                In no event shall the developers, owners, or affiliates of SafeTalk be liable for any indirect, incidental, special, consequential, or punitive damages, including without limitation: loss of profits, data, use, goodwill, device damage from external malware, financial loss from scams, or other intangible losses resulting from your access to or use of, or inability to access or use, SafeTalk.

                **6. Reverse Engineering and Misuse**
                You agree not to modify, reverse engineer, decompile, or disassemble the SafeTalk application, its on-device models, or attempting to derive the source code or proprietary threat detection algorithms.

                **7. Changes to the Terms**
                We reserve the right to modify these Terms at any time. We will provide notice of any material adjustments within the application.
            """.trimIndent()
        }
    }
}
