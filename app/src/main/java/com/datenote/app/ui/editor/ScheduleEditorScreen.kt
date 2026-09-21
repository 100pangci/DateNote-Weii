package com.datenote.app.ui.editor

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DisplayMode
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.width
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleTypeWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.domain.model.statusAfterStepChange
import com.datenote.app.reminder.NotificationAccess
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.ui.reminder.NotificationUnavailableDialog
import com.datenote.app.ui.components.AppCard
import com.datenote.app.ui.components.AppOutlinedTextField
import com.datenote.app.ui.components.AppPrimaryButton
import com.datenote.app.ui.components.AppSectionTitle
import com.datenote.app.ui.components.AppTimePickerDialog
import com.datenote.app.ui.components.AppTopAppBar
import com.datenote.app.ui.components.AppValueRow
import com.datenote.app.ui.components.ReorderableColumn
import com.datenote.app.ui.components.moveItem
import com.datenote.app.ui.theme.AppSpacing
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
    var reminderText by remember(schedule.id) {
        mutableStateOf((schedule.remindBeforeMinutes ?: defaultReminderMinutes.toLong()).toString())
    }
    var titleSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var stepsSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var reminderSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var datePickerTarget by rememberSaveable { mutableStateOf<DateTarget?>(null) }
    var typeMenuVisible by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf<ScheduleTypeWithSteps?>(null) }
    var notificationUnavailable by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var timePickerVisible by rememberSaveable(schedule.id) { mutableStateOf(false) }
    // Stable row ids for unsaved steps, which all share the database id 0.
    val transientStepIds = remember(schedule.id) { mutableMapOf<ScheduleStepEntity, Long>() }
    var nextTransientStepId by remember(schedule.id) { mutableLongStateOf(-1L) }
    fun stepEntityId(step: ScheduleStepEntity): Long = if (step.id != 0L) {
        step.id
    } else {
        transientStepIds.getOrPut(step) { nextTransientStepId-- }
    }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val titleError = titleSubmitted && title.trim().isEmpty()
    val stepsError = stepsSubmitted && steps.any { it.title.trim().isEmpty() }
    val reminderMinutes = reminderText.trim().toLongOrNull()?.takeIf { it in 0..43_200 }
    val reminderError = reminderSubmitted && reminderEnabled && reminderMinutes == null
    val parsedTime = remember(timeText) { parseTime(timeText) }
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(steps.size) {
        if (steps.lastOrNull()?.title.isNullOrEmpty()) focusRequester.requestFocus()
    }

    Scaffold(
        topBar = {
            AppTopAppBar(
                title = if (schedule.id == 0L) stringResource(R.string.new_schedule_title) else stringResource(R.string.edit_schedule_title),
                onBack = onBack,
            )
        },
    ) { padding ->
    Column(
        Modifier
            .fillMaxSize()
            .padding(padding)
            .imePadding()
            .verticalScroll(rememberScrollState())
            .padding(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.ScreenTop),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Compact),
    ) {
        AppOutlinedTextField(
            value = title,
            onValueChange = { title = it; titleSubmitted = false },
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.schedule_title_label)) },
            placeholder = { Text(stringResource(R.string.schedule_title_placeholder)) },
            isError = titleError,
            supportingText = { if (titleError) Text(stringResource(R.string.title_required)) },
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Tight),
        ) {
            AppValueRow(
                label = stringResource(R.string.schedule_start_label),
                value = stringResource(R.string.date_year_month_day, startDate.year, startDate.monthValue, startDate.dayOfMonth),
                onClick = { datePickerTarget = DateTarget.START },
                modifier = Modifier.weight(1f),
            )
            AppValueRow(
                label = stringResource(R.string.schedule_end_label),
                value = stringResource(R.string.date_year_month_day, endDate.year, endDate.monthValue, endDate.dayOfMonth),
                onClick = { datePickerTarget = DateTarget.END },
                modifier = Modifier.weight(1f),
            )
        }
        if (startDate != endDate) {
            Text(
                stringResource(
                    R.string.date_range_summary,
                    endDate.toEpochDay() - startDate.toEpochDay() + 1,
                ),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = AppSpacing.Content),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Hairline)) {
            AppValueRow(
                label = stringResource(R.string.schedule_deadline_label),
                value = timeText.ifBlank { stringResource(R.string.schedule_deadline_not_set) },
                onClick = { timePickerVisible = true },
                modifier = Modifier.fillMaxWidth(),
            )
            Text(
                text = stringResource(R.string.schedule_deadline_supporting),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = AppSpacing.Content),
            )
        }
        Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Hairline)) {
            BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                AppOutlinedTextField(
                    value = category,
                    onValueChange = { category = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.schedule_category_label)) },
                    trailingIcon = {
                        IconButton(onClick = { typeMenuVisible = true }) {
                            Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.choose_schedule_type))
                        }
                    },
                    singleLine = true,
                )
                DropdownMenu(
                    expanded = typeMenuVisible,
                    onDismissRequest = { typeMenuVisible = false },
                    modifier = Modifier
                        .width(maxWidth)
                        .heightIn(max = 320.dp),
                ) {
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.no_schedule_type)) },
                        onClick = {
                            typeMenuVisible = false
                            category = ""
                        },
                    )
                    state.scheduleTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.type.name, maxLines = 1) },
                            onClick = {
                                typeMenuVisible = false
                                if (category.trim() == type.type.name) return@DropdownMenuItem
                                if (steps.isNotEmpty() && type.orderedSteps.isNotEmpty()) {
                                    pendingType = type
                                } else {
                                    category = type.type.name
                                    if (steps.isEmpty()) {
                                        steps = viewModel.stepsFromType(type, schedule.id)
                                        status = statusAfterStepChange(status, steps)
                                    }
                                }
                            },
                        )
                    }
                }
            }
            Text(
                text = stringResource(R.string.schedule_category_supporting),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(horizontal = AppSpacing.Content),
            )
        }
        AppOutlinedTextField(value = note, onValueChange = { note = it }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_note_label)) }, placeholder = { Text(stringResource(R.string.schedule_note_placeholder)) }, minLines = 3)
        AppSectionTitle(stringResource(R.string.production_steps), modifier = Modifier.padding(top = AppSpacing.Tight))
        if (steps.isEmpty()) {
            AppCard(
                modifier = Modifier.fillMaxWidth(),
                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
            ) {
                Column(
                    Modifier.padding(AppSpacing.Content),
                    verticalArrangement = Arrangement.spacedBy(AppSpacing.Tight),
                ) {
                    Text(stringResource(R.string.no_steps_message), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodyMedium)
                    TextButton(
                        onClick = { steps = listOf(newStep(schedule.id)) },
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                    ) {
                        Text(stringResource(R.string.add_first_step))
                    }
                }
            }
        } else {
            val stepIds = steps.map(::stepEntityId)
            ReorderableColumn(
                items = stepIds,
                onMove = { fromIndex, toIndex ->
                    steps = steps.moveItem(fromIndex, toIndex)
                },
                onDragStart = { focusManager.clearFocus() },
            ) { stepId, dragHandleModifier ->
                val index = steps.indexOfFirst { stepEntityId(it) == stepId }
                val step = steps[index]
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
                    AppOutlinedTextField(
                        value = step.title,
                        onValueChange = { value -> steps = steps.mapIndexed { i, old -> if (i == index) old.copy(title = value) else old } },
                        modifier = Modifier.weight(1f).then(if (index == steps.lastIndex && step.title.isEmpty()) Modifier.focusRequester(focusRequester) else Modifier),
                        label = { Text(stringResource(R.string.step_name)) },
                        singleLine = true,
                        isError = stepsError && step.title.trim().isEmpty(),
                        trailingIcon = {
                            Box(
                                modifier = dragHandleModifier.size(40.dp),
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.reorder_steps), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        },
                    )
                    IconButton(onClick = { steps = steps.filterIndexed { i, _ -> i != index } }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_step)) }
                }
            }
            if (stepsError) Text(stringResource(R.string.step_required), color = MaterialTheme.colorScheme.error)
            TextButton(
                onClick = { if (steps.lastOrNull()?.title?.trim()?.isNotEmpty() != false) steps = steps + newStep(schedule.id) },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.add_step)) }
        }
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            headlineContent = { Text(stringResource(R.string.schedule_reminder_label)) },
            supportingContent = {
                Text(
                    if (reminderEnabled) {
                        stringResource(
                            R.string.schedule_reminder_enabled_supporting,
                            reminderDescription(reminderMinutes ?: defaultReminderMinutes.toLong()),
                        )
                    } else {
                        stringResource(R.string.schedule_reminder_disabled_supporting)
                    },
                )
            },
            trailingContent = {
                Switch(
                    checked = reminderEnabled,
                    onCheckedChange = {
                        reminderEnabled = it
                        reminderSubmitted = false
                    },
                )
            },
        )
        if (reminderEnabled) {
            AppOutlinedTextField(
                value = reminderText,
                onValueChange = {
                    reminderText = it
                    reminderSubmitted = false
                },
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.schedule_reminder_minutes_label)) },
                supportingText = {
                    if (reminderError) Text(stringResource(R.string.schedule_reminder_minutes_invalid))
                },
                isError = reminderError,
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
            )
        }
        AppPrimaryButton(
            onClick = {
                titleSubmitted = true
                stepsSubmitted = true
                reminderSubmitted = true
                val normalizedSteps = steps.map { it.copy(title = it.title.trim()) }
                if (title.trim().isNotEmpty() && startDate <= endDate && normalizedSteps.none { it.title.isEmpty() } && (!reminderEnabled || reminderMinutes != null)) {
                    val updated = schedule.copy(
                        title = title,
                        note = note,
                        category = category,
                        startEpochDay = startDate.toEpochDay(),
                        endEpochDay = endDate.toEpochDay(),
                        minuteOfDay = parsedTime?.let { it.hour * 60 + it.minute },
                        status = status,
                        remindBeforeMinutes = if (reminderEnabled) reminderMinutes else null,
                    )
                    val notificationAvailable = NotificationAccess.status(context).canPost
                    viewModel.save(updated, normalizedSteps) {
                        if (reminderEnabled && !notificationAvailable) notificationUnavailable = true else onSaved(schedule.id == 0L)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
        ) { Text(stringResource(R.string.save_schedule)) }
        Spacer(Modifier.height(AppSpacing.ScreenBottom))
    }
    }

    datePickerTarget?.let { target ->
        val initial = if (target == DateTarget.START) startDate else endDate
        val initialMillis = initial.toEpochDay() * 86_400_000L
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = initialMillis,
            initialDisplayedMonthMillis = initialMillis,
            initialDisplayMode = DisplayMode.Picker,
        )
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
        ) {
            // Recreate only the DatePicker UI when switching modes so Material3's
            // slow AnimatedContent transition does not run. The state is retained.
            val displayMode = pickerState.displayMode
            key(displayMode) {
                DatePicker(
                    state = pickerState,
                    showModeToggle = true,
                )
            }
        }
    }
    if (timePickerVisible) {
        val initialTime = parsedTime ?: LocalTime.of(defaultReminderTimeMinutes / 60, defaultReminderTimeMinutes % 60)
        AppTimePickerDialog(
            title = stringResource(R.string.schedule_deadline_label),
            initialHour = initialTime.hour,
            initialMinute = initialTime.minute,
            confirmLabel = stringResource(R.string.confirm),
            cancelLabel = stringResource(R.string.cancel),
            clearLabel = stringResource(R.string.schedule_deadline_clear),
            onConfirm = { hour, minute ->
                timeText = "%02d:%02d".format(hour, minute)
                timePickerVisible = false
            },
            onCancel = { timePickerVisible = false },
            onClear = {
                timeText = ""
                timePickerVisible = false
            },
        )
    }
    if (notificationUnavailable) {
        NotificationUnavailableDialog(
            onEnable = { notificationUnavailable = false; onRequestNotifications(); onSaved(schedule.id == 0L) },
            onLater = { notificationUnavailable = false; onSaved(schedule.id == 0L) },
        )
    }
    pendingType?.let { type ->
        val hasCompletedSteps = steps.any { it.isCompleted }
        AlertDialog(
            onDismissRequest = { pendingType = null },
            title = { Text(stringResource(R.string.apply_schedule_type_title, type.type.name)) },
            text = {
                Text(
                    if (hasCompletedSteps) {
                        stringResource(R.string.apply_schedule_type_completed_message)
                    } else {
                        stringResource(R.string.apply_schedule_type_message)
                    },
                )
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingType = null }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        category = type.type.name
                        pendingType = null
                    }) { Text(stringResource(R.string.only_change_schedule_type)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    category = type.type.name
                    steps = viewModel.stepsFromType(type, schedule.id)
                    status = statusAfterStepChange(status, steps)
                    pendingType = null
                }) { Text(stringResource(R.string.replace_steps_with_template)) }
            },
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
