package com.rate.sdk.catalog.model.productconfig

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The kind of adaptive/dynamic pricing rule an adaptive category applies (Dorian slide 11). */
@Serializable
enum class AdaptiveRuleType {
    @SerialName("DISCOUNT") DISCOUNT,
    @SerialName("LOADING") LOADING,
    @SerialName("ELIGIBILITY") ELIGIBILITY,
    @SerialName("SUBLIMIT") SUBLIMIT,
    @SerialName("COPAY") COPAY,
}

/**
 * Admin-CRUD adaptive-category master: a dynamic-pricing rule category (Dorian slide 11) that
 * conditionally adjusts a quote. [ruleType] selects the lever (discount, loading, eligibility gate,
 * sublimit, or copay); [conditionExpr] is a free-text predicate evaluated by the rating engine
 * (e.g. "groupSize >= 500 && claimRatio < 0.6"); [adjustmentPct] is the percentage adjustment
 * applied when the condition holds; [priority] orders rules when several match (lower runs first).
 */
@Serializable
data class AdaptiveCategory(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("name") val name: String = "",
    @SerialName("ruleType") val ruleType: AdaptiveRuleType = AdaptiveRuleType.DISCOUNT,
    @SerialName("conditionExpr") val conditionExpr: String = "",
    @SerialName("adjustmentPct") val adjustmentPct: Double = 0.0,
    @SerialName("priority") val priority: Int = 0,
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
        fun defaults(): List<AdaptiveCategory> = listOf(
            AdaptiveCategory(
                name = "Large Group Discount",
                ruleType = AdaptiveRuleType.DISCOUNT,
                conditionExpr = "groupSize >= 500",
                adjustmentPct = -7.5,
                priority = 10,
            ),
            AdaptiveCategory(
                name = "High Claim Ratio Loading",
                ruleType = AdaptiveRuleType.LOADING,
                conditionExpr = "claimRatio > 1.0",
                adjustmentPct = 15.0,
                priority = 20,
            ),
            AdaptiveCategory(
                name = "Senior Eligibility Gate",
                ruleType = AdaptiveRuleType.ELIGIBILITY,
                conditionExpr = "memberAge > 80",
                adjustmentPct = 0.0,
                priority = 5,
            ),
            AdaptiveCategory(
                name = "Maternity Sublimit Cap",
                ruleType = AdaptiveRuleType.SUBLIMIT,
                conditionExpr = "benefit == 'MATERNITY'",
                adjustmentPct = 0.0,
                priority = 30,
            ),
            AdaptiveCategory(
                name = "Voluntary Copay",
                ruleType = AdaptiveRuleType.COPAY,
                conditionExpr = "memberAge >= 60",
                adjustmentPct = 10.0,
                priority = 40,
            ),
        )
    }
}
