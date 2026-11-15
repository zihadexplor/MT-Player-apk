package com.mtplayer.app.designsystem.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.compositeOver
import androidx.compose.ui.graphics.luminance
import kotlin.math.max
import kotlin.math.min

/** WCAG 2.x contrast helpers used by the theme and verified by unit tests. */
object Contrast {
    const val AA_TEXT = 4.5
    const val AA_LARGE = 3.0

    fun ratio(foreground: Color, background: Color): Double {
        val bg = if (background.alpha < 1f) background.compositeOver(Color.Black) else background
        val fg = if (foreground.alpha < 1f) foreground.compositeOver(bg) else foreground
        val l1 = fg.luminance().toDouble()
        val l2 = bg.luminance().toDouble()
        return (max(l1, l2) + 0.05) / (min(l1, l2) + 0.05)
    }

    /**
     * Returns [color] moved toward white or black (whichever direction increases contrast
     * with [background]) until it reaches [minRatio]. Hue is preserved as far as possible.
     */
    fun ensure(color: Color, background: Color, minRatio: Double = AA_TEXT): Color {
        if (ratio(color, background) >= minRatio) return color
        // Prefer the direction away from the background; fall back to the other one for
        // mid-tone backgrounds where only black (or only white) can reach the target.
        val preferred = if (background.luminance() < 0.5f) Color.White else Color.Black
        val other = if (preferred == Color.White) Color.Black else Color.White
        return toward(color, background, preferred, minRatio)
            ?: toward(color, background, other, minRatio)
            ?: onColor(background)
    }

    private fun toward(color: Color, background: Color, target: Color, minRatio: Double): Color? {
        if (ratio(target, background) < minRatio) return null
        var low = 0f
        var high = 1f
        var best = target
        repeat(18) {
            val mid = (low + high) / 2f
            val candidate = mix(color, target, mid)
            if (ratio(candidate, background) >= minRatio) {
                best = candidate
                high = mid
            } else {
                low = mid
            }
        }
        return best
    }

    /** Black or white, whichever reads better on [background]. */
    fun onColor(background: Color): Color =
        if (ratio(Color.White, background) >= ratio(Color.Black, background)) Color.White else Color.Black

    fun mix(a: Color, b: Color, fraction: Float): Color {
        val f = fraction.coerceIn(0f, 1f)
        return Color(
            red = a.red + (b.red - a.red) * f,
            green = a.green + (b.green - a.green) * f,
            blue = a.blue + (b.blue - a.blue) * f,
            alpha = 1f
        )
    }
}
