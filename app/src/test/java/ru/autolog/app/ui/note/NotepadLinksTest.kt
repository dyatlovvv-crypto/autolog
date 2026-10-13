package ru.autolog.app.ui.note

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NotepadLinksTest {

    @Test
    fun findsHttpsAndWww() {
        val text = "смотри https://example.com/path и www.autolog.app ок"
        val spans = NotepadLinks.find(text)
        assertEquals(2, spans.size)
        assertEquals("https://example.com/path", spans[0].url)
        assertEquals("https://www.autolog.app", spans[1].url)
    }

    @Test
    fun stripsTrailingPunctuation() {
        val text = "ссылка https://ya.ru."
        val spans = NotepadLinks.find(text)
        assertEquals(1, spans.size)
        assertEquals("https://ya.ru", spans[0].url)
        assertEquals(text.indexOf("https"), spans[0].start)
        assertEquals(text.indexOf("https") + "https://ya.ru".length, spans[0].end)
    }

    @Test
    fun emptyHasNoSpans() {
        assertTrue(NotepadLinks.find("").isEmpty())
        assertTrue(NotepadLinks.find("просто текст без ссылок").isEmpty())
    }

    @Test
    fun urlAt_returnsLinkUnderOffset() {
        val text = "см https://ya.ru тут"
        val start = text.indexOf("https")
        assertEquals("https://ya.ru", NotepadLinks.urlAt(text, start + 3))
        assertEquals(null, NotepadLinks.urlAt(text, 0))
    }
}
