package com.mtplayer.app.core

import java.util.Locale

object Format {
    /** 3:07, 1:02:09. Negative or unknown values render as 0:00. */
    fun duration(ms: Long): String {
        val totalSec = (ms.coerceAtLeast(0) / 1000)
        val h = totalSec / 3600
        val m = (totalSec % 3600) / 60
        val s = totalSec % 60
        return if (h > 0) String.format(Locale.US, "%d:%02d:%02d", h, m, s)
        else String.format(Locale.US, "%d:%02d", m, s)
    }

    fun size(bytes: Long): String {
        if (bytes <= 0) return "0 B"
        val units = arrayOf("B", "KB", "MB", "GB", "TB")
        var value = bytes.toDouble()
        var unit = 0
        while (value >= 1024 && unit < units.lastIndex) { value /= 1024; unit++ }
        return if (unit == 0) "$bytes B" else String.format(Locale.US, "%.1f %s", value, units[unit])
    }

    fun count(n: Int, singular: String, plural: String = singular + "s"): String =
        "$n ${if (n == 1) singular else plural}"

    /** Total time, e.g. "2 h 14 min" or "38 min". */
    fun longDuration(ms: Long): String {
        val min = ms.coerceAtLeast(0) / 60_000
        return if (min >= 60) "${min / 60} h ${min % 60} min" else "$min min"
    }
}
