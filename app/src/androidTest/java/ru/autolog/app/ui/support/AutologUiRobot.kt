package ru.autolog.app.ui.support

import androidx.compose.ui.test.junit4.AndroidComposeTestRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.rules.ActivityScenarioRule
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import ru.autolog.app.AutologApp
import ru.autolog.app.MainActivity
import ru.autolog.app.data.repository.DashboardSnapshot
import ru.autolog.app.ui.dashboard.DashboardTags
import ru.autolog.app.ui.dashboard.VehicleSelectorTags
import ru.autolog.app.ui.note.NotepadBlock
import ru.autolog.app.ui.note.NotepadDocument
import ru.autolog.app.ui.settings.SettingsTags
import ru.autolog.app.ui.vehicle.AddVehicleTags

/** Isolated UITest vehicle helpers. Never wipes the whole database. */
class AutologUiRobot(
    private val composeRule: AndroidComposeTestRule<ActivityScenarioRule<MainActivity>, MainActivity>,
) {
    fun waitHome(timeoutMs: Long = 15_000) {
        composeRule.waitUntil(timeoutMs) {
            hasTag(AddVehicleTags.SCREEN) || hasTag(DashboardTags.ROOT)
        }
    }

    fun waitTag(tag: String, timeoutMs: Long = 12_000) {
        composeRule.waitUntil(timeoutMs) { hasTag(tag) }
    }

    fun hasTag(tag: String): Boolean =
        composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()

    fun pruneUitestVehicles() {
        runBlocking {
            val repo = AutologApp.instance.container.repository
            repo.observeVehicles().first()
                .filter { it.name.startsWith("UITest") }
                .forEach { repo.deleteVehicle(it.id) }
        }
    }

    fun createVehicle(name: String, odometer: String, tank: String, plate: String? = null): Long {
        waitHome()
        if (!hasTag(AddVehicleTags.SCREEN)) {
            composeRule.onNodeWithTag(VehicleSelectorTags.TRIGGER).performClick()
            waitTag(VehicleSelectorTags.SHEET)
            composeRule.onNodeWithTag(VehicleSelectorTags.ADD_VEHICLE).performClick()
            waitTag(AddVehicleTags.SCREEN)
        }
        composeRule.onNodeWithTag(AddVehicleTags.NAME).performTextInput(name)
        if (plate != null) {
            composeRule.onNodeWithTag(AddVehicleTags.PLATE).performTextInput(plate)
        }
        composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextInput(odometer)
        composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextClearance()
        composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextInput(tank)
        composeRule.onNodeWithTag(AddVehicleTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        return runBlocking {
            AutologApp.instance.container.repository.observeVehicles().first()
                .first { it.name == name }.id
        }
    }

    fun deleteVehicleViaSettings(vehicleId: Long) {
        runBlocking { AutologApp.instance.container.repository.switchVehicle(vehicleId) }
        composeRule.waitForIdle()
        if (hasTag(SettingsTags.ROOT)) {
            composeRule.onNodeWithTag(SettingsTags.BACK).performClick()
            composeRule.waitForIdle()
        }
        waitTag(DashboardTags.ROOT)
        composeRule.onNodeWithTag(DashboardTags.OPEN_SETTINGS).performClick()
        waitTag(SettingsTags.ROOT)
        composeRule.onNodeWithTag(SettingsTags.DELETE_VEHICLE).performScrollTo().performClick()
        composeRule.onNodeWithTag(SettingsTags.DELETE_VEHICLE_CONFIRM).performClick()
        composeRule.waitUntil(12_000) {
            runBlocking {
                AutologApp.instance.container.repository.observeVehicles().first()
                    .none { it.id == vehicleId }
            }
        }
        if (hasTag(SettingsTags.ROOT)) {
            composeRule.onNodeWithTag(SettingsTags.BACK).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(15_000) {
            hasTag(DashboardTags.ROOT) || hasTag(AddVehicleTags.SCREEN)
        }
    }

    fun pressSystemBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
    }

    fun dash(vehicleId: Long): DashboardSnapshot.Data = runBlocking {
        AutologApp.instance.container.repository.observeDashboard(vehicleId).first()
            as DashboardSnapshot.Data
    }

    fun notepadText(vehicleId: Long): String = runBlocking {
        val raw = AutologApp.instance.container.repository.getNotepad(vehicleId)?.text.orEmpty()
        NotepadDocument.decode(raw).blocks
            .filterIsInstance<NotepadBlock.Text>()
            .joinToString("\n") { it.body }
    }

    fun switchVehicle(vehicleId: Long) {
        composeRule.onNodeWithTag(VehicleSelectorTags.TRIGGER).performClick()
        waitTag(VehicleSelectorTags.SHEET)
        composeRule.onNodeWithTag(VehicleSelectorTags.item(vehicleId)).performClick()
        waitTag(DashboardTags.ROOT)
    }

    /** Opens Сервис hub, expands История, taps a history filter. */
    fun openServiceHubHistory(filter: String) {
        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag(DashboardTags.SERVICE_HUB_HISTORY_SECTION).performClick()
        composeRule.onNodeWithTag(DashboardTags.serviceHubHistory(filter)).performClick()
    }
}
