package ru.autolog.app.ui.history

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import ru.autolog.app.data.local.entity.ExpenseEntity
import ru.autolog.app.data.local.entity.RefuellingEntity
import ru.autolog.app.data.local.entity.ServiceEntity
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.domain.FuelType
import ru.autolog.app.domain.ServiceKind

enum class HistoryFilter(val label: String) {
    ALL("Все"),
    REFUEL("Заправки"),
    TO("ТО"),
    REPAIR("Ремонт"),
    PLANNED("Запланированно"),
    WASH("Мойка");

    companion object {
        fun fromArg(raw: String?): HistoryFilter =
            entries.find { it.name.equals(raw, ignoreCase = true) } ?: ALL
    }
}

sealed class HistoryEntry {
    abstract val id: Long
    abstract val dateEpochMs: Long
    abstract val odometerKm: Int?
    abstract val typeLabel: String
    abstract val amount: Double

    data class Refuel(
        override val id: Long,
        override val dateEpochMs: Long,
        override val odometerKm: Int,
        override val amount: Double,
        val liters: Double,
        val fuelLabel: String,
    ) : HistoryEntry() {
        override val typeLabel: String = "Заправка"
    }

    data class Service(
        override val id: Long,
        override val dateEpochMs: Long,
        override val odometerKm: Int,
        override val amount: Double,
        val kind: ServiceKind,
    ) : HistoryEntry() {
        override val typeLabel: String = kind.label
    }

    data class Expense(
        override val id: Long,
        override val dateEpochMs: Long,
        override val odometerKm: Int?,
        override val amount: Double,
        val category: String,
    ) : HistoryEntry() {
        override val typeLabel: String = "Расход"
    }
}

data class HistoryUiState(
    val loading: Boolean = true,
    val vehicleId: Long? = null,
    val vehicleName: String = "",
    val filter: HistoryFilter = HistoryFilter.ALL,
    val entries: List<HistoryEntry> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryViewModel(
    private val repository: AutologRepository,
    initialFilter: HistoryFilter = HistoryFilter.ALL,
) : ViewModel() {

    private val filterFlow = MutableStateFlow(initialFilter)

    private val vehicleFlow = repository.observeActiveVehicle()

    private val rawFlow = vehicleFlow.flatMapLatest { vehicle ->
        if (vehicle == null) {
            flowOf(Triple(emptyList<RefuellingEntity>(), emptyList<ServiceEntity>(), emptyList<ExpenseEntity>()))
        } else {
            combine(
                repository.observeRefuellings(vehicle.id),
                repository.observeServices(vehicle.id),
                repository.observeExpenses(vehicle.id),
            ) { refuels, services, expenses ->
                Triple(refuels, services, expenses)
            }
        }
    }

    val uiState: StateFlow<HistoryUiState> = combine(vehicleFlow, filterFlow, rawFlow) { vehicle, filter, raw ->
        val (refuels, services, expenses) = raw
        val all = buildList {
            refuels.forEach { r ->
                add(
                    HistoryEntry.Refuel(
                        id = r.id,
                        dateEpochMs = r.dateEpochMs,
                        odometerKm = r.odometerKm,
                        amount = r.totalCost,
                        liters = r.volumeLiters,
                        fuelLabel = FuelType.fromStorage(r.fuelType)?.label ?: "топливо",
                    ),
                )
            }
            services.forEach { s ->
                add(
                    HistoryEntry.Service(
                        id = s.id,
                        dateEpochMs = s.dateEpochMs,
                        odometerKm = s.odometerKm,
                        amount = s.totalCost,
                        kind = ServiceKind.fromStorage(s.serviceType),
                    ),
                )
            }
            expenses.forEach { e ->
                add(
                    HistoryEntry.Expense(
                        id = e.id,
                        dateEpochMs = e.dateEpochMs,
                        odometerKm = e.odometerKm,
                        amount = e.totalCost,
                        category = e.category,
                    ),
                )
            }
        }.sortedByDescending { it.dateEpochMs }

        val filtered = when (filter) {
            HistoryFilter.ALL -> all
            HistoryFilter.REFUEL -> all.filterIsInstance<HistoryEntry.Refuel>()
            HistoryFilter.TO -> all.filterIsInstance<HistoryEntry.Service>()
                .filter { it.kind == ServiceKind.TO }
            HistoryFilter.REPAIR -> all.filterIsInstance<HistoryEntry.Service>()
                .filter { it.kind == ServiceKind.REPAIR }
            HistoryFilter.PLANNED -> all.filterIsInstance<HistoryEntry.Service>()
                .filter { it.kind == ServiceKind.PLANNED }
            HistoryFilter.WASH -> all.filterIsInstance<HistoryEntry.Expense>()
                .filter { it.category.contains("мойк", ignoreCase = true) }
        }

        HistoryUiState(
            loading = false,
            vehicleId = vehicle?.id,
            vehicleName = vehicle?.name.orEmpty(),
            filter = filter,
            entries = filtered,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        HistoryUiState(),
    )

    fun setFilter(filter: HistoryFilter) {
        filterFlow.update { filter }
    }
}
