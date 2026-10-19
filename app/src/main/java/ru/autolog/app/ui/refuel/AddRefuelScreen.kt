package ru.autolog.app.ui.refuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewmodel.CreationExtras
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.collectLatest
import ru.autolog.app.AutologApp
import ru.autolog.app.domain.FuelType
import ru.autolog.app.ui.fluid.FluidCheckboxRow
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeLarge
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.glass.glassFieldColors
import ru.autolog.app.ui.map.StationMiniMap

object AddRefuelTags {
    const val SCREEN = "add_refuel_screen"
    const val SAVE = "add_refuel_save"
    const val ODOMETER = "add_refuel_odometer"
    const val LITERS = "add_refuel_liters"
    const val PRICE = "add_refuel_price"
    const val STATION = "add_refuel_station"
    const val TANK_FULL = "add_refuel_tank_full"
    const val OPEN_MAP = "add_refuel_open_map"
    const val CLEAR_MAP = "add_refuel_clear_map"
    const val FUEL_TYPE = "add_refuel_fuel_type"
    const val DELETE = "add_refuel_delete"
    const val LOW_ODO_CONFIRM = "add_refuel_low_odo_confirm"
    const val LOW_ODO_CANCEL = "add_refuel_low_odo_cancel"
}

private val WarnOrange = Color(0xFFE65100)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRefuelScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onPickStationOnMap: () -> Unit,
    onDeleted: (() -> Unit)? = null,
    editRefuelId: Long? = null,
    backStackEntry: NavBackStackEntry? = null,
) {
    val viewModel: AddRefuelViewModel = viewModel(
        key = "refuel-${editRefuelId ?: "new"}",
        factory = AddRefuelVmFactory(editRefuelId),
    )
    val state by viewModel.form.collectAsStateWithLifecycle()
    viewModel.vehicleContext.collectAsStateWithLifecycle()
    var fuelMenuOpen by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        // Prefetch АЗС while the user fills liters/price — map should open with markers ready.
        AutologApp.instance.container.fuelStations.prefetchAsync()
    }

    LaunchedEffect(backStackEntry) {
        val handle = backStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>("picked_station", null).collectLatest { name ->
            if (name != null) {
                val lat = handle.get<Double>("picked_station_lat")
                val lon = handle.get<Double>("picked_station_lon")
                if (lat != null && lon != null && lat.isFinite() && lon.isFinite()) {
                    viewModel.onStationPicked(name, lat, lon)
                } else {
                    viewModel.onStation(name)
                }
                handle["picked_station"] = null
                handle.remove<Double>("picked_station_lat")
                handle.remove<Double>("picked_station_lon")
            }
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    val editing = state.editId != null
    val odoWarn = state.odometer.toIntOrNull()?.let { it < state.currentOdometer } == true

    GlassScaffold(
        modifier = Modifier.testTag(AddRefuelTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text(if (editing) "Заправка" else "Заправка") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
                actions = {
                    if (editing && onDeleted != null) {
                        TextButton(
                            onClick = { confirmDelete = true },
                            modifier = Modifier.testTag(AddRefuelTags.DELETE),
                        ) {
                            Text("Удалить", color = MaterialTheme.colorScheme.error)
                        }
                    }
                },
            )
        },
    ) { padding ->
        if (!state.loaded) {
            Text("Загрузка…", modifier = Modifier.padding(padding).padding(16.dp))
        } else {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(16.dp)
                    .rubberBandOverscroll()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                GlassPanel(
                    modifier = Modifier.fillMaxWidth(),
                    shape = GlassShapeLarge,
                    elevation = 10.dp,
                ) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        if (state.vehicleName.isNotEmpty()) {
                            Text("Авто: ${state.vehicleName}", style = MaterialTheme.typography.titleMedium)
                            Text("Текущий пробег: ${state.currentOdometer} км")
                        }
                        if (editing) {
                            Text("Исправить запись", style = MaterialTheme.typography.titleMedium)
                        }
                        OutlinedTextField(
                            value = state.odometer,
                            onValueChange = viewModel::onOdometer,
                            label = { Text("Пробег, км") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddRefuelTags.ODOMETER),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                            isError = odoWarn,
                        )
                        if (odoWarn) {
                            Text(
                                "Введённый пробег меньше текущего",
                                color = WarnOrange,
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        ExposedDropdownMenuBox(
                            expanded = fuelMenuOpen,
                            onExpandedChange = { fuelMenuOpen = it },
                        ) {
                            OutlinedTextField(
                                value = state.fuelType.label,
                                onValueChange = {},
                                readOnly = true,
                                label = { Text("Тип топлива") },
                                trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(fuelMenuOpen) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .menuAnchor()
                                    .testTag(AddRefuelTags.FUEL_TYPE),
                                shape = GlassShapeLarge,
                                colors = glassFieldColors(),
                            )
                            ExposedDropdownMenu(
                                expanded = fuelMenuOpen,
                                onDismissRequest = { fuelMenuOpen = false },
                            ) {
                                FuelType.entries.forEach { type ->
                                    DropdownMenuItem(
                                        text = { Text(type.label) },
                                        onClick = {
                                            viewModel.onFuelType(type)
                                            fuelMenuOpen = false
                                        },
                                        modifier = Modifier.testTag("fuel_type_${type.name}"),
                                    )
                                }
                            }
                        }
                        OutlinedTextField(
                            value = state.liters,
                            onValueChange = viewModel::onLiters,
                            label = { Text("Литры") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddRefuelTags.LITERS),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                            isError = state.error?.contains("литр", ignoreCase = true) == true,
                        )
                        FluidCheckboxRow(
                            label = "До полного",
                            checked = state.isTankFull,
                            onCheckedChange = viewModel::onTankFull,
                            modifier = Modifier.testTag(AddRefuelTags.TANK_FULL),
                        )
                        OutlinedTextField(
                            value = state.price,
                            onValueChange = viewModel::onPrice,
                            label = { Text("Цена за литр (необязательно)") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddRefuelTags.PRICE),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                        OutlinedTextField(
                            value = state.station,
                            onValueChange = viewModel::onStation,
                            label = { Text("АЗС") },
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddRefuelTags.STATION),
                            singleLine = true,
                            shape = GlassShapeLarge,
                            colors = glassFieldColors(),
                        )
                        if (state.hasStationMap) {
                            StationMiniMap(
                                lat = state.stationLat,
                                lon = state.stationLon,
                                label = state.station.ifBlank { null },
                                refuelId = state.editId,
                                modifier = Modifier.fillMaxWidth(),
                            )
                            TextButton(
                                onClick = viewModel::clearStationMap,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag(AddRefuelTags.CLEAR_MAP),
                            ) {
                                Text(
                                    "Убрать отметку с карты",
                                    color = MaterialTheme.colorScheme.error,
                                )
                            }
                        }
                        // Separate button: trailingIcon often misses taps after keyboard / filled form.
                        OutlinedButton(
                            onClick = onPickStationOnMap,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddRefuelTags.OPEN_MAP),
                            shape = GlassShapeLarge,
                        ) {
                            Text(
                                when {
                                    state.hasStationMap -> "Сменить АЗС на карте"
                                    state.station.isBlank() -> "Выбрать АЗС на карте"
                                    else -> "Отметить АЗС на карте"
                                },
                            )
                        }
                        state.error?.let {
                            Text(
                                text = it,
                                color = MaterialTheme.colorScheme.error,
                                style = MaterialTheme.typography.bodyMedium,
                            )
                        }
                        Button(
                            onClick = viewModel::save,
                            enabled = !state.saving,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag(AddRefuelTags.SAVE),
                            shape = GlassShapeLarge,
                        ) {
                            Text(
                                when {
                                    state.saving -> "Сохранение…"
                                    editing -> "Сохранить изменения"
                                    else -> "Сохранить заправку"
                                },
                            )
                        }
                    }
                }
            }
        }
    }

    if (state.showLowOdoConfirm) {
        AlertDialog(
            onDismissRequest = viewModel::dismissLowOdoConfirm,
            title = { Text("Пробег меньше текущего") },
            text = {
                Text("Введённый пробег меньше текущего. Вы уверены, что хотите сохранить?")
            },
            confirmButton = {
                TextButton(
                    onClick = viewModel::confirmLowOdoAndSave,
                    modifier = Modifier.testTag(AddRefuelTags.LOW_ODO_CONFIRM),
                ) { Text("Сохранить") }
            },
            dismissButton = {
                TextButton(
                    onClick = viewModel::dismissLowOdoConfirm,
                    modifier = Modifier.testTag(AddRefuelTags.LOW_ODO_CANCEL),
                ) { Text("Отмена") }
            },
        )
    }

    if (confirmDelete && onDeleted != null) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Удалить заправку?") },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.delete(onDeleted)
                    },
                    modifier = Modifier.testTag("refuel_delete_confirm"),
                ) { Text("Удалить") }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) { Text("Отмена") }
            },
        )
    }
}

private class AddRefuelVmFactory(
    private val editRefuelId: Long?,
) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
        val container = AutologApp.instance.container
        return AddRefuelViewModel(
            repository = container.repository,
            editRefuelId = editRefuelId,
            savedStateHandle = extras.createSavedStateHandle(),
            draftStore = container.formDraftStore,
        ) as T
    }
}
