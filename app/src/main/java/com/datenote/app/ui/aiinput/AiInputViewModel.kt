package com.datenote.app.ui.aiinput

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.remote.AiNetworkException
import com.datenote.app.data.remote.AiNotConfiguredException
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.domain.parser.AiParseException
import com.datenote.app.domain.parser.ValidatedAiResult
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.reminder.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

enum class AiInputError { NOT_CONFIGURED, NETWORK, PARSE, EMPTY }

data class AiInputState(
    val input: String = "",
    val isProcessing: Boolean = false,
    val result: ValidatedAiResult? = null,
    val error: AiInputError? = null,
)

class AiInputViewModel(
    private val aiRepository: AiRepository,
    private val scheduleRepository: ScheduleRepository,
    private val reminderScheduler: ReminderScheduler,
    private val defaultReminderTimeMinutes: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(AiInputState())
    val state: StateFlow<AiInputState> = _state.asStateFlow()
    private var parseJob: Job? = null

    fun setInput(value: String) { _state.value = _state.value.copy(input = value) }

    fun submit() {
        val input = _state.value.input.trim()
        if (input.isBlank() || _state.value.isProcessing) return
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, result = null, error = null)
            val result = runCatching { aiRepository.parse(input) }
            _state.value = result.fold(
                onSuccess = { parsed ->
                    if (parsed.items.isEmpty()) _state.value.copy(isProcessing = false, error = AiInputError.EMPTY)
                    else _state.value.copy(isProcessing = false, result = parsed)
                },
                onFailure = { error ->
                    val kind = when (error) {
                        is AiNotConfiguredException -> AiInputError.NOT_CONFIGURED
                        is AiNetworkException -> AiInputError.NETWORK
                        is AiParseException -> AiInputError.PARSE
                        else -> AiInputError.NETWORK
                    }
                    _state.value.copy(isProcessing = false, error = kind)
                },
            )
        }
    }

    fun startOver() {
        parseJob?.cancel()
        _state.value = _state.value.copy(result = null, error = null)
    }

    fun saveSchedules(schedules: List<ScheduleEntity>, onFinished: (Boolean) -> Unit) {
        if (schedules.isEmpty()) return onFinished(false)
        viewModelScope.launch {
            val result = runCatching { scheduleRepository.insertAll(schedules) }
            result.onSuccess { ids ->
                schedules.forEachIndexed { index, schedule ->
                    reminderScheduler.sync(schedule.copy(id = ids[index]), defaultReminderTimeMinutes)
                }
            }
            onFinished(result.isSuccess)
        }
    }

    class Factory(
        private val aiRepository: AiRepository,
        private val scheduleRepository: ScheduleRepository,
        private val reminderScheduler: ReminderScheduler,
        private val defaultReminderTimeMinutes: Int,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AiInputViewModel(aiRepository, scheduleRepository, reminderScheduler, defaultReminderTimeMinutes) as T
    }
}
