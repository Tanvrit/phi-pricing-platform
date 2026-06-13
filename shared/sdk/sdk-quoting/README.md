# sdk-quoting

The **quoting** feature of the PHI platform: turn a `QuoteRequest` into a priced, persisted
`Quote`, and produce the IRDAI documents that must accompany it.

Layer: **sdk** (pure-KMP commonMain). Depends on:
`project(":shared:core:core")`, `project(":shared:sdk:sdk-catalog")`, `project(":shared:sdk:sdk-party")`.

No MongoDB driver, no Ktor-server, no Apache POI, no Compose. The `QuoteRepository` and
`RatingPort` *actuals* are bound by the **app** layer (server-persistence over the Mongo
snapshot; sdk-rating's `PricingEngine` as the `RatingPort`).

## Package layout (`com.rate.sdk.quoting`)

```
model/
  Quote.kt                  sealed Quote : RetailQuote | GroupQuote  (: BaseDataClass)
                            wraps QuoteRequest + QuoteResult + status/meta. QuoteStatus enum.
handler/
  QuoteHandler.kt           calculate(request) via injected RatingPort (core);
                            calculateAndSave / get / list via core QuoteRepository.
  doc/
    IrdaiMasterCircular2024.kt   RELOCATED circular constants + compliance audit + UinRegistry.
    SalesIllustrationBuilder.kt  RELOCATED — IRDAI Sales Illustration (tenure ≥ 3 yrs).
    CisBuilder.kt                RELOCATED — IRDAI Customer Information Sheet.
    ProspectusBuilder.kt         RELOCATED — IRDAI prospectus (12 mandatory sections).
    GroupBenefitScheduleBuilder.kt  NEW — GROUP GHI benefit schedule (Annexure A).
event/
  QuoteEvents.kt            QuoteEvent (Calculated/Shared/Converted) + QuoteEventSink PORT (NOOP).
network/
  QuoteApi.kt               Ktor-client read/calculate surface.
  QuoteDtos.kt              composed request/response wire DTOs.
di/
  QuotingModule.kt          Koin module() binding QuoteHandler from the injected PORTs.
```

There is **no** `repository/` package: quotes persist through core's generic `QuoteRepository`
PORT (the Mongo actual lives in server-persistence), and quotes are transactional, not
admin-CRUD, so no `ConfigRepository` is added.

## The Money / Double boundary

Per the rating contract, the engine accumulates in **Double** for byte-for-byte parity with the
original Excel-derived calculator. `QuoteResult` stays Double end-to-end. Conversion to `Money`
happens **only** at assembly/display: `Quote.totalIncludingGstMoney`, the CIS line items, and the
group benefit-schedule limits/totals.

## Relocation notes

The three RETAIL IRDAI builders are relocated from
`shared/src/commonMain/kotlin/com/rate/domain/regulatory/{SalesIllustration, CustomerInformationSheet,
Prospectus, IrdaiMasterCircular2024}.kt`. Imports were repackaged onto the core rating-contract
types and the sdk-catalog `Cover` entity (the old `CoverCatalog.CoverMeta` is now the catalog's
first-class `Cover`). The `SalesIllustrationBuilder.fromRenewalIllustration` path was adapted to
core's leaner `RenewalIllustrationLine` (`year`/`age`/`projectedPremium`/`ncbPercent`/`ncbAmount`):
the pre-NCB base is reconstructed as `projectedPremium + ncbAmount` and the age-band label is
resolved via `core.regulatory.getAgeBand`. `GroupBenefitScheduleBuilder` is new — the GROUP-side
counterpart the monolith never had.
```
