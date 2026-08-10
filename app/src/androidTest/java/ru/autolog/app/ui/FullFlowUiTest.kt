package ru.autolog.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.compose.ui.test.performTouchInput
import androidx.compose.ui.test.swipeLeft
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.autolog.app.AutologApp
import ru.autolog.app.MainActivity
import ru.autolog.app.data.repository.DashboardSnapshot
import ru.autolog.app.ui.dashboard.DashboardTags
import ru.autolog.app.ui.dashboard.VehicleSelectorTags
import ru.autolog.app.ui.feed.RefuelDetailTags
import ru.autolog.app.ui.feed.ReminderDetailTags
import ru.autolog.app.ui.map.GasMapTags
import ru.autolog.app.ui.refuel.AddRefuelTags
import ru.autolog.app.ui.reminder.AddReminderTags
import ru.autolog.app.ui.vehicle.AddVehicleTags

/**
 * Full device UI flow on phone.
 * Does NOT wipe existing user data: creates an isolated UITest vehicle and deletes only it.
 *
 * Example: 40 л на 500 км между двумя «до полного» = 8.0 л/100 км.
 */
@RunWith(AndroidJUnit4::class)
class FullFlowUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun fullJournalFlow_vehicleReminderRefuelMapEditConsumption() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carName = "UITest Car $suffix"
        val reminderTitle = "Замена масла UITest $suffix"

        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty()
        }

        // 1) Add isolated test vehicle (keep any existing cars)
        ensureAddVehicleScreen()
        composeRule.onNodeWithTag(AddVehicleTags.NAME).performTextInput(carName)
        composeRule.onNodeWithTag(AddVehicleTags.PLATE).performTextInput("T$suffix")
        composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextInput("50000")
        composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextClearance()
        composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextInput("55")
        composeRule.onNodeWithTag(AddVehicleTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        composeRule.onNodeWithText(carName, substring = true).assertIsDisplayed()

        val vehicleId = runBlocking {
            AutologApp.instance.container.repository.observeVehicles().first()
                .first { it.name == carName }.id
        }

        // 2) Reminder with fields
        composeRule.onNodeWithTag(DashboardTags.ADD_REMINDER).performClick()
        waitTag(AddReminderTags.SCREEN)
        composeRule.onNodeWithTag(AddReminderTags.TITLE).performTextClearance()
        composeRule.onNodeWithTag(AddReminderTags.TITLE).performTextInput(reminderTitle)
        composeRule.onNodeWithTag(AddReminderTags.INTERVAL_KM).performTextClearance()
        composeRule.onNodeWithTag(AddReminderTags.INTERVAL_KM).performTextInput("7500")
        composeRule.onNodeWithTag(AddReminderTags.INTERVAL_DAYS).performTextClearance()
        composeRule.onNodeWithTag(AddReminderTags.INTERVAL_DAYS).performTextInput("365")
        composeRule.onNodeWithTag(AddReminderTags.LAST_ODO).performTextClearance()
        composeRule.onNodeWithTag(AddReminderTags.LAST_ODO).performTextInput("50000")
        composeRule.onNodeWithTag(AddReminderTags.LAST_DATE).assertIsDisplayed()
        composeRule.onNodeWithTag(AddReminderTags.SAVE).performClick()
        waitTag(AddReminderTags.CHANGED_OK)
        composeRule.onNodeWithTag(AddReminderTags.CHANGED_OK).performClick()
        waitTag(DashboardTags.ROOT)
        composeRule.onNodeWithText(reminderTitle, substring = true).assertIsDisplayed()

        // 3) Delete reminder from detail
        val reminderId = dash(vehicleId).activeReminders.first { it.title == reminderTitle }.id
        composeRule.onNodeWithTag(DashboardTags.feedReminder(reminderId)).performClick()
        waitTag(ReminderDetailTags.SCREEN)
        composeRule.onNodeWithTag(ReminderDetailTags.DELETE).performClick()
        composeRule.onNodeWithTag("reminder_delete_confirm").performClick()
        waitTag(DashboardTags.ROOT)

        // 4) Refuel #1 full @ 50000 / 45L + map pick
        composeRule.onNodeWithTag(DashboardTags.ADD_REFUEL).performClick()
        waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextInput("50000")
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextInput("45")
        composeRule.onNodeWithTag(AddRefuelTags.PRICE).performTextInput("50")
        composeRule.onNodeWithTag(AddRefuelTags.OPEN_MAP).performClick()
        waitTag(GasMapTags.SCREEN)
        composeRule.onNodeWithTag(GasMapTags.PICK_TEST).performClick()
        waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.STATION).assertTextContains("UITest АЗС", substring = true)
        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)

        // 5) Refuel #2 full @ 50500 / 40L → 8.0 л/100
        composeRule.onNodeWithTag(DashboardTags.ADD_REFUEL).performClick()
        waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextInput("50500")
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextInput("40")
        composeRule.onNodeWithTag(AddRefuelTags.PRICE).performTextInput("52")
        composeRule.onNodeWithTag(AddRefuelTags.STATION).performTextInput("Ручная АЗС")
        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodes(hasText("8.0", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().size >= 2
        }
        assert(
            composeRule.onAllNodes(hasText("8.0", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().size >= 2,
        )

        // 6) Edit refuel
        val refuelId = dash(vehicleId).recentRefuellings.first().id
        composeRule.onNodeWithTag(DashboardTags.feedRefuel(refuelId)).performClick()
        waitTag(RefuelDetailTags.SCREEN)
        composeRule.onNodeWithTag(RefuelDetailTags.STATION).performTextClearance()
        composeRule.onNodeWithTag(RefuelDetailTags.STATION).performTextInput("Исправленная АЗС")
        composeRule.onNodeWithTag(RefuelDetailTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        composeRule.onNodeWithText("Исправленная АЗС", substring = true).assertIsDisplayed()

        // 7) Swipe-delete one feed refuel of this vehicle
        val olderId = dash(vehicleId).recentRefuellings.lastOrNull()?.id
        if (olderId != null) {
            composeRule.onNodeWithTag(DashboardTags.feedRefuel(olderId)).performTouchInput { swipeLeft() }
            composeRule.waitForIdle()
            runCatching { composeRule.onNodeWithTag("swipe_delete_action").performClick() }
            composeRule.waitForIdle()
        }

        // 8) Delete only the test vehicle (leave user cars alone)
        composeRule.onNodeWithTag(VehicleSelectorTags.TRIGGER).performClick()
        waitTag(VehicleSelectorTags.SHEET)
        composeRule.onNodeWithTag(VehicleSelectorTags.delete(vehicleId)).performClick()
        composeRule.onNodeWithTag("vehicle_delete_confirm").performClick()
        composeRule.waitForIdle()
        composeRule.waitUntil(10_000) {
            runBlocking {
                AutologApp.instance.container.repository.observeVehicles().first()
                    .none { it.id == vehicleId }
            }
        }
        val remaining = runBlocking {
            AutologApp.instance.container.repository.observeVehicles().first()
        }
        if (remaining.isEmpty()) {
            waitTag(AddVehicleTags.SCREEN)
            composeRule.onNodeWithTag(AddVehicleTags.SCREEN).assertIsDisplayed()
        } else {
            waitTag(DashboardTags.ROOT)
            composeRule.onNodeWithTag(DashboardTags.ROOT).assertIsDisplayed()
            assertTrue(
                composeRule.onAllNodesWithText(carName, substring = true)
                    .fetchSemanticsNodes()
                    .isEmpty(),
            )
        }
    }

    private fun ensureAddVehicleScreen() {
        val onAdd = composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN)
            .fetchSemanticsNodes().isNotEmpty()
        if (onAdd) return
        composeRule.onNodeWithTag(VehicleSelectorTags.TRIGGER).performClick()
        waitTag(VehicleSelectorTags.SHEET)
        composeRule.onNodeWithTag(VehicleSelectorTags.ADD_VEHICLE).performClick()
        waitTag(AddVehicleTags.SCREEN)
    }

    private fun waitTag(tag: String, timeoutMs: Long = 12_000) {
        composeRule.waitUntil(timeoutMs) {
            composeRule.onAllNodesWithTag(tag).fetchSemanticsNodes().isNotEmpty()
        }
    }

    private fun dash(vehicleId: Long): DashboardSnapshot.Data = runBlocking {
        AutologApp.instance.container.repository.observeDashboard(vehicleId).first()
            as DashboardSnapshot.Data
    }
}
