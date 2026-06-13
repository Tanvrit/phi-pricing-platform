package com.rate.sdk.proposal.handler

import com.rate.core.base.error.AppResult
import com.rate.core.base.error.DomainError
import com.rate.core.base.model.PageRequest
import com.rate.core.base.time.Now
import com.rate.core.rating.ports.model.CoverSelection
import com.rate.core.rating.ports.model.Member
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.AddOnItem
import com.rate.sdk.catalog.repository.AddOnRepository
import com.rate.sdk.proposal.event.ProposalEvent
import com.rate.sdk.proposal.event.ProposalEventSink
import com.rate.sdk.proposal.model.journey.PremiumRequest
import com.rate.sdk.proposal.model.journey.PremiumResult
import com.rate.sdk.quoting.handler.QuoteHandler

/**
 * Premium pricing for the buy-online journey. RELOCATED from the monolith's `BuyOnlineRoutes`
 * `/premium` route, which assembled a `QuoteRequest` inline and called `engine.calculate(...)`
 * directly with hardcoded `BuyOnlineTier.toPlanId()` + `BUYONLINE_ADDONS` lookups.
 *
 * The re-arch makes this a thin presentation handler over the real rating path:
 *  - tier ("PREMIER"/"SIGNATURE"/"GLOBAL") → actuarial Plan id is resolved from the admin-CRUD
 *    sdk-catalog [AddOn] bundle (`AddOn.code` == tier, `AddOn.planRef` == Plan id) — the
 *    hardcoded `toPlanId()` map is gone;
 *  - selected add-on ids → [CoverSelection]s are resolved from the bundle's [AddOn.items]
 *    (each item's `coverCode` + `defaultParam`), so editing a bundle in the console changes the
 *    journey with no code change;
 *  - pricing is delegated to sdk-quoting's [QuoteHandler] (which prices via the injected
 *    `RatingPort` and — for [calculateAndSave] — persists), NOT a concrete engine.
 *
 * The Money/Double boundary is honoured: [PremiumResult] stays Double (the journey UI shows it
 * directly); conversion to Money happens only when a [com.rate.sdk.proposal.model.Proposal] is
 * assembled.
 */
class PremiumHandler(
    private val quotes: QuoteHandler,
    private val addOns: AddOnRepository,
    private val events: ProposalEventSink = ProposalEventSink.NOOP,
    private val maxSumInsured: Long = 100_000_000L,
    private val defaultMaxDiscountCap: Double = 0.30,
) {

    /**
     * Price the journey premium WITHOUT persisting a quote. Validates the tier / tenure / SI /
     * add-on bounds (the monolith's route guards), resolves the bundle, and delegates to
     * [QuoteHandler.calculate].
     */
    suspend fun quote(request: PremiumRequest): AppResult<PremiumResult> =
        priceInternal(request, persist = false, actor = null)

    /**
     * Price AND persist a quote (so a proposal can later reference [PremiumResult.quoteRef]).
     * Delegates to [QuoteHandler.calculateAndSave].
     */
    suspend fun quoteAndSave(request: PremiumRequest, actor: String? = null): AppResult<PremiumResult> =
        priceInternal(request, persist = true, actor = actor)

    // ── internals ────────────────────────────────────────────────────────────

    private suspend fun priceInternal(
        request: PremiumRequest,
        persist: Boolean,
        actor: String?,
    ): AppResult<PremiumResult> {
        val tenure = Tenure.entries.firstOrNull { it.years == request.tenure }
            ?: return err("Tenure must be 1..5 years")
        if (request.sumInsured <= 0 || request.sumInsured > maxSumInsured) {
            return err("Sum insured out of allowed range")
        }
        if (request.addOnIds.size > 50) return err("Too many add-ons selected")

        val bundle = resolveBundle(request.tier)
            ?: return err("Unknown tier '${request.tier}'")
        if (bundle.planRef.isBlank()) {
            return err("Tier '${request.tier}' is not mapped to an actuarial plan")
        }

        val selectedCovers = resolveCovers(bundle, request.addOnIds)
        val members = request.members.ifEmpty {
            listOf(Member(memberId = 1, age = request.primaryAge, relationship = "Self"))
        }
        val familyType = request.familyType.ifBlank { "1A" }

        val quoteRequest = QuoteRequest(
            planId = bundle.planRef,
            primaryAge = request.primaryAge,
            sumInsured = request.sumInsured,
            familyType = familyType,
            zone = request.zone,
            tenure = tenure,
            // ANNUAL mode: the engine ignores DISC_TENURE (tenure discount applies to
            // SINGLE_PREMIUM only, per the Excel rules the monolith documented).
            paymentMode = PaymentMode.ANNUAL,
            paymentTenure = tenure,
            members = members,
            selectedCovers = selectedCovers,
            selectedDiscounts = emptyList(),
            uwLoadingFactor = 0.0,
            maxDiscountCap = defaultMaxDiscountCap,
        )

        val (result, quoteRef) = if (persist) {
            when (val saved = quotes.calculateAndSave(quoteRequest, actor)) {
                is AppResult.Ok -> saved.value.result to saved.value.id
                is AppResult.Err -> return AppResult.Err(saved.error)
            }
        } else {
            quotes.calculate(quoteRequest) to null
        }

        if (!result.isValid) return AppResult.Err(DomainError.Validation(result.validationErrors))

        val premium = toPremiumResult(result, quoteRef)
        events.emit(
            ProposalEvent.PremiumQuoted(
                tier = request.tier,
                planRef = bundle.planRef,
                sumInsured = request.sumInsured,
                totalIncludingGst = premium.totalIncludingGst,
                quoteRef = quoteRef,
                at = Now.instant(),
            ),
        )
        return AppResult.Ok(premium)
    }

    /** Match the bundle whose code equals the tier (case-insensitive). */
    private suspend fun resolveBundle(tier: String): AddOn? {
        val page = addOns.list(PageRequest(size = 200))
        return page.items.firstOrNull { it.code.equals(tier, ignoreCase = true) }
    }

    /**
     * Resolve the selected add-on ids to [CoverSelection]s using the bundle's items. An add-on id
     * here is an item's `coverCode` (the journey selects covers by their catalog code, matching the
     * monolith's `BUYONLINE_ADDONS[id]` lookup). Unknown ids are silently dropped (the route did
     * the same via `mapNotNull`).
     */
    private fun resolveCovers(bundle: AddOn, selectedIds: List<String>): List<CoverSelection> {
        if (selectedIds.isEmpty()) {
            // None explicitly chosen → ship the bundle's pre-selected covers (Signature/Global
            // pre-bundle; Premier ships empty per AddOn.preSelected semantics).
            return if (bundle.preSelected) bundle.items.toSelections() else emptyList()
        }
        val byCode = bundle.items.associateBy { it.coverCode }
        return selectedIds.mapNotNull { id ->
            byCode[id]?.let { CoverSelection(it.coverCode, it.defaultParam) }
        }
    }

    private fun List<AddOnItem>.toSelections(): List<CoverSelection> =
        map { CoverSelection(it.coverCode, it.defaultParam) }

    private fun toPremiumResult(result: QuoteResult, quoteRef: String?): PremiumResult {
        // Annual premium = totalAfterDiscount (single-year tenure headline, as the monolith did).
        val annual = result.totalAfterDiscount
        return PremiumResult(
            annualPremium = annual,
            monthlyPremium = annual / 12.0,
            gstAmount = result.gstAmount,
            totalIncludingGst = result.totalIncludingGst,
            basePremium = result.basePremiumTotal,
            totalAddons = result.totalAddons,
            totalDiscountAmount = result.totalDiscountAmount,
            tenureDiscountRate = 0.0,
            quoteRef = quoteRef,
        )
    }

    private fun err(message: String): AppResult<PremiumResult> =
        AppResult.Err(DomainError.Validation(listOf(message)))
}
