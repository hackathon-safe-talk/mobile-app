package com.snow.safetalk.sms

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.snow.safetalk.MainActivity

internal const val CHANNEL_ID   = "safetalk_sms_alerts"
private  const val CHANNEL_NAME = "SafeTalk SMS Alert"
private  const val CHANNEL_DESC = "Shubhali SMS xabarlari haqida ogohlantirishlar"

object NotificationHelper {

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
     * Show a high-priority notification for a suspicious SMS.
     * Tapping opens [MainActivity] with the analysisId extra, which then
     * navigates to the main Tekshiruv natijasi screen.
     */
    fun showSmsAlert(context: Context, analysisId: String, riskScore: Int) {
        createChannel(context)

        val deepLinkIntent = Intent(context, MainActivity::class.java).apply {
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
            putExtra("analysis_id", analysisId)
            putExtra("from_safetalk_internal", true)
        }

        val pendingIntent = PendingIntent.getActivity(
            context,
            analysisId.hashCode(),             // unique request code per analysis
            deepLinkIntent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_alert)
            .setContentTitle("⚠ Xavfli SMS aniqlandi")
            .setContentText("Xavf darajasi: $riskScore% • Batafsil ko'rish uchun bosing")
            .setStyle(NotificationCompat.BigTextStyle()
                .bigText("Xavf darajasi: $riskScore%\nSMS xabari shubhali — batafsil ko'rish uchun bosing"))
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_ALARM)
            .setAutoCancel(true)
            .setContentIntent(pendingIntent)
            .build()

        with(NotificationManagerCompat.from(context)) {
            notify(analysisId.hashCode(), notification)
        }
    }
}
