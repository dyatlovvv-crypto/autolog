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
    val kind: MaintenanceKind?,
    val reminder: ReminderEntity?,
) {
    val title: String
        get() = kind?.title
            ?: reminder?.title?.takeIf { it.isNotBlank() }
            ?: "Напоминание"

    val listKey: String
        get() = kind?.name ?: "custom_${reminder?.id ?: "new"}"
}

object MaintenanceCatalog {
    /** Fixed kinds + user-defined reminders (titles that don't match a catalog kind). */
    fun allListSlots(reminders: List<ReminderEntity>): List<MaintenanceSlot> {
        val kindSlots = MaintenanceKind.entries.map { kind ->
            val match = reminders
                .filter { kind.matches(it.title) }
                .minByOrNull { it.remainingKm ?: Int.MAX_VALUE }
            MaintenanceSlot(kind, match)
        }
        val customs = reminders
            .filter { MaintenanceKind.fromTitle(it.title) == null }
            .sortedBy { it.remainingKm ?: Int.MAX_VALUE }
            .map { MaintenanceSlot(kind = null, reminder = it) }
        return kindSlots + customs
    }

    /**
     * Up to [limit] slots for the dashboard: filled reminders nearest by remaining km
     * (catalog + custom), then empty kinds as placeholders.
     */
    fun dashboardSlots(
        reminders: List<ReminderEntity>,
        limit: Int = 2,
    ): List<MaintenanceSlot> {
        if (limit <= 0) return emptyList()
        val byKind = LinkedHashMap<MaintenanceKind, ReminderEntity>()
        val customs = ArrayList<ReminderEntity>()
        reminders.forEach { reminder ->
            val kind = MaintenanceKind.fromTitle(reminder.title)
            if (kind == null) {
                customs += reminder
                return@forEach
            }
            val existing = byKind[kind]
            if (existing == null ||
                (reminder.remainingKm ?: Int.MAX_VALUE) < (existing.remainingKm ?: Int.MAX_VALUE)
            ) {
                byKind[kind] = reminder
            }
        }
        val nearestFilled = (
            byKind.entries.map { MaintenanceSlot(it.key, it.value) } +
                customs.map { MaintenanceSlot(null, it) }
            )
            .sortedWith(compareBy(nullsLast()) { it.reminder?.remainingKm })
            .take(limit)
        if (nearestFilled.size >= limit) return nearestFilled
        val usedKinds = nearestFilled.mapNotNull { it.kind }.toSet()
        val pads = MaintenanceKind.entries
            .filter { it !in usedKinds }
            .take(limit - nearestFilled.size)
            .map { MaintenanceSlot(it, null) }
        return nearestFilled + pads
    }
}
