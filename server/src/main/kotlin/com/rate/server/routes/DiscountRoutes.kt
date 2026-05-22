package com.rate.server.routes

import com.rate.domain.data.CoverCatalog
import com.rate.domain.data.CoverMeta
import com.rate.domain.repository.RateDataProvider
import io.ktor.server.response.*
import io.ktor.server.routing.*
import kotlinx.serialization.Serializable

/**
 * Public read-only surface that exposes the discount catalogue with live
 * rates resolved by the server's [RateDataProvider]. Powers the Aegis
 * BUSINESS-shell Discounts surface.
 */
@Serializable
data class DiscountListItem(
    val id: String,
    val name: String,
    val description: String,
    val params: List<DiscountParamOption>,
    /** Headline rate for the no-param case (or 0.0 if every variant requires a param). */
    val defaultRate: Double
)

@Serializable
data class DiscountParamOption(
    val paramName: String,    // e.g. "CIBIL Score band" or "Members"
    val paramKey: String,     // value sent to engine, e.g. "701-750" or "2-3 members"
    val rate: Double          // decimal, e.g. 0.025 for 2.5%
)

fun Route.discountRoutes(rateProvider: RateDataProvider) {
    get("/api/discounts") {
        val items = CoverCatalog.DISCOUNTS.map { meta -> meta.toDiscountListItem(rateProvider) }
        call.respond(items)
    }
}

private suspend fun CoverMeta.toDiscountListItem(rateProvider: RateDataProvider): DiscountListItem {
    val defaultRate = runCatching { rateProvider.getDiscountRate(id, null) }.getOrDefault(0.0)
    val paramDef = param1
    val params: List<DiscountParamOption> = if (paramDef == null) {
        emptyList()
    } else {
        paramDef.options.map { opt ->
            val rate = runCatching { rateProvider.getDiscountRate(id, opt) }.getOrDefault(0.0)
            DiscountParamOption(paramName = paramDef.name, paramKey = opt, rate = rate)
        }
    }
    return DiscountListItem(
        id = id,
        name = name,
        description = description,
        params = params,
        defaultRate = defaultRate
    )
}
