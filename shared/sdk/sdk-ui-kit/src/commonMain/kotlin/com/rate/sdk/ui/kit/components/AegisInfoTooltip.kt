package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Info
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisElevation
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/** Where the definition panel floats relative to the (i) trigger. */
enum class AegisTooltipPlacement { Below, Above }

/**
 * AegisInfoTooltip — a small "(i)" info affordance that reveals a short definition
 * for a piece of jargon. Built for the operator console where actuarial terms
 * (EVA, IBNR, U-factor, …) need an inline, glanceable explainer.
 *
 *   AegisInfoTooltip(term = "IBNR", definition = glossaryDefinition("glossary.ibnr"))
 *
 * Interaction:
 *   - Hover (desktop) reveals the panel; moving away hides it.
 *   - Click toggles a "pinned" panel so it survives the pointer leaving — useful
 *     on touch and for reading longer definitions. Clicking again unpins it.
 *
 * The panel is rendered inline (not a platform Popup) so it stays inside common
 * Compose-MP APIs and compiles on every target. It anchors to the trigger via a
 * wrapping [Box] and floats [placement] of the icon; the parent should leave a
 * little vertical room (it overlaps siblings, it does not push layout).
 */
@Composable
fun AegisInfoTooltip(
    term: String,
    definition: String,
    modifier: Modifier = Modifier,
    placement: AegisTooltipPlacement = AegisTooltipPlacement.Below,
    iconSize: androidx.compose.ui.unit.Dp = 16.dp,
    tint: Color = AegisColors.iconMuted,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    var pinned by remember { mutableStateOf(false) }

    // While the pointer is over the trigger the panel shows; a click pins it open.
    val visible = hovered || pinned

    Box(modifier.wrapContentSize(), contentAlignment = Alignment.Center) {
        Icon(
            imageVector = Icons.Outlined.Info,
            // Null here; the richer description lives on the semantics modifier below.
            contentDescription = null,
            tint = tint,
            modifier = Modifier
                .size(iconSize)
                .hoverable(interaction)
                .clickable { pinned = !pinned }
                .semantics { contentDescription = "$term: $definition" },
        )

        if (visible) {
            // Float the panel above/below the trigger without affecting layout.
            val panelAlignment = when (placement) {
                AegisTooltipPlacement.Below -> Alignment.TopCenter
                AegisTooltipPlacement.Above -> Alignment.BottomCenter
            }
            Box(
                Modifier
                    .wrapContentSize(unbounded = true)
                    .align(panelAlignment),
            ) {
                TooltipPanel(
                    term = term,
                    definition = definition,
                    placement = placement,
                    triggerSize = iconSize,
                )
            }
        }
    }
}

/** The floating definition card. Kept private — it only exists for [AegisInfoTooltip]. */
@Composable
private fun TooltipPanel(
    term: String,
    definition: String,
    placement: AegisTooltipPlacement,
    triggerSize: androidx.compose.ui.unit.Dp,
) {
    // Nudge the panel clear of the trigger glyph in the chosen direction.
    val verticalNudge = triggerSize + AegisSpacing.s2
    val offsetModifier = when (placement) {
        AegisTooltipPlacement.Below -> Modifier.padding(top = verticalNudge)
        AegisTooltipPlacement.Above -> Modifier.padding(bottom = verticalNudge)
    }
    val shape = AegisRadii.shapeMd
    Column(
        offsetModifier
            .width(260.dp)
            .shadow(AegisElevation.popover, shape, clip = false)
            .background(AegisColors.surface, shape)
            .border(1.dp, AegisColors.border, shape)
            .padding(horizontal = AegisSpacing.s3, vertical = AegisSpacing.s3),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s1),
    ) {
        Text(term, style = AegisTypography.small.copy(color = AegisColors.textPrimary))
        Text(definition, style = AegisTypography.caption.copy(color = AegisColors.textSecondary))
    }
}

/**
 * AegisTermLabel — a label followed by an inline [AegisInfoTooltip], for headers
 * and field captions that contain jargon.
 *
 *   AegisTermLabel(label = "Loss Ratio", term = "Loss Ratio", definition = …)
 */
@Composable
fun AegisTermLabel(
    label: String,
    term: String,
    definition: String,
    modifier: Modifier = Modifier,
    style: androidx.compose.ui.text.TextStyle = AegisTypography.small.copy(color = AegisColors.textPrimary),
    placement: AegisTooltipPlacement = AegisTooltipPlacement.Below,
) {
    androidx.compose.foundation.layout.Row(
        modifier,
        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s1),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(label, style = style)
        AegisInfoTooltip(term = term, definition = definition, placement = placement)
    }
}
