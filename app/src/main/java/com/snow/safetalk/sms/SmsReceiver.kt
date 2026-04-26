package com.snow.safetalk.sms

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Build
import android.provider.Telephony
import com.snow.safetalk.history.AnalysisResultUi
import com.snow.safetalk.history.HistoryDatabase
import com.snow.safetalk.history.HistoryRepository
import com.snow.safetalk.history.MessageSource
import com.snow.safetalk.history.RiskLabel
import com.snow.safetalk.history.toUiModel
import com.snow.safetalk.sources.SourcesDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch


class SmsReceiver : BroadcastReceiver() {

    private val job   = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Telephony.Sms.Intents.SMS_RECEIVED_ACTION) return

        // ── HARD GATE: legal consent must be accepted ──────────────────
        val settingsStore = com.snow.safetalk.settings.SettingsDataStore(context)
        if (!settingsStore.hasAcceptedLegalSync()) return

        val pendingResult = goAsync()

        scope.launch {
            try {
                // ── HARD GATE: smsSourceEnabled must be ON ─────────────────
                val sourcesStore = SourcesDataStore(context)
                if (!sourcesStore.smsEnabled.first()) return@launch

                // ── Parse SMS ──────────────────────────────────────────────
                val messages = Telephony.Sms.Intents.getMessagesFromIntent(intent)
                    ?: return@launch
                if (messages.isEmpty()) return@launch

                val sender = messages[0].displayOriginatingAddress ?: "Unknown"
                val body   = messages.joinToString("") { it.displayMessageBody ?: "" }.trim()
                if (body.isBlank()) return@launch

                // ── Analyze locally ────────────────────────────────────────
                // Important: Only body text goes to analysis engine.
                // Sender does NOT affect risk score.
                val result = com.snow.safetalk.analysis.SafeTalkAnalyzer.analyzeMessage(
                    payload = com.snow.safetalk.analysis.MessageAnalysisPayload(cleanMessageText = body),
                    source = com.snow.safetalk.analysis.MessageSource.SMS_AUTO,
                    context = context
                )

                // ── Build model ────────────────────────────────────────────
                val analysisId = java.util.UUID.randomUUID().toString()
                val uiModel = result.toUiModel(
                    source = com.snow.safetalk.history.MessageSource.AUTO_SMS,
                    id = analysisId
                ).copy(
                    // We re-inject the sender info into the storage message field so history display works: "[sender]\nbody"
                    message = "[$sender]\n$body"
                )

                // ── Save to history (only when gate is ON) ─────────────────
                val repo = HistoryRepository(HistoryDatabase.getDatabase(context))
                val retention = settingsStore.historyRetentionDays.firstOrNull() ?: 90
                repo.addResult(uiModel, retentionDays = retention)

                // ── Notify if risk >= threshold + permission granted ────────
                val threshold = settingsStore.notificationThreshold.first()
                if (result.risk.percent >= threshold) {
                    val canNotify = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        android.content.pm.PackageManager.PERMISSION_GRANTED ==
                            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS)
                    } else true

                    if (canNotify) {
                        val riskPercent = result.risk.percent

                        if (riskPercent in com.snow.safetalk.analysis.AnalysisConstants.SUSPICIOUS_MIN
                                until com.snow.safetalk.analysis.AnalysisConstants.DANGEROUS_MIN) {
                            // ── SUSPICIOUS (40–69): Persistent non-dismissible notification
                            com.snow.safetalk.notification.PersistentNotificationManager
                                .showPersistentSuspiciousNotification(
                                    context    = context,
                                    analysisId = analysisId,
                                    title      = "⚠ Shubhali SMS aniqlandi",
                                    message    = "Xabarni tekshirish uchun bosing",
                                    riskScore  = riskPercent,
                                    senderName = sender
                                )
                        } else {
                            // ── DANGEROUS (70+): Full persistent alert + conditional full-screen
                            // SecurityAlertNotificationManager handles:
                            //   • setOngoing(true) + FLAG_NO_CLEAR (non-dismissible)
                            //   • setFullScreenIntent() for screen-off/locked path
                            //   • startActivity(SecurityAlertActivity) for screen-on path
                            //   • Rate limiting, deduplication, registry
                            //   • User setting gate (full_screen_alert_enabled)
                            com.snow.safetalk.notification.SecurityAlertNotificationManager
                                .showSecurityAlert(
                                    context     = context,
                                    analysisId  = analysisId,
                                    riskScore   = riskPercent,
                                    source      = com.snow.safetalk.notification.SecurityAlertNotificationManager.SecurityAlertSource.SMS,
                                    senderTitle = sender
                                )
                        }
                    }
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
