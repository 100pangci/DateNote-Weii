package com.datenote.app.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.userPreferencesDataStore by preferencesDataStore(name = "user_preferences")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

data class UserPreferences(
    val nickname: String = "",
    val notificationGuideCompleted: Boolean = false,
    val notificationPermissionRequested: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val defaultReminderMinutes: Int = 24 * 60,
    val defaultReminderTimeMinutes: Int = 9 * 60,
    val defaultExpandSteps: Boolean = true,
    val autoCollapseCompletedSteps: Boolean = true,
    val aiBaseUrl: String = "https://api.openai.com/v1",
    val aiModel: String = "gpt-4o-mini",
)

class UserPreferencesRepository(private val context: Context) {
    private object Keys {
        val nickname = stringPreferencesKey("nickname")
        val notificationGuideCompleted = booleanPreferencesKey("notification_guide_completed")
        val notificationPermissionRequested = booleanPreferencesKey("notification_permission_requested")
        val themeMode = stringPreferencesKey("theme_mode")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val defaultReminderMinutes = intPreferencesKey("default_reminder_minutes")
        val defaultReminderTimeMinutes = intPreferencesKey("default_reminder_time_minutes")
        val defaultExpandSteps = booleanPreferencesKey("default_expand_steps")
        val autoCollapseCompletedSteps = booleanPreferencesKey("auto_collapse_completed_steps")
        val aiBaseUrl = stringPreferencesKey("ai_base_url")
        val aiModel = stringPreferencesKey("ai_model")
    }

    val preferences: Flow<UserPreferences> = context.userPreferencesDataStore.data.map { values ->
        UserPreferences(
            nickname = values[Keys.nickname].orEmpty(),
            notificationGuideCompleted = values[Keys.notificationGuideCompleted] ?: false,
            notificationPermissionRequested = values[Keys.notificationPermissionRequested] ?: false,
            themeMode = values[Keys.themeMode]
                ?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() }
                ?: ThemeMode.SYSTEM,
            dynamicColor = values[Keys.dynamicColor] ?: true,
            defaultReminderMinutes = values[Keys.defaultReminderMinutes] ?: 24 * 60,
            defaultReminderTimeMinutes = values[Keys.defaultReminderTimeMinutes] ?: 9 * 60,
            defaultExpandSteps = values[Keys.defaultExpandSteps] ?: true,
            autoCollapseCompletedSteps = values[Keys.autoCollapseCompletedSteps] ?: true,
            aiBaseUrl = values[Keys.aiBaseUrl] ?: "https://api.openai.com/v1",
            aiModel = values[Keys.aiModel] ?: "gpt-4o-mini",
        )
    }

    suspend fun setNickname(nickname: String) {
        context.userPreferencesDataStore.edit { it[Keys.nickname] = nickname.trim() }
    }

    suspend fun setNotificationGuideCompleted(completed: Boolean = true) {
        context.userPreferencesDataStore.edit { it[Keys.notificationGuideCompleted] = completed }
    }

    suspend fun setNotificationPermissionRequested(requested: Boolean = true) {
        context.userPreferencesDataStore.edit { it[Keys.notificationPermissionRequested] = requested }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.userPreferencesDataStore.edit { it[Keys.themeMode] = mode.name }
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.dynamicColor] = enabled }
    }

    suspend fun setDefaultReminderMinutes(minutes: Int) {
        context.userPreferencesDataStore.edit { it[Keys.defaultReminderMinutes] = minutes }
    }

    suspend fun setDefaultReminderTimeMinutes(minutes: Int) {
        context.userPreferencesDataStore.edit { it[Keys.defaultReminderTimeMinutes] = minutes }
    }

    suspend fun setDefaultExpandSteps(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.defaultExpandSteps] = enabled }
    }

    suspend fun setAutoCollapseCompletedSteps(enabled: Boolean) {
        context.userPreferencesDataStore.edit { it[Keys.autoCollapseCompletedSteps] = enabled }
    }

    suspend fun setAiBaseUrl(url: String) {
        context.userPreferencesDataStore.edit { it[Keys.aiBaseUrl] = url.trim() }
    }

    suspend fun setAiModel(model: String) {
        context.userPreferencesDataStore.edit { it[Keys.aiModel] = model.trim() }
    }
}
