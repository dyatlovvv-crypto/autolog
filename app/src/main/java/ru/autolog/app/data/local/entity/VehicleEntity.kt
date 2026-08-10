package ru.autolog.app.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "vehicles",
    indices = [
        Index(value = ["isActive"]),
    ],
)
data class VehicleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    /** Display name / model shown in the switcher (required on first registration). */
    val name: String,
    val manufacturer: String? = null,
    val model: String? = null,
    val plate: String? = null,
    /** Odometer at registration or last known reading (km). */
    val currentOdometerKm: Int,
    /** Fuel tank capacity in litres (required). */
    val tankCapacityLiters: Double,
    val year: Int? = null,
    val fuelTypeDefault: String? = null,
    /** Currently selected vehicle on the dashboard (at most one should be true). */
    val isActive: Boolean = false,
    val createdAtEpochMs: Long,
    val updatedAtEpochMs: Long,
)
