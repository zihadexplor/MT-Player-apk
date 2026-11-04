package com.example.update

import com.example.BuildConfig

/** Build-time configuration for the GitHub Releases updater. */
internal object UpdateConfig {
    val owner: String = BuildConfig.UPDATE_REPO_OWNER
    val repo: String = BuildConfig.UPDATE_REPO_NAME

    val latestReleaseApi: String
        get() = "https://api.github.com/repos/$owner/$repo/releases/latest"

    val releasesPage: String
        get() = "https://github.com/$owner/$repo/releases"

    /**
     * Read-only GitHub token injected at build time (secret `MT_UPDATER_TOKEN`).
     * Empty when not configured, in which case only the public endpoint is used.
     */
    val token: String by lazy {
        val key = BuildConfig.UPDATER_TOKEN_KEY
        val bytes = BuildConfig.UPDATER_TOKEN_BYTES
        if (bytes.isEmpty()) "" else String(ByteArray(bytes.size) { i: Int -> (bytes[i] xor key).toByte() }, Charsets.UTF_8)
    }

    val hasToken: Boolean get() = token.isNotBlank()

    /** Current installed version name without build-type suffixes such as `-debug`. */
    val currentVersionName: String
        get() = BuildConfig.VERSION_NAME.substringBefore('-')

    val currentVersionCode: Long get() = BuildConfig.VERSION_CODE.toLong()
}
