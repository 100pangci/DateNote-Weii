package com.datenote.app.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.animation.core.MutableTransitionState
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.shape.CircleShape
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
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntRect
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.LayoutDirection
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Popup
import androidx.compose.ui.window.PopupPositionProvider
import androidx.compose.ui.window.PopupProperties
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleEntity
import com.datenote.app.data.local.ScheduleStepEntity
import com.datenote.app.data.local.ScheduleWithSteps
import com.datenote.app.domain.model.ScheduleStatus
import com.datenote.app.domain.model.isOverdue
import com.datenote.app.domain.model.progress
import com.datenote.app.ui.theme.AppSpacing
import java.time.LocalDate
import kotlinx.coroutines.delay

private const val MenuEnterDuration = 190
private const val MenuExitDuration = 140

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
    autoCollapseCompletedSteps: Boolean,
    onEdit: () -> Unit,
    onToggleCompleted: () -> Unit,
    onToggleStep: (ScheduleStepEntity) -> Unit,
    onPostpone: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    val entity = schedule.schedule
    val hasSteps = schedule.progress.hasSteps
    val completed = entity.status == ScheduleStatus.COMPLETED
    var expanded by rememberSaveable(entity.id, defaultExpandSteps, autoCollapseCompletedSteps, hasSteps, completed) {
        mutableStateOf(defaultExpandSteps && hasSteps && (!completed || !autoCollapseCompletedSteps))
    }
    var menuExpanded by rememberSaveable(entity.id, "menu") { mutableStateOf(false) }
    val overdue = isOverdue(entity, LocalDate.now())
    val toggleCompletedAndCollapse = {
        if (autoCollapseCompletedSteps) expanded = false
        onToggleCompleted()
    }
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(
                    horizontal = AppSpacing.Content,
                    vertical = AppSpacing.CardVertical,
                ),
        ) {
            Box(
                modifier = Modifier.fillMaxWidth(),
            ) {
                Column(modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.Top,
                        horizontalArrangement = Arrangement.spacedBy(AppSpacing.CardColumnGap),
                    ) {
                        Spacer(Modifier.width(AppSpacing.CardLeading))
                        Column(modifier = Modifier.weight(1f)) {
                            ScheduleCardInfo(
                                schedule = schedule,
                                modifier = Modifier.fillMaxWidth(),
                            )
                        }
                        ScheduleCardActions(
                            hasSteps = hasSteps,
                            expanded = expanded,
                            completed = completed,
                            menuExpanded = menuExpanded,
                            onToggleExpanded = { expanded = !expanded },
                            onMenuExpandChange = { menuExpanded = it },
                            onEdit = onEdit,
                            onToggleCompleted = toggleCompletedAndCollapse,
                            onPostpone = onPostpone,
                            onDelete = onDelete,
                        )
                    }
                    ScheduleCardMeta(
                        schedule = schedule,
                        overdue = overdue,
                        completed = completed,
                        modifier = Modifier
                                .fillMaxWidth()
                                .padding(
                                    start = AppSpacing.CardLeading + AppSpacing.CardColumnGap,
                                    top = AppSpacing.Hairline,
                                ),
                    )
                }
                Box(
                    modifier = Modifier.matchParentSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .width(AppSpacing.CardLeading)
                            .fillMaxHeight(),
                        contentAlignment = Alignment.Center,
                    ) {
                        Checkbox(
                            checked = completed,
                            modifier = Modifier.size(AppSpacing.CardLeading),
                            onCheckedChange = { toggleCompletedAndCollapse() },
                        )
                    }
                }
            }
            if (hasSteps) {
                LinearProgressIndicator(
                    progress = { animatedProgress.coerceIn(0f, 1f) },
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(
                            top = AppSpacing.CardSection,
                        )
                        .height(AppSpacing.ProgressHeight)
                        .clip(CircleShape),
                    color = MaterialTheme.colorScheme.primary,
                    trackColor = MaterialTheme.colorScheme.primary.copy(alpha = 0.16f),
                )
            }
            AnimatedVisibility(
                visible = expanded && hasSteps,
                enter = expandVertically() + fadeIn(),
                exit = shrinkVertically() + fadeOut(),
            ) {
                ScheduleStepsSection(
                    steps = schedule.orderedSteps,
                    onToggleStep = onToggleStep,
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(top = AppSpacing.CardSection),
                )
            }
        }
    }
}

@Composable
private fun ScheduleCardInfo(
    schedule: ScheduleWithSteps,
    modifier: Modifier = Modifier,
) {
    val entity = schedule.schedule
    Row(
        modifier = modifier.heightIn(min = AppSpacing.CardLeading),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Hairline),
    ) {
        Text(
            text = entity.title,
            modifier = Modifier.weight(1f, fill = false),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        entity.category
            ?.takeIf { it.isNotBlank() }
            ?.let { category ->
                ScheduleTypeChip(label = category)
            }
        if (entity.remindBeforeMinutes != null) {
            Box(
                modifier = Modifier.size(AppSpacing.CardActionSlot),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = Icons.Default.Alarm,
                    contentDescription = stringResource(R.string.reminder_enabled),
                    modifier = Modifier.size(16.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ScheduleCardMeta(
    schedule: ScheduleWithSteps,
    overdue: Boolean,
    completed: Boolean,
    modifier: Modifier = Modifier,
) {
    val entity = schedule.schedule
    val today = LocalDate.now().toEpochDay()
    val startsLater = today < entity.startEpochDay
    val dueToday = today == entity.endEpochDay
    val inProgress = today >= entity.startEpochDay && today < entity.endEpochDay
    // Keep the home list focused on the date range. The exact deadline remains
    // available in the editor and is still used for reminder scheduling.
    val dateLine = dateSummary(entity)

    Column(
        modifier = modifier,
        verticalArrangement = Arrangement.spacedBy(AppSpacing.CardSection),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AppSpacing.Hairline),
        ) {
            Text(
                text = dateLine,
                modifier = Modifier.weight(1f, fill = false),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (overdue || completed || startsLater || dueToday || inProgress) {
                ScheduleStatusChip(
                    text = scheduleProgressText(schedule),
                    overdue = overdue,
                )
            }
        }
        if (!overdue && !completed && !startsLater && !dueToday && !inProgress) {
            Text(
                text = scheduleProgressText(schedule),
                modifier = Modifier.fillMaxWidth(),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                style = MaterialTheme.typography.labelMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
    }
}

@Composable
private fun ScheduleStatusChip(
    text: String,
    overdue: Boolean,
) {
    val containerColor = if (overdue) {
        MaterialTheme.colorScheme.errorContainer.copy(alpha = 0.7f)
    } else {
        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.55f)
    }
    val contentColor = if (overdue) {
        MaterialTheme.colorScheme.onErrorContainer
    } else {
        MaterialTheme.colorScheme.onPrimaryContainer
    }
    Box(
        modifier = Modifier
            .clip(CircleShape)
            .background(containerColor)
            .padding(
                horizontal = AppSpacing.ChipHorizontal,
                vertical = AppSpacing.ChipVertical,
            ),
    ) {
        Text(
            text = text,
            color = contentColor,
            style = MaterialTheme.typography.labelSmall,
            maxLines = 1,
        )
    }
}

@Composable
private fun ScheduleCardActions(
    hasSteps: Boolean,
    expanded: Boolean,
    completed: Boolean,
    menuExpanded: Boolean,
    onToggleExpanded: () -> Unit,
    onMenuExpandChange: (Boolean) -> Unit,
    onEdit: () -> Unit,
    onToggleCompleted: () -> Unit,
    onPostpone: (Long) -> Unit,
    onDelete: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.Top,
        horizontalArrangement = Arrangement.spacedBy(AppSpacing.Hairline),
    ) {
        if (hasSteps) {
            IconButton(
                modifier = Modifier.size(48.dp),
                onClick = onToggleExpanded,
            ) {
                Icon(
                    imageVector = if (expanded) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                    contentDescription = stringResource(
                        if (expanded) R.string.collapse_steps else R.string.expand_steps,
                    ),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        ScheduleMoreMenu(
            expanded = menuExpanded,
            completed = completed,
            onExpandChange = onMenuExpandChange,
            onEdit = onEdit,
            onToggleCompleted = onToggleCompleted,
            onPostpone = onPostpone,
            onDelete = onDelete,
        )
    }
}

@Composable
private fun ScheduleStepsSection(
    steps: List<ScheduleStepEntity>,
    onToggleStep: (ScheduleStepEntity) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier
            .clip(MaterialTheme.shapes.medium)
            .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f))
            .padding(
                horizontal = AppSpacing.StepGroupHorizontal,
                vertical = AppSpacing.StepGroupVertical,
            ),
        verticalArrangement = Arrangement.spacedBy(AppSpacing.StepRowGap),
    ) {
        steps.forEach { step ->
            ScheduleStepItem(
                step = step,
                onToggle = { checked ->
                    if (checked != step.isCompleted) onToggleStep(step)
                },
            )
        }
    }
}

@Composable
private fun ScheduleStepItem(
    step: ScheduleStepEntity,
    onToggle: (Boolean) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .heightIn(min = 48.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = step.isCompleted,
            modifier = Modifier.size(AppSpacing.CardLeading),
            onCheckedChange = onToggle,
        )
        Text(
            text = step.title,
            modifier = Modifier
                .weight(1f)
                .padding(start = AppSpacing.CardColumnGap),
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
    var opensDown by remember { mutableStateOf(true) }
    var popupMounted by remember { mutableStateOf(false) }
    val menuTransition = remember { MutableTransitionState(false) }
    val density = LocalDensity.current
    val positionProvider = remember(density) {
        ScheduleMenuPositionProvider(
            verticalMargin = with(density) { 48.dp.roundToPx() },
            onPositionCalculated = { opensDown = it },
        )
    }
    val menuActions = if (opensDown) {
        ScheduleMenuAction.entries.toList()
    } else {
        ScheduleMenuAction.entries.reversed()
    }
    LaunchedEffect(expanded) {
        if (expanded) {
            popupMounted = true
            menuTransition.targetState = true
        } else if (popupMounted) {
            menuTransition.targetState = false
            delay(MenuExitDuration.toLong())
            popupMounted = false
        }
    }
    Box {
        IconButton(
            modifier = Modifier.size(48.dp),
            onClick = { onExpandChange(true) },
        ) {
            Icon(
                imageVector = Icons.Default.MoreVert,
                contentDescription = stringResource(R.string.schedule_more),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        if (popupMounted) {
            Popup(
                popupPositionProvider = positionProvider,
                onDismissRequest = { onExpandChange(false) },
                properties = PopupProperties(focusable = true),
            ) {
                AnimatedVisibility(
                    visibleState = menuTransition,
                    enter = fadeIn(tween(MenuEnterDuration, easing = FastOutSlowInEasing)) + scaleIn(
                        animationSpec = tween(MenuEnterDuration, easing = FastOutSlowInEasing),
                        initialScale = 0.92f,
                        transformOrigin = TransformOrigin(1f, if (opensDown) 0f else 1f),
                    ),
                    exit = fadeOut(tween(MenuExitDuration, easing = FastOutSlowInEasing)) + scaleOut(
                        animationSpec = tween(MenuExitDuration, easing = FastOutSlowInEasing),
                        targetScale = 0.96f,
                        transformOrigin = TransformOrigin(1f, if (opensDown) 0f else 1f),
                    ),
                ) {
                    Surface(
                        modifier = Modifier.widthIn(max = 240.dp),
                        shape = MaterialTheme.shapes.extraLarge,
                        color = MaterialTheme.colorScheme.surfaceContainer,
                        tonalElevation = 3.dp,
                        shadowElevation = 3.dp,
                    ) {
                        Column(Modifier.padding(vertical = 8.dp)) {
                            menuActions.forEach { action ->
                                when (action) {
                    ScheduleMenuAction.DELETE -> DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                        text = { Text(stringResource(R.string.delete_schedule), color = MaterialTheme.colorScheme.error) },
                        leadingIcon = { Icon(Icons.Default.DeleteOutline, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                        onClick = {
                            onExpandChange(false)
                            onDelete()
                        },
                    )

                    ScheduleMenuAction.EDIT -> DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                        text = { Text(stringResource(R.string.edit_schedule)) },
                        leadingIcon = { Icon(Icons.Default.Edit, contentDescription = null) },
                        onClick = {
                            onExpandChange(false)
                            onEdit()
                        },
                    )

                    ScheduleMenuAction.TOGGLE_COMPLETED -> DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                        text = { Text(stringResource(if (completed) R.string.mark_todo else R.string.mark_completed)) },
                        leadingIcon = { Icon(Icons.Default.Check, contentDescription = null) },
                        onClick = {
                            onExpandChange(false)
                            onToggleCompleted()
                        },
                    )

                    ScheduleMenuAction.POSTPONE_DAY -> DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                        text = { Text(stringResource(R.string.postpone_one_day)) },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        onClick = {
                            onExpandChange(false)
                            onPostpone(1)
                        },
                    )

                    ScheduleMenuAction.POSTPONE_WEEK -> DropdownMenuItem(
                        modifier = Modifier.heightIn(min = 48.dp),
                        contentPadding = PaddingValues(horizontal = AppSpacing.Content),
                        text = { Text(stringResource(R.string.postpone_one_week)) },
                        leadingIcon = { Icon(Icons.Default.Schedule, contentDescription = null) },
                        onClick = {
                            onExpandChange(false)
                            onPostpone(7)
                        },
                    )
                }
                        }
                    }
                                }
                            }
                        }
                    }
                }
            }
private class ScheduleMenuPositionProvider(
    private val verticalMargin: Int,
    private val onPositionCalculated: (opensDown: Boolean) -> Unit,
) : PopupPositionProvider {
    override fun calculatePosition(
        anchorBounds: IntRect,
        windowSize: IntSize,
        layoutDirection: LayoutDirection,
        popupContentSize: IntSize,
    ): IntOffset {
        val maxX = (windowSize.width - popupContentSize.width).coerceAtLeast(0)
        val x = if (layoutDirection == LayoutDirection.Ltr) {
            (anchorBounds.right - popupContentSize.width).coerceIn(0, maxX)
        } else {
            anchorBounds.left.coerceIn(0, maxX)
        }
        val downY = anchorBounds.bottom
        val upY = anchorBounds.top - popupContentSize.height
        val (y, opensDown) = when {
            downY >= verticalMargin && downY + popupContentSize.height <= windowSize.height - verticalMargin -> downY to true
            upY >= verticalMargin && upY + popupContentSize.height <= windowSize.height - verticalMargin -> upY to false
            anchorBounds.center.y < windowSize.height / 2 -> verticalMargin to true
            else -> (windowSize.height - popupContentSize.height - verticalMargin).coerceAtLeast(verticalMargin) to false
        }
        onPositionCalculated(opensDown)
        return IntOffset(x, y)
    }
}

private enum class ScheduleMenuAction {
    DELETE,
    EDIT,
    TOGGLE_COMPLETED,
    POSTPONE_DAY,
    POSTPONE_WEEK,
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
