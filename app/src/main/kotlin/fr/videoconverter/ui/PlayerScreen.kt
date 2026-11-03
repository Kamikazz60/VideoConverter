package fr.videoconverter.ui

import android.app.Activity
import android.net.Uri
import android.provider.MediaStore
import android.view.Gravity
import android.widget.FrameLayout
import android.widget.MediaController
import android.widget.VideoView
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.videoconverter.data.OutputFiles
import fr.videoconverter.engine.Converter
import fr.videoconverter.engine.Ffmpeg
import fr.videoconverter.model.ItemState
import fr.videoconverter.model.Mode
import java.io.File

/**
 * Lecture du fichier produit dans l'application, puis suppression éventuelle de l'original après validation.
 * Les AVI non compressés ne sont pas lisibles par le lecteur du téléphone : un aperçu H.264 (60 premières
 * secondes) est décodé depuis l'AVI par FFmpeg, ce qui montre ce que contient réellement le fichier.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerScreen(vm: AppViewModel, id: String, back: () -> Unit) {
    val ctx = LocalContext.current
    val items by vm.queue.items.collectAsStateWithLifecycle()
    val item = items.firstOrNull { it.id == id }
    var playUri by remember { mutableStateOf<Uri?>(null) }
    var status by remember { mutableStateOf<String?>(null) }
    var error by remember { mutableStateOf<String?>(null) }
    var confirmDelete by remember { mutableStateOf(false) }
    var deleted by remember { mutableStateOf(false) }
    val deleter = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { r ->
        if (r.resultCode == Activity.RESULT_OK) { deleted = true; item?.let { vm.queue.markOriginalsDeleted(listOf(it.sourceUri)) } }
    }

    LaunchedEffect(id, item?.outputUri) {
        val it = item ?: return@LaunchedEffect
        val out = Uri.parse(it.outputUri ?: return@LaunchedEffect)
        if (!OutputFiles.canOpenForRead(ctx, out)) { error = "Le fichier produit n'existe plus ou n'est plus accessible."; return@LaunchedEffect }
        if (it.profile.mode == Mode.AVI) {
            status = "Préparation de l'aperçu (décodage de l'AVI par FFmpeg)…"
            val dir = File(ctx.cacheDir, "preview").apply { mkdirs() }
            val f = File(dir, "apercu.mp4").also { f -> f.delete() }
            val r = try {
                Ffmpeg.run(
                    listOf(
                        "-hide_banner", "-y", "-i", Ffmpeg.readParam(ctx, out), "-t", "60", "-map", "0:v:0", "-map", "0:a?",
                        "-vf", "scale=trunc(iw/2)*2:trunc(ih/2)*2,format=yuv420p", "-c:v", "libx264", "-preset", "ultrafast", "-crf", "24",
                        "-c:a", "aac", "-movflags", "+faststart", f.path,
                    )
                )
            } catch (e: Exception) { null }
            status = null
            if (r?.success == true && f.length() > 0) playUri = Uri.fromFile(f) else error = "Impossible de décoder l'AVI pour l'aperçu."
        } else playUri = out
    }

    fun deletableOriginal(): Uri? = item?.let {
        val u = Uri.parse(it.sourceUri)
        u.takeIf { s -> it.state == ItemState.DONE && it.verified && s.authority == MediaStore.AUTHORITY && OutputFiles.canOpenForRead(ctx, s) }
    }

    Scaffold(topBar = {
        TopAppBar(title = { Text(item?.outputName ?: "Lecture") }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } })
    }) { pad ->
        if (item == null) { Text("Fichier introuvable dans l'historique.", Modifier.padding(pad).padding(24.dp)); return@Scaffold }
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Box(Modifier.fillMaxWidth().height(420.dp), contentAlignment = Alignment.Center) {
                val u = playUri
                when {
                    u != null -> {
                        val view = remember(u) {
                            VideoView(ctx).also { vv ->
                                vv.setMediaController(MediaController(ctx).also { c -> c.setAnchorView(vv) })
                                vv.setOnPreparedListener { it.isLooping = false; vv.start() }
                                vv.setOnErrorListener { _, _, _ -> error = "Le lecteur du téléphone ne sait pas lire ce fichier."; true }
                                vv.setVideoURI(u)
                            }
                        }
                        DisposableEffect(view) { onDispose { view.stopPlayback() } }
                        AndroidView({ c -> FrameLayout(c).also { fl -> (view.parent as? android.view.ViewGroup)?.removeView(view); fl.addView(view, FrameLayout.LayoutParams(-1, -1, Gravity.CENTER)) } }, Modifier.fillMaxSize())
                    }
                    status != null -> Column(horizontalAlignment = Alignment.CenterHorizontally) { CircularProgressIndicator(); Spacer(Modifier.height(8.dp)); Text(status!!) }
                    else -> Text(error ?: "")
                }
            }
            error?.takeIf { playUri != null }?.let { InfoCard(it, warning = true) }
            if (item.profile.mode == Mode.AVI) InfoCard("Aperçu converti pour le téléphone (60 premières secondes) à partir de l'AVI décodé : il montre ce que FFmpeg lit dans le fichier. La compatibilité avec AviMéca / AviStep ne peut être validée que sur ordinateur.")
            Spacer(Modifier.height(8.dp))
            Text("${item.name}", style = MaterialTheme.typography.titleSmall)
            Text("${formatSize(item.sizeBytes)} → ${formatSize(item.outputSize)} · encodeur ${Converter.engineLabel(item.engineUsed)}")
            item.warnings.forEach { w -> Text("⚠ $w", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.secondary) }
            Spacer(Modifier.height(12.dp))
            when {
                deleted || item.originalDeleted -> InfoCard("Original supprimé. Le fichier converti est conservé dans Films/VideoConverter.")
                deletableOriginal() != null -> {
                    Button({ confirmDelete = true }, Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)) { Text("Le résultat me convient : supprimer l'original…") }
                    Text("Rien n'est supprimé sans votre confirmation, puis celle d'Android.", style = MaterialTheme.typography.bodySmall)
                }
                else -> Text("L'original ne peut pas être supprimé d'ici (choisi via le sélecteur de fichiers, déjà supprimé, ou conversion non vérifiée). Supprimez-le depuis l'application Fichiers ou Photos si besoin.", style = MaterialTheme.typography.bodySmall)
            }
            Spacer(Modifier.height(24.dp))
        }
    }

    if (confirmDelete) AlertDialog(
        onDismissRequest = { confirmDelete = false },
        title = { Text("Supprimer l'original ?") },
        text = { Text("« ${item?.name} » (${formatSize(item?.sizeBytes ?: 0)}) sera supprimé définitivement après votre confirmation Android. Vérifiez que la vidéo convertie est bien lue en entier avant de continuer.") },
        confirmButton = {
            TextButton({
                confirmDelete = false
                deletableOriginal()?.let { deleter.launch(IntentSenderRequest.Builder(MediaStore.createDeleteRequest(ctx.contentResolver, listOf(it)).intentSender).build()) }
            }) { Text("Continuer") }
        },
        dismissButton = { TextButton({ confirmDelete = false }) { Text("Annuler") } },
    )
}
