package fr.videoconverter.model

import kotlinx.serialization.Serializable

@Serializable enum class Mode(val label: String) { OPTIMIZE("Optimisation MP4"), AVI("Conversion AVI") }
@Serializable enum class Quality(val label: String) { HIGH("Élevée"), BALANCED("Équilibrée"), COMPACT("Compacte"), CUSTOM("Personnalisée") }
@Serializable enum class OptCodec(val label: String) { HEVC("H.265 / HEVC"), AVC("H.264 / AVC") }
@Serializable enum class EncoderEngine(val label: String) {
    AUTO("Matériel si disponible (rapide)"), SOFTWARE("Logiciel (meilleure compression, lent)")
}
@Serializable enum class Resolution(val label: String, val shortSide: Int) {
    ORIGINAL("Originale", 0), P1080("1080p", 1080), P720("720p", 720), P480("480p", 480), CUSTOM("Personnalisée", 0)
}
@Serializable enum class AviCodec(val label: String, val detail: String) {
    RGB("RGB non compressé", "Sans perte, fichiers très volumineux"),
    YUY2("YUY2 (YUV 4:2:2 non compressé)", "Sans compression, 2 octets par pixel"),
    MSVIDEO1("Microsoft Video 1", "Codec historique, blocs 4×4, qualité limitée"),
    CINEPAK("Cinepak", "Codec historique, fichiers compacts"),
    XVID("Xvid / MPEG-4 Part 2", "Compact, plus récent")
}

/** Un profil de conversion complet (réglages d'un des deux modes). */
@Serializable
data class Profile(
    val id: String,
    val name: String,
    val mode: Mode,
    // --- Optimisation MP4 ---
    val optCodec: OptCodec = OptCodec.HEVC,
    val engine: EncoderEngine = EncoderEngine.AUTO,
    val resolution: Resolution = Resolution.P720,
    val customShortSide: Int = 720,
    val quality: Quality = Quality.BALANCED,
    val customCrf: Int = 26,
    val keepAudio: Boolean = true,
    // --- AVI ---
    val aviCodec: AviCodec = AviCodec.RGB,
    val aviResolution: Resolution = Resolution.ORIGINAL,
    val aviWidth: Int = 0,
    val aviHeight: Int = 0,
    val keepAspect: Boolean = true,
    /** 0 = fréquence d'images d'origine (conseillé pour AviMéca / AviStep). */
    val aviFps: Double = 0.0,
    val rgb32: Boolean = false,
    /** Codecs avec perte : 1 (meilleur) à 31. */
    val aviQuality: Int = 3,
    /** Par défaut sans son : inutile pour l'analyse dans AviMéca / AviStep. */
    val aviKeepAudio: Boolean = false,
    /** Cadre : 0 = dimensions de l'image, 1 = paysage 16:9, 2 = paysage 4:3 (bandes noires si l'image est plus étroite). */
    val aviFrame: Int = 0,
) {
    val summary: String
        get() = when (mode) {
            Mode.OPTIMIZE -> "${optCodec.label} · ${if (resolution == Resolution.CUSTOM) "${customShortSide}p" else resolution.label} · ${quality.label}"
            Mode.AVI -> "${aviCodec.label} · " + (if (aviResolution == Resolution.ORIGINAL) "dimensions d'origine" else "${aviWidth}×${aviHeight}") +
                " · " + (if (aviFps <= 0) "cadence d'origine" else "$aviFps i/s")
        }
}

object DefaultProfiles {
    val all = listOf(
        Profile("opt-720", "Vidéo personnelle 720p", Mode.OPTIMIZE, resolution = Resolution.P720),
        Profile("opt-1080", "Vidéo personnelle 1080p", Mode.OPTIMIZE, resolution = Resolution.P1080, quality = Quality.HIGH),
        // Seul Xvid / MPEG-4 est proposé ; cadre paysage 16:9 (AviMéca étire l'image dans une fenêtre large).
        Profile("avi-avimeca", "AVI AviMéca", Mode.AVI, aviCodec = AviCodec.XVID, aviFrame = 1),
        Profile("avi-avistep", "AVI AviStep", Mode.AVI, aviCodec = AviCodec.XVID, aviFrame = 1),
    )
}

/** Informations lues par ffprobe (dimensions codées ; utiliser display* pour la rotation appliquée). */
@Serializable
data class VideoInfo(
    val uri: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val rotation: Int = 0,
    val fps: Double = 0.0,
    val fpsText: String = "",
    val videoCodec: String = "?",
    val audioCodec: String? = null,
    val pixFmt: String = "",
    val bitrate: Long = 0,
    val frames: Long = 0,
) {
    val displayWidth get() = if (rotation == 90 || rotation == 270) height else width
    val displayHeight get() = if (rotation == 90 || rotation == 270) width else height
    val bitDepth get() = if (pixFmt.contains("10")) 10 else if (pixFmt.contains("12")) 12 else 8
}

@Serializable enum class ItemState { PENDING, RUNNING, DONE, FAILED, CANCELED }

@Serializable
data class QueueItem(
    val id: String,
    val batchId: String,
    val sourceUri: String,
    val name: String,
    val sizeBytes: Long,
    val durationMs: Long,
    val profile: Profile,
    val state: ItemState = ItemState.PENDING,
    val progress: Float = 0f,
    val outputUri: String? = null,
    val outputName: String? = null,
    val outputSize: Long = 0,
    val error: String? = null,
    val warnings: List<String> = emptyList(),
    val addedAt: Long = 0,
    val startedAt: Long = 0,
    val finishedAt: Long = 0,
    val engineUsed: String? = null,
    val verified: Boolean = false,
)

@Serializable
data class AppSettings(
    val destFolder: String = "VideoConverter",
    val deepVerify: Boolean = true,
    val pauseOnThermal: Boolean = true,
)
