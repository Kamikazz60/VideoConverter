package fr.videoconverter.ui

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.videoconverter.data.MediaRepo
import fr.videoconverter.data.VideoItem
import fr.videoconverter.model.Mode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SelectScreen(vm: AppViewModel, nav: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val videos by vm.videos.collectAsStateWithLifecycle()
    val extra by vm.extra.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val loading by vm.loading.collectAsStateWithLifecycle()
    val perm = if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO else Manifest.permission.READ_EXTERNAL_STORAGE
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(ctx, perm) == PackageManager.PERMISSION_GRANTED) }
    val permLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted = it; if (it) vm.refreshVideos() }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris ->
        uris.forEach { u -> runCatching { ctx.contentResolver.takePersistableUriPermission(u, Intent.FLAG_GRANT_READ_URI_PERMISSION) } }
        vm.addDocuments(uris)
    }
    LaunchedEffect(granted) { if (granted) vm.refreshVideos() else permLauncher.launch(perm) }

    val all = extra + videos
    val folders = remember(all) { all.map { it.folder }.distinct().sorted() }
    val settings by vm.settings.collectAsStateWithLifecycle()
    val hideEncoded = settings.hideEncoded && vm.mode == Mode.OPTIMIZE
    val inFolder = all.filter { vm.folderFilter == null || it.folder == vm.folderFilter }
    LaunchedEffect(hideEncoded, inFolder.size) { if (hideEncoded) vm.probeAll(inFolder) }
    val toScan = if (hideEncoded) vm.unprobed(inFolder) else 0
    // Filtre actif : une vidéo n'apparaît qu'une fois son codec connu et différent de H.264 / H.265 (ou si la lecture a échoué).
    val shown = vm.sortOrder.apply(
        if (hideEncoded) inFolder.filter { v -> vm.isEncoded(v) == false || vm.probeFailed(v) } else inFolder
    )
    var menu by remember { mutableStateOf(false) }
    var sortMenu by remember { mutableStateOf(false) }
    val selectedSize = all.filter { it.key in selected }.sumOf { it.sizeBytes }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.mode == Mode.AVI) "Choisir les vidéos (AVI)" else "Choisir les vidéos") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = { IconButton({ picker.launch(arrayOf("video/*")) }) { Icon(Icons.Default.FolderOpen, "Sélecteur de fichiers") } },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("${selected.size} sélectionnée(s)", style = MaterialTheme.typography.titleSmall)
                        Text(formatSize(selectedSize), style = MaterialTheme.typography.bodySmall)
                    }
                    Button(onClick = { nav("config") }, enabled = selected.isNotEmpty()) { Text("Suivant") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad)) {
            if (!granted) {
                Column(Modifier.padding(24.dp)) {
                    Text("L'accès à vos vidéos est nécessaire pour les lister. Les fichiers restent sur le téléphone.")
                    Spacer(Modifier.height(12.dp))
                    Button({ permLauncher.launch(perm) }) { Text("Autoriser l'accès") }
                    Spacer(Modifier.height(8.dp))
                    OutlinedButton({ picker.launch(arrayOf("video/*")) }) { Text("Ou choisir les fichiers avec le sélecteur système") }
                }
            }
            Row(Modifier.padding(horizontal = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.weight(1f)) {
                    OutlinedButton({ menu = true }) {
                        Icon(Icons.Default.Folder, null); Spacer(Modifier.width(6.dp))
                        Text(vm.folderFilter ?: "Tous les dossiers", maxLines = 1)
                    }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Tous les dossiers") }, { vm.folderFilter = null; menu = false })
                        folders.forEach { f -> DropdownMenuItem({ Text(f) }, { vm.folderFilter = f; menu = false }) }
                    }
                }
                TextButton({ vm.selectAll(shown.map { it.key }) }) { Text(if (vm.folderFilter == null) "Tout" else "Tout le dossier") }
                TextButton({ vm.clearSelection() }) { Text("Aucune") }
            }
            Box(Modifier.padding(horizontal = 12.dp)) {
                OutlinedButton({ sortMenu = true }, Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.SwapVert, null); Spacer(Modifier.width(6.dp))
                    Text("Tri : ${vm.sortOrder.label}", maxLines = 1)
                }
                DropdownMenu(sortMenu, { sortMenu = false }) {
                    VideoSort.entries.forEach { s -> DropdownMenuItem({ Text(s.label) }, { vm.sortOrder = s; sortMenu = false }, trailingIcon = { if (s == vm.sortOrder) Icon(Icons.Default.Check, null) }) }
                }
            }
            if (vm.mode == Mode.OPTIMIZE) Row(
                Modifier.fillMaxWidth().clickable { vm.updateSettings(settings.copy(hideEncoded = !settings.hideEncoded)) }.padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Checkbox(settings.hideEncoded, { vm.updateSettings(settings.copy(hideEncoded = it)) })
                Column {
                    Text("Masquer les vidéos déjà en H.264 / H.265")
                    Text("Option : décochez-la pour revoir toutes les vidéos.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            if (loading) LinearProgressIndicator(Modifier.fillMaxWidth())
            if (toScan > 0) {
                LinearProgressIndicator({ 1f - toScan.toFloat() / inFolder.size.coerceAtLeast(1) }, Modifier.fillMaxWidth())
                Text("Analyse des codecs : ${inFolder.size - toScan} / ${inFolder.size}", Modifier.padding(horizontal = 16.dp), style = MaterialTheme.typography.bodySmall)
            }
            if (shown.isEmpty() && !loading && granted && toScan == 0) Text(
                if (hideEncoded && inFolder.isNotEmpty()) "Toutes les vidéos sont déjà en H.264 / H.265 (masquées). Décochez l'option pour les voir." else "Aucune vidéo trouvée.",
                Modifier.padding(24.dp),
            )
            LazyColumn(Modifier.fillMaxSize()) {
                items(shown, key = { it.key }) { v -> VideoRow(vm, v, v.key in selected) }
            }
        }
    }
}

@Composable
private fun VideoRow(vm: AppViewModel, v: VideoItem, checked: Boolean) {
    val ctx = LocalContext.current
    val info = vm.infoCache[v.key]
    LaunchedEffect(v.key) { vm.ensureInfo(v) }
    var thumb by remember(v.key) { mutableStateOf<android.graphics.Bitmap?>(null) }
    LaunchedEffect(v.key) { thumb = withContext(Dispatchers.IO) { MediaRepo.thumbnail(ctx, v.uri) } }
    ListItem(
        modifier = Modifier.clickableRow { vm.toggle(v.key) },
        leadingContent = {
            Box(Modifier.size(64.dp).clip(RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
                val t = thumb
                if (t != null) Image(t.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
                else Icon(Icons.Default.Videocam, null)
            }
        },
        headlineContent = { Text(v.name, maxLines = 1) },
        supportingContent = {
            val w = info?.displayWidth ?: v.width
            val h = info?.displayHeight ?: v.height
            val dur = info?.durationMs ?: v.durationMs
            Column {
                Text("${formatDuration(dur)} · ${formatSize(v.sizeBytes)}" + if (w > 0) " · ${w}×$h" else "")
                Text(
                    if (info != null) "${formatFps(info.fps)} i/s · ${info.videoCodec} / ${info.audioCodec ?: "sans audio"}" else "lecture des détails…",
                    style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        trailingContent = { Checkbox(checked, { vm.toggle(v.key) }) },
    )
}

private fun Modifier.clickableRow(onClick: () -> Unit): Modifier = this.then(Modifier.clickable(onClick = onClick))
