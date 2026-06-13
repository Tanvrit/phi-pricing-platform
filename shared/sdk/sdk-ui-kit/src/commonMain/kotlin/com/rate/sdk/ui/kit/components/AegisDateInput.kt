package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.DateRange
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisDateInput — a validated text date field.
 *
 * Compose-MP has no stock date picker in `commonMain`, so this is a plain (masked-by-convention)
 * text input reusing [AegisInput] under the hood, with a calendar leading icon and inline ISO
 * validation. Dates are kept as ISO strings "YYYY-MM-DD"; the helper shows the expected format and
 * the field turns red on an invalid (non-blank) value.
 *
 * The icon sits to the left of the field rather than inside it (AegisInput's adornment slots are
 * text-only), so the control reads as a date affordance without modifying [AegisInput].
 */
@Composable
fun AegisDateInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
) {
    val trimmed = value.trim()
    val invalid = trimmed.isNotEmpty() && !isValidIsoDate(trimmed)
    DateRow(
        icon = Icons.Filled.CalendarMonth,
        invalid = invalid,
    ) {
        AegisInput(
            value = value,
            onValueChange = onValueChange,
            label = label,
            helper = helper ?: "Format YYYY-MM-DD (e.g. 2026-06-10)",
            error = if (invalid) "Enter a valid date as YYYY-MM-DD" else null,
            placeholder = "2026-06-10",
            enabled = enabled,
            modifier = Modifier.fillMaxWidth(),
        )
    }
}

/**
 * AegisDateRangeInput — two [AegisDateInput]s editing one "from..to" ISO range string.
 *
 * The range is stored as a single buffer value "YYYY-MM-DD..YYYY-MM-DD"; this control splits it on
 * the "`..`" separator for editing and re-joins on every change. An end before the start is flagged
 * inline. Either side may be left blank (an open-ended range) without erroring.
 */
@Composable
fun AegisDateRangeInput(
    value: String,
    onValueChange: (String) -> Unit,
    label: String,
    modifier: Modifier = Modifier,
    helper: String? = null,
    required: Boolean = false,
    enabled: Boolean = true,
) {
    val (from, to) = splitIsoRange(value)
    val fromInvalid = from.isNotEmpty() && !isValidIsoDate(from)
    val toInvalid = to.isNotEmpty() && !isValidIsoDate(to)
    val orderInvalid = !fromInvalid && !toInvalid && from.isNotEmpty() && to.isNotEmpty() && to < from

    Column(modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(AegisSpacing.s1)) {
        Text(
            text = label,
            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
        )
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
            verticalAlignment = Alignment.Top,
        ) {
            DateRow(
                icon = Icons.Filled.DateRange,
                invalid = fromInvalid,
                modifier = Modifier.weight(1f),
            ) {
                AegisInput(
                    value = from,
                    onValueChange = { onValueChange(joinIsoRange(it, to)) },
                    label = "From",
                    helper = "YYYY-MM-DD",
                    error = if (fromInvalid) "Invalid date" else null,
                    placeholder = "2026-06-10",
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            DateRow(
                icon = Icons.Filled.DateRange,
                invalid = toInvalid || orderInvalid,
                modifier = Modifier.weight(1f),
            ) {
                AegisInput(
                    value = to,
                    onValueChange = { onValueChange(joinIsoRange(from, it)) },
                    label = "To",
                    helper = "YYYY-MM-DD",
                    error = when {
                        toInvalid -> "Invalid date"
                        orderInvalid -> "End is before start"
                        else -> null
                    },
                    placeholder = "2026-12-31",
                    enabled = enabled,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        }
        Text(
            text = helper ?: "Inclusive range — leave a side blank for open-ended.",
            style = AegisTypography.small.copy(color = AegisColors.textSecondary),
        )
    }
}

@Composable
private fun DateRow(
    icon: ImageVector,
    invalid: Boolean,
    modifier: Modifier = Modifier,
    field: @Composable () -> Unit,
) {
    Row(
        modifier = modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
        verticalAlignment = Alignment.Top,
    ) {
        Box(Modifier.padding(top = 26.dp)) {
            Icon(
                imageVector = icon,
                contentDescription = null,
                tint = if (invalid) AegisColors.danger500 else AegisColors.textTertiary,
                modifier = Modifier.size(18.dp),
            )
        }
        Box(Modifier.weight(1f)) { field() }
    }
}

/**
 * Validate a strict ISO calendar date "YYYY-MM-DD": four-digit year, 1–12 month, day within the
 * month's real length (leap-year aware for February). Pure + dependency-free for commonMain.
 */
fun isValidIsoDate(s: String): Boolean {
    val parts = s.split('-')
    if (parts.size != 3) return false
    val (yStr, mStr, dStr) = parts
    if (yStr.length != 4 || mStr.length != 2 || dStr.length != 2) return false
    val year = yStr.toIntOrNull() ?: return false
    val month = mStr.toIntOrNull() ?: return false
    val day = dStr.toIntOrNull() ?: return false
    if (month !in 1..12) return false
    if (day < 1 || day > daysInMonth(year, month)) return false
    return true
}

private fun daysInMonth(year: Int, month: Int): Int = when (month) {
    1, 3, 5, 7, 8, 10, 12 -> 31
    4, 6, 9, 11 -> 30
    2 -> if (isLeapYear(year)) 29 else 28
    else -> 0
}

private fun isLeapYear(year: Int): Boolean =
    (year % 4 == 0 && year % 100 != 0) || (year % 400 == 0)

/** Split a "from..to" range buffer string into its two ISO halves (either may be blank). */
internal fun splitIsoRange(value: String): Pair<String, String> {
    val idx = value.indexOf("..")
    if (idx < 0) return value.trim() to ""
    return value.substring(0, idx).trim() to value.substring(idx + 2).trim()
}

/** Re-join two ISO halves into a "from..to" buffer string (collapses to "" when both blank). */
internal fun joinIsoRange(from: String, to: String): String {
    val f = from.trim()
    val t = to.trim()
    if (f.isEmpty() && t.isEmpty()) return ""
    return "$f..$t"
}
