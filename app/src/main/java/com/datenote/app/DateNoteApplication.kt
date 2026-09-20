package com.datenote.app

import android.app.Application
import com.datenote.app.data.local.DateNoteDatabase
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.secure.SecureApiKeyStore
import com.datenote.app.reminder.ReminderNotifications
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.UserPreferencesRepository

class DateNoteApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ReminderNotifications.createChannel(this)
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
}
