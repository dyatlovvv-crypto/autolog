package ru.autolog.app.domain.reminder

import com.google.common.truth.Truth.assertThat
import org.junit.Test

class ReminderForecastTest {

    @Test
    fun picksEarlierOfKmAndCalendar() {
        val day = 24L * 60 * 60 * 1000
        val now = 1_700_000_000_000L
        val result = ReminderForecast.compute(
            ReminderForecast.Input(
                currentOdometerKm = 10_000,
                baselineOdometerKm = 10_000,
                baselineDateEpochMs = now,
                intervalKm = 7_500,
                intervalDays = 365,
                avgKmPerDay = 50.0, // 7500/50 = 150 days < 365
                nowEpochMs = now,
            ),
        )
        assertThat(result.remainingKm).isEqualTo(7_500)
        assertThat(result.remainingDays).isEqualTo(150)
        assertThat(result.nextDueDateEpochMs).isEqualTo(now + 150 * day)
    }

    @Test
    fun averageKmPerDay_fromSamples() {
        val day = 24L * 60 * 60 * 1000
        val avg = ReminderForecast.averageKmPerDay(
            listOf(
                0L to 1_000,
                10 * day to 1_500,
            ),
        )
        assertThat(avg).isWithin(0.01).of(50.0)
    }
}
