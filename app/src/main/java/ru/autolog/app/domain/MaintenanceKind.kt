package ru.autolog.app.domain

import ru.autolog.app.data.local.entity.ReminderEntity

/** Fixed service reminders shown on dashboard / maintenance list. */
enum class MaintenanceKind(
    val title: String,
    private val matchTokens: List<String>,
) {
    OIL("Замена масла", listOf("масл")),
    SPARK_PLUGS("Замена свечей", listOf("свеч")),
    CABIN_FILTER("Замена салонного фильтра", listOf("салонн")),
    AIR_FILTER("Замена воздушного фильтра", listOf("воздушн")),
    BRAKE_FLUID("Замена тормозной жидкости", listOf("тормозн")),
    ANTIFREEZE("Замена антифриза", listOf("антифриз", "охлаждающ")),
    ;

    fun matches(title: String): Boolean {
        val lower = title.lowercase()
        return matchTokens.any { it in lower }
    }

    companion object {
        fun fromTitle(title: String): MaintenanceKind? =
            entries.firstOrNull { it.matches(title) }

        fun fromStorage(name: String): MaintenanceKind? =
            entries.firstOrNull { it.name.equals(name, ignoreCase = true) }
    }
}

data class MaintenanceSlot(
    val kind: MaintenanceKind,
    val reminder: ReminderEntity?,
)

object MaintenanceCatalog {
    /**
     * Up to [limit] slots for the dashboard: filled reminders nearest by remaining km,
     * then empty kinds as placeholders so the UI always has [limit] cards when needed.
     */
    fun dashboardSlots(
        reminders: List<ReminderEntity>,
        limit: Int = 2,
    ): List<MaintenanceSlot> {
        if (limit <= 0) return emptyList()
        val byKind = LinkedHashMap<MaintenanceKind, ReminderEntity>()
        reminders.forEach { reminder ->
            val kind = MaintenanceKind.fromTitle(reminder.title) ?: return@forEach
            val existing = byKind[kind]
            if (existing == null ||
                (reminder.remainingKm ?: Int.MAX_VALUE) < (existing.remainingKm ?: Int.MAX_VALUE)
            ) {
                byKind[kind] = reminder
            }
        }
        val nearestFilled = byKind.entries
            .map { MaintenanceSlot(it.key, it.value) }
            .sortedWith(
                compareBy(
                    nullsLast(),
                ) { it.reminder?.remainingKm },
            )
            .take(limit)
        if (nearestFilled.size >= limit) return nearestFilled
        val used = nearestFilled.map { it.kind }.toSet()
        val pads = MaintenanceKind.entries
            .filter { it !in used }
            .take(limit - nearestFilled.size)
            .map { MaintenanceSlot(it, null) }
        return nearestFilled + pads
    }
}
