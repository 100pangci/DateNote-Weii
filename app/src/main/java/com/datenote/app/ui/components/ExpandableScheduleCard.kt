package com.datenote.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Alarm
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.isOverdue
import com.datenote.app.domain.model.progress
import com.datenote.app.ui.theme.AppSpacing
import java.time.LocalDate

/**
 * The single schedule card used by both the home day list and the all-schedules list.
 *
 * The expanded state is keyed by the database id so a refresh cannot move expansion
 * state to a different item in a LazyColumn.
 */
@Composable
fun ExpandableScheduleCard(
    schedule: ScheduleWithSteps,
    defaultExpandSteps: Boolean,
    onEdit: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleStep: (ScheduleStepEntity) -> Unit,
    onPostpone: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val entity = schedule.schedule
    val hasSteps = schedule.progress.hasSteps
    var expanded by rememberSaveable(entity.id, defaultExpandSteps, hasSteps) {
        mutableStateOf(defaultExpandSteps && hasSteps)
    }
    var menuExpanded by rememberSaveable(entity.id, "menu") { mutableStateOf(false) }
    val overdue = isOverdue(entity, LocalDate.now())
    val completed = entity.status == ScheduleStatus.COMPLETED
    val animatedProgress by animateFloatAsState(
        targetValue = schedule.progress.fraction,
        animationSpec = spring(
            dampingRatio = Spring.DampingRatioMediumBouncy,
            stiffness = Spring.StiffnessMedium,
        ),
        label = "schedule progress",
    )

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .animateContentSize(),
        shape = MaterialTheme.shapes.large,
        colors = CardDefaults.cardColors(
            containerColor = if (completed) {
                MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f)
            } else {
                MaterialTheme.colorScheme.surfaceContainer
            },
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 0.dp),
    ) {
        Column {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = AppSpacing.Tight, top = AppSpacing.Hairline, bottom = AppSpacing.Hairline, end = AppSpacing.Hairline),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(
                    checked = completed,
                    modifier = Modifier.size(48.dp),
                    onCheckedChange = { onToggleCompleted() },
                )
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .padding(horizontal = AppSpacing.Hairline),
                    verticalArrangement = Arrangement.spacedBy(1.dp),
                ) {
                    Text(
                        text = entity.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        text = dateSummary(entity),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 2,
                        softWrap = true,
                        overflow = TextOverflow.Ellipsis,
                    )
                    val detail = buildString {
                        entity.minuteOfDay?.let { append("%02d:%02d".format(it / 60, it % 60)) }
                        if (entity.minuteOfDay != null && entity.category != null) append(" · ")
                        entity.category?.let(::append)
                    }
                    if (detail.isNotBlank()) {
                        Text(
                            text = detail,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        Text(
                            text = scheduleProgressText(schedule),
                            modifier = Modifier.weight(1f),
                            color = if (overdue) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                             style = MaterialTheme.typography.labelMedium,
                            maxLines = 2,
                            softWrap = true,
                        )
                        if (entity.remindBeforeMinutes != null) {
                            Icon(
                                imageVector = Icons.Default.Alarm,
                                contentDescription = stringResource(R.string.reminder_enabled),
                                modifier = Modifier.size(16.dp),
                                tint = MaterialTheme.colorScheme.primary,
                            )
                        }
                    }
                    if (hasSteps) {
                        Text(
                            text = stringResource(R.string.progress_completed, schedule.progress.completedCount, schedule.progress.totalCount),
                            style = MaterialTheme.typography.labelMedium,
                        )
                        LinearProgressIndicator(
                            progress = { animatedProgress.coerceIn(0f, 1f) },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = AppSpacing.Hairline),
                        )
                    }
                }
                if (hasSteps) {
                    IconButton(
                        modifier = Modifier.size(48.dp),
                        onClick = { expanded = !expanded },
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = stringResource(
                                if (expanded) R.string.collapse_steps else R.string.expand_steps,
                            ),
                        )
                    }
                }
                ScheduleMoreMenu(
                    expanded = menuExpanded,
                    completed = completed,
                    onExpandChange = { menuExpanded = it },
                    onEdit = onEdit,
                    onToggleCompleted = onToggleCompleted,
                    onPostpone = onPostpone,
                    onDelete = onDelete,
                )
            }
            AnimatedVisibility(
                visible = expanded && hasSteps,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(start = AppSpacing.Content, end = AppSpacing.Tight, bottom = AppSpacing.Hairline),
                ) {
                    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.55f))
                    schedule.orderedSteps.forEach { step ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .heightIn(min = 48.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            Checkbox(
                                checked = step.isCompleted,
                                modifier = Modifier.size(48.dp),
                                onCheckedChange = { checked ->
                                    if (checked != step.isCompleted) onToggleStep(step)
                                },
                            )
                            Text(
                                text = step.title,
                                modifier = Modifier.weight(1f),
                                color = if (step.isCompleted) {
                                    MaterialTheme.colorScheme.onSurfaceVariant
                                } else {
                                    MaterialTheme.colorScheme.onSurface
                                },
                                textDecoration = if (step.isCompleted) TextDecoration.LineThrough else null,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScheduleMoreMenu(
    expanded: Boolean,
    completed: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onToggleCompleted: () -> Unit,
    onPostpone: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    Box {
        IconButton(
            modifier = Modifier.size(48.dp),
            onClick = { onExpandChange(true) },
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.schedule_more),
            )
        }
        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { onExpandChange(false) },
            modifier = Modifier.widthIn(max = 240.dp),
        ) {
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                text = { Text(stringResource(R.string.edit_schedule)) },
                leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                onClick = {
                    onExpandChange(false)
                    onEdit()
                },
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                text = { Text(stringResource(if (completed) R.string.mark_todo else R.string.mark_completed)) },
                leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                onClick = {
                    onExpandChange(false)
                    onToggleCompleted()
                },
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                text = { Text(stringResource(R.string.postpone_one_day)) },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                onClick = {
                    onExpandChange(false)
                    onPostpone(1)
                },
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                text = { Text(stringResource(R.string.postpone_one_week)) },
                leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                onClick = {
                    onExpandChange(false)
                    onPostpone(7)
                },
            )
            DropdownMenuItem(
                modifier = Modifier.heightIn(min = 48.dp),
                contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                text = { Text(stringResource(R.string.delete_schedule), color = MaterialTheme.colorScheme.error) },
                leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                onClick = {
                    onExpandChange(false)
                    onDelete()
                },
            )
        }
    }
}

@Composable
private fun dateSummary(schedule: ScheduleEntity): String {
    val start = LocalDate.ofEpochDay(schedule.startEpochDay)
    val end = LocalDate.ofEpochDay(schedule.endEpochDay)
    return if (start == end) {
        stringResource(R.string.single_day_summary, start.monthValue, start.dayOfMonth)
    } else {
        stringResource(
            R.string.schedule_card_date_range,
            start.monthValue,
            start.dayOfMonth,
            end.monthValue,
            end.dayOfMonth,
        )
    }
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
