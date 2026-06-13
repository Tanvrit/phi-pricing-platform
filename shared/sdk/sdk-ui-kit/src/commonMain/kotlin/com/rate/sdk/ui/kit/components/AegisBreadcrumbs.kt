package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisBreadcrumbs — text-only navigation crumbs with `›` separators.
 *
 *   AegisBreadcrumbs(listOf("Plans", "PHI Flagship 2"), onCrumbClick = { … })
 *
 * Last crumb is rendered as plain text (not clickable) since you're already there.
 */
@Composable
fun AegisBreadcrumbs(
    crumbs: List<String>,
    onCrumbClick: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        crumbs.forEachIndexed { idx, crumb ->
            val isLast = idx == crumbs.lastIndex
            if (isLast) {
                Text(
                    text = crumb,
                    style = AegisTypography.small.copy(
                        color = AegisColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            } else {
                Text(
                    text = crumb,
                    style = AegisTypography.small.copy(
                        color = AegisColors.textSecondary,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = Modifier
                        .clickable { onCrumbClick(idx) }
                        .padding(vertical = 2.dp),
                )
                Text(
                    text = "›",
                    style = AegisTypography.small.copy(color = AegisColors.slate6),
                )
            }
        }
    }
}
