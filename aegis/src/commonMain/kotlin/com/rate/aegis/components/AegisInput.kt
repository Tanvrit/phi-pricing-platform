package com.rate.aegis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsFocusedAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.unit.dp
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.theme.AegisTypography

/**
 * AegisInput — single-line text input.
 *
 * Layout: label on top, input field, helper / error below. Prefix and suffix
 * adornments sit inside the field with subdued styling. Error state replaces the
 * helper text and tints the border in `danger500`.
 */
@Composable
fun AegisInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    error: String? = null,
    prefix: String? = null,
    suffix: String? = null,
    placeholder: String? = null,
    enabled: Boolean = true,
) {
    val interaction = remember { MutableInteractionSource() }
    val focused by interaction.collectIsFocusedAsState()

    val borderColor = when {
        error != null -> AegisColors.danger500
        focused -> AegisColors.brand
        else -> AegisColors.border
    }
    val shape = AegisRadii.shapeMd

    Column(modifier.fillMaxWidth()) {
        Text(
            text = label,
            style = AegisTypography.small.copy(
                color = AegisColors.textSecondary,
                fontWeight = androidx.compose.ui.text.font.FontWeight.Medium,
            ),
            modifier = Modifier.padding(bottom = 6.dp),
        )
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .defaultMinSize(minHeight = 38.dp)
                .background(
                    if (enabled) AegisColors.surface else AegisColors.surfaceMuted,
                    shape,
                )
                .border(1.dp, borderColor, shape)
                .padding(horizontal = AegisSpacing.s3, vertical = 0.dp),
        ) {
            if (prefix != null) {
                Text(
                    text = prefix,
                    style = AegisTypography.body.copy(color = AegisColors.textTertiary),
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Box(Modifier.weight(1f)) {
                BasicTextField(
                    value = value,
                    onValueChange = onValueChange,
                    enabled = enabled,
                    interactionSource = interaction,
                    singleLine = true,
                    textStyle = AegisTypography.body.copy(
                        color = if (enabled) AegisColors.textPrimary else AegisColors.textDisabled,
                    ),
                    cursorBrush = SolidColor(AegisColors.brand),
                    modifier = Modifier.fillMaxWidth().padding(vertical = 9.dp),
                    decorationBox = { inner ->
                        if (value.isEmpty() && placeholder != null) {
                            Text(
                                text = placeholder,
                                style = AegisTypography.body.copy(color = AegisColors.textTertiary),
                            )
                        }
                        inner()
                    },
                )
            }
            if (suffix != null) {
                Text(
                    text = suffix,
                    style = AegisTypography.body.copy(color = AegisColors.textTertiary),
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
        }
        val foot = error ?: helper
        if (foot != null) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                modifier = Modifier.padding(top = 4.dp),
            ) {
                if (error != null) {
                    Icon(
                        imageVector = Icons.Filled.Error,
                        contentDescription = null,
                        tint = AegisColors.danger500,
                        modifier = Modifier.size(12.dp),
                    )
                }
                Text(
                    text = foot,
                    style = AegisTypography.small.copy(
                        color = if (error != null) AegisColors.danger700 else AegisColors.textSecondary,
                    ),
                )
            }
        }
    }
}

/**
 * AegisMoneyField — display + edit a `Money` value as Indian-grouped rupees.
 * Keeps editing string-buffered so partial input doesn't fight the formatter.
 */
@Composable
fun AegisMoneyField(
    value: com.rate.domain.money.Money,
    onValueChange: (com.rate.domain.money.Money) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    enabled: Boolean = true,
) {
    // For read/edit parity: show rupees only (no paise) — the engine rounds.
    val text = if (value.paise == 0L) "" else (value.paise / 100L).toString()
    AegisInput(
        value = text,
        onValueChange = { raw ->
            val digits = raw.filter { it.isDigit() }
            val rupees = digits.toLongOrNull() ?: 0L
            onValueChange(com.rate.domain.money.Money.fromRupees(rupees))
        },
        label = label,
        helper = helper ?: value.formatIndian(showSymbol = true, showDecimals = false),
        prefix = "₹",
        enabled = enabled,
        modifier = modifier,
    )
}
