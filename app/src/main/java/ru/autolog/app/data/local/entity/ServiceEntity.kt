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
    ],
    indices = [
        Index("vehicleId"),
        Index("dateEpochMs"),
        Index(value = ["vehicleId", "dateEpochMs"]),
    ],
)
data class ServiceEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateEpochMs: Long,
    val odometerKm: Int,
    val totalCost: Double,
    /** e.g. oil, filters, tires, inspection */
    val serviceType: String,
    val description: String? = null,
    /** Baseline service performed at vehicle purchase. */
    val doneAtPurchase: Boolean = false,
    /** Optional link to the reminder this service closed. */
    val linkedReminderId: Long? = null,
    val createdAtEpochMs: Long,
)
