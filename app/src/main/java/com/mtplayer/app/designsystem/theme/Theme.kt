package com.mtplayer.app.designsystem.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.remember
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp

enum class ThemeMode { SYSTEM, LIGHT, DARK }

val TideDarkScheme: ColorScheme = darkColorScheme(
    primary = Tide.EmberDark,
    onPrimary = Tide.OnEmberDark,
    primaryContainer = Tide.EmberContainerDark,
    onPrimaryContainer = Tide.OnEmberContainerDark,
    secondary = Tide.IrisDark,
    onSecondary = Tide.OnIrisDark,
    secondaryContainer = Tide.IrisContainerDark,
    onSecondaryContainer = Tide.OnIrisContainerDark,
    tertiary = Tide.LagoonDark,
    onTertiary = Tide.OnLagoonDark,
    tertiaryContainer = Tide.LagoonContainerDark,
    onTertiaryContainer = Tide.OnLagoonContainerDark,
    background = Tide.Ink100,
    onBackground = Tide.Ink900,
    surface = Tide.Ink100,
    onSurface = Tide.Ink900,
    surfaceVariant = Tide.Ink300,
    onSurfaceVariant = Tide.Ink700,
    surfaceTint = Tide.EmberDark,
    inverseSurface = Tide.Ink900,
    inverseOnSurface = Tide.Ink150,
    inversePrimary = Tide.EmberLight,
    error = Tide.ErrorDark,
    onError = Tide.OnErrorDark,
    errorContainer = Tide.ErrorContainerDark,
    onErrorContainer = Tide.OnErrorContainerDark,
    outline = Tide.Ink500,
    outlineVariant = Tide.Ink300,
    scrim = Tide.Ink0,
    surfaceBright = Tide.Ink300,
    surfaceDim = Tide.Ink50,
    surfaceContainerLowest = Tide.Ink50,
    surfaceContainerLow = Tide.Ink150,
    surfaceContainer = Tide.Ink200,
    surfaceContainerHigh = Tide.Ink250,
    surfaceContainerHighest = Tide.Ink300,
)

val TideLightScheme: ColorScheme = lightColorScheme(
    primary = Tide.EmberLight,
    onPrimary = Tide.OnEmberLight,
    primaryContainer = Tide.EmberContainerLight,
    onPrimaryContainer = Tide.OnEmberContainerLight,
    secondary = Tide.IrisLight,
    onSecondary = Tide.OnIrisLight,
    secondaryContainer = Tide.IrisContainerLight,
    onSecondaryContainer = Tide.OnIrisContainerLight,
    tertiary = Tide.LagoonLight,
    onTertiary = Tide.OnLagoonLight,
    tertiaryContainer = Tide.LagoonContainerLight,
    onTertiaryContainer = Tide.OnLagoonContainerLight,
    background = Tide.Paper50,
    onBackground = Tide.Paper900,
    surface = Tide.Paper50,
    onSurface = Tide.Paper900,
    surfaceVariant = Tide.Paper200,
    onSurfaceVariant = Tide.Paper700,
    surfaceTint = Tide.EmberLight,
    inverseSurface = Tide.Ink200,
    inverseOnSurface = Tide.Ink900,
    inversePrimary = Tide.EmberDark,
    error = Tide.ErrorLight,
    onError = Tide.OnErrorLight,
    errorContainer = Tide.ErrorContainerLight,
    onErrorContainer = Tide.OnErrorContainerLight,
    outline = Tide.Paper500,
    outlineVariant = Tide.Paper300,
    scrim = Tide.Ink0,
    surfaceBright = Tide.Paper0,
    surfaceDim = Tide.Paper200,
    surfaceContainerLowest = Tide.Paper0,
    surfaceContainerLow = Tide.Paper100,
    surfaceContainer = Tide.Paper150,
    surfaceContainerHigh = Tide.Paper200,
    surfaceContainerHighest = Tide.Paper300,
)

/** Pure-black variant for OLED screens. Only the surfaces change. */
fun ColorScheme.pureBlack(): ColorScheme = copy(
    background = Tide.Ink0,
    surface = Tide.Ink0,
    surfaceDim = Tide.Ink0,
    surfaceContainerLowest = Tide.Ink0,
    surfaceContainerLow = Tide.Ink50,
    surfaceContainer = Tide.Ink100,
    surfaceContainerHigh = Tide.Ink150,
    surfaceContainerHighest = Tide.Ink200,
)

/**
 * Passes every text/icon colour through [Contrast.ensure] against the surfaces it is used
 * on. Hand-tuned Tide schemes already pass; this protects dynamic (wallpaper) colours.
 */
fun ColorScheme.guarded(): ColorScheme {
    val surfaces = listOf(surface, surfaceContainerLow, surfaceContainer, surfaceContainerHigh)
    fun onAll(c: Color): Color = surfaces.fold(c) { acc, bg -> Contrast.ensure(acc, bg) }
    // Accents are fixed first, then their "on" colours are checked against the fixed accents.
    val p = onAll(primary)
    val s = onAll(secondary)
    val t = onAll(tertiary)
    val e = onAll(error)
    return copy(
        onSurface = onAll(onSurface),
        onBackground = Contrast.ensure(onBackground, background),
        onSurfaceVariant = onAll(onSurfaceVariant),
        primary = p,
        secondary = s,
        tertiary = t,
        error = e,
        onPrimary = Contrast.ensure(onPrimary, p),
        onSecondary = Contrast.ensure(onSecondary, s),
        onTertiary = Contrast.ensure(onTertiary, t),
        onError = Contrast.ensure(onError, e),
        onPrimaryContainer = Contrast.ensure(onPrimaryContainer, primaryContainer),
        onSecondaryContainer = Contrast.ensure(onSecondaryContainer, secondaryContainer),
        onTertiaryContainer = Contrast.ensure(onTertiaryContainer, tertiaryContainer),
        onErrorContainer = Contrast.ensure(onErrorContainer, errorContainer),
        inverseOnSurface = Contrast.ensure(inverseOnSurface, inverseSurface),
    )
}

@Immutable
data class MtExtraColors(
    val isDark: Boolean,
    val scrimStrong: Color = Tide.ScrimStrong,
    val scrimSoft: Color = Tide.ScrimSoft,
    val onScrim: Color = Tide.OnScrim,
    val onScrimMuted: Color = Tide.OnScrimMuted,
    val favorite: Color,
)

val LocalMtColors = staticCompositionLocalOf { MtExtraColors(isDark = true, favorite = Tide.EmberDark) }

object MtColors {
    val current: MtExtraColors
        @Composable get() = LocalMtColors.current
}

val MtShapes = Shapes(
    extraSmall = RoundedCornerShape(6.dp),
    small = RoundedCornerShape(10.dp),
    medium = RoundedCornerShape(16.dp),
    large = RoundedCornerShape(22.dp),
    extraLarge = RoundedCornerShape(30.dp),
)

fun resolveDark(mode: ThemeMode, systemDark: Boolean): Boolean = when (mode) {
    ThemeMode.SYSTEM -> systemDark
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

fun buildScheme(dark: Boolean, pureBlack: Boolean, dynamic: ColorScheme?): ColorScheme {
    val base = dynamic?.guarded() ?: if (dark) TideDarkScheme else TideLightScheme
    return if (dark && pureBlack) base.pureBlack() else base
}

@Composable
fun MtTheme(
    mode: ThemeMode = ThemeMode.SYSTEM,
    pureBlack: Boolean = false,
    dynamicColor: Boolean = false,
    content: @Composable () -> Unit,
) {
    val dark = resolveDark(mode, isSystemInDarkTheme())
    val context = LocalContext.current
    val useDynamic = dynamicColor && Build.VERSION.SDK_INT >= Build.VERSION_CODES.S
    val scheme = remember(dark, pureBlack, useDynamic) {
        val dynamic = if (useDynamic) {
            if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        } else null
        buildScheme(dark, pureBlack, dynamic)
    }
    val extra = remember(dark, scheme) { MtExtraColors(isDark = dark, favorite = scheme.primary) }
    CompositionLocalProvider(LocalMtColors provides extra) {
        MaterialTheme(colorScheme = scheme, typography = MtTypography, shapes = MtShapes, content = content)
    }
}
