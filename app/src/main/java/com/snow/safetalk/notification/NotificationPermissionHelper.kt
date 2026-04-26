package com.snow.safetalk.notification

import android.Manifest
import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat

/**
 * Centralized notification permission and channel management for SafeTalk.
 *
 * Covers:
 * - Early channel creation (must happen before system settings page shows anything)
 * - Android 13+ POST_NOTIFICATIONS runtime permission checks
 * - "Permanently denied" detection via shouldShowRequestPermissionRationale
 * - NotificationManagerCompat.areNotificationsEnabled() for real system state
 * - Deep-link to system notification settings as fallback
 */
object NotificationPermissionHelper {

    // ── Channel definitions ────────────────────────────────────────────

    private data class ChannelDef(
        val id: String,
        val name: String,
        val description: String,
        val importance: Int
    )

    private val ALL_CHANNELS = listOf(
        ChannelDef(
            id = "safetalk_sms_alerts",
            name = "SafeTalk SMS Alert",
            description = "Shubhali SMS xabarlari haqida ogohlantirishlar",
            importance = NotificationManager.IMPORTANCE_HIGH
        ),
        ChannelDef(
            id = "safetalk_alerts",
            name = "SafeTalk ogohlantirishlari",
            description = "Shubhali va xavfli xabarlar haqida ogohlantirishlar",
            importance = NotificationManager.IMPORTANCE_HIGH
        ),
        ChannelDef(
            id = "safetalk_protection",
            name = "SafeTalk Protection",
            description = "Doimiy himoya rejimi bildirishnomalari",
            importance = NotificationManager.IMPORTANCE_LOW
        ),
        ChannelDef(
            id = PersistentNotificationManager.CHANNEL_ID,
            name = "SafeTalk – Shubhali xabar ogohlantirishlari",
            description = "O'chirib bo'lmaydigan doimiy ogohlantirish – shubhali xabarlar uchun",
            importance = NotificationManager.IMPORTANCE_HIGH
        )
    )

    /**
     * Create all notification channels immediately. Idempotent — safe to call
     * any number of times. Must be called in Application.onCreate() so that the
     * system notification settings page shows channels even before any
     * notification is posted.
     *
     * This is the #1 fix for the "notifications greyed-out / can't enable" issue
     * on Android 8+ and especially MIUI devices with sideloaded APKs.
     */
    fun ensureAllChannels(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

        val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        for (def in ALL_CHANNELS) {
            val channel = NotificationChannel(def.id, def.name, def.importance).apply {
                description = def.description
                if (def.importance == NotificationManager.IMPORTANCE_HIGH) {
                    enableLights(true)
                    enableVibration(true)
                } else {
                    setShowBadge(false)
                }
            }
            nm.createNotificationChannel(channel)
        }
    }

    // ── Permission queries ─────────────────────────────────────────────

    /**
     * Returns true ONLY when both conditions are met:
     * 1. POST_NOTIFICATIONS runtime permission is granted (Android 13+) or N/A (< 13)
     * 2. System-level notifications are enabled (NotificationManagerCompat)
     *
     * On MIUI, sideloaded APKs often have notifications disabled at the system
     * level even when the runtime permission hasn't been asked yet. This catches
     * that case.
     */
    fun areNotificationsFullyEnabled(context: Context): Boolean {
        val runtimeOk = isRuntimePermissionGranted(context)
        val systemOk = NotificationManagerCompat.from(context).areNotificationsEnabled()
        return runtimeOk && systemOk
    }

    /**
     * Check POST_NOTIFICATIONS runtime permission. On Android < 13, always true.
     */
    fun isRuntimePermissionGranted(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            ContextCompat.checkSelfPermission(
                context, Manifest.permission.POST_NOTIFICATIONS
            ) == PackageManager.PERMISSION_GRANTED
        } else {
            true
        }
    }

    /**
     * Detect "permanently denied" state on Android 13+.
     *
     * Logic:
     * - Permission NOT granted
     * - AND shouldShowRequestPermissionRationale returns false
     *   (which means "Don't ask again" was checked, OR first launch)
     * - AND the app has already asked at least once (tracked via SharedPreferences)
     *
     * We track "has asked" because on first launch, rationale is also false,
     * but the permission isn't permanently denied — it just hasn't been requested.
     */
    fun isPermissionPermanentlyDenied(activity: Activity): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        if (isRuntimePermissionGranted(activity)) return false

        val hasAskedBefore = activity.getSharedPreferences(
            "safetalk_notif_perm", Context.MODE_PRIVATE
        ).getBoolean("has_asked_notif_perm", false)

        if (!hasAskedBefore) return false

        return !activity.shouldShowRequestPermissionRationale(
            Manifest.permission.POST_NOTIFICATIONS
        )
    }

    /**
     * Mark that the app has requested POST_NOTIFICATIONS permission at least once.
     * Call this right before launching the permission request dialog.
     */
    fun markPermissionAsked(context: Context) {
        context.getSharedPreferences("safetalk_notif_perm", Context.MODE_PRIVATE)
            .edit()
            .putBoolean("has_asked_notif_perm", true)
            .apply()
    }

    // ── Navigation to system settings ──────────────────────────────────

    /**
     * Open the app-specific notification settings page.
     * Works on Android 8+ (API 26+). On older devices, falls back to app details.
     */
    fun openNotificationSettings(context: Context) {
        try {
            val intent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                    putExtra(Settings.EXTRA_APP_PACKAGE, context.packageName)
                }
            } else {
                Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS).apply {
                    data = Uri.fromParts("package", context.packageName, null)
                }
            }
            intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            context.startActivity(intent)
        } catch (_: Exception) {
            // Fallback for edge-case OEM launchers that intercept the intent
            openAppDetailsSettings(context)
        }
    }

    /**
     * Open general app details settings page (permissions, storage, etc.).
     */
    fun openAppDetailsSettings(context: Context) {
        try {
            val intent = Intent(
                Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                Uri.fromParts("package", context.packageName, null)
            ).apply {
                addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            }
            context.startActivity(intent)
        } catch (_: Exception) {
            // Extremely unlikely — swallow silently
        }
    }
}
