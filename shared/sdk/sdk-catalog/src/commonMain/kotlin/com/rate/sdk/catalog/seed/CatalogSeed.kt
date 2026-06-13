package com.rate.sdk.catalog.seed

import com.rate.sdk.catalog.model.AddOn
import com.rate.sdk.catalog.model.CIItem
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.model.CriticalIllnessList
import com.rate.sdk.catalog.model.PincodeZone
import com.rate.sdk.catalog.model.Tenure

/**
 * The single public entry point to the relocated FALLBACK seed data. The handler's
 * [com.rate.sdk.catalog.handler.CatalogHandler.seedDefaults] bulk-upserts these into the
 * repositories on first boot when a collection is empty; thereafter the admin-CRUD /
 * Mongo / CSV-import data is authoritative.
 *
 * All hardcoded tables from the old monolith now live here as seed values:
 * - covers ← CoverCatalog (display) + CoverDefinitions (RateKind + accumBase)
 * - pincodeZones ← PincodeZoneMap
 * - addOns / tenures ← BuyOnlinePlanMapping
 * - criticalIllnessLists ← a minimal tiered CI seed (full lists come from CSV import)
 */
object CatalogSeed {
    fun covers(): List<Cover> = CoverSeed.covers
    fun pincodeZones(): List<PincodeZone> = PincodeZoneSeed.rows
    fun addOns(): List<AddOn> = AddOnSeed.addOns
    fun tenures(): List<Tenure> = AddOnSeed.tenures

    /**
     * Minimal tiered CI list seed referenced by the `critical_illness` cover
     * (`criticalIllnessListRef = "CI_TIER1"`). The authoritative 92/101/Plan-1..5 lists are
     * loaded from `data/List of CI 101 and 92.csv` + the EE `CI List.csv` by sdk-ingestion.
     */
    fun criticalIllnessLists(): List<CriticalIllnessList> = listOf(
        CriticalIllnessList(
            listCode = "CI_TIER1",
            name = "Critical Illness — Base (13 conditions)",
            size = 13,
            items = BASE_13_CI.mapIndexed { i, n -> CIItem(slNo = i + 1, name = n) },
        ),
    )

    private val BASE_13_CI = listOf(
        "Major Cancer",
        "Kidney Failure requiring regular dialysis",
        "Major organ/Bone Marrow Transplant",
        "Open Heart Replacement/Repair of Heart Valves",
        "Open Chest Coronary Artery Bypass Graft",
        "Myocardial Infarction (First Heart attack of specified severity)",
        "Stroke resulting in permanent symptoms",
        "Permanent Paralysis of Limbs",
        "Parkinson's disease",
        "Multiple Sclerosis with persisting symptoms",
        "Coma of specified severity",
        "Blindness",
        "End Stage Liver Failure",
    )
}
