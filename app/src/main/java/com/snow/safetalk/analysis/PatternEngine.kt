package com.snow.safetalk.analysis

import com.snow.safetalk.signals.Signals

/**
 * Result model for the Pattern Engine evaluation.
 */
data class PatternMatchResult(
    val matchedPatternKeys: List<String>,
    val patternFloor: Int,
    val reasons: List<String>,
    val hasHardThreat: Boolean = false
)

/**
 * Dedicated engine for detecting dangerous combinations of signals, text, links, and filenames.
 * Provides risk floors to ensure high-risk scams are not dampened by ML uncertainty.
 */
object PatternEngine {

    /**
     * Evaluates collected signals and extracted metadata for known scam patterns.
     */
    fun evaluate(
        detectedSignals: Set<String>,
        normalizedText: String,
        links: List<String>,
        fileName: String?,
        fileType: String?,
        intent: MessageIntent = MessageIntent.UNKNOWN
    ): PatternMatchResult {
        val matchedKeys = mutableListOf<String>()
        val reasons = mutableListOf<String>()
        var highestFloor = 0

        val hasLink = links.isNotEmpty()
        val hasSuspiciousTld = links.any { link -> 
            AnalysisConstants.SUSPICIOUS_TLDS.any { tld -> link.lowercase().contains(tld) } 
        }
        val isMaliciousFile = detectedSignals.contains("dangerous_file_extension") || 
                             AnalysisConstants.DANGEROUS_EXTENSIONS.any { fileName?.lowercase()?.endsWith(it) == true }
        
        // Better double extension detection: excludes things like report_v1.2.pdf
        val isDoubleExtension = fileName?.contains(Regex("\\.[a-z0-9]{2,4}\\.(?:apk|exe|scr|bat|msi|cmd|vbs|ps1)$", RegexOption.IGNORE_CASE)) == true || 
                               detectedSignals.contains("double_extension_trick")

        // --- STAGE 3: Intent Detection (Boolean Flags) ---
        val hasClickIntent = detectedSignals.contains("click_intent")
        val hasDownloadIntent = detectedSignals.contains("download_intent")
        val hasLoginIntent = detectedSignals.contains("login_intent")
        val hasPaymentIntent = detectedSignals.contains("payment_intent")
        val hasUrgencyIntent = detectedSignals.contains("urgency_intent")
        val hasSubmitIntent = detectedSignals.contains("submit_intent")
        // --------------------------------------------------

        // POLICY: Intent-Aware Synergies (Combination Rules)
        if (intent == MessageIntent.REQUEST) {
            if (detectedSignals.contains("otp_request")) {
                matchedKeys.add("request_otp_danger")
                reasons.add("Xatarli so'rov: SMS kodni yuborish talab qilinmoqda (Phishing)")
                highestFloor = Math.max(highestFloor, 85)
            }
            if (detectedSignals.contains("card_phishing_bait") || detectedSignals.contains("financial_data_request")) {
                matchedKeys.add("request_card_scam")
                reasons.add("Xatarli so'rov: bank karta ma'lumotlarini kiritish yoki yuborish talab qilinmoqda")
                highestFloor = Math.max(highestFloor, 90)
            }
            if (hasLink) {
                matchedKeys.add("request_link_phishing")
                reasons.add("Shubhali so'rov va havola: fishing sahifasiga o'tish ehtimoli")
                highestFloor = Math.max(highestFloor, 70)
            }
        }

        // POLICY: Safe Overrides (Warning/Info)
        if (intent == MessageIntent.WARNING || intent == MessageIntent.INFO) {
            val hasSafePattern = AnalysisConstants.SAFE_OTP_WARNING_PATTERNS.any { normalizedText.contains(it) } ||
                                 AnalysisConstants.SAFE_TRANSACTION_PATTERNS.any { normalizedText.contains(it) } ||
                                 AnalysisConstants.SAFE_SERVICE_NOTIFICATION_PATTERNS.any { normalizedText.contains(it) }
            
            if (hasSafePattern && !hasSuspiciousTld && !isMaliciousFile) {
                matchedKeys.add("safe_override_applied")
                // Floor is intentionally 0 or very low to allow safe logic
                highestFloor = Math.max(highestFloor, 5) 
            }
        }

        // 0. Mixed Content Detection (High Risk Signature)
        if (hasLink && (isMaliciousFile || fileName != null) && (detectedSignals.contains("urgency_pressure") || detectedSignals.contains("prize_bait"))) {
            matchedKeys.add("mixed_content_danger")
            reasons.add("Xatarli kombinatsiya: matn + shubhali havola + fayl")
            highestFloor = Math.max(highestFloor, 90)
        }

        // 1. Phishing Giveaway (Reward + Link)
        if (detectedSignals.contains("prize_bait") && hasLink) {
            matchedKeys.add("phishing_giveaway")
            reasons.add("Yutuq va havola kombinatsiyasi: firibgarlik ehtimoli yuqori")
            highestFloor = Math.max(highestFloor, 75)
        }

        // 2. Fake Campaign (Giveaway + Suspicious TLD)
        if (detectedSignals.contains("prize_bait") && hasSuspiciousTld) {
            matchedKeys.add("scam_promo")
            reasons.add("Shubhali domen orqali o'tkazilayotgan soxta aksiya")
            highestFloor = Math.max(highestFloor, 80)
        }

        // 2b. Prize Bait + Refined TLD Floor (Stage 2)
        if (detectedSignals.contains("prize_bait") && hasSuspiciousTld) {
            highestFloor = Math.max(highestFloor, 55)
        }

        // 3. Financial Phishing (Bank + Link)
        if (detectedSignals.contains("financial_data_request") && hasLink) {
            matchedKeys.add("financial_phishing")
            reasons.add("Bank nomidan soxta havola orqali ma'lumotlarni o'g'irlash harakati")
            highestFloor = Math.max(highestFloor, 85)
        }

        // 4. Credential Theft (Personal Data + Urgent)
        if ((detectedSignals.contains("otp_request") || detectedSignals.contains("personal_info_request")) && detectedSignals.contains("urgency_pressure")) {
            matchedKeys.add("credential_theft")
            reasons.add("Shaxsiy ma'lumotlarni zudlik bilan kiritish talabi (Fishing)")
            highestFloor = Math.max(highestFloor, 80)
        }

        // 5. Telegram Premium Scam (TG Premium + Link)
        if (detectedSignals.contains("phishing_auth_bait") && hasLink) {
            matchedKeys.add("tg_premium_scam")
            reasons.add("Telegram Premium sovg'asi niqobi ostidagi firibgarlik")
            highestFloor = Math.max(highestFloor, 80)
        }

        // 6. Telegram Phishing (TG Premium + Suspicious TLD)
        if (detectedSignals.contains("phishing_auth_bait") && hasSuspiciousTld) {
            matchedKeys.add("tg_phishing_extreme")
            reasons.add("Shubhali havola orqali Telegram hisobini o'g'irlashga urinish")
            highestFloor = Math.max(highestFloor, 85)
        }

        // 7. Malware Delivery (Malicious File)
        if (isMaliciousFile) {
            matchedKeys.add("malware_delivery")
            reasons.add("Zararli bo'lishi mumkin bo'lgan dasturiy ta'minot (.apk, .exe va h.k.)")
            highestFloor = Math.max(highestFloor, 90)
        }

        // 8. Disguised Malware (Double Extension + Malicious File)
        if (isDoubleExtension && (isMaliciousFile || fileType != null)) {
            matchedKeys.add("hidden_malware")
            reasons.add("Niqoblangan zararli fayl aniqlandi (ikki tomonlama kengaytma)")
            highestFloor = Math.max(highestFloor, 95)
        }

        // 9. Curiosity Bait + Malicious File
        if ((detectedSignals.contains("prize_bait") || detectedSignals.contains("curiosity_lure")) && isMaliciousFile) {
            matchedKeys.add("social_malware")
            reasons.add("Qiziqtirish orqali zararli faylni yuklatish harakati")
            highestFloor = Math.max(highestFloor, 90)
        }

        // 10. Phishing Login (Fake Login + Suspicious TLD)
        if (detectedSignals.contains("phishing_login") && hasSuspiciousTld) {
            matchedKeys.add("phishing_login_match")
            reasons.add("Soxta tizimga kirish sahibasi aniqlandi")
            highestFloor = Math.max(highestFloor, 90)
        }

        // 11. Fake Banking Alert (Financial Alert + Link)
        if (detectedSignals.contains("financial_data_request") && hasLink) {
            matchedKeys.add("banking_alert_scam")
            reasons.add("Soxta bank bildirishnomasi va shubhali havola")
            highestFloor = Math.max(highestFloor, 85)
        }

        // 12. Fear + Link
        if (detectedSignals.contains("fear_threat") && hasLink) {
            matchedKeys.add("fear_phishing")
            reasons.add("Qo'rqitish va shubhali havola kombinatsiyasi")
            highestFloor = Math.max(highestFloor, 75)
        }

        // --- STAGE 5.2: Balanced Suspicious Detection ---
        
        // 1. Define Hard Threats (URLs, APKs, OTP/Payment/Card credential submission)
        // Hard threats include explicit evidence or high-risk requests.
        // Generic "intent" phrases without evidence (link/file/otp) are NOT hard threats by default.
        val hasHardThreat = hasLink || isMaliciousFile || 
                           detectedSignals.contains("otp_request") || 
                           detectedSignals.contains("card_phishing_bait") ||
                           (hasPaymentIntent && (hasLink || detectedSignals.contains("urgency_pressure") || normalizedText.contains("yuboring"))) ||
                           (hasSubmitIntent && (hasLink || detectedSignals.contains("otp_request") || normalizedText.contains("yuboring"))) ||
                           (hasLoginIntent && hasLink)

        // 2. Define Soft Manipulation Signals
        val softSignals = listOf("account_update_pressure", "urgency_pressure", "promo_pressure", "vague_action_request")
        val detectedSoftCount = softSignals.count { detectedSignals.contains(it) }
        
        // 3. Define Strong Pressure Phrases
        val strongPressurePhrases = listOf("aks holda", "cheklanadi", "vaqt tugayapti")
        val hasStrongPressure = strongPressurePhrases.any { normalizedText.contains(it) }

        // 4. Apply Floor/Cap Calibration
        if (!hasHardThreat) {
            // Apply Floor 40 if 2+ soft signals OR 1+ strong pressure
            if (detectedSoftCount >= 2 || hasStrongPressure) {
                matchedKeys.add("soft_manipulation_floor")
                reasons.add("Xabarda manipuyativ elementlar aniqlandi")
                
                // Start floor at 40
                highestFloor = Math.max(highestFloor, 40)
                
                // Signal bonus (+10 if 3+ soft signals or strong pressure)
                if (detectedSoftCount >= 3 || hasStrongPressure) {
                    highestFloor = Math.max(highestFloor, 50)
                }
            }
            
            // STRICT CAP 60 (No hard threat - cannot be DANGEROUS)
            if (highestFloor > 60) {
                highestFloor = 60
            }
        }
        // --------------------------------------------------

        // --- STAGE 3: Intent-Based Combination Rules ---

        // 1. Link + Login / Verify
        if (hasLink && hasLoginIntent) {
            matchedKeys.add("intent_link_login")
            reasons.add("Xatarli kombinatsiya: havola va tizimga kirish/tasdiqlash so'rovi")
            highestFloor = Math.max(highestFloor, 70)
        }

        // 2. Link + Payment
        if (hasLink && hasPaymentIntent) {
            matchedKeys.add("intent_link_payment")
            reasons.add("Xatarli kombinatsiya: havola va to'lovni yakunlash so'rovi")
            highestFloor = Math.max(highestFloor, 65)
        }

        // 3. Download + APK / Install
        if (hasDownloadIntent && (isMaliciousFile || fileName?.lowercase()?.endsWith(".apk") == true)) {
            matchedKeys.add("intent_download_malware")
            reasons.add("Zararli ilovani yuklab olish va o'rnatishga undash")
            highestFloor = Math.max(highestFloor, 90)
        }

        // 4. Urgency + Account Threat + Login
        if (hasUrgencyIntent && detectedSignals.contains("fear_threat") && hasLoginIntent) {
            matchedKeys.add("intent_urgent_account_threat")
            reasons.add("Shoshilinch bosim va hisobni bloklash orqali login qilishga undash")
            highestFloor = Math.max(highestFloor, 80)
        }

        // 5. Prize / Bait + Click
        if ((detectedSignals.contains("prize_bait") || detectedSignals.contains("curiosity_lure")) && hasClickIntent) {
            matchedKeys.add("intent_bait_click")
            reasons.add("Sovrin yoki qiziqish orqali havolani ochishga undash")
            highestFloor = Math.max(highestFloor, 55)
        }

        // 6. Payment + Urgency
        if (hasPaymentIntent && hasUrgencyIntent) {
            matchedKeys.add("intent_payment_urgency")
            reasons.add("To'lovni zudlik bilan amalga oshirish talabi (Scam pattern)")
            highestFloor = Math.max(highestFloor, 70)
        }

        // 7. Code / OTP Submission + Link OR Login
        if (hasSubmitIntent && (hasLink || hasLoginIntent)) {
            matchedKeys.add("intent_submit_scam")
            reasons.add("Kod yuborish va tizimga kirishga undash (Akkauntni o'g'irlash harakati)")
            highestFloor = Math.max(highestFloor, 75)
        }

        // Rule of 5 (Restricted): Only escalates to 90+ if hard threat exists
        if (hasHardThreat && (detectedSignals.size >= 5 || (detectedSignals.size >= 4 && (hasLink || isMaliciousFile)))) {
            highestFloor = Math.max(highestFloor, 95)
            reasons.add("O'ta yuqori xavf: ko'plab firibgarlik belgilari birgalikda aniqlandi")
        }

        return PatternMatchResult(
            matchedPatternKeys = matchedKeys,
            patternFloor = highestFloor,
            reasons = reasons,
            hasHardThreat = hasHardThreat
        )
    }
}
