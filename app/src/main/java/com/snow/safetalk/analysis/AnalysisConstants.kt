package com.snow.safetalk.analysis

/**
 * Centralized constants for the SafeTalk analysis pipeline.
 * Ensures consistent thresholds across manual tahlil, SMS, and Telegram.
 */
object AnalysisConstants {
    const val SAFE_MAX = 39
    const val SUSPICIOUS_MIN = 40
    const val SUSPICIOUS_MAX = 69
    const val DANGEROUS_MIN = 70

    // Fusion safety floors (Tuned for the 70/40 model)
    const val FLOOR_CONFIDENT_SCAM = 85
    const val FLOOR_FINANCIAL_SCAM = 90
    const val FLOOR_MALICIOUS_FILE = 95

    // Centralized extension lists for robustness
    val DANGEROUS_EXTENSIONS = listOf(
        ".apk", ".exe", ".scr", ".bat", ".msi", ".cmd", ".vbs", ".ps1", ".js", ".jar", ".hta"
    )
    val ARCHIVE_EXTENSIONS = listOf(".zip", ".rar", ".7z", ".tar")

    // Suspicious TLDs and domains for pattern detection
    val SUSPICIOUS_TLDS = listOf(
        ".click", ".xyz", ".top", ".info", ".online", ".site", 
        ".work", ".gift", ".vip", ".shop", ".biz"
    )
    
    // Intent-Aware Phrase Patterns (Semantic Policy Alignment)
    val REQUEST_PATTERNS = setOf(
        "yuboring", "bering", "ayting", "kiriting", "tasdiqlang", "o'rnating", 
        "send the code", "tell me", "enter the code", "confirm your login", "verify your account",
        "kodni yuboring", "kodni ayting", "kodni kiriting", "nomerga yuboring",
        "отправьте код", "введите код", "подтвердите"
    )

    val WARNING_PATTERNS = setOf(
        "bermang", "aytman", "ko'rsatman", "do not share", "never share", "don't give",
        "hech kimga bermang", "xodimlariga ham", "hich kimg berma", "sir saqlang",
        "никому не сообщайте", "не передавайте"
    )

    val INFO_PATTERNS = setOf(
        "kodi:", "kod:", "tasdiqlash kodi", "sms kodi", "parol:", "verification code:", 
        "transaksiya", "o'tkazma", "yechildi", "tushum", "balance:", "hisobingizda",
        "kod подтверждения", "транзакция", "списание"
    )

    // Semantic Keyword Families
    val OTP_KEYWORDS = setOf(
        "otp", "one time password", "tasdiqlash kodi", "sms kodi", "verification code", 
        "kodni yuboring", "kodni ayting", "kodni kiriting", "подтверждения", "код подтверждения"
    )

    val BANKING_KEYWORDS = setOf(
        "bank", "karta", "hisob", "balans", "to‘lov", "o‘tkazma", "karta raqami",
        "pul mablag‘lari", "onlayn bank", "hisob raqami", " карта ", "счет", "перевод"
    )

    val LOGIN_AUTH_KEYWORDS = setOf(
        "login", "parol", "verifikatsiya", "identifikatsiya", "verify", "secure", "update",
        "account", "bank-login", "confirm-account", "security-update"
    )

    val CARD_PAYMENT_KEYWORDS = setOf(
        "cvv", "cvc", "muddat", "kartaning amal qilish muddati", "to'lov", "payment",
        "plastik karta", "uzcard", "humo", "visa", "mastercard", "karta raqami", "karta raqamini",
        "muddatini", "amal qilish muddati"
    )

    val URGENCY_KEYWORDS = setOf(
        "urgent", "immediately", "darhol", "shoshiling", "zudlik bilan", "hozir",
        "kechiktirmang", "tezda", "24 soat", "10 daqiqa", "tezkor javob", "срочно", "немедленно",
        "aks holda", "vaqt tugayapti", "cheklanadi"
    )

    val FEAR_THREAT_KEYWORDS = setOf(
        "bloklandi", "muzlatildi", "yopiladi", "hisob yopiladi", "cheklangan",
        "xavf ostida", "jarima", "to‘xtatiladi", "shoshilinch xavf", "блокировка", "заблокирован"
    )

    val PRIZE_BAIT_KEYWORDS = setOf(
        "yutdingiz", "sovrin", "bonus", "mukofot", "bepul", "hadya", "lotereya", 
        "sovg'a", "yutuq", "giveaway", "free premium", "stars giveaway", "вы выиграли", "приз",
        "premium yutdingiz", "premium yutib oldingiz", "sovg'a tayyorlangan", "siz yutdingiz"
    )

    val CURIOSITY_BAIT_KEYWORDS = setOf(
        "bu senmi", "rasmga qaragin", "shu rasmda sen bormi", "look at this photo", 
        "is this you", "senmisan", "foto", "video", "shaxsiy rasm",
        "siz uchun tayyorlangan", "sizga maxsus", "ko'rib chiqing", "tekshirib ko'ring", "sizni kutmoqda",
        "tavsiya etiladi", "saqlab qolish uchun"
    )

    val PROMO_PRESSURE_KEYWORDS = setOf(
        "ajoyib imkoniyat", "bugunoq", "hoziroq", "ro'yxatdan o'ting", "aktivlashtiring", 
        "maxsus taklif", "aksiya", "foydali taklif", "imkoniyatni boy bermang", "siz uchun maxsus",
        "foydali", "imkoniyat", "foydali imkoniyat"
    )

    val ACCOUNT_UPDATE_KEYWORDS = setOf(
        "profilingizni yangilang", "profilingizni tasdiqlang", "akkauntingizni yangilang",
        "akkauntingizni tasdiqlang", "loginni tasdiqlang", "hisobingizni tasdiqlang",
        "funksiyalar ishlamasligi mumkin", "bloklanishi mumkin",
        "hisobingiz bo'yicha yangilanish", "akkauntni tasdiqlang", "yangilanish mavjud",
        "hisobingiz bo'yicha", "yangilanish", "akkaunt yangilanishi", "hisob yangilanishi"
    )

    val VAGUE_ACTION_KEYWORDS = setOf(
        "tekshirib chiqing", "yangilang", "tasdiqlang", "ko'rib chiqing", "haqidagiyangilanish",
        "tasdiqlash", "tasdiqlanish", "qayta tasdiqlash", "aniqlash", "aniqlashtirish", "ma'lumotlarni aniqlashtirish"
    )

    // --- STAGE 3: Intent Keyword Groups ---
    val CLICK_INTENT_KEYWORDS = setOf(
        "bosing", "bos", "havolani oching", "linkni oching", "open", "click", "tap",
        "batafsil ko‘ring", "see details", "ko‘rib chiqing"
    )

    val DOWNLOAD_INTENT_KEYWORDS = setOf(
        "yuklab oling", "yuklab olish", "download", "install", "o‘rnating",
        "ilovani o‘rnating", ".apk", "apk", "faylni yuklab oling"
    )

    val LOGIN_INTENT_KEYWORDS = setOf(
        "login qiling", "hisobga kiring", "akkauntga kiring", "sign in", "confirm account",
        "parolni kiriting", "identifikatsiyadan o'ting", "shaxsingizni tasdiqlang"
    )

    val PAYMENT_INTENT_KEYWORDS = setOf(
        "to‘lov qiling", "to‘lovni yakunlang", "payment", "pay now", 
        "karta ma’lumotlarini kiriting", "card details", "transfer qiling", 
        "jarimani to‘lang", "click.uz", "payme", "karta raqami", "muddatini"
    )

    val URGENCY_INTENT_KEYWORDS = setOf(
        "hoziroq", "darhol", "zudlik bilan", "immediately", "urgent", 
        "bugun oxirigacha", "24 soat ichida", "kechiktirmang", "aks holda", "bloklanadi"
    )

    val SUBMIT_INTENT_KEYWORDS = setOf(
        "yuboring", "jo‘nating", "reply qiling", "javob bering", 
        "sms kodni yuboring", "confirm code", "kodni kiriting", "tasdiqlash kodini yuboring"
    )

    // SAFE Pattern Signatures (Information/Warning only)
    val SAFE_OTP_WARNING_PATTERNS = setOf(
        "bu kodni hech kimga bermang", "xodimlariga ham", "don't share", 
        "ni hech kimga aytmang", "никому не сообщайте"
    )

    val SAFE_TRANSACTION_PATTERNS = setOf(
        "yechildi", "tushum", "to'lov qabul qilindi", "balans:", "balance:", "o'tkazildi"
    )

    val SAFE_SERVICE_NOTIFICATION_PATTERNS = setOf(
        "successfully updated", "sizning profilingiz", "xizmat yoqildi",
        "tasdiqlash kodi", "sms kodi", "ariza qabul qilindi", "my.gov.uz",
        "yetkazib berildi", "buyurtmangiz borgan", "tarif rejangiz", "daqiqalar qoldi",
        "hisobingiz to'ldirildi", "ijobiy hal etildi", "id.egov.uz"
    )

    val SAFE_BANK_ALERT_PATTERNS = setOf(
        "o'tkazildi", "tushum:", "xarid:", "yechildi:", "karta bloklandi",
        "shaxsiy kabinetingizga kirish", "vaqtida to'lov qiling", "hisobingiz holati"
    )

    // Characters commonly used to bypass filters
    val OBFUSCATION_PATTERNS = Regex(
        "[\u200B\u200C\u200D\uFEFF\u00AD]", // Removed double backslashes which caused literal matching instead of unicode characters
        RegexOption.IGNORE_CASE
    )

    /**
     * Centralized mapping from numeric score to human-readable level.
     * 0-39 -> XAVFSIZ
     * 40-69 -> SHUBHALI
     * 70-100 -> XAVFLI
     */
    fun getLevel(score: Int): String = when {
        score >= DANGEROUS_MIN -> "XAVFLI"
        score >= SUSPICIOUS_MIN -> "SHUBHALI"
        else -> "XAVFSIZ"
    }

    fun getColor(score: Int): String = when {
        score >= DANGEROUS_MIN -> "red"
        score >= SUSPICIOUS_MIN -> "yellow"
        else -> "green"
    }

    fun getConfidenceLabel(score: Int): String = when {
        score >= DANGEROUS_MIN -> "yuqori"
        score >= SUSPICIOUS_MIN -> "o\u2018rta"
        else -> "past"
    }

    /**
     * Replaced with ML-based probability logic where applicable. Retained as fallback wrapper.
     */
    fun getNumericConfidence(score: Int): Int = when {
        score >= DANGEROUS_MIN -> 90
        score >= SUSPICIOUS_MIN -> 60
        else -> 30
    }
}
