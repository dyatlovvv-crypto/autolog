package ru.autolog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.autolog.app.data.local.entity.ReminderEntity

@Dao
interface ReminderDao {
    @Query(
        """
        SELECT * FROM reminders
        WHERE vehicleId = :vehicleId
        ORDER BY isActive DESC, nextDueDateEpochMs ASC
        """,
    )
    fun observeByVehicle(vehicleId: Long): Flow<List<ReminderEntity>>

    @Query(
        """
        SELECT * FROM reminders
        WHERE isActive = 1 AND showPhoneNotifications = 1
        ORDER BY nextDueDateEpochMs ASC
        """,
    )
    suspend fun getActiveWithNotifications(): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ReminderEntity?

    @Query(
        """
        SELECT * FROM reminders
        WHERE vehicleId = :vehicleId AND isActive = 1
        ORDER BY nextDueDateEpochMs ASC
        """,
    )
    suspend fun getActiveByVehicle(vehicleId: Long): List<ReminderEntity>

    @Query("SELECT * FROM reminders WHERE vehicleId = :vehicleId")
    suspend fun getByVehicle(vehicleId: Long): List<ReminderEntity>

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(reminder: ReminderEntity): Long

    @Update
    suspend fun update(reminder: ReminderEntity)

    @Delete
    suspend fun delete(reminder: ReminderEntity)

    @Query("DELETE FROM reminders WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicle(vehicleId: Long)
}
