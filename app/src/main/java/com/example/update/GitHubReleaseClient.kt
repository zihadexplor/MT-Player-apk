package com.example.update

import com.example.BuildConfig
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Talks to the GitHub Releases REST API.
 *
 * Strategy (works before AND after the repository is made private):
 *  1. If a token is bundled, call the API authenticated (required for private repositories).
 *  2. If there is no token, or the authenticated call fails (revoked/expired token, rate limit,
 *     network hiccup), fall back to the unauthenticated public endpoint.
 */
internal class GitHubReleaseClient(
    private val token: String = UpdateConfig.token,
    private val endpoint: String = UpdateConfig.latestReleaseApi,
) {

    class UpdateCheckException(message: String, cause: Throwable? = null) : IOException(message, cause)

    suspend fun fetchLatestRelease(): ReleaseInfo = withContext(Dispatchers.IO) {
        var authError: Throwable? = null
        if (token.isNotBlank()) {
            try {
                return@withContext parseRelease(get(endpoint, token), UpdateSource.AUTHENTICATED)
            } catch (t: Throwable) {
                authError = t
            }
        }
        try {
            parseRelease(get(endpoint, null), UpdateSource.PUBLIC)
        } catch (t: Throwable) {
            val reason = when {
                t is HttpStatusException && t.code == 404 ->
                    "No published release was found (the repository may be private)."
                t is HttpStatusException && (t.code == 403 || t.code == 429) ->
                    "GitHub rate limit reached. Please try again later."
                t is UpdateCheckException -> t.message ?: "Update check failed."
                else -> "Couldn't reach GitHub. Check your internet connection."
            }
            throw UpdateCheckException(reason, authError ?: t)
        }
    }

    /**
     * Returns a URL the system DownloadManager can fetch WITHOUT extra headers.
     * For private repositories the API asset URL answers with a short-lived signed redirect,
     * which is resolved here so the token never leaves the app process.
     */
    suspend fun resolveDownload(release: ReleaseInfo): ResolvedDownload = withContext(Dispatchers.IO) {
        if (release.source == UpdateSource.PUBLIC || token.isBlank()) {
            return@withContext ResolvedDownload(release.asset.browserUrl, needsAuthHeader = false)
        }
        val conn = (URL(release.asset.apiUrl).openConnection() as HttpURLConnection).apply {
            instanceFollowRedirects = false
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/octet-stream")
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", userAgent())
        }
        try {
            when (val code = conn.responseCode) {
                in 300..399 -> {
                    val location = conn.getHeaderField("Location")
                        ?: throw UpdateCheckException("Download link missing from GitHub response.")
                    ResolvedDownload(location, needsAuthHeader = false)
                }
                200 -> ResolvedDownload(release.asset.apiUrl, needsAuthHeader = true)
                else -> {
                    // Token problem: try the public URL as a last resort.
                    if (code == 401 || code == 403 || code == 404) {
                        ResolvedDownload(release.asset.browserUrl, needsAuthHeader = false)
                    } else {
                        throw UpdateCheckException("GitHub returned HTTP $code for the download.")
                    }
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    data class ResolvedDownload(val url: String, val needsAuthHeader: Boolean)

    private class HttpStatusException(val code: Int) : IOException("HTTP $code")

    private fun get(url: String, token: String?): String {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = TIMEOUT_MS
            readTimeout = TIMEOUT_MS
            setRequestProperty("Accept", "application/vnd.github+json")
            setRequestProperty("X-GitHub-Api-Version", API_VERSION)
            setRequestProperty("User-Agent", userAgent())
            if (!token.isNullOrBlank()) setRequestProperty("Authorization", "Bearer $token")
        }
        try {
            val code = conn.responseCode
            if (code != 200) throw HttpStatusException(code)
            return conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    companion object {
        private const val TIMEOUT_MS = 15_000
        private const val API_VERSION = "2022-11-28"
        private val VERSION_CODE_MARKER = Regex("""<!--\s*versionCode:\s*(\d+)\s*-->""")

        private fun userAgent() = "MT-Player/${BuildConfig.VERSION_NAME} (Android)"

        /** Parses a GitHub `release` JSON object. Exposed for unit tests. */
        fun parseRelease(json: String, source: UpdateSource): ReleaseInfo {
            val obj = JSONObject(json)
            val assets = obj.optJSONArray("assets")
                ?: throw UpdateCheckException("The latest release has no files attached.")
            var best: JSONObject? = null
            for (i in 0 until assets.length()) {
                val a = assets.getJSONObject(i)
                val name = a.optString("name")
                if (!name.endsWith(".apk", ignoreCase = true)) continue
                if (best == null || name.startsWith("release-", ignoreCase = true)) best = a
            }
            val apk = best ?: throw UpdateCheckException("The latest release doesn't contain an APK.")
            val tag = obj.optString("tag_name")
            val body = obj.optString("body")
            val digest = apk.optString("digest").takeIf { it.startsWith("sha256:") }?.removePrefix("sha256:")
            return ReleaseInfo(
                tagName = tag,
                versionName = tag.removePrefix("v").removePrefix("V"),
                versionCode = VERSION_CODE_MARKER.find(body)?.groupValues?.get(1)?.toLongOrNull(),
                title = obj.optString("name").ifBlank { tag },
                notes = body.replace(VERSION_CODE_MARKER, "").trim(),
                publishedAt = obj.optString("published_at"),
                htmlUrl = obj.optString("html_url"),
                asset = ApkAsset(
                    id = apk.optLong("id"),
                    name = apk.optString("name"),
                    sizeBytes = apk.optLong("size"),
                    apiUrl = apk.optString("url"),
                    browserUrl = apk.optString("browser_download_url"),
                    sha256 = digest?.lowercase(),
                ),
                source = source,
            )
        }
    }
}
