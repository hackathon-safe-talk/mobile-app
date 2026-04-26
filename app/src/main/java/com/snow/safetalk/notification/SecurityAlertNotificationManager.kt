package com.snow.safetalk.notification

import android.app.Notification
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import android.util.Log
import androidx.annotation.GuardedBy
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snow.safetalk.MainActivity

/**
 * Production-grade, unified security alert notification manager for SafeTalk.
 *
 * Implements a **hybrid alert strategy** that guarantees DANGEROUS threats (≥70%)
 * ALWAYS produce a full-screen alert, regardless of app state:
 *
 *  **Path A — App in FOREGROUND:**
 *    Android intentionally suppresses `setFullScreenIntent()` when the app is
 *    visible. To bypass this, we detect foreground state via [AppForegroundObserver]
 *    and directly call `startActivity(SecurityAlertActivity)`. This is guaranteed
 *    to display immediately.
 *
 *  **Path B — App in BACKGROUND / screen locked:**
 *    Uses `setFullScreenIntent()` on the notification, targeting
 *    [SecurityAlertActivity] (which has `showWhenLocked` + `turnScreenOn`).
 *    The system displays it over the lock screen.
 *
 *  In BOTH paths, a persistent non-dismissible notification is ALSO posted.
 *  The notification can only be cleared via [cancelForAnalysis] from ResultScreen.
 *
 * Additional protections:
 *  - Deduplication: same analysisId never triggers twice
 *  - Rate-limiting: max 1 full-screen alert per 10s, max 10 notifications per minute
 *  - Android 14+ `canUseFullScreenIntent()` runtime check with fallback
 *  - OEM hardening: FLAG_NO_CLEAR + FLAG_ONGOING_EVENT applied at raw Notification level
 *  - Persistent ID registry via [NotificationIdRegistry] (survives process death)
 *
 * Thread-safety: All public methods are synchronized.
 */
object SecurityAlertNotificationManager {

    private const val TAG = "SecAlertNotifMgr"

    // ── Channel IDs (must match NotificationPermissionHelper definitions) ─────
    private const val CHANNEL_SMS = "safetalk_sms_alerts"
    private const val CHANNEL_TELEGRAM = "safetalk_alerts"

    // ── Full-screen / direct-launch rate limiter ──────────────────────────────
    private const val FULL_SCREEN_COOLDOWN_MS = 10_000L // 10 seconds
    @GuardedBy("this")
    @Volatile
    private var lastFullScreenTimestamp: Long = 0L

    // ── Anti-loop: max notifications in a short burst ─────────────────────────
    private const val BURST_WINDOW_MS = 60_000L // 1 minute
    private const val MAX_BURST_COUNT = 10
    @GuardedBy("this")
    private val recentPostTimestamps = mutableListOf<Long>()

    /**
     * Message source enum — determines notification channel and content text.
     */
    enum class SecurityAlertSource {
        SMS,
        TELEGRAM
    }

    // ── Public: Post a security alert ─────────────────────────────────────────

    /**
     * Post a persistent, non-dismissible security notification.
     *
     * For DANGEROUS threats (≥70%), additionally triggers a full-screen alert via
     * the hybrid dispatch strategy (direct Activity launch if foreground,
     * setFullScreenIntent if background).
     *
     * @param context     Application or service context.
     * @param analysisId  Unique UUID string for this analysis.
     * @param riskScore   Risk percentage (0–100). Only scores ≥ 40 trigger a notification.
     * @param source      [SecurityAlertSource.SMS] or [SecurityAlertSource.TELEGRAM].
     * @param senderTitle Optional sender name for display (Telegram messages).
     */
    @Synchronized
    fun showSecurityAlert(
        context: Context,
        analysisId: String,
        riskScore: Int,
        source: SecurityAlertSource,
        senderTitle: String? = null
    ) {
        // ── Gate: only SUSPICIOUS (≥40) or DANGEROUS (≥70) ────────────────
        if (riskScore < 40) {
            Log.d(TAG, "showSecurityAlert() skipped: riskScore=$riskScore < 40")
            return
        }

        // ── Deduplication: do NOT re-post if this analysisId is already active ─
        if (NotificationIdRegistry.isRegistered(context, analysisId)) {
            Log.d(TAG, "showSecurityAlert() skipped: duplicate analysisId=$analysisId")
            return
        }

        // ── Anti-loop: burst protection ───────────────────────────────────────
        val now = System.currentTimeMillis()
        recentPostTimestamps.removeAll { (now - it) > BURST_WINDOW_MS }
        if (recentPostTimestamps.size >= MAX_BURST_COUNT) {
            Log.w(TAG, "showSecurityAlert() BLOCKED by burst limiter (${recentPostTimestamps.size} in ${BURST_WINDOW_MS}ms)")
            return
        }
        recentPostTimestamps.add(now)

        // ── Permission check (Android 13+) ────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "showSecurityAlert() skipped: POST_NOTIFICATIONS not granted")
                return
            }
        }

        // ── Register in persistent registry ───────────────────────────────────
        val notificationId = NotificationIdRegistry.register(context, analysisId)

        // ── Determine tier and channel ────────────────────────────────────────
        val isDangerous = riskScore >= 70
        val channelId = when (source) {
            SecurityAlertSource.SMS -> CHANNEL_SMS
            SecurityAlertSource.TELEGRAM -> CHANNEL_TELEGRAM
        }

        // ── Ensure notification channels exist (idempotent) ───────────────────
        // SecurityAlertNotificationManager is called directly — the old helpers
        // that previously created channels are no longer invoked for DANGEROUS.
        com.snow.safetalk.sms.NotificationHelper.createChannel(context)
        com.snow.safetalk.telegram.TelegramNotificationHelper.createChannel(context)

        // ── Read user setting: is full-screen alert enabled? ──────────────────
        // Synchronous SharedPreferences read — safe from any thread/service context.
        val fullScreenSettingEnabled =
            com.snow.safetalk.settings.SettingsDataStore(context).isFullScreenAlertEnabledSync()

        // ── Build deep-link PendingIntent (for notification tap) ──────────────
        // from_safetalk_internal=true is required: MainActivity silently discards
        // analysis_id from untrusted callers (see MainActivity.onCreate trust check).
        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("analysis_id", analysisId)
            putExtra("from_safetalk_internal", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,   // unique per-notification request code
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Build notification content ────────────────────────────────────────
        val (title, text, bigText) = buildNotificationContent(
            riskScore = riskScore,
            isDangerous = isDangerous,
            source = source,
            senderTitle = senderTitle
        )

        val builder = NotificationCompat.Builder(context, channelId)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setContentIntent(pendingIntent)
            // ── NON-DISMISSIBLE CORE ──────────────────────────────────────
            .setOngoing(true)
            .setAutoCancel(false)
            // ── MAX PRIORITY / FULL-SCREEN CAPABLE ─────────────────────────
            .setPriority(NotificationCompat.PRIORITY_MAX)
            .setCategory(NotificationCompat.CATEGORY_CALL)
            // ── Vibration: stronger pattern for DANGEROUS, standard for others ─
            .setVibrate(
                if (isDangerous) longArrayOf(0, 500, 200, 500, 200, 500)
                else longArrayOf(0, 400, 200, 400)
            )
            // ── Sound + lights + vibration fallback (belt-and-suspenders) ──
            .setDefaults(Notification.DEFAULT_SOUND or Notification.DEFAULT_LIGHTS)
            // ── Prevent user from removing via notification shade clear-all ─
            .setDeleteIntent(null)
            // ── Lock-screen visibility ────────────────────────────────────
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // ── Timestamp ─────────────────────────────────────────────────
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)

        // ── DUAL DISPATCH FULL-SCREEN ALERT (DANGEROUS ONLY, SETTING GATE) ──────
        //
        //  User setting "To'liq ekran ogohlantirish" (full_screen_alert_enabled_sync)
        //  MUST be ON for full-screen dispatch. When OFF, the persistent notification
        //  is still posted (below) so the user sees the alert — just no pop-up overlay.
        //
        //  DUAL STRATEGY — fires BOTH mechanisms simultaneously:
        //   1. setFullScreenIntent() → guarantees launch when screen OFF or LOCKED.
        //   2. startActivity(SecurityAlertActivity) directly → guarantees launch when
        //      screen is ON and unlocked (Android suppresses setFullScreenIntent to
        //      heads-up in that case).
        //
        //  Rate-limiter (10s cooldown) and deduplication are preserved.
        //
        if (isDangerous && fullScreenSettingEnabled) {
            val withinCooldown = (now - lastFullScreenTimestamp) < FULL_SCREEN_COOLDOWN_MS

            if (!withinCooldown) {
                val isForeground = AppForegroundObserver.isAppInForeground
                val popupPermEnabled = BackgroundPopupPermissionHelper.isPermissionEnabled(context)
                Log.d(TAG, "DISPATCH: dual strategy START — analysisId=$analysisId, " +
                    "risk=$riskScore, isForeground=$isForeground, " +
                    "miuiPopupPerm=$popupPermEnabled, settingEnabled=true")

                // ── STEP 1: Arm setFullScreenIntent (screen OFF / locked path) ──
                val armed = armFullScreenIntent(context, builder, analysisId, riskScore, notificationId)
                Log.d(TAG, "DISPATCH STEP 1: setFullScreenIntent armed=$armed")

                // ── STEP 2: Direct Activity launch (screen ON / unlocked path) ──
                // NLS is a system-bound service with background activity start rights
                // on most devices. On MIUI: requires "Display pop-up windows" permission.
                try {
                    launchSecurityAlertDirectly(context, analysisId, riskScore)
                    Log.d(TAG, "DISPATCH STEP 2: startActivity SUCCESS")
                } catch (e: Exception) {
                    Log.e(TAG, "DISPATCH STEP 2: startActivity FAILED: ${e.message}", e)
                }

                // ── STEP 3: Wake screen (belt-and-suspenders for screen-off) ────
                wakeScreen(context)

                lastFullScreenTimestamp = now
                Log.d(TAG, "DISPATCH: dual strategy COMPLETE")
            } else {
                Log.d(TAG, "Full-screen alert RATE-LIMITED for analysisId=$analysisId " +
                    "(cooldown: ${FULL_SCREEN_COOLDOWN_MS - (now - lastFullScreenTimestamp)}ms remaining)")
            }
        } else if (isDangerous && !fullScreenSettingEnabled) {
            Log.d(TAG, "Full-screen alert SUPPRESSED by user setting for analysisId=$analysisId — " +
                "persistent notification will still be posted.")
        }

        // ── Build and apply FLAG_NO_CLEAR at Notification level ───────────────
        val notification = builder.build().apply {
            // Belt-and-suspenders: force FLAG_NO_CLEAR + FLAG_ONGOING_EVENT at the
            // raw Notification level. Some OEMs (MIUI, ColorOS) respect these flags
            // even when they override NotificationCompat behavior.
            flags = flags or Notification.FLAG_NO_CLEAR or Notification.FLAG_ONGOING_EVENT
        }

        // ── Post the persistent notification (ALWAYS, regardless of path) ─────
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            Log.d(TAG, "Posted security alert: analysisId=$analysisId, notifId=$notificationId, " +
                "risk=$riskScore, source=$source, dangerous=$isDangerous")
        } catch (e: SecurityException) {
            // POST_NOTIFICATIONS revoked between check and post — benign on Android 13+
            Log.w(TAG, "SecurityException posting notification: ${e.message}")
            NotificationIdRegistry.unregister(context, analysisId)
        }
    }

    // ── PATH A: Direct Activity launch for foreground bypass ──────────────────

    /**
     * Directly start [SecurityAlertActivity] when the app is in the foreground.
     *
     * This bypasses Android's suppression of `setFullScreenIntent()` for
     * foreground apps. The SecurityAlertActivity renders a full-screen danger
     * overlay that the user must interact with.
     *
     * Uses FLAG_ACTIVITY_NEW_TASK + FLAG_ACTIVITY_CLEAR_TOP to ensure only one
     * instance of the alert Activity exists at a time (prevents stacking).
     */
    private fun launchSecurityAlertDirectly(
        context: Context,
        analysisId: String,
        riskScore: Int
    ) {
        try {
            val intent = Intent(context, SecurityAlertActivity::class.java).apply {
                flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP or
                    Intent.FLAG_ACTIVITY_NO_USER_ACTION or
                    Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                putExtra("analysis_id", analysisId)
                putExtra("risk_score", riskScore)
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            // Extremely unlikely but guard against OEM restrictions on
            // startActivity from background (some MIUI versions)
            Log.e(TAG, "Direct launch FAILED: ${e.message}")
        }
    }

    // ── PATH B: Full-screen intent for background/locked ─────────────────────

    /**
     * Attach a full-screen intent to the notification builder.
     *
     * Returns true if the intent was armed, false if the system denied the
     * permission (Android 14+: USE_FULL_SCREEN_INTENT is a special permission
     * that may not be granted for non-phone/alarm apps).
     */
    private fun armFullScreenIntent(
        context: Context,
        builder: NotificationCompat.Builder,
        analysisId: String,
        riskScore: Int,
        notificationId: Int
    ): Boolean {
        // Android 14+ runtime check
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            if (!nm.canUseFullScreenIntent()) {
                Log.w(TAG, "USE_FULL_SCREEN_INTENT DENIED by system. " +
                    "Full-screen will degrade to heads-up. " +
                    "User must grant via Settings > Apps > SafeTalk > Notifications.")
                return false
            }
        }

        // Build a SEPARATE PendingIntent targeting SecurityAlertActivity
        // (NOT the same contentIntent targeting MainActivity)
        val fullScreenIntent = Intent(context, SecurityAlertActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                Intent.FLAG_ACTIVITY_NO_USER_ACTION
            putExtra("analysis_id", analysisId)
            putExtra("risk_score", riskScore)
        }

        val fullScreenPendingIntent = PendingIntent.getActivity(
            context,
            notificationId + 100_000, // offset to avoid collision with contentIntent
            fullScreenIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        builder.setFullScreenIntent(fullScreenPendingIntent, true)
        return true
    }

    // ── Public: Cancel a specific notification (ResultScreen gate) ─────────────

    /**
     * Cancel the persistent notification tied to [analysisId].
     *
     * This is the **ONLY** sanctioned way to remove a security alert notification.
     * Must be called from ResultScreen's lifecycle-aware trigger (LaunchedEffect /
     * DisposableEffect after successful render).
     *
     * Safe to call even if no notification exists for the given ID.
     */
    @Synchronized
    fun cancelForAnalysis(context: Context, analysisId: String) {
        val notificationId = NotificationIdRegistry.getNotificationId(context, analysisId)
        if (notificationId != null) {
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.cancel(notificationId)
            NotificationIdRegistry.unregister(context, analysisId)
            Log.d(TAG, "cancelForAnalysis() CLEARED: analysisId=$analysisId, notifId=$notificationId")
        } else {
            Log.d(TAG, "cancelForAnalysis() no-op: analysisId=$analysisId not in registry")
        }
    }

    // ── Public: Cancel ALL security alerts (emergency / full reset) ────────────

    /**
     * Cancel every active security alert notification. Use sparingly — this is a
     * nuclear option for edge cases like user logout, app data wipe, or debug.
     */
    @Synchronized
    fun cancelAllSecurityAlerts(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val allIds = NotificationIdRegistry.getAllActiveIds(context)
        for ((analysisId, notifId) in allIds) {
            nm.cancel(notifId)
            Log.d(TAG, "cancelAllSecurityAlerts() CLEARED: $analysisId → $notifId")
        }
        NotificationIdRegistry.clearAll(context)
        Log.w(TAG, "cancelAllSecurityAlerts() completed — ${allIds.size} notifications cleared")
    }

    // ── Public: Query active count ────────────────────────────────────────────

    /**
     * Returns the number of currently tracked (non-dismissed) security alerts.
     */
    fun getActiveAlertCount(context: Context): Int =
        NotificationIdRegistry.getAllActiveIds(context).size

    // ── Internal: Content builder ─────────────────────────────────────────────

    private data class NotificationContent(
        val title: String,
        val text: String,
        val bigText: String
    )

    private fun buildNotificationContent(
        riskScore: Int,
        isDangerous: Boolean,
        source: SecurityAlertSource,
        senderTitle: String?
    ): NotificationContent {
        val sourceLabel = when (source) {
            SecurityAlertSource.SMS -> "SMS"
            SecurityAlertSource.TELEGRAM -> "Telegram"
        }

        val senderLine = if (!senderTitle.isNullOrBlank()) "Manba: $senderTitle\n" else ""

        return if (isDangerous) {
            NotificationContent(
                title = "🔴 Xavfli $sourceLabel xabar aniqlandi",
                text = "Xavf: $riskScore% — O'chirib tashlash mumkin EMAS. Tekshiring!",
                bigText = "${senderLine}Xavf darajasi: $riskScore%\n" +
                    "Bu xabar jiddiy xavf tug'diradi.\n" +
                    "Batafsil ko'rish uchun bosing — ogohlantirish faqat tekshirgandan keyin o'chadi."
            )
        } else {
            NotificationContent(
                title = "🟡 Shubhali $sourceLabel xabar",
                text = "Xavf: $riskScore% — Tekshirish talab etiladi",
                bigText = "${senderLine}Xavf darajasi: $riskScore%\n" +
                    "Xabar shubhali belgilarga ega.\n" +
                    "Batafsil ko'rish uchun bosing — ogohlantirish faqat tekshirgandan keyin o'chadi."
            )
        }
    }

    // ── Internal: Screen wake for urgent alerts ───────────────────────────────

    /**
     * Attempt to wake the screen so the user sees the full-screen alert immediately.
     * This is best-effort — many OEMs restrict [PowerManager.ACQUIRE_CAUSES_WAKEUP].
     */
    private fun wakeScreen(context: Context) {
        try {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            if (!pm.isInteractive) {
                @Suppress("DEPRECATION")
                val wl = pm.newWakeLock(
                    PowerManager.FULL_WAKE_LOCK or
                        PowerManager.ACQUIRE_CAUSES_WAKEUP or
                        PowerManager.ON_AFTER_RELEASE,
                    "safetalk:security_alert_wake"
                )
                wl.acquire(3_000L) // 3 seconds — just enough to show heads-up
                Log.d(TAG, "wakeScreen() acquired wake lock")
            }
        } catch (e: Exception) {
            // Non-critical — OEM restrictions or missing permission
            Log.w(TAG, "wakeScreen() failed (OEM restriction?): ${e.message}")
        }
    }
}
