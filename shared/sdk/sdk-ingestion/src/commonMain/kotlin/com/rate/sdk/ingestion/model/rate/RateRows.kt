package com.rate.sdk.ingestion.model.rate

import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Tenure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Immutable actuarial rate rows — the engine's lookup tables, relocated from the monolith's
 * Exposed tables (`BaseRatesTable`, `CoverRateLookupTable`, `MemberLevelRatesTable`,
 * `DiscountRatesTable`, `InstalmentConfigTable`, `CoverAvailabilityTable`) into pure-KMP value
 * types tagged with a [BaseRateRow.version] (or per-row `version`) pointer.
 *
 * Design rules:
 *  - Rows are NEVER mutated; an import writes a brand-new immutable set under a new version and
 *    flips the [com.rate.sdk.ingestion.model.RateMeta.active] pointer.
 *  - Rate values are [Double] (INR / fraction) — engine parity with the original Excel-derived
 *    calculator (Money conversion happens only at QuoteResult assembly, per the rating contract).
 *  - Each row carries a DETERMINISTIC composite `id` (see each type's `id`/`stableId`) so the
 *    same logical row across re-imports of the same version upserts in place — making
 *    [com.rate.sdk.ingestion.repository.RateImportRepository.bulkUpsert] idempotent.
 *
 * The composite-id columns mirror the engine's [com.rate.core.rating.ports.RateDataProvider]
 * lookup parameters exactly, so a Mongo actual can index on them and resolve a row per call.
 */

private const val SEP = "|"

/** Normalise a nullable key segment so null and "" never collide in a composite id. */
private fun seg(value: String?): String = value?.takeIf { it.isNotBlank() } ?: "_"
private fun seg(value: Int?): String = value?.toString() ?: "_"
private fun seg(value: Long?): String = value?.toString() ?: "_"

/**
 * BASE premium row — `(version, planId, familyType, zone, ageBandMin, sumInsured) → annualPremium`.
 * Backs [com.rate.core.rating.ports.RateDataProvider.getBasePremium]. Relocated from `BaseRatesTable`.
 */
@Serializable
data class BaseRateRow(
    @SerialName("_id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("planId") val planId: String,
    @SerialName("familyType") val familyType: String,
    @SerialName("zone") val zone: String,
    @SerialName("ageBandMin") val ageBandMin: Int,
    @SerialName("sumInsured") val sumInsured: Long,
    /** Annual premium in INR (Double for engine parity). */
    @SerialName("annualPremium") val annualPremium: Double,
) {
    companion object {
        fun stableId(
            version: String,
            planId: String,
            familyType: String,
            zone: String,
            ageBandMin: Int,
            sumInsured: Long,
        ): String = listOf(
            "base", version, planId, familyType, zone, ageBandMin.toString(), sumInsured.toString(),
        ).joinToString(SEP)

        fun of(
            version: String,
            planId: String,
            familyType: String,
            zone: String,
            ageBandMin: Int,
            sumInsured: Long,
            annualPremium: Double,
        ): BaseRateRow = BaseRateRow(
            id = stableId(version, planId, familyType, zone, ageBandMin, sumInsured),
            version = version,
            planId = planId,
            familyType = familyType,
            zone = zone,
            ageBandMin = ageBandMin,
            sumInsured = sumInsured,
            annualPremium = annualPremium,
        )
    }
}

/**
 * COVER rate row — the general per-cover lookup keyed by the full engine parameter tuple.
 * Backs [com.rate.core.rating.ports.RateDataProvider.getCoverRate]. Relocated from
 * `CoverRateLookupTable` (cover_id, param1_key, param2_key, age_band_min, sum_insured, plan_id).
 *
 * [planOrTenureKey] is the relocated `plan_id` column — the engine passes either a plan id
 * (e.g. "PHI_SENIOR"), a tenure label (e.g. "3 Years") or a co-pay table id depending on cover.
 */
@Serializable
data class CoverRateRow(
    @SerialName("_id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("coverId") val coverId: String,
    @SerialName("param1") val param1: String? = null,
    @SerialName("param2") val param2: String? = null,
    @SerialName("ageBandMin") val ageBandMin: Int? = null,
    @SerialName("sumInsured") val sumInsured: Long? = null,
    @SerialName("planOrTenureKey") val planOrTenureKey: String? = null,
    /** Rate (fraction for %-covers, INR for flat covers). Double for engine parity. */
    @SerialName("rate") val rate: Double,
) {
    companion object {
        fun stableId(
            version: String,
            coverId: String,
            param1: String?,
            param2: String?,
            ageBandMin: Int?,
            sumInsured: Long?,
            planOrTenureKey: String?,
        ): String = listOf(
            "cover", version, coverId,
            seg(param1), seg(param2), seg(ageBandMin), seg(sumInsured), seg(planOrTenureKey),
        ).joinToString(SEP)

        fun of(
            version: String,
            coverId: String,
            rate: Double,
            param1: String? = null,
            param2: String? = null,
            ageBandMin: Int? = null,
            sumInsured: Long? = null,
            planOrTenureKey: String? = null,
        ): CoverRateRow = CoverRateRow(
            id = stableId(version, coverId, param1, param2, ageBandMin, sumInsured, planOrTenureKey),
            version = version,
            coverId = coverId,
            param1 = param1,
            param2 = param2,
            ageBandMin = ageBandMin,
            sumInsured = sumInsured,
            planOrTenureKey = planOrTenureKey,
            rate = rate,
        )
    }
}

/**
 * MEMBER-LEVEL rate row — per-member-age-band rate (Personal Accident, Critical Illness, …).
 * Backs [com.rate.core.rating.ports.RateDataProvider.getMemberLevelRate]. Relocated from
 * `MemberLevelRatesTable` (cover_id, age_band_min, param1_key).
 */
@Serializable
data class MemberLevelRow(
    @SerialName("_id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("coverId") val coverId: String,
    @SerialName("ageBandMin") val ageBandMin: Int? = null,
    @SerialName("param1") val param1: String? = null,
    @SerialName("sumInsured") val sumInsured: Long? = null,
    @SerialName("rate") val rate: Double,
) {
    companion object {
        fun stableId(
            version: String,
            coverId: String,
            ageBandMin: Int?,
            param1: String?,
            sumInsured: Long?,
        ): String = listOf(
            "member", version, coverId, seg(ageBandMin), seg(param1), seg(sumInsured),
        ).joinToString(SEP)

        fun of(
            version: String,
            coverId: String,
            rate: Double,
            ageBandMin: Int? = null,
            param1: String? = null,
            sumInsured: Long? = null,
        ): MemberLevelRow = MemberLevelRow(
            id = stableId(version, coverId, ageBandMin, param1, sumInsured),
            version = version,
            coverId = coverId,
            ageBandMin = ageBandMin,
            param1 = param1,
            sumInsured = sumInsured,
            rate = rate,
        )
    }
}

/**
 * DISCOUNT rate row — `(version, discountId, paramKey) → rate`.
 * Backs [com.rate.core.rating.ports.RateDataProvider.getDiscountRate]. Relocated from
 * `DiscountRatesTable` (discount_id, param_key).
 */
@Serializable
data class DiscountRow(
    @SerialName("_id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("discountId") val discountId: String,
    @SerialName("paramKey") val paramKey: String? = null,
    /** Discount fraction (e.g. 0.10 = 10%). Double for engine parity. */
    @SerialName("rate") val rate: Double,
) {
    companion object {
        fun stableId(version: String, discountId: String, paramKey: String?): String =
            listOf("discount", version, discountId, seg(paramKey)).joinToString(SEP)

        fun of(version: String, discountId: String, rate: Double, paramKey: String? = null): DiscountRow =
            DiscountRow(
                id = stableId(version, discountId, paramKey),
                version = version,
                discountId = discountId,
                paramKey = paramKey,
                rate = rate,
            )
    }
}

/**
 * INSTALMENT count row — `(version, policyTenure, paymentTenure, paymentMode) → instalmentCount`.
 * Backs [com.rate.core.rating.ports.RateDataProvider.getInstalmentCount]. Relocated from
 * `InstalmentConfigTable` (policy_tenure, payment_tenure, payment_mode). Tenure/mode are stored
 * as their enum names for round-trip stability.
 */
@Serializable
data class InstalmentRow(
    @SerialName("_id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("policyTenure") val policyTenure: Tenure,
    @SerialName("paymentTenure") val paymentTenure: Tenure,
    @SerialName("paymentMode") val paymentMode: PaymentMode,
    @SerialName("instalmentCount") val instalmentCount: Int,
) {
    companion object {
        fun stableId(
            version: String,
            policyTenure: Tenure,
            paymentTenure: Tenure,
            paymentMode: PaymentMode,
        ): String = listOf(
            "instalment", version, policyTenure.name, paymentTenure.name, paymentMode.name,
        ).joinToString(SEP)

        fun of(
            version: String,
            policyTenure: Tenure,
            paymentTenure: Tenure,
            paymentMode: PaymentMode,
            instalmentCount: Int,
        ): InstalmentRow = InstalmentRow(
            id = stableId(version, policyTenure, paymentTenure, paymentMode),
            version = version,
            policyTenure = policyTenure,
            paymentTenure = paymentTenure,
            paymentMode = paymentMode,
            instalmentCount = instalmentCount,
        )

        /**
         * Rule-based instalment count, relocated from the monolith's
         * `computeAndInsertInstalmentConfig` fallback (used when the sheet lacks Sheet2).
         */
        fun computeCount(policyTenure: Tenure, paymentMode: PaymentMode): Int = when (paymentMode) {
            PaymentMode.SINGLE_PREMIUM -> 1
            PaymentMode.ANNUAL -> policyTenure.years
            PaymentMode.HALF_YEARLY -> policyTenure.years * 2
            PaymentMode.QUARTERLY -> policyTenure.years * 4
            PaymentMode.MONTHLY -> policyTenure.years * 12
        }
    }
}

/**
 * COVER-AVAILABILITY row — `(version, planId, coverId)` flag that a cover is offered on a plan.
 * Backs [com.rate.core.rating.ports.RateDataProvider.getCoverAvailability]. Relocated from
 * `CoverAvailabilityTable` (plan_id, cover_id). Presence of the row == available.
 */
@Serializable
data class CoverAvailabilityRow(
    @SerialName("_id") val id: String,
    @SerialName("version") val version: String,
    @SerialName("planId") val planId: String,
    @SerialName("coverId") val coverId: String,
    @SerialName("available") val available: Boolean = true,
) {
    companion object {
        fun stableId(version: String, planId: String, coverId: String): String =
            listOf("avail", version, planId, coverId).joinToString(SEP)

        fun of(version: String, planId: String, coverId: String, available: Boolean = true): CoverAvailabilityRow =
            CoverAvailabilityRow(
                id = stableId(version, planId, coverId),
                version = version,
                planId = planId,
                coverId = coverId,
                available = available,
            )
    }
}

/**
 * A single import payload: ALL immutable rate rows for one [version], grouped by kind.
 * Produced by a (JVM-only, server-persistence) Excel importer and handed to
 * [com.rate.sdk.ingestion.repository.RateImportRepository] for a single transactional write.
 * Kept here (not in repository) so the pure-KMP layer can construct/validate a batch in tests.
 */
@Serializable
data class RateRowBatch(
    @SerialName("version") val version: String,
    @SerialName("baseRates") val baseRates: List<BaseRateRow> = emptyList(),
    @SerialName("coverRates") val coverRates: List<CoverRateRow> = emptyList(),
    @SerialName("memberLevelRates") val memberLevelRates: List<MemberLevelRow> = emptyList(),
    @SerialName("discountRates") val discountRates: List<DiscountRow> = emptyList(),
    @SerialName("instalmentConfig") val instalmentConfig: List<InstalmentRow> = emptyList(),
    @SerialName("coverAvailability") val coverAvailability: List<CoverAvailabilityRow> = emptyList(),
) {
    val totalRows: Int
        get() = baseRates.size + coverRates.size + memberLevelRates.size +
            discountRates.size + instalmentConfig.size + coverAvailability.size

    /** Per-kind counts for [com.rate.sdk.ingestion.model.ImportSummary.counts] / RateMeta. */
    fun counts(): Map<String, Int> = mapOf(
        "baseRates" to baseRates.size,
        "coverRates" to coverRates.size,
        "memberLevelRates" to memberLevelRates.size,
        "discountRates" to discountRates.size,
        "instalmentConfig" to instalmentConfig.size,
        "coverAvailability" to coverAvailability.size,
    )
}
