package com.rate.sdk.ui.kit.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.rate.sdk.ui.kit.i18n.AegisLocale
import com.rate.sdk.ui.kit.i18n.LocalAegisLocale

/**
 * Root Aegis theme. Wraps Material3 so any composable inside is styled correctly
 * when it reaches for `MaterialTheme.colorScheme`.
 *
 * Aegis components prefer the explicit [AegisColors] accessor over MaterialTheme
 * (typed, not nullable, contrast contract enforced at the token level), but plain
 * Material3 widgets (DropdownMenu, ModalBottomSheet, …) inherit reasonable
 * defaults via the [lightColorScheme] this builds against the active palette.
 *
 * Unlike the old monolith, this theme does NOT read any settings store itself —
 * persistence lives in the app layer. The host (operator shell / buyonline app)
 * decides [dark] and [locale] (typically from its own settings) and passes them
 * in; flipping either re-themes everything composed below on the next
 * recomposition. We keep `lightColorScheme` even for dark — Material's dark
 * scheme has its own opinions about contrast that fight ours; we thread our
 * tokens directly instead.
 *
 * @param dark   true → [DarkColors], false → [LightColors].
 * @param locale active customer-journey locale, provided to [LocalAegisLocale].
 */
@Composable
fun AegisTheme(
    dark: Boolean = false,
    locale: AegisLocale = AegisLocale.EN,
    content: @Composable () -> Unit,
) {
    val palette = if (dark) DarkColors else LightColors

    val colorScheme = lightColorScheme(
        primary = palette.brand,
        onPrimary = Color.White,
        primaryContainer = palette.indigo100,
        onPrimaryContainer = palette.indigo700,
        secondary = palette.slate9,
        onSecondary = Color.White,
        secondaryContainer = palette.slate3,
        onSecondaryContainer = palette.slate10,
        tertiary = palette.premium500,
        onTertiary = Color.White,
        background = palette.canvas,
        onBackground = palette.textBody,
        surface = palette.surface,
        onSurface = palette.textBody,
        surfaceVariant = palette.surfaceMuted,
        onSurfaceVariant = palette.textSecondary,
        outline = palette.border,
        outlineVariant = palette.borderStrong,
        error = palette.danger500,
        onError = Color.White,
        errorContainer = palette.danger50,
        onErrorContainer = palette.danger700,
    )

    CompositionLocalProvider(
        LocalAegisColors provides palette,
        LocalAegisLocale provides locale,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = AegisTypography.material,
        ) {
            CompositionLocalProvider(
                LocalContentColor provides palette.textBody,
            ) {
                content()
            }
        }
    }
}
