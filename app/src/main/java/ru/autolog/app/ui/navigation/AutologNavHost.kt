package ru.autolog.app.ui.navigation

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.autolog.app.ui.AutologViewModelFactory
import ru.autolog.app.ui.dashboard.DashboardScreen
import ru.autolog.app.ui.dashboard.DashboardViewModel
import ru.autolog.app.ui.feed.RefuelDetailScreen
import ru.autolog.app.ui.feed.ReminderDetailScreen
import ru.autolog.app.ui.fluid.FluidNavTransitions
import ru.autolog.app.ui.map.GasStationMapScreen
import ru.autolog.app.ui.refuel.AddRefuelScreen
import ru.autolog.app.ui.reminder.AddReminderScreen
import ru.autolog.app.ui.vehicle.AddVehicleScreen

@Composable
fun AutologNavHost() {
    val navController = rememberNavController()
    val dashboardVm: DashboardViewModel = viewModel(factory = AutologViewModelFactory())
    val dashState by dashboardVm.uiState.collectAsStateWithLifecycle()

    val start = if (!dashState.loading && dashState.vehicles.isEmpty()) {
        Routes.ADD_VEHICLE
    } else {
        Routes.DASHBOARD
    }

    if (dashState.loading) return

    NavHost(
        navController = navController,
        startDestination = start,
        enterTransition = FluidNavTransitions.enter(),
        exitTransition = FluidNavTransitions.exit(),
        popEnterTransition = FluidNavTransitions.popEnter(),
        popExitTransition = FluidNavTransitions.popExit(),
    ) {
        composable(Routes.DASHBOARD) {
            DashboardScreen(
                viewModel = dashboardVm,
                onAddVehicle = { navController.navigate(Routes.ADD_VEHICLE) },
                onAddRefuel = { navController.navigate(Routes.ADD_REFUEL) },
                onAddReminder = { navController.navigate(Routes.ADD_REMINDER) },
                onOpenRefuel = { id -> navController.navigate(Routes.refuelDetail(id)) },
                onOpenReminder = { id -> navController.navigate(Routes.reminderDetail(id)) },
            )
        }
        composable(Routes.ADD_VEHICLE) {
            AddVehicleScreen(
                onSaved = {
                    navController.navigate(Routes.DASHBOARD) {
                        popUpTo(Routes.ADD_VEHICLE) { inclusive = true }
                        launchSingleTop = true
                    }
                },
                onBack = if (dashState.vehicles.isEmpty()) null else ({ navController.popBackStack() }),
            )
        }
        composable(Routes.ADD_REFUEL) { entry ->
            AddRefuelScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onPickStationOnMap = { navController.navigate(Routes.GAS_MAP) },
                backStackEntry = entry,
            )
        }
        composable(Routes.GAS_MAP) {
            GasStationMapScreen(
                onStationPicked = { name ->
                    navController.previousBackStackEntry
                        ?.savedStateHandle
                        ?.set("picked_station", name)
                    navController.popBackStack()
                },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ADD_REMINDER) {
            AddReminderScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.REFUEL_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            RefuelDetailScreen(
                refuelId = id,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(
            Routes.REMINDER_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ReminderDetailScreen(
                reminderId = id,
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }
    }
}
