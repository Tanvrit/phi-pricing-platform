# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

### Aegis platform unification & shell
- Single Compose Multiplatform app (`:aegis`) replaces the retired `:desktop` and
  `:buyonline` modules; JVM + WASM targets share `commonMain`.
- 14 operator surfaces routed via `AegisShell` — Home, Calculator, Quote Explorer,
  Plan Configurator, Cover Catalog, Discounts, Reports, Audit, Import, Settings,
  Product Catalog, IRDAI Prospectus, Server Health, Gallery.
- Role-based routing (`CUSTOMER` / `BUSINESS` / `ADMIN`) honoured at startup and
  via URL params on the WASM build.
- `⌘K` command palette with deep-links to plans, quotes, covers, and discounts.
- Global Refresh button in the top bar plus 30s auto-refresh on dashboard data.
- Bell-icon `NotificationCenter` dropdown surfacing live system events.
- Top-bar connectivity indicator on the JVM desktop binary (window title +
  status dot).
- Dark mode toggle with persisted theme preference.

### Customer journey (buyonline)
- 5-stage `StepIndicator` rendered across the 22-screen journey.
- Save+resume via `?session=` URL token (server-side `BuyOnlineSessionRepository`).
- Shareable quotes via `?quote=` (`SharedQuoteView`) for sending mid-funnel quotes
  to a peer or device.
- `ResumeBanner` + clipboard helper on every active screen.
- Browser `beforeunload` guard so customers don't lose progress on accidental
  tab close.

### Data + analytics
- `HomeSurface` KPI sparklines, tooltips, and live activity feed.
- Reports surface with time-bucket aggregations (day / week / month),
  distributions, customer-journey funnel, and completion stats.
- Date-range filter on Reports.
- Quote-total histogram on Reports.
- Per-section CSV export plus an "Export all" bundle.
- Plan duplicate action (next-id heuristic) on Plan Configurator.

### Audit + compliance
- `GET /api/audit/events` list endpoint backing the Aegis Audit surface.
- `GET /api/audit/verify` chain-integrity check with a dedicated Aegis card.
- `GET /api/audit/idempotency` cache diagnostic for replay troubleshooting.
- `GET /api/audit/stream` Server-Sent Events feed (consumed by `NotificationCenter`
  and the live activity feed).
- Plan diff capture (old → new field set) with colour-coded render in the audit
  drawer.
- Resource-id deep-links from audit rows into the originating surface.
- Search box + action and actor filter chips on the Audit surface; `?actor=` query
  honoured.
- "Copy event JSON" action on the audit drawer.
- IRDAI Prospectus HTML endpoint plus its Compose surface.
- PII redaction applied to `/api/buy-online/sessions` responses.
- "My audit trail" panel embedded in the Settings surface.

### Server platform
- Operators allowlist at `~/.aegis/operators.json` driving Phase-1 RBAC (5 scopes).
- `X-Aegis-Actor` header pass-through, written into `audit_event` rows for
  attribution.
- Idempotency-replay metrics (`otp_sent_total`, `idempotent_replay_total`, etc.).
- `Plan.lifecycle` field on the domain `Plan` plus V4 migration.
- `BuyOnlineSessionRepository` plus V5 migration backing customer save+resume.
- Settings-driven runtime so feature toggles flow from Settings into live behaviour.

### Build / ops
- `scripts/smoke.sh` smoke-test script hitting 27 endpoints.
- Aegis shield favicon shipped on the WASM bundle.
- `README.md` refresh (162 → 341 lines): surface inventory, endpoint tables, RBAC
  scopes section, runbook catalogue references.

### Refactoring
- `ApiClient` / `rememberApiClient` extracted as the single Aegis-wide HTTP entry
  point; legacy per-surface clients removed.
- WASM-portability fixes in `commonMain` (no `java.*` types leaking through).
- 10-runbook catalogue completed (RB-13 sms-gateway-degraded was the closer).

## [0.1.0] - 2026-05-22

### Changed — Platform unification (Aegis is everything)

- **One module to rule them all**: the customer buyonline journey and the operator
  rate calculator were merged into a single Compose Multiplatform module, `:aegis`,
  with JVM + WASM targets and role-based routing (`CUSTOMER` / `BUSINESS` / `ADMIN`).
  JVM defaults to BUSINESS (operator console). WASM defaults to CUSTOMER (the
  Cloudflare Pages deployment at https://phi-buyonline.pages.dev/).
- **Retired modules**: `:desktop` and `:buyonline` were removed from
  `settings.gradle.kts` and their source physically deleted (preserved in git
  history). All UI now lives under `:aegis` —
  `aegis/src/commonMain/.../customer/buyonline/` for the customer journey and
  `aegis/src/jvmMain/.../business/calculator/desktop/` for the operator calculator.
  Role-router lives in `aegis/src/commonMain/kotlin/com/rate/aegis/AegisRoot.kt`.
- **Cloudflare Pages**: `wrangler.toml` now points at
  `aegis/build/dist/wasmJs/productionExecutable`. WASM bundle is `aegis.js`
  (was `buyonline.js`).
- **Makefile**: `desktop` + `buyonline` targets replaced by `aegis`,
  `aegis-customer` (`-Daegis.role=CUSTOMER`), and `aegis-web`.

### Added — Foundation Pack
- **Tests**: JUnit 5 + Kotest runner wired into `:shared:jvmTest`. Engine, validators,
  Money, and age-band tests cover the core arithmetic and edge cases.
- **Kover coverage**: aggregated XML/HTML reports across `:shared` and `:server`.
- **`Money` value class** (`com.rate.domain.money.Money`) backing all currency totals
  with `Long paise`. Half-even rounding, Indian grouping formatter (`₹1,23,45,678.90`).
- **`Validators`** for Indian PAN, IFSC, mobile, pincode, Aadhaar (Verhoeff), account
  number. Same regex used across server, desktop, buy-online.
- **GST 18% on every quote** — `Plan.gstRate` (default 0.18), `QuoteResult.gstAmount`,
  `QuoteResult.totalIncludingGst`. Engine computes GST on
  `(totalAfterDiscount + instalmentLoading)`.
- **Audit fields on QuoteResult**: `engineVersion`, `rateTableVersion`, `calculatedAt`.
- **ULID-like quote IDs** replacing the prior epoch-millis IDs (collision-resistant
  under concurrent load).
- **Configurable CORS allowlist** via `CORS_ALLOWED_ORIGINS` env var (no more `anyHost()`).
- **Security headers** on every response (`HSTS`, `X-Frame-Options: DENY`,
  `X-Content-Type-Options: nosniff`, `Referrer-Policy`, `Permissions-Policy`).
- **Non-destructive Flyway migration**: V1 no longer drops every table on startup;
  `baselineOnMigrate=true`. V2 adds `rate_meta` and `otp_records`.
- **`OtpService`** with `SecureRandom` 6-digit codes, 5-minute TTL, max 5 attempts,
  per-mobile rate limit (5 sends/hour), SHA-256 hashed code-at-rest, constant-time compare.
- **Logback JSON encoder + PII masking** — mobile, Aadhaar, PAN auto-masked in every
  log line.
- **`Dockerfile`** (multi-stage build → distroless JRE image), `docker-compose.yml`
  (Postgres + server stack), `.dockerignore`.
- **`.github/workflows/build.yml`** — CI on every push/PR running `./gradlew build
  koverXmlReport`.
- **`Makefile`** with `dev`, `test`, `desktop`, `buyonline`, `server` targets.
- **`.editorconfig`** (Kotlin official style), `CONTRIBUTING.md`, `LICENSE` (Apache 2.0).

### Changed
- **Engine discount cap is now binding** for cover-pass discounts (`smart_select`,
  `per_claim_deductible`, `aggregate_deductible`, `co_pay`). Prior behaviour allowed
  these to bypass the plan's `maxDiscountCap` and reach 50%+ combined; now they flow
  into the same cap pool as standalone discounts.
- **`getAgeBand(age)`** rejects out-of-range input (`age !in 0..120`) — silent fallback
  to the lowest band was hiding upstream bugs.
- **`getFamilyTypeInfo(code)`** throws on unknown codes instead of silently returning
  `1A`. Caught at engine boundary and surfaced as a validation error.
- **`uwLoadingFactor`** is clamped to `[0.0, 2.0]`. Out-of-range values land in
  `validationErrors` as a clamp note.
- **`BuyOnlineViewModel.estimatedPremium()` removed** — Buy-Online now calls the real
  `PricingEngine` (via the existing server `/api/buy-online/premium` endpoint, which
  itself now invokes the engine instead of hardcoded math).
- **Server `/api/buy-online/premium`** route rewired to the real engine.
- **Server `/api/buy-online/proposal`** uses ULID-style IDs (`"PHI" + ULID`).
- **`BuyOnlineRoutes` OTP endpoints** wired to real `OtpService` (server-side store,
  TTL, rate-limit, constant-time compare).
- **`application.conf`** secrets via env vars only: `DB_USER`, `DB_PASSWORD`, `DB_URL`,
  `DB_POOL_SIZE`, `CORS_ALLOWED_ORIGINS`, `OTP_TOKEN_SECRET`. Plaintext `rate123`
  removed.
- **`HikariCP` hardening**: explicit `connectionTimeout`, `idleTimeout`, `maxLifetime`,
  `leakDetectionThreshold`, increased pool size to 20.
- **`StatusPages`** error envelope is now stable (`errorCode`, `message`, `requestId`).
  Stack traces no longer leaked to clients; logged server-side only.
- **`gradle.properties`** — removed hardcoded JBR path; modules use `jvmToolchain(21)`.
  Configuration cache enabled.

### Fixed
- Critical UX copy bug in `CriticalIllnessScreen` ("Why declare pre-existing
  diseases?" → "Why declare critical illnesses?").
- `otpError` is now displayed to the user on the OTP screen (previously declared but
  never rendered).
- GST line item now visible in `PlanSummaryScreen`, `PaymentScreen`,
  `CalculatorScreen`.
- Aadhaar / PAN are masked at every UI render (last 4 only).
- Payment screen now requires explicit T&C acceptance before "Pay" is enabled.

### Security
- Removed plaintext DB password from source.
- OTP brute-force window narrowed from "infinite tries forever" → "5 tries per OTP,
  5 sends per hour per mobile, 5-min code expiry".
- Bank account / Aadhaar / PAN visibly masked in UI; raw values still in transit and
  at rest pending Phase 2 (column-level encryption + KMS).

## [0.0.1] — 2026-04-?? — first commit
- Initial KMP scaffolding, pricing engine, server, desktop, buy-online journey.

[Unreleased]: https://example.com/compare/v0.1.0...HEAD
[0.1.0]: https://example.com/compare/v0.0.1...v0.1.0
[0.0.1]: https://example.com/releases/tag/v0.0.1
