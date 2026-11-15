package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.unit.dp
import com.example.ui.image.ContrastUtils

val LocalIsDarkTheme = staticCompositionLocalOf { true }

fun createDarkColorScheme(rawAccent: Color): ColorScheme {
    val primary = ContrastUtils.adaptAccentForSurface(rawAccent, MidnightSurface, minRatio = 4.5)
    val onPrimary = ContrastUtils.readableOnColor(primary)
    val primaryContainer = lerp(MidnightSurfaceContainerHigh, primary, 0.24f)
    val onPrimaryContainer = ContrastUtils.adaptAccentForSurface(primary, primaryContainer, minRatio = 4.5)
    val secondary = lerp(primary, Color(0xFF9EB4FF), 0.28f)

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = ContrastUtils.readableOnColor(secondary),
        secondaryContainer = lerp(MidnightSurfaceContainerHigh, secondary, 0.20f),
        onSecondaryContainer = MidnightOnBackground,
        background = MidnightBackground,
        onBackground = MidnightOnBackground,
        surface = MidnightSurface,
        onSurface = MidnightOnBackground,
        surfaceVariant = MidnightSurfaceVariant,
        onSurfaceVariant = MidnightOnSurfaceVariant,
        surfaceContainerLowest = ObsidianBackground,
        surfaceContainerLow = MidnightSurfaceContainerLow,
        surfaceContainer = MidnightSurfaceContainer,
        surfaceContainerHigh = MidnightSurfaceContainerHigh,
        surfaceContainerHighest = lerp(MidnightSurfaceContainerHigh, Color.White, 0.05f),
        outline = MidnightOutline,
        outlineVariant = MidnightOutlineVariant,
        error = StatusErrorDark,
        onError = Color(0xFF1A0004)
    )
}

fun createAmoledColorScheme(rawAccent: Color): ColorScheme {
    val primary = ContrastUtils.adaptAccentForSurface(rawAccent, ObsidianSurface, minRatio = 4.5)
    val onPrimary = ContrastUtils.readableOnColor(primary)
    val primaryContainer = lerp(ObsidianSurfaceContainerHigh, primary, 0.24f)
    val onPrimaryContainer = ContrastUtils.adaptAccentForSurface(primary, primaryContainer, minRatio = 4.5)
    val secondary = lerp(primary, Color(0xFF9EB4FF), 0.28f)

    return darkColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = ContrastUtils.readableOnColor(secondary),
        secondaryContainer = lerp(ObsidianSurfaceContainerHigh, secondary, 0.20f),
        onSecondaryContainer = MidnightOnBackground,
        background = ObsidianBackground,
        onBackground = MidnightOnBackground,
        surface = ObsidianSurface,
        onSurface = MidnightOnBackground,
        surfaceVariant = ObsidianSurfaceVariant,
        onSurfaceVariant = MidnightOnSurfaceVariant,
        surfaceContainerLowest = ObsidianBackground,
        surfaceContainerLow = ObsidianSurfaceContainerLow,
        surfaceContainer = ObsidianSurfaceContainer,
        surfaceContainerHigh = ObsidianSurfaceContainerHigh,
        surfaceContainerHighest = lerp(ObsidianSurfaceContainerHigh, Color.White, 0.05f),
        outline = ObsidianOutline,
        outlineVariant = ObsidianOutlineVariant,
        error = StatusErrorDark,
        onError = Color(0xFF1A0004)
    )
}

fun createLightColorScheme(rawAccent: Color): ColorScheme {
    val primary = ContrastUtils.adaptAccentForSurface(rawAccent, DaylightSurface, minRatio = 4.5)
    val onPrimary = ContrastUtils.readableOnColor(primary)
    val primaryContainer = lerp(DaylightSurface, primary, 0.16f)
    val onPrimaryContainer = ContrastUtils.adaptAccentForSurface(primary, primaryContainer, minRatio = 4.8)
    val secondary = ContrastUtils.adaptAccentForSurface(lerp(primary, Color(0xFF4C5FD5), 0.28f), DaylightSurface, minRatio = 4.5)

    return lightColorScheme(
        primary = primary,
        onPrimary = onPrimary,
        primaryContainer = primaryContainer,
        onPrimaryContainer = onPrimaryContainer,
        secondary = secondary,
        onSecondary = ContrastUtils.readableOnColor(secondary),
        secondaryContainer = lerp(DaylightSurface, secondary, 0.14f),
        onSecondaryContainer = DaylightOnBackground,
        background = DaylightBackground,
        onBackground = DaylightOnBackground,
        surface = DaylightSurface,
        onSurface = DaylightOnBackground,
        surfaceVariant = DaylightSurfaceVariant,
        onSurfaceVariant = DaylightOnSurfaceVariant,
        surfaceContainerLowest = Color.White,
        surfaceContainerLow = DaylightSurfaceContainerLow,
        surfaceContainer = DaylightSurfaceContainer,
        surfaceContainerHigh = DaylightSurfaceContainerHigh,
        surfaceContainerHighest = lerp(DaylightSurfaceContainerHigh, Color.Black, 0.04f),
        outline = DaylightOutline,
        outlineVariant = DaylightOutlineVariant,
        error = StatusErrorLight,
        onError = Color.White
    )
}

val MTPlayerShapes = Shapes(
    extraSmall = RoundedCornerShape(8.dp),
    small = RoundedCornerShape(12.dp),
    medium = RoundedCornerShape(18.dp),
    large = RoundedCornerShape(24.dp),
    extraLarge = RoundedCornerShape(30.dp)
)

fun resolveIsDarkTheme(themeMode: String, isSystemDark: Boolean): Boolean = when {
    themeMode.equals("AMOLED", ignoreCase = true) -> true
    themeMode.equals("DARK", ignoreCase = true) -> true
    themeMode.equals("LIGHT", ignoreCase = true) -> false
    themeMode.equals("SYSTEM", ignoreCase = true) -> isSystemDark
    else -> true
}

@Composable
fun MTPlayerTheme(
    themeMode: String = "DARK",
    accentColorHex: String = DEFAULT_ACCENT_HEX,
    pureBlackAmoled: Boolean = false,
    dynamicSeedColor: Color? = null,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val effectiveAccent = dynamicSeedColor ?: parseAccentColor(accentColorHex)
    val isDark = resolveIsDarkTheme(themeMode, isSystemDark)

    val colorScheme = when {
        themeMode.equals("AMOLED", ignoreCase = true) -> createAmoledColorScheme(effectiveAccent)
        themeMode.equals("LIGHT", ignoreCase = true) -> createLightColorScheme(effectiveAccent)
        themeMode.equals("DARK", ignoreCase = true) -> createDarkColorScheme(effectiveAccent)
        themeMode.equals("SYSTEM", ignoreCase = true) -> {
            if (isSystemDark) {
                if (pureBlackAmoled) createAmoledColorScheme(effectiveAccent) else createDarkColorScheme(effectiveAccent)
            } else {
                createLightColorScheme(effectiveAccent)
            }
        }
        else -> createDarkColorScheme(effectiveAccent)
    }

    CompositionLocalProvider(LocalIsDarkTheme provides isDark) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            shapes = MTPlayerShapes,
            content = content
        )
    }
}
