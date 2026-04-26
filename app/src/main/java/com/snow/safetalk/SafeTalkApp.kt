package com.snow.safetalk

import android.app.Application
import com.snow.safetalk.notification.AppForegroundObserver
import com.snow.safetalk.notification.NotificationPermissionHelper
import com.snow.safetalk.notification.PersistentNotificationManager

/**
 * Application subclass for SafeTalk.
 *
 * Primary purpose: register notification channels at the earliest possible
 * moment (before any Activity/Service/BroadcastReceiver runs). This is
 * critical for:
 * - Android 8+: channels must exist before the system settings page can
 *   show notification controls
 * - MIUI/sideloaded APKs: without early channel creation, the system
 *   notification settings page shows nothing or is greyed out
 */
class SafeTalkApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Track foreground/background state for SecurityAlertNotificationManager dispatch.
        // MUST be registered before any receiver or service fires.
        AppForegroundObserver.init()
        // Create all notification channels immediately. Idempotent.
        NotificationPermissionHelper.ensureAllChannels(this)
        // Also ensure the dedicated persistent suspicious channel exists.
        PersistentNotificationManager.createChannel(this)
    }
}
