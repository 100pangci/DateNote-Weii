package com.datenote.app.ui.editor

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.domain.model.ScheduleStatus
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
    var date by remember(schedule.id) { mutableStateOf(LocalDate.ofEpochDay(schedule.scheduledEpochDay)) }
    var timeText by remember(schedule.id) {
        mutableStateOf(schedule.minuteOfDay?.let { "%02d:%02d".format(it / 60, it % 60) }.orEmpty())
    }
    var status by remember(schedule.id) { mutableStateOf(schedule.status) }
    var reminderEnabled by remember(schedule.id) { mutableStateOf(schedule.remindBeforeMinutes != null) }
    var titleSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var datePickerVisible by rememberSaveable { mutableStateOf(false) }
    var statusMenuVisible by remember { mutableStateOf(false) }
    val titleError = titleSubmitted && title.trim().isEmpty()
    val parsedTime = remember(timeText) { parseTime(timeText) }
    val timeError = timeText.isNotBlank() && parsedTime == null

    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 20.dp, vertical = 16.dp)) {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            TextButton(onClick = onBack) { Text(stringResource(R.string.cancel)) }
            Text(
                if (schedule.id == 0L) stringResource(R.string.new_schedule_title) else stringResource(R.string.edit_schedule_title),
                style = MaterialTheme.typography.titleLarge,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = title,
            onValueChange = { title = it; titleSubmitted = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.schedule_title_label)) },
            placeholder = { Text(stringResource(R.string.schedule_title_placeholder)) },
            isError = titleError,
            supportingText = { if (titleError) Text(stringResource(R.string.title_required)) },
            singleLine = false,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.schedule_date_label), style = MaterialTheme.typography.labelLarge)
        Spacer(Modifier.height(4.dp))
        OutlinedButton(onClick = { datePickerVisible = true }, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.date_year_month_day, date.year, date.monthValue, date.dayOfMonth))
        }
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = timeText,
            onValueChange = { timeText = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.schedule_time_label)) },
            supportingText = { Text(if (timeError) stringResource(R.string.invalid_time) else stringResource(R.string.schedule_time_supporting)) },
            isError = timeError,
            placeholder = { Text("09:30") },
            singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Ascii),
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(value = category, onValueChange = { category = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_category_label)) }, singleLine = true)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = note,
            onValueChange = { note = it },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.schedule_note_label)) },
            placeholder = { Text(stringResource(R.string.schedule_note_placeholder)) },
            minLines = 3,
        )
        Spacer(Modifier.height(12.dp))
        Text(stringResource(R.string.schedule_status_label), style = MaterialTheme.typography.labelLarge)
        Box {
            FilterChip(selected = false, onClick = { statusMenuVisible = true }, label = { Text(statusLabel(status)) })
            DropdownMenu(expanded = statusMenuVisible, onDismissRequest = { statusMenuVisible = false }) {
                ScheduleStatus.entries.forEach { option ->
                    DropdownMenuItem(text = { Text(statusLabel(option)) }, onClick = { status = option; statusMenuVisible = false })
                }
            }
        }
        Spacer(Modifier.height(12.dp))
        Card(Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.schedule_reminder_label), modifier = Modifier.weight(1f))
                Switch(checked = reminderEnabled, onCheckedChange = { reminderEnabled = it })
            }
        }
        Spacer(Modifier.height(24.dp))
        Button(
            onClick = {
                titleSubmitted = true
                if (title.trim().isNotEmpty() && !timeError) {
                    val updated = schedule.copy(
                        title = title,
                        note = note,
                        category = category,
                        scheduledEpochDay = date.toEpochDay(),
                        minuteOfDay = parsedTime?.let { it.hour * 60 + it.minute },
                        status = status,
                        remindBeforeMinutes = if (reminderEnabled) schedule.remindBeforeMinutes ?: defaultReminderMinutes.toLong() else null,
                    )
                    viewModel.save(updated) { onSaved(schedule.id == 0L) }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save_schedule)) }
        Spacer(Modifier.height(20.dp))
    }

    if (datePickerVisible) {
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = date.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { datePickerVisible = false },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis -> date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate() }
                    datePickerVisible = false
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { datePickerVisible = false }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
}

private fun parseTime(value: String): LocalTime? = if (value.isBlank()) null else runCatching { LocalTime.parse(value.trim()) }.getOrNull()

@Composable
private fun statusLabel(status: ScheduleStatus): String = when (status) {
    ScheduleStatus.TODO -> stringResource(R.string.status_todo)
    ScheduleStatus.IN_PROGRESS -> stringResource(R.string.status_in_progress)
    ScheduleStatus.COMPLETED -> stringResource(R.string.status_completed)
}
