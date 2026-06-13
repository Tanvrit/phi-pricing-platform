package com.rate.sdk.ui.buyonline.screens.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.sdk.ui.kit.brand.PruBackground
import com.rate.sdk.ui.kit.brand.PruRed
import com.rate.sdk.ui.kit.brand.PruText

/**
 * "Session expires soon" sticky banner for the buyonline journey.
 *
 * Server-side save+resume rows have a documented 30-day TTL (not yet enforced
 * by a cleanup task — see Phase-2 backlog). Once a saved session crosses the
 * 25-day mark, we surface this callout above the active screen body so the
 * customer either submits or knowingly accepts a restart.
 *
 * Two visual treatments share the same chrome:
 *   - WARN (≤5 days left, >2): PruRed border, calm amber icon tint
 *   - DANGER (≤2 days left)  : PruRedDark border + red icon + bolder body
 *
 * Visual lineage is the existing WelcomeBackCallout — PRUHealth chrome on
 * PruBackground rather than Aegis callout chrome, since this is the customer
 * journey, not the operator console.
 */
@Composable
fun SessionExpiresCallout(
    ageDays: Long,
    daysLeft: Long,
    modifier: Modifier = Modifier
) {
    val danger = daysLeft <= 2
    val accent: Color = if (danger) Color(0xFFB0122A) else PruRed
    // WARN gets an amber icon to read as "heads up" rather than "alarm"; DANGER
    // promotes to PruRed so it matches the border and reads as urgent.
    val iconTint: Color = if (danger) PruRed else Color(0xFFE08600)
    val shape = RoundedCornerShape(8.dp)

    Box(
        modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 6.dp)
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .background(PruBackground, shape)
                .border(if (danger) 2.dp else 1.dp, accent, shape)
                .padding(horizontal = 12.dp, vertical = 10.dp),
            verticalAlignment = Alignment.Top
        ) {
            Icon(
                imageVector = Icons.Filled.Warning,
                contentDescription = null,
                tint = iconTint,
                modifier = Modifier.size(18.dp)
            )
            Spacer(Modifier.width(8.dp))
            Column(Modifier.fillMaxWidth()) {
                Text(
                    text = "Session expires soon",
                    color = accent,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold
                )
                Text(
                    text = "Saved $ageDays days ago. Auto-expires in $daysLeft days. " +
                            "Complete your application or it'll need to restart.",
                    color = PruText,
                    fontSize = 12.sp,
                    fontWeight = if (danger) FontWeight.Medium else FontWeight.Normal,
                    modifier = Modifier.padding(top = 2.dp)
                )
            }
        }
    }
}
