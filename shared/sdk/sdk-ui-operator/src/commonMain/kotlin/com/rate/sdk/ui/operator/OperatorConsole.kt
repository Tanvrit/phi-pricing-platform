package com.rate.sdk.ui.operator

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.foundation.focusable
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import com.rate.core.network.client.TanvritClient
import com.rate.sdk.ui.kit.components.AegisCommand
import com.rate.sdk.ui.kit.components.AegisCommandPalette
import com.rate.sdk.ui.kit.components.AegisNavItem
import com.rate.sdk.ui.kit.components.AegisShell
import com.rate.sdk.ui.kit.components.AegisUser
import com.rate.sdk.ui.kit.i18n.AegisLocale
import com.rate.sdk.ui.kit.theme.AegisTheme
import com.rate.sdk.ui.operator.network.AuditReadApi
import com.rate.sdk.ui.operator.network.GroupQuotingApi
import com.rate.sdk.ui.operator.registry.HubGroup
import com.rate.sdk.ui.operator.surface.AddonMatrixSurface
import com.rate.sdk.ui.operator.surface.AuditSurface
import com.rate.sdk.ui.operator.surface.CalculatorSurface
import com.rate.sdk.ui.operator.surface.ConfigHubSurface
import com.rate.sdk.ui.operator.surface.DashboardSurface
import com.rate.sdk.ui.operator.surface.GroupManualQuoteSurface
import com.rate.sdk.ui.operator.surface.GroupQuotingSurface
import com.rate.sdk.ui.operator.surface.ImportSurface
import com.rate.sdk.ui.operator.surface.QuoteExplorerSurface
import com.rate.sdk.ui.operator.surface.SalesDashboardSurface
import com.rate.sdk.ui.operator.surface.SalesPipelineSurface
import com.rate.sdk.ui.operator.screen.ConfigEntityScreen

/**
 * THE operator + admin console — the single composable an app shell mounts to get the whole
 * operator workbench. It builds an [OperatorContext] from the shared [TanvritClient] + [role],
 * wires the [AegisShell] (left rail + top bar + toast host) and routes between the relocated
 * operational surfaces (dashboard, quote explorer, calculator, group quoting, imports, audit), the
 * business HUB card-grids ([ConfigHubSurface]) and the metadata-driven admin config editor for
 * EVERY registered entity.
 *
 * Navigation IA (OWNER/ADMIN):
 *  - Operational surfaces stay flat at the top of the rail (Home dashboard, Quotes, Calculator,
 *    Group, Imports, Audit).
 *  - Every admin entity collapses under its business [HubGroup] section header in the rail; each
 *    group also gets an "Overview" hub card-grid that drills into the per-entity config editor.
 *  - The landing route is the Configuration hub grid (the masters operators reach for first).
 *  - ⌘K opens the [AegisCommandPalette] searching every surface, hub and entity descriptor.
 *
 * Role gating:
 *  - OWNER/ADMIN → every surface + hubs + the full retail/group/identity config editor + ingestion.
 *  - BUSINESS    → operational surfaces only (dashboard, quotes, calculator, group quoting, audit).
 *  - CUSTOMER    → minimal read view (dashboard only); the real journey is sdk-ui-buyonline.
 *
 * @param client the wired shared transport (auth + retry + AppJson).
 * @param role   the operator role driving surface visibility.
 * @param actor  optional operator identity stamped into admin mutations (audit attribution).
 * @param dark   theme toggle; the host owns the preference.
 * @param locale active locale provided to the kit i18n.
 */
@Composable
fun OperatorConsole(
    client: TanvritClient,
    role: OperatorRole,
    actor: String? = null,
    dark: Boolean = false,
    locale: AegisLocale = AegisLocale.EN,
    modifier: Modifier = Modifier,
) {
    val ctx = remember(client, role, actor) { OperatorContext(client, role, actor) }
    OperatorConsole(ctx, dark, locale, modifier)
}

/** Lower-level entry that takes a pre-built [OperatorContext] (used by DI / tests). */
@Composable
fun OperatorConsole(
    ctx: OperatorContext,
    dark: Boolean = false,
    locale: AegisLocale = AegisLocale.EN,
    modifier: Modifier = Modifier,
) {
    val routes = remember(ctx) { buildRoutes(ctx) }
    val landingId = remember(ctx, routes) { landingRouteId(ctx, routes) }
    var activeId by remember(ctx) { mutableStateOf(landingId) }
    // Consumed once: when a hub "Quick add" navigates into a Config route we want the create
    // drawer to auto-open; subsequent visits to the same route should not re-trigger it.
    var pendingCreateEntityId by remember(ctx) { mutableStateOf<String?>(null) }
    var paletteOpen by remember(ctx) { mutableStateOf(false) }

    val active = routes.firstOrNull { it.id == activeId } ?: routes.first()

    val audit = remember(ctx) { AuditReadApi(ctx.client) }
    val group = remember(ctx) { GroupQuotingApi(ctx.client) }

    // ⌘K / Ctrl+K opens the command palette. A focusable wrapper owns the shortcut so it works
    // before any field is focused; requestFocus is guarded for WASM.
    val shortcutFocus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { shortcutFocus.requestFocus() } }

    fun navigateTo(id: String) {
        activeId = id
        pendingCreateEntityId = null
    }

    /** Open an entity's config editor; [create] forces the create drawer open on arrival. */
    fun openEntity(entityId: String, create: Boolean) {
        val routeId = "config:$entityId"
        if (routes.any { it.id == routeId }) {
            activeId = routeId
            pendingCreateEntityId = if (create) entityId else null
        }
    }

    AegisTheme(dark = dark, locale = locale) {
        AegisShell(
            modifier = modifier,
            items = routes.map { AegisNavItem(id = it.id, label = it.label, icon = it.icon, group = groupLabel(it)) },
            activeId = activeId,
            onSelect = { navigateTo(it) },
            title = active.label,
            user = AegisUser(
                displayName = ctx.actor ?: "Operator",
                initials = (ctx.actor ?: "OP").take(2).uppercase(),
                role = ctx.role.name,
            ),
        ) {
            Box(
                Modifier
                    .fillMaxSize()
                    .focusRequester(shortcutFocus)
                    .focusable()
                    .onPreviewKeyEvent { event ->
                        if (event.type == KeyEventType.KeyDown &&
                            event.key == Key.K &&
                            (event.isMetaPressed || event.isCtrlPressed)
                        ) {
                            paletteOpen = true
                            true
                        } else {
                            false
                        }
                    },
            ) {
                when (val route = active) {
                    is OperatorRoute.Surface -> when (route) {
                        OperatorRoute.Surface.HOME -> DashboardSurface(ctx.quotes)
                        OperatorRoute.Surface.QUOTES -> QuoteExplorerSurface(ctx.quotes)
                        OperatorRoute.Surface.CALCULATOR -> CalculatorSurface(ctx.configAdmin, ctx.quotes)
                        OperatorRoute.Surface.SALES_DASHBOARD -> SalesDashboardSurface(ctx.configAdmin)
                        OperatorRoute.Surface.SALES_PIPELINE -> SalesPipelineSurface(ctx.configAdmin, ctx.quotes, ctx.actor)
                        OperatorRoute.Surface.GROUP -> GroupQuotingSurface(group)
                        OperatorRoute.Surface.GROUP_MANUAL -> GroupManualQuoteSurface()
                        OperatorRoute.Surface.ADDON_MATRIX -> AddonMatrixSurface(ctx.configAdmin, ctx.actor)
                        OperatorRoute.Surface.IMPORTS -> ImportSurface(ctx.ingestion)
                        OperatorRoute.Surface.AUDIT -> AuditSurface(audit)
                    }
                    is OperatorRoute.Hub -> {
                        val hub = remember(route.group, ctx) {
                            ctx.registry.hubs.first { it.group == route.group }
                        }
                        ConfigHubSurface(
                            hub = hub,
                            api = ctx.configAdmin,
                            onManage = { entityId -> openEntity(entityId, create = false) },
                            onQuickAdd = { entityId -> openEntity(entityId, create = true) },
                        )
                    }
                    is OperatorRoute.Config -> {
                        val descriptor = ctx.registry.get(route.entityId)
                        if (descriptor != null) {
                            ConfigEntityScreen(
                                descriptor = descriptor,
                                api = ctx.configAdmin,
                                actor = ctx.actor,
                                openInCreate = pendingCreateEntityId == route.entityId,
                            )
                        }
                    }
                }
            }

            AegisCommandPalette(
                open = paletteOpen,
                commands = buildCommands(ctx, routes) { navigateTo(it) },
                onDismiss = { paletteOpen = false },
            )
        }
    }
}

/** The rail section label for a route (null = flat at top; non-null = collapses under a header). */
private fun groupLabel(route: OperatorRoute): String? = when (route) {
    is OperatorRoute.Surface -> null
    is OperatorRoute.Hub -> route.group.title
    is OperatorRoute.Config -> route.group.title
}

/** The route the console lands on. OWNER/ADMIN land on the Configuration hub; others on Home. */
private fun landingRouteId(ctx: OperatorContext, routes: List<OperatorRoute>): String {
    if (ctx.canEditConfig) {
        routes.firstOrNull { it is OperatorRoute.Hub && it.group == HubGroup.CONFIGURATION }
            ?.let { return it.id }
    }
    return routes.first().id
}

/** Assemble the nav routes for the [ctx]'s role — operational surfaces + role-gated hubs/editors. */
private fun buildRoutes(ctx: OperatorContext): List<OperatorRoute> = buildList {
    when (ctx.role) {
        OperatorRole.CUSTOMER -> {
            add(OperatorRoute.Surface.HOME)
        }
        OperatorRole.BUSINESS -> {
            add(OperatorRoute.Surface.HOME)
            add(OperatorRoute.Surface.QUOTES)
            add(OperatorRoute.Surface.CALCULATOR)
            add(OperatorRoute.Surface.SALES_DASHBOARD)
            add(OperatorRoute.Surface.SALES_PIPELINE)
            add(OperatorRoute.Surface.GROUP)
            add(OperatorRoute.Surface.GROUP_MANUAL)
            add(OperatorRoute.Surface.AUDIT)
        }
        OperatorRole.OWNER, OperatorRole.ADMIN -> {
            // Operational surfaces stay flat at the top of the rail.
            add(OperatorRoute.Surface.HOME)
            add(OperatorRoute.Surface.QUOTES)
            add(OperatorRoute.Surface.CALCULATOR)
            add(OperatorRoute.Surface.SALES_DASHBOARD)
            add(OperatorRoute.Surface.SALES_PIPELINE)
            add(OperatorRoute.Surface.GROUP)
            add(OperatorRoute.Surface.GROUP_MANUAL)
            add(OperatorRoute.Surface.ADDON_MATRIX)
            add(OperatorRoute.Surface.IMPORTS)
            add(OperatorRoute.Surface.AUDIT)
            // Each business hub: an Overview card-grid + one config editor per entity, all collapsing
            // under the hub's section header in the rail.
            ctx.registry.hubs.forEach { hub ->
                if (hub.descriptors.isEmpty()) return@forEach
                add(OperatorRoute.Hub(hub.group))
                hub.descriptors.forEach { d ->
                    add(
                        OperatorRoute.Config(
                            entityId = d.id,
                            label = d.plural,
                            icon = OperatorRoute.configIcon(d.isGroup),
                            isGroup = d.isGroup,
                            group = hub.group,
                        ),
                    )
                }
            }
        }
    }
}

/**
 * Build the command-palette catalog: every nav route plus a deep target per entity descriptor so
 * ⌘K can jump straight to any surface, hub or master. Descriptor commands carry the singular +
 * description as keywords so fuzzy search ("vendor", "ci list", "disability") lands the right row.
 */
private fun buildCommands(
    ctx: OperatorContext,
    routes: List<OperatorRoute>,
    onNavigate: (routeId: String) -> Unit,
): List<AegisCommand> = buildList {
    routes.forEach { route ->
        val subtitle = when (route) {
            is OperatorRoute.Surface -> "Surface"
            is OperatorRoute.Hub -> "Hub · ${route.group.title}"
            is OperatorRoute.Config -> "Config · ${route.group.title}"
        }
        add(
            AegisCommand(
                id = route.id,
                title = route.label,
                subtitle = subtitle,
                keywords = when (route) {
                    is OperatorRoute.Config -> ctx.registry.get(route.entityId)?.let { "${it.singular} ${it.description}" } ?: ""
                    else -> ""
                },
                action = { onNavigate(route.id) },
            ),
        )
    }
}
