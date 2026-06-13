package com.rate.persistence.rating

import com.rate.core.rating.ports.GroupRateDataProvider
import com.rate.core.rating.ports.RateDataProvider
import kotlin.math.ln
import kotlin.math.max

/**
 * Production GROUP [GroupRateDataProvider]. Reuses the retail rate tables (delegates the whole
 * [RateDataProvider] surface to [MongoRateDataProvider] over the same cache) and adds the two
 * GROUP factors so a single rate snapshot serves both lines — GROUP stays a discriminator, not
 * a parallel rate tree.
 *
 * ════════════════════════════════════════════════════════════════════════════════════════════
 *  ILLUSTRATIVE BASE-PREMIUM FALLBACK — NOT ACTUARIAL.  SWAPPABLE.  GROUP-ONLY.
 * ════════════════════════════════════════════════════════════════════════════════════════════
 * The seeded `/data` GROUP catalog (e.g. the Group Personal Accident product) carries only
 * benefit limits and templated "x% of SI" copy — there is NO concrete actuarial rate grid yet,
 * so the underlying `baseRates` collection is empty and the retail
 * [MongoRateDataProvider.getBasePremium] returns `0.0` for every group bucket. With no rates,
 * every group quote would price to ZERO and break the end-to-end flow (census → aggregation →
 * quote → proposal).
 *
 * To keep the flow runnable this provider OVERRIDES [getBasePremium]: it uses the concrete Mongo
 * rate row when one exists, and otherwise falls back to a DETERMINISTIC, clearly-illustrative
 * per-life base premium (see [illustrativePerLifePremium]). The fallback is GROUP-only (it lives
 * in this provider, not the retail one), so the retail pricing path is byte-for-byte unchanged
 * (retail keeps returning `0.0` when unconfigured, where a 0 rate means "priced at zero" in the
 * Excel-parity engine).
 *
 * HOW TO SWAP IN REAL RATES (no engine change required)
 * -----------------------------------------------------
 *  1. Ingest a real GROUP rate Excel/CSV via the rate importer so `baseRates` (and friends) are
 *     populated and `rateMeta` has an ACTIVE GROUP version.
 *  2. On boot/activation `RateTableCache.load(ProductLine.GROUP, ...)` builds a snapshot whose
 *     `base` map holds the concrete `(plan, family, zone, ageBandMin, SI)` rows.
 *  3. [MongoRateDataProvider.getBasePremium] then returns the concrete row (> 0.0) and the
 *     fallback below NEVER fires — no code change needed. Once every live group product has real
 *     rates, the [illustrativePerLifePremium] block + its companion factor tables can be deleted
 *     and [getBasePremium]/[rateTableVersion] reverted to plain delegation.
 *
 * The size-discount band table and industry loadings are platform defaults that an admin can
 * later override via group-config (sdk-catalog GroupProductConfig). Returns Double per the
 * rating contract.
 */
class MongoGroupRateDataProvider(
    cache: RateTableCache,
    private val retail: MongoRateDataProvider = MongoRateDataProvider(cache),
) : GroupRateDataProvider, RateDataProvider by retail {

    /**
     * Per-life base premium for a (grade × age-band) census bucket. Prefers the concrete Mongo
     * rate row; falls back to the illustrative basis when no row exists (concrete row absent ⇒
     * retail provider returns 0.0). The illustrative branch keeps group quotes non-zero until a
     * real rate set is ingested; once it is, the concrete branch wins and this override is a
     * no-op.
     */
    override suspend fun getBasePremium(
        planId: String,
        familyType: String,
        zone: String,
        ageBandMinAge: Int,
        sumInsured: Long,
    ): Double {
        val concrete = retail.getBasePremium(planId, familyType, zone, ageBandMinAge, sumInsured)
        if (concrete > 0.0) return concrete
        // No concrete actuarial row → deterministic illustrative fallback (GROUP-only).
        return illustrativePerLifePremium(ageBandMinAge, sumInsured, zone)
    }

    /**
     * Stamps an illustrative marker onto the version when the active snapshot has no real rate
     * set, so every quote built on the fallback is auditable as illustrative. When a real GROUP
     * rate version is active, that version string is returned unchanged.
     */
    override suspend fun rateTableVersion(): String {
        val v = retail.rateTableVersion()
        return if (v.isBlank() || v == "unspecified") ILLUSTRATIVE_VERSION_TAG else v
    }

    override suspend fun groupSizeDiscount(totalLives: Int): Double = when {
        totalLives >= 1000 -> 0.25
        totalLives >= 500 -> 0.20
        totalLives >= 250 -> 0.15
        totalLives >= 100 -> 0.10
        totalLives >= 50 -> 0.05
        else -> 0.0
    }

    override suspend fun industryLoading(industryCode: String): Double =
        INDUSTRY_LOADINGS[industryCode.uppercase()] ?: 0.0

    /**
     * Deterministic ILLUSTRATIVE per-life annual base premium (INR) for one census bucket.
     *
     *   premium = floorRate(SI) × ageBandFactor(ageBandMin) × zoneFactor(zone) × siFactor(SI)
     *
     *  - floorRate: a rate-per-lakh of SI, log-tapered so premium grows SUB-linearly with SI
     *    (doubling SI does not double premium — GROUP covers pay a multiple of SI).
     *  - ageBandFactor: a 5-year-band escalation curve (accelerates past 50), keyed by the
     *    band's `minAge` — the SAME key the census aggregation and `getBasePremium` use.
     *  - zoneFactor / siFactor: light multipliers so zones/SIs separate cleanly.
     *
     * INR (Double) per the [RateDataProvider] contract; the GROUP engine converts to Money paise
     * at the bucket boundary.
     */
    private fun illustrativePerLifePremium(ageBandMinAge: Int, sumInsured: Long, zone: String): Double {
        if (sumInsured <= 0L) return 0.0
        val lakhs = sumInsured / 100_000.0
        // Log taper keeps the floor gentle for large SI: 1 + ln(lakhs).
        val floor = BASE_RATE_PER_LAKH * (1.0 + ln(max(lakhs, 1.0)))
        val ageF = ageBandFactor(ageBandMinAge)
        val zoneF = ZONE_FACTOR[zone] ?: 1.0
        val siF = siFactor(sumInsured)
        // Never collapse to zero for a positive SI — the whole point is that the flow prices.
        return max(floor * ageF * zoneF * siF, MIN_PER_LIFE_PREMIUM)
    }

    /** 5-year-band age escalation; accelerates after 50 (group accident/health curve). */
    private fun ageBandFactor(ageBandMin: Int): Double = when {
        ageBandMin < 18 -> 0.50
        ageBandMin < 26 -> 0.75
        ageBandMin < 36 -> 1.00
        ageBandMin < 46 -> 1.30
        ageBandMin < 56 -> 1.80
        ageBandMin < 66 -> 2.50
        ageBandMin < 76 -> 3.40
        else -> 4.40
    }

    /** SI tier separation (on top of the log-tapered floor) so SI bands are visibly distinct. */
    private fun siFactor(si: Long): Double = when {
        si <= 1_000_000L -> 1.00
        si <= 2_500_000L -> 1.25
        si <= 5_000_000L -> 1.55
        si <= 10_000_000L -> 1.90
        si <= 50_000_000L -> 2.50
        else -> 3.10
    }

    private companion object {
        /** Marker stamped into the rate-table version so audit trails flag illustrative quotes. */
        const val ILLUSTRATIVE_VERSION_TAG: String = "illustrative-group-v1"

        /** Illustrative base rate (INR) per lakh of SI before tapering/factors. */
        const val BASE_RATE_PER_LAKH: Double = 95.0

        /** Floor so a positive SI never prices to literally zero. */
        const val MIN_PER_LIFE_PREMIUM: Double = 50.0

        /** Light zone separation; unknown zones fall back to 1.0 via the map default. */
        val ZONE_FACTOR: Map<String, Double> = mapOf(
            "Zone 1" to 1.00,
            "Zone 2" to 0.90,
            "Zone 3" to 0.82,
            "Zone 4" to 0.75,
            "Pan India" to 1.00,
        )

        val INDUSTRY_LOADINGS: Map<String, Double> = mapOf(
            "IT" to 0.0, "BFSI" to 0.0, "SERVICES" to 0.05,
            "MANUFACTURING" to 0.10, "CONSTRUCTION" to 0.20,
            "MINING" to 0.30, "CHEMICALS" to 0.25,
        )
    }
}
