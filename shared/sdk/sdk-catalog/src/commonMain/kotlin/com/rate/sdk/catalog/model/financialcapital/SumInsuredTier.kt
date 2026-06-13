package com.rate.sdk.catalog.model.financialcapital

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * The product line a sum-insured tier applies to. Kept local (a String-stable enum) so this
 * pure-KMP financial-capital model never depends on another domain's type — the engine's own
 * ProductLine is mirrored value-for-value (RETAIL / GROUP) by name.
 */
@Serializable
enum class SiProductLine { RETAIL, GROUP }

/**
 * A sum-insured band offered for a product line (Dorian slide 09). Each tier is a [minSi]..[maxSi]
 * rupee window the customer/broker can pick; rating, obligatory-RI retention and cost-of-capital
 * all key off the tier the policy lands in. Stored in paise via [Money]; edited in whole rupees.
 */
@Serializable
data class SumInsuredTier(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("label") val label: String,
    @SerialName("minSi") val minSi: Money = Money.ZERO,
    @SerialName("maxSi") val maxSi: Money = Money.ZERO,
    @SerialName("sortOrder") val sortOrder: Int = 0,
    @SerialName("productLine") val productLine: SiProductLine = SiProductLine.GROUP,
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
        /** Standard Indian group-health SI ladder: 1L through 1Cr. */
        fun defaults(): List<SumInsuredTier> = listOf(
            SumInsuredTier(label = "1 Lakh", minSi = Money.fromRupees(100_000L), maxSi = Money.fromRupees(100_000L), sortOrder = 10, productLine = SiProductLine.GROUP),
            SumInsuredTier(label = "2 Lakh", minSi = Money.fromRupees(200_000L), maxSi = Money.fromRupees(200_000L), sortOrder = 20, productLine = SiProductLine.GROUP),
            SumInsuredTier(label = "3 Lakh", minSi = Money.fromRupees(300_000L), maxSi = Money.fromRupees(300_000L), sortOrder = 30, productLine = SiProductLine.GROUP),
            SumInsuredTier(label = "5 Lakh", minSi = Money.fromRupees(500_000L), maxSi = Money.fromRupees(500_000L), sortOrder = 40, productLine = SiProductLine.GROUP),
            SumInsuredTier(label = "10 Lakh", minSi = Money.fromRupees(1_000_000L), maxSi = Money.fromRupees(1_000_000L), sortOrder = 50, productLine = SiProductLine.RETAIL),
            SumInsuredTier(label = "25 Lakh", minSi = Money.fromRupees(2_500_000L), maxSi = Money.fromRupees(2_500_000L), sortOrder = 60, productLine = SiProductLine.RETAIL),
            SumInsuredTier(label = "50 Lakh", minSi = Money.fromRupees(5_000_000L), maxSi = Money.fromRupees(5_000_000L), sortOrder = 70, productLine = SiProductLine.RETAIL),
            SumInsuredTier(label = "1 Crore", minSi = Money.fromRupees(10_000_000L), maxSi = Money.fromRupees(10_000_000L), sortOrder = 80, productLine = SiProductLine.RETAIL),
        )
    }
}
