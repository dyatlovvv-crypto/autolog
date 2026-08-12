package ru.autolog.app.ui.service

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import java.io.File
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import java.util.UUID
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import ru.autolog.app.data.draft.FormDraftStore
import ru.autolog.app.data.repository.AutologRepository
import ru.autolog.app.domain.ServiceChecklistCodec
import ru.autolog.app.domain.ServiceChecklistItem
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.domain.ServicePhotosCodec

data class ServiceUiState(
    val editId: Long? = null,
    val vehicleId: Long? = null,
    val currentOdometer: Int = 0,
    val kind: ServiceKind = ServiceKind.TO,
    /** Kind cannot be changed — ТО / Ремонт / Запланированно are separate entities. */
    val kindLocked: Boolean = false,
    val amount: String = "",
    val odometer: String = "",
    val dateEpochMs: Long = System.currentTimeMillis(),
    val description: String = "",
    val checklistDraft: String = "",
    val checklist: List<ServiceChecklistItem> = emptyList(),
    val photoPaths: List<String> = emptyList(),
    val saving: Boolean = false,
    val error: String? = null,
    val saved: Boolean = false,
    val loaded: Boolean = false,
)

class ServiceViewModel(
    private val repository: AutologRepository,
    private val editId: Long?,
    private val appContext: Context,
    private val savedStateHandle: SavedStateHandle,
    private val draftStore: FormDraftStore,
    fixedKind: ServiceKind? = null,
) : ViewModel() {
    private val dateFmt = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    private val initialKind = fixedKind ?: ServiceKind.TO

    private data class Meta(
        val editId: Long? = null,
        val vehicleId: Long? = null,
        val currentOdometer: Int = 0,
        val kind: ServiceKind = ServiceKind.TO,
        val kindLocked: Boolean = false,
        val saving: Boolean = false,
        val error: String? = null,
        val saved: Boolean = false,
        val loaded: Boolean = false,
        val draftRestored: Boolean = false,
    )

    private val amountFlow = savedStateHandle.getStateFlow(KEY_AMOUNT, "")
    private val odometerFlow = savedStateHandle.getStateFlow(KEY_ODOMETER, "")
    private val dateFlow = savedStateHandle.getStateFlow(KEY_DATE, System.currentTimeMillis())
    private val descriptionFlow = savedStateHandle.getStateFlow(KEY_DESCRIPTION, "")
    private val checklistDraftFlow = savedStateHandle.getStateFlow(KEY_CHECKLIST_DRAFT, "")
    private val checklistJsonFlow = savedStateHandle.getStateFlow(KEY_CHECKLIST_JSON, "[]")
    private val photosJsonFlow = savedStateHandle.getStateFlow(KEY_PHOTOS_JSON, "[]")

    private val _meta = MutableStateFlow(
        Meta(
            editId = editId,
            kind = initialKind,
            kindLocked = fixedKind != null || editId != null,
            loaded = editId == null,
        ),
    )

    private data class Fields(
        val amount: String,
        val odometer: String,
        val dateEpochMs: Long,
        val description: String,
        val checklistDraft: String,
        val checklistJson: String,
        val photosJson: String,
    )

    private val fieldsFlow = combine(
        combine(amountFlow, odometerFlow, dateFlow, descriptionFlow) { amount, odometer, date, description ->
            Fields(
                amount = amount,
                odometer = odometer,
                dateEpochMs = date,
                description = description,
                checklistDraft = checklistDraftFlow.value,
                checklistJson = checklistJsonFlow.value,
                photosJson = photosJsonFlow.value,
            )
        },
        checklistDraftFlow,
        checklistJsonFlow,
        photosJsonFlow,
    ) { base, draft, checklistJson, photosJson ->
        base.copy(
            checklistDraft = draft,
            checklistJson = checklistJson,
            photosJson = photosJson,
        )
    }

    val form: StateFlow<ServiceUiState> = combine(fieldsFlow, _meta) { fields, meta ->
        ServiceUiState(
            editId = meta.editId,
            vehicleId = meta.vehicleId,
            currentOdometer = meta.currentOdometer,
            kind = meta.kind,
            kindLocked = meta.kindLocked,
            amount = fields.amount,
            odometer = fields.odometer,
            dateEpochMs = fields.dateEpochMs,
            description = fields.description,
            checklistDraft = fields.checklistDraft,
            checklist = ServiceChecklistCodec.decode(fields.checklistJson),
            photoPaths = ServicePhotosCodec.decode(fields.photosJson)
                .filter { File(it).exists() },
            saving = meta.saving,
            error = meta.error,
            saved = meta.saved,
            loaded = meta.loaded,
        )
    }.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        ServiceUiState(
            editId = editId,
            kind = initialKind,
            kindLocked = fixedKind != null || editId != null,
            loaded = editId == null,
        ),
    )

    val activeVehicle = repository.observeActiveVehicle()
        .map { v ->
            if (v != null) {
                _meta.update {
                    if (it.editId != null) {
                        it.copy(vehicleId = v.id, currentOdometer = v.currentOdometerKm)
                    } else {
                        it.copy(vehicleId = v.id, currentOdometer = v.currentOdometerKm)
                    }
                }
                if (editId == null) {
                    restoreCreateDraft(v.id, v.currentOdometerKm)
                }
            }
            v
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    init {
        if (editId != null && savedStateHandle.get<Boolean>(KEY_EDIT_LOADED) != true) {
            viewModelScope.launch {
                val s = repository.getService(editId) ?: return@launch
                savedStateHandle[KEY_AMOUNT] = s.totalCost.toString()
                savedStateHandle[KEY_ODOMETER] = s.odometerKm.toString()
                savedStateHandle[KEY_DATE] = s.dateEpochMs
                savedStateHandle[KEY_DESCRIPTION] = s.description.orEmpty()
                savedStateHandle[KEY_CHECKLIST_DRAFT] = ""
                savedStateHandle[KEY_CHECKLIST_JSON] = s.checklistJson ?: "[]"
                savedStateHandle[KEY_PHOTOS_JSON] = s.photoPathsJson ?: "[]"
                savedStateHandle[KEY_EDIT_LOADED] = true
                _meta.update {
                    it.copy(
                        editId = s.id,
                        vehicleId = s.vehicleId,
                        kind = ServiceKind.fromStorage(s.serviceType),
                        kindLocked = true,
                        loaded = true,
                    )
                }
            }
        }
    }

    private fun restoreCreateDraft(vehicleId: Long, currentOdo: Int) {
        if (_meta.value.draftRestored) return
        val draft = draftStore.loadServiceDraft(vehicleId, _meta.value.kind)
        if (draft != null) {
            if (amountFlow.value.isEmpty()) savedStateHandle[KEY_AMOUNT] = draft.amount
            if (odometerFlow.value.isEmpty()) {
                savedStateHandle[KEY_ODOMETER] = draft.odometer.ifEmpty { currentOdo.toString() }
            }
            if (!savedStateHandle.contains(KEY_DATE_SET) && draft.dateEpochMs > 0L) {
                savedStateHandle[KEY_DATE] = draft.dateEpochMs
                savedStateHandle[KEY_DATE_SET] = true
            }
            if (descriptionFlow.value.isEmpty()) {
                savedStateHandle[KEY_DESCRIPTION] = draft.description
            }
            if (checklistDraftFlow.value.isEmpty()) {
                savedStateHandle[KEY_CHECKLIST_DRAFT] = draft.checklistDraft
            }
            if (checklistJsonFlow.value == "[]" || checklistJsonFlow.value.isBlank()) {
                savedStateHandle[KEY_CHECKLIST_JSON] = draft.checklistJson
            }
            if (photosJsonFlow.value == "[]" || photosJsonFlow.value.isBlank()) {
                savedStateHandle[KEY_PHOTOS_JSON] = draft.photoPathsJson
            }
        } else if (odometerFlow.value.isEmpty()) {
            savedStateHandle[KEY_ODOMETER] = currentOdo.toString()
        }
        _meta.update { it.copy(draftRestored = true) }
    }

    private fun persistDraft() {
        if (editId != null) return
        val vehicleId = _meta.value.vehicleId ?: return
        draftStore.saveServiceDraft(
            vehicleId,
            _meta.value.kind,
            FormDraftStore.ServiceDraft(
                amount = amountFlow.value,
                odometer = odometerFlow.value,
                dateEpochMs = dateFlow.value,
                description = descriptionFlow.value,
                checklistDraft = checklistDraftFlow.value,
                checklistJson = checklistJsonFlow.value,
                photoPathsJson = photosJsonFlow.value,
            ),
        )
    }

    fun dateLabel(): String = dateFmt.format(Date(dateFlow.value))

    fun onKind(v: ServiceKind) {
        if (_meta.value.kindLocked) return
        _meta.update { it.copy(kind = v, error = null) }
        persistDraft()
    }

    fun onAmount(v: String) {
        savedStateHandle[KEY_AMOUNT] = v.filter { c -> c.isDigit() || c == '.' || c == ',' }
        _meta.update { it.copy(error = null) }
        persistDraft()
    }

    fun onOdometer(v: String) {
        savedStateHandle[KEY_ODOMETER] = v.filter(Char::isDigit)
        _meta.update { it.copy(error = null) }
        persistDraft()
    }

    fun onDescription(v: String) {
        savedStateHandle[KEY_DESCRIPTION] = v
        persistDraft()
    }

    fun onChecklistDraft(v: String) {
        savedStateHandle[KEY_CHECKLIST_DRAFT] = v
        persistDraft()
    }

    fun onDate(year: Int, month: Int, day: Int) {
        val cal = Calendar.getInstance().apply {
            set(Calendar.YEAR, year)
            set(Calendar.MONTH, month)
            set(Calendar.DAY_OF_MONTH, day)
            set(Calendar.HOUR_OF_DAY, 12)
            set(Calendar.MINUTE, 0)
            set(Calendar.SECOND, 0)
            set(Calendar.MILLISECOND, 0)
        }
        savedStateHandle[KEY_DATE] = cal.timeInMillis
        savedStateHandle[KEY_DATE_SET] = true
        persistDraft()
    }

    fun addChecklistItem() {
        val text = checklistDraftFlow.value.trim()
        if (text.isEmpty()) return
        val next = ServiceChecklistCodec.decode(checklistJsonFlow.value) +
            ServiceChecklistItem(text = text)
        savedStateHandle[KEY_CHECKLIST_JSON] = ServiceChecklistCodec.encode(next)
        savedStateHandle[KEY_CHECKLIST_DRAFT] = ""
        persistDraft()
    }

    /** Commit typed draft into checklist before save (IME "Add" often missed). */
    private fun flushChecklistDraft() {
        val text = checklistDraftFlow.value.trim()
        if (text.isEmpty()) return
        addChecklistItem()
    }

    fun toggleChecklist(id: String, done: Boolean) {
        val next = ServiceChecklistCodec.decode(checklistJsonFlow.value).map { item ->
            if (item.id == id) item.copy(done = done) else item
        }
        savedStateHandle[KEY_CHECKLIST_JSON] = ServiceChecklistCodec.encode(next)
        persistDraft()
    }

    fun removeChecklist(id: String) {
        val next = ServiceChecklistCodec.decode(checklistJsonFlow.value)
            .filterNot { it.id == id }
        savedStateHandle[KEY_CHECKLIST_JSON] =
            if (next.isEmpty()) "[]" else ServiceChecklistCodec.encode(next)
        persistDraft()
    }

    fun addPhotoFromUri(uri: Uri) {
        viewModelScope.launch {
            val path = withContext(Dispatchers.IO) { copyUriToServicePhotos(appContext, uri) }
            if (path != null) {
                val next = ServicePhotosCodec.decode(photosJsonFlow.value) + path
                savedStateHandle[KEY_PHOTOS_JSON] = ServicePhotosCodec.encode(next)
                persistDraft()
            } else {
                _meta.update { it.copy(error = "Не удалось добавить фото") }
            }
        }
    }

    fun addPhotoFromFile(path: String) {
        if (File(path).exists()) {
            val next = ServicePhotosCodec.decode(photosJsonFlow.value) + path
            savedStateHandle[KEY_PHOTOS_JSON] = ServicePhotosCodec.encode(next)
            persistDraft()
        }
    }

    fun removePhoto(path: String) {
        val next = ServicePhotosCodec.decode(photosJsonFlow.value).filterNot { it == path }
        savedStateHandle[KEY_PHOTOS_JSON] =
            if (next.isEmpty()) "[]" else ServicePhotosCodec.encode(next)
        persistDraft()
        viewModelScope.launch(Dispatchers.IO) {
            runCatching { File(path).delete() }
        }
    }

    fun save() {
        val s = form.value
        val vehicleId = s.vehicleId ?: run {
            _meta.update { it.copy(error = "Сначала добавьте автомобиль") }
            return
        }
        val amount = s.amount.replace(',', '.').toDoubleOrNull()
        val odo = s.odometer.toIntOrNull()
        if (amount == null || amount < 0) {
            _meta.update { it.copy(error = "Укажите сумму") }
            return
        }
        if (odo == null) {
            _meta.update { it.copy(error = "Укажите пробег") }
            return
        }
        flushChecklistDraft()
        val checklist = ServiceChecklistCodec.decode(checklistJsonFlow.value)
        val checklistJson = ServiceChecklistCodec.encode(checklist).takeIf { checklist.isNotEmpty() }
        val saved = form.value
        val photosJson = ServicePhotosCodec.encode(saved.photoPaths).takeIf { saved.photoPaths.isNotEmpty() }
        viewModelScope.launch {
            _meta.update { it.copy(saving = true, error = null) }
            val result = if (saved.editId != null) {
                repository.updateService(
                    id = saved.editId,
                    odometerKm = odo,
                    totalCost = amount,
                    serviceType = saved.kind.name,
                    description = saved.description,
                    dateEpochMs = saved.dateEpochMs,
                    checklistJson = checklistJson,
                    photoPathsJson = photosJson,
                )
            } else {
                repository.addService(
                    vehicleId = vehicleId,
                    odometerKm = odo,
                    totalCost = amount,
                    serviceType = saved.kind.name,
                    description = saved.description,
                    dateEpochMs = saved.dateEpochMs,
                    checklistJson = checklistJson,
                    photoPathsJson = photosJson,
                )
            }
            result.onSuccess {
                if (editId == null) draftStore.clearServiceDraft(vehicleId, saved.kind)
                _meta.update { it.copy(saving = false, saved = true) }
            }.onFailure { e ->
                _meta.update { it.copy(saving = false, error = e.message ?: "Ошибка") }
            }
        }
    }

    fun discardDraftAndFinish(onDone: () -> Unit) {
        val vehicleId = _meta.value.vehicleId
        val kind = _meta.value.kind
        if (editId == null && vehicleId != null) {
            draftStore.clearServiceDraft(vehicleId, kind)
        }
        onDone()
    }

    fun delete(onDone: () -> Unit) {
        val id = form.value.editId ?: return
        viewModelScope.launch {
            repository.deleteService(id)
            onDone()
        }
    }

    companion object {
        private const val KEY_AMOUNT = "amount"
        private const val KEY_ODOMETER = "odometer"
        private const val KEY_DATE = "dateEpochMs"
        private const val KEY_DATE_SET = "dateEpochMs_set"
        private const val KEY_DESCRIPTION = "description"
        private const val KEY_CHECKLIST_DRAFT = "checklistDraft"
        private const val KEY_CHECKLIST_JSON = "checklistJson"
        private const val KEY_PHOTOS_JSON = "photoPathsJson"
        private const val KEY_EDIT_LOADED = "edit_loaded"
    }
}

internal fun copyUriToServicePhotos(context: Context, uri: Uri): String? {
    return try {
        val dir = File(context.filesDir, "service_photos").also { it.mkdirs() }
        val out = File(dir, "${UUID.randomUUID()}.jpg")
        context.contentResolver.openInputStream(uri)?.use { input ->
            out.outputStream().use { output -> input.copyTo(output) }
        } ?: return null
        out.absolutePath
    } catch (_: Exception) {
        null
    }
}
