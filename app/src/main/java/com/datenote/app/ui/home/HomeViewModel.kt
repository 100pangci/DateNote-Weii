package com.datenote.app.ui.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.sortForHome
import com.datenote.app.reminder.ReminderScheduler
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class HomeViewModel(
    private val repository: ScheduleRepository,
    private val reminderScheduler: ReminderScheduler,
    private val defaultReminderTimeMinutes: Int,
) : ViewModel() {
    private val initialDate = LocalDate.now()
    private val _selectedDate = MutableStateFlow<LocalDate?>(initialDate)
    private val _displayedMonth = MutableStateFlow(YearMonth.from(initialDate))
    private val selectedDatesByMonth = mutableMapOf(YearMonth.from(initialDate) to initialDate)

    val selectedDate: StateFlow<LocalDate?> = _selectedDate
    val displayedMonth: StateFlow<YearMonth> = _displayedMonth

    /**
     * Keep one observation for the calendar instead of starting a Room query for
     * every pager page while the user is dragging between months.
     */
    val allSchedulesWithSteps: StateFlow<List<ScheduleWithSteps>> = repository.observeAllWithSteps()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val monthSchedules: StateFlow<List<ScheduleWithSteps>> = combine(
        allSchedulesWithSteps,
        _displayedMonth,
    ) { schedules, value -> schedulesForMonth(schedules, value) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    val selectedSchedules: StateFlow<List<ScheduleWithSteps>> = combine(
        allSchedulesWithSteps,
        _selectedDate,
    ) { schedules, date ->
        date?.let {
            val epochDay = it.toEpochDay()
            schedules.filter { schedule ->
                schedule.schedule.startEpochDay <= epochDay && schedule.schedule.endEpochDay >= epochDay
            }
        }.orEmpty().sortForHome()
    }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    fun selectDate(date: LocalDate) {
        selectedDatesByMonth[YearMonth.from(date)] = date
        _selectedDate.value = date
        _displayedMonth.value = YearMonth.from(date)
    }

    fun changeMonth(delta: Long) {
        val next = _displayedMonth.value.plusMonths(delta)
        setMonth(next)
    }

    fun setMonth(value: YearMonth) {
        if (value == _displayedMonth.value) return
        _displayedMonth.value = value
        _selectedDate.value = selectedDatesByMonth[value]
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

    private fun schedulesForMonth(
        schedules: List<ScheduleWithSteps>,
        month: YearMonth,
    ): List<ScheduleWithSteps> {
        val start = month.atDay(1).toEpochDay()
        val end = month.atEndOfMonth().toEpochDay()
        return schedules.filter { it.schedule.startEpochDay <= end && it.schedule.endEpochDay >= start }
    }
}
