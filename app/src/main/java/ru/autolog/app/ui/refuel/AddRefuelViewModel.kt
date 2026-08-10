package ru.autolog.app.ui.refuel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.autolog.app.data.repository.AutologRepository

data class AddRefuelUiState(
    val vehicleId: Long? = null,
    val vehicleName: String = "",
    val currentOdometer: Int = 0,
    val odometer: String = "",
    val liters: String = "",
    val price: String = "",
    val isTankFull: Boolean = true,
    val station: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
)

class AddRefuelViewModel(
    private val repository: AutologRepository,
) : ViewModel() {

    private val _form = MutableStateFlow(AddRefuelUiState())
    val form: StateFlow<AddRefuelUiState> = _form.asStateFlow()

    val vehicleContext = repository.observeActiveVehicle()
        .map { vehicle ->
            if (vehicle != null) {
                _form.update {
                    if (it.vehicleId == vehicle.id) {
                        it
                    } else {
                        it.copy(
                            vehicleId = vehicle.id,
                            vehicleName = vehicle.name,
                            currentOdometer = vehicle.currentOdometerKm,
                            odometer = vehicle.currentOdometerKm.toString(),
                        )
                    }
                }
            }
            vehicle
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun onOdometer(v: String) = _form.update { it.copy(odometer = v.filter(Char::isDigit), error = null) }
    fun onLiters(v: String) = _form.update {
        it.copy(liters = v.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null)
    }
    fun onPrice(v: String) = _form.update {
        it.copy(price = v.filter { c -> c.isDigit() || c == '.' || c == ',' }, error = null)
    }
    fun onStation(v: String) = _form.update { it.copy(station = v) }
    fun onTankFull(v: Boolean) = _form.update { it.copy(isTankFull = v) }

    fun save() {
        val s = _form.value
        val vehicleId = s.vehicleId ?: run {
            _form.update { it.copy(error = "Сначала добавьте автомобиль") }
            return
        }
        val odo = s.odometer.toIntOrNull()
        val liters = s.liters.replace(',', '.').toDoubleOrNull()
        val price = s.price.replace(',', '.').trim()
            .takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()
            ?: 0.0
        if (odo == null) {
            _form.update { it.copy(error = "Укажите пробег") }
            return
        }
        if (liters == null || liters <= 0) {
            _form.update { it.copy(error = "Укажите литры — без этого заправку не сохранить") }
            return
        }
        if (price < 0) {
            _form.update { it.copy(error = "Цена не может быть отрицательной") }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            repository.addRefuelling(
                vehicleId = vehicleId,
                odometerKm = odo,
                volumeLiters = liters,
                pricePerLiter = price,
                isTankFull = s.isTankFull,
                stationName = s.station,
            ).onSuccess {
                _form.update { it.copy(saving = false, saved = true) }
            }.onFailure { e ->
                _form.update { it.copy(saving = false, error = e.message ?: "Ошибка") }
            }
        }
    }
}
