package com.rate.sdk.catalog.model.rfq

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Admin-CRUD experience-rating master: the burning-cost / loss-experience record attached to an RFQ
 * ([rfqRef]) for a defined past [periodLabel]. It holds the premium earned ([premiumCollected]) and
 * the claims experience ([claimsPaid] + [claimsOutstanding]) that yield a [lossRatio]; the
 * [credibilityFactor] weights how much of the group's own experience is trusted versus book rates,
 * and [recommendedLoadingPct] is the actuary's suggested loading/discount on the renewal price.
 */
@Serializable
data class ExperienceRating(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("rfqRef") val rfqRef: String = "",
    @SerialName("periodLabel") val periodLabel: String,
    @SerialName("premiumCollected") val premiumCollected: Money = Money.ZERO,
    @SerialName("claimsPaid") val claimsPaid: Money = Money.ZERO,
    @SerialName("claimsOutstanding") val claimsOutstanding: Money = Money.ZERO,
    @SerialName("lossRatio") val lossRatio: Double = 0.0,
    @SerialName("credibilityFactor") val credibilityFactor: Double = 0.0,
    @SerialName("recommendedLoadingPct") val recommendedLoadingPct: Double = 0.0,
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
        fun defaults(): List<ExperienceRating> = listOf(
            ExperienceRating(
                periodLabel = "FY 2024-25",
                premiumCollected = Money.fromRupees(12_500_000L),
                claimsPaid = Money.fromRupees(9_800_000L),
                claimsOutstanding = Money.fromRupees(1_200_000L),
                lossRatio = 0.88,
                credibilityFactor = 0.6,
                recommendedLoadingPct = 12.5,
            ),
            ExperienceRating(
                periodLabel = "FY 2023-24",
                premiumCollected = Money.fromRupees(11_000_000L),
                claimsPaid = Money.fromRupees(7_400_000L),
                claimsOutstanding = Money.fromRupees(600_000L),
                lossRatio = 0.73,
                credibilityFactor = 0.5,
                recommendedLoadingPct = 5.0,
            ),
        )
    }
}
