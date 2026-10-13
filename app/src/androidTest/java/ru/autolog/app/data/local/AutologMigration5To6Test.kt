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
class AutologMigration5To6Test {

    private val dbName = "autolog-migration-5-6.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AutologDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate5To6_addsVehicleDocumentsAndKeepsVehicles() {
        helper.createDatabase(dbName, 5).apply {
            execSQL(
                "INSERT INTO vehicles (id, name, currentOdometerKm, tankCapacityLiters, isActive, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES (1, 'Polo', 80820, 55.0, 1, 1700000000000, 1700000000000)",
            )
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            6,
            true,
            AutologMigrations.MIGRATION_5_6,
        ).use { db ->
            assertThat(db.version).isEqualTo(6)
            db.query("SELECT COUNT(*) FROM vehicles").use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(1)
            }
            db.query("SELECT COUNT(*) FROM vehicle_documents").use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(0)
            }
            db.query(
                "SELECT COUNT(*) FROM sqlite_master WHERE type = 'index' AND name = ?",
                arrayOf("index_vehicle_documents_vehicleId"),
            ).use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(1)
            }
        }
    }
}
