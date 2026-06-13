package com.rate.sdk.catalog.seed

import com.rate.core.rating.ports.model.CoverParam
import com.rate.core.rating.ports.model.ProductLine
import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.AddOnItem
import com.rate.sdk.catalog.model.Tenure

/**
 * Relocated `com.rate.domain.buyonline.BuyOnlinePlanMapping` — the customer-facing
 * Premier/Signature/Global tier bundles + tier→plan mapping + the curated BUYONLINE_ADDONS
 * list, converted into admin-CRUD [AddOn] seed entities. FALLBACK seed only.
 *
 * Original tier→plan: PREMIER→PHI_BASIC, SIGNATURE→PHI_FLAGSHIP1, GLOBAL→PHI_GLOBAL1.
 * Original pre-selection: Premier empty; Signature/Global ship the bundle pre-checked.
 */
internal object AddOnSeed {

    val addOns: List<AddOn> = listOf(
        AddOn(
            code = "premier",
            name = "PRU Premier",
            description = "Base tier — customer chooses add-ons.",
            productLine = ProductLine.RETAIL,
            planRef = "PHI_BASIC",
            preSelected = false,
            items = emptyList(),
            displayOrder = 0,
        ),
        AddOn(
            code = "signature",
            name = "PRU Signature",
            description = "Mid tier — maternity, OPD, pre/post-hosp & home-care bundled.",
            productLine = ProductLine.RETAIL,
            planRef = "PHI_FLAGSHIP1",
            preSelected = true,
            displayOrder = 1,
            items = listOf(
                AddOnItem("maternity_newborn", CoverParam("50000", "9 Months")),
                AddOnItem("cashless_opd", CoverParam("5000")),
                AddOnItem("pre_post_hosp"),
                AddOnItem("home_care"),
            ),
        ),
        AddOn(
            code = "global",
            name = "PRU Global",
            description = "Top tier — full bundle incl. air-ambulance, second-opinion & global geo.",
            productLine = ProductLine.RETAIL,
            planRef = "PHI_GLOBAL1",
            preSelected = true,
            displayOrder = 2,
            items = listOf(
                AddOnItem("maternity_newborn", CoverParam("100000", "9 Months")),
                AddOnItem("cashless_opd", CoverParam("10000")),
                AddOnItem("pre_post_hosp"),
                AddOnItem("home_care"),
                AddOnItem("air_ambulance"),
                AddOnItem("second_opinion"),
                AddOnItem("enhanced_geo", CoverParam("Worldwide excl. USA & Canada")),
            ),
        ),
    )

    /**
     * Tenure config seed — relocated from the buy-online hardcoded discount steps
     * (1yr=0%, 2yr=7.5%, 3yr=10%, 4yr=12.5%, 5yr=15%).
     */
    val tenures: List<Tenure> = listOf(
        Tenure(years = 1, label = "1 Year", multiTenureDiscount = 0.0),
        Tenure(years = 2, label = "2 Years", multiTenureDiscount = 0.075),
        Tenure(years = 3, label = "3 Years", multiTenureDiscount = 0.10),
        Tenure(years = 4, label = "4 Years", multiTenureDiscount = 0.125),
        Tenure(years = 5, label = "5 Years", multiTenureDiscount = 0.15),
    )
}
