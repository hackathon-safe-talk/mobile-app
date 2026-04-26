package com.snow.safetalk.analysis

enum class MessageSource {
    SMS_AUTO,
    TELEGRAM_AUTO,
    MANUAL
}

enum class MessageIntent {
    INFO,       // Neutral service information (OTP, balance)
    WARNING,    // Safety advisories (don't share, security)
    REQUEST,    // Active solicitation (send code, enter data)
    UNKNOWN     // No clear system-relevant intent
}

data class SignalDefinition(
    val keywords: List<String>,
    val score: Int,
    val category: String
)

data class RiskInfo(
    val percent: Int,
    val level: String,
    val color: String,
    val confidence: String
)

data class AnalysisResult(
    val risk: RiskInfo,
    val reasons: List<String>,
    val recommendations: List<String> = emptyList(),
    val links: List<String>,
    val recommendation: String,
    val source: MessageSource,
    val intent: MessageIntent = MessageIntent.UNKNOWN,
    val originalText: String,
    val senderName: String? = null,
    val sourceApp: String? = null,
    val receivedTimestamp: Long? = null,
    val detectedFileName: String? = null,
    val detectedFileType: String? = null,
    val detectedUrl: String? = null
)
