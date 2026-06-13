package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.PlainTooltip
import androidx.compose.material3.Text
import androidx.compose.material3.TooltipBox
import androidx.compose.material3.TooltipDefaults
import androidx.compose.material3.rememberTooltipState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp

/**
 * AegisSparkline — minimal Canvas-based trend line for KPI tiles.
 *
 * No axes, dots, or per-point hover — a pure visual hint showing the shape of the
 * last-N-buckets trend so an operator can scan the KPI strip and see at a glance
 * whether things are climbing, sliding, or flat.
 *
 * When `labels` is provided (and matches `values.size`), a Material3 tooltip
 * shows the count of points + the latest label on hover/long-press. A mismatched
 * `labels` list falls back to stringifying the last value.
 *
 * Empty / single-value series fall back to a flat horizontal mid-line so the tile
 * still shows something rather than a blank gap.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AegisSparkline(
    values: List<Double>,
    accent: Color,
    modifier: Modifier = Modifier.fillMaxWidth().height(28.dp),
    labels: List<String>? = null,
) {
    val tooltipText = remember(values, labels) {
        if (values.isEmpty()) {
            "no data"
        } else {
            val safeLabels = labels?.takeIf { it.size == values.size }
            val latest = safeLabels?.lastOrNull() ?: values.last().toString()
            val n = values.size
            "$n points · latest $latest"
        }
    }
    TooltipBox(
        positionProvider = TooltipDefaults.rememberPlainTooltipPositionProvider(),
        tooltip = { PlainTooltip { Text(tooltipText) } },
        state = rememberTooltipState(isPersistent = false),
    ) {
        Canvas(modifier) {
            val w = size.width
            val h = size.height
            if (w <= 0f || h <= 0f) return@Canvas

            val strokePx = 1.5.dp.toPx()
            // Inset by half-stroke so the line doesn't clip at the top/bottom edges.
            val pad = strokePx / 2f
            val plotH = (h - pad * 2f).coerceAtLeast(0f)

            if (values.size < 2) {
                // Flat line at the vertical centre — readable empty state.
                val midY = h / 2f
                drawLine(
                    color = accent,
                    start = Offset(0f, midY),
                    end = Offset(w, midY),
                    strokeWidth = strokePx,
                )
                return@Canvas
            }

            val minV = values.min()
            val maxV = values.max()
            val range = (maxV - minV).takeIf { it > 0.0 } ?: 1.0
            val stepX = w / (values.size - 1).toFloat()

            val path = Path()
            values.forEachIndexed { i, v ->
                val x = i * stepX
                val norm = ((v - minV) / range).toFloat().coerceIn(0f, 1f)
                // Invert Y so larger values rise upward on screen.
                val y = pad + (1f - norm) * plotH
                if (i == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(path = path, color = accent, style = Stroke(width = strokePx))
        }
    }
}
