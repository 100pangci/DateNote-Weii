package com.datenote.app.ui.navigation

import androidx.compose.animation.AnimatedContentTransitionScope
import androidx.compose.animation.EnterTransition
import androidx.compose.animation.ExitTransition
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationBarDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.animation.core.tween
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraph.Companion.findStartDestination
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
import com.datenote.app.ui.settings.SettingsAboutScreen
import com.datenote.app.ui.settings.SettingsAiScreen
import com.datenote.app.ui.settings.SettingsDataScreen
import com.datenote.app.ui.settings.SettingsDestination
import com.datenote.app.ui.settings.SettingsPersonalizationScreen
import com.datenote.app.ui.settings.SettingsRemindersScreen
import com.datenote.app.ui.settings.SettingsReliabilityScreen
import com.datenote.app.ui.settings.ScheduleTypeEditorScreen
import com.datenote.app.ui.settings.ScheduleTypesScreen

private const val HomeRoute = "home"
private const val AllRoute = "all"
private const val AiRoute = "ai"
private const val SettingsRoute = "settings"
private const val AddRoute = "schedule/new"
private const val EditRoute = "schedule/{id}"

private const val MotionDuration = 240

private val topLevelEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    fadeIn(tween(MotionDuration)) + scaleIn(tween(MotionDuration), initialScale = 0.98f)
}
private val topLevelExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    fadeOut(tween(MotionDuration))
}
private val detailEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(MotionDuration)) + fadeIn(tween(MotionDuration))
}
private val detailExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Left, tween(MotionDuration)) + fadeOut(tween(MotionDuration))
}
private val detailPopEnter: AnimatedContentTransitionScope<NavBackStackEntry>.() -> EnterTransition = {
    slideIntoContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(MotionDuration)) + fadeIn(tween(MotionDuration))
}
private val detailPopExit: AnimatedContentTransitionScope<NavBackStackEntry>.() -> ExitTransition = {
    slideOutOfContainer(AnimatedContentTransitionScope.SlideDirection.Right, tween(MotionDuration)) + fadeOut(tween(MotionDuration))
}

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
            if (current in rootRoutes) NavigationBar(
                containerColor = MaterialTheme.colorScheme.surfaceContainer,
                tonalElevation = 0.dp,
                windowInsets = NavigationBarDefaults.windowInsets,
            ) {
                items.forEach { (route, label, icon) ->
                    NavigationBarItem(
                        selected = current == route,
                        onClick = {
                            navController.navigate(route) {
                                popUpTo(navController.graph.findStartDestination().id) {
                                    saveState = true
                                }
                                launchSingleTop = true
                                restoreState = true
                            }
                        },
                        icon = { Icon(icon, contentDescription = stringResource(label)) },
                        label = { Text(stringResource(label), style = MaterialTheme.typography.labelSmall) },
                        colors = NavigationBarItemDefaults.colors(
                            selectedIconColor = MaterialTheme.colorScheme.onPrimaryContainer,
                            selectedTextColor = MaterialTheme.colorScheme.primary,
                            indicatorColor = MaterialTheme.colorScheme.primaryContainer,
                            unselectedIconColor = MaterialTheme.colorScheme.onSurfaceVariant,
                            unselectedTextColor = MaterialTheme.colorScheme.onSurfaceVariant,
                        ),
                    )
                }
            }
        },
    ) { padding ->
        NavHost(navController, startDestination = HomeRoute, modifier = Modifier.padding(padding)) {
            composable(HomeRoute, enterTransition = topLevelEnter, exitTransition = topLevelExit) {
                HomeScreen(
                    nickname = nickname,
                    repository = repository,
                    reminderScheduler = reminderScheduler,
                    defaultReminderTimeMinutes = preferences.defaultReminderTimeMinutes,
                    defaultExpandSteps = preferences.defaultExpandSteps,
                    showWelcome = showWelcome,
                    onAdd = { navController.navigate(AddRoute) },
                    onEdit = { navController.navigate("schedule/$it") },
                )
            }
            composable(AllRoute, enterTransition = topLevelEnter, exitTransition = topLevelExit) { AllSchedulesScreen(repository = repository, reminderScheduler = reminderScheduler, defaultReminderTimeMinutes = preferences.defaultReminderTimeMinutes, defaultExpandSteps = preferences.defaultExpandSteps, onEdit = { navController.navigate("schedule/$it") }) }
            composable(AiRoute, enterTransition = topLevelEnter, exitTransition = topLevelExit) { AiInputScreen(aiRepository = aiRepository, repository = repository, defaultReminderMinutes = preferences.defaultReminderMinutes, reminderScheduler = reminderScheduler, defaultReminderTimeMinutes = preferences.defaultReminderTimeMinutes, onRequestNotifications = onRequestNotifications) }
            composable(SettingsRoute, enterTransition = topLevelEnter, exitTransition = topLevelExit) {
                SettingsScreen(
                    preferencesRepository = preferencesRepository,
                    keyStore = keyStore,
                    aiRepository = aiRepository,
                    scheduleRepository = repository,
                    reminderScheduler = reminderScheduler,
                    onRequestNotifications = onRequestNotifications,
                    onNavigate = { route -> navController.navigate(route) },
                )
            }
            composable(SettingsDestination.PERSONALIZATION, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
                SettingsPersonalizationScreen(preferencesRepository, keyStore, aiRepository, repository, reminderScheduler, navController::popBackStack)
            }
            composable(SettingsDestination.SCHEDULE_TYPES, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
                ScheduleTypesScreen(
                    repository = repository,
                    onBack = navController::popBackStack,
                    onAdd = { navController.navigate(SettingsDestination.NEW_SCHEDULE_TYPE) },
                    onEdit = { id -> navController.navigate("settings/schedule-types/$id") },
                )
            }
            composable(SettingsDestination.NEW_SCHEDULE_TYPE, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
                ScheduleTypeEditorScreen(repository, null, navController::popBackStack, navController::popBackStack)
            }
            composable(SettingsDestination.EDIT_SCHEDULE_TYPE, arguments = listOf(navArgument("id") { type = NavType.LongType }), enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) { backStackEntry ->
                ScheduleTypeEditorScreen(
                    repository = repository,
                    typeId = backStackEntry.arguments?.getLong("id"),
                    onBack = navController::popBackStack,
                    onSaved = navController::popBackStack,
                )
            }
            composable(SettingsDestination.AI, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
                SettingsAiScreen(preferencesRepository, keyStore, aiRepository, repository, reminderScheduler, navController::popBackStack)
            }
            composable(SettingsDestination.REMINDERS, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
                SettingsRemindersScreen(preferencesRepository, keyStore, aiRepository, repository, reminderScheduler, navController::popBackStack)
            }
            composable(SettingsDestination.RELIABILITY, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
                SettingsReliabilityScreen(preferencesRepository, keyStore, aiRepository, repository, reminderScheduler, onRequestNotifications, navController::popBackStack)
            }
            composable(SettingsDestination.DATA, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
                SettingsDataScreen(preferencesRepository, keyStore, aiRepository, repository, reminderScheduler, navController::popBackStack)
            }
            composable(SettingsDestination.ABOUT, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
                SettingsAboutScreen(navController::popBackStack)
            }
            composable(AddRoute, enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) {
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
            composable(EditRoute, arguments = listOf(navArgument("id") { type = NavType.LongType }), enterTransition = detailEnter, exitTransition = detailExit, popEnterTransition = detailPopEnter, popExitTransition = detailPopExit) { backStackEntry ->
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
