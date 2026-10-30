package ru.autolog.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ServiceAttachmentsTest {
    @Test
    fun classify_empty() {
        val flags = classifyAttachments(emptyList())
        assertFalse(flags.hasImage)
        assertFalse(flags.hasFile)
    }

    @Test
    fun classify_images_only() {
        val flags = classifyAttachments(
            listOf("/data/a.jpg", "/data/b.PNG", "/data/c.webp"),
        )
        assertTrue(flags.hasImage)
        assertFalse(flags.hasFile)
    }

    @Test
    fun classify_files_only() {
        val flags = classifyAttachments(
            listOf("/data/a.pdf", "/data/b.docx", "/data/c.bin"),
        )
        assertFalse(flags.hasImage)
        assertTrue(flags.hasFile)
    }

    @Test
    fun classify_both() {
        val flags = classifyAttachments(
            listOf("/data/a.jpg", "/data/invoice.pdf"),
        )
        assertTrue(flags.hasImage)
        assertTrue(flags.hasFile)
    }

    @Test
    fun isImageAttachmentPath_by_extension() {
        assertTrue(isImageAttachmentPath("/x/y/photo.JPEG"))
        assertTrue(isImageAttachmentPath("shot.heic"))
        assertFalse(isImageAttachmentPath("/x/y/doc.pdf"))
        assertFalse(isImageAttachmentPath("/x/y/noext"))
    }

    @Test
    fun photosCodec_roundtrip() {
        val raw = ServicePhotosCodec.encode(listOf("/a.jpg", "/b.pdf"))
        assertEquals(listOf("/a.jpg", "/b.pdf"), ServicePhotosCodec.decode(raw))
    }
}
