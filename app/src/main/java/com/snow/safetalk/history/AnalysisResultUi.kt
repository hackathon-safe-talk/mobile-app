package com.snow.safetalk.history

import com.snow.safetalk.analysis.AnalysisConstants
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import java.util.UUID

@Serializable
enum class MessageSource {
    MANUAL,
    @SerialName("SMS") AUTO_SMS,       // @SerialName("SMS") keeps backward compat with old JSON
    AUTO_TELEGRAM
}

@Serializable
enum class RiskLabel { SAFE, SUSPICIOUS, DANGEROUS }

@Serializable
data class AnalysisResultUi(
    val id: String,
    val source: MessageSource = MessageSource.MANUAL,
    val message: String,
    val riskScore: Int,
    val confidence: Int,
    val label: RiskLabel,
    val reasons: List<String>,
    val recommendations: List<String> = emptyList(),
    val analyzedAt: Long = System.currentTimeMillis(),
    val isRead: Boolean = false,              // new: unread indicator support
    val senderName: String? = null,
    val sourceApp: String? = null,
    val receivedTimestamp: Long? = null,
    val detectedFileName: String? = null,
    val detectedFileType: String? = null,
    val detectedUrl: String? = null
) {
    val displayTitle: String
        get() = when (label) {
            RiskLabel.SAFE       -> "Xavfsiz xabar"
            RiskLabel.SUSPICIOUS -> "Shubhali xabar aniqlandi"
            RiskLabel.DANGEROUS  -> "Xavfli xabar aniqlandi"
        }

    val labelShort: String
        get() = when (label) {
            RiskLabel.SAFE       -> "XAVFSIZ"
            RiskLabel.SUSPICIOUS -> "SHUBHALI"
            RiskLabel.DANGEROUS  -> "XAVFLI"
        }

    val sourceShort: String
        get() = when (source) {
            MessageSource.MANUAL         -> "MANUAL"
            MessageSource.AUTO_SMS       -> "SMS"
            MessageSource.AUTO_TELEGRAM  -> "TELEGRAM"
        }

    val timestampFormatted: String
        get() {
            val cal = java.util.Calendar.getInstance().apply { timeInMillis = analyzedAt }
            return "%02d.%02d %02d:%02d".format(
                cal.get(java.util.Calendar.DAY_OF_MONTH),
                cal.get(java.util.Calendar.MONTH) + 1,
                cal.get(java.util.Calendar.HOUR_OF_DAY),
                cal.get(java.util.Calendar.MINUTE)
            )
        }

    /** Is this item unread and should show a blue dot? */
    val showUnreadDot: Boolean
        get() = !isRead && source != MessageSource.MANUAL

}



/** Convert the NEW AnalysisResult produced by SafeTalkAnalyzer into the UI model */
fun com.snow.safetalk.analysis.AnalysisResult.toUiModel(
    source: com.snow.safetalk.history.MessageSource = com.snow.safetalk.history.MessageSource.MANUAL,
    id: String = UUID.randomUUID().toString()
): AnalysisResultUi {
    val score = risk.percent
    val mappedLabel = when {
        score >= AnalysisConstants.DANGEROUS_MIN -> RiskLabel.DANGEROUS
        score >= AnalysisConstants.SUSPICIOUS_MIN -> RiskLabel.SUSPICIOUS
        else -> RiskLabel.SAFE
    }

    val numericConfidence = AnalysisConstants.getNumericConfidence(score)

    return AnalysisResultUi(
        id         = id,
        source     = source,
        message    = originalText,
        riskScore  = score,
        confidence = numericConfidence,
        label      = mappedLabel,
        reasons    = reasons,
        recommendations = recommendations,
        analyzedAt = System.currentTimeMillis(),
        isRead     = source == com.snow.safetalk.history.MessageSource.MANUAL,
        senderName = senderName,
        sourceApp  = sourceApp,
        receivedTimestamp = receivedTimestamp,
        detectedFileName = detectedFileName,
        detectedFileType = detectedFileType,
        detectedUrl = detectedUrl
    )
}
