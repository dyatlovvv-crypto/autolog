package ru.autolog.app.ui

import android.graphics.Bitmap
import android.graphics.Color
import androidx.compose.ui.test.assertIsDisplayed
import androidx.compose.ui.test.junit4.createAndroidComposeRule
import androidx.compose.ui.test.onAllNodesWithTag
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.performTextClearance
import androidx.compose.ui.test.performTextInput
import androidx.test.ext.junit.runners.AndroidJUnit4
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import ru.autolog.app.AutologApp
import ru.autolog.app.MainActivity
import ru.autolog.app.data.local.VehicleDocsStore
import ru.autolog.app.data.local.entity.VehicleDocumentEntity
import ru.autolog.app.domain.ServiceKind
import ru.autolog.app.ui.components.TimelineTags
import ru.autolog.app.ui.dashboard.DashboardTags
import ru.autolog.app.ui.docs.DocumentViewerTags
import ru.autolog.app.ui.docs.VehicleDocsTags
import ru.autolog.app.ui.history.HistoryTags
import ru.autolog.app.ui.note.NoteTags
import ru.autolog.app.ui.refuel.AddRefuelTags
import ru.autolog.app.ui.service.ServiceTags
import ru.autolog.app.ui.support.AutologUiRobot
import java.io.File
import java.io.FileOutputStream

/**
 * Journeys not covered by FullFlow: repair/planned, 0 km between TOs,
 * explicit full-tank checkbox, vehicle switch isolation.
 * Seeds and deletes only UITest vehicles.
 */
@RunWith(AndroidJUnit4::class)
class CoreGapUiTest {

    @get:Rule
    val composeRule = createAndroidComposeRule<MainActivity>()

    private val robot by lazy { AutologUiRobot(composeRule) }

    @Before
    fun pruneStaleUitestVehicles() {
        composeRule.waitForIdle()
        robot.pruneUitestVehicles()
    }

    @Test
    fun repairAndPlanned_historyViewAndDelete() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carName = "UITest Repair $suffix"
        val vehicleId = robot.createVehicle(carName, "80000", "50", "R$suffix")

        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag("add_service_kind_REPAIR").performClick()
        robot.waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextInput("80000")
        composeRule.onNodeWithTag(ServiceTags.AMOUNT).performTextInput("4200")
        composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
        robot.waitTag(DashboardTags.ROOT)

        val repairId = robot.dash(vehicleId).recentServices.first {
            ServiceKind.fromStorage(it.serviceType) == ServiceKind.REPAIR
        }.id

        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag(DashboardTags.SERVICE_HUB_HISTORY_SECTION).performClick()
        composeRule.onNodeWithTag(DashboardTags.serviceHubHistory("REPAIR")).performClick()
        robot.waitTag(HistoryTags.ROOT)
        composeRule.onNodeWithTag(HistoryTags.FILTER_REPAIR).assertIsDisplayed()
        composeRule.onNodeWithTag("history_service_$repairId").assertIsDisplayed()
        composeRule.onNodeWithTag("history_service_$repairId").performClick()
        robot.waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.EDIT).assertIsDisplayed()
        robot.pressSystemBack()
        robot.waitTag(HistoryTags.ROOT)
        robot.pressSystemBack()
        robot.waitTag(DashboardTags.ROOT)

        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag("add_service_kind_PLANNED").performClick()
        robot.waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextInput("80100")
        composeRule.onNodeWithTag(ServiceTags.AMOUNT).performTextInput("0")
        composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
        robot.waitTag(DashboardTags.ROOT)

        val plannedId = robot.dash(vehicleId).recentServices.first {
            ServiceKind.fromStorage(it.serviceType) == ServiceKind.PLANNED
        }.id
        composeRule.onNodeWithTag(DashboardTags.feedService(plannedId)).performClick()
        composeRule.onNodeWithTag(TimelineTags.actionView("SERVICE", plannedId)).performClick()
        robot.waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.EDIT).assertIsDisplayed()
        robot.pressSystemBack()
        robot.waitTag(DashboardTags.ROOT)

        composeRule.onNodeWithTag(DashboardTags.feedService(repairId)).performClick()
        composeRule.onNodeWithTag(TimelineTags.actionDelete("SERVICE", repairId)).performClick()
        composeRule.onNodeWithTag(TimelineTags.DELETE_CONFIRM).performClick()
        composeRule.waitUntil(8_000) {
            composeRule.onAllNodesWithTag(DashboardTags.feedService(repairId))
                .fetchSemanticsNodes()
                .isEmpty()
        }

        robot.deleteVehicleViaSettings(vehicleId)
    }

    @Test
    fun twoToSameOdometer_showsZeroKmSincePrevious() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carName = "UITest ZeroTO $suffix"
        val vehicleId = robot.createVehicle(carName, "116000", "45", "Z$suffix")

        repeat(2) {
            composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
            composeRule.onNodeWithTag("add_service_kind_TO").performClick()
            robot.waitTag(ServiceTags.SCREEN)
            composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextClearance()
            composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextInput("116000")
            composeRule.onNodeWithTag(ServiceTags.AMOUNT).performTextInput("100")
            composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
            robot.waitTag(DashboardTags.ROOT)
        }

        val newerToId = robot.dash(vehicleId).recentServices
            .filter { ServiceKind.fromStorage(it.serviceType) == ServiceKind.TO }
            .maxBy { it.id }
            .id
        composeRule.onNodeWithTag(DashboardTags.feedService(newerToId)).performClick()
        composeRule.waitForIdle()
        composeRule.onNodeWithText("С прошлого ТО · 0 км", substring = false).assertIsDisplayed()

        robot.deleteVehicleViaSettings(vehicleId)
    }

    @Test
    fun notepad_autoSaveAndPerVehicleIsolation() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carA = "UITest NoteA $suffix"
        val carB = "UITest NoteB $suffix"
        val idA = robot.createVehicle(carA, "50000", "45", "A$suffix")
        val idB = robot.createVehicle(carB, "60000", "50", "B$suffix")

        composeRule.onNodeWithTag(DashboardTags.OPEN_NOTEPAD).performClick()
        robot.waitTag(NoteTags.SCREEN)
        composeRule.onNodeWithTag(NoteTags.TEXT).performClick()
        composeRule.onNodeWithTag(NoteTags.TEXT).performTextInput("Мысли B $suffix")
        composeRule.onNodeWithTag(NoteTags.BACK).performClick()
        robot.waitTag(DashboardTags.ROOT)
        assertEquals("Мысли B $suffix", robot.notepadText(idB))

        robot.switchVehicle(idA)
        composeRule.onNodeWithTag(DashboardTags.OPEN_NOTEPAD).performClick()
        robot.waitTag(NoteTags.SCREEN)
        composeRule.onNodeWithTag(NoteTags.TEXT).performClick()
        composeRule.onNodeWithTag(NoteTags.TEXT).performTextInput("Мысли A $suffix")
        composeRule.onNodeWithTag(NoteTags.BACK).performClick()
        robot.waitTag(DashboardTags.ROOT)
        assertEquals("Мысли A $suffix", robot.notepadText(idA))

        robot.switchVehicle(idB)
        composeRule.onNodeWithTag(DashboardTags.OPEN_NOTEPAD).performClick()
        robot.waitTag(NoteTags.SCREEN)
        composeRule.onNodeWithTag(NoteTags.TEXT).performClick()
        composeRule.onNodeWithTag(NoteTags.TEXT).performTextClearance()
        composeRule.onNodeWithTag(NoteTags.TEXT).performTextInput("Правка B $suffix")
        composeRule.onNodeWithTag(NoteTags.BACK).performClick()
        robot.waitTag(DashboardTags.ROOT)

        assertEquals("Мысли A $suffix", robot.notepadText(idA))
        assertEquals("Правка B $suffix", robot.notepadText(idB))

        robot.deleteVehicleViaSettings(idA)
        robot.deleteVehicleViaSettings(idB)
    }

    @Test
    fun vehicleDocs_openAndSwitchViewModes() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carName = "UITest Docs $suffix"
        val vehicleId = robot.createVehicle(carName, "50000", "45", "D$suffix")

        composeRule.onNodeWithTag(DashboardTags.OPEN_DOCS).performClick()
        robot.waitTag(VehicleDocsTags.SCREEN)
        composeRule.onNodeWithTag(VehicleDocsTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(VehicleDocsTags.VIEW_MODE).performClick()
        composeRule.onNodeWithTag(VehicleDocsTags.VIEW_GRID).performClick()
        composeRule.onNodeWithTag(VehicleDocsTags.VIEW_MODE).performClick()
        composeRule.onNodeWithTag(VehicleDocsTags.VIEW_CARDS).performClick()
        composeRule.onNodeWithTag(VehicleDocsTags.VIEW_MODE).performClick()
        composeRule.onNodeWithTag(VehicleDocsTags.VIEW_LIST).performClick()
        composeRule.onNodeWithTag(VehicleDocsTags.EMPTY).assertIsDisplayed()
        composeRule.onNodeWithTag(VehicleDocsTags.BACK).performClick()
        robot.waitTag(DashboardTags.ROOT)

        robot.deleteVehicleViaSettings(vehicleId)
    }

    @Test
    fun vehicleDocs_openPdfAndPhotoInAppViewer() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carName = "UITest DocsOpen $suffix"
        val vehicleId = robot.createVehicle(carName, "51000", "45", "O$suffix")
        val (pdfId, photoId) = seedPdfAndPhoto(vehicleId, suffix)

        composeRule.onNodeWithTag(DashboardTags.OPEN_DOCS).performClick()
        robot.waitTag(VehicleDocsTags.SCREEN)
        composeRule.onNodeWithTag(VehicleDocsTags.item(pdfId)).assertIsDisplayed()
        composeRule.onNodeWithTag(VehicleDocsTags.item(pdfId)).performClick()
        robot.waitTag(DocumentViewerTags.SCREEN)
        composeRule.waitUntil(15_000) {
            val err = composeRule.onAllNodesWithTag(DocumentViewerTags.PDF_ERROR)
                .fetchSemanticsNodes()
            if (err.isNotEmpty()) {
                error("PDF viewer error on screen")
            }
            composeRule.onAllNodesWithTag(DocumentViewerTags.PDF_LIST)
                .fetchSemanticsNodes()
                .isNotEmpty()
        }
        composeRule.onNodeWithTag(DocumentViewerTags.PDF_LIST).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentViewerTags.BACK).performClick()
        robot.waitTag(VehicleDocsTags.SCREEN)

        composeRule.onNodeWithTag(VehicleDocsTags.item(photoId)).performClick()
        robot.waitTag(DocumentViewerTags.SCREEN)
        composeRule.onNodeWithTag(DocumentViewerTags.OPEN_EXTERNAL).assertIsDisplayed()
        composeRule.onNodeWithTag(DocumentViewerTags.BACK).performClick()
        robot.waitTag(VehicleDocsTags.SCREEN)
        composeRule.onNodeWithTag(VehicleDocsTags.BACK).performClick()
        robot.waitTag(DashboardTags.ROOT)

        robot.deleteVehicleViaSettings(vehicleId)
    }

    private fun seedPdfAndPhoto(vehicleId: Long, suffix: String): Pair<Long, Long> = runBlocking {
        val app = AutologApp.instance
        val ctx = app.applicationContext
        val dir = VehicleDocsStore.dir(ctx, vehicleId)
        val pdfFile = File(dir, "uitest_$suffix.pdf")
        pdfFile.writeBytes(MINIMAL_PDF)
        val photoFile = File(dir, "uitest_$suffix.png")
        FileOutputStream(photoFile).use { out ->
            Bitmap.createBitmap(16, 16, Bitmap.Config.ARGB_8888).apply {
                eraseColor(Color.BLUE)
            }.compress(Bitmap.CompressFormat.PNG, 100, out)
        }
        val dao = app.container.database.vehicleDocumentDao()
        val now = System.currentTimeMillis()
        val pdfId = dao.insert(
            VehicleDocumentEntity(
                vehicleId = vehicleId,
                title = "UITest PDF $suffix",
                mimeType = "application/pdf",
                filePath = pdfFile.absolutePath,
                createdAtEpochMs = now,
            ),
        )
        val photoId = dao.insert(
            VehicleDocumentEntity(
                vehicleId = vehicleId,
                title = "UITest Photo $suffix",
                mimeType = "image/png",
                filePath = photoFile.absolutePath,
                createdAtEpochMs = now + 1,
            ),
        )
        pdfId to photoId
    }

    private companion object {
        // Valid one-page PDF with correct xref (PdfRenderer rejects broken offsets).
        val MINIMAL_PDF = hexToBytes(
            "255044462d312e340a312030206f626a0a3c3c202f54797065202f436174616c6f67202f5061676573203220302052203e3e0a656e646f626a0a322030206f626a0a3c3c202f54797065202f5061676573202f4b696473205b33203020525d202f436f756e742031203e3e0a656e646f626a0a332030206f626a0a3c3c202f54797065202f50616765202f506172656e74203220302052202f4d65646961426f78205b30203020333030203430305d202f436f6e74656e7473203420302052202f5265736f7572636573203c3c202f466f6e74203c3c202f4631203520302052203e3e203e3e203e3e0a656e646f626a0a342030206f626a0a3c3c202f4c656e677468203337203e3e0a73747265616d0a4254202f46312032342054662035302033353020546420285549546573742920546a2045540a656e6473747265616d0a656e646f626a0a352030206f626a0a3c3c202f54797065202f466f6e74202f53756274797065202f5479706531202f42617365466f6e74202f48656c766574696361203e3e0a656e646f626a0a787265660a3020360a303030303030303030302036353533352066200a30303030303030303039203030303030206e200a30303030303030303538203030303030206e200a30303030303030313135203030303030206e200a30303030303030323431203030303030206e200a30303030303030333238203030303030206e200a747261696c65720a3c3c202f53697a652036202f526f6f74203120302052203e3e0a7374617274787265660a3339380a2525454f460a",
        )

        fun hexToBytes(hex: String): ByteArray {
            val clean = hex.trim()
            return ByteArray(clean.length / 2) { i ->
                clean.substring(i * 2, i * 2 + 2).toInt(16).toByte()
            }
        }
    }

    @Test
    fun vehicleSwitch_isolatesRepairAndTankFullCheckboxToggles() {
        val suffix = System.currentTimeMillis().toString().takeLast(6)
        val carA = "UITest SwitchA $suffix"
        val carB = "UITest SwitchB $suffix"
        val idA = robot.createVehicle(carA, "10000", "40", "A$suffix")

        composeRule.onNodeWithTag(DashboardTags.ADD_SERVICE).performClick()
        composeRule.onNodeWithTag("add_service_kind_REPAIR").performClick()
        robot.waitTag(ServiceTags.SCREEN)
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(ServiceTags.ODOMETER).performTextInput("10000")
        composeRule.onNodeWithTag(ServiceTags.AMOUNT).performTextInput("900")
        composeRule.onNodeWithTag(ServiceTags.SAVE).performClick()
        robot.waitTag(DashboardTags.ROOT)
        val repairId = robot.dash(idA).recentServices.first {
            ServiceKind.fromStorage(it.serviceType) == ServiceKind.REPAIR
        }.id
        composeRule.onNodeWithTag(DashboardTags.feedService(repairId)).assertIsDisplayed()

        composeRule.onNodeWithTag(DashboardTags.ADD_REFUEL).performClick()
        robot.waitTag(AddRefuelTags.SCREEN)
        composeRule.onNodeWithTag(AddRefuelTags.TANK_FULL).assertIsDisplayed()
        composeRule.onNodeWithTag(AddRefuelTags.TANK_FULL).performClick()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextClearance()
        composeRule.onNodeWithTag(AddRefuelTags.ODOMETER).performTextInput("10000")
        composeRule.onNodeWithTag(AddRefuelTags.LITERS).performTextInput("20")
        composeRule.onNodeWithTag(AddRefuelTags.SAVE).performClick()
        robot.waitTag(DashboardTags.ROOT)
        val partial = robot.dash(idA).recentRefuellings.first()
        assertTrue("checkbox must persist un-full tank", !partial.isTankFull)

        val idB = robot.createVehicle(carB, "20000", "55", "B$suffix")
        composeRule.onNodeWithText(carB, substring = true).assertIsDisplayed()
        assertTrue(
            composeRule.onAllNodesWithTag(DashboardTags.feedService(repairId))
                .fetchSemanticsNodes()
                .isEmpty(),
        )

        robot.switchVehicle(idA)
        composeRule.onNodeWithText(carA, substring = true).assertIsDisplayed()
        composeRule.onNodeWithTag(DashboardTags.feedService(repairId)).assertIsDisplayed()

        robot.deleteVehicleViaSettings(idB)
        robot.deleteVehicleViaSettings(idA)
        val leftover = runBlocking {
            AutologApp.instance.container.repository.observeVehicles().first()
                .any { it.id == idA || it.id == idB }
        }
        assertTrue("UITest switch cars must be gone", !leftover)
    }
}
