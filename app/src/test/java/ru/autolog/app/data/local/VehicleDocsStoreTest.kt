package ru.autolog.app.data.local

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class VehicleDocsStoreTest {
    @Test
    fun allowsPdfAndImages() {
        assertTrue(VehicleDocsStore.isAllowedMime("application/pdf"))
        assertTrue(VehicleDocsStore.isAllowedMime("image/jpeg"))
        assertTrue(VehicleDocsStore.isAllowedMime("image/png"))
        assertTrue(VehicleDocsStore.isAllowedMime("image/webp"))
        assertFalse(VehicleDocsStore.isAllowedMime("application/zip"))
        assertFalse(VehicleDocsStore.isAllowedMime("text/plain"))
        assertFalse(VehicleDocsStore.isAllowedMime(null))
    }
}
