package com.rate.sdk.rating.model

import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.regulatory.FAMILY_TYPES

/**
 * Plan-aware quote validation, relocated from the monolith's
 * `com.rate.domain.validation.Validators.quoteRequest(...)`.
 *
 * The foundational, dependency-free FIELD validators (mobile/PAN/Aadhaar/IFSC/…) moved to
 * `com.rate.core.base.util.Validators`; that object intentionally dropped `quoteRequest(...)`
 * because it depends on the rating MODEL (Plan / QuoteRequest), which would have forced a
 * project dependency on core-base. So the plan-aware checks live HERE next to the engine that
 * consumes them — the [PricingEngine] runs these BEFORE pricing so configuration errors
 * surface as an invalid [com.rate.core.rating.ports.model.QuoteResult] rather than wrong money.
 *
 * Pure (no IO) so it runs identically on JVM, iOS and WASM.
 */
object QuoteValidators {

    /**
     * Validates a [QuoteRequest] against its target [plan]. Returns an empty list when all
     * rules pass. These checks complement (and are stricter than) the engine's intra-request
     * rules — they catch consistency between the request and the plan's catalogue limits.
     */
    fun quoteRequest(plan: Plan, req: QuoteRequest): List<String> {
        val errors = mutableListOf<String>()
        if (!plan.isActive) errors += "Plan ${plan.id} is not active"
        if (req.primaryAge < plan.minAge || req.primaryAge > plan.maxAge)
            errors += "Primary age ${req.primaryAge} is outside plan range ${plan.minAge}-${plan.maxAge}"
        if (plan.availableSumInsureds.isNotEmpty() && req.sumInsured !in plan.availableSumInsureds)
            errors += "Sum insured ${req.sumInsured} is not in plan grid ${plan.availableSumInsureds}"
        if (plan.availableZones.isNotEmpty() && req.zone !in plan.availableZones)
            errors += "Zone '${req.zone}' is not available on plan ${plan.id} (allowed: ${plan.availableZones})"
        if (plan.availableFamilyTypes.isNotEmpty() && req.familyType !in plan.availableFamilyTypes)
            errors += "Family type '${req.familyType}' is not available on plan ${plan.id}"
        if (req.paymentTenure.years > req.tenure.years)
            errors += "Payment tenure (${req.paymentTenure.label}) cannot exceed policy tenure (${req.tenure.label})"
        val ft = FAMILY_TYPES.firstOrNull { it.code == req.familyType }
        if (ft != null && req.members.size != ft.totalMembers)
            errors += "Family type ${req.familyType} expects ${ft.totalMembers} members; request has ${req.members.size}"
        // Maternity/infertility requires a female adult in floater families
        val needsFemale = setOf(
            CoverIds.MATERNITY_NEWBORN, CoverIds.MATERNITY_FIXED, CoverIds.INFERTILITY,
        )
        if (req.selectedCovers.any { it.coverId in needsFemale } &&
            req.members.none { it.gender.equals("F", ignoreCase = true) && it.isAdult }
        ) {
            errors += "Maternity / Infertility covers require at least one female adult member"
        }
        // co_pay + (per_claim_deductible OR aggregate_deductible) is not allowed
        val ids = req.selectedCovers.map { it.coverId }.toSet()
        if (CoverIds.CO_PAY in ids && (CoverIds.PER_CLAIM_DEDUCTIBLE in ids || CoverIds.AGGREGATE_DEDUCTIBLE in ids))
            errors += "Co-Pay cannot be combined with a Deductible"
        return errors
    }
}
