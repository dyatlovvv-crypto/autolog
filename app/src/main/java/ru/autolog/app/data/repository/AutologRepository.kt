package ru.autolog.app.data.repository

import android.content.Context
import android.net.Uri
import java.io.File
import java.util.UUID
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import org.json.JSONArray
import org.json.JSONObject
import ru.autolog.app.data.backup.BackupFileCodec
import ru.autolog.app.data.local.AutologDatabase
import ru.autolog.app.data.local.VehicleDocsStore
import ru.autolog.app.data.local.entity.ExpenseEntity
import ru.autolog.app.data.local.entity.NoteEntity
import ru.autolog.app.data.local.entity.RefuellingEntity
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.data.local.entity.ServiceEntity
import ru.autolog.app.data.local.entity.VehicleDocumentEntity
import ru.autolog.app.data.local.entity.VehicleEntity
import ru.autolog.app.domain.ServicePhotosCodec
import ru.autolog.app.domain.VehicleLimits
import ru.autolog.app.domain.consumption.ConsumptionCalculator
import ru.autolog.app.domain.consumption.ConsumptionCalculator.FullTankPoint
import ru.autolog.app.domain.consumption.ConsumptionCalculator.RefuelPoint
import ru.autolog.app.domain.reminder.ReminderForecast
import ru.autolog.app.ui.note.NotepadBlock
import ru.autolog.app.ui.note.NotepadDocument
import ru.autolog.app.ui.note.NotepadPhotos

class AutologRepository(
    private val db: AutologDatabase,
    private val appContext: Context,
) {

    fun observeVehicles(): Flow<List<VehicleEntity>> = db.vehicleDao().observeAll()

    fun observeActiveVehicle(): Flow<VehicleEntity?> = db.vehicleDao().observeActive()

    /** True if the phone has any vehicles (link must never wipe these). */
    suspend fun hasLocalJournalData(): Boolean = db.vehicleDao().getAll().isNotEmpty()

    fun observeDashboard(vehicleId: Long): Flow<DashboardSnapshot> = combine(
        db.vehicleDao().observeAll().map { list -> list.find { it.id == vehicleId } },
        db.refuellingDao().observeByVehicle(vehicleId),
        db.reminderDao().observeByVehicle(vehicleId),
        db.expenseDao().observeByVehicle(vehicleId),
        db.serviceDao().observeByVehicle(vehicleId),
    ) { vehicle, refuels, reminders, expenses, services ->
        buildDashboard(
            vehicle = vehicle,
            refuelsNewestFirst = refuels,
            reminders = reminders,
            expensesNewestFirst = expenses,
            servicesNewestFirst = services,
        )
    }

    fun observeNotepad(vehicleId: Long): Flow<NoteEntity?> =
        db.noteDao().observeNotepad(vehicleId)

    suspend fun addVehicle(
        name: String,
        odometerKm: Int,
        tankCapacityLiters: Double,
        plate: String? = null,
    ): Result<Long> {
        val count = db.vehicleDao().count()
        if (!VehicleLimits.canAdd(count)) {
            return Result.failure(IllegalStateException("Максимум ${VehicleLimits.MAX} автомобилей"))
        }
        val now = System.currentTimeMillis()
        val id = db.vehicleDao().insert(
            VehicleEntity(
                name = name.trim(),
                plate = plate,
                currentOdometerKm = odometerKm,
                tankCapacityLiters = tankCapacityLiters,
                isActive = true,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        db.vehicleDao().switchActive(id, now)
        return Result.success(id)
    }

    suspend fun switchVehicle(id: Long) {
        db.vehicleDao().switchActive(id, System.currentTimeMillis())
    }

    suspend fun deleteVehicle(id: Long): Result<Unit> {
        val vehicle = db.vehicleDao().getById(id)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        val wasActive = vehicle.isActive
        db.vehicleDao().delete(vehicle)
        VehicleDocsStore.deleteVehicleDir(appContext, id)
        if (wasActive) {
            val next = db.vehicleDao().getAll().firstOrNull()
            if (next != null) {
                db.vehicleDao().switchActive(next.id, System.currentTimeMillis())
            }
        }
        return Result.success(Unit)
    }

    /** Test helper: wipe local data so UI flows start clean. */
    suspend fun clearAllData() {
        db.clearAllTables()
    }

    suspend fun updateRefuelling(
        id: Long,
        odometerKm: Int,
        volumeLiters: Double,
        pricePerLiter: Double,
        isTankFull: Boolean,
        stationName: String?,
        fuelType: String?,
    ): Result<Unit> {
        val existing = db.refuellingDao().getById(id)
            ?: return Result.failure(IllegalArgumentException("Заправка не найдена"))
        val siblings = db.refuellingDao().getByVehicle(existing.vehicleId)
            .filter { it.id != id }
        val updated = existing.copy(
            odometerKm = odometerKm,
            volumeLiters = volumeLiters,
            pricePerLiter = pricePerLiter,
            totalCost = volumeLiters * pricePerLiter,
            isTankFull = isTankFull,
            stationName = stationName?.trim()?.ifEmpty { null },
            fuelType = fuelType,
            consumptionLPer100Km = null,
        )
        val withConsumption = applyConsumption(updated, siblings)
        db.refuellingDao().update(withConsumption)
        syncVehicleOdometer(existing.vehicleId)
        refreshRemindersForVehicle(existing.vehicleId)
        return Result.success(Unit)
    }

    suspend fun addRefuelling(
        vehicleId: Long,
        odometerKm: Int,
        volumeLiters: Double,
        pricePerLiter: Double,
        isTankFull: Boolean,
        stationName: String?,
        fuelType: String?,
    ): Result<Long> {
        val vehicle = db.vehicleDao().getById(vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        val now = System.currentTimeMillis()
        val existing = db.refuellingDao().getByVehicle(vehicleId)
        val draft = RefuellingEntity(
            vehicleId = vehicleId,
            dateEpochMs = now,
            odometerKm = odometerKm,
            volumeLiters = volumeLiters,
            pricePerLiter = pricePerLiter,
            totalCost = volumeLiters * pricePerLiter,
            isTankFull = isTankFull,
            stationName = stationName?.trim()?.ifEmpty { null },
            fuelType = fuelType,
            consumptionLPer100Km = null,
            createdAtEpochMs = now,
        )
        val withConsumption = applyConsumption(draft, existing)
        val id = db.refuellingDao().insert(withConsumption)
        if (!fuelType.isNullOrBlank() && fuelType != vehicle.fuelTypeDefault) {
            db.vehicleDao().update(
                vehicle.copy(fuelTypeDefault = fuelType, updatedAtEpochMs = now),
            )
        }
        syncVehicleOdometer(vehicleId)
        refreshRemindersForVehicle(vehicleId)
        return Result.success(id)
    }

    suspend fun addReminder(
        vehicleId: Long,
        title: String,
        intervalKm: Int?,
        intervalDays: Int?,
        lastChangeOdometerKm: Int?,
        lastChangeDateEpochMs: Long?,
        forecastFromFirstRecord: Boolean,
        showPhoneNotifications: Boolean,
        doneAtPurchase: Boolean,
    ): Result<Long> {
        val vehicle = db.vehicleDao().getById(vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        if (intervalKm == null && intervalDays == null) {
            return Result.failure(IllegalArgumentException("Укажите км и/или дни"))
        }
        val now = System.currentTimeMillis()
        val services = db.serviceDao().getByVehicleOldestFirst(vehicleId)
        val baseline = when {
            lastChangeOdometerKm != null ->
                lastChangeOdometerKm to (lastChangeDateEpochMs ?: now)
            forecastFromFirstRecord && services.isNotEmpty() ->
                services.first().odometerKm to services.first().dateEpochMs
            doneAtPurchase -> vehicle.currentOdometerKm to now
            else -> vehicle.currentOdometerKm to now
        }

        val avg = avgKmPerDay(vehicleId)
        val forecast = ReminderForecast.compute(
            ReminderForecast.Input(
                currentOdometerKm = vehicle.currentOdometerKm,
                baselineOdometerKm = baseline.first,
                baselineDateEpochMs = baseline.second,
                intervalKm = intervalKm,
                intervalDays = intervalDays,
                avgKmPerDay = avg,
                nowEpochMs = now,
            ),
        )

        val id = db.reminderDao().insert(
            ReminderEntity(
                vehicleId = vehicleId,
                title = title.trim(),
                intervalKm = intervalKm,
                intervalDays = intervalDays,
                baselineOdometerKm = baseline.first,
                baselineDateEpochMs = baseline.second,
                forecastFromFirstRecord = forecastFromFirstRecord,
                showPhoneNotifications = showPhoneNotifications,
                doneAtPurchase = doneAtPurchase,
                avgKmPerDay = forecast.avgKmPerDay,
                remainingKm = forecast.remainingKm,
                remainingDays = forecast.remainingDays,
                nextDueDateEpochMs = forecast.nextDueDateEpochMs,
                nextDueOdometerKm = forecast.nextDueOdometerKm,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )
        return Result.success(id)
    }

    suspend fun updateReminder(
        id: Long,
        title: String,
        intervalKm: Int?,
        intervalDays: Int?,
        lastChangeOdometerKm: Int?,
        lastChangeDateEpochMs: Long?,
        forecastFromFirstRecord: Boolean,
        showPhoneNotifications: Boolean,
        doneAtPurchase: Boolean,
    ): Result<Unit> {
        val existing = db.reminderDao().getById(id)
            ?: return Result.failure(IllegalArgumentException("Напоминание не найдено"))
        val vehicle = db.vehicleDao().getById(existing.vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        if (intervalKm == null && intervalDays == null) {
            return Result.failure(IllegalArgumentException("Укажите км и/или дни"))
        }
        val now = System.currentTimeMillis()
        val baselineOdo = lastChangeOdometerKm ?: existing.baselineOdometerKm
        val baselineDate = lastChangeDateEpochMs ?: existing.baselineDateEpochMs
        val avg = avgKmPerDay(existing.vehicleId)
        val forecast = ReminderForecast.compute(
            ReminderForecast.Input(
                currentOdometerKm = vehicle.currentOdometerKm,
                baselineOdometerKm = baselineOdo,
                baselineDateEpochMs = baselineDate,
                intervalKm = intervalKm,
                intervalDays = intervalDays,
                avgKmPerDay = avg,
                nowEpochMs = now,
            ),
        )
        db.reminderDao().update(
            existing.copy(
                title = title.trim(),
                intervalKm = intervalKm,
                intervalDays = intervalDays,
                baselineOdometerKm = baselineOdo,
                baselineDateEpochMs = baselineDate,
                forecastFromFirstRecord = forecastFromFirstRecord,
                showPhoneNotifications = showPhoneNotifications,
                doneAtPurchase = doneAtPurchase,
                avgKmPerDay = forecast.avgKmPerDay,
                remainingKm = forecast.remainingKm,
                remainingDays = forecast.remainingDays,
                nextDueDateEpochMs = forecast.nextDueDateEpochMs,
                nextDueOdometerKm = forecast.nextDueOdometerKm,
                updatedAtEpochMs = now,
            ),
        )
        return Result.success(Unit)
    }

    suspend fun addExpense(
        vehicleId: Long,
        totalCost: Double,
        category: String,
        description: String?,
        odometerKm: Int?,
    ): Result<Long> {
        db.vehicleDao().getById(vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        if (category.isBlank()) {
            return Result.failure(IllegalArgumentException("Укажите категорию"))
        }
        if (totalCost < 0) {
            return Result.failure(IllegalArgumentException("Сумма не может быть отрицательной"))
        }
        val now = System.currentTimeMillis()
        val id = db.expenseDao().insert(
            ExpenseEntity(
                vehicleId = vehicleId,
                dateEpochMs = now,
                odometerKm = odometerKm,
                totalCost = totalCost,
                category = category.trim(),
                description = description?.trim()?.ifEmpty { null },
                createdAtEpochMs = now,
            ),
        )
        return Result.success(id)
    }

    suspend fun updateExpense(
        id: Long,
        totalCost: Double,
        category: String,
        description: String?,
        odometerKm: Int?,
    ): Result<Unit> {
        val existing = db.expenseDao().getById(id)
            ?: return Result.failure(IllegalArgumentException("Расход не найден"))
        if (category.isBlank()) {
            return Result.failure(IllegalArgumentException("Укажите категорию"))
        }
        if (totalCost < 0) {
            return Result.failure(IllegalArgumentException("Сумма не может быть отрицательной"))
        }
        db.expenseDao().update(
            existing.copy(
                totalCost = totalCost,
                category = category.trim(),
                description = description?.trim()?.ifEmpty { null },
                odometerKm = odometerKm,
            ),
        )
        return Result.success(Unit)
    }

    suspend fun addService(
        vehicleId: Long,
        odometerKm: Int,
        totalCost: Double,
        serviceType: String,
        description: String?,
        dateEpochMs: Long = System.currentTimeMillis(),
        checklistJson: String? = null,
        photoPathsJson: String? = null,
    ): Result<Long> {
        db.vehicleDao().getById(vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        if (serviceType.isBlank()) {
            return Result.failure(IllegalArgumentException("Укажите тип сервиса"))
        }
        if (totalCost < 0) {
            return Result.failure(IllegalArgumentException("Сумма не может быть отрицательной"))
        }
        val now = System.currentTimeMillis()
        val id = db.serviceDao().insert(
            ServiceEntity(
                vehicleId = vehicleId,
                dateEpochMs = dateEpochMs,
                odometerKm = odometerKm,
                totalCost = totalCost,
                serviceType = serviceType.trim(),
                description = description?.trim()?.ifEmpty { null },
                checklistJson = checklistJson,
                photoPathsJson = photoPathsJson,
                createdAtEpochMs = now,
            ),
        )
        syncVehicleOdometer(vehicleId)
        refreshRemindersForVehicle(vehicleId)
        return Result.success(id)
    }

    suspend fun updateService(
        id: Long,
        odometerKm: Int,
        totalCost: Double,
        serviceType: String,
        description: String?,
        dateEpochMs: Long,
        checklistJson: String? = null,
        photoPathsJson: String? = null,
    ): Result<Unit> {
        val existing = db.serviceDao().getById(id)
            ?: return Result.failure(IllegalArgumentException("Сервис не найден"))
        if (serviceType.isBlank()) {
            return Result.failure(IllegalArgumentException("Укажите тип сервиса"))
        }
        if (totalCost < 0) {
            return Result.failure(IllegalArgumentException("Сумма не может быть отрицательной"))
        }
        db.serviceDao().update(
            existing.copy(
                odometerKm = odometerKm,
                totalCost = totalCost,
                serviceType = serviceType.trim(),
                description = description?.trim()?.ifEmpty { null },
                dateEpochMs = dateEpochMs,
                checklistJson = checklistJson,
                photoPathsJson = photoPathsJson,
            ),
        )
        syncVehicleOdometer(existing.vehicleId)
        refreshRemindersForVehicle(existing.vehicleId)
        return Result.success(Unit)
    }

    suspend fun deleteRefuelling(id: Long) {
        val item = db.refuellingDao().getById(id) ?: return
        db.refuellingDao().delete(item)
        syncVehicleOdometer(item.vehicleId)
        refreshRemindersForVehicle(item.vehicleId)
    }

    suspend fun deleteReminder(id: Long) {
        val item = db.reminderDao().getById(id) ?: return
        db.reminderDao().delete(item)
    }

    suspend fun deleteExpense(id: Long) {
        val item = db.expenseDao().getById(id) ?: return
        db.expenseDao().delete(item)
    }

    suspend fun deleteService(id: Long) {
        val item = db.serviceDao().getById(id) ?: return
        db.serviceDao().delete(item)
        syncVehicleOdometer(item.vehicleId)
        refreshRemindersForVehicle(item.vehicleId)
    }

    suspend fun saveNotepad(vehicleId: Long, text: String): Result<Unit> {
        db.vehicleDao().getById(vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        val now = System.currentTimeMillis()
        val existing = db.noteDao().getNotepad(vehicleId)
        if (existing == null) {
            if (text.isEmpty()) return Result.success(Unit)
            db.noteDao().insert(
                NoteEntity(
                    vehicleId = vehicleId,
                    text = text,
                    updatedAtEpochMs = now,
                ),
            )
        } else {
            db.noteDao().update(
                existing.copy(
                    text = text,
                    updatedAtEpochMs = now,
                ),
            )
        }
        return Result.success(Unit)
    }

    suspend fun getNotepad(vehicleId: Long): NoteEntity? =
        db.noteDao().getNotepad(vehicleId)

    fun observeVehicleDocuments(vehicleId: Long): Flow<List<VehicleDocumentEntity>> =
        db.vehicleDocumentDao().observeByVehicle(vehicleId)

    suspend fun addVehicleDocument(vehicleId: Long, uri: Uri): Result<Long> {
        db.vehicleDao().getById(vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        val copied = VehicleDocsStore.copyFromUri(appContext, vehicleId, uri)
            ?: return Result.failure(
                IllegalArgumentException("Нужен PDF или фото (JPG/PNG/WEBP)"),
            )
        val id = db.vehicleDocumentDao().insert(
            VehicleDocumentEntity(
                vehicleId = vehicleId,
                title = copied.displayName,
                mimeType = copied.mimeType,
                filePath = copied.absolutePath,
                createdAtEpochMs = System.currentTimeMillis(),
            ),
        )
        return Result.success(id)
    }

    suspend fun deleteVehicleDocument(id: Long): Result<Unit> {
        val doc = db.vehicleDocumentDao().getById(id)
            ?: return Result.failure(IllegalArgumentException("Документ не найден"))
        db.vehicleDocumentDao().deleteById(id)
        VehicleDocsStore.deleteFile(doc.filePath)
        return Result.success(Unit)
    }

    suspend fun getVehicleDocument(id: Long): VehicleDocumentEntity? =
        db.vehicleDocumentDao().getById(id)

    fun observeRefuellings(vehicleId: Long): Flow<List<RefuellingEntity>> =
        db.refuellingDao().observeByVehicle(vehicleId)

    fun observeServices(vehicleId: Long): Flow<List<ServiceEntity>> =
        db.serviceDao().observeByVehicle(vehicleId)

    fun observeExpenses(vehicleId: Long): Flow<List<ExpenseEntity>> =
        db.expenseDao().observeByVehicle(vehicleId)

    suspend fun getRefuelling(id: Long) = db.refuellingDao().getById(id)

    suspend fun getReminder(id: Long) = db.reminderDao().getById(id)

    suspend fun getExpense(id: Long) = db.expenseDao().getById(id)

    suspend fun getService(id: Long) = db.serviceDao().getById(id)

    /**
     * Full local backup as JSON (vehicles + journal + attachments).
     * Version 2 embeds vehicle docs, notepad images, and service photos as base64.
     */
    suspend fun exportBackupJson(): String {
        val root = JSONObject()
        root.put("version", BACKUP_VERSION)
        root.put("exportedAtEpochMs", System.currentTimeMillis())
        val vehiclesArr = JSONArray()
        db.vehicleDao().getAll().forEach { v ->
            val vo = JSONObject()
            vo.put("name", v.name)
            vo.put("manufacturer", v.manufacturer)
            vo.put("model", v.model)
            vo.put("plate", v.plate)
            vo.put("currentOdometerKm", v.currentOdometerKm)
            vo.put("tankCapacityLiters", v.tankCapacityLiters)
            vo.put("year", v.year)
            vo.put("fuelTypeDefault", v.fuelTypeDefault)
            vo.put("isActive", v.isActive)
            vo.put("createdAtEpochMs", v.createdAtEpochMs)
            vo.put("updatedAtEpochMs", v.updatedAtEpochMs)

            val refuels = JSONArray()
            db.refuellingDao().getByVehicle(v.id).forEach { r ->
                refuels.put(
                    JSONObject()
                        .put("dateEpochMs", r.dateEpochMs)
                        .put("odometerKm", r.odometerKm)
                        .put("volumeLiters", r.volumeLiters)
                        .put("pricePerLiter", r.pricePerLiter)
                        .put("totalCost", r.totalCost)
                        .put("fuelType", r.fuelType)
                        .put("isTankFull", r.isTankFull)
                        .put("stationName", r.stationName)
                        .put("note", r.note)
                        .put("consumptionLPer100Km", r.consumptionLPer100Km)
                        .put("createdAtEpochMs", r.createdAtEpochMs),
                )
            }
            vo.put("refuellings", refuels)

            val expenses = JSONArray()
            db.expenseDao().getByVehicle(v.id).forEach { e ->
                expenses.put(
                    JSONObject()
                        .put("dateEpochMs", e.dateEpochMs)
                        .put("odometerKm", e.odometerKm)
                        .put("totalCost", e.totalCost)
                        .put("category", e.category)
                        .put("description", e.description)
                        .put("createdAtEpochMs", e.createdAtEpochMs),
                )
            }
            vo.put("expenses", expenses)

            val notepad = db.noteDao().getNotepad(v.id)
            if (notepad != null) {
                val notepadObj = JSONObject()
                    .put("text", notepad.text)
                    .put("updatedAtEpochMs", notepad.updatedAtEpochMs)
                val imageFiles = JSONArray()
                NotepadDocument.decode(notepad.text).blocks.forEach { block ->
                    if (block is NotepadBlock.Image) {
                        BackupFileCodec.fileEntry(block.path) { put("sourcePath", block.path) }
                            ?.let { imageFiles.put(it) }
                    }
                }
                if (imageFiles.length() > 0) notepadObj.put("images", imageFiles)
                vo.put("notepad", notepadObj)
            }

            val services = JSONArray()
            db.serviceDao().getByVehicleOldestFirst(v.id).forEach { s ->
                val so = JSONObject()
                    .put("dateEpochMs", s.dateEpochMs)
                    .put("odometerKm", s.odometerKm)
                    .put("totalCost", s.totalCost)
                    .put("serviceType", s.serviceType)
                    .put("description", s.description)
                    .put("checklistJson", s.checklistJson)
                    .put("photoPathsJson", s.photoPathsJson)
                    .put("doneAtPurchase", s.doneAtPurchase)
                    .put("createdAtEpochMs", s.createdAtEpochMs)
                val photos = JSONArray()
                ServicePhotosCodec.decode(s.photoPathsJson).forEach { path ->
                    BackupFileCodec.fileEntry(path)?.let { photos.put(it) }
                }
                if (photos.length() > 0) so.put("photos", photos)
                services.put(so)
            }
            vo.put("services", services)

            val reminders = JSONArray()
            db.reminderDao().getByVehicle(v.id).forEach { rem ->
                reminders.put(
                    JSONObject()
                        .put("title", rem.title)
                        .put("intervalKm", rem.intervalKm)
                        .put("intervalDays", rem.intervalDays)
                        .put("baselineOdometerKm", rem.baselineOdometerKm)
                        .put("baselineDateEpochMs", rem.baselineDateEpochMs)
                        .put("forecastFromFirstRecord", rem.forecastFromFirstRecord)
                        .put("showPhoneNotifications", rem.showPhoneNotifications)
                        .put("doneAtPurchase", rem.doneAtPurchase)
                        .put("avgKmPerDay", rem.avgKmPerDay)
                        .put("remainingKm", rem.remainingKm)
                        .put("remainingDays", rem.remainingDays)
                        .put("nextDueDateEpochMs", rem.nextDueDateEpochMs)
                        .put("nextDueOdometerKm", rem.nextDueOdometerKm)
                        .put("isActive", rem.isActive)
                        .put("createdAtEpochMs", rem.createdAtEpochMs)
                        .put("updatedAtEpochMs", rem.updatedAtEpochMs),
                )
            }
            vo.put("reminders", reminders)

            val documents = JSONArray()
            db.vehicleDocumentDao().getByVehicle(v.id).forEach { doc ->
                val b64 = BackupFileCodec.readBase64(doc.filePath) ?: return@forEach
                documents.put(
                    JSONObject()
                        .put("title", doc.title)
                        .put("mimeType", doc.mimeType)
                        .put("fileName", File(doc.filePath).name)
                        .put("contentBase64", b64)
                        .put("createdAtEpochMs", doc.createdAtEpochMs),
                )
            }
            vo.put("documents", documents)

            vehiclesArr.put(vo)
        }
        root.put("vehicles", vehiclesArr)
        return root.toString(2)
    }

    /**
     * Replace local DB with backup contents.
     * Only call after explicit user confirmation in Settings.
     */
    suspend fun restoreBackupJson(json: String): Result<Unit> = runCatching {
        val root = JSONObject(json)
        val vehicles = root.getJSONArray("vehicles")
        clearAttachmentDirs()
        db.clearAllTables()
        var firstId: Long? = null
        for (i in 0 until vehicles.length()) {
            val vo = vehicles.getJSONObject(i)
            val now = System.currentTimeMillis()
            val newId = db.vehicleDao().insert(
                VehicleEntity(
                    name = vo.getString("name"),
                    manufacturer = vo.optString("manufacturer").ifBlank { null },
                    model = vo.optString("model").ifBlank { null },
                    plate = vo.optString("plate").ifBlank { null },
                    currentOdometerKm = vo.getInt("currentOdometerKm"),
                    tankCapacityLiters = vo.getDouble("tankCapacityLiters"),
                    year = if (vo.has("year") && !vo.isNull("year")) vo.getInt("year") else null,
                    fuelTypeDefault = vo.optString("fuelTypeDefault").ifBlank { null },
                    isActive = false,
                    createdAtEpochMs = vo.optLong("createdAtEpochMs", now),
                    updatedAtEpochMs = vo.optLong("updatedAtEpochMs", now),
                ),
            )
            if (firstId == null) firstId = newId

            val refuels = vo.optJSONArray("refuellings")
            if (refuels != null) {
                for (j in 0 until refuels.length()) {
                    val r = refuels.getJSONObject(j)
                    db.refuellingDao().insert(
                        RefuellingEntity(
                            vehicleId = newId,
                            dateEpochMs = r.getLong("dateEpochMs"),
                            odometerKm = r.getInt("odometerKm"),
                            volumeLiters = r.getDouble("volumeLiters"),
                            pricePerLiter = r.getDouble("pricePerLiter"),
                            totalCost = r.getDouble("totalCost"),
                            fuelType = r.optString("fuelType").ifBlank { null },
                            isTankFull = r.optBoolean("isTankFull", false),
                            stationName = r.optString("stationName").ifBlank { null },
                            note = r.optString("note").ifBlank { null },
                            consumptionLPer100Km = if (r.has("consumptionLPer100Km") && !r.isNull("consumptionLPer100Km")) {
                                r.getDouble("consumptionLPer100Km")
                            } else {
                                null
                            },
                            createdAtEpochMs = r.optLong("createdAtEpochMs", now),
                        ),
                    )
                }
            }

            val expenses = vo.optJSONArray("expenses")
            if (expenses != null) {
                for (j in 0 until expenses.length()) {
                    val e = expenses.getJSONObject(j)
                    db.expenseDao().insert(
                        ExpenseEntity(
                            vehicleId = newId,
                            dateEpochMs = e.getLong("dateEpochMs"),
                            odometerKm = if (e.has("odometerKm") && !e.isNull("odometerKm")) e.getInt("odometerKm") else null,
                            totalCost = e.getDouble("totalCost"),
                            category = e.getString("category"),
                            description = e.optString("description").ifBlank { null },
                            createdAtEpochMs = e.optLong("createdAtEpochMs", now),
                        ),
                    )
                }
            }

            val notepad = vo.optJSONObject("notepad")
            if (notepad != null) {
                val restoredText = restoreNotepadText(
                    vehicleId = newId,
                    rawText = notepad.optString("text", ""),
                    imagesArr = notepad.optJSONArray("images"),
                )
                db.noteDao().insert(
                    NoteEntity(
                        vehicleId = newId,
                        text = restoredText,
                        updatedAtEpochMs = notepad.optLong(
                            "updatedAtEpochMs",
                            System.currentTimeMillis(),
                        ),
                    ),
                )
            } else {
                val notes = vo.optJSONArray("notes")
                if (notes != null && notes.length() > 0) {
                    val merged = buildString {
                        for (j in 0 until notes.length()) {
                            val n = notes.getJSONObject(j)
                            if (isNotEmpty()) append("\n\n")
                            append(n.optString("text", ""))
                        }
                    }
                    if (merged.isNotBlank()) {
                        db.noteDao().insert(
                            NoteEntity(
                                vehicleId = newId,
                                text = merged,
                                updatedAtEpochMs = now,
                            ),
                        )
                    }
                }
            }

            val services = vo.optJSONArray("services")
            if (services != null) {
                for (j in 0 until services.length()) {
                    val s = services.getJSONObject(j)
                    val photoPathsJson = restoreServicePhotos(
                        photosArr = s.optJSONArray("photos"),
                        legacyPathsJson = s.optString("photoPathsJson").ifBlank { null },
                    )
                    db.serviceDao().insert(
                        ServiceEntity(
                            vehicleId = newId,
                            dateEpochMs = s.getLong("dateEpochMs"),
                            odometerKm = s.getInt("odometerKm"),
                            totalCost = s.getDouble("totalCost"),
                            serviceType = s.getString("serviceType"),
                            description = s.optString("description").ifBlank { null },
                            checklistJson = s.optString("checklistJson").ifBlank { null },
                            photoPathsJson = photoPathsJson,
                            doneAtPurchase = s.optBoolean("doneAtPurchase", false),
                            createdAtEpochMs = s.optLong("createdAtEpochMs", now),
                        ),
                    )
                }
            }

            val reminders = vo.optJSONArray("reminders")
            if (reminders != null) {
                for (j in 0 until reminders.length()) {
                    val rem = reminders.getJSONObject(j)
                    db.reminderDao().insert(
                        ReminderEntity(
                            vehicleId = newId,
                            title = rem.getString("title"),
                            intervalKm = if (rem.has("intervalKm") && !rem.isNull("intervalKm")) rem.getInt("intervalKm") else null,
                            intervalDays = if (rem.has("intervalDays") && !rem.isNull("intervalDays")) rem.getInt("intervalDays") else null,
                            baselineOdometerKm = rem.getInt("baselineOdometerKm"),
                            baselineDateEpochMs = rem.getLong("baselineDateEpochMs"),
                            forecastFromFirstRecord = rem.optBoolean("forecastFromFirstRecord", false),
                            showPhoneNotifications = rem.optBoolean("showPhoneNotifications", true),
                            doneAtPurchase = rem.optBoolean("doneAtPurchase", false),
                            avgKmPerDay = if (rem.has("avgKmPerDay") && !rem.isNull("avgKmPerDay")) rem.getDouble("avgKmPerDay") else null,
                            remainingKm = if (rem.has("remainingKm") && !rem.isNull("remainingKm")) rem.getInt("remainingKm") else null,
                            remainingDays = if (rem.has("remainingDays") && !rem.isNull("remainingDays")) rem.getInt("remainingDays") else null,
                            nextDueDateEpochMs = if (rem.has("nextDueDateEpochMs") && !rem.isNull("nextDueDateEpochMs")) rem.getLong("nextDueDateEpochMs") else null,
                            nextDueOdometerKm = if (rem.has("nextDueOdometerKm") && !rem.isNull("nextDueOdometerKm")) rem.getInt("nextDueOdometerKm") else null,
                            isActive = rem.optBoolean("isActive", true),
                            createdAtEpochMs = rem.optLong("createdAtEpochMs", now),
                            updatedAtEpochMs = rem.optLong("updatedAtEpochMs", now),
                        ),
                    )
                }
            }

            val documents = BackupFileCodec.parseFileEntries(vo.optJSONArray("documents"))
            documents.forEach { entry ->
                val written = BackupFileCodec.writeBase64(
                    dir = VehicleDocsStore.dir(appContext, newId),
                    fileName = entry.fileName.ifBlank { "${UUID.randomUUID()}.bin" },
                    base64 = entry.contentBase64,
                ) ?: return@forEach
                db.vehicleDocumentDao().insert(
                    VehicleDocumentEntity(
                        vehicleId = newId,
                        title = entry.title,
                        mimeType = entry.mimeType,
                        filePath = written.absolutePath,
                        createdAtEpochMs = entry.createdAtEpochMs.takeIf { it > 0L } ?: now,
                    ),
                )
            }
        }
        firstId?.let { db.vehicleDao().switchActive(it, System.currentTimeMillis()) }
    }

    private fun clearAttachmentDirs() {
        runCatching { File(appContext.filesDir, "vehicle_docs").deleteRecursively() }
        runCatching { File(appContext.filesDir, "notepad_photos").deleteRecursively() }
        runCatching { File(appContext.filesDir, "service_photos").deleteRecursively() }
    }

    private fun restoreNotepadText(
        vehicleId: Long,
        rawText: String,
        imagesArr: JSONArray?,
    ): String {
        val entries = BackupFileCodec.parseFileEntries(imagesArr)
        if (entries.isEmpty()) return rawText
        val bySource = entries.associateBy { it.sourcePath.orEmpty() }
        val byName = entries.associateBy { it.fileName }
        val doc = NotepadDocument.decode(rawText)
        val remapped = doc.blocks.map { block ->
            if (block !is NotepadBlock.Image) return@map block
            val entry = bySource[block.path]
                ?: byName[File(block.path).name]
                ?: return@map block
            val written = BackupFileCodec.writeBase64(
                dir = NotepadPhotos.dir(appContext, vehicleId),
                fileName = entry.fileName.ifBlank { "${UUID.randomUUID()}.jpg" },
                base64 = entry.contentBase64,
            ) ?: return@map block
            block.copy(path = written.absolutePath)
        }
        return NotepadDocument(remapped).encode()
    }

    private fun restoreServicePhotos(
        photosArr: JSONArray?,
        legacyPathsJson: String?,
    ): String? {
        val entries = BackupFileCodec.parseFileEntries(photosArr)
        if (entries.isNotEmpty()) {
            val dir = File(appContext.filesDir, "service_photos").also { it.mkdirs() }
            val paths = entries.mapNotNull { entry ->
                BackupFileCodec.writeBase64(
                    dir = dir,
                    fileName = entry.fileName.ifBlank { "${UUID.randomUUID()}.jpg" },
                    base64 = entry.contentBase64,
                )?.absolutePath
            }
            return ServicePhotosCodec.encode(paths).takeIf { paths.isNotEmpty() }
        }
        return legacyPathsJson
    }

    suspend fun refreshRemindersForVehicle(vehicleId: Long) {
        val vehicle = db.vehicleDao().getById(vehicleId) ?: return
        val avg = avgKmPerDay(vehicleId)
        val now = System.currentTimeMillis()
        db.reminderDao().getActiveByVehicle(vehicleId).forEach { reminder ->
            val forecast = ReminderForecast.compute(
                ReminderForecast.Input(
                    currentOdometerKm = vehicle.currentOdometerKm,
                    baselineOdometerKm = reminder.baselineOdometerKm,
                    baselineDateEpochMs = reminder.baselineDateEpochMs,
                    intervalKm = reminder.intervalKm,
                    intervalDays = reminder.intervalDays,
                    avgKmPerDay = avg,
                    nowEpochMs = now,
                ),
            )
            db.reminderDao().update(
                reminder.copy(
                    avgKmPerDay = forecast.avgKmPerDay,
                    remainingKm = forecast.remainingKm,
                    remainingDays = forecast.remainingDays,
                    nextDueDateEpochMs = forecast.nextDueDateEpochMs,
                    nextDueOdometerKm = forecast.nextDueOdometerKm,
                    updatedAtEpochMs = now,
                ),
            )
        }
    }

    /**
     * Vehicle odometer = max(refuel odo, service odo).
     * Backdated lower entries do not reduce current if a higher reading exists.
     * If there are no mileage records, keep the vehicle value.
     */
    private suspend fun syncVehicleOdometer(vehicleId: Long) {
        val vehicle = db.vehicleDao().getById(vehicleId) ?: return
        val maxRefuel = db.refuellingDao().getByVehicle(vehicleId).maxOfOrNull { it.odometerKm }
        val maxService = db.serviceDao().getByVehicleOldestFirst(vehicleId).maxOfOrNull { it.odometerKm }
        val fromRecords = listOfNotNull(maxRefuel, maxService).maxOrNull()
        val newOdo = fromRecords ?: vehicle.currentOdometerKm
        if (newOdo != vehicle.currentOdometerKm) {
            db.vehicleDao().update(
                vehicle.copy(
                    currentOdometerKm = newOdo,
                    updatedAtEpochMs = System.currentTimeMillis(),
                ),
            )
        }
    }

    private fun applyConsumption(
        draft: RefuellingEntity,
        siblings: List<RefuellingEntity>,
    ): RefuellingEntity {
        if (!draft.isTankFull) return draft
        val allNewest = listOf(draft) + siblings
        val fullNewest = allNewest.filter { it.isTankFull }.map {
            FullTankPoint(it.odometerKm, it.dateEpochMs)
        }
        val points = allNewest.map {
            RefuelPoint(it.odometerKm, it.volumeLiters, it.dateEpochMs)
        }
        val segment = ConsumptionCalculator.lastFullTankSegment(fullNewest, points)
        return draft.copy(consumptionLPer100Km = segment?.litersPer100Km)
    }

    private suspend fun avgKmPerDay(vehicleId: Long): Double? {
        val refuels = db.refuellingDao().getByVehicle(vehicleId)
            .sortedBy { it.dateEpochMs }
            .map { it.dateEpochMs to it.odometerKm }
        val vehicle = db.vehicleDao().getById(vehicleId) ?: return null
        val samples = if (refuels.isEmpty()) {
            listOf(vehicle.createdAtEpochMs to vehicle.currentOdometerKm)
        } else {
            refuels
        }
        return ReminderForecast.averageKmPerDay(samples)
    }

    private fun buildDashboard(
        vehicle: VehicleEntity?,
        refuelsNewestFirst: List<RefuellingEntity>,
        reminders: List<ReminderEntity>,
        expensesNewestFirst: List<ExpenseEntity>,
        servicesNewestFirst: List<ServiceEntity>,
    ): DashboardSnapshot {
        if (vehicle == null) {
            return DashboardSnapshot.Empty
        }
        val fullNewest = refuelsNewestFirst.filter { it.isTankFull }.map {
            FullTankPoint(it.odometerKm, it.dateEpochMs)
        }
        val points = refuelsNewestFirst.map {
            RefuelPoint(it.odometerKm, it.volumeLiters, it.dateEpochMs)
        }
        val last = ConsumptionCalculator.lastFullTankSegment(fullNewest, points)?.litersPer100Km
        val lifetime = ConsumptionCalculator.lifetimeAverage(
            fullTankOldestFirst = fullNewest.reversed(),
            allBetween = points,
        )?.litersPer100Km

        val activeReminders = reminders.filter { it.isActive }
        val timeline = buildTimeline(
            refuels = refuelsNewestFirst.take(TIMELINE_PER_TYPE),
            expenses = expensesNewestFirst.take(TIMELINE_PER_TYPE),
            services = servicesNewestFirst.take(TIMELINE_PER_TYPE),
        )
        return DashboardSnapshot.Data(
            vehicle = vehicle,
            averageConsumptionLPer100 = lifetime,
            lastConsumptionLPer100 = last,
            recentRefuellings = refuelsNewestFirst.take(5),
            activeReminders = activeReminders,
            recentExpenses = expensesNewestFirst.take(5),
            recentServices = servicesNewestFirst.take(5),
            timeline = timeline,
        )
    }

    private fun buildTimeline(
        refuels: List<RefuellingEntity>,
        expenses: List<ExpenseEntity>,
        services: List<ServiceEntity>,
    ): List<TimelineEntry> {
        val entries = ArrayList<TimelineEntry>(
            refuels.size + expenses.size + services.size,
        )
        refuels.forEach { entries += TimelineEntry.Refuel(it) }
        expenses.forEach { entries += TimelineEntry.Expense(it) }
        services.forEach { entries += TimelineEntry.Service(it) }
        return entries.sortedByDescending { it.sortEpochMs }
    }

    companion object {
        private const val TIMELINE_PER_TYPE = 30
        /** 2 = journal + vehicle docs / notepad images / service photos (base64). */
        const val BACKUP_VERSION = 2
    }
}

sealed class TimelineEntry {
    abstract val id: Long
    abstract val sortEpochMs: Long

    data class Refuel(val entity: RefuellingEntity) : TimelineEntry() {
        override val id: Long get() = entity.id
        override val sortEpochMs: Long get() = entity.dateEpochMs
    }

    data class Expense(val entity: ExpenseEntity) : TimelineEntry() {
        override val id: Long get() = entity.id
        override val sortEpochMs: Long get() = entity.dateEpochMs
    }

    data class Service(val entity: ServiceEntity) : TimelineEntry() {
        override val id: Long get() = entity.id
        override val sortEpochMs: Long get() = entity.dateEpochMs
    }

    data class Reminder(val entity: ReminderEntity) : TimelineEntry() {
        override val id: Long get() = entity.id
        override val sortEpochMs: Long
            get() = entity.nextDueDateEpochMs ?: entity.createdAtEpochMs
    }
}

sealed class DashboardSnapshot {
    data object Empty : DashboardSnapshot()
    data class Data(
        val vehicle: VehicleEntity,
        val averageConsumptionLPer100: Double?,
        val lastConsumptionLPer100: Double?,
        val recentRefuellings: List<RefuellingEntity>,
        val activeReminders: List<ReminderEntity>,
        val recentExpenses: List<ExpenseEntity> = emptyList(),
        val recentServices: List<ServiceEntity> = emptyList(),
        val timeline: List<TimelineEntry> = emptyList(),
    ) : DashboardSnapshot()
}
