# sdk-catalog

The product/benefit **CATALOG** — the largest admin-CRUD surface of the rate PHI platform.
Pure-KMP (`commonMain` only kotlinx + koin + ktor-client). Depends on `:shared:core:core`.

Package root: `com.rate.sdk.catalog`.

## What lives here

Both **RETAIL** and **GROUP** product configuration, discriminated by `ProductLine` (from
core). Every persisted entity is an `@Serializable data class` implementing
`ConfigEntity` (id `_id`, createdAt/updatedAt, v, isDeleted, status, draftOf, created/updatedBy)
with `@SerialName` on every field, and gets a PORT repository extending the generic
`ConfigRepository<T>`. The MongoDB-backed actuals are implemented later in `server-persistence`.

```
model/
  Product, Section, Cover/Benefit (+ RateKind, CoverOption), CriticalIllnessList (+ CIItem),
  Annexure (+ AnnexureCategory), AddOn (+ AddOnItem), Tenure, PincodeZone
model/group/
  GroupProductConfig (+ GroupPolicyType), GroupGrade, BenefitSchedule (+ line),
  WaitingPeriod, EligibilityCriteria, PpdPtdTable (+ row, DisabilityTableType),
  DayCareProcedure, ConsumablesList (+ ConsumablesListType), HealthCheckupPackage,
  ChronicOpdGrid
repository/
  <Entity>Repository : ConfigRepository<Entity>      (17 PORT interfaces)
  PincodeZoneResolver + DefaultPincodeZoneResolver    (zone-from-pincode, seed-backed)
handler/
  CatalogHandler                                       (validated admin-CRUD + zone resolution + seeding)
  CatalogValidation                                    (pure per-entity rules)
network/
  CatalogApi (ktor-client read surface) + CatalogDtos  (ProductCatalogView, ZoneLookupResponse)
di/
  catalogModule()                                      (Koin: resolver + handler)
seed/
  CatalogSeed (public)                                 (relocated fallback defaults)
  CoverSeed, CoverAccumBases, PincodeZoneSeed, AddOnSeed (internal)
```

## Cover rate dispatch (`RateKind`)

Covers carry an explicit `rateKind` so the rating engine dispatches on **data**, not the old
hardcoded Excel row numbers: `PERCENT_MULTIPLIER` (rate × accumulation base), `FLAT`,
`MEMBER_LEVEL`, `POST` (forward-referencing covers), `UW_LOADING`, `DISCOUNT`. The
`accumBase` field carries the relocated `COVER_ACCUM_BASES` SUM() ranges.

## Money / Double boundary

Monetary catalog fields (`minSumInsured`, `maxSumInsured`, benefit limits, grade SI) use
`Money`. Rate lookups themselves are not in this module — they live behind the
`RateDataProvider` PORT in core and return `Double` for engine parity.

## Relocated from the monolith (now FALLBACK seed values)

| Old (shared/src/.../domain) | New |
|---|---|
| `model/CoverDefinitions.kt` (CoverIds, COVER_ACCUM_BASES) | `seed/CoverAccumBases.kt` + `Cover.accumBase` |
| `data/CoverCatalog.kt` (display copy + param options) | `seed/CoverSeed.kt` → seed `Cover`s |
| `data/PincodeZoneMap.kt` (`when` over prefixes) | `seed/PincodeZoneSeed.kt` → `PincodeZone` rows + `DefaultPincodeZoneResolver` |
| `buyonline/BuyOnlinePlanMapping.kt` (tiers, add-on bundles, tenure discounts) | `seed/AddOnSeed.kt` → `AddOn` + `Tenure` seeds |

GROUP config is relocated from the `/data` CSVs (Indemnity, EE GHI, PA/CI): Eligibility,
Waiting Periods, PPD/PTD tables, Day Care list, Consumables List I–IV, Health Check-up
packages, Chronic OPD grid, CI lists. The hardcoded tables are kept **only as seed
fallbacks** — actual data is imported from Mongo/CSV (sdk-ingestion) at runtime.

`CatalogHandler.seedDefaults()` idempotently bulk-upserts the seeds into any empty collection
on first boot.
