package ru.autolog.app.ui.history

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.autolog.app.AutologApp
import ru.autolog.app.ui.components.ActivityFeedCard
import ru.autolog.app.ui.components.ActivityKind
import ru.autolog.app.ui.components.expenseActivityKind
import ru.autolog.app.ui.components.toActivityKind
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.util.Formatters

object HistoryTags {
    const val ROOT = "history_root"
    const val FILTER_ALL = "history_filter_ALL"
    const val FILTER_REFUEL = "history_filter_REFUEL"
    const val FILTER_TO = "history_filter_TO"
    const val FILTER_REPAIR = "history_filter_REPAIR"
    const val FILTER_PLANNED = "history_filter_PLANNED"
    const val FILTER_WASH = "history_filter_WASH"
    fun item(entry: HistoryEntry): String = when (entry) {
        is HistoryEntry.Refuel -> "history_refuel_${entry.id}"
        is HistoryEntry.Service -> "history_service_${entry.id}"
        is HistoryEntry.Expense -> "history_expense_${entry.id}"
    }
    fun filterTag(filter: HistoryFilter): String = when (filter) {
        HistoryFilter.ALL -> FILTER_ALL
        HistoryFilter.REFUEL -> FILTER_REFUEL
        HistoryFilter.TO -> FILTER_TO
        HistoryFilter.REPAIR -> FILTER_REPAIR
        HistoryFilter.PLANNED -> FILTER_PLANNED
        HistoryFilter.WASH -> FILTER_WASH
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(
    onBack: () -> Unit,
    onOpenRefuel: (Long) -> Unit,
    onOpenService: (Long) -> Unit,
    onOpenExpense: (Long) -> Unit,
    initialFilter: HistoryFilter = HistoryFilter.ALL,
    /** When opened from wrench hub, hide fuel/all chips. */
    serviceHubMode: Boolean = false,
) {
    val vm: HistoryViewModel = viewModel(
        key = "history-${initialFilter.name}-$serviceHubMode",
        factory = HistoryVmFactory(initialFilter),
    )
    val state by vm.uiState.collectAsStateWithLifecycle()
    val chips = if (serviceHubMode) {
        listOf(
            HistoryFilter.TO,
            HistoryFilter.REPAIR,
            HistoryFilter.PLANNED,
            HistoryFilter.WASH,
        )
    } else {
        HistoryFilter.entries.toList()
    }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(HistoryTags.ROOT),
        topBar = {
            GlassTopAppBar(
                title = {
                    Text(
                        if (state.vehicleName.isNotBlank()) {
                            "История · ${state.vehicleName}"
                        } else {
                            "История"
                        },
                    )
                },
                navigationIcon = { IconButton(onClick = onBack) { Text("←") } },
            )
        },
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(horizontal = 16.dp),
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(vertical = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                chips.forEach { filter ->
                    FilterChip(
                        selected = state.filter == filter,
                        onClick = { vm.setFilter(filter) },
                        label = { Text(filter.label) },
                        modifier = Modifier.testTag(HistoryTags.filterTag(filter)),
                    )
                }
            }

            when {
                state.loading -> Text("Загрузка…", modifier = Modifier.padding(16.dp))
                state.vehicleId == null -> Text(
                    "Сначала выберите автомобиль",
                    modifier = Modifier.padding(16.dp),
                )
                state.entries.isEmpty() -> Text(
                    "Пока пусто",
                    style = MaterialTheme.typography.bodyLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(16.dp),
                )
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .rubberBandOverscroll()
                            .verticalScroll(rememberScrollState()),
                        verticalArrangement = Arrangement.spacedBy(10.dp),
                    ) {
                        state.entries.forEach { entry ->
                            val (kind, title, subtitle) = when (entry) {
                                is HistoryEntry.Refuel -> Triple(
                                    ActivityKind.REFUEL,
                                    "${entry.typeLabel} · ${Formatters.money(entry.amount)}",
                                    buildString {
                                        append(Formatters.date(entry.dateEpochMs))
                                        append(" · ")
                                        append("${entry.odometerKm} км")
                                        append(" · ")
                                        append("${entry.liters} л")
                                        append(" · ")
                                        append(entry.fuelLabel)
                                    },
                                )
                                is HistoryEntry.Service -> Triple(
                                    entry.kind.toActivityKind(),
                                    "${entry.typeLabel} · ${Formatters.money(entry.amount)}",
                                    "${Formatters.date(entry.dateEpochMs)} · ${entry.odometerKm} км",
                                )
                                is HistoryEntry.Expense -> Triple(
                                    expenseActivityKind(entry.category),
                                    "${entry.category} · ${Formatters.money(entry.amount)}",
                                    buildString {
                                        append(Formatters.date(entry.dateEpochMs))
                                        entry.odometerKm?.let { append(" · $it км") }
                                    },
                                )
                            }
                            ActivityFeedCard(
                                kind = kind,
                                title = title,
                                subtitle = subtitle,
                                modifier = Modifier.testTag(HistoryTags.item(entry)),
                                onClick = {
                                    when (entry) {
                                        is HistoryEntry.Refuel -> onOpenRefuel(entry.id)
                                        is HistoryEntry.Service -> onOpenService(entry.id)
                                        is HistoryEntry.Expense -> onOpenExpense(entry.id)
                                    }
                                },
                            )
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

private class HistoryVmFactory(
    private val initialFilter: HistoryFilter,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return HistoryViewModel(
            AutologApp.instance.container.repository,
            initialFilter,
        ) as T
    }
}
