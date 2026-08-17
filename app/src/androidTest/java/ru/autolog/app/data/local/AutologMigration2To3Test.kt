package ru.autolog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.sqlite.db.SupportSQLiteDatabase
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutologMigration2To3Test {

    private val context: Context = ApplicationProvider.getApplicationContext()
    private val dbName = "autolog-migration-2-3.db"

    @Before
    fun setUp() {
        context.deleteDatabase(dbName)
    }

    @After
    fun tearDown() {
        context.deleteDatabase(dbName)
    }

    @Test
    fun migrate2To3_keepsVehiclesRefuelsServicesAndChecklistJson() {
        val helper = FrameworkSQLiteOpenHelperFactory().create(
            androidx.sqlite.db.SupportSQLiteOpenHelper.Configuration.builder(context)
                .name(dbName)
                .callback(V2Callback())
                .build(),
        )
        try {
            helper.writableDatabase.use { db ->
                seedV2(db)
                assertThat(scalar(db, "SELECT COUNT(*) FROM vehicles")).isEqualTo(2)
                assertThat(scalar(db, "SELECT COUNT(*) FROM refuellings")).isEqualTo(2)
                assertThat(scalar(db, "SELECT COUNT(*) FROM services")).isEqualTo(2)
                assertThat(scalar(db, "SELECT COUNT(*) FROM reminders")).isEqualTo(1)
                assertThat(
                    text(db, "SELECT checklistJson FROM services WHERE id = 11"),
                ).contains("масло")
            }
        } finally {
            helper.close()
        }

        val room = Room.databaseBuilder(context, AutologDatabase::class.java, dbName)
            .addMigrations(AutologMigrations.MIGRATION_1_2, AutologMigrations.MIGRATION_2_3)
            .build()
        try {
            room.openHelper.writableDatabase.use { db ->
                assertThat(db.version).isEqualTo(3)
                assertThat(scalar(db, "SELECT COUNT(*) FROM vehicles")).isEqualTo(2)
                assertThat(scalar(db, "SELECT COUNT(*) FROM refuellings")).isEqualTo(2)
                assertThat(scalar(db, "SELECT COUNT(*) FROM services")).isEqualTo(2)
                assertThat(scalar(db, "SELECT COUNT(*) FROM reminders")).isEqualTo(1)
                assertThat(scalar(db, "SELECT COUNT(*) FROM expenses")).isEqualTo(1)
                assertThat(text(db, "SELECT name FROM vehicles WHERE id = 1")).isEqualTo("Polo")
                assertThat(text(db, "SELECT name FROM vehicles WHERE id = 2")).isEqualTo("Fiesta")
                assertThat(
                    text(db, "SELECT checklistJson FROM services WHERE id = 11"),
                ).contains("масло")
                assertThat(
                    text(db, "SELECT photoPathsJson FROM services WHERE id = 11"),
                ).contains("/tmp/photo.jpg")
                assertThat(scalar(db, "SELECT odometerKm FROM refuellings WHERE id = 21")).isEqualTo(80_820)
                assertThat(
                    indexExists(db, "index_refuellings_vehicleId_isTankFull_dateEpochMs"),
                ).isTrue()
                assertThat(
                    indexExists(db, "index_services_vehicleId_serviceType_dateEpochMs"),
                ).isTrue()
                assertThat(
                    indexExists(db, "index_services_linkedReminderId"),
                ).isTrue()
                assertThat(
                    indexExists(db, "index_reminders_isActive_showPhoneNotifications_nextDueDateEpochMs"),
                ).isTrue()
                assertThat(indexExists(db, "index_refuellings_odometerKm")).isFalse()
            }
        } finally {
            room.close()
        }
    }

    private class V2Callback : androidx.sqlite.db.SupportSQLiteOpenHelper.Callback(2) {
        override fun onCreate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `vehicles` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `name` TEXT NOT NULL,
                  `manufacturer` TEXT,
                  `model` TEXT,
                  `plate` TEXT,
                  `currentOdometerKm` INTEGER NOT NULL,
                  `tankCapacityLiters` REAL NOT NULL,
                  `year` INTEGER,
                  `fuelTypeDefault` TEXT,
                  `isActive` INTEGER NOT NULL,
                  `createdAtEpochMs` INTEGER NOT NULL,
                  `updatedAtEpochMs` INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_vehicles_isActive` ON `vehicles` (`isActive`)")
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `refuellings` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `vehicleId` INTEGER NOT NULL,
                  `dateEpochMs` INTEGER NOT NULL,
                  `odometerKm` INTEGER NOT NULL,
                  `volumeLiters` REAL NOT NULL,
                  `pricePerLiter` REAL NOT NULL,
                  `totalCost` REAL NOT NULL,
                  `fuelType` TEXT,
                  `isTankFull` INTEGER NOT NULL,
                  `stationName` TEXT,
                  `note` TEXT,
                  `consumptionLPer100Km` REAL,
                  `createdAtEpochMs` INTEGER NOT NULL,
                  FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_refuellings_vehicleId` ON `refuellings` (`vehicleId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_refuellings_odometerKm` ON `refuellings` (`odometerKm`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_refuellings_dateEpochMs` ON `refuellings` (`dateEpochMs`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_refuellings_vehicleId_dateEpochMs` ON `refuellings` (`vehicleId`, `dateEpochMs`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `expenses` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `vehicleId` INTEGER NOT NULL,
                  `dateEpochMs` INTEGER NOT NULL,
                  `odometerKm` INTEGER,
                  `totalCost` REAL NOT NULL,
                  `category` TEXT NOT NULL,
                  `description` TEXT,
                  `createdAtEpochMs` INTEGER NOT NULL,
                  FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_vehicleId` ON `expenses` (`vehicleId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_expenses_dateEpochMs` ON `expenses` (`dateEpochMs`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_expenses_vehicleId_dateEpochMs` ON `expenses` (`vehicleId`, `dateEpochMs`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `reminders` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `vehicleId` INTEGER NOT NULL,
                  `title` TEXT NOT NULL,
                  `intervalKm` INTEGER,
                  `intervalDays` INTEGER,
                  `baselineOdometerKm` INTEGER NOT NULL,
                  `baselineDateEpochMs` INTEGER NOT NULL,
                  `forecastFromFirstRecord` INTEGER NOT NULL,
                  `showPhoneNotifications` INTEGER NOT NULL,
                  `doneAtPurchase` INTEGER NOT NULL,
                  `avgKmPerDay` REAL,
                  `remainingKm` INTEGER,
                  `remainingDays` INTEGER,
                  `nextDueDateEpochMs` INTEGER,
                  `nextDueOdometerKm` INTEGER,
                  `isActive` INTEGER NOT NULL,
                  `lastNotifiedAtEpochMs` INTEGER,
                  `createdAtEpochMs` INTEGER NOT NULL,
                  `updatedAtEpochMs` INTEGER NOT NULL,
                  FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_reminders_vehicleId` ON `reminders` (`vehicleId`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminders_nextDueDateEpochMs` ON `reminders` (`nextDueDateEpochMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminders_vehicleId_isActive` ON `reminders` (`vehicleId`, `isActive`)",
            )
            db.execSQL(
                """
                CREATE TABLE IF NOT EXISTS `services` (
                  `id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                  `vehicleId` INTEGER NOT NULL,
                  `dateEpochMs` INTEGER NOT NULL,
                  `odometerKm` INTEGER NOT NULL,
                  `totalCost` REAL NOT NULL,
                  `serviceType` TEXT NOT NULL,
                  `description` TEXT,
                  `checklistJson` TEXT,
                  `photoPathsJson` TEXT,
                  `doneAtPurchase` INTEGER NOT NULL,
                  `linkedReminderId` INTEGER,
                  `createdAtEpochMs` INTEGER NOT NULL,
                  FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_services_vehicleId` ON `services` (`vehicleId`)")
            db.execSQL("CREATE INDEX IF NOT EXISTS `index_services_dateEpochMs` ON `services` (`dateEpochMs`)")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_services_vehicleId_dateEpochMs` ON `services` (`vehicleId`, `dateEpochMs`)",
            )
        }

        override fun onUpgrade(db: SupportSQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private fun seedV2(db: SupportSQLiteDatabase) {
        val now = 1_700_000_000_000L
        db.execSQL(
            "INSERT INTO vehicles (id, name, currentOdometerKm, tankCapacityLiters, isActive, createdAtEpochMs, updatedAtEpochMs) VALUES (1, 'Polo', 80820, 55.0, 1, $now, $now)",
        )
        db.execSQL(
            "INSERT INTO vehicles (id, name, currentOdometerKm, tankCapacityLiters, isActive, createdAtEpochMs, updatedAtEpochMs) VALUES (2, 'Fiesta', 12000, 42.0, 0, $now, $now)",
        )
        db.execSQL(
            "INSERT INTO refuellings (id, vehicleId, dateEpochMs, odometerKm, volumeLiters, pricePerLiter, totalCost, isTankFull, createdAtEpochMs) VALUES (21, 1, $now, 80820, 40.0, 55.0, 2200.0, 1, $now)",
        )
        db.execSQL(
            "INSERT INTO refuellings (id, vehicleId, dateEpochMs, odometerKm, volumeLiters, pricePerLiter, totalCost, isTankFull, createdAtEpochMs) VALUES (22, 2, $now, 12000, 30.0, 50.0, 1500.0, 1, $now)",
        )
        db.execSQL(
            "INSERT INTO expenses (id, vehicleId, dateEpochMs, totalCost, category, createdAtEpochMs) VALUES (31, 1, $now, 500.0, 'wash', $now)",
        )
        db.execSQL(
            "INSERT INTO reminders (id, vehicleId, title, intervalKm, intervalDays, baselineOdometerKm, baselineDateEpochMs, forecastFromFirstRecord, showPhoneNotifications, doneAtPurchase, isActive, createdAtEpochMs, updatedAtEpochMs) VALUES (41, 1, 'Масло', 7500, 365, 80000, $now, 0, 1, 0, 1, $now, $now)",
        )
        db.execSQL(
            """
            INSERT INTO services (
              id, vehicleId, dateEpochMs, odometerKm, totalCost, serviceType, description,
              checklistJson, photoPathsJson, doneAtPurchase, linkedReminderId, createdAtEpochMs
            ) VALUES (
              11, 1, $now, 80000, 3500.0, 'TO', 'ТО',
              '[{"id":"a","text":"масло","done":true}]',
              '["/tmp/photo.jpg"]',
              0, 41, $now
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            INSERT INTO services (
              id, vehicleId, dateEpochMs, odometerKm, totalCost, serviceType,
              doneAtPurchase, linkedReminderId, createdAtEpochMs
            ) VALUES (12, 2, $now, 11000, 0.0, 'REPAIR', 0, NULL, $now)
            """.trimIndent(),
        )
    }

    private fun scalar(db: SupportSQLiteDatabase, sql: String): Long {
        db.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getLong(0)
        }
    }

    private fun text(db: SupportSQLiteDatabase, sql: String): String {
        db.query(sql).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getString(0)
        }
    }

    private fun indexExists(db: SupportSQLiteDatabase, name: String): Boolean {
        db.query(
            "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
            arrayOf(name),
        ).use { cursor ->
            check(cursor.moveToFirst())
            return cursor.getLong(0) > 0
        }
    }
}
