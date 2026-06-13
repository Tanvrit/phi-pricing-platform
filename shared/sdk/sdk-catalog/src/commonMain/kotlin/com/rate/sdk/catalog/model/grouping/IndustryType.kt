package com.rate.sdk.catalog.model.grouping

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Occupational/industry risk tier driving the base industry loading on group health premium. */
@Serializable
enum class IndustryRiskCategory { LOW, MEDIUM, HIGH, VERY_HIGH }

/**
 * Admin-CRUD master: the industry / occupation class of the group, classified to an NIC-like code
 * and a risk tier, with a base loading factor applied to premium. Hazardous sectors (mining,
 * construction) carry higher loadings; low-risk desk sectors (IT/ITES, BFSI) carry the lowest.
 *
 * [baseLoadingFactor] is a plain multiplier on base premium (1.0 = neutral).
 */
@Serializable
data class IndustryType(
    @SerialName("_id") override val id: String = newId(),
    /** NIC-like industry code (e.g. "620" for IT services, "41" for construction). */
    @SerialName("code") val code: String,
    @SerialName("name") val name: String,
    /** Broad sector grouping (e.g. "Services", "Manufacturing", "Primary"). */
    @SerialName("sector") val sector: String = "",
    @SerialName("riskCategory") val riskCategory: IndustryRiskCategory = IndustryRiskCategory.MEDIUM,
    /** Premium multiplier for groups in this industry (e.g. 1.35 = 35% industry load). */
    @SerialName("baseLoadingFactor") val baseLoadingFactor: Double = 1.0,
    @SerialName("sortOrder") val sortOrder: Int = 0,
    @SerialName("active") val active: Boolean = true,
    // ConfigEntity envelope (verbatim):
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
        fun defaults(): List<IndustryType> = listOf(
            IndustryType(code = "620", name = "IT / ITES", sector = "Services", riskCategory = IndustryRiskCategory.LOW, baseLoadingFactor = 0.95, sortOrder = 1),
            IndustryType(code = "640", name = "BFSI", sector = "Services", riskCategory = IndustryRiskCategory.LOW, baseLoadingFactor = 0.98, sortOrder = 2),
            IndustryType(code = "850", name = "Education", sector = "Services", riskCategory = IndustryRiskCategory.LOW, baseLoadingFactor = 1.00, sortOrder = 3),
            IndustryType(code = "470", name = "Retail Trade", sector = "Services", riskCategory = IndustryRiskCategory.MEDIUM, baseLoadingFactor = 1.05, sortOrder = 4),
            IndustryType(code = "550", name = "Hospitality", sector = "Services", riskCategory = IndustryRiskCategory.MEDIUM, baseLoadingFactor = 1.10, sortOrder = 5),
            IndustryType(code = "860", name = "Healthcare", sector = "Services", riskCategory = IndustryRiskCategory.MEDIUM, baseLoadingFactor = 1.12, sortOrder = 6),
            IndustryType(code = "490", name = "Logistics & Transport", sector = "Services", riskCategory = IndustryRiskCategory.HIGH, baseLoadingFactor = 1.20, sortOrder = 7),
            IndustryType(code = "100", name = "Manufacturing", sector = "Manufacturing", riskCategory = IndustryRiskCategory.HIGH, baseLoadingFactor = 1.25, sortOrder = 8),
            IndustryType(code = "410", name = "Construction", sector = "Manufacturing", riskCategory = IndustryRiskCategory.VERY_HIGH, baseLoadingFactor = 1.45, sortOrder = 9),
            IndustryType(code = "050", name = "Mining & Quarrying", sector = "Primary", riskCategory = IndustryRiskCategory.VERY_HIGH, baseLoadingFactor = 1.60, sortOrder = 10),
        )
    }
}
