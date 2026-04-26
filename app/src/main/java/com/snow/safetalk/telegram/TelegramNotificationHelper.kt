package com.snow.safetalk.telegram

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snow.safetalk.MainActivity

object TelegramNotificationHelper {

    private const val CHANNEL_ID = "safetalk_alerts"
    private const val CHANNEL_NAME = "SafeTalk ogohlantirishlari"
    private const val CHANNEL_DESC = "Shubhali va xavfli xabarlar haqida ogohlantirishlar"

    /** Ensure notification channel exists (idempotent — safe to call multiple times). */
    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                CHANNEL_ID,
                CHANNEL_NAME,
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = CHANNEL_DESC
                enableLights(true)
                enableVibration(true)
            }
            val nm = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            nm.createNotificationChannel(channel)
        }
    }

    /**
     * Show a high-priority notification for a suspicious or dangerous Telegram message.
     * Tapping opens MainActivity with the analysisId extra.
     */
    fun showTelegramAlert(
        context: Context,
        analysisId: String,
        riskScore: Int,
        senderTitle: String
    ) {
        if (riskScore < 40) return // Below 40 is safe, no warning

        createChannel(context)

        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("analysis_id", analysisId)
            putExtra("from_safetalk_internal", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            analysisId.hashCode(),
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val title: String
        val text: String
        val bigText: String

        if (riskScore >= 70) {
            title = "⚠️ Xavfli Telegram xabar aniqlandi"
            text = "Xabarni ochishdan oldin tekshiring"
            bigText = "Manba: $senderTitle\nXavf darajasi: $riskScore%\nXabarni ochishdan oldin tekshiring."
        } else {
            title = "⚠️ Shubhali Telegram xabar"
            text = "Xabar ehtiyotkorlik bilan tekshirilsin"
            bigText = "Manba: $senderTitle\nXavf darajasi: $riskScore%\nXabar ehtiyotkorlik bilan tekshirilsin."
        }

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info) // Safe generic icon
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(NotificationCompat.BigTextStyle().bigText(bigText))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        with(NotificationManagerCompat.from(context)) {
            try {
                notify(analysisId.hashCode(), notification)
            } catch (e: SecurityException) {
                // Permission might be missing on Android 13+, safely ignore
            }
        }
    }
}
