package ru.autolog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.autolog.app.data.local.entity.VehicleEntity

@Dao
interface VehicleDao {
    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE ASC")
    fun observeAll(): Flow<List<VehicleEntity>>

    @Query("SELECT * FROM vehicles ORDER BY name COLLATE NOCASE ASC")
    suspend fun getAll(): List<VehicleEntity>

    @Query("SELECT COUNT(*) FROM vehicles")
    suspend fun count(): Int

    @Query("SELECT * FROM vehicles WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): VehicleEntity?

    @Query("SELECT * FROM vehicles WHERE isActive = 1 LIMIT 1")
    fun observeActive(): Flow<VehicleEntity?>

    @Query("SELECT * FROM vehicles WHERE isActive = 1 LIMIT 1")
    suspend fun getActive(): VehicleEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(vehicle: VehicleEntity): Long

    @Update
    suspend fun update(vehicle: VehicleEntity)

    @Delete
    suspend fun delete(vehicle: VehicleEntity)

    @Query("UPDATE vehicles SET isActive = 0 WHERE isActive = 1")
    suspend fun clearActive()

    @Query("UPDATE vehicles SET isActive = 1, updatedAtEpochMs = :updatedAt WHERE id = :id")
    suspend fun setActive(id: Long, updatedAt: Long)

    @Transaction
    suspend fun switchActive(id: Long, updatedAt: Long) {
        clearActive()
        setActive(id, updatedAt)
    }
}
