package com.datenote.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.statusAfterStepChange
import com.datenote.app.reminder.NotificationAccess
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.ui.reminder.NotificationUnavailableDialog
import java.time.Instant
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneOffset

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ScheduleEditorScreen(
    scheduleId: Long?,
    repository: ScheduleRepository,
    defaultReminderMinutes: Int,
    reminderScheduler: ReminderScheduler,
    defaultReminderTimeMinutes: Int,
    onBack: () -> Unit,
    onSaved: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val viewModel: ScheduleEditorViewModel = viewModel(
        key = "editor-${scheduleId ?: "new"}",
        factory = ScheduleEditorViewModel.Factory(repository, scheduleId, defaultReminderMinutes, reminderScheduler, defaultReminderTimeMinutes),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val schedule = state.schedule
    if (state.isLoading || schedule == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var title by remember(schedule.id) { mutableStateOf(schedule.title) }
    var note by remember(schedule.id) { mutableStateOf(schedule.note) }
    var category by remember(schedule.id) { mutableStateOf(schedule.category.orEmpty()) }
    var startDate by remember(schedule.id) { mutableStateOf(LocalDate.ofEpochDay(schedule.startEpochDay)) }
    var endDate by remember(schedule.id) { mutableStateOf(LocalDate.ofEpochDay(maxOf(schedule.startEpochDay, schedule.endEpochDay))) }
    var timeText by remember(schedule.id) { mutableStateOf(schedule.minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty()) }
    var status by remember(schedule.id) { mutableStateOf(schedule.status) }
    var steps by remember(schedule.id) { mutableStateOf(state.steps) }
    var reminderEnabled by remember(schedule.id) { mutableStateOf(schedule.remindBeforeMinutes != null) }
    var titleSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var stepsSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var datePickerTarget by rememberSaveable { mutableStateOf<DateTarget?>(null) }
    var statusMenuVisible by remember { mutableStateOf(false) }
    var completionDialogVisible by remember { mutableStateOf(false) }
    var notificationUnavailable by rememberSaveable(schedule.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val titleError = titleSubmitted && title.trim().isEmpty()
    val stepsError = stepsSubmitted && steps.any { it.title.trim().isEmpty() }
    val parsedTime = remember(timeText) { parseTime(timeText) }
    val timeError = timeText.isNotBlank() && parsedTime == null
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(steps.size) {
        if (steps.lastOrNull()?.title.isNullOrEmpty()) focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(if (schedule.id == 0L) stringResource(R.string.new_schedule_title) else stringResource(R.string.edit_schedule_title))
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
            )
        },
    ) { padding ->
    Column(Modifier.fillMaxSize().padding(padding).imePadding().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; titleSubmitted = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.schedule_title_label)) },
            placeholder = { Text(stringResource(R.string.schedule_title_placeholder)) },
            isError = titleError,
            supportingText = { if (titleError) Text(stringResource(R.string.title_required)) },
        )
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.schedule_start_label), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { datePickerTarget = DateTarget.START }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.date_year_month_day, startDate.year, startDate.monthValue, startDate.dayOfMonth))
        }
        Spacer(Modifier.height(8.dp))
        Text(stringResource(R.string.schedule_end_label), style = MaterialTheme.typography.labelLarge)
        OutlinedButton(onClick = { datePickerTarget = DateTarget.END }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.date_year_month_day, endDate.year, endDate.monthValue, endDate.dayOfMonth))
        }
        Text(
            if (startDate == endDate) stringResource(R.string.single_day_summary, startDate.monthValue, startDate.dayOfMonth)
            else stringResource(R.string.date_range_summary, startDate.monthValue, startDate.dayOfMonth, endDate.monthValue, endDate.dayOfMonth, endDate.toEpochDay() - startDate.toEpochDay() + 1),
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.schedule_time_label)) },
            supportingText = { Text(if (timeError) stringResource(R.string.invalid_time) else stringResource(R.string.schedule_time_supporting)) },
            isError = timeError,
            placeholder = { Text(stringResource(R.string.schedule_time_placeholder)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = category, onValueChange = { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_category_label)) }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_note_label)) }, placeholder = { Text(stringResource(R.string.schedule_note_placeholder)) }, minLines = 3)
        Spacer(Modifier.height(16.dp))
        Text(stringResource(R.string.production_steps), style = MaterialTheme.typography.titleMedium)
        if (steps.isEmpty()) {
            Text(stringResource(R.string.no_steps_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { steps = listOf(newStep(schedule.id)) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_first_step)) }
        } else {
            steps.forEachIndexed { index, step ->
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = step.isCompleted,
                        onCheckedChange = { checked ->
                            val now = System.currentTimeMillis()
                            val updated = steps.mapIndexed { i, old -> if (i == index) old.copy(isCompleted = checked, completedAt = if (checked) now else null, updatedAt = now) else old }
                            steps = updated
                            status = statusAfterStepChange(status, updated)
                        },
                    )
                    OutlinedTextField(
                        value = step.title,
                        onValueChange = { value -> steps = steps.mapIndexed { i, old -> if (i == index) old.copy(title = value) else old } },
                        modifier = Modifier.weight(1f).then(if (index == steps.lastIndex && step.title.isEmpty()) Modifier.focusRequester(focusRequester) else Modifier),
                        label = { Text(stringResource(R.string.step_name)) },
                        singleLine = true,
                        isError = stepsError && step.title.trim().isEmpty(),
                    )
                    IconButton(enabled = index > 0, onClick = {
                        val reordered = steps.toMutableList()
                        val previous = reordered[index - 1]
                        reordered[index - 1] = reordered[index]
                        reordered[index] = previous
                        steps = reordered
                    }) { Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.move_step_up)) }
                    IconButton(enabled = index < steps.lastIndex, onClick = {
                        val reordered = steps.toMutableList()
                        val next = reordered[index + 1]
                        reordered[index + 1] = reordered[index]
                        reordered[index] = next
                        steps = reordered
                    }) { Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.move_step_down)) }
                    IconButton(onClick = { steps = steps.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_step)) }
                }
            }
            if (stepsError) Text(stringResource(R.string.step_required), color = MaterialTheme.colorScheme.error)
            TextButton(
                onClick = { if (steps.lastOrNull()?.title?.trim()?.isNotEmpty() != false) steps = steps + newStep(schedule.id) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.add_step)) }
        }
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.schedule_status_label), style = MaterialTheme.typography.labelLarge)
        Box {
            FilterChip(selected = false, onClick = { statusMenuVisible = true }, label = { Text(statusLabel(status)) })
            DropdownMenu(expanded = statusMenuVisible, onDismissRequest = { statusMenuVisible = false }) {
                ScheduleStatus.entries.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(statusLabel(option)) },
                        onClick = {
                            statusMenuVisible = false
                            if (option == ScheduleStatus.COMPLETED && steps.any { !it.isCompleted }) completionDialogVisible = true
                            else status = option
                        },
                    )
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            headlineContent = { Text(stringResource(R.string.schedule_reminder_label)) },
            supportingContent = {
                Text(
                    if (reminderEnabled) {
                        stringResource(
                            R.string.schedule_reminder_enabled_supporting,
                            reminderDescription(schedule.remindBeforeMinutes ?: defaultReminderMinutes.toLong()),
                        )
                    } else {
                        stringResource(R.string.schedule_reminder_disabled_supporting)
                    },
                )
            },
            trailingContent = { Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it }) },
        )
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                titleSubmitted = true
                stepsSubmitted = true
                val normalizedSteps = steps.map { it.copy(title = it.title.trim()) }
                if (title.trim().isNotEmpty() && !timeError && startDate <= endDate && normalizedSteps.none { it.title.isEmpty() }) {
                    val updated = schedule.copy(
                        title = title,
                        note = note,
                        category = category,
                        startEpochDay = startDate.toEpochDay(),
                        endEpochDay = endDate.toEpochDay(),
                        minuteOfDay = parsedTime?.let { it.hour * 60 + it.minute },
                        status = status,
                        remindBeforeMinutes = if (reminderEnabled) schedule.remindBeforeMinutes ?: defaultReminderMinutes.toLong() else null,
                    )
                    val notificationAvailable = NotificationAccess.status(context).canPost
                    viewModel.save(updated, normalizedSteps) {
                        if (reminderEnabled && !notificationAvailable) notificationUnavailable = true else onSaved(schedule.id == 0L)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save_schedule)) }
        Spacer(Modifier.height(20.dp))
    }
    }

    datePickerTarget?.let { target ->
        val initial = if (target == DateTarget.START) startDate else endDate
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (target == DateTarget.START) {
                            startDate = selected
                            if (endDate.isBefore(selected)) endDate = selected
                        } else if (!selected.isBefore(startDate)) endDate = selected
                    }
                    datePickerTarget = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { datePickerTarget = null }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
    if (completionDialogVisible) {
        AlertDialog(
            onDismissRequest = { completionDialogVisible = false },
            title = { Text(stringResource(R.string.incomplete_steps_title)) },
            text = { Text(stringResource(R.string.incomplete_steps_message)) },
            dismissButton = { TextButton(onClick = { completionDialogVisible = false }) { Text(stringResource(R.string.check_again)) } },
            confirmButton = {
                TextButton(onClick = {
                    val now = System.currentTimeMillis()
                    steps = steps.map { it.copy(isCompleted = true, completedAt = it.completedAt ?: now, updatedAt = now) }
                    status = ScheduleStatus.COMPLETED
                    completionDialogVisible = false
                }) { Text(stringResource(R.string.complete_all)) }
            },
        )
    }
    if (notificationUnavailable) {
        NotificationUnavailableDialog(
            onEnable = { notificationUnavailable = false; onRequestNotifications(); onSaved(schedule.id == 0L) },
            onLater = { notificationUnavailable = false; onSaved(schedule.id == 0L) },
        )
    }
}

private enum class DateTarget { START, END }

private fun newStep(scheduleId: Long) = ScheduleStepEntity(scheduleId = scheduleId, title = "", position = 0)

private fun parseTime(value: String): LocalTime? = if (value.isBlank()) null else runCatching { LocalTime.parse(value.trim()) }.getOrNull()

@Composable
private fun reminderDescription(minutes: Long): String = when {
    minutes % (7L * 24L * 60L) == 0L -> stringResource(R.string.reminder_before_weeks, minutes / (7L * 24L * 60L))
    minutes % (24L * 60L) == 0L -> stringResource(R.string.reminder_before_days, minutes / (24L * 60L))
    minutes % 60L == 0L -> stringResource(R.string.reminder_before_hours, minutes / 60L)
    else -> stringResource(R.string.reminder_before_minutes, minutes)
}

@Composable
private fun statusLabel(status: ScheduleStatus): String = when (status) {
    ScheduleStatus.TODO -> stringResource(R.string.status_todo)
    ScheduleStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
    ScheduleStatus.COMPLETED -> stringResource(R.string.status_completed)
}
