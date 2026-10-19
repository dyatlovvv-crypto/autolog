package ru.autolog.app.ui.dashboard

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.StickyNote2
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.outlined.AttachFile
import androidx.compose.material.icons.filled.Build
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.LocalGasStation
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FabPosition
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontWeight
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
import ru.autolog.app.data.settings.ConsumptionUnit
import ru.autolog.app.data.settings.UserSettings
import ru.autolog.app.domain.FuelType
import ru.autolog.app.domain.MaintenanceCatalog
import ru.autolog.app.domain.ServiceChecklistCodec
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.ui.AutologViewModelFactory
import ru.autolog.app.ui.components.AccentIcon
import ru.autolog.app.ui.components.AccentIconButton
import ru.autolog.app.ui.components.ActivityKind
import ru.autolog.app.ui.components.ExpandableTimelineItem
import ru.autolog.app.ui.components.StatCard
import ru.autolog.app.ui.components.TimelineTags
import ru.autolog.app.ui.components.expenseActivityKind
import ru.autolog.app.ui.components.icon
import ru.autolog.app.ui.components.tint
import ru.autolog.app.ui.components.toActivityKind
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.history.HistoryFilter
import ru.autolog.app.ui.theme.AutologElevation
import ru.autolog.app.ui.theme.AutologRadii
import ru.autolog.app.ui.theme.AutologSpace
import ru.autolog.app.ui.util.Formatters

object DashboardTags {
    const val ROOT = "dashboard_root"
    const val TITLE = "dashboard_title"
    const val AVG_CONSUMPTION = "stat_avg_consumption"
    const val LAST_CONSUMPTION = "stat_last_consumption"
    const val OIL_REMINDER = "stat_oil_reminder"
    const val MAINTENANCE_SLOT_0 = "stat_maintenance_0"
    const val MAINTENANCE_SLOT_1 = "stat_maintenance_1"
    const val ADD_REFUEL = "dashboard_add_refuel"
    const val ADD_REMINDER = "dashboard_add_reminder"
    const val ADD_SERVICE = "dashboard_add_service"
    const val OPEN_ANALYTICS = "dashboard_open_analytics"
    const val OPEN_SETTINGS = "dashboard_open_settings"
    const val ACTIVITY_FEED = "dashboard_activity_feed"
    const val SERVICE_HUB_ADD_WASH = "service_hub_add_wash"
    const val SERVICE_HUB_ADD_SECTION = "service_hub_add_section"
    const val SERVICE_HUB_HISTORY_SECTION = "service_hub_history_section"
    const val OPEN_NOTEPAD = "dashboard_open_notepad"
    const val OPEN_DOCS = "dashboard_open_docs"
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
    onOpenNotepad: () -> Unit,
    onOpenDocs: () -> Unit,
    onOpenServiceHistory: (HistoryFilter) -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenRefuel: (Long) -> Unit,
    onOpenReminder: (Long) -> Unit,
    onOpenMaintenance: () -> Unit,
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
    var serviceHubAddOpen by remember { mutableStateOf(true) }
    var serviceHubHistoryOpen by remember { mutableStateOf(false) }
    var expandedKey by remember { mutableStateOf<String?>(null) }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(DashboardTags.ROOT),
        floatingActionButtonPosition = FabPosition.Center,
        topBar = {
            TopAppBar(
                title = {
                    if (state.vehicles.isEmpty()) {
                        Text(
                            text = "Autolog",
                            style = MaterialTheme.typography.titleLarge,
                            modifier = Modifier.testTag(DashboardTags.TITLE),
                        )
                    }
                },
                actions = {
                    if (state.vehicles.isNotEmpty()) {
                        AccentIconButton(
                            onClick = onOpenDocs,
                            imageVector = Icons.Outlined.AttachFile,
                            contentDescription = "Документы",
                            modifier = Modifier.testTag(DashboardTags.OPEN_DOCS),
                        )
                        AccentIconButton(
                            onClick = onOpenNotepad,
                            imageVector = Icons.AutoMirrored.Outlined.StickyNote2,
                            contentDescription = "Заметки",
                            modifier = Modifier.testTag(DashboardTags.OPEN_NOTEPAD),
                        )
                    }
                    AccentIconButton(
                        onClick = onOpenAnalytics,
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Аналитика",
                        modifier = Modifier.testTag(DashboardTags.OPEN_ANALYTICS),
                    )
                    AccentIconButton(
                        onClick = onOpenSettings,
                        imageVector = Icons.Default.Settings,
                        contentDescription = "Настройки",
                        modifier = Modifier.testTag(DashboardTags.OPEN_SETTINGS),
                    )
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = MaterialTheme.colorScheme.background,
                    titleContentColor = MaterialTheme.colorScheme.onBackground,
                    actionIconContentColor = MaterialTheme.colorScheme.onSurface,
                ),
            )
        },
        floatingActionButton = {
            if (state.vehicles.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = AutologSpace.md),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    OutlinedButton(
                        onClick = { serviceHubOpen = true },
                        modifier = Modifier.testTag(DashboardTags.ADD_SERVICE),
                        shape = RoundedCornerShape(AutologRadii.card),
                    ) {
                        AccentIcon(
                            imageVector = Icons.Default.Build,
                            contentDescription = null,
                        )
                        Spacer(modifier = Modifier.width(AutologSpace.sm))
                        Text("Сервис", style = MaterialTheme.typography.bodyMedium)
                    }
                    RefuelFab(
                        onClick = onAddRefuel,
                        modifier = Modifier.testTag(DashboardTags.ADD_REFUEL),
                    )
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
                        .padding(AutologSpace.lg),
                ) { Text("Загрузка…", style = MaterialTheme.typography.bodyMedium) }
            }
            state.vehicles.isEmpty() -> {
                Column(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .padding(AutologSpace.lg),
                    verticalArrangement = Arrangement.Center,
                ) {
                    Text(
                        "Добавьте первый автомобиль",
                        style = MaterialTheme.typography.titleLarge,
                    )
                    Spacer(modifier = Modifier.height(AutologSpace.sm))
                    Text(
                        "Нужны название, пробег и объём бака.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    Spacer(modifier = Modifier.height(AutologSpace.md))
                    TextButton(onClick = onAddVehicle) { Text("Добавить автомобиль") }
                }
            }
            else -> {
                val dash = state.dashboard as? DashboardSnapshot.Data
                val timeline = dash?.timeline.orEmpty()
                val maintenanceSlots = remember(dash?.activeReminders) {
                    MaintenanceCatalog.dashboardSlots(dash?.activeReminders.orEmpty(), limit = 2)
                }
                val grouped = remember(timeline) {
                    timeline.mapIndexed { index, entry -> index to entry }
                        .groupBy { monthKey(it.second.sortEpochMs) }
                }
                LazyColumn(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(padding)
                        .rubberBandOverscroll()
                        .testTag(DashboardTags.ACTIVITY_FEED),
                    contentPadding = PaddingValues(
                        start = AutologSpace.md,
                        end = AutologSpace.md,
                        top = AutologSpace.sm,
                        bottom = AutologSpace.xl,
                    ),
                    verticalArrangement = Arrangement.spacedBy(AutologSpace.sm),
                ) {
                    item(key = "hero") {
                        VehicleSelectorBar(
                            vehicles = state.vehicles,
                            currentVehicleId = state.currentVehicleId,
                            expanded = vehicleSelectorOpen,
                            canAddVehicle = state.canAddVehicle,
                            onExpandChange = { vehicleSelectorOpen = it },
                            onSelectVehicle = viewModel::selectVehicle,
                            onAddVehicle = onAddVehicle,
                            odometerKm = dash?.vehicle?.currentOdometerKm,
                            tankLabel = dash?.vehicle?.tankCapacityLiters?.let {
                                Formatters.volumeLabel(it, settings.volumeUnit)
                            },
                        )
                    }
                    item(key = "metrics") {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(AutologSpace.sm),
                        ) {
                            ConsumptionPairCard(
                                lastValue = Formatters.consumptionValue(
                                    dash?.lastConsumptionLPer100,
                                    settings.consumptionUnit,
                                ),
                                lastUnit = if (dash?.lastConsumptionLPer100 == null) {
                                    Formatters.consumptionHint(null)
                                } else {
                                    Formatters.consumptionUnitLabel(settings.consumptionUnit)
                                },
                                avgValue = Formatters.consumptionValue(
                                    dash?.averageConsumptionLPer100,
                                    settings.consumptionUnit,
                                ),
                                avgUnit = if (dash?.averageConsumptionLPer100 == null) {
                                    Formatters.consumptionHint(null)
                                } else {
                                    Formatters.consumptionUnitLabel(settings.consumptionUnit)
                                },
                                modifier = Modifier.weight(1f),
                            )
                            Column(
                                modifier = Modifier.weight(1f),
                                verticalArrangement = Arrangement.spacedBy(AutologSpace.sm),
                            ) {
                                maintenanceSlots.forEachIndexed { index, slot ->
                                    val reminder = slot.reminder
                                    val tag = when (index) {
                                        0 -> DashboardTags.OIL_REMINDER
                                        1 -> DashboardTags.MAINTENANCE_SLOT_1
                                        else -> DashboardTags.MAINTENANCE_SLOT_0
                                    }
                                    StatCard(
                                        title = reminder?.title?.takeIf { it.isNotBlank() }
                                            ?: slot.title,
                                        value = reminder?.remainingKm?.toString() ?: "нет данных",
                                        unit = when {
                                            reminder == null -> "не задано"
                                            reminder.remainingDays != null ->
                                                "км · ${reminder.remainingDays} дн."
                                            else -> "км до замены"
                                        },
                                        cardModifier = Modifier.fillMaxWidth(),
                                        testTag = tag,
                                        onClick = onOpenMaintenance,
                                    )
                                }
                            }
                        }
                    }
                    item(key = "feed_title") {
                        Text(
                            "Лента",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onBackground,
                            modifier = Modifier.padding(top = AutologSpace.xs),
                        )
                    }
                    if (timeline.isEmpty()) {
                        item(key = "feed_empty") {
                            Text(
                                "Пока пусто. Добавьте заправку, расход или сервис.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    } else {
                        grouped.forEach { (_, rows) ->
                            val monthEpoch = rows.first().second.sortEpochMs
                            item(key = "month-${monthKey(monthEpoch)}") {
                                MonthLabel(text = monthHeaderRu(monthEpoch))
                            }
                            items(
                                items = rows,
                                key = { (_, entry) -> "${entry::class.simpleName}_${entry.id}" },
                            ) { (index, entry) ->
                                val presentation = remember(entry, index, timeline, settings.consumptionUnit) {
                                    val toKm = TimelineDistance.kmSincePreviousTo(timeline, index)
                                    val refuelKm = TimelineDistance.kmSincePreviousRefuel(timeline, index)
                                    val segmentLPer100 =
                                        TimelineDistance.consumptionLPer100SincePreviousFull(timeline, index)
                                    timelinePresentation(
                                        entry = entry,
                                        kmSincePreviousTo = toKm,
                                        kmSincePreviousRefuel = refuelKm,
                                        segmentConsumptionLPer100 = segmentLPer100,
                                        consumptionUnit = settings.consumptionUnit,
                                    )
                                }
                                val key = "${presentation.kindKey}_${entry.id}"
                                val roadKm = TimelineDistance.kmSincePreviousRefuel(timeline, index)
                                val segmentLPer100 =
                                    TimelineDistance.consumptionLPer100SincePreviousFull(timeline, index)
                                ExpandableTimelineItem(
                                    kind = presentation.kind,
                                    kindKey = presentation.kindKey,
                                    id = entry.id,
                                    title = presentation.title,
                                    subtitle = presentation.subtitle,
                                    detailLines = presentation.details,
                                    dateText = presentation.dateText,
                                    kmText = presentation.kmText,
                                    moneyText = presentation.moneyText,
                                    expanded = expandedKey == key,
                                    isFirst = index == 0,
                                    isLast = index == timeline.lastIndex,
                                    roadKm = roadKm,
                                    roadKmLabel = roadKm?.let { TimelineDistance.formatKm(it) },
                                    roadConsumptionLabel = segmentLPer100?.let {
                                        Formatters.consumptionValue(it, settings.consumptionUnit)
                                    },
                                    roadConsumptionUnit = segmentLPer100?.let {
                                        Formatters.consumptionUnitShort(settings.consumptionUnit)
                                    },
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
                }
            }
        }
    }

    if (serviceHubOpen) {
        AlertDialog(
            onDismissRequest = { serviceHubOpen = false },
            title = { Text("Сервис", style = MaterialTheme.typography.titleLarge) },
            text = {
                val hubScroll = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(hubScroll),
                    verticalArrangement = Arrangement.spacedBy(AutologSpace.xs),
                ) {
                    ServiceHubSectionHeader(
                        title = "Добавить",
                        expanded = serviceHubAddOpen,
                        testTag = DashboardTags.SERVICE_HUB_ADD_SECTION,
                        onClick = { serviceHubAddOpen = !serviceHubAddOpen },
                    )
                    AnimatedVisibility(visible = serviceHubAddOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(AutologSpace.xs)) {
                            TextButton(
                                onClick = {
                                    serviceHubOpen = false
                                    onAddReminder()
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(DashboardTags.ADD_REMINDER),
                            ) {
                                Icon(
                                    imageVector = Icons.Default.Notifications,
                                    contentDescription = null,
                                )
                                Spacer(modifier = Modifier.size(AutologSpace.sm))
                                Text("Напоминание", style = MaterialTheme.typography.bodyMedium)
                            }
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
                                    Spacer(modifier = Modifier.size(AutologSpace.sm))
                                    Text(kind.label, style = MaterialTheme.typography.bodyMedium)
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
                                Spacer(modifier = Modifier.size(AutologSpace.sm))
                                Text("Мойка", style = MaterialTheme.typography.bodyMedium)
                            }
                        }
                    }

                    ServiceHubSectionHeader(
                        title = "История",
                        expanded = serviceHubHistoryOpen,
                        testTag = DashboardTags.SERVICE_HUB_HISTORY_SECTION,
                        onClick = { serviceHubHistoryOpen = !serviceHubHistoryOpen },
                    )
                    AnimatedVisibility(visible = serviceHubHistoryOpen) {
                        Column(verticalArrangement = Arrangement.spacedBy(AutologSpace.xs)) {
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
                                    Spacer(modifier = Modifier.size(AutologSpace.sm))
                                    Text(
                                        "История · ${filter.label}",
                                        style = MaterialTheme.typography.bodyMedium,
                                    )
                                }
                            }
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

@Composable
private fun ServiceHubSectionHeader(
    title: String,
    expanded: Boolean,
    testTag: String,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(AutologRadii.card))
            .clickable(onClick = onClick)
            .testTag(testTag)
            .padding(vertical = AutologSpace.sm),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(title, style = MaterialTheme.typography.titleMedium)
        Icon(
            imageVector = if (expanded) {
                Icons.Default.KeyboardArrowUp
            } else {
                Icons.Default.KeyboardArrowDown
            },
            contentDescription = null,
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RefuelFab(
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val shape = RoundedCornerShape(AutologRadii.card)
    val start = MaterialTheme.colorScheme.primary
    val end = MaterialTheme.colorScheme.tertiary
    Row(
        modifier = modifier
            .shadow(
                elevation = AutologSpace.md,
                shape = shape,
                ambientColor = start.copy(alpha = 0.35f),
                spotColor = start.copy(alpha = 0.45f),
            )
            .clip(shape)
            .background(brush = Brush.linearGradient(listOf(start, end)))
            .clickable(role = Role.Button, onClick = onClick)
            .padding(horizontal = AutologSpace.lg, vertical = AutologSpace.md),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AutologSpace.sm),
    ) {
        Icon(
            imageVector = Icons.Default.LocalGasStation,
            contentDescription = "Заправка",
            tint = MaterialTheme.colorScheme.onPrimary,
        )
        Text(
            text = "Заправка",
            style = MaterialTheme.typography.bodyMedium.copy(fontWeight = FontWeight.SemiBold),
            color = MaterialTheme.colorScheme.onPrimary,
        )
    }
}

@Composable
private fun ConsumptionPairCard(
    lastValue: String,
    lastUnit: String,
    avgValue: String,
    avgUnit: String,
    modifier: Modifier = Modifier,
) {
    val lastEmpty = lastValue.equals("нет данных", ignoreCase = true)
    val avgEmpty = avgValue.equals("нет данных", ignoreCase = true)
    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(AutologRadii.card),
        tonalElevation = AutologElevation.card,
        shadowElevation = 0.dp,
    ) {
        Column(modifier = Modifier.padding(AutologSpace.md)) {
            Text(
                text = "Расход",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Последний",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AutologSpace.sm),
            )
            Text(
                text = lastValue,
                style = if (lastEmpty) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                },
                color = if (lastEmpty) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .padding(top = AutologSpace.xs)
                    .testTag(DashboardTags.LAST_CONSUMPTION),
            )
            Text(
                text = lastUnit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = "Средний",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = AutologSpace.sm),
            )
            Text(
                text = avgValue,
                style = if (avgEmpty) {
                    MaterialTheme.typography.labelSmall
                } else {
                    MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold)
                },
                color = if (avgEmpty) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.primary
                },
                modifier = Modifier
                    .padding(top = AutologSpace.xs)
                    .testTag(DashboardTags.AVG_CONSUMPTION),
            )
            Text(
                text = avgUnit,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun MonthLabel(text: String) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = AutologSpace.xs),
    ) {
        Surface(
            color = MaterialTheme.colorScheme.surface,
            contentColor = MaterialTheme.colorScheme.onSurface,
            shape = RoundedCornerShape(AutologRadii.card),
            tonalElevation = AutologElevation.card,
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(
                    horizontal = AutologSpace.sm,
                    vertical = AutologSpace.xs,
                ),
            )
        }
    }
}


private data class TimelinePresentation(
    val kind: ActivityKind,
    val kindKey: String,
    val title: String,
    val subtitle: String,
    val details: List<String>,
    val dateText: String? = null,
    val kmText: String? = null,
    val moneyText: String? = null,
)

private fun timelinePresentation(
    entry: TimelineEntry,
    kmSincePreviousTo: Int? = null,
    kmSincePreviousRefuel: Int? = null,
    segmentConsumptionLPer100: Double? = null,
    consumptionUnit: ConsumptionUnit = ConsumptionUnit.L_PER_100,
): TimelinePresentation = when (entry) {
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
            dateText = buildString {
                append(Formatters.date(r.dateEpochMs))
                r.stationName?.takeIf { it.isNotBlank() }?.let { append(" · $it") }
            },
            kmText = "${r.odometerKm} км",
            moneyText = Formatters.money(r.totalCost),
            details = buildList {
                kmSincePreviousRefuel?.let {
                    add("С прошлой заправки · ${TimelineDistance.formatKm(it)} км")
                }
                segmentConsumptionLPer100?.let { lPer100 ->
                    add(
                        "Расход · ${Formatters.consumptionValue(lPer100, consumptionUnit)} " +
                            Formatters.consumptionUnitLabel(consumptionUnit),
                    )
                }
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
            dateText = Formatters.date(e.dateEpochMs),
            kmText = e.odometerKm?.let { "$it км" },
            moneyText = Formatters.money(e.totalCost),
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
            dateText = Formatters.date(s.dateEpochMs),
            kmText = "${s.odometerKm} км",
            moneyText = Formatters.money(s.totalCost),
            details = buildList {
                kmSincePreviousTo?.let {
                    add("С прошлого ТО · ${TimelineDistance.formatKm(it)} км")
                }
                s.description?.takeIf { it.isNotBlank() }?.let { add(it) }
                ServiceChecklistCodec.decode(s.checklistJson).forEach { item ->
                    add(
                        buildString {
                            append(if (item.done) "✓ " else "○ ")
                            append(item.text)
                        },
                    )
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
            dateText = Formatters.date(reminder.nextDueDateEpochMs),
            kmText = kmPart,
            moneyText = daysPart,
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
        "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
        "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь",
    )
    val month = months[cal.get(Calendar.MONTH)]
    val year = cal.get(Calendar.YEAR)
    return "$month $year"
}
