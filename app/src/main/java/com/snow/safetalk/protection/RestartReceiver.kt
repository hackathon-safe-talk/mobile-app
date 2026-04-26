package com.snow.safetalk.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.util.Log
import com.snow.safetalk.settings.SettingsDataStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * STOP-only proxy receiver for the protection notification's "TO'XTATISH" button.
 *
 * Routes the stop request through the same central path as the in-app toggle:
 * 1. Persists the disabled state to DataStore + SharedPreferences mirror
 * 2. Stops the foreground service
 * 3. Cancels the periodic watchdog job
 *
 * This ensures UI/toggle state stays consistent regardless of whether
 * the user stops protection via the notification or from inside the app.
 *
 * NOTE: This receiver contains NO restart logic. It is exclusively a stop proxy.
 */
class RestartReceiver : BroadcastReceiver() {

    companion object {
        const val ACTION_STOP_PROXY = "com.snow.safetalk.action.STOP_PROTECTION_PROXY"
        private const val TAG = "SafeTalk-StopProxy"
    }

    private val job = SupervisorJob()
    private val scope = CoroutineScope(Dispatchers.IO + job)

    override fun onReceive(context: Context, intent: Intent?) {
        Log.w(TAG, "📡 onReceive action=${intent?.action} pid=${android.os.Process.myPid()}")
        val appContext = context.applicationContext

        if (!com.snow.safetalk.settings.SettingsDataStore(appContext).hasAcceptedLegalSync()) {
            return
        }

        if (intent?.action == ACTION_STOP_PROXY) {
            Log.w(TAG, "📡 Notification STOP request received. Disabling protection cleanly.")
            val pendingResult = goAsync()
            scope.launch {
                try {
                    val settings = SettingsDataStore(appContext)
                    settings.setAlwaysOnProtectionEnabled(false)
                    ProtectionForegroundService.stop(appContext)
                    ProtectionJobService.cancel(appContext)
                    Log.w(TAG, "📡 Protection disabled via notification STOP proxy.")
                } finally {
                    pendingResult.finish()
                }
            }
            return
        }

        // Unknown action — ignore silently
        Log.w(TAG, "📡 Unknown action received, ignoring: ${intent?.action}")
    }
}
