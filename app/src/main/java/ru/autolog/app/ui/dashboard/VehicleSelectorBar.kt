package ru.autolog.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import ru.autolog.app.data.local.entity.VehicleEntity
import ru.autolog.app.ui.fluid.bouncePressable
import ru.autolog.app.ui.fluid.liquidAppear
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassShapePill
import ru.autolog.app.ui.glass.liquidGlassPanel
import ru.autolog.app.ui.theme.LocalGlassTokens

object VehicleSelectorTags {
    const val TRIGGER = "vehicle_selector_trigger"
    const val SHEET = "vehicle_selector_sheet"
    const val ADD_VEHICLE = "vehicle_selector_add"
    fun item(id: Long) = "vehicle_selector_item_$id"
    fun delete(id: Long) = "vehicle_selector_delete_$id"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VehicleSelectorBar(
    vehicles: List<VehicleEntity>,
    currentVehicleId: Long?,
    expanded: Boolean,
    canAddVehicle: Boolean,
    onExpandChange: (Boolean) -> Unit,
    onSelectVehicle: (Long) -> Unit,
    onAddVehicle: () -> Unit,
    onDeleteVehicle: (Long) -> Unit = {},
    modifier: Modifier = Modifier,
) {
    val current = vehicles.firstOrNull { it.id == currentVehicleId } ?: vehicles.firstOrNull()
    val tokens = LocalGlassTokens.current
    var pendingDeleteId by remember { mutableStateOf<Long?>(null) }

    Column(modifier = modifier) {
        Text(
            text = "Autolog",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.testTag(DashboardTags.TITLE),
        )
        Spacer(modifier = Modifier.height(4.dp))
        Box(
            modifier = Modifier
                .testTag(VehicleSelectorTags.TRIGGER)
                .liquidGlassPanel(shape = GlassShapePill, elevation = 4.dp)
                .bouncePressable(enabled = vehicles.isNotEmpty()) {
                    onExpandChange(true)
                },
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    imageVector = Icons.Default.DirectionsCar,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(20.dp),
                )
                Column(modifier = Modifier.weight(1f, fill = false)) {
                    Text(
                        text = current?.name ?: "Нет автомобиля",
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                    )
                    val plate = current?.plate
                    if (!plate.isNullOrBlank()) {
                        Text(
                            text = plate,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
                Icon(
                    imageVector = Icons.Default.KeyboardArrowDown,
                    contentDescription = "Выбрать автомобиль",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandChange(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = tokens.fill,
            scrimColor = tokens.scrim.copy(alpha = 0.45f),
        ) {
            Column(
                modifier = Modifier
                    .testTag(VehicleSelectorTags.SHEET)
                    .liquidAppear(visible = true)
                    .padding(bottom = 8.dp),
            ) {
                Text(
                    text = "Автомобили",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
                LazyColumn(modifier = Modifier.rubberBandOverscroll()) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        val selected = vehicle.id == currentVehicleId
                        ListItem(
                            headlineContent = { Text(vehicle.name) },
                            supportingContent = {
                                val plate = vehicle.plate?.takeIf { it.isNotBlank() }
                                Text(
                                    buildString {
                                        append("${vehicle.currentOdometerKm} км")
                                        if (plate != null) {
                                            append(" · ")
                                            append(plate)
                                        }
                                    },
                                )
                            },
                            leadingContent = {
                                Icon(
                                    imageVector = Icons.Default.DirectionsCar,
                                    contentDescription = null,
                                    tint = if (selected) {
                                        MaterialTheme.colorScheme.primary
                                    } else {
                                        MaterialTheme.colorScheme.onSurfaceVariant
                                    },
                                )
                            },
                            trailingContent = {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    if (selected) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Выбрано",
                                            tint = MaterialTheme.colorScheme.primary,
                                        )
                                    }
                                    IconButton(
                                        onClick = { pendingDeleteId = vehicle.id },
                                        modifier = Modifier.testTag(VehicleSelectorTags.delete(vehicle.id)),
                                    ) {
                                        Icon(
                                            imageVector = Icons.Default.Delete,
                                            contentDescription = "Удалить автомобиль",
                                            tint = MaterialTheme.colorScheme.error,
                                        )
                                    }
                                }
                            },
                            colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 2.dp)
                                .liquidGlassPanel(elevation = 3.dp)
                                .testTag(VehicleSelectorTags.item(vehicle.id))
                                .bouncePressable {
                                    onSelectVehicle(vehicle.id)
                                    onExpandChange(false)
                                },
                        )
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
                        if (canAddVehicle) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "+ Добавить автомобиль",
                                        color = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                leadingContent = {
                                    Icon(
                                        Icons.Default.Add,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                },
                                colors = ListItemDefaults.colors(containerColor = Color.Transparent),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp)
                                    .liquidGlassPanel(elevation = 3.dp)
                                    .testTag(VehicleSelectorTags.ADD_VEHICLE)
                                    .bouncePressable {
                                        onExpandChange(false)
                                        onAddVehicle()
                                    },
                            )
                        }
                        Spacer(modifier = Modifier.height(28.dp))
                    }
                }
            }
        }
    }

    pendingDeleteId?.let { id ->
        val name = vehicles.firstOrNull { it.id == id }?.name ?: "автомобиль"
        AlertDialog(
            onDismissRequest = { pendingDeleteId = null },
            title = { Text("Удалить автомобиль?") },
            text = { Text("«$name» и все связанные записи будут удалены.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteVehicle(id)
                        pendingDeleteId = null
                        onExpandChange(false)
                    },
                    modifier = Modifier.testTag("vehicle_delete_confirm"),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { pendingDeleteId = null }) { Text("Отмена") }
            },
        )
    }
}
