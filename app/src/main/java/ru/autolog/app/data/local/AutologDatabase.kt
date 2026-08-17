package ru.autolog.app.data.local

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import ru.autolog.app.data.local.dao.ExpenseDao
import ru.autolog.app.data.local.dao.RefuellingDao
import ru.autolog.app.data.local.dao.ReminderDao
import ru.autolog.app.data.local.dao.ServiceDao
import ru.autolog.app.data.local.dao.VehicleDao
import ru.autolog.app.data.local.entity.ExpenseEntity
import ru.autolog.app.data.local.entity.RefuellingEntity
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.data.local.entity.ServiceEntity
import ru.autolog.app.data.local.entity.VehicleEntity

@Database(
    entities = [
        VehicleEntity::class,
        RefuellingEntity::class,
        ExpenseEntity::class,
        ServiceEntity::class,
        ReminderEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class AutologDatabase : RoomDatabase() {
    abstract fun vehicleDao(): VehicleDao
    abstract fun refuellingDao(): RefuellingDao
    abstract fun expenseDao(): ExpenseDao
    abstract fun serviceDao(): ServiceDao
    abstract fun reminderDao(): ReminderDao

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
                    )
                    // Never wipe user data on schema mismatch. Add an explicit Migration instead.
                    .build()
                    .also { instance = it }
            }
        }
    }
}
