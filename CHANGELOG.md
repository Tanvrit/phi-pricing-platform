# Changelog

All notable changes to this project will be documented in this file.

The format is based on [Keep a Changelog](https://keepachangelog.com/en/1.1.0/),
and this project adheres to [Semantic Versioning](https://semver.org/spec/v2.0.0.html).

## [Unreleased]

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

[Unreleased]: https://example.com/compare/v0.0.1...HEAD
[0.0.1]: https://example.com/releases/tag/v0.0.1
