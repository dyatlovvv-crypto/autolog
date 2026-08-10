package ru.autolog.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.data.repository.DashboardSnapshot
import ru.autolog.app.ui.AutologViewModelFactory
import ru.autolog.app.ui.components.ActivityFeedCard
import ru.autolog.app.ui.components.ActivityKind
import ru.autolog.app.ui.components.StatCard
import ru.autolog.app.ui.components.SwipeDeleteRow
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassFab
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.util.Formatters

object DashboardTags {
    const val ROOT = "dashboard_root"
    const val TITLE = "dashboard_title"
    const val AVG_CONSUMPTION = "stat_avg_consumption"
    const val LAST_CONSUMPTION = "stat_last_consumption"
    const val ADD_REFUEL = "dashboard_add_refuel"
    const val ADD_REMINDER = "dashboard_add_reminder"
    const val ACTIVITY_FEED = "dashboard_activity_feed"
    fun feedReminder(id: Long) = "feed_reminder_$id"
    fun feedRefuel(id: Long) = "feed_refuel_$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddVehicle: () -> Unit,
    onAddRefuel: () -> Unit,
    onAddReminder: () -> Unit,
    onOpenRefuel: (Long) -> Unit,
    onOpenReminder: (Long) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = AutologViewModelFactory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var vehicleSelectorOpen by remember { mutableStateOf(false) }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DashboardTags.ROOT),
        topBar = {
            GlassTopAppBar(
                title = {
                    if (state.vehicles.isNotEmpty()) {
                        VehicleSelectorBar(
                            vehicles = state.vehicles,
                            currentVehicleId = state.currentVehicleId,
                            expanded = vehicleSelectorOpen,
                            canAddVehicle = state.canAddVehicle,
                            onExpandChange = { vehicleSelectorOpen = it },
                            onSelectVehicle = viewModel::selectVehicle,
                            onAddVehicle = onAddVehicle,
                            onDeleteVehicle = viewModel::deleteVehicle,
                        )
                    } else {
                        Text(
                            text = "Autolog",
                            modifier = Modifier.testTag(DashboardTags.TITLE),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = onAddReminder,
                        modifier = Modifier.testTag(DashboardTags.ADD_REMINDER),
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Напоминание")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.vehicles.isNotEmpty()) {
                GlassFab(
                    onClick = onAddRefuel,
                    modifier = Modifier.testTag(DashboardTags.ADD_REFUEL),
                ) {
                    Icon(Icons.Default.LocalGasStation, contentDescription = "Заправка")
                }
            }
        },
    ) { padding ->
        when {
            state.loading -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                ) { Text("Загрузка…") }
            }
            state.vehicles.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(24.dp),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text("Добавьте первый автомобиль", style = MaterialTheme.typography.headlineSmall)
                    Spacer(modifier = Modifier.height(8.dp))
                    Text("Нужны название, пробег и объём бака.")
                    Spacer(modifier = Modifier.height(16.dp))
                    TextButton(onClick = onAddVehicle) { Text("Добавить автомобиль") }
                }
            }
            else -> {
                val dash = state.dashboard as? DashboardSnapshot.Data
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(horizontal = 16.dp)
                        .rubberBandOverscroll()
                        .verticalScroll(rememberScrollState()),
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "Пробег: ${dash?.vehicle?.currentOdometerKm ?: "—"} км · бак ${dash?.vehicle?.tankCapacityLiters ?: "—"} л",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatCard(
                        title = "Средний расход за всё время",
                        value = Formatters.litersPer100(dash?.averageConsumptionLPer100),
                        unit = Formatters.consumptionHint(dash?.averageConsumptionLPer100),
                        testTag = DashboardTags.AVG_CONSUMPTION,
                    )
                    StatCard(
                        title = "Последний расход (до полного)",
                        value = Formatters.litersPer100(dash?.lastConsumptionLPer100),
                        unit = Formatters.consumptionHint(dash?.lastConsumptionLPer100),
                        testTag = DashboardTags.LAST_CONSUMPTION,
                    )

                    Text("Лента", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Тап — открыть · свайп влево — удалить",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier.testTag(DashboardTags.ACTIVITY_FEED),
                        verticalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        val reminders = dash?.activeReminders.orEmpty()
                        val refuels = dash?.recentRefuellings.orEmpty()
                        if (reminders.isEmpty() && refuels.isEmpty()) {
                            Text(
                                "Пока пусто. Добавьте заправку или напоминание.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                        reminders.forEach { reminder ->
                            SwipeDeleteRow(onDelete = { viewModel.deleteReminder(reminder.id) }) {
                                ReminderFeedItem(
                                    reminder = reminder,
                                    onClick = { onOpenReminder(reminder.id) },
                                    modifier = Modifier.testTag(DashboardTags.feedReminder(reminder.id)),
                                )
                            }
                        }
                        refuels.forEach { r ->
                            SwipeDeleteRow(onDelete = { viewModel.deleteRefuelling(r.id) }) {
                                ActivityFeedCard(
                                    kind = ActivityKind.REFUEL,
                                    title = "Заправка · ${r.volumeLiters} л",
                                    subtitle = buildString {
                                        append(Formatters.date(r.dateEpochMs))
                                        append(" · ")
                                        append("${r.odometerKm} км")
                                        append(" · ")
                                        append(Formatters.money(r.totalCost))
                                        if (r.isTankFull) append(" · до полного")
                                        r.stationName?.let { append(" · $it") }
                                    },
                                    onClick = { onOpenRefuel(r.id) },
                                    modifier = Modifier.testTag(DashboardTags.feedRefuel(r.id)),
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }
}

@Composable
private fun ReminderFeedItem(
    reminder: ReminderEntity,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val kmPart = reminder.remainingKm?.let { "Осталось $it км" } ?: "км н/д"
    val daysPart = reminder.remainingDays?.let { "$it дн." } ?: "дн. н/д"
    val datePart = Formatters.date(reminder.nextDueDateEpochMs)
    ActivityFeedCard(
        kind = ActivityKind.REMINDER,
        title = reminder.title,
        subtitle = "$kmPart или $daysPart (примерно: $datePart)",
        onClick = onClick,
        modifier = modifier,
    )
}
