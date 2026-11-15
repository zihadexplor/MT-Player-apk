package com.example.update.ui

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.OpenInNew
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.BuildConfig
import com.example.update.UpdateManager
import com.example.update.UpdateState

/** "App updates" card for the Settings screen. */
@Composable
fun UpdateSettingsCard(modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val manager = remember { UpdateManager.get(context) }
    val state by manager.state.collectAsStateWithLifecycle()
    val autoCheck by manager.autoCheckEnabled.collectAsStateWithLifecycle()
    val lastChecked by manager.lastCheckedAt.collectAsStateWithLifecycle()

    val cs = MaterialTheme.colorScheme

    Card(
        modifier = modifier.fillMaxWidth().testTag("settings_updates_card"),
        shape = RoundedCornerShape(16.dp),
        colors = CardDefaults.cardColors(containerColor = cs.surfaceContainerHigh),
        border = BorderStroke(0.5.dp, cs.outlineVariant),
    ) {
        Column(Modifier.padding(14.dp)) {
            // Current version
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(40.dp).clip(CircleShape).background(cs.primary.copy(alpha = 0.16f)),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Default.SystemUpdate, contentDescription = null, tint = cs.primary, modifier = Modifier.size(22.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(
                        "Version ${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.titleMedium,
                        color = cs.onSurface,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        "Build ${BuildConfig.VERSION_CODE} · ${if (manager.usesAuthenticatedChannel) "Private" else "Public"} GitHub channel",
                        style = MaterialTheme.typography.bodySmall,
                        color = cs.onSurfaceVariant,
                    )
                }
            }

            Divider()

            // Check for updates row with live status
            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable(enabled = state !is UpdateState.Checking) { manager.checkForUpdates(userInitiated = true) }
                    .padding(vertical = 8.dp)
                    .testTag("setting_check_updates"),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Check for updates", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text(statusText(state, lastChecked), style = MaterialTheme.typography.bodySmall, color = statusColor(state))
                }
                Spacer(Modifier.width(12.dp))
                StatusIcon(state)
            }

            val downloading = state as? UpdateState.Downloading
            if (downloading != null) {
                Spacer(Modifier.height(6.dp))
                LinearProgressIndicator(
                    progress = { downloading.progress },
                    modifier = Modifier.fillMaxWidth().height(6.dp).clip(RoundedCornerShape(3.dp)),
                    color = cs.primary,
                    trackColor = cs.surfaceVariant,
                    strokeCap = StrokeCap.Round,
                )
            }

            Divider()

            Row(Modifier.fillMaxWidth().padding(vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Check automatically", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text("Look for new versions each time the app opens", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Spacer(Modifier.width(12.dp))
                Switch(
                    checked = autoCheck,
                    onCheckedChange = manager::setAutoCheckEnabled,
                    colors = SwitchDefaults.colors(
                        checkedThumbColor = cs.onPrimary,
                        checkedTrackColor = cs.primary,
                        uncheckedThumbColor = cs.onSurfaceVariant,
                        uncheckedTrackColor = cs.surfaceVariant,
                    ),
                    modifier = Modifier.testTag("toggle_auto_update"),
                )
            }

            Divider()

            Row(
                Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp))
                    .clickable {
                        runCatching {
                            context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(manager.releasesPageUrl)))
                        }
                    }
                    .padding(vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    Text("Release history", style = MaterialTheme.typography.titleMedium, color = cs.onSurface)
                    Text("View all versions and changelogs on GitHub", style = MaterialTheme.typography.bodySmall, color = cs.onSurfaceVariant)
                }
                Icon(Icons.AutoMirrored.Filled.OpenInNew, contentDescription = null, tint = cs.onSurfaceVariant, modifier = Modifier.size(20.dp))
            }
        }
    }
}

@Composable
private fun StatusIcon(state: UpdateState) {
    val cs = MaterialTheme.colorScheme
    when (state) {
        is UpdateState.Checking -> CircularProgressIndicator(Modifier.size(22.dp), color = cs.primary, strokeWidth = 2.5.dp)
        is UpdateState.UpToDate -> Icon(Icons.Default.CheckCircle, null, tint = cs.primary, modifier = Modifier.size(22.dp))
        is UpdateState.Available, is UpdateState.ReadyToInstall ->
            Box(Modifier.clip(RoundedCornerShape(50)).background(cs.primary).padding(horizontal = 10.dp, vertical = 4.dp)) {
                Text("NEW", style = MaterialTheme.typography.labelMedium, color = cs.onPrimary, fontWeight = FontWeight.Black)
            }
        is UpdateState.Downloading -> Icon(Icons.Default.CloudDownload, null, tint = cs.primary, modifier = Modifier.size(22.dp))
        is UpdateState.Failed -> Icon(Icons.Default.ErrorOutline, null, tint = cs.error, modifier = Modifier.size(22.dp))
        else -> Icon(Icons.Default.SystemUpdate, null, tint = cs.primary, modifier = Modifier.size(22.dp))
    }
}

private fun statusText(state: UpdateState, lastChecked: Long): String = when (state) {
    is UpdateState.Checking -> "Checking GitHub releases…"
    is UpdateState.UpToDate -> "You're on the latest version"
    is UpdateState.Available -> "v${state.release.versionName} is available — tap to view"
    is UpdateState.Downloading -> "Downloading v${state.release.versionName} · ${(state.progress * 100).toInt()}%"
    is UpdateState.ReadyToInstall -> "v${state.release.versionName} is ready — tap to install"
    is UpdateState.Installing -> "Installing v${state.release.versionName}…"
    is UpdateState.Failed -> state.message
    else -> "Last checked: ${formatRelative(lastChecked)}"
}

@Composable
private fun statusColor(state: UpdateState): Color = when (state) {
    is UpdateState.Available, is UpdateState.ReadyToInstall, is UpdateState.Downloading -> MaterialTheme.colorScheme.primary
    is UpdateState.Failed -> MaterialTheme.colorScheme.error
    else -> MaterialTheme.colorScheme.onSurfaceVariant
}

@Composable
private fun Divider() {
    Box(Modifier.fillMaxWidth().padding(vertical = 10.dp).height(0.5.dp).background(MaterialTheme.colorScheme.outlineVariant))
}
