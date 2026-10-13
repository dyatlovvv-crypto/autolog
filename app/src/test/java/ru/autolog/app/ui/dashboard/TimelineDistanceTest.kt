package ru.autolog.app.ui.dashboard

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import ru.autolog.app.data.local.entity.RefuellingEntity
import ru.autolog.app.data.local.entity.ServiceEntity
import ru.autolog.app.data.repository.TimelineEntry
import ru.autolog.app.domain.ServiceKind

class TimelineDistanceTest {

    @Test
    fun kmSincePreviousRefuel_usesOlderFillInFeed() {
        val newer = refuel(id = 2, odo = 80_820, t = 200)
        val older = refuel(id = 1, odo = 80_582, t = 100)
        val timeline = listOf(TimelineEntry.Refuel(newer), TimelineEntry.Refuel(older))
        assertEquals(238, TimelineDistance.kmSincePreviousRefuel(timeline, 0))
        assertNull(TimelineDistance.kmSincePreviousRefuel(timeline, 1))
    }

    @Test
    fun kmSincePreviousRefuel_skipsNonRefuelRows() {
        val newer = refuel(id = 2, odo = 50_500, t = 300)
        val wash = service(id = 9, odo = 50_200, t = 200, type = ServiceKind.REPAIR.name)
        val older = refuel(id = 1, odo = 50_000, t = 100)
        val timeline = listOf(
            TimelineEntry.Refuel(newer),
            TimelineEntry.Service(wash),
            TimelineEntry.Refuel(older),
        )
        assertEquals(500, TimelineDistance.kmSincePreviousRefuel(timeline, 0))
        assertNull(TimelineDistance.kmSincePreviousRefuel(timeline, 1))
    }

    @Test
    fun consumption_usesStoredSegmentValue() {
        val newer = refuel(id = 2, odo = 50_500, t = 200, full = true, consumption = 8.0)
        val older = refuel(id = 1, odo = 50_000, t = 100, full = true, consumption = null)
        val timeline = listOf(TimelineEntry.Refuel(newer), TimelineEntry.Refuel(older))
        assertEquals(8.0, TimelineDistance.consumptionLPer100SincePreviousFull(timeline, 0)!!, 0.001)
        assertNull(TimelineDistance.consumptionLPer100SincePreviousFull(timeline, 1))
    }

    @Test
    fun consumption_recomputesWhenStoredMissing() {
        val newer = refuel(id = 2, odo = 50_500, t = 300, full = true, liters = 40.0)
        val older = refuel(id = 1, odo = 50_000, t = 100, full = true, liters = 45.0)
        val timeline = listOf(TimelineEntry.Refuel(newer), TimelineEntry.Refuel(older))
        // 40 L / 500 km → 8.0 L/100
        assertEquals(8.0, TimelineDistance.consumptionLPer100SincePreviousFull(timeline, 0)!!, 0.001)
    }

    @Test
    fun consumption_nullForPartialFill() {
        val partial = refuel(id = 2, odo = 50_200, t = 200, full = false, liters = 20.0)
        val older = refuel(id = 1, odo = 50_000, t = 100, full = true, liters = 45.0)
        val timeline = listOf(TimelineEntry.Refuel(partial), TimelineEntry.Refuel(older))
        assertNull(TimelineDistance.consumptionLPer100SincePreviousFull(timeline, 0))
    }

    @Test
    fun kmSincePreviousTo_onlyCountsToAndStaysOffRoad() {
        val newer = service(id = 3, odo = 116_000, t = 300, type = ServiceKind.TO.name)
        val repair = service(id = 2, odo = 115_500, t = 200, type = ServiceKind.REPAIR.name)
        val older = service(id = 1, odo = 115_000, t = 100, type = ServiceKind.TO.name)
        val timeline = listOf(
            TimelineEntry.Service(newer),
            TimelineEntry.Service(repair),
            TimelineEntry.Service(older),
        )
        assertEquals(1_000, TimelineDistance.kmSincePreviousTo(timeline, 0))
        assertNull(TimelineDistance.kmSincePreviousTo(timeline, 1))
        assertNull(TimelineDistance.kmSincePreviousTo(timeline, 2))
    }

    @Test
    fun kmSincePreviousTo_showsZeroWhenOdometerUnchanged() {
        val newer = service(id = 2, odo = 116_000, t = 200, type = ServiceKind.TO.name)
        val older = service(id = 1, odo = 116_000, t = 100, type = ServiceKind.TO.name)
        val timeline = listOf(TimelineEntry.Service(newer), TimelineEntry.Service(older))
        assertEquals(0, TimelineDistance.kmSincePreviousTo(timeline, 0))
        assertNull(TimelineDistance.kmSincePreviousTo(timeline, 1))
    }

    @Test
    fun formatKm_usesNbspStyleGrouping() {
        assertEquals("1 000", TimelineDistance.formatKm(1_000))
        assertEquals("238", TimelineDistance.formatKm(238))
    }

    private fun refuel(
        id: Long,
        odo: Int,
        t: Long,
        full: Boolean = false,
        liters: Double = 20.0,
        consumption: Double? = null,
    ) = RefuellingEntity(
        id = id,
        vehicleId = 1,
        dateEpochMs = t,
        odometerKm = odo,
        volumeLiters = liters,
        pricePerLiter = 50.0,
        totalCost = liters * 50.0,
        isTankFull = full,
        consumptionLPer100Km = consumption,
        createdAtEpochMs = t,
    )

    private fun service(id: Long, odo: Int, t: Long, type: String) = ServiceEntity(
        id = id,
        vehicleId = 1,
        dateEpochMs = t,
        odometerKm = odo,
        totalCost = 1000.0,
        serviceType = type,
        createdAtEpochMs = t,
    )
}
