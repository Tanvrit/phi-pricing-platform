package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Inbox
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisEmptyState — friendly "nothing here yet" panel.
 *
 *   AegisEmptyState(
 *       title = "No quotes match your filters",
 *       helper = "Adjust the filters above or clear them to see all quotes.",
 *       action = { AegisButton("Clear filters", …, variant = Secondary) },
 *   )
 */
@Composable
fun AegisEmptyState(
    title: String,
    helper: String,
    modifier: Modifier = Modifier,
    action: (@Composable () -> Unit)? = null,
    icon: ImageVector? = Icons.Outlined.Inbox,
) {
    Column(
        modifier = modifier
            .fillMaxWidth()
            .padding(AegisSpacing.s5),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
    ) {
        if (icon != null) {
            Box(
                modifier = Modifier
                    .size(56.dp)
                    .background(AegisColors.slate2, AegisRadii.shapeLg),
                contentAlignment = Alignment.Center,
            ) {
                Icon(
                    imageVector = icon,
                    contentDescription = null,
                    tint = AegisColors.slate7,
                    modifier = Modifier.size(28.dp),
                )
            }
        }
        Text(
            text = title,
            style = AegisTypography.h3.copy(color = AegisColors.textPrimary),
            textAlign = TextAlign.Center,
        )
        Text(
            text = helper,
            style = AegisTypography.body.copy(color = AegisColors.textSecondary),
            textAlign = TextAlign.Center,
        )
        if (action != null) {
            Box(modifier = Modifier.padding(top = AegisSpacing.s2)) { action() }
        }
    }
}
