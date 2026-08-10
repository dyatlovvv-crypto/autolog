package ru.autolog.app.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

object Formatters {
    private val dateRu = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))

    fun litersPer100(value: Double?): String =
        if (value == null) "нет данных" else String.format(Locale.US, "%.1f", value)

    fun consumptionHint(value: Double?): String =
        if (value == null) "нужны 2 заправки «до полного»" else "л / 100 км"

    fun date(epochMs: Long?): String =
        if (epochMs == null) "нет даты" else dateRu.format(Date(epochMs))

    fun money(value: Double): String =
        String.format(Locale("ru"), "%.0f ₽", value)
}
