@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.datenote.app.ui.settings

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.R
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.ThemeMode
import com.datenote.app.data.repository.UserPreferences
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.data.secure.SecureApiKeyStore
import com.datenote.app.reminder.ReminderScheduler

@Composable
fun SettingsScreen(
    preferencesRepository: UserPreferencesRepository,
    keyStore: SecureApiKeyStore,
    aiRepository: AiRepository,
    scheduleRepository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    onRequestNotifications: () -> Unit,
    onNavigate: (String) -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(
        factory = SettingsViewModel.Factory(
            preferencesRepository,
            keyStore,
            aiRepository,
            scheduleRepository,
            reminderScheduler,
        ),
    )
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    LaunchedEffect(Unit) { viewModel.refreshReliability(context) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshReliability(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsHomeScreen(state = state, onNavigate = onNavigate)
}

@Composable
private fun SettingsHomeScreen(
    state: SettingsState,
    onNavigate: (String) -> Unit,
) {
    val reliability = state.reliability
    val reliabilitySummary = if (
        reliability?.notificationStatus?.permissionGranted == true &&
        reliability.notificationStatus.appNotificationsEnabled &&
        reliability.notificationStatus.channelEnabled
    ) {
        stringResource(R.string.reliability_enabled)
    } else {
        stringResource(R.string.reliability_not_enabled)
    }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings)) }) },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding),
            contentPadding = PaddingValues(horizontal = 20.dp, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(0.dp),
        ) {
            item { SettingsGroupTitle(stringResource(R.string.settings_group_preferences)) }
            item {
                SettingsEntry(
                    icon = Icons.Default.Person,
                    title = stringResource(R.string.settings_personalization),
                    supporting = stringResource(R.string.nickname_value, state.preferences.nickname),
                    onClick = { onNavigate(SettingsDestination.PERSONALIZATION) },
                )
            }
            item {
                SettingsEntry(
                    icon = Icons.AutoMirrored.Filled.List,
                    title = stringResource(R.string.settings_schedule_types),
                    supporting = stringResource(R.string.settings_schedule_types_summary),
                    onClick = { onNavigate(SettingsDestination.SCHEDULE_TYPES) },
                )
            }
            item { HorizontalDivider() }
            item { SettingsGroupTitle(stringResource(R.string.settings_group_assistant)) }
            item {
                SettingsEntry(
                    icon = Icons.Default.AutoAwesome,
                    title = stringResource(R.string.settings_ai),
                    supporting = stringResource(R.string.ai_settings_summary, state.preferences.aiModel),
                    onClick = { onNavigate(SettingsDestination.AI) },
                )
            }
            item { HorizontalDivider() }
            item { SettingsGroupTitle(stringResource(R.string.settings_group_notifications)) }
            item {
                SettingsEntry(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.settings_reminders),
                    supporting = stringResource(R.string.reminder_settings_summary, reminderLabel(state.preferences)),
                    onClick = { onNavigate(SettingsDestination.REMINDERS) },
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsEntry(
                    icon = Icons.Default.VerifiedUser,
                    title = stringResource(R.string.settings_reminder_reliability),
                    supporting = reliabilitySummary,
                    onClick = { onNavigate(SettingsDestination.RELIABILITY) },
                )
            }
            item { HorizontalDivider() }
            item { SettingsGroupTitle(stringResource(R.string.settings_group_data)) }
            item {
                SettingsEntry(
                    icon = Icons.Default.Backup,
                    title = stringResource(R.string.settings_data),
                    supporting = stringResource(R.string.data_backup_summary),
                    onClick = { onNavigate(SettingsDestination.DATA) },
                )
            }
            item { HorizontalDivider() }
            item {
                SettingsEntry(
                    icon = Icons.Default.Info,
                    title = stringResource(R.string.settings_about),
                    supporting = stringResource(R.string.app_subtitle),
                    onClick = { onNavigate(SettingsDestination.ABOUT) },
                )
            }
        }
    }
}

@Composable
private fun SettingsGroupTitle(title: String) {
    Text(
        text = title,
        modifier = Modifier.padding(start = 16.dp, top = 18.dp, bottom = 6.dp),
        color = MaterialTheme.colorScheme.primary,
        style = MaterialTheme.typography.labelLarge,
    )
}

@Composable
private fun SettingsEntry(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    supporting: String,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.clickable(onClick = onClick),
        leadingContent = {
            Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
        },
        headlineContent = { Text(title) },
        supportingContent = { Text(supporting) },
        trailingContent = {
            Icon(
                Icons.Default.ChevronRight,
                contentDescription = stringResource(R.string.open_setting),
            )
        },
    )
}

@Composable
private fun reminderLabel(preferences: UserPreferences): String = when (preferences.defaultReminderMinutes) {
    1440 -> stringResource(R.string.reminder_one_day)
    4320 -> stringResource(R.string.reminder_three_days)
    10080 -> stringResource(R.string.reminder_one_week)
    else -> stringResource(R.string.reminder_minutes, preferences.defaultReminderMinutes)
}
