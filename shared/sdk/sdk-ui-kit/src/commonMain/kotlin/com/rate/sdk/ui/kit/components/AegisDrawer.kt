package com.rate.sdk.ui.kit.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisMotion
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisDrawer — right-side modal drawer (Pass 6 §6.2).
 *
 * Slides in from the right when [open] flips to true. The scrim is a tappable
 * overlay that closes the drawer; clicks inside the panel are absorbed so the
 * inner controls keep working. The shell composes the drawer at its top-level so
 * the scrim covers content + sidebar both.
 */
@Composable
fun AegisDrawer(
    open: Boolean,
    onClose: () -> Unit,
    title: String? = null,
    subtitle: String? = null,
    width: Dp = AegisSpacing.drawerWide,
    modifier: Modifier = Modifier,
    actions: (@Composable () -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier = modifier.fillMaxSize()) {
        // Scrim
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(animationSpec = AegisMotion.tweenDrawer()),
            exit = fadeOut(animationSpec = AegisMotion.tweenDrawer()),
        ) {
            val noopSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = noopSource,
                        indication = null,
                        onClick = onClose,
                    ),
            )
        }
        // Panel
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.CenterEnd),
            enter = slideInHorizontally(
                initialOffsetX = { it },
                animationSpec = AegisMotion.tweenDrawer(),
            ) + fadeIn(animationSpec = AegisMotion.tweenDrawer()),
            exit = slideOutHorizontally(
                targetOffsetX = { it },
                animationSpec = AegisMotion.tweenDrawer(),
            ) + fadeOut(animationSpec = AegisMotion.tweenDrawer()),
        ) {
            val absorb = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .fillMaxHeight()
                    .width(width)
                    .background(AegisColors.canvas)
                    .border(1.dp, AegisColors.border)
                    .clickable(
                        interactionSource = absorb,
                        indication = null,
                        onClick = {},
                    ),
            ) {
                Row(
                    modifier = Modifier
                        .background(AegisColors.surface)
                        .padding(
                            start = AegisSpacing.s5,
                            end = AegisSpacing.s4,
                            top = AegisSpacing.s4,
                            bottom = AegisSpacing.s4,
                        ),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Column(Modifier.weight(1f)) {
                        if (title != null) {
                            Text(title, style = AegisTypography.h2.copy(color = AegisColors.textPrimary))
                        }
                        if (subtitle != null) {
                            Text(
                                subtitle,
                                style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                            )
                        }
                    }
                    if (actions != null) {
                        Box(Modifier.padding(end = AegisSpacing.s2)) { actions() }
                    }
                    IconButton(onClick = onClose) {
                        Icon(
                            imageVector = Icons.Filled.Close,
                            contentDescription = "Close",
                            tint = AegisColors.textSecondary,
                        )
                    }
                }
                AegisHDivider()
                Box(Modifier.weight(1f)) { content() }
            }
        }
    }
}
