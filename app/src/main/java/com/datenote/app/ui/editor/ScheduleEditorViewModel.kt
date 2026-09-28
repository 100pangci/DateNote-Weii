package com.datenote.app.ui.editor

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.CreationExtras
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleTypeWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.reminder.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

data class EditorState(
    val isLoading: Boolean = true,
    val schedule: ScheduleEntity? = null,
    val draft: ScheduleEditorDraft? = null,
    val scheduleTypes: List<ScheduleTypeWithSteps> = emptyList(),
    val isSaving: Boolean = false,
    val saveFailed: Boolean = false,
)

class ScheduleEditorViewModel(
    private val repository: ScheduleRepository,
    private val scheduleId: Long?,
    private val defaultReminderMinutes: Int,
    private val reminderScheduler: ReminderScheduler,
    private val preferencesRepository: UserPreferencesRepository,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()
    private var nextTransientStepId = -1L
    private val saveGate = SubmissionGate()

    init {
        val expectedId = scheduleId ?: 0L
        val restoredDraft = savedStateHandle.get<String>(DRAFT_KEY)
            ?.let { runCatching { Json.decodeFromString<SavedEditorDraft>(it) }.getOrNull() }
            ?.takeIf { it.scheduleId == expectedId }
            ?.toEditorDraft()
        restoredDraft?.steps?.map { it.rowId }?.filter { it < 0L }?.minOrNull()?.let {
            nextTransientStepId = minOf(nextTransientStepId, it - 1L)
        }

        viewModelScope.launch {
            val loaded = scheduleId?.let { repository.getWithSteps(it) }
            val schedule = loaded?.schedule ?: ScheduleEntity(
                title = "",
                startEpochDay = LocalDate.now().toEpochDay(),
                status = ScheduleStatus.TODO,
                remindBeforeMinutes = defaultReminderMinutes.toLong(),
            )
            val initialDraft = restoredDraft ?: ScheduleEditorDraft(
                title = schedule.title,
                note = schedule.note,
                category = schedule.category.orEmpty(),
                startEpochDay = schedule.startEpochDay,
                endEpochDay = maxOf(schedule.startEpochDay, schedule.endEpochDay),
                minuteOfDay = schedule.minuteOfDay,
                status = schedule.status,
                steps = loaded?.orderedSteps.orEmpty().map(::newStepDraft),
                reminderEnabled = schedule.remindBeforeMinutes != null,
                reminderText = (schedule.remindBeforeMinutes ?: defaultReminderMinutes.toLong()).toString(),
            )
            if (restoredDraft == null) persistDraft(expectedId, initialDraft)
            _state.value = EditorState(
                isLoading = false,
                schedule = schedule,
                draft = initialDraft,
                scheduleTypes = _state.value.scheduleTypes,
            )
        }
        viewModelScope.launch {
            repository.observeScheduleTypes().collect { types ->
                _state.value = _state.value.copy(scheduleTypes = types)
            }
        }
    }

    fun updateDraft(transform: (ScheduleEditorDraft) -> ScheduleEditorDraft) {
        val current = _state.value
        val draft = current.draft ?: return
        if (current.isSaving) return
        val updated = transform(draft)
        _state.value = current.copy(draft = updated, saveFailed = false)
        persistDraft(scheduleId ?: 0L, updated)
    }

    fun addStep() {
        updateDraft { draft ->
            val scheduleDbId = _state.value.schedule?.id ?: 0L
            draft.copy(steps = draft.steps + newStepDraft(ScheduleStepEntity(scheduleId = scheduleDbId, title = "", position = draft.steps.size)))
        }
    }

    fun stepsFromType(type: ScheduleTypeWithSteps, targetScheduleId: Long): List<EditorStepDraft> =
        type.toScheduleSteps(targetScheduleId).map(::newStepDraft)

    fun save(schedule: ScheduleEntity, steps: List<ScheduleStepEntity>, onSaved: () -> Unit) {
        if (!saveGate.tryStart()) return
        _state.value = _state.value.copy(isSaving = true, saveFailed = false)
        viewModelScope.launch {
            try {
                val now = System.currentTimeMillis()
                val normalized = schedule.copy(
                    title = schedule.title.trim(),
                    note = schedule.note.trim(),
                    category = schedule.category?.trim()?.takeIf { it.isNotEmpty() },
                    endEpochDay = maxOf(schedule.startEpochDay, schedule.endEpochDay),
                    updatedAt = now,
                )
                val normalizedSteps = steps.mapIndexed { index, step ->
                    step.copy(
                        id = step.id.takeIf { it > 0L } ?: 0L,
                        title = step.title.trim(),
                        position = index,
                        updatedAt = now,
                    )
                }
                val pendingId = savedStateHandle.get<Long>(PENDING_SAVED_ID)
                val toSave = if (normalized.id == 0L && pendingId != null) normalized.copy(id = pendingId) else normalized
                val id = repository.saveWithSteps(toSave, normalizedSteps)
                if (schedule.id == 0L) savedStateHandle[PENDING_SAVED_ID] = id
                val latestReminderTime = preferencesRepository.preferences.first().defaultReminderTimeMinutes
                val persisted = repository.getById(id) ?: normalized.copy(id = id)
                reminderScheduler.sync(persisted, latestReminderTime)
                savedStateHandle.remove<Long>(PENDING_SAVED_ID)
                _state.value = _state.value.copy(isSaving = false)
                onSaved()
            } catch (error: CancellationException) {
                throw error
            } catch (_: Exception) {
                _state.value = _state.value.copy(isSaving = false, saveFailed = true)
            } finally {
                saveGate.finish()
            }
        }
    }

    private fun newStepDraft(step: ScheduleStepEntity): EditorStepDraft = EditorStepDraft(
        rowId = if (step.id != 0L) step.id else nextTransientStepId--,
        step = step,
    )

    private fun persistDraft(id: Long, draft: ScheduleEditorDraft) {
        savedStateHandle[DRAFT_KEY] = Json.encodeToString(draft.toSavedSnapshot(id))
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val scheduleId: Long?,
        private val defaultReminderMinutes: Int,
        private val reminderScheduler: ReminderScheduler,
        private val preferencesRepository: UserPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T =
            ScheduleEditorViewModel(
                repository = repository,
                scheduleId = scheduleId,
                defaultReminderMinutes = defaultReminderMinutes,
                reminderScheduler = reminderScheduler,
                preferencesRepository = preferencesRepository,
                savedStateHandle = extras.createSavedStateHandle(),
            ) as T
    }

    private companion object {
        const val DRAFT_KEY = "schedule_editor_draft"
        const val PENDING_SAVED_ID = "schedule_editor_pending_saved_id"
    }
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
