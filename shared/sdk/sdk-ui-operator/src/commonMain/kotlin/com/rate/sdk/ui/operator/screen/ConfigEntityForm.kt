package com.rate.sdk.ui.operator.screen

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import com.rate.sdk.ui.kit.components.AegisToggle
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.rate.sdk.ui.kit.components.AegisCallout
import com.rate.sdk.ui.kit.components.AegisChip
import com.rate.sdk.ui.kit.components.AegisDateInput
import com.rate.sdk.ui.kit.components.AegisDateRangeInput
import com.rate.sdk.ui.kit.components.AegisInput
import com.rate.sdk.ui.kit.components.CalloutKind
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography
import com.rate.sdk.ui.operator.model.EntityDescriptor
import com.rate.sdk.ui.operator.model.FieldKind
import com.rate.sdk.ui.operator.model.FormBuffer
import com.rate.sdk.ui.operator.model.FormField

/**
 * Renders an admin entity's edit form purely from its [EntityDescriptor.fields]. Each [FormField]'s
 * [FieldKind] picks the right control (text, multiline, number, money, enum-chips, bool-switch,
 * comma-list, readonly). The buffer is owned by the [com.rate.sdk.ui.operator.viewmodel.ConfigEntityViewModel];
 * this composable is stateless and just paints + bubbles edits via [onFieldChange].
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ConfigEntityForm(
    descriptor: EntityDescriptor,
    buffer: FormBuffer,
    errors: List<String>,
    onFieldChange: (String, String) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier
            .fillMaxWidth()
            .verticalScroll(rememberScrollState())
            .padding(AegisSpacing.s5),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4),
    ) {
        if (errors.isNotEmpty()) {
            AegisCallout(
                kind = CalloutKind.DANGER,
                title = "Please fix ${errors.size} issue${if (errors.size == 1) "" else "s"}",
                body = errors.joinToString("\n") { "• $it" },
            )
        }

        descriptor.fields.forEach { field ->
            val value = buffer[field.key] ?: ""
            when (field.kind) {
                FieldKind.TEXT, FieldKind.NUMBER, FieldKind.MONEY ->
                    AegisInput(
                        value = value,
                        onValueChange = { onFieldChange(field.key, sanitize(field.kind, it)) },
                        label = labelOf(field),
                        helper = field.helper,
                        prefix = if (field.kind == FieldKind.MONEY) "₹" else null,
                    )

                FieldKind.MULTILINE ->
                    AegisInput(
                        value = value,
                        onValueChange = { onFieldChange(field.key, it) },
                        label = labelOf(field),
                        helper = field.helper,
                    )

                FieldKind.LIST, FieldKind.MULTI_SELECT ->
                    AegisInput(
                        value = value,
                        onValueChange = { onFieldChange(field.key, it) },
                        label = labelOf(field),
                        helper = field.helper
                            ?: field.refEntityId?.let { "Comma-separated ids from $it" }
                            ?: "Comma or newline separated",
                    )

                FieldKind.ENTITY_PICKER ->
                    AegisInput(
                        value = value,
                        onValueChange = { onFieldChange(field.key, it) },
                        label = labelOf(field),
                        helper = field.helper ?: ("Id from " + (field.refEntityId ?: "linked entity")),
                    )

                // Structured row/map editors: edited as JSON until the rich row-grid lands.
                // The descriptor JSON-round-trips these through the buffer, so this stays correct.
                FieldKind.TABLE, FieldKind.KEY_VALUE ->
                    AegisInput(
                        value = value,
                        onValueChange = { onFieldChange(field.key, it) },
                        label = labelOf(field),
                        helper = field.helper ?: "Structured rows (JSON)",
                    )

                FieldKind.DATE ->
                    AegisDateInput(
                        value = value,
                        onValueChange = { onFieldChange(field.key, it) },
                        label = labelOf(field),
                        helper = field.helper,
                        required = field.required,
                    )

                FieldKind.DATE_RANGE ->
                    AegisDateRangeInput(
                        value = value,
                        onValueChange = { onFieldChange(field.key, it) },
                        label = labelOf(field),
                        helper = field.helper,
                        required = field.required,
                    )

                FieldKind.BOOL ->
                    BoolField(
                        label = labelOf(field),
                        helper = field.helper,
                        checked = value.equals("true", ignoreCase = true),
                        onChange = { onFieldChange(field.key, it.toString()) },
                    )

                FieldKind.ENUM ->
                    EnumField(
                        field = field,
                        value = value,
                        onSelect = { onFieldChange(field.key, it) },
                    )

                FieldKind.READONLY ->
                    Column {
                        Text(labelOf(field), style = AegisTypography.small.copy(color = AegisColors.textSecondary))
                        Text(
                            value.ifBlank { "—" },
                            style = AegisTypography.body.copy(color = AegisColors.textTertiary),
                        )
                        field.helper?.let {
                            Text(it, style = AegisTypography.small.copy(color = AegisColors.textTertiary))
                        }
                    }
            }
        }
    }
}

private fun labelOf(field: FormField): String =
    if (field.required) "${field.label} *" else field.label

/** Strips non-numeric characters for NUMBER/MONEY kinds so the buffer stays parseable. */
private fun sanitize(kind: FieldKind, raw: String): String = when (kind) {
    FieldKind.MONEY -> raw.filter { it.isDigit() }
    FieldKind.NUMBER -> raw.filter { it.isDigit() || it == '.' || it == '-' }
    else -> raw
}

@Composable
private fun BoolField(label: String, helper: String?, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
    ) {
        AegisToggle(checked = checked, onCheckedChange = onChange)
        Column {
            Text(label, style = AegisTypography.body.copy(color = AegisColors.textPrimary))
            helper?.let { Text(it, style = AegisTypography.small.copy(color = AegisColors.textSecondary)) }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EnumField(field: FormField, value: String, onSelect: (String) -> Unit) {
    Column(verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
        Text(labelOf(field), style = AegisTypography.small.copy(color = AegisColors.textSecondary))
        FlowRow(horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2)) {
            field.options.forEach { opt ->
                AegisChip(
                    label = opt,
                    selected = opt.equals(value, ignoreCase = true),
                    onClick = { onSelect(opt) },
                )
            }
        }
        field.helper?.let { Text(it, style = AegisTypography.small.copy(color = AegisColors.textSecondary)) }
    }
}
