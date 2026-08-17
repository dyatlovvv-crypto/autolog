package ru.autolog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "services",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
        ForeignKey(
            entity = ReminderEntity::class,
            parentColumns = ["id"],
            childColumns = ["linkedReminderId"],
            onDelete = ForeignKey.SET_NULL,
        ),
    ],
    indices = [
        Index("vehicleId"),
        Index("dateEpochMs"),
        Index(value = ["vehicleId", "dateEpochMs"]),
        Index("linkedReminderId"),
        Index(value = ["vehicleId", "serviceType", "dateEpochMs"]),
    ],
)
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateEpochMs: Long,
    val odometerKm: Int,
    val totalCost: Double,
    /** ServiceKind.name: TO / REPAIR / PLANNED */
    val serviceType: String,
    val description: String? = null,
    /** JSON array of {id,text,done} checklist items. */
    val checklistJson: String? = null,
    /** JSON array of absolute local photo paths. */
    val photoPathsJson: String? = null,
    /** Baseline service performed at vehicle purchase. */
    val doneAtPurchase: Boolean = false,
    /** Optional link to the reminder this service closed. */
    val linkedReminderId: Long? = null,
    val createdAtEpochMs: Long,
)
