package com.rate.sdk.ui.kit.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aegis spacing scale — 4px base. Pass 4 §4.3.
 *
 * Use names rather than magic numbers everywhere:
 *   `Modifier.padding(AegisSpacing.s4)` // 16dp
 */
object AegisSpacing {
    val s1: Dp = 4.dp
    val s2: Dp = 8.dp
    val s3: Dp = 12.dp
    val s4: Dp = 16.dp
    val s5: Dp = 24.dp
    val s6: Dp = 32.dp
    val s7: Dp = 48.dp
    val s8: Dp = 64.dp

    // Convenience widths used by the shell
    val railExpanded: Dp = 240.dp
    val railCollapsed: Dp = 64.dp
    val drawerNarrow: Dp = 480.dp
    val drawerWide: Dp = 640.dp
    val topBarHeight: Dp = 56.dp
}
