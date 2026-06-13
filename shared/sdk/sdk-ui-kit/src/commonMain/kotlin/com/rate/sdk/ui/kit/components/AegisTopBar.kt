package com.rate.sdk.ui.kit.components

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.NavigateNext
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/** A single segment of a breadcrumb trail. */
data class AegisCrumb(val label: String, val onClick: (() -> Unit)? = null)

/**
 * AegisTopBar — page-level top frame.
 *
 *   AegisTopBar(
 *       breadcrumbs = listOf(AegisCrumb("Plans"), AegisCrumb("Flagship Premier")),
 *       user = AegisUser("Vivek", "VS", "Business"),
 *       onSearchClick = {},
 *   )
 *
 * Slots:
 *   • breadcrumbs — list of crumbs (clickable if `onClick` provided).
 *   • search — placeholder search affordance (wire it to the command palette).
 *   • actions — composable for right-aligned page actions (e.g. "Publish", "Save").
 *   • user — avatar + menu trigger.
 */
@Composable
fun AegisTopBar(
    modifier: Modifier = Modifier,
    breadcrumbs: List<AegisCrumb> = emptyList(),
    user: AegisUser? = null,
    onUserClick: (() -> Unit)? = null,
    onSearchClick: (() -> Unit)? = null,
    actions: (@Composable () -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .height(AegisSpacing.topBarHeight)
            .background(AegisColors.surface)
            .padding(horizontal = AegisSpacing.s4),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Row(
            Modifier.weight(1f),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            breadcrumbs.forEachIndexed { idx, crumb ->
                val isLast = idx == breadcrumbs.lastIndex
                val color = if (isLast) AegisColors.textPrimary else AegisColors.textSecondary
                val style = AegisTypography.body.copy(
                    color = color,
                    fontWeight = if (isLast) FontWeight.SemiBold else FontWeight.Normal,
                )
                val mod = if (crumb.onClick != null && !isLast) {
                    Modifier.clickable(role = Role.Button) { crumb.onClick.invoke() }
                } else Modifier
                Text(crumb.label, style = style, modifier = mod)
                if (!isLast) {
                    Icon(
                        Icons.Filled.NavigateNext,
                        contentDescription = null,
                        tint = AegisColors.iconMuted,
                        modifier = Modifier.size(16.dp).padding(horizontal = 2.dp),
                    )
                }
            }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(AegisSpacing.s3),
        ) {
            if (onSearchClick != null) {
                Row(
                    Modifier
                        .background(AegisColors.slate2, AegisRadii.shapeMd)
                        .clickable(onClick = onSearchClick, role = Role.Button)
                        .semantics { contentDescription = "Open search and command palette" }
                        .padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(
                        Icons.Filled.Search,
                        contentDescription = null,
                        tint = AegisColors.iconMuted,
                        modifier = Modifier.size(14.dp),
                    )
                    Text(
                        "Search…",
                        style = AegisTypography.small.copy(color = AegisColors.textTertiary),
                    )
                    AegisKbd("⌘K")
                }
            }
            if (actions != null) actions()
            if (user != null) {
                AvatarMenu(user = user, onClick = onUserClick)
            }
        }
    }
}

@Composable
private fun AvatarMenu(user: AegisUser, onClick: (() -> Unit)?) {
    Box(
        modifier = Modifier
            .size(32.dp)
            .background(AegisColors.indigo100, CircleShape)
            .let { mod ->
                if (onClick != null) mod.clickable(role = Role.Button, onClick = onClick) else mod
            }
            .semantics { contentDescription = "User menu for ${user.displayName}" },
        contentAlignment = Alignment.Center,
    ) {
        Text(
            user.initials.uppercase().take(2),
            style = AegisTypography.caption.copy(color = AegisColors.indigo700),
        )
    }
}
