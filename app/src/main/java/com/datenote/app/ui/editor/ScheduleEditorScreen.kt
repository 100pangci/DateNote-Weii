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
import com.datenote.app.data.repository.UserPreferencesRepository
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
    preferencesRepository: UserPreferencesRepository,
    defaultReminderTimeMinutes: Int,
    onBack: () -> Unit,
    onSaved: (Boolean) -> Unit,
    onRequestNotifications: () -> Unit,
) {
    val viewModel: ScheduleEditorViewModel = viewModel(
        key = "editor-${scheduleId ?: "new"}",
        factory = ScheduleEditorViewModel.Factory(repository, scheduleId, defaultReminderMinutes, reminderScheduler, preferencesRepository),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val schedule = state.schedule
    val draft = state.draft
    if (state.isLoading || schedule == null || draft == null) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        return
    }

    var titleSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var stepsSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var reminderSubmitted by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var datePickerTarget by rememberSaveable { mutableStateOf<DateTarget?>(null) }
    var typeMenuVisible by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf<ScheduleTypeWithSteps?>(null) }
    var notificationUnavailable by rememberSaveable(schedule.id) { mutableStateOf(false) }
    var timePickerVisible by rememberSaveable(schedule.id) { mutableStateOf(false) }
    val context = LocalContext.current
    val focusManager = LocalFocusManager.current
    val steps = draft.steps.map { it.step }
    val titleError = titleSubmitted && draft.title.trim().isEmpty()
    val stepsError = stepsSubmitted && steps.any { it.title.trim().isEmpty() }
    val reminderMinutes = draft.reminderText.trim().toLongOrNull()?.takeIf { it in 0..43_200 }
    val reminderError = reminderSubmitted && draft.reminderEnabled && reminderMinutes == null
    val parsedTime = draft.minuteOfDay?.let { LocalTime.of(it / 60, it % 60) }
    val timeText = parsedTime?.let { "%02d:%02d".format(it.hour, it.minute) }.orEmpty()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(draft.steps.lastOrNull()?.rowId) {
        if (draft.shouldFocusLastEmptyStep()) focusRequester.requestFocus()
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
            value = draft.title,
            onValueChange = { value -> viewModel.updateDraft { it.copy(title = value) }; titleSubmitted = false },
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
                value = stringResource(R.string.date_year_month_day, LocalDate.ofEpochDay(draft.startEpochDay).year, LocalDate.ofEpochDay(draft.startEpochDay).monthValue, LocalDate.ofEpochDay(draft.startEpochDay).dayOfMonth),
                onClick = { datePickerTarget = DateTarget.START },
                modifier = Modifier.weight(1f),
            )
            AppValueRow(
                label = stringResource(R.string.schedule_end_label),
                value = stringResource(R.string.date_year_month_day, LocalDate.ofEpochDay(draft.endEpochDay).year, LocalDate.ofEpochDay(draft.endEpochDay).monthValue, LocalDate.ofEpochDay(draft.endEpochDay).dayOfMonth),
                onClick = { datePickerTarget = DateTarget.END },
                modifier = Modifier.weight(1f),
            )
        }
        if (draft.startEpochDay != draft.endEpochDay) {
            Text(
                stringResource(
                    R.string.date_range_summary,
                    draft.endEpochDay - draft.startEpochDay + 1,
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
                    value = draft.category,
                    onValueChange = { value -> viewModel.updateDraft { it.copy(category = value) } },
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
                            viewModel.updateDraft { it.copy(category = "") }
                        },
                    )
                    state.scheduleTypes.forEach { type ->
                        DropdownMenuItem(
                            text = { Text(type.type.name, maxLines = 1) },
                            onClick = {
                                typeMenuVisible = false
                                if (draft.category.trim() == type.type.name) return@DropdownMenuItem
                                if (steps.isNotEmpty() && type.orderedSteps.isNotEmpty()) {
                                    pendingType = type
                                } else {
                                    viewModel.updateDraft { it.copy(category = type.type.name) }
                                    if (steps.isEmpty()) {
                                        val typeSteps = viewModel.stepsFromType(type, schedule.id)
                                        viewModel.updateDraft { it.copy(steps = typeSteps, status = statusAfterStepChange(it.status, typeSteps.map { item -> item.step })) }
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
        AppOutlinedTextField(value = draft.note, onValueChange = { value -> viewModel.updateDraft { it.copy(note = value) } }, modifier = Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_note_label)) }, placeholder = { Text(stringResource(R.string.schedule_note_placeholder)) }, minLines = 3)
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
                        onClick = viewModel::addStep,
                        modifier = Modifier.fillMaxWidth(),
                        shape = CircleShape,
                    ) {
                        Text(stringResource(R.string.add_first_step))
                    }
                }
            }
        } else {
            val stepIds = draft.steps.map { it.rowId }
            ReorderableColumn(
                items = stepIds,
                onMove = { fromIndex, toIndex ->
                    viewModel.updateDraft { it.copy(steps = it.steps.moveItem(fromIndex, toIndex)) }
                },
                onDragStart = { focusManager.clearFocus() },
            ) { stepId, dragHandleModifier ->
                val index = draft.steps.indexOfFirst { it.rowId == stepId }
                val stepDraft = draft.steps[index]
                val step = stepDraft.step
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Checkbox(
                        checked = step.isCompleted,
                        onCheckedChange = { checked ->
                            val now = System.currentTimeMillis()
                            val updated = draft.steps.mapIndexed { i, old ->
                                if (i == index) old.copy(step = old.step.copy(isCompleted = checked, completedAt = if (checked) now else null, updatedAt = now)) else old
                            }
                            viewModel.updateDraft { it.copy(steps = updated, status = statusAfterStepChange(it.status, updated.map { item -> item.step })) }
                        },
                    )
                    AppOutlinedTextField(
                        value = step.title,
                        onValueChange = { value ->
                            viewModel.updateDraft { it.copy(steps = it.steps.mapIndexed { i, old -> if (i == index) old.copy(step = old.step.copy(title = value)) else old }) }
                        },
                        modifier = Modifier.weight(1f).then(if (index == draft.steps.lastIndex && step.title.isEmpty()) Modifier.focusRequester(focusRequester) else Modifier),
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
                    IconButton(onClick = { viewModel.updateDraft { it.copy(steps = it.steps.filterIndexed { i, _ -> i != index }) } }) { Icon(Icons.Default.DeleteOutline, stringResource(R.string.delete_step)) }
                }
            }
            if (stepsError) Text(stringResource(R.string.step_required), color = MaterialTheme.colorScheme.error)
            TextButton(
                onClick = { if (steps.lastOrNull()?.title?.trim()?.isNotEmpty() != false) viewModel.addStep() },
                modifier = Modifier.fillMaxWidth(),
            ) { Text(stringResource(R.string.add_step)) }
        }
        ListItem(
            modifier = Modifier.fillMaxWidth(),
            headlineContent = { Text(stringResource(R.string.schedule_reminder_label)) },
            supportingContent = {
                Text(
                    if (draft.reminderEnabled) {
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
                checked = draft.reminderEnabled,
                onCheckedChange = {
                        viewModel.updateDraft { draft -> draft.copy(reminderEnabled = it) }
                        reminderSubmitted = false
                    },
                )
            },
        )
        if (draft.reminderEnabled) {
            AppOutlinedTextField(
                value = draft.reminderText,
                onValueChange = {
                    viewModel.updateDraft { draft -> draft.copy(reminderText = it) }
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
                val normalizedSteps = draft.steps.map { it.step.copy(title = it.step.title.trim()) }
                if (draft.title.trim().isNotEmpty() && draft.startEpochDay <= draft.endEpochDay && normalizedSteps.none { it.title.isEmpty() } && (!draft.reminderEnabled || reminderMinutes != null)) {
                    val updated = draft.toScheduleEntity(schedule).copy(
                        title = draft.title,
                        remindBeforeMinutes = if (draft.reminderEnabled) reminderMinutes else null,
                    )
                    val notificationAvailable = NotificationAccess.status(context).canPost
                    viewModel.save(updated, normalizedSteps) {
                        if (draft.reminderEnabled && !notificationAvailable) notificationUnavailable = true else onSaved(schedule.id == 0L)
                    }
                }
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = !state.isSaving,
        ) {
            if (state.isSaving) CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
            else Text(stringResource(R.string.save_schedule))
        }
        if (state.saveFailed) Text(stringResource(R.string.schedule_save_failed), color = MaterialTheme.colorScheme.error)
        Spacer(Modifier.height(AppSpacing.ScreenBottom))
    }
    }

    datePickerTarget?.let { target ->
        val initial = LocalDate.ofEpochDay(if (target == DateTarget.START) draft.startEpochDay else draft.endEpochDay)
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
                        viewModel.updateDraft { current ->
                            if (target == DateTarget.START) {
                                current.copy(
                                    startEpochDay = selected.toEpochDay(),
                                    endEpochDay = maxOf(current.endEpochDay, selected.toEpochDay()),
                                )
                            } else if (selected.toEpochDay() >= current.startEpochDay) {
                                current.copy(endEpochDay = selected.toEpochDay())
                            } else current
                        }
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
                viewModel.updateDraft { it.copy(minuteOfDay = hour * 60 + minute) }
                timePickerVisible = false
            },
            onCancel = { timePickerVisible = false },
            onClear = {
                viewModel.updateDraft { it.copy(minuteOfDay = null) }
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
                        viewModel.updateDraft { it.copy(category = type.type.name) }
                        pendingType = null
                    }) { Text(stringResource(R.string.only_change_schedule_type)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val typeSteps = viewModel.stepsFromType(type, schedule.id)
                    viewModel.updateDraft {
                        it.copy(
                            category = type.type.name,
                            steps = typeSteps,
                            status = statusAfterStepChange(it.status, typeSteps.map { item -> item.step }),
                        )
                    }
                    pendingType = null
                }) { Text(stringResource(R.string.replace_steps_with_template)) }
            },
        )
    }
}

private enum class DateTarget { START, END }

@Composable
private fun reminderDescription(minutes: Long): String = when {
    minutes % (7L * 24L * 60L) == 0L -> stringResource(R.string.reminder_before_weeks, minutes / (7L * 24L * 60L))
    minutes % (24L * 60L) == 0L -> stringResource(R.string.reminder_before_days, minutes / (24L * 60L))
    minutes % 60L == 0L -> stringResource(R.string.reminder_before_hours, minutes / 60L)
    else -> stringResource(R.string.reminder_before_minutes, minutes)
}
