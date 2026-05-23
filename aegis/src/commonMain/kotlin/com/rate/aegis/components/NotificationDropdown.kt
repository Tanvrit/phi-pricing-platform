package com.rate.aegis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.Notification
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.theme.AegisTypography

/**
 * NotificationDropdown — an overlay panel anchored visually under the top-bar
 * bell that lists the latest recorded audit events.
 *
 * We render this as a custom modal-style overlay (full-screen invisible
 * backdrop + positioned card) rather than a Material3 [androidx.compose.material3.DropdownMenu]
 * because:
 *   - The dropdown state lives in the parent shell (OperatorShell), not in the
 *     top bar — wiring a Material3 DropdownMenu would need either an anchor
 *     callback up through AegisShell or threading the state down into the top
 *     bar. Both leak shell internals.
 *   - The codebase already established this pattern for [AegisCommandPalette]
 *     (backdrop dismiss + absorb-click card). Re-using it keeps the visual
 *     vocabulary consistent.
 *
 * Visual:
 *   - 360dp wide, max 480dp tall (scrolls beyond).
 *   - Positioned ~12dp from the top, offset to the right side of the canvas.
 *     We approximate the bell's screen-x with right-end alignment + a margin
 *     that roughly matches the avatar block — the shell guarantees the bell
 *     sits to the right of the search box but left of the avatar.
 *
 * Each row mirrors ActivityFeed's compact layout (accent dot · action mono
 * · resource summary · actor · timestamp) but adds a "jump" right-side
 * affordance that calls [onJumpToEvent] for the parent to route via deep link.
 */
@Composable
fun NotificationDropdown(
    open: Boolean,
    notifications: List<Notification>,
    onDismiss: () -> Unit,
    onJumpToEvent: (Notification) -> Unit,
    onViewAll: () -> Unit = {},
    maxVisible: Int = 10,
) {
    if (!open) return

    val backdropClick = remember { MutableInteractionSource() }
    val absorbClick = remember { MutableInteractionSource() }

    Box(
        Modifier
            .fillMaxSize()
            .clickable(
                interactionSource = backdropClick,
                indication = null,
                onClick = onDismiss,
            ),
        contentAlignment = Alignment.TopEnd,
    ) {
        Column(
            Modifier
                // Approx vertical clearance under the top bar; matches topBarHeight.
                .padding(top = AegisSpacing.topBarHeight + 4.dp, end = AegisSpacing.s5)
                .width(360.dp)
                .heightIn(max = 480.dp)
                .shadow(16.dp, AegisRadii.shapeLg, clip = false)
                .background(AegisColors.surface, AegisRadii.shapeLg)
                .border(1.dp, AegisColors.border, AegisRadii.shapeLg)
                .clickable(
                    interactionSource = absorbClick,
                    indication = null,
                    onClick = { /* absorb */ },
                ),
        ) {
            // Header
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = AegisSpacing.s4, vertical = AegisSpacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    "Notifications",
                    style = AegisTypography.h3.copy(
                        color = AegisColors.textPrimary,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
                Box(Modifier.weight(1f))
                if (notifications.isNotEmpty()) {
                    AegisBadge(
                        count = notifications.size,
                        tone = AegisBadgeTone.Brand,
                        semanticLabel = "${notifications.size} recent events",
                    )
                }
            }
            AegisHDivider()

            // Body
            if (notifications.isEmpty()) {
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(AegisSpacing.s5),
                    verticalArrangement = Arrangement.spacedBy(AegisSpacing.s2),
                ) {
                    Text(
                        "No recent events.",
                        style = AegisTypography.body.copy(color = AegisColors.textSecondary),
                    )
                    Text(
                        "Server-recorded audit rows since this session started appear here.",
                        style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                    )
                }
            } else {
                LazyColumn(
                    Modifier
                        .fillMaxWidth()
                        .heightIn(max = 400.dp),
                ) {
                    val visible = notifications.take(maxVisible)
                    items(visible, key = { it.id }) { n ->
                        NotificationRow(n, onClick = { onJumpToEvent(n) })
                        AegisHDivider()
                    }
                }
            }

            // Footer
            Row(
                Modifier
                    .fillMaxWidth()
                    .clickable(onClick = {
                        onDismiss()
                        onViewAll()
                    })
                    .padding(horizontal = AegisSpacing.s4, vertical = AegisSpacing.s3),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Box(Modifier.weight(1f))
                Text(
                    "View full audit log →",
                    style = AegisTypography.small.copy(
                        color = AegisColors.brand,
                        fontWeight = FontWeight.Medium,
                    ),
                )
            }
        }
    }
}

@Composable
private fun NotificationRow(n: Notification, onClick: () -> Unit) {
    val resourceSummary =
        if (n.resourceId.isNullOrBlank()) n.resourceType
        else "${n.resourceType} ${n.resourceId}"
    val accent = accentFor(n.action)
    Column(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = AegisSpacing.s4, vertical = AegisSpacing.s3),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(6.dp).background(accent, CircleShape))
            Spacer(Modifier.width(AegisSpacing.s3))
            Text(
                sliceClock(n.timestamp),
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
                color = AegisColors.textTertiary,
            )
            Spacer(Modifier.width(AegisSpacing.s3))
            Text(
                n.action,
                fontSize = 13.sp,
                fontFamily = FontFamily.Monospace,
                fontWeight = FontWeight.Medium,
                color = AegisColors.textBody,
                modifier = Modifier.weight(1f),
            )
        }
        Spacer(Modifier.size(2.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            // Indent under the dot+gap to visually anchor the secondary line.
            Spacer(Modifier.width(6.dp + AegisSpacing.s3))
            Text(
                resourceSummary,
                fontSize = 13.sp,
                color = AegisColors.textBody,
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(AegisSpacing.s2))
            Text(
                "by ${n.actor ?: "unknown"}",
                fontSize = 12.sp,
                color = AegisColors.textSecondary,
            )
        }
    }
}

/**
 * Same accent mapping as ActivityFeed — copied (not depended on) per the brief
 * to keep the dropdown self-contained. Marked @Composable because the
 * [AegisColors] palette is theme-aware (light/dark) and exposes its tokens
 * through composition locals.
 */
@Composable
private fun accentFor(action: String): Color {
    val a = action.lowercase()
    return when {
        a.contains("delete") || a.contains("retired") || a.contains("reject") -> AegisColors.danger500
        a.contains("import") || a.contains("upsert") || a.contains("created") || a.contains("create") -> AegisColors.success500
        a.contains("update") || a.contains("change") || a.contains("edit") -> AegisColors.info500
        a.contains("quote") -> AegisColors.premium500
        else -> AegisColors.brand
    }
}

/**
 * Server stamps `eventAt` as ISO-8601 (`2026-05-22T10:42:11Z`). Slice the HH:MM
 * portion for the dropdown — the date is implicit ("today / this session")
 * and a full timestamp would crowd the 360dp panel.
 */
private fun sliceClock(iso: String): String {
    if (iso.length < 16) return "—"
    val t = iso.indexOf('T')
    return if (t in 0..(iso.length - 6)) iso.substring(t + 1, t + 6) else "—"
}
