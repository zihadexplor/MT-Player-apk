package com.example

import android.app.Application
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.example.ui.image.VideoThumbnailFetcher

class MTApplication : Application(), ImageLoaderFactory {
    override fun newImageLoader(): ImageLoader = ImageLoader.Builder(this)
        .components {
            add(VideoThumbnailFetcher.Keyer())
            add(VideoThumbnailFetcher.Factory(this@MTApplication))
        }
        .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.20).build() }
        .diskCache { DiskCache.Builder().directory(cacheDir.resolve("image_cache")).maxSizeBytes(64L * 1024 * 1024).build() }
        .crossfade(150)
        .respectCacheHeaders(false)
        .build()
}
