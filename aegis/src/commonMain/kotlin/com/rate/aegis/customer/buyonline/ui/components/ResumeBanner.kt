package com.rate.aegis.customer.buyonline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.customer.buyonline.copyToClipboard
import com.rate.aegis.customer.buyonline.resumeUrl
import com.rate.aegis.customer.buyonline.ui.theme.PruRed
import com.rate.aegis.customer.buyonline.ui.theme.PruSubtext
import com.rate.aegis.customer.buyonline.ui.theme.PruText
import kotlinx.coroutines.delay

/**
 * Slim "Resume later" affordance that sits above the active screen body for
 * every buyonline step beyond the landing page. Surfaces the session id in
 * compact form (`?session=<first 8>…`) and exposes a one-click copy that
 * writes the full URL to the host clipboard.
 *
 * Renders nothing when [sessionId] is blank — the VM only mints an id after
 * the first persistable interaction, and we don't want to show a half-baked
 * URL on the landing screen.
 */
@Composable
fun ResumeBanner(sessionId: String, modifier: Modifier = Modifier) {
    if (sessionId.isBlank()) return

    var copied by remember { mutableStateOf(false) }

    // Auto-revert the "Copied" confirmation after a few seconds so the banner
    // returns to its default neutral state. Keyed on `copied` so each new
    // copy click restarts the timer (the customer might re-copy after the
    // session id changes — unlikely, but cheap to handle).
    LaunchedEffect(copied) {
        if (copied) {
            delay(3500)
            copied = false
        }
    }

    val short = sessionId.take(8)
    val bodyText =
        if (copied) "Link copied. Reopen this page anytime in the next 30 days."
        else        "Resume later · ?session=$short…"

    Surface(
        modifier = modifier.fillMaxWidth(),
        color = Color(0xFFFFF4F5),                          // pale wash of PruRed
        shape = RoundedCornerShape(0.dp),
        shadowElevation = 0.dp
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(Color(0xFFFFF4F5))
                .padding(horizontal = 16.dp, vertical = 6.dp)
                .height(32.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Start
        ) {
            Icon(
                Icons.Filled.Link,
                contentDescription = null,
                tint = PruRed,
                modifier = Modifier.height(16.dp)
            )
            Spacer(Modifier.width(8.dp))
            Text(
                bodyText,
                fontSize = 12.sp,
                color = if (copied) PruText else PruSubtext,
                fontWeight = if (copied) FontWeight.Medium else FontWeight.Normal,
                modifier = Modifier.weight(1f)
            )
            Spacer(Modifier.width(8.dp))
            TextButton(
                onClick = {
                    val url = resumeUrl(sessionId)
                    val ok = copyToClipboard(url)
                    // Even when the platform returns false (e.g. headless JDK
                    // or browser denied permission), we leave `copied` alone
                    // — the customer can still hand-copy the visible short
                    // form, no need to flash a misleading confirmation.
                    if (ok) copied = true
                },
                colors = ButtonDefaults.textButtonColors(contentColor = PruRed),
                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                    horizontal = 8.dp, vertical = 0.dp
                )
            ) {
                Text(
                    if (copied) "Copied ✓" else "Copy",
                    fontSize = 12.sp,
                    fontWeight = FontWeight.SemiBold
                )
            }
        }
    }
}
