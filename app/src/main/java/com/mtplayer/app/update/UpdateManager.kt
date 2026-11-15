package com.mtplayer.app.update

import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageInstaller
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings as AndroidSettings
import com.mtplayer.app.BuildConfig
import com.mtplayer.app.data.prefs.SettingsStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.security.MessageDigest

data class ReleaseInfo(
    val versionName: String,
    val notes: String,
    val apkUrl: String,
    val apkName: String,
    val apkSize: Long,
    val checksumsUrl: String?,
    val pageUrl: String,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    data class Available(val release: ReleaseInfo) : UpdateState
    data class Downloading(val release: ReleaseInfo, val progress: Float) : UpdateState
    data class Verifying(val release: ReleaseInfo) : UpdateState
    data class ReadyToInstall(val release: ReleaseInfo, val file: File) : UpdateState
    data object NeedsInstallPermission : UpdateState
    data class Failed(val reason: Reason, val detail: String? = null) : UpdateState

    enum class Reason { NETWORK, NO_APK, CHECKSUM_MISSING, CHECKSUM_MISMATCH, WRONG_PACKAGE, NOT_NEWER, WRONG_SIGNATURE, INSTALL }
}

/**
 * Secure self-updater (ADR-009). Uses the public GitHub Releases API without any credential,
 * and refuses to install anything that is not: the published SHA-256, the same package name,
 * a higher versionCode, and signed with the same certificate as the installed app.
 */
class UpdateManager(
    private val context: Context,
    private val settings: SettingsStore,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()
    private var job: Job? = null

    private val dir get() = File(context.cacheDir, "updates").apply { mkdirs() }

    /** Background check from app start: release builds only, opt-out in Settings, at most once a day. */
    fun maybeAutoCheck() {
        if (!BuildConfig.AUTO_UPDATE_CHECK) return
        scope.launch {
            val s = settings.current()
            if (!s.autoUpdateCheck) return@launch
            if (System.currentTimeMillis() - s.lastUpdateCheck < DAY_MS) return@launch
            check(silent = true)
        }
    }

    fun check(silent: Boolean = false) {
        if (job?.isActive == true) return
        job = scope.launch {
            if (!silent) _state.value = UpdateState.Checking
            val result = runCatching { fetchLatest() }
            settings.update { it.copy(lastUpdateCheck = System.currentTimeMillis()) }
            _state.value = result.fold(
                onSuccess = { release ->
                    when {
                        release == null -> if (silent) UpdateState.Idle else UpdateState.Failed(UpdateState.Reason.NO_APK)
                        compareVersions(release.versionName, currentVersionName()) > 0 -> UpdateState.Available(release)
                        else -> if (silent) UpdateState.Idle else UpdateState.UpToDate
                    }
                },
                onFailure = { if (silent) UpdateState.Idle else UpdateState.Failed(UpdateState.Reason.NETWORK, it.message) },
            )
        }
    }

    fun download(release: ReleaseInfo) {
        if (job?.isActive == true) return
        job = scope.launch {
            _state.value = UpdateState.Downloading(release, 0f)
            try {
                dir.listFiles()?.forEach { it.delete() }
                val expected = release.checksumsUrl?.let { fetchChecksum(it, release.apkName) }
                    ?: run { _state.value = UpdateState.Failed(UpdateState.Reason.CHECKSUM_MISSING); return@launch }
                val file = File(dir, "update.apk")
                downloadTo(release, file)
                _state.value = UpdateState.Verifying(release)
                _state.value = verify(release, file, expected)
            } catch (e: IOException) {
                _state.value = UpdateState.Failed(UpdateState.Reason.NETWORK, e.message)
            }
        }
    }

    fun cancel() {
        job?.cancel()
        _state.value = UpdateState.Idle
        dir.listFiles()?.forEach { it.delete() }
    }

    fun dismiss() { if (_state.value !is UpdateState.Downloading) _state.value = UpdateState.Idle }

    fun canInstall(): Boolean = context.packageManager.canRequestPackageInstalls()

    fun installPermissionIntent(): Intent =
        Intent(AndroidSettings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    fun install(file: File) {
        if (!canInstall()) { _state.value = UpdateState.NeedsInstallPermission; return }
        scope.launch(Dispatchers.IO) {
            try {
                val installer = context.packageManager.packageInstaller
                val params = PackageInstaller.SessionParams(PackageInstaller.SessionParams.MODE_FULL_INSTALL).apply {
                    setAppPackageName(context.packageName)
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) setRequireUserAction(PackageInstaller.SessionParams.USER_ACTION_NOT_REQUIRED)
                }
                val id = installer.createSession(params)
                installer.openSession(id).use { session ->
                    session.openWrite("base.apk", 0, file.length()).use { out ->
                        file.inputStream().use { it.copyTo(out) }
                        session.fsync(out)
                    }
                    val intent = Intent(context, InstallResultReceiver::class.java).setPackage(context.packageName)
                    val flags = PendingIntent.FLAG_UPDATE_CURRENT or
                        (if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_MUTABLE else 0)
                    session.commit(PendingIntent.getBroadcast(context, id, intent, flags).intentSender)
                }
            } catch (e: Exception) {
                _state.value = UpdateState.Failed(UpdateState.Reason.INSTALL, e.message)
            }
        }
    }

    internal fun onInstallFailed(message: String?) {
        _state.value = UpdateState.Failed(UpdateState.Reason.INSTALL, message)
    }

    // --- Network --------------------------------------------------------------------------

    private suspend fun fetchLatest(): ReleaseInfo? = withContext(Dispatchers.IO) {
        val url = "https://api.github.com/repos/${BuildConfig.UPDATE_REPO_OWNER}/${BuildConfig.UPDATE_REPO_NAME}/releases/latest"
        val json = JSONObject(get(url))
        val assets = json.optJSONArray("assets") ?: return@withContext null
        var apk: JSONObject? = null
        var sums: JSONObject? = null
        for (i in 0 until assets.length()) {
            val a = assets.getJSONObject(i)
            val name = a.optString("name")
            if (name.endsWith(".apk") && apk == null) apk = a
            if (name.equals("SHA256SUMS.txt", true)) sums = a
        }
        val asset = apk ?: return@withContext null
        ReleaseInfo(
            versionName = json.optString("tag_name").removePrefix("v"),
            notes = json.optString("body").take(4_000),
            apkUrl = asset.getString("browser_download_url"),
            apkName = asset.getString("name"),
            apkSize = asset.optLong("size"),
            checksumsUrl = sums?.optString("browser_download_url"),
            pageUrl = json.optString("html_url"),
        )
    }

    private suspend fun fetchChecksum(url: String, apkName: String): String? = withContext(Dispatchers.IO) {
        parseChecksums(get(url))[apkName]
    }

    private fun open(url: String): HttpURLConnection {
        val conn = URL(url).openConnection() as HttpURLConnection
        conn.connectTimeout = 15_000
        conn.readTimeout = 30_000
        conn.instanceFollowRedirects = true
        conn.setRequestProperty("Accept", "application/vnd.github+json")
        conn.setRequestProperty("User-Agent", "MT-Player/${currentVersionName()}")
        if (conn.responseCode !in 200..299) {
            val code = conn.responseCode
            conn.disconnect()
            throw IOException("HTTP $code")
        }
        return conn
    }

    private fun get(url: String): String {
        val conn = open(url)
        return try { conn.inputStream.bufferedReader().use { it.readText() } } finally { conn.disconnect() }
    }

    private suspend fun downloadTo(release: ReleaseInfo, file: File) = withContext(Dispatchers.IO) {
        val conn = open(release.apkUrl)
        try {
            val total = conn.contentLengthLong.takeIf { it > 0 } ?: release.apkSize
            conn.inputStream.use { input ->
                file.outputStream().use { out ->
                    val buf = ByteArray(64 * 1024)
                    var done = 0L
                    var lastEmit = 0L
                    while (isActive) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        done += n
                        if (total > 0 && done - lastEmit > 256 * 1024) {
                            lastEmit = done
                            _state.value = UpdateState.Downloading(release, (done.toFloat() / total).coerceIn(0f, 1f))
                        }
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    // --- Verification ---------------------------------------------------------------------

    private suspend fun verify(release: ReleaseInfo, file: File, expectedSha: String): UpdateState = withContext(Dispatchers.IO) {
        if (!sha256(file).equals(expectedSha, ignoreCase = true)) {
            file.delete(); return@withContext UpdateState.Failed(UpdateState.Reason.CHECKSUM_MISMATCH)
        }
        val pm = context.packageManager
        @Suppress("DEPRECATION")
        val archive: PackageInfo = pm.getPackageArchiveInfo(file.path, PackageManager.GET_SIGNING_CERTIFICATES)
            ?: run { file.delete(); return@withContext UpdateState.Failed(UpdateState.Reason.WRONG_PACKAGE) }
        if (archive.packageName != context.packageName) {
            file.delete(); return@withContext UpdateState.Failed(UpdateState.Reason.WRONG_PACKAGE)
        }
        val installed = pm.getPackageInfo(context.packageName, PackageManager.GET_SIGNING_CERTIFICATES)
        if (archive.longVersionCode <= installed.longVersionCode) {
            file.delete(); return@withContext UpdateState.Failed(UpdateState.Reason.NOT_NEWER)
        }
        if (certDigests(archive) != certDigests(installed) || certDigests(installed).isEmpty()) {
            file.delete(); return@withContext UpdateState.Failed(UpdateState.Reason.WRONG_SIGNATURE)
        }
        UpdateState.ReadyToInstall(release, file)
    }

    /** Full signer set (including rotation history); an update must match the installed app exactly. */
    private fun certDigests(info: PackageInfo): Set<String> {
        val signing = info.signingInfo ?: return emptySet()
        return if (signing.hasMultipleSigners()) {
            signing.apkContentsSigners.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        } else {
            signing.signingCertificateHistory.orEmpty().map { sha256(it.toByteArray()) }.toSet()
        }
    }

    private fun currentVersionName(): String = BuildConfig.VERSION_NAME.removeSuffix("-debug")

    companion object {
        private const val DAY_MS = 24L * 60 * 60 * 1000

        fun sha256(file: File): String = file.inputStream().use { input ->
            val md = MessageDigest.getInstance("SHA-256")
            val buf = ByteArray(64 * 1024)
            while (true) { val n = input.read(buf); if (n < 0) break; md.update(buf, 0, n) }
            md.digest().joinToString("") { "%02x".format(it) }
        }

        fun sha256(bytes: ByteArray): String =
            MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

        /** Parses `sha256sum` output: "<hex>  <name>" per line. */
        fun parseChecksums(text: String): Map<String, String> = text.lines().mapNotNull { line ->
            val parts = line.trim().split(Regex("\\s+"), limit = 2)
            if (parts.size == 2 && parts[0].length == 64) parts[1].trimStart('*') to parts[0].lowercase() else null
        }.toMap()

        /** Numeric semantic-version comparison; non-numeric suffixes are ignored. */
        fun compareVersions(a: String, b: String): Int {
            fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
            val pa = parts(a); val pb = parts(b)
            for (i in 0 until maxOf(pa.size, pb.size)) {
                val c = (pa.getOrElse(i) { 0 }).compareTo(pb.getOrElse(i) { 0 })
                if (c != 0) return c
            }
            return 0
        }
    }
}
