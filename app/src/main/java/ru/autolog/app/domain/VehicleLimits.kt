package ru.autolog.app.domain

/** Soft product limit for vehicles in the journal. */
object VehicleLimits {
    const val MIN = 1
    const val MAX = 10

    fun canAdd(currentCount: Int): Boolean = currentCount in 0 until MAX
}
