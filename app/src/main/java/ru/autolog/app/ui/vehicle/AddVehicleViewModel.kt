package ru.autolog.app.ui.vehicle

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.autolog.app.data.repository.AutologRepository

data class AddVehicleUiState(
    val name: String = "",
    val plate: String = "",
    val odometer: String = "",
    val tankLiters: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class AddVehicleViewModel(
    private val repository: AutologRepository,
) : ViewModel() {

    private val _ui = MutableStateFlow(AddVehicleUiState())
    val ui: StateFlow<AddVehicleUiState> = _ui.asStateFlow()

    fun onName(value: String) = _ui.update { it.copy(name = value, error = null) }
    fun onPlate(value: String) = _ui.update { it.copy(plate = value.uppercase(), error = null) }
    fun onOdometer(value: String) = _ui.update { it.copy(odometer = value.filter { c -> c.isDigit() }, error = null) }
    fun onTank(value: String) = _ui.update {
        it.copy(tankLiters = value.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null)
    }

    fun save() {
        val state = _ui.value
        val name = state.name.trim()
        val odo = state.odometer.toIntOrNull()
        val tank = state.tankLiters.replace(',', '.').toDoubleOrNull()
        if (name.isEmpty()) {
            _ui.update { it.copy(error = "Укажите название или модель") }
            return
        }
        if (odo == null || odo < 0) {
            _ui.update { it.copy(error = "Укажите текущий пробег") }
            return
        }
        if (tank == null || tank <= 0) {
            _ui.update { it.copy(error = "Укажите объём бака в литрах") }
            return
        }
        viewModelScope.launch {
            _ui.update { it.copy(saving = true, error = null) }
            repository.addVehicle(
                name = name,
                odometerKm = odo,
                tankCapacityLiters = tank,
                plate = state.plate.trim().ifEmpty { null },
            )
                .onSuccess { _ui.update { it.copy(saving = false, saved = true) } }
                .onFailure { e ->
                    _ui.update { it.copy(saving = false, error = e.message ?: "Ошибка сохранения") }
                }
        }
    }
}
