package ru.autolog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.autolog.app.data.local.entity.ExpenseEntity

@Dao
interface ExpenseDao {
    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId
        ORDER BY dateEpochMs DESC
        """,
    )
    fun observeByVehicle(vehicleId: Long): Flow<List<ExpenseEntity>>

    @Query(
        """
        SELECT * FROM expenses
        WHERE vehicleId = :vehicleId
        ORDER BY dateEpochMs DESC
        """,
    )
    suspend fun getByVehicle(vehicleId: Long): List<ExpenseEntity>

    @Query("SELECT * FROM expenses WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): ExpenseEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(expense: ExpenseEntity): Long

    @Update
    suspend fun update(expense: ExpenseEntity)

    @Delete
    suspend fun delete(expense: ExpenseEntity)

    @Query("DELETE FROM expenses WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicle(vehicleId: Long)
}
