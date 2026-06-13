package com.rate.sdk.catalog.model

import com.rate.core.base.id.newId
import com.rate.core.base.model.ConfigEntity
import com.rate.core.base.model.EntityStatus
import com.rate.core.base.time.Now
import com.rate.core.regulatory.Zone
import kotlinx.datetime.Instant
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Maps a pincode prefix to a rating [Zone]. Relocated from the monolith's `PincodeZoneMap`
 * (hardcoded `when` over 3-digit / 2-digit prefixes) into admin-CRUD rows so the zone map
 * is editable without a code change.
 *
 * [prefix] is matched longest-first by [com.rate.sdk.catalog.repository.PincodeZoneResolver].
 * A 3-digit prefix beats a 2-digit one; [prefixLength] is the number of leading pincode
 * digits this row matches (3 or 2). [cityHint] is the user-facing city label.
 */
@Serializable
data class PincodeZone(
    @SerialName("_id") override val id: String = newId(),
    /** Leading-digit prefix of the pincode (e.g. "110", "40"). */
    @SerialName("prefix") val prefix: String,
    @SerialName("prefixLength") val prefixLength: Int = prefix.length,
    @SerialName("zone") val zone: Zone,
    @SerialName("cityHint") val cityHint: String? = null,
    // ── ConfigEntity envelope ──────────────────────────────────────────────
    @SerialName("createdAt") override val createdAt: Instant = Now.instant(),
    @SerialName("updatedAt") override val updatedAt: Instant = Now.instant(),
    @SerialName("v") override val v: Long = 1,
    @SerialName("isDeleted") override val isDeleted: Boolean = false,
    @SerialName("status") override val status: EntityStatus = EntityStatus.PUBLISHED,
    @SerialName("draftOf") override val draftOf: String? = null,
    @SerialName("createdBy") override val createdBy: String? = null,
    @SerialName("updatedBy") override val updatedBy: String? = null,
) : ConfigEntity
