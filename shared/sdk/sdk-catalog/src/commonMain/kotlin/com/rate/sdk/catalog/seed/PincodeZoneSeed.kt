package com.rate.sdk.catalog.seed

import com.rate.core.regulatory.Zone
import com.rate.sdk.catalog.model.PincodeZone

/**
 * Relocated `com.rate.domain.data.PincodeZoneMap` → flat admin-CRUD [PincodeZone] seed rows.
 *
 * The original `when` over 3-digit / 2-digit pincode prefixes becomes one row per prefix; the
 * longest-prefix-wins ordering is enforced at resolve time by [DefaultPincodeZoneResolver]
 * (3-digit beats 2-digit). City hints are carried where the original supplied one. This is the
 * FALLBACK seed only — actual rows are loaded from Mongo/CSV later.
 */
internal object PincodeZoneSeed {

    private fun z3(prefix: String, zone: Zone, city: String? = null) =
        PincodeZone(prefix = prefix, prefixLength = 3, zone = zone, cityHint = city)

    private fun z2(prefix: String, zone: Zone) =
        PincodeZone(prefix = prefix, prefixLength = 2, zone = zone)

    /** Expand an inclusive 3-digit numeric range into one row per prefix. */
    private fun range3(from: Int, to: Int, zone: Zone, city: String? = null): List<PincodeZone> =
        (from..to).map { z3(it.toString().padStart(3, '0'), zone, city) }

    val rows: List<PincodeZone> = buildList {
        // ── Zone 1: Tier-1 metros ────────────────────────────────────────────
        add(z3("110", Zone.ZONE_1, "Delhi"))
        addAll(range3(120, 122, Zone.ZONE_1, "NCR / Gurgaon"))
        add(z3("124", Zone.ZONE_1, "Gurugram"))
        add(z3("201", Zone.ZONE_1, "Noida"))
        addAll(range3(400, 401, Zone.ZONE_1, "Mumbai / MMR"))
        addAll(range3(410, 421, Zone.ZONE_1, "Mumbai / MMR"))   // Navi Mumbai, Thane
        addAll(range3(560, 562, Zone.ZONE_1, "Bangalore"))
        addAll(range3(500, 502, Zone.ZONE_1, "Hyderabad"))
        add(z3("503", Zone.ZONE_1, "Hyderabad"))
        addAll(range3(600, 603, Zone.ZONE_1, "Chennai"))
        add(z3("700", Zone.ZONE_1, "Kolkata"))
        add(z3("711", Zone.ZONE_1, "Kolkata"))
        addAll(range3(411, 412, Zone.ZONE_1, "Pune"))
        addAll(range3(380, 382, Zone.ZONE_1, "Ahmedabad"))

        // ── Zone 2: Tier-2 cities ────────────────────────────────────────────
        addAll(range3(302, 303, Zone.ZONE_2, "Jaipur"))
        addAll(range3(226, 227, Zone.ZONE_2, "Lucknow"))
        addAll(range3(395, 396, Zone.ZONE_2, "Surat"))
        addAll(range3(208, 209, Zone.ZONE_2, "Kanpur"))
        addAll(range3(440, 441, Zone.ZONE_2, "Nagpur"))
        addAll(range3(452, 453, Zone.ZONE_2, "Indore"))
        addAll(range3(462, 463, Zone.ZONE_2, "Bhopal"))
        addAll(range3(800, 801, Zone.ZONE_2, "Patna"))
        addAll(range3(160, 161, Zone.ZONE_2, "Chandigarh"))
        addAll(range3(641, 642, Zone.ZONE_2, "Coimbatore"))
        addAll(range3(682, 683, Zone.ZONE_2, "Kochi"))
        addAll(range3(530, 531, Zone.ZONE_2, "Visakhapatnam"))
        addAll(range3(390, 391, Zone.ZONE_2, "Vadodara"))
        addAll(range3(282, 283, Zone.ZONE_2, "Agra"))
        addAll(range3(422, 423, Zone.ZONE_2, "Nashik"))
        addAll(range3(250, 251, Zone.ZONE_2, "Meerut"))
        addAll(range3(221, 222, Zone.ZONE_2, "Varanasi"))
        addAll(range3(143, 144, Zone.ZONE_2, "Amritsar"))
        addAll(range3(141, 142, Zone.ZONE_2, "Ludhiana"))
        addAll(range3(781, 782, Zone.ZONE_2, "Guwahati"))
        add(z3("360", Zone.ZONE_2, "Rajkot"))
        add(z3("575", Zone.ZONE_2, "Mangaluru"))
        add(z3("695", Zone.ZONE_2, "Thiruvananthapuram"))
        add(z3("570", Zone.ZONE_2, "Mysore"))
        addAll(range3(625, 626, Zone.ZONE_2, "Madurai"))

        // ── Zone 3: other urban / Tier-3 (2-digit prefixes) ──────────────────
        addAll(range2(11, 13, Zone.ZONE_3))   // North: Haryana / UP outskirts
        addAll(range2(40, 45, Zone.ZONE_3))   // Maharashtra non-metro
        addAll(range2(56, 58, Zone.ZONE_3))   // Karnataka non-metro
        addAll(range2(60, 64, Zone.ZONE_3))   // Tamil Nadu non-metro
        addAll(range2(50, 51, Zone.ZONE_3))   // Telangana / AP non-metro
        addAll(range2(38, 39, Zone.ZONE_3))   // Gujarat non-metro
        addAll(range2(30, 31, Zone.ZONE_3))   // Rajasthan urban
        addAll(range2(22, 25, Zone.ZONE_3))   // UP urban
        addAll(range2(46, 47, Zone.ZONE_3))   // MP urban
        addAll(range2(16, 17, Zone.ZONE_3))   // Punjab / Himachal
        addAll(range2(68, 69, Zone.ZONE_3))   // Kerala non-metro
        addAll(range2(75, 79, Zone.ZONE_3))   // Odisha / WB non-metro
        // Everything else → Zone 4 (the resolver's default; no rows needed).
    }

    private fun range2(from: Int, to: Int, zone: Zone): List<PincodeZone> =
        (from..to).map { z2(it.toString().padStart(2, '0'), zone) }
}
