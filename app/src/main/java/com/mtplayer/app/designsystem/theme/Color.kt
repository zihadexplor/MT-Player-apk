package com.mtplayer.app.designsystem.theme

import androidx.compose.ui.graphics.Color

/**
 * "Ember Night" colour tokens (object name kept as Tide for continuity). These are the only raw colours in the app; components read
 * colours from [androidx.compose.material3.MaterialTheme.colorScheme] or [MtColors].
 */
internal object Tide {
    // Ink neutrals (dark)
    val Ink0 = Color(0xFF000000)
    val Ink50 = Color(0xFF0B0A0C)
    val Ink100 = Color(0xFF121013)
    val Ink150 = Color(0xFF19161A)
    val Ink200 = Color(0xFF1F1B21)
    val Ink250 = Color(0xFF27222A)
    val Ink300 = Color(0xFF312B34)
    val Ink400 = Color(0xFF463F49)
    val Ink500 = Color(0xFF6E6570)
    val Ink700 = Color(0xFFBCB2AD)
    val Ink900 = Color(0xFFF6F0EB)

    // Paper neutrals (light)
    val Paper0 = Color(0xFFFFFFFF)
    val Paper50 = Color(0xFFFBF8F5)
    val Paper100 = Color(0xFFF5F0EB)
    val Paper150 = Color(0xFFEFE9E3)
    val Paper200 = Color(0xFFE8E1DA)
    val Paper300 = Color(0xFFDAD1C9)
    val Paper500 = Color(0xFF8E847D)
    val Paper700 = Color(0xFF4F4641)
    val Paper900 = Color(0xFF1B1614)

    // Ember (primary)
    val EmberDark = Color(0xFFFFA764)       // on dark surfaces
    val OnEmberDark = Color(0xFF2E1300)
    val EmberContainerDark = Color(0xFF4D2815)
    val OnEmberContainerDark = Color(0xFFFFDDC8)
    val EmberLight = Color(0xFFA3410F)      // on light surfaces
    val OnEmberLight = Color(0xFFFFFFFF)
    val EmberContainerLight = Color(0xFFFFE1D3)
    val OnEmberContainerLight = Color(0xFF3B1300)

    // Iris (secondary)
    val IrisDark = Color(0xFFBDB1FF)
    val OnIrisDark = Color(0xFF1E1452)
    val IrisContainerDark = Color(0xFF3A2C27) // warm clay container (Ember Night)
    val OnIrisContainerDark = Color(0xFFF6E4D8)
    val IrisLight = Color(0xFF5543C9)
    val OnIrisLight = Color(0xFFFFFFFF)
    val IrisContainerLight = Color(0xFFF2E2D5) // warm sand container (Ember Night)
    val OnIrisContainerLight = Color(0xFF3A2416)

    // Lagoon (tertiary)
    val LagoonDark = Color(0xFF6ADCC6)
    val OnLagoonDark = Color(0xFF00382F)
    val LagoonContainerDark = Color(0xFF0F4A40)
    val OnLagoonContainerDark = Color(0xFFB9F5E7)
    val LagoonLight = Color(0xFF006B5B)
    val OnLagoonLight = Color(0xFFFFFFFF)
    val LagoonContainerLight = Color(0xFFC6F3E8)
    val OnLagoonContainerLight = Color(0xFF00201A)

    // Brand gradient (logo, accent fills). Amber -> ember.
    val BrandAmber = Color(0xFFFFB45E)
    val BrandEmber = Color(0xFFFF6A3D)

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
