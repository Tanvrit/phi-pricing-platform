package com.rate.server.routes

import com.rate.core.base.model.PageRequest
import com.rate.core.rating.ports.PlanRepository
import com.rate.core.rating.ports.model.PaymentMode
import com.rate.core.rating.ports.model.Plan
import com.rate.core.rating.ports.model.PlanLifecycle
import com.rate.core.rating.ports.model.PlanType
import com.rate.core.rating.ports.model.ProductLine
import com.rate.core.rating.ports.model.Tenure
import com.rate.core.regulatory.AGE_BANDS
import com.rate.persistence.rating.RateTableCache
import com.rate.sdk.catalog.model.Cover
import com.rate.sdk.catalog.repository.CoverRepository
import com.rate.sdk.ingestion.handler.RateImportHandler
import com.rate.sdk.ingestion.model.rate.BaseRateRow
import com.rate.sdk.ingestion.model.rate.CoverAvailabilityRow
import com.rate.sdk.ingestion.model.rate.CoverRateRow
import com.rate.sdk.ingestion.model.rate.DiscountRow
import com.rate.sdk.ingestion.model.rate.InstalmentRow
import com.rate.sdk.ingestion.model.rate.RateRowBatch
import com.rate.sdk.rating.model.CoverIds
import io.ktor.http.HttpStatusCode
import io.ktor.server.application.call
import io.ktor.server.response.respond
import io.ktor.server.routing.Route
import io.ktor.server.routing.post
import kotlinx.serialization.Serializable

/** Version tag for the dev rate scaffold. Re-running the seed upserts the same rows in place. */
private const val SCAFFOLD_VERSION = "dev-scaffold-v1"

/** The fallback RETAIL plan id this scaffold guarantees exists + prices end-to-end in dev. */
private const val DEMO_PLAN_ID = "PHI_BASIC"

/** Fallback knobs when a plan carries no `availableX` lists (illustrative dev values only). */
private val DEFAULT_FAMILY_TYPES = listOf("1A", "2A")
private val DEFAULT_ZONES = listOf("Zone 1", "Zone 2")
private val DEFAULT_SUM_INSUREDS = listOf(500_000L, 1_000_000L, 2_500_000L)

/**
 * Engine cover CODES this scaffold prices a FLAT INR/year rate for. They are deliberately the
 * covers whose [com.rate.sdk.rating.handler.PricingEngine] lookup passes NO param/age/SI/plan
 * key — i.e. `getCoverRate(code)` with every nullable segment null — so a single all-null
 * [CoverRateRow] resolves them, and the engine multiplies a flat INR amount straight through.
 *
 * `code → flat INR per policy year`. Codes are the [CoverIds] constants (== the engine
 * [com.rate.core.rating.ports.model.CoverSelection.coverId] AND the seed [Cover.code]).
 */
private val DEMO_FLAT_COVER_RATES: Map<String, Double> = linkedMapOf(
    CoverIds.HOME_CARE to 167.0,            // row 19 — flat INR
    CoverIds.AIR_AMBULANCE to 433.0,        // row 36 — flat INR
    CoverIds.WELLNESS_PACKAGE to 750.0,     // row 38 — flat INR
    CoverIds.SECOND_OPINION to 300.0,       // row 39 — flat INR
    CoverIds.POST_DELIVERY_CARE to 500.0,   // row 41 — flat INR
    CoverIds.PRU_HEALTH_SPECIALIST to 600.0,// row 49 — flat INR
)

@Serializable
internal data class SeedRatesResult(
    val version: String,
    val plans: Int,
    val baseRates: Int,
    val instalments: Int,
    val discounts: Int,
    val coverRates: Int,
    val coverAvailability: Int,
    val planUpserted: Boolean,
    /** The cover CODES the scaffold priced + made available (engine CoverSelection.coverId). */
    val allowedCoverCodes: List<String>,
    /** The real Mongo `_id`s placed on the plan's allowlist (refEntityId="covers" multi-select). */
    val allowedCoverIds: List<String>,
    val activated: Boolean,
)

/**
 * DEV-ONLY rate scaffold. Retail quotes price 0 because the freshly-seeded environment has only
 * catalog/masters — no active [com.rate.sdk.ingestion.model.RateMeta] and an empty base-rate table,
 * so [com.rate.core.rating.ports.RateDataProvider.getBasePremium] returns 0.0 for every lookup.
 * The 5 catalog-seeded plans are GROUP-line with empty availableSumInsureds/Zones/FamilyTypes, so
 * the RETAIL Rate Calculator has no grid to drive dropdowns and add-on covers price 0.
 *
 * This route makes the RETAIL calculator fully demoable. It:
 *  1. upserts a real RETAIL [Plan] id "PHI_BASIC" (LIVE, active, gst 18%, age 18-80) whose
 *     availableSumInsureds/Zones/FamilyTypes match the base-rate grid below, and whose
 *     `allowedCoverIds` holds the real Mongo `_id`s of a handful of seeded covers;
 *  2. synthesises a plausible-but-illustrative base-rate grid (and the instalment/discount rows
 *     the engine consults) under version "dev-scaffold-v1";
 *  3. seeds an all-null [CoverRateRow] per [DEMO_FLAT_COVER_RATES] cover so each add-on prices a
 *     NON-ZERO flat INR amount (keyed EXACTLY as the engine looks it up — all nullable segments
 *     null → stored "_"), plus a [CoverAvailabilityRow] marking each available on PHI_BASIC;
 *  4. drives the SAME ingestion path `/api/import` uses — [RateImportHandler.importBatch]
 *     (idempotent row upsert + RateMeta upsert + single-active activation) — then reloads
 *     [RateTableCache] WITH the refreshed plan list so the version + plan go LIVE without a
 *     server restart and `/api/quotes/calculate` prices NON-ZERO.
 *
 * The base-rate grid covers EVERY age-band minimum in [AGE_BANDS] (the exact key the engine derives
 * via `getAgeBand(age).minAge`), so a quote for any supported primary age resolves a row. Rates are
 * a deterministic `ratePerLakh × ageFactor × zoneFactor` formula — clearly synthetic, not actuarial.
 *
 * Mounted ONLY inside the AEGIS_DEV_PROFILE dev block. POST `/api/dev/seed-rates`. Idempotent.
 */
fun Route.seedRatesRoutes(
    plansRepo: PlanRepository,
    coversRepo: CoverRepository,
    rateImport: RateImportHandler,
    rateCache: RateTableCache,
) {
    post("/api/dev/seed-rates") {
        // ── 0. Resolve which real seeded covers to allow on the demo plan ────────
        // The engine prices by cover CODE (== CoverSelection.coverId == Cover.code), but the Plan
        // admin form's `allowedCoverIds` is a multi-select over cover `_id`s (refEntityId="covers").
        // So we map the engine codes we price → the real seeded covers' `_id`s, and fall back to
        // the code itself when no catalog row carries that code (a fresh env may seed only GROUP
        // covers with unrelated codes). The codes we PRICE/AVAIL are always the engine codes, so
        // the calculator always prices non-zero regardless of which `_id`s end up on the plan.
        val allCovers: List<Cover> = runCatching {
            coversRepo.list(PageRequest(page = 0, size = 1000)).items
        }.getOrElse { emptyList() }
        val coverByCode: Map<String, Cover> = allCovers.associateBy { it.code }

        val allowedCoverCodes: List<String> = DEMO_FLAT_COVER_RATES.keys.toList()
        // Real `_id`s where the seeded catalog actually has the code; otherwise fall back to the
        // code so the plan still carries a stable allowlist entry the operator can see.
        val allowedCoverIds: List<String> = allowedCoverCodes.map { code ->
            coverByCode[code]?.id ?: code
        }

        // ── 1. Upsert the demo RETAIL plan (idempotent — replaces an existing PHI_BASIC) ──
        // Preserve the existing ConfigEntity envelope (createdAt / createdBy / v) when re-running.
        val existing: Plan? = plansRepo.getPlan(DEMO_PLAN_ID)
        val demoPlan = (existing ?: Plan(id = DEMO_PLAN_ID, name = "PHI Basic (demo)", planType = PlanType.DOMESTIC)).copy(
            id = DEMO_PLAN_ID,
            name = "PHI Basic (demo)",
            planType = PlanType.DOMESTIC,
            productLine = ProductLine.RETAIL,
            description = "Synthetic dev demo plan — illustrative rates, not actuarial.",
            availableSumInsureds = DEFAULT_SUM_INSUREDS,
            availableZones = DEFAULT_ZONES,
            availableFamilyTypes = DEFAULT_FAMILY_TYPES,
            allowedCoverIds = allowedCoverIds.toSet(),
            minAge = 18,
            maxAge = 80,
            gstRate = 0.18,
            isActive = true,
            lifecycle = PlanLifecycle.LIVE,
            rateVersion = SCAFFOLD_VERSION,
            updatedBy = "owner-seed",
        )
        plansRepo.upsertPlan(demoPlan)
        val planUpserted = true

        // The plan grid the base rates (and the calculator's dropdowns) drive off.
        val familyTypes = demoPlan.availableFamilyTypes
        val zones = demoPlan.availableZones
        val sumInsureds = demoPlan.availableSumInsureds

        // Canonical age-band minimums — the exact keys the engine queries with.
        val ageBandMins = AGE_BANDS.map { it.minAge }

        // ── 2. Base-rate grid for the demo plan ──────────────────────────────────
        val baseRates = buildList {
            for (familyType in familyTypes) {
                for (zone in zones) {
                    for (sumInsured in sumInsureds) {
                        for (ageBandMin in ageBandMins) {
                            add(
                                BaseRateRow.of(
                                    version = SCAFFOLD_VERSION,
                                    planId = DEMO_PLAN_ID,
                                    familyType = familyType,
                                    zone = zone,
                                    ageBandMin = ageBandMin,
                                    sumInsured = sumInsured,
                                    annualPremium = syntheticPremium(
                                        familyType = familyType,
                                        zone = zone,
                                        ageBandMin = ageBandMin,
                                        sumInsured = sumInsured,
                                    ),
                                ),
                            )
                        }
                    }
                }
            }
        }

        // ── 3. Cover rates — one all-null row per demo cover (NON-ZERO flat INR) ──
        // Keyed EXACTLY as PricingEngine looks each up: getCoverRate(code) → every nullable
        // segment (param1, param2, ageBandMin, sumInsured, planOrTenureKey) is null, so the row's
        // stableId stores each as "_" and RateTableCache.Keys.cover(...) rebuilds the same key.
        val coverRates = DEMO_FLAT_COVER_RATES.map { (code, rate) ->
            CoverRateRow.of(
                version = SCAFFOLD_VERSION,
                coverId = code,
                rate = rate,
                // All lookup segments null — matches the engine's all-null call for these covers.
                param1 = null,
                param2 = null,
                ageBandMin = null,
                sumInsured = null,
                planOrTenureKey = null,
            )
        }

        // ── 4. Cover availability — mark each demo cover available on PHI_BASIC ──
        val coverAvailability = allowedCoverCodes.map { code ->
            CoverAvailabilityRow.of(
                version = SCAFFOLD_VERSION,
                planId = DEMO_PLAN_ID,
                coverId = code,
                available = true,
            )
        }

        // ── 5. Instalment counts for every policy-tenure × payment-mode ──────────
        // The engine falls back to a rule-based count when a row is missing, but seeding them
        // makes the configuration explicit. paymentTenure mirrors the policy tenure.
        val instalments = buildList {
            for (tenure in Tenure.entries) {
                for (mode in PaymentMode.entries) {
                    add(
                        InstalmentRow.of(
                            version = SCAFFOLD_VERSION,
                            policyTenure = tenure,
                            paymentTenure = tenure,
                            paymentMode = mode,
                            instalmentCount = InstalmentRow.computeCount(tenure, mode),
                        ),
                    )
                }
            }
        }

        // A couple of standalone (flat) discounts the engine looks up by id with no param key.
        val discounts = listOf(
            DiscountRow.of(version = SCAFFOLD_VERSION, discountId = CoverIds.DISC_EMPLOYEE, rate = 0.10),
            DiscountRow.of(version = SCAFFOLD_VERSION, discountId = CoverIds.DISC_AUTO_DEBIT, rate = 0.025),
        )

        val batch = RateRowBatch(
            version = SCAFFOLD_VERSION,
            baseRates = baseRates,
            coverRates = coverRates,
            instalmentConfig = instalments,
            discountRates = discounts,
            coverAvailability = coverAvailability,
        )

        // Drive the SAME ingestion path /api/import uses. sha="" → no SHA dedupe, so re-seeding
        // re-runs idempotently (rows upsert by deterministic id); activate=true flips the active
        // RETAIL RateMeta pointer (deactivating any prior active version).
        val summary = rateImport.importBatch(
            batch = batch,
            sha = "",
            sourceFileName = "dev-scaffold",
            productLine = ProductLine.RETAIL,
            activate = true,
            actor = "owner-seed",
            note = "Synthetic dev rate scaffold (illustrative, not actuarial).",
        )

        // Reload the in-RAM engine snapshot so the freshly-activated version serves traffic without
        // a restart. Pass the CURRENT plan list as the override so the just-upserted PHI_BASIC is
        // visible to the engine (getPlan/getAllPlans read the snapshot, which is otherwise seeded
        // with the boot-time plan list only). Plan-aware validation + gstRate then apply correctly.
        if (summary.ok) {
            val freshPlans = runCatching { plansRepo.getAllPlans() }.getOrElse { listOf(demoPlan) }
            rateCache.load(ProductLine.RETAIL, plansOverride = freshPlans)
        }

        call.respond(
            HttpStatusCode.OK,
            SeedRatesResult(
                version = summary.version.ifBlank { SCAFFOLD_VERSION },
                plans = 1,
                baseRates = baseRates.size,
                instalments = instalments.size,
                discounts = discounts.size,
                coverRates = coverRates.size,
                coverAvailability = coverAvailability.size,
                planUpserted = planUpserted,
                allowedCoverCodes = allowedCoverCodes,
                allowedCoverIds = allowedCoverIds,
                activated = summary.ok,
            ),
        )
    }
}

/**
 * Deterministic, plausible-but-illustrative annual premium (INR). NOT actuarial — a transparent
 * `ratePerLakh × ageFactor × zoneFactor × familyFactor` formula so the calculator shows non-zero,
 * monotonically-sensible numbers in dev. Same inputs always yield the same value (idempotent seed).
 */
private fun syntheticPremium(
    familyType: String,
    zone: String,
    ageBandMin: Int,
    sumInsured: Long,
): Double {
    val lakhs = sumInsured / 100_000.0
    val ratePerLakh = 1_200.0 // base rate per lakh of sum insured
    // Older bands cost more: +4% per year above the youngest band's minimum.
    val ageFactor = 1.0 + (ageBandMin - AGE_BANDS.first().minAge).coerceAtLeast(0) * 0.04
    // Zone 1 (metro) richer than outer zones; default 1.0 for any unrecognised label.
    val zoneFactor = when (zone) {
        "Zone 1" -> 1.20
        "Zone 2" -> 1.00
        "Zone 3" -> 0.90
        "Zone 4" -> 0.80
        else -> 1.00
    }
    // Floater family types add a per-extra-member load on top of the single-life "1A".
    val familyFactor = when {
        familyType.startsWith("1A") -> 1.0
        familyType.startsWith("2A") -> 1.6
        else -> 1.2
    }
    val raw = lakhs * ratePerLakh * ageFactor * zoneFactor * familyFactor
    // Round to whole rupees for tidy illustrative figures.
    return kotlin.math.round(raw)
}
