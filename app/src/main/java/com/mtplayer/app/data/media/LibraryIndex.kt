package com.mtplayer.app.data.media

import androidx.compose.runtime.Immutable
import java.text.Normalizer
import java.util.Locale

/**
 * Immutable, in-memory view of the local library. Built off the main thread from MediaStore
 * rows; all grouping is done once so screens only read precomputed lists.
 */
@Immutable
class LibraryIndex(
    songs: List<Song>,
    videos: List<Video>,
) {
    val songs: List<Song> = songs.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    val videos: List<Video> = videos.sortedByDescending { it.dateAddedSec }

    val songById: Map<Long, Song> = this.songs.associateBy { it.id }
    val videoById: Map<Long, Video> = this.videos.associateBy { it.id }

    val albums: List<Album> = this.songs.groupBy { it.albumId }.map { (id, list) ->
        val sorted = list.sortedWith(compareBy<Song> { it.trackNumber }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
        val first = sorted.first()
        val artist = first.albumArtist.ifBlank {
            val distinct = list.map { it.artist }.distinct()
            if (distinct.size == 1) distinct.first() else "Various artists"
        }
        Album(id, first.album, artist, list.maxOf { it.year }, sorted)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })

    val artists: List<Artist> = this.songs.groupBy { it.artist }.map { (name, list) ->
        Artist(name, list.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.album }), list.map { it.albumId }.distinct().size)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    val genres: List<Genre> = this.songs.filter { it.genre.isNotBlank() }
        .groupBy { it.genre.trim().lowercase(Locale.ROOT) }
        .map { (_, list) -> Genre(list.first().genre.trim(), list) }
        .sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    val musicFolders: List<MusicFolder> = this.songs.groupBy { it.folderPath }.map { (path, list) ->
        MusicFolder(path, list.first().folderName, list)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    val videoFolders: List<VideoFolder> = this.videos.groupBy { it.folderPath }.map { (path, list) ->
        VideoFolder(path, list.first().folderName, list)
    }.sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.name })

    private val songKeys: Array<String> = Array(this.songs.size) { i ->
        val s = this.songs[i]
        normalize("${s.title} ${s.artist} ${s.album} ${s.albumArtist}")
    }
    private val videoKeys: Array<String> = Array(this.videos.size) { i ->
        normalize("${this.videos[i].title} ${this.videos[i].folderName}")
    }

    val isEmpty: Boolean get() = songs.isEmpty() && videos.isEmpty()

    fun album(id: Long) = albums.firstOrNull { it.id == id }
    fun artist(name: String) = artists.firstOrNull { it.name == name }
    fun genre(name: String) = genres.firstOrNull { it.name.equals(name, ignoreCase = true) }
    fun musicFolder(path: String) = musicFolders.firstOrNull { it.path == path }
    fun videoFolder(path: String) = videoFolders.firstOrNull { it.path == path }

    /** Every whitespace-separated term must appear (accent- and case-insensitive). */
    fun search(query: String, limit: Int = 50): SearchResults {
        val terms = normalize(query).split(' ').filter { it.isNotBlank() }
        if (terms.isEmpty()) return SearchResults.EMPTY
        fun matches(key: String) = terms.all { key.contains(it) }
        val songs = ArrayList<Song>()
        for (i in songKeys.indices) if (matches(songKeys[i])) { songs += this.songs[i]; if (songs.size >= limit) break }
        val videos = ArrayList<Video>()
        for (i in videoKeys.indices) if (matches(videoKeys[i])) { videos += this.videos[i]; if (videos.size >= limit) break }
        val albums = albums.filter { matches(normalize("${it.title} ${it.artist}")) }.take(limit)
        val artists = artists.filter { matches(normalize(it.name)) }.take(limit)
        return SearchResults(songs, albums, artists, videos)
    }

    companion object {
        val EMPTY = LibraryIndex(emptyList(), emptyList())
        private val marks = Regex("\\p{Mn}+")
        fun normalize(text: String): String =
            Normalizer.normalize(text, Normalizer.Form.NFD).replace(marks, "").lowercase(Locale.ROOT)
    }
}

@Immutable
data class SearchResults(
    val songs: List<Song>,
    val albums: List<Album>,
    val artists: List<Artist>,
    val videos: List<Video>,
) {
    val isEmpty get() = songs.isEmpty() && albums.isEmpty() && artists.isEmpty() && videos.isEmpty()
    companion object { val EMPTY = SearchResults(emptyList(), emptyList(), emptyList(), emptyList()) }
}

fun List<Song>.sortedBy(sort: SongSort): List<Song> = when (sort) {
    SongSort.TITLE -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.ARTIST -> sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.artist }.thenBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    SongSort.ALBUM -> sortedWith(compareBy<Song, String>(String.CASE_INSENSITIVE_ORDER) { it.album }.thenBy { it.trackNumber })
    SongSort.DATE_ADDED -> sortedByDescending { it.dateAddedSec }
    SongSort.DURATION -> sortedByDescending { it.durationMs }
}

fun List<Video>.sortedBy(sort: VideoSort): List<Video> = when (sort) {
    VideoSort.DATE_ADDED -> sortedByDescending { it.dateAddedSec }
    VideoSort.TITLE -> sortedWith(compareBy(String.CASE_INSENSITIVE_ORDER) { it.title })
    VideoSort.DURATION -> sortedByDescending { it.durationMs }
    VideoSort.SIZE -> sortedByDescending { it.sizeBytes }
}
