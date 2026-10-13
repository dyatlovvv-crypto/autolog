package ru.autolog.app.ui

import androidx.compose.ui.test.assert
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.hasAnyDescendant
import androidx.compose.ui.test.hasTestTag
import androidx.compose.ui.test.hasText
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.autolog.app.AutologApp
import ru.autolog.app.MainActivity
import ru.autolog.app.data.repository.DashboardSnapshot
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.ui.analytics.AnalyticsEntitySpec
import ru.autolog.app.ui.analytics.AnalyticsPeriod
import ru.autolog.app.ui.analytics.AnalyticsTags
import ru.autolog.app.ui.components.TimelineTags
import ru.autolog.app.ui.dashboard.DashboardTags
import ru.autolog.app.ui.dashboard.VehicleSelectorTags
import ru.autolog.app.ui.expense.ExpenseTags
import ru.autolog.app.ui.history.HistoryTags
import ru.autolog.app.ui.map.GasMapTags
import ru.autolog.app.ui.refuel.AddRefuelTags
import ru.autolog.app.ui.reminder.AddReminderTags
import ru.autolog.app.ui.service.ServiceTags
import ru.autolog.app.ui.settings.SettingsTags
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

        // Reminder create + edit
        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
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
        composeRule.onNodeWithTag(DashboardTags.OIL_REMINDER).assertIsDisplayed()
        composeRule.onNodeWithText(reminderTitle, substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(DashboardTags.OIL_REMINDER).performClick()
        waitTag(AddReminderTags.SCREEN)
        composeRule.onNodeWithTag(AddReminderTags.TITLE).performTextClearance()
        composeRule.onNodeWithTag(AddReminderTags.TITLE).performTextInput("$reminderTitle edit")
        composeRule.onNodeWithTag(AddReminderTags.SAVE).performClick()
        waitTag(AddReminderTags.CHANGED_OK)
        composeRule.onNodeWithTag(AddReminderTags.CHANGED_OK).performClick()
        waitTag(DashboardTags.ROOT)
        composeRule.onNodeWithText("$reminderTitle edit", substring = true).assertIsDisplayed()

        // Delete reminder from edit screen
        composeRule.onNodeWithTag(DashboardTags.OIL_REMINDER).performClick()
        waitTag(AddReminderTags.SCREEN)
        composeRule.onNodeWithTag(AddReminderTags.DELETE).performClick()
        composeRule.onNodeWithTag("reminder_delete_confirm").performClick()
        waitTag(DashboardTags.ROOT)

        // Wash (мойка) via service hub + edit
        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag(DashboardTags.SERVICE_HUB_ADD_WASH).performClick()
        waitTag(ExpenseTags.SCREEN)
        composeRule.onNodeWithTag(ExpenseTags.CATEGORY).assertIsDisplayed()
        composeRule.onNodeWithTag(ExpenseTags.AMOUNT).performTextInput("500")
        composeRule.onNodeWithTag(ExpenseTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        val expenseId = dash(vehicleId).recentExpenses.first {
            it.category.contains("мойк", ignoreCase = true)
        }.id
        composeRule.onNodeWithTag(DashboardTags.feedExpense(expenseId)).performClick()
        composeRule.onNodeWithTag(TimelineTags.actionEdit("EXPENSE", expenseId)).performClick()
        waitTag(ExpenseTags.SCREEN)
        composeRule.onNodeWithTag(ExpenseTags.AMOUNT).performTextClearance()
        composeRule.onNodeWithTag(ExpenseTags.AMOUNT).performTextInput("550")
        composeRule.onNodeWithTag(ExpenseTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)

        // Service create + checklist + edit checks (ТО / Ремонт / Запланированно separate)
        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag("add_service_kind_TO").performClick()
        waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.TYPE).assertIsDisplayed()
        composeRule.onNodeWithTag(ServiceTags.CHECKLIST_INPUT).performTextInput("Масло")
        composeRule.onNodeWithTag(ServiceTags.CHECKLIST_ADD).performClick()
        composeRule.onNodeWithTag(ServiceTags.CHECKLIST_INPUT).performTextInput("Фильтр")
        composeRule.onNodeWithTag(ServiceTags.CHECKLIST_ADD).performClick()
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextInput("50000")
        composeRule.onNodeWithTag(ServiceTags.DATE).assertIsDisplayed()
        composeRule.onNodeWithTag(ServiceTags.AMOUNT).performTextInput("3000")
        composeRule.onNodeWithTag(ServiceTags.ATTACH).assertIsDisplayed()
        composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        val serviceId = dash(vehicleId).recentServices.first {
            ServiceKind.fromStorage(it.serviceType) == ServiceKind.TO
        }.id
        composeRule.onNodeWithTag(DashboardTags.feedService(serviceId)).performClick()
        composeRule.onNodeWithTag(TimelineTags.actionEdit("SERVICE", serviceId)).performClick()
        waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithText("Масло", substring = true).performClick()
        composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        // Feed shows checklist item texts (not a bare "Чек-лист" label)
        composeRule.onNodeWithTag(DashboardTags.feedService(serviceId)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("Масло", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("Фильтр", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.feedService(serviceId)).performClick()
        composeRule.waitForIdle()

        // History via wrench hub: ТО + open read-only view
        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag(DashboardTags.SERVICE_HUB_HISTORY_SECTION).performClick()
        composeRule.onNodeWithTag(DashboardTags.serviceHubHistory("TO")).performClick()
        waitTag(HistoryTags.ROOT)
        composeRule.onNodeWithTag(HistoryTags.FILTER_TO).assertIsDisplayed()
        composeRule.onNodeWithTag("history_service_$serviceId").assertIsDisplayed()
        composeRule.onNodeWithTag("history_service_$serviceId").performClick()
        waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.EDIT).assertIsDisplayed()
        composeRule.onNodeWithText("Масло", substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(ServiceTags.EDIT).performClick()
        composeRule.onNodeWithTag(ServiceTags.SAVE).assertIsDisplayed()
        pressSystemBack()
        waitTag(HistoryTags.ROOT)
        pressSystemBack()
        waitTag(DashboardTags.ROOT)

        // Analytics: independent periods per tab
        composeRule.onNodeWithTag(DashboardTags.OPEN_ANALYTICS).performClick()
        waitTag(AnalyticsTags.ROOT)
        composeRule.onNodeWithTag(AnalyticsTags.tab(AnalyticsEntitySpec.FUEL)).performClick()
        composeRule.onNodeWithTag(
            AnalyticsTags.period(AnalyticsEntitySpec.FUEL, AnalyticsPeriod.YEAR),
        ).performClick()
        composeRule.onNodeWithTag(AnalyticsTags.tab(AnalyticsEntitySpec.TO)).performClick()
        composeRule.onNodeWithTag(
            AnalyticsTags.period(AnalyticsEntitySpec.TO, AnalyticsPeriod.ALL),
        ).performClick()
        composeRule.onNodeWithTag(AnalyticsTags.tab(AnalyticsEntitySpec.FUEL)).performClick()
        composeRule.onNodeWithTag(
            AnalyticsTags.period(AnalyticsEntitySpec.FUEL, AnalyticsPeriod.YEAR),
        ).assertIsDisplayed()
        pressSystemBack()
        waitTag(DashboardTags.ROOT)

        // Refuel #1 full @ 50000 / 45L + map + fuel type
        composeRule.onNodeWithTag(DashboardTags.ADD_REFUEL).performClick()
        waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextInput("50000")
        composeRule.onNodeWithTag(AddRefuelTags.FUEL_TYPE).performClick()
        composeRule.onNodeWithTag("fuel_type_AI_100").performClick()
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextInput("45")
        composeRule.onNodeWithTag(AddRefuelTags.PRICE).performTextInput("50")
        composeRule.onNodeWithTag(AddRefuelTags.OPEN_MAP).performClick()
        waitTag(GasMapTags.SCREEN)
        composeRule.onNodeWithTag(GasMapTags.PICK_TEST).performClick()
        waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.STATION).assertTextContains("UITest АЗС", substring = true)
        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)

        // Low odometer confirm path
        composeRule.onNodeWithTag(DashboardTags.ADD_REFUEL).performClick()
        waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextInput("49900")
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextInput("10")
        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performClick()
        waitTag(AddRefuelTags.LOW_ODO_CONFIRM)
        composeRule.onNodeWithTag(AddRefuelTags.LOW_ODO_CANCEL).performClick()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextInput("50500")
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextInput("40")
        composeRule.onNodeWithTag(AddRefuelTags.PRICE).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.PRICE).performTextInput("52")
        composeRule.onNodeWithTag(AddRefuelTags.STATION).performTextInput("Ручная АЗС")
        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodes(hasText("8.0", substring = true), useUnmergedTree = true)
                .fetchSemanticsNodes().size >= 2
        }
        val newerRefuelId = dash(vehicleId).recentRefuellings.maxBy { it.odometerKm }.id
        composeRule.onNodeWithTag(TimelineTags.roadKm("REFUEL", newerRefuelId)).assertIsDisplayed()
        composeRule.onNode(
            hasTestTag(TimelineTags.roadKm("REFUEL", newerRefuelId))
                .and(hasAnyDescendant(hasText("500 км", substring = false))),
            useUnmergedTree = true,
        ).assertExists()
        composeRule.onNode(
            hasTestTag(TimelineTags.roadKm("REFUEL", newerRefuelId))
                .and(hasAnyDescendant(hasText("8.0 л/100", substring = false))),
            useUnmergedTree = true,
        ).assertExists()

        // Edit refuel via timeline
        val refuelId = dash(vehicleId).recentRefuellings.first().id
        composeRule.onNodeWithTag(DashboardTags.feedRefuel(refuelId)).performClick()
        composeRule.onNodeWithTag(TimelineTags.actionEdit("REFUEL", refuelId)).performClick()
        waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.STATION).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.STATION).performTextInput("Исправленная АЗС")
        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        composeRule.onNodeWithText("Исправленная АЗС", substring = true).assertIsDisplayed()

        val olderId = dash(vehicleId).recentRefuellings.lastOrNull()?.id
        if (olderId != null && olderId != refuelId) {
            composeRule.onNodeWithTag(DashboardTags.feedRefuel(olderId)).performClick()
            composeRule.onNodeWithTag(TimelineTags.actionDelete("REFUEL", olderId)).performClick()
            composeRule.onNodeWithTag(TimelineTags.DELETE_CONFIRM).performClick()
            composeRule.waitForIdle()
        }

        deleteVehicleViaSettings(vehicleId)
        val remaining = runBlocking {
            AutologApp.instance.container.repository.observeVehicles().first()
        }
        if (remaining.isEmpty()) {
            composeRule.onNodeWithTag(AddVehicleTags.SCREEN).assertIsDisplayed()
        } else {
            composeRule.onNodeWithTag(DashboardTags.ROOT).assertIsDisplayed()
            assertTrue(
                composeRule.onAllNodesWithText(carName, substring = true)
                    .fetchSemanticsNodes()
                    .isEmpty(),
            )
        }
    }

    /**
     * Regression: checklist "Добавить" on edit must append the work item (not leave text in the field).
     * Feed expand must show each checklist line, not a bare "Чек-лист" label.
     * Also: typed draft without tapping Add must flush on Save.
     */
    @Test
    fun serviceChecklist_addOnEdit_listsItemsAndShowsFullFeed() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carName = "UITest Checklist $suffix"
        val workOnEdit = "иридиевые свечи $suffix"
        val workOnSaveFlush = "масло моторное $suffix"

        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty()
        }

        ensureAddVehicleScreen()
        composeRule.onNodeWithTag(AddVehicleTags.NAME).performTextInput(carName)
        composeRule.onNodeWithTag(AddVehicleTags.PLATE).performTextInput("C$suffix")
        composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextInput("106400")
        composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextClearance()
        composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextInput("45")
        composeRule.onNodeWithTag(AddVehicleTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)

        val vehicleId = runBlocking {
            AutologApp.instance.container.repository.observeVehicles().first()
                .first { it.name == carName }.id
        }

        // Create ТО without works
        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag("add_service_kind_TO").performClick()
        waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextInput("106400")
        composeRule.onNodeWithTag(ServiceTags.AMOUNT).performTextInput("1200")
        composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)

        val serviceId = dash(vehicleId).recentServices.first {
            ServiceKind.fromStorage(it.serviceType) == ServiceKind.TO
        }.id

        // Edit: Add button must move draft into the list (bug: text stayed in the field)
        composeRule.onNodeWithTag(DashboardTags.feedService(serviceId)).performClick()
        composeRule.onNodeWithTag(TimelineTags.actionEdit("SERVICE", serviceId)).performClick()
        waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.CHECKLIST_INPUT).performTextInput(workOnEdit)
        composeRule.onNodeWithTag(ServiceTags.CHECKLIST_ADD).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(workOnEdit, substring = true).assertIsDisplayed()
        // Bug regression: draft must leave the input after Add
        composeRule.onNodeWithTag(ServiceTags.CHECKLIST_INPUT)
            .assert(hasText(workOnEdit, substring = true).not())

        // Type second item but do not tap Add — Save must flush draft
        composeRule.onNodeWithTag(ServiceTags.CHECKLIST_INPUT).performTextInput(workOnSaveFlush)
        composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)

        val saved = dash(vehicleId).recentServices.first { it.id == serviceId }
        val checklistJson = saved.checklistJson.orEmpty()
        assertTrue("checklist must contain edit-add item", checklistJson.contains("иридиевые свечи"))
        assertTrue("checklist must contain save-flush item", checklistJson.contains("масло моторное"))

        // Feed expand shows full checklist lines, not only "Чек-лист"
        composeRule.onNodeWithTag(DashboardTags.feedService(serviceId)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText(workOnEdit, substring = true).assertIsDisplayed()
        composeRule.onNodeWithText(workOnSaveFlush, substring = true).assertIsDisplayed()
        assertFalse(
            "feed must not show bare Чек-лист placeholder",
            composeRule.onAllNodesWithText("Чек-лист", substring = false)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        composeRule.onNodeWithTag(DashboardTags.feedService(serviceId)).performClick()
        composeRule.waitForIdle()

        // Newer ТО: km since previous ТО lives in the expansion, not on the road
        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag("add_service_kind_TO").performClick()
        waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextInput("107400")
        composeRule.onNodeWithTag(ServiceTags.AMOUNT).performTextInput("800")
        composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
        waitTag(DashboardTags.ROOT)
        val newerToId = dash(vehicleId).recentServices
            .filter { ServiceKind.fromStorage(it.serviceType) == ServiceKind.TO }
            .maxBy { it.odometerKm }
            .id
        composeRule.onNodeWithTag(DashboardTags.feedService(newerToId)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("С прошлого ТО", substring = true).assertIsDisplayed()
        composeRule.onNodeWithText("1 000 км", substring = true).assertIsDisplayed()

        // Cleanup via Settings (delete no longer in vehicle selector)
        deleteVehicleViaSettings(vehicleId)
    }

    /**
     * Settings / Google copy must stay product-facing:
     * no Cloud Console / sync jargon in visible UI.
     */
    @Test
    fun settingsGoogleSection_showsProductCopy_withoutDevJargon() {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty()
        }

        // Need a vehicle so dashboard settings gear is available
        if (composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN).fetchSemanticsNodes().isNotEmpty()) {
            val suffix = System.currentTimeMillis().toString().takeLast(6)
            composeRule.onNodeWithTag(AddVehicleTags.NAME).performTextInput("UITest Settings $suffix")
            composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextClearance()
            composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextInput("1000")
            composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextClearance()
            composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextInput("40")
            composeRule.onNodeWithTag(AddVehicleTags.SAVE).performClick()
            waitTag(DashboardTags.ROOT)
        } else {
            waitTag(DashboardTags.ROOT)
        }

        composeRule.onNodeWithTag(DashboardTags.OPEN_SETTINGS).performClick()
        waitTag(SettingsTags.ROOT)
        composeRule.onNodeWithTag(SettingsTags.GOOGLE_SECTION).performScrollTo().assertIsDisplayed()

        val linkVisible = composeRule.onAllNodesWithTag(SettingsTags.GOOGLE_LINK)
            .fetchSemanticsNodes().isNotEmpty()
        if (linkVisible) {
            composeRule.onNodeWithTag(SettingsTags.GOOGLE_LINK).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Привязать Google", substring = false).assertIsDisplayed()
            composeRule.onNodeWithText("Записи на телефоне не удаляются", substring = true)
                .assertIsDisplayed()
        } else {
            // Already linked on this device — still check product copy around sync controls
            composeRule.onNodeWithTag(SettingsTags.GOOGLE_SYNC_NOW).performScrollTo().assertIsDisplayed()
            composeRule.onNodeWithText("Сохранить в Google сейчас", substring = true).assertIsDisplayed()
        }
        composeRule.onNodeWithTag(SettingsTags.GOOGLE_RESTORE).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithText("Восстановить из Google", substring = false).assertIsDisplayed()
        composeRule.onNodeWithText("Заменит записи на телефоне", substring = true).assertIsDisplayed()

        composeRule.onNodeWithTag(SettingsTags.VEHICLE_SECTION).performScrollTo().assertIsDisplayed()
        composeRule.onNodeWithTag(SettingsTags.DELETE_VEHICLE).assertIsDisplayed()

        composeRule.onNodeWithText("Резервное копирование", substring = true)
            .performScrollTo()
            .assertIsDisplayed()

        val forbidden = listOf(
            "OAuth",
            "Cloud Console",
            "SHA-1",
            "local.properties",
            "BuildConfig",
            "синк",
            "снимок",
            "чек-лист",
            "Чек-лист",
            "→ облако",
            "телефон →",
        )
        forbidden.forEach { bad ->
            assertTrue(
                "UI must not show \"$bad\"",
                composeRule.onAllNodesWithText(bad, substring = true)
                    .fetchSemanticsNodes()
                    .isEmpty(),
            )
        }

        pressSystemBack()
        waitTag(DashboardTags.ROOT)
    }

    private fun deleteVehicleViaSettings(vehicleId: Long) {
        runBlocking {
            AutologApp.instance.container.repository.switchVehicle(vehicleId)
        }
        composeRule.waitForIdle()

        if (composeRule.onAllNodesWithTag(SettingsTags.ROOT).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(SettingsTags.BACK).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(12_000) {
            composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty()
        }
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
        composeRule.waitForIdle()

        // Leave settings: Back, or NavHost moves to AddVehicle when no cars left
        if (composeRule.onAllNodesWithTag(SettingsTags.ROOT).fetchSemanticsNodes().isNotEmpty()) {
            composeRule.onNodeWithTag(SettingsTags.BACK).performClick()
            composeRule.waitForIdle()
        }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(SettingsTags.ROOT).fetchSemanticsNodes().isEmpty()
        }
    }

    private fun pruneUitestVehicles() {
        runBlocking {
            val repo = AutologApp.instance.container.repository
            repo.observeVehicles().first()
                .filter { it.name.startsWith("UITest") }
                .forEach { repo.deleteVehicle(it.id) }
        }
    }

    private fun ensureAddVehicleScreen() {
        pruneUitestVehicles()
        val onAdd = composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN)
            .fetchSemanticsNodes().isNotEmpty()
        if (onAdd) return
        composeRule.onNodeWithTag(VehicleSelectorTags.TRIGGER).performClick()
        waitTag(VehicleSelectorTags.SHEET)
        composeRule.onNodeWithTag(VehicleSelectorTags.ADD_VEHICLE).performClick()
        waitTag(AddVehicleTags.SCREEN)
    }

    private fun pressSystemBack() {
        composeRule.runOnUiThread {
            composeRule.activity.onBackPressedDispatcher.onBackPressed()
        }
        composeRule.waitForIdle()
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
