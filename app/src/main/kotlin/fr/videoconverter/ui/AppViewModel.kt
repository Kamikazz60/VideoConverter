package fr.videoconverter.ui

import android.app.Application
import android.net.Uri
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import fr.videoconverter.VideoApp
import fr.videoconverter.data.MediaRepo
import fr.videoconverter.data.VideoItem
import fr.videoconverter.engine.CodecCheck
import fr.videoconverter.engine.CodecStatus
import fr.videoconverter.engine.Converter
import fr.videoconverter.engine.Ffmpeg
import fr.videoconverter.model.*
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext
import java.util.UUID

class AppViewModel(private val app: Application) : AndroidViewModel(app) {
    private val va get() = app as VideoApp
    val store get() = va.store
    val queue get() = va.queue

    // ----- Vidéos -----
    private val _videos = MutableStateFlow<List<VideoItem>>(emptyList())
    val videos: StateFlow<List<VideoItem>> = _videos.asStateFlow()
    val loading = MutableStateFlow(false)
    val selected = MutableStateFlow<Set<Long>>(emptySet())
    val extra = MutableStateFlow<List<VideoItem>>(emptyList())     // vidéos ajoutées par le sélecteur système
    var folderFilter by mutableStateOf<String?>(null)
    val infoCache = mutableStateMapOf<Long, VideoInfo>()
    private val probeGate = Semaphore(2)

    fun refreshVideos() {
        viewModelScope.launch {
            loading.value = true
            _videos.value = withContext(Dispatchers.IO) { runCatching { MediaRepo.list(app) }.getOrDefault(emptyList()) }
            loading.value = false
        }
    }

    fun addDocuments(uris: List<Uri>) {
        val base = extra.value.size
        val items = uris.mapIndexedNotNull { i, u -> MediaRepo.fromDocument(app, u, base + i) }
        extra.value = extra.value + items
        selected.value = selected.value + items.map { it.key }
    }

    fun allVideos(): List<VideoItem> = extra.value + _videos.value

    fun toggle(key: Long) { selected.value = if (key in selected.value) selected.value - key else selected.value + key }
    fun selectAll(keys: Collection<Long>) { selected.value = selected.value + keys }
    fun clearSelection() { selected.value = emptySet() }
    fun selectedVideos(): List<VideoItem> = allVideos().filter { it.key in selected.value }

    /** Lit fps, codecs, etc. à la demande (2 sondes simultanées au plus). */
    fun ensureInfo(v: VideoItem) {
        if (infoCache.containsKey(v.key)) return
        viewModelScope.launch {
            probeGate.withPermit {
                if (infoCache.containsKey(v.key)) return@withPermit
                Ffmpeg.probe(app, v.uri, v.name, v.sizeBytes)?.let { infoCache[v.key] = it }
            }
        }
    }

    suspend fun infoOf(v: VideoItem): VideoInfo? =
        infoCache[v.key] ?: Ffmpeg.probe(app, v.uri, v.name, v.sizeBytes)?.also { infoCache[v.key] = it }

    // ----- Profils -----
    val profiles = MutableStateFlow(store.loadProfiles())
    var mode by mutableStateOf(Mode.OPTIMIZE)
    var draft by mutableStateOf(DefaultProfiles.all[0])

    fun startMode(m: Mode) {
        mode = m
        draft = profiles.value.first { it.mode == m }
    }

    fun profilesFor(m: Mode) = profiles.value.filter { it.mode == m }

    private fun persistProfiles(l: List<Profile>) { profiles.value = l; store.saveProfiles(l) }

    fun saveDraft(name: String? = null, asNew: Boolean = false) {
        val d = if (asNew || profiles.value.none { it.id == draft.id }) draft.copy(id = UUID.randomUUID().toString(), name = name ?: draft.name)
        else draft.copy(name = name ?: draft.name)
        draft = d
        persistProfiles(if (profiles.value.any { it.id == d.id }) profiles.value.map { if (it.id == d.id) d else it } else profiles.value + d)
    }

    fun renameProfile(id: String, name: String) = persistProfiles(profiles.value.map { if (it.id == id) it.copy(name = name) else it })
    fun duplicateProfile(id: String) {
        val p = profiles.value.firstOrNull { it.id == id } ?: return
        persistProfiles(profiles.value + p.copy(id = UUID.randomUUID().toString(), name = p.name + " (copie)"))
    }
    fun deleteProfile(id: String) {
        val l = profiles.value.filterNot { it.id == id }
        persistProfiles(l)
        if (draft.id == id) draft = l.firstOrNull { it.mode == mode } ?: DefaultProfiles.all.first { it.mode == mode }
    }

    // ----- Lot -----
    /** Ajoute la sélection à la file avec le profil en cours (sondes ffprobe incluses) puis démarre. */
    fun enqueueSelectionAndStart(onDone: () -> Unit) {
        viewModelScope.launch {
            val vids = selectedVideos()
            val infos = vids.mapNotNull { infoOf(it) }
            queue.add(infos, draft)
            clearSelection()
            queue.start()
            onDone()
        }
    }

    // ----- Réglages -----
    val settings = MutableStateFlow(store.loadSettings())
    fun updateSettings(s: AppSettings) { settings.value = s; store.saveSettings(s) }

    // ----- Codecs -----
    val codecs = MutableStateFlow<List<CodecStatus>?>(CodecCheck.cached(store))
    val checkingCodecs = MutableStateFlow(false)
    fun runCodecCheck() {
        if (checkingCodecs.value) return
        viewModelScope.launch {
            checkingCodecs.value = true
            codecs.value = CodecCheck.run(app, store)
            checkingCodecs.value = false
        }
    }

    // ----- Test sur un extrait -----
    val testRunning = MutableStateFlow(false)
    val testResult = MutableStateFlow<Converter.SampleResult?>(null)
    val testProgress = MutableStateFlow(0f)
    var testSource by mutableStateOf<VideoInfo?>(null)
    fun runTest(lengthSec: Double, startFraction: Double) {
        val first = selectedVideos().firstOrNull() ?: return
        viewModelScope.launch {
            testRunning.value = true
            testProgress.value = 0f
            testResult.value = null
            val info = infoOf(first)
            if (info == null) {
                testResult.value = Converter.SampleResult(java.io.File(""), null, emptyList(), "Format non pris en charge ou fichier inaccessible.", null, null, 0, emptyList())
            } else {
                testSource = info
                val dur = info.durationMs / 1000.0
                val len = minOf(lengthSec, dur)
                val start = (dur * startFraction).coerceAtMost((dur - len).coerceAtLeast(0.0))
                testResult.value = Converter(app, store).testSample(info, draft, start, len, settings.value) { testProgress.value = it }
            }
            testRunning.value = false
        }
    }
}
