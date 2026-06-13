package com.rate.sdk.catalog.model.productconfig

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD category-model master: a reusable benefit template that binds a benefit [Category] to
 * the set of benefit types it can carry and a default limit. It is the building block product
 * configurators pick from when assembling a plan's benefit grid.
 *
 * [categoryRef] points at a `categories` row's id string; [benefitTypeRefs] is a list of
 * `benefit-types` row id strings (a MULTI_SELECT, stored as a comma-separated id list). Neither is a
 * typed cross-domain reference. [defaultLimit] is the default rupee limit applied when the model is
 * instantiated; [productLine] ("RETAIL"/"GROUP") scopes the model to a product line.
 */
@Serializable
data class CategoryModel(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String = "",
    @SerialName("categoryRef") val categoryRef: String = "",
    @SerialName("benefitTypeRefs") val benefitTypeRefs: List<String> = emptyList(),
    @SerialName("defaultLimit") val defaultLimit: Money = Money.ZERO,
    @SerialName("description") val description: String = "",
    @SerialName("productLine") val productLine: String = "RETAIL",
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
        fun defaults(): List<CategoryModel> = listOf(
            CategoryModel(
                name = "Standard Hospitalization",
                defaultLimit = Money.fromRupees(500_000L),
                description = "Base in-patient hospitalization model",
                productLine = "RETAIL",
            ),
            CategoryModel(
                name = "Group OPD Wallet",
                defaultLimit = Money.fromRupees(25_000L),
                description = "OPD wallet for group employees",
                productLine = "GROUP",
            ),
            CategoryModel(
                name = "Maternity Benefit",
                defaultLimit = Money.fromRupees(75_000L),
                description = "Normal and caesarean delivery cover",
                productLine = "GROUP",
            ),
        )
    }
}
