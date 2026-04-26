package com.snow.safetalk.history

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.snow.safetalk.history.MessageSource
import com.snow.safetalk.history.RiskLabel

import java.util.UUID

@Entity(tableName = "analysis_history")
data class HistoryEntity(
    @PrimaryKey val id: String = UUID.randomUUID().toString(),
    val source: MessageSource,
    val message: String,
    val riskScore: Int,
    val confidence: Int,
    val label: RiskLabel,
    val reasons: List<String>,
    val recommendations: List<String>,
    val analyzedAt: Long,
    val isRead: Boolean = false,
    val senderName: String?,
    val sourceApp: String?,
    val receivedTimestamp: Long?,
    val detectedFileName: String?,
    val detectedFileType: String?,
    val detectedUrl: String?,
    val isHiddenFromHistory: Boolean = false // Isolates this row from the History UI without affecting external raw Stats calculations
)

fun HistoryEntity.toUiModel(): AnalysisResultUi {
    return AnalysisResultUi(
        id = id,
        source = source,
        message = message,
        riskScore = riskScore,
        confidence = confidence,
        label = label,
        reasons = reasons,
        recommendations = recommendations,
        analyzedAt = analyzedAt,
        isRead = isRead,
        senderName = senderName,
        sourceApp = sourceApp,
        receivedTimestamp = receivedTimestamp,
        detectedFileName = detectedFileName,
        detectedFileType = detectedFileType,
        detectedUrl = detectedUrl
    )
}

fun AnalysisResultUi.toEntity(isHiddenFromHistory: Boolean = false): HistoryEntity {
    return HistoryEntity(
        id = id,
        source = source,
        message = message,
        riskScore = riskScore,
        confidence = confidence,
        label = label,
        reasons = reasons,
        recommendations = recommendations,
        analyzedAt = analyzedAt,
        isRead = isRead,
        senderName = senderName,
        sourceApp = sourceApp,
        receivedTimestamp = receivedTimestamp,
        detectedFileName = detectedFileName,
        detectedFileType = detectedFileType,
        detectedUrl = detectedUrl,
        isHiddenFromHistory = isHiddenFromHistory
    )
}
