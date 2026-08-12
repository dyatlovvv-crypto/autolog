package ru.autolog.app.ui.analytics

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
import androidx.compose.material3.ScrollableTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.autolog.app.AutologApp
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeLarge
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.theme.AutologColors
import ru.autolog.app.ui.util.Formatters

object AnalyticsTags {
    const val ROOT = "analytics_root"
    fun tab(spec: AnalyticsEntitySpec) = "analytics_tab_${spec.name}"
    fun period(spec: AnalyticsEntitySpec, period: AnalyticsPeriod) =
        "analytics_period_${spec.name}_${period.name}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnalyticsScreen(
    onBack: () -> Unit,
    viewModel: AnalyticsViewModel = viewModel(factory = AnalyticsVmFactory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val specs = AnalyticsEntitySpec.entries
    val selected = state.selectedTab.coerceIn(0, specs.lastIndex)
    val activeSpec = specs[selected]
    val slice = when (activeSpec) {
        AnalyticsEntitySpec.OVERALL -> state.overall
        AnalyticsEntitySpec.FUEL -> state.fuel
        AnalyticsEntitySpec.TO -> state.to
        AnalyticsEntitySpec.REPAIR -> state.repair
    }
    val lineColor = when (activeSpec) {
        AnalyticsEntitySpec.OVERALL -> AutologColors.Expense
        AnalyticsEntitySpec.FUEL -> AutologColors.Fuel
        AnalyticsEntitySpec.TO -> AutologColors.Service
        AnalyticsEntitySpec.REPAIR -> AutologColors.PrimaryDark
    }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(AnalyticsTags.ROOT),
        topBar = {
            GlassTopAppBar(
                title = {
                    Text(
                        if (state.vehicleName.isNotBlank()) {
                            "Аналитика · ${state.vehicleName}"
                        } else {
                            "Аналитика"
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
                .padding(padding),
        ) {
            ScrollableTabRow(
                selectedTabIndex = selected,
                edgePadding = 12.dp,
                containerColor = Color.Transparent,
                divider = {},
            ) {
                specs.forEachIndexed { index, spec ->
                    Tab(
                        selected = selected == index,
                        onClick = { viewModel.selectTab(index) },
                        text = {
                            Text(
                                text = when (spec) {
                                    AnalyticsEntitySpec.OVERALL -> "Общие"
                                    AnalyticsEntitySpec.FUEL -> "Заправки"
                                    AnalyticsEntitySpec.TO -> "ТО"
                                    AnalyticsEntitySpec.REPAIR -> "Ремонты"
                                },
                            )
                        },
                        modifier = Modifier.testTag(AnalyticsTags.tab(spec)),
                    )
                }
            }

            when {
                state.loading -> Text("Загрузка…", modifier = Modifier.padding(16.dp))
                state.vehicleId == null -> Text(
                    "Сначала выберите автомобиль",
                    modifier = Modifier.padding(16.dp),
                )
                else -> {
                    Column(
                        modifier = Modifier
                            .fillMaxSize()
                            .rubberBandOverscroll()
                            .verticalScroll(rememberScrollState())
                            .padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Text(activeSpec.label, style = MaterialTheme.typography.titleMedium)

                        PeriodChips(
                            spec = activeSpec,
                            selected = slice.period,
                            onSelect = { viewModel.setPeriod(activeSpec, it) },
                        )

                        GlassPanel(
                            modifier = Modifier.fillMaxWidth(),
                            shape = GlassShapeLarge,
                            elevation = 8.dp,
                        ) {
                            Column(
                                modifier = Modifier.padding(16.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                            ) {
                                Text(
                                    "График · ось X: пробег",
                                    style = MaterialTheme.typography.titleSmall,
                                )
                                Text(
                                    "Ось Y: ${
                                        when (activeSpec) {
                                            AnalyticsEntitySpec.FUEL -> "расход топлива (л/100 км)"
                                            AnalyticsEntitySpec.TO -> "стоимость обслуживания"
                                            AnalyticsEntitySpec.REPAIR -> "стоимость ремонта"
                                            AnalyticsEntitySpec.OVERALL -> "сумма расходов"
                                        }
                                    }",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                )
                                if (slice.linePoints.isEmpty()) {
                                    Text(
                                        slice.emptyHint,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(vertical = 24.dp),
                                    )
                                } else {
                                    AnalyticsLineChart(
                                        points = slice.linePoints,
                                        yUnit = slice.yAxisUnit,
                                        lineColor = lineColor,
                                    )
                                }
                            }
                        }

                        if (activeSpec == AnalyticsEntitySpec.OVERALL) {
                            GlassPanel(
                                modifier = Modifier.fillMaxWidth(),
                                shape = GlassShapeLarge,
                                elevation = 8.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Доли за период · ${Formatters.money(slice.totalAmount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    AnalyticsDonutChart(slices = slice.donut)
                                }
                            }
                        }

                        if (activeSpec == AnalyticsEntitySpec.TO ||
                            activeSpec == AnalyticsEntitySpec.REPAIR
                        ) {
                            GlassPanel(
                                modifier = Modifier.fillMaxWidth(),
                                shape = GlassShapeLarge,
                                elevation = 8.dp,
                            ) {
                                Column(
                                    modifier = Modifier.padding(16.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp),
                                ) {
                                    Text(
                                        "Что меняли · ${Formatters.money(slice.totalAmount)}",
                                        style = MaterialTheme.typography.titleSmall,
                                    )
                                    Text(
                                        "Список работ из записей за период",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                    AnalyticsWorksList(
                                        works = slice.workStats,
                                        accent = lineColor,
                                    )
                                }
                            }
                        }
                        Spacer(modifier = Modifier.height(24.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun PeriodChips(
    spec: AnalyticsEntitySpec,
    selected: AnalyticsPeriod,
    onSelect: (AnalyticsPeriod) -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .horizontalScroll(rememberScrollState()),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        AnalyticsPeriod.entries.forEach { period ->
            FilterChip(
                selected = selected == period,
                onClick = { onSelect(period) },
                label = { Text(period.label) },
                modifier = Modifier.testTag(AnalyticsTags.period(spec, period)),
            )
        }
    }
}

private class AnalyticsVmFactory : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        return AnalyticsViewModel(AutologApp.instance.container.repository) as T
    }
}
