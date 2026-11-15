package com.mtplayer.app.playback

import android.media.audiofx.BassBoost
import android.media.audiofx.Equalizer
import android.media.audiofx.Virtualizer
import androidx.compose.runtime.Immutable
import com.mtplayer.app.data.prefs.Settings
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

@Immutable
data class EqualizerInfo(
    val available: Boolean,
    val bandFrequenciesHz: List<Int> = emptyList(),
    val minLevel: Int = -1500,
    val maxLevel: Int = 1500,
    val presets: List<String> = emptyList(),
    val presetLevels: List<List<Int>> = emptyList(),
    val bassBoostSupported: Boolean = false,
    val virtualizerSupported: Boolean = false,
)

/**
 * Owns the platform audio effects attached to the music player's audio session. Effects are
 * optional hardware features; every call is guarded so a missing or busy effect engine never
 * crashes playback. The UI reads [info] and writes settings; the service calls [apply].
 */
class AudioEffects {
    private var equalizer: Equalizer? = null
    private var bassBoost: BassBoost? = null
    private var virtualizer: Virtualizer? = null
    private var sessionId = 0
    private var last: Settings? = null

    private val _info = MutableStateFlow(EqualizerInfo(available = false))
    val info: StateFlow<EqualizerInfo> = _info.asStateFlow()

    @Synchronized
    fun attach(audioSessionId: Int) {
        if (audioSessionId == sessionId && equalizer != null) return
        release()
        if (audioSessionId == 0) return
        sessionId = audioSessionId
        equalizer = runCatching { Equalizer(0, audioSessionId) }.getOrNull()
        bassBoost = runCatching { BassBoost(0, audioSessionId) }.getOrNull()
        virtualizer = runCatching { Virtualizer(0, audioSessionId) }.getOrNull()
        _info.value = readInfo()
        last?.let { apply(it) }
    }

    private fun readInfo(): EqualizerInfo {
        val eq = equalizer ?: return EqualizerInfo(
            available = false,
            bassBoostSupported = bassBoost?.strengthSupported == true,
            virtualizerSupported = virtualizer?.strengthSupported == true,
        )
        return runCatching {
            val bands = eq.numberOfBands.toInt()
            val range = eq.bandLevelRange
            val presetCount = eq.numberOfPresets.toInt()
            val names = List(presetCount) { eq.getPresetName(it.toShort()) }
            val levels = List(presetCount) { p ->
                eq.usePreset(p.toShort())
                List(bands) { b -> eq.getBandLevel(b.toShort()).toInt() }
            }
            EqualizerInfo(
                available = bands > 0,
                bandFrequenciesHz = List(bands) { eq.getCenterFreq(it.toShort()) / 1000 },
                minLevel = range[0].toInt(),
                maxLevel = range[1].toInt(),
                presets = names,
                presetLevels = levels,
                bassBoostSupported = bassBoost?.strengthSupported == true,
                virtualizerSupported = virtualizer?.strengthSupported == true,
            )
        }.getOrElse { EqualizerInfo(available = false) }
    }

    @Synchronized
    fun apply(settings: Settings) {
        last = settings
        val info = _info.value
        equalizer?.let { eq ->
            runCatching {
                eq.enabled = settings.eqEnabled
                if (settings.eqEnabled) {
                    val levels = when {
                        settings.eqBands.size == info.bandFrequenciesHz.size -> settings.eqBands
                        settings.eqPreset in info.presetLevels.indices -> info.presetLevels[settings.eqPreset]
                        else -> List(info.bandFrequenciesHz.size) { 0 }
                    }
                    levels.forEachIndexed { band, level ->
                        eq.setBandLevel(band.toShort(), level.coerceIn(info.minLevel, info.maxLevel).toShort())
                    }
                }
            }
        }
        bassBoost?.let { bb ->
            runCatching {
                bb.enabled = settings.eqEnabled && settings.bassBoost > 0
                if (bb.strengthSupported) bb.setStrength(settings.bassBoost.coerceIn(0, 1000).toShort())
            }
        }
        virtualizer?.let { v ->
            runCatching {
                v.enabled = settings.eqEnabled && settings.virtualizer > 0
                if (v.strengthSupported) v.setStrength(settings.virtualizer.coerceIn(0, 1000).toShort())
            }
        }
    }

    @Synchronized
    fun release() {
        runCatching { equalizer?.release() }
        runCatching { bassBoost?.release() }
        runCatching { virtualizer?.release() }
        equalizer = null; bassBoost = null; virtualizer = null; sessionId = 0
    }
}
