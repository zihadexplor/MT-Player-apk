package com.mtplayer.app.feature.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.mtplayer.app.R
import com.mtplayer.app.core.Format
import com.mtplayer.app.feature.shell.LocalContainer
import com.mtplayer.app.update.UpdateState

/** Dialog flow for an available update: notes → download progress → verify → install. */
@Composable
fun UpdatePrompt() {
    val updates = LocalContainer.current.updates
    val state by updates.state.collectAsStateWithLifecycle()
    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) {
        (updates.state.value as? UpdateState.ReadyToInstall)?.let { if (updates.canInstall()) updates.install(it.file) }
    }
    val s = state
    when (s) {
        is UpdateState.Available -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.update_available, s.release.versionName)) },
            text = {
                Column(Modifier.heightIn(max = 320.dp).verticalScroll(rememberScrollState())) {
                    Text(stringResource(R.string.update_size, Format.size(s.release.apkSize)), style = MaterialTheme.typography.labelLarge)
                    Spacer(Modifier.height(8.dp))
                    Text(s.release.notes.ifBlank { stringResource(R.string.update_no_notes) }, style = MaterialTheme.typography.bodyMedium)
                }
            },
            confirmButton = { TextButton(onClick = { updates.download(s.release) }) { Text(stringResource(R.string.update_download)) } },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.later)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        is UpdateState.Downloading -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_downloading)) },
            text = {
                Column {
                    LinearProgressIndicator(progress = { s.progress }, modifier = Modifier.fillMaxWidth())
                    Spacer(Modifier.height(8.dp))
                    Text("${(s.progress * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
                }
            },
            confirmButton = {},
            dismissButton = { TextButton(onClick = updates::cancel) { Text(stringResource(R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        is UpdateState.Verifying -> AlertDialog(
            onDismissRequest = {},
            title = { Text(stringResource(R.string.update_verifying)) },
            text = { LinearProgressIndicator(Modifier.fillMaxWidth()) },
            confirmButton = {},
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        is UpdateState.ReadyToInstall -> AlertDialog(
            onDismissRequest = updates::cancel,
            title = { Text(stringResource(R.string.update_ready)) },
            text = { Text(stringResource(R.string.update_ready_body)) },
            confirmButton = {
                TextButton(onClick = {
                    if (updates.canInstall()) updates.install(s.file) else permissionLauncher.launch(updates.installPermissionIntent())
                }) { Text(stringResource(R.string.install)) }
            },
            dismissButton = { TextButton(onClick = updates::cancel) { Text(stringResource(R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        is UpdateState.Failed -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.update_failed)) },
            text = { Text(stringResource(failureText(s.reason))) },
            confirmButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.ok)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        UpdateState.NeedsInstallPermission -> AlertDialog(
            onDismissRequest = updates::dismiss,
            title = { Text(stringResource(R.string.update_permission_title)) },
            text = { Text(stringResource(R.string.update_permission_body)) },
            confirmButton = { TextButton(onClick = { permissionLauncher.launch(updates.installPermissionIntent()); updates.dismiss() }) { Text(stringResource(R.string.open_settings)) } },
            dismissButton = { TextButton(onClick = updates::dismiss) { Text(stringResource(R.string.cancel)) } },
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
        )
        else -> {}
    }
}

fun failureText(reason: UpdateState.Reason) = when (reason) {
    UpdateState.Reason.NETWORK -> R.string.update_err_network
    UpdateState.Reason.NO_APK -> R.string.update_err_no_apk
    UpdateState.Reason.CHECKSUM_MISSING -> R.string.update_err_checksum_missing
    UpdateState.Reason.CHECKSUM_MISMATCH -> R.string.update_err_checksum
    UpdateState.Reason.WRONG_PACKAGE -> R.string.update_err_package
    UpdateState.Reason.NOT_NEWER -> R.string.update_err_not_newer
    UpdateState.Reason.WRONG_SIGNATURE -> R.string.update_err_signature
    UpdateState.Reason.INSTALL -> R.string.update_err_install
}
