package com.rate.aegis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisTypography

/**
 * AegisChip — pill-shaped selectable chip.
 *
 *   AegisChip("Domestic", selected = true, onClick = { … })
 *   AegisChip("2A2C", selected = false)
 *
 * Selected chips show indigo50 bg + indigo500 border + indigo700 text; unselected
 * are slate2 / slate4 border / slate9 text. Hover lifts the bg one slate step.
 * Read-only chips (passing `onClick = null`) skip the hover state.
 *
 * Local stub.
 */
@Composable
fun AegisChip(
    label: String,
    modifier: Modifier = Modifier,
    selected: Boolean = false,
    onClick: (() -> Unit)? = null,
    leadingIcon: (@Composable () -> Unit)? = null,
    tone: AegisChipTone = AegisChipTone.Neutral,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val bg: Color
    val border: Color
    val fg: Color
    when {
        selected && tone == AegisChipTone.Neutral -> {
            bg = AegisColors.indigo50
            border = AegisColors.indigo500
            fg = AegisColors.indigo700
        }
        tone == AegisChipTone.Success -> {
            bg = AegisColors.success50
            border = AegisColors.success500
            fg = AegisColors.success700
        }
        tone == AegisChipTone.Warn -> {
            bg = AegisColors.warn50
            border = AegisColors.warn500
            fg = AegisColors.warn700
        }
        tone == AegisChipTone.Danger -> {
            bg = AegisColors.danger50
            border = AegisColors.danger500
            fg = AegisColors.danger700
        }
        tone == AegisChipTone.Info -> {
            bg = AegisColors.info50
            border = AegisColors.info500
            fg = AegisColors.info700
        }
        else -> {
            bg = if (hovered && onClick != null) AegisColors.slate3 else AegisColors.slate2
            border = AegisColors.slate4
            fg = AegisColors.textPrimary
        }
    }

    val clickMod = if (onClick != null) Modifier.clickable(
        interactionSource = interaction,
        indication = null,
        onClick = onClick,
    ) else Modifier

    Row(
        modifier = modifier
            .background(bg, AegisRadii.shapePill)
            .border(1.dp, border, AegisRadii.shapePill)
            .then(clickMod)
            .padding(PaddingValues(horizontal = 12.dp, vertical = 6.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (selected && leadingIcon == null && tone == AegisChipTone.Neutral) {
            Icon(
                imageVector = Icons.Filled.Check,
                contentDescription = null,
                tint = fg,
                modifier = Modifier.size(14.dp),
            )
        } else if (leadingIcon != null) {
            leadingIcon()
        }
        Text(
            text = label,
            style = AegisTypography.small.copy(
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}

enum class AegisChipTone { Neutral, Success, Warn, Danger, Info }
