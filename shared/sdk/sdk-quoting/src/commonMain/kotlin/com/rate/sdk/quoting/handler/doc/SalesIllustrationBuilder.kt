package com.rate.sdk.quoting.handler.doc

import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.core.rating.ports.model.RenewalIllustrationLine
import kotlinx.serialization.Serializable

/**
 * IRDAI Sales Illustration — mandatory for any product with tenure ≥ 3 years.
 *
 * RELOCATED from the monolith (`com.rate.domain.regulatory.SalesIllustration`); repackaged to
 * consume the core rating-contract types ([QuoteResult], [RenewalIllustrationLine]).
 *
 * Shows the customer the year-by-year premium projection (base + adjustments) for the full
 * tenure, so they can see how their commitment evolves. The illustration is "indicative" —
 * actual renewals re-quote at then-prevailing rates. The app layer renders this structured
 * form to PDF; the operator/buyonline UIs preview it on tenure≥3 quotes.
 */
@Serializable
data class IllustrationYear(
    val policyYear: Int,
    val age: Int,
    val ageBand: String,
    val basePremium: Double,
    val totalAddons: Double,
    val totalDiscount: Double,
    val subtotal: Double,
    val gstAmount: Double,
    val totalIncludingGst: Double,
    val cumulativeTotal: Double,
)

@Serializable
data class SalesIllustration(
    val productName: String,
    val uin: String,
    val planId: String,
    val tenureYears: Int,
    val years: List<IllustrationYear>,
    val disclaimer: String = DEFAULT_DISCLAIMER,
) {
    val totalOverTenure: Double get() = years.sumOf { it.totalIncludingGst }

    companion object {
        const val DEFAULT_DISCLAIMER: String =
            "This illustration is indicative based on the rates in effect at quote time. " +
                "Renewal premiums in years 2-N will be recomputed at the rates prevailing on " +
                "the renewal date and your age band at that time. NCB and other discounts " +
                "apply as per the Policy Wording."
    }
}

object SalesIllustrationBuilder {

    /**
     * Build a sales illustration from the [QuoteResult]'s year-by-year breakdown. The result
     * already contains per-year line items (the engine computes them); the illustration is
     * derived from those, pro-rating the policy-level add-on/discount totals by each year's
     * share of the subtotal and applying GST consistently.
     */
    fun build(request: QuoteRequest, result: QuoteResult): SalesIllustration {
        val uin = UinRegistry.forPlan(request.planId)

        val totalSubtotal = result.yearlyBreakdown.sumOf { it.subtotal }
        var cumulative = 0.0
        val years = result.yearlyBreakdown.map { yb ->
            val subtotal = yb.subtotal
            val proportional = if (totalSubtotal > 0) subtotal / totalSubtotal else 0.0
            val addons = result.totalAddons * proportional
            val discount = result.totalDiscountAmount * proportional
            val gst = subtotal * result.gstRate
            val incGst = subtotal + gst
            cumulative += incGst
            IllustrationYear(
                policyYear = yb.year,
                age = yb.age,
                ageBand = yb.ageBand,
                basePremium = yb.basePremium,
                totalAddons = addons,
                totalDiscount = discount,
                subtotal = subtotal,
                gstAmount = gst,
                totalIncludingGst = incGst,
                cumulativeTotal = cumulative,
            )
        }

        return SalesIllustration(
            productName = "Product UIN $uin",
            uin = uin,
            planId = request.planId,
            tenureYears = request.tenure.years,
            years = years,
        )
    }

    /**
     * Build an illustration from a renewal-engine projection (sdk-policy's RenewalEngine emits
     * [RenewalIllustrationLine]s) so the customer sees N future renewal years rather than the
     * historical multi-year quote. The core line carries the *net* `projectedPremium` (after
     * NCB) plus the NCB percent/amount; the illustration's pre-NCB base is reconstructed as
     * `projectedPremium + ncbAmount`. Age-band is resolved from the line's age.
     */
    fun fromRenewalIllustration(
        planId: String,
        renewalLines: List<RenewalIllustrationLine>,
        gstRate: Double = 0.18,
    ): SalesIllustration {
        val uin = UinRegistry.forPlan(planId)
        var cumulative = 0.0
        val years = renewalLines.map { line ->
            val net = line.projectedPremium
            val preNcbBase = net + line.ncbAmount
            val gst = net * gstRate
            val incGst = net + gst
            cumulative += incGst
            IllustrationYear(
                policyYear = line.year,
                age = line.age,
                ageBand = ageBandLabel(line.age),
                basePremium = preNcbBase,
                totalAddons = 0.0,
                totalDiscount = -line.ncbAmount,
                subtotal = net,
                gstAmount = gst,
                totalIncludingGst = incGst,
                cumulativeTotal = cumulative,
            )
        }
        return SalesIllustration(
            productName = "Renewal projection for plan $planId",
            uin = uin,
            planId = planId,
            tenureYears = years.size,
            years = years,
        )
    }

    /** Resolve the regulatory age-band label for an age (core [com.rate.core.regulatory.getAgeBand]). */
    private fun ageBandLabel(age: Int): String =
        com.rate.core.regulatory.getAgeBand(age.coerceIn(0, 120)).label
}
