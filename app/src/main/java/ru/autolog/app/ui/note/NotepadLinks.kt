package ru.autolog.app.ui.note

import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.LinkAnnotation
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextLinkStyles
import androidx.compose.ui.text.buildAnnotatedString

/** Detects http(s)/www URLs in notepad text for clickable spans. */
object NotepadLinks {
    private val URL_REGEX = Regex(
        """(?i)\b((?:https?://|www\.)[^\s<>\[\]"'()]+)""",
    )
    private val TRAILING_PUNCT = Regex("""[.,;:!?)\]}>]+$""")

    data class Span(val start: Int, val end: Int, val url: String)

    fun find(text: String): List<Span> {
        if (text.isEmpty()) return emptyList()
        return URL_REGEX.findAll(text).mapNotNull { match ->
            val raw = match.groupValues[1]
            val trimmed = raw.replace(TRAILING_PUNCT, "")
            if (trimmed.isEmpty()) return@mapNotNull null
            val start = match.range.first
            val end = start + trimmed.length
            Span(start = start, end = end, url = normalizeUrl(trimmed))
        }.toList()
    }

    fun normalizeUrl(raw: String): String {
        val t = raw.trim()
        return when {
            t.startsWith("http://", ignoreCase = true) ||
                t.startsWith("https://", ignoreCase = true) -> t
            t.startsWith("www.", ignoreCase = true) -> "https://$t"
            else -> t
        }
    }

    /** URL covering [offset], if any (for tap-to-open). */
    fun urlAt(text: String, offset: Int): String? {
        if (text.isEmpty() || offset < 0) return null
        val clamped = offset.coerceAtMost(text.length)
        return find(text).firstOrNull { span ->
            clamped in span.start until span.end || clamped == span.end
        }?.url
    }

    fun annotate(text: String, linkStyle: SpanStyle): AnnotatedString {
        val spans = find(text)
        if (spans.isEmpty()) return AnnotatedString(text)
        return buildAnnotatedString {
            append(text)
            spans.forEach { span ->
                addLink(
                    LinkAnnotation.Url(
                        url = span.url,
                        styles = TextLinkStyles(style = linkStyle),
                    ),
                    span.start,
                    span.end,
                )
            }
        }
    }
}
