package fr.videoconverter.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.provider.OpenableColumns
import android.util.Size
import android.graphics.Bitmap

/** Une vidéo du téléphone telle que connue de MediaStore (avant sonde ffprobe). */
data class VideoItem(
    val key: Long,
    val uri: Uri,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val folder: String,
    /** Date d'ajout (secondes depuis 1970) ; 0 si inconnue (fichier choisi via le sélecteur système). */
    val dateAdded: Long = 0,
)

object MediaRepo {
    fun list(context: Context): List<VideoItem> {
        val out = mutableListOf<VideoItem>()
        val proj = arrayOf(
            MediaStore.Video.Media._ID, MediaStore.Video.Media.DISPLAY_NAME, MediaStore.Video.Media.SIZE,
            MediaStore.Video.Media.DURATION, MediaStore.Video.Media.WIDTH, MediaStore.Video.Media.HEIGHT, MediaStore.Video.Media.RELATIVE_PATH,
            MediaStore.Video.Media.DATE_ADDED,
        )
        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI, proj,
            "${MediaStore.Video.Media.IS_PENDING}=0", null, "${MediaStore.Video.Media.DATE_ADDED} DESC",
        )?.use { c ->
            val iId = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val iName = c.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val iSize = c.getColumnIndexOrThrow(MediaStore.Video.Media.SIZE)
            val iDur = c.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val iW = c.getColumnIndexOrThrow(MediaStore.Video.Media.WIDTH)
            val iH = c.getColumnIndexOrThrow(MediaStore.Video.Media.HEIGHT)
            val iRel = c.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            val iDate = c.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            while (c.moveToNext()) {
                val id = c.getLong(iId)
                out += VideoItem(
                    key = id,
                    uri = ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id),
                    name = c.getString(iName) ?: "video_$id",
                    sizeBytes = c.getLong(iSize), durationMs = c.getLong(iDur),
                    width = c.getInt(iW), height = c.getInt(iH),
                    folder = (c.getString(iRel) ?: "").trimEnd('/').ifBlank { "(racine)" },
                    dateAdded = c.getLong(iDate),
                )
            }
        }
        return out
    }

    /** Vidéo choisie via le sélecteur de fichiers du système (accès SAF). */
    fun fromDocument(context: Context, uri: Uri, index: Int): VideoItem? {
        var name = "video"
        var size = 0L
        context.contentResolver.query(uri, null, null, null, null)?.use { c ->
            if (c.moveToFirst()) {
                c.getColumnIndex(OpenableColumns.DISPLAY_NAME).takeIf { it >= 0 }?.let { name = c.getString(it) ?: name }
                c.getColumnIndex(OpenableColumns.SIZE).takeIf { it >= 0 }?.let { size = c.getLong(it) }
            }
        }
        return VideoItem(-1L - index, uri, name, size, 0, 0, 0, "Sélecteur de fichiers")
    }

    fun thumbnail(context: Context, uri: Uri): Bitmap? = runCatching {
        if (Build.VERSION.SDK_INT >= 29) context.contentResolver.loadThumbnail(uri, Size(240, 240), null) else null
    }.getOrNull()
}
