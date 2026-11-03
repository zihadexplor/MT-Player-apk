package com.example.ui.screens.settings

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
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
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Equalizer
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.MusicNote
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.minimumInteractiveComponentSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
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
import com.example.BuildConfig
import com.example.ui.theme.DarkBackground
import com.example.update.ui.UpdateSettingsCard
import com.example.ui.theme.DarkOutline
import com.example.ui.theme.DarkOutlineVariant
import com.example.ui.theme.DarkSurface
import com.example.ui.theme.DarkSurfaceContainerHigh
import com.example.ui.theme.DarkSurfaceVariant
import com.example.ui.theme.SpotifyGreen
import com.example.ui.theme.TextPrimary
import com.example.ui.theme.TextSecondary
import com.example.ui.theme.TextTertiary

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    onBackClick: () -> Unit,
    onRescanClick: () -> Unit,
    onNavigateToEqualizer: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    BackHandler { onBackClick() }

    var gaplessPlayback by remember { mutableStateOf(true) }
    var bassBoost by remember { mutableStateOf(true) }
    var hardwareAcceleration by remember { mutableStateOf(true) }
    var resumePlayback by remember { mutableStateOf(true) }
    var autoScanOnLaunch by remember { mutableStateOf(true) }
    var selectedPreset by remember { mutableStateOf("Spotify Flat") }
    var showPresetMenu by remember { mutableStateOf(false) }

    val eqPresets = listOf("Spotify Flat", "Bass Boosted", "Vocal Clarity", "Rock & Metal", "Electronic Dance", "Acoustic")

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
                        color = TextPrimary
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
                            tint = TextPrimary
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = DarkSurface,
                    titleContentColor = TextPrimary
                )
            )
        },
        containerColor = DarkBackground
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            // Audio & Equalizer Section
            item {
                SettingsSectionHeader(title = "Audio & Playback", icon = Icons.Default.MusicNote)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        // Equalizer Profile & Sound FX Navigator
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
                            Column {
                                Text(
                                    text = "Advanced Equalizer & Sound FX",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Customize 5 frequency bands, presets, bass boost & 3D virtualizer",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = SpotifyGreen
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Equalizer,
                                contentDescription = "Open Equalizer",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(24.dp)
                            )
                        }

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Bass Boost Engine",
                            subtitle = "Enhance low frequencies with dynamic punch",
                            checked = bassBoost,
                            onCheckedChange = { bassBoost = it },
                            testTag = "toggle_bass_boost"
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Gapless Playback",
                            subtitle = "Eliminate silent delays between tracks",
                            checked = gaplessPlayback,
                            onCheckedChange = { gaplessPlayback = it },
                            testTag = "toggle_gapless"
                        )
                    }
                }
            }

            // Video Playback Section
            item {
                SettingsSectionHeader(title = "Video Playback", icon = Icons.Default.Videocam)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SettingsToggleRow(
                            title = "Hardware Acceleration",
                            subtitle = "Use AndroidX Media3 hardware decoding",
                            checked = hardwareAcceleration,
                            onCheckedChange = { hardwareAcceleration = it },
                            testTag = "toggle_hw_accel"
                        )

                        SettingsDivider()

                        SettingsToggleRow(
                            title = "Resume Playback",
                            subtitle = "Remember last watched position",
                            checked = resumePlayback,
                            onCheckedChange = { resumePlayback = it },
                            testTag = "toggle_resume_playback"
                        )
                    }
                }
            }

            // Storage & Library Scanner Section
            item {
                SettingsSectionHeader(title = "Storage & Media Scanner", icon = Icons.Default.Folder)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkOutline)
                ) {
                    Column(modifier = Modifier.padding(14.dp)) {
                        SettingsToggleRow(
                            title = "Scan Media on Launch",
                            subtitle = "Automatically index new tracks and videos",
                            checked = autoScanOnLaunch,
                            onCheckedChange = { autoScanOnLaunch = it },
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
                            Column {
                                Text(
                                    text = "Force Media Rescan",
                                    style = MaterialTheme.typography.titleMedium,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Re-index device storage right now",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                            Icon(
                                imageVector = Icons.Default.Refresh,
                                contentDescription = "Rescan",
                                tint = SpotifyGreen,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                }
            }

            // App Updates (GitHub Releases updater)
            item {
                SettingsSectionHeader(title = "App Updates", icon = Icons.Default.SystemUpdate)
                Spacer(modifier = Modifier.height(8.dp))
                UpdateSettingsCard()
            }

            // About & System Info
            item {
                SettingsSectionHeader(title = "About & Privacy", icon = Icons.Default.Info)
                Spacer(modifier = Modifier.height(8.dp))
                Card(
                    modifier = Modifier.fillMaxWidth(),
                    shape = RoundedCornerShape(14.dp),
                    colors = CardDefaults.cardColors(containerColor = DarkSurfaceContainerHigh),
                    border = androidx.compose.foundation.BorderStroke(0.5.dp, DarkOutline)
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(SpotifyGreen),
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "MT",
                                    fontWeight = FontWeight.Black,
                                    fontSize = 14.sp,
                                    color = Color.Black
                                )
                            }
                            Spacer(modifier = Modifier.width(12.dp))
                            Column {
                                Text(
                                    text = "MT Player",
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = TextPrimary
                                )
                                Text(
                                    text = "Version ${BuildConfig.VERSION_NAME} (build ${BuildConfig.VERSION_CODE})",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = TextSecondary
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(14.dp))

                        Text(
                            text = "Engine: AndroidX Media3 (ExoPlayer)\nArchitecture: Jetpack Compose + MVVM\nPrivacy: 100% Offline, Zero Tracking, Zero Ads",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextTertiary,
                            lineHeight = 18.sp
                        )
                    }
                }
            }
        }
    }
}

@Composable
fun SettingsSectionHeader(
    title: String,
    icon: ImageVector
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier.padding(start = 4.dp)
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = SpotifyGreen,
            modifier = Modifier.size(18.dp)
        )
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            text = title,
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = SpotifyGreen,
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
                color = TextPrimary
            )
            Spacer(modifier = Modifier.height(2.dp))
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = TextSecondary
            )
        }
        Spacer(modifier = Modifier.width(12.dp))
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(
                checkedThumbColor = Color.Black,
                checkedTrackColor = SpotifyGreen,
                uncheckedThumbColor = TextTertiary,
                uncheckedTrackColor = DarkSurfaceVariant
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
            .padding(vertical = 10.dp)
            .height(0.5.dp)
            .background(DarkOutlineVariant)
    )
}
