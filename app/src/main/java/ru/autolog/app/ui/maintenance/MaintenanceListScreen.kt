package ru.autolog.app.ui.maintenance

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.domain.MaintenanceKind
import ru.autolog.app.domain.MaintenanceSlot
import ru.autolog.app.ui.AutologViewModelFactory
import ru.autolog.app.ui.dashboard.DashboardViewModel
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeMedium
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.theme.AutologSpace
import ru.autolog.app.data.repository.DashboardSnapshot

object MaintenanceListTags {
    const val SCREEN = "maintenance_list_screen"
    const val BACK = "maintenance_list_back"
    fun row(kind: MaintenanceKind) = "maintenance_row_${kind.name}"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MaintenanceListScreen(
    onBack: () -> Unit,
    onOpenReminder: (Long) -> Unit,
    onAddReminderForKind: (MaintenanceKind) -> Unit,
    viewModel: DashboardViewModel = viewModel(factory = AutologViewModelFactory()),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val dash = state.dashboard as? DashboardSnapshot.Data
    val reminders = dash?.activeReminders.orEmpty()
    val slots = remember(reminders) { allMaintenanceSlots(reminders) }

    GlassScaffold(
        modifier = Modifier
            .fillMaxSize()
            .testTag(MaintenanceListTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text("Плановое ТО") },
                navigationIcon = {
                    IconButton(
                        onClick = onBack,
                        modifier = Modifier.testTag(MaintenanceListTags.BACK),
                    ) { Text("←") }
                },
            )
        },
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .rubberBandOverscroll(),
            contentPadding = PaddingValues(
                horizontal = AutologSpace.md,
                vertical = AutologSpace.sm,
            ),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            item {
                Text(
                    "Заполнять не обязательно — только то, что нужно.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = AutologSpace.xs),
                )
            }
            items(slots, key = { it.kind.name }) { slot ->
                MaintenanceRow(
                    slot = slot,
                    onClick = {
                        val reminder = slot.reminder
                        if (reminder != null) onOpenReminder(reminder.id)
                        else onAddReminderForKind(slot.kind)
                    },
                )
            }
        }
    }
}

@Composable
private fun MaintenanceRow(
    slot: MaintenanceSlot,
    onClick: () -> Unit,
) {
    val reminder = slot.reminder
    GlassPanel(
        modifier = Modifier
            .fillMaxWidth()
            .testTag(MaintenanceListTags.row(slot.kind))
            .clickable(onClick = onClick),
        shape = GlassShapeMedium,
        elevation = 6.dp,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = 12.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    slot.kind.title,
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    when {
                        reminder == null -> "не задано"
                        reminder.remainingKm != null && reminder.remainingDays != null ->
                            "осталось ${reminder.remainingKm} км · ${reminder.remainingDays} дн."
                        reminder.remainingKm != null ->
                            "осталось ${reminder.remainingKm} км"
                        reminder.remainingDays != null ->
                            "осталось ${reminder.remainingDays} дн."
                        else -> "нет расчёта"
                    },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
            Text(
                if (reminder == null) "Добавить" else "Открыть",
                style = MaterialTheme.typography.labelLarge,
                color = MaterialTheme.colorScheme.primary,
            )
        }
    }
}

private fun allMaintenanceSlots(reminders: List<ReminderEntity>): List<MaintenanceSlot> {
    return MaintenanceKind.entries.map { kind ->
        val match = reminders
            .filter { kind.matches(it.title) }
            .minByOrNull { it.remainingKm ?: Int.MAX_VALUE }
        MaintenanceSlot(kind, match)
    }
}
