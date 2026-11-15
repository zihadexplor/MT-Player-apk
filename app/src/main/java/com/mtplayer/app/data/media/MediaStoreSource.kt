package com.mtplayer.app.data.media

import android.content.ContentResolver
import android.content.ContentUris
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import java.io.File

/**
 * Reads audio and video rows from MediaStore. Must be called off the main thread.
 * Only metadata columns are projected; no file I/O happens here.
 */
class MediaStoreSource(private val resolver: ContentResolver) {

    private val audioCollection: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Audio.Media.EXTERNAL_CONTENT_URI

    private val videoCollection: Uri =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
        else MediaStore.Video.Media.EXTERNAL_CONTENT_URI

    val observedUris: List<Uri> get() = listOf(audioCollection, videoCollection)

    @Suppress("DEPRECATION") // DATA is still the only portable way to get a display path on API 28.
    fun querySongs(minDurationMs: Long): List<Song> {
        val genreById = if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) legacyGenres() else emptyMap()
        val projection = buildList {
            add(MediaStore.Audio.Media._ID)
            add(MediaStore.Audio.Media.TITLE)
            add(MediaStore.Audio.Media.ARTIST)
            add(MediaStore.Audio.Media.ALBUM)
            add(MediaStore.Audio.Media.ALBUM_ID)
            add(MediaStore.Audio.Media.DURATION)
            add(MediaStore.Audio.Media.TRACK)
            add(MediaStore.Audio.Media.YEAR)
            add(MediaStore.Audio.Media.DATE_ADDED)
            add(MediaStore.Audio.Media.SIZE)
            add(MediaStore.Audio.Media.MIME_TYPE)
            add(MediaStore.Audio.Media.DATA)
            add(MediaStore.Audio.Media.DISPLAY_NAME)
            add("album_artist")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Audio.Media.RELATIVE_PATH)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) add(MediaStore.Audio.Media.GENRE)
        }.toTypedArray()
        // AND, not OR: short non-music files (ringtones, voice notes) must never leak in.
        val selection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 AND " +
            "(${MediaStore.Audio.Media.DURATION} >= ? OR ${MediaStore.Audio.Media.DURATION} IS NULL)"
        val args = arrayOf(minDurationMs.coerceAtLeast(0).toString())
        val result = ArrayList<Song>(1024)
        resolver.query(audioCollection, projection, selection, args, null)?.use { c ->
            val id = c.idx(MediaStore.Audio.Media._ID)
            val title = c.idx(MediaStore.Audio.Media.TITLE)
            val artist = c.idx(MediaStore.Audio.Media.ARTIST)
            val album = c.idx(MediaStore.Audio.Media.ALBUM)
            val albumId = c.idx(MediaStore.Audio.Media.ALBUM_ID)
            val duration = c.idx(MediaStore.Audio.Media.DURATION)
            val track = c.idx(MediaStore.Audio.Media.TRACK)
            val year = c.idx(MediaStore.Audio.Media.YEAR)
            val added = c.idx(MediaStore.Audio.Media.DATE_ADDED)
            val size = c.idx(MediaStore.Audio.Media.SIZE)
            val mime = c.idx(MediaStore.Audio.Media.MIME_TYPE)
            val data = c.idx(MediaStore.Audio.Media.DATA)
            val name = c.idx(MediaStore.Audio.Media.DISPLAY_NAME)
            val albumArtist = c.idx("album_artist")
            val relPath = c.idx("relative_path")
            val genre = c.idx("genre")
            while (c.moveToNext()) {
                val rowId = c.getLong(id)
                val path = c.str(data)
                val (folderPath, folderName) = folderOf(path, c.str(relPath))
                val fileName = c.str(name)
                result += Song(
                    id = rowId,
                    uri = ContentUris.withAppendedId(audioCollection, rowId),
                    title = c.str(title).ifBlank { fileName.substringBeforeLast('.').ifBlank { UNKNOWN_TITLE } },
                    artist = c.str(artist).cleanUnknown(UNKNOWN_ARTIST),
                    album = c.str(album).cleanUnknown(UNKNOWN_ALBUM),
                    albumId = c.long(albumId),
                    albumArtist = c.str(albumArtist),
                    durationMs = c.long(duration),
                    trackNumber = (c.long(track) % 1000).toInt(),
                    year = c.long(year).toInt(),
                    genre = (if (genre >= 0) c.str(genre) else genreById[rowId].orEmpty()).trim(),
                    folderPath = folderPath,
                    folderName = folderName,
                    dateAddedSec = c.long(added),
                    sizeBytes = c.long(size),
                    mimeType = c.str(mime),
                    filePath = path,
                )
            }
        }
        return result
    }

    @Suppress("DEPRECATION")
    fun queryVideos(): List<Video> {
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.TITLE)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.WIDTH)
            add(MediaStore.Video.Media.HEIGHT)
            add(MediaStore.Video.Media.SIZE)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.MIME_TYPE)
            add(MediaStore.Video.Media.DATA)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) add(MediaStore.Video.Media.RELATIVE_PATH)
        }.toTypedArray()
        val result = ArrayList<Video>(256)
        resolver.query(videoCollection, projection, null, null, null)?.use { c ->
            val id = c.idx(MediaStore.Video.Media._ID)
            val title = c.idx(MediaStore.Video.Media.TITLE)
            val name = c.idx(MediaStore.Video.Media.DISPLAY_NAME)
            val duration = c.idx(MediaStore.Video.Media.DURATION)
            val width = c.idx(MediaStore.Video.Media.WIDTH)
            val height = c.idx(MediaStore.Video.Media.HEIGHT)
            val size = c.idx(MediaStore.Video.Media.SIZE)
            val added = c.idx(MediaStore.Video.Media.DATE_ADDED)
            val mime = c.idx(MediaStore.Video.Media.MIME_TYPE)
            val data = c.idx(MediaStore.Video.Media.DATA)
            val relPath = c.idx("relative_path")
            while (c.moveToNext()) {
                val rowId = c.getLong(id)
                val path = c.str(data)
                val (folderPath, folderName) = folderOf(path, c.str(relPath))
                val displayName = c.str(name)
                result += Video(
                    id = rowId,
                    uri = ContentUris.withAppendedId(videoCollection, rowId),
                    title = c.str(title).ifBlank { displayName.substringBeforeLast('.') }.ifBlank { UNKNOWN_TITLE },
                    durationMs = c.long(duration),
                    width = c.long(width).toInt(),
                    height = c.long(height).toInt(),
                    sizeBytes = c.long(size),
                    folderPath = folderPath,
                    folderName = folderName,
                    dateAddedSec = c.long(added),
                    mimeType = c.str(mime),
                    filePath = path,
                )
            }
        }
        return result
    }

    /** Genre lookup for API 28–29, where the audio table has no GENRE column. */
    private fun legacyGenres(): Map<Long, String> {
        val map = HashMap<Long, String>()
        runCatching {
            resolver.query(
                MediaStore.Audio.Genres.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Audio.Genres._ID, MediaStore.Audio.Genres.NAME), null, null, null
            )?.use { g ->
                while (g.moveToNext()) {
                    val genreId = g.getLong(0)
                    val genreName = g.getString(1)?.trim().orEmpty()
                    if (genreName.isEmpty()) continue
                    val members = MediaStore.Audio.Genres.Members.getContentUri("external", genreId)
                    resolver.query(members, arrayOf(MediaStore.Audio.Genres.Members.AUDIO_ID), null, null, null)?.use { m ->
                        while (m.moveToNext()) map[m.getLong(0)] = genreName
                    }
                }
            }
        }
        return map
    }

    companion object {
        const val UNKNOWN_TITLE = "Unknown title"
        const val UNKNOWN_ARTIST = "Unknown artist"
        const val UNKNOWN_ALBUM = "Unknown album"

        internal fun folderOf(dataPath: String, relativePath: String): Pair<String, String> {
            if (dataPath.isNotBlank()) {
                val parent = File(dataPath).parentFile
                if (parent != null) return parent.absolutePath to (parent.name.ifBlank { "Storage" })
            }
            val rel = relativePath.trim('/')
            if (rel.isNotBlank()) return rel to rel.substringAfterLast('/')
            return "" to "Storage"
        }

        private fun String.cleanUnknown(fallback: String): String =
            if (isBlank() || this == MediaStore.UNKNOWN_STRING) fallback else this
    }
}

private fun Cursor.idx(column: String): Int = getColumnIndex(column)
private fun Cursor.str(index: Int): String = if (index < 0 || isNull(index)) "" else getString(index).orEmpty()
private fun Cursor.long(index: Int): Long = if (index < 0 || isNull(index)) 0L else getLong(index)
