package com.mtplayer.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * "Tide" colour tokens. These are the only raw colours in the app; components read
 * colours from [androidx.compose.material3.MaterialTheme.colorScheme] or [MtColors].
 */
internal object Tide {
    // Ink neutrals (dark)
    val Ink0 = Color(0xFF000000)
    val Ink50 = Color(0xFF07080C)
    val Ink100 = Color(0xFF0C0D13)
    val Ink150 = Color(0xFF12141C)
    val Ink200 = Color(0xFF181B25)
    val Ink250 = Color(0xFF1E222E)
    val Ink300 = Color(0xFF262B39)
    val Ink400 = Color(0xFF3A4052)
    val Ink500 = Color(0xFF5B6278)
    val Ink700 = Color(0xFFA6ACBF)
    val Ink900 = Color(0xFFEEF0F6)

    // Paper neutrals (light)
    val Paper0 = Color(0xFFFFFFFF)
    val Paper50 = Color(0xFFFAFAFC)
    val Paper100 = Color(0xFFF3F4F8)
    val Paper150 = Color(0xFFECEEF3)
    val Paper200 = Color(0xFFE4E7EE)
    val Paper300 = Color(0xFFD5D9E3)
    val Paper500 = Color(0xFF8A90A3)
    val Paper700 = Color(0xFF474D60)
    val Paper900 = Color(0xFF12141C)

    // Ember (primary)
    val EmberDark = Color(0xFFFF9157)       // on dark surfaces
    val OnEmberDark = Color(0xFF2B1000)
    val EmberContainerDark = Color(0xFF4A2412)
    val OnEmberContainerDark = Color(0xFFFFDCCB)
    val EmberLight = Color(0xFFAE4114)      // on light surfaces
    val OnEmberLight = Color(0xFFFFFFFF)
    val EmberContainerLight = Color(0xFFFFE1D3)
    val OnEmberContainerLight = Color(0xFF3B1300)

    // Iris (secondary)
    val IrisDark = Color(0xFFBDB1FF)
    val OnIrisDark = Color(0xFF1E1452)
    val IrisContainerDark = Color(0xFF332A6B)
    val OnIrisContainerDark = Color(0xFFE6E0FF)
    val IrisLight = Color(0xFF5543C9)
    val OnIrisLight = Color(0xFFFFFFFF)
    val IrisContainerLight = Color(0xFFE6E1FF)
    val OnIrisContainerLight = Color(0xFF1A0E55)

    // Lagoon (tertiary)
    val LagoonDark = Color(0xFF6ADCC6)
    val OnLagoonDark = Color(0xFF00382F)
    val LagoonContainerDark = Color(0xFF0F4A40)
    val OnLagoonContainerDark = Color(0xFFB9F5E7)
    val LagoonLight = Color(0xFF006B5B)
    val OnLagoonLight = Color(0xFFFFFFFF)
    val LagoonContainerLight = Color(0xFFC6F3E8)
    val OnLagoonContainerLight = Color(0xFF00201A)

    // Status
    val ErrorDark = Color(0xFFFF8A80)
    val OnErrorDark = Color(0xFF410002)
    val ErrorContainerDark = Color(0xFF5C1714)
    val OnErrorContainerDark = Color(0xFFFFDAD5)
    val ErrorLight = Color(0xFFB3261E)
    val OnErrorLight = Color(0xFFFFFFFF)
    val ErrorContainerLight = Color(0xFFFFDAD5)
    val OnErrorContainerLight = Color(0xFF410002)

    // Video scrim: video content is always dark, so overlay text is always light.
    val ScrimStrong = Color(0xCC000000)
    val ScrimSoft = Color(0x66000000)
    val OnScrim = Color(0xFFFFFFFF)
    val OnScrimMuted = Color(0xFFD9DCE5)
}
