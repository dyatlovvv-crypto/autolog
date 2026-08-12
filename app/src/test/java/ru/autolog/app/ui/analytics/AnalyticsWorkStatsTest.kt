package ru.autolog.app.ui.analytics

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ru.autolog.app.data.local.entity.ServiceEntity
import ru.autolog.app.domain.ServiceKind

class AnalyticsWorkStatsTest {

    @Test
    fun buildWorkStats_aggregatesChecklistByNormalizedLabel() {
        val services = listOf(
            service(checklist = """[{"id":"1","text":"Масло","done":true},{"id":"2","text":"Фильтр","done":false}]"""),
            service(checklist = """[{"id":"3","text":"масло","done":true}]"""),
            service(checklist = """[{"id":"4","text":"Свечи","done":true}]"""),
        )
        val stats = AnalyticsViewModel.buildWorkStats(services)
        assertEquals(3, stats.size)
        assertEquals("Масло", stats[0].label)
        assertEquals(2, stats[0].count)
        assertEquals(1f, stats[0].relative, 0.001f)
        assertTrue(stats.any { it.label.equals("Фильтр", ignoreCase = true) && it.count == 1 })
        assertTrue(stats.any { it.label.equals("Свечи", ignoreCase = true) && it.count == 1 })
    }

    @Test
    fun buildServiceKind_attachesWorkStats() {
        val services = listOf(
            service(
                type = ServiceKind.TO.name,
                checklist = """[{"id":"1","text":"Антифриз","done":true}]""",
            ),
            service(type = ServiceKind.REPAIR.name, checklist = """[{"id":"2","text":"Стойка","done":true}]"""),
        )
        val to = AnalyticsViewModel.buildServiceKind(services, ServiceKind.TO, AnalyticsPeriod.ALL)
        assertEquals(1, to.workStats.size)
        assertEquals("Антифриз", to.workStats.first().label)
        val repair = AnalyticsViewModel.buildServiceKind(services, ServiceKind.REPAIR, AnalyticsPeriod.ALL)
        assertEquals("Стойка", repair.workStats.first().label)
    }

    private fun service(
        type: String = ServiceKind.TO.name,
        checklist: String?,
    ) = ServiceEntity(
        id = 0,
        vehicleId = 1,
        dateEpochMs = 1_700_000_000_000L,
        odometerKm = 100_000,
        totalCost = 1000.0,
        serviceType = type,
        description = null,
        checklistJson = checklist,
        createdAtEpochMs = 1_700_000_000_000L,
    )
}
