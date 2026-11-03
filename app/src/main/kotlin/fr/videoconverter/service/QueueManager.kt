package fr.videoconverter.service

import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.PowerManager
import androidx.core.content.ContextCompat
import fr.videoconverter.data.Store
import fr.videoconverter.engine.Converter
import fr.videoconverter.engine.Ffmpeg
import fr.videoconverter.engine.Outcome
import fr.videoconverter.model.*
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import java.util.UUID
import java.util.concurrent.atomic.AtomicBoolean

/** Source de vérité de la file d'attente (persistante) et boucle de traitement séquentielle. */
class QueueManager(private val context: Context, private val store: Store) {
    private val _items = MutableStateFlow<List<QueueItem>>(emptyList())
    val items: StateFlow<List<QueueItem>> = _items.asStateFlow()

    private val _paused = MutableStateFlow(false)
    val paused: StateFlow<Boolean> = _paused.asStateFlow()

    private val _status = MutableStateFlow("")
    val status: StateFlow<String> = _status.asStateFlow()

    private val _active = MutableStateFlow(false)
    val active: StateFlow<Boolean> = _active.asStateFlow()

    /** Vrai si l'application a été interrompue pendant une conversion (reprise proposée). */
    val interrupted = MutableStateFlow(false)

    private val loopRunning = AtomicBoolean(false)
    @Volatile private var cancelAllRequested = false
    @Volatile private var cancelCurrentId: String? = null
    private val converter = Converter(context, store)
    val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    init {
        val loaded = store.loadItems().map {
            if (it.state == ItemState.RUNNING) { interrupted.value = true; it.copy(state = ItemState.PENDING, progress = 0f) } else it
        }
        _items.value = loaded
        if (loaded.any { it.state == ItemState.PENDING }) interrupted.value = true
    }

    private fun update(persist: Boolean = true, f: (List<QueueItem>) -> List<QueueItem>) {
        _items.update(f)
        if (persist) store.saveItems(_items.value)
    }

    private fun updateItem(id: String, persist: Boolean = true, f: (QueueItem) -> QueueItem) =
        update(persist) { l -> l.map { if (it.id == id) f(it) else it } }

    fun add(videos: List<VideoInfo>, profile: Profile): String {
        val batch = UUID.randomUUID().toString()
        val now = System.currentTimeMillis()
        val already = _items.value.filter { it.state == ItemState.PENDING || it.state == ItemState.RUNNING }.map { it.sourceUri to it.profile.id }.toSet()
        val news = videos.filter { (it.uri to profile.id) !in already }.map {
            QueueItem(UUID.randomUUID().toString(), batch, it.uri, it.name, it.sizeBytes, it.durationMs, profile, addedAt = now)
        }
        update { it + news }
        store.log("INFO", "${news.size} vidéo(s) ajoutée(s) à la file avec le profil « ${profile.name} »")
        return batch
    }

    fun remove(id: String) {
        val it = _items.value.firstOrNull { x -> x.id == id } ?: return
        if (it.state == ItemState.RUNNING) return
        update { l -> l.filterNot { x -> x.id == id } }
    }

    fun clearFinished() = update { l -> l.filter { it.state == ItemState.PENDING || it.state == ItemState.RUNNING } }

    fun deleteHistoryEntry(id: String) = update { l -> l.filterNot { it.id == id } }

    fun retryFailed() = update { l -> l.map { if (it.state == ItemState.FAILED || it.state == ItemState.CANCELED) it.copy(state = ItemState.PENDING, progress = 0f, error = null) else it } }

    fun start() {
        _paused.value = false
        interrupted.value = false
        if (_items.value.none { it.state == ItemState.PENDING }) return
        val i = Intent(context, ConversionService::class.java)
        ContextCompat.startForegroundService(context, i)
    }

    /** Pause : le fichier en cours se termine, puis la file s'arrête. (FFmpeg ne sait pas figer un encodage.) */
    fun pauseAfterCurrent() { _paused.value = true }

    fun resume() = start()

    /** Interrompt immédiatement le fichier en cours (il sera marqué « annulé »). */
    fun cancelCurrent() {
        _items.value.firstOrNull { it.state == ItemState.RUNNING }?.let { cancelCurrentId = it.id; Ffmpeg.cancelCurrent() }
    }

    fun cancelAll() {
        cancelAllRequested = true
        cancelCurrent()
        update { l -> l.map { if (it.state == ItemState.PENDING) it.copy(state = ItemState.CANCELED, finishedAt = System.currentTimeMillis()) else it } }
    }

    private fun thermalSevere(): Boolean {
        if (Build.VERSION.SDK_INT < 29) return false
        val pm = context.getSystemService(PowerManager::class.java)
        return pm.currentThermalStatus >= PowerManager.THERMAL_STATUS_SEVERE
    }

    /** Boucle principale ; appelée par le service de premier plan. Retourne quand la file est vide ou en pause. */
    suspend fun runLoop(onNotify: (String, Float) -> Unit) {
        if (!loopRunning.compareAndSet(false, true)) return
        _active.value = true
        cancelAllRequested = false
        val settings = store.loadSettings()
        try {
            while (currentCoroutineContext().isActive) {
                if (_paused.value || cancelAllRequested) break
                val next = _items.value.firstOrNull { it.state == ItemState.PENDING } ?: break
                // Surchauffe : attendre le refroidissement entre deux fichiers.
                var waited = 0
                while (settings.pauseOnThermal && thermalSevere() && waited < 40 && !_paused.value) {
                    _status.value = "Téléphone trop chaud : pause pour refroidir…"
                    onNotify(_status.value, -1f)
                    delay(15_000); waited++
                }
                val total = _items.value.count { it.batchId == next.batchId }
                val done = _items.value.count { it.batchId == next.batchId && it.state != ItemState.PENDING && it.state != ItemState.RUNNING }
                updateItem(next.id) { it.copy(state = ItemState.RUNNING, progress = 0f, startedAt = System.currentTimeMillis(), error = null, warnings = emptyList()) }
                _status.value = "${done + 1}/$total · ${next.name}"
                onNotify(_status.value, 0f)
                var lastNotify = 0L
                val outcome = converter.convert(next, settings) { p ->
                    updateItem(next.id, persist = false) { it.copy(progress = p) }
                    val now = System.currentTimeMillis()
                    if (now - lastNotify > 1000) { lastNotify = now; onNotify(_status.value, p) }
                }
                val now = System.currentTimeMillis()
                when (outcome) {
                    is Outcome.Success -> updateItem(next.id) {
                        it.copy(state = ItemState.DONE, progress = 1f, outputUri = outcome.uri.toString(), outputName = outcome.name, outputSize = outcome.size,
                            warnings = outcome.warnings, finishedAt = now, engineUsed = outcome.engine, verified = outcome.verified)
                    }
                    is Outcome.Failure -> updateItem(next.id) { it.copy(state = ItemState.FAILED, error = outcome.message, finishedAt = now) }
                    Outcome.Cancelled -> updateItem(next.id) { it.copy(state = ItemState.CANCELED, finishedAt = now, error = "Conversion annulée.") }
                }
                cancelCurrentId = null
            }
        } finally {
            // Un fichier laissé « en cours » (arrêt brutal du service) repart en attente.
            update { l -> l.map { if (it.state == ItemState.RUNNING) it.copy(state = ItemState.PENDING, progress = 0f) else it } }
            _status.value = ""
            _active.value = false
            loopRunning.set(false)
        }
    }

    fun summary(): Pair<Int, Int> {
        val l = _items.value
        return l.count { it.state == ItemState.DONE } to l.count { it.state == ItemState.FAILED }
    }
}
