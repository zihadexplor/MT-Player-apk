package com.mtplayer.app.playback

import android.net.Uri
import android.os.Bundle
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mtplayer.app.data.media.Song
import com.mtplayer.app.data.media.Video

/** Conversion between library models and Media3 [MediaItem]s. The mediaId is the MediaStore id. */
object MediaItems {
    const val EXTRA_KIND = "mt.kind"
    const val KIND_SONG = "song"
    const val KIND_VIDEO = "video"
    const val EXTRA_ALBUM_ID = "mt.album_id"

    fun song(song: Song): MediaItem = MediaItem.Builder()
        .setMediaId(song.id.toString())
        .setUri(song.uri)
        .setMimeType(song.mimeType.ifBlank { null })
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setDisplayTitle(song.title)
                .setArtist(song.artist)
                .setSubtitle(song.artist)
                .setAlbumTitle(song.album)
                .setAlbumArtist(song.albumArtist.ifBlank { null })
                .setGenre(song.genre.ifBlank { null })
                .setTrackNumber(song.trackNumber.takeIf { it > 0 })
                .setReleaseYear(song.year.takeIf { it > 0 })
                .setArtworkUri(song.artworkUri)
                .setDurationMs(song.durationMs.takeIf { it > 0 })
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_MUSIC)
                .setExtras(Bundle().apply {
                    putString(EXTRA_KIND, KIND_SONG)
                    putLong(EXTRA_ALBUM_ID, song.albumId)
                })
                .build()
        )
        .build()

    /** A video file played as audio only (background audio hand-off from the video player). */
    fun videoAsAudio(video: Video): MediaItem = MediaItem.Builder()
        .setMediaId("video:${video.id}")
        .setUri(video.uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(video.title)
                .setDisplayTitle(video.title)
                .setArtist(video.folderName)
                .setSubtitle(video.folderName)
                .setIsPlayable(true)
                .setIsBrowsable(false)
                .setMediaType(MediaMetadata.MEDIA_TYPE_VIDEO)
                .setExtras(Bundle().apply { putString(EXTRA_KIND, KIND_VIDEO) })
                .build()
        )
        .build()

    fun browsable(id: String, title: String, artwork: Uri? = null, mediaType: Int = MediaMetadata.MEDIA_TYPE_FOLDER_MIXED): MediaItem =
        MediaItem.Builder()
            .setMediaId(id)
            .setMediaMetadata(
                MediaMetadata.Builder()
                    .setTitle(title)
                    .setArtworkUri(artwork)
                    .setIsBrowsable(true)
                    .setIsPlayable(false)
                    .setMediaType(mediaType)
                    .build()
            )
            .build()

    fun songId(item: MediaItem?): Long? = item?.mediaId?.toLongOrNull()

    fun isVideo(item: MediaItem?): Boolean =
        item?.mediaMetadata?.extras?.getString(EXTRA_KIND) == KIND_VIDEO || item?.mediaId?.startsWith("video:") == true
}
