package com.datenote.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.ExperimentalCoroutinesApi

@OptIn(ExperimentalCoroutinesApi::class)
class HomeViewModel(
    private val repository: ScheduleRepository,
    private val reminderScheduler: ReminderScheduler,
    private val defaultReminderTimeMinutes: Int,
) : ViewModel() {
    private val selectedDate = MutableStateFlow(LocalDate.now())
    private val displayedMonth = MutableStateFlow(YearMonth.now())

    val selectedDay: StateFlow<LocalDate> = selectedDate
    val month: StateFlow<YearMonth> = displayedMonth

    val monthSchedules: StateFlow<List<ScheduleWithSteps>> = displayedMonth
        .flatMapLatest { value ->
            repository.observeForMonthWithSteps(
                value.atDay(1).toEpochDay(),
                value.atEndOfMonth().toEpochDay(),
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedSchedules: StateFlow<List<ScheduleWithSteps>> = combine(
        monthSchedules,
        selectedDate,
    ) { schedules, date ->
        val epochDay = date.toEpochDay()
        schedules.filter { it.schedule.startEpochDay <= epochDay && it.schedule.endEpochDay >= epochDay }
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        selectedDate.value = date
        if (YearMonth.from(date) != displayedMonth.value) displayedMonth.value = YearMonth.from(date)
    }

    fun changeMonth(delta: Long) {
        val next = displayedMonth.value.plusMonths(delta)
        displayedMonth.value = next
        selectedDate.value = next.atDay(selectedDate.value.dayOfMonth.coerceAtMost(next.lengthOfMonth()))
    }

    fun insert(schedule: ScheduleEntity, steps: List<ScheduleStepEntity> = emptyList(), onSaved: () -> Unit) {
        viewModelScope.launch {
            val id = repository.saveWithSteps(schedule, steps)
            reminderScheduler.sync(schedule.copy(id = id), defaultReminderTimeMinutes)
            onSaved()
        }
    }

    fun update(schedule: ScheduleEntity, onSaved: () -> Unit = {}) {
        viewModelScope.launch {
            repository.update(schedule)
            reminderScheduler.sync(schedule, defaultReminderTimeMinutes)
            onSaved()
        }
    }

    fun toggleCompleted(schedule: ScheduleWithSteps, completeSteps: Boolean = false) {
        val completed = schedule.schedule.status == ScheduleStatus.COMPLETED
        update(
            schedule.schedule.copy(
                status = if (completed) ScheduleStatus.TODO else ScheduleStatus.COMPLETED,
                completedAt = if (completed) null else System.currentTimeMillis(),
                updatedAt = System.currentTimeMillis(),
            ),
        )
    }

    fun markCompleted(schedule: ScheduleWithSteps, completeSteps: Boolean) {
        viewModelScope.launch {
            val updated = repository.setStatus(schedule.schedule.id, ScheduleStatus.COMPLETED, completeSteps)
            updated?.let { reminderScheduler.sync(it, defaultReminderTimeMinutes) }
        }
    }

    fun toggleStep(schedule: ScheduleWithSteps, step: ScheduleStepEntity) {
        viewModelScope.launch {
            val updated = repository.setStepCompleted(schedule.schedule.id, step.id, !step.isCompleted)
            updated?.let { reminderScheduler.sync(it, defaultReminderTimeMinutes) }
        }
    }

    fun postpone(schedule: ScheduleWithSteps, days: Long) {
        update(schedule.schedule.copy(startEpochDay = schedule.schedule.startEpochDay + days, endEpochDay = schedule.schedule.endEpochDay + days, updatedAt = System.currentTimeMillis()))
    }

    fun delete(schedule: ScheduleWithSteps) {
        viewModelScope.launch { repository.delete(schedule.schedule); reminderScheduler.cancel(schedule.schedule.id) }
    }

    class Factory(
        private val repository: ScheduleRepository,
        private val reminderScheduler: ReminderScheduler,
        private val defaultReminderTimeMinutes: Int,
    ) : ViewModelProvider.Factory {
        @Suppress("UNCHECKED_CAST")
        override fun <T : ViewModel> create(modelClass: Class<T>): T = HomeViewModel(repository, reminderScheduler, defaultReminderTimeMinutes) as T
    }
}
