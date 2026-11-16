package com.catetin.app.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.catetin.app.backup.BackupSettings
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.settingsDataStore by preferencesDataStore(name = "catet_settings")

data class AppSettings(
    val onboardingComplete: Boolean = false,
    val defaultCurrency: String = "IDR",
    val themeMode: String = "SYSTEM",
    val dynamicColor: Boolean = false,
    val appLockEnabled: Boolean = false,
    val lockTimeoutSeconds: Int = 60,
    val rawTextRetentionDays: Int = 90,
    val includeRawTextInBackup: Boolean = false,
    val gmailBackfillDays: Int = 30,
    val aiModelConsent: Boolean = false,
    val keepScreenSecure: Boolean = false,
)

@Singleton
class SettingsRepository @Inject constructor(
    @ApplicationContext private val context: Context,
) {
    private object Keys {
        val onboarding = booleanPreferencesKey("onboarding_complete")
        val currency = stringPreferencesKey("default_currency")
        val theme = stringPreferencesKey("theme_mode")
        val dynamic = booleanPreferencesKey("dynamic_color")
        val lock = booleanPreferencesKey("app_lock_enabled")
        val lockTimeout = intPreferencesKey("lock_timeout_seconds")
        val retention = intPreferencesKey("raw_text_retention_days")
        val rawInBackup = booleanPreferencesKey("include_raw_text_in_backup")
        val backfill = intPreferencesKey("gmail_backfill_days")
        val aiConsent = booleanPreferencesKey("ai_model_consent")
        val secure = booleanPreferencesKey("keep_screen_secure")
    }

    val settings: Flow<AppSettings> = context.settingsDataStore.data.map { prefs ->
        AppSettings(
            onboardingComplete = prefs[Keys.onboarding] ?: false,
            defaultCurrency = prefs[Keys.currency] ?: "IDR",
            themeMode = prefs[Keys.theme] ?: "SYSTEM",
            dynamicColor = prefs[Keys.dynamic] ?: false,
            appLockEnabled = prefs[Keys.lock] ?: false,
            lockTimeoutSeconds = prefs[Keys.lockTimeout] ?: 60,
            rawTextRetentionDays = prefs[Keys.retention] ?: 90,
            includeRawTextInBackup = prefs[Keys.rawInBackup] ?: false,
            gmailBackfillDays = prefs[Keys.backfill] ?: 30,
            aiModelConsent = prefs[Keys.aiConsent] ?: false,
            keepScreenSecure = prefs[Keys.secure] ?: false,
        )
    }

    suspend fun setOnboardingComplete(value: Boolean) = edit { it[Keys.onboarding] = value }
    suspend fun setDefaultCurrency(value: String) = edit { it[Keys.currency] = value }
    suspend fun setThemeMode(value: String) = edit { it[Keys.theme] = value }
    suspend fun setDynamicColor(value: Boolean) = edit { it[Keys.dynamic] = value }
    suspend fun setAppLockEnabled(value: Boolean) = edit { it[Keys.lock] = value }
    suspend fun setLockTimeoutSeconds(value: Int) = edit { it[Keys.lockTimeout] = value }
    suspend fun setRawTextRetentionDays(value: Int) = edit { it[Keys.retention] = value }
    suspend fun setIncludeRawTextInBackup(value: Boolean) = edit { it[Keys.rawInBackup] = value }
    suspend fun setGmailBackfillDays(value: Int) = edit { it[Keys.backfill] = value }
    suspend fun setAiModelConsent(value: Boolean) = edit { it[Keys.aiConsent] = value }
    suspend fun setKeepScreenSecure(value: Boolean) = edit { it[Keys.secure] = value }

    suspend fun applyBackup(value: BackupSettings) = context.settingsDataStore.edit { prefs ->
        prefs[Keys.currency] = value.defaultCurrency
        prefs[Keys.theme] = value.themeMode
        prefs[Keys.dynamic] = value.dynamicColor
        prefs[Keys.lock] = value.appLockEnabled
        prefs[Keys.lockTimeout] = value.lockTimeoutSeconds
        prefs[Keys.retention] = value.rawTextRetentionDays
        prefs[Keys.backfill] = value.gmailBackfillDays
        prefs[Keys.secure] = value.keepScreenSecure
    }

    private suspend fun edit(block: (androidx.datastore.preferences.core.MutablePreferences) -> Unit) {
        context.settingsDataStore.edit { prefs -> block(prefs) }
    }
}
