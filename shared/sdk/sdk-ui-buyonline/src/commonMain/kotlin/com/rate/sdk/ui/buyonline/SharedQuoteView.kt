package com.rate.sdk.ui.buyonline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.core.money.formatRupees
import com.rate.core.rating.ports.model.Plan
import com.rate.sdk.quoting.network.QuoteApi
import com.rate.sdk.quoting.network.SavedQuoteView
import com.rate.sdk.ui.buyonline.navigation.BuyOnlineScreen
import com.rate.sdk.ui.buyonline.screens.components.StepIndicator
import com.rate.sdk.ui.kit.brand.PRUButton
import com.rate.sdk.ui.kit.brand.PRUHealthTheme
import com.rate.sdk.ui.kit.brand.PruBackground
import com.rate.sdk.ui.kit.brand.PruRed
import com.rate.sdk.ui.kit.brand.PruSubtext
import com.rate.sdk.ui.kit.brand.PruSurface
import com.rate.sdk.ui.kit.brand.PruText

/**
 * Read-only summary of a shared quote. Invoked from [BuyOnlineApp] when the page
 * was loaded with `?quote=<id>` — the operator-handed link.
 *
 * RELOCATED from the monolith's
 * `com.rate.aegis.customer.buyonline.SharedQuoteView`. The hand-rolled
 * `BuyOnlineApiClient` lookups are replaced by sdk-quoting's [QuoteApi] (`get()`
 * returns the saved request + priced result) and an injected [loadPlan] for the
 * optional "About this plan" enrichment (the plan is a core rating-contract
 * type, fetched over the wire by the host so this module stays free of a
 * catalog dependency).
 *
 * Failure modes: stale/unknown id, plan-lookup miss and server-unreachable all
 * degrade gracefully (friendly card / raw id / headline-only).
 */
@Composable
fun SharedQuoteView(
    quoteId: String,
    quotes: QuoteApi,
    onContinue: () -> Unit,
    onContinueWithQuote: (SavedQuoteView) -> Unit = {},
    loadPlan: suspend (planId: String) -> Plan? = { null },
) {
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<SavedQuoteView?>(null) }
    var plan by remember { mutableStateOf<Plan?>(null) }

    LaunchedEffect(quoteId) {
        loading = true
        val fetched = runCatching { quotes.get(quoteId) }.getOrNull()
        detail = fetched
        if (fetched != null) {
            plan = runCatching { loadPlan(fetched.request.planId) }.getOrNull()
        }
        loading = false
    }

    PRUHealthTheme {
        Column(
            Modifier.fillMaxSize().background(PruBackground).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Header(quoteId)
            JourneyTimelineCard(detail)
            when {
                loading -> LoadingCard()
                detail == null -> NotFoundCard(quoteId)
                else -> {
                    QuoteSummaryCard(detail!!, plan)
                    plan?.let { AboutPlanCard(it) }
                }
            }
            WhatsNextCard()
            ContinueCta {
                detail?.let { onContinueWithQuote(it) }
                onContinue()
            }
        }
    }
}

@Composable
private fun JourneyTimelineCard(detail: SavedQuoteView?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp)) {
            Text(
                "You shared this quote at the Plan stage. Continue from here when you're ready.",
                fontSize = 12.sp,
                color = PruSubtext,
                modifier = Modifier.padding(horizontal = 12.dp),
            )
            Spacer(Modifier.height(6.dp))
            StepIndicator(currentScreen = BuyOnlineScreen.Quote)
            detail?.let {
                val req = it.request
                Spacer(Modifier.height(4.dp))
                Text(
                    "Profile: ${req.familyType} · ${req.primaryAge} yrs · ${req.zone}",
                    fontSize = 11.sp,
                    color = PruSubtext,
                    modifier = Modifier.padding(horizontal = 12.dp),
                )
            }
        }
    }
}

@Composable
private fun WhatsNextCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What's next", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PruText)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                NextChip("1. Confirm details", Modifier.weight(1f))
                NextChip("2. Health declaration", Modifier.weight(1f))
                NextChip("3. Payment & KYC", Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun NextChip(label: String, modifier: Modifier = Modifier) {
    androidx.compose.foundation.layout.Box(
        modifier
            .background(PruBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(label, fontSize = 11.sp, color = PruText, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Header(quoteId: String) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Text("PRUDENTIAL", color = PruRed, fontWeight = FontWeight.Bold, fontSize = 18.sp)
        Spacer(Modifier.height(4.dp))
        Text("Your shared quote", color = PruText, fontWeight = FontWeight.SemiBold, fontSize = 22.sp)
        Spacer(Modifier.height(4.dp))
        Text("Reference: $quoteId", color = PruSubtext, fontSize = 12.sp)
    }
}

@Composable
private fun LoadingCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            CircularProgressIndicator(Modifier.size(20.dp), strokeWidth = 2.dp, color = PruRed)
            Text("Fetching your quote…", color = PruSubtext, fontSize = 14.sp)
        }
    }
}

@Composable
private fun NotFoundCard(quoteId: String) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("We couldn't find that quote", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = PruText)
            Text(
                "The shared link for $quoteId may have expired or been mistyped. " +
                    "Please ask your advisor to re-share the link, or start a fresh application below.",
                fontSize = 13.sp, color = PruSubtext,
            )
        }
    }
}

@Composable
private fun QuoteSummaryCard(detail: SavedQuoteView, plan: Plan?) {
    val req = detail.request
    val res = detail.result
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Total payable (incl. GST)", fontSize = 12.sp, color = PruSubtext)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatRupees(res.totalIncludingGst),
                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PruRed,
                )
                Text(
                    "for ${req.tenure.label.lowercase()} of cover",
                    fontSize = 12.sp, color = PruSubtext,
                )
            }
            HorizontalDivider(color = Color(0xFFE0E0E0))
            SummaryRow("Plan", plan?.name ?: req.planId)
            SummaryRow("Sum insured", formatRupees(req.sumInsured.toDouble()))
            SummaryRow("Family", req.familyType)
            SummaryRow("Primary age", req.primaryAge.toString())
            SummaryRow("Zone", req.zone)
            SummaryRow("Tenure", req.tenure.label)
            HorizontalDivider(color = Color(0xFFE0E0E0))
            SummaryRow("Premium (pre-tax)", formatRupees(res.totalAfterDiscount, 2))
            if (res.gstAmount > 0.0) {
                val gstPct = (res.gstRate * 100).toInt()
                SummaryRow("GST ($gstPct%)", formatRupees(res.gstAmount, 2))
            }
            SummaryRow("Total payable", formatRupees(res.totalIncludingGst), bold = true)
        }
    }
}

@Composable
private fun AboutPlanCard(plan: Plan) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp),
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text(
                "About ${plan.name}",
                fontWeight = FontWeight.SemiBold,
                fontSize = 16.sp,
                color = PruRed,
            )
            HorizontalDivider(color = Color(0xFFE0E0E0))
            SummaryRow("Plan", plan.name)
            SummaryRow("Geography", formatGeography(plan.availableZones))
            SummaryRow("Family types covered", "${plan.availableFamilyTypes.size} family types covered")
            val sis = plan.availableSumInsureds
            if (sis.isNotEmpty()) {
                val minSi = formatRupees(sis.min().toDouble())
                val maxSi = formatRupees(sis.max().toDouble())
                SummaryRow("Sum insured", "$minSi — $maxSi")
            }
            SummaryRow("Max discount cap", "${(plan.maxDiscountCap * 100).toInt()}%")
        }
    }
}

private fun formatGeography(zones: List<String>): String {
    if (zones.isEmpty()) return "—"
    if (zones.size <= 3) return zones.joinToString(", ")
    val hasPanIndia = zones.any { it.contains("Pan India", ignoreCase = true) }
    return if (hasPanIndia) "${zones.size} zones incl. Pan India" else "${zones.size} zones"
}

@Composable
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = PruSubtext)
        Text(
            value, fontSize = 13.sp, color = PruText,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium,
        )
    }
}

@Composable
private fun ContinueCta(onContinue: () -> Unit) {
    Column(Modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        PRUButton(text = "Continue to apply", onClick = onContinue)
        Text(
            "Continuing starts a fresh application. We'll re-confirm the price " +
                "once you tell us about your family.",
            fontSize = 11.sp, color = PruSubtext, modifier = Modifier.fillMaxWidth(),
        )
    }
}
