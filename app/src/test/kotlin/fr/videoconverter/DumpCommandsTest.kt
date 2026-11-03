package fr.videoconverter

import fr.videoconverter.engine.CommandBuilder
import fr.videoconverter.model.*
import org.junit.Test
import java.io.File

/** Écrit les lignes de commande réellement générées, pour les rejouer avec un FFmpeg de bureau (validation de syntaxe). */
class DumpCommandsTest {
    @Test fun dump() {
        val outFile = System.getenv("VC_DUMP") ?: return
        val src = VideoInfo("u", "a.mp4", 1_000_000, 4000, 642, 478, 0, 25.0, "25/1", "mpeg4", "aac", "yuv420p", 2_000_000, 100)
        val lines = mutableListOf<String>()
        fun add(tag: String, b: CommandBuilder.Built) = lines.add("$tag\t" + b.args.joinToString("\u001f"))
        for (c in AviCodec.entries) add("avi_${c.name}", CommandBuilder.avi("IN", "OUT", src, DefaultProfiles.all[2].copy(aviCodec = c)))
        add("avi_RGB_custom", CommandBuilder.avi("IN", "OUT", src, DefaultProfiles.all[2].copy(aviResolution = Resolution.CUSTOM, aviWidth = 320, aviHeight = 240)))
        add("avi_MSV_custom", CommandBuilder.avi("IN", "OUT", src, DefaultProfiles.all[2].copy(aviCodec = AviCodec.MSVIDEO1, aviResolution = Resolution.CUSTOM, aviWidth = 321, aviHeight = 241)))
        add("avi_RGB_fps", CommandBuilder.avi("IN", "OUT", src, DefaultProfiles.all[2].copy(aviFps = 10.0)))
        add("mp4_hevc_sw", CommandBuilder.optimize("IN", "OUT", src, DefaultProfiles.all[0].copy(resolution = Resolution.P480), false))
        add("mp4_avc_sw", CommandBuilder.optimize("IN", "OUT", src, DefaultProfiles.all[0].copy(optCodec = OptCodec.AVC, resolution = Resolution.P480), false))
        add("mp4_noaudio", CommandBuilder.optimize("IN", "OUT", src, DefaultProfiles.all[0].copy(keepAudio = false, resolution = Resolution.P480), false))
        File(outFile).writeText(lines.joinToString("\n"))
    }
}
