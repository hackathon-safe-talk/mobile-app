package com.snow.safetalk.protection

import android.app.ActivityManager
import android.app.ForegroundServiceStartNotAllowedException
import android.content.Context
import android.content.Intent
import android.os.Build
import android.util.Log
import androidx.core.content.ContextCompat

enum class StarterPolicy {
    IGNORE_COOLDOWN, // For user-initiated acts or high-privilege states (e.g. NLS Connect)
    RESPECT_COOLDOWN // For recurring backgrounds (JobService, Receivers)
}

object SafeServiceStarter {
    private const val TAG = "SafeTalk-ServiceStarter"
    private const val COOLDOWN_MS = 60_000L // 60 seconds

    @Volatile
    private var isServiceBelievedRunning = false

    fun markServiceStopped() {
        isServiceBelievedRunning = false
    }

    fun startSafe(
        context: Context,
        intent: Intent,
        source: String,
        policy: StarterPolicy,
        serviceClass: Class<*>
    ): ServiceStarterResult {
        Log.d(TAG, "[$source] Attempting to start Foreground Service: ${serviceClass.simpleName}")

        val synchronizer = ProtectionStateSynchronizer.getInstance(context)
        val now = System.currentTimeMillis()
        val lastAttemptTime = synchronizer.lastFgsAttemptTimeMillis
        val lastResult = synchronizer.lastFgsAttemptResult

        // Cooldown check
        if (policy == StarterPolicy.RESPECT_COOLDOWN && now - lastAttemptTime < COOLDOWN_MS && lastResult == "BLOCKED") {
            Log.w(TAG, "[$source] Blocked by Cooldown (Previous attempt failed < 60s ago).")
            return ServiceStarterResult.BlockedBySystem("Cooldown active")
        }

        if (isServiceBelievedRunning || isServiceAlreadyRunning(context, serviceClass)) {
            Log.d(TAG, "[$source] Service already running. Skipping redundant start.")
            isServiceBelievedRunning = true
            synchronizer.lastFgsAttemptTimeMillis = now
            synchronizer.lastFgsSuccessTimeMillis = now
            synchronizer.lastFgsAttemptResult = "ALREADY_RUNNING"
            return ServiceStarterResult.AlreadyRunning
        }

        return try {
            ContextCompat.startForegroundService(context, intent)
            isServiceBelievedRunning = true
            Log.i(TAG, "[$source] Successfully started Foreground Service.")
            synchronizer.lastFgsAttemptTimeMillis = now
            synchronizer.lastFgsSuccessTimeMillis = now
            synchronizer.lastFgsAttemptResult = "STARTED"
            ServiceStarterResult.Started
        } catch (e: Exception) {
            handleException(e, source, synchronizer, now)
        }
    }

    private fun handleException(e: Exception, source: String, synchronizer: ProtectionStateSynchronizer, now: Long): ServiceStarterResult {
        isServiceBelievedRunning = false
        synchronizer.lastFgsAttemptTimeMillis = now

        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && e is ForegroundServiceStartNotAllowedException) {
            Log.w(TAG, "[$source] Blocked by Android 12+ background restrictions. (Graceful degrade)")
            synchronizer.lastBlockedTimeMillis = now
            synchronizer.lastFgsAttemptResult = "BLOCKED"
            ServiceStarterResult.BlockedBySystem("ForegroundServiceStartNotAllowedException")
        } else if (e is IllegalStateException) {
            Log.w(TAG, "[$source] Blocked by system state (IllegalStateException). (Graceful degrade)")
            synchronizer.lastBlockedTimeMillis = now
            synchronizer.lastFgsAttemptResult = "BLOCKED"
            ServiceStarterResult.BlockedBySystem("IllegalStateException")
        } else {
            Log.e(TAG, "[$source] Failed unexpectedly: ${e.message}", e)
            synchronizer.lastFgsAttemptResult = "ERROR"
            ServiceStarterResult.FailedUnexpectedly(e)
        }
    }

    @Suppress("DEPRECATION")
    private fun isServiceAlreadyRunning(context: Context, serviceClass: Class<*>): Boolean {
        try {
            val manager = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager?
            manager?.getRunningServices(Integer.MAX_VALUE)?.forEach { service ->
                if (serviceClass.name == service.service.className) {
                    return true
                }
            }
        } catch (e: Exception) {
            // Ignore constraints
        }
        return false
    }
}
