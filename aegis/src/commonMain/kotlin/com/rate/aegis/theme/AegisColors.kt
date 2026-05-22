package com.rate.aegis.theme

import androidx.compose.ui.graphics.Color

/**
 * Aegis colour tokens. LIGHT THEME ONLY.
 *
 * The hex values are anchored to Pass 4 §"Colour system" of DASHBOARD_REDESIGN_PLAN.md
 * with two small reconciliations vs Pass 6 §6.3:
 *   - Pass 4 spec (the visual one designers signed off) uses a 0..11 slate ramp that
 *     reads "0 = canvas, 11 = inky text". Pass 6's draft snippet inverted that
 *     orientation. We ship Pass 4 because (a) it is the visual contract and
 *     (b) it matches the colour-contrast assertions in §4.13.
 *   - Pass 6 named the steps slate1..slate12 — we keep the slate1..slate12 names
 *     but map them to Pass 4 values: slate1 = canvas, slate12 = inky.
 *
 * NEVER hardcode "#hhhhhh" anywhere outside this file.
 */
object AegisColors {
    // ── Slate ramp (1 = canvas, 12 = inky text) ────────────────────────────
    val slate1: Color = Color(0xFFFAFBFC)   // page canvas
    val slate2: Color = Color(0xFFF4F6F8)   // card subtle bg
    val slate3: Color = Color(0xFFEBEEF2)   // hover row, dividers
    val slate4: Color = Color(0xFFDDE2E8)   // borders, inputs
    val slate5: Color = Color(0xFFC2C9D2)   // hairline, disabled bg
    val slate6: Color = Color(0xFFA2ABB7)   // tertiary icons
    val slate7: Color = Color(0xFF7C8593)   // disabled text
    val slate8: Color = Color(0xFF5C6573)   // secondary text
    val slate9: Color = Color(0xFF404754)   // body text, table cells
    val slate10: Color = Color(0xFF2E3441)  // headings
    val slate11: Color = Color(0xFF1E2330)  // display heads
    val slate12: Color = Color(0xFF0E1218)  // inky text (rare)

    // ── Brand accent — Indigo ──────────────────────────────────────────────
    val indigo50: Color = Color(0xFFEEF2FF)
    val indigo100: Color = Color(0xFFE0E7FF)
    val indigo300: Color = Color(0xFFA5B4FC)
    val indigo500: Color = Color(0xFF4F46E5)   // primary buttons, links, active
    val indigo600: Color = Color(0xFF4338CA)   // hover
    val indigo700: Color = Color(0xFF3730A3)   // pressed
    val indigo900: Color = Color(0xFF312E81)   // deepest accents (rare)

    // ── Semantic ramps ─────────────────────────────────────────────────────
    val success50: Color = Color(0xFFECFDF5)
    val success100: Color = Color(0xFFD1FAE5)
    val success500: Color = Color(0xFF10B981)
    val success700: Color = Color(0xFF047857)

    val warn50: Color = Color(0xFFFFFBEB)
    val warn100: Color = Color(0xFFFEF3C7)
    val warn500: Color = Color(0xFFF59E0B)
    val warn700: Color = Color(0xFFB45309)

    val danger50: Color = Color(0xFFFEF2F2)
    val danger100: Color = Color(0xFFFEE2E2)
    val danger500: Color = Color(0xFFEF4444)
    val danger700: Color = Color(0xFFB91C1C)

    val info50: Color = Color(0xFFEFF6FF)
    val info100: Color = Color(0xFFDBEAFE)
    val info500: Color = Color(0xFF3B82F6)
    val info700: Color = Color(0xFF1D4ED8)

    // ── Premium accent (single allowed purple — hero KPI on Home) ─────────
    val premium50: Color = Color(0xFFFDF4FF)
    val premium100: Color = Color(0xFFFAE8FF)
    val premium500: Color = Color(0xFFA855F7)
    val premium700: Color = Color(0xFF7E22CE)

    // ── Convenience semantic aliases used by the components ────────────────
    val canvas: Color = slate1
    val surface: Color = Color.White
    val surfaceMuted: Color = slate2
    val border: Color = slate4
    val borderStrong: Color = slate5
    val textPrimary: Color = slate10
    val textBody: Color = slate9
    val textSecondary: Color = slate8
    val textTertiary: Color = slate7
    val textDisabled: Color = slate7
    val iconDefault: Color = slate8
    val iconMuted: Color = slate6

    val brand: Color = indigo500
    val brandHover: Color = indigo600
    val brandPressed: Color = indigo700
    val brandTint: Color = indigo50
    val brandTintStrong: Color = indigo100

    // Focus halo (Pass 4 §4.13)
    val focusRing: Color = indigo500
    val focusHalo: Color = indigo100
}
