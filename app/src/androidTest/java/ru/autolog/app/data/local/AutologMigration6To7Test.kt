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
class AutologMigration6To7Test {

    private val dbName = "autolog-migration-6-7.db"

    @get:Rule
    val helper = MigrationTestHelper(
        InstrumentationRegistry.getInstrumentation(),
        AutologDatabase::class.java,
        emptyList(),
        FrameworkSQLiteOpenHelperFactory(),
    )

    @Test
    fun migrate6To7_addsStationCoordsAndKeepsRefuels() {
        helper.createDatabase(dbName, 6).apply {
            execSQL(
                "INSERT INTO vehicles (id, name, currentOdometerKm, tankCapacityLiters, isActive, createdAtEpochMs, updatedAtEpochMs) " +
                    "VALUES (1, 'Polo', 80820, 55.0, 1, 1700000000000, 1700000000000)",
            )
            execSQL(
                """
                INSERT INTO refuellings (
                  id, vehicleId, dateEpochMs, odometerKm, volumeLiters, pricePerLiter, totalCost,
                  isTankFull, stationName, createdAtEpochMs
                ) VALUES (21, 1, 1700000000000, 80820, 40.0, 55.0, 2200.0, 1, 'Shell', 1700000000000)
                """.trimIndent(),
            )
            close()
        }

        helper.runMigrationsAndValidate(
            dbName,
            7,
            true,
            AutologMigrations.MIGRATION_6_7,
        ).use { db ->
            assertThat(db.version).isEqualTo(7)
            db.query("SELECT COUNT(*) FROM refuellings").use { c ->
                check(c.moveToFirst())
                assertThat(c.getLong(0)).isEqualTo(1)
            }
            db.query("SELECT stationName, stationLat, stationLon FROM refuellings WHERE id = 21").use { c ->
                check(c.moveToFirst())
                assertThat(c.getString(0)).isEqualTo("Shell")
                assertThat(c.isNull(1)).isTrue()
                assertThat(c.isNull(2)).isTrue()
            }
        }
    }
}
