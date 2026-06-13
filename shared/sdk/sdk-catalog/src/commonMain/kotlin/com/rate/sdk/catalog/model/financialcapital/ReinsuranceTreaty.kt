package com.rate.sdk.catalog.model.financialcapital

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Reinsurance arrangement structure (Dorian slide 10 — obligatory RI cession). */
@Serializable
enum class ReinsuranceType { QUOTA_SHARE, SURPLUS, EXCESS_OF_LOSS, OBLIGATORY, FACULTATIVE }

/**
 * A reinsurance treaty under which a share of risk is ceded to a reinsurer. The OBLIGATORY GIC Re
 * cession (statutory in India) is modelled here alongside proportional (quota-share / surplus) and
 * non-proportional (excess-of-loss) and one-off facultative covers.
 *
 * [reinsurerRef] points at an `insurers` master row (the reinsurer party) — stored as that entity's
 * id string, never as a typed cross-domain reference. [cededPct]/[commissionPct] are percentages
 * (0..100). [retentionLimit] is the retained amount above which the treaty engages.
 * [effectiveFrom]/[effectiveTo] are ISO "YYYY-MM-DD" date strings.
 */
@Serializable
data class ReinsuranceTreaty(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("reinsurerRef") val reinsurerRef: String = "",
    @SerialName("type") val type: ReinsuranceType = ReinsuranceType.QUOTA_SHARE,
    @SerialName("cededPct") val cededPct: Double = 0.0,
    @SerialName("retentionLimit") val retentionLimit: Money = Money.ZERO,
    @SerialName("commissionPct") val commissionPct: Double = 0.0,
    @SerialName("effectiveFrom") val effectiveFrom: String = "",
    @SerialName("effectiveTo") val effectiveTo: String = "",
    @SerialName("active") val active: Boolean = true,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity {
    companion object {
        fun defaults(): List<ReinsuranceTreaty> = listOf(
            ReinsuranceTreaty(
                name = "GIC Re Obligatory Cession",
                type = ReinsuranceType.OBLIGATORY,
                cededPct = 4.0,
                retentionLimit = Money.fromRupees(0L),
                commissionPct = 12.5,
                effectiveFrom = "2026-04-01",
                effectiveTo = "2027-03-31",
            ),
            ReinsuranceTreaty(
                name = "Group Health Quota Share",
                type = ReinsuranceType.QUOTA_SHARE,
                cededPct = 25.0,
                retentionLimit = Money.fromRupees(5_000_000L),
                commissionPct = 20.0,
                effectiveFrom = "2026-04-01",
                effectiveTo = "2027-03-31",
            ),
            ReinsuranceTreaty(
                name = "Surplus Treaty - 5 Lines",
                type = ReinsuranceType.SURPLUS,
                cededPct = 0.0,
                retentionLimit = Money.fromRupees(10_000_000L),
                commissionPct = 17.5,
                effectiveFrom = "2026-04-01",
                effectiveTo = "2027-03-31",
            ),
            ReinsuranceTreaty(
                name = "Catastrophe XoL Layer 1",
                type = ReinsuranceType.EXCESS_OF_LOSS,
                cededPct = 0.0,
                retentionLimit = Money.fromRupees(50_000_000L),
                commissionPct = 0.0,
                effectiveFrom = "2026-04-01",
                effectiveTo = "2027-03-31",
            ),
            ReinsuranceTreaty(
                name = "Large Group Facultative",
                type = ReinsuranceType.FACULTATIVE,
                cededPct = 50.0,
                retentionLimit = Money.fromRupees(25_000_000L),
                commissionPct = 15.0,
                effectiveFrom = "2026-04-01",
                effectiveTo = "2027-03-31",
            ),
        )
    }
}
