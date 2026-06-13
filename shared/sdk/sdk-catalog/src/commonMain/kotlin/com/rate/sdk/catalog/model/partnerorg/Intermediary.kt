package com.rate.sdk.catalog.model.partnerorg

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** IRDAI licence category an [Intermediary] holds. */
@Serializable
enum class IntermediaryType { BROKER, INDIVIDUAL_AGENT, CORPORATE_AGENT, POSP, WEB_AGGREGATOR }

/**
 * An IRDAI-licensed distribution intermediary (broker / agent / POSP / aggregator) that sources
 * group-insurance business. Each intermediary belongs to a [Channel] (via [channelRef], an id
 * string resolved through the ENTITY_PICKER) and carries a licence validity window so expired
 * licences can be flagged before placement.
 */
@Serializable
data class Intermediary(
    @SerialName("_id") override val id: String = newId(),
    /** Legal / trading name of the intermediary. */
    @SerialName("name") val name: String,
    /** IRDAI licence / registration number. */
    @SerialName("irdaiLicenseNo") val irdaiLicenseNo: String = "",
    /** Id of the [Channel] this intermediary places through (ENTITY_PICKER → "channels"). */
    @SerialName("channelRef") val channelRef: String = "",
    /** Licence category. */
    @SerialName("type") val type: IntermediaryType = IntermediaryType.BROKER,
    /** Negotiated commission percentage (must be within the channel commission cap). */
    @SerialName("commissionPct") val commissionPct: Double = 0.0,
    /** Contact email. */
    @SerialName("email") val email: String = "",
    /** Contact phone. */
    @SerialName("phone") val phone: String = "",
    /** Licence validity start — ISO date "YYYY-MM-DD". */
    @SerialName("validFrom") val validFrom: String = "",
    /** Licence validity end — ISO date "YYYY-MM-DD". */
    @SerialName("validTo") val validTo: String = "",
    /** Whether this intermediary may currently place new business. */
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
        // channelRef is left blank in seed rows — the integrator/admin links it to a seeded
        // Channel id after both collections are populated.
        fun defaults(): List<Intermediary> = listOf(
            Intermediary(
                name = "Marsh India Insurance Brokers",
                irdaiLicenseNo = "DB-228/05",
                type = IntermediaryType.BROKER,
                commissionPct = 12.5,
                email = "groupbenefits@marsh.com",
                phone = "022-6651-2900",
                validFrom = "2024-04-01",
                validTo = "2027-03-31",
            ),
            Intermediary(
                name = "Policybazaar Insurance Brokers",
                irdaiLicenseNo = "DB-694/19",
                type = IntermediaryType.WEB_AGGREGATOR,
                commissionPct = 8.0,
                email = "corporate@policybazaar.com",
                phone = "1800-208-8787",
                validFrom = "2023-07-01",
                validTo = "2026-06-30",
            ),
            Intermediary(
                name = "Aditya Birla Insurance Brokers",
                irdaiLicenseNo = "DB-046/03",
                type = IntermediaryType.BROKER,
                commissionPct = 10.0,
                email = "ebsupport@abibl.com",
                phone = "1800-270-7000",
                validFrom = "2024-01-01",
                validTo = "2026-12-31",
            ),
            Intermediary(
                name = "SBI General — Corporate Agency",
                irdaiLicenseNo = "CA-0078/14",
                type = IntermediaryType.CORPORATE_AGENT,
                commissionPct = 7.5,
                email = "corp.agency@sbigeneral.in",
                phone = "1800-102-1111",
                validFrom = "2024-04-01",
                validTo = "2027-03-31",
            ),
            Intermediary(
                name = "Ramesh Kulkarni (Individual Agent)",
                irdaiLicenseNo = "IA-553201",
                type = IntermediaryType.INDIVIDUAL_AGENT,
                commissionPct = 12.5,
                email = "ramesh.k.agent@gmail.com",
                phone = "98200-11223",
                validFrom = "2023-09-15",
                validTo = "2026-09-14",
            ),
        )
    }
}
