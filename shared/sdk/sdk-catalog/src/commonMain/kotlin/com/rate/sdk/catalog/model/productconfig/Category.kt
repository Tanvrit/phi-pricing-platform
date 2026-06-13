package com.rate.sdk.catalog.model.productconfig

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD benefit-category master: the taxonomy that groups covers/benefits into a navigable
 * hierarchy (Hospitalization, OPD, Maternity, ...). Categories can nest via [parentRef] (a self
 * reference to another `categories` row's id string), letting the console render a tree and roll up
 * limits. [sortOrder] keeps siblings in natural order; [code] is the stable machine token.
 */
@Serializable
data class Category(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("code") val code: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("parentRef") val parentRef: String = "",
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
        fun defaults(): List<Category> = listOf(
            Category(code = "HOSPITALIZATION", name = "Hospitalization", description = "In-patient hospitalization benefits", sortOrder = 1),
            Category(code = "OPD", name = "OPD", description = "Out-patient department consultations and pharmacy", sortOrder = 2),
            Category(code = "MATERNITY", name = "Maternity", description = "Maternity and newborn cover", sortOrder = 3),
            Category(code = "WELLNESS", name = "Wellness", description = "Preventive health checks and wellness benefits", sortOrder = 4),
            Category(code = "CRITICAL_ILLNESS", name = "Critical Illness", description = "Lump-sum critical-illness benefits", sortOrder = 5),
            Category(code = "PERSONAL_ACCIDENT", name = "Personal Accident", description = "Accidental death and disablement cover", sortOrder = 6),
        )
    }
}
