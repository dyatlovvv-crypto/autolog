package ru.autolog.app.ui.reminder

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.autolog.app.data.repository.AutologRepository

data class AddReminderUiState(
    val editId: Long? = null,
    val vehicleId: Long? = null,
    val currentOdometer: Int = 0,
    val title: String = "Замена масла",
    val intervalKm: String = "7500",
    val intervalDays: String = "365",
    val lastChangeOdometer: String = "",
    val lastChangeDateEpochMs: Long = System.currentTimeMillis(),
    val forecastFromFirstRecord: Boolean = false,
    val showPhoneNotifications: Boolean = true,
    val doneAtPurchase: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
    val showChangedDialog: Boolean = false,
    val changedSummary: String = "",
)

class AddReminderViewModel(
    private val repository: AutologRepository,
    private val editReminderId: Long? = null,
    private val savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

    private data class Meta(
        val editId: Long? = null,
        val vehicleId: Long? = null,
        val currentOdometer: Int = 0,
        val saving: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        val loaded: Boolean = false,
        val showChangedDialog: Boolean = false,
        val changedSummary: String = "",
        val draftRestored: Boolean = false,
    )

    private val titleFlow = savedStateHandle.getStateFlow(KEY_TITLE, "Замена масла")
    private val intervalKmFlow = savedStateHandle.getStateFlow(KEY_INTERVAL_KM, "7500")
    private val intervalDaysFlow = savedStateHandle.getStateFlow(KEY_INTERVAL_DAYS, "365")
    private val lastOdoFlow = savedStateHandle.getStateFlow(KEY_LAST_ODO, "")
    private val lastDateFlow = savedStateHandle.getStateFlow(KEY_LAST_DATE, System.currentTimeMillis())
    private val forecastFlow = savedStateHandle.getStateFlow(KEY_FORECAST, false)
    private val notificationsFlow = savedStateHandle.getStateFlow(KEY_NOTIFICATIONS, true)
    private val doneAtPurchaseFlow = savedStateHandle.getStateFlow(KEY_DONE_AT_PURCHASE, false)

    private val _meta = MutableStateFlow(
        Meta(editId = editReminderId, loaded = editReminderId == null),
    )

    private data class Fields(
        val title: String,
        val intervalKm: String,
        val intervalDays: String,
        val lastChangeOdometer: String,
        val lastChangeDateEpochMs: Long,
        val forecastFromFirstRecord: Boolean,
        val showPhoneNotifications: Boolean,
        val doneAtPurchase: Boolean,
    )

    private val textFields = combine(
        combine(titleFlow, intervalKmFlow, intervalDaysFlow, lastOdoFlow) { title, km, days, lastOdo ->
            Fields(
                title = title,
                intervalKm = km,
                intervalDays = days,
                lastChangeOdometer = lastOdo,
                lastChangeDateEpochMs = lastDateFlow.value,
                forecastFromFirstRecord = forecastFlow.value,
                showPhoneNotifications = notificationsFlow.value,
                doneAtPurchase = doneAtPurchaseFlow.value,
            )
        },
        lastDateFlow,
        forecastFlow,
        notificationsFlow,
        doneAtPurchaseFlow,
    ) { base, lastDate, forecast, notifications, doneAtPurchase ->
        base.copy(
            lastChangeDateEpochMs = lastDate,
            forecastFromFirstRecord = forecast,
            showPhoneNotifications = notifications,
            doneAtPurchase = doneAtPurchase,
        )
    }

    private val fieldsFlow = textFields

    val form: StateFlow<AddReminderUiState> = combine(fieldsFlow, _meta) { fields, meta ->
        AddReminderUiState(
            editId = meta.editId,
            vehicleId = meta.vehicleId,
            currentOdometer = meta.currentOdometer,
            title = fields.title,
            intervalKm = fields.intervalKm,
            intervalDays = fields.intervalDays,
            lastChangeOdometer = fields.lastChangeOdometer,
            lastChangeDateEpochMs = fields.lastChangeDateEpochMs,
            forecastFromFirstRecord = fields.forecastFromFirstRecord,
            showPhoneNotifications = fields.showPhoneNotifications,
            doneAtPurchase = fields.doneAtPurchase,
            saving = meta.saving,
            error = meta.error,
            saved = meta.saved,
            loaded = meta.loaded,
            showChangedDialog = meta.showChangedDialog,
            changedSummary = meta.changedSummary,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        AddReminderUiState(editId = editReminderId, loaded = editReminderId == null),
    )

    val activeVehicle = repository.observeActiveVehicle()
        .map { vehicle ->
            if (vehicle != null) {
                _meta.update {
                    it.copy(vehicleId = vehicle.id, currentOdometer = vehicle.currentOdometerKm)
                }
                if (editReminderId == null && !_meta.value.draftRestored) {
                    // Last change odometer stays optional — do not prefill.
                    _meta.update { it.copy(draftRestored = true) }
                }
            }
            vehicle
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (editReminderId != null && savedStateHandle.get<Boolean>(KEY_EDIT_LOADED) != true) {
            viewModelScope.launch {
                val r = repository.getReminder(editReminderId) ?: return@launch
                savedStateHandle[KEY_TITLE] = r.title
                savedStateHandle[KEY_INTERVAL_KM] = r.intervalKm?.toString().orEmpty()
                savedStateHandle[KEY_INTERVAL_DAYS] = r.intervalDays?.toString().orEmpty()
                savedStateHandle[KEY_LAST_ODO] = r.baselineOdometerKm.toString()
                savedStateHandle[KEY_LAST_DATE] = r.baselineDateEpochMs
                savedStateHandle[KEY_FORECAST] = r.forecastFromFirstRecord
                savedStateHandle[KEY_NOTIFICATIONS] = r.showPhoneNotifications
                savedStateHandle[KEY_DONE_AT_PURCHASE] = r.doneAtPurchase
                savedStateHandle[KEY_EDIT_LOADED] = true
                _meta.update {
                    it.copy(editId = r.id, vehicleId = r.vehicleId, loaded = true)
                }
            }
        }
    }

    fun lastChangeDateLabel(): String = dateFmt.format(Date(lastDateFlow.value))

    fun onTitle(v: String) {
        savedStateHandle[KEY_TITLE] = v
        _meta.update { it.copy(error = null) }
    }

    fun onIntervalKm(v: String) {
        savedStateHandle[KEY_INTERVAL_KM] = v.filter(Char::isDigit)
        _meta.update { it.copy(error = null) }
    }

    fun onIntervalDays(v: String) {
        savedStateHandle[KEY_INTERVAL_DAYS] = v.filter(Char::isDigit)
        _meta.update { it.copy(error = null) }
    }

    fun onLastChangeOdometer(v: String) {
        savedStateHandle[KEY_LAST_ODO] = v.filter(Char::isDigit)
        _meta.update { it.copy(error = null) }
    }

    fun onLastChangeDate(year: Int, month: Int, day: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        savedStateHandle[KEY_LAST_DATE] = cal.timeInMillis
        _meta.update { it.copy(error = null) }
    }

    fun onForecastFromFirst(v: Boolean) {
        savedStateHandle[KEY_FORECAST] = v
    }

    fun onNotifications(v: Boolean) {
        savedStateHandle[KEY_NOTIFICATIONS] = v
    }

    fun onDoneAtPurchase(v: Boolean) {
        savedStateHandle[KEY_DONE_AT_PURCHASE] = v
    }

    fun dismissChangedDialog() = _meta.update { it.copy(showChangedDialog = false, saved = true) }

    fun save() {
        val s = form.value
        val vehicleId = s.vehicleId ?: run {
            _meta.update { it.copy(error = "Сначала добавьте автомобиль") }
            return
        }
        val km = s.intervalKm.toIntOrNull()
        val days = s.intervalDays.toIntOrNull()
        val lastOdo = s.lastChangeOdometer.toIntOrNull()
        if (s.title.isBlank()) {
            _meta.update { it.copy(error = "Укажите название") }
            return
        }
        if (km == null && days == null) {
            _meta.update { it.copy(error = "Укажите интервал в км или днях") }
            return
        }
        viewModelScope.launch {
            _meta.update { it.copy(saving = true, error = null) }
            val result = if (s.editId != null) {
                repository.updateReminder(
                    id = s.editId,
                    title = s.title,
                    intervalKm = km,
                    intervalDays = days,
                    lastChangeOdometerKm = lastOdo,
                    lastChangeDateEpochMs = s.lastChangeDateEpochMs,
                    forecastFromFirstRecord = false,
                    showPhoneNotifications = s.showPhoneNotifications,
                    doneAtPurchase = s.doneAtPurchase,
                )
            } else {
                repository.addReminder(
                    vehicleId = vehicleId,
                    title = s.title,
                    intervalKm = km,
                    intervalDays = days,
                    lastChangeOdometerKm = lastOdo,
                    lastChangeDateEpochMs = s.lastChangeDateEpochMs,
                    forecastFromFirstRecord = false,
                    showPhoneNotifications = s.showPhoneNotifications,
                    doneAtPurchase = s.doneAtPurchase,
                )
            }
            result.onSuccess {
                val baselineKm = lastOdo ?: s.currentOdometer
                val next = baselineKm + (km ?: 0)
                val summary = buildString {
                    append("Интервал: ")
                    if (km != null) append("каждые $km км")
                    if (km != null && days != null) append(" или ")
                    if (days != null) append("каждые $days дн.")
                    if (lastOdo != null) {
                        append(
                            "\nПоследняя замена: $lastOdo км, ${dateFmt.format(Date(s.lastChangeDateEpochMs))}",
                        )
                    } else {
                        append("\nПробег последней замены не указан")
                    }
                    if (km != null) append("\nСледующая по пробегу: ~$next км")
                }
                _meta.update {
                    it.copy(
                        saving = false,
                        showChangedDialog = true,
                        changedSummary = summary,
                    )
                }
            }.onFailure { e ->
                _meta.update { it.copy(saving = false, error = e.message ?: "Ошибка") }
            }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = form.value.editId ?: return
        viewModelScope.launch {
            repository.deleteReminder(id)
            onDone()
        }
    }

    companion object {
        private const val KEY_TITLE = "title"
        private const val KEY_INTERVAL_KM = "intervalKm"
        private const val KEY_INTERVAL_DAYS = "intervalDays"
        private const val KEY_LAST_ODO = "lastChangeOdometer"
        private const val KEY_LAST_DATE = "lastChangeDateEpochMs"
        private const val KEY_FORECAST = "forecastFromFirstRecord"
        private const val KEY_NOTIFICATIONS = "showPhoneNotifications"
        private const val KEY_DONE_AT_PURCHASE = "doneAtPurchase"
        private const val KEY_EDIT_LOADED = "edit_loaded"
    }
}
