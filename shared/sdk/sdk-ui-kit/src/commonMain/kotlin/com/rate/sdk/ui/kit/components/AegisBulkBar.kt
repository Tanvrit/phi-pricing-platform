package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisBulkBar — contextual action strip that appears when one or more table rows
 * are selected. It pins the multi-row affordances (delete / restore) next to a
 * live selection count and a Clear escape hatch, so operators can act on a batch
 * without hunting through per-row menus.
 *
 *   AegisBulkBar(
 *       count = selected.size,
 *       onClear = { selected = emptySet() },
 *       actions = {
 *           AegisButton("Delete", onClick = { … }, variant = Danger, size = Sm)
 *           AegisButton("Restore", onClick = { … }, variant = Secondary, size = Sm)
 *       },
 *   )
 *
 * Stateless and back-compatible: render it conditionally on `count > 0`. The
 * [actions] slot is a [Row] scope so callers drop in as many [AegisButton]s as the
 * surface supports. [label] lets the screen pass a grammar-correct noun ("3 plans
 * selected"); when null it falls back to a generic "N selected".
 */
@Composable
fun AegisBulkBar(
    count: Int,
    onClear: () -> Unit,
    modifier: Modifier = Modifier,
    label: String? = null,
    actions: @Composable () -> Unit,
) {
    if (count <= 0) return
    val shape = AegisRadii.shapeMd
    Row(
        modifier
            .fillMaxWidth()
            .background(AegisColors.brandTint, shape)
            .border(1.dp, AegisColors.brandTintStrong, shape)
            .padding(horizontal = AegisSpacing.s4, vertical = AegisSpacing.s3),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
    ) {
        Text(
            text = label ?: "$count selected",
            style = AegisTypography.body.copy(
                color = AegisColors.textPrimary,
                fontWeight = FontWeight.SemiBold,
            ),
        )
        Box(Modifier.weight(1f))
        actions()
        AegisButton(
            label = "Clear",
            onClick = onClear,
            variant = AegisButtonVariant.Ghost,
            size = AegisButtonSize.Sm,
        )
    }
}
