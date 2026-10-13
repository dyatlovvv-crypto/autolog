package ru.autolog.app.ui

import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.assertTextContains
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onAllNodesWithText
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performScrollTo
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.autolog.app.AutologApp
import ru.autolog.app.MainActivity
import ru.autolog.app.data.settings.SyncPeriod
import ru.autolog.app.data.sync.CloudBackupGateway
import ru.autolog.app.data.sync.CloudSyncWorker
import ru.autolog.app.data.sync.FakeCloudBackupGateway
import ru.autolog.app.data.sync.GoogleDriveBackupRepository
import ru.autolog.app.ui.dashboard.DashboardTags
import ru.autolog.app.ui.settings.SettingsTags
import ru.autolog.app.ui.vehicle.AddVehicleTags

/**
 * Cloud sync failure UX that FullFlow cannot cover against live Drive.
 * Fake gateway injects quota / network / API-disabled without wiping user data.
 */
@RunWith(AndroidJUnit4::class)
class CloudSyncFailureUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private lateinit var realGateway: CloudBackupGateway
    private var createdVehicleId: Long? = null

    @Before
    fun captureRealGateway() {
        realGateway = AutologApp.instance.container.googleDrive
    }

    @After
    fun cleanup() {
        runCatching { dismissTransientUi() }
        val app = AutologApp.instance
        app.container.googleDrive = when (val g = realGateway) {
            is GoogleDriveBackupRepository -> g
            else -> GoogleDriveBackupRepository(app)
        }
        runBlocking {
            app.container.settingsStore.clearGoogleAccount()
            app.container.settingsStore.setLastCloudSyncEpochMs(null)
            app.container.settingsStore.setSyncPeriod(SyncPeriod.OFF)
            CloudSyncWorker.enqueue(app, SyncPeriod.OFF)
            createdVehicleId?.let { id ->
                runCatching { app.container.repository.deleteVehicle(id) }
            }
            createdVehicleId = null
        }
    }

    @Test
    fun syncNow_whenDriveQuotaExceeded_showsDialog_andDoesNotMarkSynced() {
        val fake = FakeCloudBackupGateway(
            uploadFailure = Exception(
                "403 Forbidden: The user's Drive storage quota has been exceeded. " +
                    "reason=storageQuotaExceeded",
            ),
        )
        prepareLinkedSettings(fake)
        openSettingsGoogleSection()

        composeRule.onNodeWithText("Ещё не сохраняли в Google", substring = true)
            .assertIsDisplayed()
        triggerSyncAndAwaitError(fake, "закончилось место")
        composeRule.onNodeWithTag("${SettingsTags.GOOGLE_ERROR_DIALOG}_text")
            .assertTextContains("Освободите место", substring = true)

        assertEquals(1, fake.uploadCalls)
        val snapshot = runBlocking { appSettings() }
        assertNull(snapshot.lastCloudSyncEpochMs)
        // Dismiss dialog so status line under chips is visible again
        composeRule.onNodeWithText("ОК").performClick()
        composeRule.onNodeWithText("Ещё не сохраняли в Google", substring = true)
            .assertIsDisplayed()
    }

    @Test
    fun syncNow_whenNetworkFails_showsOfflineCopy() {
        val fake = FakeCloudBackupGateway(
            uploadFailure = Exception(
                "Unable to resolve host www.googleapis.com: No address associated with hostname",
            ),
        )
        prepareLinkedSettings(fake)
        openSettingsGoogleSection()
        triggerSyncAndAwaitError(fake, "Нет связи с Google")
    }

    @Test
    fun syncNow_whenDriveApiDisabled_showsUnavailableCopy() {
        val fake = FakeCloudBackupGateway(
            uploadFailure = Exception(
                "Access Not Configured. Drive API has not been used in project before or it is disabled.",
            ),
        )
        prepareLinkedSettings(fake)
        openSettingsGoogleSection()
        triggerSyncAndAwaitError(fake, "пока недоступно")
    }

    @Test
    fun syncNow_success_updatesLastSyncLine() {
        val fake = FakeCloudBackupGateway(uploadFailure = null)
        prepareLinkedSettings(fake)
        openSettingsGoogleSection()
        composeRule.onNodeWithTag(SettingsTags.GOOGLE_SYNC_NOW).performScrollTo().performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithText("Последнее сохранение", substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(fake.uploadCalls >= 1)
        assertTrue(runBlocking { appSettings() }.lastCloudSyncEpochMs != null)
    }

    private suspend fun appSettings() =
        AutologApp.instance.container.settingsStore.settings.first()

    private fun triggerSyncAndAwaitError(fake: FakeCloudBackupGateway, textSubstring: String) {
        assertTrue(AutologApp.instance.container.googleDrive === fake)
        composeRule.onNodeWithTag(SettingsTags.GOOGLE_SYNC_NOW)
            .assertIsDisplayed()
            .performScrollTo()
            .performClick()
        composeRule.waitUntil(25_000) { fake.uploadCalls >= 1 }
        // One UI may host AlertDialog in a separate window; match copy in any semantics node.
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithText(textSubstring, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        assertTrue(
            "expected error copy: $textSubstring",
            composeRule.onAllNodesWithText(textSubstring, substring = true)
                .fetchSemanticsNodes()
                .isNotEmpty(),
        )
        assertNull(
            "failed sync must not mark lastCloudSync",
            runBlocking { appSettings() }.lastCloudSyncEpochMs,
        )
    }

    private fun prepareLinkedSettings(fake: FakeCloudBackupGateway) {
        AutologApp.instance.container.googleDrive = fake
        runBlocking {
            AutologApp.instance.container.settingsStore.setGoogleAccount(
                email = "uitest-cloud@example.com",
                displayName = "UITest Cloud",
                photoUrl = null,
            )
            AutologApp.instance.container.settingsStore.setLastCloudSyncEpochMs(null)
            AutologApp.instance.container.settingsStore.setSyncPeriod(SyncPeriod.OFF)
            CloudSyncWorker.enqueue(AutologApp.instance, SyncPeriod.OFF)
        }
        assertTrue(
            "fake gateway must be installed",
            AutologApp.instance.container.googleDrive === fake,
        )
    }

    private fun dismissTransientUi() {
        repeat(4) {
            val hasDialog = composeRule.onAllNodesWithTag(SettingsTags.GOOGLE_ERROR_DIALOG)
                .fetchSemanticsNodes()
                .isNotEmpty()
            val hasSettings = composeRule.onAllNodesWithTag(SettingsTags.ROOT)
                .fetchSemanticsNodes()
                .isNotEmpty()
            val onDash = composeRule.onAllNodesWithTag(DashboardTags.ROOT)
                .fetchSemanticsNodes()
                .isNotEmpty()
            if (onDash && !hasDialog) return
            if (hasDialog || hasSettings) {
                composeRule.runOnUiThread {
                    composeRule.activity.onBackPressedDispatcher.onBackPressed()
                }
                composeRule.waitForIdle()
            } else {
                return
            }
        }
    }

    private fun openSettingsGoogleSection() {
        // Always return to dashboard so settings recomposes with the injected gateway.
        dismissTransientUi()
        repeat(3) {
            if (composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty()) {
                return@repeat
            }
            if (composeRule.onAllNodesWithTag(SettingsTags.ROOT).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(SettingsTags.GOOGLE_ERROR_DIALOG)
                    .fetchSemanticsNodes()
                    .isNotEmpty()
            ) {
                composeRule.runOnUiThread {
                    composeRule.activity.onBackPressedDispatcher.onBackPressed()
                }
                composeRule.waitForIdle()
            }
        }
        composeRule.waitUntil(15_000) {
            composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN).fetchSemanticsNodes().isNotEmpty() ||
                composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty()
        }
        if (composeRule.onAllNodesWithTag(AddVehicleTags.SCREEN).fetchSemanticsNodes().isNotEmpty()) {
            val suffix = System.currentTimeMillis().toString().takeLast(6)
            val name = "UITest SyncGate $suffix"
            composeRule.onNodeWithTag(AddVehicleTags.NAME).performTextInput(name)
            composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextClearance()
            composeRule.onNodeWithTag(AddVehicleTags.ODOMETER).performTextInput("1000")
            composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextClearance()
            composeRule.onNodeWithTag(AddVehicleTags.TANK).performTextInput("50")
            composeRule.onNodeWithTag(AddVehicleTags.SAVE).performClick()
            composeRule.waitUntil(10_000) {
                composeRule.onAllNodesWithTag(DashboardTags.ROOT).fetchSemanticsNodes().isNotEmpty()
            }
            createdVehicleId = runBlocking {
                AutologApp.instance.container.repository.observeVehicles().first()
                    .firstOrNull { it.name == name }?.id
            }
        }
        composeRule.onNodeWithTag(DashboardTags.OPEN_SETTINGS).performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag(SettingsTags.ROOT).fetchSemanticsNodes().isNotEmpty()
        }
        composeRule.onNodeWithTag(SettingsTags.GOOGLE_SECTION).performScrollTo().assertIsDisplayed()
    }
}
