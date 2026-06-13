package com.rate.sdk.ui.kit.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisElevation
import com.rate.sdk.ui.kit.theme.AegisMotion
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisConfirmDialog — small centred modal that gates a destructive (or otherwise
 * irreversible) action behind an explicit confirm.
 *
 *   AegisConfirmDialog(
 *       open = pendingDelete != null,
 *       title = "Delete plan?",
 *       body = "This soft-deletes it; you can restore later.",
 *       confirmLabel = "Delete",
 *       danger = true,
 *       onConfirm = { vm.softDelete(pendingDelete!!); pendingDelete = null },
 *       onCancel = { pendingDelete = null },
 *   )
 *
 * Stateless: the caller owns the [open] flag and clears it from inside the
 * callbacks. Rendered INLINE as a full-size scrim + centred panel (NOT a platform
 * Popup) so it stays in common code and matches [AegisDrawer]'s approach — mount
 * it at the screen root (a sibling of the scrolling content) so the scrim covers
 * the surface. Tapping the scrim fires [onCancel]; clicks on the panel are
 * absorbed. [danger] paints the confirm button red and swaps the leading glyph
 * for a warning triangle so destructive intent reads instantly.
 */
@Composable
fun AegisConfirmDialog(
    open: Boolean,
    title: String,
    body: String,
    onConfirm: () -> Unit,
    onCancel: () -> Unit,
    modifier: Modifier = Modifier,
    confirmLabel: String = "Confirm",
    cancelLabel: String = "Cancel",
    danger: Boolean = true,
) {
    Box(modifier.fillMaxSize()) {
        // Scrim — tappable to cancel.
        AnimatedVisibility(
            visible = open,
            enter = fadeIn(animationSpec = AegisMotion.tweenDrawer()),
            exit = fadeOut(animationSpec = AegisMotion.tweenDrawer()),
        ) {
            val scrimSource = remember { MutableInteractionSource() }
            Box(
                Modifier
                    .fillMaxSize()
                    .background(Color.Black.copy(alpha = 0.32f))
                    .clickable(
                        interactionSource = scrimSource,
                        indication = null,
                        onClick = onCancel,
                    ),
            )
        }
        // Centred panel.
        AnimatedVisibility(
            visible = open,
            modifier = Modifier.align(Alignment.Center),
            enter = fadeIn(animationSpec = AegisMotion.tweenDrawer()) +
                scaleIn(initialScale = 0.96f, animationSpec = AegisMotion.tweenDrawer()),
            exit = fadeOut(animationSpec = AegisMotion.tweenDrawer()) +
                scaleOut(targetScale = 0.96f, animationSpec = AegisMotion.tweenDrawer()),
        ) {
            val shape = AegisRadii.shapeLg
            val absorb = remember { MutableInteractionSource() }
            Column(
                Modifier
                    .widthIn(min = 360.dp, max = 440.dp)
                    .shadow(AegisElevation.modal, shape, clip = false)
                    .background(AegisColors.surface, shape)
                    .border(1.dp, AegisColors.border, shape)
                    .clickable(interactionSource = absorb, indication = null, onClick = {})
                    .padding(AegisSpacing.s5),
                verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
            ) {
                Row(
                    Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.Top,
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
                ) {
                    Icon(
                        imageVector = if (danger) Icons.Filled.Warning else Icons.Filled.Error,
                        contentDescription = null,
                        tint = if (danger) AegisColors.danger500 else AegisColors.info500,
                        modifier = Modifier.size(22.dp).padding(top = 2.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(
                            title,
                            style = AegisTypography.h3.copy(color = AegisColors.textPrimary),
                        )
                        Box(Modifier.size(AegisSpacing.s1))
                        Text(
                            body,
                            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
                        )
                    }
                }
                Row(
                    Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2, Alignment.End),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    AegisButton(
                        label = cancelLabel,
                        onClick = onCancel,
                        variant = AegisButtonVariant.Secondary,
                        size = AegisButtonSize.Sm,
                    )
                    AegisButton(
                        label = confirmLabel,
                        onClick = onConfirm,
                        variant = if (danger) AegisButtonVariant.Danger else AegisButtonVariant.Primary,
                        size = AegisButtonSize.Sm,
                    )
                }
            }
        }
    }
}
