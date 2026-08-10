package ru.autolog.app.domain.consumption

/**
 * Fuel economy helpers. Consumption is L/100 km between full-tank fills
 * (Drivvo-style segment: volume summed from newer full fill back to previous full fill).
 */
object ConsumptionCalculator {
    data class Segment(
        val distanceKm: Int,
        val volumeLiters: Double,
    ) {
        val litersPer100Km: Double?
            get() = if (distanceKm > 0 && volumeLiters > 0) {
                volumeLiters * 100.0 / distanceKm
            } else {
                null
            }
    }

    /**
     * @param fullTankNewestFirst refuellings with isTankFull, newest first
     * @param allNewestFirst all refuellings for the vehicle, newest first (to sum volumes between full tanks)
     */
    fun lastFullTankSegment(
        fullTankNewestFirst: List<FullTankPoint>,
        allNewestFirst: List<RefuelPoint>,
    ): Segment? {
        if (fullTankNewestFirst.size < 2) return null
        val newer = fullTankNewestFirst[0]
        val older = fullTankNewestFirst[1]
        val distance = newer.odometerKm - older.odometerKm
        if (distance <= 0) return null
        val volume = allNewestFirst
            .filter { it.odometerKm > older.odometerKm && it.odometerKm <= newer.odometerKm }
            .sumOf { it.volumeLiters }
            .takeIf { it > 0 }
            ?: return null
        return Segment(distanceKm = distance, volumeLiters = volume)
    }

    fun lifetimeAverage(
        fullTankOldestFirst: List<FullTankPoint>,
        allBetween: List<RefuelPoint>,
    ): Segment? {
        if (fullTankOldestFirst.size < 2) return null
        val first = fullTankOldestFirst.first()
        val last = fullTankOldestFirst.last()
        val distance = last.odometerKm - first.odometerKm
        if (distance <= 0) return null
        val volume = allBetween
            .filter { it.odometerKm > first.odometerKm && it.odometerKm <= last.odometerKm }
            .sumOf { it.volumeLiters }
            .takeIf { it > 0 }
            ?: return null
        return Segment(distanceKm = distance, volumeLiters = volume)
    }

    data class FullTankPoint(val odometerKm: Int, val dateEpochMs: Long)
    data class RefuelPoint(val odometerKm: Int, val volumeLiters: Double, val dateEpochMs: Long)
}
