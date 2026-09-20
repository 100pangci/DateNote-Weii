package com.datenote.app.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.BuildConfig
import com.datenote.app.R
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.ThemeMode
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.secure.SecureApiKeyStore
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.reminder.ReminderSettings
import com.datenote.app.data.backup.BackupDocument
import com.datenote.app.data.backup.BackupSchedule
import com.datenote.app.data.backup.toBackup
import com.datenote.app.data.backup.toScheduleWithSteps
import com.datenote.app.data.local.ScheduleWithSteps
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Composable
fun SettingsScreen(
    preferencesRepository: UserPreferencesRepository,
    keyStore: SecureApiKeyStore,
    aiRepository: AiRepository,
    scheduleRepository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    onRequestNotifications: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(preferencesRepository, keyStore, aiRepository, scheduleRepository, reminderScheduler))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var nicknameDialog by remember { mutableStateOf(false) }
    var nicknameInput by remember(state.preferences.nickname) { mutableStateOf(state.preferences.nickname) }
    var baseUrl by remember(state.preferences.aiBaseUrl) { mutableStateOf(state.preferences.aiBaseUrl) }
    var model by remember(state.preferences.aiModel) { mutableStateOf(state.preferences.aiModel) }
    var apiKey by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var timeText by remember(state.preferences.defaultReminderTimeMinutes) { mutableStateOf(minutesToTime(state.preferences.defaultReminderTimeMinutes)) }
    var clearDialog by remember { mutableStateOf(false) }
    var savedMessageVisible by remember { mutableStateOf(false) }
    var feedback by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<List<ScheduleWithSteps>?>(null) }
    var replaceDialog by remember { mutableStateOf(false) }
    var expandedSections by rememberSaveable { mutableStateOf(setOf("personalization")) }
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var reliabilityGuideVisible by remember { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshReliability(context) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshReliability(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    val backupFailedText = stringResource(R.string.backup_failed)
    val backupSavedText = stringResource(R.string.backup_saved)
    val backupRestoredText = stringResource(R.string.backup_restored)
    val backupFileName = stringResource(R.string.backup_file_name)
    val scope = androidx.compose.runtime.rememberCoroutineScope()
    val backupJson = remember { Json { prettyPrint = true; ignoreUnknownKeys = true } }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val success = runCatching {
                    val schedules = withContext(Dispatchers.IO) { scheduleRepository.observeAllWithSteps().first() }
                    val document = BackupDocument(exportedAt = System.currentTimeMillis(), schedules = schedules.map { it.toBackup() })
                withContext(Dispatchers.IO) { context.contentResolver.openOutputStream(uri)?.use { it.write(backupJson.encodeToString(document).toByteArray()) } ?: error("write") }
            }.isSuccess
            feedback = if (success) backupSavedText else backupFailedText
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: error("read")
                    backupJson.decodeFromString<BackupDocument>(text).let { document ->
                        require(document.schemaVersion in 1..2)
                        document.schedules.mapNotNull(BackupSchedule::toScheduleWithSteps)
                    }
                }.getOrNull()
            }
            if (imported == null) feedback = backupFailedText else pendingImport = imported
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(horizontal = 20.dp, vertical = 18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item { Text(stringResource(R.string.settings), style = MaterialTheme.typography.headlineSmall) }
        item {
            CollapsibleSettingsSection(
                title = stringResource(R.string.settings_personalization),
                expanded = "personalization" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("personalization") },
            ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = { nicknameDialog = true }, modifier = Modifier.fillMaxWidth()) {
                        Column(Modifier.fillMaxWidth(), horizontalAlignment = Alignment.Start) {
                            Text(stringResource(R.string.my_nickname), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.nickname_value, state.preferences.nickname), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                    Text(stringResource(R.string.privacy_nickname), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(stringResource(R.string.theme_mode), style = MaterialTheme.typography.titleMedium)
                    ThemePicker(state.preferences.themeMode, viewModel::setThemeMode)
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.titleMedium)
                            Text(stringResource(R.string.dynamic_color_support), color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(state.preferences.dynamicColor, viewModel::setDynamicColor)
                    }
                }
            }
            }
        }
        item {
            CollapsibleSettingsSection(
                title = stringResource(R.string.settings_ai),
                expanded = "ai" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("ai") },
            ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.base_url)) }, supportingText = { Text(stringResource(R.string.base_url_support)) }, singleLine = true)
                    OutlinedTextField(apiKey, { apiKey = it; viewModel.setApiKey(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.api_key)) }, supportingText = { Text(stringResource(R.string.api_key_support)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
                    OutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model_name)) }, singleLine = true)
                    Button(onClick = { viewModel.saveAiSettings(baseUrl, model); savedMessageVisible = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.save_ai_settings)) }
                    Button(onClick = { viewModel.testConnection(baseUrl, model, apiKey) }, enabled = state.connectionState != ConnectionState.TESTING, modifier = Modifier.fillMaxWidth()) { Text(if (state.connectionState == ConnectionState.TESTING) stringResource(R.string.connection_testing) else stringResource(R.string.test_ai_connection)) }
                    when (state.connectionState) {
                        ConnectionState.SUCCESS -> Text(stringResource(R.string.connection_success), color = MaterialTheme.colorScheme.primary)
                        ConnectionState.FAILED -> Text(stringResource(R.string.connection_failed), color = MaterialTheme.colorScheme.error)
                        else -> Unit
                    }
                    if (savedMessageVisible) Text(stringResource(R.string.connection_success), color = MaterialTheme.colorScheme.primary)
                }
            }
            }
        }
        item {
            CollapsibleSettingsSection(
                title = stringResource(R.string.settings_reminders),
                expanded = "reminders" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("reminders") },
            ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(stringResource(R.string.default_reminder), style = MaterialTheme.typography.titleMedium)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(1440 to R.string.reminder_one_day, 4320 to R.string.reminder_three_days, 10080 to R.string.reminder_one_week).forEach { (minutes, label) ->
                            FilterChip(selected = state.preferences.defaultReminderMinutes == minutes, onClick = { viewModel.setDefaultReminder(minutes) }, label = { Text(stringResource(label)) })
                        }
                    }
                    OutlinedTextField(timeText, { value ->
                        timeText = value
                        parseTimeMinutes(value)?.let(viewModel::setDefaultReminderTime)
                    }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.default_reminder_time)) }, supportingText = { Text(stringResource(R.string.time_format_hint)) }, singleLine = true)
                }
            }
            }
        }
        item {
            CollapsibleSettingsSection(
                title = stringResource(R.string.settings_reminder_reliability),
                expanded = "reliability" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("reliability") },
            ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    val reliability = state.reliability
                    ReliabilityRow(
                        stringResource(R.string.reliability_notification_permission),
                        if (reliability?.notificationStatus?.permissionGranted == true && reliability.notificationStatus.appNotificationsEnabled) stringResource(R.string.reliability_enabled) else stringResource(R.string.reliability_not_enabled),
                        reliability?.notificationStatus?.permissionGranted == true && reliability.notificationStatus.appNotificationsEnabled,
                    )
                    ReliabilityRow(
                        stringResource(R.string.reliability_channel),
                        if (reliability?.notificationStatus?.channelEnabled == true) stringResource(R.string.reliability_available) else stringResource(R.string.reliability_closed),
                        reliability?.notificationStatus?.channelEnabled == true,
                    )
                    ReliabilityRow(
                        stringResource(R.string.reliability_battery),
                        if (reliability?.batteryOptimizationIgnored == true) stringResource(R.string.reliability_battery_ok) else stringResource(R.string.reliability_battery_limited),
                        reliability?.batteryOptimizationIgnored == true,
                    )
                    ReliabilityRow(
                        stringResource(R.string.reliability_autostart),
                        stringResource(R.string.reliability_manual),
                        null,
                    )
                    Text(stringResource(R.string.reliability_background_hint), color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Button(onClick = onRequestNotifications, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reliability_check_notifications)) }
                    Button(onClick = { ReminderSettings.openAppDetails(context) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reliability_check_background)) }
                    Button(onClick = { ReminderSettings.openBatterySettings(context) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reliability_check_battery)) }
                    TextButton(onClick = { reliabilityGuideVisible = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reliability_view_guide)) }
                }
            }
            }
        }
        item {
            CollapsibleSettingsSection(
                title = stringResource(R.string.settings_data),
                expanded = "data" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("data") },
            ) {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(onClick = { exportLauncher.launch(backupFileName) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.export_backup)) }
                    Button(onClick = { importLauncher.launch(arrayOf("application/json", "text/plain")) }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.import_backup)) }
                    feedback?.let { Text(it, color = if (it == backupFailedText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
                }
            }
            TextButton(onClick = { clearDialog = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.clear_all_schedules), color = MaterialTheme.colorScheme.error) }
            }
        }
        item {
            CollapsibleSettingsSection(
                title = stringResource(R.string.settings_about),
                expanded = "about" in expandedSections,
                onToggle = { expandedSections = expandedSections.toggle("about") },
            ) {
            Card { Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) { Text(stringResource(R.string.app_name), style = MaterialTheme.typography.titleMedium); Text(stringResource(R.string.app_subtitle)); Text(stringResource(R.string.about_description), color = MaterialTheme.colorScheme.onSurfaceVariant); Text(stringResource(R.string.version_name, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant) } }
            }
        }
    }

    if (nicknameDialog) {
        AlertDialog(
            onDismissRequest = { nicknameDialog = false },
            title = { Text(stringResource(R.string.change_nickname_title)) },
            text = { OutlinedTextField(nicknameInput, { nicknameInput = it }, label = { Text(stringResource(R.string.nickname_label)) }, supportingText = { Text(stringResource(R.string.nickname_settings_support)) }, singleLine = true) },
            dismissButton = { TextButton(onClick = { nicknameDialog = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = { if (nicknameInput.trim().isNotEmpty() && nicknameInput.trim().codePointCount(0, nicknameInput.trim().length) <= 12) { viewModel.saveNickname(nicknameInput); nicknameDialog = false } }) { Text(stringResource(R.string.confirm)) } },
        )
    }
    if (clearDialog) {
        AlertDialog(onDismissRequest = { clearDialog = false }, title = { Text(stringResource(R.string.clear_all_title)) }, text = { Text(stringResource(R.string.clear_all_message)) }, dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.cancel)) } }, confirmButton = { TextButton(onClick = { viewModel.clearAllSchedules { clearDialog = false } }) { Text(stringResource(R.string.clear_all_confirm), color = MaterialTheme.colorScheme.error) } })
    }
    pendingImport?.let { schedules ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.backup_found, schedules.size)) },
            text = { Text(stringResource(R.string.backup_import_question)) },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.restoreSchedules(schedules, false) { ok -> feedback = if (ok) backupRestoredText else backupFailedText; pendingImport = null } }) { Text(stringResource(R.string.backup_append)) }
                    TextButton(onClick = { replaceDialog = true }) { Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error) }
                }
            },
        )
    }
    if (replaceDialog) {
        AlertDialog(
            onDismissRequest = { replaceDialog = false },
            title = { Text(stringResource(R.string.backup_replace_title)) },
            text = { Text(stringResource(R.string.backup_replace_message)) },
            dismissButton = { TextButton(onClick = { replaceDialog = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = { TextButton(onClick = {
                val schedules = pendingImport.orEmpty()
                viewModel.restoreSchedules(schedules, true) { ok -> feedback = if (ok) backupRestoredText else backupFailedText; pendingImport = null; replaceDialog = false }
            }) { Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error) } },
        )
    }
    if (reliabilityGuideVisible) {
        AlertDialog(
            onDismissRequest = { reliabilityGuideVisible = false },
            title = { Text(stringResource(R.string.reliability_guide_title)) },
            text = { Text(stringResource(R.string.reliability_guide_message, ReminderSettings.vendorName(context), ReminderSettings.vendorGuide(context)) ) },
            confirmButton = { TextButton(onClick = { reliabilityGuideVisible = false }) { Text(stringResource(R.string.confirm)) } },
        )
    }
}

@Composable
private fun ReliabilityRow(label: String, value: String, enabled: Boolean?) {
    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        Text(label, modifier = Modifier.weight(1f), style = MaterialTheme.typography.titleSmall)
        Text(
            value,
            color = when (enabled) {
                true -> MaterialTheme.colorScheme.primary
                false -> MaterialTheme.colorScheme.error
                null -> MaterialTheme.colorScheme.onSurfaceVariant
            },
        )
    }
}

@Composable
private fun CollapsibleSettingsSection(
    title: String,
    expanded: Boolean,
    onToggle: () -> Unit,
    content: @Composable () -> Unit,
) {
    Column(Modifier.fillMaxWidth()) {
        TextButton(onClick = onToggle, modifier = Modifier.fillMaxWidth()) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                Text(title, style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.primary, modifier = Modifier.weight(1f))
                Text(if (expanded) "⌃" else "⌄", style = MaterialTheme.typography.titleMedium)
            }
        }
        if (expanded) content()
    }
}

private fun Set<String>.toggle(value: String): Set<String> =
    if (value in this) this - value else this + value

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        ThemeMode.entries.forEach { mode ->
            FilterChip(selected = mode == selected, onClick = { onSelect(mode) }, label = { Text(themeLabel(mode)) })
        }
    }
}

@Composable
private fun themeLabel(mode: ThemeMode): String = when (mode) {
    ThemeMode.SYSTEM -> stringResource(R.string.theme_system)
    ThemeMode.LIGHT -> stringResource(R.string.theme_light)
    ThemeMode.DARK -> stringResource(R.string.theme_dark)
}

private fun minutesToTime(minutes: Int): String = "%02d:%02d".format(minutes / 60, minutes % 60)
private fun parseTimeMinutes(value: String): Int? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val hour = parts[0].toIntOrNull() ?: return null
    val minute = parts[1].toIntOrNull() ?: return null
    return if (hour in 0..23 && minute in 0..59) hour * 60 + minute else null
}
