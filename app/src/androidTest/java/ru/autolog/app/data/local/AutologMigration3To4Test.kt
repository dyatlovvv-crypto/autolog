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
class AutologMigration3To4Test {

    private val dbName = "autolog-migration-3-4.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AutologDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate3To4_addsNotesTableAndKeepsVehicles() {
        helper.createDatabase(dbName, 3).apply {
            execSQL(
                "INSERT INTO vehicles (id, name, currentOdometerKm, tankCapacityLiters, isActive, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES (1, 'Polo', 80820, 55.0, 1, 1700000000000, 1700000000000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            4,
            true,
            AutologMigrations.MIGRATION_3_4,
        ).use { db ->
            assertThat(db.version).isEqualTo(4)
            db.query("SELECT COUNT(*) FROM vehicles").use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(1)
            }
            db.query("SELECT name FROM vehicles WHERE id = 1").use { c ->
                check(c.moveToFirst())
                assertThat(c.getString(0)).isEqualTo("Polo")
            }
            db.query("SELECT COUNT(*) FROM notes").use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(0)
            }
            db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("index_notes_vehicleId"),
            ).use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(1)
            }
        }
    }
}
