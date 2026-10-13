package ru.autolog.app.ui.dashboard

import java.util.Locale
import ru.autolog.app.data.repository.TimelineEntry
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.domain.consumption.ConsumptionCalculator

/** Distances / economy along the feed: refuel km + consumption on the road, TO km inside the card. */
object TimelineDistance {
    fun kmSincePreviousRefuel(timeline: List<TimelineEntry>, index: Int): Int? {
        val current = (timeline.getOrNull(index) as? TimelineEntry.Refuel)?.entity ?: return null
        val previous = timeline.asSequence()
            .drop(index + 1)
            .filterIsInstance<TimelineEntry.Refuel>()
            .firstOrNull()
            ?.entity ?: return null
        return (current.odometerKm - previous.odometerKm).takeIf { it > 0 }
    }

    /**
     * L/100 km for the full-tank segment ending at this refuel (Drivvo-style).
     * Uses the value stored on the entity when present.
     */
    fun consumptionLPer100SincePreviousFull(timeline: List<TimelineEntry>, index: Int): Double? {
        val current = (timeline.getOrNull(index) as? TimelineEntry.Refuel)?.entity ?: return null
        if (!current.isTankFull) return null
        current.consumptionLPer100Km?.let { return it }

        val refuelsNewestFirst = timeline.asSequence()
            .drop(index)
            .filterIsInstance<TimelineEntry.Refuel>()
            .map { it.entity }
            .toList()
        val fullNewest = refuelsNewestFirst.filter { it.isTankFull }.map {
            ConsumptionCalculator.FullTankPoint(it.odometerKm, it.dateEpochMs)
        }
        val points = refuelsNewestFirst.map {
            ConsumptionCalculator.RefuelPoint(it.odometerKm, it.volumeLiters, it.dateEpochMs)
        }
        return ConsumptionCalculator.lastFullTankSegment(fullNewest, points)?.litersPer100Km
    }

    fun kmSincePreviousTo(timeline: List<TimelineEntry>, index: Int): Int? {
        val current = (timeline.getOrNull(index) as? TimelineEntry.Service)?.entity ?: return null
        if (ServiceKind.fromStorage(current.serviceType) != ServiceKind.TO) return null
        val previous = timeline.asSequence()
            .drop(index + 1)
            .filterIsInstance<TimelineEntry.Service>()
            .map { it.entity }
            .firstOrNull { ServiceKind.fromStorage(it.serviceType) == ServiceKind.TO }
            ?: return null
        return (current.odometerKm - previous.odometerKm).takeIf { it >= 0 }
    }

    fun formatKm(km: Int): String =
        String.format(Locale("ru"), "%,d", km)
            .replace(',', ' ')
            .replace('\u00a0', ' ')
            .replace('\u202f', ' ')
}
