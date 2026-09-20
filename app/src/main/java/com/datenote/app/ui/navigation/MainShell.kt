package com.datenote.app.ui.navigation

import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.datenote.app.R
import com.datenote.app.data.repository.ScheduleRepository
import com.datenote.app.data.repository.UserPreferences
import com.datenote.app.data.remote.AiRepository
import com.datenote.app.data.repository.UserPreferencesRepository
import com.datenote.app.data.secure.SecureApiKeyStore
import com.datenote.app.reminder.ReminderScheduler
import com.datenote.app.ui.aiinput.AiInputScreen
import com.datenote.app.ui.all.AllSchedulesScreen
import com.datenote.app.ui.editor.ScheduleEditorScreen
import com.datenote.app.ui.home.HomeScreen
import com.datenote.app.ui.settings.SettingsScreen

private const val HomeRoute = "home"
private const val AllRoute = "all"
private const val AiRoute = "ai"
private const val SettingsRoute = "settings"
private const val AddRoute = "schedule/new"
private const val EditRoute = "schedule/{id}"

@Composable
fun MainShell(
    nickname: String,
    preferences: UserPreferences,
    repository: ScheduleRepository,
    aiRepository: AiRepository,
    preferencesRepository: UserPreferencesRepository,
    keyStore: SecureApiKeyStore,
    reminderScheduler: ReminderScheduler,
    initialScheduleId: Long?,
    showWelcome: Boolean,
    onRequestNotifications: () -> Unit,
) {
    val navController = rememberNavController()
    val entry = navController.currentBackStackEntryAsState().value
    val current = entry?.destination?.route
    LaunchedEffect(initialScheduleId) {
        if (initialScheduleId != null) navController.navigate("schedule/$initialScheduleId")
    }
    val rootRoutes = setOf(HomeRoute, AllRoute, AiRoute, SettingsRoute)
    val items = listOf(
        Triple(HomeRoute, R.string.home, Icons.Default.Home),
        Triple(AllRoute, R.string.all_schedules, Icons.Default.CalendarMonth),
        Triple(AiRoute, R.string.ai_input_short, Icons.Default.EditNote),
        Triple(SettingsRoute, R.string.settings, Icons.Default.Settings),
    )
    Scaffold(
        bottomBar = {
            if (current in rootRoutes) NavigationBar {
                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = current == route,
                        onClick = { navController.navigate(route) { launchSingleTop = true; restoreState = true } },
                        icon = { Icon(icon, contentDescription = stringResource(label)) },
                        label = { Text(stringResource(label)) },
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable(HomeRoute) {
                HomeScreen(
                    nickname = nickname,
                    repository = repository,
                    reminderScheduler = reminderScheduler,
                    defaultReminderTimeMinutes = preferences.defaultReminderTimeMinutes,
                    showWelcome = showWelcome,
                    onAdd = { navController.navigate(AddRoute) },
                    onEdit = { navController.navigate("schedule/$it") },
                )
            }
            composable(AllRoute) { AllSchedulesScreen(repository = repository, reminderScheduler = reminderScheduler, defaultReminderTimeMinutes = preferences.defaultReminderTimeMinutes, onEdit = { navController.navigate("schedule/$it") }) }
            composable(AiRoute) { AiInputScreen(aiRepository = aiRepository, repository = repository, defaultReminderMinutes = preferences.defaultReminderMinutes, reminderScheduler = reminderScheduler, defaultReminderTimeMinutes = preferences.defaultReminderTimeMinutes, onRequestNotifications = onRequestNotifications) }
            composable(SettingsRoute) {
                SettingsScreen(
                    preferencesRepository = preferencesRepository,
                    keyStore = keyStore,
                    aiRepository = aiRepository,
                    scheduleRepository = repository,
                    reminderScheduler = reminderScheduler,
                    onRequestNotifications = onRequestNotifications,
                )
            }
            composable(AddRoute) {
                ScheduleEditorScreen(
                    scheduleId = null,
                    repository = repository,
                    defaultReminderMinutes = preferences.defaultReminderMinutes,
                    reminderScheduler = reminderScheduler,
                    defaultReminderTimeMinutes = preferences.defaultReminderTimeMinutes,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onRequestNotifications = onRequestNotifications,
                )
            }
            composable(EditRoute, arguments = listOf(navArgument("id") { type = NavType.LongType })) { backStackEntry ->
                ScheduleEditorScreen(
                    scheduleId = backStackEntry.arguments?.getLong("id"),
                    repository = repository,
                    defaultReminderMinutes = preferences.defaultReminderMinutes,
                    reminderScheduler = reminderScheduler,
                    defaultReminderTimeMinutes = preferences.defaultReminderTimeMinutes,
                    onBack = { navController.popBackStack() },
                    onSaved = { navController.popBackStack() },
                    onRequestNotifications = onRequestNotifications,
                )
            }
        }
    }
}
