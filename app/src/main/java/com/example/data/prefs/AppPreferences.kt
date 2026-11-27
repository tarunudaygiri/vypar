package com.example.data.prefs

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.data.model.AppLanguage
import com.example.data.model.FontSizeOption
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore by preferencesDataStore(name = "vyapardesk_prefs")

data class UserPreferences(
    val language: AppLanguage = AppLanguage.ENGLISH,
    val fontSize: FontSizeOption = FontSizeOption.NORMAL,
    val darkTheme: Boolean = false,
    val activeEmployeeId: Long = 1L,
    val autoBackupEnabled: Boolean = true,
    val lastBackupTimestamp: Long = 0L
)

class AppPreferences(private val context: Context) {
    private object Keys {
        val LANGUAGE = stringPreferencesKey("language")
        val FONT_SIZE = stringPreferencesKey("font_size")
        val DARK_THEME = booleanPreferencesKey("dark_theme")
        val ACTIVE_EMPLOYEE_ID = longPreferencesKey("active_employee_id")
        val AUTO_BACKUP = booleanPreferencesKey("auto_backup")
        val LAST_BACKUP = longPreferencesKey("last_backup")
    }

    val preferencesFlow: Flow<UserPreferences> = context.dataStore.data.map { prefs ->
        UserPreferences(
            language = AppLanguage.fromCode(prefs[Keys.LANGUAGE] ?: "en"),
            fontSize = FontSizeOption.fromKey(prefs[Keys.FONT_SIZE] ?: "normal"),
            darkTheme = prefs[Keys.DARK_THEME] ?: false,
            activeEmployeeId = prefs[Keys.ACTIVE_EMPLOYEE_ID] ?: 1L,
            autoBackupEnabled = prefs[Keys.AUTO_BACKUP] ?: true,
            lastBackupTimestamp = prefs[Keys.LAST_BACKUP] ?: 0L
        )
    }

    suspend fun setLanguage(language: AppLanguage) {
        context.dataStore.edit { it[Keys.LANGUAGE] = language.code }
    }

    suspend fun setFontSize(fontSize: FontSizeOption) {
        context.dataStore.edit { it[Keys.FONT_SIZE] = fontSize.key }
    }

    suspend fun setDarkTheme(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DARK_THEME] = enabled }
    }

    suspend fun setActiveEmployeeId(employeeId: Long) {
        context.dataStore.edit { it[Keys.ACTIVE_EMPLOYEE_ID] = employeeId }
    }

    suspend fun setAutoBackup(enabled: Boolean) {
        context.dataStore.edit { it[Keys.AUTO_BACKUP] = enabled }
    }

    suspend fun setLastBackupTimestamp(timestamp: Long) {
        context.dataStore.edit { it[Keys.LAST_BACKUP] = timestamp }
    }
}
