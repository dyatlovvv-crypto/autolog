package ru.autolog.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.autolog.app.AutologApp
import ru.autolog.app.MainActivity
import ru.autolog.app.data.map.FuelStationsJson
import ru.autolog.app.ui.dashboard.DashboardTags
import ru.autolog.app.ui.map.GasMapTags
import ru.autolog.app.ui.map.StationMiniMapTags
import ru.autolog.app.ui.refuel.AddRefuelTags
import ru.autolog.app.ui.support.AutologUiRobot

/**
 * Gas map: open/back without crash, Espresso pick returns station name.
 * Uses cached stations seed (no live Overpass). Isolated UITest vehicle only.
 */
@RunWith(AndroidJUnit4::class)
class GasMapUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { AutologUiRobot(composeRule) }

    @Before
    fun pruneStaleUitestVehicles() {
        composeRule.waitForIdle()
        robot.pruneUitestVehicles()
        AutologApp.instance.container.fuelStations.seedCacheForTests(
            lat = 55.75,
            lon = 37.62,
            json = """{"elements":[{"type":"node","lat":55.75,"lon":37.62,"tags":{"name":"UITest Cached АЗС"}}]}""",
        )
    }

    @Test
    fun fuelStationsCache_seedReadableOnDevice() {
        val cached = AutologApp.instance.container.fuelStations.cachedOrNull()
        assertNotNull(cached)
        assertEquals(55.75, cached!!.lat, 0.0001)
    }

    @Test
    fun fuelStationsCache_rejectsOutTagsWithoutCoordinates() {
        val store = AutologApp.instance.container.fuelStations
        // Simulate broken Overpass `out tags` payload (no lat/lon).
        store.seedCacheForTests(
            lat = 55.75,
            lon = 37.62,
            json = """{"elements":[{"type":"node","id":1,"tags":{"amenity":"fuel","name":"Без координат"}}]}""",
        )
        assertNull(store.cachedOrNull())
        assertNull(
            FuelStationsJson.sortAndCapByDistance(
                """{"elements":[{"type":"node","id":1,"tags":{"name":"X"}}]}""",
                55.75,
                37.62,
            ),
        )
        // Restore valid seed for later tests in the class.
        store.seedCacheForTests(
            lat = 55.75,
            lon = 37.62,
            json = """{"elements":[{"type":"node","lat":55.75,"lon":37.62,"tags":{"name":"UITest Cached АЗС"}}]}""",
        )
        assertNotNull(store.cachedOrNull())
    }

    @Test
    fun fuelStationsCache_acceptsGdebenzShapedStations() {
        val store = AutologApp.instance.container.fuelStations
        val fromGdebenz = FuelStationsJson.fromGdebenzArray(
            org.json.JSONArray(
                """[{"osm_id":"248014390","name":"Роснефть","brand":"Роснефть","lat":55.75,"lon":37.62,"addr":"тест"}]""",
            ),
        )
        assertNotNull(fromGdebenz)
        store.seedCacheForTests(55.75, 37.62, fromGdebenz!!)
        assertNotNull(store.cachedOrNull())
        assertEquals(55.75, store.cachedOrNull()!!.lat, 0.0001)
    }

    @Test
    fun map_showsMyLocationButton() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carName = "UITest Loc $suffix"
        val vehicleId = robot.createVehicle(carName, "51100", "55", "L$suffix")
        composeRule.onNodeWithTag(DashboardTags.ADD_REFUEL).performClick()
        robot.waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextInput("51100")
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextInput("10")
        composeRule.onNodeWithTag(AddRefuelTags.OPEN_MAP).performScrollTo().performClick()
        robot.waitTag(GasMapTags.SCREEN)
        composeRule.onNodeWithTag(GasMapTags.MY_LOCATION).assertIsDisplayed()
        composeRule.onNodeWithTag(GasMapTags.BACK).performClick()
        robot.waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performScrollTo().performClick()
        robot.waitTag(DashboardTags.ROOT)
        robot.deleteVehicleViaSettings(vehicleId)
    }

    @Test
    fun map_openBack_andPickStation_doesNotCrash() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carName = "UITest Map $suffix"
        val vehicleId = robot.createVehicle(carName, "51000", "55", "M$suffix")

        assertNotNull(AutologApp.instance.container.fuelStations.cachedOrNull())

        composeRule.onNodeWithTag(DashboardTags.ADD_REFUEL).performClick()
        robot.waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextInput("51000")
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextInput("20")

        composeRule.onNodeWithTag(AddRefuelTags.OPEN_MAP).performScrollTo().performClick()
        robot.waitTag(GasMapTags.SCREEN)
        composeRule.onNodeWithTag(GasMapTags.BACK).assertIsDisplayed()
        composeRule.onNodeWithTag(GasMapTags.BACK).performClick()
        robot.waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).assertIsDisplayed()

        composeRule.onNodeWithTag(AddRefuelTags.OPEN_MAP).performScrollTo().performClick()
        robot.waitTag(GasMapTags.SCREEN)
        composeRule.onNodeWithTag(GasMapTags.PICK_TEST).assertIsDisplayed()
        composeRule.onNodeWithTag(GasMapTags.PICK_TEST).performClick()
        robot.waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.STATION)
            .assertTextContains("UITest АЗС", substring = true)
        // Slot present (snapshot may still be building offline).
        composeRule.onNodeWithTag(StationMiniMapTags.MAP).performScrollTo().assertIsDisplayed()

        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performScrollTo().performClick()
        robot.waitTag(DashboardTags.ROOT, timeoutMs = 20_000)

        robot.deleteVehicleViaSettings(vehicleId)
    }
}
