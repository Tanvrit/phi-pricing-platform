package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.PointerIcon
import androidx.compose.ui.input.pointer.pointerHoverIcon
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisTypography

enum class AegisButtonVariant { Primary, Secondary, Ghost, Danger }
enum class AegisButtonSize { Sm, Md, Lg }

private data class ButtonPalette(
    val bg: Color,
    val bgHover: Color,
    val bgPressed: Color,
    val fg: Color,
    val border: Color?,
    val borderHover: Color?,
)

@Composable
private fun paletteFor(variant: AegisButtonVariant): ButtonPalette = when (variant) {
    AegisButtonVariant.Primary -> ButtonPalette(
        bg = AegisColors.brand,
        bgHover = AegisColors.brandHover,
        bgPressed = AegisColors.brandPressed,
        fg = Color.White,
        border = null,
        borderHover = null,
    )
    AegisButtonVariant.Secondary -> ButtonPalette(
        bg = AegisColors.surface,
        bgHover = AegisColors.slate2,
        bgPressed = AegisColors.slate3,
        fg = AegisColors.textPrimary,
        border = AegisColors.border,
        borderHover = AegisColors.borderStrong,
    )
    AegisButtonVariant.Ghost -> ButtonPalette(
        bg = Color.Transparent,
        bgHover = AegisColors.slate2,
        bgPressed = AegisColors.slate3,
        fg = AegisColors.textPrimary,
        border = null,
        borderHover = null,
    )
    AegisButtonVariant.Danger -> ButtonPalette(
        bg = AegisColors.danger500,
        bgHover = AegisColors.danger700,
        bgPressed = AegisColors.danger700,
        fg = Color.White,
        border = null,
        borderHover = null,
    )
}

/**
 * AegisButton — primary action affordance.
 *
 *   AegisButton(label = "Publish", onClick = { … })
 *   AegisButton(label = "Save draft", variant = AegisButtonVariant.Secondary)
 *   AegisButton(label = "Delete", variant = AegisButtonVariant.Danger)
 *   AegisButton(label = "Saving…", loading = true)
 *
 * Slots:
 *   leadingIcon — composable (typically an Icon) rendered before the label.
 *   trailingIcon — composable rendered after the label.
 *
 * Loading state replaces the leading icon with a 16dp spinner; the click handler
 * is disabled while loading is true.
 */
@Composable
fun AegisButton(
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    variant: AegisButtonVariant = AegisButtonVariant.Primary,
    size: AegisButtonSize = AegisButtonSize.Md,
    enabled: Boolean = true,
    loading: Boolean = false,
    leadingIcon: (@Composable () -> Unit)? = null,
    trailingIcon: (@Composable () -> Unit)? = null,
    contentDescription: String = label,
) {
    val palette = paletteFor(variant)
    val interactionSource = remember { MutableInteractionSource() }
    val isHovered by interactionSource.collectIsHoveredAsState()
    val isPressed by interactionSource.collectIsPressedAsState()
    val isFocused by interactionSource.collectIsFocusedAsState()

    val effectiveEnabled = enabled && !loading
    val bg = when {
        !effectiveEnabled -> AegisColors.slate3
        isPressed -> palette.bgPressed
        isHovered -> palette.bgHover
        else -> palette.bg
    }
    val fg = if (effectiveEnabled) palette.fg else AegisColors.textDisabled
    val borderColor = when {
        !effectiveEnabled -> AegisColors.slate4
        isHovered -> palette.borderHover ?: palette.border
        else -> palette.border
    }

    val (padding, minH, textStyle) = when (size) {
        AegisButtonSize.Sm -> Triple(PaddingValues(horizontal = 10.dp, vertical = 4.dp), 28.dp, AegisTypography.small.copy(fontWeight = AegisTypography.h3.fontWeight))
        AegisButtonSize.Md -> Triple(PaddingValues(horizontal = 14.dp, vertical = 8.dp), 36.dp, AegisTypography.body.copy(fontWeight = AegisTypography.h3.fontWeight))
        AegisButtonSize.Lg -> Triple(PaddingValues(horizontal = 18.dp, vertical = 12.dp), 44.dp, AegisTypography.bodyL.copy(fontWeight = AegisTypography.h3.fontWeight))
    }

    val baseModifier = modifier
        .semantics {
            role = Role.Button
            this.contentDescription = contentDescription
        }
        .pointerHoverIcon(PointerIcon.Default)
        .defaultMinSize(minHeight = minH)
        .background(bg, AegisRadii.shapeMd)

    val withBorder = if (borderColor != null) {
        baseModifier.border(BorderStroke(1.dp, borderColor), AegisRadii.shapeMd)
    } else if (isFocused && effectiveEnabled) {
        baseModifier.border(BorderStroke(2.dp, AegisColors.focusRing), AegisRadii.shapeMd)
    } else {
        baseModifier
    }

    val clickable = withBorder
        .clickable(
            enabled = effectiveEnabled,
            interactionSource = interactionSource,
            indication = null,
            onClick = onClick,
        )
        .padding(padding)

    Row(
        modifier = clickable,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        CompositionLocalProvider(LocalContentColor provides fg) {
            if (loading) {
                CircularProgressIndicator(
                    color = fg,
                    strokeWidth = 1.5.dp,
                    modifier = Modifier.size(14.dp),
                )
            } else if (leadingIcon != null) {
                leadingIcon()
            }
            Text(text = label, style = textStyle.copy(color = fg))
            if (!loading && trailingIcon != null) {
                trailingIcon()
            }
        }
    }
}
