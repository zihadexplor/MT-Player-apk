package com.example

import com.example.ui.image.ContrastUtils
import com.example.ui.theme.AccentColors
import com.example.ui.theme.createAmoledColorScheme
import com.example.ui.theme.createDarkColorScheme
import com.example.ui.theme.createLightColorScheme
import com.example.ui.theme.parseAccentColor
import com.example.ui.theme.resolveIsDarkTheme
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class ThemeContrastTest {

    @Test
    fun `all curated accents meet WCAG AA contrast on Light, Dark, and AMOLED schemes`() {
        val hexes = AccentColors.map { it.hex } + listOf("#1DB954", "#FFFFFF", "#111111")

        hexes.forEach { hex ->
            val rawAccent = parseAccentColor(hex)
            val schemes = listOf(
                "LIGHT" to createLightColorScheme(rawAccent),
                "DARK" to createDarkColorScheme(rawAccent),
                "AMOLED" to createAmoledColorScheme(rawAccent)
            )

            schemes.forEach { (mode, scheme) ->
                val primaryVsSurface = ContrastUtils.contrastRatio(scheme.primary, scheme.surface)
                assertTrue(
                    "$mode primary vs surface contrast too low for $hex: $primaryVsSurface",
                    primaryVsSurface >= 4.4
                )

                val onPrimaryVsPrimary = ContrastUtils.contrastRatio(scheme.onPrimary, scheme.primary)
                assertTrue(
                    "$mode onPrimary vs primary contrast too low for $hex: $onPrimaryVsPrimary",
                    onPrimaryVsPrimary >= 4.5
                )

                val onBgVsBg = ContrastUtils.contrastRatio(scheme.onBackground, scheme.background)
                assertTrue(
                    "$mode onBackground vs background contrast too low: $onBgVsBg",
                    onBgVsBg >= 7.0
                )

                val onSurfaceVarVsSurface = ContrastUtils.contrastRatio(scheme.onSurfaceVariant, scheme.surface)
                assertTrue(
                    "$mode onSurfaceVariant vs surface contrast too low: $onSurfaceVarVsSurface",
                    onSurfaceVarVsSurface >= 4.5
                )

                val onPrimContVsPrimCont = ContrastUtils.contrastRatio(scheme.onPrimaryContainer, scheme.primaryContainer)
                assertTrue(
                    "$mode onPrimaryContainer vs primaryContainer contrast too low for $hex: $onPrimContVsPrimCont",
                    onPrimContVsPrimCont >= 4.4
                )
            }
        }
    }

    @Test
    fun `resolveIsDarkTheme handles all modes consistently`() {
        assertTrue(resolveIsDarkTheme("DARK", isSystemDark = false))
        assertTrue(resolveIsDarkTheme("AMOLED", isSystemDark = false))
        assertFalse(resolveIsDarkTheme("LIGHT", isSystemDark = true))
        assertTrue(resolveIsDarkTheme("SYSTEM", isSystemDark = true))
        assertFalse(resolveIsDarkTheme("SYSTEM", isSystemDark = false))
        assertEquals(6, AccentColors.size)
    }
}
