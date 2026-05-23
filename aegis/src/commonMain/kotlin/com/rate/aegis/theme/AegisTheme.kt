package com.rate.aegis.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import com.rate.aegis.settings.AegisSettingsStore

/**
 * Root Aegis theme. Wraps Material3 so any composable inside is styled correctly
 * when it reaches for MaterialTheme.colorScheme.
 *
 * Aegis components prefer the explicit `AegisColors` accessor over MaterialTheme
 * (typed, not nullable, contrast contract enforced at the token level), but plain
 * Material3 widgets (DropdownMenu, ModalBottomSheet placeholders) inherit
 * reasonable defaults via this scheme.
 *
 * Dark mode: [AegisSettingsStore.load] is read on every composition; if the
 * persisted `theme` is "dark" (case-insensitive) we provide [DarkColors] via
 * [LocalAegisColors]. Anything else falls back to [LightColors]. Reading the
 * store on every recomposition rather than once is intentional — flips happen
 * the next time anything composing under this theme recomposes, which is the
 * "apply on next page open" contract the Settings surface advertises.
 *
 * The Material3 [lightColorScheme] is rebuilt against whichever palette is
 * active so chrome bits (DropdownMenu surface, etc.) follow along. We keep
 * `lightColorScheme` even for dark — Material's dark scheme has its own opinions
 * about contrast that fight ours; we'd rather thread our tokens directly.
 */
@Composable
fun AegisTheme(content: @Composable () -> Unit) {
    val themeName = runCatching { AegisSettingsStore.load().theme }
        .getOrNull()
        ?.lowercase()
        ?: "light"
    val palette = if (themeName == "dark") DarkColors else LightColors

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

    CompositionLocalProvider(LocalAegisColors provides palette) {
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
