package ru.autolog.app.data.local.entity

import androidx.room.Entity
import androidx.room.ForeignKey
import androidx.room.Index
import androidx.room.PrimaryKey

/** Per-vehicle attached file (PDF / photo): insurance, license, STS, etc. */
@Entity(
    tableName = "vehicle_documents",
    foreignKeys = [
        ForeignKey(
            entity = VehicleEntity::class,
            parentColumns = ["id"],
            childColumns = ["vehicleId"],
            onDelete = ForeignKey.CASCADE,
        ),
    ],
    indices = [
        Index(value = ["vehicleId"]),
        Index(value = ["vehicleId", "createdAtEpochMs"]),
    ],
)
data class VehicleDocumentEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val vehicleId: Long,
    /** User-facing name, e.g. file name or "ОСАГО". */
    val title: String,
    val mimeType: String,
    /** Absolute path under app filesDir. */
    val filePath: String,
    val createdAtEpochMs: Long,
)
