package com.snow.safetalk.telegram

import com.snow.safetalk.analysis.AnalysisConstants
import com.snow.safetalk.analysis.AnalysisResult

object TelegramSignalExtractor {

    /**
     * Enriches the analysis result with Telegram-specific scam patterns.
     * Aligned with core thresholds from AnalysisConstants.
     */
    fun enrichResult(originalMessage: String, baseResult: AnalysisResult): AnalysisResult {
        val lowerText = originalMessage.lowercase()
        val extraReasons = mutableListOf<String>()
        var extraScore = 0

        // 1. DANGEROUS ATTACHMENTS (Additional escalation for TG)
        val attachments = listOf(".apk", ".zip", ".rar", ".exe", ".scr", ".bat")
        if (attachments.any { lowerText.contains(it) }) {
            extraReasons.add("Telegram orqali yuborilgan shubhali fayl aniqlandi")
            extraScore += 40
        }

        // 2. TELEGRAM SCAM KEYWORDS (Modern variants)
        val scams = listOf(
            "tekin", "bonus", "yutuq", "free premium", "telegram premium", 
            "telegram stars", "claim", "reward", "hadya", "stars giveaway"
        )
        if (scams.any { lowerText.contains(it) }) {
            extraReasons.add("Telegram firibgarligiga xos so'zlar aniqlandi (Premium, yutuq va h.k.)")
            extraScore += 30
        }

        // 3. CURIOSITY TRIGGERS
        val curiosity = listOf("bu senmi", "rasmga qaragin", "shu rasmda sen bormi", "look at this photo", "is this you", "senmisan")
        if (curiosity.any { lowerText.contains(it) }) {
            val hasFile = baseResult.detectedFileName != null || attachments.any { lowerText.contains(it) }
            val hasLink = baseResult.links.isNotEmpty()
            
            if (hasFile) {
                extraReasons.add("Manipulyatsiya: qiziqtirish orqali xavfli faylni ochishga undashmoqda")
                extraScore += 50
            } else if (hasLink) {
                extraReasons.add("Manipulyatsiya: qiziqtirish orqali shubhali havolaga o'tkazishmoqda")
                extraScore += 40
            }
        }

        if (extraReasons.isEmpty() && extraScore == 0) {
            return baseResult
        }

        val finalScore = (baseResult.risk.percent + extraScore).coerceIn(0, 100)
        
        val newLevel = AnalysisConstants.getLevel(finalScore)
        val newColor = AnalysisConstants.getColor(finalScore)
        val newConfidence = AnalysisConstants.getConfidenceLabel(finalScore)

        val newRecommendation = if (finalScore >= AnalysisConstants.DANGEROUS_MIN) {
            "Xabar firibgarlik bo\u2018lishi mumkin. Havolalarni ochmang. Yuboruvchini bloklang."
        } else baseResult.recommendation

        val combinedReasons = (baseResult.reasons + extraReasons).distinct()

        return baseResult.copy(
            risk = baseResult.risk.copy(
                percent = finalScore,
                level = newLevel,
                color = newColor,
                confidence = newConfidence
            ),
            reasons = combinedReasons,
            recommendation = newRecommendation
        )
    }
}
