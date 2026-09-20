package com.datenote.app.ui.editor

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.reminder.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

data class EditorState(
    val isLoading: Boolean = true,
    val schedule: ScheduleEntity? = null,
)

class ScheduleEditorViewModel(
    private val repository: ScheduleRepository,
    private val scheduleId: Long?,
    private val defaultReminderMinutes: Int,
    private val reminderScheduler: ReminderScheduler,
    private val defaultReminderTimeMinutes: Int,
) : ViewModel() {
    private val _state = MutableStateFlow(EditorState())
    val state: StateFlow<EditorState> = _state.asStateFlow()

    init {
        viewModelScope.launch {
            val schedule = scheduleId?.let { repository.getById(it) }
                ?: ScheduleEntity(
                    title = "",
                    scheduledEpochDay = LocalDate.now().toEpochDay(),
                    status = ScheduleStatus.TODO,
                    remindBeforeMinutes = defaultReminderMinutes.toLong(),
                )
            _state.value = EditorState(isLoading = false, schedule = schedule)
        }
    }

    fun save(schedule: ScheduleEntity, onSaved: () -> Unit) {
        viewModelScope.launch {
            val normalized = schedule.copy(
                title = schedule.title.trim(),
                note = schedule.note.trim(),
                category = schedule.category?.trim()?.takeIf { it.isNotEmpty() },
                updatedAt = System.currentTimeMillis(),
            )
            val saved = if (normalized.id == 0L) {
                normalized.copy(id = repository.insert(normalized))
            } else {
                repository.update(normalized)
                normalized
            }
            reminderScheduler.sync(saved, defaultReminderTimeMinutes)
            onSaved()
        }
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val scheduleId: Long?,
        private val defaultReminderMinutes: Int,
        private val reminderScheduler: ReminderScheduler,
        private val defaultReminderTimeMinutes: Int,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T =
            ScheduleEditorViewModel(repository, scheduleId, defaultReminderMinutes, reminderScheduler, defaultReminderTimeMinutes) as T
    }
}
