package com.mtplayer.app.designsystem.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import com.mtplayer.app.R

private fun manrope(weight: FontWeight) = Font(
    resId = R.font.manrope,
    weight = weight,
    variationSettings = FontVariation.Settings(FontVariation.weight(weight.weight)),
)

val Manrope = FontFamily(
    manrope(FontWeight.Normal),
    manrope(FontWeight.Medium),
    manrope(FontWeight.SemiBold),
    manrope(FontWeight.Bold),
    manrope(FontWeight.ExtraBold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double = 0.0) = TextStyle(
    fontFamily = Manrope,
    fontWeight = weight,
    fontSize = size.sp,
    lineHeight = line.sp,
    letterSpacing = tracking.em,
)

val MtTypography = Typography(
    displayLarge = style(52, 60, FontWeight.ExtraBold, -0.02),
    displayMedium = style(42, 50, FontWeight.ExtraBold, -0.02),
    displaySmall = style(34, 42, FontWeight.Bold, -0.015),
    headlineLarge = style(30, 38, FontWeight.Bold, -0.01),
    headlineMedium = style(26, 34, FontWeight.Bold, -0.01),
    headlineSmall = style(22, 30, FontWeight.Bold),
    titleLarge = style(20, 28, FontWeight.Bold),
    titleMedium = style(16, 24, FontWeight.SemiBold),
    titleSmall = style(14, 20, FontWeight.SemiBold),
    bodyLarge = style(16, 24, FontWeight.Normal),
    bodyMedium = style(14, 20, FontWeight.Normal),
    bodySmall = style(12, 16, FontWeight.Medium),
    labelLarge = style(14, 20, FontWeight.SemiBold, 0.005),
    labelMedium = style(12, 16, FontWeight.SemiBold, 0.01),
    labelSmall = style(11, 16, FontWeight.SemiBold, 0.02),
)

/** Tabular figures so times do not jitter while playing. */
val TabularNumbers = TextStyle(fontFeatureSettings = "tnum")
