package fr.videoconverter.engine

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFmpegKit
import com.arthenica.ffmpegkit.FFmpegKitConfig
import com.arthenica.ffmpegkit.FFprobeKit
import com.arthenica.ffmpegkit.Level
import com.arthenica.ffmpegkit.ReturnCode
import fr.videoconverter.model.VideoInfo
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import org.json.JSONObject
import kotlin.coroutines.resume

data class RunResult(val success: Boolean, val cancelled: Boolean, val log: String, val returnCode: Int)

/** Enveloppe fine autour de FFmpegKit : exécution asynchrone, annulation, sonde. */
object Ffmpeg {
    @Volatile private var currentSession: Long = 0

    fun init() {
        FFmpegKitConfig.setLogLevel(Level.AV_LOG_WARNING)
    }

    fun readParam(context: Context, uri: Uri): String =
        if (uri.scheme == "file") uri.path!! else FFmpegKitConfig.getSafParameterForRead(context, uri)

    fun writeParam(context: Context, uri: Uri): String =
        if (uri.scheme == "file") uri.path!! else FFmpegKitConfig.getSafParameterForWrite(context, uri)

    /** Exécute FFmpeg ; [onTimeMs] reçoit la position de sortie traitée en millisecondes. */
    suspend fun run(args: List<String>, onTimeMs: ((Long) -> Unit)? = null): RunResult =
        suspendCancellableCoroutine { cont ->
            val session = FFmpegKit.executeWithArgumentsAsync(
                args.toTypedArray(),
                { s ->
                    if (currentSession == s.sessionId) currentSession = 0
                    val rc = s.returnCode
                    if (cont.isActive) cont.resume(RunResult(ReturnCode.isSuccess(rc), ReturnCode.isCancel(rc), s.allLogsAsString ?: "", rc?.value ?: -1))
                },
                null,
                { st -> onTimeMs?.invoke(st.time.toLong()) },
            )
            currentSession = session.sessionId
            cont.invokeOnCancellation { FFmpegKit.cancel(session.sessionId) }
        }

    /**
     * Similarité (PSNR moyen, en dB) entre la sortie et l'original sur [seconds] secondes ; null si non mesurable.
     * Une image noire, rayée ou corrompue donne une valeur très basse (< 15 dB).
     */
    suspend fun psnr(outPath: String, refPath: String, w: Int, h: Int, refStartSec: Double, seconds: Double, workDir: java.io.File, x: Int = 0, y: Int = 0): Double? {
        val stats = java.io.File(workDir, "psnr_${System.nanoTime()}.log")
        try {
            val a = mutableListOf("-hide_banner", "-v", "error", "-t", CommandBuilder.fmt(seconds), "-i", outPath)
            if (refStartSec > 0) a += listOf("-ss", CommandBuilder.fmt(refStartSec))
            a += listOf(
                "-t", CommandBuilder.fmt(seconds), "-i", refPath, "-an", "-lavfi",
                "[0:v]crop=$w:$h:$x:$y,format=yuv420p[a];[1:v]scale=$w:$h:flags=bicubic,format=yuv420p[b];[a][b]psnr=stats_file=${stats.path}",
                "-f", "null", "-",
            )
            val r = run(a)
            if (!r.success || !stats.isFile) return null
            val v = Regex("psnr_avg:(\\S+)").findAll(stats.readText()).map { m -> m.groupValues[1].toDoubleOrNull() ?: 100.0 }.map { d -> if (d.isInfinite()) 100.0 else d }.toList()
            return if (v.isEmpty()) null else v.average()
        } finally {
            stats.delete()
        }
    }

    fun cancelCurrent() {
        val id = currentSession
        if (id != 0L) FFmpegKit.cancel(id) else FFmpegKit.cancel()
    }

    /** Lit les informations d'un média ; null si illisible. */
    suspend fun probe(context: Context, uri: Uri, name: String, sizeBytes: Long): VideoInfo? = withContext(Dispatchers.IO) {
        val path = try { readParam(context, uri) } catch (e: Exception) { return@withContext null }
        val session = FFprobeKit.getMediaInformation(path)
        val mi = session.mediaInformation ?: return@withContext null
        val streams = mi.streams ?: return@withContext null
        val v = streams.firstOrNull { it.type == "video" } ?: return@withContext null
        val a = streams.firstOrNull { it.type == "audio" }
        val props: JSONObject = v.allProperties ?: JSONObject()
        val rot = rotationOf(props)
        val avg = v.averageFrameRate ?: ""
        val real = v.realFrameRate ?: ""
        val rateText = listOf(avg, real).firstOrNull { parseRate(it) > 0 } ?: ""
        VideoInfo(
            uri = uri.toString(),
            name = name,
            sizeBytes = if (sizeBytes > 0) sizeBytes else (mi.size?.toLongOrNull() ?: 0),
            durationMs = ((mi.duration?.toDoubleOrNull() ?: 0.0) * 1000).toLong(),
            width = v.width?.toInt() ?: 0,
            height = v.height?.toInt() ?: 0,
            rotation = rot,
            fps = parseRate(rateText),
            fpsText = rateText,
            videoCodec = v.codec ?: "?",
            audioCodec = a?.codec,
            pixFmt = v.format ?: "",
            bitrate = mi.bitrate?.toLongOrNull() ?: 0,
            frames = props.optString("nb_frames", "0").toLongOrNull() ?: 0,
        )
    }

    fun parseRate(s: String): Double {
        if (s.isBlank()) return 0.0
        val p = s.split("/")
        return try {
            if (p.size == 2) { val d = p[1].toDouble(); if (d == 0.0) 0.0 else p[0].toDouble() / d } else s.toDouble()
        } catch (e: NumberFormatException) { 0.0 }
    }

    private fun rotationOf(props: JSONObject): Int {
        val side = props.optJSONArray("side_data_list")
        if (side != null) for (i in 0 until side.length()) {
            val o = side.optJSONObject(i) ?: continue
            if (o.has("rotation")) return normalize(o.optInt("rotation"))
        }
        props.optJSONObject("tags")?.optString("rotate")?.toIntOrNull()?.let { return normalize(it) }
        return 0
    }

    // ffprobe donne une rotation négative (ex. -90 = 270°)
    private fun normalize(r: Int) = ((r % 360) + 360) % 360
}
