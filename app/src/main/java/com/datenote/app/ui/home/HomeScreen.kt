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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowForwardIos
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowBackIosNew
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.isOverdue
import com.datenote.app.domain.model.monthGrid
import com.datenote.app.domain.model.progress
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.ui.components.ExpandableScheduleCard
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.launch

@Composable
fun HomeScreen(
    nickname: String,
    repository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    defaultReminderTimeMinutes: Int,
    defaultExpandSteps: Boolean,
    showWelcome: Boolean,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val viewModel: HomeViewModel = viewModel(factory = HomeViewModel.Factory(repository, reminderScheduler, defaultReminderTimeMinutes))
    val month by viewModel.displayedMonth.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val monthSchedules by viewModel.monthSchedules.collectAsStateWithLifecycle()
    val allSchedules by viewModel.allSchedulesWithSteps.collectAsStateWithLifecycle()
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
                    allSchedules = allSchedules,
                    onMonthSettled = viewModel::setMonth,
                    onSelectDate = viewModel::selectDate,
                )
            }
            item {
                Text(
                    text = selectedDate?.let { date ->
                        if (date == LocalDate.now()) {
                            stringResource(R.string.today_schedules)
                        } else {
                            stringResource(
                                R.string.date_month_day,
                                date.monthValue,
                                date.dayOfMonth,
                            )
                        }
                    } ?: stringResource(R.string.select_day_to_view_schedules),
                    style = MaterialTheme.typography.titleLarge,
                )
            }
            if (selectedDate != null && selectedSchedules.isEmpty()) {
                item { EmptyDayState(isToday = selectedDate == LocalDate.now(), monthIsEmpty = monthSchedules.isEmpty()) }
            } else if (selectedDate != null) {
                items(selectedSchedules, key = { it.schedule.id }) { schedule ->
                    ExpandableScheduleCard(
                        schedule = schedule,
                        defaultExpandSteps = defaultExpandSteps,
                        onEdit = { onEdit(schedule.schedule.id) },
                        onToggleCompleted = {
                            if (schedule.schedule.status == ScheduleStatus.COMPLETED) {
                                viewModel.toggleCompleted(schedule)
                            } else if (schedule.progress.hasSteps && schedule.progress.completedCount < schedule.progress.totalCount) {
                                pendingComplete = schedule
                            } else {
                                viewModel.markCompleted(schedule, completeSteps = false)
                            }
                        },
                        onToggleStep = { step -> viewModel.toggleStep(schedule, step) },
                        onPostpone = { days -> viewModel.postpone(schedule, days) },
                        onDelete = { pendingDelete = schedule },
                    )
                }
            }
        }
        FloatingActionButton(
            onClick = onAdd,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp),
            containerColor = MaterialTheme.colorScheme.primaryContainer,
        ) {
            Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_schedule))
        }
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
                }) {
                    Text(stringResource(R.string.confirm_delete), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    pendingComplete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingComplete = null },
            title = { Text(stringResource(R.string.incomplete_steps_title)) },
            text = { Text(stringResource(R.string.incomplete_steps_message)) },
            dismissButton = {
                TextButton(onClick = { pendingComplete = null }) { Text(stringResource(R.string.check_again)) }
            },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.markCompleted(schedule, true)
                    pendingComplete = null
                }) { Text(stringResource(R.string.complete_all)) }
            },
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
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp)) {
            Text(stringResource(R.string.welcome_back, nickname), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.welcome_back_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

private const val CalendarAnchorPage = 10_000
private const val CalendarPageCount = 20_001

@Composable
private fun CalendarCard(
    month: YearMonth,
    selectedDate: LocalDate?,
    allSchedules: List<ScheduleWithSteps>,
    onMonthSettled: (YearMonth) -> Unit,
    onSelectDate: (LocalDate) -> Unit,
) {
    val pagerBaseMonth = remember { YearMonth.now() }
    val initialPage = remember(pagerBaseMonth, month) {
        CalendarAnchorPage + (month.toEpochMonth() - pagerBaseMonth.toEpochMonth()).toInt()
    }
    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { CalendarPageCount },
    )
    val scope = rememberCoroutineScope()
    val visibleMonth by remember(pagerBaseMonth, pagerState) {
        derivedStateOf {
            pagerBaseMonth.plusMonths((pagerState.currentPage - CalendarAnchorPage).toLong())
        }
    }
    val weekdayLabels = listOf(
        R.string.monday, R.string.tuesday, R.string.wednesday, R.string.thursday,
        R.string.friday, R.string.saturday, R.string.sunday,
    )

    LaunchedEffect(month) {
        val targetPage = CalendarAnchorPage + (month.toEpochMonth() - pagerBaseMonth.toEpochMonth()).toInt()
        if (targetPage != pagerState.currentPage) pagerState.animateScrollToPage(targetPage)
    }
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.settledPage }.collect { settledPage ->
            val targetMonth = pagerBaseMonth.plusMonths((settledPage - CalendarAnchorPage).toLong())
            onMonthSettled(targetMonth)
        }
    }

    Card(shape = RoundedCornerShape(24.dp)) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Text(
                    text = stringResource(R.string.month_title, visibleMonth.year, visibleMonth.monthValue),
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.weight(1f),
                )
                IconButton(
                    onClick = { onSelectDate(LocalDate.now()) },
                ) {
                    Icon(Icons.Default.Today, stringResource(R.string.go_to_today), Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage - 1) }
                    },
                ) {
                    Icon(Icons.Default.ArrowBackIosNew, stringResource(R.string.previous_month), Modifier.size(18.dp))
                }
                IconButton(
                    onClick = {
                        scope.launch { pagerState.animateScrollToPage(pagerState.currentPage + 1) }
                    },
                ) {
                    Icon(Icons.AutoMirrored.Filled.ArrowForwardIos, stringResource(R.string.next_month), Modifier.size(18.dp))
                }
            }
            Row(Modifier.fillMaxWidth()) {
                weekdayLabels.forEach { label ->
                    Text(
                        stringResource(label),
                        modifier = Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            Spacer(Modifier.height(4.dp))
            HorizontalPager(
                state = pagerState,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(312.dp),
                key = { it },
            ) { page ->
                val pageMonth = pagerBaseMonth.plusMonths((page - CalendarAnchorPage).toLong())
                val pageSchedules = schedulesForMonth(allSchedules, pageMonth)
                CalendarMonthPage(
                    month = pageMonth,
                    selectedDate = selectedDate,
                    schedules = pageSchedules,
                    onSelectDate = onSelectDate,
                )
            }
        }
    }
}

@Composable
private fun CalendarMonthPage(
    month: YearMonth,
    selectedDate: LocalDate?,
    schedules: List<ScheduleWithSteps>,
    onSelectDate: (LocalDate) -> Unit,
) {
    val counts = remember(schedules, month) {
        monthGrid(month).associateWith { date ->
            schedules.count { it.schedule.startEpochDay <= date.toEpochDay() && it.schedule.endEpochDay >= date.toEpochDay() }
        }
    }
    val today = LocalDate.now()
    Column(Modifier.fillMaxWidth()) {
        monthGrid(month).chunked(7).forEach { week ->
            Row(Modifier.fillMaxWidth()) {
                week.forEach { date ->
                    val count = counts[date] ?: 0
                    CalendarDay(
                        modifier = Modifier.weight(1f),
                        date = date,
                        inMonth = date.month == month.month,
                        isSelected = selectedDate != null && date == selectedDate,
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
    val hasCompleted = schedules.isNotEmpty() && schedules.all { it.schedule.status == ScheduleStatus.COMPLETED }
    val dotColor = when {
        hasOverdue -> MaterialTheme.colorScheme.error
        hasCompleted && count > 0 -> MaterialTheme.colorScheme.tertiary
        else -> MaterialTheme.colorScheme.primary
    }
    val dateDescription = stringResource(R.string.calendar_date_description, date.year, date.monthValue, date.dayOfMonth)
    Box(
        modifier = modifier
            .height(52.dp)
            .padding(2.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick)
            .semantics {
                contentDescription = dateDescription
            },
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

private fun schedulesForMonth(schedules: List<ScheduleWithSteps>, month: YearMonth): List<ScheduleWithSteps> {
    val start = month.atDay(1).toEpochDay()
    val end = month.atEndOfMonth().toEpochDay()
    return schedules.filter { it.schedule.startEpochDay <= end && it.schedule.endEpochDay >= start }
}

private fun YearMonth.toEpochMonth(): Long = year.toLong() * 12L + monthValue - 1L
