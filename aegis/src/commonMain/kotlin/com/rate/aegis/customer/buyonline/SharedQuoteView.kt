package com.rate.aegis.customer.buyonline

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Text
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.rate.aegis.customer.buyonline.api.BuyOnlineApiClient
import com.rate.aegis.customer.buyonline.api.QuoteDetailResponse
import com.rate.aegis.customer.buyonline.navigation.BuyOnlineScreen
import com.rate.aegis.customer.buyonline.ui.components.PRUButton
import com.rate.aegis.customer.buyonline.ui.components.StepIndicator
import com.rate.aegis.customer.buyonline.ui.theme.*
import com.rate.domain.model.Plan
import com.rate.domain.money.formatRupees

/**
 * Read-only summary of a shared quote. Invoked from `BuyOnlineApp` when the
 * page was loaded with `?quote=<id>` — the operator-handed link.
 *
 * v1 scope: render the agreed price + key parameters and a "start your
 * application" CTA. We deliberately do NOT auto-seed the buyonline VM from the
 * saved quote (the customer hasn't confirmed yet that the assumptions are
 * still right, and seeding the VM mid-journey is fragile across the
 * 22-screen flow).
 *
 * Failure modes:
 *  - Stale/unknown id    → friendly "couldn't find that quote" card + CTA.
 *  - Plan lookup fails   → headline still renders; plan name shows the raw id.
 *  - Server unreachable  → same as stale id (loader resolves to null).
 *
 * @param quoteId   the saved quote id parsed from `?quote=<id>` / `-Daegis.quote`.
 * @param onContinue invoked when the customer taps "Continue to apply" — the
 *                  caller is expected to flip the SharedQuoteView off and
 *                  fall through to the normal buyonline journey.
 * @param onContinueWithQuote optional hook fired on "Continue" if a quote has
 *                  loaded — lets the host seed the buyonline VM from the
 *                  shared quote so the journey starts at the Plan screen
 *                  with the customer's inputs already filled in.
 */
@Composable
fun SharedQuoteView(
    quoteId: String,
    onContinue: () -> Unit,
    onContinueWithQuote: (QuoteDetailResponse) -> Unit = {}
) {
    val client = remember { BuyOnlineApiClient() }
    var loading by remember { mutableStateOf(true) }
    var detail by remember { mutableStateOf<QuoteDetailResponse?>(null) }
    var plan by remember { mutableStateOf<Plan?>(null) }

    LaunchedEffect(quoteId) {
        loading = true
        val fetched = client.getQuoteById(quoteId)
        detail = fetched
        if (fetched != null) {
            plan = client.getPlan(fetched.request.planId)
        }
        loading = false
    }

    PRUHealthTheme {
        Column(
            Modifier.fillMaxSize().background(PruBackground).verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(20.dp)
        ) {
            Header(quoteId)

            JourneyTimelineCard(detail)

            when {
                loading -> LoadingCard()
                detail == null -> NotFoundCard(quoteId)
                else -> QuoteSummaryCard(detail!!, plan)
            }

            WhatsNextCard()

            // Wire both callbacks: seed the VM first (if we actually have a
            // loaded quote), then flip the showSharedQuote flag in the host.
            // Order matters — seeding sets the journey screen to Quote, then
            // onContinue tears down this view, dropping the customer directly
            // on the seeded Quote screen.
            ContinueCta {
                detail?.let { onContinueWithQuote(it) }
                onContinue()
            }
        }
    }
}

/**
 * Pinned "you got this far" widget shown above the quote summary. Re-uses the
 * live-journey [StepIndicator] frozen at [BuyOnlineScreen.Quote] (stage 2 of
 * 5 — Plan), so the customer recognises the same chrome they saw before the
 * advisor handed off the link.
 *
 * If we have the quote request data, surface a one-line profile sentence
 * underneath so they can sanity-check what was priced. We keep the sentence
 * defensive — only the fields actually present on the request are emitted, in
 * case the server ever returns sparser data.
 */
@Composable
private fun JourneyTimelineCard(detail: QuoteDetailResponse?) {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(vertical = 12.dp, horizontal = 4.dp)) {
            Text(
                "You shared this quote at the Plan stage. Continue from here when you're ready.",
                fontSize = 12.sp,
                color = PruSubtext,
                modifier = Modifier.padding(horizontal = 12.dp)
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
                    modifier = Modifier.padding(horizontal = 12.dp)
                )
            }
        }
    }
}

/**
 * Tiny "here's what happens next" strip below the quote card. Three flat
 * pill-shaped chips covering the remaining stages 3-5 of the journey, so the
 * customer has a mental model of the work left before they tap Continue.
 *
 * Inlined as a Row of styled Boxes rather than reaching for AegisChip — this
 * screen uses PRUHealth chrome (PruRed / PruSurface), and the Aegis chip
 * palette would clash visually.
 */
@Composable
private fun WhatsNextCard() {
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Text("What's next", fontWeight = FontWeight.SemiBold, fontSize = 14.sp, color = PruText)
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
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
    Box(
        modifier
            .background(PruBackground, RoundedCornerShape(8.dp))
            .padding(horizontal = 10.dp, vertical = 8.dp),
        contentAlignment = Alignment.Center
    ) {
        Text(label, fontSize = 11.sp, color = PruText, fontWeight = FontWeight.Medium)
    }
}

@Composable
private fun Header(quoteId: String) {
    Column(
        Modifier.fillMaxWidth(),
        horizontalAlignment = Alignment.CenterHorizontally
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Row(
            Modifier.fillMaxWidth().padding(24.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp)
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
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text("We couldn't find that quote", fontWeight = FontWeight.SemiBold, fontSize = 16.sp, color = PruText)
            Text(
                "The shared link for $quoteId may have expired or been mistyped. " +
                        "Please ask your advisor to re-share the link, or start a fresh application below.",
                fontSize = 13.sp, color = PruSubtext
            )
        }
    }
}

@Composable
private fun QuoteSummaryCard(detail: QuoteDetailResponse, plan: Plan?) {
    val req = detail.request
    val res = detail.result
    Card(
        Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = PruSurface),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(Modifier.fillMaxWidth().padding(20.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            // ── Headline figure ───────────────────────────────────────────
            Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.fillMaxWidth()) {
                Text("Total payable (incl. GST)", fontSize = 12.sp, color = PruSubtext)
                Spacer(Modifier.height(4.dp))
                Text(
                    formatRupees(res.totalIncludingGst),
                    fontSize = 32.sp, fontWeight = FontWeight.Bold, color = PruRed
                )
                Text(
                    "for ${req.tenure.label.lowercase()} of cover",
                    fontSize = 12.sp, color = PruSubtext
                )
            }
            HorizontalDivider(color = Color(0xFFE0E0E0))

            // ── Plan / inputs ─────────────────────────────────────────────
            SummaryRow("Plan", plan?.name ?: req.planId)
            SummaryRow("Sum insured", formatRupees(req.sumInsured.toDouble()))
            SummaryRow("Family", req.familyType)
            SummaryRow("Primary age", req.primaryAge.toString())
            SummaryRow("Zone", req.zone)
            SummaryRow("Tenure", req.tenure.label)

            HorizontalDivider(color = Color(0xFFE0E0E0))

            // ── Premium breakdown ─────────────────────────────────────────
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
private fun SummaryRow(label: String, value: String, bold: Boolean = false) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text(label, fontSize = 13.sp, color = PruSubtext)
        Text(
            value, fontSize = 13.sp, color = PruText,
            fontWeight = if (bold) FontWeight.Bold else FontWeight.Medium
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
            fontSize = 11.sp, color = PruSubtext, modifier = Modifier.fillMaxWidth()
        )
    }
}
