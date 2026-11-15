package com.mtplayer.app.core

import android.app.ActivityManager
import android.app.UiModeManager
import android.content.Context
import android.content.pm.PackageManager
import android.content.res.Configuration

/** Coarse device profile used to size caches and choose TV-friendly behaviour. */
data class DeviceClass(
    val isLowRam: Boolean,
    val isTv: Boolean,
    val memoryClassMb: Int,
) {
    /** Fraction of the app heap Coil's memory cache may use. */
    val imageMemoryCacheFraction: Double get() = when {
        isLowRam || memoryClassMb <= 192 -> 0.10
        memoryClassMb <= 384 -> 0.18
        else -> 0.25
    }

    /** Pixel size for list artwork thumbnails. */
    val thumbnailPx: Int get() = if (isLowRam) 160 else 256

    companion object {
        fun detect(context: Context): DeviceClass {
            val am = context.getSystemService(ActivityManager::class.java)
            val ui = context.getSystemService(UiModeManager::class.java)
            val pm = context.packageManager
            val tv = ui?.currentModeType == Configuration.UI_MODE_TYPE_TELEVISION ||
                pm.hasSystemFeature(PackageManager.FEATURE_LEANBACK)
            return DeviceClass(
                isLowRam = am?.isLowRamDevice == true,
                isTv = tv,
                memoryClassMb = am?.memoryClass ?: 128,
            )
        }
    }
}
