package com.snow.safetalk.telegram

import android.app.Notification
import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import com.snow.safetalk.analysis.SafeTalkAnalyzer
import com.snow.safetalk.history.HistoryDatabase
import com.snow.safetalk.history.HistoryRepository
import com.snow.safetalk.history.MessageSource
import com.snow.safetalk.history.toUiModel
import com.snow.safetalk.protection.ProtectionForegroundService
import com.snow.safetalk.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.firstOrNull
import kotlinx.coroutines.launch
import java.util.concurrent.ConcurrentHashMap

class TelegramNotificationListenerService : NotificationListenerService() {

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)
    private lateinit var historyRepository: HistoryRepository

    override fun onCreate() {
        super.onCreate()
        historyRepository = HistoryRepository(HistoryDatabase.getDatabase(applicationContext))
    }

    companion object {
        private const val TAG = "SafeTalk-Telegram"
        private val TELEGRAM_PACKAGES = setOf(
            "org.telegram.messenger",
            "org.thunderdog.challegram"
        )
        private val debounceJobs = ConcurrentHashMap<String, kotlinx.coroutines.Job>()
        private val pendingEntries = ConcurrentHashMap<String, PendingTelegramEntry>()
        private const val DEBOUNCE_DELAY_MS = 1500L
    }

    override fun onListenerConnected() {
        super.onListenerConnected()
        Log.w(TAG, "⚡ onListenerConnected pid=${android.os.Process.myPid()}")
        com.snow.safetalk.protection.ProtectionStateSynchronizer.getInstance(applicationContext).isNlsActivelyBound = true

        val store = SettingsDataStore(applicationContext)
        if (!store.hasAcceptedLegalSync()) return

        val enabled = store.isAlwaysOnProtectionEnabledSync()
        Log.w(TAG, "⚡ protection SP mirror = $enabled")
        if (enabled) {
            val result = ProtectionForegroundService.start(applicationContext, "NLS_BIND", com.snow.safetalk.protection.StarterPolicy.IGNORE_COOLDOWN)
            if (result is com.snow.safetalk.protection.ServiceStarterResult.FailedUnexpectedly) {
                Log.e(TAG, "⚡ FGS restore FAILED from NLS: ${result.error.message}")
            } else {
                Log.w(TAG, "⚡ FGS restore executed from NLS ($result)")
            }
        }
    }

    override fun onListenerDisconnected() {
        super.onListenerDisconnected()
        Log.w(TAG, "⚡ onListenerDisconnected pid=${android.os.Process.myPid()}")
        com.snow.safetalk.protection.ProtectionStateSynchronizer.getInstance(applicationContext).isNlsActivelyBound = false
    }

    data class TelegramMessageSnapshot(
        val packageName: String,
        val senderName: String?,
        val title: String?,
        val postTime: Long,
        val notificationKey: String?,
        val groupKey: String?,
        val normalizedTextParts: List<String>,
        val captionText: String?,
        val detectedFileName: String?,
        val detectedFileType: String?,
        val detectedUrls: List<String>,
        val rawCompositeText: String
    )

    data class PendingTelegramEntry(
        val snapshot: TelegramMessageSnapshot,
        val firstSeenAt: Long,
        val lastUpdatedAt: Long
    )

    override fun onNotificationPosted(sbn: StatusBarNotification?) {
        if (sbn == null) return
        if (sbn.packageName !in TELEGRAM_PACKAGES) return

        // ── HARD GATE: legal consent must be accepted ──────────────────
        if (!SettingsDataStore(applicationContext).hasAcceptedLegalSync()) return

        val snapshot = extractTelegramSnapshot(sbn) ?: return
        if (snapshot.rawCompositeText.isBlank() && snapshot.detectedFileName.isNullOrBlank() && snapshot.detectedUrls.isEmpty()) return

        val logicalKey = snapshot.senderName ?: snapshot.packageName

        val existingJob = debounceJobs[logicalKey]
        val existingEntry = pendingEntries[logicalKey]

        if (existingJob != null && existingEntry != null && isSameLogicalMessage(existingEntry.snapshot, snapshot)) {
            val mergedSnapshot = mergeSameMessageSnapshots(existingEntry.snapshot, snapshot)
            pendingEntries[logicalKey] = existingEntry.copy(
                snapshot = mergedSnapshot,
                lastUpdatedAt = System.currentTimeMillis()
            )
            Log.d(TAG, "TG same logical message = true because of containment or close time. Merged snapshot.")
        } else {
            if (existingJob != null && existingEntry != null) {
                Log.d(TAG, "TG same logical message = false because text/file/url materially differ")
                existingJob.cancel()
                flushSnapshot(logicalKey)
            }

            Log.d(TAG, "TG creating new pending entry chain for: \$logicalKey")
            pendingEntries[logicalKey] = PendingTelegramEntry(
                snapshot = snapshot,
                firstSeenAt = System.currentTimeMillis(),
                lastUpdatedAt = System.currentTimeMillis()
            )
            
            debounceJobs[logicalKey] = scope.launch {
                kotlinx.coroutines.delay(DEBOUNCE_DELAY_MS)
                flushSnapshot(logicalKey)
            }
        }
    }

    private fun TelegramMessageSnapshot.toAnalysisPayload(): com.snow.safetalk.analysis.MessageAnalysisPayload {
        val mergedText = buildList {
            captionText?.takeIf { it.isNotBlank() }?.let { add(it) }
            normalizedTextParts.forEach { part ->
                if (part.isNotBlank() && part != captionText) add(part)
            }
        }.distinct()

        return com.snow.safetalk.analysis.MessageAnalysisPayload(
            cleanMessageText = mergedText.joinToString("\n").trim(),
            senderName = senderName,
            sourceApp = "Telegram",
            receivedTimestamp = postTime,
            detectedFileName = detectedFileName,
            detectedFileType = detectedFileType,
            detectedUrls = detectedUrls,
            detectedUrl = detectedUrls.firstOrNull()
        )
    }

    private fun flushSnapshot(logicalKey: String) {
        val finalEntry = pendingEntries.remove(logicalKey) ?: return
        val finalSnapshot = finalEntry.snapshot
        debounceJobs.remove(logicalKey)

        val isDuplicate = MessageDeduplicator.isDuplicate(
            packageName = finalSnapshot.packageName,
            postTime = finalSnapshot.postTime,
            textHash = finalSnapshot.rawCompositeText.hashCode(),
            senderOrTitle = finalSnapshot.senderName
        )
        if (isDuplicate) {
            Log.d(TAG, "TG Dropping duplicate message to avoid redundant processing.")
            return
        }

        val payload = finalSnapshot.toAnalysisPayload()
        Log.d(TAG, "TG dispatching payload: text.length=${payload.cleanMessageText.length}, file=${payload.detectedFileName}, urls=${payload.detectedUrls.size}")

        val baseResult = SafeTalkAnalyzer.analyzeMessage(
            payload = payload,
            source = com.snow.safetalk.analysis.MessageSource.TELEGRAM_AUTO,
            context = this
        )

        val enrichedResult = TelegramSignalExtractor.enrichResult(payload.cleanMessageText, baseResult)
        val analysisId = java.util.UUID.randomUUID().toString()
        
        val uiModel = enrichedResult.toUiModel(
            source = MessageSource.AUTO_TELEGRAM,
            id = analysisId
        )

        scope.launch {
            val retention = com.snow.safetalk.settings.SettingsDataStore(applicationContext).historyRetentionDays.firstOrNull() ?: 90
            historyRepository.addResult(uiModel, retentionDays = retention)
            Log.d(TAG, "TG cleanup completed for entry: \$logicalKey")
        }

        if (enrichedResult.risk.percent >= 40) {
            val riskPercent = enrichedResult.risk.percent

            if (riskPercent in com.snow.safetalk.analysis.AnalysisConstants.SUSPICIOUS_MIN
                    until com.snow.safetalk.analysis.AnalysisConstants.DANGEROUS_MIN) {
                // ── SUSPICIOUS (40–69): Persistent non-dismissible notification
                com.snow.safetalk.notification.PersistentNotificationManager
                    .showPersistentSuspiciousNotification(
                        context    = applicationContext,
                        analysisId = analysisId,
                        title      = "⚠ Shubhali Telegram xabar",
                        message    = "Xabar ehtiyotkorlik bilan tekshirilsin",
                        riskScore  = riskPercent,
                        senderName = finalSnapshot.senderName ?: "Telegram"
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
                        context     = applicationContext,
                        analysisId  = analysisId,
                        riskScore   = riskPercent,
                        source      = com.snow.safetalk.notification.SecurityAlertNotificationManager.SecurityAlertSource.TELEGRAM,
                        senderTitle = finalSnapshot.senderName ?: "Telegram"
                    )
            }
        }
    }

    private fun normalizeParts(parts: List<String>): List<String> {
        return parts
            .map { it.replace("\\s+".toRegex(), " ").replace("\n+".toRegex(), "\n").trim() }
            .filter { it.isNotBlank() }
            .distinct()
    }

    private fun detectVisibleFileName(texts: List<String>): String? {
        val fileRegex = Regex("""(?i)\b[\w\s\-\(\)\[\]\.]+\.(apk|zip|rar|7z|exe|pdf|docx?|xlsx?|pptx?)\b""")
        return texts.firstNotNullOfOrNull { text ->
            fileRegex.find(text)?.value?.trim()
        }
    }

    private fun detectFileType(fileName: String?): String? {
        val lower = fileName?.lowercase()?.trim() ?: return null
        return when {
            lower.endsWith(".apk") -> "APK dastur fayli"
            lower.endsWith(".zip") -> "Arxiv fayli"
            lower.endsWith(".rar") -> "RAR arxiv fayli"
            lower.endsWith(".7z") -> "7Z arxiv fayli"
            lower.endsWith(".exe") -> "EXE dastur fayli"
            lower.endsWith(".pdf") -> "PDF hujjat"
            else -> null
        }
    }

    private fun extractUrls(texts: List<String>): List<String> {
        val urlRegex = Regex("""(?i)\b((?:https?://|www\.)\S+|bit\.ly/\S+)\b""")
        return texts.flatMap { text ->
            urlRegex.findAll(text).map { it.value.trim() }.toList()
        }.distinct()
    }

    private fun extractTelegramSnapshot(sbn: StatusBarNotification): TelegramMessageSnapshot? {
        val extras = sbn.notification.extras ?: return null
        val titleText = extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()
        val sender = titleText

        val parts = mutableListOf<String>()

        val messages = extras.getParcelableArray(Notification.EXTRA_MESSAGES)
        if (messages != null && messages.isNotEmpty()) {
            data class TgMsg(val text: String, val ts: Long)
            val parsedMsgs = mutableListOf<TgMsg>()
            for (msg in messages) {
                var txt = ""
                var ts = 0L
                if (msg is android.app.Notification.MessagingStyle.Message) {
                    txt = msg.text?.toString() ?: ""
                    ts = msg.timestamp
                } else if (msg is android.os.Bundle) {
                    txt = msg.getCharSequence("text")?.toString() ?: ""
                    ts = msg.getLong("time", 0L)
                }
                if (txt.isNotBlank()) {
                    parsedMsgs.add(TgMsg(txt.trim(), ts))
                }
            }
            if (parsedMsgs.isNotEmpty()) {
                val maxTs = parsedMsgs.maxOf { it.ts }
                val latestBurst = parsedMsgs.filter { it.ts >= maxTs - 1500L }
                parts.addAll(latestBurst.map { it.text })
            }
        }

        if (parts.isEmpty()) {
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.let { parts.add(it) }
            extras.getCharSequence(Notification.EXTRA_BIG_TEXT)?.toString()?.let { parts.add(it) }
            extras.getCharSequence(Notification.EXTRA_SUMMARY_TEXT)?.toString()?.let { parts.add(it) }
            val lines = extras.getCharSequenceArray(Notification.EXTRA_TEXT_LINES)
            if (lines != null && lines.isNotEmpty()) {
                parts.addAll(lines.mapNotNull { it?.toString() })
            }
        }

        val cleanedParts = parts.map { text ->
            var clean = text
            if (!sender.isNullOrBlank()) {
                if (clean.startsWith("$sender: ")) clean = clean.substringAfter("$sender: ")
                if (clean.startsWith("$sender - ")) clean = clean.substringAfter("$sender - ")
            }
            clean.removePrefix("📎").removePrefix("Файл").trim()
        }

        val normalized = normalizeParts(cleanedParts)
        val fileName = detectVisibleFileName(normalized)
        val fileType = detectFileType(fileName)
        val urls = extractUrls(normalized)

        val nonFileUrlsParts = normalized.filter { it != fileName && !urls.contains(it) }
        val captionText = if (nonFileUrlsParts.isNotEmpty()) nonFileUrlsParts.joinToString("\n") else null

        val composite = normalized.joinToString("\n")

        Log.d(TAG, "TG raw notification received. Snapshot built: File=\$fileName, UrlsCount=\${urls.size}, Caption=\${captionText != null}")

        return TelegramMessageSnapshot(
            packageName = sbn.packageName,
            senderName = sender,
            title = sender,
            postTime = sbn.postTime.takeIf { it > 0 } ?: System.currentTimeMillis(),
            notificationKey = sbn.key,
            groupKey = sbn.groupKey,
            normalizedTextParts = normalized,
            captionText = captionText,
            detectedFileName = fileName,
            detectedFileType = fileType,
            detectedUrls = urls,
            rawCompositeText = composite
        )
    }

    private fun normalizedComparableText(snapshot: TelegramMessageSnapshot): String {
        return buildList {
            snapshot.captionText?.let { add(it.lowercase()) }
            snapshot.normalizedTextParts.forEach { add(it.lowercase()) }
            snapshot.detectedFileName?.let { add(it.lowercase()) }
            snapshot.detectedUrls.forEach { add(it.lowercase()) }
        }.joinToString(" | ")
    }

    private fun isSameLogicalMessage(
        old: TelegramMessageSnapshot,
        new: TelegramMessageSnapshot
    ): Boolean {
        val sameSender = old.senderName == new.senderName
        val sameTitle = old.title == new.title
        val closeTime = kotlin.math.abs(old.postTime - new.postTime) <= 3500L

        val oldFile = old.detectedFileName?.lowercase()
        val newFile = new.detectedFileName?.lowercase()
        val sameFile = !oldFile.isNullOrBlank() && oldFile == newFile

        val oldText = normalizedComparableText(old)
        val newText = normalizedComparableText(new)

        val containsRelation =
            oldText.isNotBlank() && newText.isNotBlank() &&
            (oldText.contains(newText) || newText.contains(oldText))

        val sameNotification = !old.notificationKey.isNullOrBlank() &&
            old.notificationKey == new.notificationKey

        return when {
            sameNotification && closeTime -> true
            sameSender && sameTitle && sameFile && closeTime -> true
            sameSender && sameTitle && containsRelation && closeTime -> true
            else -> false
        }
    }

    private fun mergeSameMessageSnapshots(
        old: TelegramMessageSnapshot,
        new: TelegramMessageSnapshot
    ): TelegramMessageSnapshot {
        val mergedTextParts = buildList {
            old.captionText?.takeIf { it.isNotBlank() }?.let { add(it) }
            old.normalizedTextParts.forEach { add(it) }
            new.captionText?.takeIf { it.isNotBlank() }?.let { add(it) }
            new.normalizedTextParts.forEach { add(it) }
        }.map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val mergedUrls = (old.detectedUrls + new.detectedUrls)
            .map { it.trim() }
            .filter { it.isNotBlank() }
            .distinct()

        val mergedCaption = listOfNotNull(
            new.captionText?.takeIf { it.isNotBlank() },
            old.captionText?.takeIf { it.isNotBlank() }
        ).firstOrNull()

        val mergedFileName = new.detectedFileName ?: old.detectedFileName
        val mergedFileType = new.detectedFileType ?: old.detectedFileType

        val composite = buildList {
            mergedCaption?.let { add(it) }
            mergedTextParts.forEach { if (it != mergedCaption) add(it) }
            mergedFileName?.let { add(it) }
            mergedUrls.forEach { add(it) }
        }.distinct().joinToString("\n").trim()

        return TelegramMessageSnapshot(
            packageName = new.packageName.ifBlank { old.packageName },
            senderName = new.senderName ?: old.senderName,
            title = new.title ?: old.title,
            postTime = maxOf(old.postTime, new.postTime),
            notificationKey = new.notificationKey ?: old.notificationKey,
            groupKey = new.groupKey ?: old.groupKey,
            normalizedTextParts = mergedTextParts,
            captionText = mergedCaption,
            detectedFileName = mergedFileName,
            detectedFileType = mergedFileType,
            detectedUrls = mergedUrls,
            rawCompositeText = composite
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        job.cancel()
    }
}
