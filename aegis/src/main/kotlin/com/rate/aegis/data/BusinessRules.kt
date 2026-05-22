package com.rate.aegis.data

import com.rate.domain.model.Plan
import com.rate.domain.model.PlanType
import com.rate.domain.model.UnderwritingCategory

/**
 * Read-only catalog of the 28 business rules surfaced by `docs/07-business-rules.md`.
 *
 * Each entry carries an `appliesTo(Plan)` heuristic so the Plan Configurator
 * can show "applies to this plan: yes/no" without yet having a server-driven
 * rule engine. The heuristics are deliberately conservative — when in doubt
 * a rule is marked `appliesTo = true`, leaving Ship-6 to tighten the predicate
 * once the rule engine lands.
 */
data class BusinessRule(
    val id: String,
    val title: String,
    val category: String,
    val summary: String,
    val appliesTo: (Plan) -> Boolean,
)

object BusinessRules {
    val ALL: List<BusinessRule> = listOf(
        BusinessRule("R1", "Primary Insured Age Range",
            "Age", "Primary insured must satisfy this plan's [minAge, maxAge] window.",
            { true }),
        BusinessRule("R2", "Senior Plan Entry Age ≥ 60",
            "Age", "PHI Senior only accepts policies where the primary insured is 60+.",
            { it.id == "PHI_SENIOR" }),
        BusinessRule("R3", "POSP Max Entry Age 60",
            "Age", "POSP underwriting caps primary entry age at 60.",
            { it.planType == PlanType.DOMESTIC_POSP }),
        BusinessRule("R4", "Sum Insured Whitelist",
            "Sum Insured", "Only the plan's `availableSumInsureds` may be requested; arbitrary values are rejected.",
            { true }),
        BusinessRule("R5", "Nearest-SI Interpolation",
            "Sum Insured", "If a quoted SI sits between two grid values, the rate engine interpolates linearly.",
            { true }),
        BusinessRule("R6", "Family Type Whitelist",
            "Family", "Only the plan's `availableFamilyTypes` codes are valid (e.g. 2A2C).",
            { true }),
        BusinessRule("R7", "Adult = age ≥ 18",
            "Family", "Children are members with age < 18; everyone else is an adult for cover eligibility.",
            { true }),
        BusinessRule("R8", "ME-1: Chronic Instant + PED Waiting",
            "Mutual Exclusion", "Cannot combine Chronic Instant Hospitalisation with PED Waiting Modification.",
            { it.allowedCoverIds.isEmpty() || it.allowedCoverIds.containsAll(listOf("chronic_instant", "ped_waiting")) }),
        BusinessRule("R9", "ME-2: Consumables List I + Consumable Plus",
            "Mutual Exclusion", "Consumable Plus is a superset; cannot stack with List I.",
            { it.allowedCoverIds.isEmpty() || it.allowedCoverIds.containsAll(listOf("consumables_list1", "consumable_plus")) }),
        BusinessRule("R10", "ME-3: Per Claim Deductible + Aggregate Deductible",
            "Mutual Exclusion", "Choose one — they're not simultaneously applicable.",
            { true }),
        BusinessRule("R11", "ME-4: Employee Discount + Commission Discount",
            "Mutual Exclusion", "Discount in Lieu of Commission cannot stack with Employee discount.",
            { true }),
        BusinessRule("R12", "Spouse Protect — 1A only",
            "Cover Eligibility", "Available only on individual (1A) policies because the cover extends to the spouse.",
            { it.availableFamilyTypes.contains("1A") }),
        BusinessRule("R13", "Female Vaccination — female members only",
            "Cover Eligibility", "Premium charged only against female members.",
            { it.allowedCoverIds.isEmpty() || it.allowedCoverIds.contains("female_vaccination") }),
        BusinessRule("R14", "Enhanced Geo — Global plans only",
            "Cover Eligibility", "Enhanced Geographical Scope add-on is exclusive to GLOBAL / GLOBAL_PLUS plans.",
            { it.planType == PlanType.GLOBAL || it.planType == PlanType.GLOBAL_PLUS }),
        BusinessRule("R15", "Disease Sub-limit Plus — PHI Basic only",
            "Cover Eligibility", "The Disease Specific Sub-limit Plus rider only meaningfully applies to PHI Basic.",
            { it.id == "PHI_BASIC" }),
        BusinessRule("R16", "PED Waiting — Year 1 Only",
            "Waiting Period", "PED Waiting Modification is charged in policy year 1 only; year 2+ premium is zero.",
            { true }),
        BusinessRule("R17", "Maximum Total Discount Cap",
            "Discount", "Total discount is bounded by Plan.maxDiscountCap (e.g. 30%, 35%).",
            { true }),
        BusinessRule("R18", "Tenure Discount: 1y=0%, 2y=7.5%, 3y=10%, 4y=12.5%, 5y=15%",
            "Discount", "Stepped multi-year discount for Single Premium policies; tenure ≥ 2 only.",
            { true }),
        BusinessRule("R19", "Instalment Loading: monthly +6%, quarterly +4%, half-yearly +2%",
            "Payment", "Sub-annual modes attract a loading on the after-discount premium.",
            { true }),
        BusinessRule("R20", "Tenure Discount × Instalment Mode = invalid",
            "Payment", "Multi-year single premium discount is incompatible with sub-annual instalments.",
            { true }),
        BusinessRule("R21", "Senior / Sub-Standard Zone = Pan India",
            "Geography", "PHI Senior and PHI Sub Standard quote on a single Pan India zone factor.",
            { it.underwritingCategory == UnderwritingCategory.SENIOR || it.underwritingCategory == UnderwritingCategory.SUB_STANDARD }),
        BusinessRule("R22", "Zone Resolution from Pincode",
            "Geography", "Quote requests resolve a pincode to one of Z1–Z4 / Pan India via `PincodeZoneMap`.",
            { true }),
        BusinessRule("R23", "Plan Factor on Base Rate",
            "Plan", "Each plan multiplies the base rate by an actuarial plan-factor before stacking covers.",
            { true }),
        BusinessRule("R24", "Allowed Cover ID Whitelist",
            "Plan", "If `Plan.allowedCoverIds` is non-empty, only those covers may be selected.",
            { it.allowedCoverIds.isNotEmpty() }),
        BusinessRule("R25", "UW Loading on UW Loading Base",
            "Underwriting", "Underwriter-applied loading is multiplied against the R59 base accumulation chain.",
            { true }),
        BusinessRule("R26", "Co-Payment Table by UW Category",
            "Underwriting", "STANDARD → OMNIBUS, SENIOR → SENIOR, SUB_STANDARD → SUB_STANDARD.",
            { true }),
        BusinessRule("R27", "Single Premium = entire policy paid upfront",
            "Payment", "Single Premium policies invoice the full tenure × annual premium in one shot.",
            { true }),
        BusinessRule("R28", "Critical Illness — Adult-only aggregation",
            "Member-Level", "CI premium is summed across adult members only; children are excluded.",
            { it.allowedCoverIds.isEmpty() || it.allowedCoverIds.contains("critical_illness") }),
    )
}
