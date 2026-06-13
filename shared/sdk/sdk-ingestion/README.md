# sdk-ingestion

The **data-ingestion** layer of the rate PHI platform. It turns the `/data` CSV exports and the
actuarial rate sheets into catalog entities (via sdk-catalog PORTs) and **immutable, versioned**
rate rows.

Pure-KMP (`commonMain`: kotlinx + koin + ktor-client only). Depends on `:shared:core:core` and
`:shared:sdk:sdk-catalog`.

Package root: `com.rate.sdk.ingestion`.

## What lives here

```
model/
  RateMeta              (: BaseDataClass)  version pointer + sourceFileSha256 + active flag
  ImportSummary                            typed result (counts/skipped/warnings/errors/deduped)
  rate/RateRows.kt                         BaseRateRow, CoverRateRow, MemberLevelRow, DiscountRow,
                                           InstalmentRow, CoverAvailabilityRow (+ RateRowBatch);
                                           value types with DETERMINISTIC composite-id helpers
handler/
  Csv                                      pure-KMP RFC-4180 CSV reader (quotes, embedded \n / ,)
  SourceHash                               pure-KMP SHA-256 (file dedupe hashing)
  CsvCatalogParser                         /data CSV shapes → sdk-catalog entities (per-shape, pure)
  CatalogSeeder (+ CatalogSourceBundle)    idempotent seed via sdk-catalog ConfigRepository PORTs
  RateImportHandler                        SHA-dedupe → write rows → record RateMeta → activate
repository/
  RateImportRepository                     PORT: bulkUpsert immutable rate rows (+ per-kind, reads)
  RateMetaRepository                       PORT: active-version pointer + SHA dedupe + history
network/
  IngestionApi (ktor-client) + IngestionDtos   admin console read/write surface
event/
  IngestionEvent (sealed) + IngestionEventSink (PORT, NOOP default)
di/
  ingestionModule()                        Koin: parser + CatalogSeeder + RateImportHandler
```

## Two surfaces, one principle: immutability + idempotency

### 1. Rate ingestion (immutable, versioned)

Rate rows are **never mutated in place**. An import writes a fresh set under a new `version`, then
`RateMetaRepository.activate(version)` atomically flips which version the engine reads (the
in-RAM snapshot is rebuilt in server-persistence). Each row carries a deterministic composite id
(e.g. `BaseRateRow.stableId(version, planId, familyType, zone, ageBandMin, sumInsured)`), so
re-running the same import upserts the same documents — `RateImportRepository.bulkUpsert` is
idempotent. The composite-id columns mirror `RateDataProvider`'s lookup tuple exactly, so the
Mongo actual indexes on them and resolves one row per engine call.

`RateMeta.sourceFileSha256` (lowercase-hex SHA-256 of the uploaded file's bytes) gives **re-upload
dedupe**: `RateImportHandler` short-circuits via `RateMetaRepository.findBySha` before writing
anything, returning an `ImportSummary(deduped = true)`.

Rate values are `Double` (INR / fraction) — **engine parity** with the original Excel-derived
calculator; Money conversion happens only at `QuoteResult` assembly (per the rating contract).

Relocated from the monolith's Exposed tables: `BaseRatesTable`, `CoverRateLookupTable`,
`MemberLevelRatesTable`, `DiscountRatesTable`, `InstalmentConfigTable`, `CoverAvailabilityTable`.

### 2. Catalog ingestion (pure-KMP CSV → entities)

`CsvCatalogParser` parses the real `/data` shapes (one tolerant method per shape, no IO/Mongo/POI):

| `/data` file | parser method | → entity |
|---|---|---|
| `PBT Index.csv` | `parseSections` | `Section` (sub-sections, Day-1/Day-2) |
| `Indemnity- NEW ADDITION.csv` / `Employer Employee.csv` | `parseGroupProductConfig`, `parseBaseBenefitSchedule` | `GroupProductConfig`, `BenefitSchedule` (+ `Cover` stubs) |
| `List of CI 101 and 92.csv` | `parseFlatCriticalIllnessList` | `CriticalIllnessList` (deduped) |
| `…/CI List.csv` (Plan 1..5) | `parseTieredCriticalIllnessLists` | tiered `CriticalIllnessList` (cumulative) |
| `Waiting Periods.csv` | `parseWaitingPeriods` | `WaitingPeriod` |
| `Eligibility.csv` | `parseEligibility` | `EligibilityCriteria` |
| `PPD PTD Tables.csv` | `parsePpdPtdTables` | `PpdPtdTable` (PPD/PTD split) |
| `Day Care List.csv` | `parseDayCare` | `DayCareProcedure` (grouped) |
| `Consumables List.csv` | `parseConsumables` | 4× `ConsumablesList` |
| `Health Check Up Packages.csv` | `parseHealthCheckupPackages` | `HealthCheckupPackage` |
| `Annexure.csv` | `parseAnnexures`, `parseChronicOpdGrid` | `Annexure`, `ChronicOpdGrid` |
| `D1-*/D2-*` PHI section files | `parseCoversFromSection` | `Cover` |

`CatalogSeeder` upserts the parsed entities through the sdk-catalog `ConfigRepository<T>` PORTs.
Idempotency: each entity is stamped with a stable id derived (via `SourceHash`) from its business
key (section number, cover code, list code, …), so re-seeding the same source updates in place.
Parent→child refs the parser can't know (e.g. `groupProductRef`) are stitched after the parent id
is fixed. `force = false` is a first-boot-only seed (skips populated collections); `force = true`
re-imports (CSV becomes authoritative — the admin "re-import" action).

## Boundaries

- **NO** MongoDB driver, Ktor-server, Apache POI or Compose. The Excel(POI) importer is JVM-only
  and is added later in **server-persistence**, where it builds a `RateRowBatch` and hands it to
  `RateImportHandler`. The repository PORT actuals (Mongo) and a real `IngestionEventSink`
  (audit/bus) are also bound there.
- Tolerant parsing: an unmappable row is skipped (surfaced as an `ImportSummary` warning), never an
  exception — one bad row never fails a whole import.

## DI

`ingestionModule()` binds the pure-constructible pieces: `CsvCatalogParser`, `CatalogSeeder`
(wired from the sdk-catalog repo PORTs), `RateImportHandler` (wired from the ingestion PORTs), and
the `IngestionEventSink.NOOP` default. The server overrides the sink + binds the repo actuals.
