package com.example.ui.theme

import androidx.compose.ui.graphics.Color

// Supported Accent Colors
data class AccentColorOption(
    val id: String,
    val name: String,
    val color: Color,
    val hex: String
)

val AccentColors = listOf(
    AccentColorOption("spotify_green", "Spotify Green", Color(0xFF1DB954), "#1DB954"),
    AccentColorOption("emerald", "Emerald Forest", Color(0xFF10B981), "#10B981"),
    AccentColorOption("neon_lime", "Neon Mint", Color(0xFF00E676), "#00E676"),
    AccentColorOption("cyan", "Electric Cyan", Color(0xFF00E5FF), "#00E5FF"),
    AccentColorOption("amber", "Warm Gold", Color(0xFFFFB300), "#FFB300"),
    AccentColorOption("purple", "Deep Violet", Color(0xFFBB86FC), "#BB86FC")
)

fun parseAccentColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        val longVal = cleanHex.toLong(16)
        if (cleanHex.length == 6) {
            Color(0xFF000000 or longVal)
        } else {
            Color(longVal)
        }
    } catch (_: Exception) {
        SpotifyGreen
    }
}

// 1. Spotify-inspired Dark Palette
val SpotifyGreen = Color(0xFF1DB954)
val SpotifyGreenBright = Color(0xFF1ED760)
val DarkBackground = Color(0xFF101312)
val DarkSurface = Color(0xFF1B211E)
val DarkSurfaceContainer = Color(0xFF171D1A)
val DarkSurfaceContainerHigh = Color(0xFF222B26)
val DarkSurfaceVariant = Color(0xFF26322C)
val DarkOutline = Color(0xFF2E3D35)
val DarkOutlineVariant = Color(0xFF1E2822)

// 2. AMOLED Pure Black Palette
val AmoledBackground = Color(0xFF000000)
val AmoledSurface = Color(0xFF090C0B)
val AmoledSurfaceContainer = Color(0xFF111513)
val AmoledSurfaceContainerHigh = Color(0xFF181F1B)
val AmoledSurfaceVariant = Color(0xFF1E2622)
val AmoledOutline = Color(0xFF24302A)
val AmoledOutlineVariant = Color(0xFF18201C)

// 3. Crisp Modern Light Palette
val LightBackground = Color(0xFFF5F7F6)
val LightSurface = Color(0xFFFFFFFF)
val LightSurfaceContainer = Color(0xFFECEFEF)
val LightSurfaceContainerHigh = Color(0xFFE2E7E5)
val LightSurfaceVariant = Color(0xFFD6DED9)
val LightOutline = Color(0xFFBAC5C0)
val LightOutlineVariant = Color(0xFFD3DCD8)

// Text & Content colors
val TextPrimary = Color(0xFFFFFFFF)
val TextSecondary = Color(0xFFB0BDB6)
val TextTertiary = Color(0xFF75857D)
val LightTextPrimary = Color(0xFF101513)
val LightTextSecondary = Color(0xFF4C5852)
val LightTextTertiary = Color(0xFF7C8983)

val TrackCardOverlay = Color(0x331DB954)
val PlayerScrim = Color(0xE6101312)
val AmoledPlayerScrim = Color(0xF2000000)
val LightPlayerScrim = Color(0xEEFFFFFF)
