package com.snow.safetalk.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

/** SharedPreferences file used as a synchronous mirror of critical protection flags. */
private const val PROTECTION_SP_NAME = "safetalk_protection_sync"
private const val SP_KEY_ALWAYS_ON   = "always_on_protection_enabled"

private val Context.settingsDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "safetalk_settings")

/**
 * Persistent settings backed by DataStore.
 */
class SettingsDataStore(private val context: Context) {

    companion object {
        private val PENDING_DEEP_LINK_ID = androidx.datastore.preferences.core.stringPreferencesKey("pending_deep_link_id")
        private val IS_DARK_MODE        = booleanPreferencesKey("is_dark_mode")
        private val SOUND_ENABLED       = booleanPreferencesKey("sound_enabled")
        private val STRONG_HIGHLIGHT    = booleanPreferencesKey("strong_highlight")
        private val SMS_NOTIFICATIONS   = booleanPreferencesKey("sms_notifications")
        private val SMS_SOURCE_ENABLED  = booleanPreferencesKey("sms_source_enabled")
        private val NOTIFICATION_THRESH = intPreferencesKey("notification_threshold")
        private val SHOW_EXPLANATION    = booleanPreferencesKey("show_explanation")
        private val HAS_COMPLETED_ONBOARDING = booleanPreferencesKey("has_completed_onboarding")
        private val HAS_ACCEPTED_LEGAL       = booleanPreferencesKey("has_accepted_legal")
        private val HISTORY_RETENTION_DAYS   = intPreferencesKey("history_retention_days")
        private val ALWAYS_ON_PROTECTION_ENABLED      = booleanPreferencesKey("always_on_protection_enabled")
        private val BOOT_RESTORE_ENABLED              = booleanPreferencesKey("boot_restore_enabled")
        private val BACKGROUND_PROTECTION_INITIALIZED = booleanPreferencesKey("background_protection_initialized")
        private val FULL_SCREEN_ALERT_ENABLED         = booleanPreferencesKey("full_screen_alert_enabled")
    }

    val historyRetentionDays: Flow<Int> = context.settingsDataStore.data
        .map { it[HISTORY_RETENTION_DAYS] ?: 90 }

    val isDarkMode: Flow<Boolean> = context.settingsDataStore.data
        .map { it[IS_DARK_MODE] ?: true }

    val soundEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { it[SOUND_ENABLED] ?: true }

    val strongHighlight: Flow<Boolean> = context.settingsDataStore.data
        .map { it[STRONG_HIGHLIGHT] ?: true }

    /** Real-time SMS protection + notification toggle. Default OFF. */
    val smsNotifications: Flow<Boolean> = context.settingsDataStore.data
        .map { it[SMS_NOTIFICATIONS] ?: false }

    /** SMS as a message source (background capture). Default OFF. */
    val smsSourceEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { it[SMS_SOURCE_ENABLED] ?: false }

    val notificationThreshold: Flow<Int> = context.settingsDataStore.data
        .map { it[NOTIFICATION_THRESH] ?: 40 }

    val showExplanation: Flow<Boolean> = context.settingsDataStore.data
        .map { it[SHOW_EXPLANATION] ?: true }

    /** Master toggle for always-on background protection. Default OFF. */
    val alwaysOnProtectionEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { it[ALWAYS_ON_PROTECTION_ENABLED] ?: false }

    /** Whether to auto-restore protection after device reboot. Default ON. */
    val bootRestoreEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { it[BOOT_RESTORE_ENABLED] ?: true }

    /** First-run flag for background protection setup. Default false. */
    val backgroundProtectionInitialized: Flow<Boolean> = context.settingsDataStore.data
        .map { it[BACKGROUND_PROTECTION_INITIALIZED] ?: false }

    /** Whether to show a full-screen alert for DANGEROUS (≥70) messages. Default ON. */
    val fullScreenAlertEnabled: Flow<Boolean> = context.settingsDataStore.data
        .map { it[FULL_SCREEN_ALERT_ENABLED] ?: true }

    suspend fun setDarkMode(v: Boolean)          { context.settingsDataStore.edit { it[IS_DARK_MODE] = v } }
    suspend fun setSoundEnabled(v: Boolean)       { context.settingsDataStore.edit { it[SOUND_ENABLED] = v } }
    suspend fun setStrongHighlight(v: Boolean)    { context.settingsDataStore.edit { it[STRONG_HIGHLIGHT] = v } }
    suspend fun setSmsNotifications(v: Boolean)   { context.settingsDataStore.edit { it[SMS_NOTIFICATIONS] = v } }
    suspend fun setSmsSourceEnabled(v: Boolean)   { context.settingsDataStore.edit { it[SMS_SOURCE_ENABLED] = v } }
    suspend fun setNotificationThreshold(v: Int)  { context.settingsDataStore.edit { it[NOTIFICATION_THRESH] = v } }
    suspend fun setShowExplanation(v: Boolean)    { context.settingsDataStore.edit { it[SHOW_EXPLANATION] = v } }
    suspend fun setHasCompletedOnboarding(v: Boolean) { context.settingsDataStore.edit { it[HAS_COMPLETED_ONBOARDING] = v } }
    suspend fun setHasAcceptedLegal(v: Boolean)   {
        // Write to SharedPreferences mirror FIRST (synchronous, survives process death)
        context.getSharedPreferences(PROTECTION_SP_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("has_accepted_legal_sync", v).commit()
        // Then write to DataStore (async)
        context.settingsDataStore.edit { it[HAS_ACCEPTED_LEGAL] = v }
    }
    suspend fun setHistoryRetentionDays(v: Int)   { context.settingsDataStore.edit { it[HISTORY_RETENTION_DAYS] = v } }
    suspend fun setAlwaysOnProtectionEnabled(v: Boolean) {
        // Write to SharedPreferences mirror FIRST (synchronous, survives process death)
        context.getSharedPreferences(PROTECTION_SP_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean(SP_KEY_ALWAYS_ON, v).commit()
        // Then write to DataStore (async, source of truth for Flow observers)
        context.settingsDataStore.edit { it[ALWAYS_ON_PROTECTION_ENABLED] = v }
    }
    suspend fun setBootRestoreEnabled(v: Boolean)        { context.settingsDataStore.edit { it[BOOT_RESTORE_ENABLED] = v } }
    suspend fun setBackgroundProtectionInitialized(v: Boolean) { context.settingsDataStore.edit { it[BACKGROUND_PROTECTION_INITIALIZED] = v } }
    suspend fun setFullScreenAlertEnabled(v: Boolean) {
        // Mirror to SharedPreferences so background services can read synchronously.
        context.getSharedPreferences(PROTECTION_SP_NAME, Context.MODE_PRIVATE)
            .edit().putBoolean("full_screen_alert_enabled_sync", v).commit()
        context.settingsDataStore.edit { it[FULL_SCREEN_ALERT_ENABLED] = v }
    }

    val hasCompletedOnboardingFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { it[HAS_COMPLETED_ONBOARDING] ?: false }

    val hasAcceptedLegalFlow: Flow<Boolean> = context.settingsDataStore.data
        .map { it[HAS_ACCEPTED_LEGAL] ?: false }

    val pendingDeepLinkIdFlow: Flow<String?> = context.settingsDataStore.data
        .map { it[PENDING_DEEP_LINK_ID] }

    suspend fun setPendingDeepLinkId(id: String?) {
        context.settingsDataStore.edit { preferences ->
            if (id == null) preferences.remove(PENDING_DEEP_LINK_ID) else preferences[PENDING_DEEP_LINK_ID] = id
        }
    }

    /** Disable all real-time SMS features in one atomic write. */
    suspend fun disableAllSmsFeatures() {
        context.settingsDataStore.edit {
            it[SMS_NOTIFICATIONS]  = false
            it[SMS_SOURCE_ENABLED] = false
        }
    }

    /** Real synchronous check for legal acceptance */
    fun hasAcceptedLegalSync(): Boolean =
        context.getSharedPreferences(PROTECTION_SP_NAME, Context.MODE_PRIVATE)
            .getBoolean("has_accepted_legal_sync", false)

    /**
     * Synchronous read of always-on protection flag from SharedPreferences mirror.
     * Safe to call from [Service.onTaskRemoved] / [Service.onDestroy] where
     * coroutine-based DataStore reads may not complete before process death.
     */
    fun isAlwaysOnProtectionEnabledSync(): Boolean =
        context.getSharedPreferences(PROTECTION_SP_NAME, Context.MODE_PRIVATE)
            .getBoolean(SP_KEY_ALWAYS_ON, false)

    /**
     * Synchronous read of full-screen alert preference.
     * Safe to call from BroadcastReceiver / NotificationListenerService context.
     * Default is true (full-screen ON) so first-run behavior is correct.
     */
    fun isFullScreenAlertEnabledSync(): Boolean =
        context.getSharedPreferences(PROTECTION_SP_NAME, Context.MODE_PRIVATE)
            .getBoolean("full_screen_alert_enabled_sync", true)
}
