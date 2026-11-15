package com.mtplayer.app

import android.Manifest
import android.content.Context
import android.os.Build
import android.provider.MediaStore
import androidx.test.platform.app.InstrumentationRegistry
import java.io.File
import java.io.RandomAccessFile

object TestSupport {
    val context: Context get() = InstrumentationRegistry.getInstrumentation().targetContext

    fun shell(cmd: String): String {
        val pfd = InstrumentationRegistry.getInstrumentation().uiAutomation.executeShellCommand(cmd)
        return android.os.ParcelFileDescriptor.AutoCloseInputStream(pfd).bufferedReader().use { it.readText() }
    }

    /** Grants runtime permissions explicitly (AGP usually grants them at install, but be sure). */
    fun grantAll() {
        val pkg = context.packageName
        val perms = buildList {
            if (Build.VERSION.SDK_INT >= 33) {
                add(Manifest.permission.READ_MEDIA_AUDIO); add(Manifest.permission.READ_MEDIA_VIDEO)
                add(Manifest.permission.POST_NOTIFICATIONS)
            } else add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        perms.forEach { shell("pm grant $pkg $it") }
    }

    /** Writes a mono 16-bit PCM WAV sine tone. */
    fun writeTone(file: File, seconds: Int, hz: Double = 440.0) {
        val rate = 8000
        val samples = rate * seconds
        RandomAccessFile(file, "rw").use { f ->
            f.setLength(0)
            fun i32(v: Int) { f.write(v and 0xff); f.write(v shr 8 and 0xff); f.write(v shr 16 and 0xff); f.write(v shr 24 and 0xff) }
            fun i16(v: Int) { f.write(v and 0xff); f.write(v shr 8 and 0xff) }
            f.writeBytes("RIFF"); i32(36 + samples * 2); f.writeBytes("WAVEfmt "); i32(16); i16(1); i16(1)
            i32(rate); i32(rate * 2); i16(2); i16(16); f.writeBytes("data"); i32(samples * 2)
            val buf = ByteArray(samples * 2)
            for (i in 0 until samples) {
                val v = (Math.sin(2 * Math.PI * hz * i / rate) * 6000).toInt()
                buf[2 * i] = (v and 0xff).toByte(); buf[2 * i + 1] = (v shr 8 and 0xff).toByte()
            }
            f.write(buf)
        }
    }

    /** True when the CI-provided media file with this display title is indexed by MediaStore. */
    fun mediaStoreHas(title: String, video: Boolean): Boolean {
        val uri = if (video) MediaStore.Video.Media.EXTERNAL_CONTENT_URI else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
        return runCatching {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), "${MediaStore.MediaColumns.TITLE} = ?", arrayOf(title), null)
                ?.use { it.count > 0 } ?: false
        }.getOrDefault(false)
    }

    fun waitUntil(timeoutMs: Long = 10_000, intervalMs: Long = 200, condition: () -> Boolean): Boolean {
        val end = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < end) {
            if (runCatching(condition).getOrDefault(false)) return true
            Thread.sleep(intervalMs)
        }
        return runCatching(condition).getOrDefault(false)
    }
}
