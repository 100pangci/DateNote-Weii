package com.datenote.app.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.remote.AiNotConfiguredException
import com.datenote.app.data.remote.AiNetworkException
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.ThemeMode
import com.datenote.app.data.repository.UserPreferences
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.data.secure.SecureApiKeyStore
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.data.local.ScheduleEntity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class ConnectionState { IDLE, TESTING, SUCCESS, FAILED }

data class SettingsState(
    val preferences: UserPreferences = UserPreferences(),
    val apiKey: String = "",
    val connectionState: ConnectionState = ConnectionState.IDLE,
)

class SettingsViewModel(
    private val preferencesRepository: UserPreferencesRepository,
    private val keyStore: SecureApiKeyStore,
    private val aiRepository: AiRepository,
    private val scheduleRepository: ScheduleRepository,
    private val reminderScheduler: ReminderScheduler,
) : ViewModel() {
    private val _state = MutableStateFlow(SettingsState(apiKey = keyStore.read()))
    val state: StateFlow<SettingsState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            preferencesRepository.preferences.collect { value -> _state.value = _state.value.copy(preferences = value) }
        }
    }

    fun setApiKey(value: String) { _state.value = _state.value.copy(apiKey = value, connectionState = ConnectionState.IDLE) }
    fun saveNickname(value: String) { viewModelScope.launch { preferencesRepository.setNickname(value.trim()) } }
    fun setThemeMode(value: ThemeMode) { viewModelScope.launch { preferencesRepository.setThemeMode(value) } }
    fun setDynamicColor(value: Boolean) { viewModelScope.launch { preferencesRepository.setDynamicColor(value) } }
    fun setDefaultReminder(value: Int) { viewModelScope.launch { preferencesRepository.setDefaultReminderMinutes(value) } }
    fun setDefaultReminderTime(value: Int) { viewModelScope.launch { preferencesRepository.setDefaultReminderTimeMinutes(value) } }

    fun saveAiSettings(baseUrl: String, model: String) {
        keyStore.save(_state.value.apiKey)
        viewModelScope.launch {
            preferencesRepository.setAiBaseUrl(baseUrl)
            preferencesRepository.setAiModel(model)
        }
    }

    fun testConnection(baseUrl: String, model: String, apiKey: String) {
        if (_state.value.connectionState == ConnectionState.TESTING) return
        keyStore.save(apiKey)
        viewModelScope.launch {
            preferencesRepository.setAiBaseUrl(baseUrl)
            preferencesRepository.setAiModel(model)
            _state.value = _state.value.copy(apiKey = apiKey, connectionState = ConnectionState.TESTING)
            _state.value = runCatching { aiRepository.testConnection() }
                .fold(
                    onSuccess = { _state.value.copy(connectionState = ConnectionState.SUCCESS) },
                    onFailure = { _state.value.copy(connectionState = ConnectionState.FAILED) },
                )
        }
    }

    fun clearAllSchedules(onFinished: () -> Unit) {
        viewModelScope.launch { scheduleRepository.deleteAll(); reminderScheduler.cancelAll(); onFinished() }
    }

    fun restoreSchedules(schedules: List<ScheduleEntity>, replace: Boolean, onFinished: (Boolean) -> Unit) {
        viewModelScope.launch {
            val result = runCatching {
                val ids = if (replace) {
                    reminderScheduler.cancelAll()
                    scheduleRepository.replaceAll(schedules)
                } else scheduleRepository.insertAll(schedules)
                schedules.forEachIndexed { index, schedule ->
                    reminderScheduler.sync(schedule.copy(id = ids[index]), _state.value.preferences.defaultReminderTimeMinutes)
                }
            }
            onFinished(result.isSuccess)
        }
    }

    class Factory(
        private val preferencesRepository: UserPreferencesRepository,
        private val keyStore: SecureApiKeyStore,
        private val aiRepository: AiRepository,
        private val scheduleRepository: ScheduleRepository,
        private val reminderScheduler: ReminderScheduler,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = SettingsViewModel(preferencesRepository, keyStore, aiRepository, scheduleRepository, reminderScheduler) as T
    }
}
