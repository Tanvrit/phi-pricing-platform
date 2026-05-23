package com.rate.aegis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.Key
import androidx.compose.ui.input.key.KeyEventType
import androidx.compose.ui.input.key.isCtrlPressed
import androidx.compose.ui.input.key.isMetaPressed
import androidx.compose.ui.input.key.key
import androidx.compose.ui.input.key.onPreviewKeyEvent
import androidx.compose.ui.input.key.type
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisCallout
import com.rate.aegis.components.AegisCommand
import com.rate.aegis.components.AegisCommandPalette
import com.rate.aegis.components.AegisShell
import com.rate.aegis.components.AegisSurface
import com.rate.aegis.components.AegisUser
import com.rate.aegis.components.CalloutKind
import com.rate.aegis.components.NotificationDropdown
import com.rate.aegis.customer.buyonline.BuyOnlineApp
import com.rate.aegis.data.rememberApiClient
import com.rate.aegis.data.rememberDashboardData
import com.rate.aegis.settings.AegisSettingsStore
import com.rate.aegis.surfaces.audit.AuditEventsSurface
import com.rate.aegis.surfaces.calculator.CalculatorSurface
import com.rate.aegis.surfaces.covers.CoverCatalogSurface
import com.rate.aegis.surfaces.discounts.DiscountsSurface
import com.rate.aegis.surfaces.health.ServerHealthSurface
import com.rate.aegis.surfaces.home.HomeSurface
import com.rate.aegis.surfaces.imports.ImportSurface
import com.rate.aegis.surfaces.plans.PlanConfiguratorSurface
import com.rate.aegis.surfaces.products.ProductCatalogSurface
import com.rate.aegis.surfaces.prospectus.ProspectusSurface
import com.rate.aegis.surfaces.quotes.QuoteExplorerSurface
import com.rate.aegis.surfaces.reports.ReportsSurface
import com.rate.aegis.surfaces.settings.SettingsSurface
import com.rate.aegis.surfaces.uw.UwQueueSurface
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.theme.AegisTheme
import com.rate.domain.data.CoverCatalog
import com.rate.domain.model.Plan

/**
 * Single entry composable for the whole Aegis platform. Dispatches based on role:
 *
 *   CUSTOMER → Buyonline journey
 *   BUSINESS → operator shell: Home / Quotes / Plans / Covers / …
 *   ADMIN    → Audit-centric variant of the operator shell
 */
@Composable
fun AegisRoot(role: AegisRole) {
    // One-time browser-locale auto-seed. Runs once per AegisRoot construction
    // (NOT per recomposition — `Unit` key) and persists the result so we never
    // re-seed on subsequent launches.
    //
    // Trigger conditions (all must hold):
    //   1. `AegisSettings.localeAutoSeeded == false` (this is the first launch
    //      since the flag was added, OR the operator has never touched locale).
    //   2. `AegisLaunchContext.hostLocale?.startsWith("hi") == true` (Hindi is
    //      the only non-default language we seed for; everything else falls
    //      through to the "en" default).
    //
    // After flipping the flag once we leave it alone forever — even if the
    // operator manually switches back to "en", we will NOT re-seed on the next
    // boot. This is the apply-on-next-page-open contract the rest of the
    // settings surface already advertises.
    //
    // `AegisTheme` reads the persisted settings on every recomposition, so once
    // the save lands the next theme recomposition picks up `locale = "hi"`
    // automatically — no need to manually invalidate anything from here.
    LaunchedEffect(Unit) {
        val current = runCatching { AegisSettingsStore.load() }.getOrNull() ?: return@LaunchedEffect
        if (current.localeAutoSeeded) return@LaunchedEffect
        val host = AegisLaunchContext.hostLocale?.lowercase()
        if (host?.startsWith("hi") == true) {
            // Persist BOTH the locale flip AND the guard flag in a single save
            // so the next boot is a no-op. We deliberately leave the flag at
            // its default `false` when the host language ISN'T Hindi — that
            // way a customer whose browser later switches to Hindi still gets
            // the auto-seed on their next visit (the contract is "first time
            // we see a Hindi browser", not "first ever boot").
            runCatching {
                AegisSettingsStore.save(current.copy(locale = "hi", localeAutoSeeded = true))
            }
        }
    }
    AegisTheme {
        when (role) {
            AegisRole.CUSTOMER -> BuyOnlineApp()
            AegisRole.BUSINESS -> BusinessRoot()
            AegisRole.ADMIN    -> AdminRoot()
        }
    }
}

private val operatorUser = AegisUser(
    displayName = "Operator",
    initials = "OP",
    role = "Underwriter"
)

/** Surfaces reachable from the command palette, in display order. */
private val OPERATOR_SURFACES: List<AegisSurface> = listOf(
    AegisSurface.HOME,
    AegisSurface.CALCULATOR,
    AegisSurface.QUOTES,
    AegisSurface.PRODUCT_CATALOG,
    AegisSurface.PLAN_CONFIGURATOR,
    AegisSurface.COVER_CATALOG,
    AegisSurface.DISCOUNTS,
    AegisSurface.RULES,
    AegisSurface.REPORTS,
    AegisSurface.UW_QUEUE,
    AegisSurface.AUDIT,
    AegisSurface.RATE_TABLES,
    AegisSurface.IMPORT,
    AegisSurface.SETTINGS,
)

@Composable
private fun BusinessRoot() {
    var active by remember { mutableStateOf(AegisSurface.HOME) }
    OperatorShell(operatorUser, active, onSurfaceChange = { active = it })
}

@Composable
private fun AdminRoot() {
    var active by remember { mutableStateOf(AegisSurface.AUDIT) }
    OperatorShell(operatorUser.copy(role = "Admin"), active, onSurfaceChange = { active = it })
}

/**
 * Shared operator shell — wraps AegisShell with the command-palette overlay
 * and a ⌘K / Ctrl+K key listener. BUSINESS and ADMIN share the same
 * routing table; the difference is just the default landing surface.
 */
@Composable
private fun OperatorShell(
    user: AegisUser,
    active: AegisSurface,
    onSurfaceChange: (AegisSurface) -> Unit,
) {
    var paletteOpen by remember { mutableStateOf(false) }
    var bellOpen by remember { mutableStateOf(false) }

    // Recent server events for the top-bar bell. Lives at OperatorShell scope so
    // the count survives surface navigation and the SSE/initial fetch is shared
    // across the dropdown and the bell badge (one consumer, per the brief).
    val notifications by rememberNotifications()

    // Cross-surface targeting: palette commands set this; the destination surface
    // reads it on first composition and clears it.
    val deepLink = remember { mutableStateOf(DeepLink.NONE) }

    // Global refresh signal — the top-bar Refresh button bumps this counter;
    // every surface that includes [LocalRefreshTicker] in its LaunchedEffect
    // keys refetches in lockstep. See [RefreshTicker.kt].
    val refreshTicker = remember { mutableStateOf(0) }

    // Live data sources for searchable commands:
    //  - quotes piggyback on the dashboard cache (already polled for Home/Quotes/Reports/UW)
    //  - plans need a side-channel fetch; palette is opened on demand, so one-shot is fine
    //  - covers + discounts are static catalog metadata in :shared
    val dashboard by rememberDashboardData()
    val client = rememberApiClient()
    var plans by remember { mutableStateOf<List<Plan>>(emptyList()) }
    LaunchedEffect(client) {
        plans = runCatching { client.getPlans() }.getOrElse { emptyList() }
    }

    val commands = remember(active, plans, dashboard.quotes) {
        val surfaceCommands = OPERATOR_SURFACES.map { surface ->
            AegisCommand(
                id = "surface.${surface.name}",
                title = "Open ${surface.displayName}",
                subtitle = "Surface · operator",
                keywords = surface.name,
                action = {
                    onSurfaceChange(surface)
                    paletteOpen = false
                }
            )
        }
        // Deep-link: the surface action sets a typed DeepLink so the destination
        // surface auto-opens that row's drawer / selects it on first composition.
        val planCommands = plans.map { plan ->
            AegisCommand(
                id = "plan.${plan.id}",
                title = "Plan · ${plan.name}",
                subtitle = "${plan.planType.name} · ${plan.id}",
                keywords = plan.id + " " + plan.lifecycle.name,
                action = {
                    deepLink.value = DeepLink(planId = plan.id)
                    onSurfaceChange(AegisSurface.PLAN_CONFIGURATOR)
                    paletteOpen = false
                }
            )
        }
        val discountCommands = CoverCatalog.DISCOUNTS.map { d ->
            AegisCommand(
                id = "discount.${d.id}",
                title = "Discount · ${d.name}",
                subtitle = d.id,
                keywords = d.id,
                action = {
                    deepLink.value = DeepLink(discountId = d.id)
                    onSurfaceChange(AegisSurface.DISCOUNTS)
                    paletteOpen = false
                }
            )
        }
        val coverCommands = CoverCatalog.ALL.filter { !it.isDiscount }.map { cover ->
            AegisCommand(
                id = "cover.${cover.id}",
                title = "Cover · ${cover.name}",
                subtitle = cover.id,
                keywords = cover.id,
                action = {
                    deepLink.value = DeepLink(coverId = cover.id)
                    onSurfaceChange(AegisSurface.COVER_CATALOG)
                    paletteOpen = false
                }
            )
        }
        val quoteCommands = dashboard.quotes.take(50).map { q ->
            AegisCommand(
                id = "quote.${q.id}",
                title = "Quote · ${q.id}",
                subtitle = "${q.planName} · ${q.familyType} · age ${q.primaryAge}",
                keywords = "${q.planId} ${q.zone} ${q.tenureLabel}",
                action = {
                    deepLink.value = DeepLink(quoteId = q.id)
                    onSurfaceChange(AegisSurface.QUOTES)
                    paletteOpen = false
                }
            )
        }
        surfaceCommands + planCommands + discountCommands + coverCommands + quoteCommands
    }

    CompositionLocalProvider(
        LocalAegisDeepLink provides deepLink,
        LocalSurfaceRouter provides onSurfaceChange,
        LocalRefreshTicker provides refreshTicker,
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .onPreviewKeyEvent { event ->
                    if (event.type != KeyEventType.KeyDown) return@onPreviewKeyEvent false
                    val isMod = event.isMetaPressed || event.isCtrlPressed
                    if (isMod && event.key == Key.K) {
                        paletteOpen = !paletteOpen
                        true
                    } else false
                }
        ) {
            AegisShell(
                activeSurface = active,
                onSurfaceChange = onSurfaceChange,
                user = user,
                onRefresh = { refreshTicker.value += 1 },
                unreadCount = notifications.size,
                onBellClick = { bellOpen = !bellOpen },
            ) {
                when (active) {
                    AegisSurface.HOME              -> HomeSurface()
                    AegisSurface.CALCULATOR        -> CalculatorSurface()
                    AegisSurface.QUOTES            -> QuoteExplorerSurface()
                    AegisSurface.PRODUCT_CATALOG   -> ProductCatalogSurface()
                    AegisSurface.PLAN_CONFIGURATOR -> PlanConfiguratorSurface()
                    AegisSurface.COVER_CATALOG     -> CoverCatalogSurface()
                    AegisSurface.DISCOUNTS         -> DiscountsSurface()
                    AegisSurface.RULES             -> ProspectusSurface()
                    AegisSurface.REPORTS           -> ReportsSurface()
                    AegisSurface.UW_QUEUE          -> UwQueueSurface()
                    AegisSurface.AUDIT             -> AuditEventsSurface()
                    AegisSurface.RATE_TABLES       -> ServerHealthSurface()
                    AegisSurface.IMPORT            -> ImportSurface()
                    AegisSurface.SETTINGS          -> SettingsSurface()
                    else -> SurfaceTodo(active)
                }
            }
            AegisCommandPalette(
                open = paletteOpen,
                commands = commands,
                onDismiss = { paletteOpen = false }
            )
            NotificationDropdown(
                open = bellOpen,
                notifications = notifications,
                onDismiss = { bellOpen = false },
                onJumpToEvent = { n ->
                    bellOpen = false
                    // Reuse the existing deep-link plumbing: resourceType drives
                    // which surface to open; resourceId becomes the typed id.
                    // Unsupported types just route to the Audit surface so the
                    // operator still gets context.
                    when (n.resourceType.lowercase()) {
                        "plan" -> {
                            if (!n.resourceId.isNullOrBlank()) {
                                deepLink.value = DeepLink(planId = n.resourceId)
                            }
                            onSurfaceChange(AegisSurface.PLAN_CONFIGURATOR)
                        }
                        "quote" -> {
                            if (!n.resourceId.isNullOrBlank()) {
                                deepLink.value = DeepLink(quoteId = n.resourceId)
                            }
                            onSurfaceChange(AegisSurface.QUOTES)
                        }
                        else -> onSurfaceChange(AegisSurface.AUDIT)
                    }
                },
                onViewAll = { onSurfaceChange(AegisSurface.AUDIT) },
            )
        }
    }
}

@Composable
private fun SurfaceTodo(surface: AegisSurface) {
    Column(
        Modifier.fillMaxSize().background(AegisColors.canvas).padding(AegisSpacing.s8),
        verticalArrangement = Arrangement.spacedBy(AegisSpacing.s4)
    ) {
        Text(surface.displayName, fontSize = 24.sp, fontWeight = FontWeight.SemiBold)
        AegisCallout(
            kind = CalloutKind.INFO,
            title = "Surface coming next",
            body = "This surface is queued for a future iteration."
        )
    }
}
