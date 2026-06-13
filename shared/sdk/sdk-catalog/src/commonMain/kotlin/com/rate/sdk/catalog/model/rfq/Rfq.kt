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
 * Lifecycle state of an RFQ (Request For Quote) as it moves through the SALES pipeline: a broker/
 * client submits the enquiry, the team puts an indicative estimate on it, firms it into a
 * consolidated price, wins (and manages) the account, or it closes/loses. Stored as a stable
 * String token so the funnel stays comparable across product lines and over time.
 */
@Serializable
enum class RfqStatus {
    @SerialName("SUBMITTED") SUBMITTED,
    @SerialName("ESTIMATED_PRICE") ESTIMATED_PRICE,
    @SerialName("CONSOLIDATED_PRICE") CONSOLIDATED_PRICE,
    @SerialName("MANAGED") MANAGED,
    @SerialName("CLOSED") CLOSED,
    @SerialName("LOST") LOST,
}

/**
 * Admin-CRUD RFQ master: a group-insurance Request For Quote — the top-of-funnel sales record for
 * a prospective client account. It captures who is asking ([clientName]), the kind/industry of the
 * group (via [groupTypeRef]/[industryRef] picks), the headcount ([lives]) and desired cover
 * ([requestedSumInsured]), and the channel/intermediary that sourced it ([channelRef]/
 * [intermediaryRef]). The [status] drives the SALES pipeline board; downstream pricing
 * (estimated -> consolidated) keys off the same record.
 */
@Serializable
data class Rfq(
    @SerialName("_id") override val id: String = newId(),
    @SerialName("rfqNumber") val rfqNumber: String,
    @SerialName("clientName") val clientName: String,
    @SerialName("groupTypeRef") val groupTypeRef: String = "",
    @SerialName("industryRef") val industryRef: String = "",
    @SerialName("lives") val lives: Int = 0,
    @SerialName("rfqStatus") val rfqStatus: RfqStatus = RfqStatus.SUBMITTED,
    @SerialName("requestedSumInsured") val requestedSumInsured: Money = Money.ZERO,
    @SerialName("effectiveDate") val effectiveDate: String = "",
    @SerialName("channelRef") val channelRef: String = "",
    @SerialName("intermediaryRef") val intermediaryRef: String = "",
    @SerialName("notes") val notes: String = "",
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
        fun defaults(): List<Rfq> = listOf(
            Rfq(
                rfqNumber = "RFQ-2026-0001",
                clientName = "Aarav Technologies Pvt Ltd",
                lives = 240,
                rfqStatus = RfqStatus.SUBMITTED,
                requestedSumInsured = Money.fromRupees(500_000L),
                effectiveDate = "2026-07-01",
                notes = "New logo via broker; GMC for tech employees + dependents.",
            ),
            Rfq(
                rfqNumber = "RFQ-2026-0002",
                clientName = "Bharat Logistics Ltd",
                lives = 1_850,
                rfqStatus = RfqStatus.ESTIMATED_PRICE,
                requestedSumInsured = Money.fromRupees(300_000L),
                effectiveDate = "2026-08-15",
                notes = "Indicative price shared; awaiting census refresh.",
            ),
            Rfq(
                rfqNumber = "RFQ-2026-0003",
                clientName = "Chandra Hospitality Group",
                lives = 620,
                rfqStatus = RfqStatus.CONSOLIDATED_PRICE,
                requestedSumInsured = Money.fromRupees(1_000_000L),
                effectiveDate = "2026-09-01",
                notes = "Final price consolidated after experience review.",
            ),
        )
    }
}
