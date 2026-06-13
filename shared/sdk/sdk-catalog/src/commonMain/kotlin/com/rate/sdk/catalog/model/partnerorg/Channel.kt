package com.rate.sdk.catalog.model.partnerorg

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/** Distribution mechanism a [Channel] represents. */
@Serializable
enum class ChannelType { DIRECT, BROKER, BANCASSURANCE, AGENCY, ONLINE, POSP }

/**
 * A distribution channel through which group-insurance business is sourced.
 *
 * Each channel carries a commission-cap policy (the regulatory/commercial ceiling on payout for
 * intermediaries placing through it) used to validate intermediary commission setups. Channels are
 * referenced by [Intermediary.channelRef].
 */
@Serializable
data class Channel(
    @SerialName("_id") override val id: String = newId(),
    /** Short stable code (uppercase) used in commission schedules and reports. */
    @SerialName("code") val code: String,
    /** Human-readable channel name. */
    @SerialName("name") val name: String = "",
    /** Distribution mechanism. */
    @SerialName("type") val type: ChannelType = ChannelType.DIRECT,
    /** Maximum allowable commission percentage payable through this channel. */
    @SerialName("commissionCapPct") val commissionCapPct: Double = 0.0,
    /** Default brokerage/commission percentage applied when an intermediary leaves it blank. */
    @SerialName("defaultCommissionPct") val defaultCommissionPct: Double = 0.0,
    /** Display ordering in pickers and reports (ascending). */
    @SerialName("sortOrder") val sortOrder: Int = 0,
    /** Whether new business can currently be sourced through this channel. */
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
        fun defaults(): List<Channel> = listOf(
            Channel(code = "DIRECT", name = "Direct Sales", type = ChannelType.DIRECT, commissionCapPct = 0.0, defaultCommissionPct = 0.0, sortOrder = 1),
            Channel(code = "BROKER", name = "Insurance Brokers", type = ChannelType.BROKER, commissionCapPct = 17.5, defaultCommissionPct = 10.0, sortOrder = 2),
            Channel(code = "BANCA", name = "Bancassurance", type = ChannelType.BANCASSURANCE, commissionCapPct = 15.0, defaultCommissionPct = 7.5, sortOrder = 3),
            Channel(code = "AGENCY", name = "Agency Force", type = ChannelType.AGENCY, commissionCapPct = 15.0, defaultCommissionPct = 12.5, sortOrder = 4),
            Channel(code = "ONLINE", name = "Online / D2C Portal", type = ChannelType.ONLINE, commissionCapPct = 0.0, defaultCommissionPct = 0.0, sortOrder = 5),
            Channel(code = "POSP", name = "Point of Sale Persons", type = ChannelType.POSP, commissionCapPct = 12.5, defaultCommissionPct = 8.0, sortOrder = 6),
        )
    }
}
