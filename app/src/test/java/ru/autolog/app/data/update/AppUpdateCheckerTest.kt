package ru.autolog.app.data.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AppUpdateCheckerTest {
    @Test
    fun isNewer_comparesSemver() {
        assertTrue(AppUpdateChecker.isNewer("0.7.0", "0.6.9"))
        assertTrue(AppUpdateChecker.isNewer("v1.0.0", "0.9.9"))
        assertFalse(AppUpdateChecker.isNewer("0.6.9", "0.7.0"))
        assertFalse(AppUpdateChecker.isNewer("0.7.0", "0.7.0"))
        assertFalse(AppUpdateChecker.isNewer("bad", "0.7.0"))
    }

    @Test
    fun sanitizeReleaseNotes_trimsBlankLines() {
        val notes = AppUpdateChecker.sanitizeReleaseNotes(
            "\n1. Появилась возможность добавлять напоминания о других деталях ТО\n\n",
        )
        assertEquals(
            "1. Появилась возможность добавлять напоминания о других деталях ТО",
            notes,
        )
        assertEquals("", AppUpdateChecker.sanitizeReleaseNotes("   "))
        assertEquals("", AppUpdateChecker.sanitizeReleaseNotes(null))
    }
}
