package com.rate.buyonline.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

val PruRed        = Color(0xFFE31837)
val PruRedDark    = Color(0xFFB0122A)
val PruBackground = Color(0xFFF5F5F5)
val PruSurface    = Color(0xFFFFFFFF)
val PruText       = Color(0xFF212121)
val PruSubtext    = Color(0xFF757575)
val PruSuccess    = Color(0xFF4CAF50)
val PruInfo       = Color(0xFF2196F3)
val PruInfoBg     = Color(0xFFE3F2FD)
val PruBadge      = Color(0xFF1565C0)

private val PRUColorScheme = lightColorScheme(
    primary          = PruRed,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFFF5252),
    secondary        = PruRedDark,
    background       = PruBackground,
    surface          = PruSurface,
    onSurface        = PruText,
    outline          = Color(0xFFBDBDBD),
    error            = Color(0xFFD32F2F),
    errorContainer   = Color(0xFFFFEBEE)
)

@Composable
fun PRUHealthTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = PRUColorScheme,
        typography  = Typography(),
        content     = content
    )
}
