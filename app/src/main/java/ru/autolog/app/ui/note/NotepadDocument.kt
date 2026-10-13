package ru.autolog.app.ui.note

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

sealed class NotepadBlock {
    abstract val id: String

    data class Text(
        override val id: String = UUID.randomUUID().toString(),
        val body: String = "",
    ) : NotepadBlock()

    data class Check(
        override val id: String = UUID.randomUUID().toString(),
        val label: String = "",
        val done: Boolean = false,
    ) : NotepadBlock()

    data class Image(
        override val id: String = UUID.randomUUID().toString(),
        val path: String,
    ) : NotepadBlock()
}

data class NotepadDocument(
    val blocks: List<NotepadBlock> = listOf(NotepadBlock.Text()),
) {
    fun encode(): String {
        val arr = JSONArray()
        blocks.forEach { block ->
            arr.put(
                when (block) {
                    is NotepadBlock.Text -> JSONObject()
                        .put("t", "text")
                        .put("id", block.id)
                        .put("body", block.body)
                    is NotepadBlock.Check -> JSONObject()
                        .put("t", "check")
                        .put("id", block.id)
                        .put("label", block.label)
                        .put("done", block.done)
                    is NotepadBlock.Image -> JSONObject()
                        .put("t", "image")
                        .put("id", block.id)
                        .put("path", block.path)
                },
            )
        }
        return JSONObject()
            .put("v", VERSION)
            .put("blocks", arr)
            .toString()
    }

    companion object {
        const val VERSION = 1

        fun empty(): NotepadDocument = NotepadDocument(listOf(NotepadBlock.Text()))

        /** Plain legacy notes become one text block; JSON docs decode as-is. */
        fun decode(raw: String?): NotepadDocument {
            if (raw.isNullOrBlank()) return empty()
            val trimmed = raw.trim()
            if (!trimmed.startsWith("{")) {
                return NotepadDocument(listOf(NotepadBlock.Text(body = raw)))
            }
            return runCatching {
                val root = JSONObject(trimmed)
                if (!root.has("blocks")) {
                    return NotepadDocument(listOf(NotepadBlock.Text(body = raw)))
                }
                val arr = root.getJSONArray("blocks")
                val blocks = buildList {
                    for (i in 0 until arr.length()) {
                        val o = arr.optJSONObject(i) ?: continue
                        val id = o.optString("id").ifBlank { UUID.randomUUID().toString() }
                        when (o.optString("t")) {
                            "check" -> add(
                                NotepadBlock.Check(
                                    id = id,
                                    label = o.optString("label"),
                                    done = o.optBoolean("done", false),
                                ),
                            )
                            "image" -> {
                                val path = o.optString("path")
                                if (path.isNotBlank()) {
                                    add(NotepadBlock.Image(id = id, path = path))
                                }
                            }
                            else -> add(
                                NotepadBlock.Text(
                                    id = id,
                                    body = o.optString("body"),
                                ),
                            )
                        }
                    }
                }
                if (blocks.isEmpty()) empty() else NotepadDocument(blocks)
            }.getOrElse {
                NotepadDocument(listOf(NotepadBlock.Text(body = raw)))
            }
        }
    }
}
