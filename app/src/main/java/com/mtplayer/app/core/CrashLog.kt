package com.mtplayer.app.core

import android.content.Context
import android.os.Build
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Local-only crash recorder. Keeps the last few crashes in app-private storage so the user can
 * read and share them from Settings. Nothing is uploaded. File paths and content URIs are
 * redacted so a shared report never exposes the user's folder names.
 */
class CrashLog(private val context: Context) {
    // Resolved lazily: Application.onCreate must not touch the disk on the main thread.
    private val dir by lazy { File(context.filesDir, "crash") }
    private val versionLine by lazy {
        runCatching {
            val info = context.packageManager.getPackageInfo(context.packageName, 0)
            "${info.versionName} (${info.longVersionCode})"
        }.getOrDefault("unknown")
    }

    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread.name, error) }
            previous?.uncaughtException(thread, error)
        }
    }

    fun record(threadName: String, error: Throwable) {
        val stamp = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val body = buildString {
            appendLine("MT Player $versionLine")
            appendLine("Android ${Build.VERSION.RELEASE} (API ${Build.VERSION.SDK_INT}) ${Build.MANUFACTURER} ${Build.MODEL}")
            appendLine("Thread: $threadName")
            appendLine("Time: $stamp")
            appendLine()
            append(redact(error.stackTraceToString()))
        }
        dir.mkdirs()
        File(dir, "crash-$stamp.txt").writeText(body)
        trim()
    }

    fun entries(): List<File> = dir.listFiles { f -> f.name.startsWith("crash-") }
        ?.sortedByDescending { it.name }.orEmpty()

    fun latestText(): String? = entries().firstOrNull()?.readText()

    fun clear() { entries().forEach { it.delete() } }

    private fun trim() { entries().drop(MAX_ENTRIES).forEach { it.delete() } }

    companion object {
        private const val MAX_ENTRIES = 5
        private val pathPattern = Regex("""(/storage/|/sdcard/|/data/user/\d+/|content://)[^\n'")]*""")
        fun redact(text: String): String = text.replace(pathPattern) { m -> m.groupValues[1] + "<redacted>" }
    }
}
