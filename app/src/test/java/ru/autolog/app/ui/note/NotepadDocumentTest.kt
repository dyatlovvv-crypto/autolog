package ru.autolog.app.ui.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NotepadDocumentTest {

    @Test
    fun legacyPlainText_becomesTextBlock() {
        val doc = NotepadDocument.decode("мысли\nhttps://ya.ru")
        assertEquals(1, doc.blocks.size)
        val text = doc.blocks.first() as NotepadBlock.Text
        assertEquals("мысли\nhttps://ya.ru", text.body)
    }

    @Test
    fun roundTrip_checkAndImage() {
        val original = NotepadDocument(
            listOf(
                NotepadBlock.Text(body = "привет"),
                NotepadBlock.Check(label = "Раз", done = true),
                NotepadBlock.Check(label = "Два", done = false),
                NotepadBlock.Image(path = "/tmp/a.jpg"),
            ),
        )
        val decoded = NotepadDocument.decode(original.encode())
        assertEquals(4, decoded.blocks.size)
        assertEquals("привет", (decoded.blocks[0] as NotepadBlock.Text).body)
        val c0 = decoded.blocks[1] as NotepadBlock.Check
        assertEquals("Раз", c0.label)
        assertTrue(c0.done)
        val c1 = decoded.blocks[2] as NotepadBlock.Check
        assertEquals("Два", c1.label)
        assertFalse(c1.done)
        assertEquals("/tmp/a.jpg", (decoded.blocks[3] as NotepadBlock.Image).path)
    }

    @Test
    fun empty_decodesToOneTextBlock() {
        val doc = NotepadDocument.decode("")
        assertEquals(1, doc.blocks.size)
        assertTrue(doc.blocks.first() is NotepadBlock.Text)
    }
}
