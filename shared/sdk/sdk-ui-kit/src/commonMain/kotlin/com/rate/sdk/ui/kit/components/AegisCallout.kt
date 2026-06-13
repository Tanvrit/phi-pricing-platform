package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * AegisCallout — a banner-style attention block (Pass 6 §6.2).
 *
 * Tinted background + matched border + leading icon. Used for "this is intentionally
 * a placeholder", inline form errors that are too long for a helper, and big
 * announcement banners ("Rate import succeeded — 12 plans now live").
 */
enum class CalloutKind { INFO, SUCCESS, WARN, DANGER }

private data class CalloutPalette(
    val bg: Color,
    val border: Color,
    val icon: Color,
    val title: Color,
    val body: Color,
    val vector: ImageVector,
)

@Composable
private fun paletteFor(kind: CalloutKind): CalloutPalette = when (kind) {
    CalloutKind.INFO -> CalloutPalette(
        AegisColors.info50, AegisColors.info100, AegisColors.info500,
        AegisColors.info700, AegisColors.slate10, Icons.Filled.Info,
    )
    CalloutKind.SUCCESS -> CalloutPalette(
        AegisColors.success50, AegisColors.success100, AegisColors.success500,
        AegisColors.success700, AegisColors.slate10, Icons.Filled.CheckCircle,
    )
    CalloutKind.WARN -> CalloutPalette(
        AegisColors.warn50, AegisColors.warn100, AegisColors.warn500,
        AegisColors.warn700, AegisColors.slate10, Icons.Filled.Warning,
    )
    CalloutKind.DANGER -> CalloutPalette(
        AegisColors.danger50, AegisColors.danger100, AegisColors.danger500,
        AegisColors.danger700, AegisColors.slate10, Icons.Filled.Error,
    )
}

@Composable
fun AegisCallout(
    kind: CalloutKind,
    title: String,
    body: String? = null,
    modifier: Modifier = Modifier,
) {
    val p = paletteFor(kind)
    val shape = RoundedCornerShape(AegisRadii.rMd)
    Row(
        modifier
            .fillMaxWidth()
            .background(p.bg, shape)
            .border(1.dp, p.border, shape)
            .padding(horizontal = AegisSpacing.s4, vertical = AegisSpacing.s3),
        verticalAlignment = Alignment.Top,
    ) {
        Icon(
            imageVector = p.vector,
            contentDescription = null,
            tint = p.icon,
            modifier = Modifier.size(20.dp).padding(top = 1.dp),
        )
        Box(Modifier.width(AegisSpacing.s3))
        Column(Modifier.fillMaxWidth()) {
            Text(
                text = title,
                style = AegisTypography.body.copy(color = p.title, fontWeight = FontWeight.SemiBold),
            )
            if (body != null) {
                Text(
                    text = body,
                    style = AegisTypography.body.copy(color = p.body),
                    modifier = Modifier.padding(top = 2.dp),
                )
            }
        }
    }
}
