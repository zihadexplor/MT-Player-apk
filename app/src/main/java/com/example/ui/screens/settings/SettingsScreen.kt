package com.example.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.FolderOff
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.PrivacyTip
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.TouchApp
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.ui.theme.AccentColors
import com.example.ui.theme.parseAccentColor
import com.example.ui.viewmodel.MainViewModel
import com.example.update.ui.UpdateSettingsCard

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    viewModel: MainViewModel,
    onBackClick: () -> Unit,
    onRescanClick: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    // Theme & Personalization from Room
    val themePref by viewModel.themePreference.collectAsStateWithLifecycle()
    val activeThemeMode = themePref?.themeMode ?: "DARK"
    val activeAccentHex = themePref?.accentColorHex ?: "#1DB954"
    val activeAmoled = themePref?.pureBlackAmoled ?: false

    // Sleep Timer from ViewModel / Room
    val sleepTimerPref by viewModel.sleepTimerPreference.collectAsStateWithLifecycle()
    val sleepTimerSeconds by viewModel.sleepTimerRemainingSeconds.collectAsStateWithLifecycle()
    var selectedSleepMinutes by remember(sleepTimerPref) {
        mutableIntStateOf(sleepTimerPref?.defaultDurationMinutes ?: 30)
    }
    var finishLastSong by remember(sleepTimerPref) {
        mutableStateOf(sleepTimerPref?.finishLastSong ?: false)
    }
    var fadeOutAudio by remember(sleepTimerPref) {
        mutableStateOf(sleepTimerPref?.fadeOutEnabled ?: true)
    }

    // Playback preferences
    val gapless by viewModel.gaplessPlayback.collectAsStateWithLifecycle()
    val autoPauseHeadset by viewModel.autoPauseOnHeadset.collectAsStateWithLifecycle()
    val crossfade by viewModel.crossfadeSeconds.collectAsStateWithLifecycle()

    // Video preferences
    val hwAccel by viewModel.hardwareAcceleration.collectAsStateWithLifecycle()
    val resumeVid by viewModel.resumeVideo.collectAsStateWithLifecycle()
    val defaultSpeed by viewModel.defaultVideoSpeed.collectAsStateWithLifecycle()

    // Gesture preferences
    val doubleTapSeek by viewModel.doubleTapSeekSeconds.collectAsStateWithLifecycle()
    val gestureBrightness by viewModel.gestureBrightness.collectAsStateWithLifecycle()
    val gestureVolume by viewModel.gestureVolume.collectAsStateWithLifecycle()
    val gestureScrub by viewModel.gestureScrub.collectAsStateWithLifecycle()

    // Media scan & exclusions
    val minAudioDur by viewModel.minAudioDurationSeconds.collectAsStateWithLifecycle()
    val autoScan by viewModel.autoScanOnLaunch.collectAsStateWithLifecycle()
    val hiddenFolders by viewModel.hiddenVideoFolderPaths.collectAsStateWithLifecycle()

    // Equalizer state
    val eqState by viewModel.equalizerState.collectAsStateWithLifecycle()

    // Dialog state
    var showClearListeningDialog by remember { mutableStateOf(false) }
    var showClearWatchingDialog by remember { mutableStateOf(false) }
    var showAboutDialog by remember { mutableStateOf(false) }
    var showPrivacyDialog by remember { mutableStateOf(false) }
    var showHiddenFoldersDialog by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary
    val surfaceColor = MaterialTheme.colorScheme.surface
    val containerHigh = MaterialTheme.colorScheme.surfaceContainerHigh
    val outlineColor = MaterialTheme.colorScheme.outline
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Scaffold(
        modifier = modifier
            .fillMaxSize()
            .testTag("settings_screen_root"),
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        text = "Settings",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = onSurface
                    )
                },
                navigationIcon = {
                    IconButton(
                        onClick = onBackClick,
                        modifier = Modifier
                            .minimumInteractiveComponentSize()
                            .testTag("settings_back_button")
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            tint = onSurface
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = surfaceColor,
                    titleContentColor = onSurface
                )
            )
        },
        containerColor = MaterialTheme.colorScheme.background
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // -----------------------------------------------------------------
            // 1, 2, 3, 4. Theme & Appearance (Dark, AMOLED Black, Light, Accents)
            // -----------------------------------------------------------------
            item(key = "section_theme") {
                SettingsSectionHeader(title = "Theme & Appearance", icon = Icons.Default.Palette)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerHigh),
                    border = BorderStroke(0.5.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Text(
                            text = "Theme Mode",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface
                        )
                        Spacer(modifier = Modifier.height(8.dp))

                        val themeChoices = listOf(
                            Triple("DARK", "Dark Theme", "Classic Spotify-inspired dark layout"),
                            Triple("AMOLED", "AMOLED Black Theme", "True pure black #000000 for OLED energy savings"),
                            Triple("LIGHT", "Light Theme", "Crisp, bright surface with clean contrast"),
                            Triple("SYSTEM", "Follow System", "Adapts automatically to Android system mode")
                        )

                        themeChoices.forEachIndexed { index, (modeKey, title, subtitle) ->
                            val isSelected = activeThemeMode == modeKey
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(8.dp))
                                    .clickable {
                                        val isAmoled = modeKey == "AMOLED"
                                        viewModel.saveThemePreference(
                                            themeMode = modeKey,
                                            accentColorHex = activeAccentHex,
                                            pureBlackAmoled = isAmoled
                                        )
                                    }
                                    .padding(vertical = 8.dp, horizontal = 4.dp)
                                    .testTag("theme_option_$modeKey"),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                        color = if (isSelected) primaryColor else onSurface
                                    )
                                    Text(
                                        text = subtitle,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = onSurfaceVariant,
                                        fontSize = 12.sp
                                    )
                                }
                                RadioButton(
                                    selected = isSelected,
                                    onClick = {
                                        val isAmoled = modeKey == "AMOLED"
                                        viewModel.saveThemePreference(
                                            themeMode = modeKey,
                                            accentColorHex = activeAccentHex,
                                            pureBlackAmoled = isAmoled
                                        )
                                    },
                                    colors = RadioButtonDefaults.colors(
                                        selectedColor = primaryColor,
                                        unselectedColor = onSurfaceVariant
                                    )
                                )
                            }
                            if (index < themeChoices.size - 1) {
                                SettingsDivider()
                            }
                        }

                        Spacer(modifier = Modifier.height(12.dp))
                        SettingsDivider()
                        Spacer(modifier = Modifier.height(6.dp))

                        // Accent Color Customization
                        Text(
                            text = "Accent Color",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = onSurface
                        )
                        Spacer(modifier = Modifier.height(4.dp))
                        Text(
                            text = "Choose your personalized highlight and control tone",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                        Spacer(modifier = Modifier.height(10.dp))

                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            AccentColors.forEach { opt ->
                                val isChosen = activeAccentHex.equals(opt.hex, ignoreCase = true)
                                Box(
                                    modifier = Modifier
                                        .size(42.dp)
                                        .clip(CircleShape)
                                        .background(opt.color)
                                        .border(
                                            width = if (isChosen) 2.5.dp else 1.dp,
                                            color = if (isChosen) onSurface else Color.Transparent,
                                            shape = CircleShape
                                        )
                                        .clickable {
                                            viewModel.saveThemePreference(
                                                themeMode = activeThemeMode,
                                                accentColorHex = opt.hex,
                                                pureBlackAmoled = activeAmoled
                                            )
                                        }
                                        .testTag("accent_${opt.id}"),
                                    contentAlignment = Alignment.Center
                                ) {
                                    if (isChosen) {
                                        Icon(
                                            imageVector = Icons.Default.Check,
                                            contentDescription = "Selected",
                                            tint = if (opt.hex == "#FFB300" || opt.hex == "#00E676") Color.Black else Color.White,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 5, 8. Music Playback & Audio Effects Preferences
            // -----------------------------------------------------------------
            item(key = "section_audio") {
                SettingsSectionHeader(title = "Music Playback & Sound FX", icon = Icons.Default.MusicNote)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerHigh),
                    border = BorderStroke(0.5.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Equalizer Navigator
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onNavigateToEqualizer() }
                                .padding(vertical = 8.dp)
                                .testTag("setting_equalizer_selector"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Advanced Audio Equalizer",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = if (eqState.isEnabled) "Status: Enabled (${eqState.currentPreset})" else "Status: Disabled",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = primaryColor
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Open Equalizer",
                                tint = primaryColor,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Gapless Playback",
                            subtitle = "Eliminate silent transitions between album songs",
                            checked = gapless,
                            onCheckedChange = { viewModel.gaplessPlayback.value = it },
                            testTag = "toggle_gapless"
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Auto-Pause on Disconnect",
                            subtitle = "Pause music when wired or Bluetooth headphones unplug",
                            checked = autoPauseHeadset,
                            onCheckedChange = { viewModel.autoPauseOnHeadset.value = it },
                            testTag = "toggle_auto_pause_headset"
                        )

                        SettingsDivider()

                        // Crossfade Duration Selector
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Crossfade Duration",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = if (crossfade == 0) "Immediate transition" else "$crossfade seconds crossfade",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0, 3, 5, 10).forEach { sec ->
                                    val isSelected = crossfade == sec
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clickable { viewModel.crossfadeSeconds.value = sec }
                                            .testTag("crossfade_${sec}s")
                                    ) {
                                        Text(
                                            text = if (sec == 0) "Off" else "${sec}s",
                                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                            color = if (isSelected) Color.Black else onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 6, 7, 9. Video Playback & Gesture Control Settings
            // -----------------------------------------------------------------
            item(key = "section_video") {
                SettingsSectionHeader(title = "Video Playback & Gestures", icon = Icons.Default.Videocam)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerHigh),
                    border = BorderStroke(0.5.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SettingsToggleRow(
                            title = "Hardware Acceleration",
                            subtitle = "Use AndroidX Media3 hardware video decoding",
                            checked = hwAccel,
                            onCheckedChange = { viewModel.hardwareAcceleration.value = it },
                            testTag = "toggle_hw_accel"
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Resume Video Playback",
                            subtitle = "Continue playing from where you last left off",
                            checked = resumeVid,
                            onCheckedChange = { viewModel.resumeVideo.value = it },
                            testTag = "toggle_resume_playback"
                        )

                        SettingsDivider()

                        // Default Playback Speed
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Default Video Speed",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = "Current: ${defaultSpeed}x",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                                listOf(0.75f, 1.0f, 1.25f, 1.5f).forEach { spd ->
                                    val isSelected = defaultSpeed == spd
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clickable { viewModel.defaultVideoSpeed.value = spd }
                                            .testTag("speed_${spd}x")
                                    ) {
                                        Text(
                                            text = "${spd}x",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            color = if (isSelected) Color.Black else onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        SettingsDivider()

                        // Double Tap Seek Duration
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Double Tap Seek",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = "Seek forward/backward on sides",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(5, 10, 15, 30).forEach { sec ->
                                    val isSelected = doubleTapSeek == sec
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clickable { viewModel.doubleTapSeekSeconds.value = sec }
                                            .testTag("seek_${sec}s")
                                    ) {
                                        Text(
                                            text = "${sec}s",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            color = if (isSelected) Color.Black else onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 12.sp
                                        )
                                    }
                                }
                            }
                        }

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Brightness Swipe (Left)",
                            subtitle = "Vertical swipe on left half to adjust screen brightness",
                            checked = gestureBrightness,
                            onCheckedChange = { viewModel.gestureBrightness.value = it },
                            testTag = "toggle_gesture_brightness"
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Volume Swipe (Right)",
                            subtitle = "Vertical swipe on right half to adjust media volume",
                            checked = gestureVolume,
                            onCheckedChange = { viewModel.gestureVolume.value = it },
                            testTag = "toggle_gesture_volume"
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Horizontal Scrub Seek",
                            subtitle = "Swipe horizontally anywhere to fast-scrub video timeline",
                            checked = gestureScrub,
                            onCheckedChange = { viewModel.gestureScrub.value = it },
                            testTag = "toggle_gesture_scrub"
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // 10. Sleep Timer with Configurable Duration & Countdown
            // -----------------------------------------------------------------
            item(key = "section_sleep_timer") {
                SettingsSectionHeader(title = "Sleep Timer", icon = Icons.Default.Timer)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerHigh),
                    border = BorderStroke(0.5.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        val isRunning = sleepTimerSeconds != null
                        if (isRunning) {
                            val secs = sleepTimerSeconds ?: 0
                            val m = secs / 60
                            val s = secs % 60
                            Surface(
                                shape = RoundedCornerShape(10.dp),
                                color = primaryColor.copy(alpha = 0.15f),
                                border = BorderStroke(1.dp, primaryColor),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(12.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column {
                                        Text(
                                            text = "Timer Active",
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.Bold,
                                            color = primaryColor
                                        )
                                        Text(
                                            text = String.format("%02d:%02d remaining until pause", m, s),
                                            style = MaterialTheme.typography.bodySmall,
                                            color = onSurface
                                        )
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.stopSleepTimer() },
                                        colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor),
                                        modifier = Modifier.testTag("cancel_sleep_timer_btn")
                                    ) {
                                        Text("Cancel")
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(12.dp))
                        } else {
                            Text(
                                text = "Select timer duration to automatically pause playback",
                                style = MaterialTheme.typography.bodySmall,
                                color = onSurfaceVariant
                            )
                            Spacer(modifier = Modifier.height(10.dp))
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                listOf(15, 30, 45, 60, 90).forEach { mins ->
                                    val isSelected = selectedSleepMinutes == mins
                                    Surface(
                                        shape = RoundedCornerShape(10.dp),
                                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable { selectedSleepMinutes = mins }
                                            .testTag("sleep_timer_${mins}m")
                                    ) {
                                        Box(
                                            modifier = Modifier.padding(vertical = 8.dp),
                                            contentAlignment = Alignment.Center
                                        ) {
                                            Text(
                                                text = "${mins}m",
                                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
                                                color = if (isSelected) Color.Black else onSurface,
                                                fontSize = 12.sp
                                            )
                                        }
                                    }
                                }
                            }
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = {
                                    viewModel.startSleepTimer(
                                        minutes = selectedSleepMinutes,
                                        finishLastSong = finishLastSong,
                                        fadeOutAudio = fadeOutAudio
                                    )
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("start_sleep_timer_btn"),
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = primaryColor)
                            ) {
                                Text("Start $selectedSleepMinutes-Minute Sleep Timer")
                            }
                        }

                        Spacer(modifier = Modifier.height(10.dp))
                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Finish Current Song",
                            subtitle = "Wait for active audio track to end before pausing",
                            checked = finishLastSong,
                            onCheckedChange = {
                                finishLastSong = it
                                viewModel.saveSleepTimerPreference(selectedSleepMinutes, it, fadeOutAudio)
                            },
                            testTag = "toggle_sleep_finish_song"
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Fade Out Audio",
                            subtitle = "Smooth volume attenuation over the final 30 seconds",
                            checked = fadeOutAudio,
                            onCheckedChange = {
                                fadeOutAudio = it
                                viewModel.saveSleepTimerPreference(selectedSleepMinutes, finishLastSong, it)
                            },
                            testTag = "toggle_sleep_fade_out"
                        )
                    }
                }
            }

            // -----------------------------------------------------------------
            // 11, 12. Storage, Folder Exclusion & Media Scanner
            // -----------------------------------------------------------------
            item(key = "section_scanner") {
                SettingsSectionHeader(title = "Storage & Media Scanning", icon = Icons.Default.Folder)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerHigh),
                    border = BorderStroke(0.5.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Excluded folders management
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showHiddenFoldersDialog = true }
                                .padding(vertical = 8.dp)
                                .testTag("setting_excluded_folders"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Excluded / Hidden Folders",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = "${hiddenFolders.size} folder(s) currently hidden from video library",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.FolderOff,
                                contentDescription = "Manage Excluded",
                                tint = primaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        SettingsDivider()

                        // Minimum Audio Duration Filter
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(vertical = 6.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Exclude Short Audio Clips",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = if (minAudioDur == 0) "All files" else "Hide audio under ${minAudioDur}s (ringtones/SFX)",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                listOf(0, 15, 30, 60).forEach { dur ->
                                    val isSelected = minAudioDur == dur
                                    Surface(
                                        shape = RoundedCornerShape(8.dp),
                                        color = if (isSelected) primaryColor else MaterialTheme.colorScheme.surfaceVariant,
                                        modifier = Modifier
                                            .clickable { viewModel.minAudioDurationSeconds.value = dur }
                                            .testTag("filter_${dur}s")
                                    ) {
                                        Text(
                                            text = if (dur == 0) "None" else "${dur}s",
                                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                            color = if (isSelected) Color.Black else onSurface,
                                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                            fontSize = 11.sp
                                        )
                                    }
                                }
                            }
                        }

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Scan Media on Launch",
                            subtitle = "Automatically re-index media library when opening Melody Player",
                            checked = autoScan,
                            onCheckedChange = { viewModel.autoScanOnLaunch.value = it },
                            testTag = "toggle_auto_scan"
                        )

                        SettingsDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { onRescanClick() }
                                .padding(vertical = 8.dp)
                                .testTag("setting_manual_rescan"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Force Media Rescan",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = "Immediately query Android MediaStore for new audio and videos",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan",
                                tint = primaryColor,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // 13, 14. Clear Listening & Watching History (Room Database)
            // -----------------------------------------------------------------
            item(key = "section_history") {
                SettingsSectionHeader(title = "History & Data Management", icon = Icons.Default.DeleteSweep)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerHigh),
                    border = BorderStroke(0.5.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showClearListeningDialog = true }
                                .padding(vertical = 8.dp)
                                .testTag("btn_clear_listening_history"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clear Music Listening History",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = "Reset Recently Played and Most Played local records",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Music History",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(22.dp)
                            )
                        }

                        SettingsDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showClearWatchingDialog = true }
                                .padding(vertical = 8.dp)
                                .testTag("btn_clear_watching_history"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = "Clear Video Watching History",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = "Reset Recently Watched list and saved playback bookmarks",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.DeleteSweep,
                                contentDescription = "Clear Video History",
                                tint = Color(0xFFEF5350),
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // -----------------------------------------------------------------
            // App Updates
            // -----------------------------------------------------------------
            item(key = "section_updates") {
                SettingsSectionHeader(title = "App Updates", icon = Icons.Default.SystemUpdate)
                Spacer(modifier = Modifier.height(8.dp))
                UpdateSettingsCard()
            }

            // -----------------------------------------------------------------
            // 15, 16. App Information, About & Privacy Pages
            // -----------------------------------------------------------------
            item(key = "section_about") {
                SettingsSectionHeader(title = "About & Privacy", icon = Icons.Default.Info)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = containerHigh),
                    border = BorderStroke(0.5.dp, outlineColor)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(46.dp)
                                    .clip(CircleShape)
                                    .background(primaryColor),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "MT",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 16.sp,
                                    color = Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(14.dp))
                            Column {
                                Text(
                                    text = "MT Player",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = onSurface
                                )
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME} (Build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                                Text(
                                    text = "Created & Developed by TOWFIK ESTIAK ZIHAD",
                                    style = MaterialTheme.typography.bodySmall,
                                    fontWeight = FontWeight.Medium,
                                    color = primaryColor
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))
                        SettingsDivider()
                        Spacer(modifier = Modifier.height(6.dp))

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showAboutDialog = true }
                                .padding(vertical = 8.dp)
                                .testTag("btn_about_app"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "About MT Player",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = "Creator, architecture, tech stack & engine details",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurfaceVariant
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Info,
                                contentDescription = "About",
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }

                        SettingsDivider()

                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clip(RoundedCornerShape(8.dp))
                                .clickable { showPrivacyDialog = true }
                                .padding(vertical = 8.dp)
                                .testTag("btn_privacy_page"),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column {
                                Text(
                                    text = "Privacy & Security Policy",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = onSurface
                                )
                                Text(
                                    text = "100% Offline, Zero Tracking, Zero Ads",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = primaryColor
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.PrivacyTip,
                                contentDescription = "Privacy",
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                }
            }
        }
    }

    // -------------------------------------------------------------------------
    // Dialogs: Clear Listening History
    // -------------------------------------------------------------------------
    if (showClearListeningDialog) {
        AlertDialog(
            onDismissRequest = { showClearListeningDialog = false },
            title = { Text("Clear Listening History?", color = onSurface) },
            text = {
                Text(
                    "This will remove all recorded play counts and recently played track entries from the local database. Songs on your device storage will not be affected.",
                    color = onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearListeningHistory()
                        showClearListeningDialog = false
                    }
                ) {
                    Text("Clear", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearListeningDialog = false }) {
                    Text("Cancel", color = onSurface)
                }
            },
            containerColor = surfaceColor
        )
    }

    // -------------------------------------------------------------------------
    // Dialogs: Clear Video Watching History
    // -------------------------------------------------------------------------
    if (showClearWatchingDialog) {
        AlertDialog(
            onDismissRequest = { showClearWatchingDialog = false },
            title = { Text("Clear Watching History?", color = onSurface) },
            text = {
                Text(
                    "This will erase video watch counts, continue-watching timestamps, and saved playback positions. Your video files will remain intact.",
                    color = onSurfaceVariant
                )
            },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.clearWatchingHistory()
                        showClearWatchingDialog = false
                    }
                ) {
                    Text("Clear", color = Color(0xFFEF5350), fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearWatchingDialog = false }) {
                    Text("Cancel", color = onSurface)
                }
            },
            containerColor = surfaceColor
        )
    }

    // -------------------------------------------------------------------------
    // Dialogs: Excluded / Hidden Folders Manager
    // -------------------------------------------------------------------------
    if (showHiddenFoldersDialog) {
        AlertDialog(
            onDismissRequest = { showHiddenFoldersDialog = false },
            title = { Text("Excluded Folders", color = onSurface) },
            text = {
                if (hiddenFolders.isEmpty()) {
                    Text("No folders are currently hidden from your library.", color = onSurfaceVariant)
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text(
                            text = "Videos located in these directories are hidden from view:",
                            style = MaterialTheme.typography.bodySmall,
                            color = onSurfaceVariant
                        )
                        hiddenFolders.forEach { folder ->
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(6.dp))
                                    .background(containerHigh)
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = folder,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = onSurface,
                                    modifier = Modifier.weight(1f)
                                )
                                TextButton(
                                    onClick = { viewModel.unhideVideoFolder(folder) }
                                ) {
                                    Text("Unhide", color = primaryColor, fontSize = 12.sp)
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                if (hiddenFolders.isNotEmpty()) {
                    TextButton(
                        onClick = {
                            viewModel.unhideAllVideoFolders()
                            showHiddenFoldersDialog = false
                        }
                    ) {
                        Text("Unhide All", color = primaryColor, fontWeight = FontWeight.Bold)
                    }
                } else {
                    TextButton(onClick = { showHiddenFoldersDialog = false }) {
                        Text("Close", color = primaryColor)
                    }
                }
            },
            dismissButton = {
                if (hiddenFolders.isNotEmpty()) {
                    TextButton(onClick = { showHiddenFoldersDialog = false }) {
                        Text("Close", color = onSurface)
                    }
                }
            },
            containerColor = surfaceColor
        )
    }

    // -------------------------------------------------------------------------
    // Dialogs: About MT Player
    // -------------------------------------------------------------------------
    if (showAboutDialog) {
        AlertDialog(
            onDismissRequest = { showAboutDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(primaryColor),
                        contentAlignment = Alignment.Center
                    ) {
                        Text("MT", fontWeight = FontWeight.Bold, fontSize = 11.sp, color = Color.Black)
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("About MT Player", color = onSurface)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "MT Player is a high-performance, 100% offline audio and video media engine engineered specifically for Android devices.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurface,
                        lineHeight = 20.sp
                    )
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = primaryColor.copy(alpha = 0.12f),
                        border = BorderStroke(1.dp, primaryColor)
                    ) {
                        Column(modifier = Modifier.padding(10.dp)) {
                            Text(
                                text = "Creator & Lead Developer",
                                style = MaterialTheme.typography.labelSmall,
                                color = primaryColor,
                                fontWeight = FontWeight.Bold
                            )
                            Text(
                                text = "TOWFIK ESTIAK ZIHAD",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Black,
                                color = onSurface
                            )
                        }
                    }
                    Text(
                        text = "• Core Engine: AndroidX Media3 (ExoPlayer 1.5.1)\n• Database: Android Room SQLite (12 Persistent Entities)\n• Audio Effects: Android AudioEffect Framework (5-Band Equalizer, Bass Boost, 3D Virtualizer)\n• Video Player: MX Player-style gestures (brightness, volume, scrub seeking, auto-PiP)\n• UI Architecture: 100% Jetpack Compose with Material Design 3\n• Media Source: Android MediaStore API (Direct on-device storage)",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                    Text(
                        text = "Engineered with zero advertisements, zero tracking telemetry, and 100% offline data integrity.",
                        style = MaterialTheme.typography.bodySmall,
                        color = primaryColor,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showAboutDialog = false }) {
                    Text("Done", color = primaryColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = surfaceColor
        )
    }

    // -------------------------------------------------------------------------
    // Dialogs: Privacy & Security Guarantee
    // -------------------------------------------------------------------------
    if (showPrivacyDialog) {
        AlertDialog(
            onDismissRequest = { showPrivacyDialog = false },
            title = {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.PrivacyTip,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(24.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text("Privacy Guarantee", color = onSurface)
                }
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Your privacy is paramount. Melody Player is architected with strict zero-knowledge principles:",
                        style = MaterialTheme.typography.bodyMedium,
                        color = onSurface,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        text = "1. 100% Local Playback: All songs and videos are loaded directly from your device storage. We never upload, stream, or index your private content remotely.\n\n2. Zero Data Telemetry: There are NO tracking beacons, analytics SDKs, session loggers, or advertisement frameworks in this app.\n\n3. Local Database: All playlists, favorites, listening stats, and equalizer curves are stored strictly in an on-device SQLite database (melody_player_local.db) in your private application sandbox.\n\n4. Zero Cloud Sync: We will never require login, sign-up, or email registration.",
                        style = MaterialTheme.typography.bodySmall,
                        color = onSurfaceVariant,
                        lineHeight = 18.sp
                    )
                }
            },
            confirmButton = {
                TextButton(onClick = { showPrivacyDialog = false }) {
                    Text("Understood", color = primaryColor, fontWeight = FontWeight.Bold)
                }
            },
            containerColor = surfaceColor
        )
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = primaryColor,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = primaryColor,
            letterSpacing = 0.5.sp
        )
    }
}

@Composable
fun SettingsToggleRow(
    title: String,
    subtitle: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    testTag: String
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    val onSurface = MaterialTheme.colorScheme.onSurface
    val onSurfaceVariant = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = onSurface
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = onSurfaceVariant
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = primaryColor,
                uncheckedThumbColor = onSurfaceVariant,
                uncheckedTrackColor = MaterialTheme.colorScheme.surfaceVariant
            ),
            modifier = Modifier.testTag(testTag)
        )
    }
}

@Composable
fun SettingsDivider() {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
            .height(0.5.dp)
            .background(MaterialTheme.colorScheme.outlineVariant)
    )
}
