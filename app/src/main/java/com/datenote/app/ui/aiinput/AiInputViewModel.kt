package com.datenote.app.ui.aiinput

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.datenote.app.data.local.ScheduleTypeWithSteps
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.remote.AiNetworkException
import com.datenote.app.data.remote.AiNotConfiguredException
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.domain.parser.AiParseException
import com.datenote.app.domain.parser.ParsedSchedule
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.ui.components.ReorderableItem
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

enum class AiInputError { NOT_CONFIGURED, NETWORK, PARSE, EMPTY }
enum class AiSaveError { INVALID_DRAFT, SAVE_FAILED, REMINDER_SYNC_FAILED }

@Serializable
data class EditableAiDraft(
    val id: Long,
    val title: String,
    val startDate: String,
    val endDate: String,
    val time: String,
    val category: String,
    val note: String,
    val reminder: String,
    val reminderEnabled: Boolean,
    val steps: List<EditableAiStep>,
    val confidence: Double,
    val uncertainties: List<String>,
)

@Serializable
data class EditableAiStep(
    val id: Long,
    val title: String,
    val isCompleted: Boolean,
) : ReorderableItem {
    override val stableId: Long get() = id
}

data class AiBatchSaveResult(
    val saved: Boolean,
    val remindersScheduled: Boolean = true,
)

data class AiInputState(
    val input: String = "",
    val isProcessing: Boolean = false,
    val reviewing: Boolean = false,
    val warnings: List<String> = emptyList(),
    val drafts: List<EditableAiDraft> = emptyList(),
    val error: AiInputError? = null,
    val saveError: AiSaveError? = null,
    val isSaving: Boolean = false,
    val hasPendingReminderSync: Boolean = false,
    val scheduleTypes: List<ScheduleTypeWithSteps> = emptyList(),
)

class AiInputViewModel(
    private val aiRepository: AiRepository,
    private val scheduleRepository: ScheduleRepository,
    private val reminderScheduler: ReminderScheduler,
    private val preferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val saveGate = SubmissionGate()
    private val restored = savedStateHandle.get<String>(STATE_KEY)
        ?.let { runCatching { Json.decodeFromString<SavedAiInputDraft>(it) }.getOrNull() }
    private var pendingScheduleIds = savedStateHandle.get<String>(PENDING_IDS_KEY)
        ?.let { runCatching { Json.decodeFromString<List<Long>>(it) }.getOrNull() }
    private val _state = MutableStateFlow(
        (restored?.toState() ?: AiInputState(input = savedStateHandle[INPUT_KEY] ?: "")).copy(
            hasPendingReminderSync = pendingScheduleIds != null,
            saveError = if (pendingScheduleIds != null) AiSaveError.REMINDER_SYNC_FAILED else null,
        ),
    )
    val state: StateFlow<AiInputState> = _state.asStateFlow()
    private var parseJob: Job? = null
    private var nextStepId = restored?.drafts?.flatMap { it.steps }?.map { it.id }?.filter { it < 0L }?.minOrNull()?.let { it - 1L } ?: -1L

    init {
        viewModelScope.launch {
            scheduleRepository.observeScheduleTypes().collect { types ->
                _state.value = _state.value.copy(scheduleTypes = types)
            }
        }
    }

    fun setInput(value: String) {
        _state.value = _state.value.copy(input = value)
        savedStateHandle[INPUT_KEY] = value
    }

    fun submit() {
        val input = _state.value.input.trim()
        if (input.isBlank() || _state.value.isProcessing) return
        parseJob?.cancel()
        parseJob = viewModelScope.launch {
            _state.value = _state.value.copy(isProcessing = true, reviewing = false, error = null, saveError = null)
            val result = runCatching { aiRepository.parse(input) }
            result.fold(
                onSuccess = { parsed ->
                    if (parsed.items.isEmpty()) {
                        _state.value = _state.value.copy(isProcessing = false, error = AiInputError.EMPTY)
                    } else {
                        val availableTypes = scheduleRepository.observeScheduleTypes().first()
                        val defaultReminderMinutes = preferencesRepository.preferences.first().defaultReminderMinutes
                        val drafts = parsed.items.mapIndexed { index, item ->
                            val templateSteps = availableTypes.firstOrNull { it.type.name == item.category?.trim() }
                                ?.orderedSteps.orEmpty()
                            item.toEditable(defaultReminder = defaultReminderMinutes, id = index.toLong(), templateSteps = templateSteps.map { it.title })
                        }
                        val updated = _state.value.copy(
                            isProcessing = false,
                            reviewing = true,
                            warnings = parsed.warnings,
                            drafts = drafts,
                            error = null,
                            saveError = null,
                        )
                        _state.value = updated
                        persistDraftState(updated)
                    }
                },
                onFailure = { error ->
                    val kind = when (error) {
                        is AiNotConfiguredException -> AiInputError.NOT_CONFIGURED
                        is AiNetworkException -> AiInputError.NETWORK
                        is AiParseException -> AiInputError.PARSE
                        else -> AiInputError.NETWORK
                    }
                    _state.value = _state.value.copy(isProcessing = false, error = kind)
                },
            )
        }
    }

    fun updateDraft(index: Int, draft: EditableAiDraft) {
        val current = _state.value
        if (current.isSaving || current.hasPendingReminderSync || index !in current.drafts.indices) return
        val updated = current.copy(
            drafts = current.drafts.toMutableList().also { it[index] = draft },
            saveError = null,
        )
        _state.value = updated
        persistDraftState(updated)
    }

    fun removeDraft(index: Int) {
        val current = _state.value
        if (current.isSaving || current.hasPendingReminderSync || index !in current.drafts.indices) return
        val updated = current.copy(drafts = current.drafts.toMutableList().also { it.removeAt(index) }, saveError = null)
        _state.value = updated
        persistDraftState(updated)
    }

    fun addStep(index: Int) {
        val draft = _state.value.drafts.getOrNull(index) ?: return
        updateDraft(index, draft.copy(steps = draft.steps + EditableAiStep(nextStepId--, "", false)))
    }

    fun applyType(index: Int, type: ScheduleTypeWithSteps, replaceSteps: Boolean) {
        val draft = _state.value.drafts.getOrNull(index) ?: return
        val steps = if (replaceSteps) type.orderedSteps.map { EditableAiStep(nextStepId--, it.title, false) } else draft.steps
        updateDraft(index, draft.copy(category = type.type.name, steps = steps))
    }

    fun markInvalidDraft() {
        if (_state.value.hasPendingReminderSync) return
        _state.value = _state.value.copy(saveError = AiSaveError.INVALID_DRAFT)
    }

    fun startOver() {
        if (_state.value.isSaving || _state.value.hasPendingReminderSync) return
        parseJob?.cancel()
        val updated = _state.value.copy(reviewing = false, warnings = emptyList(), drafts = emptyList(), error = null, saveError = null)
        _state.value = updated
        persistDraftState(updated)
    }

    /** Reset only after the database batch committed, so a DB failure can be retried safely. */
    fun clearAfterSaved() {
        parseJob?.cancel()
        _state.value = AiInputState(scheduleTypes = _state.value.scheduleTypes)
        savedStateHandle.remove<String>(STATE_KEY)
        savedStateHandle.remove<String>(INPUT_KEY)
        savedStateHandle.remove<String>(PENDING_IDS_KEY)
        pendingScheduleIds = null
    }

    fun saveSchedules(schedules: List<ScheduleWithSteps>, onFinished: (AiBatchSaveResult) -> Unit) {
        if (schedules.isEmpty() || !saveGate.tryStart()) return
        _state.value = _state.value.copy(isSaving = true, saveError = null)
        viewModelScope.launch {
            var saved = pendingScheduleIds != null
            var remindersScheduled = true
            var cancelled = false
            try {
                val defaultReminderTime = preferencesRepository.preferences.first().defaultReminderTimeMinutes
                val ids = pendingScheduleIds ?: scheduleRepository.saveAllWithSteps(schedules).also { savedIds ->
                    saved = true
                    require(savedIds.size == schedules.size) { "Saved batch size does not match the draft" }
                    pendingScheduleIds = savedIds
                    savedStateHandle[PENDING_IDS_KEY] = Json.encodeToString(savedIds)
                    _state.value = _state.value.copy(hasPendingReminderSync = true)
                }
                require(ids.size == schedules.size) { "Saved batch size does not match the draft" }
                saved = true
                schedules.zip(ids).forEach { (draft, id) ->
                    try {
                        val persisted = scheduleRepository.getById(id) ?: draft.schedule.copy(id = id)
                        reminderScheduler.sync(persisted, defaultReminderTime)
                    } catch (_: Exception) {
                        remindersScheduled = false
                    }
                }
            } catch (error: CancellationException) {
                cancelled = true
                if (saved) remindersScheduled = false
                throw error
            } catch (_: Exception) {
                // saveAllWithSteps is one Room transaction, so a failed write leaves no partial rows.
                if (saved) remindersScheduled = false
            } finally {
                saveGate.finish()
                _state.value = _state.value.copy(
                    isSaving = false,
                    hasPendingReminderSync = saved && !remindersScheduled,
                    saveError = when {
                        !saved -> AiSaveError.SAVE_FAILED
                        !remindersScheduled -> AiSaveError.REMINDER_SYNC_FAILED
                        else -> null
                    },
                )
                if (!cancelled) onFinished(AiBatchSaveResult(saved, remindersScheduled))
            }
        }
    }

    private fun persistDraftState(value: AiInputState) {
        savedStateHandle[STATE_KEY] = Json.encodeToString(
            SavedAiInputDraft(
                input = value.input,
                reviewing = value.reviewing,
                warnings = value.warnings,
                drafts = value.drafts,
            ),
        )
    }

    class Factory(
        private val aiRepository: AiRepository,
        private val scheduleRepository: ScheduleRepository,
        private val reminderScheduler: ReminderScheduler,
        private val preferencesRepository: UserPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            AiInputViewModel(
                aiRepository = aiRepository,
                scheduleRepository = scheduleRepository,
                reminderScheduler = reminderScheduler,
                preferencesRepository = preferencesRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
    }

    private companion object {
        const val INPUT_KEY = "ai_input"
        const val STATE_KEY = "ai_review_draft"
        const val PENDING_IDS_KEY = "ai_pending_schedule_ids"
    }
}

@Serializable
private data class SavedAiInputDraft(
    val input: String,
    val reviewing: Boolean,
    val warnings: List<String>,
    val drafts: List<EditableAiDraft>,
) {
    fun toState(): AiInputState = AiInputState(input = input, reviewing = reviewing, warnings = warnings, drafts = drafts)
}

internal class SubmissionGate {
    private var active = false

    @Synchronized
    fun tryStart(): Boolean {
        if (active) return false
        active = true
        return true
    }

    @Synchronized
    fun finish() {
        active = false
    }
}

private fun ParsedSchedule.toEditable(defaultReminder: Int, id: Long, templateSteps: List<String>): EditableAiDraft = EditableAiDraft(
    id = id,
    title = title,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    time = time?.toString()?.take(5).orEmpty(),
    category = category.orEmpty(),
    note = note,
    reminder = (remindBeforeMinutes ?: defaultReminder.toLong()).toString(),
    reminderEnabled = remindBeforeMinutes != null,
    steps = if (steps.isEmpty()) templateSteps.mapIndexed { index, title -> EditableAiStep(index.toLong(), title, false) }
    else steps.mapIndexed { index, step -> EditableAiStep(index.toLong(), step.title, step.isCompleted) },
    confidence = confidence,
    uncertainties = uncertainties,
)
