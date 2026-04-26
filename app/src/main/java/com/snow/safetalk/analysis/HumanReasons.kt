package com.snow.safetalk.analysis

object HumanReasons {

    // --- Signal Categories ---
    private const val CAT_DOWNLOAD = "DOWNLOAD"
    private const val CAT_FINANCIAL = "FINANCIAL"
    private const val CAT_ACCOUNT = "ACCOUNT"
    private const val CAT_ACTION = "ACTION"
    private const val CAT_PRESSURE = "PRESSURE"
    private const val CAT_BAIT = "BAIT"

    // --- Signal to Category Mapping ---
    private val SIGNAL_TO_CAT = mapOf(
        "download_intent" to CAT_DOWNLOAD,
        "dangerous_file_extension" to CAT_DOWNLOAD,
        "archive_payload" to CAT_DOWNLOAD,
        "double_extension_trick" to CAT_DOWNLOAD,
        "malware_delivery" to CAT_DOWNLOAD,
        "hidden_malware" to CAT_DOWNLOAD,
        "intent_download_malware" to CAT_DOWNLOAD,

        "payment_intent" to CAT_FINANCIAL,
        "card_phishing_bait" to CAT_FINANCIAL,
        "financial_data_request" to CAT_FINANCIAL,
        "intent_link_payment" to CAT_FINANCIAL,
        "intent_payment_urgency" to CAT_FINANCIAL,
        "request_card_scam" to CAT_FINANCIAL,

        "login_intent" to CAT_ACCOUNT,
        "phishing_login" to CAT_ACCOUNT,
        "phishing_auth_bait" to CAT_ACCOUNT,
        "otp_request" to CAT_ACCOUNT,
        "account_update_pressure" to CAT_ACCOUNT,
        "personal_info_request" to CAT_ACCOUNT,
        "intent_link_login" to CAT_ACCOUNT,
        "intent_urgent_account_threat" to CAT_ACCOUNT,
        "intent_submit_scam" to CAT_ACCOUNT,
        "account_update_phishing" to CAT_ACCOUNT,
        "account_update_login_scam" to CAT_ACCOUNT,
        "request_otp_danger" to CAT_ACCOUNT,

        "click_intent" to CAT_ACTION,
        "suspicious_link" to CAT_ACTION,
        "suspicious_tld_match" to CAT_ACTION,
        "intent_bait_click" to CAT_ACTION,
        "request_link_phishing" to CAT_ACTION,

        "urgency_intent" to CAT_PRESSURE,
        "fear_threat" to CAT_PRESSURE,
        "urgency_pressure" to CAT_PRESSURE,
        "account_update_pressure_high" to CAT_PRESSURE,

        "prize_bait" to CAT_BAIT,
        "curiosity_lure" to CAT_BAIT,
        "promo_pressure" to CAT_BAIT,
        "promo_link_suspicious" to CAT_BAIT,
        "scam_promo" to CAT_BAIT,
        "phishing_giveaway" to CAT_BAIT
    )

    // --- Category Priorities ---
    private val CAT_PRIORITY = listOf(
        CAT_DOWNLOAD,
        CAT_FINANCIAL,
        CAT_ACCOUNT,
        CAT_ACTION,
        CAT_PRESSURE,
        CAT_BAIT
    )

    /**
     * Generates a fully natural, non-redundant Uzbek explanation.
     */
    fun generateGroupedReasons(
        signals: Set<String>, 
        riskLevel: String,
        patternMatchedKeys: List<String> = emptyList()
    ): List<String> {
        if (riskLevel == "XAVFSIZ") {
            return listOf("Xabar xavfsiz ko‘rinadi. Xavfli belgilar aniqlanmadi.")
        }

        val allKeys = signals + patternMatchedKeys
        val detectedCats = CAT_PRIORITY.filter { cat -> 
            allKeys.any { key -> SIGNAL_TO_CAT[key] == cat }
        }

        if (detectedCats.isEmpty()) {
            return listOf("Xabarda shubhali elementlar aniqlandi.")
        }

        val topCats = detectedCats.take(2)
        val hasLink = allKeys.contains("suspicious_link") || allKeys.contains("intent_link_login") || 
                      allKeys.contains("intent_link_payment") || allKeys.contains("phishing_login")
        
        val sb = StringBuilder("Xabar ")

        // Category-based component building
        val mainCat = topCats[0]
        val secondaryCat = if (topCats.size > 1) topCats[1] else null

        when (mainCat) {
            CAT_DOWNLOAD -> {
                sb.append("zararli APK ilova yoki faylni yuklab olishga ")
            }
            CAT_FINANCIAL -> {
                val hasFinancialEvidence = allKeys.contains("card_phishing_bait") || allKeys.contains("payment_intent")
                if (hasLink) sb.append("shubhali havola orqali ")
                if (hasFinancialEvidence) {
                    sb.append("to‘lov qilishga yoki karta ma’lumotlarini kiritishga ")
                } else {
                    sb.append("shubhali moliyaviy amallarni bajarishga ")
                }
            }
            CAT_ACCOUNT -> {
                if (hasLink) sb.append("shubhali havola yordamida ")
                val isAccountExploitation = allKeys.contains("phishing_login") || allKeys.contains("otp_request")
                if (isAccountExploitation) {
                    sb.append("shaxsiy akkauntingizni egallashga yoki hisobni tasdiqlashga ")
                } else {
                    sb.append("akkaunt ma’lumotlarini yangilash niqobi ostida manipulyatsiyaga ")
                }
            }
            CAT_ACTION -> {
                if (hasLink) sb.append("shubhali havolani ochishga va ")
                sb.append("noaniq harakatlarni bajarishga ")
            }
            CAT_PRESSURE -> {
                sb.append("ruhiy bosim o‘tkazish va shoshiltirish orqali chalg‘itishga ")
            }
            CAT_BAIT -> {
                sb.append("turli sovg‘alar yoki qiziqarli va’dalar bilan sizni jalb qilishga ")
            }
        }

        if (secondaryCat != null) {
            sb.append("va ")
            when (secondaryCat) {
                CAT_FINANCIAL -> sb.append("moliyaviy ma’lumotlarni olishga ")
                CAT_ACCOUNT -> sb.append("profilni boshqarishga ")
                CAT_ACTION -> sb.append("shubhali havolaga o'tishga ")
                CAT_PRESSURE -> sb.append("shoshilinch qaror qabul qildirishga ")
                CAT_BAIT -> sb.append("sizni qiziqtirishga ")
                else -> sb.append("boshqa shubhali harakatlarga ")
            }
        }

        sb.append("urinmoqda.")

        val finalOutput = mutableListOf<String>()
        finalOutput.add(sb.toString())

        // Add severity warning for DANGEROUS level
        if (riskLevel == "XAVFLI") {
            finalOutput.add("Bu aniq firibgarlik harakati bo‘lishi mumkin, ehtiyot bo‘ling.")
        } else if (riskLevel == "SHUBHALI") {
            finalOutput.add("Ehtiyot bo‘lish tavsiya etiladi.")
        }

        return finalOutput
    }

    // Legacy Map for fallback or simple mapping - kept but updated entries to avoid raw labels
    val REASONS: Map<String, String> = mapOf(
        "urgency_pressure" to "Shoshilinch bosim",
        "fear_threat" to "Qo‘rqitish va tahdid",
        "prize_bait" to "Yutuq va bonuslar va’dasi",
        "financial_data_request" to "Moliyaviy so‘rov",
        "otp_request" to "SMS kod so‘rovi",
        "suspicious_link" to "Shubhali havola",
        "dangerous_file_extension" to "Xavfli fayl kengaytmasi"
    )
}
