package ru.autolog.app.domain

/**
 * Popular fuel grades for the refuel form.
 * Order: 100 → 92, then diesel, then gas.
 */
enum class FuelType(val label: String) {
    AI_100("АИ-100"),
    AI_98("АИ-98"),
    AI_95_PULSAR("АИ-95 Pulsar"),
    AI_95("АИ-95"),
    AI_92("АИ-92"),
    DIESEL("ДТ"),
    LPG("Пропан/Бутан"),
    CNG("Метан / СУГ");

    companion object {
        fun fromStorage(raw: String?): FuelType? =
            raw?.let { value -> entries.find { it.name == value } }
    }
}
