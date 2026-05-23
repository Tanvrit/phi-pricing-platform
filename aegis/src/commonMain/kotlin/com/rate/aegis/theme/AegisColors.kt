package com.rate.aegis.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Aegis colour tokens.
 *
 * Originally a single light-mode `object`; now a [AegisColorTokens] data class
 * with two canonical instances ([LightColors], [DarkColors]) selected by
 * [AegisTheme] and threaded through [LocalAegisColors].
 *
 * The hex values for the light palette are anchored to Pass 4 §"Colour system"
 * of DASHBOARD_REDESIGN_PLAN.md with two small reconciliations vs Pass 6 §6.3
 * (kept verbatim during the refactor):
 *   - Pass 4 spec (the visual one designers signed off) uses a 0..11 slate ramp
 *     that reads "0 = canvas, 11 = inky text"; Pass 6 inverted that. We ship
 *     Pass 4.
 *   - Slate steps named slate1..slate12 → mapped to Pass 4 values
 *     (slate1 = canvas, slate12 = inky).
 *
 * The DARK palette is a fresh derivation — not a mechanical invert. canvas is
 * the GitHub-dark-style #0d1117 near-black; surfaces step up gently from there
 * so cards still feel raised. Brand stays #E31837-adjacent at #ff4d63 so it
 * pops against the dark canvas without the eye-watering saturation of the
 * pure indigo. The slate ramp is inverted (slate1 = darkest canvas,
 * slate12 = lightest inky-replacement) so semantic aliases (textBody = slate9,
 * canvas = slate1, etc.) keep working unchanged.
 *
 * NEVER hardcode "#hhhhhh" anywhere outside this file.
 */
data class AegisColorTokens(
    // ── Slate ramp (1 = canvas, 12 = inky text) ────────────────────────────
    val slate1: Color,
    val slate2: Color,
    val slate3: Color,
    val slate4: Color,
    val slate5: Color,
    val slate6: Color,
    val slate7: Color,
    val slate8: Color,
    val slate9: Color,
    val slate10: Color,
    val slate11: Color,
    val slate12: Color,

    // ── Brand accent ──────────────────────────────────────────────────────
    val indigo50: Color,
    val indigo100: Color,
    val indigo300: Color,
    val indigo500: Color,
    val indigo600: Color,
    val indigo700: Color,
    val indigo900: Color,

    // ── Semantic ramps ─────────────────────────────────────────────────────
    val success50: Color,
    val success100: Color,
    val success500: Color,
    val success700: Color,

    val warn50: Color,
    val warn100: Color,
    val warn500: Color,
    val warn700: Color,

    val danger50: Color,
    val danger100: Color,
    val danger500: Color,
    val danger700: Color,

    val info50: Color,
    val info100: Color,
    val info500: Color,
    val info700: Color,

    // ── Premium accent ────────────────────────────────────────────────────
    val premium50: Color,
    val premium100: Color,
    val premium500: Color,
    val premium700: Color,

    // ── Convenience semantic aliases ───────────────────────────────────────
    val canvas: Color,
    val surface: Color,
    val surfaceMuted: Color,
    val border: Color,
    val borderStrong: Color,
    val textPrimary: Color,
    val textBody: Color,
    val textSecondary: Color,
    val textTertiary: Color,
    val textDisabled: Color,
    val iconDefault: Color,
    val iconMuted: Color,

    val brand: Color,
    val brandHover: Color,
    val brandPressed: Color,
    val brandTint: Color,
    val brandTintStrong: Color,

    val focusRing: Color,
    val focusHalo: Color,
)

// ── Light palette (the Pass 4 design contract) ────────────────────────────
val LightColors: AegisColorTokens = run {
    val slate1 = Color(0xFFFAFBFC)   // page canvas
    val slate2 = Color(0xFFF4F6F8)   // card subtle bg
    val slate3 = Color(0xFFEBEEF2)   // hover row, dividers
    val slate4 = Color(0xFFDDE2E8)   // borders, inputs
    val slate5 = Color(0xFFC2C9D2)   // hairline, disabled bg
    val slate6 = Color(0xFFA2ABB7)   // tertiary icons
    val slate7 = Color(0xFF7C8593)   // disabled text
    val slate8 = Color(0xFF5C6573)   // secondary text
    val slate9 = Color(0xFF404754)   // body text, table cells
    val slate10 = Color(0xFF2E3441)  // headings
    val slate11 = Color(0xFF1E2330)  // display heads
    val slate12 = Color(0xFF0E1218)  // inky text (rare)

    val indigo50 = Color(0xFFEEF2FF)
    val indigo100 = Color(0xFFE0E7FF)
    val indigo300 = Color(0xFFA5B4FC)
    val indigo500 = Color(0xFF4F46E5)
    val indigo600 = Color(0xFF4338CA)
    val indigo700 = Color(0xFF3730A3)
    val indigo900 = Color(0xFF312E81)

    AegisColorTokens(
        slate1 = slate1, slate2 = slate2, slate3 = slate3, slate4 = slate4,
        slate5 = slate5, slate6 = slate6, slate7 = slate7, slate8 = slate8,
        slate9 = slate9, slate10 = slate10, slate11 = slate11, slate12 = slate12,

        indigo50 = indigo50, indigo100 = indigo100, indigo300 = indigo300,
        indigo500 = indigo500, indigo600 = indigo600, indigo700 = indigo700,
        indigo900 = indigo900,

        success50 = Color(0xFFECFDF5),
        success100 = Color(0xFFD1FAE5),
        success500 = Color(0xFF10B981),
        success700 = Color(0xFF047857),

        warn50 = Color(0xFFFFFBEB),
        warn100 = Color(0xFFFEF3C7),
        warn500 = Color(0xFFF59E0B),
        warn700 = Color(0xFFB45309),

        danger50 = Color(0xFFFEF2F2),
        danger100 = Color(0xFFFEE2E2),
        danger500 = Color(0xFFEF4444),
        danger700 = Color(0xFFB91C1C),

        info50 = Color(0xFFEFF6FF),
        info100 = Color(0xFFDBEAFE),
        info500 = Color(0xFF3B82F6),
        info700 = Color(0xFF1D4ED8),

        premium50 = Color(0xFFFDF4FF),
        premium100 = Color(0xFFFAE8FF),
        premium500 = Color(0xFFA855F7),
        premium700 = Color(0xFF7E22CE),

        canvas = slate1,
        surface = Color.White,
        surfaceMuted = slate2,
        border = slate4,
        borderStrong = slate5,
        textPrimary = slate10,
        textBody = slate9,
        textSecondary = slate8,
        textTertiary = slate7,
        textDisabled = slate7,
        iconDefault = slate8,
        iconMuted = slate6,

        brand = indigo500,
        brandHover = indigo600,
        brandPressed = indigo700,
        brandTint = indigo50,
        brandTintStrong = indigo100,

        focusRing = indigo500,
        focusHalo = indigo100,
    )
}

// ── Dark palette (operator surfaces only — chrome uses Material defaults) ─
val DarkColors: AegisColorTokens = run {
    // Inverted slate ramp: 1 = darkest canvas, 12 = brightest near-white.
    // Anchored on GitHub-dark style canvas #0D1117 with cards stepping up.
    val slate1 = Color(0xFF0D1117)   // page canvas (deepest)
    val slate2 = Color(0xFF161B22)   // card subtle bg
    val slate3 = Color(0xFF1F2630)   // hover row, dividers
    val slate4 = Color(0xFF2A323D)   // borders, inputs
    val slate5 = Color(0xFF3A4452)   // hairline, disabled bg
    val slate6 = Color(0xFF4D5765)   // tertiary icons
    val slate7 = Color(0xFF6E7781)   // disabled text
    val slate8 = Color(0xFF9DA7B3)   // secondary text
    val slate9 = Color(0xFFE6EDF3)   // body text, table cells (high contrast)
    val slate10 = Color(0xFFEEF2F7)  // headings
    val slate11 = Color(0xFFF4F7FB)  // display heads
    val slate12 = Color(0xFFFFFFFF)  // brightest

    // Brand stays indigo-family but lifted for dark-bg contrast.
    val indigo50 = Color(0xFF1A1B33)
    val indigo100 = Color(0xFF22244A)
    val indigo300 = Color(0xFF6B6DE0)
    val indigo500 = Color(0xFF818CF8)   // primary on dark
    val indigo600 = Color(0xFF9CA6FB)
    val indigo700 = Color(0xFFC0C7FD)
    val indigo900 = Color(0xFFE0E4FE)

    AegisColorTokens(
        slate1 = slate1, slate2 = slate2, slate3 = slate3, slate4 = slate4,
        slate5 = slate5, slate6 = slate6, slate7 = slate7, slate8 = slate8,
        slate9 = slate9, slate10 = slate10, slate11 = slate11, slate12 = slate12,

        indigo50 = indigo50, indigo100 = indigo100, indigo300 = indigo300,
        indigo500 = indigo500, indigo600 = indigo600, indigo700 = indigo700,
        indigo900 = indigo900,

        success50 = Color(0xFF0F2A1F),
        success100 = Color(0xFF143D2C),
        success500 = Color(0xFF34D399),
        success700 = Color(0xFF6EE7B7),

        warn50 = Color(0xFF2C2008),
        warn100 = Color(0xFF3F2D0D),
        warn500 = Color(0xFFFBBF24),
        warn700 = Color(0xFFFCD34D),

        danger50 = Color(0xFF2E1212),
        danger100 = Color(0xFF441A1A),
        danger500 = Color(0xFFF87171),
        danger700 = Color(0xFFFCA5A5),

        info50 = Color(0xFF0F1F38),
        info100 = Color(0xFF15294B),
        info500 = Color(0xFF60A5FA),
        info700 = Color(0xFF93C5FD),

        premium50 = Color(0xFF2A1238),
        premium100 = Color(0xFF3A1A4D),
        premium500 = Color(0xFFC084FC),
        premium700 = Color(0xFFD8B4FE),

        canvas = slate1,
        surface = slate2,
        surfaceMuted = slate3,
        border = slate4,
        borderStrong = slate5,
        textPrimary = slate10,
        textBody = slate9,
        textSecondary = slate8,
        textTertiary = slate7,
        textDisabled = slate7,
        iconDefault = slate8,
        iconMuted = slate6,

        brand = indigo500,
        brandHover = indigo600,
        brandPressed = indigo700,
        brandTint = indigo50,
        brandTintStrong = indigo100,

        focusRing = indigo500,
        focusHalo = indigo100,
    )
}

/**
 * CompositionLocal that carries the current [AegisColorTokens] palette through
 * the composition tree. Wired by [AegisTheme]; consumed via the [AegisColors]
 * accessor object below. Defaults to [LightColors] if no theme has been set
 * (so isolated previews / tests don't crash).
 */
internal val LocalAegisColors = staticCompositionLocalOf { LightColors }

/**
 * Token lookup used at every callsite (`AegisColors.brand`, `AegisColors.canvas`).
 * Each property is a `@Composable get()` that resolves against [LocalAegisColors]
 * so the same callsite reads the right colour in light or dark composition.
 *
 * If you add a new field to [AegisColorTokens], add a matching accessor here.
 */
object AegisColors {
    // Slate ramp
    val slate1: Color @Composable get() = LocalAegisColors.current.slate1
    val slate2: Color @Composable get() = LocalAegisColors.current.slate2
    val slate3: Color @Composable get() = LocalAegisColors.current.slate3
    val slate4: Color @Composable get() = LocalAegisColors.current.slate4
    val slate5: Color @Composable get() = LocalAegisColors.current.slate5
    val slate6: Color @Composable get() = LocalAegisColors.current.slate6
    val slate7: Color @Composable get() = LocalAegisColors.current.slate7
    val slate8: Color @Composable get() = LocalAegisColors.current.slate8
    val slate9: Color @Composable get() = LocalAegisColors.current.slate9
    val slate10: Color @Composable get() = LocalAegisColors.current.slate10
    val slate11: Color @Composable get() = LocalAegisColors.current.slate11
    val slate12: Color @Composable get() = LocalAegisColors.current.slate12

    // Indigo / brand ramp
    val indigo50: Color @Composable get() = LocalAegisColors.current.indigo50
    val indigo100: Color @Composable get() = LocalAegisColors.current.indigo100
    val indigo300: Color @Composable get() = LocalAegisColors.current.indigo300
    val indigo500: Color @Composable get() = LocalAegisColors.current.indigo500
    val indigo600: Color @Composable get() = LocalAegisColors.current.indigo600
    val indigo700: Color @Composable get() = LocalAegisColors.current.indigo700
    val indigo900: Color @Composable get() = LocalAegisColors.current.indigo900

    // Semantic ramps
    val success50: Color @Composable get() = LocalAegisColors.current.success50
    val success100: Color @Composable get() = LocalAegisColors.current.success100
    val success500: Color @Composable get() = LocalAegisColors.current.success500
    val success700: Color @Composable get() = LocalAegisColors.current.success700

    val warn50: Color @Composable get() = LocalAegisColors.current.warn50
    val warn100: Color @Composable get() = LocalAegisColors.current.warn100
    val warn500: Color @Composable get() = LocalAegisColors.current.warn500
    val warn700: Color @Composable get() = LocalAegisColors.current.warn700

    val danger50: Color @Composable get() = LocalAegisColors.current.danger50
    val danger100: Color @Composable get() = LocalAegisColors.current.danger100
    val danger500: Color @Composable get() = LocalAegisColors.current.danger500
    val danger700: Color @Composable get() = LocalAegisColors.current.danger700

    val info50: Color @Composable get() = LocalAegisColors.current.info50
    val info100: Color @Composable get() = LocalAegisColors.current.info100
    val info500: Color @Composable get() = LocalAegisColors.current.info500
    val info700: Color @Composable get() = LocalAegisColors.current.info700

    // Premium
    val premium50: Color @Composable get() = LocalAegisColors.current.premium50
    val premium100: Color @Composable get() = LocalAegisColors.current.premium100
    val premium500: Color @Composable get() = LocalAegisColors.current.premium500
    val premium700: Color @Composable get() = LocalAegisColors.current.premium700

    // Semantic aliases
    val canvas: Color @Composable get() = LocalAegisColors.current.canvas
    val surface: Color @Composable get() = LocalAegisColors.current.surface
    val surfaceMuted: Color @Composable get() = LocalAegisColors.current.surfaceMuted
    val border: Color @Composable get() = LocalAegisColors.current.border
    val borderStrong: Color @Composable get() = LocalAegisColors.current.borderStrong
    val textPrimary: Color @Composable get() = LocalAegisColors.current.textPrimary
    val textBody: Color @Composable get() = LocalAegisColors.current.textBody
    val textSecondary: Color @Composable get() = LocalAegisColors.current.textSecondary
    val textTertiary: Color @Composable get() = LocalAegisColors.current.textTertiary
    val textDisabled: Color @Composable get() = LocalAegisColors.current.textDisabled
    val iconDefault: Color @Composable get() = LocalAegisColors.current.iconDefault
    val iconMuted: Color @Composable get() = LocalAegisColors.current.iconMuted

    val brand: Color @Composable get() = LocalAegisColors.current.brand
    val brandHover: Color @Composable get() = LocalAegisColors.current.brandHover
    val brandPressed: Color @Composable get() = LocalAegisColors.current.brandPressed
    val brandTint: Color @Composable get() = LocalAegisColors.current.brandTint
    val brandTintStrong: Color @Composable get() = LocalAegisColors.current.brandTintStrong

    val focusRing: Color @Composable get() = LocalAegisColors.current.focusRing
    val focusHalo: Color @Composable get() = LocalAegisColors.current.focusHalo
}
