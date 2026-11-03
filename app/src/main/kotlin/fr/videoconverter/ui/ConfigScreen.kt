package fr.videoconverter.ui

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import fr.videoconverter.engine.CodecCheck
import fr.videoconverter.engine.CommandBuilder
import fr.videoconverter.model.*
import java.util.Locale

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ConfigScreen(vm: AppViewModel, nav: (String) -> Unit, back: () -> Unit) {
    val profiles by vm.profiles.collectAsStateWithLifecycle()
    val codecs by vm.codecs.collectAsStateWithLifecycle()
    val selected by vm.selected.collectAsStateWithLifecycle()
    val d = vm.draft
    var menu by remember { mutableStateOf(false) }
    var dialog by remember { mutableStateOf<String?>(null) }   // "save", "rename"
    var nameField by remember { mutableStateOf("") }
    LaunchedEffect(Unit) { if (codecs == null) vm.runCodecCheck() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(if (vm.mode == Mode.AVI) "Réglages AVI" else "Réglages d'optimisation") },
                navigationIcon = { IconButton(back) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Retour") } },
                actions = {
                    IconButton({ menu = true }) { Icon(Icons.Default.MoreVert, "Profils") }
                    DropdownMenu(menu, { menu = false }) {
                        DropdownMenuItem({ Text("Enregistrer le profil") }, { menu = false; vm.saveDraft() })
                        DropdownMenuItem({ Text("Enregistrer sous un nouveau nom…") }, { menu = false; nameField = d.name; dialog = "save" })
                        DropdownMenuItem({ Text("Renommer…") }, { menu = false; nameField = d.name; dialog = "rename" })
                        DropdownMenuItem({ Text("Dupliquer") }, { menu = false; vm.duplicateProfile(d.id) })
                        DropdownMenuItem({ Text("Supprimer") }, { menu = false; vm.deleteProfile(d.id) })
                    }
                },
            )
        },
        bottomBar = {
            Surface(tonalElevation = 3.dp) {
                Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton({ vm.testResult.value = null; nav("test") }, Modifier.weight(1f)) { Text("Tester un extrait") }
                    Button({ vm.enqueueSelectionAndStart { nav("queue") } }, Modifier.weight(1f)) { Text("Convertir ${selected.size}") }
                }
            }
        },
    ) { pad ->
        Column(Modifier.padding(pad).padding(horizontal = 16.dp).verticalScroll(rememberScrollState())) {
            SectionTitle("Profil")
            var pm by remember { mutableStateOf(false) }
            Box {
                OutlinedButton({ pm = true }, Modifier.fillMaxWidth()) { Text(d.name) }
                DropdownMenu(pm, { pm = false }) {
                    profiles.filter { it.mode == vm.mode }.forEach { p -> DropdownMenuItem({ Text(p.name) }, { vm.draft = p; pm = false }) }
                }
            }
            if (vm.mode == Mode.OPTIMIZE) OptimizeSettings(d, { vm.draft = it }, vm) else AviSettings(d, { vm.draft = it }, vm)
            Spacer(Modifier.height(24.dp))
        }
    }

    if (dialog != null) {
        AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text(if (dialog == "save") "Enregistrer sous" else "Renommer le profil") },
            text = { OutlinedTextField(nameField, { nameField = it }, singleLine = true, label = { Text("Nom") }) },
            confirmButton = {
                TextButton({
                    if (nameField.isNotBlank()) {
                        if (dialog == "save") vm.saveDraft(nameField.trim(), asNew = true)
                        else { vm.renameProfile(d.id, nameField.trim()); vm.draft = d.copy(name = nameField.trim()) }
                    }
                    dialog = null
                }) { Text("OK") }
            },
            dismissButton = { TextButton({ dialog = null }) { Text("Annuler") } },
        )
    }
}

@Composable
private fun OptimizeSettings(d: Profile, set: (Profile) -> Unit, vm: AppViewModel) {
    SectionTitle("Codec")
    ChoiceChips(OptCodec.entries, d.optCodec, { it.label }) { set(d.copy(optCodec = it)) }
    SectionTitle("Moteur d'encodage")
    ChoiceChips(EncoderEngine.entries, d.engine, { it.label }) { set(d.copy(engine = it)) }
    val hwId = if (d.optCodec == OptCodec.HEVC) "hevc_hw" else "avc_hw"
    val hwStatus = vm.codecs.collectAsStateWithLifecycle().value?.firstOrNull { it.id == hwId }
    InfoCard(
        when {
            d.engine == EncoderEngine.SOFTWARE -> "Encodage logiciel (x265 / x264) : meilleure qualité pour une taille donnée, mais beaucoup plus lent et le téléphone chauffe davantage."
            hwStatus == null -> "Test de l'encodeur matériel en cours…"
            !hwStatus.ok -> "L'encodeur matériel de ce téléphone n'a pas passé le contrôle de l'image (${hwStatus.detail}) : il est désactivé et l'encodage logiciel est utilisé à la place."
            else -> "Utilise l'encodeur matériel du téléphone (contrôle de l'image réussi). Rapide, mais à taille égale la qualité est un peu inférieure. Si le résultat est invalide, le fichier est refait automatiquement en logiciel."
        },
        warning = d.engine == EncoderEngine.AUTO && hwStatus != null && !hwStatus.ok,
    )
    SectionTitle("Résolution (petit côté de l'image, jamais d'agrandissement)")
    ChoiceChips(Resolution.entries, d.resolution, { it.label }) { set(d.copy(resolution = it)) }
    if (d.resolution == Resolution.CUSTOM) {
        NumberField("Petit côté (pixels)", d.customShortSide) { set(d.copy(customShortSide = it.coerceAtMost(4320))) }
    }
    SectionTitle("Qualité")
    ChoiceChips(Quality.entries, d.quality, { it.label }) { set(d.copy(quality = it)) }
    if (d.quality == Quality.CUSTOM) {
        Text("Facteur de qualité (CRF) : ${d.customCrf} — plus bas = meilleure qualité, fichier plus gros", style = MaterialTheme.typography.bodySmall)
        Slider(d.customCrf.toFloat(), { set(d.copy(customCrf = it.toInt())) }, valueRange = 16f..38f, steps = 21)
    }
    SectionTitle("Audio")
    LabeledSwitch("Conserver les pistes audio", d.keepAudio, { set(d.copy(keepAudio = it)) }, "L'audio AAC est recopié tel quel, sans perte.")
    InfoCard("La taille finale dépend du contenu : aucune taille fixe n'est garantie. Les métadonnées utiles (date, lieu) sont conservées lorsque possible.")
}

@Composable
private fun AviSettings(d: Profile, set: (Profile) -> Unit, vm: AppViewModel) {
    val codecs by vm.codecs.collectAsStateWithLifecycle()
    LaunchedEffect(d.aviCodec) { if (d.aviCodec != AviCodec.XVID) set(d.copy(aviCodec = AviCodec.XVID)) }
    SectionTitle("Codec vidéo")
    val st = codecs?.firstOrNull { it.id == "avi_xvid" }
    Text("Xvid / MPEG-4 (compatible AviMéca et AviStep)", color = if (st == null || st.ok) MaterialTheme.colorScheme.onSurface else MaterialTheme.colorScheme.error)
    Text(
        when { st == null -> "Test de disponibilité en cours…"; st.ok -> "Disponible (test réel réussi)."; else -> "Indisponible : ${st.detail}" },
        style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    SectionTitle("Qualité : ${d.aviQuality} (1 = meilleure, 31 = plus petite)")
    Slider(d.aviQuality.toFloat(), { set(d.copy(aviQuality = it.toInt())) }, valueRange = 1f..31f, steps = 29)
    SectionTitle("Dimensions")
    ChoiceChips(listOf(Resolution.ORIGINAL, Resolution.CUSTOM), d.aviResolution, { if (it == Resolution.ORIGINAL) "Originales" else "Personnalisées" }) {
        set(d.copy(aviResolution = it, aviWidth = if (it == Resolution.ORIGINAL) 0 else d.aviWidth.takeIf { w -> w > 0 } ?: 1280, aviHeight = if (it == Resolution.ORIGINAL) 0 else d.aviHeight.takeIf { h -> h > 0 } ?: 720))
    }
    if (d.aviResolution == Resolution.CUSTOM) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Box(Modifier.weight(1f)) { NumberField("Largeur", d.aviWidth) { set(d.copy(aviWidth = it.coerceAtMost(8192))) } }
            Box(Modifier.weight(1f)) { NumberField("Hauteur", d.aviHeight) { set(d.copy(aviHeight = it.coerceAtMost(8192))) } }
        }
        LabeledSwitch("Conserver les proportions", d.keepAspect, { set(d.copy(keepAspect = it)) }, "L'image tient dans la taille choisie, sans déformation.")
    }
    SectionTitle("Cadre de l'image")
    ChoiceChips(listOf(1, 0), d.aviFrame, { if (it == 1) "Paysage 16:9 (conseillé)" else "Taille de l'image" }) { set(d.copy(aviFrame = it)) }
    InfoCard(
        if (d.aviFrame == 0) "L'AVI a exactement les dimensions de l'image. AviMéca étire une vidéo portrait dans sa fenêtre large : préférez le cadre paysage."
        else "Des bandes noires sont ajoutées de chaque côté d'une vidéo portrait pour obtenir un cadre paysage. L'image n'est ni étirée ni réduite (même échelle en pixels) ; testé dans AviMéca : la balle reste ronde.",
    )
    SectionTitle("Fréquence d'images")
    ChoiceChips(listOf(false, true), d.aviFps > 0, { if (it) "Personnalisée" else "Originale (conseillé)" }) { set(d.copy(aviFps = if (it) 25.0 else 0.0)) }
    if (d.aviFps > 0) {
        OutlinedTextField(
            value = CommandBuilder.fmt(d.aviFps), onValueChange = { t -> t.replace(',', '.').toDoubleOrNull()?.let { set(d.copy(aviFps = it.coerceIn(1.0, 240.0))) } },
            label = { Text("Images par seconde") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal), singleLine = true,
        )
        InfoCard("Attention : changer la cadence duplique ou supprime des images. Les mesures de vitesse et d'accélération dans AviMéca / AviStep seront faussées. Gardez la cadence d'origine pour des vidéos scientifiques.", warning = true)
    } else InfoCard("La cadence d'origine est conservée à l'identique. Aucune image n'est interpolée, ajoutée ou retirée.")
    SectionTitle("Son")
    LabeledSwitch("Inclure le son dans l'AVI", d.aviKeepAudio, { set(d.copy(aviKeepAudio = it)) }, if (d.aviKeepAudio) "Le son est ajouté en PCM 16 bits non compressé." else "Désactivé : l'AVI ne contient que l'image (fichier plus léger, inutile pour AviMéca / AviStep).")
    InfoCard("Fichiers AVI de sortie : Films/VideoConverter/AVI. La compatibilité avec AviMéca et AviStep ne peut être validée que sur un ordinateur (voir « Tester un extrait » pour obtenir un fichier à essayer).")
}

@Composable
private fun NumberField(label: String, value: Int, onChange: (Int) -> Unit) {
    var text by remember(value) { mutableStateOf(value.toString()) }
    OutlinedTextField(
        text, { t -> text = t.filter { it.isDigit() }.take(5); text.toIntOrNull()?.let(onChange) },
        label = { Text(label) }, singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
        modifier = Modifier.fillMaxWidth(),
    )
}
