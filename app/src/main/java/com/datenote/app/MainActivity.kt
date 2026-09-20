package com.datenote.app

import android.os.Bundle
import android.Manifest
import android.os.Build
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Alignment
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import com.datenote.app.ui.onboarding.OnboardingScreen
import com.datenote.app.ui.navigation.MainShell
import com.datenote.app.ui.startup.AppStartupState
import com.datenote.app.ui.startup.StartupViewModel
import com.datenote.app.ui.startup.StartupReminderState
import com.datenote.app.ui.theme.DateNoteTheme
import com.datenote.app.reminder.NotificationAccess
import com.datenote.app.reminder.ReminderSettings

class MainActivity : ComponentActivity() {
    private val openedScheduleId = MutableStateFlow<Long?>(null)
    private val notificationSettingsPromptVisible = MutableStateFlow(false)
    private val notificationRequestInProgress = MutableStateFlow(false)
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) {
        notificationRequestInProgress.value = false
        lifecycleScope.launch {
            val app = application as DateNoteApplication
            app.userPreferencesRepository.setNotificationPermissionRequested()
            rescheduleReminders()
        }
    }

    override fun onNewIntent(intent: android.content.Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        openedScheduleId.value = intent.getLongExtra(com.datenote.app.reminder.ReminderNotifications.SCHEDULE_ID, 0L).takeIf { it != 0L }
    }
    private val startupViewModel: StartupViewModel by viewModels {
        StartupViewModel.Factory(
            (application as DateNoteApplication).userPreferencesRepository,
            (application as DateNoteApplication).scheduleRepository,
        )
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        openedScheduleId.value = intent.getLongExtra(com.datenote.app.reminder.ReminderNotifications.SCHEDULE_ID, 0L).takeIf { it != 0L }
        enableEdgeToEdge()
        setContent {
            val startupState by startupViewModel.startupState.collectAsStateWithLifecycle()
            val preferences by startupViewModel.preferences.collectAsStateWithLifecycle()
            val showWelcome by startupViewModel.showWelcome.collectAsStateWithLifecycle()
            val reminderState by startupViewModel.reminderState.collectAsStateWithLifecycle()
            val notificationScheduleId by openedScheduleId.collectAsState()
            val notificationGuideVisible = startupState == AppStartupState.Ready && !preferences.notificationGuideCompleted
            val requestInProgress by notificationRequestInProgress.collectAsState()
            val settingsPromptVisible by notificationSettingsPromptVisible.collectAsState()
            DateNoteTheme(
                themeMode = preferences.themeMode,
                dynamicColor = preferences.dynamicColor,
            ) {
                Surface(modifier = Modifier.fillMaxSize()) {
                    StartupContent(
                        state = startupState,
                        preferences = preferences,
                        repository = (application as DateNoteApplication).scheduleRepository,
                        aiRepository = (application as DateNoteApplication).aiRepository,
                        preferencesRepository = (application as DateNoteApplication).userPreferencesRepository,
                        keyStore = (application as DateNoteApplication).secureApiKeyStore,
                        reminderScheduler = (application as DateNoteApplication).reminderScheduler,
                        initialScheduleId = notificationScheduleId,
                        showWelcome = showWelcome,
                        reminderState = reminderState,
                        onLoadReminder = startupViewModel::loadStartupReminder,
                        onDismissReminder = startupViewModel::dismissStartupReminder,
                        onNicknameSaved = startupViewModel::saveNickname,
                        notificationGuideVisible = notificationGuideVisible,
                        notificationRequestInProgress = requestInProgress,
                        onEnableNotifications = {
                            startupViewModel.completeNotificationGuide()
                            requestNotifications(waitForStartup = true)
                        },
                        onSkipNotificationGuide = {
                            startupViewModel.completeNotificationGuide()
                            notificationRequestInProgress.value = false
                        },
                        onRequestNotifications = { requestNotifications() },
                    )
                    if (settingsPromptVisible) {
                        NotificationSettingsPrompt(
                            onOpenSettings = {
                                notificationSettingsPromptVisible.value = false
                                notificationRequestInProgress.value = false
                                ReminderSettings.openNotificationSettings(this@MainActivity)
                            },
                            onDismiss = {
                                notificationSettingsPromptVisible.value = false
                                notificationRequestInProgress.value = false
                            },
                        )
                    }
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        rescheduleReminders()
    }

    private fun requestNotifications(waitForStartup: Boolean = false) {
        if (waitForStartup) notificationRequestInProgress.value = true
        val status = NotificationAccess.status(this)
        if (status.canPost) {
            notificationRequestInProgress.value = false
            return
        }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && !status.permissionGranted) {
            val requested = startupViewModel.preferences.value.notificationPermissionRequested
            if (!requested || shouldShowRequestPermissionRationale(Manifest.permission.POST_NOTIFICATIONS)) {
                lifecycleScope.launch {
                    (application as DateNoteApplication).userPreferencesRepository.setNotificationPermissionRequested()
                }
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                return
            }
        }
        notificationSettingsPromptVisible.value = true
    }

    private fun rescheduleReminders() {
        val app = application as DateNoteApplication
        lifecycleScope.launch {
            val preferences = app.userPreferencesRepository.preferences.first()
            val schedules = app.scheduleRepository.observeAll().first()
            app.reminderScheduler.rescheduleAll(schedules, preferences.defaultReminderTimeMinutes)
        }
    }
}

@Composable
private fun StartupContent(
    state: AppStartupState,
    preferences: com.datenote.app.data.repository.UserPreferences,
    repository: com.datenote.app.data.repository.ScheduleRepository,
    aiRepository: com.datenote.app.data.remote.AiRepository,
    preferencesRepository: com.datenote.app.data.repository.UserPreferencesRepository,
    keyStore: com.datenote.app.data.secure.SecureApiKeyStore,
    reminderScheduler: com.datenote.app.reminder.ReminderScheduler,
    initialScheduleId: Long?,
    showWelcome: Boolean,
    reminderState: StartupReminderState,
    onLoadReminder: () -> Unit,
    onDismissReminder: () -> Unit,
    onNicknameSaved: (String) -> Unit,
    notificationGuideVisible: Boolean,
    notificationRequestInProgress: Boolean,
    onEnableNotifications: () -> Unit,
    onSkipNotificationGuide: () -> Unit,
    onRequestNotifications: () -> Unit,
) {
    LaunchedEffect(state, notificationGuideVisible, notificationRequestInProgress) {
        if (state == AppStartupState.Ready && !notificationGuideVisible && !notificationRequestInProgress) onLoadReminder()
    }
    when (state) {
        AppStartupState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        AppStartupState.NeedsOnboarding -> OnboardingScreen(
            initialNickname = if (BuildConfig.WEII_PRECONFIGURED) BuildConfig.WEII_NICKNAME else "",
            onNicknameSaved = onNicknameSaved,
        )
        AppStartupState.Ready -> {
            MainShell(
                nickname = preferences.nickname,
                preferences = preferences,
                repository = repository,
                aiRepository = aiRepository,
                preferencesRepository = preferencesRepository,
                keyStore = keyStore,
                reminderScheduler = reminderScheduler,
                initialScheduleId = initialScheduleId,
                showWelcome = showWelcome,
                onRequestNotifications = onRequestNotifications,
            )
            if (notificationGuideVisible) {
                NotificationPermissionGuide(onEnable = onEnableNotifications, onSkip = onSkipNotificationGuide)
            }
            if (reminderState is StartupReminderState.Show) {
                StartupReminderDialog(reminderState.schedules, preferences.nickname, onDismissReminder)
            }
        }
    }
}

@Composable
private fun NotificationPermissionGuide(
    onEnable: () -> Unit,
    onSkip: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onSkip,
        title = { Text(stringResource(com.datenote.app.R.string.notification_guide_title)) },
        text = { Text(stringResource(com.datenote.app.R.string.notification_guide_message)) },
        dismissButton = { TextButton(onClick = onSkip) { Text(stringResource(com.datenote.app.R.string.notification_guide_skip)) } },
        confirmButton = { TextButton(onClick = onEnable) { Text(stringResource(com.datenote.app.R.string.notification_guide_enable)) } },
    )
}

@Composable
private fun NotificationSettingsPrompt(
    onOpenSettings: () -> Unit,
    onDismiss: () -> Unit,
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(com.datenote.app.R.string.notification_disabled_title)) },
        text = { Text(stringResource(com.datenote.app.R.string.notification_disabled_message)) },
        dismissButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.datenote.app.R.string.later)) } },
        confirmButton = { TextButton(onClick = onOpenSettings) { Text(stringResource(com.datenote.app.R.string.notification_open_settings)) } },
    )
}

@Composable
private fun StartupReminderDialog(
    schedules: List<com.datenote.app.data.local.ScheduleWithSteps>,
    nickname: String,
    onDismiss: () -> Unit,
) {
    val today = java.time.LocalDate.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(com.datenote.app.R.string.startup_reminder_title, nickname)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                items(schedules, key = { it.schedule.id }) { schedule ->
                    val entity = schedule.schedule
                    val days = entity.endEpochDay - today.toEpochDay()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text(entity.title, maxLines = 2)
                            val start = java.time.LocalDate.ofEpochDay(entity.startEpochDay)
                            val end = java.time.LocalDate.ofEpochDay(entity.endEpochDay)
                            Text(if (start == end) "${start.monthValue}月${start.dayOfMonth}日" else "${start.monthValue}月${start.dayOfMonth}日～${end.monthValue}月${end.dayOfMonth}日", color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(startupRemainingText(entity, today), color = when {
                                days < 0 -> MaterialTheme.colorScheme.error
                                days <= 3L -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            }, style = MaterialTheme.typography.labelLarge)
                            if (schedule.orderedSteps.isNotEmpty()) {
                                Text("制作进度 ${schedule.orderedSteps.count { it.isCompleted }}/${schedule.orderedSteps.size}", style = MaterialTheme.typography.labelSmall)
                                schedule.orderedSteps.firstOrNull { !it.isCompleted }?.let { next -> Text("下一步：${next.title}", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelSmall) }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text(stringResource(com.datenote.app.R.string.startup_reminder_acknowledge)) } },
    )
}

@Composable
private fun remainingText(days: Long): String = when {
    days > 1L -> stringResource(com.datenote.app.R.string.days_remaining, days)
    days == 1L -> stringResource(com.datenote.app.R.string.tomorrow)
    days == 0L -> stringResource(com.datenote.app.R.string.today_due)
    else -> stringResource(com.datenote.app.R.string.days_overdue, -days)
}

private fun startupRemainingText(schedule: com.datenote.app.data.local.ScheduleEntity, today: java.time.LocalDate): String {
    val todayEpoch = today.toEpochDay()
    return when {
        todayEpoch < schedule.startEpochDay -> "还有 ${schedule.startEpochDay - todayEpoch} 天开始"
        todayEpoch > schedule.endEpochDay -> "已逾期 ${todayEpoch - schedule.endEpochDay} 天"
        todayEpoch == schedule.endEpochDay -> "今天截止"
        else -> "进行中 · 还剩 ${schedule.endEpochDay - todayEpoch} 天"
    }
}
