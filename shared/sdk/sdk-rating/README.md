# sdk-rating

The **rating engine** layer of the re-architected PHI platform. Pure-KMP (`commonMain` only;
JVM + wasmJs + iOS). Depends on `:shared:core:core`, `:shared:sdk:sdk-catalog`,
`:shared:sdk:sdk-party`. **No Mongo, no Ktor-server, no Compose, no POI** — the Mongo-backed
`RateDataProvider` actual lives later in `:server-persistence`.

It relocates the monolith's `PricingEngine` **verbatim** (Double math + `MONEY_EPS` preserved
for byte-for-byte Excel `Rate_Calculator_v7.0` parity) behind the core `RatingPort`, and adds a
first-class **GROUP** rating path.

```
com.rate.sdk.rating
├── handler/
│   ├── PricingEngine.kt        RELOCATED retail engine : RatingPort
│   │                           (calculate() == rate()); plan-aware + intra-request validation,
│   │                           accumulating-% / flat / member-level covers, capped discounts,
│   │                           UW-loading clamp, instalment loading, GST → QuoteResult (Double).
│   └── GroupPricingEngine.kt   GROUP engine over GroupRateDataProvider + Census (sdk-party):
│                               per-(grade × age-band) book premium, group-size discount,
│                               industry loading, experience/credibility blend, employer/
│                               employee allocation → GroupQuoteResult (Money).
├── model/
│   ├── CoverIds.kt             RELOCATED CoverIds + COVER_ACCUM_BASES (engine rate-row contract).
│   ├── QuoteValidators.kt      RELOCATED plan-aware quoteRequest(plan, req) (moved out of
│   │                           core-base.util.Validators to keep core-base dependency-free).
│   ├── RatingStrategy.kt       sealed Manual / Experience / Hybrid (group pricing approach).
│   ├── ClaimYear.kt            one policy-year of group claims experience (lossRatio, …).
│   ├── PremiumAllocation.kt    employer/employee split of a Money premium (reconciles exactly).
│   └── GroupQuoteResult.kt     perMember / perGrade lines + manual/experience/blended : Money,
│                               gst, total — the GROUP sibling of core's QuoteResult.
├── data/
│   └── InProcessRateDataProvider.kt   RELOCATED offline RateDataProvider (WASM customer journey
│                                      / desktop fallback) + InProcessGroupRateDataProvider.
├── repository/
│   └── RatingPortAdapters.kt   RateDataRenewalProvider : RenewalRateProvider over RateDataProvider
│                               (baseFor → age-banded getBasePremium) for sdk-policy's RenewalEngine.
└── di/
    └── RatingModule.kt         binds RatingPort=PricingEngine, GroupPricingEngine,
                                RenewalRateProvider; in-process providers as offline fallback.
```

## Key contracts

| Port (core)                          | Implementation here                         |
|--------------------------------------|---------------------------------------------|
| `RatingPort`                         | `PricingEngine`                             |
| `RenewalRateProvider`                | `RateDataRenewalProvider`                   |
| `RateDataProvider` (offline)         | `InProcessRateDataProvider`                 |
| `GroupRateDataProvider` (offline)    | `InProcessGroupRateDataProvider`            |

The production `RateDataProvider` / `GroupRateDataProvider` (Mongo snapshot loaded at boot) are
bound by the app layer; sdk-rating depends only on the PORTs. Downstream features
(sdk-quoting's `QuoteHandler`, sdk-policy's `RenewalEngine`) depend on `RatingPort` /
`RenewalRateProvider`, never on the concrete engines — inverting the one real cross-feature edge.

## The Money/Double boundary

The retail engine accumulates in **Double** (INR) — the rate-table ports return Double and
`MONEY_EPS` zero-checks are preserved exactly — so the QuoteResult is byte-for-byte identical to
the original Excel-derived calculator. Conversion to `Money` happens later, only at
display/QuoteResult-money assembly (sdk-quoting). The GROUP path converts to `Money` at the
census-bucket boundary and does all subsequent math in exact paise (group figures are exact
rupee amounts, not Excel-parity sensitive).

## Group rating

`GroupPricingEngine.rate(...)` reuses the SAME `getBasePremium` lookup as retail (keeping GROUP a
discriminator, not a parallel rate tree). It rolls the census up by (grade × age-band) via
sdk-party's `CensusAggregation`, applies `(1 - sizeDiscount) × (1 + industryLoading)`, then for
`Experience`/`Hybrid` strategies blends a burning-cost premium (`incurredClaims / (1 - expenseRatio)`)
against the manual book rate by credibility (classical √-rule, or an underwriter-set weight),
spreads the blended total back onto buckets/grades (largest bucket absorbs the rounding
remainder), and splits the GST-inclusive total employer/employee.
