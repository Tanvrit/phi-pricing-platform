package com.rate.aegis.customer.buyonline.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.HelpOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
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
import com.rate.aegis.customer.buyonline.ui.theme.PruRed
import com.rate.aegis.customer.buyonline.ui.theme.PruSubtext
import com.rate.aegis.customer.buyonline.ui.theme.PruText

/**
 * Floating circular "Need help?" affordance overlaid on the buyonline journey.
 *
 * Sits in the bottom-right corner (anchored via the caller's `Modifier.align`
 * inside an outer Box) and opens a small placeholder support dialog with the
 * three usual contact channels — phone, email, and a disabled chat row.
 *
 * The contact details are intentionally placeholders. This iteration just
 * adds the affordance; a follow-up wires real numbers/routes once support
 * confirms the live channels.
 *
 * The composable owns its own open/closed state — callers only place it; no
 * lifting needed because no other surface needs to programmatically open it.
 */
@Composable
fun FloatingHelpButton(modifier: Modifier = Modifier) {
    var open by remember { mutableStateOf(false) }

    Box(
        modifier
            .size(56.dp)
            .background(PruRed, CircleShape)
            .clickable { open = true },
        contentAlignment = Alignment.Center
    ) {
        Icon(
            imageVector = Icons.Filled.HelpOutline,
            contentDescription = "Need help?",
            tint = Color.White,
            modifier = Modifier.size(28.dp)
        )
    }

    if (open) {
        HelpDialog(onDismiss = { open = false })
    }
}

/**
 * Modal placeholder for the three customer-support contact channels. Phone
 * and email are static strings (no `tel:` / `mailto:` deep-link wiring yet —
 * that's a separate iteration once we confirm the production support routes).
 * Chat is intentionally disabled with an inline "Coming soon" hint so the
 * customer knows the channel exists but isn't yet live.
 */
@Composable
private fun HelpDialog(onDismiss: () -> Unit) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Need help?", fontWeight = FontWeight.SemiBold) },
        text = {
            Column {
                ContactRow(
                    label = "Phone",
                    value = "1800-XXX-XXXX",
                    hint = "Mon-Sat, 9 AM - 9 PM"
                )
                Spacer(Modifier.height(12.dp))
                ContactRow(
                    label = "Email",
                    value = "support@pruhealth.example.com",
                    hint = "We reply within 1 business day"
                )
                Spacer(Modifier.height(12.dp))
                // Chat row — same visual shape as the others but with a
                // disabled button stub and an inline rationale. We render
                // it inside the dialog (rather than hiding) so the
                // customer can see the channel is planned.
                ContactRow(
                    label = "Chat",
                    value = "Live chat",
                    hint = "Coming soon — try phone or email"
                )
                Spacer(Modifier.height(8.dp))
                Button(
                    onClick = {},
                    enabled = false,
                    colors = ButtonDefaults.buttonColors(containerColor = PruRed)
                ) {
                    Text("Start chat")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close", color = PruRed, fontWeight = FontWeight.SemiBold)
            }
        }
    )
}

@Composable
private fun ContactRow(label: String, value: String, hint: String) {
    Column(Modifier.fillMaxWidth()) {
        Text(
            text = label,
            color = PruSubtext,
            fontSize = 11.sp,
            fontWeight = FontWeight.SemiBold
        )
        Text(
            text = value,
            color = PruText,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium
        )
        Text(
            text = hint,
            color = PruSubtext,
            fontSize = 11.sp
        )
    }
}
