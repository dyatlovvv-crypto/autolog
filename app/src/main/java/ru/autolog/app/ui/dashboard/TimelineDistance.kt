package ru.autolog.app.ui.dashboard

import java.util.Locale
import ru.autolog.app.data.repository.TimelineEntry
import ru.autolog.app.domain.ServiceKind

/** Distances along the feed: refuels on the road, TO only inside the card. */
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
