package com.rate.aegis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import com.rate.aegis.customer.buyonline.BuyOnlineApp
import com.rate.aegis.surfaces.audit.AuditEventsSurface
import com.rate.aegis.surfaces.calculator.CalculatorSurface
import com.rate.aegis.surfaces.covers.CoverCatalogSurface
import com.rate.aegis.surfaces.discounts.DiscountsSurface
import com.rate.aegis.surfaces.home.HomeSurface
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

/**
 * Single entry composable for the whole Aegis platform. Dispatches based on role:
 *
 *   CUSTOMER → Buyonline journey
 *   BUSINESS → operator shell: Home / Quotes / Plans / Covers / …
 *   ADMIN    → Audit-centric variant of the operator shell
 */
@Composable
fun AegisRoot(role: AegisRole) {
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
    val commands = remember(active) {
        OPERATOR_SURFACES.map { surface ->
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
    }

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
            user = user
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
                AegisSurface.SETTINGS          -> SettingsSurface()
                else -> SurfaceTodo(active)
            }
        }
        AegisCommandPalette(
            open = paletteOpen,
            commands = commands,
            onDismiss = { paletteOpen = false }
        )
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
