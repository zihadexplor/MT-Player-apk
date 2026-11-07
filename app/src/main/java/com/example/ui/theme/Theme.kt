package com.example.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

fun createDarkColorScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    primaryContainer = DarkSurfaceContainerHigh,
    onPrimaryContainer = accent,
    secondary = accent,
    onSecondary = DarkBackground,
    background = DarkBackground,
    onBackground = TextPrimary,
    surface = DarkSurface,
    onSurface = TextPrimary,
    surfaceVariant = DarkSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = DarkSurfaceContainer,
    surfaceContainerHigh = DarkSurfaceContainerHigh,
    outline = DarkOutline,
    outlineVariant = DarkOutlineVariant
)

fun createAmoledColorScheme(accent: Color): ColorScheme = darkColorScheme(
    primary = accent,
    onPrimary = Color.Black,
    primaryContainer = AmoledSurfaceContainerHigh,
    onPrimaryContainer = accent,
    secondary = accent,
    onSecondary = AmoledBackground,
    background = AmoledBackground,
    onBackground = TextPrimary,
    surface = AmoledSurface,
    onSurface = TextPrimary,
    surfaceVariant = AmoledSurfaceVariant,
    onSurfaceVariant = TextSecondary,
    surfaceContainer = AmoledSurfaceContainer,
    surfaceContainerHigh = AmoledSurfaceContainerHigh,
    outline = AmoledOutline,
    outlineVariant = AmoledOutlineVariant
)

fun createLightColorScheme(accent: Color): ColorScheme = lightColorScheme(
    primary = accent,
    onPrimary = Color.White,
    primaryContainer = LightSurfaceContainerHigh,
    onPrimaryContainer = accent,
    secondary = accent,
    onSecondary = Color.White,
    background = LightBackground,
    onBackground = LightTextPrimary,
    surface = LightSurface,
    onSurface = LightTextPrimary,
    surfaceVariant = LightSurfaceVariant,
    onSurfaceVariant = LightTextSecondary,
    surfaceContainer = LightSurfaceContainer,
    surfaceContainerHigh = LightSurfaceContainerHigh,
    outline = LightOutline,
    outlineVariant = LightOutlineVariant
)

val MTPlayerShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(28.dp)
)

@Composable
fun MTPlayerTheme(
    themeMode: String = "DARK",
    accentColorHex: String = "#1DB954",
    pureBlackAmoled: Boolean = false,
    content: @Composable () -> Unit
) {
    val isSystemDark = isSystemInDarkTheme()
    val accent = parseAccentColor(accentColorHex)

    val colorScheme = when {
        themeMode.equals("AMOLED", ignoreCase = true) -> {
            createAmoledColorScheme(accent)
        }
        themeMode.equals("LIGHT", ignoreCase = true) -> {
            createLightColorScheme(accent)
        }
        themeMode.equals("DARK", ignoreCase = true) -> {
            createDarkColorScheme(accent)
        }
        themeMode.equals("SYSTEM", ignoreCase = true) -> {
            if (isSystemDark) {
                if (pureBlackAmoled) createAmoledColorScheme(accent) else createDarkColorScheme(accent)
            } else {
                createLightColorScheme(accent)
            }
        }
        else -> {
            createDarkColorScheme(accent)
        }
    }

    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        shapes = MTPlayerShapes,
        content = content
    )
}
