package com.datenote.app.ui.all

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.datenote.app.domain.model.progress
import java.time.LocalDate

@Composable
fun AllSchedulesScreen(
    repository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    defaultReminderTimeMinutes: Int,
    onEdit: (Long) -> Unit,
) {
    val viewModel: AllSchedulesViewModel = viewModel(factory = AllSchedulesViewModel.Factory(repository, reminderScheduler, defaultReminderTimeMinutes))
    val filter by viewModel.filter.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val schedules by viewModel.schedules.collectAsStateWithLifecycle()
    var pendingDelete by remember { mutableStateOf<ScheduleWithSteps?>(null) }
    var pendingComplete by remember { mutableStateOf<ScheduleWithSteps?>(null) }
    val filters = listOf(
        ScheduleFilter.ALL to R.string.all_filter_all,
        ScheduleFilter.RECENT to R.string.all_filter_recent,
        ScheduleFilter.TODAY to R.string.all_filter_today,
        ScheduleFilter.OVERDUE to R.string.all_filter_overdue,
        ScheduleFilter.COMPLETED to R.string.all_filter_completed,
    )

    Column(Modifier.fillMaxSize().padding(horizontal = 20.dp, vertical = 16.dp)) {
        Text(stringResource(R.string.all_schedules), style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = query,
            onValueChange = viewModel::setQuery,
            modifier = Modifier.fillMaxWidth(),
            label = { Text(stringResource(R.string.search_schedules)) },
            singleLine = true,
        )
        Spacer(Modifier.height(12.dp))
        Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            filters.forEach { (value, label) ->
                FilterChip(selected = filter == value, onClick = { viewModel.setFilter(value) }, label = { Text(stringResource(label)) })
            }
        }
        Spacer(Modifier.height(12.dp))
        if (schedules.isEmpty()) {
            EmptyAllState(filter = filter, query = query)
        } else {
            LazyColumn(contentPadding = PaddingValues(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                items(schedules, key = { it.schedule.id }) { schedule ->
                    AllScheduleRow(
                        schedule = schedule,
                        onClick = { onEdit(schedule.schedule.id) },
                        onToggle = {
                            if (schedule.schedule.status == ScheduleStatus.COMPLETED) viewModel.toggleCompleted(schedule)
                            else if (schedule.progress.hasSteps && schedule.progress.completedCount < schedule.progress.totalCount) pendingComplete = schedule
                            else viewModel.markCompleted(schedule, false)
                        },
                        onDelete = { pendingDelete = schedule },
                    )
                }
            }
        }
    }
    pendingDelete?.let { schedule ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = { Text(stringResource(R.string.delete_confirm_message)) },
            dismissButton = { TextButton(onClick = { pendingDelete = null }) { Text(stringResource(R.string.think_again)) } },
            confirmButton = { TextButton(onClick = { viewModel.delete(schedule); pendingDelete = null }) { Text(stringResource(R.string.confirm_delete), color = MaterialTheme.colorScheme.error) } },
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
 private fun AllScheduleRow(schedule: ScheduleWithSteps, onClick: () -> Unit, onToggle: () -> Unit, onDelete: () -> Unit) {
    Card(
        modifier = Modifier.fillMaxWidth(),
         colors = CardDefaults.cardColors(containerColor = if (schedule.schedule.status == ScheduleStatus.COMPLETED) MaterialTheme.colorScheme.surfaceVariant.copy(alpha = .5f) else MaterialTheme.colorScheme.surfaceContainer),
        onClick = onClick,
    ) {
        Row(Modifier.fillMaxWidth().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
             Checkbox(schedule.schedule.status == ScheduleStatus.COMPLETED, onCheckedChange = { onToggle() })
             Column(Modifier.weight(1f).padding(horizontal = 4.dp)) {
                 Text(schedule.schedule.title, style = MaterialTheme.typography.titleMedium, maxLines = 2, overflow = TextOverflow.Ellipsis)
                 val start = LocalDate.ofEpochDay(schedule.schedule.startEpochDay)
                 val end = LocalDate.ofEpochDay(schedule.schedule.endEpochDay)
                 Text(if (start == end) "${start.monthValue}月${start.dayOfMonth}日" else "${start.monthValue}月${start.dayOfMonth}日～${end.monthValue}月${end.dayOfMonth}日", color = MaterialTheme.colorScheme.onSurfaceVariant)
                 if (schedule.schedule.category != null) Text(schedule.schedule.category!!, color = MaterialTheme.colorScheme.onSurfaceVariant)
                 if (schedule.schedule.note.isNotBlank()) Text(schedule.schedule.note, maxLines = 1, overflow = TextOverflow.Ellipsis, color = MaterialTheme.colorScheme.onSurfaceVariant)
                 if (schedule.progress.hasSteps) {
                     Text(stringResource(R.string.progress_completed, schedule.progress.completedCount, schedule.progress.totalCount), style = MaterialTheme.typography.labelMedium)
                     LinearProgressIndicator(progress = { schedule.progress.fraction }, modifier = Modifier.fillMaxWidth())
                     schedule.orderedSteps.firstOrNull { !it.isCompleted }?.let { next -> Text(stringResource(R.string.next_step, next.title), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelMedium) }
                 }
            }
            TextButton(onClick = onDelete) { Text(stringResource(R.string.delete_schedule)) }
        }
    }
}

@Composable
private fun EmptyAllState(filter: ScheduleFilter, query: String) {
    val search = query.isNotBlank()
    Column(Modifier.fillMaxWidth().padding(top = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(if (search) stringResource(R.string.search_empty_title) else when (filter) {
            ScheduleFilter.RECENT -> stringResource(R.string.recent_empty_title)
            ScheduleFilter.TODAY -> stringResource(R.string.today_empty_title)
            ScheduleFilter.OVERDUE -> stringResource(R.string.overdue_empty_title)
            ScheduleFilter.COMPLETED -> stringResource(R.string.completed_empty_title)
            ScheduleFilter.ALL -> stringResource(R.string.recent_empty_title)
        }, style = MaterialTheme.typography.titleMedium)
        Text(if (search) stringResource(R.string.search_empty_message) else when (filter) {
            ScheduleFilter.RECENT -> stringResource(R.string.recent_empty_message)
            ScheduleFilter.TODAY -> stringResource(R.string.today_empty_message)
            ScheduleFilter.OVERDUE -> stringResource(R.string.overdue_empty_message)
            ScheduleFilter.COMPLETED, ScheduleFilter.ALL -> ""
        }, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}
