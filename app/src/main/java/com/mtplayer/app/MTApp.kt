package com.mtplayer.app

import android.app.Application
import android.content.ComponentCallbacks2
import android.os.StrictMode
import androidx.lifecycle.ProcessLifecycleOwner
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.mtplayer.app.ui.image.VideoFrameFetcher
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch

class MTApp : Application(), ImageLoaderFactory {
    lateinit var container: AppContainer
        private set

    private val _importMessage = MutableStateFlow<String?>(null)
    /** One-shot message shown once after MT Player 1.x data was imported. */
    val importMessage: StateFlow<String?> = _importMessage

    fun consumeImportMessage() { _importMessage.value = null }

    override fun onCreate() {
        super.onCreate()
        if (BuildConfig.DEBUG) enableStrictMode()
        container = AppContainer(this)
        container.crashLog.install()
        ProcessLifecycleOwner.get().lifecycle.addObserver(container.player)
        container.appScope.launch {
            val report = runCatching { container.legacyImporter.importIfNeeded() }
                .onFailure { container.crashLog.record("legacy-import", it) }
                .getOrNull()
            if (report != null && !report.skipped && report.total > 0) {
                _importMessage.value = getString(
                    R.string.import_done,
                    report.favoriteSongs + report.favoriteVideos,
                    report.playlists,
                )
            }
        }
    }

    /** Debug only: log (never crash on) main-thread disk/network work and leaked closeables. */
    private fun enableStrictMode() {
        StrictMode.setThreadPolicy(
            StrictMode.ThreadPolicy.Builder().detectDiskReads().detectDiskWrites().detectNetwork()
                .penaltyLog().build(),
        )
        StrictMode.setVmPolicy(
            StrictMode.VmPolicy.Builder().detectLeakedClosableObjects().detectLeakedSqlLiteObjects()
                .detectActivityLeaks().detectLeakedRegistrationObjects().penaltyLog().build(),
        )
    }

    override fun newImageLoader(): ImageLoader {
        val device = container.deviceClass
        return ImageLoader.Builder(this)
            .components {
                add(VideoFrameFetcher.Factory(this@MTApp))
                add(VideoFrameFetcher.Key())
            }
            .memoryCache {
                MemoryCache.Builder(this).maxSizePercent(device.imageMemoryCacheFraction).build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("artwork"))
                    .maxSizeBytes(if (device.isLowRam) 32L * 1024 * 1024 else 128L * 1024 * 1024)
                    .build()
            }
            .crossfade(!device.isLowRam)
            .respectCacheHeaders(false)
            .build()
    }

    override fun onTrimMemory(level: Int) {
        super.onTrimMemory(level)
        if (level >= ComponentCallbacks2.TRIM_MEMORY_BACKGROUND) {
            coil.Coil.imageLoader(this).memoryCache?.clear()
        }
    }
}
