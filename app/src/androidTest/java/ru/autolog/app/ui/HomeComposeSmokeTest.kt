package ru.autolog.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.autolog.app.MainActivity
import ru.autolog.app.ui.dashboard.DashboardTags
import ru.autolog.app.ui.vehicle.AddVehicleTags

@RunWith(AndroidJUnit4::class)
class HomeComposeSmokeTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    @Test
    fun rootComposable_rendersPrimaryScreen() {
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty()
        }
        val ok = runCatching {
            composeRule.onNodeWithTag(AddVehicleTags.SCREEN).assertIsDisplayed()
        }.isSuccess || runCatching {
            composeRule.onNodeWithTag(DashboardTags.ROOT).assertIsDisplayed()
        }.isSuccess
        assert(ok)
    }
}
