package com.rate.sdk.catalog.model.actuarial

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** What a [ClaimThreshold] crossing signifies for downstream handling. */
@Serializable
enum class ClaimThresholdType { LARGE_CLAIM, CATASTROPHE, ALERT, REINSURANCE_TRIGGER }

/**
 * A monetary threshold that, when a claim crosses it, drives a defined action — flag a large claim
 * for review, declare a catastrophe accumulation, raise an alert, or trigger the reinsurance layer.
 * Used by claims monitoring and the reinsurance / cat-cover settlement logic.
 */
@Serializable
data class ClaimThreshold(
    @SerialName("_id") override val id: String = newId(),
    /** Operator-facing name (e.g. "Large claim review"). */
    @SerialName("name") val name: String,
    /** The claim amount at/above which this threshold fires. */
    @SerialName("thresholdAmount") val thresholdAmount: Money = Money.ZERO,
    /** Classification of the threshold. */
    @SerialName("type") val type: ClaimThresholdType = ClaimThresholdType.ALERT,
    /** Action taken when crossed (e.g. "Refer to actuary", "Notify reinsurer"). */
    @SerialName("action") val action: String = "",
    /** Display/severity order — lower sorts first. */
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
        fun defaults(): List<ClaimThreshold> = listOf(
            ClaimThreshold(
                name = "Claim alert", type = ClaimThresholdType.ALERT, action = "Notify claims supervisor",
                thresholdAmount = Money.fromRupees(200_000L), sortOrder = 10,
            ),
            ClaimThreshold(
                name = "Large claim review", type = ClaimThresholdType.LARGE_CLAIM, action = "Refer to underwriter",
                thresholdAmount = Money.fromRupees(1_000_000L), sortOrder = 20,
            ),
            ClaimThreshold(
                name = "Actuary referral", type = ClaimThresholdType.LARGE_CLAIM, action = "Refer to appointed actuary",
                thresholdAmount = Money.fromRupees(5_000_000L), sortOrder = 30,
            ),
            ClaimThreshold(
                name = "Reinsurance recovery", type = ClaimThresholdType.REINSURANCE_TRIGGER, action = "Notify reinsurer and book recovery",
                thresholdAmount = Money.fromRupees(10_000_000L), sortOrder = 40,
            ),
            ClaimThreshold(
                name = "Catastrophe accumulation", type = ClaimThresholdType.CATASTROPHE, action = "Invoke cat-cover protocol",
                thresholdAmount = Money.fromRupees(50_000_000L), sortOrder = 50,
            ),
        )
    }
}
