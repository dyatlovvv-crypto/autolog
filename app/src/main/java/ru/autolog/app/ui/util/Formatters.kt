package ru.autolog.app.ui.util

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ru.autolog.app.data.settings.ConsumptionUnit
import ru.autolog.app.data.settings.VolumeUnit

object Formatters {
    private val dateRu = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
    private val dateTimeRu = SimpleDateFormat("dd.MM.yyyy HH:mm", Locale("ru"))

    fun litersPer100(value: Double?): String =
        if (value == null) "нет данных" else String.format(Locale.US, "%.1f", value)

    fun consumptionHint(value: Double?): String =
        if (value == null) "нужны 2 заправки «до полного»" else "л / 100 км"

    fun consumptionValue(lPer100: Double?, unit: ConsumptionUnit): String {
        if (lPer100 == null) return "нет данных"
        val converted = when (unit) {
            ConsumptionUnit.L_PER_100 -> lPer100
            ConsumptionUnit.KM_PER_L -> if (lPer100 > 0) 100.0 / lPer100 else 0.0
            ConsumptionUnit.MPG -> if (lPer100 > 0) 235.214583 / lPer100 else 0.0
        }
        return String.format(Locale.US, "%.1f", converted)
    }

    fun consumptionUnitLabel(unit: ConsumptionUnit): String = unit.label

    /** Compact unit for timeline road gutter. */
    fun consumptionUnitShort(unit: ConsumptionUnit): String = when (unit) {
        ConsumptionUnit.L_PER_100 -> "л/100"
        ConsumptionUnit.KM_PER_L -> "км/л"
        ConsumptionUnit.MPG -> "mpg"
    }

    fun volumeLabel(liters: Double, unit: VolumeUnit): String = when (unit) {
        VolumeUnit.LITERS -> String.format(Locale.US, "%.1f л", liters)
        VolumeUnit.GALLONS -> String.format(Locale.US, "%.2f гал", liters / 3.785411784)
    }

    fun date(epochMs: Long?): String =
        if (epochMs == null) "нет даты" else dateRu.format(Date(epochMs))

    fun dateTime(epochMs: Long?): String =
        if (epochMs == null) "нет даты" else dateTimeRu.format(Date(epochMs))

    fun money(value: Double): String =
        String.format(Locale("ru"), "%.0f ₽", value)

    /** Prefer Russian user messages; hide raw English/technical exceptions. */
    fun userMessage(error: Throwable?, fallback: String): String {
        val chain = generateSequence(error) { it.cause }
            .mapNotNull { it.message?.trim()?.takeIf(String::isNotEmpty) }
            .joinToString(" ")
        if (chain.isEmpty()) return fallback
        val lower = chain.lowercase()
        when {
            "accessnotconfigured" in lower ||
                "drive api has not been used" in lower ||
                "drive api" in lower && "disabled" in lower ->
                return "Сохранение в Google пока недоступно. Попробуйте позже или напишите в поддержку."
            "storagequotaexceeded" in lower ||
                "storage quota has been exceeded" in lower ||
                "quota has been exceeded" in lower ->
                return "На Google Диске закончилось место. Освободите место или войдите другим аккаунтом."
            "userecoverableauthexception" in lower ||
                "needremoteconsent" in lower ||
                "authorization" in lower && "required" in lower ->
                return "Нужно снова разрешить доступ Google. Отвяжите аккаунт и привяжите заново."
            "network" in lower || "unable to resolve" in lower || "timeout" in lower ->
                return "Нет связи с Google. Проверьте интернет и попробуйте снова."
            "insufficientpermissions" in lower || "access_denied" in lower ->
                return "Google не дал доступ к сохранению. Отвяжите аккаунт и привяжите заново."
            "403" in lower ->
                return "Google отклонил сохранение. Проверьте место на Диске или попробуйте другой аккаунт."
        }
        val raw = error?.message?.trim().orEmpty()
        val looksRussian = raw.any { it in '\u0400'..'\u04FF' }
        return if (looksRussian) raw else fallback
    }
}
