package ru.autolog.app.data.local.dao

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow
import ru.autolog.app.data.local.entity.NoteEntity

@Dao
interface NoteDao {
    @Query("SELECT * FROM notes WHERE vehicleId = :vehicleId LIMIT 1")
    fun observeNotepad(vehicleId: Long): Flow<NoteEntity?>

    @Query("SELECT * FROM notes WHERE vehicleId = :vehicleId LIMIT 1")
    suspend fun getNotepad(vehicleId: Long): NoteEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(note: NoteEntity): Long

    @Update
    suspend fun update(note: NoteEntity)

    @Query("DELETE FROM notes WHERE vehicleId = :vehicleId")
    suspend fun deleteByVehicle(vehicleId: Long)
}
