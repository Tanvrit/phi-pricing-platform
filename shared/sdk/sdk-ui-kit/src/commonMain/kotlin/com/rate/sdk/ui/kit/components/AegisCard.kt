package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisElevation
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisCard — surface container with hairline border + tiny shadow.
 *
 *   AegisCard(title = "Premium flow", action = { AegisButton("Export", …) }) { … }
 *
 * Slots:
 *   title — optional header title (h3).
 *   subtitle — small descriptor under title.
 *   action — composable in the top-right (button row, kebab, etc.).
 *   footer — composable rendered below content with a divider.
 *   content — main card body.
 */
@Composable
fun AegisCard(
    modifier: Modifier = Modifier,
    title: String? = null,
    subtitle: String? = null,
    action: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
    padding: PaddingValues = PaddingValues(AegisSpacing.s5),
    elevation: Dp = AegisElevation.raised,
    content: @Composable () -> Unit,
) {
    val shape = AegisRadii.shapeLg
    Column(
        modifier
            .shadow(elevation, shape, clip = false)
            .background(AegisColors.surface, shape)
            .border(1.dp, AegisColors.border, shape),
    ) {
        if (title != null || subtitle != null || action != null) {
            Row(
                Modifier.fillMaxWidth().padding(
                    start = AegisSpacing.s5,
                    end = AegisSpacing.s5,
                    top = AegisSpacing.s5,
                    bottom = if (title != null || subtitle != null) AegisSpacing.s3 else AegisSpacing.s4,
                ),
                verticalAlignment = Alignment.Top,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column(Modifier.weight(1f)) {
                    if (title != null) {
                        Text(title, style = AegisTypography.h3.copy(color = AegisColors.textPrimary))
                    }
                    if (subtitle != null) {
                        Text(
                            subtitle,
                            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                        )
                    }
                }
                if (action != null) {
                    Box(Modifier.padding(start = AegisSpacing.s3)) { action() }
                }
            }
        }
        Box(Modifier.padding(padding)) { content() }
        if (footer != null) {
            AegisHDivider()
            Box(Modifier.padding(horizontal = AegisSpacing.s5, vertical = AegisSpacing.s3)) {
                footer()
            }
        }
    }
}
