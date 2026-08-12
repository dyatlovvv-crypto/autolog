package ru.autolog.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import java.util.Calendar
import java.util.Locale
import ru.autolog.app.AutologApp
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.data.repository.DashboardSnapshot
import ru.autolog.app.data.repository.TimelineEntry
import ru.autolog.app.data.settings.UserSettings
import ru.autolog.app.domain.FuelType
import ru.autolog.app.domain.ServiceChecklistCodec
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.ui.AutologViewModelFactory
import ru.autolog.app.ui.components.ActivityKind
import ru.autolog.app.ui.components.ExpandableTimelineItem
import ru.autolog.app.ui.components.StatCard
import ru.autolog.app.ui.components.TimelineRoadConnector
import ru.autolog.app.ui.components.TimelineTags
import ru.autolog.app.ui.components.expenseActivityKind
import ru.autolog.app.ui.components.icon
import ru.autolog.app.ui.components.tint
import ru.autolog.app.ui.components.toActivityKind
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassFab
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.history.HistoryFilter
import ru.autolog.app.ui.util.Formatters

object DashboardTags {
    const val ROOT = "dashboard_root"
    const val TITLE = "dashboard_title"
    const val AVG_CONSUMPTION = "stat_avg_consumption"
    const val LAST_CONSUMPTION = "stat_last_consumption"
    const val OIL_REMINDER = "stat_oil_reminder"
    const val ADD_REFUEL = "dashboard_add_refuel"
    const val ADD_REMINDER = "dashboard_add_reminder"
    const val ADD_SERVICE = "dashboard_add_service"
    const val OPEN_ANALYTICS = "dashboard_open_analytics"
    const val OPEN_SETTINGS = "dashboard_open_settings"
    const val ACTIVITY_FEED = "dashboard_activity_feed"
    const val SERVICE_HUB_ADD_WASH = "service_hub_add_wash"
    fun serviceHubHistory(filter: String) = "service_hub_history_$filter"
    fun feedReminder(id: Long) = TimelineTags.item("REMINDER", id)
    fun feedRefuel(id: Long) = TimelineTags.item("REFUEL", id)
    fun feedExpense(id: Long) = TimelineTags.item("EXPENSE", id)
    fun feedService(id: Long) = TimelineTags.item("SERVICE", id)
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DashboardScreen(
    onAddVehicle: () -> Unit,
    onAddRefuel: () -> Unit,
    onAddReminder: () -> Unit,
    onAddService: (ServiceKind) -> Unit,
    onAddWash: () -> Unit,
    onOpenServiceHistory: (HistoryFilter) -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRefuel: (Long) -> Unit,
    onOpenReminder: (Long) -> Unit,
    onOpenExpense: (Long) -> Unit,
    onOpenService: (Long) -> Unit,
    onEditService: (Long) -> Unit = onOpenService,
    viewModel: DashboardViewModel = viewModel(factory = AutologViewModelFactory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val settings by AutologApp.instance.container.settingsStore.settings
        .collectAsStateWithLifecycle(initialValue = UserSettings())
    var vehicleSelectorOpen by remember { mutableStateOf(false) }
    var serviceHubOpen by remember { mutableStateOf(false) }
    var expandedKey by remember { mutableStateOf<String?>(null) }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DashboardTags.ROOT),
        floatingActionButtonPosition = FabPosition.Center,
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
                        onClick = onOpenAnalytics,
                        modifier = Modifier.testTag(DashboardTags.OPEN_ANALYTICS),
                    ) {
                        Icon(Icons.Default.Analytics, contentDescription = "Аналитика")
                    }
                    IconButton(
                        onClick = onAddReminder,
                        modifier = Modifier.testTag(DashboardTags.ADD_REMINDER),
                    ) {
                        Icon(Icons.Default.Notifications, contentDescription = "Напоминание")
                    }
                    IconButton(
                        onClick = onOpenSettings,
                        modifier = Modifier.testTag(DashboardTags.OPEN_SETTINGS),
                    ) {
                        Icon(Icons.Default.Settings, contentDescription = "Настройки")
                    }
                },
            )
        },
        floatingActionButton = {
            if (state.vehicles.isNotEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                ) {
                    GlassFab(
                        onClick = { serviceHubOpen = true },
                        modifier = Modifier
                            .align(Alignment.CenterStart)
                            .testTag(DashboardTags.ADD_SERVICE),
                    ) {
                        Icon(Icons.Default.Build, contentDescription = "Сервис")
                    }
                    GlassFab(
                        onClick = onAddRefuel,
                        modifier = Modifier
                            .align(Alignment.CenterEnd)
                            .testTag(DashboardTags.ADD_REFUEL),
                    ) {
                        Icon(Icons.Default.LocalGasStation, contentDescription = "Заправка")
                    }
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
                val timeline = dash?.timeline.orEmpty()
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
                        text = "Пробег: ${dash?.vehicle?.currentOdometerKm ?: "—"} км · бак ${
                            dash?.vehicle?.tankCapacityLiters?.let {
                                Formatters.volumeLabel(it, settings.volumeUnit)
                            } ?: "—"
                        }",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    StatCard(
                        title = "Последний расход (до полного)",
                        value = Formatters.consumptionValue(
                            dash?.lastConsumptionLPer100,
                            settings.consumptionUnit,
                        ),
                        unit = if (dash?.lastConsumptionLPer100 == null) {
                            Formatters.consumptionHint(null)
                        } else {
                            Formatters.consumptionUnitLabel(settings.consumptionUnit)
                        },
                        testTag = DashboardTags.LAST_CONSUMPTION,
                    )
                    val oilReminder = remember(dash?.activeReminders) {
                        primaryOilReminder(dash?.activeReminders.orEmpty())
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        StatCard(
                            title = "Средний расход",
                            value = Formatters.consumptionValue(
                                dash?.averageConsumptionLPer100,
                                settings.consumptionUnit,
                            ),
                            unit = if (dash?.averageConsumptionLPer100 == null) {
                                Formatters.consumptionHint(null)
                            } else {
                                Formatters.consumptionUnitLabel(settings.consumptionUnit)
                            },
                            cardModifier = Modifier.weight(1f),
                            testTag = DashboardTags.AVG_CONSUMPTION,
                        )
                        StatCard(
                            title = oilReminder?.title?.takeIf { it.isNotBlank() } ?: "Замена масла",
                            value = oilReminder?.remainingKm?.toString() ?: "нет данных",
                            unit = when {
                                oilReminder == null -> "добавьте напоминание"
                                oilReminder.remainingDays != null ->
                                    "км · ${oilReminder.remainingDays} дн."
                                else -> "км до замены"
                            },
                            cardModifier = Modifier.weight(1f),
                            testTag = DashboardTags.OIL_REMINDER,
                            onClick = {
                                if (oilReminder != null) {
                                    onOpenReminder(oilReminder.id)
                                } else {
                                    onAddReminder()
                                }
                            },
                        )
                    }

                    Text("Лента", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Тап — раскрыть · смотреть / править / удалить",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DashboardTags.ACTIVITY_FEED),
                    ) {
                        if (timeline.isEmpty()) {
                            Text(
                                "Пока пусто. Добавьте заправку, расход или сервис.",
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        } else {
                            var lastMonthKey: String? = null
                            timeline.forEachIndexed { index, entry ->
                                val monthKey = monthKey(entry.sortEpochMs)
                                if (monthKey != lastMonthKey) {
                                    lastMonthKey = monthKey
                                    Row(
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = if (index == 0) 4.dp else 0.dp),
                                        verticalAlignment = Alignment.CenterVertically,
                                    ) {
                                        // Keep the road continuous through month labels
                                        TimelineRoadConnector(
                                            modifier = Modifier
                                                .width(40.dp)
                                                .height(28.dp),
                                        )
                                        Text(
                                            text = monthHeaderRu(entry.sortEpochMs),
                                            style = MaterialTheme.typography.labelLarge,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                                            modifier = Modifier.padding(start = 4.dp, bottom = 2.dp),
                                        )
                                    }
                                }
                                val presentation = remember(entry) { timelinePresentation(entry) }
                                val key = "${presentation.kindKey}_${entry.id}"
                                ExpandableTimelineItem(
                                    kind = presentation.kind,
                                    kindKey = presentation.kindKey,
                                    id = entry.id,
                                    title = presentation.title,
                                    subtitle = presentation.subtitle,
                                    detailLines = presentation.details,
                                    expanded = expandedKey == key,
                                    isFirst = index == 0,
                                    isLast = index == timeline.lastIndex,
                                    onToggle = {
                                        expandedKey = if (expandedKey == key) null else key
                                    },
                                    onView = {
                                        when (entry) {
                                            is TimelineEntry.Refuel -> onOpenRefuel(entry.id)
                                            is TimelineEntry.Expense -> onOpenExpense(entry.id)
                                            is TimelineEntry.Reminder -> onOpenReminder(entry.id)
                                            is TimelineEntry.Service -> onOpenService(entry.id)
                                        }
                                    },
                                    onEdit = {
                                        when (entry) {
                                            is TimelineEntry.Refuel -> onOpenRefuel(entry.id)
                                            is TimelineEntry.Expense -> onOpenExpense(entry.id)
                                            is TimelineEntry.Reminder -> onOpenReminder(entry.id)
                                            is TimelineEntry.Service -> onEditService(entry.id)
                                        }
                                    },
                                    onDelete = {
                                        when (entry) {
                                            is TimelineEntry.Refuel -> viewModel.deleteRefuelling(entry.id)
                                            is TimelineEntry.Expense -> viewModel.deleteExpense(entry.id)
                                            is TimelineEntry.Reminder -> viewModel.deleteReminder(entry.id)
                                            is TimelineEntry.Service -> viewModel.deleteService(entry.id)
                                        }
                                        if (expandedKey == key) expandedKey = null
                                    },
                                )
                            }
                        }
                    }
                    Spacer(modifier = Modifier.height(72.dp))
                }
            }
        }
    }

    if (serviceHubOpen) {
        AlertDialog(
            onDismissRequest = { serviceHubOpen = false },
            title = { Text("Сервис") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                    Text("Добавить", style = MaterialTheme.typography.titleSmall)
                    ServiceKind.entries.forEach { kind ->
                        val activity = kind.toActivityKind()
                        TextButton(
                            onClick = {
                                serviceHubOpen = false
                                onAddService(kind)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("add_service_kind_${kind.name}"),
                        ) {
                            Icon(
                                imageVector = activity.icon(),
                                contentDescription = null,
                                tint = activity.tint(),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text(kind.label)
                        }
                    }
                    TextButton(
                        onClick = {
                            serviceHubOpen = false
                            onAddWash()
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(DashboardTags.SERVICE_HUB_ADD_WASH),
                    ) {
                        Icon(
                            imageVector = ActivityKind.WASH.icon(),
                            contentDescription = null,
                            tint = ActivityKind.WASH.tint(),
                        )
                        Spacer(modifier = Modifier.size(8.dp))
                        Text("Мойка")
                    }

                    Spacer(modifier = Modifier.height(8.dp))
                    Text("История", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        HistoryFilter.TO to ActivityKind.TO,
                        HistoryFilter.REPAIR to ActivityKind.REPAIR,
                        HistoryFilter.PLANNED to ActivityKind.PLANNED,
                        HistoryFilter.WASH to ActivityKind.WASH,
                    ).forEach { (filter, activity) ->
                        TextButton(
                            onClick = {
                                serviceHubOpen = false
                                onOpenServiceHistory(filter)
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(DashboardTags.serviceHubHistory(filter.name)),
                        ) {
                            Icon(
                                imageVector = activity.icon(),
                                contentDescription = null,
                                tint = activity.tint(),
                            )
                            Spacer(modifier = Modifier.size(8.dp))
                            Text("История · ${filter.label}")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { serviceHubOpen = false }) { Text("Отмена") }
            },
        )
    }
}

private fun primaryOilReminder(
    reminders: List<ReminderEntity>,
): ReminderEntity? {
    if (reminders.isEmpty()) return null
    return reminders.firstOrNull { it.title.contains("масл", ignoreCase = true) }
        ?: reminders.minByOrNull { it.remainingKm ?: Int.MAX_VALUE }
}

private data class TimelinePresentation(
    val kind: ActivityKind,
    val kindKey: String,
    val title: String,
    val subtitle: String,
    val details: List<String>,
)

private fun timelinePresentation(entry: TimelineEntry): TimelinePresentation = when (entry) {
    is TimelineEntry.Refuel -> {
        val r = entry.entity
        val fuelLabel = FuelType.fromStorage(r.fuelType)?.label
        TimelinePresentation(
            kind = ActivityKind.REFUEL,
            kindKey = "REFUEL",
            title = "Заправка",
            subtitle = buildString {
                append(Formatters.date(r.dateEpochMs))
                r.stationName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
                append(" · ${r.odometerKm} км")
                append(" · ${Formatters.money(r.totalCost)}")
            },
            details = buildList {
                add(
                    buildString {
                        append(r.volumeLiters)
                        append(" л")
                        fuelLabel?.let { append(" · $it") }
                        if (r.isTankFull) append(" · до полного")
                    },
                )
                if (r.pricePerLiter > 0) add("${Formatters.money(r.pricePerLiter)} / л")
                r.note?.takeIf { it.isNotBlank() }?.let { add(it) }
            },
        )
    }
    is TimelineEntry.Expense -> {
        val e = entry.entity
        val kind = expenseActivityKind(e.category)
        TimelinePresentation(
            kind = kind,
            kindKey = "EXPENSE",
            title = e.category.ifBlank { "Расход" },
            subtitle = buildString {
                append(Formatters.date(e.dateEpochMs))
                append(" · ${Formatters.money(e.totalCost)}")
                e.odometerKm?.let { append(" · $it км") }
            },
            details = listOfNotNull(
                e.description?.takeIf { it.isNotBlank() },
            ),
        )
    }
    is TimelineEntry.Service -> {
        val s = entry.entity
        val serviceKind = ServiceKind.fromStorage(s.serviceType)
        TimelinePresentation(
            kind = serviceKind.toActivityKind(),
            kindKey = "SERVICE",
            title = serviceKind.label,
            subtitle = buildString {
                append(Formatters.date(s.dateEpochMs))
                append(" · ${s.odometerKm} км")
                append(" · ${Formatters.money(s.totalCost)}")
            },
            details = listOfNotNull(
                s.description?.takeIf { it.isNotBlank() },
            ) + ServiceChecklistCodec.decode(s.checklistJson).map { item ->
                buildString {
                    append(if (item.done) "✓ " else "○ ")
                    append(item.text)
                }
            },
        )
    }
    is TimelineEntry.Reminder -> {
        val reminder = entry.entity
        val kmPart = reminder.remainingKm?.let { "Осталось $it км" } ?: "км н/д"
        val daysPart = reminder.remainingDays?.let { "$it дн." } ?: "дн. н/д"
        TimelinePresentation(
            kind = ActivityKind.REMINDER,
            kindKey = "REMINDER",
            title = reminder.title,
            subtitle = "$kmPart или $daysPart · ${Formatters.date(reminder.nextDueDateEpochMs)}",
            details = buildList {
                reminder.intervalKm?.let { add("Интервал: $it км") }
                reminder.intervalDays?.let { add("Интервал: $it дн.") }
                reminder.nextDueOdometerKm?.let { add("След. пробег: $it км") }
            },
        )
    }
}

private fun monthKey(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    return "${cal.get(Calendar.YEAR)}-${cal.get(Calendar.MONTH)}"
}

private fun monthHeaderRu(epochMs: Long): String {
    val cal = Calendar.getInstance().apply { timeInMillis = epochMs }
    val months = arrayOf(
        "ЯНВАРЯ", "ФЕВРАЛЯ", "МАРТА", "АПРЕЛЯ", "МАЯ", "ИЮНЯ",
        "ИЮЛЯ", "АВГУСТА", "СЕНТЯБРЯ", "ОКТЯБРЯ", "НОЯБРЯ", "ДЕКАБРЯ",
    )
    val month = months[cal.get(Calendar.MONTH)]
    val year = cal.get(Calendar.YEAR)
    return "$month $year".uppercase(Locale("ru"))
}
