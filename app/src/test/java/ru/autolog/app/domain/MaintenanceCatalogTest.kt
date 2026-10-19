package ru.autolog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.autolog.app.data.local.entity.ReminderEntity

class MaintenanceCatalogTest {
    @Test
    fun fromTitle_matchesKinds() {
        assertEquals(MaintenanceKind.OIL, MaintenanceKind.fromTitle("Замена масла UITest"))
        assertEquals(MaintenanceKind.SPARK_PLUGS, MaintenanceKind.fromTitle("Замена свечей"))
        assertEquals(MaintenanceKind.CABIN_FILTER, MaintenanceKind.fromTitle("салонный фильтр"))
        assertEquals(MaintenanceKind.AIR_FILTER, MaintenanceKind.fromTitle("Воздушный фильтр"))
        assertEquals(MaintenanceKind.BRAKE_FLUID, MaintenanceKind.fromTitle("тормозная жидкость"))
        assertEquals(MaintenanceKind.ANTIFREEZE, MaintenanceKind.fromTitle("Антифриз"))
        assertNull(MaintenanceKind.fromTitle("Поменять дворники"))
    }

    @Test
    fun dashboardSlots_picksTwoNearestByRemainingKm() {
        val reminders = listOf(
            reminder("Замена масла", remainingKm = 5000),
            reminder("Замена свечей", remainingKm = 1200),
            reminder("Антифриз", remainingKm = 800),
            reminder("салонный фильтр", remainingKm = 3000),
        )
        val slots = MaintenanceCatalog.dashboardSlots(reminders, limit = 2)
        assertEquals(2, slots.size)
        assertEquals(MaintenanceKind.ANTIFREEZE, slots[0].kind)
        assertEquals(800, slots[0].reminder?.remainingKm)
        assertEquals(MaintenanceKind.SPARK_PLUGS, slots[1].kind)
        assertEquals(1200, slots[1].reminder?.remainingKm)
    }

    @Test
    fun dashboardSlots_padsWithEmptyKinds() {
        val slots = MaintenanceCatalog.dashboardSlots(
            listOf(reminder("Замена масла", remainingKm = 100)),
            limit = 2,
        )
        assertEquals(2, slots.size)
        assertEquals(MaintenanceKind.OIL, slots[0].kind)
        assertTrue(slots[0].reminder != null)
        assertNull(slots[1].reminder)
    }

    private fun reminder(title: String, remainingKm: Int) = ReminderEntity(
        id = title.hashCode().toLong(),
        vehicleId = 1L,
        title = title,
        baselineOdometerKm = 0,
        baselineDateEpochMs = 0L,
        remainingKm = remainingKm,
        createdAtEpochMs = 0L,
        updatedAtEpochMs = 0L,
    )
}
