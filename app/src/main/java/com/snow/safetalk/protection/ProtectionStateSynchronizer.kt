package com.snow.safetalk.protection

import android.content.Context
import android.content.SharedPreferences

/**
 * Thread-safe lightweight state synchronizer that bridges user intent with actual runtime capabilities.
 * Acts as the definitive shared source of truth across the background protection components.
 */
class ProtectionStateSynchronizer private constructor(context: Context) {
    private val prefs: SharedPreferences = context.applicationContext.getSharedPreferences("safetalk_protection_state", Context.MODE_PRIVATE)

    companion object {
        @Volatile
        private var INSTANCE: ProtectionStateSynchronizer? = null
        private const val FGS_FRESH_WINDOW_MS = 20 * 60 * 1000L // 20 minutes

        fun getInstance(context: Context): ProtectionStateSynchronizer {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: ProtectionStateSynchronizer(context).also { INSTANCE = it }
            }
        }
    }

    var isNlsActivelyBound: Boolean
        @Synchronized get() = prefs.getBoolean("nls_bound", false)
        @Synchronized set(value) = prefs.edit().putBoolean("nls_bound", value).apply()

    var isIntentionalStop: Boolean
        @Synchronized get() = prefs.getBoolean("intentional_stop", false)
        @Synchronized set(value) = prefs.edit().putBoolean("intentional_stop", value).apply()

    var lastFgsAttemptTimeMillis: Long
        @Synchronized get() = prefs.getLong("last_fgs_attempt_time", 0L)
        @Synchronized set(value) = prefs.edit().putLong("last_fgs_attempt_time", value).apply()

    var lastFgsSuccessTimeMillis: Long
        @Synchronized get() = prefs.getLong("last_fgs_success_time", 0L)
        @Synchronized set(value) = prefs.edit().putLong("last_fgs_success_time", value).apply()

    var lastBlockedTimeMillis: Long
        @Synchronized get() = prefs.getLong("last_fgs_blocked_time", 0L)
        @Synchronized set(value) = prefs.edit().putLong("last_fgs_blocked_time", value).apply()

    var lastFgsAttemptResult: String
        @Synchronized get() = prefs.getString("last_fgs_attempt_result", "NONE") ?: "NONE"
        @Synchronized set(value) = prefs.edit().putString("last_fgs_attempt_result", value).apply()

    /**
     * Computes the current runtime protection state conservatively.
     * @param userIntentEnabled True if the user has enabled protection in settings.
     * @param smsEnabled True if SMS protection is enabled in settings.
     * @param nlsPermissionGranted True if Notification Listener permission is granted.
     */
    @Synchronized
    fun computeState(
        userIntentEnabled: Boolean,
        smsEnabled: Boolean,
        nlsPermissionGranted: Boolean
    ): ProtectionState {
        if (!userIntentEnabled) return ProtectionState.DISABLED
        if (isIntentionalStop) return ProtectionState.DISABLED

        if (!nlsPermissionGranted && !smsEnabled) return ProtectionState.DISABLED
        if (!nlsPermissionGranted && smsEnabled) return ProtectionState.LIMITED

        val now = System.currentTimeMillis()

        val hasRecentSuccess =
            lastFgsSuccessTimeMillis > 0L &&
            now - lastFgsSuccessTimeMillis <= FGS_FRESH_WINDOW_MS

        val blockedAfterSuccess =
            lastBlockedTimeMillis > 0L &&
            lastBlockedTimeMillis > lastFgsSuccessTimeMillis

        // Infer isServiceBelievedRunning from the latest valid outcome
        val isServiceBelievedRunning = 
            lastFgsAttemptResult == "STARTED" || 
            lastFgsAttemptResult == "ALREADY_RUNNING"

        val strongFgsEvidence =
            isNlsActivelyBound &&
            isServiceBelievedRunning &&
            hasRecentSuccess &&
            !blockedAfterSuccess

        return when {
            strongFgsEvidence -> ProtectionState.FULLY_ACTIVE
            isNlsActivelyBound -> ProtectionState.DEGRADED
            smsEnabled -> ProtectionState.LIMITED
            else -> ProtectionState.DISABLED
        }
    }
}
