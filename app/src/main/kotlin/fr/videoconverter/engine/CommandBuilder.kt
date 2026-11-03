package fr.videoconverter.engine

import fr.videoconverter.model.*
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/** Dimensions de sortie calculées à partir de la source et du profil. */
data class OutputPlan(
    val width: Int,
    val height: Int,
    val padWidth: Int,
    val padHeight: Int,
    val notes: List<String>,
    /** Position de l'image dans le cadre (bandes noires centrées si un cadre paysage est demandé). */
    val padX: Int = 0,
    val padY: Int = 0,
)

object SizePlanner {
    private fun roundUp(v: Int, m: Int) = if (m <= 1) v else ((v + m - 1) / m) * m
    private fun even(v: Int) = max(2, v / 2 * 2)

    /** Optimisation : redimensionne sur le petit côté, jamais d'agrandissement, dimensions paires. */
    fun optimize(src: VideoInfo, p: Profile): OutputPlan {
        val w0 = src.displayWidth
        val h0 = src.displayHeight
        val target = when (p.resolution) {
            Resolution.ORIGINAL -> 0
            Resolution.CUSTOM -> max(120, p.customShortSide)
            else -> p.resolution.shortSide
        }
        val shortSide = min(w0, h0)
        if (target <= 0 || target >= shortSide) return OutputPlan(even(w0), even(h0), even(w0), even(h0), emptyList())
        val scale = target.toDouble() / shortSide
        val w = even((w0 * scale).roundToInt())
        val h = even((h0 * scale).roundToInt())
        return OutputPlan(w, h, w, h, emptyList())
    }

    /** Multiple imposé par le codec AVI (largeur et hauteur). */
    fun aviMultiple(c: AviCodec) = when (c) {
        AviCodec.RGB -> 1
        AviCodec.YUY2, AviCodec.XVID -> 2
        AviCodec.MSVIDEO1, AviCodec.CINEPAK -> 4
    }

    fun avi(src: VideoInfo, p: Profile): OutputPlan {
        val m = aviMultiple(p.aviCodec)
        val w0 = src.displayWidth
        val h0 = src.displayHeight
        val notes = mutableListOf<String>()
        var w = w0
        var h = h0
        if (p.aviResolution != Resolution.ORIGINAL) {
            val rw = if (p.aviWidth > 0) max(16, p.aviWidth) else w0
            val rh = if (p.aviHeight > 0) max(16, p.aviHeight) else h0
            if (p.keepAspect) {
                val s = min(rw.toDouble() / w0, rh.toDouble() / h0)
                w = max(1, (w0 * s).roundToInt())
                h = max(1, (h0 * s).roundToInt())
            } else {
                w = rw; h = rh
                if (abs(rw.toDouble() / rh - w0.toDouble() / h0) > 0.01) notes += "Les proportions de l'image seront modifiées."
            }
        }
        var pw = roundUp(w, m)
        var ph = roundUp(h, m)
        // Cadre paysage : bandes noires gauche/droite (image non modifiée, même échelle en pixels).
        val ratio = when (p.aviFrame) { 1 -> 16.0 / 9; 2 -> 4.0 / 3; 3 -> 1.0; else -> 0.0 }
        if (ratio > 0 && abs(w.toDouble() / h - ratio) > 0.005) {
            if (w.toDouble() / h < ratio) { pw = roundUp(kotlin.math.ceil(h * ratio).toInt(), max(m, 2)); ph = roundUp(h, m) }
            else { ph = roundUp(kotlin.math.ceil(w / ratio).toInt(), max(m, 2)); pw = roundUp(w, m) }
            val x = (pw - w) / 2 / 2 * 2
            val y = (ph - h) / 2 / 2 * 2
            notes += "Cadre ${when (p.aviFrame) { 1 -> "16:9"; 2 -> "4:3"; else -> "carré 1:1" }} : ${pw}×$ph avec bandes noires autour de l'image (l'image n'est ni étirée ni redimensionnée)."
            return OutputPlan(w, h, pw, ph, notes, x, y)
        }
        if (pw != w || ph != h) notes += "Le codec ${p.aviCodec.label} impose des dimensions multiples de $m : bandes noires ajoutées (${pw - w} px à droite, ${ph - h} px en bas), sans rééchantillonner l'image."
        return OutputPlan(w, h, pw, ph, notes)
    }
}

/** Construit les arguments FFmpeg. Fonctions pures, testées sur PC. */
object CommandBuilder {
    data class Built(val args: List<String>, val plan: OutputPlan, val encoder: String, val warnings: List<String>)

    /** Débit cible (b/s) pour l'encodeur matériel, calculé en bits par pixel et par image. */
    fun hwBitrate(src: VideoInfo, plan: OutputPlan, p: Profile): Long {
        val bpp = when (p.quality) {
            Quality.HIGH -> 0.10
            Quality.BALANCED -> 0.065
            Quality.COMPACT -> 0.045
            Quality.CUSTOM -> 0.065 * 2.0.pow((26 - p.customCrf) / 6.0)
        } * (if (p.optCodec == OptCodec.AVC) 1.5 else 1.0)
        val fps = if (src.fps > 0) src.fps else 30.0
        var br = (plan.width.toLong() * plan.height * fps * bpp).toLong()
        if (src.bitrate > 0) br = min(br, (src.bitrate * 0.9).toLong())
        return max(br, 300_000L)
    }

    fun crf(p: Profile): Int = when (p.quality) {
        Quality.CUSTOM -> p.customCrf
        Quality.HIGH -> if (p.optCodec == OptCodec.HEVC) 23 else 20
        Quality.BALANCED -> if (p.optCodec == OptCodec.HEVC) 27 else 24
        Quality.COMPACT -> if (p.optCodec == OptCodec.HEVC) 30 else 28
    }

    fun optimize(
        input: String, output: String, src: VideoInfo, p: Profile, hardware: Boolean,
        startSec: Double? = null, durationSec: Double? = null,
    ): Built {
        val plan = SizePlanner.optimize(src, p)
        val warn = mutableListOf<String>()
        val a = mutableListOf("-hide_banner", "-y", "-nostdin")
        if (startSec != null) a += listOf("-ss", fmt(startSec))
        a += listOf("-i", input)
        if (durationSec != null) a += listOf("-t", fmt(durationSec))
        a += listOf("-map", "0:v:0")
        val audio = p.keepAudio && src.audioCodec != null
        if (audio) a += listOf("-map", "0:a?")
        a += listOf("-map_metadata", "0", "-map_chapters", "-1")
        val tenBit = src.bitDepth > 8 && p.optCodec == OptCodec.HEVC && !hardware
        val pix = if (hardware) "nv12" else if (tenBit) "yuv420p10le" else "yuv420p"
        val vf = buildList {
            if (plan.width != src.displayWidth || plan.height != src.displayHeight) add("scale=${plan.width}:${plan.height}:flags=bicubic")
            add("format=$pix")
        }.joinToString(",")
        a += listOf("-vf", vf)
        val encoder: String
        if (hardware) {
            encoder = if (p.optCodec == OptCodec.HEVC) "hevc_mediacodec" else "h264_mediacodec"
            a += listOf("-c:v", encoder, "-b:v", hwBitrate(src, plan, p).toString())
        } else if (p.optCodec == OptCodec.HEVC) {
            encoder = "libx265"
            a += listOf("-c:v", encoder, "-crf", crf(p).toString(), "-preset", "veryfast", "-x265-params", "log-level=error")
        } else {
            encoder = "libx264"
            a += listOf("-c:v", encoder, "-crf", crf(p).toString(), "-preset", "veryfast")
        }
        if (p.optCodec == OptCodec.HEVC) a += listOf("-tag:v", "hvc1")
        if (p.optCodec == OptCodec.AVC && src.bitDepth > 8) warn += "Source 10 bits : conversion en 8 bits (HDR non converti, les couleurs peuvent paraître ternes)."
        if (hardware && src.bitDepth > 8) warn += "Source 10 bits encodée en 8 bits par l'encodeur matériel."
        if (audio) {
            if (src.audioCodec == "aac") a += listOf("-c:a", "copy") else a += listOf("-c:a", "aac", "-b:a", "128k")
        } else a += "-an"
        a += listOf("-movflags", "+faststart", "-f", "mp4", output)
        return Built(a, plan, encoder, warn)
    }

    fun avi(
        input: String, output: String, src: VideoInfo, p: Profile,
        startSec: Double? = null, durationSec: Double? = null,
    ): Built {
        val plan = SizePlanner.avi(src, p)
        val warn = plan.notes.toMutableList()
        val a = mutableListOf("-hide_banner", "-y", "-nostdin")
        if (startSec != null) a += listOf("-ss", fmt(startSec))
        a += listOf("-i", input)
        if (durationSec != null) a += listOf("-t", fmt(durationSec))
        a += listOf("-map", "0:v:0")
        val audio = p.aviKeepAudio && src.audioCodec != null
        if (audio) a += listOf("-map", "0:a:0")
        a += listOf("-map_metadata", "0", "-map_chapters", "-1")
        val pix = when (p.aviCodec) {
            AviCodec.RGB -> if (p.rgb32) "bgr0" else "bgr24"
            AviCodec.YUY2 -> "yuyv422"
            AviCodec.MSVIDEO1 -> "rgb555le"
            AviCodec.CINEPAK, AviCodec.XVID -> "yuv420p"
        }
        val vf = buildList {
            if (plan.width != src.displayWidth || plan.height != src.displayHeight) add("scale=${plan.width}:${plan.height}:flags=bicubic")
            if (plan.padWidth != plan.width || plan.padHeight != plan.height) add("pad=${plan.padWidth}:${plan.padHeight}:${plan.padX}:${plan.padY}:black")
            // Pixels carrés : un ratio d'aspect non 1:1 écrit dans le flux étirerait l'image dans AviMéca / AviStep.
            add("setsar=1")
            add("format=$pix")
        }.joinToString(",")
        a += listOf("-vf", vf)
        // AVI = cadence constante. On impose la fréquence d'origine ; aucune interpolation.
        if (p.aviFps > 0 && src.fps > 0 && abs(p.aviFps - src.fps) > 0.001)
            warn += "Fréquence d'images modifiée (${fmt(src.fps)} → ${fmt(p.aviFps)} i/s) : des images seront dupliquées ou supprimées. Les mesures de vitesse et d'accélération peuvent être faussées."
        val rate = if (p.aviFps > 0) fmt(p.aviFps) else src.fpsText.ifBlank { if (src.fps > 0) fmt(src.fps) else "" }
        if (rate.isNotEmpty()) a += listOf("-r", rate, "-fps_mode", "cfr")
        val encoder: String
        when (p.aviCodec) {
            AviCodec.RGB -> { encoder = "rawvideo"; a += listOf("-c:v", "rawvideo") }
            AviCodec.YUY2 -> { encoder = "rawvideo"; a += listOf("-c:v", "rawvideo", "-tag:v", "YUY2") }
            AviCodec.MSVIDEO1 -> { encoder = "msvideo1"; a += listOf("-c:v", "msvideo1", "-q:v", p.aviQuality.toString()) }
            AviCodec.CINEPAK -> { encoder = "cinepak"; a += listOf("-c:v", "cinepak", "-q:v", p.aviQuality.toString()) }
            AviCodec.XVID -> { encoder = "libxvid"; a += listOf("-c:v", "libxvid", "-q:v", p.aviQuality.toString(), "-vtag", "XVID") }
        }
        if (audio) a += listOf("-c:a", "pcm_s16le") else a += "-an"
        a += listOf("-f", "avi", output)
        return Built(a, plan, encoder, warn)
    }

    fun fmt(d: Double): String =
        if (d == d.toLong().toDouble()) d.toLong().toString() else "%.3f".format(java.util.Locale.ROOT, d).trimEnd('0').trimEnd('.')

    /** Estimation grossière (majorant) de la taille de sortie, pour la vérification d'espace disponible. */
    fun estimateBytes(src: VideoInfo, p: Profile, plan: OutputPlan, durationSec: Double): Long {
        val fps = if (src.fps > 0) src.fps else 30.0
        return when (p.mode) {
            Mode.OPTIMIZE -> (hwBitrate(src, plan, p) / 8.0 * durationSec * 1.15 + 5_000_000).toLong()
            Mode.AVI -> {
                val px = plan.padWidth.toLong() * plan.padHeight
                val audio = (48_000L * 2 * 2 * durationSec).toLong()
                val video = when (p.aviCodec) {
                    AviCodec.RGB -> px * (if (p.rgb32) 4 else 3) * fps * durationSec
                    AviCodec.YUY2 -> px * 2 * fps * durationSec
                    else -> px * 0.5 * fps * durationSec
                }
                video.toLong() + audio + 1_000_000
            }
        }
    }
}
