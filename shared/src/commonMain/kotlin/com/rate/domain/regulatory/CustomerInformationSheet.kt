package com.rate.domain.regulatory

import com.rate.domain.model.Plan
import com.rate.domain.model.QuoteResult
import kotlinx.serialization.Serializable

/**
 * IRDAI Customer Information Sheet (CIS) — a single-page summary issued with every
 * policy. Required to be in a customer's preferred language; format and content fixed
 * by IRDAI Health Insurance (Amendment) Regulations 2023.
 *
 * Differs from the Prospectus in that the CIS is customer-specific: it shows the
 * actual sum insured, premium, members, and the chosen options.
 *
 * Phase 7 will render this as a PDF that gets emailed/printed at policy issuance.
 */
@Serializable
data class CisField(val label: String, val value: String)

@Serializable
data class CustomerInformationSheet(
    val productName: String,
    val uin: String,
    val proposalNumber: String,
    val fields: List<CisField>,
    val grievanceContact: String,
    val ombudsmanUrl: String,
    val freeLookDays: Int,
    val gstDisclosure: String
)

object CisBuilder {

    fun build(
        plan: Plan,
        quoteResult: QuoteResult,
        proposalNumber: String,
        customerName: String,
        customerMobile: String
    ): CustomerInformationSheet {
        val uin = UinRegistry.forPlan(plan.id)?.value ?: "TBD-UIN-${plan.id}"

        val fields = listOf(
            CisField("Product Name", plan.name),
            CisField("UIN", uin),
            CisField("Proposal Number", proposalNumber),
            CisField("Customer Name", customerName),
            CisField("Customer Mobile", "XXXXXX${customerMobile.takeLast(4)}"),
            CisField("Plan Type", plan.planType.displayName),
            CisField("Sum Insured", "₹${formatSumInsured(quoteResult)}"),
            CisField("Family Type", "(see quote)"),
            CisField("Zone", "(see quote)"),
            CisField("Base Premium",
                "₹%,.2f".format(quoteResult.basePremiumTotal)),
            CisField("Total Add-ons",
                "₹%,.2f".format(quoteResult.totalAddons)),
            CisField("UW Loading",
                "₹%,.2f".format(quoteResult.uwLoadingAmount)),
            CisField("Total Discount",
                "₹%,.2f".format(quoteResult.totalDiscountAmount)),
            CisField("Sub-total (pre-tax)",
                "₹%,.2f".format(quoteResult.totalAfterDiscount + quoteResult.instalmentLoadingAmount)),
            CisField("GST (${(quoteResult.gstRate * 100).toInt()}%)",
                "₹%,.2f".format(quoteResult.gstAmount)),
            CisField("Total Premium (incl. GST)",
                "₹%,.2f".format(quoteResult.totalIncludingGst)),
            CisField("Free-Look Period", "${IrdaiCircular2024.FREE_LOOK_DAYS_ONLINE} days"),
            CisField("PED Waiting Period", "Up to ${IrdaiCircular2024.PED_MAX_WAITING_MONTHS} months"),
            CisField("Initial Waiting (Sickness)", "${IrdaiCircular2024.INITIAL_WAITING_DAYS_SICKNESS} days"),
            CisField("NCB", "Up to ${(IrdaiCircular2024.MAX_NCB_PERCENT * 100).toInt()}% (5% per claim-free year)"),
            CisField("Engine Version", quoteResult.engineVersion),
            CisField("Rate Table Version", quoteResult.rateTableVersion),
            CisField("Calculated At", quoteResult.calculatedAt?.toString() ?: "n/a")
        )

        return CustomerInformationSheet(
            productName = plan.name,
            uin = uin,
            proposalNumber = proposalNumber,
            fields = fields,
            grievanceContact = "grievance@example.com / 1800-XXX-XXXX",
            ombudsmanUrl = "https://www.cioins.co.in/",
            freeLookDays = IrdaiCircular2024.FREE_LOOK_DAYS_ONLINE,
            gstDisclosure = "GST @ 18% (HSN 9971) is included in the Total Premium above."
        )
    }

    private fun formatSumInsured(qr: QuoteResult): String {
        // Quote result doesn't carry the SI directly — caller would normally pass it
        // in; for the stub we derive from base+total magnitudes.
        return "(see proposal)"
    }
}
