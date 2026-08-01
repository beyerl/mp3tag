package de.lb.mp3tag

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.Composable
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import de.lb.mp3tag.di.AppContainer
import de.lb.mp3tag.ui.browser.BrowserScreen
import de.lb.mp3tag.ui.filelist.FileListScreen
import de.lb.mp3tag.ui.session.SessionViewModel
import de.lb.mp3tag.ui.settings.SettingsScreen
import de.lb.mp3tag.ui.theme.Mp3tagTheme

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val container = (application as App).container
        setContent {
            Mp3tagTheme {
                Mp3tagApp(container)
            }
        }
    }
}

@Composable
private fun Mp3tagApp(container: AppContainer) {
    val navController = rememberNavController()
    val sessionViewModel: SessionViewModel = viewModel(factory = container.viewModelFactory)

    NavHost(navController = navController, startDestination = "browser") {
        composable("browser") {
            BrowserScreen(
                storage = container.storage,
                settings = container.settings,
                onOpenDirectory = { dir, recursive ->
                    sessionViewModel.openDirectory(dir, recursive)
                    navController.navigate("files")
                },
                onOpenSettings = { navController.navigate("settings") },
            )
        }
        composable("settings") {
            SettingsScreen(
                settings = container.settings,
                onBack = { navController.popBackStack() },
            )
        }
        composable("files") {
            FileListScreen(
                viewModel = sessionViewModel,
                settings = container.settings,
                onBack = { navController.popBackStack() },
            )
        }
    }
}
