package com.rate.sdk.rating.handler

import com.rate.core.rating.ports.RateDataProvider
import com.rate.core.rating.ports.RatingPort
import com.rate.core.rating.ports.model.CoverParam
import com.rate.core.rating.ports.model.CoverPremiumBreakdown
import com.rate.core.rating.ports.model.DiscountBreakdown
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.core.rating.ports.model.Tenure
import com.rate.core.rating.ports.model.YearBreakdown
import com.rate.core.regulatory.getAgeBand
import com.rate.core.regulatory.getFamilyTypeInfo
import com.rate.sdk.rating.model.COVER_ACCUM_BASES
import com.rate.sdk.rating.model.CoverIds
import com.rate.sdk.rating.model.QuoteValidators
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/**
 * Core pricing engine — replicates Excel Rate_Calculator_v7.0 logic exactly.
 *
 * Relocated VERBATIM from the monolith's `com.rate.domain.engine.PricingEngine`; only the
 * imports were repackaged (rating model + [RateDataProvider] now live in core; [CoverIds] /
 * [COVER_ACCUM_BASES] in this module's model; plan-aware validation via [QuoteValidators]).
 * Double math + [MONEY_EPS] are preserved byte-for-byte — parity with the original engine is
 * critical (the Money/Double boundary is crossed later, only at display/QuoteResult-money
 * assembly in sdk-quoting). Implements the core [RatingPort] so callers (e.g. sdk-quoting's
 * QuoteHandler) depend on the port, never on this concrete class.
 *
 * Calculation order (mirrors Excel Calculator sheet row order):
 *  1. Base premium per policy year  (age-progressive, rate-table lookup)
 *  2. Rows  7–18  : accumulating % covers
 *  3. Row   19    : HOME_CARE flat
 *  4. Rows 20–30  : accumulating % covers (continued, some skip r19)
 *  5. Rows 31–51  : flat / member-level covers (independent)
 *  6. Rows 54, 55, 57, 58 : pre-calculated (referenced by rows 52 & 56)
 *  7. Rows 52, 53, 56 : % covers referencing "future" rows
 *  8. Row  59     : UW loading
 *  9. Rows 64–72  : discounts, capped at 30 %
 * 10. Rows 76–77  : instalment loading ÷ instalment count
 */
class PricingEngine(private val data: RateDataProvider) : RatingPort {

    companion object {
        const val ENGINE_VERSION: String = "1.1.0"

        /**
         * Clamp range for [QuoteRequest.uwLoadingFactor]. Underwriters occasionally
         * leave the factor as raw input; without clamping a factor of `10.0` would
         * apply a 1000% loading silently. The upper bound 2.0 (200%) is the IRDAI
         * conventional ceiling for sub-standard health risks.
         */
        const val UW_LOADING_MIN: Double = 0.0
        const val UW_LOADING_MAX: Double = 2.0

        /** FP epsilon for "is this zero" comparisons that survive Double accumulation. */
        private const val MONEY_EPS: Double = 0.005
    }

    /** [RatingPort] entry point — the single rating edge other features depend on. */
    override suspend fun rate(request: QuoteRequest): QuoteResult = calculate(request)

    suspend fun calculate(request: QuoteRequest): QuoteResult {
        // Plan-aware validation (zone/SI/familyType/age within plan grid) runs FIRST so
        // we surface configuration errors before consuming the engine.
        val plan = data.getPlan(request.planId)
        val planErrors = if (plan != null) QuoteValidators.quoteRequest(plan, request) else emptyList()
        val engineErrors = validate(request)
        val allErrors = engineErrors + planErrors
        if (allErrors.isNotEmpty()) {
            return emptyResult(request, allErrors, gstRate = plan?.gstRate ?: 0.18)
        }

        // Clamp the UW loading factor instead of trusting raw input.
        val clampedUwFactor = max(UW_LOADING_MIN, min(UW_LOADING_MAX, request.uwLoadingFactor))
        val uwClampNote: List<String> =
            if (clampedUwFactor != request.uwLoadingFactor)
                listOf("UW loading factor ${request.uwLoadingFactor} clamped to $clampedUwFactor (range $UW_LOADING_MIN..$UW_LOADING_MAX)")
            else emptyList()

        val years      = request.tenure.years
        val selected   = request.selectedCovers.associateBy { it.coverId }
        fun enabled(id: String) = id in selected
        fun params(id: String)  = selected[id]?.params ?: CoverParam()

        // ── 1. Base premium per year ───────────────────────────────────────
        val base = DoubleArray(years) { yr ->
            val band = getAgeBand(request.primaryAge + yr)
            data.getBasePremium(request.planId, request.familyType, request.zone, band.minAge, request.sumInsured)
        }

        // Working map: coverId → INR array (one element per policy year)
        val yr = mutableMapOf<String, DoubleArray>()
        yr[CoverIds.BASE] = base

        fun sumYr(ids: List<String>, y: Int) = ids.sumOf { yr[it]?.getOrElse(y) { 0.0 } ?: 0.0 }

        // ── Helper: constant-rate cover ───────────────────────────────────
        suspend fun pctCover(id: String, base: List<String>, rateBlock: suspend () -> Double) {
            if (!enabled(id)) return
            val rate = rateBlock()
            yr[id] = DoubleArray(years) { y -> rate * sumYr(base, y) }
        }

        // ── Helper: per-year age-band factor cover ────────────────────────
        suspend fun pctCoverPerYear(id: String, base: List<String>, rateBlock: suspend (ageBandMin: Int) -> Double) {
            if (!enabled(id)) return
            yr[id] = DoubleArray(years) { y ->
                val band = getAgeBand(request.primaryAge + y)
                rateBlock(band.minAge) * sumYr(base, y)
            }
        }

        val accumBases = COVER_ACCUM_BASES

        // ── 2. Rows 7–18 ──────────────────────────────────────────────────

        // Row 7 — Day 1 Instant (factor by coverage multiple × SI)
        if (enabled(CoverIds.DAY1_INSTANT)) {
            val p = params(CoverIds.DAY1_INSTANT)
            val rate = data.getCoverRate(CoverIds.DAY1_INSTANT, p.param1, null, null, request.sumInsured)
            yr[CoverIds.DAY1_INSTANT] = DoubleArray(years) { y -> rate * sumYr(accumBases[CoverIds.DAY1_INSTANT]!!, y) }
        }

        // Row 8 — Loyalty Bonus (per-year age-band × SI factor)
        pctCoverPerYear(CoverIds.LOYALTY_BONUS, accumBases[CoverIds.LOYALTY_BONUS]!!) { band ->
            val p = params(CoverIds.LOYALTY_BONUS)
            data.getCoverRate(CoverIds.LOYALTY_BONUS, p.param1, null, band, request.sumInsured)
        }

        // Row 9 — Double Your Cover (flat 3.25%)
        pctCover(CoverIds.DOUBLE_COVER_7YR, accumBases[CoverIds.DOUBLE_COVER_7YR]!!) {
            data.getCoverRate(CoverIds.DOUBLE_COVER_7YR)
        }

        // Row 10 — Chronic Instant (by condition count)
        pctCover(CoverIds.CHRONIC_INSTANT, accumBases[CoverIds.CHRONIC_INSTANT]!!) {
            data.getCoverRate(CoverIds.CHRONIC_INSTANT, params(CoverIds.CHRONIC_INSTANT).param1)
        }

        // Row 11 — Consumables List I (flat 5%)
        pctCover(CoverIds.CONSUMABLES_LIST1, accumBases[CoverIds.CONSUMABLES_LIST1]!!) {
            data.getCoverRate(CoverIds.CONSUMABLES_LIST1)
        }

        // Row 12 — PED Waiting (first year only)
        if (enabled(CoverIds.PED_WAITING)) {
            val rate = data.getCoverRate(CoverIds.PED_WAITING, params(CoverIds.PED_WAITING).param1)
            val arr  = DoubleArray(years)
            arr[0]   = rate * sumYr(accumBases[CoverIds.PED_WAITING]!!, 0)
            yr[CoverIds.PED_WAITING] = arr
        }

        // Row 13 — Specific Illness Waiting (by primary age band)
        pctCover(CoverIds.SPECIFIC_ILLNESS_WAITING, accumBases[CoverIds.SPECIFIC_ILLNESS_WAITING]!!) {
            data.getCoverRate(CoverIds.SPECIFIC_ILLNESS_WAITING, null, null, getAgeBand(request.primaryAge).minAge, null)
        }

        // Row 14 — Modern Treatment Plus (by plan)
        pctCover(CoverIds.MODERN_TREATMENT_PLUS, accumBases[CoverIds.MODERN_TREATMENT_PLUS]!!) {
            data.getCoverRate(CoverIds.MODERN_TREATMENT_PLUS, null, null, null, null, request.planId)
        }

        // Row 15 — Room Rent Modification (by room type; rates uniform across plans)
        pctCover(CoverIds.ROOM_RENT_MOD, accumBases[CoverIds.ROOM_RENT_MOD]!!) {
            val p = params(CoverIds.ROOM_RENT_MOD)
            data.getCoverRate(CoverIds.ROOM_RENT_MOD, p.param1, null, null, null, null)
        }

        // Row 16 — Diseases Sub-limit Plus (by plan / SI)
        pctCover(CoverIds.DISEASE_SUBLIMIT, accumBases[CoverIds.DISEASE_SUBLIMIT]!!) {
            data.getCoverRate(CoverIds.DISEASE_SUBLIMIT, null, null, null, request.sumInsured, request.planId)
        }

        // Row 17 — Pre-Post Hosp Lump Sum (flat 10%)
        pctCover(CoverIds.PRE_POST_HOSP, accumBases[CoverIds.PRE_POST_HOSP]!!) {
            data.getCoverRate(CoverIds.PRE_POST_HOSP)
        }

        // Row 18 — Consumable Plus List I-IV (flat 9%)
        pctCover(CoverIds.CONSUMABLE_PLUS, accumBases[CoverIds.CONSUMABLE_PLUS]!!) {
            data.getCoverRate(CoverIds.CONSUMABLE_PLUS)
        }

        // ── 3. Row 19 — Home Care (flat INR 167/year) ─────────────────────
        if (enabled(CoverIds.HOME_CARE)) {
            val flat = data.getCoverRate(CoverIds.HOME_CARE)
            yr[CoverIds.HOME_CARE] = DoubleArray(years) { flat }
        }

        // ── 4. Rows 20–30 ─────────────────────────────────────────────────

        // Row 20 — Infinite Claim (by SI; base skips HOME_CARE)
        pctCover(CoverIds.INFINITE_CLAIM, accumBases[CoverIds.INFINITE_CLAIM]!!) {
            data.getCoverRate(CoverIds.INFINITE_CLAIM, null, null, null, request.sumInsured)
        }

        // Row 21 — Restoration Plus (per-year age+SI factor)
        pctCoverPerYear(CoverIds.RESTORATION_PLUS, accumBases[CoverIds.RESTORATION_PLUS]!!) { band ->
            data.getCoverRate(CoverIds.RESTORATION_PLUS, null, null, band, request.sumInsured)
        }

        // Row 22 — Donor Plus (flat per member)
        if (enabled(CoverIds.DONOR_PLUS)) {
            val perMember = data.getCoverRate(CoverIds.DONOR_PLUS)
            val count     = getFamilyTypeInfo(request.familyType).totalMembers
            yr[CoverIds.DONOR_PLUS] = DoubleArray(years) { perMember * count }
        }

        // Row 23 — Spouse Protect (by SI; base includes r19 + r22)
        pctCover(CoverIds.SPOUSE_PROTECT, accumBases[CoverIds.SPOUSE_PROTECT]!!) {
            data.getCoverRate(CoverIds.SPOUSE_PROTECT, null, null, null, request.sumInsured)
        }

        // Row 24 — Durable Medical Equipment (per-year age+SI factor)
        pctCoverPerYear(CoverIds.DURABLE_MEDICAL, accumBases[CoverIds.DURABLE_MEDICAL]!!) { band ->
            data.getCoverRate(CoverIds.DURABLE_MEDICAL, null, null, band, request.sumInsured)
        }

        // Row 25 — Tenure Wise (by tenure label × SI)
        pctCover(CoverIds.TENURE_WISE, accumBases[CoverIds.TENURE_WISE]!!) {
            data.getCoverRate(CoverIds.TENURE_WISE, null, null, null, request.sumInsured, request.tenure.label)
        }

        // Row 26 — Smart Select Network Discount (-15%; base skips HOME_CARE)
        pctCover(CoverIds.SMART_SELECT, accumBases[CoverIds.SMART_SELECT]!!) {
            data.getCoverRate(CoverIds.SMART_SELECT)
        }

        // Row 27 — Incentivize Good Health (currently 0%)
        pctCover(CoverIds.GOOD_HEALTH, accumBases[CoverIds.GOOD_HEALTH]!!) {
            data.getCoverRate(CoverIds.GOOD_HEALTH)
        }

        // Row 28 — Per Claim Deductible (discount by deductible × SI)
        pctCover(CoverIds.PER_CLAIM_DEDUCTIBLE, accumBases[CoverIds.PER_CLAIM_DEDUCTIBLE]!!) {
            data.getCoverRate(CoverIds.PER_CLAIM_DEDUCTIBLE, params(CoverIds.PER_CLAIM_DEDUCTIBLE).param1, null, null, request.sumInsured)
        }

        // Row 29 — Aggregate Deductible (discount by deductible × SI)
        pctCover(CoverIds.AGGREGATE_DEDUCTIBLE, accumBases[CoverIds.AGGREGATE_DEDUCTIBLE]!!) {
            data.getCoverRate(CoverIds.AGGREGATE_DEDUCTIBLE, params(CoverIds.AGGREGATE_DEDUCTIBLE).param1, null, null, request.sumInsured)
        }

        // Row 30 — Co-Pay (discount by plan co-pay table)
        pctCover(CoverIds.CO_PAY, accumBases[CoverIds.CO_PAY]!!) {
            data.getCoverRate(CoverIds.CO_PAY, params(CoverIds.CO_PAY).param1, null, null, null, request.planId)
        }

        // ── 5. Flat / member-level covers (rows 31–51) ────────────────────

        // Row 31 — Child Protect (flat INR 100)
        if (enabled(CoverIds.CHILD_PROTECT)) {
            val flat = data.getCoverRate(CoverIds.CHILD_PROTECT)
            yr[CoverIds.CHILD_PROTECT] = DoubleArray(years) { flat }
        }

        // Row 32 — Daily Hospital Cash (per-member per age-band × cash limit)
        if (enabled(CoverIds.DAILY_HOSPITAL_CASH)) {
            val p   = params(CoverIds.DAILY_HOSPITAL_CASH)
            val arr = DoubleArray(years)
            for (y in 0 until years) {
                arr[y] = request.members.sumOf { m ->
                    val band = getAgeBand(m.age + y)
                    data.getMemberLevelRate(CoverIds.DAILY_HOSPITAL_CASH, band.minAge, p.param1)
                }
            }
            yr[CoverIds.DAILY_HOSPITAL_CASH] = arr
        }

        // Row 33 — Convalescence Benefit (flat by amount × trigger)
        if (enabled(CoverIds.CONVALESCENCE)) {
            val p    = params(CoverIds.CONVALESCENCE)
            val flat = data.getCoverRate(CoverIds.CONVALESCENCE, p.param1, p.param2)
            yr[CoverIds.CONVALESCENCE] = DoubleArray(years) { flat }
        }

        // Row 34 — Compassionate Benefit (flat by amount)
        if (enabled(CoverIds.COMPASSIONATE)) {
            val flat = data.getCoverRate(CoverIds.COMPASSIONATE, params(CoverIds.COMPASSIONATE).param1)
            yr[CoverIds.COMPASSIONATE] = DoubleArray(years) { flat }
        }

        // Row 35 — Personal Accident (per adult member; coverage-amount lookup)
        if (enabled(CoverIds.PERSONAL_ACCIDENT)) {
            val p   = params(CoverIds.PERSONAL_ACCIDENT)
            val arr = DoubleArray(years)
            for (y in 0 until years) {
                arr[y] = request.members.filter { it.isAdult }.sumOf {
                    data.getMemberLevelRate(CoverIds.PERSONAL_ACCIDENT, 0, p.param1)
                }
            }
            yr[CoverIds.PERSONAL_ACCIDENT] = arr
        }

        // Rows 36–45 — Simple flat amounts
        suspend fun flatCover(id: String, paramKey: String? = null) {
            if (!enabled(id)) return
            val flat = data.getCoverRate(id, paramKey)
            yr[id]   = DoubleArray(years) { flat }
        }
        flatCover(CoverIds.AIR_AMBULANCE)
        flatCover(CoverIds.FITNESS_PLUS)
        flatCover(CoverIds.WELLNESS_PACKAGE)
        flatCover(CoverIds.SECOND_OPINION)

        // Row 40 — Maternity & New Born (limit × waiting period)
        if (enabled(CoverIds.MATERNITY_NEWBORN)) {
            val p    = params(CoverIds.MATERNITY_NEWBORN)
            val flat = data.getCoverRate(CoverIds.MATERNITY_NEWBORN, p.param1, p.param2)
            yr[CoverIds.MATERNITY_NEWBORN] = DoubleArray(years) { flat }
        }

        flatCover(CoverIds.POST_DELIVERY_CARE)

        // Row 42 — Infertility (limit × waiting period)
        if (enabled(CoverIds.INFERTILITY)) {
            val p    = params(CoverIds.INFERTILITY)
            val flat = data.getCoverRate(CoverIds.INFERTILITY, p.param1, p.param2)
            yr[CoverIds.INFERTILITY] = DoubleArray(years) { flat }
        }

        flatCover(CoverIds.SURROGATE_MOTHER)
        flatCover(CoverIds.OOCYTE_DONOR)
        flatCover(CoverIds.POST_DISCHARGE_CARE)

        // Row 46 — Adventure Sports (flat per adult)
        if (enabled(CoverIds.ADVENTURE_SPORTS)) {
            val perAdult    = data.getCoverRate(CoverIds.ADVENTURE_SPORTS)
            val adultCount  = getFamilyTypeInfo(request.familyType).adultCount
            yr[CoverIds.ADVENTURE_SPORTS] = DoubleArray(years) { perAdult * adultCount }
        }

        // Row 47 — Chronic Management (per adult member; condition count)
        if (enabled(CoverIds.CHRONIC_MANAGEMENT)) {
            val p   = params(CoverIds.CHRONIC_MANAGEMENT)
            val arr = DoubleArray(years)
            for (y in 0 until years) {
                arr[y] = request.members.filter { it.isAdult }.sumOf {
                    data.getMemberLevelRate(CoverIds.CHRONIC_MANAGEMENT, 0, p.param1)
                }
            }
            yr[CoverIds.CHRONIC_MANAGEMENT] = arr
        }

        // Row 48 — Female Vaccination (flat per female member)
        if (enabled(CoverIds.FEMALE_VACCINATION)) {
            val perFemale = data.getCoverRate(CoverIds.FEMALE_VACCINATION)
            val females   = request.members.count { it.gender == "F" }
            if (females > 0)
                yr[CoverIds.FEMALE_VACCINATION] = DoubleArray(years) { perFemale * females }
        }

        flatCover(CoverIds.PRU_HEALTH_SPECIALIST)

        // Row 50 — Advance Health Check-up (flat per adult × tier)
        if (enabled(CoverIds.ADVANCE_HEALTH_CHECKUP)) {
            val p          = params(CoverIds.ADVANCE_HEALTH_CHECKUP)
            val perAdult   = data.getCoverRate(CoverIds.ADVANCE_HEALTH_CHECKUP, p.param1)
            val adultCount = getFamilyTypeInfo(request.familyType).adultCount
            yr[CoverIds.ADVANCE_HEALTH_CHECKUP] = DoubleArray(years) { perAdult * adultCount }
        }

        // Row 51 — Cashless OPD (by limit)
        if (enabled(CoverIds.CASHLESS_OPD)) {
            val flat = data.getCoverRate(CoverIds.CASHLESS_OPD, params(CoverIds.CASHLESS_OPD).param1)
            yr[CoverIds.CASHLESS_OPD] = DoubleArray(years) { flat }
        }

        // ── 6. Pre-calculated rows (54, 55, 57, 58) ───────────────────────

        // Row 54 — Critical Illness (per adult member; rate per mille × coverage)
        if (enabled(CoverIds.CRITICAL_ILLNESS)) {
            val p        = params(CoverIds.CRITICAL_ILLNESS)
            val coverage = p.param1?.toLongOrNull() ?: 500_000L
            val arr      = DoubleArray(years)
            for (y in 0 until years) {
                arr[y] = request.members.filter { it.isAdult }.sumOf { m ->
                    val band       = getAgeBand(m.age + y)
                    val ratePerMil = data.getMemberLevelRate(CoverIds.CRITICAL_ILLNESS, band.minAge)
                    ratePerMil * coverage / 1000.0
                }
            }
            yr[CoverIds.CRITICAL_ILLNESS] = arr
        }

        // Row 55 — Maternity Fixed Benefit (limit × waiting period)
        if (enabled(CoverIds.MATERNITY_FIXED)) {
            val p    = params(CoverIds.MATERNITY_FIXED)
            val flat = data.getCoverRate(CoverIds.MATERNITY_FIXED, p.param1, p.param2)
            yr[CoverIds.MATERNITY_FIXED] = DoubleArray(years) { flat }
        }

        // Row 57 — Cancer Booster (per-year age+SI factor × rows 4–30 base)
        pctCoverPerYear(CoverIds.CANCER_BOOSTER, COVER_ACCUM_BASES[CoverIds.CANCER_BOOSTER]!!) { band ->
            data.getCoverRate(CoverIds.CANCER_BOOSTER, null, null, band, request.sumInsured)
        }

        // Row 58 — Cancer Annual Screening (flat by age band; global plans use "GLOBAL" rate column)
        if (enabled(CoverIds.CANCER_SCREENING)) {
            val screeningPlanKey = if (request.planId.contains("GLOBAL", ignoreCase = true)) "GLOBAL" else null
            yr[CoverIds.CANCER_SCREENING] = DoubleArray(years) { y ->
                val band = getAgeBand(request.primaryAge + y)
                data.getCoverRate(CoverIds.CANCER_SCREENING, null, null, band.minAge, null, screeningPlanKey)
            }
        }

        // ── 7. Post covers (52, 53, 56) ───────────────────────────────────

        // Row 52 — Prudential Healthy Loading (0.75% of accumulated base incl. r54/57/58)
        pctCover(CoverIds.PRUDENTIAL_HEALTHY, COVER_ACCUM_BASES[CoverIds.PRUDENTIAL_HEALTHY]!!) {
            data.getCoverRate(CoverIds.PRUDENTIAL_HEALTHY)
        }

        // Row 53 — Premium Return
        pctCover(CoverIds.PREMIUM_RETURN, COVER_ACCUM_BASES[CoverIds.PREMIUM_RETURN]!!) {
            data.getCoverRate(CoverIds.PREMIUM_RETURN)
        }

        // Row 56 — Enhanced Geographical Scope (by geography)
        pctCover(CoverIds.ENHANCED_GEO, COVER_ACCUM_BASES[CoverIds.ENHANCED_GEO]!!) {
            data.getCoverRate(CoverIds.ENHANCED_GEO, params(CoverIds.ENHANCED_GEO).param1)
        }

        // ── 8. UW Loading (row 59) ─────────────────────────────────────────
        val uwArr = DoubleArray(years)
        if (clampedUwFactor > 0.0) {
            val uwBase = COVER_ACCUM_BASES[CoverIds.UW_LOADING]!!
            for (y in 0 until years) {
                uwArr[y] = clampedUwFactor * sumYr(uwBase, y)
            }
        }
        yr[CoverIds.UW_LOADING] = uwArr

        // ── 9. Totals ──────────────────────────────────────────────────────
        fun total(id: String) = yr[id]?.sum() ?: 0.0

        val basePremiumTotal  = total(CoverIds.BASE)

        // Cover-pass items that are SEMANTICALLY discounts (smart_select, deductibles, co_pay)
        // are computed in the cover loop above. Pre-fix, they bypassed the discount-cap rule:
        // a customer could stack smart_select (-15%) + per_claim_deductible + aggregate_deductible
        // + co_pay and reach 40%+ discount, exceeding the 30% plan cap.
        //
        // Fix: separate cover-pass discounts from cover-pass positives, then fold them into the
        // same capping pool as the standalone discounts. The cap applies to the COMBINED total.
        val coverPassDiscountIds = setOf(
            CoverIds.SMART_SELECT, CoverIds.PER_CLAIM_DEDUCTIBLE,
            CoverIds.AGGREGATE_DEDUCTIBLE, CoverIds.CO_PAY
        )
        val coverPassDiscountSum = selected.keys.filter { it in coverPassDiscountIds }.sumOf { total(it) }
        val coverPassPositiveSum = selected.keys.filter { it !in coverPassDiscountIds }.sumOf { total(it) }

        val uwLoadingTotal    = uwArr.sum()
        // totalBeforeDisc is the "gross" — positive covers + UW loading + base. Negatives
        // (cover-pass discounts) flow into the capped discount pool below.
        val totalBeforeDisc   = basePremiumTotal + coverPassPositiveSum + uwLoadingTotal
        // For schema compatibility, totalAddons remains the customer-visible sum of selected
        // cover line items (positive + negative cover-pass), matching prior client expectations.
        val addonTotal        = coverPassPositiveSum + coverPassDiscountSum

        // ── 10. Discounts (rows 64–72), hard-capped at plan.maxDiscountCap ───
        val discBreakdowns  = mutableListOf<DiscountBreakdown>()
        val discountIds     = request.selectedDiscounts.map { it.discountId }.toSet()

        // Tenure discount (single premium only, per-year rates)
        if (CoverIds.DISC_TENURE in discountIds &&
            request.paymentMode == PaymentMode.SINGLE_PREMIUM &&
            request.tenure != Tenure.ONE_YEAR) {
            val tenureRates = listOf(0.0, 0.075, 0.10, 0.125, 0.15)
            var discAmt     = 0.0
            for (y in 0 until years) {
                val yearTotal = yr.values.sumOf { it.getOrElse(y) { 0.0 } }
                discAmt      += yearTotal * (-tenureRates.getOrElse(y) { 0.0 })
            }
            if (abs(discAmt) > MONEY_EPS && abs(totalBeforeDisc) > MONEY_EPS)
                discBreakdowns += DiscountBreakdown(CoverIds.DISC_TENURE, "Tenure Discount",
                    discAmt / totalBeforeDisc, discAmt)
        }

        suspend fun flatDiscount(id: String, name: String) {
            if (id !in discountIds) return
            val rate = -data.getDiscountRate(id)  // discount_rates table: stored as positive fraction
            discBreakdowns += DiscountBreakdown(id, name, rate, rate * totalBeforeDisc)
        }
        flatDiscount(CoverIds.DISC_EMPLOYEE,        "Employee / Affiliate Discount")
        flatDiscount(CoverIds.DISC_NRI,              "NRI Discount")
        flatDiscount(CoverIds.DISC_AUTO_DEBIT,       "Auto Debit Discount")
        flatDiscount(CoverIds.DISC_COMMISSION_LIEU,  "Discount in Lieu of Commission")
        flatDiscount(CoverIds.DISC_GMC,              "Corporate GMC Discount")

        if (CoverIds.DISC_CIBIL in discountIds) {
            val param = request.selectedDiscounts.first { it.discountId == CoverIds.DISC_CIBIL }.param
            val rate  = -data.getDiscountRate(CoverIds.DISC_CIBIL, param)
            discBreakdowns += DiscountBreakdown(CoverIds.DISC_CIBIL, "CIBIL Score Discount", rate, rate * totalBeforeDisc)
        }

        if (CoverIds.DISC_MULTI_MEMBER in discountIds) {
            val param = request.selectedDiscounts.first { it.discountId == CoverIds.DISC_MULTI_MEMBER }.param
            val rate  = -data.getDiscountRate(CoverIds.DISC_MULTI_MEMBER, param)
            discBreakdowns += DiscountBreakdown(CoverIds.DISC_MULTI_MEMBER, "Multiple Member Discount", rate, rate * totalBeforeDisc)
        }

        // Hard cap at plan's maxDiscountCap (default 30%) — applied to the COMBINED pool of
        // cover-pass discounts (smart_select, deductibles, co_pay) + standalone discounts.
        // This closes the pre-fix bypass where cover-pass discounts could push the effective
        // reduction past the plan ceiling.
        val standaloneDiscTotal = discBreakdowns.sumOf { it.amount }
        val rawDiscTotal        = standaloneDiscTotal + coverPassDiscountSum
        val capAmount           = totalBeforeDisc * request.maxDiscountCap
        val cappedDiscTotal     = -min(abs(rawDiscTotal), capAmount)

        // If the cap binds, scale BOTH pools proportionally so the customer breakdown is honest.
        val capScale: Double = if (abs(rawDiscTotal) > capAmount && abs(rawDiscTotal) > MONEY_EPS)
            capAmount / abs(rawDiscTotal) else 1.0
        if (capScale < 1.0) {
            // Rewrite standalone discount line items to the scaled amounts so the displayed
            // breakdown sums to the actual applied discount.
            for (i in discBreakdowns.indices) {
                val d = discBreakdowns[i]
                discBreakdowns[i] = d.copy(amount = d.amount * capScale)
            }
        }
        // After capping: standalone breakdowns above are already scaled; the cover-pass
        // pieces are scaled later when constructing coverBreakdownList using capScale.
        val totalAfterDisc  = totalBeforeDisc + cappedDiscTotal

        // ── 11. Instalment loading (row 76) ───────────────────────────────
        val instalLoadingRate  = request.paymentMode.instalmentLoadingRate
        val instalLoadingAmt   = instalLoadingRate * totalAfterDisc
        val instalCount        = if (request.paymentMode == PaymentMode.SINGLE_PREMIUM) 1
                                  else data.getInstalmentCount(request.tenure, request.paymentTenure, request.paymentMode)
        require(instalCount > 0) { "Instalment count must be positive, got $instalCount" }
        val instalPremium      = if (request.paymentMode == PaymentMode.SINGLE_PREMIUM) 0.0
                                  else (totalAfterDisc + instalLoadingAmt) / instalCount

        // ── 11.5. GST (Indian HSN 9971: 18% on health insurance) ──────────
        // Applied to the financed total (after discount + after instalment loading).
        val gstRate            = plan?.gstRate ?: 0.18
        val gstAmount          = (totalAfterDisc + instalLoadingAmt) * gstRate
        val totalIncludingGst  = totalAfterDisc + instalLoadingAmt + gstAmount

        // ── Build result ───────────────────────────────────────────────────
        val coverBreakdownList = (selected.keys + CoverIds.UW_LOADING).map { id ->
            val arr  = yr[id] ?: DoubleArray(years)
            val disc = id.startsWith("disc_") || id in coverPassDiscountIds || id == CoverIds.GOOD_HEALTH
            // Apply discount cap-scale to cover-pass discounts so the breakdown sums honestly
            val scaledTotal = if (disc && id in coverPassDiscountIds && capScale < 1.0)
                arr.sum() * capScale else arr.sum()
            CoverPremiumBreakdown(
                coverId        = id,
                coverName      = coverDisplayName(id),
                yearlyPremiums = arr.toList(),
                totalPremium   = scaledTotal,
                isDiscount     = disc
            )
        }.filter { abs(it.totalPremium) > MONEY_EPS }

        val yearlyBreakdowns = (0 until years).map { y ->
            YearBreakdown(
                year         = y + 1,
                age          = request.primaryAge + y,
                ageBand      = getAgeBand(request.primaryAge + y).label,
                basePremium  = base[y],
                coverPremiums = yr.filterKeys { it != CoverIds.BASE }
                    .mapValues { it.value.getOrElse(y) { 0.0 } }
                    .filter { abs(it.value) > MONEY_EPS },
                subtotal     = yr.values.sumOf { it.getOrElse(y) { 0.0 } }
            )
        }

        return QuoteResult(
            requestId              = generateId(),
            planId                 = request.planId,
            basePremiumTotal       = basePremiumTotal,
            coverBreakdown         = coverBreakdownList,
            totalAddons            = addonTotal,
            uwLoadingAmount        = uwLoadingTotal,
            totalBeforeDiscount    = totalBeforeDisc,
            discountBreakdown      = discBreakdowns,
            totalDiscountAmount    = cappedDiscTotal,
            totalAfterDiscount     = totalAfterDisc,
            instalmentLoadingAmount = instalLoadingAmt,
            instalmentPremium      = instalPremium,
            instalmentCount        = instalCount,
            yearlyBreakdown        = yearlyBreakdowns,
            isValid                = true,
            validationErrors       = uwClampNote,
            gstRate                = gstRate,
            gstAmount              = gstAmount,
            totalIncludingGst      = totalIncludingGst,
            engineVersion          = ENGINE_VERSION,
            rateTableVersion       = data.rateTableVersion(),
            calculatedAt           = Clock.System.now()
        )
    }

    // ── Validation — Pre-Read sheet business rules ─────────────────────────
    private fun validate(req: QuoteRequest): List<String> {
        val errors  = mutableListOf<String>()
        val ids     = req.selectedCovers.map { it.coverId }.toSet()
        val discIds = req.selectedDiscounts.map { it.discountId }.toSet()
        // getFamilyTypeInfo throws on unknown codes; turn that into a friendly error
        // instead of an uncaught exception so the caller gets a useful response.
        val ft = try {
            getFamilyTypeInfo(req.familyType)
        } catch (e: IllegalArgumentException) {
            errors += e.message ?: "Unknown family type"
            return errors
        }
        val singlePA = ft.isIndividual || !ft.isFloater  // single-adult or multi-individual

        if (req.primaryAge < 5 || req.primaryAge > 99)
            errors += "Age must be between 5 and 99"
        if (req.sumInsured <= 0)
            errors += "Sum insured must be positive"

        // Rule 1: Chronic Instant + PED Waiting cannot coexist
        if (CoverIds.CHRONIC_INSTANT in ids && CoverIds.PED_WAITING in ids)
            errors += "Instant Hospitalisation for Chronic Conditions cannot be combined with PED Waiting Period modification"

        // Rule 2: Consumables List I + Consumable Plus cannot coexist
        if (CoverIds.CONSUMABLES_LIST1 in ids && CoverIds.CONSUMABLE_PLUS in ids)
            errors += "Consumables Cover List I cannot be combined with Consumable Plus (List I–IV)"

        // Rule 3: Aggregate Deductible + Per Claim Deductible cannot coexist
        if (CoverIds.PER_CLAIM_DEDUCTIBLE in ids && CoverIds.AGGREGATE_DEDUCTIBLE in ids)
            errors += "Per Claim Deductible and Aggregate Deductible cannot be combined"

        // Rule 4: Chronic Management requires Chronic Instant
        if (CoverIds.CHRONIC_MANAGEMENT in ids && CoverIds.CHRONIC_INSTANT !in ids)
            errors += "Chronic Management requires Instant Hospitalisation for Chronic Conditions"

        // Rule 5: Employee + Commission Lieu cannot coexist
        if (CoverIds.DISC_EMPLOYEE in discIds && CoverIds.DISC_COMMISSION_LIEU in discIds)
            errors += "Employee Discount and Discount in Lieu of Commission cannot be combined"

        // Rule 6: Spouse Protect only for 1A, 1A+child family types
        if (CoverIds.SPOUSE_PROTECT in ids) {
            val spouseAllowed = setOf("1A", "1A1C", "1A2C", "1A3C", "1A4C")
            if (req.familyType !in spouseAllowed)
                errors += "Spouse Protect is only available for single-adult family types (1A, 1A+children)"
        }

        // Rule 7: Child Protect not available for 1A or multi-individual
        if (CoverIds.CHILD_PROTECT in ids) {
            if (req.familyType == "1A" || req.familyType == "multi")
                errors += "Child Protect is not available for 1A or multi-individual family types"
        }

        // Rule 8: Maternity & NewBorn not for single-adult families
        if (CoverIds.MATERNITY_NEWBORN in ids) {
            val singleAdultTypes = setOf("1A", "1A1C", "1A2C", "1A3C", "1A4C")
            if (req.familyType in singleAdultTypes)
                errors += "Maternity & New Born Expenses is not available for single-adult family types"
        }

        // Rule 9: Maternity Fixed not for single-adult families
        if (CoverIds.MATERNITY_FIXED in ids) {
            val singleAdultTypes = setOf("1A", "1A1C", "1A2C", "1A3C", "1A4C")
            if (req.familyType in singleAdultTypes)
                errors += "Maternity Fixed Benefit is not available for single-adult family types"
        }

        // Rule 10: Tenure Wise requires tenure >= 3 years
        if (CoverIds.TENURE_WISE in ids && req.tenure.years < 3)
            errors += "Tenure Wise benefit requires a policy tenure of 3 years or more"

        // Rule 11: Maternity Fixed requires tenure >= 3 years AND Single Premium
        if (CoverIds.MATERNITY_FIXED in ids) {
            if (req.tenure.years < 3 || req.paymentMode != PaymentMode.SINGLE_PREMIUM)
                errors += "Maternity Fixed Benefit requires a 3+ year policy with Single Premium payment"
        }

        // Rule 12: Surrogate Mother requires tenure = 3 years AND Single Premium
        if (CoverIds.SURROGATE_MOTHER in ids) {
            if (req.tenure.years < 3 || req.paymentMode != PaymentMode.SINGLE_PREMIUM)
                errors += "Surrogate Mother cover requires a 3-year Single Premium policy"
        }

        // Rule 13: Maternity NewBorn with 9-month waiting requires tenure = 3 + Single Premium
        if (CoverIds.MATERNITY_NEWBORN in ids) {
            val p = req.selectedCovers.firstOrNull { it.coverId == CoverIds.MATERNITY_NEWBORN }?.params
            if (p?.param2 == "9 Months" && (req.tenure.years < 3 || req.paymentMode != PaymentMode.SINGLE_PREMIUM))
                errors += "Maternity & New Born with 9-month waiting period requires a 3-year Single Premium policy"
        }

        // Rule 14: Personal Accident requires age >= 18 for floater members
        if (CoverIds.PERSONAL_ACCIDENT in ids && ft.isFloater) {
            if (req.members.any { !it.isAdult })
                errors += "Personal Accident cover requires all members to be 18+ in floater policy"
        }

        // Rule 15: Adventure Sports requires adult members
        if (CoverIds.ADVENTURE_SPORTS in ids && ft.adultCount == 0)
            errors += "Adventure Sports Cover requires at least one adult member"

        // Rule 16: Advance Health Check-up requires adult members
        if (CoverIds.ADVANCE_HEALTH_CHECKUP in ids && ft.adultCount == 0)
            errors += "Advance Health Check-up requires at least one adult member"

        // Rule 17: Critical Illness requires age >= 18 in floater
        if (CoverIds.CRITICAL_ILLNESS in ids && ft.isFloater) {
            if (req.members.any { !it.isAdult })
                errors += "Critical Illness Cover requires all members to be 18+ in floater policy"
        }

        // Rule 18: Fitness Plus requires age >= 12 for multi/1A
        if (CoverIds.FITNESS_PLUS in ids && singlePA) {
            if (req.primaryAge < 12)
                errors += "Fitness Plus requires insured age of at least 12 years"
        }

        // Rule 19: Chronic Management requires age >= 18
        if (CoverIds.CHRONIC_MANAGEMENT in ids) {
            if (req.members.filter { it.isAdult }.isEmpty() && req.primaryAge < 18)
                errors += "Chronic Management Program requires all enrolled members to be 18+"
        }

        // Rule 20: Maternity NewBorn with 50K limit + Infertility cannot coexist
        if (CoverIds.MATERNITY_NEWBORN in ids && CoverIds.INFERTILITY in ids) {
            val p = req.selectedCovers.firstOrNull { it.coverId == CoverIds.MATERNITY_NEWBORN }?.params
            if (p?.param1 == "50000")
                errors += "Maternity & New Born with ₹50,000 limit cannot be combined with Infertility Cover"
        }

        // Rule 21: Enhanced Geo only for Global plans (planId contains "GLOBAL")
        if (CoverIds.ENHANCED_GEO in ids && !req.planId.contains("GLOBAL", ignoreCase = true))
            errors += "Enhanced Geographical Scope is only available for Global plans"

        // Rule 22: Advance Health Check-up tier restrictions per plan
        if (CoverIds.ADVANCE_HEALTH_CHECKUP in ids) {
            val tier = req.selectedCovers.firstOrNull { it.coverId == CoverIds.ADVANCE_HEALTH_CHECKUP }?.params?.param1
            if (tier == "Advance" && !req.planId.contains("FLAGSHIP3", ignoreCase = true))
                errors += "Advance Health Check-up (Advance tier) is only available for PHI Flagship 3"
            if (tier == "Basic" && req.planId.contains("FLAGSHIP2", ignoreCase = true))
                errors += "Advance Health Check-up (Basic tier) is not available for PHI Flagship 2"
        }

        return errors
    }

    private fun emptyResult(req: QuoteRequest, errors: List<String>, gstRate: Double = 0.18) = QuoteResult(
        requestId = generateId(), planId = req.planId,
        basePremiumTotal = 0.0, coverBreakdown = emptyList(), totalAddons = 0.0,
        uwLoadingAmount = 0.0, totalBeforeDiscount = 0.0, discountBreakdown = emptyList(),
        totalDiscountAmount = 0.0, totalAfterDiscount = 0.0,
        instalmentLoadingAmount = 0.0, instalmentPremium = 0.0, instalmentCount = 0,
        yearlyBreakdown = emptyList(), isValid = false, validationErrors = errors,
        gstRate = gstRate, gstAmount = 0.0, totalIncludingGst = 0.0,
        engineVersion = ENGINE_VERSION, rateTableVersion = "unknown",
        calculatedAt = Clock.System.now()
    )

    /**
     * ULID-like sortable ID with millisecond timestamp + small random suffix.
     * Replaces the prior `Q-${epochMs}` which collided under concurrent load.
     */
    private fun generateId(): String {
        val ms = Clock.System.now().toEpochMilliseconds()
        val rnd = kotlin.random.Random.nextInt(0, 0xFFFF)
        return "Q-${ms.toString(16).uppercase()}-${rnd.toString(16).uppercase().padStart(4, '0')}"
    }

    private fun coverDisplayName(id: String) =
        id.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
