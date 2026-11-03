package fr.videoconverter.engine

import android.content.Context
import android.net.Uri
import fr.videoconverter.data.Store
import fr.videoconverter.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File

@Serializable
data class CodecStatus(val id: String, val label: String, val ok: Boolean, val detail: String, val v: Int = 1)

/**
 * Teste pour de vrai chaque encodeur : encode une courte vidéo de test, relit le fichier avec ffprobe
 * et décode son contenu. Un codec n'est affiché « disponible » que si ce test réussit.
 */
object CodecCheck {
    private val json = Json { ignoreUnknownKeys = true }
    private val ser = ListSerializer(CodecStatus.serializer())

    /** Version du test : v2 ajoute le contrôle de l'image (PSNR). Un rapport plus ancien est ignoré et refait. */
    private const val VERSION = 2

    /** Similarité minimale (dB) pour accepter une image produite ; en dessous : noire, rayée ou corrompue. */
    const val MIN_PSNR = 15.0
    private const val MIN_PSNR_STRICT = 22.0

    fun cached(store: Store): List<CodecStatus>? =
        store.loadCodecReport()?.let { runCatching { json.decodeFromString(ser, it) }.getOrNull() }
            ?.takeIf { l -> l.isNotEmpty() && l.all { it.v >= VERSION } }

    fun isOk(list: List<CodecStatus>?, id: String) = list?.firstOrNull { it.id == id }?.ok == true

    suspend fun run(context: Context, store: Store): List<CodecStatus> = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "codec-check").apply { deleteRecursively(); mkdirs() }
        val src = File(dir, "source.mp4")
        val make = Ffmpeg.run(
            listOf(
                "-hide_banner", "-y", "-f", "lavfi", "-i", "testsrc2=size=322x242:rate=25", "-f", "lavfi", "-i", "sine=frequency=440:sample_rate=44100",
                "-t", "2", "-c:v", "mpeg4", "-q:v", "3", "-pix_fmt", "yuv420p", "-c:a", "aac", src.path,
            )
        )
        val out = mutableListOf<CodecStatus>()
        val info = if (make.success) Ffmpeg.probe(context, Uri.fromFile(src), "source.mp4", src.length()) else null
        if (info == null) {
            store.log("ERROR", "CodecCheck : impossible de créer la vidéo de test.\n${make.log}")
            return@withContext (allIds.map { CodecStatus(it.first, it.second, false, "Test impossible (moteur FFmpeg inutilisable).") }).also {
                store.saveCodecReport(json.encodeToString(ser, it))
            }
        }
        // Source type « téléphone en portrait » 1080×1920 pour les encodeurs matériels (alignement, rotation, 720p en sortie).
        val srcHd = File(dir, "source_hd.mp4")
        val makeHd = Ffmpeg.run(listOf("-hide_banner", "-y", "-f", "lavfi", "-i", "testsrc2=size=1080x1920:rate=25", "-t", "1", "-c:v", "mpeg4", "-q:v", "3", "-pix_fmt", "yuv420p", srcHd.path))
        val infoHd = if (makeHd.success) Ffmpeg.probe(context, Uri.fromFile(srcHd), "source_hd.mp4", srcHd.length()) else null
        for ((id, label) in allIds) {
            val hw = id.endsWith("_hw")
            val r = if (hw && infoHd != null) testOne(context, id, dir, srcHd, infoHd) else testOne(context, id, dir, src, info)
            out += CodecStatus(id, label, r.first, r.second, VERSION)
            store.log(if (r.first) "INFO" else "WARN", "CodecCheck $id : ${r.second}")
        }
        dir.deleteRecursively()
        store.saveCodecReport(json.encodeToString(ser, out))
        out
    }

    val allIds = listOf(
        "hevc_sw" to "H.265 logiciel (libx265)",
        "avc_sw" to "H.264 logiciel (libx264)",
        "hevc_hw" to "H.265 matériel (MediaCodec)",
        "avc_hw" to "H.264 matériel (MediaCodec)",
        "avi_xvid" to "AVI Xvid / MPEG-4 (libxvid)",
    )

    private suspend fun testOne(context: Context, id: String, dir: File, src: File, info: VideoInfo): Pair<Boolean, String> {
        val isAvi = id.startsWith("avi_")
        val out = File(dir, "test_$id." + if (isAvi) "avi" else "mp4")
        val inParam = src.path
        val built = when (id) {
            "hevc_sw" -> CommandBuilder.optimize(inParam, out.path, info, DefaultProfiles.all[0].copy(optCodec = OptCodec.HEVC, resolution = Resolution.ORIGINAL), false)
            "avc_sw" -> CommandBuilder.optimize(inParam, out.path, info, DefaultProfiles.all[0].copy(optCodec = OptCodec.AVC, resolution = Resolution.ORIGINAL), false)
            "hevc_hw" -> CommandBuilder.optimize(inParam, out.path, info, DefaultProfiles.all[0].copy(optCodec = OptCodec.HEVC, resolution = Resolution.ORIGINAL), true)
            "avc_hw" -> CommandBuilder.optimize(inParam, out.path, info, DefaultProfiles.all[0].copy(optCodec = OptCodec.AVC, resolution = Resolution.ORIGINAL), true)
            "avi_xvid" -> CommandBuilder.avi(inParam, out.path, info, DefaultProfiles.all[2].copy(aviCodec = AviCodec.XVID, aviFrame = 0))
            else -> return false to "Inconnu"
        }
        val r = Ffmpeg.run(built.args)
        if (!r.success || !out.isFile || out.length() == 0L) return false to ("Encodage refusé : " + lastLine(r.log))
        val back = Ffmpeg.probe(context, Uri.fromFile(out), out.name, out.length()) ?: return false to "Fichier produit illisible"
        val d = Ffmpeg.run(listOf("-hide_banner", "-v", "error", "-i", out.path, "-f", "null", "-"))
        if (!d.success || d.log.isNotBlank()) return false to ("Décodage en erreur : " + lastLine(d.log))
        val dimOk = back.displayWidth == built.plan.padWidth && back.displayHeight == built.plan.padHeight
        if (!dimOk) return false to "Dimensions inattendues ${back.width}×${back.height}"
        // Contrôle de l'image : le résultat doit ressembler à la source (détecte image noire / rayée / verte).
        val ps = Ffmpeg.psnr(out.path, src.path, built.plan.width, built.plan.height, 0.0, 1.0, dir)
            ?: return false to "Contrôle de l'image impossible"
        val min = MIN_PSNR_STRICT
        val psTxt = "%.1f dB".format(java.util.Locale.ROOT, ps)
        if (ps < min) return false to "Image corrompue ou noire au test (similarité $psTxt, minimum ${min.toInt()} dB)"
        return true to "OK (${back.videoCodec}, ${back.width}×${back.height}, ${out.length() / 1024} Ko, similarité $psTxt)"
    }

    private fun lastLine(log: String) = log.lines().map { it.trim() }.lastOrNull { it.isNotEmpty() } ?: "(aucun détail)"
}
