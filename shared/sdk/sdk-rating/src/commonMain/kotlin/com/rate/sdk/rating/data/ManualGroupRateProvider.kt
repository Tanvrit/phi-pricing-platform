package com.rate.sdk.rating.data

import com.rate.core.rating.ports.GroupRateDataProvider
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.rating.model.ManualGroupRateInput

/**
 * A [GroupRateDataProvider] backed ENTIRELY by an operator-typed [ManualGroupRateInput] — no
 * rate table is ever consulted. It exists so the table-driven [com.rate.sdk.rating.handler.GroupPricingEngine]
 * can be reused verbatim for a fully-manual quote: the underwriter's per-life book rates, their
 * manual group-size discount and their manual industry loading are all served from [input].
 *
 * Group rating only ever calls three of the [GroupRateDataProvider] methods
 * ([getBasePremium], [groupSizeDiscount], [industryLoading]); the remaining
 * [com.rate.core.rating.ports.RateDataProvider] surface is inert here (it belongs to the retail
 * cover-rating path, which the group engine does not exercise).
 */
class ManualGroupRateProvider(
    private val input: ManualGroupRateInput,
) : GroupRateDataProvider {

    /**
     * Per-life book rate keyed by (ageBandMinAge, sumInsured) — exactly the two coordinates the
     * group engine varies its [getBasePremium] lookup by (planId/familyType/zone are constant for
     * a manual quote). If two rows share a key the last one wins; absent keys price to 0.0.
     */
    private val rateByKey: Map<Pair<Int, Long>, Double> =
        input.demography.associate { row ->
            (row.ageBandMinAge to row.sumInsured) to row.ratePerLifeRupees.toDouble()
        }

    // ── The three methods the group engine actually uses ──────────────────────

    override suspend fun getBasePremium(
        planId: String,
        familyType: String,
        zone: String,
        ageBandMinAge: Int,
        sumInsured: Long,
    ): Double = rateByKey[ageBandMinAge to sumInsured] ?: 0.0

    /** Manual override: the operator-typed group-size discount fraction (0..1). */
    override suspend fun groupSizeDiscount(totalLives: Int): Double =
        input.groupSizeDiscountPct / 100.0

    /** Manual override: the operator-typed industry loading fraction (0..1+). */
    override suspend fun industryLoading(industryCode: String): Double =
        input.industryLoadingPct / 100.0

    // ── Inert retail surface (group rating never calls these) ─────────────────

    override suspend fun getCoverRate(
        coverId: String,
        param1: String?,
        param2: String?,
        ageBandMinAge: Int?,
        sumInsured: Long?,
        planOrTenureKey: String?,
    ): Double = 0.0

    override suspend fun getMemberLevelRate(
        coverId: String,
        memberAgeBandMin: Int,
        param1: String?,
        sumInsured: Long?,
    ): Double = 0.0

    override suspend fun getInstalmentCount(
        tenure: Tenure,
        paymentTenure: Tenure,
        paymentMode: PaymentMode,
    ): Int = 0

    override suspend fun getDiscountRate(discountId: String, paramKey: String?): Double = 0.0

    override suspend fun getAllPlans(): List<Plan> = emptyList()

    override suspend fun getPlan(planId: String): Plan? = null

    override suspend fun getCoverAvailability(planId: String): Set<String> = emptySet()

    override suspend fun rateTableVersion(): String = "manual"
}
