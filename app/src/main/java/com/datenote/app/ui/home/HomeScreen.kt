package com.datenote.app.ui.home

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.isOverdue
import com.datenote.app.domain.model.monthGrid
import com.datenote.app.domain.model.progress
import com.datenote.app.domain.model.inclusiveDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

@Composable
fun HomeScreen(
    nickname: String,
    repository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    defaultReminderTimeMinutes: Int,
    showWelcome: Boolean,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository, reminderScheduler, defaultReminderTimeMinutes))
    val month by viewModel.month.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDay.collectAsStateWithLifecycle()
    val monthSchedules by viewModel.monthSchedules.collectAsStateWithLifecycle()
    val selectedSchedules by viewModel.selectedSchedules.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ScheduleWithSteps?>(null) }
    var pendingComplete by remember { mutableStateOf<ScheduleWithSteps?>(null) }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 20.dp, top = 22.dp, end = 20.dp, bottom = 96.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            item {
                GreetingBlock(nickname)
                if (showWelcome) {
                    Spacer(Modifier.height(12.dp))
                    WelcomeCard(nickname)
                }
            }
            item {
                CalendarCard(
                    month = month,
                    selectedDate = selectedDate,
                    schedules = monthSchedules,
                    onPrevious = { viewModel.changeMonth(-1) },
                    onNext = { viewModel.changeMonth(1) },
                    onSelectDate = viewModel::selectDate,
                )
            }
            item {
                Text(
                    text = if (selectedDate == LocalDate.now()) {
                        stringResource(R.string.today_schedules)
                    } else {
                        stringResource(R.string.date_month_day, selectedDate.monthValue, selectedDate.dayOfMonth)
                    },
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (selectedSchedules.isEmpty()) {
                item { EmptyDayState(isToday = selectedDate == LocalDate.now(), monthIsEmpty = monthSchedules.isEmpty()) }
            } else {
                items(selectedSchedules, key = { it.schedule.id }) { schedule ->
                    ScheduleCard(
                        schedule = schedule,
                        onClick = { onEdit(schedule.schedule.id) },
                        onToggleComplete = {
                            if (schedule.schedule.status == ScheduleStatus.COMPLETED) viewModel.toggleCompleted(schedule)
                            else if (schedule.progress.hasSteps && schedule.progress.completedCount < schedule.progress.totalCount) pendingComplete = schedule
                            else viewModel.markCompleted(schedule, completeSteps = false)
                        },
                        onPostpone = { viewModel.postpone(schedule, it) },
                        onDelete = { pendingDelete = schedule },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) { Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_schedule)) }
    }

    pendingDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            dismissButton = {
                TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.think_again)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(schedule)
                    pendingDelete = null
                }) { Text(stringResource(R.string.confirm_delete), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
    pendingComplete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingComplete = null },
            title = { Text(stringResource(R.string.incomplete_steps_title)) },
            text = { Text(stringResource(R.string.incomplete_steps_message)) },
            dismissButton = { TextButton(onClick = { pendingComplete = null }) { Text(stringResource(R.string.check_again)) } },
            confirmButton = { TextButton(onClick = { viewModel.markCompleted(schedule, true); pendingComplete = null }) { Text(stringResource(R.string.complete_all)) } },
        )
    }
}

@Composable
private fun GreetingBlock(nickname: String) {
    val hour = java.time.LocalTime.now().hour
    val greeting = when {
        hour < 12 -> stringResource(R.string.greeting_morning, nickname)
        hour < 18 -> stringResource(R.string.greeting_afternoon, nickname)
        else -> stringResource(R.string.greeting_evening, nickname)
    }
    val message = when {
        hour < 12 -> stringResource(R.string.greeting_morning_message)
        hour < 18 -> stringResource(R.string.greeting_afternoon_message)
        else -> stringResource(R.string.greeting_evening_message)
    }
    Column {
        Text(greeting, style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(4.dp))
        Text(message, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun WelcomeCard(nickname: String) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)) {
        Column(Modifier.padding(16.dp)) {
            Text(stringResource(R.string.welcome_back, nickname), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.welcome_back_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun CalendarCard(
    month: YearMonth,
    selectedDate: LocalDate,
    schedules: List<ScheduleWithSteps>,
    onPrevious: () -> Unit,
    onNext: () -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val counts = remember(schedules) {
        monthGrid(month).map { it.toEpochDay() }.associateWith { epochDay ->
            schedules.count { it.schedule.startEpochDay <= epochDay && it.schedule.endEpochDay >= epochDay }
        }
    }
    val today = LocalDate.now()
    val weekdayLabels = listOf(
        R.string.monday, R.string.tuesday, R.string.wednesday, R.string.thursday,
        R.string.friday, R.string.saturday, R.string.sunday,
    )
    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(stringResource(R.string.month_title, month.year, month.monthValue), style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                IconButton(onClick = onPrevious) { Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.previous_month), Modifier.size(18.dp)) }
                IconButton(onClick = onNext) { Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, stringResource(R.string.next_month), Modifier.size(18.dp)) }
            }
            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(stringResource(label), modifier = Modifier.weight(1f), textAlign = androidx.compose.ui.text.style.TextAlign.Center, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(Modifier.height(4.dp))
            monthGrid(month).chunked(7).forEach { week ->
                Row(Modifier.fillMaxWidth()) {
                    week.forEach { date ->
                        val count = counts[date.toEpochDay()] ?: 0
                        CalendarDay(
                            modifier = Modifier.weight(1f),
                            date = date,
                            inMonth = date.month == month.month,
                            isSelected = date == selectedDate,
                            isToday = date == today,
                            count = count,
                            schedules = schedules.filter { it.schedule.startEpochDay <= date.toEpochDay() && it.schedule.endEpochDay >= date.toEpochDay() },
                            onClick = { onSelectDate(date) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun CalendarDay(
    modifier: Modifier,
    date: LocalDate,
    inMonth: Boolean,
    isSelected: Boolean,
    isToday: Boolean,
    count: Int,
    schedules: List<ScheduleWithSteps>,
    onClick: () -> Unit,
) {
    val hasOverdue = schedules.any { isOverdue(it.schedule, LocalDate.now()) }
    val hasCompleted = schedules.all { it.schedule.status == ScheduleStatus.COMPLETED }
    val dotColor = when {
        hasOverdue -> MaterialTheme.colorScheme.error
        hasCompleted && count > 0 -> Color(0xFF5B8C5A)
        else -> MaterialTheme.colorScheme.primary
    }
    Box(
        modifier = modifier.height(52.dp).padding(2.dp).clip(CircleShape).clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        if (isSelected) Box(Modifier.size(38.dp).background(MaterialTheme.colorScheme.primaryContainer, CircleShape))
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text = date.dayOfMonth.toString(),
                color = when {
                    !inMonth -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.35f)
                    isToday -> MaterialTheme.colorScheme.primary
                    else -> MaterialTheme.colorScheme.onSurface
                },
                style = MaterialTheme.typography.bodyMedium,
            )
            if (count > 0) Box(Modifier.size(5.dp).background(dotColor, CircleShape))
            else Spacer(Modifier.size(5.dp))
        }
    }
}

@Composable
private fun EmptyDayState(isToday: Boolean, monthIsEmpty: Boolean) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f))) {
        Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                if (monthIsEmpty) stringResource(R.string.month_empty_title)
                else if (isToday) stringResource(R.string.today_empty)
                else stringResource(R.string.other_day_empty),
                style = MaterialTheme.typography.titleMedium,
            )
            if (monthIsEmpty) Text(stringResource(R.string.month_empty_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ScheduleCard(
    schedule: ScheduleWithSteps,
    onClick: () -> Unit,
    onToggleComplete: () -> Unit,
    onPostpone: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val today = LocalDate.now()
    val entity = schedule.schedule
    val overdue = isOverdue(entity, today)
    val completed = entity.status == ScheduleStatus.COMPLETED
    Card(
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
        colors = CardDefaults.cardColors(
            containerColor = if (completed) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            else MaterialTheme.colorScheme.surfaceContainer,
        ),
    ) {
        Row(Modifier.fillMaxWidth().padding(start = 8.dp, top = 12.dp, bottom = 12.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            Checkbox(checked = completed, onCheckedChange = { onToggleComplete() })
            Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                Text(entity.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(dateSummary(entity), color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                val detail = buildString {
                    entity.minuteOfDay?.let { append(String.format("%02d:%02d", it / 60, it % 60)) }
                    if (entity.minuteOfDay != null && entity.category != null) append(" · ")
                    entity.category?.let(::append)
                }
                if (detail.isNotBlank()) Text(detail, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (entity.note.isNotBlank()) Text(entity.note, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(scheduleProgressText(schedule), color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.labelMedium)
                if (schedule.progress.hasSteps) {
                    Text(stringResource(R.string.progress_completed, schedule.progress.completedCount, schedule.progress.totalCount), style = MaterialTheme.typography.labelMedium)
                    LinearProgressIndicator(progress = { schedule.progress.fraction }, modifier = Modifier.fillMaxWidth())
                    schedule.orderedSteps.firstOrNull { !it.isCompleted }?.let { next ->
                        Text(stringResource(R.string.next_step, next.title), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
                    }
                }
                if (entity.remindBeforeMinutes != null) Text(stringResource(R.string.reminder_enabled), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium)
            }
            Box {
                IconButton(onClick = { menuExpanded = true }) { Icon(Icons.Default.MoreVert, stringResource(R.string.schedule_more)) }
                DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                    DropdownMenuItem(
                        text = { Text(if (completed) stringResource(R.string.mark_todo) else stringResource(R.string.mark_completed)) },
                        onClick = { menuExpanded = false; onToggleComplete() },
                    )
                    DropdownMenuItem(text = { Text(stringResource(R.string.postpone_one_day)) }, onClick = { menuExpanded = false; onPostpone(1) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.postpone_one_week)) }, onClick = { menuExpanded = false; onPostpone(7) })
                    DropdownMenuItem(text = { Text(stringResource(R.string.delete_schedule)) }, onClick = { menuExpanded = false; onDelete() }, leadingIcon = { Icon(Icons.Default.DeleteOutline, null) })
                }
            }
        }
    }
}

private fun dateSummary(schedule: ScheduleEntity): String {
    val start = LocalDate.ofEpochDay(schedule.startEpochDay)
    val end = LocalDate.ofEpochDay(schedule.endEpochDay)
    return if (start == end) "${start.monthValue}月${start.dayOfMonth}日"
    else "${start.monthValue}月${start.dayOfMonth}日～${end.monthValue}月${end.dayOfMonth}日 · 共 ${inclusiveDays(schedule.startEpochDay, schedule.endEpochDay)} 天"
}

@Composable
private fun scheduleProgressText(schedule: ScheduleWithSteps): String {
    if (schedule.schedule.status == ScheduleStatus.COMPLETED) return stringResource(R.string.schedule_done)
    val today = LocalDate.now().toEpochDay()
    return when {
        today < schedule.schedule.startEpochDay -> stringResource(R.string.days_to_start, schedule.schedule.startEpochDay - today)
        today == schedule.schedule.endEpochDay -> stringResource(R.string.today_due)
        today > schedule.schedule.endEpochDay -> stringResource(R.string.days_overdue, today - schedule.schedule.endEpochDay)
        else -> stringResource(R.string.in_progress_remaining, schedule.schedule.endEpochDay - today)
    }
}
