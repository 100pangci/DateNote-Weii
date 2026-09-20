package com.datenote.app.ui.aiinput

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.Switch
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.domain.parser.ParsedSchedule
import java.time.LocalDate
import java.time.LocalTime

private data class EditableAiDraft(
    val id: Long,
    val title: String,
    val date: String,
    val time: String,
    val category: String,
    val note: String,
    val reminder: String,
    val reminderEnabled: Boolean,
    val confidence: Double,
    val uncertainties: List<String>,
)

@Composable
fun AiInputScreen(
    aiRepository: AiRepository,
    repository: ScheduleRepository,
    defaultReminderMinutes: Int,
    reminderScheduler: ReminderScheduler,
    defaultReminderTimeMinutes: Int,
) {
    val viewModel: AiInputViewModel = viewModel(factory = AiInputViewModel.Factory(aiRepository, repository, reminderScheduler, defaultReminderTimeMinutes))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var drafts by remember(state.result) {
        mutableStateOf(state.result?.items.orEmpty().mapIndexed { index, item -> item.toEditable(defaultReminderMinutes, index.toLong()) })
    }
    var saveError by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    if (state.result == null) {
        InputPage(state = state, onInput = viewModel::setInput, onSubmit = viewModel::submit)
    } else {
        ReviewPage(
            drafts = drafts,
            warnings = state.result?.warnings.orEmpty(),
            saving = saving,
            saveError = saveError,
            onDraftChange = { index, draft -> drafts = drafts.toMutableList().also { it[index] = draft } },
            onRemove = { index -> drafts = drafts.toMutableList().also { it.removeAt(index) } },
            onStartOver = viewModel::startOver,
            onConfirm = {
                val schedules = drafts.mapNotNull { it.toEntityOrNull() }
                if (schedules.size != drafts.size || schedules.isEmpty()) {
                    saveError = true
                } else {
                    saving = true
                    viewModel.saveSchedules(schedules) { success ->
                        saving = false
                        if (success) {
                            saveError = false
                            viewModel.startOver()
                        } else saveError = true
                    }
                }
            },
        )
    }
}

@Composable
private fun InputPage(state: AiInputState, onInput: (String) -> Unit, onSubmit: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        item {
            Text(stringResource(R.string.ai_page_title), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.ai_input_placeholder), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        item {
            OutlinedTextField(
                value = state.input,
                onValueChange = onInput,
                modifier = Modifier.fillMaxWidth(),
                minLines = 7,
                maxLines = 12,
                label = { Text(stringResource(R.string.ai_input_short)) },
            )
        }
        state.error?.let { error ->
            item { ErrorMessage(error) }
        }
        item {
            Button(onClick = onSubmit, enabled = state.input.isNotBlank() && !state.isProcessing, modifier = Modifier.fillMaxWidth()) {
                if (state.isProcessing) {
                    CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.height(4.dp))
                    Text(stringResource(R.string.ai_processing))
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
        Text(message, Modifier.fillMaxWidth().padding(14.dp), color = MaterialTheme.colorScheme.onErrorContainer)
    }
}

@Composable
private fun ReviewPage(
    drafts: List<EditableAiDraft>,
    warnings: List<String>,
    saving: Boolean,
    saveError: Boolean,
    onDraftChange: (Int, EditableAiDraft) -> Unit,
    onRemove: (Int) -> Unit,
    onStartOver: () -> Unit,
    onConfirm: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 20.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            Text(stringResource(R.string.ai_review_title), style = MaterialTheme.typography.headlineSmall)
            Text(stringResource(R.string.ai_review_summary, drafts.size), style = MaterialTheme.typography.titleMedium)
            Text(stringResource(R.string.ai_review_subtitle), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        if (warnings.isNotEmpty()) item {
            Surface(color = MaterialTheme.colorScheme.tertiaryContainer, shape = MaterialTheme.shapes.medium) {
                Text(warnings.joinToString("\n"), Modifier.fillMaxWidth().padding(14.dp))
            }
        }
        itemsIndexed(drafts, key = { _, draft -> draft.id }) { index, draft ->
            DraftCard(draft, onChange = { onDraftChange(index, it) }, onRemove = { onRemove(index) })
        }
        if (saveError) item { Text(stringResource(R.string.ai_invalid_draft), color = MaterialTheme.colorScheme.error) }
        item {
            Button(onClick = onConfirm, enabled = !saving && drafts.isNotEmpty(), modifier = Modifier.fillMaxWidth()) {
                if (saving) CircularProgressIndicator(modifier = Modifier.height(20.dp), strokeWidth = 2.dp)
                else Text(stringResource(R.string.ai_confirm_save))
            }
            TextButton(onClick = onStartOver, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.ai_start_over)) }
        }
    }
}

@Composable
private fun DraftCard(draft: EditableAiDraft, onChange: (EditableAiDraft) -> Unit, onRemove: () -> Unit) {
    Card(Modifier.fillMaxWidth()) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedTextField(draft.title, { onChange(draft.copy(title = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.schedule_title_label)) })
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(draft.date, { onChange(draft.copy(date = it)) }, Modifier.weight(1f), label = { Text(stringResource(R.string.ai_date)) }, singleLine = true)
                OutlinedTextField(draft.time, { onChange(draft.copy(time = it)) }, Modifier.weight(1f), label = { Text(stringResource(R.string.ai_time)) }, singleLine = true)
            }
            OutlinedTextField(draft.category, { onChange(draft.copy(category = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_category)) }, singleLine = true)
            OutlinedTextField(draft.note, { onChange(draft.copy(note = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_note)) }, minLines = 2)
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(stringResource(R.string.schedule_reminder_label), Modifier.weight(1f))
                Switch(draft.reminderEnabled, { onChange(draft.copy(reminderEnabled = it)) })
            }
            if (draft.reminderEnabled) OutlinedTextField(draft.reminder, { onChange(draft.copy(reminder = it)) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.ai_reminder_minutes)) }, singleLine = true)
            Text(stringResource(R.string.ai_confidence, (draft.confidence * 100).toInt()), color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.End, modifier = Modifier.fillMaxWidth())
            if (draft.uncertainties.isNotEmpty()) {
                Text(stringResource(R.string.ai_uncertain_title), color = MaterialTheme.colorScheme.error)
                Text(draft.uncertainties.joinToString("\n"), color = MaterialTheme.colorScheme.error)
                Text(stringResource(R.string.ai_uncertain_message), color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            TextButton(onClick = onRemove, modifier = Modifier.align(Alignment.End)) { Text(stringResource(R.string.ai_remove_item)) }
        }
    }
}

private fun ParsedSchedule.toEditable(defaultReminder: Int, id: Long): EditableAiDraft = EditableAiDraft(
    id = id,
    title = title,
    date = date.toString(),
    time = time?.toString()?.take(5).orEmpty(),
    category = category.orEmpty(),
    note = note,
    reminder = (remindBeforeMinutes ?: defaultReminder.toLong()).toString(),
    reminderEnabled = remindBeforeMinutes != null,
    confidence = confidence,
    uncertainties = uncertainties,
)

private fun EditableAiDraft.toEntityOrNull(): ScheduleEntity? {
    val parsedDate = runCatching { LocalDate.parse(date.trim()) }.getOrNull() ?: return null
    val parsedTime = time.trim().takeIf { it.isNotEmpty() }?.let { runCatching { LocalTime.parse(it) }.getOrNull() } ?: if (time.isBlank()) null else return null
    val reminderMinutes = if (reminderEnabled) reminder.trim().toLongOrNull()?.takeIf { it in 0..43_200 } ?: return null else null
    return ScheduleEntity(
        title = title.trim().takeIf { it.isNotEmpty() } ?: return null,
        scheduledEpochDay = parsedDate.toEpochDay(),
        minuteOfDay = parsedTime?.let { it.hour * 60 + it.minute },
        category = category.trim().takeIf { it.isNotEmpty() },
        note = note.trim(),
        remindBeforeMinutes = reminderMinutes,
    )
}
