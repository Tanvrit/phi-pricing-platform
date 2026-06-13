package com.rate.sdk.ui.kit.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * Aegis type scale. Pass 4 §4.2.
 *
 * System fonts for now (Inter / JetBrains Mono are intended; we fall back to the
 * platform's default sans + monospace so the kit launches without bundled font
 * assets). Money / numeric styles use `FontFamily.Monospace` and request tabular
 * figures via `fontFeatureSettings = "tnum"` so digits align in tables.
 */
object AegisTypography {
    private val sans: FontFamily = FontFamily.Default
    private val mono: FontFamily = FontFamily.Monospace

    /** Hero KPI on Home (Pass 4 says 56/60; the spec for Ship-1 requested 28/36). */
    val display: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 36.sp,
        letterSpacing = (-0.5).sp,
    )

    val h1: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 22.sp,
        lineHeight = 30.sp,
        letterSpacing = (-0.25).sp,
    )

    val h2: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp,
        lineHeight = 26.sp,
    )

    val h3: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 16.sp,
        lineHeight = 24.sp,
    )

    val bodyL: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 15.sp,
        lineHeight = 22.sp,
    )

    val body: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 14.sp,
        lineHeight = 20.sp,
    )

    val small: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        lineHeight = 16.sp,
    )

    val caption: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.Medium,
        fontSize = 12.sp,
        lineHeight = 16.sp,
        letterSpacing = 0.1.sp,
    )

    /** UPPERCASE micro label for pills, kbd chips, badges. */
    val micro: TextStyle = TextStyle(
        fontFamily = sans,
        fontWeight = FontWeight.SemiBold,
        fontSize = 11.sp,
        lineHeight = 14.sp,
        letterSpacing = 0.5.sp,
    )

    /** Mono base — UIN, code IDs, kbd chips. */
    val mono14: TextStyle = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        lineHeight = 18.sp,
    )

    /** Money — big (KPI tile big number). */
    val moneyL: TextStyle = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.SemiBold,
        fontSize = 28.sp,
        lineHeight = 32.sp,
        fontFeatureSettings = "tnum",
    )

    /** Money — default table cell. */
    val money: TextStyle = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Medium,
        fontSize = 14.sp,
        lineHeight = 20.sp,
        fontFeatureSettings = "tnum",
    )

    /** Money — small contexts. */
    val moneyS: TextStyle = TextStyle(
        fontFamily = mono,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        lineHeight = 18.sp,
        fontFeatureSettings = "tnum",
    )

    /** Material3 Typography mapping for any default-style consumers. */
    val material: Typography = Typography(
        displayLarge = display,
        headlineLarge = h1,
        headlineMedium = h2,
        headlineSmall = h3,
        titleLarge = h2,
        titleMedium = h3,
        titleSmall = h3,
        bodyLarge = bodyL,
        bodyMedium = body,
        bodySmall = small,
        labelLarge = caption,
        labelMedium = caption,
        labelSmall = micro,
    )
}
