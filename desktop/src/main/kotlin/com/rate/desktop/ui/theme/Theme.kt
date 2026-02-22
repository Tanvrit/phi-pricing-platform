package com.rate.desktop.ui.theme

import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val PrimaryBlue    = Color(0xFF1565C0)
private val PrimaryDark    = Color(0xFF003C8F)
private val SecondaryTeal  = Color(0xFF00897B)
private val SurfaceLight   = Color(0xFFF5F7FA)
private val ErrorRed       = Color(0xFFD32F2F)
private val SuccessGreen   = Color(0xFF2E7D32)

val AppColorScheme = lightColorScheme(
    primary          = PrimaryBlue,
    onPrimary        = Color.White,
    primaryContainer = Color(0xFFBBDEFB),
    secondary        = SecondaryTeal,
    onSecondary      = Color.White,
    background       = SurfaceLight,
    surface          = Color.White,
    error            = ErrorRed
)

@Composable
fun AppTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = AppColorScheme,
        typography  = Typography(),
        content     = content
    )
}

// Semantic colours used across screens
val GreenPositive  = SuccessGreen
val RedNegative    = ErrorRed
val DiscountColor  = Color(0xFF1B5E20)
val LoadingColor   = Color(0xFFE65100)
