package com.snow.safetalk.bot

import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.util.Log

object TelegramAppDetector {

    private const val TAG = "TelegramAppDetector"

    private val supportedPackages = listOf(
        "org.telegram.messenger",
        "org.telegram.plus",
        "org.thunderdog.challegram",
        "org.telegram.messenger.web",
        "org.telegram.messenger.beta",
        "nekox.messenger",
        "com.ayugram.android"
    )

    fun hasSupportedTelegramClient(context: Context): Boolean {
        // 1. Intent-based detection (Preferred for Android 11+)
        val tgIntent = Intent(Intent.ACTION_VIEW, Uri.parse(BotLinks.TG_RESOLVE))
        val httpsIntent = Intent(Intent.ACTION_VIEW, Uri.parse(BotLinks.T_ME_URL))

        val pm = context.packageManager
        
        // Try tg:// scheme first
        val tgActivities = pm.queryIntentActivities(tgIntent, PackageManager.MATCH_DEFAULT_ONLY)
        if (tgActivities.isNotEmpty()) {
            Log.d(TAG, "tg:// intent is supported by ${tgActivities.size} apps.")
            return true
        }

        // Try https://t.me scheme checking if Telegram handles it
        val httpsActivities = pm.queryIntentActivities(httpsIntent, PackageManager.MATCH_DEFAULT_ONLY)
        for (activityInfo in httpsActivities) {
            val packageName = activityInfo.activityInfo.packageName
            Log.d(TAG, "https://t.me intent supported by: $packageName")
            if (supportedPackages.contains(packageName)) {
                Log.d(TAG, "Supported Telegram client found handling https intent: $packageName")
                return true
            }
        }

        // 2. Fallback to package-name check
        for (pkg in supportedPackages) {
            try {
                // Returns PackageInfo if installed, throws exception otherwise
                pm.getPackageInfo(pkg, PackageManager.GET_META_DATA)
                Log.d(TAG, "Package found via GET_META_DATA: $pkg")
                return true
            } catch (e: PackageManager.NameNotFoundException) {
                // Ignore and check next
            }
        }

        Log.d(TAG, "No supported Telegram client found.")
        return false
    }
}
