package com.rate.aegis.theme

import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/** Aegis corner radii. Pass 6 §6.3. */
object AegisRadii {
    val rSm: Dp = 4.dp
    val rMd: Dp = 8.dp
    val rLg: Dp = 12.dp
    val rXl: Dp = 20.dp
    val rFull: Dp = 999.dp

    val shapeSm: Shape = RoundedCornerShape(rSm)
    val shapeMd: Shape = RoundedCornerShape(rMd)
    val shapeLg: Shape = RoundedCornerShape(rLg)
    val shapeXl: Shape = RoundedCornerShape(rXl)
    val shapePill: Shape = RoundedCornerShape(rFull)
}
