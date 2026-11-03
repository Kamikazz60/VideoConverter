package fr.videoconverter.ui

import android.content.Intent
import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.videoconverter.model.Mode
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TestScreen(vm: AppViewModel, nav: (String) -> Unit, back: () -> Unit) {
    val ctx = LocalContext.current
    val running by vm.testRunning.collectAsStateWithLifecycle()
    val res by vm.testResult.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    var length by remember { mutableFloatStateOf(10f) }
    var pos by remember { mutableFloatStateOf(0.3f) }
    val first = vm.selectedVideos().firstOrNull()

    Scaffold(topBar = {
        TopAppBar(title = { Text("Test sur un extrait") }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            Text("Vidéo testée : ${first?.name ?: "aucune"}", style = MaterialTheme.typography.titleSmall)
            Text("Profil : ${vm.draft.name} — ${vm.draft.summary}", style = MaterialTheme.typography.bodySmall)
            SectionTitle("Extrait")
            Text("Durée : ${length.toInt()} s · Début à ${(pos * 100).toInt()} % de la vidéo")
            Slider(length, { length = it }, valueRange = 3f..30f)
            Slider(pos, { pos = it }, valueRange = 0f..0.9f)
            Button({ vm.runTest(length.toDouble(), pos.toDouble()) }, enabled = !running && first != null) { Text(if (running) "Conversion de l'extrait…" else "Lancer le test") }
            if (running) {
                val prog by vm.testProgress.collectAsStateWithLifecycle()
                LinearProgressIndicator({ prog }, Modifier.fillMaxWidth().padding(top = 8.dp))
                Text("${(prog * 100).toInt()} %", style = MaterialTheme.typography.bodyMedium)
            }
            res?.let { r ->
                Spacer(Modifier.height(12.dp))
                if (r.error != null) InfoCard(r.error, warning = true)
                else {
                    SectionTitle("Original / Résultat (même image)")
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Original" to r.originalFrame, "Résultat" to r.resultFrame).forEach { (label, f) ->
                            Column(Modifier.weight(1f)) {
                                Text(label, style = MaterialTheme.typography.labelMedium)
                                val bmp = remember(f) { f?.let { BitmapFactory.decodeFile(it.path) } }
                                if (bmp != null) Image(bmp.asImageBitmap(), label, Modifier.fillMaxWidth(), contentScale = ContentScale.Fit) else Text("(image indisponible)")
                            }
                        }
                    }
                    SectionTitle("Paramètres du fichier produit")
                    r.details.forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                    val src = vm.testSource
                    if (src != null && r.estimatedFullBytes > 0) {
                        Spacer(Modifier.height(8.dp))
                        Text("Estimation pour la vidéo entière : ${formatSize(r.estimatedFullBytes)} (original ${formatSize(src.sizeBytes)}). Estimation indicative, le contenu varie.")
                    }
                    r.warnings.forEach { InfoCard(it, warning = true) }
                    if (vm.mode == Mode.AVI) {
                        InfoCard("Test de compatibilité AVI : le fichier a été relu et décodé par FFmpeg. Il n'est PAS validé pour AviMéca / AviStep : copiez l'extrait sur un ordinateur et ouvrez-le dans ces logiciels avant de lancer un lot.")
                        OutlinedButton({
                            val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", r.file)
                            ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("video/x-msvideo").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Envoyer l'extrait"))
                        }) { Text("Envoyer l'extrait vers un ordinateur") }
                    }
                    Spacer(Modifier.height(12.dp))
                    Button({ vm.enqueueSelectionAndStart { nav("queue") } }, Modifier.fillMaxWidth()) { Text("Valider ce profil et convertir ${selected.size} vidéo(s)") }
                    OutlinedButton(back, Modifier.fillMaxWidth()) { Text("Modifier les réglages") }
                }
            }
            Spacer(Modifier.height(24.dp))
        }
    }
}
