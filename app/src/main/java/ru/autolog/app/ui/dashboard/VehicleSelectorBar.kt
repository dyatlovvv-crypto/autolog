package ru.autolog.app.ui.dashboard

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DirectionsCar
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import ru.autolog.app.data.local.entity.VehicleEntity
import ru.autolog.app.ui.components.AccentIcon
import ru.autolog.app.ui.fluid.bouncePressable
import ru.autolog.app.ui.fluid.liquidAppear
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.theme.AutologElevation
import ru.autolog.app.ui.theme.AutologRadii
import ru.autolog.app.ui.theme.AutologSpace

object VehicleSelectorTags {
    const val TRIGGER = "vehicle_selector_trigger"
    const val SHEET = "vehicle_selector_sheet"
    const val ADD_VEHICLE = "vehicle_selector_add"
    fun item(id: Long) = "vehicle_selector_item_$id"
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
    modifier: Modifier = Modifier,
    odometerLine: String? = null,
    odometerKm: Int? = null,
    tankLabel: String? = null,
) {
    val current = vehicles.firstOrNull { it.id == currentVehicleId } ?: vehicles.firstOrNull()

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
        shape = RoundedCornerShape(AutologRadii.card),
        tonalElevation = AutologElevation.card,
        shadowElevation = 0.dp,
    ) {
        Row(
            modifier = Modifier
                .testTag(VehicleSelectorTags.TRIGGER)
                .bouncePressable(enabled = vehicles.isNotEmpty()) {
                    onExpandChange(true)
                }
                .padding(AutologSpace.md),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AutologSpace.sm),
        ) {
            AccentIcon(
                imageVector = Icons.Default.DirectionsCar,
                contentDescription = null,
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = "Autolog",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = current?.name ?: "Нет автомобиля",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    maxLines = 1,
                    modifier = Modifier
                        .padding(top = AutologSpace.xs)
                        .testTag(DashboardTags.TITLE),
                )
                val plate = current?.plate
                if (odometerKm != null) {
                    Row(
                        modifier = Modifier.padding(top = AutologSpace.xs),
                        verticalAlignment = Alignment.Bottom,
                        horizontalArrangement = Arrangement.spacedBy(AutologSpace.xs),
                    ) {
                        Text(
                            text = odometerKm.toString(),
                            style = MaterialTheme.typography.titleLarge.copy(fontWeight = FontWeight.Bold),
                            color = MaterialTheme.colorScheme.primary,
                        )
                        Text(
                            text = "км",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = AutologSpace.xs),
                        )
                        tankLabel?.let {
                            Text(
                                text = "бак $it",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = AutologSpace.xs),
                            )
                        }
                        plate?.takeIf { it.isNotBlank() }?.let {
                            Text(
                                text = it,
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = AutologSpace.xs),
                            )
                        }
                    }
                } else if (!odometerLine.isNullOrBlank() || !plate.isNullOrBlank()) {
                    Text(
                        text = buildString {
                            if (!odometerLine.isNullOrBlank()) append(odometerLine)
                            if (!plate.isNullOrBlank()) {
                                if (isNotEmpty()) append(" · ")
                                append(plate)
                            }
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(top = AutologSpace.xs),
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

    if (expanded) {
        ModalBottomSheet(
            onDismissRequest = { onExpandChange(false) },
            sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
            containerColor = MaterialTheme.colorScheme.surface,
        ) {
            Column(
                modifier = Modifier
                    .testTag(VehicleSelectorTags.SHEET)
                    .liquidAppear(visible = true)
                    .padding(bottom = AutologSpace.sm),
            ) {
                Text(
                    text = "Автомобили",
                    style = MaterialTheme.typography.titleLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = AutologSpace.lg, vertical = AutologSpace.sm),
                )
                LazyColumn(modifier = Modifier.rubberBandOverscroll()) {
                    items(vehicles, key = { it.id }) { vehicle ->
                        val selected = vehicle.id == currentVehicleId
                        ListItem(
                            headlineContent = {
                                Text(
                                    vehicle.name,
                                    style = MaterialTheme.typography.bodyMedium,
                                )
                            },
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
                                    style = MaterialTheme.typography.labelSmall,
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
                                if (selected) {
                                    Icon(
                                        imageVector = Icons.Default.Check,
                                        contentDescription = "Выбрано",
                                        tint = MaterialTheme.colorScheme.primary,
                                    )
                                }
                            },
                            colors = ListItemDefaults.colors(
                                containerColor = MaterialTheme.colorScheme.surface,
                            ),
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = AutologSpace.md, vertical = AutologSpace.xs)
                                .testTag(VehicleSelectorTags.item(vehicle.id))
                                .bouncePressable {
                                    onSelectVehicle(vehicle.id)
                                    onExpandChange(false)
                                },
                        )
                    }
                    item {
                        HorizontalDivider(modifier = Modifier.padding(vertical = AutologSpace.sm))
                        if (canAddVehicle) {
                            ListItem(
                                headlineContent = {
                                    Text(
                                        "+ Добавить автомобиль",
                                        style = MaterialTheme.typography.bodyMedium,
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
                                colors = ListItemDefaults.colors(
                                    containerColor = MaterialTheme.colorScheme.surface,
                                ),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = AutologSpace.md)
                                    .testTag(VehicleSelectorTags.ADD_VEHICLE)
                                    .bouncePressable {
                                        onExpandChange(false)
                                        onAddVehicle()
                                    },
                            )
                        }
                        Spacer(modifier = Modifier.height(AutologSpace.xl))
                    }
                }
            }
        }
    }
}
