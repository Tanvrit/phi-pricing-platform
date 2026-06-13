package com.rate.sdk.catalog.model.demographics

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD premium payment-frequency master: how the annual premium is split into instalments
 * and the instalment loading that applies for the convenience of paying in parts.
 *
 * [installmentsPerYear] is the number of payments in a policy year (Single/Annual = 1, Monthly = 12);
 * [loadingRate] is the fractional uplift applied to the annual premium when paid at this frequency
 * (e.g. 0.03 = +3% for monthly). Single-pay typically carries a slight discount (negative loading).
 */
@Serializable
data class PaymentFrequency(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("code") val code: String,
    @SerialName("label") val label: String,
    @SerialName("installmentsPerYear") val installmentsPerYear: Int = 1,
    @SerialName("loadingRate") val loadingRate: Double = 0.0,
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
        fun defaults(): List<PaymentFrequency> = listOf(
            PaymentFrequency(code = "SINGLE", label = "Single", installmentsPerYear = 1, loadingRate = -0.02, sortOrder = 1),
            PaymentFrequency(code = "ANNUAL", label = "Annual", installmentsPerYear = 1, loadingRate = 0.0, sortOrder = 2),
            PaymentFrequency(code = "HALF_YEARLY", label = "Half-Yearly", installmentsPerYear = 2, loadingRate = 0.02, sortOrder = 3),
            PaymentFrequency(code = "QUARTERLY", label = "Quarterly", installmentsPerYear = 4, loadingRate = 0.03, sortOrder = 4),
            PaymentFrequency(code = "MONTHLY", label = "Monthly", installmentsPerYear = 12, loadingRate = 0.05, sortOrder = 5),
        )
    }
}
