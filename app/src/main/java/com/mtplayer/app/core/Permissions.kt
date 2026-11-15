package com.mtplayer.app.core

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat

object Permissions {
    /** Runtime permissions needed to read the user's music. */
    val audio: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_AUDIO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val video: Array<String> = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        arrayOf(Manifest.permission.READ_MEDIA_VIDEO)
    else arrayOf(Manifest.permission.READ_EXTERNAL_STORAGE)

    val media: Array<String> = (audio + video).distinct().toTypedArray()

    val notifications: String? = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU)
        Manifest.permission.POST_NOTIFICATIONS else null

    fun granted(context: Context, permission: String): Boolean =
        ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    fun hasAudio(context: Context) = audio.all { granted(context, it) }

    /**
     * On Android 14+ the user may grant "selected photos and videos" only. We treat that as
     * partial video access and still show what MediaStore returns.
     */
    fun hasVideo(context: Context): Boolean = video.all { granted(context, it) } ||
        (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE &&
            granted(context, Manifest.permission.READ_MEDIA_VISUAL_USER_SELECTED))

    fun hasNotifications(context: Context) = notifications?.let { granted(context, it) } ?: true
}
