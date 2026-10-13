package ru.autolog.app.data.update

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
}
