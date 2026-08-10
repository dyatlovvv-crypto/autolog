package ru.autolog.app.ui.refuel

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.NavBackStackEntry
import kotlinx.coroutines.flow.collectLatest
import ru.autolog.app.ui.AutologViewModelFactory
import ru.autolog.app.ui.fluid.FluidCheckboxRow
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeLarge
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.glass.glassFieldColors

object AddRefuelTags {
    const val SCREEN = "add_refuel_screen"
    const val SAVE = "add_refuel_save"
    const val ODOMETER = "add_refuel_odometer"
    const val LITERS = "add_refuel_liters"
    const val PRICE = "add_refuel_price"
    const val STATION = "add_refuel_station"
    const val TANK_FULL = "add_refuel_tank_full"
    const val OPEN_MAP = "add_refuel_open_map"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddRefuelScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    onPickStationOnMap: () -> Unit,
    viewModel: AddRefuelViewModel = viewModel(factory = AutologViewModelFactory()),
    backStackEntry: NavBackStackEntry? = null,
) {
    val state by viewModel.form.collectAsStateWithLifecycle()
    viewModel.vehicleContext.collectAsStateWithLifecycle()

    LaunchedEffect(backStackEntry) {
        val handle = backStackEntry?.savedStateHandle ?: return@LaunchedEffect
        handle.getStateFlow<String?>("picked_station", null).collectLatest { name ->
            if (name != null) {
                viewModel.onStation(name)
                handle["picked_station"] = null
            }
        }
    }

    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    GlassScaffold(
        modifier = Modifier.testTag(AddRefuelTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text("Заправка") },
                navigationIcon = {
                    IconButton(onClick = onBack) { Text("←") }
                },
            )
        },
    ) { padding ->
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
                    )
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
                        trailingIcon = {
                            TextButton(
                                onClick = onPickStationOnMap,
                                modifier = Modifier.testTag(AddRefuelTags.OPEN_MAP),
                            ) {
                                Text("Карта")
                            }
                        },
                    )
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
                        Text(if (state.saving) "Сохранение…" else "Сохранить заправку")
                    }
                }
            }
        }
    }
}
