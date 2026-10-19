package ru.autolog.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ru.autolog.app.data.local.dao.ExpenseDao
import ru.autolog.app.data.local.dao.NoteDao
import ru.autolog.app.data.local.dao.RefuellingDao
import ru.autolog.app.data.local.dao.ReminderDao
import ru.autolog.app.data.local.dao.ServiceDao
import ru.autolog.app.data.local.dao.VehicleDao
import ru.autolog.app.data.local.dao.VehicleDocumentDao
import ru.autolog.app.data.local.entity.ExpenseEntity
import ru.autolog.app.data.local.entity.NoteEntity
import ru.autolog.app.data.local.entity.RefuellingEntity
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.data.local.entity.ServiceEntity
import ru.autolog.app.data.local.entity.VehicleDocumentEntity
import ru.autolog.app.data.local.entity.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class,
        RefuellingEntity::class,
        ExpenseEntity::class,
        ServiceEntity::class,
        ReminderEntity::class,
        NoteEntity::class,
        VehicleDocumentEntity::class,
    ],
    version = 7,
    exportSchema = true,
)
abstract class AutologDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun refuellingDao(): RefuellingDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun serviceDao(): ServiceDao
    abstract fun reminderDao(): ReminderDao
    abstract fun noteDao(): NoteDao
    abstract fun vehicleDocumentDao(): VehicleDocumentDao

    companion object {
        const val NAME = "autolog.db"

        @Volatile
        private var instance: AutologDatabase? = null

        fun getInstance(context: Context): AutologDatabase {
            return instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AutologDatabase::class.java,
                    NAME,
                )
                    .addMigrations(
                        AutologMigrations.MIGRATION_1_2,
                        AutologMigrations.MIGRATION_2_3,
                        AutologMigrations.MIGRATION_3_4,
                        AutologMigrations.MIGRATION_4_5,
                        AutologMigrations.MIGRATION_5_6,
                        AutologMigrations.MIGRATION_6_7,
                    )
                    // Never wipe user data on schema mismatch. Add an explicit Migration instead.
                    .build()
                    .also { instance = it }
            }
        }
    }
}
