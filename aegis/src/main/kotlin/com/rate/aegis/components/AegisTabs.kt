package com.rate.aegis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.theme.AegisTypography

/**
 * AegisTabs — segmented tab bar with indigo500 underline for the active tab.
 *
 *   AegisTabs(listOf("Overview", "Coverage"), active = 0, onChange = { … })
 *
 * Local stub. Foundation will promote.
 */
@Composable
fun AegisTabs(
    tabs: List<String>,
    activeIndex: Int,
    onTabChange: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(40.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            tabs.forEachIndexed { idx, label ->
                AegisTab(
                    label = label,
                    selected = idx == activeIndex,
                    onClick = { onTabChange(idx) },
                )
            }
            Box(Modifier.weight(1f))
        }
        // The 1px slate3 line on which tab underlines float.
        AegisHDivider()
    }
}

@Composable
private fun AegisTab(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()

    val color = when {
        selected -> AegisColors.indigo700
        hovered -> AegisColors.textPrimary
        else -> AegisColors.textSecondary
    }

    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clickable(
                interactionSource = interaction,
                indication = null,
                onClick = onClick,
            )
            .padding(horizontal = AegisSpacing.s4)
            .height(40.dp),
    ) {
        Box(Modifier.weight(1f), contentAlignment = Alignment.Center) {
            Text(
                text = label,
                style = AegisTypography.body.copy(
                    color = color,
                    fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
                ),
            )
        }
        // Active underline floats just above the 1px divider below the tabs row.
        Box(
            Modifier
                .width(48.dp)
                .height(2.dp)
                .background(if (selected) AegisColors.indigo500 else androidx.compose.ui.graphics.Color.Transparent),
        )
    }
}
