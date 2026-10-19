package ru.autolog.app.data.backup

import java.io.File
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.junit.Rule

class BackupFileCodecTest {
    @get:Rule
    val tmp = TemporaryFolder()

    @Test
    fun roundTrip_preservesBytes() {
        val src = tmp.newFile("doc.pdf")
        val payload = byteArrayOf(1, 2, 3, 9, 0, 127)
        src.writeBytes(payload)
        val b64 = BackupFileCodec.readBase64(src.absolutePath)
        assertNotNull(b64)
        val outDir = tmp.newFolder("out")
        val restored = BackupFileCodec.writeBase64(outDir, "doc.pdf", b64!!)
        assertNotNull(restored)
        assertArrayEquals(payload, restored!!.readBytes())
    }

    @Test
    fun parseFileEntries_readsDocumentsShape() {
        val arr = JSONArray().put(
            JSONObject()
                .put("title", "ОСАГО")
                .put("mimeType", "application/pdf")
                .put("fileName", "osago.pdf")
                .put("contentBase64", "AQID")
                .put("createdAtEpochMs", 123L),
        )
        val entries = BackupFileCodec.parseFileEntries(arr)
        assertEquals(1, entries.size)
        assertEquals("ОСАГО", entries[0].title)
        assertEquals("application/pdf", entries[0].mimeType)
        assertEquals("osago.pdf", entries[0].fileName)
        assertTrue(entries[0].contentBase64.isNotBlank())
    }
}
