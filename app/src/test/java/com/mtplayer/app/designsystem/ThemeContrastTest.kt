package com.mtplayer.app.designsystem

import androidx.compose.material3.ColorScheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import com.mtplayer.app.designsystem.theme.Contrast
import com.mtplayer.app.designsystem.theme.ThemeMode
import com.mtplayer.app.designsystem.theme.TideDarkScheme
import com.mtplayer.app.designsystem.theme.TideLightScheme
import com.mtplayer.app.designsystem.theme.buildScheme
import com.mtplayer.app.designsystem.theme.guarded
import com.mtplayer.app.designsystem.theme.pureBlack
import com.mtplayer.app.designsystem.theme.resolveDark
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the "no white text on light backgrounds" requirement: every text/icon role must meet
 * WCAG AA (4.5:1) on every surface it is drawn on, for every shipped scheme.
 */
class ThemeContrastTest {

    private fun ColorScheme.pairs(): List<Triple<String, Color, Color>> {
        val surfaces = listOf(
            "surface" to surface, "background" to background,
            "surfaceContainerLow" to surfaceContainerLow, "surfaceContainer" to surfaceContainer,
            "surfaceContainerHigh" to surfaceContainerHigh,
        )
        val texts = listOf("onSurface" to onSurface, "onSurfaceVariant" to onSurfaceVariant, "primary" to primary, "error" to error)
        return surfaces.flatMap { (sn, s) -> texts.map { (tn, t) -> Triple("$tn on $sn", t, s) } } + listOf(
            Triple("onPrimary on primary", onPrimary, primary),
            Triple("onPrimaryContainer on primaryContainer", onPrimaryContainer, primaryContainer),
            Triple("onSecondaryContainer on secondaryContainer", onSecondaryContainer, secondaryContainer),
            Triple("onErrorContainer on errorContainer", onErrorContainer, errorContainer),
            Triple("inverseOnSurface on inverseSurface", inverseOnSurface, inverseSurface),
            Triple("onBackground on background", onBackground, background),
        )
    }

    private fun assertReadable(name: String, scheme: ColorScheme) {
        val failures = scheme.pairs().filter { (_, fg, bg) -> Contrast.ratio(fg, bg) < Contrast.AA_TEXT }
            .map { (n, fg, bg) -> "$n = ${"%.2f".format(Contrast.ratio(fg, bg))}" }
        assertTrue("$name has low-contrast pairs: $failures", failures.isEmpty())
    }

    @Test fun darkSchemeIsReadable() = assertReadable("dark", TideDarkScheme)
    @Test fun lightSchemeIsReadable() = assertReadable("light", TideLightScheme)
    @Test fun pureBlackSchemeIsReadable() = assertReadable("pureBlack", TideDarkScheme.pureBlack())
    @Test fun builtSchemesAreReadable() {
        for (dark in listOf(true, false)) for (black in listOf(true, false)) {
            assertReadable("built dark=$dark black=$black", buildScheme(dark, black, null))
        }
    }

    @Test fun lightSchemeTextIsDark() {
        // The v1 bug: white text on light surfaces. Text on light surfaces must be darker than the surface.
        assertTrue(TideLightScheme.onSurface.luminanceValue() < TideLightScheme.surface.luminanceValue())
        assertTrue(TideDarkScheme.onSurface.luminanceValue() > TideDarkScheme.surface.luminanceValue())
    }

    @Test fun guardFixesHostileDynamicScheme() {
        // Worst case wallpaper scheme: white-ish text on a light surface, dark text on dark surface.
        val hostileLight = lightColorScheme(
            surface = Color(0xFFF5F5F5), background = Color(0xFFF5F5F5),
            surfaceContainerLow = Color(0xFFEEEEEE), surfaceContainer = Color(0xFFE8E8E8), surfaceContainerHigh = Color(0xFFE0E0E0),
            onSurface = Color(0xFFDDDDDD), onSurfaceVariant = Color(0xFFCCCCCC), onBackground = Color.White,
            primary = Color(0xFFFFE082), onPrimary = Color(0xFFFFF8E1), error = Color(0xFFFFCDD2),
        )
        assertReadable("guarded hostile light", hostileLight.guarded())
        val hostileDark = darkColorScheme(
            surface = Color(0xFF111111), background = Color(0xFF111111),
            surfaceContainerLow = Color(0xFF1A1A1A), surfaceContainer = Color(0xFF202020), surfaceContainerHigh = Color(0xFF2A2A2A),
            onSurface = Color(0xFF333333), onSurfaceVariant = Color(0xFF2B2B2B), onBackground = Color.Black,
            primary = Color(0xFF1A237E), onPrimary = Color(0xFF283593), error = Color(0xFF4A0000),
        )
        assertReadable("guarded hostile dark", hostileDark.guarded())
    }

    @Test fun ensureKeepsCompliantColors() {
        val c = Color(0xFF111111)
        assertEquals(c, Contrast.ensure(c, Color.White))
    }

    @Test fun ratioMatchesWcagExtremes() {
        assertEquals(21.0, Contrast.ratio(Color.Black, Color.White), 0.01)
        assertEquals(1.0, Contrast.ratio(Color.White, Color.White), 0.001)
    }

    @Test fun themeModeResolution() {
        assertTrue(resolveDark(ThemeMode.SYSTEM, systemDark = true))
        assertTrue(!resolveDark(ThemeMode.SYSTEM, systemDark = false))
        assertTrue(!resolveDark(ThemeMode.LIGHT, systemDark = true))
        assertTrue(resolveDark(ThemeMode.DARK, systemDark = false))
    }

    private fun Color.luminanceValue(): Float = luminance()
}
