package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors

/** Horizontal hairline divider using `slate3`. */
@Composable
fun AegisHDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = AegisColors.slate3,
) {
    Box(
        modifier
            .fillMaxWidth()
            .height(thickness)
            .background(color),
    )
}

/** Vertical hairline divider using `slate3`. */
@Composable
fun AegisVDivider(
    modifier: Modifier = Modifier,
    thickness: Dp = 1.dp,
    color: Color = AegisColors.slate3,
) {
    Box(
        modifier
            .fillMaxHeight()
            .width(thickness)
            .background(color),
    )
}
