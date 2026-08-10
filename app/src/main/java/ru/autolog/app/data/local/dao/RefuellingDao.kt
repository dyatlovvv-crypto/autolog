package ru.autolog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.autolog.app.data.local.entity.RefuellingEntity

@Dao
interface RefuellingDao {
    @Query(
        """
        SELECT * FROM refuellings
        WHERE vehicleId = :vehicleId
        ORDER BY dateEpochMs DESC, odometerKm DESC
        """,
    )
    fun observeByVehicle(vehicleId: Long): Flow<List<RefuellingEntity>>

    @Query(
        """
        SELECT * FROM refuellings
        WHERE vehicleId = :vehicleId
        ORDER BY dateEpochMs DESC, odometerKm DESC
        """,
    )
    suspend fun getByVehicle(vehicleId: Long): List<RefuellingEntity>

    @Query(
        """
        SELECT * FROM refuellings
        WHERE vehicleId = :vehicleId AND isTankFull = 1
        ORDER BY dateEpochMs DESC, odometerKm DESC
        """,
    )
    suspend fun getFullTankByVehicle(vehicleId: Long): List<RefuellingEntity>

    @Query("SELECT * FROM refuellings WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): RefuellingEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(refuelling: RefuellingEntity): Long

    @Update
    suspend fun update(refuelling: RefuellingEntity)

    @Delete
    suspend fun delete(refuelling: RefuellingEntity)

    @Query("DELETE FROM refuellings WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicle(vehicleId: Long)
}
