package ru.autolog.app.domain

import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ServiceChecklistItem(
    val id: String = UUID.randomUUID().toString(),
    val text: String,
    val done: Boolean = false,
)

object ServiceChecklistCodec {
    fun encode(items: List<ServiceChecklistItem>): String {
        val arr = JSONArray()
        items.forEach { item ->
            arr.put(
                JSONObject()
                    .put("id", item.id)
                    .put("text", item.text)
                    .put("done", item.done),
            )
        }
        return arr.toString()
    }

    fun decode(raw: String?): List<ServiceChecklistItem> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.optJSONObject(i) ?: continue
                    val text = o.optString("text").trim()
                    if (text.isEmpty()) continue
                    add(
                        ServiceChecklistItem(
                            id = o.optString("id").ifBlank { UUID.randomUUID().toString() },
                            text = text,
                            done = o.optBoolean("done", false),
                        ),
                    )
                }
            }
        }.getOrDefault(emptyList())
    }
}

object ServicePhotosCodec {
    fun encode(paths: List<String>): String {
        val arr = JSONArray()
        paths.forEach { arr.put(it) }
        return arr.toString()
    }

    fun decode(raw: String?): List<String> {
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val p = arr.optString(i).trim()
                    if (p.isNotEmpty()) add(p)
                }
            }
        }.getOrDefault(emptyList())
    }
}
