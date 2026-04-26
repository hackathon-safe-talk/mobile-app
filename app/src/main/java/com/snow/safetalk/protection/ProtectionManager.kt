package com.snow.safetalk.protection

import android.content.Context
import android.util.Log
import androidx.core.app.NotificationManagerCompat
import com.snow.safetalk.settings.SettingsDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first

/**
 * Central orchestration layer for SafeTalk's always-on background protection.
 * Focuses heavily on realistic runtime state tracking for UI honesty.
 */
class ProtectionManager(context: Context) {

    companion object {
        private const val TAG = "SafeTalk-PM"
    }

    private val appContext: Context = context.applicationContext
    private val settingsDataStore = SettingsDataStore(appContext)

    fun observeAlwaysOnProtectionEnabled(): Flow<Boolean> =
        settingsDataStore.alwaysOnProtectionEnabled

    fun observeBootRestoreEnabled(): Flow<Boolean> =
        settingsDataStore.bootRestoreEnabled

    fun observeBackgroundProtectionInitialized(): Flow<Boolean> =
        settingsDataStore.backgroundProtectionInitialized

    suspend fun enableAlwaysOnProtection() {
        Log.w(TAG, "🟢 enableAlwaysOnProtection called")
        if (!settingsDataStore.hasAcceptedLegalSync()) {
            Log.e(TAG, "🟢 Rejected: Legal terms not accepted")
            return
        }

        settingsDataStore.setAlwaysOnProtectionEnabled(true)
        if (!settingsDataStore.backgroundProtectionInitialized.first()) {
            settingsDataStore.setBackgroundProtectionInitialized(true)
        }
        val result = ProtectionForegroundService.start(appContext, "ProtectionManager", StarterPolicy.IGNORE_COOLDOWN)
        if (result is ServiceStarterResult.FailedUnexpectedly) {
            Log.e(TAG, "🟢 FGS start failed from enable: ${result.error.message}")
        }
        ProtectionJobService.schedule(appContext)
    }

    suspend fun disableAlwaysOnProtection() {
        Log.w(TAG, "🔴 disableAlwaysOnProtection called")
        settingsDataStore.setAlwaysOnProtectionEnabled(false)
        ProtectionForegroundService.stop(appContext)
        ProtectionJobService.cancel(appContext)
    }

    suspend fun setBootRestoreEnabled(enabled: Boolean) {
        settingsDataStore.setBootRestoreEnabled(enabled)
    }

    suspend fun markBackgroundProtectionInitialized() {
        settingsDataStore.setBackgroundProtectionInitialized(true)
    }

    suspend fun syncProtectionState() {
        val enabled = settingsDataStore.alwaysOnProtectionEnabled.first()
        val legalAccepted = settingsDataStore.hasAcceptedLegalSync()

        Log.w(TAG, "🔄 syncProtectionState: DataStore=$enabled, Legal=$legalAccepted")
        
        if (!legalAccepted) {
            settingsDataStore.setAlwaysOnProtectionEnabled(false)
            ProtectionForegroundService.stop(appContext)
            ProtectionJobService.cancel(appContext)
            return
        }

        settingsDataStore.setAlwaysOnProtectionEnabled(enabled)
        
        if (enabled) {
            val result = ProtectionForegroundService.start(appContext, "ProtectionManager_Sync", StarterPolicy.IGNORE_COOLDOWN)
            if (result is ServiceStarterResult.FailedUnexpectedly) {
                Log.e(TAG, "🔄 FGS start failed from sync: ${result.error.message}")
            }
            ProtectionJobService.schedule(appContext)
        } else {
            ProtectionForegroundService.stop(appContext)
            ProtectionJobService.cancel(appContext)
        }
    }

    fun isProtectionEnabledSync(): Boolean =
        settingsDataStore.isAlwaysOnProtectionEnabledSync()

    private fun isNlsPermissionGranted(): Boolean {
        val enabledListeners = NotificationManagerCompat.getEnabledListenerPackages(appContext)
        return enabledListeners.contains(appContext.packageName)
    }

    /**
     * Computes the honest runtime state natively tracked by the singleton synchronizer.
     * Incorporates actual NLS permission capabilities to ensure zero false positives.
     */
    fun getRuntimeProtectionState(smsEnabled: Boolean): ProtectionState {
        val synchronizer = ProtectionStateSynchronizer.getInstance(appContext)
        val userEnabled = isProtectionEnabledSync()
        val nlsGranted = isNlsPermissionGranted()
        
        return synchronizer.computeState(
            userIntentEnabled = userEnabled,
            smsEnabled = smsEnabled,
            nlsPermissionGranted = nlsGranted
        )
    }
}
