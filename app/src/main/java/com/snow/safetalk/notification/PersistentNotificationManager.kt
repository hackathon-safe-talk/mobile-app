package com.snow.safetalk.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.SharedPreferences
import android.os.Build
import android.util.Log
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snow.safetalk.MainActivity
import com.snow.safetalk.R
import com.snow.safetalk.analysis.AnalysisConstants
import java.util.concurrent.ConcurrentHashMap

/**
 * Persistent Warning Notification Manager for SUSPICIOUS-level messages only (risk 40–69).
 *
 * KEY BEHAVIORS:
 * ─────────────
 * • Notification is NON-DISMISSIBLE (setOngoing = true, setAutoCancel = false)
 * • Notification persists until ResultScreen is ACTUALLY DISPLAYED to the user
 * • If the user ignores it, the notification stays INDEFINITELY
 * • Each analysisId produces a unique notificationId — no duplicates, supports multiple
 * • Uses IMPORTANCE_HIGH channel for heads-up display + sound/vibration
 * • Active notifications are persisted to SharedPreferences to survive process death & reboot
 *
 * MIUI / XIAOMI HARDENING:
 * ────────────────────────
 * • Does NOT use startForeground() — avoids FGS quota limits and OEM restrictions
 * • Uses CATEGORY_SERVICE to appear as a service notification (OEM-friendly)
 * • setForegroundServiceBehavior(FOREGROUND_SERVICE_IMMEDIATE) for instant display
 * • Channel created early in Application.onCreate() to survive MIUI channel caching
 *
 * DISMISSAL CONTRACT:
 * ───────────────────
 * • Notification is NEVER dismissed in MainActivity (onCreate/onNewIntent)
 * • Notification is ONLY dismissed when ResultScreen composable RENDERS
 * • Dismiss is called exactly ONCE per analysisId (internal guard)
 *
 * REBOOT RESTORE:
 * ──────────────
 * • Active analysisIds are stored in SharedPreferences
 * • On BOOT_COMPLETED, BootReceiver calls restoreAllAfterReboot()
 * • All pending persistent notifications are re-posted
 *
 * THREAD SAFETY:
 * ──────────────
 * All public methods are safe to call from any thread. ConcurrentHashMap +
 * synchronized SharedPreferences access ensures correctness.
 */
object PersistentNotificationManager {

    private const val TAG = "SafeTalk-PersistentNotif"

    // ── Channel Configuration ───────────────────────────────────────────────
    const val CHANNEL_ID = "safetalk_suspicious_persistent"
    private const val CHANNEL_NAME = "SafeTalk – Shubhali xabar ogohlantirishlari"
    private const val CHANNEL_DESC = "O'chirib bo'lmaydigan doimiy ogohlantirish – shubhali xabarlar uchun"

    // ── Notification ID namespace ───────────────────────────────────────────
    private const val NOTIFICATION_ID_OFFSET = 50_000

    // ── SharedPreferences keys ──────────────────────────────────────────────
    private const val PREFS_NAME = "safetalk_persistent_notifications"
    private const val KEY_ACTIVE_IDS = "active_analysis_ids"
    // Per-notification metadata stored as: "meta_{analysisId}_title", "meta_{analysisId}_message", etc.
    private const val META_PREFIX = "meta_"

    // ── In-memory state ─────────────────────────────────────────────────────
    // Maps analysisId → notificationId. Also serves as dismiss-once guard.
    private val activeNotifications = ConcurrentHashMap<String, Int>()

    // Tracks IDs that have already been dismissed this session to prevent
    // double-dismiss from concurrent callers (dismiss-once guarantee).
    private val dismissedThisSession = ConcurrentHashMap.newKeySet<String>()

    // ── Spam and Limit Protection ───────────────────────────────────────────
    private var lastShownAt = 0L
    private const val MAX_ACTIVE = 3

    // ═══════════════════════════════════════════════════════════════════════
    //  CHANNEL
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Creates the dedicated persistent notification channel.
     * MUST be called during Application.onCreate(). Idempotent.
     */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            CHANNEL_NAME,
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = CHANNEL_DESC
            enableLights(true)
            enableVibration(true)
            lockscreenVisibility = android.app.Notification.VISIBILITY_PUBLIC
        }

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.createNotificationChannel(channel)
        Log.d(TAG, "Persistent suspicious channel created/ensured: $CHANNEL_ID")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  SHOW
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Show a persistent, non-dismissible notification for a SUSPICIOUS message.
     *
     * Enforces the SUSPICIOUS band guard internally (40–69). Calling with
     * a riskScore outside this range is a safe no-op.
     *
     * @param context       Application or service context
     * @param analysisId    Unique UUID for this analysis result
     * @param title         Notification title
     * @param message       Notification body text
     * @param riskScore     Risk score (must be 40–69)
     * @param senderName    Optional sender name for richer text
     */
    fun showPersistentSuspiciousNotification(
        context: Context,
        analysisId: String,
        title: String,
        message: String,
        riskScore: Int,
        senderName: String? = null,
        isRestore: Boolean = false
    ) {
        // ── STRICT GUARD: Only SUSPICIOUS band (40–69) ──────────────────
        if (riskScore < AnalysisConstants.SUSPICIOUS_MIN || riskScore >= AnalysisConstants.DANGEROUS_MIN) {
            Log.d(TAG, "Skipping: riskScore=$riskScore outside SUSPICIOUS band (40–69)")
            return
        }

        if (!isRestore) {
            val now = System.currentTimeMillis()
            if (now - lastShownAt < 3000) {
                Log.d(TAG, "Skipping: rate limit exceeded")
                return
            }
            lastShownAt = now

            if (activeNotifications.size >= MAX_ACTIVE) {
                Log.d(TAG, "Skipping: max active notifications ($MAX_ACTIVE) reached")
                return
            }
        }

        // ── DUPLICATE GUARD (in-memory + persistent) ────────────────────
        if (activeNotifications.containsKey(analysisId)) {
            Log.d(TAG, "Skipping duplicate for analysisId=$analysisId")
            return
        }
        if (!isRestore && getPersistedIds(context).contains(analysisId)) {
            Log.d(TAG, "Skipping duplicate (persisted) for analysisId=$analysisId")
            return
        }

        // ── PERMISSION CHECK ────────────────────────────────────────────
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val granted = context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
            if (!granted) {
                Log.w(TAG, "POST_NOTIFICATIONS not granted, cannot show persistent notification")
                android.os.Handler(android.os.Looper.getMainLooper()).post {
                    val fallbackMsg = if (senderName.isNullOrBlank()) "Shubhali xabar aniqlandi! Tekshiruv natijalarini ko'ring." else "Shubhali $senderName xabari aniqlandi! Tekshiruv natijalarini ko'ring."
                    android.widget.Toast.makeText(context, fallbackMsg, android.widget.Toast.LENGTH_LONG).show()
                }
                return
            }
        }

        // ── Ensure channel exists ───────────────────────────────────────
        createChannel(context)

        // ── Build deep-link PendingIntent ───────────────────────────────
        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or
                    Intent.FLAG_ACTIVITY_CLEAR_TOP or
                    Intent.FLAG_ACTIVITY_SINGLE_TOP
            putExtra("analysis_id", analysisId)
            putExtra("from_persistent_notification", true)
        }

        val notificationId = computeNotificationId(analysisId)

        val pendingIntent = PendingIntent.getActivity(
            context,
            notificationId,
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // ── Build notification body ─────────────────────────────────────
        val bodyText = buildString {
            if (!senderName.isNullOrBlank()) {
                append("Manba: $senderName\n")
            }
            append("Xavf darajasi: $riskScore%\n")
            append(message)
        }

        // ── Build the persistent notification ───────────────────────────
        val builder = NotificationCompat.Builder(context, CHANNEL_ID)
            // ── Core persistence flags ──────────────────────────────────
            .setOngoing(true)
            .setAutoCancel(false)
            // ── OEM Hardening ───────────────────────────────────────────
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            // ── Visual ──────────────────────────────────────────────────
            .setSmallIcon(R.drawable.ic_shield_protection)
            .setContentTitle(title)
            .setContentText(message)
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(bodyText)
                    .setBigContentTitle(title)
            )
            // ── Color accent (warning amber) ────────────────────────────
            .setColor(0xFFFFA726.toInt())
            .setColorized(true)
            // ── Interaction ─────────────────────────────────────────────
            .setContentIntent(pendingIntent)
            // ── Action Button: "Ko'rish" (View Analysis) ────────────────
            .addAction(
                R.drawable.ic_shield_protection,
                "Tahlilni ko'rish",
                pendingIntent
            )
            // ── Visibility ──────────────────────────────────────────────
            .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)
            // ── Timestamp ───────────────────────────────────────────────
            .setWhen(System.currentTimeMillis())
            .setShowWhen(true)
            // ── Prevent repeated sound/vibration on updates ─────────────
            .setOnlyAlertOnce(true)

        val notification = builder.build()

        // ── Post notification ───────────────────────────────────────────
        try {
            NotificationManagerCompat.from(context).notify(notificationId, notification)
            activeNotifications[analysisId] = notificationId
            // Reset dismissed state (in case re-shown after dismiss)
            dismissedThisSession.remove(analysisId)
            // Persist to SharedPreferences for reboot restore
            persistNotificationData(context, analysisId, title, message, riskScore, senderName)
            Log.d(TAG, "✅ Posted persistent notification: id=$notificationId analysisId=$analysisId risk=$riskScore")
        } catch (e: SecurityException) {
            Log.e(TAG, "SecurityException posting notification: ${e.message}")
        }
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  DISMISS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Dismiss a persistent suspicious notification by analysisId.
     *
     * MUST ONLY be called from ResultScreen composable when it is ACTUALLY RENDERED.
     * DO NOT call from MainActivity.onCreate() or onNewIntent().
     *
     * Dismiss-once guard: calling multiple times for the same analysisId within
     * a session is safe — only the first call actually cancels.
     *
     * @param context       Application context
     * @param analysisId    The analysis ID to dismiss. If null, this is a no-op.
     */
    fun dismissNotification(context: Context, analysisId: String?) {
        if (analysisId.isNullOrBlank()) {
            Log.d(TAG, "dismissNotification: analysisId is null/blank, no-op")
            return
        }

        // ── DISMISS-ONCE GUARD ──────────────────────────────────────────
        if (!dismissedThisSession.add(analysisId)) {
            Log.d(TAG, "dismissNotification: already dismissed this session, no-op for $analysisId")
            return
        }

        val notificationId = activeNotifications.remove(analysisId)
            ?: computeNotificationId(analysisId)

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        nm.cancel(notificationId)

        // Remove from persistent storage
        removePersistedNotification(context, analysisId)

        Log.d(TAG, "🗑️ Dismissed persistent notification: id=$notificationId analysisId=$analysisId")
    }

    /**
     * Dismiss ALL active persistent suspicious notifications.
     */
    fun dismissAll(context: Context) {
        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val ids = activeNotifications.values.toList()
        for (id in ids) {
            nm.cancel(id)
        }
        val keys = activeNotifications.keys.toList()
        activeNotifications.clear()
        keys.forEach { dismissedThisSession.add(it) }

        // Clear all persisted data
        clearAllPersistedData(context)
        Log.d(TAG, "🗑️ Dismissed all ${ids.size} persistent notifications")
    }

    /**
     * Check whether a persistent notification is active (in-memory or persisted).
     */
    fun isNotificationActive(context: Context, analysisId: String): Boolean {
        return activeNotifications.containsKey(analysisId) ||
               getPersistedIds(context).contains(analysisId)
    }

    /**
     * Returns count of currently tracked active persistent notifications.
     */
    fun activeCount(): Int = activeNotifications.size

    // ═══════════════════════════════════════════════════════════════════════
    //  REBOOT RESTORE
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Restore all active persistent notifications after device reboot.
     *
     * Called from BootReceiver on BOOT_COMPLETED. Re-reads persisted metadata
     * from SharedPreferences and re-posts each notification.
     *
     * Safe to call multiple times — duplicate guard will skip already-active IDs.
     */
    fun restoreAllAfterReboot(context: Context) {
        val ids = getPersistedIds(context)
        if (ids.isEmpty()) {
            Log.d(TAG, "restoreAllAfterReboot: no persisted notifications to restore")
            return
        }

        Log.d(TAG, "restoreAllAfterReboot: restoring ${ids.size} notifications")
        val prefs = getPrefs(context)

        for (analysisId in ids) {
            val title = prefs.getString("${META_PREFIX}${analysisId}_title", null)
                ?: "⚠ Shubhali xabar aniqlandi"
            val message = prefs.getString("${META_PREFIX}${analysisId}_message", null)
                ?: "Xabarni tekshirish uchun bosing"
            val riskScore = prefs.getInt("${META_PREFIX}${analysisId}_risk", 50)
            val senderName = prefs.getString("${META_PREFIX}${analysisId}_sender", null)

            // Re-check risk band (data integrity)
            if (riskScore < AnalysisConstants.SUSPICIOUS_MIN || riskScore >= AnalysisConstants.DANGEROUS_MIN) {
                Log.w(TAG, "restoreAllAfterReboot: skipping $analysisId with invalid risk=$riskScore")
                removePersistedNotification(context, analysisId)
                continue
            }

            // Clear the in-memory duplicate guard so re-post is allowed
            activeNotifications.remove(analysisId)
            dismissedThisSession.remove(analysisId)

            showPersistentSuspiciousNotification(
                context = context,
                analysisId = analysisId,
                title = title,
                message = message,
                riskScore = riskScore,
                senderName = senderName,
                isRestore = true
            )
        }
    }

    /**
     * Returns the set of all persisted active analysisIds.
     * Used by BootReceiver and internal duplicate checks.
     */
    fun getPersistedIds(context: Context): Set<String> {
        return getPrefs(context).getStringSet(KEY_ACTIVE_IDS, emptySet()) ?: emptySet()
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PRIVATE: PERSISTENCE
    // ═══════════════════════════════════════════════════════════════════════

    private fun getPrefs(context: Context): SharedPreferences {
        return context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    /**
     * Persist notification data for reboot restore.
     * Stores the analysisId in the active set + metadata for re-creation.
     */
    private fun persistNotificationData(
        context: Context,
        analysisId: String,
        title: String,
        message: String,
        riskScore: Int,
        senderName: String?
    ) {
        val prefs = getPrefs(context)
        synchronized(this) {
            val currentIds = prefs.getStringSet(KEY_ACTIVE_IDS, emptySet())?.toMutableSet()
                ?: mutableSetOf()
            currentIds.add(analysisId)

            prefs.edit()
                .putStringSet(KEY_ACTIVE_IDS, currentIds)
                .putString("${META_PREFIX}${analysisId}_title", title)
                .putString("${META_PREFIX}${analysisId}_message", message)
                .putInt("${META_PREFIX}${analysisId}_risk", riskScore)
                .apply {
                    if (senderName != null) {
                        putString("${META_PREFIX}${analysisId}_sender", senderName)
                    } else {
                        remove("${META_PREFIX}${analysisId}_sender")
                    }
                }
                .apply()
        }
        Log.d(TAG, "Persisted notification data for $analysisId")
    }

    /**
     * Remove a single notification's persisted data after dismissal.
     */
    private fun removePersistedNotification(context: Context, analysisId: String) {
        val prefs = getPrefs(context)
        synchronized(this) {
            val currentIds = prefs.getStringSet(KEY_ACTIVE_IDS, emptySet())?.toMutableSet()
                ?: mutableSetOf()
            if (currentIds.remove(analysisId)) {
                prefs.edit()
                    .putStringSet(KEY_ACTIVE_IDS, currentIds)
                    .remove("${META_PREFIX}${analysisId}_title")
                    .remove("${META_PREFIX}${analysisId}_message")
                    .remove("${META_PREFIX}${analysisId}_risk")
                    .remove("${META_PREFIX}${analysisId}_sender")
                    .apply()
            }
        }
        Log.d(TAG, "Removed persisted data for $analysisId")
    }

    /**
     * Clear all persisted notification data (used by dismissAll).
     */
    private fun clearAllPersistedData(context: Context) {
        synchronized(this) {
            getPrefs(context).edit().clear().apply()
        }
        Log.d(TAG, "Cleared all persisted notification data")
    }

    // ═══════════════════════════════════════════════════════════════════════
    //  PRIVATE: HELPERS
    // ═══════════════════════════════════════════════════════════════════════

    /**
     * Compute a deterministic notification ID from an analysisId.
     * Same analysisId always yields the same ID — enables dismissal after process restart.
     */
    private fun computeNotificationId(analysisId: String): Int {
        return NOTIFICATION_ID_OFFSET + (analysisId.hashCode() and 0x7FFFFFFF) % 40_000
    }
}
