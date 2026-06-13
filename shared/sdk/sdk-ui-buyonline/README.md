# sdk-ui-buyonline

The **public customer journey** — the 22-screen buy-online flow, rendered with
Compose Multiplatform (JVM operator-embed + WASM Cloudflare-Pages customer app).
Relocated from the monolith's `aegis/customer/buyonline` package.

Package root: `com.rate.sdk.ui.buyonline`.

## Layering

```
core → sdk(-proposal/-quoting/-rating) → sdk-ui-kit → sdk-ui-buyonline → app(:aegis)
```

- **Compose lives ONLY in `sdk-ui-*` modules.** This is a top `sdk-ui-*` leaf.
- It **reuses sdk-ui-kit** for the PRU customer brand (`PRUHealthTheme`, `PruRed`,
  `PRUTopBar`, `PRUButton`, `StickyPriceBar`, …) and the EN/HI i18n catalog
  (`Strings` / `t("…")` / `LocalAegisLocale`) rather than re-rolling widgets.
- It drives the whole flow through the feature SDKs' **Ktor-client** surfaces,
  layered on core-network's `TanvritClient`:
  - sdk-proposal `BuyOnlineApi` — OTP, pincode/hospitals, eligibility, KYC-OTP,
    proposal submit/track, save+resume;
  - sdk-quoting `QuoteApi` — premium (`calculate()` returns the full
    server-priced `QuoteResult` the Quote/AddOns/Summary screens render).
- **NO Mongo driver, NO Ktor-server, NO POI.** The concrete `PricingEngine` and
  the Mongo-backed repositories are server-only — every rupee the customer sees
  comes back over the wire from the server-side handlers.
- Targets: `jvm()` + `wasmJs { browser() }`.

## What's inside

- `BuyOnlineApp.kt` — the single public entry composable. Takes a `TanvritClient`
  (+ optional launch ids, host-owned `locale`/`onLocaleChange`, and a `loadPlan`
  enrichment hook). Builds the API clients + view-model, publishes
  `LocalAegisLocale`, and renders the journey (with the `?session=` resume chrome
  and `?quote=` shared-quote short-circuit).
- `SharedQuoteView.kt` — read-only summary of an operator-shared `?quote=<id>`
  link (via `QuoteApi.get()` → `SavedQuoteView`), with a "Continue to apply" CTA
  that seeds the journey onto the Quote screen.
- `navigation/BuyOnlineScreen.kt` — the 22 screens as a closed sealed class.
- `model/` — UI-side journey view models (`MemberType`, `PlanTier`, `AddOn`,
  `PersonalDetail`, lifestyle/medical questionnaires, KYC, bank details) +
  `BuyOnlinePlanMapping` (tier → actuarial plan id + curated add-on→cover bundle,
  on top of sdk-rating `CoverIds`).
- `viewmodel/BuyOnlineViewModel.kt` — the journey state holder. Preserves the
  monolith's screen-binding surface byte-for-byte; the only structural change is
  the data path (network handlers instead of an in-process engine).
- `screens/` — the 22 screen composables + buyonline-specific components
  (`StepIndicator`, `IrdaiCompliance`, `ResumeBanner`, `FloatingHelpButton`,
  `SessionExpiresCallout`). The generic PRU primitives live in sdk-ui-kit.
- `platform/Platform.kt` (+ `.jvm`/`.wasmJs` actuals) — the host seams: resume
  URL, before-unload guard, `?quote=` scrub, clipboard, launch-id reads.
- `di/BuyOnlineUiModule.kt` — Koin wiring binding `BuyOnlineApi` + `QuoteApi`
  on top of the app-provided `TanvritClient`.

## Host wiring

```kotlin
startKoin { modules(networkModule(config), uiKitModule(), buyOnlineUiModule()) }

// WASM customer / JVM operator-embed:
BuyOnlineApp(
    client = koin.get(),                 // TanvritClient
    locale = persistedLocale,            // host owns persistence
    onLocaleChange = { persist(it) },
    loadPlan = { id -> client.getJson<Plan>("/api/plans/$id") }, // optional enrichment
)
```

The `/api/buy-online/*` and `/api/quotes/*` wire paths are preserved verbatim
from the monolith so the existing server contract is unchanged.
