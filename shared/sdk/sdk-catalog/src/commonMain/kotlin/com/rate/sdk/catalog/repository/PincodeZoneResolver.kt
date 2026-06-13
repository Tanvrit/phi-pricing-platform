package com.rate.sdk.catalog.repository

import com.rate.core.regulatory.Zone
import com.rate.sdk.catalog.model.PincodeZone
import com.rate.sdk.catalog.seed.CatalogSeed

/** Resolved zone + optional city hint for a pincode. */
data class ZoneResolution(val zone: Zone, val cityHint: String? = null)

/**
 * Resolves a rating [Zone] from an Indian pincode. Relocated from the monolith's
 * `PincodeZoneMap.zoneForPincode` (hardcoded `when`) into a PORT so the resolution is backed
 * by admin-editable [PincodeZone] rows (server-persistence) while keeping a pure-KMP default
 * that reads the in-process seed table.
 */
interface PincodeZoneResolver {
    /** Resolve zone (defaults to [Zone.ZONE_4] for invalid/unknown pincodes — the cheapest). */
    suspend fun resolve(pincode: String): ZoneResolution
}

/**
 * Pure-KMP resolver over an in-memory list of [PincodeZone] rows. Matches the longest prefix
 * first (a 3-digit prefix beats a 2-digit one), preserving the original map's specificity.
 *
 * @param rows the zone rows (defaults to [CatalogSeed.pincodeZones], the relocated seed map).
 */
class DefaultPincodeZoneResolver(
    private val rows: List<PincodeZone> = CatalogSeed.pincodeZones(),
) : PincodeZoneResolver {

    // Pre-index by prefix-length, longest first, for deterministic longest-prefix match.
    private val byLengthDesc: List<Pair<Int, Map<String, PincodeZone>>> =
        rows.groupBy { it.prefixLength }
            .map { (len, group) -> len to group.associateBy { it.prefix } }
            .sortedByDescending { it.first }

    override suspend fun resolve(pincode: String): ZoneResolution {
        val pin = pincode.trim()
        if (pin.length != 6 || !pin.all { it.isDigit() }) return ZoneResolution(Zone.ZONE_4)
        for ((len, index) in byLengthDesc) {
            if (len > pin.length) continue
            val hit = index[pin.substring(0, len)]
            if (hit != null) return ZoneResolution(hit.zone, hit.cityHint)
        }
        return ZoneResolution(Zone.ZONE_4)
    }
}
