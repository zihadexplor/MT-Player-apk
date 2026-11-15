package com.example.ui.theme

import androidx.compose.ui.graphics.Color

data class AccentColorOption(
    val id: String,
    val name: String,
    val color: Color,
    val hex: String
)

const val DEFAULT_ACCENT_HEX = "#E8799B"

/**
 * ArchiveTune-inspired Material 3 Expressive curated accent palette.
 */
val AccentColors = listOf(
    AccentColorOption("sakura_rose", "Sakura Rose", Color(0xFFE8799B), "#E8799B"),
    AccentColorOption("orchid_violet", "Orchid Violet", Color(0xFFA87FFB), "#A87FFB"),
    AccentColorOption("mint_teal", "Mint Teal", Color(0xFF2DD4BF), "#2DD4BF"),
    AccentColorOption("cobalt_sky", "Cobalt Sky", Color(0xFF5B8DEF), "#5B8DEF"),
    AccentColorOption("warm_amber", "Warm Amber", Color(0xFFF59E0B), "#F59E0B"),
    AccentColorOption("emerald_leaf", "Emerald Leaf", Color(0xFF10B981), "#10B981")
)

fun parseAccentColor(hex: String): Color {
    return try {
        val cleanHex = hex.removePrefix("#")
        // Migrate legacy default green seamlessly if unrecognized or blank
        val longVal = cleanHex.toLong(16)
        if (cleanHex.length == 6) {
            Color(0xFF000000 or longVal)
        } else {
            Color(longVal)
        }
    } catch (_: Exception) {
        Color(0xFFE8799B)
    }
}

// 1. ArchiveTune Expressive Midnight Dark Surfaces (Neutral Slate-Indigo base, zero murky green)
val MidnightBackground = Color(0xFF0E0F14)
val MidnightSurface = Color(0xFF151720)
val MidnightSurfaceContainerLow = Color(0xFF12141C)
val MidnightSurfaceContainer = Color(0xFF191B26)
val MidnightSurfaceContainerHigh = Color(0xFF212433)
val MidnightSurfaceVariant = Color(0xFF282C3D)
val MidnightOutline = Color(0xFF474C63)
val MidnightOutlineVariant = Color(0xFF2C3042)
val MidnightOnBackground = Color(0xFFF4F5F8)
val MidnightOnSurfaceVariant = Color(0xFFB4B9C9)

// 2. AMOLED Pure Obsidian Surfaces (#000000 true black canvas with crisp tonal cards)
val ObsidianBackground = Color(0xFF000000)
val ObsidianSurface = Color(0xFF090A0F)
val ObsidianSurfaceContainerLow = Color(0xFF07080C)
val ObsidianSurfaceContainer = Color(0xFF0E1017)
val ObsidianSurfaceContainerHigh = Color(0xFF161924)
val ObsidianSurfaceVariant = Color(0xFF1E2230)
val ObsidianOutline = Color(0xFF3E4357)
val ObsidianOutlineVariant = Color(0xFF232736)

// 3. Daylight Paper Editorial Light Surfaces (Warm alabaster with WCAG AA/AAA dark inks)
val DaylightBackground = Color(0xFFF7F6FA)
val DaylightSurface = Color(0xFFFFFFFF)
val DaylightSurfaceContainerLow = Color(0xFFF3F1F7)
val DaylightSurfaceContainer = Color(0xFFECEAEF)
val DaylightSurfaceContainerHigh = Color(0xFFE4E1EB)
val DaylightSurfaceVariant = Color(0xFFDAD5E4)
val DaylightOutline = Color(0xFF8C869C)
val DaylightOutlineVariant = Color(0xFFD2CEDD)
val DaylightOnBackground = Color(0xFF17161F)
val DaylightOnSurfaceVariant = Color(0xFF4F4B5E)

// Semantic status tokens (adapted for readability)
val StatusErrorDark = Color(0xFFFF6B7A)
val StatusErrorLight = Color(0xFFC62828)
val StatusWarning = Color(0xFFFFB74D)
