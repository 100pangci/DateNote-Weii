package com.datenote.app.ui.all

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Clear
import androidx.compose.material.icons.filled.Search
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.progress
import com.datenote.app.ui.components.ExpandableScheduleCard
import com.datenote.app.ui.components.AppSoftTextField
import com.datenote.app.ui.components.AppTopAppBar
import com.datenote.app.ui.theme.AppSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AllSchedulesScreen(
    repository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    defaultReminderTimeMinutes: Int,
    defaultExpandSteps: Boolean,
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

    Scaffold(
        topBar = { AppTopAppBar(stringResource(R.string.all_schedules)) },
    ) { padding ->
        Column(
            Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.ScreenTop),
        ) {
            AppSoftTextField(
                value = query,
                onValueChange = viewModel::setQuery,
                modifier = Modifier.fillMaxWidth(),
                label = { Text(stringResource(R.string.search_schedules)) },
                leadingIcon = { androidx.compose.material3.Icon(Icons.Default.Search, contentDescription = null) },
                trailingIcon = if (query.isNotBlank()) {
                    {
                        androidx.compose.material3.IconButton(onClick = { viewModel.setQuery("") }) {
                            androidx.compose.material3.Icon(Icons.Default.Clear, contentDescription = stringResource(R.string.clear_search))
                        }
                    }
                } else null,
                singleLine = true,
            )
            Spacer(Modifier.height(AppSpacing.Compact))
            Text(stringResource(R.string.schedule_filter_label), style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary)
            Spacer(Modifier.height(AppSpacing.Tight))
            Surface(
                modifier = Modifier.fillMaxWidth(),
                shape = MaterialTheme.shapes.large,
                color = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
            ) {
                androidx.compose.foundation.layout.Row(
                    Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState())
                        .padding(horizontal = AppSpacing.Tight, vertical = AppSpacing.Hairline),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    filters.forEach { (value, label) ->
                        FilterChip(
                            selected = filter == value,
                            onClick = { viewModel.setFilter(value) },
                            label = { Text(stringResource(label), style = MaterialTheme.typography.labelMedium) },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer,
                                containerColor = MaterialTheme.colorScheme.surfaceContainerLow,
                                labelColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            ),
                        )
                    }
                }
            }
            Spacer(Modifier.height(AppSpacing.Content))
            HorizontalDivider(
                modifier = Modifier.padding(horizontal = AppSpacing.Tight),
                color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f),
            )
            if (schedules.isEmpty()) {
                EmptyAllState(filter = filter, query = query)
            } else {
                LazyColumn(
                    Modifier.weight(1f),
                    contentPadding = PaddingValues(top = AppSpacing.Content, bottom = AppSpacing.ScreenBottom),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    item {
                        androidx.compose.foundation.layout.Row(
                            Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Text(
                                text = stringResource(filterLabel(filter)),
                                style = MaterialTheme.typography.titleMedium,
                                modifier = Modifier.weight(1f),
                            )
                            Text(
                                text = stringResource(R.string.schedules_count, schedules.size),
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                    }
                    items(schedules, key = { it.schedule.id }) { schedule ->
                        ExpandableScheduleCard(
                            schedule = schedule,
                            defaultExpandSteps = defaultExpandSteps,
                            onEdit = { onEdit(schedule.schedule.id) },
                            onToggleCompleted = {
                                if (schedule.schedule.status == ScheduleStatus.COMPLETED) viewModel.toggleCompleted(schedule)
                                else if (schedule.progress.hasSteps && schedule.progress.completedCount < schedule.progress.totalCount) pendingComplete = schedule
                                else viewModel.markCompleted(schedule, false)
                            },
                            onToggleStep = { step -> viewModel.toggleStep(schedule, step) },
                            onPostpone = { days -> viewModel.postpone(schedule, days) },
                            onDelete = { pendingDelete = schedule },
                        )
                    }
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

private fun filterLabel(filter: ScheduleFilter): Int = when (filter) {
    ScheduleFilter.ALL -> R.string.all_filter_all
    ScheduleFilter.RECENT -> R.string.all_filter_recent
    ScheduleFilter.TODAY -> R.string.all_filter_today
    ScheduleFilter.OVERDUE -> R.string.all_filter_overdue
    ScheduleFilter.COMPLETED -> R.string.all_filter_completed
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
