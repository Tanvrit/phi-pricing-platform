package com.rate.sdk.catalog.model.partnerlink

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * PARTNERS domain — partner-linkage masters that bind intermediaries, insurers and distribution
 * channels together, locate client sites for zone-based rating, classify the nature of each piece
 * of business, and record corporate relationships of a group client.
 *
 * Every type here is a metadata-driven [ConfigEntity]: pure-KMP, fully `@Serializable`, and
 * surfaced as an admin-CRUD vertical via its descriptor. Cross-entity references are stored as the
 * other entity's id string (resolved by the form's ENTITY_PICKER) so this module never imports a
 * sibling domain's type.
 */

// ─────────────────────────────────────────────────────────────────────────────
//  SalesIntermediaryMapping
// ─────────────────────────────────────────────────────────────────────────────

/** How an intermediary is remunerated on a given insurer/channel link. */
@Serializable
enum class RemunerationType { COMMISSION, BROKERAGE, REWARD, FEE }

/**
 * A relational mapping that ties a sales intermediary to an insurer and a distribution channel,
 * carrying the agreed commission/brokerage and its effective window. This is the join master the
 * pricing/commission engine reads to attribute new and renewal business.
 *
 * Relational by nature — ships with an empty [Companion.defaults] seed.
 */
@Serializable
data class SalesIntermediaryMapping(
    @SerialName("_id") override val id: String = newId(),
    /** The intermediary (agent/broker/corporate-agent) — id from the `intermediaries` master. */
    @SerialName("intermediaryRef") val intermediaryRef: String = "",
    /** The insurer the mapping applies to — id from the `insurers` master. */
    @SerialName("insurerRef") val insurerRef: String = "",
    /** The distribution channel — id from the `channels` master. */
    @SerialName("channelRef") val channelRef: String = "",
    /** Kind of payout the percentage represents. */
    @SerialName("remunerationType") val remunerationType: RemunerationType = RemunerationType.COMMISSION,
    /** Agreed payout as a percentage of premium (e.g. 7.5 = 7.5%). */
    @SerialName("commissionPct") val commissionPct: Double = 0.0,
    /** Optional fixed reference / agreement number for audit. */
    @SerialName("agreementNo") val agreementNo: String = "",
    /** ISO date YYYY-MM-DD the mapping becomes effective. */
    @SerialName("effectiveFrom") val effectiveFrom: String = "",
    /** ISO date YYYY-MM-DD the mapping ceases (blank = open-ended). */
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
        /** Relational join — seeded from real intermediary/insurer/channel ids, so empty here. */
        fun defaults(): List<SalesIntermediaryMapping> = emptyList()
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  ClientLocation
// ─────────────────────────────────────────────────────────────────────────────

/**
 * A physical client site / branch used to spread risk across geographies and to pin a location to
 * a rating zone. Drives zone-based loadings and the "lives by location" census split.
 */
@Serializable
data class ClientLocation(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("addressLine") val addressLine: String = "",
    @SerialName("city") val city: String = "",
    @SerialName("stateName") val stateName: String = "",
    @SerialName("pincode") val pincode: String = "",
    /** Rating zone code this site falls in (e.g. ZONE_A / ZONE_B / ZONE_C). */
    @SerialName("zoneCode") val zoneCode: String = "",
    /** Approximate headcount at this site — used for the census split. */
    @SerialName("livesCount") val livesCount: Int = 0,
    @SerialName("sortOrder") val sortOrder: Int = 0,
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
        fun defaults(): List<ClientLocation> = listOf(
            ClientLocation(name = "Head Office - Mumbai", addressLine = "Bandra Kurla Complex", city = "Mumbai", stateName = "Maharashtra", pincode = "400051", zoneCode = "ZONE_A", livesCount = 1200, sortOrder = 1),
            ClientLocation(name = "Corporate Office - Delhi", addressLine = "Connaught Place", city = "New Delhi", stateName = "Delhi", pincode = "110001", zoneCode = "ZONE_A", livesCount = 850, sortOrder = 2),
            ClientLocation(name = "Tech Park - Bengaluru", addressLine = "Outer Ring Road, Marathahalli", city = "Bengaluru", stateName = "Karnataka", pincode = "560037", zoneCode = "ZONE_A", livesCount = 2100, sortOrder = 3),
            ClientLocation(name = "Campus - Hyderabad", addressLine = "HITEC City, Madhapur", city = "Hyderabad", stateName = "Telangana", pincode = "500081", zoneCode = "ZONE_B", livesCount = 1500, sortOrder = 4),
            ClientLocation(name = "Branch - Pune", addressLine = "Hinjewadi Phase 2", city = "Pune", stateName = "Maharashtra", pincode = "411057", zoneCode = "ZONE_B", livesCount = 600, sortOrder = 5),
            ClientLocation(name = "Branch - Chennai", addressLine = "Tidel Park, Taramani", city = "Chennai", stateName = "Tamil Nadu", pincode = "600113", zoneCode = "ZONE_B", livesCount = 720, sortOrder = 6),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  BusinessType
// ─────────────────────────────────────────────────────────────────────────────

/**
 * Classifies the nature of a policy transaction (new business, renewal, portability, …). Drives
 * commission attribution, loading rules and MIS bucketing.
 */
@Serializable
data class BusinessType(
    @SerialName("_id") override val id: String = newId(),
    /** Short stable code used by downstream rules/MIS (e.g. NB, REN). */
    @SerialName("code") val code: String,
    @SerialName("label") val label: String,
    @SerialName("description") val description: String = "",
    @SerialName("sortOrder") val sortOrder: Int = 0,
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
        fun defaults(): List<BusinessType> = listOf(
            BusinessType(code = "NB", label = "New Business", description = "A fresh group policy with no prior coverage.", sortOrder = 1),
            BusinessType(code = "REN", label = "Renewal", description = "Continuation of an existing policy at term end.", sortOrder = 2),
            BusinessType(code = "PORT", label = "Portability", description = "Inbound port-in from another insurer with credit for continuity.", sortOrder = 3),
            BusinessType(code = "END", label = "Endorsement", description = "Mid-term change — additions, deletions or SI revision.", sortOrder = 4),
            BusinessType(code = "MIG", label = "Migration", description = "Move from one product/plan to another within the same insurer.", sortOrder = 5),
        )
    }
}

// ─────────────────────────────────────────────────────────────────────────────
//  RelatedParty
// ─────────────────────────────────────────────────────────────────────────────

/** Corporate relationship of a related party to the master group client. */
@Serializable
enum class RelationType { PROMOTER, SUBSIDIARY, HOLDING, ASSOCIATE, JOINT_VENTURE }

/** The kind of statutory identifier captured for a related party. */
@Serializable
enum class IdentifierType { CIN, PAN, GST, OTHER }

/**
 * A corporate entity related to the group client (promoter, subsidiary, holding company, …) whose
 * lives may be grouped under the master policy. Used for consolidated billing and KYC linkage.
 *
 * Client-specific — ships with an empty [Companion.defaults] seed.
 */
@Serializable
data class RelatedParty(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String,
    @SerialName("relationType") val relationType: RelationType = RelationType.SUBSIDIARY,
    @SerialName("identifierType") val identifierType: IdentifierType = IdentifierType.CIN,
    @SerialName("identifierNo") val identifierNo: String = "",
    /** Optional ownership/holding percentage (e.g. 51.0). */
    @SerialName("holdingPct") val holdingPct: Double = 0.0,
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
        /** Client-specific corporate tree — populated per onboarding, so empty here. */
        fun defaults(): List<RelatedParty> = emptyList()
    }
}
