package fr.videoconverter

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import fr.videoconverter.ui.*

class MainActivity : ComponentActivity() {
    private val vm: AppViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            VCTheme {
                val nav = rememberNavController()
                val notifPerm = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
                LaunchedEffect(Unit) {
                    if (Build.VERSION.SDK_INT >= 33) notifPerm.launch(Manifest.permission.POST_NOTIFICATIONS)
                    if (vm.codecs.value == null) vm.runCodecCheck()
                }
                val go: (String) -> Unit = { nav.navigate(it) { launchSingleTop = true } }
                val back: () -> Unit = { nav.popBackStack() }
                NavHost(nav, startDestination = "home") {
                    composable("home") { HomeScreen(vm, go) }
                    composable("select") { SelectScreen(vm, go, back) }
                    composable("config") { ConfigScreen(vm, go, back) }
                    composable("test") { TestScreen(vm, go, back) }
                    composable("queue") { QueueScreen(vm, go, back) }
                    composable("history") { HistoryScreen(vm, go, back) }
                    composable("player/{id}") { e ->
                        // Le balayage remplace la vidéo affichée (sans empiler les écrans) : « Retour » revient à la liste.
                        val swap: (String) -> Unit = { nav.navigate("player/$it") { popUpTo("player/{id}") { inclusive = true } } }
                        PlayerScreen(vm, e.arguments?.getString("id") ?: "", swap, back)
                    }
                    composable("settings") { SettingsScreen(vm, back) }
                }
            }
        }
    }
}
