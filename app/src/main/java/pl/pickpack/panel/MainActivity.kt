package pl.pickpack.panel

import android.graphics.Color
import android.media.AudioManager
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.viewModels
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
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
import pl.pickpack.panel.presentation.theme.WarehouseNavy
import pl.pickpack.panel.viewmodel.MainViewModel

class MainActivity : ComponentActivity() {
    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        @Suppress("DEPRECATION")
        volumeControlStream = AudioManager.STREAM_MUSIC

        // True fullscreen landscape: draw under system bars; hide them (sticky immersive).
        // Do NOT apply horizontal safeDrawingPadding — that caused a white letterbox strip.
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(Color.TRANSPARENT),
        )
        WindowCompat.setDecorFitsSystemWindows(window, false)
        window.setBackgroundDrawableResource(R.color.warehouse_navy)
        applyImmersiveMode()

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

                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(WarehouseNavy),
                ) {
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) applyImmersiveMode()
    }

    /** Sticky immersive: hide status + nav; swipe shows them transiently. */
    private fun applyImmersiveMode() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        val controller = WindowInsetsControllerCompat(window, window.decorView)
        controller.isAppearanceLightStatusBars = false
        controller.isAppearanceLightNavigationBars = false
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }
}
