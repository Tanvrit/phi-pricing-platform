package com.rate.sdk.catalog.model.actuarial

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * An underwriting loading applied to an individual's premium for a declared condition / risk
 * (e.g. hypertension, diabetes, BMI band, hazardous occupation). The loading is a percentage uplift
 * on the rated premium, scoped to a product line (RETAIL or GROUP) and a stated basis.
 *
 * ProductLine is stored as a plain string (RETAIL/GROUP) so this master never imports another
 * domain's enum.
 */
@Serializable
data class IndividualLoadingFactor(
    @SerialName("_id") override val id: String = newId(),
    /** Operator-facing name (e.g. "Type-2 diabetes loading"). */
    @SerialName("name") val name: String,
    /** The condition / risk that triggers the loading. */
    @SerialName("condition") val condition: String = "",
    /** Premium uplift percentage (e.g. 25.0 = +25%). */
    @SerialName("loadingPct") val loadingPct: Double = 0.0,
    /** Basis the loading applies to ("Base premium", "Total premium", "Per member", …). */
    @SerialName("basis") val basis: String = "Base premium",
    /** Product line this loading applies to — RETAIL or GROUP. */
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
        fun defaults(): List<IndividualLoadingFactor> = listOf(
            IndividualLoadingFactor(
                name = "Hypertension (controlled)", condition = "Hypertension", loadingPct = 15.0,
                basis = "Base premium", productLine = "RETAIL",
            ),
            IndividualLoadingFactor(
                name = "Type-2 diabetes", condition = "Diabetes mellitus", loadingPct = 25.0,
                basis = "Base premium", productLine = "RETAIL",
            ),
            IndividualLoadingFactor(
                name = "High BMI (obesity grade I)", condition = "BMI 30-35", loadingPct = 10.0,
                basis = "Base premium", productLine = "RETAIL",
            ),
            IndividualLoadingFactor(
                name = "Tobacco use", condition = "Smoker / tobacco", loadingPct = 20.0,
                basis = "Base premium", productLine = "RETAIL",
            ),
            IndividualLoadingFactor(
                name = "Hazardous occupation", condition = "Mining / heavy industry", loadingPct = 30.0,
                basis = "Total premium", productLine = "GROUP",
            ),
            IndividualLoadingFactor(
                name = "Adverse claims experience", condition = "Group loss ratio > 100%", loadingPct = 35.0,
                basis = "Total premium", productLine = "GROUP",
            ),
        )
    }
}
