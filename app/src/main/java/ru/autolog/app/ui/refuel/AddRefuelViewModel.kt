package ru.autolog.app.ui.refuel

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.autolog.app.AutologApp
import ru.autolog.app.data.draft.FormDraftStore
import ru.autolog.app.data.map.StationMapSnapshots
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.domain.FuelType

data class AddRefuelUiState(
    val editId: Long? = null,
    val vehicleId: Long? = null,
    val vehicleName: String = "",
    val currentOdometer: Int = 0,
    val odometer: String = "",
    val liters: String = "",
    val price: String = "",
    val isTankFull: Boolean = true,
    val station: String = "",
    val stationLat: Double? = null,
    val stationLon: Double? = null,
    val fuelType: FuelType = FuelType.AI_95,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
    val showLowOdoConfirm: Boolean = false,
) {
    val hasStationMap: Boolean
        get() = (stationLat != null && stationLon != null &&
            stationLat.isFinite() && stationLon.isFinite()) ||
            station.isNotBlank()
}

class AddRefuelViewModel(
    private val repository: AutologRepository,
    private val editRefuelId: Long? = null,
    private val savedStateHandle: SavedStateHandle,
    private val draftStore: FormDraftStore,
) : ViewModel() {

    private data class Meta(
        val editId: Long? = null,
        val vehicleId: Long? = null,
        val vehicleName: String = "",
        val currentOdometer: Int = 0,
        val saving: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        val loaded: Boolean = false,
        val showLowOdoConfirm: Boolean = false,
        val draftRestored: Boolean = false,
    )

    private val odometerFlow = savedStateHandle.getStateFlow(KEY_ODOMETER, "")
    private val litersFlow = savedStateHandle.getStateFlow(KEY_LITERS, "")
    private val priceFlow = savedStateHandle.getStateFlow(KEY_PRICE, "")
    private val stationFlow = savedStateHandle.getStateFlow(KEY_STATION, "")
    private val stationLatFlow = savedStateHandle.getStateFlow(KEY_STATION_LAT, "")
    private val stationLonFlow = savedStateHandle.getStateFlow(KEY_STATION_LON, "")
    private val tankFullFlow = savedStateHandle.getStateFlow(KEY_TANK_FULL, true)
    private val fuelTypeFlow = savedStateHandle.getStateFlow(KEY_FUEL_TYPE, FuelType.AI_95.name)

    private val _meta = MutableStateFlow(
        Meta(editId = editRefuelId, loaded = editRefuelId == null),
    )

    private data class Fields(
        val odometer: String,
        val liters: String,
        val price: String,
        val station: String,
        val stationLat: String,
        val stationLon: String,
        val isTankFull: Boolean,
        val fuelType: String,
    )

    private val fieldsFlow = combine(
        combine(odometerFlow, litersFlow, priceFlow, stationFlow) { odo, liters, price, station ->
            arrayOf(odo, liters, price, station)
        },
        combine(stationLatFlow, stationLonFlow, tankFullFlow, fuelTypeFlow) { lat, lon, tank, fuel ->
            arrayOf(lat, lon, tank.toString(), fuel)
        },
    ) { texts, extras ->
        Fields(
            odometer = texts[0],
            liters = texts[1],
            price = texts[2],
            station = texts[3],
            stationLat = extras[0],
            stationLon = extras[1],
            isTankFull = extras[2].toBooleanStrictOrNull() ?: true,
            fuelType = extras[3],
        )
    }

    val form: StateFlow<AddRefuelUiState> = combine(fieldsFlow, _meta) { fields, meta ->
        AddRefuelUiState(
            editId = meta.editId,
            vehicleId = meta.vehicleId,
            vehicleName = meta.vehicleName,
            currentOdometer = meta.currentOdometer,
            odometer = fields.odometer,
            liters = fields.liters,
            price = fields.price,
            isTankFull = fields.isTankFull,
            station = fields.station,
            stationLat = fields.stationLat.toDoubleOrNull(),
            stationLon = fields.stationLon.toDoubleOrNull(),
            fuelType = FuelType.fromStorage(fields.fuelType) ?: FuelType.AI_95,
            saving = meta.saving,
            error = meta.error,
            saved = meta.saved,
            loaded = meta.loaded,
            showLowOdoConfirm = meta.showLowOdoConfirm,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AddRefuelUiState(editId = editRefuelId, loaded = editRefuelId == null),
    )

    val vehicleContext = repository.observeActiveVehicle()
        .map { vehicle ->
            if (vehicle != null) {
                _meta.update {
                    if (it.editId != null) {
                        it.copy(
                            vehicleId = vehicle.id,
                            vehicleName = vehicle.name,
                            currentOdometer = vehicle.currentOdometerKm,
                        )
                    } else if (it.vehicleId == vehicle.id) {
                        it.copy(currentOdometer = vehicle.currentOdometerKm)
                    } else {
                        it.copy(
                            vehicleId = vehicle.id,
                            vehicleName = vehicle.name,
                            currentOdometer = vehicle.currentOdometerKm,
                        )
                    }
                }
                if (editRefuelId == null) {
                    restoreCreateDefaults(vehicle.id, vehicle.currentOdometerKm, vehicle.fuelTypeDefault)
                }
            }
            vehicle
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (editRefuelId != null && savedStateHandle.get<Boolean>(KEY_EDIT_LOADED) != true) {
            viewModelScope.launch {
                val r = repository.getRefuelling(editRefuelId) ?: return@launch
                savedStateHandle[KEY_ODOMETER] = r.odometerKm.toString()
                savedStateHandle[KEY_LITERS] = r.volumeLiters.toString()
                savedStateHandle[KEY_PRICE] =
                    if (r.pricePerLiter == 0.0) "" else r.pricePerLiter.toString()
                savedStateHandle[KEY_TANK_FULL] = r.isTankFull
                savedStateHandle[KEY_STATION] = r.stationName.orEmpty()
                savedStateHandle[KEY_STATION_LAT] = r.stationLat?.toString().orEmpty()
                savedStateHandle[KEY_STATION_LON] = r.stationLon?.toString().orEmpty()
                savedStateHandle[KEY_FUEL_TYPE] =
                    (FuelType.fromStorage(r.fuelType) ?: FuelType.AI_95).name
                savedStateHandle[KEY_EDIT_LOADED] = true
                _meta.update {
                    it.copy(
                        editId = r.id,
                        vehicleId = r.vehicleId,
                        loaded = true,
                    )
                }
                // Old refuels: name without coords → geocode once and keep snapshot.
                // Skip UITest* names so device UI tests never wait on Nominatim.
                if (
                    (r.stationLat == null || r.stationLon == null) &&
                    !r.stationName.isNullOrBlank() &&
                    !r.stationName.startsWith("UITest")
                ) {
                    val geo = StationMapSnapshots.geocode(r.stationName)
                    if (geo != null) {
                        repository.updateRefuelStationCoords(r.id, geo.lat, geo.lon)
                        savedStateHandle[KEY_STATION_LAT] = geo.lat.toString()
                        savedStateHandle[KEY_STATION_LON] = geo.lon.toString()
                        StationMapSnapshots.prefetchAsync(AutologApp.instance, geo.lat, geo.lon)
                    }
                } else if (r.stationLat != null && r.stationLon != null) {
                    StationMapSnapshots.prefetchAsync(
                        AutologApp.instance,
                        r.stationLat,
                        r.stationLon,
                    )
                }
            }
        }
    }

    private fun restoreCreateDefaults(vehicleId: Long, currentOdo: Int, fuelDefault: String?) {
        if (_meta.value.draftRestored) return
        val draft = draftStore.loadRefuelDraft(vehicleId)
        if (draft != null) {
            if (odometerFlow.value.isEmpty()) savedStateHandle[KEY_ODOMETER] = draft.odometer
            if (litersFlow.value.isEmpty()) savedStateHandle[KEY_LITERS] = draft.liters
            if (priceFlow.value.isEmpty()) savedStateHandle[KEY_PRICE] = draft.price
            if (stationFlow.value.isEmpty()) savedStateHandle[KEY_STATION] = draft.station
            if (!savedStateHandle.contains(KEY_TANK_FULL_SET)) {
                savedStateHandle[KEY_TANK_FULL] = draft.isTankFull
                savedStateHandle[KEY_TANK_FULL_SET] = true
            }
            if (!savedStateHandle.contains(KEY_FUEL_SET)) {
                val ft = FuelType.fromStorage(draft.fuelType)
                    ?: FuelType.fromStorage(fuelDefault)
                    ?: FuelType.AI_95
                savedStateHandle[KEY_FUEL_TYPE] = ft.name
                savedStateHandle[KEY_FUEL_SET] = true
            }
        } else {
            if (odometerFlow.value.isEmpty()) {
                savedStateHandle[KEY_ODOMETER] = currentOdo.toString()
            }
            if (!savedStateHandle.contains(KEY_FUEL_SET)) {
                savedStateHandle[KEY_FUEL_TYPE] =
                    (FuelType.fromStorage(fuelDefault) ?: FuelType.AI_95).name
                savedStateHandle[KEY_FUEL_SET] = true
            }
        }
        _meta.update { it.copy(draftRestored = true) }
    }

    private fun persistDraft() {
        if (editRefuelId != null) return
        val vehicleId = _meta.value.vehicleId ?: return
        draftStore.saveRefuelDraft(
            vehicleId,
            FormDraftStore.RefuelDraft(
                odometer = odometerFlow.value,
                liters = litersFlow.value,
                price = priceFlow.value,
                isTankFull = tankFullFlow.value,
                station = stationFlow.value,
                fuelType = fuelTypeFlow.value,
            ),
        )
    }

    fun onOdometer(v: String) {
        savedStateHandle[KEY_ODOMETER] = v.filter(Char::isDigit)
        _meta.update { it.copy(error = null) }
        persistDraft()
    }

    fun onLiters(v: String) {
        savedStateHandle[KEY_LITERS] = v.filter { c -> c.isDigit() || c == '.' || c == ',' }
        _meta.update { it.copy(error = null) }
        persistDraft()
    }

    fun onPrice(v: String) {
        savedStateHandle[KEY_PRICE] = v.filter { c -> c.isDigit() || c == '.' || c == ',' }
        _meta.update { it.copy(error = null) }
        persistDraft()
    }

    fun onStation(v: String) {
        savedStateHandle[KEY_STATION] = v
        // Manual edit invalidates map coordinates.
        savedStateHandle[KEY_STATION_LAT] = ""
        savedStateHandle[KEY_STATION_LON] = ""
        persistDraft()
    }

    fun onStationPicked(name: String, lat: Double, lon: Double) {
        savedStateHandle[KEY_STATION] = name
        savedStateHandle[KEY_STATION_LAT] = lat.toString()
        savedStateHandle[KEY_STATION_LON] = lon.toString()
        // Warm disk snapshot once — later opens only show the PNG.
        StationMapSnapshots.prefetchAsync(AutologApp.instance, lat, lon)
        persistDraft()
    }

    fun onTankFull(v: Boolean) {
        savedStateHandle[KEY_TANK_FULL] = v
        savedStateHandle[KEY_TANK_FULL_SET] = true
        persistDraft()
    }

    fun onFuelType(v: FuelType) {
        savedStateHandle[KEY_FUEL_TYPE] = v.name
        savedStateHandle[KEY_FUEL_SET] = true
        persistDraft()
    }

    fun dismissLowOdoConfirm() = _meta.update { it.copy(showLowOdoConfirm = false) }

    fun save() = prepareSave(force = false)

    fun confirmLowOdoAndSave() {
        _meta.update { it.copy(showLowOdoConfirm = false) }
        prepareSave(force = true)
    }

    private fun prepareSave(force: Boolean) {
        val s = form.value
        val vehicleId = s.vehicleId ?: run {
            _meta.update { it.copy(error = "Сначала добавьте автомобиль") }
            return
        }
        val odo = s.odometer.toIntOrNull()
        val liters = s.liters.replace(',', '.').toDoubleOrNull()
        val price = s.price.replace(',', '.').trim()
            .takeIf { it.isNotEmpty() }
            ?.toDoubleOrNull()
            ?: 0.0
        if (odo == null) {
            _meta.update { it.copy(error = "Укажите пробег") }
            return
        }
        if (liters == null || liters <= 0) {
            _meta.update { it.copy(error = "Укажите литры — без этого заправку не сохранить") }
            return
        }
        if (price < 0) {
            _meta.update { it.copy(error = "Цена не может быть отрицательной") }
            return
        }
        if (!force && odo < s.currentOdometer) {
            _meta.update { it.copy(showLowOdoConfirm = true, error = null) }
            return
        }
        viewModelScope.launch {
            _meta.update { it.copy(saving = true, error = null) }
            val result = if (s.editId != null) {
                repository.updateRefuelling(
                    id = s.editId,
                    odometerKm = odo,
                    volumeLiters = liters,
                    pricePerLiter = price,
                    isTankFull = s.isTankFull,
                    stationName = s.station,
                    fuelType = s.fuelType.name,
                    stationLat = s.stationLat,
                    stationLon = s.stationLon,
                )
            } else {
                repository.addRefuelling(
                    vehicleId = vehicleId,
                    odometerKm = odo,
                    volumeLiters = liters,
                    pricePerLiter = price,
                    isTankFull = s.isTankFull,
                    stationName = s.station,
                    fuelType = s.fuelType.name,
                    stationLat = s.stationLat,
                    stationLon = s.stationLon,
                )
            }
            result.onSuccess {
                if (editRefuelId == null) draftStore.clearRefuelDraft(vehicleId)
                _meta.update { it.copy(saving = false, saved = true) }
            }.onFailure { e ->
                _meta.update { it.copy(saving = false, error = e.message ?: "Ошибка") }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = form.value.editId ?: return
        viewModelScope.launch {
            repository.deleteRefuelling(id)
            onDone()
        }
    }

    companion object {
        private const val KEY_ODOMETER = "odometer"
        private const val KEY_LITERS = "liters"
        private const val KEY_PRICE = "price"
        private const val KEY_STATION = "station"
        private const val KEY_STATION_LAT = "stationLat"
        private const val KEY_STATION_LON = "stationLon"
        private const val KEY_TANK_FULL = "isTankFull"
        private const val KEY_TANK_FULL_SET = "isTankFull_set"
        private const val KEY_FUEL_TYPE = "fuelType"
        private const val KEY_FUEL_SET = "fuelType_set"
        private const val KEY_EDIT_LOADED = "edit_loaded"
    }
}
