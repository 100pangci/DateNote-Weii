@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)

package com.datenote.app.ui.settings

import android.content.Context
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BatteryChargingFull
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.datenote.app.BuildConfig
import com.datenote.app.R
import com.datenote.app.data.backup.BackupDocument
import com.datenote.app.data.backup.BackupData
import com.datenote.app.data.backup.toBackup
import com.datenote.app.data.backup.toBackupData
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.ThemeMode
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.data.secure.SecureApiKeyStore
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.reminder.ReminderSettings
import com.datenote.app.ui.components.AppOutlinedTextField
import com.datenote.app.ui.components.AppPrimaryButton
import com.datenote.app.ui.components.AppSectionTitle
import com.datenote.app.ui.components.AppTopAppBar
import com.datenote.app.ui.theme.AppSpacing
import java.io.IOException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json

@Composable
private fun SettingsDetailScaffold(
    title: String,
    onBack: () -> Unit,
    content: @Composable (PaddingValues) -> Unit,
) {
    Scaffold(
        topBar = { AppTopAppBar(title = title, onBack = onBack) },
        content = content,
    )
}

@Composable
fun SettingsPersonalizationScreen(
    preferencesRepository: UserPreferencesRepository,
    keyStore: SecureApiKeyStore,
    aiRepository: AiRepository,
    scheduleRepository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(preferencesRepository, keyStore, aiRepository, scheduleRepository, reminderScheduler))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var nicknameDialog by remember { mutableStateOf(false) }
    var nicknameInput by remember(state.preferences.nickname) { mutableStateOf(state.preferences.nickname) }
    SettingsDetailScaffold(stringResource(R.string.settings_personalization), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.Tight),
        ) {
            item {
                ListItem(
                    modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable { nicknameDialog = true },
                    headlineContent = { Text(stringResource(R.string.my_nickname), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.nickname_value, state.preferences.nickname), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    leadingContent = { Icon(Icons.Default.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                )
            }
            item { Text(stringResource(R.string.privacy_nickname), Modifier.padding(horizontal = AppSpacing.Content, vertical = AppSpacing.Tight), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) }
            item { AppSectionTitle(stringResource(R.string.theme_mode), Modifier.padding(start = AppSpacing.Content, top = AppSpacing.Compact, bottom = AppSpacing.Tight)) }
            item { ThemePicker(state.preferences.themeMode, viewModel::setThemeMode) }
            item {
                ListItem(
                    modifier = Modifier.clickable { viewModel.setDynamicColor(!state.preferences.dynamicColor) },
                    headlineContent = { Text(stringResource(R.string.dynamic_color), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.dynamic_color_support), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = { Switch(checked = state.preferences.dynamicColor, onCheckedChange = viewModel::setDynamicColor) },
                )
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) }
            item {
                Text(
                    stringResource(R.string.settings_schedule_cards),
                    Modifier.padding(start = AppSpacing.Content, top = AppSpacing.Compact, bottom = AppSpacing.Tight),
                )
            }
            item {
                ListItem(
                    modifier = Modifier.clickable { viewModel.setDefaultExpandSteps(!state.preferences.defaultExpandSteps) },
                    headlineContent = { Text(stringResource(R.string.default_expand_steps), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.default_expand_steps_support), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                    trailingContent = {
                        Switch(
                            checked = state.preferences.defaultExpandSteps,
                            onCheckedChange = viewModel::setDefaultExpandSteps,
                        )
                    },
                )
            }
        }
    }
    if (nicknameDialog) {
        AlertDialog(
            onDismissRequest = { nicknameDialog = false },
            title = { Text(stringResource(R.string.change_nickname_title)) },
            text = {
                AppOutlinedTextField(
                    value = nicknameInput,
                    onValueChange = { nicknameInput = it },
                    label = { Text(stringResource(R.string.nickname_label)) },
                    supportingText = { Text(stringResource(R.string.nickname_settings_support)) },
                    singleLine = true,
                )
            },
            dismissButton = { TextButton(onClick = { nicknameDialog = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = {
                    val value = nicknameInput.trim()
                    if (value.isNotEmpty() && value.codePointCount(0, value.length) <= 12) {
                        viewModel.saveNickname(value)
                        nicknameDialog = false
                    }
                }) { Text(stringResource(R.string.confirm)) }
            },
        )
    }
}

@Composable
fun SettingsAiScreen(
    preferencesRepository: UserPreferencesRepository,
    keyStore: SecureApiKeyStore,
    aiRepository: AiRepository,
    scheduleRepository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(preferencesRepository, keyStore, aiRepository, scheduleRepository, reminderScheduler))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var baseUrl by remember(state.preferences.aiBaseUrl) { mutableStateOf(state.preferences.aiBaseUrl) }
    var model by remember(state.preferences.aiModel) { mutableStateOf(state.preferences.aiModel) }
    var apiKey by remember(state.apiKey) { mutableStateOf(state.apiKey) }
    var saved by rememberSaveable { mutableStateOf(false) }
    SettingsDetailScaffold(stringResource(R.string.settings_ai), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.ScreenTop),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Compact),
        ) {
            item {
                AppOutlinedTextField(baseUrl, { baseUrl = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.base_url)) }, supportingText = { Text(stringResource(R.string.base_url_support)) }, singleLine = true)
            }
            item {
                AppOutlinedTextField(apiKey, { apiKey = it; viewModel.setApiKey(it) }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.api_key)) }, supportingText = { Text(stringResource(R.string.api_key_support)) }, visualTransformation = PasswordVisualTransformation(), singleLine = true)
            }
            item { AppOutlinedTextField(model, { model = it }, Modifier.fillMaxWidth(), label = { Text(stringResource(R.string.model_name)) }, singleLine = true) }
            item {
                AppPrimaryButton(onClick = { viewModel.saveAiSettings(baseUrl, model); saved = true }, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.save_ai_settings))
                }
            }
            item {
                OutlinedButton(onClick = { viewModel.testConnection(baseUrl, model, apiKey) }, enabled = state.connectionState != ConnectionState.TESTING, modifier = Modifier.fillMaxWidth().heightIn(min = 48.dp)) {
                    Text(if (state.connectionState == ConnectionState.TESTING) stringResource(R.string.connection_testing) else stringResource(R.string.test_ai_connection))
                }
            }
            if (saved) item { Text(stringResource(R.string.settings_saved), color = MaterialTheme.colorScheme.primary) }
            when (state.connectionState) {
                ConnectionState.SUCCESS -> item { Text(stringResource(R.string.connection_success), color = MaterialTheme.colorScheme.primary) }
                ConnectionState.FAILED -> item { Text(stringResource(R.string.connection_failed), color = MaterialTheme.colorScheme.error) }
                else -> Unit
            }
        }
    }
}

@Composable
fun SettingsRemindersScreen(
    preferencesRepository: UserPreferencesRepository,
    keyStore: SecureApiKeyStore,
    aiRepository: AiRepository,
    scheduleRepository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(preferencesRepository, keyStore, aiRepository, scheduleRepository, reminderScheduler))
    val state by viewModel.state.collectAsStateWithLifecycle()
    var timeText by remember(state.preferences.defaultReminderTimeMinutes) { mutableStateOf(minutesToTime(state.preferences.defaultReminderTimeMinutes)) }
    SettingsDetailScaffold(stringResource(R.string.settings_reminders), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.ScreenTop),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Compact),
        ) {
            item { AppSectionTitle(stringResource(R.string.default_reminder)) }
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    val choices = listOf(1440 to R.string.reminder_one_day, 4320 to R.string.reminder_three_days, 10080 to R.string.reminder_one_week)
                    choices.forEachIndexed { index, (minutes, label) ->
                        SegmentedButton(
                            selected = state.preferences.defaultReminderMinutes == minutes,
                            onClick = { viewModel.setDefaultReminder(minutes) },
                            shape = SegmentedButtonDefaults.itemShape(index = index, count = choices.size),
                            label = { Text(stringResource(label)) },
                        )
                    }
                }
            }
            item {
                AppOutlinedTextField(
                    value = timeText,
                    onValueChange = { value ->
                        timeText = value
                        parseTimeMinutes(value)?.let(viewModel::setDefaultReminderTime)
                    },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text(stringResource(R.string.default_reminder_time)) },
                    supportingText = { Text(stringResource(R.string.time_format_hint)) },
                    singleLine = true,
                )
            }
        }
    }
}

@Composable
fun SettingsReliabilityScreen(
    preferencesRepository: UserPreferencesRepository,
    keyStore: SecureApiKeyStore,
    aiRepository: AiRepository,
    scheduleRepository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    onRequestNotifications: () -> Unit,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(preferencesRepository, keyStore, aiRepository, scheduleRepository, reminderScheduler))
    val state by viewModel.state.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val lifecycleOwner = LocalLifecycleOwner.current
    var guideVisible by rememberSaveable { mutableStateOf(false) }
    LaunchedEffect(Unit) { viewModel.refreshReliability(context) }
    DisposableEffect(lifecycleOwner, context) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshReliability(context)
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }
    SettingsDetailScaffold(stringResource(R.string.settings_reminder_reliability), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.Tight),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Hairline),
        ) {
            val reliability = state.reliability
            item {
                ReliabilityListItem(
                    icon = Icons.Default.Notifications,
                    title = stringResource(R.string.reliability_notification_permission),
                    value = if (reliability?.notificationStatus?.permissionGranted == true && reliability.notificationStatus.appNotificationsEnabled) stringResource(R.string.reliability_enabled) else stringResource(R.string.reliability_not_enabled),
                    enabled = reliability?.notificationStatus?.permissionGranted == true && reliability.notificationStatus.appNotificationsEnabled,
                    onClick = onRequestNotifications,
                )
            }
            item {
                ReliabilityListItem(
                    icon = Icons.Default.NotificationsActive,
                    title = stringResource(R.string.reliability_channel),
                    value = if (reliability?.notificationStatus?.channelEnabled == true) stringResource(R.string.reliability_available) else stringResource(R.string.reliability_closed),
                    enabled = reliability?.notificationStatus?.channelEnabled == true,
                    onClick = { ReminderSettings.openNotificationSettings(context) },
                )
            }
            item {
                ReliabilityListItem(
                    icon = Icons.Default.BatteryChargingFull,
                    title = stringResource(R.string.reliability_battery),
                    value = if (reliability?.batteryOptimizationIgnored == true) stringResource(R.string.reliability_battery_ok) else stringResource(R.string.reliability_battery_limited),
                    enabled = reliability?.batteryOptimizationIgnored == true,
                    onClick = { ReminderSettings.openBatterySettings(context) },
                )
            }
            item {
                ReliabilityListItem(
                    icon = Icons.Default.PowerSettingsNew,
                    title = stringResource(R.string.reliability_autostart),
                    value = stringResource(R.string.reliability_manual),
                    enabled = null,
                    onClick = { ReminderSettings.openAppDetails(context) },
                )
            }
            item { Text(stringResource(R.string.reliability_background_hint), Modifier.padding(AppSpacing.Content), color = MaterialTheme.colorScheme.onSurfaceVariant, style = MaterialTheme.typography.bodySmall) }
            item { TextButton(onClick = { guideVisible = true }, modifier = Modifier.fillMaxWidth()) { Text(stringResource(R.string.reliability_view_guide)) } }
        }
    }
    if (guideVisible) {
        AlertDialog(
            onDismissRequest = { guideVisible = false },
            title = { Text(stringResource(R.string.reliability_guide_title)) },
            text = { Text(stringResource(R.string.reliability_guide_message, ReminderSettings.vendorName(context), ReminderSettings.vendorGuide(context))) },
            confirmButton = { TextButton(onClick = { guideVisible = false }) { Text(stringResource(R.string.confirm)) } },
        )
    }
}

@Composable
private fun ReliabilityListItem(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    title: String,
    value: String,
    enabled: Boolean?,
    onClick: () -> Unit,
) {
    ListItem(
        modifier = Modifier.fillMaxWidth().heightIn(min = 64.dp).clickable(onClick = onClick),
        headlineContent = { Text(title, style = MaterialTheme.typography.titleMedium) },
        supportingContent = {
            Text(
                value,
                style = MaterialTheme.typography.bodySmall,
                color = when (enabled) {
                    true -> MaterialTheme.colorScheme.primary
                    false -> MaterialTheme.colorScheme.error
                    null -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        },
        leadingContent = { Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
    )
}

@Composable
fun SettingsDataScreen(
    preferencesRepository: UserPreferencesRepository,
    keyStore: SecureApiKeyStore,
    aiRepository: AiRepository,
    scheduleRepository: ScheduleRepository,
    reminderScheduler: ReminderScheduler,
    onBack: () -> Unit,
) {
    val viewModel: SettingsViewModel = viewModel(factory = SettingsViewModel.Factory(preferencesRepository, keyStore, aiRepository, scheduleRepository, reminderScheduler))
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var feedback by remember { mutableStateOf<String?>(null) }
    var pendingImport by remember { mutableStateOf<BackupData?>(null) }
    var replaceDialog by remember { mutableStateOf(false) }
    var clearDialog by remember { mutableStateOf(false) }
    val backupJson = remember { Json { prettyPrint = true; ignoreUnknownKeys = true } }
    val backupFailedText = stringResource(R.string.backup_failed)
    val backupSavedText = stringResource(R.string.backup_saved)
    val backupRestoredText = stringResource(R.string.backup_restored)
    val backupFileName = stringResource(R.string.backup_file_name)
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) scope.launch {
            val success = runCatching {
                val document = withContext(Dispatchers.IO) {
                    val schedules = scheduleRepository.observeAllWithSteps().first()
                    val types = scheduleRepository.observeScheduleTypes().first()
                    BackupDocument(
                        exportedAt = System.currentTimeMillis(),
                        schedules = schedules.map { it.toBackup() },
                        types = types.map { it.toBackup() },
                    )
                }
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { it.write(backupJson.encodeToString(document).toByteArray()) } ?: throw IOException("write")
                }
            }.isSuccess
            feedback = if (success) backupSavedText else backupFailedText
        }
    }
    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val imported = withContext(Dispatchers.IO) {
                runCatching {
                    val text = context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } ?: throw IOException("read")
                    backupJson.decodeFromString<BackupDocument>(text).let { document ->
                        require(document.schemaVersion in 1..3)
                        document.toBackupData()
                    }
                }.getOrNull()
            }
            if (imported == null) feedback = backupFailedText else pendingImport = imported
        }
    }

    SettingsDetailScaffold(stringResource(R.string.settings_data), onBack) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(horizontal = AppSpacing.ScreenHorizontal, vertical = AppSpacing.ScreenTop),
            verticalArrangement = Arrangement.spacedBy(AppSpacing.Hairline),
        ) {
            item {
                ListItem(
                    modifier = Modifier.clickable { exportLauncher.launch(backupFileName) },
                    leadingContent = { Icon(Icons.Default.FileDownload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.export_backup), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.export_backup_support), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            item { HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) }
            item {
                ListItem(
                    modifier = Modifier.clickable { importLauncher.launch(arrayOf("application/json", "text/plain")) },
                    leadingContent = { Icon(Icons.Default.FileUpload, contentDescription = null, tint = MaterialTheme.colorScheme.primary) },
                    headlineContent = { Text(stringResource(R.string.import_backup), style = MaterialTheme.typography.titleMedium) },
                    supportingContent = { Text(stringResource(R.string.import_backup_support), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
            feedback?.let { message ->
                item { Text(message, Modifier.padding(16.dp), color = if (message == backupFailedText) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary) }
            }
            item { HorizontalDivider(Modifier.padding(vertical = AppSpacing.Compact), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.45f)) }
            item {
                ListItem(
                    modifier = Modifier.clickable { clearDialog = true },
                    leadingContent = { Icon(Icons.Default.DeleteForever, contentDescription = null, tint = MaterialTheme.colorScheme.error) },
                    headlineContent = { Text(stringResource(R.string.clear_all_schedules), color = MaterialTheme.colorScheme.error) },
                    supportingContent = { Text(stringResource(R.string.clear_all_support), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }

    if (clearDialog) {
        AlertDialog(
            onDismissRequest = { clearDialog = false },
            title = { Text(stringResource(R.string.clear_all_title)) },
            text = { Text(stringResource(R.string.clear_all_message)) },
            dismissButton = { TextButton(onClick = { clearDialog = false }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                TextButton(onClick = { viewModel.clearAllSchedules { clearDialog = false } }) {
                    Text(stringResource(R.string.clear_all_confirm), color = MaterialTheme.colorScheme.error)
                }
            },
        )
    }
    pendingImport?.let { backup ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text(stringResource(R.string.backup_found, backup.schedules.size)) },
            text = { Text(stringResource(R.string.backup_import_question)) },
            dismissButton = { TextButton(onClick = { pendingImport = null }) { Text(stringResource(R.string.cancel)) } },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                    TextButton(onClick = { viewModel.restoreBackup(backup.schedules, backup.types, false) { ok -> feedback = if (ok) backupRestoredText else backupFailedText; pendingImport = null } }) { Text(stringResource(R.string.backup_append)) }
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
            confirmButton = {
                TextButton(onClick = {
                    val import = pendingImport
                    if (import != null) viewModel.restoreBackup(import.schedules, import.types, true) { ok -> feedback = if (ok) backupRestoredText else backupFailedText; pendingImport = null; replaceDialog = false }
                }) { Text(stringResource(R.string.backup_replace), color = MaterialTheme.colorScheme.error) }
            },
        )
    }
}

@Composable
fun SettingsAboutScreen(onBack: () -> Unit) {
    SettingsDetailScaffold(stringResource(R.string.settings_about), onBack) { padding ->
        Column(
            modifier = Modifier.fillMaxSize().padding(padding).padding(horizontal = 36.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
        ) {
            Image(
                painter = androidx.compose.ui.res.painterResource(R.drawable.ic_launcher),
                contentDescription = stringResource(R.string.app_name),
                modifier = Modifier.size(160.dp),
            )
            Spacer(Modifier.height(20.dp))
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineSmall)
            Spacer(Modifier.height(4.dp))
            Text(stringResource(R.string.app_subtitle), style = MaterialTheme.typography.titleMedium)
            Spacer(Modifier.height(12.dp))
            Text(
                stringResource(R.string.about_description),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(stringResource(R.string.version_name, BuildConfig.VERSION_NAME), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun ThemePicker(selected: ThemeMode, onSelect: (ThemeMode) -> Unit) {
    SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
        ThemeMode.entries.forEachIndexed { index, mode ->
            SegmentedButton(
                selected = mode == selected,
                onClick = { onSelect(mode) },
                shape = SegmentedButtonDefaults.itemShape(index = index, count = ThemeMode.entries.size),
                label = { Text(themeLabel(mode)) },
            )
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
