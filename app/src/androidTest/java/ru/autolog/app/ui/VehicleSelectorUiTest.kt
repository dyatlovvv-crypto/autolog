package ru.autolog.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assume.assumeTrue
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.autolog.app.MainActivity
import ru.autolog.app.ui.dashboard.DashboardTags
import ru.autolog.app.ui.dashboard.VehicleSelectorTags

/**
 * UX smoke on device: vehicle selector chip opens bottom sheet with add action.
 */
@RunWith(AndroidJUnit4::class)
class VehicleSelectorUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun vehicleSelector_opensSheet_showsAddVehicle() {
        composeRule.waitForIdle()
        val onDashboard = runCatching {
            composeRule.onNodeWithTag(DashboardTags.ROOT).assertIsDisplayed()
            composeRule.onNodeWithTag(VehicleSelectorTags.TRIGGER).assertIsDisplayed()
        }.isSuccess
        assumeTrue("Need existing vehicle on device for selector UX test", onDashboard)

        composeRule.onNodeWithTag(VehicleSelectorTags.TRIGGER).performClick()
        composeRule.waitForIdle()

        composeRule.onNodeWithTag(VehicleSelectorTags.SHEET).assertIsDisplayed()
        composeRule.onNodeWithTag(VehicleSelectorTags.ADD_VEHICLE).assertIsDisplayed()
    }

    @Test
    fun dashboard_showsConsumptionCards_whenVehicleExists() {
        composeRule.waitForIdle()
        val onDashboard = runCatching {
            composeRule.onNodeWithTag(DashboardTags.ROOT).assertIsDisplayed()
        }.isSuccess
        assumeTrue("Need dashboard with vehicle", onDashboard)

        composeRule.onNodeWithTag(DashboardTags.AVG_CONSUMPTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.LAST_CONSUMPTION).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.ACTIVITY_FEED).assertIsDisplayed()
    }
}
