package com.rate.persistence.rating

import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.rating.ports.model.Tenure
import com.rate.sdk.ingestion.model.rate.BaseRateRow
import com.rate.sdk.ingestion.model.rate.CoverAvailabilityRow
import com.rate.sdk.ingestion.model.rate.CoverRateRow
import com.rate.sdk.ingestion.model.rate.DiscountRow
import com.rate.sdk.ingestion.model.rate.InstalmentRow
import com.rate.sdk.ingestion.model.rate.MemberLevelRow
import com.rate.sdk.ingestion.repository.RateImportRepository
import com.rate.sdk.ingestion.repository.RateMetaRepository
import java.util.concurrent.atomic.AtomicReference

/**
 * An immutable in-RAM snapshot of ONE active rate-table version, plus the loader that builds
 * it from the Mongo rate-row collections.
 *
 * Why a snapshot: the rating engine does dozens of `RateDataProvider` lookups per quote (50+
 * covers × up to 5 years), so per-call DB round-trips would be unacceptable. The active
 * version's rows are loaded once (at boot, and again on activation) into maps keyed EXACTLY by
 * the engine's lookup tuples — the same composite keys the immutable rate rows index on — so
 * [MongoRateDataProvider] resolves a rate by a single map get with no allocation.
 *
 * Rows are versioned and immutable; swapping the active version replaces the whole [Snapshot]
 * atomically via an [AtomicReference], so an in-flight quote always sees a consistent table.
 */
class RateTableCache(
    private val rows: RateImportRepository,
    private val meta: RateMetaRepository,
    private val plans: List<Plan> = emptyList(),
) {
    private val ref = AtomicReference(Snapshot.EMPTY)

    /** The current snapshot (never null; [Snapshot.EMPTY] until [load] has run). */
    val snapshot: Snapshot get() = ref.get()

    /**
     * Load the ACTIVE version for [productLine] (or [explicitVersion] when supplied) into a new
     * snapshot and publish it atomically. Returns the loaded version (empty string if none).
     */
    suspend fun load(
        productLine: ProductLine = ProductLine.RETAIL,
        explicitVersion: String? = null,
        plansOverride: List<Plan>? = null,
    ): String {
        val version = explicitVersion
            ?: meta.getActive(productLine)?.version
            ?: run {
                ref.set(Snapshot.EMPTY.copy(plans = plansOverride ?: plans))
                return ""
            }

        val snap = Snapshot(
            version = version,
            base = rows.listBaseRates(version).associateBy { Keys.base(it) },
            cover = rows.listCoverRates(version).associateBy { Keys.cover(it) },
            member = rows.listMemberLevelRates(version).associateBy { Keys.member(it) },
            discount = rows.listDiscountRates(version).associateBy { Keys.discount(it) },
            instalment = rows.listInstalmentConfig(version).associateBy { Keys.instalment(it) },
            availability = rows.listCoverAvailability(version)
                .filter { it.available }
                .groupBy { it.planId }
                .mapValues { (_, rs) -> rs.map { it.coverId }.toSet() },
            plans = plansOverride ?: plans,
        )
        ref.set(snap)
        return version
    }

    /** Replace the in-memory plan list (e.g. after a Plan CRUD edit) without reloading rates. */
    fun updatePlans(plans: List<Plan>) {
        ref.set(ref.get().copy(plans = plans))
    }

    /**
     * Immutable rate-table snapshot. Maps are keyed by the engine's exact lookup tuples so a
     * provider call is a single map get. Lookups that need a nullable-segment fallback
     * (cover/member) are resolved by [MongoRateDataProvider] trying narrower→broader keys.
     */
    data class Snapshot(
        val version: String,
        val base: Map<String, BaseRateRow>,
        val cover: Map<String, CoverRateRow>,
        val member: Map<String, MemberLevelRow>,
        val discount: Map<String, DiscountRow>,
        val instalment: Map<String, InstalmentRow>,
        val availability: Map<String, Set<String>>,
        val plans: List<Plan>,
    ) {
        companion object {
            val EMPTY = Snapshot(
                version = "",
                base = emptyMap(),
                cover = emptyMap(),
                member = emptyMap(),
                discount = emptyMap(),
                instalment = emptyMap(),
                availability = emptyMap(),
                plans = emptyList(),
            )
        }
    }

    /**
     * Map-key builders. They mirror the rate rows' own composite-id segmenting (`seg(null)="_"`)
     * but DROP the version prefix (the snapshot is already one version), so the provider can
     * build the same key from its call arguments.
     */
    object Keys {
        private const val SEP = "|"
        private fun seg(v: String?): String = v?.takeIf { it.isNotBlank() } ?: "_"
        private fun seg(v: Int?): String = v?.toString() ?: "_"
        private fun seg(v: Long?): String = v?.toString() ?: "_"

        fun base(r: BaseRateRow): String = base(r.planId, r.familyType, r.zone, r.ageBandMin, r.sumInsured)
        fun base(planId: String, familyType: String, zone: String, ageBandMin: Int, sumInsured: Long): String =
            listOf(planId, familyType, zone, ageBandMin.toString(), sumInsured.toString()).joinToString(SEP)

        fun cover(r: CoverRateRow): String =
            cover(r.coverId, r.param1, r.param2, r.ageBandMin, r.sumInsured, r.planOrTenureKey)
        fun cover(
            coverId: String, param1: String?, param2: String?,
            ageBandMin: Int?, sumInsured: Long?, planOrTenureKey: String?,
        ): String = listOf(
            coverId, seg(param1), seg(param2), seg(ageBandMin), seg(sumInsured), seg(planOrTenureKey),
        ).joinToString(SEP)

        fun member(r: MemberLevelRow): String = member(r.coverId, r.ageBandMin, r.param1, r.sumInsured)
        fun member(coverId: String, ageBandMin: Int?, param1: String?, sumInsured: Long?): String =
            listOf(coverId, seg(ageBandMin), seg(param1), seg(sumInsured)).joinToString(SEP)

        fun discount(r: DiscountRow): String = discount(r.discountId, r.paramKey)
        fun discount(discountId: String, paramKey: String?): String =
            listOf(discountId, seg(paramKey)).joinToString(SEP)

        fun instalment(r: InstalmentRow): String =
            instalment(r.policyTenure, r.paymentTenure, r.paymentMode)
        fun instalment(policyTenure: Tenure, paymentTenure: Tenure, paymentMode: PaymentMode): String =
            listOf(policyTenure.name, paymentTenure.name, paymentMode.name).joinToString(SEP)
    }
}
