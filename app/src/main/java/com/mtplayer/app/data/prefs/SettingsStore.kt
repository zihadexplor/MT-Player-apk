package com.mtplayer.app.data.prefs

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.MutablePreferences
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.emptyPreferences
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.mtplayer.app.data.media.SongSort
import com.mtplayer.app.data.media.VideoSort
import com.mtplayer.app.designsystem.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import java.io.IOException

enum class VideoLayout { GRID, LIST }

data class Settings(
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val pureBlack: Boolean = false,
    val dynamicColor: Boolean = false,
    val artworkTint: Boolean = true,
    // Music
    val gapless: Boolean = true,
    val pauseOnDisconnect: Boolean = true,
    val minAudioDurationSec: Int = 30,
    val songSort: SongSort = SongSort.TITLE,
    val sleepFinishSong: Boolean = false,
    val sleepFade: Boolean = true,
    val sleepDefaultMinutes: Int = 30,
    // Equalizer
    val eqEnabled: Boolean = false,
    val eqPreset: Int = -1,
    val eqBands: List<Int> = emptyList(),
    val bassBoost: Int = 0,
    val virtualizer: Int = 0,
    // Video
    val resumeVideo: Boolean = true,
    val defaultVideoSpeed: Float = 1f,
    val doubleTapSeekSec: Int = 10,
    val gestureBrightness: Boolean = true,
    val gestureVolume: Boolean = true,
    val gestureSeek: Boolean = true,
    val videoBackgroundAudio: Boolean = false,
    val autoPip: Boolean = true,
    val videoLayout: VideoLayout = VideoLayout.GRID,
    val videoSort: VideoSort = VideoSort.DATE_ADDED,
    // Updates
    val autoUpdateCheck: Boolean = true,
    val lastUpdateCheck: Long = 0,
    // Onboarding
    val permissionAskedOnce: Boolean = false,
)

private val Context.settingsDataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

class SettingsStore(context: Context) {
    private val store = context.applicationContext.settingsDataStore

    private object K {
        val themeMode = stringPreferencesKey("theme_mode")
        val pureBlack = booleanPreferencesKey("pure_black")
        val dynamicColor = booleanPreferencesKey("dynamic_color")
        val artworkTint = booleanPreferencesKey("artwork_tint")
        val gapless = booleanPreferencesKey("gapless")
        val pauseOnDisconnect = booleanPreferencesKey("pause_on_disconnect")
        val minAudio = intPreferencesKey("min_audio_duration_sec")
        val songSort = stringPreferencesKey("song_sort")
        val sleepFinish = booleanPreferencesKey("sleep_finish_song")
        val sleepFade = booleanPreferencesKey("sleep_fade")
        val sleepDefault = intPreferencesKey("sleep_default_minutes")
        val eqEnabled = booleanPreferencesKey("eq_enabled")
        val eqPreset = intPreferencesKey("eq_preset")
        val eqBands = stringPreferencesKey("eq_bands")
        val bassBoost = intPreferencesKey("bass_boost")
        val virtualizer = intPreferencesKey("virtualizer")
        val resumeVideo = booleanPreferencesKey("resume_video")
        val videoSpeed = floatPreferencesKey("default_video_speed")
        val doubleTap = intPreferencesKey("double_tap_seek_sec")
        val gBrightness = booleanPreferencesKey("gesture_brightness")
        val gVolume = booleanPreferencesKey("gesture_volume")
        val gSeek = booleanPreferencesKey("gesture_seek")
        val videoBgAudio = booleanPreferencesKey("video_background_audio")
        val autoPip = booleanPreferencesKey("auto_pip")
        val videoLayout = stringPreferencesKey("video_layout")
        val videoSort = stringPreferencesKey("video_sort")
        val autoUpdate = booleanPreferencesKey("auto_update_check")
        val lastUpdate = longPreferencesKey("last_update_check")
        val permAsked = booleanPreferencesKey("permission_asked_once")
    }

    val settings: Flow<Settings> = store.data
        .catch { if (it is IOException) emit(emptyPreferences()) else throw it }
        .map(::read)

    suspend fun current(): Settings = settings.first()

    private fun read(p: Preferences): Settings {
        val d = Settings()
        return Settings(
            themeMode = p[K.themeMode]?.let { runCatching { ThemeMode.valueOf(it) }.getOrNull() } ?: d.themeMode,
            pureBlack = p[K.pureBlack] ?: d.pureBlack,
            dynamicColor = p[K.dynamicColor] ?: d.dynamicColor,
            artworkTint = p[K.artworkTint] ?: d.artworkTint,
            gapless = p[K.gapless] ?: d.gapless,
            pauseOnDisconnect = p[K.pauseOnDisconnect] ?: d.pauseOnDisconnect,
            minAudioDurationSec = p[K.minAudio] ?: d.minAudioDurationSec,
            songSort = p[K.songSort]?.let { runCatching { SongSort.valueOf(it) }.getOrNull() } ?: d.songSort,
            sleepFinishSong = p[K.sleepFinish] ?: d.sleepFinishSong,
            sleepFade = p[K.sleepFade] ?: d.sleepFade,
            sleepDefaultMinutes = p[K.sleepDefault] ?: d.sleepDefaultMinutes,
            eqEnabled = p[K.eqEnabled] ?: d.eqEnabled,
            eqPreset = p[K.eqPreset] ?: d.eqPreset,
            eqBands = p[K.eqBands]?.split(',')?.mapNotNull { it.trim().toIntOrNull() } ?: d.eqBands,
            bassBoost = p[K.bassBoost] ?: d.bassBoost,
            virtualizer = p[K.virtualizer] ?: d.virtualizer,
            resumeVideo = p[K.resumeVideo] ?: d.resumeVideo,
            defaultVideoSpeed = p[K.videoSpeed] ?: d.defaultVideoSpeed,
            doubleTapSeekSec = p[K.doubleTap] ?: d.doubleTapSeekSec,
            gestureBrightness = p[K.gBrightness] ?: d.gestureBrightness,
            gestureVolume = p[K.gVolume] ?: d.gestureVolume,
            gestureSeek = p[K.gSeek] ?: d.gestureSeek,
            videoBackgroundAudio = p[K.videoBgAudio] ?: d.videoBackgroundAudio,
            autoPip = p[K.autoPip] ?: d.autoPip,
            videoLayout = p[K.videoLayout]?.let { runCatching { VideoLayout.valueOf(it) }.getOrNull() } ?: d.videoLayout,
            videoSort = p[K.videoSort]?.let { runCatching { VideoSort.valueOf(it) }.getOrNull() } ?: d.videoSort,
            autoUpdateCheck = p[K.autoUpdate] ?: d.autoUpdateCheck,
            lastUpdateCheck = p[K.lastUpdate] ?: d.lastUpdateCheck,
            permissionAskedOnce = p[K.permAsked] ?: d.permissionAskedOnce,
        )
    }

    private fun write(p: MutablePreferences, s: Settings) {
        p[K.themeMode] = s.themeMode.name
        p[K.pureBlack] = s.pureBlack
        p[K.dynamicColor] = s.dynamicColor
        p[K.artworkTint] = s.artworkTint
        p[K.gapless] = s.gapless
        p[K.pauseOnDisconnect] = s.pauseOnDisconnect
        p[K.minAudio] = s.minAudioDurationSec.coerceIn(0, 600)
        p[K.songSort] = s.songSort.name
        p[K.sleepFinish] = s.sleepFinishSong
        p[K.sleepFade] = s.sleepFade
        p[K.sleepDefault] = s.sleepDefaultMinutes.coerceIn(1, 600)
        p[K.eqEnabled] = s.eqEnabled
        p[K.eqPreset] = s.eqPreset
        p[K.eqBands] = s.eqBands.joinToString(",")
        p[K.bassBoost] = s.bassBoost.coerceIn(0, 1000)
        p[K.virtualizer] = s.virtualizer.coerceIn(0, 1000)
        p[K.resumeVideo] = s.resumeVideo
        p[K.videoSpeed] = s.defaultVideoSpeed.coerceIn(0.25f, 4f)
        p[K.doubleTap] = s.doubleTapSeekSec.coerceIn(5, 60)
        p[K.gBrightness] = s.gestureBrightness
        p[K.gVolume] = s.gestureVolume
        p[K.gSeek] = s.gestureSeek
        p[K.videoBgAudio] = s.videoBackgroundAudio
        p[K.autoPip] = s.autoPip
        p[K.videoLayout] = s.videoLayout.name
        p[K.videoSort] = s.videoSort.name
        p[K.autoUpdate] = s.autoUpdateCheck
        p[K.lastUpdate] = s.lastUpdateCheck
        p[K.permAsked] = s.permissionAskedOnce
    }

    /** Atomic read-modify-write of the whole settings object. */
    suspend fun update(transform: (Settings) -> Settings) {
        store.edit { p -> write(p, transform(read(p))) }
    }
}
