package com.example.util

import android.content.Context
import android.os.Build
import com.example.BuildConfig
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Offline ring-buffered crash & non-fatal diagnostic logger.
 * Writes strictly to app-private filesDir (`mt_crash_logs.txt`, capped at 32 KB).
 */
object CrashLogger {
    private const val LOG_FILE_NAME = "mt_crash_logs.txt"
    private const val MAX_LOG_BYTES = 32 * 1024
    private val lock = Any()

    @Volatile
    private var appContext: Context? = null

    @Volatile
    private var installed = false

    fun install(context: Context) {
        appContext = context.applicationContext
        if (installed) return
        synchronized(lock) {
            if (installed) return
            installed = true
            val previousHandler = Thread.getDefaultUncaughtExceptionHandler()
            Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
                runCatching {
                    writeEntry(
                        context = context.applicationContext,
                        level = "FATAL",
                        tag = "Thread:${thread.name}",
                        message = throwable.message ?: throwable.javaClass.simpleName,
                        throwable = throwable
                    )
                }
                previousHandler?.uncaughtException(thread, throwable)
            }
        }
    }

    fun logNonFatal(tag: String, message: String, throwable: Throwable? = null) {
        val ctx = appContext ?: return
        runCatching {
            writeEntry(ctx, "WARN", tag, message, throwable)
        }
    }

    fun readLogs(context: Context): String = synchronized(lock) {
        val file = File(context.applicationContext.filesDir, LOG_FILE_NAME)
        if (!file.exists()) return ""
        runCatching { file.readText(Charsets.UTF_8) }.getOrDefault("")
    }

    fun clearLogs(context: Context) = synchronized(lock) {
        val file = File(context.applicationContext.filesDir, LOG_FILE_NAME)
        if (file.exists()) {
            runCatching { file.delete() }
        }
    }

    private fun writeEntry(
        context: Context,
        level: String,
        tag: String,
        message: String,
        throwable: Throwable?
    ) {
        synchronized(lock) {
            val file = File(context.filesDir, LOG_FILE_NAME)
            val timestamp = SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date())
            val stack = throwable?.let {
                val sw = StringWriter()
                it.printStackTrace(PrintWriter(sw))
                sw.toString().take(3000)
            } ?: ""

            val entry = buildString {
                appendLine("[$timestamp] [$level] [$tag] v${BuildConfig.VERSION_NAME} (API ${Build.VERSION.SDK_INT})")
                appendLine("Message: $message")
                if (stack.isNotBlank()) {
                    appendLine(stack.trimEnd())
                }
                appendLine("---")
            }

            val existing = if (file.exists()) file.readText(Charsets.UTF_8) else ""
            val combined = (entry + existing).toByteArray(Charsets.UTF_8)
            val trimmed = if (combined.size > MAX_LOG_BYTES) {
                String(combined, 0, MAX_LOG_BYTES, Charsets.UTF_8)
            } else {
                String(combined, Charsets.UTF_8)
            }
            file.writeText(trimmed, Charsets.UTF_8)
        }
    }
}
