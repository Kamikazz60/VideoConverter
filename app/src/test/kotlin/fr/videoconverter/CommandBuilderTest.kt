package fr.videoconverter

import fr.videoconverter.engine.CommandBuilder
import fr.videoconverter.engine.SizePlanner
import fr.videoconverter.model.*
import org.junit.Assert.*
import org.junit.Test

class CommandBuilderTest {
    private val src1080 = VideoInfo("u", "a.mp4", 2_400_000_000, 960_000, 1920, 1080, 0, 30.0, "30/1", "hevc", "aac", "yuv420p", 20_000_000)
    private val srcPortrait = src1080.copy(width = 1920, height = 1080, rotation = 90)
    private val srcOdd = src1080.copy(width = 642, height = 478, fps = 25.0, fpsText = "25/1", audioCodec = "pcm_s16le")

    private val aviBase = DefaultProfiles.all[2].copy(aviFrame = 0)
    private fun opt(vararg mod: (Profile) -> Profile) = mod.fold(DefaultProfiles.all[0]) { p, f -> f(p) }

    @Test fun `avi xvid portrait en cadre paysage 16 9 avec bandes centrees`() {
        val p = DefaultProfiles.all[2]   // Xvid, cadre 16:9
        assertEquals(AviCodec.XVID, p.aviCodec)
        val plan = SizePlanner.avi(srcPortrait.copy(rotation = 90, width = 1920, height = 1080), p)   // 1080×1920 affiché
        assertEquals(1080, plan.width); assertEquals(1920, plan.height)
        assertEquals(3414, plan.padWidth); assertEquals(1920, plan.padHeight)
        assertEquals(1166, plan.padX); assertEquals(0, plan.padY)
        val s = CommandBuilder.avi("in", "out", srcPortrait, p).args.joinToString(" ")
        assertTrue(s.contains("pad=3414:1920:1166:0:black")); assertTrue(s.contains("-c:v libxvid"))
    }

    @Test fun `les fichiers produits portent la signature VideoConverter`() {
        val mp4 = CommandBuilder.optimize("in", "out", src1080, DefaultProfiles.all[0], hardware = false).args
        val avi = CommandBuilder.avi("in", "out", srcOdd, aviBase).args
        for (args in listOf(mp4, avi)) {
            val i = args.indexOf("-metadata")
            assertTrue(i >= 0); assertEquals("comment=${CommandBuilder.SIGNATURE}", args[i + 1])
        }
    }

    @Test fun `1080p vers 720p conserve le rapport`() {
        val plan = SizePlanner.optimize(src1080, DefaultProfiles.all[0])
        assertEquals(1280, plan.width); assertEquals(720, plan.height)
    }

    @Test fun `portrait redimensionne le petit cote`() {
        val plan = SizePlanner.optimize(srcPortrait, DefaultProfiles.all[0])
        assertEquals(720, plan.width); assertEquals(1280, plan.height)
    }

    @Test fun `pas d agrandissement`() {
        val small = src1080.copy(width = 640, height = 360)
        val plan = SizePlanner.optimize(small, DefaultProfiles.all[0])
        assertEquals(640, plan.width); assertEquals(360, plan.height)
    }

    @Test fun `mp4 hevc logiciel utilise libx265 et copie l audio aac`() {
        val b = CommandBuilder.optimize("in", "out", src1080, DefaultProfiles.all[0], hardware = false)
        assertTrue("libx265" in b.args); assertTrue("hvc1" in b.args)
        assertEquals("copy", b.args[b.args.indexOf("-c:a") + 1])
        assertEquals("mp4", b.args[b.args.indexOf("-f") + 1])
        assertTrue(b.args.joinToString(" ").contains("scale=1280:720"))
    }

    @Test fun `mp4 h264 materiel utilise mediacodec avec debit`() {
        val p = DefaultProfiles.all[0].copy(optCodec = OptCodec.AVC)
        val b = CommandBuilder.optimize("in", "out", src1080, p, hardware = true)
        assertEquals("h264_mediacodec", b.encoder); assertTrue("-b:v" in b.args)
    }

    @Test fun `720p equilibre donne un debit compatible avec l objectif de taille`() {
        // 16 min en 720p30 doit rester dans la fourchette 200-350 Mo en HEVC equilibre.
        val plan = SizePlanner.optimize(src1080, DefaultProfiles.all[0])
        val br = CommandBuilder.hwBitrate(src1080, plan, DefaultProfiles.all[0])
        val mo = br / 8.0 * 960 / 1e6
        assertTrue("taille estimee $mo Mo", mo in 150.0..350.0)
    }

    @Test fun `audio supprime`() {
        val b = CommandBuilder.optimize("in", "out", src1080, DefaultProfiles.all[0].copy(keepAudio = false), false)
        assertTrue("-an" in b.args); assertFalse(b.args.contains("0:a?"))
    }

    @Test fun `avi rgb conserve la cadence d origine sans alerte`() {
        val b = CommandBuilder.avi("in", "out", srcOdd, aviBase.copy(aviCodec = AviCodec.RGB))
        val s = b.args.joinToString(" ")
        assertTrue(s.contains("-c:v rawvideo")); assertTrue(s.contains("format=bgr24"))
        assertTrue(s.contains("-r 25/1 -fps_mode cfr"))
        assertTrue(s.contains("-an")); assertFalse(s.contains("pcm_s16le"))   // sans son par défaut
        assertTrue(b.warnings.isEmpty())
        assertEquals(642, b.plan.padWidth); assertEquals(478, b.plan.padHeight)
    }

    @Test fun `avi avec son si demande`() {
        val b = CommandBuilder.avi("in", "out", srcOdd, aviBase.copy(aviCodec = AviCodec.RGB).copy(aviKeepAudio = true))
        assertTrue(b.args.joinToString(" ").contains("pcm_s16le"))
    }

    @Test fun `avi yuy2 force le tag YUY2`() {
        val b = CommandBuilder.avi("in", "out", srcOdd, aviBase.copy(aviCodec = AviCodec.YUY2))
        assertTrue(b.args.joinToString(" ").contains("format=yuyv422"))
        assertTrue(b.args.joinToString(" ").contains("-tag:v YUY2"))
    }

    @Test fun `msvideo1 et cinepak ajoutent des bandes pour multiple de 4`() {
        val p = aviBase.copy(aviCodec = AviCodec.RGB).copy(aviCodec = AviCodec.MSVIDEO1)
        val b = CommandBuilder.avi("in", "out", srcOdd, p)
        assertEquals(644, b.plan.padWidth); assertEquals(480, b.plan.padHeight)
        assertTrue(b.args.joinToString(" ").contains("pad=644:480:0:0:black"))
        assertTrue(b.warnings.isNotEmpty())
    }

    @Test fun `changement de cadence avertit`() {
        val b = CommandBuilder.avi("in", "out", srcOdd, aviBase.copy(aviCodec = AviCodec.RGB).copy(aviFps = 15.0))
        assertTrue(b.warnings.any { it.contains("dupliquées ou supprimées") })
    }

    @Test fun `xvid utilise libxvid`() {
        val b = CommandBuilder.avi("in", "out", srcOdd, aviBase.copy(aviCodec = AviCodec.RGB).copy(aviCodec = AviCodec.XVID))
        assertEquals("libxvid", b.encoder)
    }

    @Test fun `estimation rgb realiste`() {
        val plan = SizePlanner.avi(src1080, aviBase.copy(aviCodec = AviCodec.RGB))
        val est = CommandBuilder.estimateBytes(src1080, aviBase.copy(aviCodec = AviCodec.RGB), plan, 10.0)
        assertTrue(est > 1920L * 1080 * 3 * 30 * 10)
    }
}
