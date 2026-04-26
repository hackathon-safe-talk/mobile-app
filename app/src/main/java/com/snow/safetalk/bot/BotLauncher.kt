package com.snow.safetalk.bot

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.util.Log

object BotLauncher {

    private const val TAG = "BotLauncher"

    /**
     * Attempts to open the bot using the primary Intent format,
     * falling back to the web link if necessary.
     */
    fun openSafeTalkBot(context: Context) {
        try {
            Log.d(TAG, "Launching tg:// intent")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BotLinks.TG_RESOLVE))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "tg:// intent failed, falling back to https://")
            // Fallback to https link
            try {
                val fallbackIntent = Intent(Intent.ACTION_VIEW, Uri.parse(BotLinks.T_ME_URL))
                context.startActivity(fallbackIntent)
            } catch (fallbackEx: ActivityNotFoundException) {
                Log.e(TAG, "Both intents failed to launch.", fallbackEx)
            }
        }
    }

    /**
     * Attempts to open the Play Store page for Telegram,
     * falling back to the browser-based Play Store page.
     */
    fun installTelegram(context: Context) {
        try {
            Log.d(TAG, "Launching Play Store intent")
            val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BotLinks.PLAY_STORE_URL))
            context.startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            Log.d(TAG, "Play Store intent failed, falling back to Web Store")
            try {
                val intent = Intent(Intent.ACTION_VIEW, Uri.parse(BotLinks.WEB_STORE_URL))
                context.startActivity(intent)
            } catch (fallbackEx: ActivityNotFoundException) {
                Log.e(TAG, "Failed to launch store.", fallbackEx)
            }
        }
    }
}
