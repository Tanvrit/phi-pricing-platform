package com.rate.domain.engine

import com.rate.domain.model.*
import com.rate.domain.repository.RateDataProvider
import kotlinx.datetime.Clock
import kotlin.math.abs
import kotlin.math.min

/**
 * Core pricing engine — replicates Excel Rate_Calculator_v7.0 logic exactly.
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
class PricingEngine(private val data: RateDataProvider) {

    suspend fun calculate(request: QuoteRequest): QuoteResult {
        val errors = validate(request)
        if (errors.isNotEmpty()) {
            return emptyResult(request, errors)
        }

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
        if (request.uwLoadingFactor > 0.0) {
            val uwBase = COVER_ACCUM_BASES[CoverIds.UW_LOADING]!!
            for (y in 0 until years) {
                uwArr[y] = request.uwLoadingFactor * sumYr(uwBase, y)
            }
        }
        yr[CoverIds.UW_LOADING] = uwArr

        // ── 9. Totals ──────────────────────────────────────────────────────
        fun total(id: String) = yr[id]?.sum() ?: 0.0

        val basePremiumTotal  = total(CoverIds.BASE)
        val addonTotal        = selected.keys.sumOf { total(it) }
        val uwLoadingTotal    = uwArr.sum()
        val totalBeforeDisc   = basePremiumTotal + addonTotal + uwLoadingTotal

        // ── 10. Discounts (rows 64–72), hard-capped at 30% ────────────────
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
            if (discAmt != 0.0)
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

        // Hard cap at plan's maxDiscountCap (default 30%)
        val rawDiscTotal    = discBreakdowns.sumOf { it.amount }
        val cappedDiscTotal = -min(abs(rawDiscTotal), totalBeforeDisc * request.maxDiscountCap)

        val totalAfterDisc  = totalBeforeDisc + cappedDiscTotal

        // ── 11. Instalment loading (row 76) ───────────────────────────────
        val instalLoadingRate  = request.paymentMode.instalmentLoadingRate
        val instalLoadingAmt   = instalLoadingRate * totalAfterDisc
        val instalCount        = if (request.paymentMode == PaymentMode.SINGLE_PREMIUM) 1
                                  else data.getInstalmentCount(request.tenure, request.paymentTenure, request.paymentMode)
        val instalPremium      = if (request.paymentMode == PaymentMode.SINGLE_PREMIUM) 0.0
                                  else (totalAfterDisc + instalLoadingAmt) / instalCount

        // ── Build result ───────────────────────────────────────────────────
        val coverBreakdownList = (selected.keys + CoverIds.UW_LOADING).map { id ->
            val arr  = yr[id] ?: DoubleArray(years)
            val disc = id.startsWith("disc_") || id in listOf(
                CoverIds.SMART_SELECT, CoverIds.GOOD_HEALTH,
                CoverIds.PER_CLAIM_DEDUCTIBLE, CoverIds.AGGREGATE_DEDUCTIBLE, CoverIds.CO_PAY
            )
            CoverPremiumBreakdown(
                coverId        = id,
                coverName      = coverDisplayName(id),
                yearlyPremiums = arr.toList(),
                totalPremium   = arr.sum(),
                isDiscount     = disc
            )
        }.filter { it.totalPremium != 0.0 }

        val yearlyBreakdowns = (0 until years).map { y ->
            YearBreakdown(
                year         = y + 1,
                age          = request.primaryAge + y,
                ageBand      = getAgeBand(request.primaryAge + y).label,
                basePremium  = base[y],
                coverPremiums = yr.filterKeys { it != CoverIds.BASE }
                    .mapValues { it.value.getOrElse(y) { 0.0 } }
                    .filter { it.value != 0.0 },
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
            yearlyBreakdown        = yearlyBreakdowns
        )
    }

    // ── Validation — Pre-Read sheet business rules ─────────────────────────
    private fun validate(req: QuoteRequest): List<String> {
        val errors  = mutableListOf<String>()
        val ids     = req.selectedCovers.map { it.coverId }.toSet()
        val discIds = req.selectedDiscounts.map { it.discountId }.toSet()
        val ft      = getFamilyTypeInfo(req.familyType)
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

    private fun emptyResult(req: QuoteRequest, errors: List<String>) = QuoteResult(
        requestId = generateId(), planId = req.planId,
        basePremiumTotal = 0.0, coverBreakdown = emptyList(), totalAddons = 0.0,
        uwLoadingAmount = 0.0, totalBeforeDiscount = 0.0, discountBreakdown = emptyList(),
        totalDiscountAmount = 0.0, totalAfterDiscount = 0.0,
        instalmentLoadingAmount = 0.0, instalmentPremium = 0.0, instalmentCount = 0,
        yearlyBreakdown = emptyList(), isValid = false, validationErrors = errors
    )

    private fun generateId() = "Q-${Clock.System.now().toEpochMilliseconds()}"

    private fun coverDisplayName(id: String) =
        id.split("_").joinToString(" ") { it.replaceFirstChar { c -> c.uppercase() } }
}
