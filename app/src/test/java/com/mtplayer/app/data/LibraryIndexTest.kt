package com.mtplayer.app.data

import android.net.Uri
import com.mtplayer.app.data.media.LibraryIndex
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.data.media.SongSort
import com.mtplayer.app.data.media.Video
import com.mtplayer.app.data.media.sortedBy
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class LibraryIndexTest {
    private fun song(id: Long, title: String, artist: String, album: String, albumId: Long, track: Int = 0, genre: String = "", folder: String = "/music", albumArtist: String = "") =
        Song(id, Uri.parse("content://media/external/audio/media/$id"), title, artist, album, albumId, albumArtist,
            180_000, track, 2020, genre, folder, folder.substringAfterLast('/'), id, 1000, "audio/mpeg", "$folder/$title.mp3")

    private fun video(id: Long, title: String, folder: String, added: Long) =
        Video(id, Uri.parse("content://media/external/video/media/$id"), title, 60_000, 1920, 1080, 1000, folder, folder.substringAfterLast('/'), added, "video/mp4", "$folder/$title.mp4")

    private val index = LibraryIndex(
        listOf(
            song(1, "Zebra", "Beyoncé", "Lemonade", 10, track = 2, genre = "Pop"),
            song(2, "apple", "Beyoncé", "Lemonade", 10, track = 1, genre = "pop "),
            song(3, "Mango", "Other", "Mix", 20, genre = "Rock", folder = "/music/rock"),
            song(4, "Kiwi", "Third", "Mix", 20),
        ),
        listOf(video(5, "Holiday", "/dcim", 100), video(6, "Concert", "/movies", 200)),
    )

    @Test fun songsSortedCaseInsensitive() = assertEquals(listOf("apple", "Kiwi", "Mango", "Zebra"), index.songs.map { it.title })

    @Test fun albumsGroupAndOrderByTrack() {
        val album = index.album(10)!!
        assertEquals(listOf("apple", "Zebra"), album.songs.map { it.title })
        assertEquals("Beyoncé", album.artist)
        assertEquals("Various artists", index.album(20)!!.artist)
    }

    @Test fun genresMergeCaseAndWhitespace() {
        assertEquals(listOf("Pop", "Rock"), index.genres.map { it.name })
        assertEquals(2, index.genre("POP")!!.songs.size)
    }

    @Test fun foldersAndVideos() {
        assertEquals(2, index.musicFolders.size)
        assertEquals(listOf("Concert", "Holiday"), index.videos.map { it.title })
        assertEquals(2, index.videoFolders.size)
    }

    @Test fun searchIsAccentAndCaseInsensitiveWithAllTerms() {
        val r = index.search("beyonce ZEB")
        assertEquals(listOf(1L), r.songs.map { it.id })
        assertTrue(index.search("lemonade").albums.isNotEmpty())
        assertEquals(listOf(5L), index.search("holi").videos.map { it.id })
        assertTrue(index.search("   ").isEmpty)
        assertTrue(index.search("nothing-matches").isEmpty)
    }

    @Test fun sorting() {
        assertEquals(listOf("Beyoncé", "Beyoncé", "Other", "Third"), index.songs.sortedBy(SongSort.ARTIST).map { it.artist })
        assertTrue(LibraryIndex.EMPTY.isEmpty)
    }
}
