package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisKbd — keyboard shortcut chip.
 *
 *   AegisKbd("⌘K")
 *   AegisKbd("⏎")
 *
 * Always announces "keyboard shortcut: <label>" for screen readers.
 */
@Composable
fun AegisKbd(
    label: String,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = AegisTypography.mono14.copy(color = AegisColors.textSecondary),
        modifier = modifier
            .semantics { contentDescription = "keyboard shortcut: $label" }
            .background(AegisColors.slate2, AegisRadii.shapeSm)
            .border(1.dp, AegisColors.slate4, AegisRadii.shapeSm)
            .padding(PaddingValues(horizontal = 6.dp, vertical = 2.dp)),
    )
}
