package com.datenote.app.ui.all

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.sortForAll
import com.datenote.app.reminder.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScheduleFilter { ALL, UNCOMPLETED, RECENT, TODAY, OVERDUE, COMPLETED }

class AllSchedulesViewModel(
    private val repository: ScheduleRepository,
    private val reminderScheduler: ReminderScheduler,
    private val preferencesRepository: UserPreferencesRepository,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(ScheduleFilter.ALL)
    private val searchQuery = MutableStateFlow("")

    val filter: StateFlow<ScheduleFilter> = selectedFilter
    val query: StateFlow<String> = searchQuery
    private val all = repository.observeAll()

    val schedules: StateFlow<List<ScheduleWithSteps>> = combine(repository.observeAllWithSteps(), selectedFilter, searchQuery) { values, filter, query ->
        val today = LocalDate.now().toEpochDay()
        val normalizedQuery = query.trim()
        values.asSequence()
            .filter { normalizedQuery.isBlank() || it.schedule.title.contains(normalizedQuery, true) || it.schedule.note.contains(normalizedQuery, true) }
            .filter {
                when (filter) {
                    ScheduleFilter.ALL -> true
                    ScheduleFilter.UNCOMPLETED -> it.schedule.status != ScheduleStatus.COMPLETED
                    ScheduleFilter.RECENT -> it.schedule.status != ScheduleStatus.COMPLETED && it.schedule.startEpochDay <= today + 30 && it.schedule.endEpochDay >= today
                    ScheduleFilter.TODAY -> it.schedule.startEpochDay <= today && it.schedule.endEpochDay >= today
                    ScheduleFilter.OVERDUE -> it.schedule.status != ScheduleStatus.COMPLETED && it.schedule.endEpochDay < today
                    ScheduleFilter.COMPLETED -> it.schedule.status == ScheduleStatus.COMPLETED
                }
            }
            .toList()
            .sortForAll(today)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(value: ScheduleFilter) { selectedFilter.value = value }
    fun setQuery(value: String) { searchQuery.value = value }

    fun toggleCompleted(schedule: ScheduleWithSteps) {
        val completed = schedule.schedule.status == ScheduleStatus.COMPLETED
        viewModelScope.launch {
            val updated = schedule.schedule.copy(
                status = if (completed) ScheduleStatus.TODO else ScheduleStatus.COMPLETED,
                completedAt = if (completed) null else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            repository.update(updated)
            syncReminder(updated)
        }
    }

    fun markCompleted(schedule: ScheduleWithSteps, completeSteps: Boolean) {
        viewModelScope.launch {
            repository.setStatus(schedule.schedule.id, ScheduleStatus.COMPLETED, completeSteps)?.let {
                syncReminder(it)
            }
        }
    }

    fun toggleStep(schedule: ScheduleWithSteps, step: ScheduleStepEntity) {
        viewModelScope.launch {
            repository.setStepCompleted(schedule.schedule.id, step.id, !step.isCompleted)?.let {
                syncReminder(it)
            }
        }
    }

    fun postpone(schedule: ScheduleWithSteps, days: Long) {
        viewModelScope.launch {
            val updated = schedule.schedule.copy(
                startEpochDay = schedule.schedule.startEpochDay + days,
                endEpochDay = schedule.schedule.endEpochDay + days,
                updatedAt = System.currentTimeMillis(),
            )
            repository.update(updated)
            syncReminder(updated)
        }
    }

    fun delete(schedule: ScheduleWithSteps) { viewModelScope.launch { repository.delete(schedule.schedule); reminderScheduler.cancel(schedule.schedule.id) } }

    private suspend fun syncReminder(schedule: ScheduleEntity) {
        val reminderTime = preferencesRepository.preferences.first().defaultReminderTimeMinutes
        reminderScheduler.sync(schedule, reminderTime)
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val reminderScheduler: ReminderScheduler,
        private val preferencesRepository: UserPreferencesRepository,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AllSchedulesViewModel(repository, reminderScheduler, preferencesRepository) as T
    }
}
