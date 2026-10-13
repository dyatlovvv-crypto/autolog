package ru.autolog.app.data.local

import androidx.room.testing.MigrationTestHelper
import androidx.sqlite.db.framework.FrameworkSQLiteOpenHelperFactory
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.google.common.truth.Truth.assertThat
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class AutologMigration4To5Test {

    private val dbName = "autolog-migration-4-5.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AutologDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate4To5_mergesJournalNotesIntoOneNotepadPerVehicle() {
        val t1 = 1_700_000_000_000L
        val t2 = t1 + 86_400_000L
        helper.createDatabase(dbName, 4).apply {
            execSQL(
                "INSERT INTO vehicles (id, name, currentOdometerKm, tankCapacityLiters, isActive, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES (1, 'Polo', 80820, 55.0, 1, $t1, $t1)",
            )
            execSQL(
                "INSERT INTO notes (vehicleId, dateEpochMs, odometerKm, text, createdAtEpochMs) " +
                    "VALUES (1, $t1, 80000, 'Первая', $t1)",
            )
            execSQL(
                "INSERT INTO notes (vehicleId, dateEpochMs, odometerKm, text, createdAtEpochMs) " +
                    "VALUES (1, $t2, 80500, 'Вторая', $t2)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            5,
            true,
            AutologMigrations.MIGRATION_4_5,
        ).use { db ->
            assertThat(db.version).isEqualTo(5)
            db.query("SELECT COUNT(*) FROM notes").use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(1)
            }
            db.query("SELECT text, updatedAtEpochMs FROM notes WHERE vehicleId = 1").use { c ->
                check(c.moveToFirst())
                assertThat(c.getString(0)).isEqualTo("Первая\n\nВторая")
                assertThat(c.getLong(1)).isEqualTo(t2)
            }
            db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("index_notes_vehicleId"),
            ).use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(1)
            }
            db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("index_notes_vehicleId_dateEpochMs"),
            ).use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(0)
            }
        }
    }
}
