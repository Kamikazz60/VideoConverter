package fr.videoconverter.data

import android.content.Context
import fr.videoconverter.model.*
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Persistance simple en JSON (file d'attente, historique, profils, réglages) + journal de diagnostic. */
class Store(private val context: Context) {
    private val json = Json { ignoreUnknownKeys = true; encodeDefaults = true; prettyPrint = false }
    private val dir get() = context.filesDir
    private val lock = Any()

    private fun write(name: String, text: String) {
        val tmp = File(dir, "$name.tmp")
        tmp.writeText(text)
        if (!tmp.renameTo(File(dir, name))) { File(dir, name).writeText(text); tmp.delete() }
    }

    private fun read(name: String): String? = File(dir, name).takeIf { it.isFile }?.let { runCatching { it.readText() }.getOrNull() }

    fun loadItems(): List<QueueItem> = synchronized(lock) {
        read("queue.json")?.let { runCatching { json.decodeFromString(ListSerializer(QueueItem.serializer()), it) }.getOrNull() } ?: emptyList()
    }

    fun saveItems(items: List<QueueItem>) = synchronized(lock) {
        write("queue.json", json.encodeToString(ListSerializer(QueueItem.serializer()), items))
    }

    fun loadProfiles(): List<Profile> = synchronized(lock) {
        val saved = read("profiles.json")?.let { runCatching { json.decodeFromString(ListSerializer(Profile.serializer()), it) }.getOrNull() }
        // Seul Xvid est conservé pour l'AVI : les anciens profils RGB / YUY2 / MS Video 1 / Cinepak sont convertis.
        saved?.filter { it.id != "avi-rgb" && it.id != "avi-yuy2" }
            ?.map { if (it.mode == Mode.AVI) it.copy(aviCodec = AviCodec.XVID) else it }
            ?.takeIf { l -> l.any { it.mode == Mode.AVI } && l.any { it.mode == Mode.OPTIMIZE } }
            ?: DefaultProfiles.all
    }

    fun saveProfiles(p: List<Profile>) = synchronized(lock) {
        write("profiles.json", json.encodeToString(ListSerializer(Profile.serializer()), p))
    }

    fun loadSettings(): AppSettings = synchronized(lock) {
        read("settings.json")?.let { runCatching { json.decodeFromString(AppSettings.serializer(), it) }.getOrNull() } ?: AppSettings()
    }

    fun saveSettings(s: AppSettings) = synchronized(lock) { write("settings.json", json.encodeToString(AppSettings.serializer(), s)) }

    // ----- Capacités d'encodeurs, testées pour de vrai (voir CodecCheck) -----
    fun loadCodecReport(): String? = synchronized(lock) { read("codecs.json") }
    fun saveCodecReport(text: String) = synchronized(lock) { write("codecs.json", text) }

    // ----- Journal de diagnostic exportable -----
    val logFile get() = File(dir, "diagnostic.log")

    fun log(level: String, message: String) = synchronized(lock) {
        runCatching {
            val f = logFile
            if (f.length() > 2_000_000) { File(dir, "diagnostic.old.log").delete(); f.renameTo(File(dir, "diagnostic.old.log")) }
            val ts = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.ROOT).format(Date())
            f.appendText("$ts [$level] $message\n")
        }
        Unit
    }
}
