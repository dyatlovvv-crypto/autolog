package ru.autolog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "reminders",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index("vehicleId"),
        Index("nextDueDateEpochMs"),
        Index(value = ["vehicleId", "isActive"]),
        Index(value = ["isActive", "showPhoneNotifications", "nextDueDateEpochMs"]),
    ],
)
data class ReminderEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val title: String,
    /** Mileage interval, e.g. 7500 km. Null = calendar-only. */
    val intervalKm: Int? = null,
    /** Calendar interval in days, e.g. 365. Null = mileage-only. Whichever comes first wins. */
    val intervalDays: Int? = null,
    val baselineOdometerKm: Int,
    val baselineDateEpochMs: Long,
    /**
     * When true, interval is measured from the earliest historical service/odometer
     * record instead of the latest completed service.
     */
    val forecastFromFirstRecord: Boolean = false,
    /** Enable phone notifications via WorkManager / AlarmManager. */
    val showPhoneNotifications: Boolean = true,
    /** Marks purchase-time baseline for ownership start. */
    val doneAtPurchase: Boolean = false,
    /** Rolling average km/day derived from refuellings / odometer updates. */
    val avgKmPerDay: Double? = null,
    val remainingKm: Int? = null,
    val remainingDays: Int? = null,
    val nextDueDateEpochMs: Long? = null,
    val nextDueOdometerKm: Int? = null,
    val isActive: Boolean = true,
    val lastNotifiedAtEpochMs: Long? = null,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
