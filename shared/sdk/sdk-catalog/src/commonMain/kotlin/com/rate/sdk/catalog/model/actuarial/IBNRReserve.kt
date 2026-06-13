package com.rate.sdk.catalog.model.actuarial

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.money.Money
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** The reserving method used to estimate an [IBNRReserve]. */
@Serializable
enum class IBNRMethod { CHAIN_LADDER, BORNHUETTER_FERGUSON, EXPECTED_LOSS_RATIO }

/**
 * An Incurred-But-Not-Reported (IBNR) reserve estimate — claims that have occurred but not yet been
 * reported/settled, held back from the books. Captured per valuation period with the actuarial
 * method, the reserve percentage of earned premium, and the resulting held amount.
 */
@Serializable
data class IBNRReserve(
    @SerialName("_id") override val id: String = newId(),
    /** Operator-facing name (e.g. "FY2024-25 GMC IBNR"). */
    @SerialName("name") val name: String,
    /** Reserving method applied. */
    @SerialName("method") val method: IBNRMethod = IBNRMethod.CHAIN_LADDER,
    /** Reserve as a percentage of earned premium (e.g. 8.5 = 8.5%). */
    @SerialName("reservePct") val reservePct: Double = 0.0,
    /** Valuation period label (e.g. "FY2024-25", "Q3 FY2024-25"). */
    @SerialName("period") val period: String = "",
    /** The reserve amount held. */
    @SerialName("amount") val amount: Money = Money.ZERO,
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
        fun defaults(): List<IBNRReserve> = listOf(
            IBNRReserve(
                name = "FY2023-24 GMC IBNR", method = IBNRMethod.CHAIN_LADDER, reservePct = 7.5,
                period = "FY2023-24", amount = Money.fromRupees(12_500_000L), active = false,
            ),
            IBNRReserve(
                name = "FY2024-25 GMC IBNR", method = IBNRMethod.CHAIN_LADDER, reservePct = 8.5,
                period = "FY2024-25", amount = Money.fromRupees(18_750_000L),
            ),
            IBNRReserve(
                name = "FY2024-25 GPA IBNR", method = IBNRMethod.BORNHUETTER_FERGUSON, reservePct = 5.0,
                period = "FY2024-25", amount = Money.fromRupees(4_200_000L),
            ),
            IBNRReserve(
                name = "FY2024-25 Retail health IBNR", method = IBNRMethod.EXPECTED_LOSS_RATIO, reservePct = 6.0,
                period = "FY2024-25", amount = Money.fromRupees(9_300_000L),
            ),
            IBNRReserve(
                name = "Q3 FY2025-26 GMC IBNR (interim)", method = IBNRMethod.BORNHUETTER_FERGUSON, reservePct = 8.0,
                period = "Q3 FY2025-26", amount = Money.fromRupees(6_100_000L),
            ),
        )
    }
}
