package fr.videoconverter.ui

import android.content.Intent
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.videoconverter.data.OutputFiles
import fr.videoconverter.model.ItemState
import fr.videoconverter.model.QueueItem
import java.text.DateFormat
import java.util.Date

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HistoryScreen(vm: AppViewModel, nav: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val items by vm.queue.items.collectAsStateWithLifecycle()
    val hist = items.filter { it.state == ItemState.DONE || it.state == ItemState.FAILED || it.state == ItemState.CANCELED }.sortedByDescending { it.finishedAt }
    var confirm by remember { mutableStateOf<List<Uri>?>(null) }
    var selectedItem by remember { mutableStateOf<QueueItem?>(null) }
    var pendingDeleted by remember { mutableStateOf<List<Uri>>(emptyList()) }
    val deleter = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == android.app.Activity.RESULT_OK) vm.queue.markOriginalsDeleted(pendingDeleted.map { it.toString() })
        pendingDeleted = emptyList()
    }

    fun mediaOriginals(l: List<QueueItem>): List<Uri> =
        l.filter { it.state == ItemState.DONE && it.verified && it.outputUri != null && OutputFiles.canOpenForRead(ctx, Uri.parse(it.outputUri)) }
            .map { Uri.parse(it.sourceUri) }
            .filter { it.authority == MediaStore.AUTHORITY && OutputFiles.canOpenForRead(ctx, it) }
            .distinct()

    Scaffold(topBar = {
        TopAppBar(
            title = { Text("Historique") },
            navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
            actions = { IconButton({ confirm = mediaOriginals(hist) }) { Icon(Icons.Default.DeleteForever, "Supprimer les originaux validés") } },
        )
    }) { pad ->
        if (hist.isEmpty()) Text("L'historique est vide.", Modifier.padding(pad).padding(24.dp))
        LazyColumn(Modifier.padding(pad)) {
            items(hist, key = { it.id }) { it ->
                ListItem(
                    modifier = Modifier.clickable { selectedItem = it }.let { m -> if (it.originalDeleted) m.alpha(0.6f) else m },
                    colors = if (it.originalDeleted) ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.surfaceVariant) else ListItemDefaults.colors(),
                    headlineContent = { Text(it.outputName ?: it.name, maxLines = 1) },
                    supportingContent = {
                        Column {
                            Text(DateFormat.getDateTimeInstance(DateFormat.SHORT, DateFormat.SHORT).format(Date(it.finishedAt)) + " · " + it.profile.name)
                            if (it.state == ItemState.DONE) Text("${formatSize(it.sizeBytes)} → ${formatSize(it.outputSize)}${if (it.verified) " · vérifié" else ""}")
                            if (it.originalDeleted) OriginalDeletedTag()
                            else Text(it.error ?: "", color = MaterialTheme.colorScheme.error)
                        }
                    },
                    leadingContent = { Icon(if (it.state == ItemState.DONE) Icons.Default.CheckCircle else Icons.Default.Error, null, tint = if (it.state == ItemState.DONE) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) },
                )
            }
        }
    }

    selectedItem?.let { it ->
        AlertDialog(
            onDismissRequest = { selectedItem = null },
            title = { Text(it.outputName ?: it.name) },
            text = {
                Column {
                    Text("Source : ${it.name}")
                    Text("Profil : ${it.profile.summary}")
                    if (it.state == ItemState.DONE) {
                        Text("Résultat : Films/VideoConverter/${if (it.profile.mode == fr.videoconverter.model.Mode.AVI) "AVI" else "Optimized"}")
                        Text("Encodeur : ${fr.videoconverter.engine.Converter.engineLabel(it.engineUsed)}")
                    } else Text(it.error ?: "", color = MaterialTheme.colorScheme.error)
                    it.warnings.forEach { w -> Text("⚠ $w", style = MaterialTheme.typography.bodySmall) }
                }
            },
            confirmButton = {
                Row {
                    if (it.state == ItemState.DONE && it.outputUri != null) TextButton({ val id = it.id; selectedItem = null; nav("player/$id") }) { Text("Lire") }
                    TextButton({ vm.queue.deleteHistoryEntry(it.id); selectedItem = null }) { Text("Retirer") }
                }
            },
            dismissButton = {
                Row {
                    if (it.state == ItemState.DONE) TextButton({ confirm = mediaOriginals(listOf(it)); selectedItem = null }) { Text("Supprimer l'original…") }
                    TextButton({ selectedItem = null }) { Text("Fermer") }
                }
            },
        )
    }

    confirm?.let { uris ->
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Supprimer les originaux ?") },
            text = {
                Text(
                    if (uris.isEmpty()) "Aucun original éligible : seuls les originaux des conversions vérifiées, dont le résultat existe toujours, peuvent être proposés."
                    else "${uris.size} fichier(s) original(aux) dont la conversion a été vérifiée. Android vous demandera une confirmation avant toute suppression. VideoConverter ne supprime jamais rien sans votre accord.",
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    if (uris.isNotEmpty()) {
                        pendingDeleted = uris
                        val sender = MediaStore.createDeleteRequest(ctx.contentResolver, uris).intentSender
                        deleter.launch(IntentSenderRequest.Builder(sender).build())
                    }
                    confirm = null
                }) { Text(if (uris.isEmpty()) "OK" else "Continuer") }
            },
            dismissButton = { if (uris.isNotEmpty()) TextButton({ confirm = null }) { Text("Annuler") } },
        )
    }
}
