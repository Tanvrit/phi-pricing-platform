package com.rate.sdk.ui.kit.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.selection.toggleable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors

/**
 * A clean, brand-styled on/off toggle — replaces Material3's bulky default Switch across the
 * console. Animated track colour + sliding thumb; brand fill when on, muted track + border when off.
 *
 * Pure Compose Multiplatform (foundation only) so it renders identically on JVM desktop and WASM.
 */
@Composable
fun AegisToggle(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    enabled: Boolean = true,
) {
    val trackWidth = 42.dp
    val trackHeight = 24.dp
    val thumbSize = 18.dp
    val gap = 3.dp

    val trackColor by animateColorAsState(
        when {
            !enabled -> AegisColors.surfaceMuted
            checked -> AegisColors.brand
            else -> AegisColors.surfaceMuted
        },
    )
    val borderColor = if (checked) AegisColors.brand else AegisColors.borderStrong
    val thumbOffset by animateDpAsState(if (checked) trackWidth - thumbSize - gap else gap)

    Box(
        modifier = Modifier
            .size(trackWidth, trackHeight)
            .clip(RoundedCornerShape(percent = 50))
            .background(trackColor)
            .border(1.dp, borderColor, RoundedCornerShape(percent = 50))
            .toggleable(
                value = checked,
                enabled = enabled,
                role = Role.Switch,
                onValueChange = onCheckedChange,
            ),
        contentAlignment = Alignment.CenterStart,
    ) {
        Box(
            Modifier
                .offset(x = thumbOffset)
                .shadow(2.dp, CircleShape)
                .size(thumbSize)
                .clip(CircleShape)
                .background(Color.White),
        )
    }
}
