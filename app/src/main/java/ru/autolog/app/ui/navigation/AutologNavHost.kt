package ru.autolog.app.ui.navigation

import androidx.activity.compose.BackHandler
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.Modifier
import androidx.activity.ComponentActivity
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.ui.AutologViewModelFactory
import ru.autolog.app.ui.analytics.AnalyticsScreen
import ru.autolog.app.ui.dashboard.DashboardScreen
import ru.autolog.app.ui.dashboard.DashboardViewModel
import ru.autolog.app.ui.expense.ExpenseScreen
import ru.autolog.app.ui.fluid.FluidNavTransitions
import ru.autolog.app.ui.history.HistoryFilter
import ru.autolog.app.ui.history.HistoryScreen
import ru.autolog.app.ui.map.GasStationMapScreen
import ru.autolog.app.domain.MaintenanceKind
import ru.autolog.app.ui.docs.DocumentViewerScreen
import ru.autolog.app.ui.docs.VehicleDocsScreen
import ru.autolog.app.ui.maintenance.MaintenanceListScreen
import ru.autolog.app.ui.note.NotepadScreen
import ru.autolog.app.ui.refuel.AddRefuelScreen
import ru.autolog.app.ui.reminder.AddReminderScreen
import ru.autolog.app.ui.service.ServiceScreen
import ru.autolog.app.ui.vehicle.AddVehicleScreen

@Composable
fun AutologNavHost() {
    val navController = rememberNavController()
    val dashboardVm: DashboardViewModel = viewModel(factory = AutologViewModelFactory())
    val dashState by dashboardVm.uiState.collectAsStateWithLifecycle()
    val backStackEntry by navController.currentBackStackEntryAsState()
    val activity = LocalContext.current as? ComponentActivity
    var showExitDialog by remember { mutableStateOf(false) }

    val start = if (!dashState.loading && dashState.vehicles.isEmpty()) {
        Routes.ADD_VEHICLE
    } else {
        Routes.DASHBOARD
    }

    if (dashState.loading) return

    // After deleting the last vehicle, open AddVehicle from any screen.
    LaunchedEffect(dashState.loading, dashState.vehicles.size) {
        if (dashState.loading || dashState.vehicles.isNotEmpty()) return@LaunchedEffect
        val route = navController.currentBackStackEntry?.destination?.route
        if (route == Routes.ADD_VEHICLE) return@LaunchedEffect
        navController.navigate(Routes.ADD_VEHICLE) {
            popUpTo(Routes.DASHBOARD) { inclusive = true }
            launchSingleTop = true
        }
    }

    fun handleSystemBack() {
        val current = backStackEntry?.destination?.route
        val canPop = navController.previousBackStackEntry != null &&
            current != Routes.DASHBOARD &&
            !(current == Routes.ADD_VEHICLE && dashState.vehicles.isEmpty())
        if (canPop) {
            navController.popBackStack()
        } else {
            showExitDialog = true
        }
    }

    BackHandler {
        handleSystemBack()
    }

    if (showExitDialog) {
        AlertDialog(
            onDismissRequest = { showExitDialog = false },
            title = { Text("Точно выйти?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        showExitDialog = false
                        activity?.finish()
                    },
                    modifier = Modifier.testTag("exit_confirm"),
                ) { Text("Атвичаю") }
            },
            dismissButton = {
                TextButton(
                    onClick = { showExitDialog = false },
                    modifier = Modifier.testTag("exit_cancel"),
                ) { Text("Ни в коем случае") }
            },
        )
    }

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
                onAddService = { kind -> navController.navigate(Routes.addService(kind.name)) },
                onAddWash = { navController.navigate(Routes.ADD_EXPENSE_WASH) },
                onOpenNotepad = { navController.navigate(Routes.NOTEPAD) },
                onOpenDocs = { navController.navigate(Routes.VEHICLE_DOCS) },
                onOpenServiceHistory = { filter ->
                    navController.navigate(Routes.history(filter.name))
                },
                onOpenAnalytics = { navController.navigate(Routes.ANALYTICS) },
                onOpenSettings = { navController.navigate(Routes.SETTINGS) },
                onOpenRefuel = { id -> navController.navigate(Routes.refuelDetail(id)) },
                onOpenReminder = { id -> navController.navigate(Routes.reminderDetail(id)) },
                onOpenMaintenance = { navController.navigate(Routes.MAINTENANCE) },
                onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
                onOpenService = { id -> navController.navigate(Routes.serviceDetail(id)) },
                onEditService = { id -> navController.navigate(Routes.serviceEdit(id)) },
            )
        }
        composable(Routes.MAINTENANCE) {
            MaintenanceListScreen(
                onBack = { navController.popBackStack() },
                onOpenReminder = { id -> navController.navigate(Routes.reminderDetail(id)) },
                onAddReminderForKind = { kind ->
                    navController.navigate(Routes.addReminderKind(kind.name))
                },
            )
        }
        composable(Routes.HISTORY) {
            HistoryScreen(
                onBack = { navController.popBackStack() },
                onOpenRefuel = { id -> navController.navigate(Routes.refuelDetail(id)) },
                onOpenService = { id ->
                    navController.navigate(Routes.serviceDetail(id))
                },
                onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
            )
        }
        composable(
            Routes.HISTORY_FILTERED,
            arguments = listOf(navArgument("filter") { type = NavType.StringType }),
        ) { entry ->
            val filter = HistoryFilter.fromArg(entry.arguments?.getString("filter"))
            HistoryScreen(
                initialFilter = filter,
                serviceHubMode = true,
                onBack = { navController.popBackStack() },
                onOpenRefuel = { id -> navController.navigate(Routes.refuelDetail(id)) },
                onOpenService = { id -> navController.navigate(Routes.serviceDetail(id)) },
                onOpenExpense = { id -> navController.navigate(Routes.expenseDetail(id)) },
            )
        }
        composable(Routes.ANALYTICS) {
            AnalyticsScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.SETTINGS) {
            ru.autolog.app.ui.settings.SettingsScreen(
                onBack = { navController.popBackStack() },
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
                onBack = if (dashState.vehicles.isEmpty()) {
                    { showExitDialog = true }
                } else {
                    { navController.popBackStack() }
                },
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
            Routes.ADD_REMINDER_KIND,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            val kind = MaintenanceKind.fromStorage(entry.arguments?.getString("kind").orEmpty())
            AddReminderScreen(
                initialKind = kind,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ADD_EXPENSE) {
            ExpenseScreen(
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.ADD_EXPENSE_WASH) {
            ExpenseScreen(
                fixedCategory = "Мойка",
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.NOTEPAD) {
            NotepadScreen(
                onBack = { navController.popBackStack() },
            )
        }
        composable(Routes.VEHICLE_DOCS) {
            VehicleDocsScreen(
                onBack = { navController.popBackStack() },
                onOpenDocument = { id -> navController.navigate(Routes.documentViewer(id)) },
            )
        }
        composable(
            Routes.DOCUMENT_VIEWER,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            DocumentViewerScreen(
                documentId = id,
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.ADD_SERVICE,
            arguments = listOf(navArgument("kind") { type = NavType.StringType }),
        ) { entry ->
            val kindName = entry.arguments?.getString("kind") ?: ServiceKind.TO.name
            val kind = ServiceKind.fromStorage(kindName)
            ServiceScreen(
                fixedKind = kind,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
            )
        }
        composable(
            Routes.REFUEL_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            AddRefuelScreen(
                editRefuelId = id,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
                onPickStationOnMap = { navController.navigate(Routes.GAS_MAP) },
                backStackEntry = entry,
            )
        }
        composable(
            Routes.REMINDER_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            AddReminderScreen(
                editReminderId = id,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(
            Routes.EXPENSE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ExpenseScreen(
                editExpenseId = id,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(
            Routes.SERVICE_DETAIL,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ServiceScreen(
                editServiceId = id,
                startReadOnly = true,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }
        composable(
            Routes.SERVICE_EDIT,
            arguments = listOf(navArgument("id") { type = NavType.LongType }),
        ) { entry ->
            val id = entry.arguments?.getLong("id") ?: return@composable
            ServiceScreen(
                editServiceId = id,
                startReadOnly = false,
                onSaved = { navController.popBackStack() },
                onBack = { navController.popBackStack() },
                onDeleted = { navController.popBackStack() },
            )
        }
    }
}
