package ru.autolog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "refuellings",
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
        Index(value = ["vehicleId", "isTankFull", "dateEpochMs"]),
    ],
)
data class RefuellingEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateEpochMs: Long,
    val odometerKm: Int,
    val volumeLiters: Double,
    val pricePerLiter: Double,
    val totalCost: Double,
    val fuelType: String? = null,
    /** Full tank — used for segment consumption (L/100 km) between full fills. */
    val isTankFull: Boolean = false,
    val stationName: String? = null,
    val note: String? = null,
    /**
     * Cached L/100 km for the segment ending at this fill (full-tank chain).
     * Null when there is no previous full-tank refuelling to compare with.
     */
    val consumptionLPer100Km: Double? = null,
    val createdAtEpochMs: Long,
)
