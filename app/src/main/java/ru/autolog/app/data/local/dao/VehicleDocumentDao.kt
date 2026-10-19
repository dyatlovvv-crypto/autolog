package ru.autolog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow
import ru.autolog.app.data.local.entity.VehicleDocumentEntity

@Dao
interface VehicleDocumentDao {
    @Query(
        "SELECT * FROM vehicle_documents WHERE vehicleId = :vehicleId ORDER BY createdAtEpochMs DESC",
    )
    fun observeByVehicle(vehicleId: Long): Flow<List<VehicleDocumentEntity>>

    @Query("SELECT * FROM vehicle_documents WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VehicleDocumentEntity?

    @Query(
        "SELECT * FROM vehicle_documents WHERE vehicleId = :vehicleId ORDER BY createdAtEpochMs ASC",
    )
    suspend fun getByVehicle(vehicleId: Long): List<VehicleDocumentEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(doc: VehicleDocumentEntity): Long

    @Query("DELETE FROM vehicle_documents WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM vehicle_documents WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicle(vehicleId: Long)
}
