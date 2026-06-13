package com.rate.sdk.ui.kit.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.hoverable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsHoveredAsState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MenuOpen
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.role
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.sdk.ui.kit.theme.AegisColors
import com.rate.sdk.ui.kit.theme.AegisMotion
import com.rate.sdk.ui.kit.theme.AegisRadii
import com.rate.sdk.ui.kit.theme.AegisSpacing
import com.rate.sdk.ui.kit.theme.AegisTypography

/**
 * A navigable surface in the Aegis dashboard.
 *
 * @param group optional section label. Items sharing a non-null [group] collapse under one
 *   expandable header in [AegisSideNav]; items with a null [group] render flat at the top
 *   (the operational surfaces). Order of first appearance defines section + item order.
 */
data class AegisNavItem(
    val id: String,
    val label: String,
    val icon: ImageVector,
    val badge: Int? = null,
    val group: String? = null,
)

/**
 * AegisSideNav — collapsible side rail.
 *
 *   AegisSideNav(
 *       items = listOf(home, planConfigurator, …),
 *       activeId = activeId,
 *       onSelect = { activeId = it },
 *       collapsed = collapsed,
 *       onToggleCollapse = { collapsed = !collapsed },
 *   )
 *
 * Width animates between 240dp (expanded) and 64dp (collapsed).
 */
@Composable
fun AegisSideNav(
    items: List<AegisNavItem>,
    activeId: String,
    onSelect: (String) -> Unit,
    modifier: Modifier = Modifier,
    collapsed: Boolean = false,
    onToggleCollapse: (() -> Unit)? = null,
    brand: (@Composable () -> Unit)? = null,
    footer: (@Composable () -> Unit)? = null,
) {
    val width by animateDpAsState(
        targetValue = if (collapsed) AegisSpacing.railCollapsed else AegisSpacing.railExpanded,
        animationSpec = AegisMotion.tweenDefault(),
    )
    Column(
        modifier
            .fillMaxHeight()
            .width(width)
            .background(AegisColors.surface)
            .padding(vertical = AegisSpacing.s3),
    ) {
        // Brand header row + collapse toggle
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = AegisSpacing.s3)
                .height(40.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            if (!collapsed) {
                Box(Modifier.weight(1f)) {
                    if (brand != null) brand() else {
                        Text(
                            "Aegis",
                            style = AegisTypography.h2.copy(color = AegisColors.indigo700),
                        )
                    }
                }
            } else {
                Spacer(Modifier.weight(1f))
            }
            if (onToggleCollapse != null) {
                val icon = if (collapsed) Icons.Filled.Menu else Icons.Filled.MenuOpen
                val description = if (collapsed) "Expand navigation" else "Collapse navigation"
                Box(
                    Modifier
                        .size(32.dp)
                        .clickable(onClick = onToggleCollapse, role = Role.Button)
                        .semantics { contentDescription = description },
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = AegisColors.iconDefault,
                        modifier = Modifier.size(18.dp),
                    )
                }
            }
        }
        Spacer(Modifier.height(AegisSpacing.s3))

        // Ungrouped items stay flat at the top (operational surfaces); grouped items
        // collapse under expandable section headers, preserving first-seen order.
        val ungrouped = remember(items) { items.filter { it.group == null } }
        val sections = remember(items) {
            items.filter { it.group != null }
                .groupBy { it.group!! }
                .toList() // keeps insertion order of the first item per group
        }
        // Section expand/collapse state — default every section open; an item in the
        // active section is kept visible by forcing that section open.
        val expanded = remember { mutableStateMapOf<String, Boolean>() }
        val activeGroup = items.firstOrNull { it.id == activeId }?.group

        Column(
            verticalArrangement = Arrangement.spacedBy(2.dp),
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            ungrouped.forEach { item ->
                AegisNavRow(
                    item = item,
                    active = item.id == activeId,
                    collapsed = collapsed,
                    onClick = { onSelect(item.id) },
                )
            }
            sections.forEach { (group, groupItems) ->
                val isOpen = expanded[group] ?: true
                val effectiveOpen = isOpen || group == activeGroup
                if (!collapsed) {
                    AegisNavSectionHeader(
                        label = group,
                        expanded = effectiveOpen,
                        onToggle = { expanded[group] = !effectiveOpen },
                    )
                }
                if (effectiveOpen || collapsed) {
                    groupItems.forEach { item ->
                        AegisNavRow(
                            item = item,
                            active = item.id == activeId,
                            collapsed = collapsed,
                            onClick = { onSelect(item.id) },
                        )
                    }
                }
            }
        }
        if (footer != null) {
            AegisHDivider()
            Box(Modifier.fillMaxWidth().padding(AegisSpacing.s3)) { footer() }
        }
    }
}

/**
 * A collapsible section header row in the side nav — a tiny uppercase label + a chevron that
 * flips between expand/collapse. Only rendered in the expanded rail (the collapsed rail flattens
 * every section to its icons).
 */
@Composable
private fun AegisNavSectionHeader(
    label: String,
    expanded: Boolean,
    onToggle: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AegisSpacing.s2)
            .clickable(onClick = onToggle, role = Role.Button)
            .hoverable(interaction)
            .semantics {
                contentDescription = if (expanded) "$label section, expanded" else "$label section, collapsed"
            }
            .padding(PaddingValues(start = 10.dp, end = 10.dp, top = 12.dp, bottom = 4.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            label.uppercase(),
            style = AegisTypography.micro.copy(
                color = AegisColors.textTertiary,
                fontWeight = FontWeight.SemiBold,
                letterSpacing = 0.8.sp,
            ),
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
            contentDescription = null,
            tint = AegisColors.iconMuted,
            modifier = Modifier.size(16.dp),
        )
    }
}

@Composable
private fun AegisNavRow(
    item: AegisNavItem,
    active: Boolean,
    collapsed: Boolean,
    onClick: () -> Unit,
) {
    val interaction = remember { MutableInteractionSource() }
    val hovered by interaction.collectIsHoveredAsState()
    val bg by animateColorAsState(
        targetValue = when {
            active -> AegisColors.indigo50
            hovered -> AegisColors.slate2
            else -> AegisColors.surface
        },
        animationSpec = AegisMotion.tweenFast(),
    )
    val fg = when {
        active -> AegisColors.indigo700
        else -> AegisColors.textPrimary
    }
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = AegisSpacing.s2)
            .background(bg, AegisRadii.shapeMd)
            .hoverable(interaction)
            .clickable(onClick = onClick, role = Role.Button)
            .semantics {
                contentDescription = if (active) "${item.label} (current page)" else item.label
            }
            .padding(PaddingValues(horizontal = 10.dp, vertical = 8.dp)),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Icon(
            imageVector = item.icon,
            contentDescription = null,
            tint = fg,
            modifier = Modifier.size(18.dp),
        )
        if (!collapsed) {
            Text(
                item.label,
                style = AegisTypography.body.copy(
                    color = fg,
                    fontWeight = if (active) FontWeight.SemiBold else FontWeight.Normal,
                ),
                modifier = Modifier.weight(1f),
            )
            if (item.badge != null) {
                AegisBadge(count = item.badge, tone = if (active) AegisBadgeTone.Brand else AegisBadgeTone.Neutral)
            }
        }
    }
}
