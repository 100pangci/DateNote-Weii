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
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.local.ScheduleTypeWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.reminder.ReminderScheduler
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

enum class AiInputError { NOT_CONFIGURED, NETWORK, PARSE, EMPTY }

data class AiInputState(
    val input: String = "",
    val isProcessing: Boolean = false,
    val result: ValidatedAiResult? = null,
    val error: AiInputError? = null,
    val scheduleTypes: List<ScheduleTypeWithSteps> = emptyList(),
    val matchedTemplateSteps: Map<Int, List<String>> = emptyMap(),
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

    init {
        viewModelScope.launch {
            scheduleRepository.observeScheduleTypes().collect { types ->
                _state.value = _state.value.copy(scheduleTypes = types)
            }
        }
    }

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
                    else {
                        val availableTypes = repositoryTypes()
                        val matched = parsed.items.mapIndexedNotNull { index, item ->
                            val type = availableTypes.firstOrNull { it.type.name == item.category?.trim() }
                            if (type != null && item.steps.isEmpty()) index to type.orderedSteps.map { it.title } else null
                        }.toMap()
                        _state.value.copy(isProcessing = false, result = parsed, matchedTemplateSteps = matched)
                    }
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

    private suspend fun repositoryTypes() = scheduleRepository.observeScheduleTypes().first()

    fun startOver() {
        parseJob?.cancel()
        _state.value = _state.value.copy(result = null, error = null, matchedTemplateSteps = emptyMap())
    }

    /** Reset the draft and the source text only after schedules were saved successfully. */
    fun clearAfterSaved() {
        parseJob?.cancel()
        _state.value = AiInputState()
    }

    fun saveSchedules(schedules: List<ScheduleWithSteps>, onFinished: (Boolean) -> Unit) {
        if (schedules.isEmpty()) return onFinished(false)
        viewModelScope.launch {
            val result = runCatching {
                schedules.map { draft ->
                    val id = scheduleRepository.saveWithSteps(draft.schedule, draft.orderedSteps)
                    reminderScheduler.sync(draft.schedule.copy(id = id), defaultReminderTimeMinutes)
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
