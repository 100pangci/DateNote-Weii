package com.datenote.app.ui.aiinput

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DatePicker
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.local.ScheduleTypeWithSteps
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.reminder.NotificationAccess
import com.datenote.app.ui.reminder.NotificationUnavailableDialog
import com.datenote.app.ui.components.AppCard
import com.datenote.app.ui.components.AppOutlinedTextField
import com.datenote.app.ui.components.AppPrimaryButton
import com.datenote.app.ui.components.ReorderableColumn
import com.datenote.app.ui.components.ReorderableItem
import com.datenote.app.ui.components.moveItem
import com.datenote.app.ui.components.AppSoftTextField
import com.datenote.app.ui.theme.AppSpacing
import com.datenote.app.domain.parser.ParsedSchedule
import com.datenote.app.domain.parser.ParsedStep
import java.time.LocalDate
import java.time.LocalTime
import java.time.Instant
import java.time.ZoneOffset

private data class EditableAiDraft(
    val id: Long,
    val title: String,
    val startDate: String,
    val endDate: String,
    val time: String,
    val category: String,
    val note: String,
    val reminder: String,
    val reminderEnabled: Boolean,
    val steps: List<EditableAiStep>,
    val confidence: Double,
    val uncertainties: List<String>,
)

private data class EditableAiStep(val id: Long, val title: String, val isCompleted: Boolean) : ReorderableItem {
    override val stableId: Long
        get() = id
}

@Composable
fun AiInputScreen(
    aiRepository: AiRepository,
    repository: ScheduleRepository,
    defaultReminderMinutes: Int,
    reminderScheduler: ReminderScheduler,
    defaultReminderTimeMinutes: Int,
    onRequestNotifications: () -> Unit,
) {
    val viewModel: AiInputViewModel = viewModel(factory = AiInputViewModel.Factory(aiRepository, repository, reminderScheduler, defaultReminderTimeMinutes))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var drafts by remember(state.result) {
        mutableStateOf(state.result?.items.orEmpty().mapIndexed { index, item ->
            item.toEditable(defaultReminderMinutes, index.toLong(), state.matchedTemplateSteps[index].orEmpty())
        })
    }
    var saveError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }
    var notificationUnavailable by remember { mutableStateOf(false) }
    val context = LocalContext.current

    if (state.result == null) {
        InputPage(state = state, onInput = viewModel::setInput, onSubmit = viewModel::submit)
    } else {
        ReviewPage(
            drafts = drafts,
            scheduleTypes = state.scheduleTypes,
            warnings = state.result?.warnings.orEmpty(),
            saving = saving,
            saveError = saveError,
            onDraftChange = { index, draft -> drafts = drafts.toMutableList().also { it[index] = draft } },
            onRemove = { index -> drafts = drafts.toMutableList().also { it.removeAt(index) } },
            onStartOver = viewModel::startOver,
            onConfirm = {
                val schedules = drafts.mapNotNull { it.toScheduleWithStepsOrNull() }
                if (schedules.size != drafts.size || schedules.isEmpty()) {
                    saveError = true
                } else {
                    saving = true
                    viewModel.saveSchedules(schedules) { success ->
                        saving = false
                        if (success) {
                            saveError = false
                            viewModel.clearAfterSaved()
                            if (schedules.any { it.schedule.remindBeforeMinutes != null } && !NotificationAccess.status(context).canPost) {
                                notificationUnavailable = true
                            }
                        } else saveError = true
                    }
                }
            },
        )
    }
    if (notificationUnavailable) {
        NotificationUnavailableDialog(
            onEnable = {
                notificationUnavailable = false
                onRequestNotifications()
                viewModel.startOver()
            },
            onLater = {
                notificationUnavailable = false
                viewModel.startOver()
            },
        )
    }
}

@Composable
private fun InputPage(state: AiInputState, onInput: (String) -> Unit, onSubmit: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.ScreenTop),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Content),
    ) {
        item {
            Text(stringResource(R.string.ai_page_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(AppSpacing.Tight))
            Text(
                stringResource(R.string.ai_input_placeholder),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodySmall,
            )
        }
        item {
            AppSoftTextField(
                value = state.input,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth(),
                minLines = 5,
                maxLines = 10,
                placeholder = { Text(stringResource(R.string.ai_input_short)) },
            )
        }
        state.error?.let { error ->
            item { ErrorMessage(error) }
        }
        item {
            AppPrimaryButton(
                onClick = onSubmit,
                enabled = state.input.isNotBlank() && !state.isProcessing,
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (state.isProcessing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_processing))
                    }
                } else Text(stringResource(R.string.ai_organize))
            }
        }
    }
}

@Composable
private fun ErrorMessage(error: AiInputError) {
    val message = when (error) {
        AiInputError.NOT_CONFIGURED -> stringResource(R.string.ai_not_configured)
        AiInputError.NETWORK -> stringResource(R.string.ai_network_error)
        AiInputError.PARSE -> stringResource(R.string.ai_parse_error)
        AiInputError.EMPTY -> stringResource(R.string.ai_empty_error)
    }
    Surface(color = MaterialTheme.colorScheme.errorContainer, shape = MaterialTheme.shapes.medium) {
        Text(message, Modifier.fillMaxWidth().padding(AppSpacing.Content), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun ReviewPage(
    drafts: List<EditableAiDraft>,
    scheduleTypes: List<ScheduleTypeWithSteps>,
    warnings: List<String>,
    saving: Boolean,
    saveError: Boolean,
    onDraftChange: (Int, EditableAiDraft) -> Unit,
    onRemove: (Int) -> Unit,
    onStartOver: () -> Unit,
    onConfirm: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().imePadding(),
        contentPadding = PaddingValues(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.ScreenTop),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.Compact),
    ) {
        item {
            Text(stringResource(R.string.ai_review_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.ai_review_summary, drafts.size), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ai_review_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (warnings.isNotEmpty()) item {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(warnings.joinToString("\n"), Modifier.fillMaxWidth().padding(AppSpacing.Content))
            }
        }
        itemsIndexed(drafts, key = { _, draft -> draft.id }) { index, draft ->
            DraftCard(draft, scheduleTypes, onChange = { onDraftChange(index, it) }, onRemove = { onRemove(index) })
        }
        if (saveError) item { Text(stringResource(R.string.ai_invalid_draft), color = MaterialTheme.colorScheme.error) }
        item {
            AppPrimaryButton(
                onClick = onConfirm,
                enabled = !saving && drafts.isNotEmpty(),
                modifier = Modifier.fillMaxWidth(),
            ) {
                if (saving) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.Center,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        Spacer(Modifier.width(8.dp))
                        Text(stringResource(R.string.ai_processing))
                    }
                } else Text(stringResource(R.string.ai_confirm_save))
            }
            TextButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ai_start_over)) }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DraftCard(
    draft: EditableAiDraft,
    scheduleTypes: List<ScheduleTypeWithSteps>,
    onChange: (EditableAiDraft) -> Unit,
    onRemove: () -> Unit,
) {
    var datePickerTarget by rememberSaveable(draft.id) { mutableStateOf<AiDateTarget?>(null) }
    var expanded by rememberSaveable(draft.id) { mutableStateOf(true) }
    var typeMenuVisible by remember { mutableStateOf(false) }
    var pendingType by remember { mutableStateOf<ScheduleTypeWithSteps?>(null) }
    var nextStepId by remember { mutableLongStateOf(-1L) }
    val focusManager = LocalFocusManager.current
    AppCard(Modifier.fillMaxWidth(), containerColor = MaterialTheme.colorScheme.surfaceContainerLow) {
        Column(Modifier.padding(AppSpacing.Content), verticalArrangement = Arrangement.spacedBy(AppSpacing.Compact)) {
            if (expanded) {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    AppOutlinedTextField(
                        draft.title,
                        { onChange(draft.copy(title = it)) },
                        Modifier.weight(1f),
                        label = { Text(stringResource(R.string.schedule_title_label)) },
                    )
                    IconButton(onClick = { expanded = false }) {
                        Icon(Icons.Default.KeyboardArrowUp, contentDescription = stringResource(R.string.collapse_draft))
                    }
                }
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    AiDateButton(
                        label = stringResource(R.string.schedule_start_label),
                        date = draft.startDate.toLocalDateOrNull() ?: LocalDate.now(),
                        modifier = Modifier.weight(1f),
                        onClick = { datePickerTarget = AiDateTarget.START },
                    )
                    AiDateButton(
                        label = stringResource(R.string.schedule_end_label),
                        date = draft.endDate.toLocalDateOrNull() ?: draft.startDate.toLocalDateOrNull() ?: LocalDate.now(),
                        modifier = Modifier.weight(1f),
                        onClick = { datePickerTarget = AiDateTarget.END },
                    )
                }
                AppOutlinedTextField(
                    draft.time,
                    { onChange(draft.copy(time = it)) },
                    Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.ai_time)) },
                    singleLine = true,
                )
                Column(verticalArrangement = Arrangement.spacedBy(AppSpacing.Hairline)) {
                    BoxWithConstraints(modifier = Modifier.fillMaxWidth()) {
                        AppOutlinedTextField(
                            draft.category,
                            { onChange(draft.copy(category = it)) },
                            Modifier.fillMaxWidth(),
                            label = { Text(stringResource(R.string.ai_category)) },
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
                            modifier = Modifier.width(maxWidth),
                        ) {
                            DropdownMenuItem(
                                text = { Text(stringResource(R.string.no_schedule_type)) },
                                onClick = {
                                    typeMenuVisible = false
                                    onChange(draft.copy(category = ""))
                                },
                            )
                            scheduleTypes.forEach { type ->
                                DropdownMenuItem(
                                    text = { Text(type.type.name, maxLines = 1) },
                                    onClick = {
                                        typeMenuVisible = false
                                        if (draft.category.trim() != type.type.name) {
                                            if (draft.steps.isNotEmpty() && type.orderedSteps.isNotEmpty()) pendingType = type
                                            else onChange(
                                                draft.copy(
                                                    category = type.type.name,
                                                    steps = if (draft.steps.isEmpty()) type.toScheduleSteps(0).map { EditableAiStep(it.position.toLong(), it.title, false) } else draft.steps,
                                                ),
                                            )
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
                AppOutlinedTextField(draft.note, { onChange(draft.copy(note = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_note)) }, minLines = 2)
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(stringResource(R.string.schedule_reminder_label), Modifier.weight(1f))
                    Switch(draft.reminderEnabled, { onChange(draft.copy(reminderEnabled = it)) })
                }
                if (draft.reminderEnabled) AppOutlinedTextField(draft.reminder, { onChange(draft.copy(reminder = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_reminder_minutes)) }, singleLine = true)
                Text(stringResource(R.string.production_steps), style = MaterialTheme.typography.titleSmall)
                ReorderableColumn(
                    items = draft.steps,
                    onMove = { fromIndex, toIndex -> onChange(draft.copy(steps = draft.steps.moveItem(fromIndex, toIndex))) },
                    onDragStart = { focusManager.clearFocus() },
                ) { step, dragHandleModifier ->
                    val stepIndex = draft.steps.indexOfFirst { it.id == step.id }
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(step.isCompleted, { checked -> onChange(draft.copy(steps = draft.steps.mapIndexed { index, old -> if (index == stepIndex) old.copy(isCompleted = checked) else old })) })
                        AppOutlinedTextField(
                            value = step.title,
                            onValueChange = { value -> onChange(draft.copy(steps = draft.steps.mapIndexed { index, old -> if (index == stepIndex) old.copy(title = value) else old })) },
                            modifier = Modifier.weight(1f),
                            label = { Text(stringResource(R.string.step_name)) },
                            singleLine = true,
                            trailingIcon = {
                                Box(
                                    modifier = dragHandleModifier.size(40.dp),
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(Icons.Default.DragHandle, contentDescription = stringResource(R.string.reorder_steps), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            },
                        )
                        TextButton(onClick = { onChange(draft.copy(steps = draft.steps.filterIndexed { index, _ -> index != stepIndex })) }) { Text(stringResource(R.string.delete_step)) }
                    }
                }
                TextButton(onClick = { onChange(draft.copy(steps = draft.steps + EditableAiStep(nextStepId--, "", false))) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.add_step)) }
                Text(stringResource(R.string.ai_confidence, (draft.confidence * 100).toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
                if (draft.uncertainties.isNotEmpty()) {
                    Text(stringResource(R.string.ai_uncertain_title), color = MaterialTheme.colorScheme.error)
                    Text(draft.uncertainties.joinToString("\n"), color = MaterialTheme.colorScheme.error)
                    Text(stringResource(R.string.ai_uncertain_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ai_remove_item)) }
            } else {
                Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                    Text(
                        draft.title.ifBlank { stringResource(R.string.unnamed_draft) },
                        modifier = Modifier.weight(1f),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    IconButton(onClick = { expanded = true }) {
                        Icon(Icons.Default.KeyboardArrowDown, contentDescription = stringResource(R.string.expand_draft))
                    }
                }
            }
        }
    }
    datePickerTarget?.let { target ->
        val initial = when (target) {
            AiDateTarget.START -> draft.startDate.toLocalDateOrNull() ?: LocalDate.now()
            AiDateTarget.END -> draft.endDate.toLocalDateOrNull() ?: draft.startDate.toLocalDateOrNull() ?: LocalDate.now()
        }
        val pickerState = rememberDatePickerState(initialSelectedDateMillis = initial.toEpochDay() * 86_400_000L)
        DatePickerDialog(
            onDismissRequest = { datePickerTarget = null },
            confirmButton = {
                TextButton(onClick = {
                    pickerState.selectedDateMillis?.let { millis ->
                        val selected = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        when (target) {
                            AiDateTarget.START -> {
                                val end = draft.endDate.toLocalDateOrNull()
                                onChange(
                                    draft.copy(
                                        startDate = selected.toString(),
                                        endDate = if (end == null || end.isBefore(selected)) selected.toString() else draft.endDate,
                                    ),
                                )
                            }
                            AiDateTarget.END -> {
                                val start = draft.startDate.toLocalDateOrNull()
                                if (start == null || !selected.isBefore(start)) onChange(draft.copy(endDate = selected.toString()))
                            }
                        }
                    }
                    datePickerTarget = null
                }) { Text(stringResource(R.string.confirm)) }
            },
            dismissButton = { TextButton(onClick = { datePickerTarget = null }) { Text(stringResource(R.string.cancel)) } },
        ) { DatePicker(state = pickerState) }
    }
    pendingType?.let { type ->
        val hasCompletedSteps = draft.steps.any { it.isCompleted }
        AlertDialog(
            onDismissRequest = { pendingType = null },
            title = { Text(stringResource(R.string.apply_schedule_type_title, type.type.name)) },
            text = {
                Text(
                    if (hasCompletedSteps) stringResource(R.string.apply_schedule_type_completed_message)
                    else stringResource(R.string.apply_schedule_type_message),
                )
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { pendingType = null }) { Text(stringResource(R.string.cancel)) }
                    TextButton(onClick = {
                        pendingType = null
                        onChange(draft.copy(category = type.type.name))
                    }) { Text(stringResource(R.string.only_change_schedule_type)) }
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    pendingType = null
                    onChange(
                        draft.copy(
                            category = type.type.name,
                            steps = type.toScheduleSteps(0).map { EditableAiStep(it.position.toLong(), it.title, false) },
                        ),
                    )
                }) { Text(stringResource(R.string.replace_steps_with_template)) }
            },
        )
    }
}

@Composable
private fun AiDateButton(
    label: String,
    date: LocalDate,
    modifier: Modifier,
    onClick: () -> Unit,
) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium)
        OutlinedButton(onClick = onClick, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.date_year_month_day, date.year, date.monthValue, date.dayOfMonth))
        }
    }
}

private enum class AiDateTarget { START, END }

private fun String.toLocalDateOrNull(): LocalDate? = runCatching { LocalDate.parse(trim()) }.getOrNull()

private fun ParsedSchedule.toEditable(defaultReminder: Int, id: Long, templateSteps: List<String>): EditableAiDraft = EditableAiDraft(
    id = id,
    title = title,
    startDate = startDate.toString(),
    endDate = endDate.toString(),
    time = time?.toString()?.take(5).orEmpty(),
    category = category.orEmpty(),
    note = note,
    reminder = (remindBeforeMinutes ?: defaultReminder.toLong()).toString(),
    reminderEnabled = remindBeforeMinutes != null,
    steps = if (steps.isEmpty()) templateSteps.mapIndexed { index, title -> EditableAiStep(index.toLong(), title, false) }
    else steps.mapIndexed { index, step -> EditableAiStep(index.toLong(), step.title, step.isCompleted) },
    confidence = confidence,
    uncertainties = uncertainties,
)

private fun EditableAiDraft.toScheduleWithStepsOrNull(): ScheduleWithSteps? {
    val parsedStart = runCatching { LocalDate.parse(startDate.trim()) }.getOrNull() ?: return null
    val parsedEnd = runCatching { LocalDate.parse(endDate.trim()) }.getOrNull() ?: return null
    if (parsedEnd.isBefore(parsedStart)) return null
    val parsedTime = time.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: if (time.isBlank()) null else return null
    val reminderMinutes = if (reminderEnabled) reminder.trim().toLongOrNull()?.takeIf { it in 0..43_200 } ?: return null else null
    val schedule = ScheduleEntity(
        title = title.trim().takeIf { it.isNotEmpty() } ?: return null,
        startEpochDay = parsedStart.toEpochDay(),
        endEpochDay = parsedEnd.toEpochDay(),
        minuteOfDay = parsedTime?.let { it.hour * 60 + it.minute },
        category = category.trim().takeIf { it.isNotEmpty() },
        note = note.trim(),
        remindBeforeMinutes = reminderMinutes,
    )
    val parsedSteps = steps.mapIndexedNotNull { index, step ->
        step.title.trim().takeIf { it.isNotEmpty() }?.let { title ->
            ScheduleStepEntity(scheduleId = 0, title = title, position = index, isCompleted = step.isCompleted, completedAt = if (step.isCompleted) System.currentTimeMillis() else null)
        }
    }
    return ScheduleWithSteps(schedule, parsedSteps)
}
