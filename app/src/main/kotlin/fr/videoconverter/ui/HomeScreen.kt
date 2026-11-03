package fr.videoconverter.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.videoconverter.model.ItemState
import fr.videoconverter.model.Mode

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeScreen(vm: AppViewModel, nav: (String) -> Unit) {
    val items by vm.queue.items.collectAsStateWithLifecycle()
    val active by vm.queue.active.collectAsStateWithLifecycle()
    val interrupted by vm.queue.interrupted.collectAsStateWithLifecycle()
    val done = items.filter { it.state == ItemState.DONE }
    val saved = done.filter { it.profile.mode == Mode.OPTIMIZE }.sumOf { (it.sizeBytes - it.outputSize).coerceAtLeast(0) }
    val pending = items.count { it.state == ItemState.PENDING || it.state == ItemState.RUNNING }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            TopAppBar(
                title = { Text("VideoConverter") },
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.background),
                actions = {
                    IconButton({ nav("history") }) { Icon(Icons.Default.History, "Historique") }
                    IconButton({ nav("settings") }) { Icon(Icons.Default.Settings, "Paramètres") }
                },
            )
        },
    ) { pad ->
        LazyColumn(Modifier.padding(pad).padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                BigEntry("Optimiser mes vidéos", "Réduire la taille en MP4 (H.265 / H.264)", Icons.Default.Compress, MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.onPrimaryContainer) {
                    vm.startMode(Mode.OPTIMIZE); vm.clearSelection(); nav("select")
                }
            }
            item {
                BigEntry("Convertir en AVI", "AVI pour AviMéca / AviStep (RGB, YUY2, MS Video, Cinepak, Xvid)", Icons.Default.Movie, MaterialTheme.colorScheme.secondaryContainer, MaterialTheme.colorScheme.onSecondaryContainer) {
                    vm.startMode(Mode.AVI); vm.clearSelection(); nav("select")
                }
            }
            if (pending > 0 || interrupted) item {
                Card(Modifier.fillMaxWidth().clickable { nav("queue") }, shape = RoundedCornerShape(16.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Queue, null, tint = MaterialTheme.colorScheme.primary)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(if (active) "Conversion en cours" else "File d'attente", style = MaterialTheme.typography.titleMedium)
                            Text(
                                if (interrupted && !active) "$pending vidéo(s) à reprendre après interruption" else "$pending vidéo(s) restante(s)",
                                style = MaterialTheme.typography.bodySmall,
                            )
                        }
                        Icon(Icons.Default.ChevronRight, null)
                    }
                }
            }
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    Stat("Conversions", done.size.toString(), Modifier.weight(1f))
                    Stat("Espace économisé", formatSize(saved), Modifier.weight(1f))
                }
            }
            item { Text("Dernières conversions", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp)) }
            val last = items.filter { it.state == ItemState.DONE || it.state == ItemState.FAILED }.sortedByDescending { it.finishedAt }.take(5)
            if (last.isEmpty()) item { Text("Aucune conversion pour le moment.", color = MaterialTheme.colorScheme.onSurfaceVariant) }
            items(last, key = { it.id }) { it ->
                ListItem(
                    headlineContent = { Text(it.outputName ?: it.name, maxLines = 1) },
                    supportingContent = {
                        Text(
                            if (it.state == ItemState.DONE) "${formatSize(it.sizeBytes)} → ${formatSize(it.outputSize)} · ${it.profile.name}" else (it.error ?: "Erreur"),
                            maxLines = 2,
                        )
                    },
                    leadingContent = {
                        Icon(
                            if (it.state == ItemState.DONE) Icons.Default.CheckCircle else Icons.Default.Error, null,
                            tint = if (it.state == ItemState.DONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        )
                    },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }
            item { Spacer(Modifier.height(24.dp)) }
        }
    }
}

@Composable
private fun BigEntry(title: String, sub: String, icon: androidx.compose.ui.graphics.vector.ImageVector, bg: androidx.compose.ui.graphics.Color, fg: androidx.compose.ui.graphics.Color, onClick: () -> Unit) {
    Card(
        Modifier.fillMaxWidth().clickable(onClick = onClick), shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = bg, contentColor = fg),
    ) {
        Row(Modifier.padding(20.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(40.dp))
            Spacer(Modifier.width(16.dp))
            Column {
                Text(title, style = MaterialTheme.typography.titleLarge)
                Text(sub, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
private fun Stat(label: String, value: String, modifier: Modifier) {
    Card(modifier, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(16.dp)) {
            Text(value, style = MaterialTheme.typography.headlineSmall, color = MaterialTheme.colorScheme.primary)
            Text(label, style = MaterialTheme.typography.bodySmall)
        }
    }
}
