package com.example.update

import android.app.DownloadManager
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Environment
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import java.io.File

/**
 * Process-wide coordinator for checking, downloading and installing updates from GitHub Releases.
 *
 * Downloads run in the system [DownloadManager], so they continue when the dialog is dismissed
 * ("Download in background"), survive process death, and show native progress in the shade.
 */
class UpdateManager private constructor(context: Context) {

    private val app = context.applicationContext
    private val prefs = UpdatePreferences(app)
    private val client = GitHubReleaseClient()
    private val downloadManager = app.getSystemService(DownloadManager::class.java)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    /** Whether the update popup is currently shown (independent from the download state). */
    private val _dialogVisible = MutableStateFlow(false)
    val dialogVisible: StateFlow<Boolean> = _dialogVisible.asStateFlow()

    private val _autoCheck = MutableStateFlow(prefs.autoCheckEnabled)
    val autoCheckEnabled: StateFlow<Boolean> = _autoCheck.asStateFlow()

    private val _lastChecked = MutableStateFlow(prefs.lastCheckedAt)
    val lastCheckedAt: StateFlow<Long> = _lastChecked.asStateFlow()

    val usesAuthenticatedChannel: Boolean get() = UpdateConfig.hasToken
    val currentVersionName: String get() = UpdateConfig.currentVersionName
    val releasesPageUrl: String get() = UpdateConfig.releasesPage

    private var checkJob: Job? = null
    private var pollJob: Job? = null
    private var startupDone = false

    // ------------------------------------------------------------------------------- lifecycle

    /** Called once per process from MainActivity: restores downloads and runs the auto-check. */
    fun onAppStart(autoCheckAllowedForBuild: Boolean) {
        if (startupDone) return
        startupDone = true
        cleanupStaleApks()
        if (restoreDownload()) return
        if (autoCheckAllowedForBuild && prefs.autoCheckEnabled) checkForUpdates(userInitiated = false)
    }

    fun setAutoCheckEnabled(enabled: Boolean) {
        prefs.autoCheckEnabled = enabled
        _autoCheck.value = enabled
    }

    // ---------------------------------------------------------------------------------- check

    fun checkForUpdates(userInitiated: Boolean) {
        val current = _state.value
        if (current is UpdateState.Downloading || current is UpdateState.Installing) {
            if (userInitiated) _dialogVisible.value = true
            return
        }
        if (current is UpdateState.ReadyToInstall && userInitiated) {
            _dialogVisible.value = true
            return
        }
        if (checkJob?.isActive == true) return
        checkJob = scope.launch {
            _state.value = UpdateState.Checking
            try {
                val release = client.fetchLatestRelease()
                markChecked()
                if (isNewer(release)) {
                    _state.value = UpdateState.Available(release)
                    if (userInitiated) prefs.clearSnooze()
                    if (userInitiated || !prefs.isSnoozed(release.versionName)) _dialogVisible.value = true
                } else {
                    _state.value = UpdateState.UpToDate(System.currentTimeMillis())
                }
            } catch (t: Throwable) {
                // Silent on automatic checks: never bother the user with a popup for a failed check.
                _state.value = if (userInitiated) {
                    UpdateState.Failed(t.message ?: "Update check failed.")
                } else UpdateState.Idle
            }
        }
    }

    private fun isNewer(release: ReleaseInfo): Boolean {
        val code = release.versionCode
        if (code != null && code > 0) return code > UpdateConfig.currentVersionCode
        return SemVer.isNewer(release.versionName, UpdateConfig.currentVersionName)
    }

    private fun markChecked() {
        val now = System.currentTimeMillis()
        prefs.lastCheckedAt = now
        _lastChecked.value = now
    }

    // ------------------------------------------------------------------------------- dialog

    fun showDialog() { _dialogVisible.value = true }

    /** "Remind me later": hide the popup and snooze this version for 24 hours. */
    fun remindLater() {
        (_state.value as? UpdateState.Available)?.let {
            prefs.snooze(it.release.versionName, System.currentTimeMillis() + SNOOZE_MS)
        }
        _dialogVisible.value = false
    }

    /** Hide the popup while the download keeps running in the system DownloadManager. */
    fun continueInBackground() { _dialogVisible.value = false }

    fun dismissDialog() {
        _dialogVisible.value = false
        val s = _state.value
        if (s is UpdateState.Failed || s is UpdateState.UpToDate) _state.value = UpdateState.Idle
    }

    // ----------------------------------------------------------------------------- download

    fun startDownload() {
        val release = when (val s = _state.value) {
            is UpdateState.Available -> s.release
            is UpdateState.Failed -> s.release ?: return
            else -> return
        }
        scope.launch {
            _state.value = UpdateState.Downloading(release, 0, release.asset.sizeBytes, 0, queued = true)
            try {
                val dir = updatesDir() ?: throw IllegalStateException("Storage is not available.")
                dir.listFiles()?.forEach { it.delete() }
                val target = File(dir, safeFileName(release))
                val resolved = client.resolveDownload(release)
                val request = DownloadManager.Request(Uri.parse(resolved.url))
                    .setTitle("MT Player ${release.tagName}")
                    .setDescription("Downloading update…")
                    .setMimeType(APK_MIME)
                    .setNotificationVisibility(DownloadManager.Request.VISIBILITY_VISIBLE)
                    .setAllowedOverMetered(true)
                    .setAllowedOverRoaming(true)
                    .setDestinationInExternalFilesDir(app, Environment.DIRECTORY_DOWNLOADS, "updates/${target.name}")
                if (resolved.needsAuthHeader) {
                    request.addRequestHeader("Authorization", "Bearer ${UpdateConfig.token}")
                    request.addRequestHeader("Accept", "application/octet-stream")
                }
                val id = downloadManager.enqueue(request)
                prefs.downloadId = id
                prefs.downloadRelease = ReleaseCodec.encode(release)
                UpdateNotifier.cancel(app)
                pollDownload(id, release)
            } catch (t: Throwable) {
                prefs.clearDownload()
                _state.value = UpdateState.Failed(t.message ?: "Couldn't start the download.", release)
            }
        }
    }

    fun cancelDownload() {
        pollJob?.cancel()
        val id = prefs.downloadId
        if (id >= 0) runCatching { downloadManager.remove(id) }
        val release = (state.value as? UpdateState.Downloading)?.release
        prefs.clearDownload()
        _state.value = release?.let { UpdateState.Available(it) } ?: UpdateState.Idle
    }

    private fun pollDownload(id: Long, release: ReleaseInfo) {
        pollJob?.cancel()
        pollJob = scope.launch {
            var lastBytes = 0L
            var lastTime = System.currentTimeMillis()
            var speed = 0L
            while (isActive) {
                val snap = query(id)
                if (snap == null) {
                    // Removed externally (e.g. user cancelled from the notification shade).
                    prefs.clearDownload()
                    _state.value = UpdateState.Available(release)
                    return@launch
                }
                when (snap.status) {
                    DownloadManager.STATUS_SUCCESSFUL -> { onDownloadFinished(release, snap); return@launch }
                    DownloadManager.STATUS_FAILED -> {
                        prefs.clearDownload()
                        val msg = "Download failed (${reasonText(snap.reason)})."
                        _state.value = UpdateState.Failed(msg, release)
                        if (!_dialogVisible.value) UpdateNotifier.notifyFailed(app, msg)
                        return@launch
                    }
                    else -> {
                        val now = System.currentTimeMillis()
                        val dt = now - lastTime
                        if (dt >= 1000) {
                            speed = ((snap.downloaded - lastBytes) * 1000 / dt).coerceAtLeast(0)
                            lastBytes = snap.downloaded
                            lastTime = now
                        }
                        val total = if (snap.total > 0) snap.total else release.asset.sizeBytes
                        _state.value = UpdateState.Downloading(
                            release, snap.downloaded, total, speed,
                            queued = snap.status == DownloadManager.STATUS_PENDING ||
                                snap.status == DownloadManager.STATUS_PAUSED,
                        )
                    }
                }
                delay(POLL_MS)
            }
        }
    }

    private suspend fun onDownloadFinished(release: ReleaseInfo, snap: Snapshot) {
        val file = snap.localUri?.let { Uri.parse(it).path }?.let(::File)
            ?: File(updatesDir(), safeFileName(release))
        try {
            ApkInstaller.verify(app, file, release.asset.sha256)
            _state.value = UpdateState.ReadyToInstall(release, file.absolutePath)
            if (!_dialogVisible.value) UpdateNotifier.notifyReady(app, release.versionName)
        } catch (t: Throwable) {
            file.delete()
            prefs.clearDownload()
            val msg = t.message ?: "The downloaded update could not be verified."
            _state.value = UpdateState.Failed(msg, release)
            if (!_dialogVisible.value) UpdateNotifier.notifyFailed(app, msg)
        }
    }

    /** Entry point from [DownloadCompleteReceiver] (may run in a fresh process). */
    internal fun onSystemDownloadComplete(id: Long, done: () -> Unit) {
        if (id != prefs.downloadId) { done(); return }
        val release = ReleaseCodec.decode(prefs.downloadRelease)
        if (release == null) { done(); return }
        scope.launch {
            try {
                if (pollJob?.isActive == true) return@launch // in-process poller will handle it
                val snap = query(id) ?: return@launch
                if (snap.status == DownloadManager.STATUS_SUCCESSFUL) onDownloadFinished(release, snap)
            } finally {
                done()
            }
        }
    }

    // ------------------------------------------------------------------------------ install

    /** Returns false when the user must first allow "Install unknown apps" for MT Player. */
    fun needsInstallPermission(): Boolean = !ApkInstaller.canInstallPackages(app)

    fun installPermissionIntent() = ApkInstaller.unknownSourcesSettingsIntent(app)

    fun install() {
        val s = _state.value as? UpdateState.ReadyToInstall ?: return
        scope.launch {
            _state.value = UpdateState.Installing(s.release)
            try {
                val file = File(s.apkPath)
                ApkInstaller.verify(app, file, s.release.asset.sha256)
                ApkInstaller.install(app, file)
                UpdateNotifier.cancel(app)
            } catch (t: Throwable) {
                _state.value = UpdateState.Failed(t.message ?: "Installation failed.", s.release)
            }
        }
    }

    internal fun onInstallSucceeded() {
        prefs.clearDownload()
        _dialogVisible.value = false
    }

    internal fun onInstallCancelled() {
        val s = _state.value
        if (s is UpdateState.Installing) restoreDownload()
    }

    internal fun onInstallFailed(message: String) {
        val release = (state.value as? UpdateState.Installing)?.release
        _state.value = UpdateState.Failed(message, release)
        _dialogVisible.value = true
    }

    // ------------------------------------------------------------------------------ helpers

    /** Restores an in-flight or finished download after process restart. Returns true if found. */
    private fun restoreDownload(): Boolean {
        val id = prefs.downloadId
        val release = ReleaseCodec.decode(prefs.downloadRelease)
        if (id < 0 || release == null) return false
        if (!isNewer(release)) { prefs.clearDownload(); return false }
        val snap = query(id) ?: run { prefs.clearDownload(); return false }
        return when (snap.status) {
            DownloadManager.STATUS_SUCCESSFUL -> {
                scope.launch { onDownloadFinished(release, snap) }
                true
            }
            DownloadManager.STATUS_FAILED -> { prefs.clearDownload(); false }
            else -> {
                _state.value = UpdateState.Downloading(release, snap.downloaded, snap.total, 0, queued = true)
                pollDownload(id, release)
                true
            }
        }
    }

    private fun cleanupStaleApks() {
        if (prefs.downloadId >= 0) return
        updatesDir()?.listFiles()?.forEach { it.delete() }
    }

    private fun updatesDir(): File? =
        app.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)?.let { File(it, "updates") }?.apply { mkdirs() }

    private fun safeFileName(release: ReleaseInfo): String =
        release.asset.name.replace(Regex("[^A-Za-z0-9._-]"), "_").ifBlank { "update.apk" }

    private data class Snapshot(
        val status: Int, val reason: Int, val downloaded: Long, val total: Long, val localUri: String?,
    )

    private fun query(id: Long): Snapshot? {
        val cursor: Cursor = runCatching { downloadManager.query(DownloadManager.Query().setFilterById(id)) }
            .getOrNull() ?: return null
        cursor.use { c ->
            if (!c.moveToFirst()) return null
            fun col(name: String) = c.getColumnIndexOrThrow(name)
            return Snapshot(
                status = c.getInt(col(DownloadManager.COLUMN_STATUS)),
                reason = c.getInt(col(DownloadManager.COLUMN_REASON)),
                downloaded = c.getLong(col(DownloadManager.COLUMN_BYTES_DOWNLOADED_SO_FAR)),
                total = c.getLong(col(DownloadManager.COLUMN_TOTAL_SIZE_BYTES)),
                localUri = c.getString(col(DownloadManager.COLUMN_LOCAL_URI)),
            )
        }
    }

    private fun reasonText(reason: Int): String = when (reason) {
        DownloadManager.ERROR_INSUFFICIENT_SPACE -> "not enough storage"
        DownloadManager.ERROR_HTTP_DATA_ERROR, DownloadManager.ERROR_UNHANDLED_HTTP_CODE -> "server error"
        DownloadManager.ERROR_TOO_MANY_REDIRECTS -> "too many redirects"
        DownloadManager.ERROR_CANNOT_RESUME -> "connection lost"
        DownloadManager.ERROR_DEVICE_NOT_FOUND -> "storage unavailable"
        else -> "error $reason"
    }

    companion object {
        private const val APK_MIME = "application/vnd.android.package-archive"
        private const val POLL_MS = 400L
        private const val SNOOZE_MS = 24L * 60 * 60 * 1000

        @Volatile private var instance: UpdateManager? = null

        fun get(context: Context): UpdateManager =
            instance ?: synchronized(this) {
                instance ?: UpdateManager(context).also { instance = it }
            }
    }
}
