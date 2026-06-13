package com.rate.sdk.catalog.network

import com.rate.core.regulatory.Zone
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.Product
import com.rate.sdk.catalog.model.Section
import com.rate.sdk.catalog.model.Tenure
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable

/**
 * Read-surface DTOs for the client. The entities themselves are already wire-serializable
 * (they ARE the JSON contract), so list responses reuse [com.rate.core.base.model.Page]; these
 * DTOs are only for composed read views that don't map 1:1 to one collection.
 */

/** A product with its sections and their covers eagerly resolved — the catalog-tree read view. */
@Serializable
data class ProductCatalogView(
    @SerialName("product") val product: Product,
    @SerialName("sections") val sections: List<SectionWithCovers> = emptyList(),
    @SerialName("addOns") val addOns: List<AddOn> = emptyList(),
    @SerialName("tenures") val tenures: List<Tenure> = emptyList(),
)

@Serializable
data class SectionWithCovers(
    @SerialName("section") val section: Section,
    @SerialName("covers") val covers: List<Cover> = emptyList(),
)

/** Zone-resolution response for the buy-online pincode step. */
@Serializable
data class ZoneLookupResponse(
    @SerialName("pincode") val pincode: String,
    @SerialName("zone") val zone: Zone,
    @SerialName("cityHint") val cityHint: String? = null,
)
