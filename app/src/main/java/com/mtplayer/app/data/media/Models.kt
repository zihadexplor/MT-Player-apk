package com.mtplayer.app.data.media

import android.net.Uri
import androidx.compose.runtime.Immutable

/** A local audio file indexed by MediaStore. [id] is the MediaStore `_ID` (same as MT Player 1.x). */
@Immutable
data class Song(
    val id: Long,
    val uri: Uri,
    val title: String,
    val artist: String,
    val album: String,
    val albumId: Long,
    val albumArtist: String,
    val durationMs: Long,
    val trackNumber: Int,
    val year: Int,
    val genre: String,
    val folderPath: String,
    val folderName: String,
    val dateAddedSec: Long,
    val sizeBytes: Long,
    val mimeType: String,
    val filePath: String,
) {
    val artworkUri: Uri get() = albumArtUri(albumId)
    val key: String get() = id.toString()
}

@Immutable
data class Album(
    val id: Long,
    val title: String,
    val artist: String,
    val year: Int,
    val songs: List<Song>,
) {
    val artworkUri: Uri get() = albumArtUri(id)
    val durationMs: Long get() = songs.sumOf { it.durationMs }
}

@Immutable
data class Artist(val name: String, val songs: List<Song>, val albumCount: Int) {
    val artworkUri: Uri? get() = songs.firstOrNull()?.artworkUri
}

@Immutable
data class Genre(val name: String, val songs: List<Song>)

@Immutable
data class MusicFolder(val path: String, val name: String, val songs: List<Song>)

@Immutable
data class Video(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val width: Int,
    val height: Int,
    val sizeBytes: Long,
    val folderPath: String,
    val folderName: String,
    val dateAddedSec: Long,
    val mimeType: String,
    val filePath: String,
) {
    val isPortrait: Boolean get() = height > width && width > 0
    val resolutionLabel: String
        get() {
            val short = minOf(width, height)
            return when {
                short <= 0 -> ""
                short >= 2160 -> "4K"
                short >= 1440 -> "1440p"
                short >= 1080 -> "1080p"
                short >= 720 -> "720p"
                short >= 480 -> "480p"
                else -> "${short}p"
            }
        }
}

@Immutable
data class VideoFolder(val path: String, val name: String, val videos: List<Video>)

fun albumArtUri(albumId: Long): Uri =
    Uri.parse("content://media/external/audio/albumart/$albumId")

enum class SongSort { TITLE, ARTIST, ALBUM, DATE_ADDED, DURATION }
enum class VideoSort { DATE_ADDED, TITLE, DURATION, SIZE }
