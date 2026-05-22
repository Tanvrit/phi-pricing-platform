package com.rate.aegis.theme

import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Aegis elevation tiers. Pass 4 §4.4 / Pass 6 §6.3.
 *
 * Compose Desktop renders elevation as a soft shadow on the `Surface` composable.
 * Light theme reads better with hairlines (`1px slate-3`) than with thick shadows,
 * so the higher tiers are still small relative to typical Material defaults.
 */
object AegisElevation {
    val flat: Dp = 0.dp
    val raised: Dp = 1.dp
    val card: Dp = 2.dp
    val modal: Dp = 8.dp
    val popover: Dp = 16.dp
}
