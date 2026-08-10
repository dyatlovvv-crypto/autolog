package ru.autolog.app.data.local

import android.content.Context
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.google.common.truth.Truth.assertThat
import kotlinx.coroutines.test.runTest
import org.junit.After
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import ru.autolog.app.data.local.entity.RefuellingEntity
import ru.autolog.app.data.local.entity.ReminderEntity
import ru.autolog.app.data.local.entity.VehicleEntity
import ru.autolog.app.domain.VehicleLimits

@RunWith(AndroidJUnit4::class)
class AutologDatabaseTest {

    private lateinit var db: AutologDatabase

    @Before
    fun setUp() {
        val context = ApplicationProvider.getApplicationContext<Context>()
        db = Room.inMemoryDatabaseBuilder(context, AutologDatabase::class.java)
            .allowMainThreadQueries()
            .build()
    }

    @After
    fun tearDown() {
        db.close()
    }

    @Test
    fun insertVehicle_andCascadeDeleteRefuellings() = runTest {
        val now = 1_700_000_000_000L
        val vehicleId = db.vehicleDao().insert(
            VehicleEntity(
                name = "Octavia",
                currentOdometerKm = 50_000,
                tankCapacityLiters = 50.0,
                isActive = true,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )

        db.refuellingDao().insert(
            RefuellingEntity(
                vehicleId = vehicleId,
                dateEpochMs = now,
                odometerKm = 50_100,
                volumeLiters = 40.0,
                pricePerLiter = 55.0,
                totalCost = 2_200.0,
                isTankFull = true,
                createdAtEpochMs = now,
            ),
        )

        assertThat(db.refuellingDao().getByVehicle(vehicleId)).hasSize(1)

        db.vehicleDao().delete(db.vehicleDao().getById(vehicleId)!!)

        assertThat(db.refuellingDao().getByVehicle(vehicleId)).isEmpty()
    }

    @Test
    fun reminderFlags_persist() = runTest {
        val now = 1_700_000_000_000L
        val vehicleId = db.vehicleDao().insert(
            VehicleEntity(
                name = "Polo",
                currentOdometerKm = 10_000,
                tankCapacityLiters = 45.0,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )

        val reminderId = db.reminderDao().insert(
            ReminderEntity(
                vehicleId = vehicleId,
                title = "Замена масла",
                intervalKm = 7_500,
                intervalDays = 365,
                baselineOdometerKm = 10_000,
                baselineDateEpochMs = now,
                forecastFromFirstRecord = true,
                showPhoneNotifications = false,
                doneAtPurchase = true,
                createdAtEpochMs = now,
                updatedAtEpochMs = now,
            ),
        )

        val reminder = db.reminderDao().getById(reminderId)!!
        assertThat(reminder.forecastFromFirstRecord).isTrue()
        assertThat(reminder.showPhoneNotifications).isFalse()
        assertThat(reminder.doneAtPurchase).isTrue()
    }

    @Test
    fun vehicleLimit_allowsUpToMax() = runTest {
        val now = 1L
        repeat(VehicleLimits.MAX) { index ->
            assertThat(VehicleLimits.canAdd(index)).isTrue()
            db.vehicleDao().insert(
                VehicleEntity(
                    name = "Car $index",
                    currentOdometerKm = 1_000,
                    tankCapacityLiters = 40.0,
                    createdAtEpochMs = now,
                    updatedAtEpochMs = now,
                ),
            )
        }
        assertThat(db.vehicleDao().count()).isEqualTo(VehicleLimits.MAX)
        assertThat(VehicleLimits.canAdd(db.vehicleDao().count())).isFalse()
    }
}
