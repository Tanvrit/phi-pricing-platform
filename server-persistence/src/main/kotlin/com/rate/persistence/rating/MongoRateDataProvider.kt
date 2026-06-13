package com.rate.persistence.rating

import com.rate.core.rating.ports.RateDataProvider
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.ingestion.model.rate.InstalmentRow

/**
 * The production [RateDataProvider] — serves rate lookups from the in-RAM [RateTableCache]
 * snapshot of the ACTIVE rate-table version. Returns `Double` (INR / fraction) per the rating
 * contract; Money conversion happens only at QuoteResult assembly.
 *
 * Lookup semantics are PORTED VERBATIM from the monolith's `RateDataProviderImpl` (Exposed):
 *  - base premium is an exact 5-tuple match `(plan, family, zone, ageBandMin, SI)`;
 *  - cover rate is an EXACT match on the full param tuple — a null engine arg matches a row
 *    stored with that segment null (the old impl `andWhere { col.isNull() }`), so a `param1`
 *    cover and a `param1=null` cover never alias;
 *  - member-level rate matches `coverId` + (ageBandMin only when > 0) + param1 (exact null);
 *  - discount uses the composite `discountId[_paramKey]` key;
 *  - instalment count falls back to the rule-based count when no row exists (old impl `?: 1`).
 *  - missing rates return 0.0 (engine treats a 0 rate as "cover priced at zero"), exactly as
 *    the old impl's `?: 0.0`.
 */
class MongoRateDataProvider(
    private val cache: RateTableCache,
) : RateDataProvider {

    private val snap get() = cache.snapshot

    override suspend fun getBasePremium(
        planId: String,
        familyType: String,
        zone: String,
        ageBandMinAge: Int,
        sumInsured: Long,
    ): Double = snap.base[
        RateTableCache.Keys.base(planId, familyType, zone, ageBandMinAge, sumInsured),
    ]?.annualPremium ?: 0.0

    override suspend fun getCoverRate(
        coverId: String,
        param1: String?,
        param2: String?,
        ageBandMinAge: Int?,
        sumInsured: Long?,
        planOrTenureKey: String?,
    ): Double = snap.cover[
        RateTableCache.Keys.cover(coverId, param1, param2, ageBandMinAge, sumInsured, planOrTenureKey),
    ]?.rate ?: 0.0

    override suspend fun getMemberLevelRate(
        coverId: String,
        memberAgeBandMin: Int,
        param1: String?,
        sumInsured: Long?,
    ): Double {
        // Old impl: matched ageBandMin only when > 0, else required it null.
        val ageKey: Int? = if (memberAgeBandMin > 0) memberAgeBandMin else null
        return snap.member[
            RateTableCache.Keys.member(coverId, ageKey, param1, sumInsured),
        ]?.rate ?: 0.0
    }

    override suspend fun getInstalmentCount(
        tenure: Tenure,
        paymentTenure: Tenure,
        paymentMode: PaymentMode,
    ): Int = snap.instalment[
        RateTableCache.Keys.instalment(tenure, paymentTenure, paymentMode),
    ]?.instalmentCount
        // Fallback to the rule-based count (relocated from the monolith) when unconfigured.
        ?: InstalmentRow.computeCount(tenure, paymentMode)

    override suspend fun getDiscountRate(discountId: String, paramKey: String?): Double =
        snap.discount[RateTableCache.Keys.discount(discountId, paramKey)]?.rate
        // The old impl folded paramKey into the id; fall back to the param-less row.
            ?: snap.discount[RateTableCache.Keys.discount(discountId, null)]?.rate
            ?: 0.0

    override suspend fun getAllPlans(): List<Plan> = snap.plans

    override suspend fun getPlan(planId: String): Plan? = snap.plans.firstOrNull { it.id == planId }

    override suspend fun getCoverAvailability(planId: String): Set<String> =
        snap.availability[planId] ?: emptySet()

    override suspend fun rateTableVersion(): String = snap.version.ifBlank { "unspecified" }
}
