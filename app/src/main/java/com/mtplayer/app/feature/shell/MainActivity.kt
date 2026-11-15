package com.mtplayer.app.feature.shell

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.mtplayer.app.MTApp
import com.mtplayer.app.data.media.LibraryState
import com.mtplayer.app.designsystem.theme.MtTheme
import com.mtplayer.app.designsystem.theme.resolveDark
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private val container by lazy { (application as MTApp).container }

    /** Set when launched from the media notification; consumed by the UI. */
    var openPlayerRequest by mutableStateOf(0)
        private set

    override fun onCreate(savedInstanceState: Bundle?) {
        val splash = installSplashScreen()
        super.onCreate(savedInstanceState)
        // Keep the splash only until the first library state is known (bounded by MediaStore speed).
        splash.setKeepOnScreenCondition { container.library.state.value is LibraryState.Loading && !splashTimedOut }
        lifecycleScope.launch { kotlinx.coroutines.delay(1_200); splashTimedOut = true }
        lifecycleScope.launch { container.library.refresh() }
        container.player.connect()
        container.updates.maybeAutoCheck()
        handleIntent(intent, fromCreate = savedInstanceState == null)

        setContent {
            val vm: AppViewModel = viewModel(factory = viewModelFactory { initializer { AppViewModel(container) } })
            val settings by vm.settings.collectAsStateWithLifecycle()
            val dark = resolveDark(settings.themeMode, isSystemInDarkTheme())
            LaunchedEffect(dark) {
                val scrim = android.graphics.Color.TRANSPARENT
                enableEdgeToEdge(
                    statusBarStyle = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
                    navigationBarStyle = if (dark) SystemBarStyle.dark(scrim) else SystemBarStyle.light(scrim, scrim),
                )
            }
            MtTheme(mode = settings.themeMode, pureBlack = settings.pureBlack, dynamicColor = settings.dynamicColor) {
                AppRoot(container = container, vm = vm, openPlayerRequest = openPlayerRequest)
            }
        }
    }

    @Volatile private var splashTimedOut = false

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIntent(intent, fromCreate = false)
    }

    private fun handleIntent(intent: Intent?, fromCreate: Boolean) {
        when (intent?.action) {
            ACTION_OPEN_PLAYER -> openPlayerRequest++
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (fromCreate || intent.flags and Intent.FLAG_ACTIVITY_LAUNCHED_FROM_HISTORY == 0) {
                    container.player.playUri(uri, intent.type)
                    openPlayerRequest++
                }
            }
        }
    }

    companion object {
        const val ACTION_OPEN_PLAYER = "com.mtplayer.app.OPEN_PLAYER"
    }
}
