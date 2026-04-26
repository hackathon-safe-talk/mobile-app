package com.snow.safetalk.signals

import com.snow.safetalk.analysis.AnalysisConstants
import com.snow.safetalk.analysis.SignalDefinition

object Signals {
    val SIGNALS: Map<String, SignalDefinition> = mapOf(
        // CATEGORY: MALWARE
        "dangerous_file_extension" to SignalDefinition(
            keywords = AnalysisConstants.DANGEROUS_EXTENSIONS,
            score = 75,
            category = "MALWARE"
        ),
        "archive_payload" to SignalDefinition(
            keywords = AnalysisConstants.ARCHIVE_EXTENSIONS,
            score = 20,
            category = "MALWARE"
        ),
        "double_extension_trick" to SignalDefinition(
            keywords = emptyList(), // Handled by PatternEngine/Logic
            score = 50,
            category = "MALWARE"
        ),

        // CATEGORY: PHISHING
        "phishing_login" to SignalDefinition(
            keywords = AnalysisConstants.LOGIN_AUTH_KEYWORDS.toList(),
            score = 40,
            category = "PHISHING"
        ),
        "phishing_auth_bait" to SignalDefinition(
            keywords = listOf("telegram premium", "premium free", "free premium", "premium gift", "premium claim"),
            score = 45,
            category = "PHISHING"
        ),

        // CATEGORY: FINANCIAL
        "financial_data_request" to SignalDefinition(
            keywords = AnalysisConstants.BANKING_KEYWORDS.toList(),
            score = 25,
            category = "FINANCIAL"
        ),
        "otp_request" to SignalDefinition(
            keywords = AnalysisConstants.OTP_KEYWORDS.toList(),
            score = 45,
            category = "FINANCIAL"
        ),
        "personal_info_request" to SignalDefinition(
            keywords = listOf("pasport", "jshshir", "pinfl", "shaxsiy ma'lumot", "hujjat", "propiska", "seriyasi"), // Dedicated keywords to prevent overlap
            score = 30,
            category = "FINANCIAL"
        ),
        "card_phishing_bait" to SignalDefinition(
            keywords = AnalysisConstants.CARD_PAYMENT_KEYWORDS.toList(),
            score = 35,
            category = "FINANCIAL"
        ),

        // CATEGORY: LINK
        "suspicious_link" to SignalDefinition(
            keywords = listOf("http://", "https://", "bit.ly", "tinyurl", "rb.gy", "shorturl", "klik qiling", "bosing"),
            score = 30,
            category = "LINK"
        ),
        "suspicious_tld_match" to SignalDefinition(
            keywords = AnalysisConstants.SUSPICIOUS_TLDS,
            score = 25,
            category = "LINK"
        ),

        // CATEGORY: SOCIAL_ENGINEERING
        "urgency_pressure" to SignalDefinition(
            keywords = AnalysisConstants.URGENCY_KEYWORDS.toList(),
            score = 15, // Reduced from 20
            category = "SOCIAL_ENGINEERING"
        ),
        "fear_threat" to SignalDefinition(
            keywords = AnalysisConstants.FEAR_THREAT_KEYWORDS.toList(),
            score = 25, // Reduced from 30
            category = "SOCIAL_ENGINEERING"
        ),
        "prize_bait" to SignalDefinition(
            keywords = AnalysisConstants.PRIZE_BAIT_KEYWORDS.toList(),
            score = 30, // Reduced from 40
            category = "SOCIAL_ENGINEERING"
        ),
        "curiosity_lure" to SignalDefinition(
            keywords = AnalysisConstants.CURIOSITY_BAIT_KEYWORDS.toList(),
            score = 15, // Reduced from 20
            category = "SOCIAL_ENGINEERING"
        ),
        "promo_pressure" to SignalDefinition(
            keywords = AnalysisConstants.PROMO_PRESSURE_KEYWORDS.toList(),
            score = 15, // Reduced from 20
            category = "SOCIAL_ENGINEERING"
        ),
        "account_update_pressure" to SignalDefinition(
            keywords = AnalysisConstants.ACCOUNT_UPDATE_KEYWORDS.toList(),
            score = 15, // Reduced from 25
            category = "SOCIAL_ENGINEERING"
        ),
        "vague_action_request" to SignalDefinition(
            keywords = AnalysisConstants.VAGUE_ACTION_KEYWORDS.toList(),
            score = 10, // Reduced from 15
            category = "SOCIAL_ENGINEERING"
        ),
        "click_intent" to SignalDefinition(
            keywords = AnalysisConstants.CLICK_INTENT_KEYWORDS.toList(),
            score = 5, // Reduced from 10
            category = "INTENT"
        ),
        "download_intent" to SignalDefinition(
            keywords = AnalysisConstants.DOWNLOAD_INTENT_KEYWORDS.toList(),
            score = 15, // Reduced from 20
            category = "INTENT"
        ),
        "login_intent" to SignalDefinition(
            keywords = AnalysisConstants.LOGIN_INTENT_KEYWORDS.toList(),
            score = 20, // Reduced from 25
            category = "INTENT"
        ),
        "payment_intent" to SignalDefinition(
            keywords = AnalysisConstants.PAYMENT_INTENT_KEYWORDS.toList(),
            score = 25, // Reduced from 30
            category = "INTENT"
        ),
        "urgency_intent" to SignalDefinition(
            keywords = AnalysisConstants.URGENCY_INTENT_KEYWORDS.toList(),
            score = 5, // Reduced from 10
            category = "INTENT"
        ),
        "submit_intent" to SignalDefinition(
            keywords = AnalysisConstants.SUBMIT_INTENT_KEYWORDS.toList(),
            score = 10, // Reduced from 15
            category = "INTENT"
        ),
        "social_manipulation" to SignalDefinition(
            keywords = listOf("do‘stlaringiz kutmoqda", "jamiyat oldida", "hammasi sizga bog‘liq", "hech kimga aytmang", "maxfiy"),
            score = 15, // Reduced from 20
            category = "SOCIAL_ENGINEERING"
        ),

        // CATEGORY: SAFE (Informational / Warnings)
        "safe_otp_notice" to SignalDefinition(
            keywords = AnalysisConstants.SAFE_OTP_WARNING_PATTERNS.toList(),
            score = 0,
            category = "SAFE"
        ),
        "safe_transaction_info" to SignalDefinition(
            keywords = AnalysisConstants.SAFE_TRANSACTION_PATTERNS.toList(),
            score = 0,
            category = "SAFE"
        ),
        "safe_service_notification" to SignalDefinition(
            keywords = AnalysisConstants.SAFE_SERVICE_NOTIFICATION_PATTERNS.toList(),
            score = 0,
            category = "SAFE"
        ),
        "safe_bank_alert" to SignalDefinition(
            keywords = AnalysisConstants.SAFE_BANK_ALERT_PATTERNS.toList(),
            score = 0,
            category = "SAFE"
        )
    )

    // Legacy or utility groups
    val FINANCIAL_ALERT_KEYWORDS = AnalysisConstants.SAFE_TRANSACTION_PATTERNS.toList() + 
                                  listOf("tranzaksiya", "noma’lum to‘lov", "hisobingizda shubhali faollik", "kirish aniqlandi")
}
