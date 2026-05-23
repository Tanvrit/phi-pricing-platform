package com.rate.aegis.customer.buyonline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Link
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.customer.buyonline.api.BuyOnlineApiClient
import com.rate.aegis.util.copyToClipboard
import com.rate.aegis.customer.buyonline.resumeUrl
import com.rate.aegis.customer.buyonline.ui.theme.PruRed
import com.rate.aegis.customer.buyonline.ui.theme.PruSubtext
import com.rate.aegis.customer.buyonline.ui.theme.PruSuccess
import com.rate.aegis.customer.buyonline.ui.theme.PruText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Slim "Resume later" affordance that sits above the active screen body for
 * every buyonline step beyond the landing page. Surfaces the session id in
 * compact form (`?session=<first 8>…`) and exposes a one-click copy that
 * writes the full URL to the host clipboard.
 *
 * Renders nothing when [sessionId] is blank — the VM only mints an id after
 * the first persistable interaction, and we don't want to show a half-baked
 * URL on the landing screen.
 *
 * In addition to the "Copy" button, an "Email me" button opens a small
 * dialog that lets the customer mail the resume link to themselves. The
 * dialog hits the (Phase-1 mock) `POST /api/buy-online/session/email`
 * endpoint — no real send happens yet, the server only audit-records.
 */
@Composable
fun ResumeBanner(sessionId: String, modifier: Modifier = Modifier) {
    if (sessionId.isBlank()) return

    var copied by remember { mutableStateOf(false) }
    var emailDialogOpen by remember { mutableStateOf(false) }
    // Inline transient confirmation surfaced under the banner — this codebase
    // doesn't wire a SnackbarHost into the buyonline shell, so the "toast"
    // requested by the spec is implemented as a 3.5s inline message that
    // matches the existing "Copied ✓" pattern below.
    var toastMessage by remember { mutableStateOf<String?>(null) }

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
    LaunchedEffect(toastMessage) {
        if (toastMessage != null) {
            delay(4000)
            toastMessage = null
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
        Column(Modifier.fillMaxWidth()) {
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
                    onClick = { emailDialogOpen = true },
                    colors = ButtonDefaults.textButtonColors(contentColor = PruSubtext),
                    contentPadding = androidx.compose.foundation.layout.PaddingValues(
                        horizontal = 8.dp, vertical = 0.dp
                    )
                ) {
                    Text(
                        "Email me",
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium
                    )
                }
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
            // Inline toast — only rendered while a message is pending. Sits
            // flush under the banner so the layout doesn't jitter on screens
            // that draw immediately after.
            toastMessage?.let { msg ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .background(Color(0xFFFFF4F5))
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        msg,
                        fontSize = 11.sp,
                        color = PruSuccess,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    EmailResumeDialog(
        open = emailDialogOpen,
        resumeUrl = resumeUrl(sessionId),
        onDismiss = { emailDialogOpen = false },
        onResult = { message ->
            emailDialogOpen = false
            toastMessage = message
        }
    )
}

/**
 * Tiny "mail me this link" dialog. Material3 [AlertDialog] is reused here
 * rather than rolling a custom card overlay — every other modal in the
 * buyonline flow (terms gate, critical-illness modal, add-ons confirm)
 * already uses [AlertDialog], so this stays consistent and free.
 *
 * Email validation is intentionally only "contains @" per spec — the
 * iteration after this hardens it.
 */
@Composable
private fun EmailResumeDialog(
    open: Boolean,
    resumeUrl: String,
    onDismiss: () -> Unit,
    onResult: (String) -> Unit = {}
) {
    if (!open) return

    var email by remember { mutableStateOf("") }
    var sending by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    // Local client instance keeps the dialog self-contained. ResumeBanner is
    // rendered in many places (every step beyond landing) so threading a
    // shared client down through every call site is more disruption than
    // this micro-feature warrants. The Ktor engine is cheap to spin up.
    val client = remember { BuyOnlineApiClient() }

    val canSend = "@" in email && !sending

    AlertDialog(
        onDismissRequest = { if (!sending) onDismiss() },
        title = { Text("Email me the resume link") },
        text = {
            Column {
                Text(
                    "We'll mail this link to your inbox so you can pick up where you left off.",
                    fontSize = 13.sp,
                    color = PruSubtext
                )
                Spacer(Modifier.height(12.dp))
                OutlinedTextField(
                    value = email,
                    onValueChange = { email = it },
                    label = { Text("Your email address") },
                    singleLine = true,
                    enabled = !sending,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (!canSend) return@Button
                    sending = true
                    scope.launch {
                        val ok = client.emailResumeLink(email = email, url = resumeUrl)
                        sending = false
                        onResult(
                            if (ok) "Resume link sent to $email. Check your inbox."
                            else    "Couldn't send. Try copying the link instead."
                        )
                    }
                },
                enabled = canSend,
                colors = ButtonDefaults.buttonColors(containerColor = PruRed)
            ) {
                Text(if (sending) "Sending…" else "Send")
            }
        },
        dismissButton = {
            TextButton(
                onClick = onDismiss,
                enabled = !sending
            ) { Text("Cancel", color = PruSubtext) }
        }
    )
}
