@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.datenote.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.DragHandle
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.ListItem
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.local.ScheduleTypeWithSteps
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.ui.components.AppOutlinedTextField
import com.datenote.app.ui.components.AppPrimaryButton
import com.datenote.app.ui.components.AppSectionTitle
import com.datenote.app.ui.components.AppTopAppBar
import com.datenote.app.ui.components.ReorderableColumn
import com.datenote.app.ui.theme.AppSpacing

@Composable
fun ScheduleTypesScreen(
    repository: ScheduleRepository,
    onBack: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (Long) -> Unit,
) {
    val viewModel: ScheduleTypesViewModel = viewModel(factory = ScheduleTypesViewModel.Factory(repository))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var deleting by remember { mutableStateOf<ScheduleTypeWithSteps?>(null) }

    Scaffold(
        topBar = {
            AppTopAppBar(stringResource(R.string.schedule_types_title), onBack)
        },
        floatingActionButton = {
            FloatingActionButton(onClick = onAdd) {
                Icon(Icons.Default.Add, contentDescription = stringResource(R.string.add_schedule_type))
            }
        },
    ) { padding ->
        if (state.types.isEmpty()) {
            EmptyScheduleTypes(padding, onAdd)
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.Tight),
            ) {
                items(state.types, key = { it.type.id }) { type ->
                    ScheduleTypeListItem(
                        type = type,
                        onEdit = { onEdit(type.type.id) },
                        onDelete = { deleting = type },
                    )
                }
            }
        }
    }

    deleting?.let { type ->
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text(stringResource(R.string.delete_schedule_type_title)) },
            text = { Text(stringResource(R.string.delete_schedule_type_message)) },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text(stringResource(R.string.think_again)) } },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.delete(type.type.id) { deleting = null }
                }) { Text(stringResource(R.string.confirm_delete), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
private fun EmptyScheduleTypes(padding: PaddingValues, onAdd: () -> Unit) {
    Box(
        modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 32.dp),
        contentAlignment = Alignment.Center,
    ) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(stringResource(R.string.schedule_types_empty_title), style = MaterialTheme.typography.titleLarge)
            Text(
                stringResource(R.string.schedule_types_empty_message),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Button(onClick = onAdd) { Text(stringResource(R.string.add_schedule_type)) }
        }
    }
}

@Composable
private fun ScheduleTypeListItem(
    type: ScheduleTypeWithSteps,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
) {
    val summary = type.orderedSteps.joinToString(stringResource(R.string.schedule_type_steps_separator)) { it.title }
    ListItem(
        modifier = Modifier.fillMaxWidth(),
        headlineContent = { Text(type.type.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                stringResource(R.string.schedule_type_item_summary, type.orderedSteps.size, summary),
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            Row {
                IconButton(onClick = onEdit) {
                    Icon(Icons.Default.Edit, contentDescription = stringResource(R.string.edit_schedule_type))
                }
                IconButton(onClick = onDelete) {
                    Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete_schedule_type))
                }
            }
        },
    )
}

@Composable
fun ScheduleTypeEditorScreen(
    repository: ScheduleRepository,
    typeId: Long?,
    onBack: () -> Unit,
    onSaved: () -> Unit,
) {
    val viewModel: ScheduleTypeEditorViewModel = viewModel(
        key = "schedule-type-editor-${typeId ?: "new"}",
        factory = ScheduleTypeEditorViewModel.Factory(repository, typeId),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val focusManager = LocalFocusManager.current
    Scaffold(
        topBar = {
            AppTopAppBar(
                title = stringResource(if (typeId == null) R.string.add_schedule_type else R.string.edit_schedule_type),
                onBack = onBack,
            )
        },
    ) { padding ->
        if (state.isLoading) {
            Box(Modifier.fillMaxSize().padding(padding), contentAlignment = Alignment.Center) { CircularProgressIndicator() }
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize().padding(padding),
                contentPadding = PaddingValues(
                    start = AppSpacing.ScreenHorizontal,
                    top = AppSpacing.ScreenTop,
                    end = AppSpacing.ScreenHorizontal,
                    bottom = AppSpacing.ScreenBottom,
                ),
                verticalArrangement = Arrangement.spacedBy(AppSpacing.Compact),
            ) {
                item {
                    AppOutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::setName,
                        modifier = Modifier.fillMaxWidth(),
                        label = { Text(stringResource(R.string.schedule_type_name)) },
                        placeholder = { Text(stringResource(R.string.schedule_type_name_placeholder)) },
                        supportingText = {
                            val message = when (state.error) {
                                ScheduleTypeEditorError.NAME_REQUIRED -> stringResource(R.string.schedule_type_name_required)
                                ScheduleTypeEditorError.NAME_TOO_LONG -> stringResource(R.string.schedule_type_name_too_long)
                                ScheduleTypeEditorError.DUPLICATE_NAME -> stringResource(R.string.schedule_type_duplicate)
                                else -> stringResource(R.string.schedule_type_name_support)
                            }
                            Text(message)
                        },
                        isError = state.error in setOf(
                            ScheduleTypeEditorError.NAME_REQUIRED,
                            ScheduleTypeEditorError.NAME_TOO_LONG,
                            ScheduleTypeEditorError.DUPLICATE_NAME,
                        ),
                        singleLine = true,
                    )
                }
                item {
                    AppSectionTitle(stringResource(R.string.default_steps))
                    Text(stringResource(R.string.default_steps_support), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall)
                }
                if (state.steps.isEmpty()) {
                    item {
                        Text(stringResource(R.string.no_default_steps), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                } else {
                    item {
                        ReorderableColumn(
                            items = state.steps,
                            onMove = viewModel::moveStep,
                            onDragStart = { focusManager.clearFocus() },
                        ) { step, dragHandleModifier ->
                            val index = state.steps.indexOfFirst { it.stableId == step.stableId }
                            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                                AppOutlinedTextField(
                                    value = step.title,
                                    onValueChange = { viewModel.setStep(index, it) },
                                    modifier = Modifier.weight(1f),
                                    label = { Text(stringResource(R.string.step_name)) },
                                    isError = state.error == ScheduleTypeEditorError.STEP_REQUIRED && step.title.trim().isEmpty(),
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
                                IconButton(onClick = { viewModel.removeStep(index) }) {
                                    Icon(Icons.Default.DeleteOutline, contentDescription = stringResource(R.string.delete_step))
                                }
                            }
                        }
                    }
                }
                if (state.error == ScheduleTypeEditorError.STEP_REQUIRED) {
                    item { Text(stringResource(R.string.default_step_required), color = MaterialTheme.colorScheme.error) }
                }
                item {
                    TextButton(onClick = viewModel::addStep, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.add_step))
                    }
                }
                item {
                    AppPrimaryButton(onClick = { viewModel.save(onSaved) }, modifier = Modifier.fillMaxWidth()) {
                        Text(stringResource(R.string.save_schedule_type))
                    }
                }
            }
        }
    }
}
