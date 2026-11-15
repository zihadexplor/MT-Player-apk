package com.mtplayer.app.ui.image

import android.content.Context
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import android.util.Size
import coil.ImageLoader
import coil.decode.DataSource
import coil.fetch.DrawableResult
import coil.fetch.FetchResult
import coil.fetch.Fetcher
import coil.key.Keyer
import coil.request.Options
import coil.size.pxOrElse
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** Model for a video thumbnail request. Loaded from MediaStore's thumbnail cache, never by decoding the whole file. */
data class VideoThumb(val id: Long, val uri: Uri)

class VideoFrameFetcher(
    private val data: VideoThumb,
    private val options: Options,
    private val context: Context,
) : Fetcher {
    override suspend fun fetch(): FetchResult = withContext(Dispatchers.IO) {
        val w = options.size.width.pxOrElse { 384 }.coerceIn(64, 1024)
        val h = options.size.height.pxOrElse { w * 9 / 16 }.coerceIn(64, 1024)
        val bitmap: Bitmap = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            context.contentResolver.loadThumbnail(data.uri, Size(w, h), null)
        } else {
            @Suppress("DEPRECATION")
            MediaStore.Video.Thumbnails.getThumbnail(context.contentResolver, data.id, MediaStore.Video.Thumbnails.MINI_KIND, null)
                ?: error("No thumbnail")
        }
        DrawableResult(BitmapDrawable(context.resources, bitmap), isSampled = true, dataSource = DataSource.DISK)
    }

    class Factory(private val context: Context) : Fetcher.Factory<VideoThumb> {
        override fun create(data: VideoThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            VideoFrameFetcher(data, options, context.applicationContext)
    }

    class Key : Keyer<VideoThumb> {
        override fun key(data: VideoThumb, options: Options): String = "vthumb:${data.id}:${options.size}"
    }
}
