package com.snow.safetalk.sources

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.sourcesDataStore: DataStore<Preferences>
        by preferencesDataStore(name = "safetalk_sources")

/**
 * Persists toggle state for message sources (SMS, Telegram).
 */
class SourcesDataStore(private val context: Context) {

    companion object {
        private val SMS_ENABLED      = booleanPreferencesKey("sms_auto_enabled")
        private val TELEGRAM_ENABLED = booleanPreferencesKey("telegram_auto_enabled")
    }

    val smsEnabled: Flow<Boolean> = context.sourcesDataStore.data
        .map { it[SMS_ENABLED] ?: false }

    val telegramEnabled: Flow<Boolean> = context.sourcesDataStore.data
        .map { it[TELEGRAM_ENABLED] ?: false }

    suspend fun setSmsEnabled(enabled: Boolean) {
        context.sourcesDataStore.edit { it[SMS_ENABLED] = enabled }
    }

    suspend fun setTelegramEnabled(enabled: Boolean) {
        context.sourcesDataStore.edit { it[TELEGRAM_ENABLED] = enabled }
    }
}
