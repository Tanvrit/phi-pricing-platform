package com.rate.aegis.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.unit.dp
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisElevation
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.theme.AegisTypography
import androidx.compose.ui.draw.shadow
import kotlinx.coroutines.delay

/**
 * In-process toast bus. There is one queue per process; the host slot
 * [AegisToastHost] consumes it. Call [showToast] from anywhere — the host
 * shows the most recent four entries stacked.
 *
 * Trade-off: in a strict multi-window app this would live behind a controller
 * per AegisShell. For the Ship-2/3 surfaces (one shell, one window) the
 * module-private queue is sufficient and avoids a CompositionLocal dance.
 */
private data class Toast(val id: Long, val msg: String, val kind: CalloutKind)

private val toastQueue = mutableStateListOf<Toast>()
private var nextToastId = 0L

fun showToast(msg: String, kind: CalloutKind = CalloutKind.INFO) {
    toastQueue.add(Toast(++nextToastId, msg, kind))
    if (toastQueue.size > 6) toastQueue.removeAt(0)
}

private fun iconFor(kind: CalloutKind): ImageVector = when (kind) {
    CalloutKind.INFO -> Icons.Filled.Info
    CalloutKind.SUCCESS -> Icons.Filled.CheckCircle
    CalloutKind.WARN -> Icons.Filled.Warning
    CalloutKind.DANGER -> Icons.Filled.Error
}

@Composable
private fun tintFor(kind: CalloutKind): Color = when (kind) {
    CalloutKind.INFO -> AegisColors.info500
    CalloutKind.SUCCESS -> AegisColors.success500
    CalloutKind.WARN -> AegisColors.warn500
    CalloutKind.DANGER -> AegisColors.danger500
}

@Composable
fun AegisToastHost(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize()) {
        Column(
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(AegisSpacing.s5),
            verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
            horizontalAlignment = Alignment.End,
        ) {
            val toasts = toastQueue.takeLast(4)
            toasts.forEach { toast ->
                ToastBubble(toast)
                LaunchedEffect(toast.id) {
                    delay(4500L)
                    toastQueue.removeAll { it.id == toast.id }
                }
            }
        }
    }
}

@Composable
private fun ToastBubble(toast: Toast) {
    var visible by remember { mutableStateOf(false) }
    LaunchedEffect(toast.id) { visible = true }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + slideInVertically(initialOffsetY = { it / 2 }),
        exit = fadeOut() + slideOutVertically(targetOffsetY = { it / 2 }),
    ) {
        val shape = RoundedCornerShape(AegisRadii.rMd)
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .shadow(AegisElevation.popover, shape, clip = false)
                .background(AegisColors.slate11, shape)
                .border(1.dp, AegisColors.slate10, shape)
                .padding(PaddingValues(horizontal = 14.dp, vertical = 10.dp)),
        ) {
            Icon(
                imageVector = iconFor(toast.kind),
                contentDescription = null,
                tint = tintFor(toast.kind),
                modifier = Modifier.size(18.dp),
            )
            Box(Modifier.width(AegisSpacing.s3))
            Text(
                text = toast.msg,
                style = AegisTypography.body.copy(color = Color.White),
            )
        }
    }
}
