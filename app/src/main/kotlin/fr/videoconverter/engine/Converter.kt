package fr.videoconverter.engine

import android.content.Context
import android.net.Uri
import com.arthenica.ffmpegkit.FFprobeKit
import fr.videoconverter.data.OutputFiles
import fr.videoconverter.data.PendingOutput
import fr.videoconverter.data.Store
import fr.videoconverter.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.math.abs
import kotlin.math.max

sealed class Outcome {
    data class Success(val uri: Uri, val name: String, val size: Long, val warnings: List<String>, val engine: String, val verified: Boolean) : Outcome()
    data class Failure(val message: String) : Outcome()
    data object Cancelled : Outcome()
}

/** Une erreur « métier » : le message est destiné à l'utilisateur (en français). */
class ConversionException(message: String) : Exception(message)

class Converter(private val context: Context, private val store: Store) {

    private fun baseName(name: String) = name.substringBeforeLast('.', name).replace(Regex("[\\\\/:*?\"<>|]"), "_")

    fun outputName(info: VideoInfo, p: Profile): String {
        val base = baseName(info.name)
        return when (p.mode) {
            Mode.OPTIMIZE -> {
                val plan = SizePlanner.optimize(info, p)
                "${base}_${minOf(plan.width, plan.height)}p_${if (p.optCodec == OptCodec.HEVC) "hevc" else "avc"}.mp4"
            }
            Mode.AVI -> "${base}_${p.aviCodec.name.lowercase()}.avi"
        }
    }

    /** Convertit un élément de la file. Ne touche jamais au fichier source. */
    suspend fun convert(item: QueueItem, settings: AppSettings, onProgress: (Float) -> Unit): Outcome = withContext(Dispatchers.IO) {
        // Seul Xvid est proposé pour l'AVI (anciens éléments de file RGB / YUY2… convertis).
        val p = item.profile.let { if (it.mode == Mode.AVI) it.copy(aviCodec = AviCodec.XVID) else it }
        val srcUri = Uri.parse(item.sourceUri)
        var pending: PendingOutput? = null
        var tmpFile: File? = null
        try {
            // --- Vérifications préalables ---
            if (!OutputFiles.canOpenForRead(context, srcUri)) throw ConversionException("Fichier source inaccessible (supprimé ou autorisation perdue).")
            val info = Ffmpeg.probe(context, srcUri, item.name, item.sizeBytes)
                ?: throw ConversionException("Format non pris en charge : le fichier ne peut pas être lu comme une vidéo.")
            if (info.durationMs <= 0 || info.width <= 0) throw ConversionException("Format non pris en charge : durée ou dimensions illisibles.")
            val durationSec = info.durationMs / 1000.0
            var hardware = p.mode == Mode.OPTIMIZE && p.engine == EncoderEngine.AUTO && hwAvailable(p.optCodec, info)
            val plan = if (p.mode == Mode.AVI) SizePlanner.avi(info, p) else SizePlanner.optimize(info, p)
            checkCodec(p)
            val need = CommandBuilder.estimateBytes(info, p, plan, durationSec)
            val free = OutputFiles.freeSpace()
            // Fichier temporaire + copie finale : il faut de la place pour les deux le temps de la copie.
            if (free < need * 2 + 200_000_000L) {
                throw ConversionException("Espace de stockage insuffisant : environ ${mb(need * 2 + 200_000_000L)} nécessaires (fichier temporaire + copie), ${mb(free)} disponibles.")
            }
            val rel = OutputFiles.relativePath(settings, p.mode)
            val name = outputName(info, p)
            var engineUsed = ""
            var built: CommandBuilder.Built? = null
            var attempt = 0
            val workDir = File(context.cacheDir, "work").apply { mkdirs() }
            val tmp = File(workDir, item.id + if (p.mode == Mode.AVI) ".avi" else ".mp4")
            tmpFile = tmp
            var ver: Verification? = null
            var hwFellBack = false
            while (true) {
                attempt++
                tmp.delete()
                val outParam = tmp.path
                val inParam = Ffmpeg.readParam(context, srcUri)
                built = if (p.mode == Mode.AVI) CommandBuilder.avi(inParam, outParam, info, p) else CommandBuilder.optimize(inParam, outParam, info, p, hardware)
                engineUsed = built.encoder
                store.log("INFO", "Début « ${item.name} » → $name [${p.name}] encodeur=${built.encoder}\nffmpeg ${built.args.joinToString(" ")}")
                val total = info.durationMs.toFloat()
                val r = Ffmpeg.run(built.args) { t -> onProgress((t / total).coerceIn(0f, 0.95f)) }
                if (r.cancelled) { tmp.delete(); return@withContext Outcome.Cancelled }
                var failure: String? = null
                if (!r.success) {
                    store.log("ERROR", "FFmpeg code ${r.returnCode} pour ${item.name} (encodeur ${built.encoder})\n${r.log.takeLast(4000)}")
                    failure = "Échec de la conversion (code FFmpeg ${r.returnCode}). Détails dans le journal de diagnostic."
                } else {
                    // Vérification du fichier temporaire (structure + contenu de l'image) avant toute publication.
                    ver = verify(Uri.fromFile(tmp), info, p, built.plan, settings.deepVerify, durationSec)
                    if (ver.error != null) {
                        store.log("ERROR", "Vérification échouée pour ${item.name} (encodeur ${built.encoder}) : ${ver.error}")
                        failure = "Échec de vérification : ${ver.error}"
                    }
                }
                if (failure == null) break
                if (hardware && attempt == 1) {
                    store.log("WARN", "Encodeur matériel en échec ou résultat invalide : nouvel essai en logiciel.")
                    hardware = false
                    hwFellBack = true
                    onProgress(0f)
                    continue
                }
                throw ConversionException(failure)
            }
            val warnings = built!!.warnings.toMutableList()
            if (hwFellBack) warnings += "L'encodeur matériel a produit un résultat invalide : le fichier a été refait en encodage logiciel."
            warnings += ver!!.warnings
            // --- Copie vers Films/VideoConverter ---
            onProgress(0.97f)
            val out = OutputFiles.create(context, rel, name, OutputFiles.mime(p.mode))
                ?: throw ConversionException("Impossible de créer le fichier de sortie dans $rel.")
            pending = out
            try {
                context.contentResolver.openOutputStream(out.uri, "w")!!.use { os -> tmp.inputStream().use { it.copyTo(os, 1 shl 20) } }
            } catch (e: Exception) {
                store.log("ERROR", "Copie vers ${out.displayName} échouée : ${e.stackTraceToString()}")
                throw ConversionException("Échec de l'écriture du fichier de sortie (espace insuffisant ou dossier inaccessible).")
            }
            val size = OutputFiles.size(context, out.uri)
            if (size != tmp.length()) throw ConversionException("Échec de vérification : taille copiée ($size) différente de la taille produite (${tmp.length()}).")
            OutputFiles.publish(context, out)
            pending = null
            tmp.delete()
            store.log("INFO", "Terminé « ${item.name} » → ${out.displayName} (${mb(size)}), avertissements : ${warnings.size}")
            onProgress(1f)
            Outcome.Success(out.uri, out.displayName, size, warnings, engineUsed, true)
        } catch (e: ConversionException) {
            pending?.let { OutputFiles.discard(context, it.uri) }
            store.log("ERROR", "Échec « ${item.name} » : ${e.message}")
            Outcome.Failure(e.message ?: "Erreur inconnue.")
        } catch (e: kotlinx.coroutines.CancellationException) {
            pending?.let { OutputFiles.discard(context, it.uri) }
            throw e
        } catch (e: Exception) {
            pending?.let { OutputFiles.discard(context, it.uri) }
            store.log("ERROR", "Exception « ${item.name} » : ${e.stackTraceToString()}")
            Outcome.Failure("Conversion interrompue : ${e.message ?: e.javaClass.simpleName}")
        } finally {
            tmpFile?.delete()
        }
    }

    companion object {
        /** Nom lisible de l'encodeur réellement utilisé. */
        fun engineLabel(enc: String?): String = when (enc) {
            "libx265" -> "logiciel (x265)"
            "libx264" -> "logiciel (x264)"
            "hevc_mediacodec" -> "matériel (H.265)"
            "h264_mediacodec" -> "matériel (H.264)"
            null, "" -> "?"
            else -> enc
        }
    }

    private fun mb(b: Long) = "%.0f Mo".format(java.util.Locale.FRANCE, b / 1e6)

    private fun hwAvailable(codec: OptCodec, info: VideoInfo): Boolean {
        if (info.bitDepth > 8 && codec == OptCodec.HEVC) return false   // préserver le 10 bits : logiciel
        val rep = CodecCheck.cached(store)
        return CodecCheck.isOk(rep, if (codec == OptCodec.HEVC) "hevc_hw" else "avc_hw")
    }

    /** Refuse de lancer un profil dont l'encodeur n'a pas passé le test réel. */
    private fun checkCodec(p: Profile) {
        val rep = CodecCheck.cached(store) ?: return
        val id = when (p.mode) {
            Mode.OPTIMIZE -> if (p.engine == EncoderEngine.SOFTWARE || !CodecCheck.isOk(rep, if (p.optCodec == OptCodec.HEVC) "hevc_hw" else "avc_hw"))
                if (p.optCodec == OptCodec.HEVC) "hevc_sw" else "avc_sw" else return
            Mode.AVI -> when (p.aviCodec) {
                AviCodec.RGB -> "avi_rgb"; AviCodec.YUY2 -> "avi_yuy2"; AviCodec.MSVIDEO1 -> "avi_msvideo1"
                AviCodec.CINEPAK -> "avi_cinepak"; AviCodec.XVID -> "avi_xvid"
            }
        }
        val st = rep.firstOrNull { it.id == id }
        if (st != null && !st.ok) throw ConversionException("Codec indisponible : ${st.label} (${st.detail}).")
    }

    class Verification(val error: String?, val warnings: List<String>)

    suspend fun verify(
        out: Uri, src: VideoInfo, p: Profile, plan: OutputPlan, deep: Boolean, expectedSec: Double,
        partial: Boolean = false, refStartSec: Double = 0.0,
    ): Verification {
        val warnings = mutableListOf<String>()
        val size = OutputFiles.size(context, out)
        if (size <= 0) return Verification("le fichier de sortie est vide ou absent.", warnings)
        val path = Ffmpeg.readParam(context, out)
        val session = FFprobeKit.getMediaInformation(path)
        val mi = session.mediaInformation ?: return Verification("le fichier de sortie n'est pas lisible (conteneur invalide).", warnings)
        val format = mi.format ?: ""
        val streams = mi.streams ?: emptyList()
        val v = streams.firstOrNull { it.type == "video" } ?: return Verification("aucune piste vidéo dans le fichier de sortie.", warnings)
        val a = streams.firstOrNull { it.type == "audio" }
        val wantAudio = (if (p.mode == Mode.AVI) p.aviKeepAudio else p.keepAudio) && src.audioCodec != null
        if (wantAudio && a == null) return Verification("la piste audio attendue est absente.", warnings)
        if (!wantAudio && a != null) warnings += "Une piste audio est présente alors qu'elle devait être supprimée."
        if (p.mode == Mode.AVI && !format.contains("avi")) return Verification("le conteneur produit n'est pas de l'AVI ($format).", warnings)
        if (p.mode == Mode.OPTIMIZE && !format.contains("mp4")) return Verification("le conteneur produit n'est pas du MP4 ($format).", warnings)
        val wantCodec = when (p.mode) {
            Mode.OPTIMIZE -> if (p.optCodec == OptCodec.HEVC) "hevc" else "h264"
            Mode.AVI -> when (p.aviCodec) {
                AviCodec.RGB, AviCodec.YUY2 -> "rawvideo"; AviCodec.MSVIDEO1 -> "msvideo1"; AviCodec.CINEPAK -> "cinepak"; AviCodec.XVID -> "mpeg4"
            }
        }
        if (v.codec != wantCodec) return Verification("codec vidéo inattendu (${v.codec} au lieu de $wantCodec).", warnings)
        val w = v.width?.toInt() ?: 0
        val h = v.height?.toInt() ?: 0
        val expW = if (p.mode == Mode.AVI) plan.padWidth else plan.width
        val expH = if (p.mode == Mode.AVI) plan.padHeight else plan.height
        if (w != expW || h != expH) return Verification("dimensions inattendues (${w}×$h au lieu de ${expW}×$expH).", warnings)
        val dur = mi.duration?.toDoubleOrNull() ?: 0.0
        if (abs(dur - expectedSec) > max(1.0, expectedSec * 0.02)) return Verification("durée inattendue (%.1f s au lieu de %.1f s).".format(java.util.Locale.FRANCE, dur, expectedSec), warnings)
        if (p.mode == Mode.AVI) {
            val rate = Ffmpeg.parseRate(v.averageFrameRate ?: "")
            val expRate = if (p.aviFps > 0) p.aviFps else src.fps
            if (expRate > 0 && abs(rate - expRate) > 0.01) return Verification("cadence inattendue (%.3f i/s au lieu de %.3f).".format(java.util.Locale.FRANCE, rate, expRate), warnings)
            val nb = v.allProperties?.optString("nb_frames", "0")?.toLongOrNull() ?: 0
            if (!partial && src.frames > 0 && nb > 0 && abs(nb - src.frames) > 1 && p.aviFps <= 0)
                warnings += "Nombre d'images : $nb en sortie pour ${src.frames} en entrée (source à cadence variable : des images ont été dupliquées ou supprimées pour obtenir une cadence constante)."
        }
        // Contrôle de décodage : début et fin du fichier (ou tout le fichier si court).
        val decodeArgs = mutableListOf<List<String>>()
        if (deep) {
            decodeArgs += listOf("-v", "error", "-t", "6", "-i", path, "-f", "null", "-")
            if (expectedSec > 15) decodeArgs += listOf("-v", "error", "-sseof", "-6", "-i", path, "-f", "null", "-")
        } else decodeArgs += listOf("-v", "error", "-t", "2", "-i", path, "-f", "null", "-")
        for (args in decodeArgs) {
            val r = Ffmpeg.run(listOf("-hide_banner", "-nostdin") + args)
            if (!r.success) return Verification("le contrôle de décodage a échoué (${r.log.lines().lastOrNull { it.isNotBlank() } ?: "erreur inconnue"}).", warnings)
            if (r.log.isNotBlank()) warnings += "Avertissements au décodage : ${r.log.trim().lines().first()}"
        }
        // Contrôle du contenu : comparaison image par image (PSNR) avec l'original sur les premières secondes.
        // Détecte une image noire, rayée ou corrompue qu'un simple décodage ne signale pas.
        val work = File(context.cacheDir, "work").apply { mkdirs() }
        val ps = Ffmpeg.psnr(path, Ffmpeg.readParam(context, Uri.parse(src.uri)), plan.width, plan.height, refStartSec, minOf(3.0, expectedSec), work, plan.padX, plan.padY)
        store.log("INFO", "Similarité image (PSNR) sortie/original : ${ps?.let { "%.1f dB".format(java.util.Locale.ROOT, it) } ?: "non mesurée"}")
        if (ps == null) warnings += "Le contrôle visuel automatique (comparaison avec l'original) n'a pas pu être effectué."
        else if (ps < CodecCheck.MIN_PSNR) return Verification("l'image produite ne ressemble pas à l'original (noire, rayée ou corrompue ; similarité %.1f dB).".format(java.util.Locale.FRANCE, ps), warnings)
        return Verification(null, warnings)
    }

    // ---------- Test sur un extrait ----------
    data class SampleResult(
        val file: File, val infoOut: VideoInfo?, val warnings: List<String>, val error: String?,
        val originalFrame: File?, val resultFrame: File?, val estimatedFullBytes: Long, val details: List<String>,
    )

    suspend fun testSample(
        src: VideoInfo, p: Profile, startSec: Double, lengthSec: Double, settings: AppSettings,
        onProgress: (Float) -> Unit = {},
    ): SampleResult = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, "sample").apply { deleteRecursively(); mkdirs() }
        val out = File(dir, "extrait." + if (p.mode == Mode.AVI) "avi" else "mp4")
        val srcUri = Uri.parse(src.uri)
        val inParam = Ffmpeg.readParam(context, srcUri)
        val hw = p.mode == Mode.OPTIMIZE && p.engine == EncoderEngine.AUTO && hwAvailable(p.optCodec, src)
        try {
            checkCodec(p)
        } catch (e: ConversionException) { return@withContext SampleResult(out, null, emptyList(), e.message, null, null, 0, emptyList()) }
        val len = minOf(lengthSec, src.durationMs / 1000.0 - startSec)
        val outUri = Uri.fromFile(out)
        var useHw = hw
        var built: CommandBuilder.Built
        var r: RunResult
        var verOrNull: Verification?
        onProgress(0f)
        while (true) {
            built = if (p.mode == Mode.AVI) CommandBuilder.avi(inParam, out.path, src, p, startSec, len) else CommandBuilder.optimize(inParam, out.path, src, p, useHw, startSec, len)
            store.log("INFO", "Test extrait ${src.name} [${p.name}] ffmpeg ${built.args.joinToString(" ")}")
            r = Ffmpeg.run(built.args) { t -> onProgress((t / (len * 1000)).toFloat().coerceIn(0f, 0.9f)) }
            if (r.success) onProgress(0.92f)
            verOrNull = if (r.success) verify(outUri, src.copy(frames = 0), p, built.plan, false, len, partial = true, refStartSec = startSec) else null
            if ((!r.success || verOrNull?.error != null) && useHw) {
                store.log("WARN", "Test extrait : encodeur matériel en échec ou résultat invalide, nouvel essai en logiciel.")
                useHw = false
                onProgress(0f)
                continue
            }
            break
        }
        if (!r.success) {
            store.log("ERROR", "Test extrait échoué\n${r.log.takeLast(3000)}")
            return@withContext SampleResult(out, null, built.warnings, "Échec de la conversion de test : " + (r.log.lines().lastOrNull { it.isNotBlank() } ?: "erreur inconnue"), null, null, 0, emptyList())
        }
        val ver = verOrNull!!
        onProgress(0.96f)
        val back = Ffmpeg.probe(context, outUri, out.name, out.length())
        val tFrame = len / 2
        val f1 = File(dir, "orig.png"); val f2 = File(dir, "result.png")
        val h = 360
        Ffmpeg.run(listOf("-hide_banner", "-y", "-ss", CommandBuilder.fmt(startSec + tFrame), "-i", inParam, "-frames:v", "1", "-vf", "scale=-2:$h", f1.path))
        Ffmpeg.run(listOf("-hide_banner", "-y", "-ss", CommandBuilder.fmt(tFrame), "-i", out.path, "-frames:v", "1", "-vf", "scale=-2:$h", f2.path))
        val est = if (len > 0) (out.length() / len * (src.durationMs / 1000.0)).toLong() else 0
        val details = buildList {
            back?.let {
                add("Conteneur : ${if (p.mode == Mode.AVI) "AVI" else "MP4"}")
                add("Vidéo : ${it.videoCodec}, ${it.width}×${it.height}, ${it.fpsText.ifBlank { "?" }} i/s, ${it.pixFmt}")
                add("Audio : ${it.audioCodec ?: "aucun"}")
            }
            add("Encodeur utilisé : ${engineLabel(built.encoder)}")
            add("Extrait de %.0f s : %s".format(java.util.Locale.FRANCE, len, mb(out.length())))
        }
        onProgress(1f)
        SampleResult(out, back, built.warnings + ver.warnings, ver.error?.let { "Échec de vérification : $it" }, f1.takeIf { it.isFile }, f2.takeIf { it.isFile }, est, details)
    }
}
