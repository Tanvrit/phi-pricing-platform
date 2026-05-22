package com.rate.aegis

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.rate.aegis.components.AegisCallout
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
import com.rate.aegis.theme.AegisColors
import com.rate.aegis.theme.AegisSpacing
import com.rate.aegis.theme.AegisTheme

/**
 * Single entry composable for the whole Aegis platform. Dispatches based on role:
 *
 *   CUSTOMER → Buyonline journey (was the :buyonline module)
 *   BUSINESS → Aegis operator shell: Home / Quotes / Plans / Covers / …
 *              (the rate-calculator from the old :desktop module lives in here too,
 *              accessible as the "Calculator" sub-surface — wired in a follow-up.)
 *   ADMIN    → Placeholder for now; rendered inside the operator shell.
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

@Composable
private fun BusinessRoot() {
    var active by remember { mutableStateOf(AegisSurface.HOME) }
    AegisShell(
        activeSurface = active,
        onSurfaceChange = { active = it },
        user = operatorUser
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
            AegisSurface.AUDIT             -> AuditEventsSurface()
            AegisSurface.SETTINGS          -> SettingsSurface()
            else -> SurfaceTodo(active)
        }
    }
}

@Composable
private fun AdminRoot() {
    var active by remember { mutableStateOf(AegisSurface.AUDIT) }
    AegisShell(
        activeSurface = active,
        onSurfaceChange = { active = it },
        user = operatorUser.copy(role = "Admin")
    ) {
        when (active) {
            AegisSurface.AUDIT             -> AuditEventsSurface()
            AegisSurface.HOME              -> HomeSurface()
            AegisSurface.QUOTES            -> QuoteExplorerSurface()
            AegisSurface.PRODUCT_CATALOG   -> ProductCatalogSurface()
            AegisSurface.PLAN_CONFIGURATOR -> PlanConfiguratorSurface()
            AegisSurface.COVER_CATALOG     -> CoverCatalogSurface()
            AegisSurface.DISCOUNTS         -> DiscountsSurface()
            AegisSurface.RULES             -> ProspectusSurface()
            AegisSurface.REPORTS           -> ReportsSurface()
            AegisSurface.CALCULATOR        -> CalculatorSurface()
            AegisSurface.SETTINGS          -> SettingsSurface()
            else -> SurfaceTodo(active)
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
            body = "Home + Quote Explorer ship in this iteration; the remaining " +
                    "surfaces (Plan Configurator, Cover Catalog, Audit, Settings) are " +
                    "the next ships per DASHBOARD_REDESIGN_PLAN.md §6.1."
        )
    }
}
