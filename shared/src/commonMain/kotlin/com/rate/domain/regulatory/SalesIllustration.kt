package com.rate.domain.regulatory

import com.rate.domain.lifecycle.RenewalIllustrationLine
import com.rate.domain.model.QuoteRequest
import com.rate.domain.model.QuoteResult
import kotlinx.serialization.Serializable

/**
 * IRDAI Sales Illustration — mandatory for any product with tenure ≥ 3 years.
 *
 * Shows the customer the year-by-year premium projection (base + adjustments) for the
 * full tenure, so they can see how their commitment evolves. The illustration is
 * "indicative" — actual renewals re-quote at then-prevailing rates.
 *
 * Phase 7 PDFs this; for now we expose the structured form so Aegis can preview and
 * the buyonline can show a "View Illustration" sheet on tenure≥3 quotes.
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
    val cumulativeTotal: Double
)

@Serializable
data class SalesIllustration(
    val productName: String,
    val uin: String,
    val planId: String,
    val tenureYears: Int,
    val years: List<IllustrationYear>,
    val disclaimer: String = DEFAULT_DISCLAIMER
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
     * Build a sales illustration from the QuoteResult's year-by-year breakdown. The
     * QuoteResult already contains per-year line items (the engine computes them); we
     * derive the illustration from those.
     */
    fun build(request: QuoteRequest, result: QuoteResult): SalesIllustration {
        val uin = UinRegistry.forPlan(request.planId)?.value ?: "TBD-UIN-${request.planId}"

        var cumulative = 0.0
        val years = result.yearlyBreakdown.map { yb ->
            // The engine returns per-year base + per-cover lines. For an illustration we
            // collapse to the year's net subtotal + apply GST consistently.
            val subtotal = yb.subtotal
            // Pro-rate the year's share of total discount/addons proportional to subtotal.
            // (Not perfect, but adequate as illustration; Phase 7 plumbs exact per-year totals.)
            val totalSubtotal = result.yearlyBreakdown.sumOf { it.subtotal }
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
                cumulativeTotal = cumulative
            )
        }

        return SalesIllustration(
            productName = "Product UIN $uin",
            uin = uin,
            planId = request.planId,
            tenureYears = request.tenure.years,
            years = years
        )
    }

    /**
     * Build an illustration from a renewal-engine projection (used at renewal time so
     * the customer sees N future years rather than the historical multi-year quote).
     */
    fun fromRenewalIllustration(
        planId: String,
        renewalLines: List<RenewalIllustrationLine>,
        gstRate: Double = 0.18
    ): SalesIllustration {
        val uin = UinRegistry.forPlan(planId)?.value ?: "TBD-UIN-$planId"
        var cumulative = 0.0
        val years = renewalLines.map { line ->
            val gst = line.projectedTotalPremium * gstRate
            val incGst = line.projectedTotalPremium + gst
            cumulative += incGst
            IllustrationYear(
                policyYear = line.policyYear,
                age = line.ageAtRenewal,
                ageBand = line.ageBand,
                basePremium = line.projectedBasePremium,
                totalAddons = 0.0,
                totalDiscount = -line.ncbAmount,
                subtotal = line.projectedTotalPremium,
                gstAmount = gst,
                totalIncludingGst = incGst,
                cumulativeTotal = cumulative
            )
        }
        return SalesIllustration(
            productName = "Renewal projection for plan $planId",
            uin = uin,
            planId = planId,
            tenureYears = years.size,
            years = years
        )
    }
}
