package com.snow.safetalk.analysis

/**
 * AUTHORITATIVE SEMANTIC POLICY - DO NOT MODIFY THRESHOLDS
 * 
 * This object defines the semantic rules for SafeTalk.
 * Priorities:
 * 1. Uzbek language precision
 * 2. Intent-based scoring
 * 3. Safe overrides for service messages
 */
object ScamSemanticPolicy {

    // 1. RISK CLASSES
    const val CLASS_SAFE = "SAFE"
    const val CLASS_SUSPICIOUS = "SUSPICIOUS"
    const val CLASS_DANGEROUS = "DANGEROUS"

    // 2. INTENT CLASSES
    enum class Intent {
        INFO,       // Neutral information sharing
        WARNING,    // Safety advice or security warnings
        REQUEST,    // Active data/action solicitation
        UNKNOWN     // No clear intent detected
    }

    // 3. SIGNAL CATEGORIES
    enum class Category {
        MALWARE,
        PHISHING,
        FINANCIAL,
        LINK,
        SOCIAL_ENGINEERING,
        SAFE
    }

    // 4. POLICY DEFINITION (METADATA)
    val UZBEK_PRIMARY_SAMPLES = mapOf(
        "REQUEST_OTP" to "kodni yuboring",
        "SAFE_WARNING" to "hech kimga bermang",
        "INFO_OTP" to "tasdiqlash kodi",
        "URGENCY_UZ" to "darhol tekshiring"
    )

    /**
     * Strategic scoring synergy rules.
     * These define how intent and categories combine into higher floors.
     */
    fun getSynergyFloor(intent: Intent, categories: Set<Category>): Int {
        return when {
            // REQUEST + OTP/Bank -> High Risk
            intent == Intent.REQUEST && categories.contains(Category.FINANCIAL) -> 85
            intent == Intent.REQUEST && categories.contains(Category.PHISHING) -> 80
            
            // APK + Urgency -> Very High Risk
            categories.contains(Category.MALWARE) && categories.contains(Category.SOCIAL_ENGINEERING) -> 95
            
            // Link + Prize -> High Risk
            categories.contains(Category.LINK) && categories.contains(Category.SOCIAL_ENGINEERING) -> 75
            
            else -> 0
        }
    }
}
