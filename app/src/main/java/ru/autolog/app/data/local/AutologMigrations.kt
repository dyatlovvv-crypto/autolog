package ru.autolog.app.data.local

import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

object AutologMigrations {
    val MIGRATION_1_2 = object : Migration(1, 2) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL("ALTER TABLE services ADD COLUMN checklistJson TEXT")
            db.execSQL("ALTER TABLE services ADD COLUMN photoPathsJson TEXT")
        }
    }

    /**
     * Adds reminder FK on services, composite indexes for hot queries.
     * Copies every services row; does not drop user data.
     */
    val MIGRATION_2_3 = object : Migration(2, 3) {
        override fun migrate(db: SupportSQLiteDatabase) {
            db.execSQL(
                """
                UPDATE services SET linkedReminderId = NULL
                WHERE linkedReminderId IS NOT NULL
                  AND linkedReminderId NOT IN (SELECT id FROM reminders)
                """.trimIndent(),
            )

            db.execSQL(
                "CREATE TABLE IF NOT EXISTS `services_new` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `vehicleId` INTEGER NOT NULL, `dateEpochMs` INTEGER NOT NULL, `odometerKm` INTEGER NOT NULL, `totalCost` REAL NOT NULL, `serviceType` TEXT NOT NULL, `description` TEXT, `checklistJson` TEXT, `photoPathsJson` TEXT, `doneAtPurchase` INTEGER NOT NULL, `linkedReminderId` INTEGER, `createdAtEpochMs` INTEGER NOT NULL, FOREIGN KEY(`vehicleId`) REFERENCES `vehicles`(`id`) ON UPDATE NO ACTION ON DELETE CASCADE , FOREIGN KEY(`linkedReminderId`) REFERENCES `reminders`(`id`) ON UPDATE NO ACTION ON DELETE SET NULL )",
            )
            db.execSQL(
                """
                INSERT INTO `services_new` (
                  `id`, `vehicleId`, `dateEpochMs`, `odometerKm`, `totalCost`, `serviceType`,
                  `description`, `checklistJson`, `photoPathsJson`, `doneAtPurchase`,
                  `linkedReminderId`, `createdAtEpochMs`
                )
                SELECT
                  `id`, `vehicleId`, `dateEpochMs`, `odometerKm`, `totalCost`, `serviceType`,
                  `description`, `checklistJson`, `photoPathsJson`, `doneAtPurchase`,
                  `linkedReminderId`, `createdAtEpochMs`
                FROM `services`
                """.trimIndent(),
            )
            db.execSQL("DROP TABLE `services`")
            db.execSQL("ALTER TABLE `services_new` RENAME TO `services`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_services_vehicleId` ON `services` (`vehicleId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_services_dateEpochMs` ON `services` (`dateEpochMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_services_vehicleId_dateEpochMs` ON `services` (`vehicleId`, `dateEpochMs`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_services_linkedReminderId` ON `services` (`linkedReminderId`)",
            )
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_services_vehicleId_serviceType_dateEpochMs` ON `services` (`vehicleId`, `serviceType`, `dateEpochMs`)",
            )

            db.execSQL("DROP INDEX IF EXISTS `index_refuellings_odometerKm`")
            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_refuellings_vehicleId_isTankFull_dateEpochMs` ON `refuellings` (`vehicleId`, `isTankFull`, `dateEpochMs`)",
            )

            db.execSQL(
                "CREATE INDEX IF NOT EXISTS `index_reminders_isActive_showPhoneNotifications_nextDueDateEpochMs` ON `reminders` (`isActive`, `showPhoneNotifications`, `nextDueDateEpochMs`)",
            )
        }
    }
}
