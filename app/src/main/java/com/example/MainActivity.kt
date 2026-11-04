package com.example

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.example.ui.screens.MTPlayerApp
import com.example.ui.theme.MTPlayerTheme
import com.example.ui.viewmodel.MainViewModel
import com.example.update.UpdateManager
import com.example.update.UpdateNotifier

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Background update check on app open (once per process; restores running downloads).
        UpdateManager.get(this).onAppStart(autoCheckAllowedForBuild = BuildConfig.AUTO_UPDATE_CHECK)
        handleUpdateIntent(intent)

        setContent {
            val viewModel: MainViewModel = viewModel()
            val themePref by viewModel.themePreference.collectAsStateWithLifecycle()

            val mode = themePref?.themeMode ?: "DARK"
            val accentHex = themePref?.accentColorHex ?: "#1DB954"
            val amoled = themePref?.pureBlackAmoled ?: false

            MTPlayerTheme(
                themeMode = mode,
                accentColorHex = accentHex,
                pureBlackAmoled = amoled
            ) {
                MTPlayerApp(viewModel = viewModel)
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
