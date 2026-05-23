package com.rate.aegis.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Assignment
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Discount
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PieChart
import androidx.compose.material.icons.filled.PlaylistAddCheck
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.UploadFile
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisRadii
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.theme.AegisTypography

/** Top-level surfaces the shell can route to. Pass 6 §6.1. */
enum class AegisSurface(val displayName: String, val icon: ImageVector) {
    HOME("Home", Icons.Filled.Home),
    CALCULATOR("Calculator", Icons.Filled.Calculate),
    PRODUCT_CATALOG("Product catalog", Icons.Filled.ViewModule),
    PLAN_CONFIGURATOR("Plan configurator", Icons.Filled.Tune),
    COVER_CATALOG("Cover catalog", Icons.Filled.Shield),
    RATE_TABLES("Server health", Icons.Filled.PieChart),
    DISCOUNTS("Discounts", Icons.Filled.Discount),
    RULES("Prospectus", Icons.Filled.PlaylistAddCheck),
    IMPORT("Import", Icons.Filled.UploadFile),
    QUOTES("Quotes", Icons.Filled.Description),
    UW_QUEUE("UW queue", Icons.Filled.Assignment),
    REPORTS("Reports", Icons.Filled.Folder),
    AUDIT("Audit", Icons.Filled.History),
    SETTINGS("Settings", Icons.Filled.Settings),
}

data class AegisUser(val displayName: String, val initials: String, val role: String)

/**
 * AegisShell — the top-level frame: left rail nav, top app bar, content body,
 * and toast host overlay. Pass 4 §4.7 / Pass 6 §6.1.
 *
 * The shell is intentionally dumb — it owns *layout*, not navigation state.
 * Caller passes [activeSurface] and reacts to [onSurfaceChange]. This lets the
 * AegisRoot manage routing in one place and the shell stay testable in isolation.
 */
@Composable
fun AegisShell(
    activeSurface: AegisSurface,
    onSurfaceChange: (AegisSurface) -> Unit,
    user: AegisUser,
    modifier: Modifier = Modifier,
    onRefresh: (() -> Unit)? = null,
    unreadCount: Int = 0,
    onBellClick: (() -> Unit)? = null,
    content: @Composable () -> Unit,
) {
    Box(modifier.fillMaxSize().background(AegisColors.canvas)) {
        Row(Modifier.fillMaxSize()) {
            ShellRail(activeSurface = activeSurface, onSurfaceChange = onSurfaceChange)
            AegisVDivider()
            Column(Modifier.weight(1f).fillMaxHeight()) {
                ShellTopBar(
                    activeSurface = activeSurface,
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

@Composable
private fun ShellRail(
    activeSurface: AegisSurface,
    onSurfaceChange: (AegisSurface) -> Unit,
) {
    Column(
        modifier = Modifier
            .width(AegisSpacing.railExpanded)
            .fillMaxHeight()
            .background(AegisColors.surface),
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(AegisSpacing.topBarHeight)
                .padding(horizontal = AegisSpacing.s4),
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
        AegisHDivider()
        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(vertical = AegisSpacing.s2),
        ) {
            AegisSurface.entries.forEach { s ->
                NavItem(
                    surface = s,
                    selected = s == activeSurface,
                    onClick = { onSurfaceChange(s) },
                )
            }
        }
    }
}

@Composable
private fun NavItem(
    surface: AegisSurface,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val bg = if (selected) AegisColors.brandTint else Color.Transparent
    val fg = if (selected) AegisColors.indigo700 else AegisColors.textBody
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = AegisSpacing.s2, vertical = 2.dp)
            .background(bg, AegisRadii.shapeMd)
            .clickable(onClick = onClick)
            .padding(horizontal = AegisSpacing.s3, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = surface.icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        Text(
            text = surface.displayName,
            style = AegisTypography.body.copy(
                color = fg,
                fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Medium,
            ),
        )
    }
}

@Composable
private fun ShellTopBar(
    activeSurface: AegisSurface,
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
            text = activeSurface.displayName,
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
