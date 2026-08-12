package ru.autolog.app.ui.analytics

enum class AnalyticsPeriod(val label: String) {
    WEEK("Неделя"),
    MONTH("Месяц"),
    YEAR("Год"),
    ALL("Все время"),
}

enum class AnalyticsEntitySpec(val label: String) {
    OVERALL("Общие расходы"),
    FUEL("Заправки и Расход"),
    TO("Техническое обслуживание (ТО)"),
    REPAIR("Ремонты"),
}

data class ChartPoint(
    val xOdometer: Float,
    val yValue: Float,
    val label: String = "",
)

data class DonutSlice(
    val label: String,
    val value: Double,
    val fraction: Float,
)

data class EntityAnalyticsSlice(
    val period: AnalyticsPeriod,
    val linePoints: List<ChartPoint>,
    val donut: List<DonutSlice>,
    val totalAmount: Double,
    val yAxisUnit: String,
    val emptyHint: String,
    /** Aggregated checklist works for TO / Repair tabs. */
    val workStats: List<WorkStat> = emptyList(),
)

data class WorkStat(
    val label: String,
    val count: Int,
    /** 0..1 relative to the most frequent work in the period. */
    val relative: Float,
)

data class AnalyticsUiState(
    val loading: Boolean = true,
    val vehicleId: Long? = null,
    val vehicleName: String = "",
    val selectedTab: Int = 0,
    val overall: EntityAnalyticsSlice = emptySlice(AnalyticsPeriod.MONTH, "₽"),
    val fuel: EntityAnalyticsSlice = emptySlice(AnalyticsPeriod.MONTH, "л/100"),
    val to: EntityAnalyticsSlice = emptySlice(AnalyticsPeriod.MONTH, "₽"),
    val repair: EntityAnalyticsSlice = emptySlice(AnalyticsPeriod.MONTH, "₽"),
)

fun emptySlice(period: AnalyticsPeriod, unit: String) = EntityAnalyticsSlice(
    period = period,
    linePoints = emptyList(),
    donut = emptyList(),
    totalAmount = 0.0,
    yAxisUnit = unit,
    emptyHint = "Нет данных за период",
    workStats = emptyList(),
)
