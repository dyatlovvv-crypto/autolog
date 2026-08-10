package ru.autolog.app.ui.reminder

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import ru.autolog.app.data.repository.AutologRepository

data class AddReminderUiState(
    val vehicleId: Long? = null,
    val currentOdometer: Int = 0,
    val title: String = "Замена масла",
    val intervalKm: String = "7500",
    val intervalDays: String = "365",
    /** Пробег, на котором масло уже меняли (точка отсчёта). */
    val lastChangeOdometer: String = "",
    /** Дата последней замены, epoch ms (начало дня). */
    val lastChangeDateEpochMs: Long = System.currentTimeMillis(),
    val forecastFromFirstRecord: Boolean = false,
    val showPhoneNotifications: Boolean = true,
    val doneAtPurchase: Boolean = false,
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val showChangedDialog: Boolean = false,
    val changedSummary: String = "",
)

class AddReminderViewModel(
    private val repository: AutologRepository,
) : ViewModel() {

    private val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

    private val _form = MutableStateFlow(AddReminderUiState())
    val form: StateFlow<AddReminderUiState> = _form.asStateFlow()

    val activeVehicle = repository.observeActiveVehicle()
        .map { vehicle ->
            if (vehicle != null) {
                _form.update {
                    it.copy(
                        vehicleId = vehicle.id,
                        currentOdometer = vehicle.currentOdometerKm,
                        lastChangeOdometer = it.lastChangeOdometer.ifEmpty {
                            vehicle.currentOdometerKm.toString()
                        },
                    )
                }
            }
            vehicle
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun lastChangeDateLabel(): String = dateFmt.format(Date(_form.value.lastChangeDateEpochMs))

    fun onTitle(v: String) = _form.update { it.copy(title = v, error = null) }
    fun onIntervalKm(v: String) = _form.update { it.copy(intervalKm = v.filter(Char::isDigit), error = null) }
    fun onIntervalDays(v: String) = _form.update { it.copy(intervalDays = v.filter(Char::isDigit), error = null) }
    fun onLastChangeOdometer(v: String) = _form.update {
        it.copy(lastChangeOdometer = v.filter(Char::isDigit), error = null)
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
        _form.update { it.copy(lastChangeDateEpochMs = cal.timeInMillis, error = null) }
    }
    fun onForecastFromFirst(v: Boolean) = _form.update { it.copy(forecastFromFirstRecord = v) }
    fun onNotifications(v: Boolean) = _form.update { it.copy(showPhoneNotifications = v) }
    fun onDoneAtPurchase(v: Boolean) = _form.update { it.copy(doneAtPurchase = v) }
    fun dismissChangedDialog() = _form.update { it.copy(showChangedDialog = false, saved = true) }

    fun save() {
        val s = _form.value
        val vehicleId = s.vehicleId ?: run {
            _form.update { it.copy(error = "Сначала добавьте автомобиль") }
            return
        }
        val km = s.intervalKm.toIntOrNull()
        val days = s.intervalDays.toIntOrNull()
        val lastOdo = s.lastChangeOdometer.toIntOrNull()
        if (s.title.isBlank()) {
            _form.update { it.copy(error = "Укажите название") }
            return
        }
        if (lastOdo == null) {
            _form.update { it.copy(error = "Укажите пробег последней замены") }
            return
        }
        viewModelScope.launch {
            _form.update { it.copy(saving = true, error = null) }
            repository.addReminder(
                vehicleId = vehicleId,
                title = s.title,
                intervalKm = km,
                intervalDays = days,
                lastChangeOdometerKm = lastOdo,
                lastChangeDateEpochMs = s.lastChangeDateEpochMs,
                forecastFromFirstRecord = s.forecastFromFirstRecord,
                showPhoneNotifications = s.showPhoneNotifications,
                doneAtPurchase = s.doneAtPurchase,
            ).onSuccess {
                val next = lastOdo + (km ?: 0)
                val summary = buildString {
                    append("Интервал: ")
                    if (km != null) append("каждые $km км")
                    if (km != null && days != null) append(" или ")
                    if (days != null) append("каждые $days дн.")
                    append("\nПоследняя замена: $lastOdo км, ${dateFmt.format(Date(s.lastChangeDateEpochMs))}")
                    if (km != null) append("\nСледующая по пробегу: ~$next км")
                }
                _form.update {
                    it.copy(
                        saving = false,
                        showChangedDialog = true,
                        changedSummary = summary,
                    )
                }
            }.onFailure { e ->
                _form.update { it.copy(saving = false, error = e.message ?: "Ошибка") }
            }
        }
    }
}
