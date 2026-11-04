package com.example.data.repository

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.Cursor
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.example.data.database.EqualizerPreferenceEntity
import com.example.data.database.FavoriteSongEntity
import com.example.data.database.FavoriteVideoEntity
import com.example.data.database.HiddenFolderEntity
import com.example.data.database.LastMusicPlaybackEntity
import com.example.data.database.LastVideoPlaybackEntity
import com.example.data.database.MTPlayerDatabase
import com.example.data.database.PlaylistEntity
import com.example.data.database.PlaylistSongCrossRef
import com.example.data.database.SleepTimerPreferenceEntity
import com.example.data.database.ThemePreferenceEntity
import com.example.data.model.Album
import com.example.data.model.Artist
import com.example.data.model.AudioTrack
import com.example.data.model.MediaFolder
import com.example.data.model.MusicFolder
import com.example.data.model.Playlist
import com.example.data.model.StorageStats
import com.example.data.model.VideoFolder
import com.example.data.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.UUID

interface MediaRepository {
    val storageStats: Flow<StorageStats>
    val audioTracks: Flow<List<AudioTrack>>
    val videoItems: Flow<List<VideoItem>>
    val albums: Flow<List<Album>>
    val artists: Flow<List<Artist>>
    val musicFolders: Flow<List<MusicFolder>>
    val folders: Flow<List<MediaFolder>>
    val playlists: Flow<List<Playlist>>
    val recentlyPlayedTracks: Flow<List<AudioTrack>>
    val mostPlayedTracks: Flow<List<AudioTrack>>

    // Video specific flows
    val favoriteVideoIds: Flow<Set<String>>
    val hiddenVideoFolderPaths: Flow<Set<String>>
    val videoFolders: Flow<List<VideoFolder>>
    val recentlyWatchedVideos: Flow<List<VideoItem>>
    val continueWatchingVideos: Flow<List<VideoItem>>
    val favoriteVideos: Flow<List<VideoItem>>

    // Room Database Phase 10 flows
    val lastMusicPlayback: Flow<LastMusicPlaybackEntity?>
    val lastVideoPlaybackPositions: Flow<List<LastVideoPlaybackEntity>>
    val equalizerPreference: Flow<EqualizerPreferenceEntity?>
    val themePreference: Flow<ThemePreferenceEntity?>
    val sleepTimerPreference: Flow<SleepTimerPreferenceEntity?>

    suspend fun scanDeviceMedia()
    suspend fun toggleFavorite(trackId: String)
    suspend fun addFavorites(trackIds: List<String>)
    suspend fun createPlaylist(name: String, trackIds: List<String> = emptyList())
    suspend fun renamePlaylist(playlistId: String, newName: String)
    suspend fun deletePlaylist(playlistId: String)
    suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>)
    suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String)
    suspend fun reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int)
    suspend fun recordTrackPlayed(trackId: String)
    fun updatePermissionStatus(granted: Boolean, permanentlyDenied: Boolean = false)

    // Video specific actions
    suspend fun toggleVideoFavorite(videoId: String)
    suspend fun hideVideoFolder(folderPath: String)
    suspend fun unhideVideoFolder(folderPath: String)
    suspend fun unhideAllVideoFolders()
    suspend fun updateVideoProgress(videoId: String, positionMs: Long, durationMs: Long)
    suspend fun clearVideoProgress(videoId: String)

    // Room Database preferences & playback actions
    suspend fun saveMusicPlaybackPosition(songId: String, positionMs: Long, durationMs: Long)
    suspend fun saveEqualizerPreference(isEnabled: Boolean, presetName: String, bassBoost: Int, virtualizer: Int, bandLevelsCsv: String)
    suspend fun saveThemePreference(themeMode: String, accentColorHex: String, pureBlackAmoled: Boolean)
    suspend fun saveSleepTimerPreference(durationMinutes: Int, finishLastSong: Boolean, fadeOutEnabled: Boolean)
    suspend fun clearListeningHistory()
    suspend fun clearWatchingHistory()
}

class OfflineMediaRepository(
    private val context: Context
) : MediaRepository {

    private val database = MTPlayerDatabase.getInstance(context)
    private val dao = database.mediaDao()

    private val _storageStats = MutableStateFlow(StorageStats())
    override val storageStats = _storageStats.asStateFlow()

    private val _rawAudioTracks = MutableStateFlow<List<AudioTrack>>(emptyList())
    private val _allScannedVideos = MutableStateFlow<List<VideoItem>>(emptyList())

    // 1. Favorite Song IDs from Room
    private val favoriteSongIdsFlow: Flow<Set<String>> = dao.getAllFavoriteSongIds().map { it.toSet() }

    // 2. Favorite Video IDs from Room
    override val favoriteVideoIds: Flow<Set<String>> = dao.getAllFavoriteVideoIds().map { it.toSet() }

    // 11. Hidden Folders from Room
    override val hiddenVideoFolderPaths: Flow<Set<String>> = dao.getAllHiddenFolders().map { list ->
        list.map { it.folderPath }.toSet()
    }

    // 9, 10, 12. Preferences from Room
    override val lastMusicPlayback: Flow<LastMusicPlaybackEntity?> = dao.getLastMusicPlaybackPosition()
    override val lastVideoPlaybackPositions: Flow<List<LastVideoPlaybackEntity>> = dao.getAllVideoPlaybackPositions()
    override val equalizerPreference: Flow<EqualizerPreferenceEntity?> = dao.getEqualizerPreference()
    override val themePreference: Flow<ThemePreferenceEntity?> = dao.getThemePreference()
    override val sleepTimerPreference: Flow<SleepTimerPreferenceEntity?> = dao.getSleepTimerPreference()

    // Audio tracks mapped with Room favorite status and play counts
    override val audioTracks: Flow<List<AudioTrack>> = combine(
        _rawAudioTracks,
        favoriteSongIdsFlow,
        dao.getAllListeningHistory()
    ) { rawTracks, favIds, historyList ->
        val countMap = historyList.associate { it.songId to it.playCount }
        rawTracks.map { track ->
            track.copy(
                isFavorite = favIds.contains(track.id),
                playCount = countMap[track.id] ?: 0
            )
        }
    }

    override val albums: Flow<List<Album>> = audioTracks.map { tracks ->
        tracks.groupBy { it.albumId }
            .map { (albumId, tracksInAlbum) ->
                val first = tracksInAlbum.first()
                Album(
                    id = albumId,
                    name = first.album,
                    artist = first.artist,
                    trackCount = tracksInAlbum.size,
                    albumArtUri = first.albumArtUri,
                    tracks = tracksInAlbum
                )
            }.sortedBy { it.name.lowercase() }
    }

    override val artists: Flow<List<Artist>> = audioTracks.map { tracks ->
        tracks.groupBy { it.artist.lowercase() }
            .map { (_, tracksOfArtist) ->
                val first = tracksOfArtist.first()
                val albumCount = tracksOfArtist.map { it.albumId }.distinct().size
                Artist(
                    name = first.artist,
                    trackCount = tracksOfArtist.size,
                    albumCount = albumCount,
                    tracks = tracksOfArtist
                )
            }.sortedBy { it.name.lowercase() }
    }

    override val musicFolders: Flow<List<MusicFolder>> = audioTracks.map { tracks ->
        tracks.groupBy { if (it.folderPath.isNotBlank()) it.folderPath else it.folderName }
            .map { (pathKey, tracksInFolder) ->
                val folderName = tracksInFolder.firstOrNull()?.folderName ?: "Music"
                MusicFolder(
                    name = folderName,
                    path = pathKey,
                    trackCount = tracksInFolder.size,
                    tracks = tracksInFolder
                )
            }.sortedBy { it.name.lowercase() }
    }

    // 3. Custom Playlists & 4. Playlist Song Relationships from Room
    override val playlists: Flow<List<Playlist>> = combine(
        dao.getAllPlaylists(),
        dao.getAllPlaylistSongCrossRefs(),
        favoriteSongIdsFlow
    ) { playlistEntities, refs, favIds ->
        val refsByPlaylist = refs.groupBy { it.playlistId }

        val dbPlaylists = playlistEntities.map { entity ->
            val trackIds = if (entity.id == "favorites_default") {
                favIds.toList()
            } else {
                refsByPlaylist[entity.id]
                    ?.sortedBy { it.orderIndex }
                    ?.map { it.songId } ?: emptyList()
            }
            Playlist(
                id = entity.id,
                name = entity.name,
                trackIds = trackIds,
                createdAt = entity.createdAt
            )
        }

        if (dbPlaylists.none { it.id == "favorites_default" }) {
            listOf(Playlist(id = "favorites_default", name = "Favorite Tracks", trackIds = favIds.toList())) + dbPlaylists
        } else {
            dbPlaylists
        }
    }

    // 5. Listening History & Most Played from Room
    override val recentlyPlayedTracks: Flow<List<AudioTrack>> = combine(
        audioTracks,
        dao.getAllListeningHistory()
    ) { tracks, historyList ->
        val trackMap = tracks.associateBy { it.id }
        historyList
            .sortedByDescending { it.playedAt }
            .mapNotNull { trackMap[it.songId] }
    }

    override val mostPlayedTracks: Flow<List<AudioTrack>> = combine(
        audioTracks,
        dao.getAllListeningHistory()
    ) { tracks, historyList ->
        val countMap = historyList.associate { it.songId to it.playCount }
        tracks.filter { (countMap[it.id] ?: 0) > 0 }
            .sortedByDescending { countMap[it.id] ?: 0 }
    }

    // 6. Video Watching History, 8. Video Progress & 11. Hidden Folders
    override val videoItems: Flow<List<VideoItem>> = combine(
        _allScannedVideos,
        hiddenVideoFolderPaths,
        favoriteVideoIds,
        dao.getAllVideoPlaybackPositions(),
        dao.getAllVideoWatchingHistory()
    ) { allVideos, hiddenFolders, favIds, positions, watchHistory ->
        val progressMap = positions.associate { it.videoId to it.positionMs }
        val watchMap = watchHistory.associate { it.videoId to it.watchedAt }
        allVideos
            .filter { video ->
                !hiddenFolders.contains(video.folderPath) && !hiddenFolders.contains(video.folderName)
            }
            .map { video ->
                video.copy(
                    isFavorite = favIds.contains(video.id),
                    playbackPositionMs = progressMap[video.id] ?: 0L,
                    lastWatchedTimestamp = watchMap[video.id] ?: 0L
                )
            }
    }

    override val videoFolders: Flow<List<VideoFolder>> = combine(
        videoItems,
        hiddenVideoFolderPaths
    ) { videos, hiddenFolders ->
        videos.groupBy { if (it.folderPath.isNotBlank()) it.folderPath else it.folderName }
            .map { (pathKey, videosInFolder) ->
                val folderName = videosInFolder.firstOrNull()?.folderName ?: "Videos"
                val isHidden = hiddenFolders.contains(pathKey) || hiddenFolders.contains(folderName)
                VideoFolder(
                    id = pathKey,
                    name = folderName,
                    path = pathKey,
                    videoCount = videosInFolder.size,
                    totalSizeBytes = videosInFolder.sumOf { it.sizeBytes },
                    isHidden = isHidden,
                    videos = videosInFolder
                )
            }.sortedBy { it.name.lowercase() }
    }

    override val continueWatchingVideos: Flow<List<VideoItem>> = videoItems.map { videos ->
        videos.filter { it.playbackPositionMs > 0 && it.durationMs > 0 && it.playbackPositionMs < (it.durationMs * 0.95).toLong() }
            .sortedByDescending { it.lastWatchedTimestamp }
    }

    override val recentlyWatchedVideos: Flow<List<VideoItem>> = videoItems.map { videos ->
        videos.filter { it.lastWatchedTimestamp > 0 }
            .sortedByDescending { it.lastWatchedTimestamp }
    }

    override val favoriteVideos: Flow<List<VideoItem>> = videoItems.map { videos ->
        videos.filter { it.isFavorite }
    }

    override val folders: Flow<List<MediaFolder>> = combine(musicFolders, videoFolders) { musicF, videoF ->
        val map = mutableMapOf<String, MediaFolder>()
        musicF.forEach { mf ->
            map[mf.path] = MediaFolder(
                id = mf.path,
                name = mf.name,
                path = mf.path,
                trackCount = mf.trackCount,
                videoCount = 0
            )
        }
        videoF.forEach { vf ->
            val existing = map[vf.path]
            if (existing != null) {
                map[vf.path] = existing.copy(videoCount = vf.videoCount)
            } else {
                map[vf.path] = MediaFolder(
                    id = vf.path,
                    name = vf.name,
                    path = vf.path,
                    trackCount = 0,
                    videoCount = vf.videoCount
                )
            }
        }
        map.values.sortedBy { it.name.lowercase() }
    }

    init {
        // Migrate legacy SharedPreferences to Room if present
        CoroutineScope(Dispatchers.IO).launch {
            migrateLegacyPrefsToRoom()
        }
    }

    private suspend fun migrateLegacyPrefsToRoom() {
        try {
            val videoPrefs = context.getSharedPreferences("mt_player_video_prefs", Context.MODE_PRIVATE)
            val favVideoIds = videoPrefs.getStringSet("favorite_video_ids", null)
            if (!favVideoIds.isNullOrEmpty()) {
                dao.insertFavoriteVideos(favVideoIds.map { FavoriteVideoEntity(it) })
            }
            val hiddenFolders = videoPrefs.getStringSet("hidden_video_folders", null)
            if (!hiddenFolders.isNullOrEmpty()) {
                hiddenFolders.forEach { path ->
                    dao.insertHiddenFolder(HiddenFolderEntity(folderPath = path, mediaType = "VIDEO"))
                }
            }

            // Ensure starter playlists exist
            val existingPlaylists = dao.getAllPlaylistsDirect()
            if (existingPlaylists.isEmpty()) {
                dao.insertPlaylists(
                    listOf(
                        PlaylistEntity(id = "favorites_default", name = "Favorite Tracks"),
                        PlaylistEntity(id = "workout_mix", name = "Workout Energy"),
                        PlaylistEntity(id = "chill_vibes", name = "Late Night Chill")
                    )
                )
            }
        } catch (_: Exception) {}
    }

    override fun updatePermissionStatus(granted: Boolean, permanentlyDenied: Boolean) {
        _storageStats.value = _storageStats.value.copy(
            hasStoragePermission = granted,
            permissionPermanentlyDenied = permanentlyDenied
        )
    }

    override suspend fun scanDeviceMedia() = withContext(Dispatchers.IO) {
        _storageStats.value = _storageStats.value.copy(isScanning = true)

        val scannedAudio = mutableListOf<AudioTrack>()
        val scannedVideos = mutableListOf<VideoItem>()

        try {
            val resolver: ContentResolver = context.contentResolver

            // 1. Audio MediaStore Query
            val audioUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI
            }

            val audioProjection = mutableListOf(
                MediaStore.Audio.Media._ID,
                MediaStore.Audio.Media.TITLE,
                MediaStore.Audio.Media.ARTIST,
                MediaStore.Audio.Media.ALBUM,
                MediaStore.Audio.Media.DURATION,
                MediaStore.Audio.Media.SIZE,
                MediaStore.Audio.Media.ALBUM_ID,
                MediaStore.Audio.Media.DATE_ADDED
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Audio.Media.RELATIVE_PATH)
                    add(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                }
                add(MediaStore.Audio.Media.DATA)
            }.toTypedArray()

            val audioSelection = "${MediaStore.Audio.Media.IS_MUSIC} != 0 OR ${MediaStore.Audio.Media.DURATION} > 10000"

            val audioCursor: Cursor? = resolver.query(
                audioUri,
                audioProjection,
                audioSelection,
                null,
                "${MediaStore.Audio.Media.TITLE} ASC"
            )

            audioCursor?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Audio.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Audio.Media.TITLE)
                val artistCol = cursor.getColumnIndex(MediaStore.Audio.Media.ARTIST)
                val albumCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM)
                val durationCol = cursor.getColumnIndex(MediaStore.Audio.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Audio.Media.SIZE)
                val albumIdCol = cursor.getColumnIndex(MediaStore.Audio.Media.ALBUM_ID)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATE_ADDED)
                val dataCol = cursor.getColumnIndex(MediaStore.Audio.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.RELATIVE_PATH)
                } else -1
                val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Audio.Media.BUCKET_DISPLAY_NAME)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "Unknown Title" else "Unknown Title"
                    val artist = if (artistCol >= 0) cursor.getString(artistCol) ?: "Unknown Artist" else "Unknown Artist"
                    val album = if (albumCol >= 0) cursor.getString(albumCol) ?: "Unknown Album" else "Unknown Album"
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val albumId = if (albumIdCol >= 0) cursor.getLong(albumIdCol) else 0L
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) * 1000L else 0L

                    var folderName = "Music"
                    var folderPath = ""

                    if (bucketCol >= 0 && !cursor.isNull(bucketCol)) {
                        folderName = cursor.getString(bucketCol) ?: "Music"
                    } else if (relPathCol >= 0 && !cursor.isNull(relPathCol)) {
                        val rel = cursor.getString(relPathCol) ?: ""
                        val parts = rel.trim('/').split('/')
                        folderName = parts.lastOrNull()?.ifBlank { "Music" } ?: "Music"
                        folderPath = rel
                    } else if (dataCol >= 0 && !cursor.isNull(dataCol)) {
                        val path = cursor.getString(dataCol) ?: ""
                        val parentFile = File(path).parentFile
                        if (parentFile != null) {
                            folderName = parentFile.name
                            folderPath = parentFile.absolutePath
                        }
                    }

                    if (folderPath.isBlank() && dataCol >= 0 && !cursor.isNull(dataCol)) {
                        folderPath = File(cursor.getString(dataCol) ?: "").parentFile?.absolutePath ?: ""
                    }

                    val trackUri = ContentUris.withAppendedId(audioUri, id).toString()
                    val albumArtUri = ContentUris.withAppendedId(
                        Uri.parse("content://media/external/audio/albumart"),
                        albumId
                    ).toString()

                    scannedAudio.add(
                        AudioTrack(
                            id = id.toString(),
                            title = title,
                            artist = artist,
                            album = album,
                            durationMs = duration,
                            uri = trackUri,
                            albumArtUri = albumArtUri,
                            albumId = albumId,
                            sizeBytes = size,
                            folderName = folderName,
                            folderPath = folderPath,
                            dateAdded = dateAdded,
                            isFavorite = false,
                            playCount = 0
                        )
                    )
                }
            }

            // 2. Video MediaStore Query
            val videoUri: Uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL)
            } else {
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI
            }

            val videoProjection = mutableListOf(
                MediaStore.Video.Media._ID,
                MediaStore.Video.Media.TITLE,
                MediaStore.Video.Media.DURATION,
                MediaStore.Video.Media.SIZE,
                MediaStore.Video.Media.DATE_ADDED,
                MediaStore.Video.Media.WIDTH,
                MediaStore.Video.Media.HEIGHT
            ).apply {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    add(MediaStore.Video.Media.RELATIVE_PATH)
                    add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                }
                add(MediaStore.Video.Media.DATA)
            }.toTypedArray()

            val videoCursor: Cursor? = resolver.query(
                videoUri,
                videoProjection,
                null,
                null,
                "${MediaStore.Video.Media.DATE_ADDED} DESC"
            )

            videoCursor?.use { cursor ->
                val idCol = cursor.getColumnIndex(MediaStore.Video.Media._ID)
                val titleCol = cursor.getColumnIndex(MediaStore.Video.Media.TITLE)
                val durationCol = cursor.getColumnIndex(MediaStore.Video.Media.DURATION)
                val sizeCol = cursor.getColumnIndex(MediaStore.Video.Media.SIZE)
                val dateAddedCol = cursor.getColumnIndex(MediaStore.Video.Media.DATE_ADDED)
                val widthCol = cursor.getColumnIndex(MediaStore.Video.Media.WIDTH)
                val heightCol = cursor.getColumnIndex(MediaStore.Video.Media.HEIGHT)
                val dataCol = cursor.getColumnIndex(MediaStore.Video.Media.DATA)
                val relPathCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.RELATIVE_PATH)
                } else -1
                val bucketCol = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                    cursor.getColumnIndex(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
                } else -1

                while (cursor.moveToNext()) {
                    val id = cursor.getLong(idCol)
                    val title = if (titleCol >= 0) cursor.getString(titleCol) ?: "Video $id" else "Video $id"
                    val duration = if (durationCol >= 0) cursor.getLong(durationCol) else 0L
                    val size = if (sizeCol >= 0) cursor.getLong(sizeCol) else 0L
                    val dateAdded = if (dateAddedCol >= 0) cursor.getLong(dateAddedCol) * 1000L else 0L
                    val width = if (widthCol >= 0) cursor.getInt(widthCol) else 0
                    val height = if (heightCol >= 0) cursor.getInt(heightCol) else 0

                    var folderName = "Videos"
                    var folderPath = ""

                    if (bucketCol >= 0 && !cursor.isNull(bucketCol)) {
                        folderName = cursor.getString(bucketCol) ?: "Videos"
                    } else if (relPathCol >= 0 && !cursor.isNull(relPathCol)) {
                        val rel = cursor.getString(relPathCol) ?: ""
                        val parts = rel.trim('/').split('/')
                        folderName = parts.lastOrNull()?.ifBlank { "Videos" } ?: "Videos"
                        folderPath = rel
                    } else if (dataCol >= 0 && !cursor.isNull(dataCol)) {
                        val path = cursor.getString(dataCol) ?: ""
                        val parentFile = File(path).parentFile
                        if (parentFile != null) {
                            folderName = parentFile.name
                            folderPath = parentFile.absolutePath
                        }
                    }

                    if (folderPath.isBlank() && dataCol >= 0 && !cursor.isNull(dataCol)) {
                        folderPath = File(cursor.getString(dataCol) ?: "").parentFile?.absolutePath ?: ""
                    }

                    val uri = ContentUris.withAppendedId(videoUri, id).toString()
                    val resolution = when {
                        width >= 3840 || height >= 2160 -> "4K"
                        width >= 1920 || height >= 1080 -> "1080p"
                        width >= 1280 || height >= 720 -> "720p"
                        width >= 854 || height >= 480 -> "480p"
                        width > 0 -> "${height}p"
                        else -> "HD"
                    }

                    scannedVideos.add(
                        VideoItem(
                            id = id.toString(),
                            title = title,
                            durationMs = duration,
                            uri = uri,
                            thumbnailUri = uri,
                            sizeBytes = size,
                            resolution = resolution,
                            width = width,
                            height = height,
                            folderName = folderName,
                            folderPath = folderPath,
                            dateAdded = dateAdded,
                            isFavorite = false,
                            playbackPositionMs = 0L,
                            lastWatchedTimestamp = 0L
                        )
                    )
                }
            }

            // Cleanup deleted / moved files safely in Room
            val validSongIds = scannedAudio.map { it.id }
            if (validSongIds.isNotEmpty()) {
                dao.cleanupOrphanFavoriteSongs(validSongIds)
                dao.cleanupOrphanPlaylistSongs(validSongIds)
                dao.cleanupOrphanListeningHistory(validSongIds)
            }

            val validVideoIds = scannedVideos.map { it.id }
            if (validVideoIds.isNotEmpty()) {
                dao.cleanupOrphanFavoriteVideos(validVideoIds)
            }

        } catch (_: Exception) {}

        _rawAudioTracks.value = scannedAudio
        _allScannedVideos.value = scannedVideos

        val totalAudioDuration = scannedAudio.sumOf { it.durationMs }
        val totalSize = scannedAudio.sumOf { it.sizeBytes } + scannedVideos.sumOf { it.sizeBytes }

        _storageStats.value = _storageStats.value.copy(
            audioCount = scannedAudio.size,
            videoCount = scannedVideos.size,
            totalAudioDurationMs = totalAudioDuration,
            totalStorageUsedBytes = totalSize,
            isScanning = false
        )
    }

    // 1. Favorite Songs
    override suspend fun toggleFavorite(trackId: String) {
        val currentFavs = favoriteSongIdsFlow.first()
        if (currentFavs.contains(trackId)) {
            dao.deleteFavoriteSong(trackId)
        } else {
            dao.insertFavoriteSong(FavoriteSongEntity(songId = trackId))
        }
    }

    override suspend fun addFavorites(trackIds: List<String>) {
        dao.insertFavoriteSongs(trackIds.map { FavoriteSongEntity(songId = it) })
    }

    // 3. Custom Playlists & 4. Song Relationships
    override suspend fun createPlaylist(name: String, trackIds: List<String>) {
        val playlistId = "playlist_${UUID.randomUUID().toString().take(8)}"
        dao.insertPlaylist(PlaylistEntity(id = playlistId, name = name))
        if (trackIds.isNotEmpty()) {
            val refs = trackIds.mapIndexed { index, songId ->
                PlaylistSongCrossRef(playlistId = playlistId, songId = songId, orderIndex = index)
            }
            dao.insertPlaylistSongRefs(refs)
        }
    }

    override suspend fun renamePlaylist(playlistId: String, newName: String) {
        dao.updatePlaylistName(playlistId, newName)
    }

    override suspend fun deletePlaylist(playlistId: String) {
        dao.deletePlaylist(playlistId)
        dao.clearSongsFromPlaylist(playlistId)
    }

    override suspend fun addTracksToPlaylist(playlistId: String, trackIds: List<String>) {
        val currentRefs = dao.getAllPlaylistSongCrossRefsDirect().filter { it.playlistId == playlistId }
        val existingSongIds = currentRefs.map { it.songId }.toSet()
        var nextOrder = currentRefs.size
        val newRefs = trackIds.filter { !existingSongIds.contains(it) }.map { songId ->
            PlaylistSongCrossRef(playlistId = playlistId, songId = songId, orderIndex = nextOrder++)
        }
        if (newRefs.isNotEmpty()) {
            dao.insertPlaylistSongRefs(newRefs)
        }
    }

    override suspend fun removeTrackFromPlaylist(playlistId: String, trackId: String) {
        dao.removeSongFromPlaylist(playlistId, trackId)
    }

    override suspend fun reorderPlaylist(playlistId: String, fromIndex: Int, toIndex: Int) {
        val currentRefs = dao.getAllPlaylistSongCrossRefsDirect()
            .filter { it.playlistId == playlistId }
            .sortedBy { it.orderIndex }
            .toMutableList()

        if (fromIndex in currentRefs.indices && toIndex in currentRefs.indices) {
            val item = currentRefs.removeAt(fromIndex)
            currentRefs.add(toIndex, item)
            dao.reorderPlaylistSongs(playlistId, currentRefs.map { it.songId })
        }
    }

    // 5. Listening History
    override suspend fun recordTrackPlayed(trackId: String) {
        dao.recordSongPlayed(trackId)
    }

    // 2. Favorite Videos
    override suspend fun toggleVideoFavorite(videoId: String) {
        val currentFavs = favoriteVideoIds.first()
        if (currentFavs.contains(videoId)) {
            dao.deleteFavoriteVideo(videoId)
        } else {
            dao.insertFavoriteVideo(FavoriteVideoEntity(videoId = videoId))
        }
    }

    // 11. Hidden Folders
    override suspend fun hideVideoFolder(folderPath: String) {
        if (folderPath.isBlank()) return
        dao.insertHiddenFolder(HiddenFolderEntity(folderPath = folderPath, mediaType = "VIDEO"))
    }

    override suspend fun unhideVideoFolder(folderPath: String) {
        dao.deleteHiddenFolder(folderPath)
    }

    override suspend fun unhideAllVideoFolders() {
        dao.deleteAllHiddenFolders()
    }

    // 6. Video Watching History & 8. Last Video Playback Position
    override suspend fun updateVideoProgress(videoId: String, positionMs: Long, durationMs: Long) {
        dao.recordVideoWatched(videoId)
        val isCompleted = durationMs > 0 && positionMs >= (durationMs * 0.95).toLong()
        if (isCompleted || positionMs <= 0L) {
            dao.deleteVideoPlaybackPosition(videoId)
        } else {
            dao.saveVideoPlaybackPosition(
                LastVideoPlaybackEntity(videoId = videoId, positionMs = positionMs, durationMs = durationMs)
            )
        }
    }

    override suspend fun clearVideoProgress(videoId: String) {
        dao.deleteVideoPlaybackPosition(videoId)
    }

    // 7. Last Music Playback Position
    override suspend fun saveMusicPlaybackPosition(songId: String, positionMs: Long, durationMs: Long) {
        dao.saveMusicPlaybackPosition(
            LastMusicPlaybackEntity(songId = songId, positionMs = positionMs, durationMs = durationMs)
        )
    }

    // 9. Equalizer Preferences
    override suspend fun saveEqualizerPreference(
        isEnabled: Boolean,
        presetName: String,
        bassBoost: Int,
        virtualizer: Int,
        bandLevelsCsv: String
    ) {
        dao.saveEqualizerPreference(
            EqualizerPreferenceEntity(
                id = 1,
                isEnabled = isEnabled,
                presetName = presetName,
                bassBoost = bassBoost,
                virtualizer = virtualizer,
                bandLevelsCsv = bandLevelsCsv
            )
        )
    }

    // 10. Theme Preferences
    override suspend fun saveThemePreference(
        themeMode: String,
        accentColorHex: String,
        pureBlackAmoled: Boolean
    ) {
        dao.saveThemePreference(
            ThemePreferenceEntity(
                id = 1,
                themeMode = themeMode,
                accentColorHex = accentColorHex,
                pureBlackAmoled = pureBlackAmoled
            )
        )
    }

    // 12. Sleep Timer Preferences
    override suspend fun saveSleepTimerPreference(
        durationMinutes: Int,
        finishLastSong: Boolean,
        fadeOutEnabled: Boolean
    ) {
        dao.saveSleepTimerPreference(
            SleepTimerPreferenceEntity(
                id = 1,
                defaultDurationMinutes = durationMinutes,
                finishLastSong = finishLastSong,
                fadeOutEnabled = fadeOutEnabled
            )
        )
    }

    override suspend fun clearListeningHistory() {
        dao.clearListeningHistory()
    }

    override suspend fun clearWatchingHistory() {
        dao.clearVideoWatchingHistory()
    }
}
