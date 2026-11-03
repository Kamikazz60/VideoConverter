package fr.videoconverter.ui

import android.content.Intent
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import java.io.File

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(vm: AppViewModel, back: () -> Unit) {
    val ctx = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val codecs by vm.codecs.collectAsStateWithLifecycle()
    val checking by vm.checkingCodecs.collectAsStateWithLifecycle()
    var folder by remember(s.destFolder) { mutableStateOf(s.destFolder) }

    Scaffold(topBar = {
        TopAppBar(title = { Text("Paramètres") }, navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } })
    }) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle("Dossier de destination")
            OutlinedTextField(
                folder, { folder = it.filter { c -> c.isLetterOrDigit() || c in " _-" }.take(40) }, singleLine = true,
                label = { Text("Dossier dans Films") }, supportingText = { Text("Films/${folder.ifBlank { "VideoConverter" }}/Optimized  et  /AVI") },
                modifier = Modifier.fillMaxWidth(),
            )
            TextButton({ vm.updateSettings(s.copy(destFolder = folder.trim().ifBlank { "VideoConverter" })) }, enabled = folder.trim() != s.destFolder) { Text("Enregistrer le dossier") }

            SectionTitle("Sécurité et fiabilité")
            LabeledSwitch("Vérification de décodage approfondie", s.deepVerify, { vm.updateSettings(s.copy(deepVerify = it)) }, "Décode le début et la fin de chaque fichier produit. Les originaux ne sont jamais modifiés ni supprimés automatiquement.")
            LabeledSwitch("Pause si le téléphone surchauffe", s.pauseOnThermal, { vm.updateSettings(s.copy(pauseOnThermal = it)) }, "Attend le refroidissement entre deux fichiers.")

            SectionTitle("Diagnostic des codecs (test réel)")
            Text("Chaque codec n'est déclaré disponible que s'il a réellement encodé, relu et décodé un fichier de test sur ce téléphone.", style = MaterialTheme.typography.bodySmall)
            Spacer(Modifier.height(8.dp))
            Button({ vm.runCodecCheck() }, enabled = !checking) { Text(if (checking) "Test en cours…" else "Lancer le test des codecs") }
            if (checking) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
            codecs?.forEach { c ->
                ListItem(
                    headlineContent = { Text(c.label) }, supportingContent = { Text(c.detail) },
                    leadingContent = { Icon(if (c.ok) Icons.Default.CheckCircle else Icons.Default.Cancel, null, tint = if (c.ok) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error) },
                    colors = ListItemDefaults.colors(containerColor = MaterialTheme.colorScheme.background),
                )
            }

            SectionTitle("Journal de diagnostic")
            OutlinedButton({
                val f: File = vm.store.logFile
                if (f.isFile) {
                    val uri = FileProvider.getUriForFile(ctx, ctx.packageName + ".files", f)
                    ctx.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), "Exporter le journal"))
                }
            }) { Text("Exporter le journal") }

            SectionTitle("À propos")
            InfoCard("VideoConverter 1.0.0 — traitement 100 % local, sans publicité, sans abonnement, sans suivi.\n\nMoteur : FFmpeg 6.1 (build « full-gpl » de FFmpegKit maintenu par ffmpegkit-maintained) avec x264, x265 et Xvid : l'application est donc distribuée sous licence GPL v3 pour usage personnel. Voir docs/CODECS.md.")
            Spacer(Modifier.height(24.dp))
        }
    }
}
