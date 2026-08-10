package ru.autolog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.autolog.app.data.local.entity.ServiceEntity

@Dao
interface ServiceDao {
    @Query(
        """
        SELECT * FROM services
        WHERE vehicleId = :vehicleId
        ORDER BY dateEpochMs DESC, odometerKm DESC
        """,
    )
    fun observeByVehicle(vehicleId: Long): Flow<List<ServiceEntity>>

    @Query(
        """
        SELECT * FROM services
        WHERE vehicleId = :vehicleId
        ORDER BY dateEpochMs ASC, odometerKm ASC
        """,
    )
    suspend fun getByVehicleOldestFirst(vehicleId: Long): List<ServiceEntity>

    @Query(
        """
        SELECT * FROM services
        WHERE vehicleId = :vehicleId AND serviceType = :serviceType
        ORDER BY dateEpochMs ASC, odometerKm ASC
        LIMIT 1
        """,
    )
    suspend fun getFirstOfType(vehicleId: Long, serviceType: String): ServiceEntity?

    @Query(
        """
        SELECT * FROM services
        WHERE vehicleId = :vehicleId AND serviceType = :serviceType
        ORDER BY dateEpochMs DESC, odometerKm DESC
        LIMIT 1
        """,
    )
    suspend fun getLatestOfType(vehicleId: Long, serviceType: String): ServiceEntity?

    @Query("SELECT * FROM services WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ServiceEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(service: ServiceEntity): Long

    @Update
    suspend fun update(service: ServiceEntity)

    @Delete
    suspend fun delete(service: ServiceEntity)

    @Query("DELETE FROM services WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicle(vehicleId: Long)
}
