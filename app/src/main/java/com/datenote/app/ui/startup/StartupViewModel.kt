package com.datenote.app.ui.startup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.repository.UserPreferences
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.domain.model.ScheduleStatus
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface AppStartupState {
    data object Loading : AppStartupState
    data object NeedsOnboarding : AppStartupState
    data object Ready : AppStartupState
}

class StartupViewModel(
    private val repository: UserPreferencesRepository,
    private val scheduleRepository: ScheduleRepository,
) : ViewModel() {
    private val _startupState = MutableStateFlow<AppStartupState>(AppStartupState.Loading)
    val startupState: StateFlow<AppStartupState> = _startupState.asStateFlow()

    private val _preferences = MutableStateFlow(UserPreferences())
    val preferences: StateFlow<UserPreferences> = _preferences.asStateFlow()

    private val _showWelcome = MutableStateFlow(false)
    val showWelcome: StateFlow<Boolean> = _showWelcome.asStateFlow()

    private val _reminderState = MutableStateFlow<StartupReminderState>(StartupReminderState.Hidden)
    val reminderState: StateFlow<StartupReminderState> = _reminderState.asStateFlow()
    private var reminderLoaded = false

    init {
        viewModelScope.launch {
            repository.preferences.collect { value ->
                _preferences.value = value
                _startupState.value = if (value.nickname.isBlank()) {
                    AppStartupState.NeedsOnboarding
                } else {
                    AppStartupState.Ready
                }
            }
        }
    }

    fun saveNickname(value: String) {
        val nickname = value.trim()
        if (nickname.isNotEmpty() && nickname.codePointCount(0, nickname.length) <= 12) {
            _showWelcome.value = true
            viewModelScope.launch { repository.setNickname(nickname) }
        }
    }

    fun loadStartupReminder() {
        if (reminderLoaded || _startupState.value != AppStartupState.Ready) return
        reminderLoaded = true
        if (_showWelcome.value) return
        _reminderState.value = StartupReminderState.Loading
        viewModelScope.launch {
            runCatching { scheduleRepository.observeIncomplete().first() }
                .onSuccess { schedules ->
                    _reminderState.value = if (schedules.any { it.status != ScheduleStatus.COMPLETED }) {
                        StartupReminderState.Show(schedules.filter { it.status != ScheduleStatus.COMPLETED })
                    } else StartupReminderState.Hidden
                }
                .onFailure { _reminderState.value = StartupReminderState.Hidden }
        }
    }

    fun dismissStartupReminder() { _reminderState.value = StartupReminderState.Hidden }

    class Factory(
        private val repository: UserPreferencesRepository,
        private val scheduleRepository: ScheduleRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            StartupViewModel(repository, scheduleRepository) as T
    }
}

sealed interface StartupReminderState {
    data object Hidden : StartupReminderState
    data object Loading : StartupReminderState
    data class Show(val schedules: List<ScheduleEntity>) : StartupReminderState
}
