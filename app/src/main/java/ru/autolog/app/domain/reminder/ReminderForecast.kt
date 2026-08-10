package ru.autolog.app.domain.reminder

import kotlin.math.ceil
import kotlin.math.max

/**
 * Smart reminder: whichever comes first — mileage interval or calendar interval.
 * Approximate due date from avg km/day derived from odometer history.
 */
object ReminderForecast {
    data class Input(
        val currentOdometerKm: Int,
        val baselineOdometerKm: Int,
        val baselineDateEpochMs: Long,
        val intervalKm: Int?,
        val intervalDays: Int?,
        val avgKmPerDay: Double?,
        val nowEpochMs: Long,
    )

    data class Result(
        val remainingKm: Int?,
        val remainingDays: Int?,
        val nextDueDateEpochMs: Long?,
        val nextDueOdometerKm: Int?,
        val avgKmPerDay: Double?,
    )

    fun compute(input: Input): Result {
        val dueOdo = input.intervalKm?.let { input.baselineOdometerKm + it }
        val dueByCalendar = input.intervalDays?.let {
            input.baselineDateEpochMs + it * DAY_MS
        }

        val remainingKm = dueOdo?.let { max(0, it - input.currentOdometerKm) }

        val dueByKmDate = if (dueOdo != null && input.avgKmPerDay != null && input.avgKmPerDay > 0) {
            val kmLeft = max(0, dueOdo - input.currentOdometerKm)
            val days = ceil(kmLeft / input.avgKmPerDay).toLong()
            input.nowEpochMs + days * DAY_MS
        } else {
            null
        }

        val nextDueDate = listOfNotNull(dueByCalendar, dueByKmDate).minOrNull()
            ?: dueByCalendar
            ?: dueByKmDate

        val remainingDays = nextDueDate?.let { due ->
            max(0, ceil((due - input.nowEpochMs).toDouble() / DAY_MS).toInt())
        }

        return Result(
            remainingKm = remainingKm,
            remainingDays = remainingDays,
            nextDueDateEpochMs = nextDueDate,
            nextDueOdometerKm = dueOdo,
            avgKmPerDay = input.avgKmPerDay,
        )
    }

    /** Average km/day from chronological odometer samples (oldest → newest). */
    fun averageKmPerDay(samplesOldestFirst: List<Pair<Long, Int>>): Double? {
        if (samplesOldestFirst.size < 2) return null
        val first = samplesOldestFirst.first()
        val last = samplesOldestFirst.last()
        val daySpan = (last.first - first.first).toDouble() / DAY_MS
        val kmSpan = last.second - first.second
        if (daySpan <= 0 || kmSpan <= 0) return null
        return kmSpan / daySpan
    }

    private const val DAY_MS = 24L * 60 * 60 * 1000
}
