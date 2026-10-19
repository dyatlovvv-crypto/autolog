package ru.autolog.app.data.backup

import java.io.File
import java.util.Base64
import java.util.UUID
import org.json.JSONArray
import org.json.JSONObject

/** Encode/decode binary attachments for JSON backup (Drive / file export). */
object BackupFileCodec {
    fun readBase64(path: String?): String? {
        if (path.isNullOrBlank()) return null
        val file = File(path)
        if (!file.isFile || file.length() <= 0L) return null
        return runCatching {
            Base64.getEncoder().encodeToString(file.readBytes())
        }.getOrNull()
    }

    fun writeBase64(dir: File, fileName: String, base64: String): File? {
        if (base64.isBlank()) return null
        return runCatching {
            dir.mkdirs()
            val safe = fileName.substringAfterLast('/').ifBlank { "${UUID.randomUUID()}.bin" }
            val out = File(dir, safe)
            out.writeBytes(Base64.getDecoder().decode(base64))
            out
        }.getOrNull()
    }

    fun fileEntry(path: String, extra: JSONObject.() -> Unit = {}): JSONObject? {
        val file = File(path)
        if (!file.isFile) return null
        val b64 = readBase64(path) ?: return null
        return JSONObject()
            .put("fileName", file.name)
            .put("contentBase64", b64)
            .also { extra(it) }
    }

    fun parseFileEntries(arr: JSONArray?): List<FileEntry> {
        if (arr == null) return emptyList()
        val out = ArrayList<FileEntry>(arr.length())
        for (i in 0 until arr.length()) {
            val o = arr.optJSONObject(i) ?: continue
            val name = o.optString("fileName").ifBlank { o.optString("name") }
            val b64 = o.optString("contentBase64")
            if (name.isBlank() || b64.isBlank()) continue
            out += FileEntry(
                fileName = name,
                contentBase64 = b64,
                title = o.optString("title").ifBlank { name },
                mimeType = o.optString("mimeType").ifBlank { "application/octet-stream" },
                createdAtEpochMs = o.optLong("createdAtEpochMs", 0L),
                sourcePath = o.optString("sourcePath").ifBlank { null },
            )
        }
        return out
    }

    data class FileEntry(
        val fileName: String,
        val contentBase64: String,
        val title: String,
        val mimeType: String,
        val createdAtEpochMs: Long,
        val sourcePath: String?,
    )
}
