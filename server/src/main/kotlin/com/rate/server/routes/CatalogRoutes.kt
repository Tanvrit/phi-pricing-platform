package com.rate.server.routes

import com.rate.core.regulatory.AGE_BANDS
import com.rate.core.regulatory.FAMILY_TYPES
import com.rate.sdk.catalog.handler.CatalogHandler
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.get
import io.ktor.server.routing.route
import kotlinx.serialization.Serializable

/**
 * Reference-data + pincode-zone read surface. RELOCATED from the monolith's `CoverRoutes`
 * reference endpoints (age-bands / family-types / sum-insureds were hand-built JSON there). The
 * admin-managed config entities (plan/cover/section/tenure/zone/addon/…) are served instead by the
 * generic [adminCrudRoutes] factory, so this file is just the regulatory reference + zone lookup.
 *
 *   GET /api/catalog/age-bands       — regulatory age bands
 *   GET /api/catalog/family-types    — regulatory family-type definitions
 *   GET /api/catalog/zone?pincode=   — resolve a pincode to its rating zone
 */
@Serializable
internal data class FamilyTypeDto(
    val code: String,
    val adultCount: Int,
    val childCount: Int,
    val totalMembers: Int,
    val isFloater: Boolean,
)

@Serializable
internal data class ZoneResolutionDto(val pincode: String, val zone: String)

fun Route.catalogRoutes(catalog: CatalogHandler) {
    route("/api/catalog") {
        get("/age-bands") { call.respond(AGE_BANDS) }

        get("/family-types") {
            call.respond(
                FAMILY_TYPES.map {
                    FamilyTypeDto(
                        code = it.code,
                        adultCount = it.adultCount,
                        childCount = it.childCount,
                        totalMembers = it.totalMembers,
                        isFloater = it.isFloater,
                    )
                },
            )
        }

        get("/zone") {
            val pincode = call.request.queryParameters["pincode"]
                ?: throw IllegalArgumentException("Missing 'pincode' query parameter")
            val zone = catalog.zoneFor(pincode)
            call.respond(ZoneResolutionDto(pincode = pincode, zone = zone.name))
        }
    }
}
