package com.example.update

import android.content.Context
import androidx.core.content.edit

/** Small persisted state for the updater (survives process death). */
internal class UpdatePreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("mt_updater", Context.MODE_PRIVATE)

    var autoCheckEnabled: Boolean
        get() = prefs.getBoolean(KEY_AUTO_CHECK, true)
        set(value) = prefs.edit { putBoolean(KEY_AUTO_CHECK, value) }

    var lastCheckedAt: Long
        get() = prefs.getLong(KEY_LAST_CHECK, 0L)
        set(value) = prefs.edit { putLong(KEY_LAST_CHECK, value) }

    /** "Remind me later" snooze: the popup stays hidden for this version until the time passes. */
    fun snooze(versionName: String, untilMillis: Long) = prefs.edit {
        putString(KEY_SNOOZE_VERSION, versionName)
        putLong(KEY_SNOOZE_UNTIL, untilMillis)
    }

    fun isSnoozed(versionName: String, now: Long = System.currentTimeMillis()): Boolean =
        prefs.getString(KEY_SNOOZE_VERSION, null) == versionName && now < prefs.getLong(KEY_SNOOZE_UNTIL, 0L)

    fun clearSnooze() = prefs.edit { remove(KEY_SNOOZE_VERSION); remove(KEY_SNOOZE_UNTIL) }

    /** Active / finished DownloadManager job, plus the release JSON it belongs to. */
    var downloadId: Long
        get() = prefs.getLong(KEY_DOWNLOAD_ID, -1L)
        set(value) = prefs.edit { putLong(KEY_DOWNLOAD_ID, value) }

    var downloadRelease: String?
        get() = prefs.getString(KEY_DOWNLOAD_RELEASE, null)
        set(value) = prefs.edit { putString(KEY_DOWNLOAD_RELEASE, value) }

    fun clearDownload() = prefs.edit {
        remove(KEY_DOWNLOAD_ID); remove(KEY_DOWNLOAD_RELEASE)
    }

    private companion object {
        const val KEY_AUTO_CHECK = "auto_check"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_SNOOZE_VERSION = "snooze_version"
        const val KEY_SNOOZE_UNTIL = "snooze_until"
        const val KEY_DOWNLOAD_ID = "download_id"
        const val KEY_DOWNLOAD_RELEASE = "download_release"
    }
}
