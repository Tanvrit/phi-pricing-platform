package com.rate.aegis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cancel
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.HourglassEmpty
import androidx.compose.material.icons.filled.Inventory2
import androidx.compose.material.icons.filled.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisTypography

/**
 * IRDAI / dashboard lifecycle statuses. Pass 4 §4.6.
 *
 * The pill conveys state with BOTH colour and icon (a11y).
 */
enum class AegisStatus(val label: String) {
    Draft("Draft"),
    InReview("In Review"),
    Scheduled("Scheduled"),
    Approved("Approved"),
    Live("Live"),
    Retired("Retired"),
    Rejected("Rejected"),
}

private data class PillTokens(
    val bg: Color,
    val fg: Color,
    val dot: Color,
    val icon: ImageVector,
)

@Composable
private fun tokens(s: AegisStatus): PillTokens = when (s) {
    AegisStatus.Draft -> PillTokens(
        bg = AegisColors.slate2,
        fg = AegisColors.slate9,
        dot = AegisColors.slate6,
        icon = Icons.Filled.EditNote,
    )
    AegisStatus.InReview -> PillTokens(
        bg = AegisColors.warn50,
        fg = AegisColors.warn700,
        dot = AegisColors.warn500,
        icon = Icons.Filled.HourglassEmpty,
    )
    AegisStatus.Scheduled -> PillTokens(
        bg = AegisColors.info50,
        fg = AegisColors.info700,
        dot = AegisColors.info500,
        icon = Icons.Filled.Schedule,
    )
    AegisStatus.Approved -> PillTokens(
        bg = AegisColors.success50,
        fg = AegisColors.success700,
        dot = AegisColors.success500,
        icon = Icons.Filled.CheckCircle,
    )
    AegisStatus.Live -> PillTokens(
        bg = AegisColors.success50,
        fg = AegisColors.success700,
        dot = AegisColors.success500,
        icon = Icons.Filled.CheckCircle,
    )
    AegisStatus.Retired -> PillTokens(
        bg = AegisColors.slate3,
        fg = AegisColors.slate8,
        dot = AegisColors.slate6,
        icon = Icons.Filled.Inventory2,
    )
    AegisStatus.Rejected -> PillTokens(
        bg = AegisColors.danger50,
        fg = AegisColors.danger700,
        dot = AegisColors.danger500,
        icon = Icons.Filled.Cancel,
    )
}

/**
 * AegisStatusPill — IRDAI-aware lifecycle pill.
 *
 *   AegisStatusPill(AegisStatus.Live)
 *   AegisStatusPill(AegisStatus.InReview, showIcon = false)
 *
 * State is conveyed via colour + icon together (WCAG: never colour alone).
 */
@Composable
fun AegisStatusPill(
    status: AegisStatus,
    modifier: Modifier = Modifier,
    showIcon: Boolean = true,
) {
    val t = tokens(status)
    Row(
        modifier = modifier
            .semantics { contentDescription = "Status: ${status.label}" }
            .background(t.bg, AegisRadii.shapePill)
            .padding(PaddingValues(horizontal = 10.dp, vertical = 3.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        if (showIcon) {
            Icon(
                imageVector = t.icon,
                contentDescription = null,
                tint = t.fg,
                modifier = Modifier.size(12.dp),
            )
        } else {
            Box(
                Modifier
                    .size(6.dp)
                    .background(t.dot, CircleShape),
            )
        }
        Text(
            text = status.label.uppercase(),
            style = AegisTypography.micro.copy(color = t.fg),
        )
    }
}
