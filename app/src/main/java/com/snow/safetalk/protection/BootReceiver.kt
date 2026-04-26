package com.snow.safetalk.protection

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * Restores always-on background protection after device reboot.
 *
 * Triggered by [Intent.ACTION_BOOT_COMPLETED]. Uses [ProtectionManager]
 * exclusively — does NOT access DataStore or ForegroundService directly.
 *
 * Logic:
 * 1. Check boot_restore_enabled via ProtectionManager
 * 2. If enabled, call syncProtectionState() which starts the service
 *    only if always_on_protection_enabled is also true
 * 3. If disabled, do nothing
 */
class BootReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action != Intent.ACTION_BOOT_COMPLETED) return

        val appContext = context.applicationContext
        if (!com.snow.safetalk.settings.SettingsDataStore(appContext).hasAcceptedLegalSync()) {
            return
        }
        val manager = ProtectionManager(appContext)

        // goAsync() extends the BroadcastReceiver timeout from ~10s to ~30s,
        // giving DataStore enough time to read persisted flags safely.
        val pendingResult = goAsync()

        CoroutineScope(Dispatchers.IO).launch {
            try {
                kotlinx.coroutines.delay(3000)
                com.snow.safetalk.notification.PersistentNotificationManager.restoreAllAfterReboot(appContext)

                val bootRestoreEnabled = manager.observeBootRestoreEnabled().first()
                if (bootRestoreEnabled) {
                    manager.syncProtectionState()
                }
            } finally {
                pendingResult.finish()
            }
        }
    }
}
