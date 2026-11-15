package com.mtplayer.app.feature.settings

import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.produceState
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import android.content.Intent
import android.os.Build
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.selection.SelectionContainer
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.BuildConfig
import com.mtplayer.app.R
import com.mtplayer.app.core.CrashLog
import com.mtplayer.app.data.db.MediaType
import com.mtplayer.app.designsystem.components.ChoiceSheet
import com.mtplayer.app.designsystem.components.ConfirmDialog
import com.mtplayer.app.designsystem.components.LocalMessenger
import com.mtplayer.app.designsystem.components.MessageState
import com.mtplayer.app.designsystem.components.MediaRow
import com.mtplayer.app.designsystem.theme.ThemeMode
import com.mtplayer.app.feature.common.DetailBar
import com.mtplayer.app.feature.shell.LocalAppViewModel
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.feature.shell.LocalNavigator
import com.mtplayer.app.update.UpdateState

private enum class Picker { THEME, MIN_DURATION, DOUBLE_TAP, VIDEO_SPEED }

@Composable
fun SettingsScreen() {
    val vm = LocalAppViewModel.current
    val nav = LocalNavigator.current
    val container = LocalContainer.current
    val messenger = LocalMessenger.current
    val context = LocalContext.current
    val s by vm.settings.collectAsStateWithLifecycle()
    val update by container.updates.state.collectAsStateWithLifecycle()
    var picker by rememberSaveable { mutableStateOf<Picker?>(null) }
    var confirmClear by rememberSaveable { mutableStateOf(false) }
    var showCrash by rememberSaveable { mutableStateOf(false) }
    var crashCount by remember { mutableStateOf(0) }
    LaunchedEffect(Unit) { crashCount = withContext(Dispatchers.IO) { container.crashLog.entries().size } }
    val scope = rememberCoroutineScope()
    val cleared = stringResource(R.string.history_cleared)

    Column(Modifier.fillMaxSize()) {
        DetailBar(stringResource(R.string.settings))
        Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(bottom = 24.dp)) {
            Group(stringResource(R.string.settings_appearance))
            Choice(stringResource(R.string.theme), stringResource(themeLabel(s.themeMode))) { picker = Picker.THEME }
            Toggle(stringResource(R.string.pure_black), stringResource(R.string.pure_black_desc), s.pureBlack) { v -> vm.updateSettings { it.copy(pureBlack = v) } }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                Toggle(stringResource(R.string.dynamic_color), stringResource(R.string.dynamic_color_desc), s.dynamicColor) { v -> vm.updateSettings { it.copy(dynamicColor = v) } }
            }

            Group(stringResource(R.string.tab_music))
            Choice(stringResource(R.string.equalizer), stringResource(if (s.eqEnabled) R.string.on else R.string.off), onClick = nav::equalizer)
            Toggle(stringResource(R.string.pause_on_disconnect), stringResource(R.string.pause_on_disconnect_desc), s.pauseOnDisconnect) { v -> vm.updateSettings { it.copy(pauseOnDisconnect = v) } }
            Choice(stringResource(R.string.min_duration), if (s.minAudioDurationSec == 0) stringResource(R.string.min_duration_none)
                else stringResource(R.string.seconds_short, s.minAudioDurationSec)) { picker = Picker.MIN_DURATION }
            Toggle(stringResource(R.string.sleep_finish_song), null, s.sleepFinishSong) { v -> vm.updateSettings { it.copy(sleepFinishSong = v) } }
            Toggle(stringResource(R.string.sleep_fade), null, s.sleepFade) { v -> vm.updateSettings { it.copy(sleepFade = v) } }

            Group(stringResource(R.string.tab_videos))
            Toggle(stringResource(R.string.resume_video), stringResource(R.string.resume_video_desc), s.resumeVideo) { v -> vm.updateSettings { it.copy(resumeVideo = v) } }
            Toggle(stringResource(R.string.auto_pip), stringResource(R.string.auto_pip_desc), s.autoPip) { v -> vm.updateSettings { it.copy(autoPip = v) } }
            Toggle(stringResource(R.string.background_audio), stringResource(R.string.background_audio_desc), s.videoBackgroundAudio) { v -> vm.updateSettings { it.copy(videoBackgroundAudio = v) } }
            Choice(stringResource(R.string.double_tap_seek), stringResource(R.string.seconds_short, s.doubleTapSeekSec)) { picker = Picker.DOUBLE_TAP }
            Choice(stringResource(R.string.default_speed), "${s.defaultVideoSpeed}x") { picker = Picker.VIDEO_SPEED }
            Toggle(stringResource(R.string.gesture_brightness), null, s.gestureBrightness) { v -> vm.updateSettings { it.copy(gestureBrightness = v) } }
            Toggle(stringResource(R.string.gesture_volume), null, s.gestureVolume) { v -> vm.updateSettings { it.copy(gestureVolume = v) } }
            Toggle(stringResource(R.string.gesture_seek), null, s.gestureSeek) { v -> vm.updateSettings { it.copy(gestureSeek = v) } }

            Group(stringResource(R.string.settings_library))
            Choice(stringResource(R.string.rescan), stringResource(R.string.rescan_desc)) { vm.refreshLibrary(); messenger.show(context.getString(R.string.rescanning)) }
            Choice(stringResource(R.string.hidden_folders), stringResource(R.string.hidden_folders_desc), onClick = nav::hiddenFolders)
            Choice(stringResource(R.string.clear_history), stringResource(R.string.clear_history_desc)) { confirmClear = true }

            if (BuildConfig.AUTO_UPDATE_CHECK) {
                Group(stringResource(R.string.settings_updates))
                Toggle(stringResource(R.string.auto_update), stringResource(R.string.auto_update_desc), s.autoUpdateCheck) { v -> vm.updateSettings { it.copy(autoUpdateCheck = v) } }
                Choice(stringResource(R.string.check_updates), updateSummary(update)) { container.updates.check() }
            }

            Group(stringResource(R.string.settings_about))
            Choice(stringResource(R.string.version), "${BuildConfig.VERSION_NAME} (${BuildConfig.VERSION_CODE})", onClick = null)
            Choice(stringResource(R.string.crash_reports), if (crashCount == 0) stringResource(R.string.crash_none) else stringResource(R.string.crash_some, crashCount),
                onClick = if (crashCount > 0) ({ showCrash = true }) else null)
            Choice(stringResource(R.string.privacy), stringResource(R.string.privacy_desc), onClick = null)
        }
    }

    when (picker) {
        Picker.THEME -> ChoiceSheet(stringResource(R.string.theme), ThemeMode.entries, s.themeMode, { stringResource(themeLabel(it)) },
            { m -> vm.updateSettings { it.copy(themeMode = m) } }, { picker = null })
        Picker.MIN_DURATION -> ChoiceSheet(stringResource(R.string.min_duration), listOf(0, 10, 30, 60, 120), s.minAudioDurationSec,
            { if (it == 0) stringResource(R.string.min_duration_none) else stringResource(R.string.seconds_short, it) },
            { v -> vm.updateSettings { it.copy(minAudioDurationSec = v) } }, { picker = null })
        Picker.DOUBLE_TAP -> ChoiceSheet(stringResource(R.string.double_tap_seek), listOf(5, 10, 15, 30), s.doubleTapSeekSec,
            { stringResource(R.string.seconds_short, it) }, { v -> vm.updateSettings { it.copy(doubleTapSeekSec = v) } }, { picker = null })
        Picker.VIDEO_SPEED -> ChoiceSheet(stringResource(R.string.default_speed), listOf(0.5f, 0.75f, 1f, 1.25f, 1.5f, 2f), s.defaultVideoSpeed,
            { "${it}x" }, { v -> vm.updateSettings { it.copy(defaultVideoSpeed = v) } }, { picker = null })
        null -> {}
    }
    if (confirmClear) {
        ConfirmDialog(stringResource(R.string.clear_history_q), stringResource(R.string.clear_history_body), stringResource(R.string.clear),
            stringResource(R.string.cancel), onConfirm = { vm.clearHistory { messenger.show(cleared) } }, onDismiss = { confirmClear = false }, destructive = true)
    }
    if (showCrash) {
        val text by produceState("") { value = withContext(Dispatchers.IO) { container.crashLog.latestText()?.let(CrashLog::redact).orEmpty() } }
        AlertDialog(
            onDismissRequest = { showCrash = false },
            title = { Text(stringResource(R.string.crash_reports)) },
            text = {
                SelectionContainer(Modifier.heightIn(max = 360.dp).verticalScroll(rememberScrollState())) {
                    Text(text, style = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace))
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    val send = Intent(Intent.ACTION_SEND).setType("text/plain")
                        .putExtra(Intent.EXTRA_SUBJECT, "MT Player crash report ${BuildConfig.VERSION_NAME}")
                        .putExtra(Intent.EXTRA_TEXT, text)
                    context.startActivity(Intent.createChooser(send, context.getString(R.string.share)))
                }) { Text(stringResource(R.string.share)) }
            },
            dismissButton = {
                TextButton(onClick = { scope.launch(Dispatchers.IO) { container.crashLog.clear() }; crashCount = 0; showCrash = false }) { Text(stringResource(R.string.clear)) }
            },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
    }
}

fun themeLabel(m: ThemeMode) = when (m) {
    ThemeMode.SYSTEM -> R.string.theme_system
    ThemeMode.LIGHT -> R.string.theme_light
    ThemeMode.DARK -> R.string.theme_dark
}

@Composable
private fun updateSummary(state: UpdateState): String = when (state) {
    UpdateState.Checking -> stringResource(R.string.update_checking)
    UpdateState.UpToDate -> stringResource(R.string.update_up_to_date)
    is UpdateState.Available -> stringResource(R.string.update_available, state.release.versionName)
    is UpdateState.Downloading -> stringResource(R.string.update_downloading)
    is UpdateState.Failed -> stringResource(failureText(state.reason))
    else -> stringResource(R.string.check_updates_desc)
}

@Composable
private fun Group(title: String) {
    Text(
        title,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, end = 20.dp, top = 24.dp, bottom = 4.dp),
    )
}

@Composable
private fun Choice(title: String, summary: String?, onClick: (() -> Unit)?) {
    Column(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .let { if (onClick != null) it.clickable(role = Role.Button, onClick = onClick) else it }
            .padding(horizontal = 20.dp, vertical = 12.dp),
    ) {
        Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
        if (!summary.isNullOrBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

@Composable
private fun Toggle(title: String, summary: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().heightIn(min = 56.dp)
            .clickable(role = Role.Switch) { onChange(!checked) }
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.bodyLarge, color = MaterialTheme.colorScheme.onSurface)
            if (!summary.isNullOrBlank()) Text(summary, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Spacer(Modifier.width(16.dp))
        Switch(checked = checked, onCheckedChange = null)
    }
}

@Composable
fun HiddenFoldersScreen() {
    val vm = LocalAppViewModel.current
    val hidden by vm.hiddenFolders.collectAsStateWithLifecycle()
    Column(Modifier.fillMaxSize()) {
        DetailBar(stringResource(R.string.hidden_folders))
        if (hidden.isEmpty()) {
            MessageState(Icons.Rounded.VisibilityOff, stringResource(R.string.no_hidden_title), stringResource(R.string.no_hidden_body))
            return@Column
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = PaddingValues(vertical = 8.dp)) {
            items(hidden, key = { it.mediaType + it.path }) { h ->
                Row(verticalAlignment = Alignment.CenterVertically) {
                    MediaRow(
                        title = h.path.substringAfterLast('/').ifBlank { h.path },
                        subtitle = stringResource(if (h.mediaType == MediaType.VIDEO) R.string.tab_videos else R.string.tab_music) + " · " + h.path,
                        artwork = null,
                        fallbackIcon = Icons.Rounded.Folder,
                        onClick = {},
                        modifier = Modifier.weight(1f),
                    )
                    TextButton(onClick = { vm.unhideFolder(h.path, h.mediaType) }, modifier = Modifier.padding(end = 8.dp).heightIn(min = 48.dp)) {
                        Text(stringResource(R.string.unhide))
                    }
                }
            }
        }
    }
}
