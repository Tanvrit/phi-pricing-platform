package com.rate.sdk.quoting.handler.doc

import com.rate.core.rating.ports.model.Plan
import com.rate.sdk.catalog.model.Cover
import kotlinx.serialization.Serializable

/**
 * IRDAI-mandated prospectus — the public-facing document a customer must be able to read before
 * purchasing. Per IRDAI Health Insurance Regulations 2016, every approved product must publish a
 * prospectus with the mandatory sections enumerated in [ProspectusBuilder.MANDATORY_HEADINGS].
 *
 * RELOCATED from the monolith (`com.rate.domain.regulatory.Prospectus`); repackaged to consume
 * the core [Plan] and the sdk-catalog [Cover] list (the old `CoverCatalog.CoverMeta` is now the
 * catalog's first-class Cover entity). The app layer renders this structured form to a PDF; the
 * operator/buyonline UIs preview it in-app.
 */
@Serializable
data class ProspectusSection(
    val heading: String,
    val body: String,
    /** Optional sub-bullets — kept structured so the renderer can format them. */
    val bullets: List<String> = emptyList(),
)

@Serializable
data class ProspectusDocument(
    val productName: String,
    val uin: String,
    val sections: List<ProspectusSection>,
) {
    /** Whether every IRDAI-mandated heading is present. Used by tests to verify completeness. */
    val mandatoryHeadingsPresent: Boolean
        get() = ProspectusBuilder.MANDATORY_HEADINGS.all { req ->
            sections.any { it.heading.equals(req, ignoreCase = true) }
        }
}

object ProspectusBuilder {

    val MANDATORY_HEADINGS: List<String> = listOf(
        "Product Name and UIN",
        "Plan Highlights",
        "Coverage Details",
        "Exclusions",
        "Waiting Periods",
        "Sum Insured Options",
        "Premium Calculation Basis",
        "Discounts",
        "Free Look and Cancellation",
        "Renewal",
        "Claim Process and Grievance Redressal",
        "Tax Benefit and GST Disclosure",
    )

    /**
     * Build the prospectus for a [plan]. [covers] are the catalog covers available on the plan
     * (resolved by sdk-catalog from the plan's allowedCoverIds, or the full catalog when empty).
     */
    fun build(plan: Plan, covers: List<Cover> = emptyList()): ProspectusDocument {
        val uin = UinRegistry.forPlan(plan.id)

        val sections = listOf(
            ProspectusSection(
                "Product Name and UIN",
                "${plan.name} (UIN: $uin). Issued by PRU Health Insurance Co. Ltd., " +
                    "IRDAI Registration No. TBD-IRDAI-2026-XXX.",
            ),
            ProspectusSection(
                "Plan Highlights",
                "An individual / family-floater health insurance product covering hospitalisation, " +
                    "day-care, pre & post hospitalisation, AYUSH treatment, and optional add-on benefits.",
                bullets = listOf(
                    "Plan type: ${plan.planType.displayName}",
                    "Geographic scope: ${plan.geographyScope.label}",
                    "Sum Insured options: ${plan.availableSumInsureds.joinToString(", ") { "₹${it / 100_000}L" }}",
                    "Entry age: ${plan.minAge}–${plan.maxAge} years",
                    "Available family types: ${plan.availableFamilyTypes.joinToString(", ")}",
                ),
            ),
            ProspectusSection(
                "Coverage Details",
                "Core hospitalisation, day-care procedures, pre-hospitalisation (30 days), " +
                    "post-hospitalisation (60 days), domiciliary, AYUSH treatment, modern treatments " +
                    "per Annexure II of IRDAI Master Circular 2024. Add-on benefits as listed below.",
                bullets = covers.take(20).map { coverBullet(it) },
            ),
            ProspectusSection(
                "Exclusions",
                "Standard exclusions per IRDAI guidelines apply. Detailed list in the Policy Wording document.",
                bullets = listOf(
                    "Self-inflicted injury and attempted suicide",
                    "War, civil unrest, nuclear-related risks",
                    "Cosmetic surgery (unless reconstructive after accident)",
                    "Treatment outside India (unless under Global plan variant)",
                    "Pre-existing diseases during the waiting period — see Waiting Periods section",
                ),
            ),
            ProspectusSection(
                "Waiting Periods",
                "Per IRDAI Master Circular 2024 mandates.",
                bullets = listOf(
                    "Initial waiting (sickness): ${IrdaiCircular2024.INITIAL_WAITING_DAYS_SICKNESS} days",
                    "Pre-existing diseases (PED): up to ${IrdaiCircular2024.PED_MAX_WAITING_MONTHS} months " +
                        "(reducible via PED Waiting Period rider)",
                    "Specific illness: up to ${IrdaiCircular2024.SPECIFIC_ILLNESS_MAX_WAITING_MONTHS} months " +
                        "(reducible via Specific Illness rider)",
                    "Maternity (if cover selected): 9–48 months as per the chosen waiting period",
                ),
            ),
            ProspectusSection(
                "Sum Insured Options",
                plan.availableSumInsureds.joinToString(", ") {
                    "₹${if (it >= 10_000_000L) "${it / 10_000_000} crore" else "${it / 100_000} lakh"}"
                },
            ),
            ProspectusSection(
                "Premium Calculation Basis",
                "Premiums are computed from IRDAI-approved rate tables using age band, sum insured, " +
                    "zone, family type, selected add-ons and discounts. The full breakdown is shown " +
                    "on every quote and is auditable via your policy schedule.",
                bullets = listOf(
                    "GST @ 18% (HSN 9971) is added to the final premium.",
                    "Premium is reviewable at renewal based on then-prevailing rates and age band.",
                    "Multi-year single-premium discount: 7.5% / 10% / 12.5% / 15% for 2/3/4/5 years respectively.",
                    "Instalment loading: 2% (half-yearly), 4% (quarterly), 6% (monthly).",
                ),
            ),
            ProspectusSection(
                "Discounts",
                "Various discounts are available subject to a cumulative cap of " +
                    "${(plan.maxDiscountCap * 100).toInt()}% of the gross premium.",
                bullets = listOf(
                    "Employee / Affiliate: 10%",
                    "NRI: 15%",
                    "Auto-Debit setup: 2.5%",
                    "Corporate Group Medical: 5%",
                    "CIBIL Score-based: up to 15%",
                    "Multiple Member: 5% (2–3 members), 10% (4+ members)",
                    "Smart Select Network: 15% (use of preferred provider network)",
                    "No-Claim Bonus at renewal: 5% per claim-free year " +
                        "(cap ${(IrdaiCircular2024.MAX_NCB_PERCENT * 100).toInt()}%)",
                ),
            ),
            ProspectusSection(
                "Free Look and Cancellation",
                "You may cancel the policy within ${IrdaiCircular2024.FREE_LOOK_DAYS_ONLINE} days of " +
                    "receiving the policy document for a refund. The refund equals the premium paid minus " +
                    "proportional risk-premium for days enjoyed, stamp duty, pre-policy medical-exam expenses " +
                    "(if any) and admin charges. Beyond the free-look window, standard short-period " +
                    "cancellation rates apply per the Policy Wording.",
            ),
            ProspectusSection(
                "Renewal",
                "Lifetime renewability per IRDAI guidelines. Renewal premium is recalculated at the " +
                    "then-prevailing rate for the insured's age band. NCB applies per the Discounts section. " +
                    "A grace period of ${IrdaiCircular2024.GRACE_PERIOD_INDIVIDUAL_DAYS} days from the due " +
                    "date is provided; missing this triggers lapse with a ${IrdaiCircular2024.REVIVAL_WINDOW_YEARS}-year " +
                    "revival window.",
            ),
            ProspectusSection(
                "Claim Process and Grievance Redressal",
                "Cashless treatment at network hospitals: pre-authorise via TPA portal. Reimbursement: " +
                    "submit claim form + documents within 30 days of discharge. For complaints, contact " +
                    "grievance@example.com or 1800-XXX-XXXX. Unresolved complaints may be escalated to " +
                    "the Insurance Ombudsman (https://www.cioins.co.in/).",
            ),
            ProspectusSection(
                "Tax Benefit and GST Disclosure",
                "Premium paid is eligible for income-tax deduction under Section 80D of the Income Tax Act " +
                    "(currently up to ₹25,000 for self/family, ₹50,000 for senior citizens; consult your " +
                    "tax advisor for the applicable limits). 18% GST (HSN 9971) is included in the final " +
                    "premium and shown as a separate line item on every quote and receipt.",
            ),
        )
        return ProspectusDocument(productName = plan.name, uin = uin, sections = sections)
    }

    private fun coverBullet(cover: Cover): String =
        if (cover.description.isBlank()) cover.name else "${cover.name} — ${cover.description}"
}
