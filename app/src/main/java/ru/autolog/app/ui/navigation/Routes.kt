package ru.autolog.app.ui.navigation

object Routes {
    const val DASHBOARD = "dashboard"
    const val ADD_VEHICLE = "add_vehicle"
    const val ADD_REFUEL = "add_refuel"
    const val ADD_REMINDER = "add_reminder"
    const val GAS_MAP = "gas_map"
    const val REFUEL_DETAIL = "refuel/{id}"
    const val REMINDER_DETAIL = "reminder/{id}"

    fun refuelDetail(id: Long) = "refuel/$id"
    fun reminderDetail(id: Long) = "reminder/$id"
}
