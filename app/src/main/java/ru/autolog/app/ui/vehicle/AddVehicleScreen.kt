package ru.autolog.app.ui.vehicle

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
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ru.autolog.app.ui.AutologViewModelFactory
import ru.autolog.app.ui.fluid.rubberBandOverscroll
import ru.autolog.app.ui.glass.GlassPanel
import ru.autolog.app.ui.glass.GlassScaffold
import ru.autolog.app.ui.glass.GlassShapeLarge
import ru.autolog.app.ui.glass.GlassTopAppBar
import ru.autolog.app.ui.glass.glassFieldColors

object AddVehicleTags {
    const val SCREEN = "add_vehicle_screen"
    const val SAVE = "add_vehicle_save"
    const val NAME = "add_vehicle_name"
    const val PLATE = "add_vehicle_plate"
    const val ODOMETER = "add_vehicle_odometer"
    const val TANK = "add_vehicle_tank"
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddVehicleScreen(
    onSaved: () -> Unit,
    onBack: (() -> Unit)? = null,
    viewModel: AddVehicleViewModel = viewModel(factory = AutologViewModelFactory()),
) {
    val state by viewModel.ui.collectAsStateWithLifecycle()
    LaunchedEffect(state.saved) {
        if (state.saved) onSaved()
    }

    GlassScaffold(
        modifier = Modifier.testTag(AddVehicleTags.SCREEN),
        topBar = {
            GlassTopAppBar(
                title = { Text(if (onBack == null) "Первый автомобиль" else "Новый автомобиль") },
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
                    Text(
                        text = "Название, текущий пробег и объём бака — обязательно.",
                        style = MaterialTheme.typography.bodyMedium,
                    )
                    OutlinedTextField(
                        value = state.name,
                        onValueChange = viewModel::onName,
                        label = { Text("Название / модель") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AddVehicleTags.NAME),
                        singleLine = true,
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    OutlinedTextField(
                        value = state.plate,
                        onValueChange = viewModel::onPlate,
                        label = { Text("Госномер (необязательно)") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AddVehicleTags.PLATE),
                        singleLine = true,
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    OutlinedTextField(
                        value = state.odometer,
                        onValueChange = viewModel::onOdometer,
                        label = { Text("Текущий пробег, км") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AddVehicleTags.ODOMETER),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    OutlinedTextField(
                        value = state.tankLiters,
                        onValueChange = viewModel::onTank,
                        label = { Text("Объём бака, л") },
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AddVehicleTags.TANK),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        shape = GlassShapeLarge,
                        colors = glassFieldColors(),
                    )
                    state.error?.let {
                        Text(text = it, color = MaterialTheme.colorScheme.error)
                    }
                    Button(
                        onClick = viewModel::save,
                        enabled = !state.saving,
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag(AddVehicleTags.SAVE),
                        shape = GlassShapeLarge,
                    ) {
                        Text(if (state.saving) "Сохранение…" else "Сохранить")
                    }
                }
            }
        }
    }
}
