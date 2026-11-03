package fr.videoconverter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.videoconverter.model.ItemState
import fr.videoconverter.model.Mode
import fr.videoconverter.model.QueueItem
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun QueueScreen(vm: AppViewModel, nav: (String) -> Unit, back: () -> Unit) {
    val q = vm.queue
    val items by q.items.collectAsStateWithLifecycle()
    val active by q.active.collectAsStateWithLifecycle()
    val paused by q.paused.collectAsStateWithLifecycle()
    val status by q.status.collectAsStateWithLifecycle()
    val interrupted by q.interrupted.collectAsStateWithLifecycle()
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(active) { while (active) { now = System.currentTimeMillis(); delay(1000) } }

    val total = items.size
    val finished = items.count { it.state == ItemState.DONE || it.state == ItemState.FAILED || it.state == ItemState.CANCELED }
    val doneItems = items.filter { it.state == ItemState.DONE }
    val failed = items.count { it.state == ItemState.FAILED }
    val remaining = items.count { it.state == ItemState.PENDING || it.state == ItemState.RUNNING }
    val current = items.firstOrNull { it.state == ItemState.RUNNING }
    val overall = if (total == 0) 0f else (items.sumOf { (if (it.state == ItemState.RUNNING) it.progress else if (it.state == ItemState.PENDING) 0f else 1f).toDouble() } / total).toFloat()
    val origDone = doneItems.sumOf { it.sizeBytes }
    val outDone = doneItems.sumOf { it.outputSize }
    // Temps écoulé / restant : basé sur le temps déjà passé sur les fichiers terminés et le fichier en cours.
    val spent = items.filter { it.startedAt > 0 }.let { l -> l.sumOf { (if (it.finishedAt > 0) it.finishedAt else now) - it.startedAt } }
    val eta = if (overall > 0.02f && remaining > 0) (spent / overall * (1 - overall)).toLong() else -1L

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Conversion") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            actions = { if (!active) IconButton({ q.clearFinished() }) { Icon(Icons.Default.DeleteSweep, "Vider les terminées") } },
        )
    }) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            item {
                Card(Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(16.dp)) {
                        Text(
                            when {
                                active && paused -> "Pause demandée : fin du fichier en cours…"
                                active -> status.ifBlank { "Conversion en cours" }
                                remaining > 0 && paused -> "En pause"
                                remaining > 0 -> "Prêt à reprendre"
                                total == 0 -> "File vide"
                                else -> "Terminé"
                            },
                            style = MaterialTheme.typography.titleMedium,
                        )
                        Spacer(Modifier.height(8.dp))
                        LinearProgressIndicator({ overall }, Modifier.fillMaxWidth())
                        Spacer(Modifier.height(8.dp))
                        Text("Terminées : ${doneItems.size} · Restantes : $remaining · Erreurs : $failed")
                        Text("Temps écoulé : ${formatDuration(spent)}" + if (eta >= 0) " · Restant estimé : ${formatDuration(eta)}" else "")
                        if (doneItems.isNotEmpty()) {
                            val mode = doneItems.first().profile.mode
                            Text(
                                if (mode == Mode.OPTIMIZE) "Gain total : ${formatSize((origDone - outDone).coerceAtLeast(0))} (${formatSize(origDone)} → ${formatSize(outDone)})"
                                else "Taille produite : ${formatSize(outDone)} (originaux : ${formatSize(origDone)})",
                            )
                        }
                        if (interrupted && !active && remaining > 0) InfoCard("Une conversion a été interrompue (arrêt de l'application ou du téléphone). Les fichiers terminés sont conservés ; reprenez la file.", warning = true)
                        Spacer(Modifier.height(8.dp))
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            if (!active && remaining > 0) Button({ q.resume() }) { Icon(Icons.Default.PlayArrow, null); Spacer(Modifier.width(4.dp)); Text("Reprendre") }
                            if (active && !paused) OutlinedButton({ q.pauseAfterCurrent() }) { Icon(Icons.Default.Pause, null); Spacer(Modifier.width(4.dp)); Text("Pause") }
                            if (active) OutlinedButton({ q.cancelCurrent() }) { Text("Annuler ce fichier") }
                            if (remaining > 0 || active) OutlinedButton({ q.cancelAll() }) { Text("Tout annuler") }
                        }
                        if (!active && (failed > 0 || items.any { it.state == ItemState.CANCELED })) {
                            TextButton({ q.retryFailed(); q.start() }) { Text("Réessayer les erreurs et annulations") }
                        }
                    }
                }
            }
            if (current != null) item {
                Text("En cours : ${current.name}", style = MaterialTheme.typography.titleSmall)
                LinearProgressIndicator({ current.progress }, Modifier.fillMaxWidth().padding(vertical = 4.dp))
                Text("${(current.progress * 100).toInt()} % · ${current.profile.name}", style = MaterialTheme.typography.bodySmall)
            }
            items(items.sortedBy { orderOf(it) }, key = { it.id }) { it -> QueueRow(it, canRemove = it.state != ItemState.RUNNING, onOpen = { nav("player/${it.id}") }) { q.remove(it.id) } }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

private fun orderOf(i: QueueItem) = when (i.state) { ItemState.RUNNING -> 0; ItemState.PENDING -> 1; ItemState.FAILED -> 2; ItemState.CANCELED -> 3; ItemState.DONE -> 4 }

@Composable
fun QueueRow(it: QueueItem, canRemove: Boolean, onOpen: () -> Unit, onRemove: () -> Unit) {
    ListItem(
        modifier = if (it.state == ItemState.DONE && it.outputUri != null) Modifier.clickable(onClick = onOpen) else Modifier,
        headlineContent = { Text(it.name, maxLines = 1) },
        supportingContent = {
            Column {
                when (it.state) {
                    ItemState.DONE -> {
                        Text("${formatSize(it.sizeBytes)} → ${formatSize(it.outputSize)} · ${it.outputName}")
                        Text("Encodeur : ${fr.videoconverter.engine.Converter.engineLabel(it.engineUsed)} · toucher pour lire", style = MaterialTheme.typography.bodySmall)
                    }
                    ItemState.FAILED, ItemState.CANCELED -> Text(it.error ?: "", color = MaterialTheme.colorScheme.error)
                    ItemState.RUNNING -> Text("${(it.progress * 100).toInt()} %")
                    ItemState.PENDING -> Text("En attente · ${it.profile.name} · ${formatSize(it.sizeBytes)}")
                }
                it.warnings.forEach { w -> Text("⚠ $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            }
        },
        leadingContent = {
            Icon(
                when (it.state) {
                    ItemState.DONE -> Icons.Default.CheckCircle; ItemState.FAILED -> Icons.Default.Error
                    ItemState.CANCELED -> Icons.Default.Cancel; ItemState.RUNNING -> Icons.Default.Sync; ItemState.PENDING -> Icons.Default.Schedule
                }, null,
                tint = when (it.state) { ItemState.DONE -> MaterialTheme.colorScheme.primary; ItemState.FAILED -> MaterialTheme.colorScheme.error; else -> MaterialTheme.colorScheme.onSurfaceVariant },
            )
        },
        trailingContent = { if (canRemove) IconButton(onRemove) { Icon(Icons.Default.Close, "Retirer") } },
    )
}
