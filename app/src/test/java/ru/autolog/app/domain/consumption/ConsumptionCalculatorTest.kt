package ru.autolog.app.domain.consumption

import com.google.common.truth.Truth.assertThat
import org.junit.Test
import ru.autolog.app.domain.consumption.ConsumptionCalculator.FullTankPoint
import ru.autolog.app.domain.consumption.ConsumptionCalculator.RefuelPoint

class ConsumptionCalculatorTest {

    @Test
    fun lastFullTankSegment_computesLitersPer100Km() {
        // Full at 10_000 km, then partials, then full at 10_500 km with 40 L total in segment
        val fullNewestFirst = listOf(
            FullTankPoint(odometerKm = 10_500, dateEpochMs = 2_000),
            FullTankPoint(odometerKm = 10_000, dateEpochMs = 1_000),
        )
        val allNewestFirst = listOf(
            RefuelPoint(odometerKm = 10_500, volumeLiters = 35.0, dateEpochMs = 2_000),
            RefuelPoint(odometerKm = 10_250, volumeLiters = 5.0, dateEpochMs = 1_500),
            RefuelPoint(odometerKm = 10_000, volumeLiters = 40.0, dateEpochMs = 1_000),
        )

        val segment = ConsumptionCalculator.lastFullTankSegment(fullNewestFirst, allNewestFirst)

        assertThat(segment).isNotNull()
        assertThat(segment!!.distanceKm).isEqualTo(500)
        assertThat(segment.volumeLiters).isEqualTo(40.0)
        // 40 L / 500 km * 100 = 8.0 L/100 km
        assertThat(segment.litersPer100Km).isWithin(0.001).of(8.0)
    }

    @Test
    fun lastFullTankSegment_returnsNullWhenFewerThanTwoFullTanks() {
        val full = listOf(FullTankPoint(odometerKm = 10_000, dateEpochMs = 1_000))
        val all = listOf(RefuelPoint(odometerKm = 10_000, volumeLiters = 40.0, dateEpochMs = 1_000))

        assertThat(ConsumptionCalculator.lastFullTankSegment(full, all)).isNull()
    }

    @Test
    fun lifetimeAverage_usesFirstAndLastFullTank() {
        val fullOldestFirst = listOf(
            FullTankPoint(odometerKm = 1_000, dateEpochMs = 1_000),
            FullTankPoint(odometerKm = 2_000, dateEpochMs = 2_000),
            FullTankPoint(odometerKm = 3_000, dateEpochMs = 3_000),
        )
        val all = listOf(
            RefuelPoint(odometerKm = 3_000, volumeLiters = 50.0, dateEpochMs = 3_000),
            RefuelPoint(odometerKm = 2_000, volumeLiters = 50.0, dateEpochMs = 2_000),
            RefuelPoint(odometerKm = 1_000, volumeLiters = 50.0, dateEpochMs = 1_000),
        )

        val segment = ConsumptionCalculator.lifetimeAverage(fullOldestFirst, all)

        assertThat(segment).isNotNull()
        assertThat(segment!!.distanceKm).isEqualTo(2_000)
        assertThat(segment.volumeLiters).isEqualTo(100.0)
        // 100 L / 2000 km * 100 = 5.0
        assertThat(segment.litersPer100Km).isWithin(0.001).of(5.0)
    }
}
