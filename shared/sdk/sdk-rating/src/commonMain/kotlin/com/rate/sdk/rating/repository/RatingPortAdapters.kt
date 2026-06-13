package com.rate.sdk.rating.repository

import com.rate.core.rating.ports.RateDataProvider
import com.rate.core.rating.ports.RenewalRateProvider
import com.rate.core.regulatory.getAgeBand

/**
 * Adapts a [RateDataProvider] to the [RenewalRateProvider] PORT consumed by sdk-policy's
 * RenewalEngine. The renewal engine only needs the per-life BASE premium projected at a future
 * age; it should NOT depend on the concrete [com.rate.sdk.rating.handler.PricingEngine], so this
 * thin adapter exposes exactly `baseFor(...)` over the same rate table the retail engine uses.
 *
 * [baseFor] maps the requested [age] to its rating age-band min-age (the key the rate table
 * indexes on — see [getAgeBand]) and forwards to [RateDataProvider.getBasePremium], so a
 * renewal projection picks up the same age-progression the new-business quote did.
 */
class RateDataRenewalProvider(
    private val data: RateDataProvider,
) : RenewalRateProvider {

    override suspend fun baseFor(
        planId: String,
        age: Int,
        sumInsured: Long,
        familyType: String,
        zone: String,
    ): Double {
        val bandMinAge = getAgeBand(age.coerceIn(0, 120)).minAge
        return data.getBasePremium(
            planId = planId,
            familyType = familyType,
            zone = zone,
            ageBandMinAge = bandMinAge,
            sumInsured = sumInsured,
        )
    }
}
