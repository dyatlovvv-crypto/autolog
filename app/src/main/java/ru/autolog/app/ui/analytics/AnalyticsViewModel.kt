package ru.autolog.app.ui.analytics

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.util.Calendar
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
import ru.autolog.app.domain.ServiceChecklistCodec
import ru.autolog.app.domain.ServiceKind

@OptIn(ExperimentalCoroutinesApi::class)
class AnalyticsViewModel(
    private val repository: AutologRepository,
) : ViewModel() {

    private val selectedTab = MutableStateFlow(0)
    private val overallPeriod = MutableStateFlow(AnalyticsPeriod.MONTH)
    private val fuelPeriod = MutableStateFlow(AnalyticsPeriod.MONTH)
    private val toPeriod = MutableStateFlow(AnalyticsPeriod.MONTH)
    private val repairPeriod = MutableStateFlow(AnalyticsPeriod.MONTH)

    private val vehicleFlow = repository.observeActiveVehicle()

    private val rawFlow = vehicleFlow.flatMapLatest { vehicle ->
        if (vehicle == null) {
            flowOf(
                Triple(
                    emptyList<RefuellingEntity>(),
                    emptyList<ServiceEntity>(),
                    emptyList<ExpenseEntity>(),
                ),
            )
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

    private data class Periods(
        val overall: AnalyticsPeriod,
        val fuel: AnalyticsPeriod,
        val to: AnalyticsPeriod,
        val repair: AnalyticsPeriod,
    )

    private val periodsFlow = combine(
        overallPeriod,
        fuelPeriod,
        toPeriod,
        repairPeriod,
    ) { o, f, t, r -> Periods(o, f, t, r) }

    val uiState: StateFlow<AnalyticsUiState> = combine(
        vehicleFlow,
        selectedTab,
        periodsFlow,
        rawFlow,
    ) { vehicle, tab, periods, raw ->
        val (refuels, services, expenses) = raw
        AnalyticsUiState(
            loading = false,
            vehicleId = vehicle?.id,
            vehicleName = vehicle?.name.orEmpty(),
            selectedTab = tab,
            overall = buildOverall(refuels, services, expenses, periods.overall),
            fuel = buildFuel(refuels, periods.fuel),
            to = buildServiceKind(services, ServiceKind.TO, periods.to),
            repair = buildServiceKind(services, ServiceKind.REPAIR, periods.repair),
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AnalyticsUiState(),
    )

    fun selectTab(index: Int) {
        selectedTab.update { index.coerceIn(0, AnalyticsEntitySpec.entries.lastIndex) }
    }

    fun setPeriod(spec: AnalyticsEntitySpec, period: AnalyticsPeriod) {
        when (spec) {
            AnalyticsEntitySpec.OVERALL -> overallPeriod.value = period
            AnalyticsEntitySpec.FUEL -> fuelPeriod.value = period
            AnalyticsEntitySpec.TO -> toPeriod.value = period
            AnalyticsEntitySpec.REPAIR -> repairPeriod.value = period
        }
    }

    companion object {
        fun periodStartMs(period: AnalyticsPeriod, now: Long = System.currentTimeMillis()): Long? {
            if (period == AnalyticsPeriod.ALL) return null
            val cal = Calendar.getInstance().apply { timeInMillis = now }
            when (period) {
                AnalyticsPeriod.WEEK -> cal.add(Calendar.DAY_OF_YEAR, -7)
                AnalyticsPeriod.MONTH -> cal.add(Calendar.MONTH, -1)
                AnalyticsPeriod.YEAR -> cal.add(Calendar.YEAR, -1)
                AnalyticsPeriod.ALL -> Unit
            }
            return cal.timeInMillis
        }

        fun <T> filterByPeriod(
            items: List<T>,
            period: AnalyticsPeriod,
            dateOf: (T) -> Long,
        ): List<T> {
            val start = periodStartMs(period) ?: return items
            return items.filter { dateOf(it) >= start }
        }

        fun buildOverall(
            refuels: List<RefuellingEntity>,
            services: List<ServiceEntity>,
            expenses: List<ExpenseEntity>,
            period: AnalyticsPeriod,
        ): EntityAnalyticsSlice {
            val r = filterByPeriod(refuels, period) { it.dateEpochMs }
            val s = filterByPeriod(services, period) { it.dateEpochMs }
            val e = filterByPeriod(expenses, period) { it.dateEpochMs }

            val points = buildList {
                r.forEach { add(ChartPoint(it.odometerKm.toFloat(), it.totalCost.toFloat(), "Заправка")) }
                s.forEach {
                    add(
                        ChartPoint(
                            it.odometerKm.toFloat(),
                            it.totalCost.toFloat(),
                            ServiceKind.fromStorage(it.serviceType).label,
                        ),
                    )
                }
                e.forEach { exp ->
                    val odo = exp.odometerKm ?: return@forEach
                    val label = if (exp.category.contains("мойк", ignoreCase = true)) "Мойка" else "Расход"
                    add(ChartPoint(odo.toFloat(), exp.totalCost.toFloat(), label))
                }
            }.sortedBy { it.xOdometer }

            val wash = e.filter { it.category.contains("мойк", ignoreCase = true) }.sumOf { it.totalCost }
            val buckets = linkedMapOf(
                "Заправки" to r.sumOf { it.totalCost },
                "ТО" to s.filter { ServiceKind.fromStorage(it.serviceType) == ServiceKind.TO }.sumOf { it.totalCost },
                "Ремонт" to s.filter { ServiceKind.fromStorage(it.serviceType) == ServiceKind.REPAIR }
                    .sumOf { it.totalCost },
                "Мойка" to wash,
            )
            val total = buckets.values.sum()
            val donut = toDonut(buckets)

            return EntityAnalyticsSlice(
                period = period,
                linePoints = points,
                donut = donut,
                totalAmount = total,
                yAxisUnit = "₽",
                emptyHint = "Нет расходов за период",
            )
        }

        fun buildFuel(
            refuels: List<RefuellingEntity>,
            period: AnalyticsPeriod,
        ): EntityAnalyticsSlice {
            val filtered = filterByPeriod(refuels, period) { it.dateEpochMs }
            val points = filtered
                .mapNotNull { r ->
                    val y = r.consumptionLPer100Km ?: return@mapNotNull null
                    ChartPoint(r.odometerKm.toFloat(), y.toFloat(), "${r.odometerKm} км")
                }
                .sortedBy { it.xOdometer }

            return EntityAnalyticsSlice(
                period = period,
                linePoints = points,
                donut = emptyList(),
                totalAmount = filtered.sumOf { it.totalCost },
                yAxisUnit = "л/100",
                emptyHint = "Нужны заправки «до полного» с расходом",
            )
        }

        fun buildServiceKind(
            services: List<ServiceEntity>,
            kind: ServiceKind,
            period: AnalyticsPeriod,
        ): EntityAnalyticsSlice {
            val filtered = filterByPeriod(services, period) { it.dateEpochMs }
                .filter { ServiceKind.fromStorage(it.serviceType) == kind }
            val points = filtered
                .map { ChartPoint(it.odometerKm.toFloat(), it.totalCost.toFloat(), kind.label) }
                .sortedBy { it.xOdometer }

            return EntityAnalyticsSlice(
                period = period,
                linePoints = points,
                donut = emptyList(),
                totalAmount = filtered.sumOf { it.totalCost },
                yAxisUnit = "₽",
                emptyHint = "Нет записей ${kind.label} за период",
                workStats = buildWorkStats(filtered),
            )
        }

        fun buildWorkStats(services: List<ServiceEntity>): List<WorkStat> {
            data class Acc(var label: String, var count: Int)
            val byKey = linkedMapOf<String, Acc>()
            services.forEach { service ->
                ServiceChecklistCodec.decode(service.checklistJson).forEach { item ->
                    val key = item.text.trim().lowercase()
                    if (key.isEmpty()) return@forEach
                    val acc = byKey.getOrPut(key) { Acc(item.text.trim(), 0) }
                    acc.count += 1
                    // Prefer longer / more complete casing as display label
                    if (item.text.trim().length > acc.label.length) {
                        acc.label = item.text.trim()
                    }
                }
            }
            if (byKey.isEmpty()) return emptyList()
            val max = byKey.values.maxOf { it.count }.coerceAtLeast(1)
            return byKey.values
                .sortedWith(compareByDescending<Acc> { it.count }.thenBy { it.label.lowercase() })
                .map { WorkStat(label = it.label, count = it.count, relative = it.count.toFloat() / max) }
        }

        private fun toDonut(buckets: Map<String, Double>): List<DonutSlice> {
            val positive = buckets.filter { it.value > 0.0 }
            val total = positive.values.sum()
            if (total <= 0.0) return emptyList()
            return positive.entries
                .sortedByDescending { it.value }
                .map { (label, value) ->
                    DonutSlice(
                        label = label,
                        value = value,
                        fraction = (value / total).toFloat(),
                    )
                }
        }
    }
}
