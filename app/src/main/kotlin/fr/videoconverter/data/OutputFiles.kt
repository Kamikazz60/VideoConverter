package fr.videoconverter.data

import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.os.Environment
import android.os.StatFs
import android.provider.MediaStore
import fr.videoconverter.model.Mode

/** Un fichier de sortie « en attente » dans MediaStore (Movies/VideoConverter/...). */
class PendingOutput(val uri: Uri, val displayName: String)

object OutputFiles {
    fun relativePath(settings: fr.videoconverter.model.AppSettings, mode: Mode): String =
        "${Environment.DIRECTORY_MOVIES}/${settings.destFolder.trim('/').ifBlank { "VideoConverter" }}/${if (mode == Mode.AVI) "AVI" else "Optimized"}"

    fun mime(mode: Mode) = if (mode == Mode.AVI) "video/x-msvideo" else "video/mp4"

    fun create(context: Context, relPath: String, displayName: String, mime: String): PendingOutput? {
        val v = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Video.Media.MIME_TYPE, mime)
            put(MediaStore.Video.Media.RELATIVE_PATH, relPath)
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val uri = context.contentResolver.insert(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, v) ?: return null
        return PendingOutput(uri, queryName(context, uri) ?: displayName)
    }

    fun publish(context: Context, out: PendingOutput) {
        val v = ContentValues().apply { put(MediaStore.Video.Media.IS_PENDING, 0) }
        context.contentResolver.update(out.uri, v, null, null)
    }

    fun discard(context: Context, uri: Uri) {
        runCatching { context.contentResolver.delete(uri, null, null) }
    }

    fun size(context: Context, uri: Uri): Long =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { it.statSize } ?: 0L }.getOrDefault(0L)

    fun queryName(context: Context, uri: Uri): String? =
        runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns.DISPLAY_NAME), null, null, null)?.use { if (it.moveToFirst()) it.getString(0) else null }
        }.getOrNull()

    /** Espace libre (octets) du stockage partagé. */
    fun freeSpace(): Long = runCatching { StatFs(Environment.getExternalStorageDirectory().path).availableBytes }.getOrDefault(Long.MAX_VALUE)

    fun canOpenForRead(context: Context, uri: Uri): Boolean =
        runCatching { context.contentResolver.openFileDescriptor(uri, "r")?.use { true } ?: false }.getOrDefault(false)

    fun mediaId(uri: Uri): Long? = runCatching { ContentUris.parseId(uri) }.getOrNull()
}
