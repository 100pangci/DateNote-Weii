package com.datenote.app.ui.all

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.reminder.ReminderScheduler
import java.time.LocalDate
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

enum class ScheduleFilter { ALL, RECENT, TODAY, OVERDUE, COMPLETED }

class AllSchedulesViewModel(
    private val repository: ScheduleRepository,
    private val reminderScheduler: ReminderScheduler,
    private val defaultReminderTimeMinutes: Int,
) : ViewModel() {
    private val selectedFilter = MutableStateFlow(ScheduleFilter.ALL)
    private val searchQuery = MutableStateFlow("")

    val filter: StateFlow<ScheduleFilter> = selectedFilter
    val query: StateFlow<String> = searchQuery
    private val all = repository.observeAll()

    val schedules: StateFlow<List<ScheduleEntity>> = combine(all, selectedFilter, searchQuery) { values, filter, query ->
        val today = LocalDate.now().toEpochDay()
        val normalizedQuery = query.trim()
        values.asSequence()
            .filter { normalizedQuery.isBlank() || it.title.contains(normalizedQuery, true) || it.note.contains(normalizedQuery, true) }
            .filter {
                when (filter) {
                    ScheduleFilter.ALL -> true
                    ScheduleFilter.RECENT -> it.status != ScheduleStatus.COMPLETED && it.scheduledEpochDay in today..(today + 30)
                    ScheduleFilter.TODAY -> it.scheduledEpochDay == today
                    ScheduleFilter.OVERDUE -> it.status != ScheduleStatus.COMPLETED && it.scheduledEpochDay < today
                    ScheduleFilter.COMPLETED -> it.status == ScheduleStatus.COMPLETED
                }
            }
            .toList()
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun setFilter(value: ScheduleFilter) { selectedFilter.value = value }
    fun setQuery(value: String) { searchQuery.value = value }

    fun toggleCompleted(schedule: ScheduleEntity) {
        val completed = schedule.status == ScheduleStatus.COMPLETED
        viewModelScope.launch {
            val updated = schedule.copy(
                status = if (completed) ScheduleStatus.TODO else ScheduleStatus.COMPLETED,
                completedAt = if (completed) null else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            )
            repository.update(updated)
            reminderScheduler.sync(updated, defaultReminderTimeMinutes)
        }
    }

    fun delete(schedule: ScheduleEntity) { viewModelScope.launch { repository.delete(schedule); reminderScheduler.cancel(schedule.id) } }

    class Factory(
        private val repository: ScheduleRepository,
        private val reminderScheduler: ReminderScheduler,
        private val defaultReminderTimeMinutes: Int,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = AllSchedulesViewModel(repository, reminderScheduler, defaultReminderTimeMinutes) as T
    }
}
