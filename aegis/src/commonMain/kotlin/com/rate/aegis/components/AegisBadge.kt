package com.rate.aegis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisTypography

enum class AegisBadgeTone { Neutral, Brand, Success, Warn, Danger, Info }

private fun bgFor(tone: AegisBadgeTone): Color = when (tone) {
    AegisBadgeTone.Neutral -> AegisColors.slate3
    AegisBadgeTone.Brand -> AegisColors.indigo100
    AegisBadgeTone.Success -> AegisColors.success100
    AegisBadgeTone.Warn -> AegisColors.warn100
    AegisBadgeTone.Danger -> AegisColors.danger100
    AegisBadgeTone.Info -> AegisColors.info100
}

private fun fgFor(tone: AegisBadgeTone): Color = when (tone) {
    AegisBadgeTone.Neutral -> AegisColors.slate10
    AegisBadgeTone.Brand -> AegisColors.indigo700
    AegisBadgeTone.Success -> AegisColors.success700
    AegisBadgeTone.Warn -> AegisColors.warn700
    AegisBadgeTone.Danger -> AegisColors.danger700
    AegisBadgeTone.Info -> AegisColors.info700
}

/**
 * AegisBadge — small count or label badge.
 *
 *   AegisBadge(count = 12)
 *   AegisBadge(label = "NEW", tone = AegisBadgeTone.Brand)
 */
@Composable
fun AegisBadge(
    modifier: Modifier = Modifier,
    count: Int? = null,
    label: String? = null,
    tone: AegisBadgeTone = AegisBadgeTone.Neutral,
    semanticLabel: String? = null,
) {
    val text = when {
        label != null -> label
        count != null -> if (count > 99) "99+" else count.toString()
        else -> ""
    }
    Text(
        text = text,
        textAlign = TextAlign.Center,
        style = AegisTypography.micro.copy(color = fgFor(tone)),
        modifier = modifier
            .semantics { contentDescription = semanticLabel ?: text }
            .background(bgFor(tone), AegisRadii.shapePill)
            .defaultMinSize(minWidth = 20.dp, minHeight = 18.dp)
            .padding(PaddingValues(horizontal = 8.dp, vertical = 2.dp)),
    )
}
