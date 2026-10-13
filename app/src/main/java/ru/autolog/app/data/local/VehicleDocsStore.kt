package ru.autolog.app.data.local

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.util.Locale
import java.util.UUID

/** Copy vehicle documents into app-private storage. */
object VehicleDocsStore {
    private val ALLOWED_PREFIXES = listOf("image/", "application/pdf")

    fun dir(context: Context, vehicleId: Long): File =
        File(context.filesDir, "vehicle_docs/$vehicleId").also { it.mkdirs() }

    fun isAllowedMime(mime: String?): Boolean {
        val m = mime?.lowercase(Locale.US) ?: return false
        return ALLOWED_PREFIXES.any { m.startsWith(it) } || m == "application/pdf"
    }

    data class CopiedFile(
        val absolutePath: String,
        val displayName: String,
        val mimeType: String,
    )

    fun copyFromUri(context: Context, vehicleId: Long, uri: Uri): CopiedFile? {
        return try {
            val resolver = context.contentResolver
            val mime = resolver.getType(uri)?.lowercase(Locale.US) ?: guessMime(uri)
            if (!isAllowedMime(mime)) return null
            val displayName = queryDisplayName(context, uri) ?: defaultName(mime)
            val ext = extensionFor(mime, displayName)
            val out = File(dir(context, vehicleId), "${UUID.randomUUID()}$ext")
            resolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            CopiedFile(
                absolutePath = out.absolutePath,
                displayName = displayName,
                mimeType = mime ?: "application/octet-stream",
            )
        } catch (_: Exception) {
            null
        }
    }

    fun deleteFile(path: String) {
        runCatching { File(path).takeIf { it.exists() }?.delete() }
    }

    fun deleteVehicleDir(context: Context, vehicleId: Long) {
        runCatching {
            dir(context, vehicleId).deleteRecursively()
        }
    }

    private fun queryDisplayName(context: Context, uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            val idx = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            if (idx < 0 || !c.moveToFirst()) return@use null
            c.getString(idx)?.trim()?.ifBlank { null }
        }
    }

    private fun guessMime(uri: Uri): String? {
        val name = uri.lastPathSegment?.lowercase(Locale.US) ?: return null
        return when {
            name.endsWith(".pdf") -> "application/pdf"
            name.endsWith(".jpg") || name.endsWith(".jpeg") -> "image/jpeg"
            name.endsWith(".png") -> "image/png"
            name.endsWith(".webp") -> "image/webp"
            name.endsWith(".heic") || name.endsWith(".heif") -> "image/heic"
            else -> null
        }
    }

    private fun defaultName(mime: String?): String =
        if (mime == "application/pdf") "document.pdf" else "photo.jpg"

    private fun extensionFor(mime: String?, displayName: String): String {
        val fromName = displayName.substringAfterLast('.', missingDelimiterValue = "")
            .lowercase(Locale.US)
        if (fromName in setOf("pdf", "jpg", "jpeg", "png", "webp", "heic", "heif")) {
            return ".$fromName"
        }
        return when (mime?.lowercase(Locale.US)) {
            "application/pdf" -> ".pdf"
            "image/png" -> ".png"
            "image/webp" -> ".webp"
            "image/heic", "image/heif" -> ".heic"
            else -> ".jpg"
        }
    }
}
