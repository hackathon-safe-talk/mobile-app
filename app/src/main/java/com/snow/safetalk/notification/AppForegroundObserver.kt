package com.snow.safetalk.notification

import android.util.Log
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.ProcessLifecycleOwner

/**
 * Singleton observer that tracks whether the SafeTalk APPLICATION (not a specific
 * Activity) is in the foreground.
 *
 * **Why this exists:**
 * Android intentionally suppresses `setFullScreenIntent()` when the app is already
 * in the foreground. For a security app, this is unacceptable — a DANGEROUS threat
 * must ALWAYS produce a full-screen alert regardless of app state.
 *
 * This observer enables the hybrid dispatch strategy in
 * [SecurityAlertNotificationManager]:
 *  - Foreground → direct `startActivity(SecurityAlertActivity)` (guaranteed)
 *  - Background → `setFullScreenIntent()` on the notification (system-managed)
 *
 * **Lifecycle:**
 *  - [init] must be called once from [com.snow.safetalk.SafeTalkApp.onCreate]
 *  - Uses [ProcessLifecycleOwner] which covers the entire app process, not
 *    individual Activities. "Foreground" = at least one Activity is in STARTED state.
 *  - Survives Activity rotation. Cleared only on process death.
 *
 * Thread-safety: [isAppInForeground] is volatile, safe to read from any thread.
 */
object AppForegroundObserver : DefaultLifecycleObserver {

    private const val TAG = "AppFgObserver"

    /**
     * True when any SafeTalk Activity is in at least the STARTED state.
     * Updated on main thread by ProcessLifecycleOwner callbacks.
     */
    @Volatile
    var isAppInForeground: Boolean = false
        private set

    /**
     * Register with ProcessLifecycleOwner. Must be called ONCE from
     * Application.onCreate(). Idempotent — subsequent calls are harmless.
     */
    fun init() {
        ProcessLifecycleOwner.get().lifecycle.addObserver(this)
        Log.d(TAG, "init() — registered with ProcessLifecycleOwner")
    }

    override fun onStart(owner: LifecycleOwner) {
        isAppInForeground = true
        Log.d(TAG, "App → FOREGROUND")
    }

    override fun onStop(owner: LifecycleOwner) {
        isAppInForeground = false
        Log.d(TAG, "App → BACKGROUND")
    }
}
