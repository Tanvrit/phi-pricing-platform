package com.rate.domain.repository

import com.rate.domain.model.*

interface RateDataProvider {
    /** Base premium: planId × familyType × zone × ageBandMinAge × sumInsured → INR */
    suspend fun getBasePremium(
        planId: String,
        familyType: String,
        zone: String,
        ageBandMinAge: Int,
        sumInsured: Long
    ): Double

    /**
     * Generic cover-rate lookup.
     * Returns a factor (e.g. 0.05 = 5%), a discount (e.g. -0.15), or a flat INR amount
     * depending on the cover type.  Pass only the dimensions relevant to the cover.
     */
    suspend fun getCoverRate(
        coverId: String,
        param1: String? = null,
        param2: String? = null,
        ageBandMinAge: Int? = null,
        sumInsured: Long? = null,
        planOrTenureKey: String? = null
    ): Double

    /**
     * Per-member rate for Daily Hospital Cash, Personal Accident,
     * Chronic Management, Critical Illness.
     */
    suspend fun getMemberLevelRate(
        coverId: String,
        memberAgeBandMin: Int,
        param1: String? = null,
        sumInsured: Long? = null
    ): Double

    /** Number of payment instalments from Sheet2 lookup */
    suspend fun getInstalmentCount(
        tenure: Tenure,
        paymentTenure: Tenure,
        paymentMode: PaymentMode
    ): Int

    /**
     * Discount rate lookup — queries the discount_rates table.
     * Use this for all disc_* covers instead of getCoverRate().
     * Pass paramKey for param-based discounts (CIBIL band, member count, etc.).
     */
    suspend fun getDiscountRate(discountId: String, paramKey: String? = null): Double

    suspend fun getAllPlans(): List<Plan>
    suspend fun getPlan(planId: String): Plan?

    /**
     * Returns set of cover IDs available for the given plan (from Sheet1 coverage matrix).
     * Empty set means no availability data imported — treat all covers as available.
     */
    suspend fun getCoverAvailability(planId: String): Set<String>

    /**
     * Identifier of the rate-table snapshot in use (e.g. "excel-v7.0", "db-2026-05-20").
     * Stamped into every QuoteResult for audit + reproducibility. Default override
     * returns a generic tag so existing implementations need not change.
     */
    suspend fun rateTableVersion(): String = "unspecified"
}

interface QuoteRepository {
    suspend fun saveQuote(request: QuoteRequest, result: QuoteResult): String
    suspend fun getQuote(id: String): Pair<QuoteRequest, QuoteResult>?
    suspend fun listQuotes(limit: Int = 50): List<Pair<String, QuoteRequest>>
}

interface PlanRepository {
    suspend fun getAllPlans(): List<Plan>
    suspend fun getPlan(id: String): Plan?
    suspend fun upsertPlan(plan: Plan)
    suspend fun deletePlan(id: String)
}
