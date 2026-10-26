package pl.pickpack.panel

import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import pl.pickpack.panel.presentation.baselinker.BaselinkerConfigScreen
import pl.pickpack.panel.presentation.completeness.CompletenessScreen
import pl.pickpack.panel.presentation.importqr.ImportQrScreen
import pl.pickpack.panel.presentation.main.MainScreen
import pl.pickpack.panel.presentation.settings.SettingsScreen
import pl.pickpack.panel.presentation.theme.PickPackTheme
import pl.pickpack.panel.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            PickPackTheme {
                val panel by viewModel.panel.collectAsStateWithLifecycle()
                LaunchedEffect(panel?.settings?.keepScreenOn) {
                    if (panel?.settings?.keepScreenOn != false) {
                        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    } else {
                        window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
                    }
                }

                val nav = rememberNavController()
                NavHost(navController = nav, startDestination = "main") {
                    composable("main") {
                        MainScreen(
                            viewModel = viewModel,
                            onOpenSettings = { nav.navigate("settings") },
                        )
                    }
                    composable("settings") {
                        SettingsScreen(
                            viewModel = viewModel,
                            onBack = { nav.popBackStack() },
                            onOpenImport = { nav.navigate("import") },
                            onOpenCompleteness = { nav.navigate("completeness") },
                            onOpenBaselinker = { nav.navigate("baselinker") },
                        )
                    }
                    composable("import") {
                        ImportQrScreen(
                            viewModel = viewModel,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("completeness") {
                        CompletenessScreen(
                            viewModel = viewModel,
                            onBack = { nav.popBackStack() },
                        )
                    }
                    composable("baselinker") {
                        BaselinkerConfigScreen(
                            viewModel = viewModel,
                            onBack = { nav.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
