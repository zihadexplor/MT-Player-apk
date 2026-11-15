package com.example

import android.content.Intent
import android.media.AudioManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MTPlayerApp
import com.example.ui.theme.DEFAULT_ACCENT_HEX
import com.example.ui.theme.MTPlayerTheme
import com.example.ui.theme.resolveIsDarkTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.update.UpdateManager
import com.example.update.UpdateNotifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        volumeControlStream = AudioManager.STREAM_MUSIC

        // Background update check on app open (once per process; restores running downloads).
        UpdateManager.get(this).onAppStart(autoCheckAllowedForBuild = BuildConfig.AUTO_UPDATE_CHECK)
        handleUpdateIntent(intent)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themePref by viewModel.themePreference.collectAsStateWithLifecycle()
            val useDynamicArtworkColor by viewModel.dynamicArtworkColor.collectAsStateWithLifecycle()
            val artworkSeedColor by viewModel.dynamicArtworkSeedColor.collectAsStateWithLifecycle()

            val mode = themePref?.themeMode ?: "DARK"
            val accentHex = themePref?.accentColorHex ?: DEFAULT_ACCENT_HEX
            val amoled = themePref?.pureBlackAmoled ?: false
            val isSystemDark = isSystemInDarkTheme()
            val isDark = resolveIsDarkTheme(mode, isSystemDark)

            val view = LocalView.current
            if (!view.isInEditMode) {
                SideEffect {
                    val controller = WindowCompat.getInsetsController(window, view)
                    controller.isAppearanceLightStatusBars = !isDark
                    controller.isAppearanceLightNavigationBars = !isDark
                }
            }

            MTPlayerTheme(
                themeMode = mode,
                accentColorHex = accentHex,
                pureBlackAmoled = amoled,
                dynamicSeedColor = if (useDynamicArtworkColor) artworkSeedColor else null
            ) {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background
                ) {
                    MTPlayerApp(viewModel = viewModel)
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleUpdateIntent(intent)
    }

    /** Opened from the "update ready" notification: bring the update dialog back. */
    private fun handleUpdateIntent(intent: Intent?) {
        if (intent?.action == UpdateNotifier.ACTION_OPEN_UPDATE) {
            UpdateManager.get(this).showDialog()
        }
    }
}
