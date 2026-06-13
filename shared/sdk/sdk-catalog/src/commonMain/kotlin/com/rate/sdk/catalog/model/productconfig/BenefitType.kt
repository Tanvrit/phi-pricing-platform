package com.rate.sdk.catalog.model.productconfig

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** How a benefit's value is expressed and rated. */
@Serializable
enum class BenefitValueType {
    @SerialName("AMOUNT") AMOUNT,
    @SerialName("PERCENT") PERCENT,
    @SerialName("DAYS") DAYS,
    @SerialName("COUNT") COUNT,
    @SerialName("BOOLEAN") BOOLEAN,
}

/**
 * Admin-CRUD benefit-type master: the kind of value a benefit line carries (a rupee amount, a
 * percentage of SI, a number of days, a count, or a boolean flag). It drives how the limit field is
 * rendered and validated on category models and benefit schedules. [unit] is a display unit (e.g.
 * "days", "%", "INR"); [valueType] is the machine semantic.
 */
@Serializable
data class BenefitType(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("code") val code: String = "",
    @SerialName("name") val name: String = "",
    @SerialName("unit") val unit: String = "",
    @SerialName("valueType") val valueType: BenefitValueType = BenefitValueType.AMOUNT,
    @SerialName("description") val description: String = "",
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
        fun defaults(): List<BenefitType> = listOf(
            BenefitType(code = "ROOM_RENT", name = "Room Rent Limit", unit = "INR/day", valueType = BenefitValueType.AMOUNT, description = "Per-day room rent cap"),
            BenefitType(code = "SI_PERCENT", name = "Percentage of Sum Insured", unit = "%", valueType = BenefitValueType.PERCENT, description = "Limit expressed as a percentage of SI"),
            BenefitType(code = "WAITING_DAYS", name = "Waiting Period", unit = "days", valueType = BenefitValueType.DAYS, description = "Number of waiting-period days"),
            BenefitType(code = "OPD_VISITS", name = "OPD Visits", unit = "count", valueType = BenefitValueType.COUNT, description = "Number of covered OPD visits per year"),
            BenefitType(code = "COVERED_FLAG", name = "Covered Flag", unit = "", valueType = BenefitValueType.BOOLEAN, description = "Whether the benefit is covered"),
        )
    }
}
