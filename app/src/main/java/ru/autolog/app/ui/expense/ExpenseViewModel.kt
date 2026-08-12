package ru.autolog.app.ui.expense

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
import ru.autolog.app.data.repository.AutologRepository

data class ExpenseUiState(
    val editId: Long? = null,
    val vehicleId: Long? = null,
    val category: String = "Мойка",
    val categoryLocked: Boolean = false,
    val amount: String = "",
    val odometer: String = "",
    val description: String = "",
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
)

class ExpenseViewModel(
    private val repository: AutologRepository,
    private val editId: Long?,
    private val savedStateHandle: SavedStateHandle,
    private val fixedCategory: String? = null,
) : ViewModel() {
    private data class Meta(
        val editId: Long? = null,
        val vehicleId: Long? = null,
        val saving: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        val loaded: Boolean = false,
        val categoryLocked: Boolean = false,
    )

    private val categoryFlow = savedStateHandle.getStateFlow(
        KEY_CATEGORY,
        fixedCategory ?: "Мойка",
    )
    private val amountFlow = savedStateHandle.getStateFlow(KEY_AMOUNT, "")
    private val odometerFlow = savedStateHandle.getStateFlow(KEY_ODOMETER, "")
    private val descriptionFlow = savedStateHandle.getStateFlow(KEY_DESCRIPTION, "")

    private val _meta = MutableStateFlow(
        Meta(
            editId = editId,
            loaded = editId == null,
            categoryLocked = fixedCategory != null,
        ),
    )

    private data class Fields(
        val category: String,
        val amount: String,
        val odometer: String,
        val description: String,
    )

    private val fieldsFlow = combine(
        categoryFlow,
        amountFlow,
        odometerFlow,
        descriptionFlow,
    ) { category, amount, odometer, description ->
        Fields(category, amount, odometer, description)
    }

    val form: StateFlow<ExpenseUiState> = combine(fieldsFlow, _meta) { fields, meta ->
        ExpenseUiState(
            editId = meta.editId,
            vehicleId = meta.vehicleId,
            category = fields.category,
            categoryLocked = meta.categoryLocked,
            amount = fields.amount,
            odometer = fields.odometer,
            description = fields.description,
            saving = meta.saving,
            error = meta.error,
            saved = meta.saved,
            loaded = meta.loaded,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ExpenseUiState(
            editId = editId,
            category = fixedCategory ?: "Мойка",
            categoryLocked = fixedCategory != null,
            loaded = editId == null,
        ),
    )

    val activeVehicle = repository.observeActiveVehicle()
        .map { v ->
            if (v != null) _meta.update { it.copy(vehicleId = v.id) }
            v
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (editId == null && fixedCategory != null && !savedStateHandle.contains(KEY_CATEGORY)) {
            savedStateHandle[KEY_CATEGORY] = fixedCategory
        }
        if (editId != null && savedStateHandle.get<Boolean>(KEY_EDIT_LOADED) != true) {
            viewModelScope.launch {
                val e = repository.getExpense(editId) ?: return@launch
                savedStateHandle[KEY_CATEGORY] = e.category
                savedStateHandle[KEY_AMOUNT] = e.totalCost.toString()
                savedStateHandle[KEY_ODOMETER] = e.odometerKm?.toString().orEmpty()
                savedStateHandle[KEY_DESCRIPTION] = e.description.orEmpty()
                savedStateHandle[KEY_EDIT_LOADED] = true
                _meta.update {
                    it.copy(editId = e.id, vehicleId = e.vehicleId, loaded = true)
                }
            }
        }
    }

    fun onCategory(v: String) {
        if (fixedCategory != null) return
        savedStateHandle[KEY_CATEGORY] = v
        _meta.update { it.copy(error = null) }
    }

    fun onAmount(v: String) {
        savedStateHandle[KEY_AMOUNT] = v.filter { c -> c.isDigit() || c == '.' || c == ',' }
        _meta.update { it.copy(error = null) }
    }

    fun onOdometer(v: String) {
        savedStateHandle[KEY_ODOMETER] = v.filter(Char::isDigit)
    }

    fun onDescription(v: String) {
        savedStateHandle[KEY_DESCRIPTION] = v
    }

    fun save() {
        val s = form.value
        val vehicleId = s.vehicleId ?: run {
            _meta.update { it.copy(error = "Сначала добавьте автомобиль") }
            return
        }
        val amount = s.amount.replace(',', '.').toDoubleOrNull()
        if (s.category.isBlank()) {
            _meta.update { it.copy(error = "Укажите категорию") }
            return
        }
        if (amount == null || amount < 0) {
            _meta.update { it.copy(error = "Укажите сумму") }
            return
        }
        val odo = s.odometer.toIntOrNull()
        viewModelScope.launch {
            _meta.update { it.copy(saving = true, error = null) }
            val result = if (s.editId != null) {
                repository.updateExpense(s.editId, amount, s.category, s.description, odo)
            } else {
                repository.addExpense(vehicleId, amount, s.category, s.description, odo)
            }
            result.onSuccess { _meta.update { it.copy(saving = false, saved = true) } }
                .onFailure { e ->
                    _meta.update { it.copy(saving = false, error = e.message ?: "Ошибка") }
                }
        }
    }

    fun delete(onDone: () -> Unit) {
        val id = form.value.editId ?: return
        viewModelScope.launch {
            repository.deleteExpense(id)
            onDone()
        }
    }

    companion object {
        private const val KEY_CATEGORY = "category"
        private const val KEY_AMOUNT = "amount"
        private const val KEY_ODOMETER = "odometer"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_EDIT_LOADED = "edit_loaded"
    }
}
