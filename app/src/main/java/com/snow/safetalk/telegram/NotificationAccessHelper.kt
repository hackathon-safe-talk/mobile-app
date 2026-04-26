package com.snow.safetalk.telegram

import android.content.ComponentName
import android.content.Context
import android.content.pm.PackageManager
import android.provider.Settings

object NotificationAccessHelper {

    fun isNotificationAccessGranted(context: Context): Boolean {
        val enabledListeners = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners"
        )
        return enabledListeners?.contains(context.packageName) == true
    }

    /**
     * Forcefully rebinds the NotificationListenerService.
     * This is crucial for fixing the bug where Android (especially MIUI/HyperOS) 
     * refuses to bind the service after an app update or force stop even when permission is ON.
     */
    fun forceRebindService(context: Context) {
        val componentName = ComponentName(context, TelegramNotificationListenerService::class.java)
        val pm = context.packageManager

        // Toggling the component disabled -> enabled forces the system to re-evaluate and rebind the listener
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
            PackageManager.DONT_KILL_APP
        )
        pm.setComponentEnabledSetting(
            componentName,
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
            PackageManager.DONT_KILL_APP
        )
    }
}
