package com.datenote.app

import android.os.Bundle
import android.Manifest
import android.content.pm.PackageManager
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
import kotlinx.coroutines.flow.MutableStateFlow
import com.datenote.app.ui.onboarding.OnboardingScreen
import com.datenote.app.ui.navigation.MainShell
import com.datenote.app.ui.startup.AppStartupState
import com.datenote.app.ui.startup.StartupViewModel
import com.datenote.app.ui.startup.StartupReminderState
import com.datenote.app.ui.theme.DateNoteTheme

class MainActivity : ComponentActivity() {
    private val openedScheduleId = MutableStateFlow<Long?>(null)
    private val notificationPermissionLauncher = registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

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
            LaunchedEffect(startupState) {
                if (startupState == AppStartupState.Ready && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
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
                    )
                }
            }
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
) {
    LaunchedEffect(state) {
        if (state == AppStartupState.Ready) onLoadReminder()
    }
    when (state) {
        AppStartupState.Loading -> Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) { CircularProgressIndicator() }

        AppStartupState.NeedsOnboarding -> OnboardingScreen(onNicknameSaved = onNicknameSaved)
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
            )
            if (reminderState is StartupReminderState.Show) {
                StartupReminderDialog(reminderState.schedules, preferences.nickname, onDismissReminder)
            }
        }
    }
}

@Composable
private fun StartupReminderDialog(
    schedules: List<com.datenote.app.data.local.ScheduleEntity>,
    nickname: String,
    onDismiss: () -> Unit,
) {
    val today = java.time.LocalDate.now()
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(com.datenote.app.R.string.startup_reminder_title, nickname)) },
        text = {
            LazyColumn(modifier = Modifier.heightIn(max = 320.dp), verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(8.dp)) {
                items(schedules, key = { it.id }) { schedule ->
                    val days = schedule.scheduledEpochDay - today.toEpochDay()
                    Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
                        Text(schedule.title, modifier = Modifier.weight(1f), maxLines = 2)
                        Text(
                            remainingText(days),
                            color = when {
                                days < 0 -> MaterialTheme.colorScheme.error
                                days <= 3L -> MaterialTheme.colorScheme.primary
                                else -> MaterialTheme.colorScheme.onSurfaceVariant
                            },
                            style = MaterialTheme.typography.labelLarge,
                        )
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
