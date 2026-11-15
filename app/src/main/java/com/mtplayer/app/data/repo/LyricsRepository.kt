package com.mtplayer.app.data.repo

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.annotation.OptIn
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.MetadataRetriever
import androidx.media3.extractor.metadata.id3.BinaryFrame
import androidx.media3.extractor.metadata.id3.TextInformationFrame
import androidx.media3.extractor.metadata.vorbis.VorbisComment
import com.mtplayer.app.data.db.LyricsLinkDao
import com.mtplayer.app.data.db.LyricsLinkEntity
import com.mtplayer.app.data.media.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.guava.await
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import java.io.File
import java.nio.charset.Charset

/**
 * Finds lyrics for a song, local sources only, in this order:
 * 1. a file the user linked with the system file picker (works on every Android version),
 * 2. a sidecar `.lrc` / `.txt` with the same name (readable directly on Android 9–10),
 * 3. lyrics embedded in the file (ID3 USLT, Vorbis LYRICS, MP4 ©lyr).
 */
class LyricsRepository(private val context: Context, private val links: LyricsLinkDao) {
    private val cache = object : LinkedHashMap<Long, Lyrics?>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<Long, Lyrics?>?) = size > 32
    }

    suspend fun load(song: Song): Lyrics? = withContext(Dispatchers.IO) {
        synchronized(cache) { if (cache.containsKey(song.id)) return@withContext cache[song.id] }
        val result = linked(song) ?: sidecar(song) ?: embedded(song)
        synchronized(cache) { cache[song.id] = result }
        result
    }

    suspend fun link(song: Song, uri: Uri): Lyrics? = withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        links.upsert(LyricsLinkEntity(song.id, uri.toString()))
        synchronized(cache) { cache.remove(song.id) }
        load(song)
    }

    suspend fun unlink(song: Song) = withContext(Dispatchers.IO) {
        links.get(song.id)?.let { link ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(Uri.parse(link.uri), Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
        }
        links.delete(song.id)
        synchronized(cache) { cache.remove(song.id) }
    }

    suspend fun hasLink(song: Song) = links.get(song.id) != null

    private suspend fun linked(song: Song): Lyrics? {
        val link = links.get(song.id) ?: return null
        val text = runCatching {
            context.contentResolver.openInputStream(Uri.parse(link.uri))?.use { it.readBytes().decode() }
        }.getOrNull() ?: return null
        return LrcParser.parse(text, Lyrics.Source.LINKED)
    }

    private fun sidecar(song: Song): Lyrics? {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.Q || song.filePath.isBlank()) return null
        val base = song.filePath.substringBeforeLast('.')
        for (ext in listOf("lrc", "LRC", "txt")) {
            val f = File("$base.$ext")
            if (f.canRead() && f.length() in 1..MAX_BYTES) {
                val parsed = runCatching { LrcParser.parse(f.readBytes().decode(), Lyrics.Source.SIDECAR) }.getOrNull()
                if (parsed != null) return parsed
            }
        }
        return null
    }

    @OptIn(UnstableApi::class)
    private suspend fun embedded(song: Song): Lyrics? {
        val groups = runCatching {
            withTimeoutOrNull(4_000) {
                MetadataRetriever.Builder(context, MediaItem.fromUri(song.uri)).build().use { it.retrieveTrackGroups().await() }
            }
        }.getOrNull() ?: return null
        for (g in 0 until groups.length) {
            val group = groups[g]
            for (t in 0 until group.length) {
                val metadata = group.getFormat(t).metadata ?: continue
                for (i in 0 until metadata.length()) {
                    val text = when (val entry = metadata[i]) {
                        is BinaryFrame -> if (entry.id == "USLT" || entry.id == "SYLT") parseUslt(entry.data) else null
                        is TextInformationFrame -> if (entry.id == "USLT") entry.values.joinToString("\n") else null
                        is VorbisComment -> if (entry.key.equals("LYRICS", true) || entry.key.equals("UNSYNCEDLYRICS", true)) entry.value else null
                        else -> null
                    }
                    if (!text.isNullOrBlank()) return LrcParser.parse(text, Lyrics.Source.EMBEDDED)
                }
            }
        }
        return null
    }

    companion object {
        private const val MAX_BYTES = 512L * 1024

        /** ID3v2 USLT: encoding(1) language(3) descriptor(terminated) text. */
        fun parseUslt(data: ByteArray): String? {
            if (data.size < 5) return null
            val charset = when (data[0].toInt()) {
                1 -> Charsets.UTF_16
                2 -> Charsets.UTF_16BE
                3 -> Charsets.UTF_8
                else -> Charsets.ISO_8859_1
            }
            val wide = data[0].toInt() == 1 || data[0].toInt() == 2
            var i = 4
            if (wide) {
                while (i + 1 < data.size && !(data[i].toInt() == 0 && data[i + 1].toInt() == 0)) i += 2
                i += 2
            } else {
                while (i < data.size && data[i].toInt() != 0) i++
                i += 1
            }
            if (i >= data.size) return null
            return String(data, i, data.size - i, charset).trimEnd('\u0000').trim()
        }

        private fun ByteArray.decode(): String {
            if (size >= 2 && this[0] == 0xFF.toByte() && this[1] == 0xFE.toByte()) return String(this, Charsets.UTF_16LE).drop(1)
            if (size >= 2 && this[0] == 0xFE.toByte() && this[1] == 0xFF.toByte()) return String(this, Charsets.UTF_16BE).drop(1)
            val utf8 = String(this, Charsets.UTF_8)
            // Fall back to Latin-1 for legacy files that are not valid UTF-8.
            return if (utf8.contains('\uFFFD')) String(this, Charset.forName("ISO-8859-1")) else utf8
        }
    }
}
