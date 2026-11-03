package fr.videoconverter.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import java.util.Locale

private val Teal = Color(0xFF19C2B0)
private val Orange = Color(0xFFFF9F1C)
private val Navy = Color(0xFF0F1B2D)

private val Dark = darkColorScheme(
    primary = Teal, onPrimary = Navy, secondary = Orange, onSecondary = Navy, tertiary = Color(0xFF7AA2F7),
    background = Navy, onBackground = Color(0xFFE6EDF7), surface = Color(0xFF16253B), onSurface = Color(0xFFE6EDF7),
    surfaceVariant = Color(0xFF1F3250), onSurfaceVariant = Color(0xFFB5C3D8), error = Color(0xFFFF6B6B),
    primaryContainer = Color(0xFF0E5A55), onPrimaryContainer = Color(0xFFD5FBF6),
    secondaryContainer = Color(0xFF5A3A08), onSecondaryContainer = Color(0xFFFFE6C2),
)
private val Light = lightColorScheme(
    primary = Color(0xFF00897B), onPrimary = Color.White, secondary = Color(0xFFD97C00), onSecondary = Color.White,
    background = Color(0xFFF5F8FC), onBackground = Navy, surface = Color.White, onSurface = Navy,
    surfaceVariant = Color(0xFFE3EAF4), onSurfaceVariant = Color(0xFF44546A),
    primaryContainer = Color(0xFFC9F3EE), onPrimaryContainer = Color(0xFF00352F),
    secondaryContainer = Color(0xFFFFE6C2), onSecondaryContainer = Color(0xFF4A2D00),
)

@Composable
fun VCTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}

fun formatSize(b: Long): String = when {
    b >= 1_000_000_000L -> String.format(Locale.FRANCE, "%.2f Go", b / 1e9)
    b >= 1_000_000L -> String.format(Locale.FRANCE, "%.0f Mo", b / 1e6)
    b >= 1_000L -> String.format(Locale.FRANCE, "%.0f Ko", b / 1e3)
    else -> "$b o"
}

fun formatDuration(ms: Long): String {
    val s = ms / 1000
    val h = s / 3600; val m = (s % 3600) / 60; val sec = s % 60
    return if (h > 0) String.format(Locale.ROOT, "%d:%02d:%02d", h, m, sec) else String.format(Locale.ROOT, "%d:%02d", m, sec)
}

fun formatFps(f: Double): String = if (f <= 0) "?" else String.format(Locale.FRANCE, "%.3f", f).trimEnd('0').trimEnd(',')

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, color = MaterialTheme.colorScheme.primary, modifier = Modifier.padding(top = 16.dp, bottom = 6.dp))
}

@Composable
fun InfoCard(text: String, warning: Boolean = false, modifier: Modifier = Modifier) {
    Surface(
        shape = RoundedCornerShape(12.dp), modifier = modifier.fillMaxWidth().padding(vertical = 4.dp),
        color = if (warning) MaterialTheme.colorScheme.secondaryContainer else MaterialTheme.colorScheme.surfaceVariant,
    ) {
        Text(text, modifier = Modifier.padding(12.dp), style = MaterialTheme.typography.bodySmall,
            color = if (warning) MaterialTheme.colorScheme.onSecondaryContainer else MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
fun LabeledSwitch(label: String, checked: Boolean, onChange: (Boolean) -> Unit, hint: String? = null) {
    Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(label)
            if (hint != null) Text(hint, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked, onChange)
    }
}

@Composable
fun <T> ChoiceChips(options: List<T>, selected: T, label: (T) -> String, onSelect: (T) -> Unit) {
    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.spacedBy(0.dp)) {
        options.forEach { o -> FilterChip(selected = o == selected, onClick = { onSelect(o) }, label = { Text(label(o)) }) }
    }
}
