package ru.autolog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "expenses",
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
data class ExpenseEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    val dateEpochMs: Long,
    val odometerKm: Int? = null,
    val totalCost: Double,
    /** e.g. insurance, wash, parking, fine, other */
    val category: String,
    val description: String? = null,
    val createdAtEpochMs: Long,
)
