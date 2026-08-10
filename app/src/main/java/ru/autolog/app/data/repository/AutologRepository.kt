package ru.autolog.app.data.repository

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import ru.autolog.app.data.local.AutologDatabase
import ru.autolog.app.data.local.entity.RefuellingEntity
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.data.local.entity.VehicleEntity
import ru.autolog.app.domain.VehicleLimits
import ru.autolog.app.domain.consumption.ConsumptionCalculator
import ru.autolog.app.domain.consumption.ConsumptionCalculator.FullTankPoint
import ru.autolog.app.domain.consumption.ConsumptionCalculator.RefuelPoint
import ru.autolog.app.domain.reminder.ReminderForecast

class AutologRepository(private val db: AutologDatabase) {

    fun observeVehicles(): Flow<List<VehicleEntity>> = db.vehicleDao().observeAll()

    fun observeActiveVehicle(): Flow<VehicleEntity?> = db.vehicleDao().observeActive()

    fun observeDashboard(vehicleId: Long): Flow<DashboardSnapshot> {
        return combine(
            db.vehicleDao().observeAll().map { list -> list.find { it.id == vehicleId } },
            db.refuellingDao().observeByVehicle(vehicleId),
            db.reminderDao().observeByVehicle(vehicleId),
        ) { vehicle, refuels, reminders ->
            buildDashboard(vehicle, refuels, reminders)
        }
    }

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
    ): Result<Unit> {
        val existing = db.refuellingDao().getById(id)
            ?: return Result.failure(IllegalArgumentException("Заправка не найдена"))
        val vehicle = db.vehicleDao().getById(existing.vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        val now = System.currentTimeMillis()
        val siblings = db.refuellingDao().getByVehicle(existing.vehicleId)
            .filter { it.id != id }
        val updated = existing.copy(
            odometerKm = odometerKm,
            volumeLiters = volumeLiters,
            pricePerLiter = pricePerLiter,
            totalCost = volumeLiters * pricePerLiter,
            isTankFull = isTankFull,
            stationName = stationName?.trim()?.ifEmpty { null },
            consumptionLPer100Km = null,
        )
        val withConsumption = if (isTankFull) {
            val allNewest = (listOf(updated) + siblings).sortedByDescending { it.odometerKm }
            val fullNewest = allNewest.filter { it.isTankFull }.map {
                FullTankPoint(it.odometerKm, it.dateEpochMs)
            }
            val points = allNewest.map {
                RefuelPoint(it.odometerKm, it.volumeLiters, it.dateEpochMs)
            }
            val segment = ConsumptionCalculator.lastFullTankSegment(fullNewest, points)
            updated.copy(consumptionLPer100Km = segment?.litersPer100Km)
        } else {
            updated
        }
        db.refuellingDao().update(withConsumption)
        val maxOdo = (listOf(withConsumption) + siblings).maxOf { it.odometerKm }
            .coerceAtLeast(vehicle.currentOdometerKm)
        db.vehicleDao().update(
            vehicle.copy(currentOdometerKm = maxOdo, updatedAtEpochMs = now),
        )
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
    ): Result<Long> {
        val vehicle = db.vehicleDao().getById(vehicleId)
            ?: return Result.failure(IllegalArgumentException("Авто не найдено"))
        if (odometerKm < vehicle.currentOdometerKm) {
            return Result.failure(IllegalArgumentException("Пробег меньше текущего"))
        }
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
            consumptionLPer100Km = null,
            createdAtEpochMs = now,
        )
        val withConsumption = if (isTankFull) {
            val allNewest = listOf(draft) + existing
            val fullNewest = allNewest.filter { it.isTankFull }.map {
                FullTankPoint(it.odometerKm, it.dateEpochMs)
            }
            val points = allNewest.map {
                RefuelPoint(it.odometerKm, it.volumeLiters, it.dateEpochMs)
            }
            val segment = ConsumptionCalculator.lastFullTankSegment(fullNewest, points)
            draft.copy(consumptionLPer100Km = segment?.litersPer100Km)
        } else {
            draft
        }

        val id = db.refuellingDao().insert(withConsumption)
        db.vehicleDao().update(
            vehicle.copy(
                currentOdometerKm = odometerKm,
                updatedAtEpochMs = now,
            ),
        )
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

    suspend fun deleteRefuelling(id: Long) {
        val item = db.refuellingDao().getById(id) ?: return
        db.refuellingDao().delete(item)
        refreshRemindersForVehicle(item.vehicleId)
    }

    suspend fun deleteReminder(id: Long) {
        val item = db.reminderDao().getById(id) ?: return
        db.reminderDao().delete(item)
    }

    suspend fun getRefuelling(id: Long) = db.refuellingDao().getById(id)

    suspend fun getReminder(id: Long) = db.reminderDao().getById(id)


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

        return DashboardSnapshot.Data(
            vehicle = vehicle,
            averageConsumptionLPer100 = lifetime,
            lastConsumptionLPer100 = last,
            recentRefuellings = refuelsNewestFirst.take(5),
            activeReminders = reminders.filter { it.isActive },
        )
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
    ) : DashboardSnapshot()
}
