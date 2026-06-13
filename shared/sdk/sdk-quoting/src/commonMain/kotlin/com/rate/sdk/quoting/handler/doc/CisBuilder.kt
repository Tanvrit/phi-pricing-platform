package com.rate.sdk.quoting.handler.doc

import com.rate.core.money.Money
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import kotlinx.serialization.Serializable

/**
 * IRDAI Customer Information Sheet (CIS) — a single-page summary issued with every policy.
 * Required to be in a customer's preferred language; format and content fixed by IRDAI Health
 * Insurance (Amendment) Regulations 2023.
 *
 * RELOCATED from the monolith (`com.rate.domain.regulatory.CustomerInformationSheet`); repackaged
 * to consume the core rating-contract types and to render the actual sum-insured / family-type /
 * zone now that the [QuoteRequest] is passed alongside the [QuoteResult].
 *
 * Differs from the Prospectus in that the CIS is customer-specific: it shows the actual sum
 * insured, premium, members, and chosen options. The app layer renders it to a PDF emailed /
 * printed at issuance. Money is formatted via [Money.formatIndian] so the same Indian grouping
 * renders on every KMP target (String.format is JVM-only and breaks wasmJs commonMain).
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
    val gstDisclosure: String,
)

object CisBuilder {

    fun build(
        plan: Plan,
        request: QuoteRequest,
        quoteResult: QuoteResult,
        proposalNumber: String,
        customerName: String,
        customerMobile: String,
    ): CustomerInformationSheet {
        val uin = UinRegistry.forPlan(plan.id)

        val fields = listOf(
            CisField("Product Name", plan.name),
            CisField("UIN", uin),
            CisField("Proposal Number", proposalNumber),
            CisField("Customer Name", customerName),
            CisField("Customer Mobile", maskMobile(customerMobile)),
            CisField("Plan Type", plan.planType.displayName),
            CisField("Sum Insured", Money.fromRupees(request.sumInsured).formatIndian(showDecimals = false)),
            CisField("Family Type", request.familyType),
            CisField("Zone", request.zone),
            CisField("Tenure", request.tenure.label),
            CisField("Payment Mode", request.paymentMode.label),
            CisField("Base Premium", Money.fromRupees(quoteResult.basePremiumTotal).formatIndian()),
            CisField("Total Add-ons", Money.fromRupees(quoteResult.totalAddons).formatIndian()),
            CisField("UW Loading", Money.fromRupees(quoteResult.uwLoadingAmount).formatIndian()),
            CisField("Total Discount", Money.fromRupees(quoteResult.totalDiscountAmount).formatIndian()),
            CisField(
                "Sub-total (pre-tax)",
                Money.fromRupees(quoteResult.totalAfterDiscount + quoteResult.instalmentLoadingAmount).formatIndian(),
            ),
            CisField(
                "GST (${(quoteResult.gstRate * 100).toInt()}%)",
                Money.fromRupees(quoteResult.gstAmount).formatIndian(),
            ),
            CisField(
                "Total Premium (incl. GST)",
                Money.fromRupees(quoteResult.totalIncludingGst).formatIndian(),
            ),
            CisField("Free-Look Period", "${IrdaiCircular2024.FREE_LOOK_DAYS_ONLINE} days"),
            CisField("PED Waiting Period", "Up to ${IrdaiCircular2024.PED_MAX_WAITING_MONTHS} months"),
            CisField("Initial Waiting (Sickness)", "${IrdaiCircular2024.INITIAL_WAITING_DAYS_SICKNESS} days"),
            CisField("NCB", "Up to ${(IrdaiCircular2024.MAX_NCB_PERCENT * 100).toInt()}% (5% per claim-free year)"),
            CisField("Engine Version", quoteResult.engineVersion),
            CisField("Rate Table Version", quoteResult.rateTableVersion),
            CisField("Calculated At", quoteResult.calculatedAt?.toString() ?: "n/a"),
        )

        return CustomerInformationSheet(
            productName = plan.name,
            uin = uin,
            proposalNumber = proposalNumber,
            fields = fields,
            grievanceContact = "grievance@example.com / 1800-XXX-XXXX",
            ombudsmanUrl = "https://www.cioins.co.in/",
            freeLookDays = IrdaiCircular2024.FREE_LOOK_DAYS_ONLINE,
            gstDisclosure = "GST @ 18% (HSN 9971) is included in the Total Premium above.",
        )
    }

    /** Show only the last four digits of the mobile, masking the rest. */
    private fun maskMobile(mobile: String): String =
        if (mobile.length <= 4) mobile else "XXXXXX${mobile.takeLast(4)}"
}
