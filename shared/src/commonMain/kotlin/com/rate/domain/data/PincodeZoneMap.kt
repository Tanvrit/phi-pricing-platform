package com.rate.domain.data

/**
 * Maps Indian pincode prefixes to insurance zones.
 *
 * Zone 1 — Tier 1 metros (most expensive): Delhi NCR, Mumbai, Bangalore,
 *           Chennai, Kolkata, Hyderabad, Pune, Ahmedabad
 * Zone 2 — Tier 2 cities: Jaipur, Lucknow, Surat, Nagpur, Indore, Patna, etc.
 * Zone 3 — Other urban / Tier 3 cities
 * Zone 4 — Semi-urban / rural / rest of India (cheapest)
 *
 * Source: Standard IRDAI zone classification used by PHI Basic / most health insurers.
 */
object PincodeZoneMap {

    /** Returns the zone label ("Zone 1"…"Zone 4") for a 6-digit Indian pincode. */
    fun zoneForPincode(pincode: String): String {
        val pin = pincode.trim()
        if (pin.length != 6 || !pin.all { it.isDigit() }) return "Zone 4"
        val p6  = pin.toInt()
        val p3  = pin.substring(0, 3).toInt()
        val p2  = pin.substring(0, 2).toInt()

        return when {
            // ── Zone 1: Tier-1 metros ──────────────────────────────────────
            // Delhi / NCR
            p3 in 110..110 -> "Zone 1"          // Delhi
            p3 in 120..122 -> "Zone 1"          // Gurgaon / Faridabad / Noida belt
            p3 == 124       -> "Zone 1"          // Gurugram
            p3 == 201       -> "Zone 1"          // Noida / Ghaziabad

            // Mumbai / MMR
            p3 in 400..401 -> "Zone 1"
            p3 in 410..421 -> "Zone 1"          // Navi Mumbai, Thane

            // Bangalore
            p3 in 560..562 -> "Zone 1"

            // Hyderabad / Secunderabad
            p3 in 500..502 -> "Zone 1"
            p3 == 503       -> "Zone 1"

            // Chennai
            p3 in 600..603 -> "Zone 1"

            // Kolkata
            p3 in 700..700 -> "Zone 1"
            p3 in 711..711 -> "Zone 1"

            // Pune
            p3 in 411..412 -> "Zone 1"

            // Ahmedabad
            p3 in 380..382 -> "Zone 1"

            // ── Zone 2: Tier-2 cities ──────────────────────────────────────
            // Jaipur
            p3 in 302..303 -> "Zone 2"
            // Lucknow
            p3 in 226..227 -> "Zone 2"
            // Surat
            p3 in 395..396 -> "Zone 2"
            // Kanpur
            p3 in 208..209 -> "Zone 2"
            // Nagpur
            p3 in 440..441 -> "Zone 2"
            // Indore
            p3 in 452..453 -> "Zone 2"
            // Bhopal
            p3 in 462..463 -> "Zone 2"
            // Patna
            p3 in 800..801 -> "Zone 2"
            // Chandigarh / Mohali
            p3 in 160..161 -> "Zone 2"
            // Coimbatore
            p3 in 641..642 -> "Zone 2"
            // Kochi
            p3 in 682..683 -> "Zone 2"
            // Visakhapatnam
            p3 in 530..531 -> "Zone 2"
            // Vadodara
            p3 in 390..391 -> "Zone 2"
            // Agra
            p3 in 282..283 -> "Zone 2"
            // Nashik
            p3 in 422..423 -> "Zone 2"
            // Meerut
            p3 in 250..251 -> "Zone 2"
            // Varanasi
            p3 in 221..222 -> "Zone 2"
            // Amritsar
            p3 in 143..144 -> "Zone 2"
            // Ludhiana
            p3 in 141..142 -> "Zone 2"
            // Guwahati
            p3 in 781..782 -> "Zone 2"
            // Rajkot
            p3 == 360       -> "Zone 2"
            // Mangaluru
            p3 in 575..575 -> "Zone 2"
            // Thiruvananthapuram
            p3 in 695..695 -> "Zone 2"
            // Mysore
            p3 == 570       -> "Zone 2"
            // Madurai
            p3 in 625..626 -> "Zone 2"

            // ── Zone 3: Other urban / Tier-3 ──────────────────────────────
            // Broad ranges covering district HQs and smaller cities
            p2 in 11..13   -> "Zone 3"   // North: Haryana / UP outskirts
            p2 in 40..45   -> "Zone 3"   // Maharashtra non-metro
            p2 in 56..58   -> "Zone 3"   // Karnataka non-metro
            p2 in 60..64   -> "Zone 3"   // Tamil Nadu non-metro
            p2 in 50..51   -> "Zone 3"   // Telangana / AP non-metro
            p2 in 38..39   -> "Zone 3"   // Gujarat non-metro
            p2 in 30..31   -> "Zone 3"   // Rajasthan urban
            p2 in 22..25   -> "Zone 3"   // UP urban
            p2 in 46..47   -> "Zone 3"   // MP urban
            p2 in 16..17   -> "Zone 3"   // Punjab / Himachal
            p2 in 68..69   -> "Zone 3"   // Kerala non-metro
            p2 in 75..79   -> "Zone 3"   // Odisha / WB non-metro

            // ── Zone 4: Everything else ────────────────────────────────────
            else -> "Zone 4"
        }
    }

    /** City name hint for the detected zone (shown to user). */
    fun cityHintForPincode(pincode: String): String? {
        val pin = pincode.trim()
        if (pin.length != 6 || !pin.all { it.isDigit() }) return null
        val p3 = pin.substring(0, 3).toInt()
        return when {
            p3 in 110..110 -> "Delhi"
            p3 in 120..124 -> "NCR / Gurgaon"
            p3 == 201       -> "Noida"
            p3 in 400..421 -> "Mumbai / MMR"
            p3 in 560..562 -> "Bangalore"
            p3 in 500..503 -> "Hyderabad"
            p3 in 600..603 -> "Chennai"
            p3 in 700..711 -> "Kolkata"
            p3 in 411..412 -> "Pune"
            p3 in 380..382 -> "Ahmedabad"
            p3 in 302..303 -> "Jaipur"
            p3 in 226..227 -> "Lucknow"
            p3 in 395..396 -> "Surat"
            p3 in 440..441 -> "Nagpur"
            p3 in 452..453 -> "Indore"
            p3 in 462..463 -> "Bhopal"
            p3 in 800..801 -> "Patna"
            p3 in 160..161 -> "Chandigarh"
            p3 in 641..642 -> "Coimbatore"
            p3 in 682..683 -> "Kochi"
            p3 in 530..531 -> "Visakhapatnam"
            else            -> null
        }
    }
}
