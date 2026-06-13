package com.rate.core.rating.ports

import com.rate.core.base.model.Page
import com.rate.core.base.model.PageRequest
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.QuoteRequest
import com.rate.core.rating.ports.model.QuoteResult
import com.rate.core.rating.ports.model.Tenure
import kotlinx.datetime.Instant
import kotlinx.serialization.Serializable

/**
 * Actuarial rate-table lookup PORT. Returns Double (INR) by design — the engine
 * accumulates in Double and converts to Money only at QuoteResult assembly, preserving
 * byte-for-byte parity with the original Excel-derived calculator. The Mongo-backed
 * actual (loaded into an in-RAM snapshot at boot) lives in server-persistence.
 */
interface RateDataProvider {
    suspend fun getBasePremium(
        planId: String,
        familyType: String,
        zone: String,
        ageBandMinAge: Int,
        sumInsured: Long,
    ): Double

    suspend fun getCoverRate(
        coverId: String,
        param1: String? = null,
        param2: String? = null,
        ageBandMinAge: Int? = null,
        sumInsured: Long? = null,
        planOrTenureKey: String? = null,
    ): Double

    suspend fun getMemberLevelRate(
        coverId: String,
        memberAgeBandMin: Int,
        param1: String? = null,
        sumInsured: Long? = null,
    ): Double

    suspend fun getInstalmentCount(
        tenure: Tenure,
        paymentTenure: Tenure,
        paymentMode: PaymentMode,
    ): Int

    suspend fun getDiscountRate(discountId: String, paramKey: String? = null): Double

    suspend fun getAllPlans(): List<Plan>
    suspend fun getPlan(planId: String): Plan?

    /** Cover IDs available for a plan (empty = no availability data → treat all as available). */
    suspend fun getCoverAvailability(planId: String): Set<String>

    /** Snapshot identifier stamped into every QuoteResult for audit/reproducibility. */
    suspend fun rateTableVersion(): String = "unspecified"
}

/**
 * GROUP rate provider — reuses the retail actuarial tables and adds group-specific factors
 * (size discounts, industry loadings). Implemented in server-persistence over the same
 * Mongo snapshot. Keeps GROUP a discriminator, not a parallel rate tree.
 */
interface GroupRateDataProvider : RateDataProvider {
    suspend fun groupSizeDiscount(totalLives: Int): Double
    suspend fun industryLoading(industryCode: String): Double
}

/**
 * The single rating entry point — inverts the one real cross-feature edge. sdk-quoting's
 * QuoteHandler depends on this port (not the concrete PricingEngine in sdk-rating).
 */
interface RatingPort {
    suspend fun rate(request: QuoteRequest): QuoteResult
}

/**
 * Renewal base-rate lookup PORT used by sdk-policy's RenewalEngine (it depends on this,
 * never on the concrete engine). Returns Double (INR), consistent with RateDataProvider.
 */
interface RenewalRateProvider {
    suspend fun baseFor(
        planId: String,
        age: Int,
        sumInsured: Long,
        familyType: String,
        zone: String,
    ): Double
}

/** Lightweight saved-quote projection for dashboards. */
@Serializable
data class QuoteSummary(
    val id: String,
    val createdAt: Instant,
    val request: QuoteRequest,
    val totalIncludingGst: Double,
    val isValid: Boolean = true,
)

/** Quote persistence PORT (Mongo actual in server-persistence). */
interface QuoteRepository {
    suspend fun saveQuote(request: QuoteRequest, result: QuoteResult): String
    suspend fun getQuote(id: String): Pair<QuoteRequest, QuoteResult>?
    suspend fun listQuoteSummaries(req: PageRequest = PageRequest()): Page<QuoteSummary>
}

/** Plan persistence PORT — Plan is admin-CRUD; the generic ConfigRepository<Plan> backs it. */
interface PlanRepository {
    suspend fun getAllPlans(): List<Plan>
    suspend fun getPlan(id: String): Plan?
    suspend fun upsertPlan(plan: Plan)
    suspend fun deletePlan(id: String)
}
