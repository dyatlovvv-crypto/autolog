package ru.autolog.app.ui.note

import android.content.ClipboardManager
import android.content.Context
import android.net.Uri
import java.io.File
import java.io.FileOutputStream
import java.util.UUID

/** Persist notepad images under app files (per vehicle). */
object NotepadPhotos {
    fun dir(context: Context, vehicleId: Long): File =
        File(context.filesDir, "notepad_photos/$vehicleId").also { it.mkdirs() }

    fun copyUri(context: Context, vehicleId: Long, uri: Uri): String? {
        return try {
            val out = File(dir(context, vehicleId), "${UUID.randomUUID()}.jpg")
            context.contentResolver.openInputStream(uri)?.use { input ->
                out.outputStream().use { output -> input.copyTo(output) }
            } ?: return null
            out.absolutePath
        } catch (_: Exception) {
            null
        }
    }

    /** Best-effort image URI from clipboard. */
    fun fromClipboard(context: Context, vehicleId: Long): String? {
        val clipboard = context.getSystemService(ClipboardManager::class.java) ?: return null
        val clip = clipboard.primaryClip ?: return null
        for (i in 0 until clip.itemCount) {
            val uri = clip.getItemAt(i).uri ?: continue
            val path = copyUri(context, vehicleId, uri)
            if (path != null) return path
        }
        return null
    }
}
