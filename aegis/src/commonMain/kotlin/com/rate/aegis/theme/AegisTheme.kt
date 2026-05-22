package com.rate.aegis.theme

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color

/**
 * Root Aegis theme. Light only. Wraps Material3 so any composable inside is
 * styled correctly when it reaches for MaterialTheme.colorScheme.
 *
 * Aegis components prefer the explicit `AegisColors` object over the MaterialTheme
 * accessors (they're typed, not nullable, and the contrast contract is enforced at
 * the token level), but plain Material3 widgets (DropdownMenu, ModalBottomSheet
 * placeholders) inherit reasonable defaults via this scheme.
 */
private val AegisColorScheme = lightColorScheme(
    primary = AegisColors.brand,
    onPrimary = Color.White,
    primaryContainer = AegisColors.indigo100,
    onPrimaryContainer = AegisColors.indigo700,
    secondary = AegisColors.slate9,
    onSecondary = Color.White,
    secondaryContainer = AegisColors.slate3,
    onSecondaryContainer = AegisColors.slate10,
    tertiary = AegisColors.premium500,
    onTertiary = Color.White,
    background = AegisColors.canvas,
    onBackground = AegisColors.textBody,
    surface = AegisColors.surface,
    onSurface = AegisColors.textBody,
    surfaceVariant = AegisColors.surfaceMuted,
    onSurfaceVariant = AegisColors.textSecondary,
    outline = AegisColors.border,
    outlineVariant = AegisColors.borderStrong,
    error = AegisColors.danger500,
    onError = Color.White,
    errorContainer = AegisColors.danger50,
    onErrorContainer = AegisColors.danger700,
)

@Composable
fun AegisTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AegisColorScheme,
        typography = AegisTypography.material,
    ) {
        CompositionLocalProvider(
            LocalContentColor provides AegisColors.textBody,
        ) {
            content()
        }
    }
}
