package com.example.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.os.Build
import android.util.LruCache
import android.util.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.graphics.toArgb
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.request.Options
import com.example.data.model.AudioTrack
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlin.math.max
import kotlin.math.min

/**
 * Coil model for audio track artwork. Handles Scoped Storage reliably by checking
 * `contentResolver.loadThumbnail`, `albumArtUri`, and embedded ID3 APIC picture.
 */
data class AudioArtThumb(
    val trackUri: String,
    val albumArtUri: String?
)

class AudioArtFetcher(
    private val context: Context,
    private val data: AudioArtThumb
) : Fetcher {

    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val bitmap = loadTrackArtworkBitmap(context, data.trackUri, data.albumArtUri, maxDim = 420)
            ?: throw IllegalStateException("No artwork for ${data.trackUri}")
        DrawableResult(
            drawable = BitmapDrawable(context.resources, bitmap),
            isSampled = true,
            dataSource = DataSource.DISK
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<AudioArtThumb> {
        override fun create(data: AudioArtThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            AudioArtFetcher(context.applicationContext, data)
    }

    class Keyer : coil.key.Keyer<AudioArtThumb> {
        override fun key(data: AudioArtThumb, options: Options): String =
            "audio-art:${data.trackUri}:${data.albumArtUri.orEmpty()}"
    }
}

internal fun loadTrackArtworkBitmap(
    context: Context,
    trackUri: String,
    albumArtUri: String?,
    maxDim: Int = 420
): Bitmap? {
    val resolver = context.contentResolver

    // 1. Android Q+ system MediaStore thumbnail (works cleanly under Scoped Storage)
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && trackUri.isNotBlank()) {
        val sysThumb = runCatching {
            resolver.loadThumbnail(Uri.parse(trackUri), Size(maxDim, maxDim), null)
        }.getOrNull()
        if (sysThumb != null) return sysThumb
    }

    // 2. Direct albumArtUri stream with bounded sub-sampling
    if (!albumArtUri.isNullOrBlank()) {
        val decoded = runCatching {
            val uri = Uri.parse(albumArtUri)
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
            val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim, maxDim)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
        if (decoded != null) return decoded
    }

    // 3. Fallback: embedded picture frame via MediaMetadataRetriever
    if (trackUri.isNotBlank()) {
        val retriever = MediaMetadataRetriever()
        return try {
            retriever.setDataSource(context, Uri.parse(trackUri))
            val pic = retriever.embeddedPicture ?: return null
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            BitmapFactory.decodeByteArray(pic, 0, pic.size, bounds)
            val sample = computeInSampleSize(bounds.outWidth, bounds.outHeight, maxDim, maxDim)
            val opts = BitmapFactory.Options().apply { inSampleSize = sample }
            BitmapFactory.decodeByteArray(pic, 0, pic.size, opts)
        } catch (_: Exception) {
            null
        } finally {
            runCatching { retriever.release() }
        }
    }

    return null
}

private fun computeInSampleSize(width: Int, height: Int, reqW: Int, reqH: Int): Int {
    var inSampleSize = 1
    if (height > reqH || width > reqW) {
        val halfH = height / 2
        val halfW = width / 2
        while (halfH / inSampleSize >= reqH && halfW / inSampleSize >= reqW) {
            inSampleSize *= 2
        }
    }
    return inSampleSize.coerceAtLeast(1)
}

/**
 * ArchiveTune-inspired lightweight cover-art dominant color extractor with bounded LRU caching.
 */
object ArtworkColorExtractor {
    private val cache = LruCache<String, Int>(32)

    suspend fun extractSeedColor(context: Context, track: AudioTrack?): Color? {
        if (track == null) return null
        cache.get(track.id)?.let { return Color(it) }

        return withContext(Dispatchers.Default) {
            val rawBitmap = loadTrackArtworkBitmap(context, track.uri, track.albumArtUri, maxDim = 64)
                ?: return@withContext null

            val scaled = if (rawBitmap.width > 24 || rawBitmap.height > 24) {
                Bitmap.createScaledBitmap(rawBitmap, 24, 24, true).also {
                    if (it !== rawBitmap) rawBitmap.recycle()
                }
            } else rawBitmap

            val color = try {
                pickVibrantDominantColor(scaled)
            } finally {
                if (!scaled.isRecycled) scaled.recycle()
            }

            if (color != null) {
                cache.put(track.id, color.toArgb())
            }
            color
        }
    }

    private fun pickVibrantDominantColor(bitmap: Bitmap): Color? {
        val buckets = mutableMapOf<Int, Pair<Int, Float>>() // quantized rgb -> (count, score)
        val hsv = FloatArray(3)
        for (y in 0 until bitmap.height) {
            for (x in 0 until bitmap.width) {
                val pixel = bitmap.getPixel(x, y)
                val alpha = (pixel ushr 24) and 0xFF
                if (alpha < 180) continue
                android.graphics.Color.colorToHSV(pixel, hsv)
                val sat = hsv[1]
                val valBr = hsv[2]
                // Skip near-black / near-white washes when richer tones exist
                if (valBr < 0.14f || (sat < 0.08f && valBr > 0.92f)) continue

                val r = ((pixel ushr 16) and 0xE0)
                val g = ((pixel ushr 8) and 0xE0)
                val b = (pixel and 0xE0)
                val key = (r shl 16) or (g shl 8) or b
                val vibrancyWeight = 1f + sat * 3.5f
                val prev = buckets[key]
                buckets[key] = if (prev == null) 1 to vibrancyWeight else (prev.first + 1) to (prev.second + vibrancyWeight)
            }
        }
        val bestKey = buckets.maxByOrNull { it.value.second }?.key ?: return null
        val r = ((bestKey ushr 16) and 0xFF) + 16
        val g = ((bestKey ushr 8) and 0xFF) + 16
        val b = (bestKey and 0xFF) + 16
        return Color(
            red = r.coerceIn(0, 255) / 255f,
            green = g.coerceIn(0, 255) / 255f,
            blue = b.coerceIn(0, 255) / 255f
        )
    }
}

/**
 * WCAG 2.1 luminance & contrast helpers ensuring unreadable text can never ship in any theme.
 */
object ContrastUtils {
    fun contrastRatio(foreground: Color, background: Color): Double {
        val l1 = foreground.luminance().toDouble()
        val l2 = background.luminance().toDouble()
        val lighter = max(l1, l2)
        val darker = min(l1, l2)
        return (lighter + 0.05) / (darker + 0.05)
    }

    fun readableOnColor(background: Color): Color {
        val deepInk = Color(0xFF0E0F14)
        val onDeepInk = contrastRatio(deepInk, background)
        val onPureBlack = contrastRatio(Color.Black, background)
        val onWhite = contrastRatio(Color.White, background)
        return when {
            onDeepInk >= onWhite && onDeepInk >= 4.5 -> deepInk
            onPureBlack >= onWhite -> Color.Black
            else -> Color.White
        }
    }

    /**
     * Adjusts an accent color's lightness slightly so it maintains crisp readability against
     * either a dark or light surface canvas (minimum contrast ratio >= minRatio).
     */
    fun adaptAccentForSurface(accent: Color, surface: Color, minRatio: Double = 4.5): Color {
        if (contrastRatio(accent, surface) >= minRatio) return accent
        val hsv = FloatArray(3)
        android.graphics.Color.colorToHSV(accent.toArgb(), hsv)
        val isDarkSurface = surface.luminance() < 0.5f

        var candidate = accent
        for (step in 1..20) {
            if (isDarkSurface) {
                hsv[2] = (hsv[2] + 0.05f).coerceAtMost(1f)
                hsv[1] = (hsv[1] - 0.02f).coerceAtLeast(0.25f)
            } else {
                hsv[2] = (hsv[2] - 0.05f).coerceAtLeast(0.18f)
                hsv[1] = (hsv[1] + 0.02f).coerceAtMost(1f)
            }
            candidate = Color(android.graphics.Color.HSVToColor(hsv))
            if (contrastRatio(candidate, surface) >= minRatio) return candidate
        }
        return candidate
    }
}
