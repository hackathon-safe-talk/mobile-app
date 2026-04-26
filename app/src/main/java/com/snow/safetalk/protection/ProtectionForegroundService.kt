package com.snow.safetalk.protection

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.snow.safetalk.MainActivity

/**
 * Foreground Service that keeps SafeTalk's always-on protection mode alive.
 *
 * This service acts purely as a runtime anchor — it does NOT contain
 * analysis logic. SMS and Telegram analysis continue to work through
 * their own existing receivers/listeners independently.
 *
 * Lifecycle model:
 * - Started/stopped exclusively by [ProtectionManager] or equivalent in-app actions.
 * - Uses [START_STICKY] + manifest `stopWithTask="false"` so the service
 *   survives task removal (user swiping SafeTalk from recents).
 * - If the OS kills the process, START_STICKY requests a restart.
 * - If the process is killed via "Clear all", the service stops — this is
 *   accepted behavior by design.
 * - The [ProtectionJobService] periodic watchdog provides additional
 *   resilience against OEM-specific process killing.
 *
 * Usage:
 *   ProtectionForegroundService.start(context)
 *   ProtectionForegroundService.stop(context)
 */
class ProtectionForegroundService : Service() {

    companion object {
        private const val TAG = "SafeTalk-Protection"

        private const val ACTION_START = "com.snow.safetalk.action.START_PROTECTION"
        private const val ACTION_STOP  = "com.snow.safetalk.action.STOP_PROTECTION"

        private const val CHANNEL_ID   = "safetalk_protection"
        private const val CHANNEL_NAME = "SafeTalk Protection"
        private const val CHANNEL_DESC = "Doimiy himoya rejimi bildirishnomalari"

        private const val NOTIFICATION_ID = 9001

        /**
         * Safely starts the foreground protection service.
         * @return ServiceStarterResult representing the exact outcome. NEVER throws.
         */
        fun start(context: Context, source: String = "Unknown", policy: StarterPolicy = StarterPolicy.RESPECT_COOLDOWN): ServiceStarterResult {
            val intent = Intent(context, ProtectionForegroundService::class.java).apply {
                action = ACTION_START
            }
            return SafeServiceStarter.startSafe(context, intent, source, policy, ProtectionForegroundService::class.java)
        }

        /** Stop the foreground protection service. */
        fun stop(context: Context) {
            SafeServiceStarter.markServiceStopped()
            val intent = Intent(context, ProtectionForegroundService::class.java).apply {
                action = ACTION_STOP
            }
            context.startService(intent)
        }
    }

    /** Tracks whether the service was stopped via explicit user/app action. */
    private var stoppedByUser = false

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.w(TAG, "▶ onStartCommand action=${intent?.action} flags=$flags startId=$startId pid=${android.os.Process.myPid()}")
        when (intent?.action) {
            ACTION_START -> {
                stoppedByUser = false
                ensureNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                Log.w(TAG, "▶ startForeground called (ACTION_START)")
            }
            ACTION_STOP -> {
                stoppedByUser = true
                stopForeground(STOP_FOREGROUND_REMOVE)
                stopSelf()
                Log.w(TAG, "▶ stopSelf called (ACTION_STOP)")
            }
            else -> {
                // System restart (START_STICKY) — no action in intent
                stoppedByUser = false
                ensureNotificationChannel()
                startForeground(NOTIFICATION_ID, buildNotification())
                Log.w(TAG, "▶ startForeground called (system restart, no action)")
            }
        }
        return START_STICKY
    }

    override fun onTaskRemoved(rootIntent: Intent?) {
        super.onTaskRemoved(rootIntent)
        // With stopWithTask="false" and START_STICKY, the service continues running
        // after task removal. No restart scheduling needed — the OS keeps us alive.
        Log.w(TAG, "⚠ onTaskRemoved FIRED pid=${android.os.Process.myPid()} stoppedByUser=$stoppedByUser — service continues via stopWithTask=false")
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.w(TAG, "💀 onDestroy FIRED pid=${android.os.Process.myPid()} stoppedByUser=$stoppedByUser")
        stopForeground(STOP_FOREGROUND_REMOVE)
    }

    // ── Private helpers ─────────────────────────────────────────────────

    private fun ensureNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_LOW   // quiet, persistent indicator
            ).apply {
                description = CHANNEL_DESC
                setShowBadge(false)
            }
            val nm = getSystemService(NotificationManager::class.java)
            nm.createNotificationChannel(channel)
        }
    }

    private fun buildNotification(): android.app.Notification {
        val openAppIntent = Intent(this, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        val pendingIntent = PendingIntent.getActivity(
            this,
            0,
            openAppIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        // STOP button routes through RestartReceiver (STOP proxy) so that
        // persisted settings are updated atomically through the same central path.
        val stopIntent = Intent(this, RestartReceiver::class.java).apply {
            action = RestartReceiver.ACTION_STOP_PROXY
        }
        val stopPendingIntent = PendingIntent.getBroadcast(
            this,
            1,
            stopIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(com.snow.safetalk.R.drawable.ic_shield_protection)
            .setContentTitle("SafeTalk himoya faol")
            .setContentText("Xabar himoyasi fonda ishlayapti")
            .setOngoing(true)
            .setCategory(NotificationCompat.CATEGORY_SERVICE)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setContentIntent(pendingIntent)
            .addAction(
                android.R.drawable.ic_menu_close_clear_cancel,
                "To'xtatish",
                stopPendingIntent
            )
            .build()
    }
}
