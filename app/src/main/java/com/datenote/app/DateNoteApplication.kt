package com.datenote.app

import android.app.Application
import com.datenote.app.data.local.DateNoteDatabase
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.secure.SecureApiKeyStore
import com.datenote.app.reminder.ReminderNotifications
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.UserPreferencesRepository
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

class DateNoteApplication : Application() {
    private val applicationScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
        applyWeiiPresetIfNeeded()
    }

    val database: DateNoteDatabase by lazy { DateNoteDatabase.create(this) }
    val scheduleRepository: ScheduleRepository by lazy {
        ScheduleRepository(database.scheduleDao())
    }
    val userPreferencesRepository: UserPreferencesRepository by lazy {
        UserPreferencesRepository(this)
    }
    val secureApiKeyStore: SecureApiKeyStore by lazy { SecureApiKeyStore(this) }
    val aiRepository: AiRepository by lazy {
        AiRepository(userPreferencesRepository, secureApiKeyStore)
    }
    val reminderScheduler: ReminderScheduler by lazy { ReminderScheduler(this) }

    private fun applyWeiiPresetIfNeeded() {
        if (!BuildConfig.WEII_PRECONFIGURED) return
        applicationScope.launch {
            val preferences = userPreferencesRepository.preferences.first()
            val hasCustomAiConfiguration = secureApiKeyStore.read().isNotBlank() ||
                preferences.aiBaseUrl != com.datenote.app.data.repository.UserPreferences().aiBaseUrl ||
                preferences.aiModel != com.datenote.app.data.repository.UserPreferences().aiModel
            if (!hasCustomAiConfiguration) {
                secureApiKeyStore.save(BuildConfig.WEII_AI_API_KEY)
                userPreferencesRepository.setAiBaseUrl(BuildConfig.WEII_AI_BASE_URL)
                userPreferencesRepository.setAiModel(BuildConfig.WEII_AI_MODEL)
            }
        }
    }
}
