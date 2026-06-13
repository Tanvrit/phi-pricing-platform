package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * Signed-in operator identity rendered in the shell top bar + avatar menu.
 */
data class AegisUser(val displayName: String, val initials: String, val role: String)

/**
 * AegisShell — the top-level operator frame: left rail nav, top app bar, content
 * body, and toast host overlay. Pass 4 §4.7 / Pass 6 §6.1.
 *
 * The shell is intentionally dumb — it owns *layout*, not navigation state. The
 * host passes the nav [items] (generic [AegisNavItem]s — the kit has no opinion
 * about which surfaces an app defines), the [activeId], the page [title], and
 * reacts to [onSelect]. This lets the operator app manage routing in one place
 * and the shell stay testable in isolation. Built on [AegisSideNav] +
 * [AegisToastHost] so the design language stays consistent.
 */
@Composable
fun AegisShell(
    items: List<AegisNavItem>,
    activeId: String,
    onSelect: (String) -> Unit,
    title: String,
    user: AegisUser,
    modifier: Modifier = Modifier,
    brand: (@Composable () -> Unit)? = { ShellBrand() },
    onRefresh: (() -> Unit)? = null,
    unreadCount: Int = 0,
    onBellClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().background(AegisColors.canvas)) {
        Row(Modifier.fillMaxSize()) {
            AegisSideNav(
                items = items,
                activeId = activeId,
                onSelect = onSelect,
                brand = brand,
            )
            AegisVDivider()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ShellTopBar(
                    title = title,
                    user = user,
                    onRefresh = onRefresh,
                    unreadCount = unreadCount,
                    onBellClick = onBellClick,
                )
                AegisHDivider()
                Box(
                    Modifier
                        .weight(1f)
                        .fillMaxWidth()
                        .background(AegisColors.canvas),
                ) { content() }
            }
        }
        AegisToastHost()
    }
}

/** Default brand block for the rail header: shield mark + product name. */
@Composable
fun ShellBrand() {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        Box(
            Modifier
                .size(28.dp)
                .background(AegisColors.indigo500, AegisRadii.shapeMd),
            contentAlignment = Alignment.Center,
        ) {
            Icon(
                imageVector = Icons.Filled.Shield,
                contentDescription = null,
                tint = Color.White,
                modifier = Modifier.size(16.dp),
            )
        }
        Column {
            Text(
                "Aegis",
                style = AegisTypography.h3.copy(
                    color = AegisColors.textPrimary,
                    fontWeight = FontWeight.SemiBold,
                ),
            )
            Text(
                "Insurance Workbench",
                style = AegisTypography.small.copy(color = AegisColors.textSecondary),
            )
        }
    }
}

@Composable
private fun ShellTopBar(
    title: String,
    user: AegisUser,
    onRefresh: (() -> Unit)? = null,
    unreadCount: Int = 0,
    onBellClick: (() -> Unit)? = null,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(AegisSpacing.topBarHeight)
            .background(AegisColors.surface)
            .padding(horizontal = AegisSpacing.s5),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = AegisTypography.h2.copy(color = AegisColors.textPrimary),
        )
        Box(Modifier.weight(1f))
        Row(
            modifier = Modifier
                .background(AegisColors.slate2, AegisRadii.shapeMd)
                .border(1.dp, AegisColors.border, AegisRadii.shapeMd)
                .padding(horizontal = 10.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Search,
                contentDescription = null,
                tint = AegisColors.iconMuted,
                modifier = Modifier.size(14.dp),
            )
            Text(
                "Search Aegis…",
                style = AegisTypography.small.copy(color = AegisColors.textTertiary),
            )
            Box(Modifier.width(4.dp))
            AegisKbd("⌘K")
        }
        if (onBellClick != null) {
            Box(Modifier.width(AegisSpacing.s2))
            // Bell + badge overlay. We use a Box stack rather than positioning
            // the badge inside the IconButton so the badge can spill outside
            // the 32dp hit-target without clipping.
            Box(contentAlignment = Alignment.Center) {
                IconButton(onClick = onBellClick, modifier = Modifier.size(32.dp)) {
                    Icon(
                        imageVector = Icons.Filled.Notifications,
                        contentDescription = if (unreadCount > 0)
                            "Notifications, $unreadCount recent events"
                        else "Notifications",
                        tint = AegisColors.iconMuted,
                        modifier = Modifier.size(18.dp),
                    )
                }
                if (unreadCount > 0) {
                    Box(
                        Modifier
                            .align(Alignment.TopEnd)
                            .padding(top = 2.dp, end = 2.dp),
                    ) {
                        AegisBadge(
                            count = unreadCount,
                            tone = AegisBadgeTone.Brand,
                            semanticLabel = "$unreadCount recent events",
                        )
                    }
                }
            }
        }
        if (onRefresh != null) {
            Box(Modifier.width(AegisSpacing.s2))
            IconButton(onClick = onRefresh, modifier = Modifier.size(32.dp)) {
                Icon(
                    imageVector = Icons.Filled.Refresh,
                    contentDescription = "Refresh all data",
                    tint = AegisColors.iconMuted,
                    modifier = Modifier.size(18.dp),
                )
            }
        }
        Box(Modifier.width(AegisSpacing.s4))
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Box(
                modifier = Modifier
                    .size(32.dp)
                    .background(AegisColors.indigo100, CircleShape),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    user.initials,
                    style = AegisTypography.small.copy(
                        color = AegisColors.indigo700,
                        fontWeight = FontWeight.SemiBold,
                    ),
                )
            }
            Column(horizontalAlignment = Alignment.Start) {
                Text(
                    user.displayName,
                    style = AegisTypography.body.copy(
                        color = AegisColors.textPrimary,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    user.role,
                    style = AegisTypography.small.copy(color = AegisColors.textSecondary),
                )
            }
        }
    }
}
