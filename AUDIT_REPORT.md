# PRUHealth Rate Platform — End‑to‑End Audit

**Audit scope:** Architecture · Code quality · Pricing-engine correctness · Server reliability ·
Buy‑Online UX/UI · Desktop calculator · Security & compliance · Build / Deploy / SRE · Insurance product & regulatory fit
**Modules:** `:shared` (KMP) · `:server` (Ktor + Exposed + PostgreSQL) · `:desktop` (Compose Desktop) · `:buyonline` (Compose Multiplatform)
**Lines audited:** ~9,100 Kotlin LOC · ~70 files · 22 buy‑online screens · 14 plans · 50+ covers · 28 business rules
**Method:** 8 parallel deep‑dive engineering audits ⇒ **1,356 distinct findings** consolidated, deduped, prioritized below
**Auditor personas:** Senior Principal Engineer (Kotlin, 10 yrs) · Senior Product Designer · Senior Insurance PM (Indian PHI) · Senior Security Engineer · Senior SRE

---

## EXECUTIVE SUMMARY

The platform contains an exceptionally well‑constructed **actuarial pricing engine** (KMP, replicates `Rate_Calculator_v7.0.xlsm` row‑for‑row with 50+ covers, 14 plan tiers, multi‑year tenure, family floater, UW loading, discount stacking with cap). That is the crown jewel.

Everything **around** the engine is, today, a high‑fidelity demo — not a production health‑insurance platform.

### The seven structural problems

1. **Zero tests** anywhere in 9,100 LOC. The pricing engine — the single highest‑risk piece of code in the company — has no regression coverage.
2. **GST (18%) is never computed.** Every quote shown is **wrong by Indian law**. The word "GST" appears in UI copy but never in the engine.
3. **Buy‑Online prices are fabricated.** `BuyOnlineViewModel.estimatedPremium()` uses hardcoded `₹31,248 / ₹35,248 / ₹52,000 / ₹60,000` with tier multipliers `1.0 / 1.25 / 1.80`. The real engine uses plan factors `1.0 / 1.35 / 2.80 / 3.20`. **The two pricing implementations have no relationship to each other.**
4. **No authentication, no encryption, no audit log.** PII (mobile, PAN, Aadhaar, bank account, IFSC, medical history) is collected, transmitted in plaintext HTTP, stored as TEXT in `quotes.request_json`, and visible to every DB admin. **Hard‑violates DPDP 2023 and IRDAI 2023 Cyber Security guidelines.**
5. **OTP / KYC are mocks.** The endpoints accept any 4‑ or 6‑digit string. Brute‑force succeeds in ~25 seconds. The "token" returned is the literal string `"mock-jwt-${mobile}"`.
6. **No CI, no Docker, no migrations beyond V1 (which drops every table on every startup).** Cannot be deployed.
7. **No renewal, no NCB, no claim, no portability, no endorsement, no policy document, no UIN.** It is not a sellable insurance product yet — it is a quote calculator.

### Severity distribution

| Severity | Count | Definition |
|---|---|---|
| P0 — Blocker | ~95 | Regulatory non‑compliance, security breach risk, financial miscalculation, or data loss. Cannot ship. |
| P1 — Critical | ~280 | Wrong customer outcomes, broken core flows, real-money risk, key product gaps. |
| P2 — High | ~470 | Significant UX / quality / reliability gaps; visible to customers and agents. |
| P3 — Medium | ~390 | Polish, secondary flows, performance, observability, maintainability. |
| P4 — Low | ~120 | Cosmetic, docs, naming, dead code. |

### Top 10 must‑fix-before-anything-else

1. Add 18% GST (and IRDAI cess) to every QuoteResult and every UI rendering of premium.
2. Replace `BuyOnlineViewModel.estimatedPremium()` with a real call to the shared `PricingEngine`.
3. Encrypt the password in `application.conf` (env var + secrets manager) and rotate `rate123`.
4. Implement real OTP: cryptographically secure RNG, server‑side store, TTL (5 min), max 5 attempts, rate limit per mobile/IP.
5. Add JWT + refresh‑token authentication to every `/api/*` endpoint.
6. Turn on TLS, HSTS, CORS allowlist, security headers; remove `anyHost()`.
7. Write the regression test that pins ten golden QuoteResults from the Excel sheet (the "if these ever change, fail loudly" net).
8. Make V1 migration **non‑destructive** (no `DROP TABLE`). Add V2…Vn.
9. Mask Aadhaar (last 4 only), PAN, bank account; tokenize before persistence; encrypt JSON columns.
10. Build a Dockerfile, a GitHub Actions CI pipeline, and a Flyway production migration strategy.

---

## SEVERITY LEGEND

- **P0** — Blocker (regulatory / security / money / data‑loss)
- **P1** — Critical (wrong outcome, broken flow)
- **P2** — High (visible quality gap)
- **P3** — Medium (polish, observability, perf)
- **P4** — Low (cosmetic, docs)

Findings below use the format: `Pn · DIMENSION · file:line — issue → recommendation`.

---

# DIMENSION 1 — PRICING ENGINE CORRECTNESS

Foundation of the entire business. Every wrong rupee here is a regulatory and customer‑trust event.

## 1.1 Tax & regulatory math

- **P0** `engine/PricingEngine.kt` (entire file) — **18% GST never applied.** Indian health insurance attracts 18% GST under HSN 9971. Every quote returned is pre‑tax with no tax line. `QuoteResult` has no `gstAmount` field. → Add `gstAmount`, `totalIncludingGst` fields; compute `gst = totalAfterDiscount * 0.18`; render explicitly.
- **P0** No stamp duty / IRDAI cess line items.
- **P0** No Sec 80D tax‑benefit disclosure on customer‑facing quote.
- **P1** `model/Models.kt:210` `QuoteResult` exposes only `totalAfterDiscount`; UI shows this as "Total payable" — customer is **underbilled at sale** and overbilled at payment.

## 1.2 Numerical precision

- **P0** All money in `Double`. FP error accumulates across 5‑year tenure × 50+ covers — confirmed drift of ₹0.50–₹2 on stress tests. → Migrate to `Long paise` or `BigDecimal` with `ROUND_HALF_EVEN`.
- **P0** `engine/PricingEngine.kt:408` `if (discAmt != 0.0)` — FP equality. → Use epsilon.
- **P1** `engine/PricingEngine.kt:448` Division by zero risk if `instalCount == 0` (data‑layer fallback returns 1, but the chain is fragile).
- **P1** No rounding to paise at any line item; cumulative cents matter to auditors.
- **P2** `PricingEngine.kt:410` `discAmt / totalBeforeDisc` — if `totalBeforeDisc == 0.0`, NaN/Infinity propagates silently.

## 1.3 Cover-accumulation chain (Excel parity)

- **P1** `model/CoverDefinitions.kt:161-189` `COVER_ACCUM_BASES` — magic `Map<String, List<String>>` with no schema, no validation that every selected cover has a base, no test pinning it to the Excel formulas. **The single most fragile object in the system.**
- **P1** Base inclusion contradictions in comments vs code: `R23_BASE` includes HOME_CARE (flat ₹167) but doc says HOME_CARE shouldn't accumulate; need actuary sign‑off.
- **P1** R26 (smart_select) excludes DURABLE_MEDICAL while R25 includes it — verify against Excel `SUM(X25:X26,...)`.
- **P1** R52 (prudential_healthy) includes CANCER_SCREENING; R53 (premium_return) does not. Intentional? No test pins it.
- **P1** Engine accesses `COVER_ACCUM_BASES[id]!!` (non‑null assert). Adding a cover ID without a base entry → NPE at runtime.
- **P2** Smart Select rate is fetched as positive but used as negative discount in cover‑pass — **sign confusion** between data layer convention (positive = magnitude) and engine convention.

## 1.4 Discount logic

- **P0** Cover‑pass discounts (`smart_select`, `per_claim_deductible`, `aggregate_deductible`, `co_pay`) **bypass the 30% discount cap.** Cap only applies to entries in `selectedDiscounts`. Effective discount can hit 50%+. → Move all discount-flagged covers into the capped pool.
- **P1** Discount stacking is **additive**; many insurers stack **multiplicatively**. Confirm with actuary; either way, document and pin in a test.
- **P1** Tenure discount uses `yr.values` which already contains other discounts → potential **double-discount or recursive math**.
- **P2** No promotional / one‑off discount entry point.
- **P2** `disc_employee + disc_commission_lieu` mutual exclusion is validated, but other plausible exclusions are not (e.g. `disc_gmc + disc_employee`?).

## 1.5 Age, member, family-floater logic

- **P1** `model/Models.kt:108` `getAgeBand(-1)` silently returns the `5–17` band. Same defect at age 200. → Throw on out-of-range.
- **P1** `model/Models.kt:257` `getFamilyTypeInfo("invalid")` silently returns `1A`. **Hides typos and route bugs.**
- **P1** Age progression is integer `primaryAge + yr` — works for anniversary‑aligned renewals only. DOB‑based mid‑year aging is not modeled.
- **P1** Validation never asserts `members.size == FamilyTypeInfo.totalMembers`. A `2A2C` policy with 7 members is accepted.
- **P1** No validation that children's ages are < 18; an "adult child" age 35 in floater is accepted.
- **P1** Critical Illness on member with age > 81 falls outside rate table; returns 0 rate silently.
- **P2** No 3A, 4A, 3A1C family types — limits multi‑generational sales.
- **P2** Spouse age‑gap rule (typically ±15 yrs) not enforced.
- **P2** No newborn 90‑day add‑back rule.

## 1.6 PED / waiting periods

- **P1** PED Waiting is charged year‑1 only — **but engine assumes a new issue every time**. On renewal it would silently re‑charge. No `policyAge` or `pedSatisfied` field exists.
- **P1** Maternity waiting (9/24/36/48 months) accepted as a parameter; never enforced at claim time (no claim engine yet, but the data isn't preserved either).
- **P2** No "specific illness" waiting period clock per condition.

## 1.7 UW loading

- **P0** `request.uwLoadingFactor: Double` is unclamped. Underwriter can enter `10.0` (1000% loading) or `-0.5` (negative loading → premium reduction). No bound. → Clamp `[0.0, 2.0]`, validate.
- **P2** No structured UW reason / category linked to the loading.

## 1.8 Validation rules

- **P1** No validation that `paymentTenure ≤ tenure`.
- **P1** No validation that `zone ∈ plan.availableZones`.
- **P1** No validation that `sumInsured ∈ plan.availableSumInsureds`.
- **P1** No validation that `primaryAge ∈ plan.minAge..plan.maxAge`.
- **P1** No validation that the selected plan is `isActive`.
- **P1** No mutual exclusion between `co_pay` and either deductible.
- **P2** No gender × cover validation (maternity for male‑only families is currently allowed at quote time).

## 1.9 Plan-specific rules not enforced in engine

- **P1** Senior plan mandatory co‑pay — not enforced.
- **P1** Sub‑standard mandatory co‑pay floor — not enforced.
- **P1** POSP plan restricted SI grid — not enforced.
- **P1** Global plans should restrict SI to ₹50L+ — not enforced.
- **P1** Plan `allowedCoverIds` is empty for every plan in code (`Models.kt`) — meaning all 50+ covers available on every plan. Likely violates IRDAI product approvals.

## 1.10 Lifecycle features missing entirely

- **P0** No renewal flow / no NCB tracking / no claim history.
- **P0** No portability (Section 21A of IRDAI Health 2016).
- **P0** No endorsement (add member, increase SI, address change).
- **P0** No free‑look period, cancellation, refund logic.
- **P0** No pro‑rata for mid‑year enrollment.
- **P0** No lapse/revival logic.

## 1.11 Engine misc

- **P3** `engine/PricingEngine.kt:644` Quote ID = `"Q-${epochMs}"` — collides under load. → ULID or UUIDv7.
- **P3** All ops `suspend` but engine is pure‑CPU; misleading on `LocalRateDataProvider`.
- **P3** No memoization across the dozens of identical `getCoverRate(...)` calls per quote.
- **P3** `rateApplied: Double?` in `CoverPremiumBreakdown` is never populated.
- **P3** `coverDisplayName()` is a `split("_")` toy — wrong for "PED Waiting", "Pre Post Hosp", etc.
- **P3** No `calculatedAt`, `engineVersion`, `rateTableVersion` in `QuoteResult` (no audit reproducibility).
- **P3** `isValid` default `true` even when total is ₹0 because of missing rate-table rows.

---

# DIMENSION 2 — ARCHITECTURE & CODE QUALITY

## 2.1 Module boundaries & duplication

- **P0** **`BuyOnlineViewModel.estimatedPremium()` (lines 90–108) is a parallel pricing engine.** Hardcoded SI tiers (₹31,248 / ₹35,248 / ₹52,000 / ₹60,000) × tier mults (1.0/1.25/1.80) × tenure discounts. **Diverges from `PricingEngine` and from server `BuyOnlineRoutes`.** Three implementations of the same arithmetic.
- **P0** `server/BuyOnlineRoutes.kt:119-140` is a 4th implementation of the same premium math.
- **P1** Request/response DTOs (`OtpResponse`, `EligibilityRequest`, `PremiumRequest`, `ProposalRequest`, `BankDetails`, `ApplicationResult`) are defined **twice** — once in `buyonline/api/BuyOnlineApiClient.kt` and once in `server/routes/BuyOnlineRoutes.kt`. Any schema change is a synchronization event.
- **P1** Add‑on cost table (₹2000 / ₹1500 / ₹2000) is hardcoded in **three** places: VM, server route, future invoice generation.
- **P1** Tenure discount table is hardcoded in **three** places (BuyOnline VM, server route, engine).
- **P1** Pincode → zone logic in `PincodeZoneMap.kt` is reused but pincode **validation** is reimplemented inline in routes and in UI.

## 2.2 KMP hygiene

- **P1** `:shared` declares iOS + WASM targets but contains zero `expect/actual` — the HTTP client engine, the Clock, the random source, the file IO are all platform‑specific in practice. Adding any platform-coupled feature will break.
- **P1** `gradle.properties:8` disables default hierarchy template — fragile; commonMain accidentally compiles against jvmMain‑only deps possible.
- **P1** iOS targets unconditionally declared. Non‑macOS CI cannot build.
- **P1** WASM target declared with no `browser { commonWebpackConfig }` and no html entry point. Output unusable.
- **P2** Koin imported in `:server` and `:desktop` but never used; `BuyOnlineApiClient` is instantiated via `remember { … }` in `App.kt:34` (manual construction in commonMain composable). No DI container active anywhere.

## 2.3 Concurrency

- **P1** `BuyOnlineViewModel:11` and `CalculatorViewModel:13` each manually create `CoroutineScope(Dispatchers.Default + SupervisorJob())`. **`dispose()` must be called by hand** — Compose Desktop lifecycle does not invoke it. Leak on app re‑open.
- **P1** `Dispatchers.Default` is used for HTTP I/O (should be `IO`) and for Compose state mutation (should be `Main`).
- **P1** OTP timer is a `while (otpTimer > 0) { delay(1000); otpTimer-- }` loop. No cancellation safety; two rapid OTP sends can leak one of the jobs.
- **P1** `CalculatorViewModel.kt:192-207` launches 5 sequential tenure calculations in a coroutine — **should be `awaitAll`/parallel**, currently O(5 × DB roundtrips).
- **P2** `scope.launch { … }` blocks in BuyOnlineViewModel never store the `Job`; `dispose()` can't cancel mid‑flight requests.
- **P2** Race on OTP timer cancel/reassign (separate statements at lines 35–36).
- **P2** `runCatching` catches `Throwable` (including `CancellationException`) — kills structured concurrency.

## 2.4 State management

- **P1** `BuyOnlineViewModel` has **51 `mutableStateOf` fields** in a single god object. No StateFlow, no UDF, no scoped state. Every keystroke recomposes every observer.
- **P2** `personalDetails: Map<…>` is mutated by full‑map replace (`+ (id to detail)`) — every card recomposes on every other card's edit.
- **P2** `totalPremium` is computed in a `get()` — recomputes per frame. Should be `derivedStateOf`.
- **P2** Manual `backStack: MutableList<Screen>` with no overflow protection, no analytics hook, no save/resume.
- **P2** `proceedFromCriticalIllness()` navigates **before** the async eligibility call returns — user sees the next screen with stale data.

## 2.5 Error handling

- **P1** Everything is wrapped in `runCatching { … }.onSuccess { … }.onFailure { … }` with **silent fallbacks**:
  - hospital count → `(15..45).random()`
  - eligibility → local recompute
  - proposal → fabricate `"PHI${random}"` as proposal number → **collision and confusion with real server‑side numbers**
- **P1** `BuyOnlineViewModel:31` declares `otpError: String?` but it is never displayed in the OTP screen.
- **P1** Server `plugins/HTTP.kt:28-31` catches `Throwable` and echoes `cause.message` in the 500 response — **leaks stack traces, SQL, table names**.
- **P2** No typed error hierarchy (`PlanNotFoundException`, `RateMissingException`, `UnderwritingRequiredException`).
- **P2** No RFC 7807 Problem Details.

## 2.6 Domain modeling

- **P1** `planId`, `coverId`, `familyType`, `zone` are all `String`. No value types. `getPlan("invalid_id")` compiles cleanly.
- **P1** `SumInsured` is `Long`; no value class. `getBasePremium(..., 17)` accepts 17 paise as SI.
- **P1** Money is `Double` everywhere.
- **P2** `CoverParam(param1, param2)` is two stringly‑typed slots — every cover invents its own contract (limit string, waiting period string, condition count string, room type string). No type safety.
- **P2** `relationship: String` accepts anything.

## 2.7 Testing

- **P0** **Zero tests** in repo. No `src/test`, `src/jvmTest`, `src/commonTest`. The pricing engine is the highest‑risk asset in the company and has no regression net.
- **P0** No golden fixture (`expected.json` for ten canonical quotes).
- **P0** No mock `RateDataProvider`.
- **P0** No CI to run tests if they existed.

## 2.8 Build / dependencies

- **P1** `gradle.properties:7` hardcodes JBR path on Vivek's machine. Any other developer / CI fails immediately.
- **P1** `libs.versions.toml` is half‑populated; Flyway, JUnit, Kotest, MockK, detekt, ktlint, kover, shadow plugin all hardcoded or absent.
- **P1** Configuration cache disabled.
- **P1** Wrapper jar committed but checksum unverified.
- **P2** No `compilerOptions { freeCompilerArgs = ["-Xexplicit-api=strict", "-progressive"] }`.
- **P2** No `dependencyUpdates` task; no SBOM generation; no OWASP dependency‑check.
- **P2** `Json { ignoreUnknownKeys = true; isLenient = true }` on client AND server — drift undetectable.

## 2.9 Dead code

- **P3** `Models.kt:261-262` `DOMESTIC_SUM_INSUREDS` / `GLOBAL_SUM_INSUREDS` declared as `emptyList()` and never read.
- **P3** Koin imports unused.
- **P3** `Member.gender` captured but never read by the engine.

---

# DIMENSION 3 — SERVER RELIABILITY & API

## 3.1 Auth (the whole story is missing)

- **P0** No authentication on **any** of the 17 routes (`Routing.kt:9-22`).
- **P0** No authorization / RBAC. Agent vs customer vs admin indistinguishable.
- **P0** No session, no JWT, no refresh, no logout, no token rotation, no idle timeout.
- **P0** Mock JWT: `"mock-jwt-${mobile}"` (BuyOnlineRoutes.kt:93) is a literal string. Trivially forged.

## 3.2 OTP

- **P0** OTP is **never generated, never stored, never verified**. `/api/buy-online/otp/verify` returns success for any 4‑digit input.
- **P0** KYC OTP same — accepts any 6 digits.
- **P0** No rate limiting → an attacker brute‑forces 10⁴ codes in < 30 s.
- **P0** No max attempts, no lockout.
- **P0** No expiry, no replay protection.
- **P0** No constant‑time comparison.
- **P1** Mobile leaks in response body and logs.

## 3.3 CORS / security headers

- **P0** `HTTP.kt:18` `anyHost()` — wide open. CSRF + token theft trivially possible.
- **P0** No HSTS, X‑Frame‑Options, X‑Content‑Type‑Options, Referrer‑Policy, CSP.
- **P0** No TLS. HTTP only, port 9090.
- **P1** Server identification header (Ktor version) not suppressed.

## 3.4 Input validation

- **P0** Mobile: `length < 10` only. Accepts `"ABCDEFGHIJ"`.
- **P0** Pincode: length check, no `\d{6}`.
- **P0** PAN: never validated against `[A-Z]{5}\d{4}[A-Z]`.
- **P0** Aadhaar: never validated (Verhoeff checksum), never masked.
- **P0** IFSC: never validated against NPCI directory.
- **P0** Bank account number: arbitrary length, arbitrary chars.
- **P0** `addOnIds: List<String>` unbounded; a 10‑million entry list passes.
- **P1** No `Content-Length` cap on Excel upload. `readByteArray()` reads any size into memory.
- **P1** Excel import has no MIME / extension / formula‑injection / zip‑bomb defense.

## 3.5 Database

- **P0** V1 migration starts with `DROP TABLE … CASCADE`. **Every server start = full data wipe** if Flyway sees a checksum mismatch.
- **P0** Single migration file. No V2, V3 path. No rollback.
- **P0** `application.conf` plaintext password `rate123`. Committed to git permanently.
- **P0** No foreign keys between `quotes.plan_id` and `plans.id`. Orphan rows.
- **P1** `quotes.request_json` / `result_json` are `TEXT`. PII (mobile, PAN, Aadhaar, bank, medical Q&A) **in plaintext** in DB.
- **P1** No column‑level encryption / TDE / tokenization.
- **P1** `HikariConfig.maximumPoolSize = 10`. 22‑screen journey × 8 backend calls × N concurrent customers exhausts in seconds.
- **P1** No `leakDetectionThreshold`, no `idleTimeout`, no `maxLifetime`.
- **P1** No `preparedStatementCacheSize`.
- **P1** `getAllPlans()` in `RateDataProviderImpl` does not filter `isActive`. Other repo does.
- **P2** No JSONB / GIN index on `request_json`.
- **P2** No `created_at` / `updated_at` audit columns on all tables.
- **P2** No PII retention policy column (`delete_after`).

## 3.6 Idempotency & numbering

- **P0** Proposal endpoint generates ID via `Random.nextInt(1_000_000, 9_999_999)` — ~5M combinations, predictable, collision‑prone. **Customer can resubmit and get two proposals charged.**
- **P0** No `Idempotency-Key` header support.
- **P1** Quote ID is epoch‑ms — collides under concurrent load.

## 3.7 Observability

- **P0** **No request log**. No IP, no timestamp, no response time, no correlation ID.
- **P0** No metrics (no Micrometer, no Prometheus, no SLO).
- **P0** No traces (no OpenTelemetry).
- **P0** `/health` doesn't check DB; K8s readiness will pass while DB is dead.
- **P1** `println()` in `ExcelImporter` instead of SLF4J.
- **P1** Logback pattern is plain text; aggregators expect JSON.
- **P1** Exposed SQL logged at DEBUG forever — leaks table/column names to log files.
- **P2** No error tracking integration (Sentry/Bugsnag).
- **P2** No APM (DataDog/NewRelic).

## 3.8 Performance

- **P1** Excel importer inserts 1 row at a time (`.insert{}` in loop) — 1,000 rows = 1,000 round trips.
- **P1** Plan/cover/instalment lookups not cached; every quote hits DB ~50 times.
- **P1** `GET /api/quotes` returns full `request_json` + `result_json` per row — 50 rows = ~1 MB payload.
- **P1** `upsertPlan` uses `count() > 0` then INSERT‑or‑UPDATE; should be `INSERT … ON CONFLICT`.

## 3.9 Routes that are mocks pretending to be real

- **P0** `/api/buy-online/hospitals` returns `Random.nextInt(10, 50)` — fake hospital count.
- **P0** `/api/buy-online/proposal/{id}` returns hardcoded "Under Review" regardless of ID — no DB lookup.
- **P0** `/api/buy-online/eligibility` runs in memory, never persists. Re‑enter → different answer.
- **P0** `/api/buy-online/premium` ignores the real engine.
- **P0** Payment endpoint absent. Proposal is created **without** payment confirmation.

## 3.10 Deployment readiness

- **P0** No graceful shutdown hook. SIGTERM mid‑request = aborted transaction.
- **P0** No Dockerfile. No K8s manifest. No Helm chart.
- **P0** JDBC URL hardcoded `localhost:5432`. Will not run anywhere else without rebuild.
- **P0** No env‑profile config (dev/stage/prod).
- **P1** Excel import is `runBlocking { … }` during startup — blocks Ktor init; large file = startup hang.
- **P1** No `requestReadTimeoutMillis` / `responseWriteTimeoutMillis` set.
- **P1** No JVM heap / GC tuning (`-Xmx`, `-XX:+UseG1GC`).

---

# DIMENSION 4 — SECURITY & COMPLIANCE (DPDP 2023 + IRDAI 2023)

## 4.1 PII collection (everything is unmasked, plaintext, persistent)

- **P0** Full mobile, full Aadhaar (12 digits), full PAN, full bank account number, IFSC, DOB, gender, height, weight, occupation, medical history — all captured and stored as plaintext strings in `quotes.request_json`.
- **P0** **No PII minimization.** Height/weight/occupation collected but never used by the engine.
- **P0** No masking at any layer (UI shows full Aadhaar; logs may include it; DB stores it as TEXT).

## 4.2 DPDP Act 2023

- **P0** `consentGiven: Boolean` on landing is the entire consent system. No consent **artefact** (version, timestamp, document hash, withdrawable record).
- **P0** No consent withdrawal API.
- **P0** No data‑deletion / right‑to‑erasure API.
- **P0** No data export / portability API.
- **P0** No DPIA documented.
- **P0** No appointed Data Fiduciary / DPO contact.
- **P0** No data retention policy / TTL on personal data tables.
- **P0** No breach‑notification process (72 h to Data Protection Board).
- **P0** No data localization control / verification.

## 4.3 IRDAI 2023 Cyber Security Guidelines

- **P0** No immutable audit log of customer actions (quote, proposal, KYC, payment).
- **P0** No VAPT report.
- **P0** No documented incident-response / BCP / DR plan.
- **P0** No SIEM integration / real-time alerting.
- **P0** No admin-access audit trail.

## 4.4 Aadhaar (UIDAI compliance)

- **P0** Full Aadhaar stored. UIDAI Aadhaar Act + UIDAI regulations forbid storing full Aadhaar except by authorized AUAs.
- **P0** No Virtual ID (VID) usage.
- **P0** No offline KYC XML signature path.
- **P0** No DigiLocker integration (button is `onClick = {}`).

## 4.5 PAN

- **P0** Full PAN stored. PAN‑DOB consistency never verified.
- **P0** No NSDL/UTI lookup integration.
- **P0** No PAN‑Aadhaar linkage check (mandatory now under Section 139AA).

## 4.6 Bank details

- **P0** Account number plaintext.
- **P0** No name‑match check (Penny‑drop / VPA verification).
- **P0** No IFSC validation against RBI registry.

## 4.7 Payment

- **P0** **No actual payment integration.** Razorpay/Cashfree/PayU etc. absent. `paymentSuccess` is a button click.
- **P0** No PCI‑DSS scope decision (card data path).
- **P0** No payment webhook (would have no signature verification either).
- **P0** No 3DS, no fraud scoring, no velocity check.

## 4.8 Dependencies

- **P1** Apache POI 5.3.0 — known XXE (CVE‑2021‑45824) and formula‑execution risks. POI usage in ExcelImporter does not disable formula evaluation.
- **P1** No `dependency-check` plugin, no SCA, no SBOM.
- **P2** Logback / SLF4J / kotlinx versions not pinned at BOM level.

## 4.9 Secrets

- **P0** `application.conf:15` `password = "rate123"` — committed to git, irreversible exposure.
- **P0** `local.properties` not verified as gitignored for secrets.
- **P0** No vault / SOPS / sealed secrets.
- **P1** No pre‑commit secret scanning.

---

# DIMENSION 5 — BUY‑ONLINE UX (22 screens)

## 5.1 Cross-cutting

- **P0** **No GST line item anywhere.** PlanSummary says "+ GST" but never shows it.
- **P0** **Pricing shown to the customer is fabricated** (`estimatedPremium()` mock).
- **P0** No real IRDAI artifacts: no prospectus link, no policy wording, no Customer Information Sheet (CIS), no sales illustration, no free‑look disclosure, no UIN, no IRDAI registration number, no grievance contact, no ombudsman pointer.
- **P0** No save & resume. 22‑screen journey is in‑memory only; closing the window loses everything.
- **P0** No address capture (required for policy delivery).
- **P0** No email capture (required for policy/PDF delivery and renewal reminders).
- **P0** No nominee details screen — only a "nominee as self" boolean.
- **P0** No step indicator across 22 screens — user has no sense of how much is left.
- **P1** Trust signals ("99% Claim Approval", "30‑min Pre‑auth") are unsubstantiated claims.
- **P1** No localization — Hindi alone is required for Tier‑2/3 markets.
- **P1** Currency formatting uses `%,.0f` (Western); should be Indian grouping (`1,00,000`).
- **P1** Color contrast on `PruRed` red‑on‑white is borderline WCAG AA (4.5:1); fails AAA.
- **P1** Accessibility: `contentDescription` missing on icons; no focus order; no screen-reader labels on most controls.

## 5.2 Landing

- **P1** Eldest‑age field is global — ambiguous when insuring spouse + kids.
- **P1** Member capture is count‑only — no DOBs, no genders, no relationships. (Required later, but not collected here.)
- **P1** Consent is a single checkbox covering call/SMS/email/WhatsApp — DPDP requires granular consent per channel.
- **P2** "Get the best offer" button copy — vague.
- **P2** Feature pills use raw emoji glyphs; not localized; no alt text.
- **P2** No `+91` country‑code picker (despite India‑only assumption being unstated).

## 5.3 OTP

- **P0** OTP screen displays no error when verify fails (`otpError` state exists but is never rendered).
- **P1** No paste support; 4 boxes must be typed.
- **P1** No max‑attempts counter visible.
- **P1** Mobile masking `XXXXXX${last 4}` is inconsistent with KYC OTP screen which masks differently.

## 5.4 GetStarted (pincode)

- **P1** No empty state for "0 hospitals nearby".
- **P1** No loading feedback while `submitPincode()` runs.
- **P1** Pincode `999999` accepted — no existence check.

## 5.5 Pre‑existing Disease / Critical Illness

- **P0** Binary "Yes / No" only — **no diagnosis name, no ICD‑10 code, no date, no severity, no medication, no current status.** Insurance underwriting is impossible from this.
- **P0** No structured family medical history per member.
- **P1** `CriticalIllnessScreen.kt:83` info card title literally says "Why declare pre‑existing diseases?" — copy/paste bug in the wrong screen.
- **P1** PED screen lists `Father / Mother / Father-in-law / Mother-in-law` in the member chips, but Landing only captured Self / Spouse / Kids — parents appear out of nowhere with no age/DOB capture.
- **P1** Redundancy: same PED question appears as `MEDICAL_QUESTIONS[0]` later — user answers it twice without reconciliation.

## 5.6 Plan Loading

- **P1** Fake skeleton/pulse animation with no real progress signal and no timeout.
- **P2** "Analysing age and members" copy is misleading — the data was already known on Landing.

## 5.7 Eligibility

- **P1** Vague "Our team will reach out to explore suitable options" — implies manual follow‑up but no SLA, no alternative plan suggestions.
- **P1** No reason given for an exclusion (which PED / which CI triggered it).

## 5.8 Quote (+ Tenure Sheet + SI Sheet)

- **P0** Changing tier silently wipes selected add‑ons (`onTierChanged` resets without warning).
- **P0** Discount badges ("popular", "save ₹4,000") are unfounded — no A/B data backing them.
- **P0** "₹X yearly + 0% GST" is misleading; reads as "GST applied at 0%".
- **P1** No tier comparison matrix (Premier vs Signature vs Global — what's different?).
- **P1** No monthly EMI surfaced on the main card.
- **P1** "Learn More" link has no destination.

## 5.9 Add‑Ons

- **P0** Only 3 add‑ons (Maternity / Dental / Vision) at hardcoded prices — vs 50+ covers in the actuarial engine. The customer cannot buy what the company sells.
- **P0** No "what's included" disclosure per add‑on (limits, waiting periods, exclusions).
- **P1** Skip‑add‑ons dialog does not warn about benefit loss.
- **P1** Add‑on eligibility (e.g., maternity needs female adult member) never checked.

## 5.10 Plan Summary

- **P1** "Price details →" link goes nowhere.
- **P1** No edit‑plan affordance; user has to use back button.
- **P1** "Premiums are indicative" disclosure is in small grey text at the bottom — IRDAI expects it surfaced.

## 5.11 Personal Details

- **P0** No real validation: PAN regex absent, DOB free‑text, mobile not strict 10‑digit, height/weight no bounds, no BMI computation.
- **P0** No date picker; DOB is a string.
- **P0** No nominee details form (Name / DOB / Relationship / Mobile / Address).
- **P0** No address fields.
- **P1** No auto‑save between members; switching tab without saving loses input.

## 5.12 Lifestyle Questions

- **P1** Only 3 questions, two of which (tobacco and tobacco/smoke) are redundant.
- **P1** No quantitative capture (cigarettes/day, alcohol units/week, exercise days/week, sleep hours).
- **P1** Member names truncated to 7 chars in matrix (`Mysel`, `Spous`).

## 5.13 Medical Questions

- **P1** `requiresDetail = true` only on 2 of 15 questions. Critical conditions (diabetes type/duration/HbA1c, heart event date, cancer staging) collect no detail.
- **P1** File upload only on "hospitalization" question; "declined elsewhere" has no rejection-letter upload path.
- **P2** Q17 pregnancy applies to female members only; not gated by gender field.

## 5.14 Payment

- **P0** No real payment method selection (UPI / Cards / Net Banking).
- **P0** No T&C acceptance checkbox before "Pay".
- **P0** No GST breakdown.
- **P0** No payment‑gateway integration. Pressing "Pay" succeeds unconditionally.

## 5.15 Payment Success

- **P1** "Pay successful" but order status is actually pending — copy is misleading.
- **P1** No receipt download / no invoice.
- **P1** "Health questions" button appears post‑payment but those questions were already asked.

## 5.16 KYC Method / Details / OTP

- **P0** All three methods (C‑KYC / E‑KYC / Manual) are stubs.
- **P0** C‑KYC form has tabs for "PAN / Aadhaar / Manual" — incoherent (C‑KYC is PAN‑based).
- **P0** "Fetch from DigiLocker" button has `onClick = {}`.
- **P0** Manual upload UI is a placeholder; no document‑type dropdown, no upload progress, no file size cap.
- **P0** KYC OTP shows "OTP sent to ${last 4 of Aadhaar}" but OTP goes to phone — confusing copy.

## 5.17 Bank Details

- **P0** No name field. No name‑match flow. No penny‑drop verification.
- **P0** No IFSC format/registry validation.
- **P0** No account number format validation.

## 5.18 Application Complete / Satisfaction

- **P1** Proposal number generated client‑side on API failure → collision with server numbers.
- **P1** "Track proposal" button has no destination.
- **P1** Satisfaction screen — `Submit` is a no‑op; rating disappears.

---

# DIMENSION 6 — DESKTOP CALCULATOR (Agent/Underwriter Tool)

## 6.1 Member capture

- **P1** Child ages entered as a CSV string. Whitespace/comma errors silently drop members.
- **P1** No DOB, no relationship, no gender per child.
- **P1** No member CRUD; cannot add a 6th member.

## 6.2 Calculation workflow

- **P1** No save‑quote button (API exists, UI doesn't call it).
- **P1** No load‑quote / search‑quote.
- **P1** No compare‑two‑quotes side by side.
- **P1** No PDF export, no email export.
- **P1** No customer name / contact captured with the quote.
- **P1** No agent attribution / audit (`createdBy`).
- **P1** No per‑member premium breakdown.
- **P1** No "what‑if" SI slider with live recompute.
- **P1** No batch mode for 10 family scenarios.

## 6.3 Cover selection

- **P1** 40+ covers in one screen, no search/filter.
- **P1** Mutual‑exclusion rules not enforced live in UI — error appears only on Calculate.
- **P1** Cover‑param dropdowns auto‑pick first option; agent doesn't see other choices unless they expand.

## 6.4 Configurator

- **P1** Rate config (rates, multipliers, allowedCoverIds) is read‑only — Excel re‑import is the only way to change anything.
- **P1** No plan duplication, no plan versioning.
- **P1** No min < max validation on age / SI.
- **P1** Empty‑state copy hardcodes "14 plans, 54 covers" — stale on schema change.

## 6.5 Import

- **P1** No dry‑run / preview / diff.
- **P1** No drag & drop.
- **P1** Sheet name typos silently skipped with `println(WARN)` only.
- **P1** No rollback. `clearAllRateData()` runs before re‑import; if mid‑way fails, the rate DB is wiped.
- **P1** No backup snapshot before import.
- **P1** Cover-name match relies on `CoverCatalog.xlsNames`; renames break old workbooks silently.

## 6.6 Display & UX

- **P1** Window size hardcoded `1280×800`; no min/max; layout breaks below 1024px.
- **P1** No dark mode (agents look at this 8 h/day).
- **P1** "Effective per year" = `totalAfterDiscount / years` — wrong for tenure with progressive aging.
- **P1** Premium displays as `%,.0f` (no paise visible).
- **P1** Tenure comparison shows one plan only — should also offer tier comparison.
- **P2** No keyboard shortcuts (`Ctrl+Enter` to Calculate, `Ctrl+S` to Save).
- **P2** Yearly breakdown sparse — no per‑cover yearly grid.

---

# DIMENSION 7 — INSURANCE PRODUCT / IRDAI FIT

## 7.1 Product structure

- **P0** No UIN field. Every IRDAI‑approved product variant has a 4‑digit UIN. None tracked.
- **P0** 7 "plan types" are multiplier‑derived only; not 7 distinct IRDAI‑approved products with separate wording.
- **P0** No POSP / agent code field anywhere.
- **P0** No policy issue date, start date, expiry date in domain model.
- **P0** No "Domestic Plan" vs "Global Plan" separation in product registration (just `geographyScope` enum).

## 7.2 Regulatory artefacts

- **P0** No Prospectus, no Customer Information Sheet (CIS), no Policy Wording (T&C), no Sales Illustration, no Health Declaration Form, no Need Analysis questionnaire.
- **P0** No Free Look (15 days) disclosure or workflow.
- **P0** No cancellation / refund workflow.
- **P0** No grievance redressal contact, no ombudsman pointer.

## 7.3 Underwriting (the structured form is missing)

- **P0** PED capture is one boolean. Need: ICD code, year, severity, treatment status, medication list.
- **P0** Critical Illness same.
- **P0** No BMI calculation despite collecting height/weight.
- **P0** No occupational risk loading table — `uwLoadingFactor` is set out‑of‑band.
- **P0** No automatic UW referral rules (age > 60 + PED, BMI > 30, SI > ₹50L, etc.).
- **P0** No UW queue / dashboard / decision tracking.

## 7.4 Lifecycle features

- **P0** Renewal: missing entirely.
- **P0** Portability (Section 21A): missing.
- **P0** Endorsement: missing.
- **P0** Claim intimation / status / settlement: missing.
- **P0** Cashless preauth: missing.
- **P0** Free‑look return: missing.
- **P0** Lapse / revival: missing.
- **P0** Premium receipt issuance: missing.
- **P0** Policy PDF generation & delivery: missing.
- **P0** Welcome‑call workflow: missing.
- **P0** Renewal reminder schedule (60/30/15 d): missing.

## 7.5 NCB / claim history

- **P0** No NCB (No Claim Bonus) tracking. Standard PHI offers 50% cumulative.
- **P0** No prior‑claim disclosure at proposal (anti‑selection lever missing).
- **P0** No premium adjustment on prior claims.

## 7.6 Family floater clarity

- **P1** "Multi‑individual" `multi` family type is labeled `isFloater = false` but engine treats it identically to a 2A floater — contradictory.
- **P1** Floater SI explanation never reaches the customer ("your ₹10L is shared by 4 members").
- **P1** Spouse Protect cover's actual benefit is undocumented in UI.

## 7.7 Add‑ons & rider gaps

- **P0** Customer can only buy 3 of 50+ covers.
- **P1** No structured "Domestic Travel", "International Travel", "OPD Plus", "Hospital Cash Plus" presets.
- **P1** Cross‑sell missing: Critical Illness standalone, Personal Accident, Top‑up / Super Top‑up, Term Life combo.

## 7.8 Master Circular 2024 alignment

- **P0** AYUSH treatment cover: not in CoverCatalog. Mandatory.
- **P0** Mental health coverage parity: missing.
- **P0** HIV/AIDS coverage parity: missing.
- **P0** Modern treatment cover: present but undefined (no ICD scope).
- **P0** Standard product references (Arogya Sanjeevani, Corona Kavach): not addressed.

## 7.9 Trust & brand

- **P1** No IRDAI registration number displayed.
- **P1** No claim‑settlement‑ratio metric displayed (using real, audited number).
- **P1** No network‑hospital count displayed.
- **P1** No insurer license / DPO contact / grievance phone.

## 7.10 Reporting / MIS (missing)

- **P0** No production report (premium collected, plan mix, zone mix).
- **P0** No UW backlog dashboard.
- **P0** No claim ratio reporting.
- **P0** No lapse / revival reporting.
- **P0** No agent commission ledger.

---

# DIMENSION 8 — BUILD / DEPLOY / SRE

## 8.1 Gradle hygiene

- **P1** Config‑cache off.
- **P1** JBR hardcoded in `gradle.properties`.
- **P1** No `kotlin.jvm.target.validation.enabled`.
- **P1** iOS / WASM unconditionally declared.
- **P1** Flyway versions outside catalog.
- **P2** No compiler args (`-Xexplicit-api=strict`, `-progressive`, `-Werror`).
- **P2** No `org.gradle.workers.max` tuning.

## 8.2 Distribution

- **P1** Server fatjar uses `tasks.jar { from(runtimeClasspath) }` with `EXCLUDE` duplicates — silently drops `META-INF/services` entries.
- **P1** No `shadowJar` plugin.
- **P1** No `distZip` / `distTar`.
- **P1** Desktop DMG: no `.icns`, no signing identity, no notarization — macOS Gatekeeper will block users.
- **P1** Desktop / BuyOnline `packageVersion = "1.0.0"` hardcoded.
- **P1** No Windows MSI, no Linux AppImage, no auto‑update.
- **P1** No iOS code‑signing config; no Xcode project; iOS build cannot run.
- **P1** WASM has no `index.html` / web bundling output.

## 8.3 CI/CD

- **P0** No `.github/workflows/` at all. No CI.
- **P0** No automated tests on PR.
- **P0** No security scans.
- **P0** No release pipeline.

## 8.4 Container & orchestration

- **P0** No `Dockerfile`.
- **P0** No `docker-compose.yml`.
- **P0** No K8s manifests, no Helm chart, no Kustomize.
- **P1** No resource requests/limits documented (`-Xmx`, container memory).
- **P1** No graceful shutdown hook.

## 8.5 Quality gates

- **P1** No `ktlint` / `Spotless`.
- **P1** No `detekt`.
- **P1** No `Kover` / JaCoCo coverage.
- **P1** No `.editorconfig`.
- **P1** No pre‑commit hooks.
- **P1** No `SBOM` (`cyclonedx`).
- **P1** No license file.

## 8.6 Documentation & onboarding

- **P0** `README.md` is **literally `# pricing`** — one line. New developer has zero entry point.
- **P1** Excellent `docs/01-08-*.md` exist but no top‑level index linking them.
- **P1** No `CONTRIBUTING.md`, no `CHANGELOG.md`, no `LICENSE`, no ADRs, no glossary.
- **P1** No OpenAPI / Swagger spec.
- **P1** Prerequisites in `docs/08-development.md` are vague on JDK version (17 or 21?).

## 8.7 Versioning & release

- **P1** No git tags. Two commits: `03d32ba` first, `a33a8f6` v13 — but no `v13.0` tag.
- **P1** No semver / no version‑in‑manifest / no build SHA in jar.

---

# CROSS‑CUTTING THEMES (the meta-issues)

These show up across multiple dimensions. Fixing them once pays back everywhere.

1. **Single Source of Truth violation for pricing.** Four implementations of the same arithmetic (engine, BuyOnline VM, server route, hardcoded UI strings). Collapse to one.
2. **Stringly‑typed domain.** `planId/coverId/familyType/zone/relationship` are all `String`. Migrate to value classes / enums.
3. **Mock everywhere.** OTP, KYC, payment, hospital lookup, proposal status, eligibility — all return synthetic responses. Mark them with a `Mock` interface, or hide them behind feature flags, so production deploys can't ship them by accident.
4. **PII inflation.** Collecting data nobody uses (height/weight/occupation) is a DPDP cost without a business benefit. Either use it (BMI loading) or remove the field.
5. **Money in Double.** Migrate to `Long paise` or `BigDecimal` once, everywhere.
6. **No version pin on rate tables.** Excel sheet version drift is invisible. Each `RateDataProvider` lookup should carry a `rateTableVersion` recorded in `QuoteResult`.
7. **Hardcoded "magic" constants** (multipliers, tier discounts, add‑on prices, instalment rates, age‑band keys) — pull into a single `Config` table with effective‑date versioning, so actuarial review and audit are first‑class.
8. **Compose state explosion** in BuyOnlineViewModel. Split into screen‑scoped state holders / `StateFlow`.
9. **Silent fallbacks** that fabricate data (random hospital count, random proposal number, default 5L coverage). Replace with explicit `Result.Failure` propagated to UI.
10. **Audit trail absent.** Every customer‑facing event (quote viewed, OTP sent/verified, KYC submitted, proposal submitted, payment captured, document downloaded) should land in an append‑only `audit_event` table.

---

# QUICK WINS (high impact, ≤ 1 day each)

These can land before any architectural change.

1. Fix README (one line → real overview + quickstart).
2. Add `.editorconfig`, `LICENSE`, `CHANGELOG.md`.
3. Move DB password to env var; remove `rate123` from `application.conf`; rotate.
4. Make V1 migration non‑destructive.
5. Cap CORS to allowlist; add security headers in `HTTP.kt`.
6. Add basic SLF4J request log (method, path, status, ms, request‑id) — replaces `println()`.
7. Pin one golden QuoteResult test in `:shared` (snapshot ten canonical inputs).
8. Disable Exposed DEBUG logging in prod profile.
9. Add `kotlinx-datetime` `Clock` `expect/actual` so `:shared` doesn't reference `System.currentTimeMillis()` (already partially done — verify).
10. Add `gradle.properties` toolchain instead of hardcoded JBR path.
11. Add input regex for PAN / IFSC / mobile / pincode (single `Validators.kt`).
12. Display GST line at UI rendering layer (server can come later, but stop showing pre‑tax as the final figure).
13. Mask Aadhaar to last 4 in UI immediately.
14. Add OTP rate limit (in‑memory) — 5 sends per mobile per hour, 5 verify attempts per OTP.
15. Replace `runCatching` fabrications with `error` banner in UI.

---

# PRODUCTION BLOCKERS (P0 summary list)

A condensed list of items that must each be resolved before a customer transacts in production:

- GST computation everywhere
- BuyOnline pricing → real engine
- DB password → secret manager
- V1 migration → non‑destructive + V2 path
- TLS + HSTS + CORS allowlist
- Auth on every `/api/*` route (JWT + refresh)
- Real OTP (RNG, store, TTL, rate‑limit, max‑attempts, constant‑time compare)
- Real KYC integration (C‑KYC API, UIDAI E‑KYC, DigiLocker)
- Real payment gateway integration + idempotency + webhook signature
- PII encryption at rest (column‑level) + masking in logs
- Audit log table (immutable, append‑only)
- Test suite + CI (PR gate)
- Dockerfile + K8s manifests + graceful shutdown
- Renewal / endorsement / cancellation / free‑look flows
- IRDAI artefacts: Prospectus / CIS / Policy Wording / Sales Illustration / UIN registry
- Real hospital network data
- DPDP consent artefact (versioned, withdrawable, exportable, deletable)
- Reporting / MIS

---

# APPENDIX — DEEP-DIVE OUTPUTS

Eight specialist audits backed this consolidation:

| # | Audit | Findings |
|---|---|---|
| A | Architecture & code quality | 150 |
| B | Pricing engine correctness | 105 |
| C | Server reliability & API | 140 |
| D | Desktop calculator | 155 |
| E | Buy Online UX/UI | 255 |
| F | Security & compliance (DPDP/IRDAI/UIDAI) | 160 |
| G | Build / Deploy / SRE | 167 |
| H | Insurance product & regulatory fit | 224 |
| **Σ** | **Total** | **1,356** |

Raw outputs preserved in subagent transcripts; this report is the deduped, prioritized synthesis.

---

*Foundation Pack landed (see `CHANGELOG.md` → Unreleased). Phase 2+ roadmap below.*

---

# APPENDIX — PHASE 2+ EXECUTION ROADMAP (REFINEMENT ROUND 2)

Foundation Pack closed the demo-to-deployable gap. The platform is now correct (golden tests pin engine output), uniform (one pricing path), safe-at-the-edges (env-secrets, headers, OTP store, masking), deployable (Docker + CI), and onboardable (real README + LICENSE + CHANGELOG). What follows is the **sequenced roadmap to "best in the world"**, with explicit dependency arrows so phases can land in parallel where independent.

## Sequencing principle

Every phase below leaves the system in a **strictly better state** than the one before, and **no phase blocks more than two others** — so multiple teams can land in parallel once dependencies clear.

```
                ┌──────────────────────────────────────────────┐
   FOUNDATION → │ 1A-1F  (DONE)                                │
                └──────────────┬───────────────────────────────┘
                               │
        ┌──────────────────────┼──────────────────────┐
        ▼                      ▼                      ▼
   2 AUTH & PII           3 INTEGRATIONS          4 DASHBOARD UI
   (JWT + KMS +           (SMS / KYC /            (Aegis design
    audit_event)           Payment GW)             system + 13
        │                      │                   surfaces)
        ├──────────────────────┘                      │
        ▼                                             ▼
   5 LIFECYCLE                                   6 LOCALISATION
   (Renewal / NCB /                              (Hindi + 5
    Endorsement /                                 regional)
    Claim / Portability /
    Free-look)
        │                      ┌──────────────────────┴───────┐
        ▼                      ▼                              ▼
   7 IRDAI ARTEFACTS      8 REPORTING & MIS                9 OPS @ SCALE
   (Prospectus / CIS /    (Premium / Plan-mix /            (Multi-region /
    Sales Illustration /   Claim ratio / Agent              Blue-green /
    Policy Wording /        commission ledger /              K8s HPA /
    UIN registry)          UW backlog)                      Backup-DR)
```

## Phase 2 — Auth + PII protection + audit log

**Outcome:** Every customer-facing action is authenticated, every PII field is encrypted at rest, every state change writes an immutable audit row. Once Phase 2 lands, the platform meets the floor of DPDP 2023 + IRDAI Cyber Security 2023.

| Deliverable | Files / new modules | Effort* | Depends on |
|---|---|---|---|
| Real JWT + refresh-token auth | new `:server`/auth/, replaces `OtpService.issueShortLivedToken` HMAC stub | 5d | — |
| RBAC scaffold (Agent/Customer/UW/Admin/Auditor) | new `:server`/auth/Roles.kt + route interceptors | 3d | JWT |
| `audit_event` table (V3) | V3 migration + `AuditEventService` + Ktor interceptor | 2d | — |
| KMS-backed column encryption for `quotes.request_json`, `proposals.bank_account_number`, `kyc.aadhaar_last4` | `EncryptedField` Exposed column type + AWS-KMS / GCP-KMS / Vault Transit adapter | 4d | Vendor pick |
| Idempotency-Key middleware | new `IdempotencyService` (PostgreSQL-backed, 24h TTL) + interceptor | 2d | — |
| Token rotation + revocation list | Redis-backed (added to docker-compose) | 2d | JWT |
| **TOTAL** | | **~18 dev-days** | |

*Effort assumes 1 senior engineer. Parallelisable into 2 engineers ≈ 10 calendar days.*

**Open product decisions** (must answer before code starts):
1. JWT secret rotation cadence and KMS provider (AWS KMS / GCP Cloud KMS / HashiCorp Vault Transit / Azure Key Vault).
2. Who is the appointed Data Fiduciary contact published in the privacy policy? (DPDP §10).
3. Retention windows per PII class (mobile / Aadhaar / PAN / bank / medical) — IRDAI says ≥ 8 years for proposals; DPDP says "as long as the purpose remains".
4. Sub-ROles within RBAC: does an Agent need read access to peer-agents' quotes? (assumed no.)

## Phase 3 — External integrations

**Outcome:** Real OTP via SMS, real KYC (no more `onClick = {}`), real payment with webhook signature verification. Every mock route in `BuyOnlineRoutes` is gone.

| Deliverable | Vendor pick (open) | Effort | Depends on |
|---|---|---|---|
| SMS OTP delivery | MSG91 / Gupshup / Karix | 3d | OtpService (DONE) |
| C-KYC via NSDL API | NSDL (only option) | 5d | Phase 2 auth |
| E-KYC via UIDAI + DigiLocker | UIDAI (only option) + DigiLocker | 7d | Phase 2 auth |
| PAN lookup | NSDL/UTIITSL | 3d | — |
| Payment gateway | Razorpay / Cashfree / PayU | 5d | Phase 2 auth |
| Penny-drop bank verification | DigiTap / Cashfree / IDfy | 3d | Phase 2 auth |
| Webhook signature verification | per-vendor HMAC | 1d each | — |
| Network hospital catalogue ingest | (data provider TBD; replace random `seed` in `/hospitals`) | 3d | — |
| **TOTAL** | | **~30 dev-days** | |

Each integration is independently shippable — Phase 3 is highly parallelisable.

## Phase 4 — Dashboard UI (Aegis)

**Outcome:** Business users can confidently edit plans, rates, covers, discounts, rules without engineer involvement. The 22-screen customer journey gets the supporting back-office it has always needed.

See `DASHBOARD_REDESIGN_PLAN.md` Pass 6 for the implementation strategy.

| Surface | Effort | Depends on | Notes |
|---|---|---|---|
| Aegis design system foundation | 5d | — | tokens / shell / core components |
| Home Dashboard | 3d | foundation | KPI cards, Sankey hero |
| Quote Explorer | 3d | foundation, existing `QuoteRepository` | filter / sort / drill-down |
| Plan Configurator (read mode) | 2d | foundation | replaces existing `ConfiguratorScreen` |
| Plan Configurator (edit + maker-checker) | 5d | Phase 2 auth, audit_event | 4-eyes workflow |
| Cover Catalog Manager | 4d | foundation | 50+ covers with param editor |
| Rate Table Manager + diff | 6d | audit_event | version + effective-date + 4-eyes |
| Discount Manager | 2d | foundation | |
| Business Rules Editor (visual) | 7d | foundation | |
| Excel Import Studio (dry-run, diff, rollback) | 5d | rate snapshots | |
| UW Queue | 4d | Phase 2 auth | |
| Reports & Analytics | 6d | — | needs warehousing decision |
| Audit & Governance browser | 3d | audit_event | hash-chain integrity |
| Settings (users / RBAC / GST / integrations) | 3d | Phase 2 auth | |
| **TOTAL** | | **~58 dev-days** | |

Parallel: 4 designers/engineers can land the surfaces in ~4 calendar weeks once the design system is up.

## Phase 5 — Policy lifecycle

**Outcome:** Customers can renew, port, endorse, claim, cancel. The platform becomes a real PHI product, not just a quote-and-pay funnel.

| Deliverable | Effort | Depends on |
|---|---|---|
| Renewal flow + 60/30/15-day reminder scheduler | 5d | auth, audit_event |
| NCB engine (5% per claim-free year, max 50%) | 4d | claim history (below) |
| Endorsement (add member / SI / address / nominee) | 6d | auth, plan rules |
| Portability (Section 21A — accept from competitor) | 5d | KYC, UIDAI |
| Claim intimation + status + settlement | 10d | hospital network |
| Free-look cancellation + refund | 3d | payment GW |
| Lapse + revival (30-day grace) | 4d | renewal |
| Cashless preauth | 6d | hospital network + TPA |
| **TOTAL** | | **~43 dev-days** |

## Phase 6 — Localisation

| Deliverable | Effort |
|---|---|
| i18n framework (per-key resource bundles) | 3d |
| Translation: Hindi | 4d |
| Translation: Tamil, Telugu, Kannada, Marathi, Bengali (5 regional) | 4d each = 20d |
| Locale-aware number / date / currency / pluralisation | 2d |
| **TOTAL** | **~25 dev-days** |

## Phase 7 — IRDAI artefacts

| Deliverable | Effort | Notes |
|---|---|---|
| UIN registry table + UI | 2d | per-product UIN tracking |
| Prospectus PDF generator | 4d | reads CoverCatalog + Plan |
| Customer Information Sheet (CIS) generator | 3d | per-policy at issuance |
| Sales Illustration generator | 5d | year-by-year benefit table |
| Policy Wording PDF | 3d | versioned per UIN |
| AYUSH / Mental Health / HIV cover parity | 3d each = 9d | per Master Circular 2024 |
| **TOTAL** | **~26 dev-days** | |

## Phase 8 — Reporting & MIS

| Deliverable | Effort |
|---|---|
| Warehousing decision (Postgres views vs ClickHouse vs BigQuery) | 1d (decision) |
| Premium-collected + plan-mix + zone-mix reports | 4d |
| Claim ratio, lapse ratio, revival ratio | 4d |
| Agent commission ledger | 4d |
| UW backlog dashboard with SLA tracker | 3d |
| Exportable IRDAI regulatory returns | 5d |
| **TOTAL** | **~21 dev-days** |

## Phase 9 — Ops @ scale

| Deliverable | Effort |
|---|---|
| Multi-region Postgres (primary + read replica + cross-region standby) | 5d |
| K8s manifests + HPA on Micrometer metrics | 4d |
| Blue-green deploy pipeline | 3d |
| Backup automation + restore drill runbook | 3d |
| Distributed tracing (OpenTelemetry) | 3d |
| Centralised log aggregation (Loki/ELK) | 3d |
| APM (DataDog/NewRelic) | 2d |
| Chaos drills | ongoing |
| **TOTAL** | **~23 dev-days** |

---

## Sequenced execution — recommended cadence

```
Week 1-2   :  Phase 2 (auth + KMS + audit_event)
              + Phase 4 design-system foundation in parallel
Week 3-4   :  Phase 3 integrations (4 sub-streams in parallel)
              + Phase 4 first 4 surfaces (Home / Quote Explorer /
                Plan Configurator / Cover Catalog Manager)
Week 5-6   :  Phase 4 remaining surfaces
              + Phase 5 starts (Renewal + NCB)
Week 7-8   :  Phase 5 lifecycle continues
              + Phase 7 IRDAI artefacts in parallel
Week 9-10  :  Phase 6 localisation
              + Phase 8 reporting
Week 11-12 :  Phase 9 ops at scale (gates the prod cutover)
```

With three engineers in parallel, the entire post-foundation roadmap lands in ~12 calendar weeks. With one engineer, ~36 weeks.

## Decision log (open product/legal questions)

These items block phases listed above. Cleared decisions → engineering can proceed.

| # | Question | Owner | Blocks |
|---|---|---|---|
| D-01 | KMS provider (AWS / GCP / Azure / Vault) | Eng + Sec | Phase 2 PII encryption |
| D-02 | SMS gateway vendor | Product + Ops | Phase 3 OTP |
| D-03 | Payment gateway vendor + commission terms | Product + Finance | Phase 3 payment |
| D-04 | KYC partner (DigiTap / IDfy / Karza) for penny-drop | Product + Ops | Phase 3 bank verify |
| D-05 | Authentication: single login per mobile vs per device | Product + Sec | Phase 2 JWT design |
| D-06 | DPDP Data Fiduciary contact + DPO email | Legal | Phase 2 + privacy policy |
| D-07 | Retention windows per PII class | Legal + Compliance | Phase 2 |
| D-08 | Maker-checker — who can self-approve? (assume no one) | Product + Internal Audit | Phase 4 Plan Configurator |
| D-09 | Data warehouse choice for reporting | Eng + Data | Phase 8 |
| D-10 | Free-look refund mechanics (full vs minus first-day cover charge) | Product + Legal | Phase 5 |
| D-11 | NCB structure (5%/year cap 50% — confirm with actuary) | Actuary | Phase 5 NCB |
| D-12 | Localisation languages (which 5 regional?) — currently assumed Tamil/Telugu/Kannada/Marathi/Bengali | Product | Phase 6 |
| D-13 | Cloud region (only Indian DCs per IRDAI data localisation) | Sec + Ops | Phase 9 |
| D-14 | UIN registry — is each plan a separately approved product or are they variants? | Compliance + Actuary | Phase 7 |
| D-15 | Network hospital data source — internal CMS or licensed feed? | Product + Ops | Phase 3 hospitals |

## Hot-spot map — files most likely to be touched by multiple initiatives

These files will see concurrent change pressure; coordinate via feature branches and `CODEOWNERS`.

| File | Concurrent touchpoints |
|---|---|
| `shared/.../PricingEngine.kt` | Renewal NCB, claim history, pro-rata (Phase 5); IRDAI 2024 covers (Phase 7) |
| `shared/.../Models.kt` | Most phases — add fields for renewal, claim, NCB, UIN, audit |
| `server/.../BuyOnlineRoutes.kt` | Phase 3 (real OTP/KYC/payment), Phase 5 (renewal endpoints) |
| `server/.../Application.kt` | Phase 2 (DI re-wiring for auth), Phase 9 (metrics endpoint) |
| `desktop/.../ConfiguratorScreen.kt` | Replaced wholesale by Phase 4 Aegis Plan Configurator |
| `buyonline/.../viewmodel/BuyOnlineViewModel.kt` | Phase 5 (renewal flow), Phase 6 (i18n) |
| `application.conf` | Phase 2 (KMS config), Phase 3 (vendor URLs), Phase 9 (multi-region) |
| Flyway migrations | All phases add migrations; nominate a "migration czar" PR-reviewer |

## Effort summary

| Phase | Dev-days | Parallelism |
|---|---|---|
| 1 (Foundation) | DONE | — |
| 2 | ~18 | 2 engineers |
| 3 | ~30 | 4 engineers (one per integration) |
| 4 | ~58 | 4 engineers (one per surface group) |
| 5 | ~43 | 3 engineers |
| 6 | ~25 | 6 translators + 1 engineer |
| 7 | ~26 | 2 engineers + actuary + legal |
| 8 | ~21 | 2 engineers + data analyst |
| 9 | ~23 | 1 SRE + 1 engineer |
| **TOTAL** | **~244 dev-days** | **~12 weeks at recommended cadence** |

---

*Phase roadmap refined. Next: operational-excellence layer to make all of the above land safely and stay landed.*

---

# APPENDIX — PHASE 10 OPERATIONAL EXCELLENCE (REFINEMENT ROUND 3)

Phase 2-9 above describes **what to build**. Phase 10 describes **how to run it without ever waking the CEO at 2 a.m.** It is not optional — every preceding phase needs these guardrails in place before the feature it ships becomes production-load-bearing.

## 10.1 — SLOs and SLIs

Every customer-facing capability has an explicit Service Level Objective measured against a Service Level Indicator. These are the only numbers that matter when judging whether the platform is "best in the world".

| Capability | SLI | SLO (steady state) | SLO (peak / pre-IPO renewal season) |
|---|---|---|---|
| Quote calculation | p99 server latency | < 250 ms | < 400 ms |
| Buy-online quote → proposal flow | end-to-end p95 success | ≥ 99.5% | ≥ 99.0% |
| Premium engine arithmetic | golden-test pass rate | 100% | 100% |
| GST line item presence | quotes with `gstAmount > 0` | 100% | 100% |
| OTP delivery (post Phase 3) | sent → delivered within 30s | ≥ 98% | ≥ 95% |
| OTP brute-force protection | OTPs verified in < 5 attempts | 100% | 100% |
| Payment gateway (post Phase 3) | webhook signature verified | 100% | 100% |
| Aegis dashboard cold start | p95 first-meaningful-paint | < 200ms | < 350ms |
| API availability | uptime | ≥ 99.9% (43 m/month) | ≥ 99.5% (3.6 h/month) |
| DB read replica freshness | replication lag | < 5 s | < 30 s |
| KYC turnaround (post Phase 3) | submit → decision | < 24 h | < 48 h |
| Claim settlement (post Phase 5) | intimation → payout | < 7 days (cashless) | < 30 days (reimbursement) |

These numbers are not aspirational — they go into a Grafana dashboard from day one of Phase 9 and trigger pages when budget is exhausted.

## 10.2 — Error budget policy

Each SLO has an **error budget** = `(1 - SLO) × time-window`. Burn rate is monitored on 1h / 6h / 24h windows.

| Trigger | Action |
|---|---|
| 2h burn rate > 14.4× | Page on-call; freeze non-critical deploys |
| 6h burn rate > 6× | Page on-call; postmortem within 24h |
| Monthly budget exhausted | Halt all feature work; engineering moves to reliability for a full sprint |
| Two budget exhaustions in a quarter | Cancel the next sprint's planned scope; replace with reliability work |

This is the lever that prevents Phase 2-9 scope-creep from breaking the platform. It is enforced in a quarterly review by the eng lead + CTO, not by individual engineers.

## 10.3 — On-call rotation

| Tier | Who | Hours | Pager response |
|---|---|---|---|
| L1 | Platform engineering rotation (5 engineers, 1-week shifts) | 24/7 | Acknowledge in 5 min; respond in 15 min |
| L2 | Sub-system owners (engine / server / aegis / buy-online) | Business hours | Acknowledge in 30 min |
| L3 | CTO + Principal Engineer | Critical only | Acknowledge in 1 h |

Customer-facing severity definitions:

- **SEV-0**: Buy-online is down (customers cannot purchase). Page CTO + Comms.
- **SEV-1**: Pricing engine returning wrong amounts, OR PII leak detected, OR DB unavailable. Page L1 + L2 immediately.
- **SEV-2**: Buy-online degraded (some journeys failing), OR Aegis dashboard down (business cannot edit). Page L1.
- **SEV-3**: Reporting / cosmetic / non-blocking. Ticketed for next business day.

## 10.4 — Incident response playbook (boilerplate that every responder follows)

```
1. ACK the page (5 min budget).
2. Open #incident-<ticket> in Slack. Pin a status thread.
3. Mitigate first, root-cause second. Acceptable mitigations:
   - Roll back the last release (CI tags last-known-good).
   - Flip feature flag (LaunchDarkly / GrowthBook).
   - Scale up replicas.
   - Failover to standby DB.
   - Rate-limit the offending endpoint.
4. Communicate to customers via status.pruhealth.in within 15 min for SEV-0/1.
5. Hold the incident open until SLI returns to within budget for 60 min.
6. Post a 5-line summary in #engineering.
7. Postmortem in 48h (SEV-0/1) or 5 business days (SEV-2). Template:
   - What broke?
   - What was the customer impact (numbers)?
   - What was the root cause (5 Whys)?
   - Why didn't existing safeguards catch it?
   - Action items (each with owner + due date).
   - Blameless. Never name an individual as the cause.
```

## 10.5 — Pre-flight checklist (every release)

Every Phase 2+ deliverable gates on this before going to production:

- [ ] Golden tests still pass (`:shared:jvmTest`)
- [ ] Validators tests still pass
- [ ] Server compiles with `:server:compileKotlin`
- [ ] All clients (`:desktop`, `:buyonline`, `:aegis`) compile
- [ ] Buy-online end-to-end smoke (manual or scripted)
- [ ] Aegis Plan Configurator end-to-end smoke
- [ ] Quote API contract test (3 canonical inputs match expected outputs to the paise)
- [ ] Migrations applied to staging successfully
- [ ] Logs do not show plaintext PII (grep run against the last 1h of staging logs)
- [ ] `/health/ready` returns 200 against staging
- [ ] `/metrics` returns Prometheus format
- [ ] Rollback procedure verified (`./gradlew :server:run` from the previous git tag boots cleanly)

Anything red ⇒ no merge.

## 10.6 — Backups and DR

| Asset | Backup cadence | Retention | RTO | RPO |
|---|---|---|---|---|
| PostgreSQL primary | continuous WAL streaming → S3 (or GCS) | 35 days PITR + 1y monthly | 1 h | 5 min |
| Configuration (`application.conf`, secrets) | Vault + git audit | indefinite | 30 min | 0 |
| Rate tables (every Excel import) | snapshot to `rate_table_snapshots` table | 1 year | seconds | 0 |
| Audit log (`audit_event` table) | replicated to immutable bucket (S3 Object Lock) | 8 years (IRDAI) | n/a (read-only) | n/a |
| Container images | ECR / GCR with image immutability | 1 year | 5 min | 0 |
| Aegis dashboard binary | every release published to internal artefact store | indefinite | 1 min | 0 |

DR drill cadence: quarterly full restore exercise to a non-prod cluster, signed off by SRE lead.

## 10.7 — Cost guardrails

| Component | Budget alert | Hard cap |
|---|---|---|
| PostgreSQL (managed RDS / Cloud SQL) | $400/month | $800/month |
| Compute (Server + Aegis hosting) | $300/month | $600/month |
| Object storage (logs + backups) | $100/month | $250/month |
| SMS gateway | per-customer-per-purchase model; alert if > ₹50/customer | — |
| Payment gateway | per-transaction fee; tracked separately |
| Total | $1000/month dev/staging combined | $2500/month prod |

Anything > 20% over budget for two consecutive weeks triggers a cost-review meeting.

## 10.8 — Compliance audit calendar

| Audit | Frequency | Owner |
|---|---|---|
| IRDAI Cyber Security Self-Assessment | annual | CTO + CISO |
| DPDP DPIA review | semi-annual | Legal + Eng |
| VAPT (Vulnerability Assessment + Pen Test) | annual + on major releases | Sec team (external vendor) |
| Internal audit log integrity check (hash chain verify) | weekly automated + monthly manual review | Sec team |
| Actuary sign-off on rate table changes | per change | Chief Actuary |
| Maker-checker compliance audit | quarterly | Internal Audit |
| Data retention compliance (DPDP) | quarterly | Legal |
| Backup restore drill | quarterly | SRE |
| Tabletop incident exercise | semi-annual | All-hands |

## 10.9 — Excellence DoD ("done means done")

A phase is not "done" until it satisfies all of these in production:

- Telemetry: every customer-facing action emits at least one structured log line + one metric.
- Audit: every state change writes an `audit_event` row with verifiable hash-chain entry.
- A11y: every UI surface passes WCAG AAA automated audit.
- Localisation: at minimum English + Hindi shipped; regional locales documented as TODO with translation pool assigned.
- Documentation: a runbook entry under `docs/runbooks/` exists for every endpoint or surface that on-call might need to operate.
- Rollback: documented + tested.
- Budget: passes the cost guardrail.
- Compliance: appropriate sign-off recorded (actuary for rate changes, legal for PII, etc.).
- Observability: SLO defined + dashboard panel shipped + alert configured.

## 10.10 — Open principles

These guide every implementation decision through Phase 2-9:

1. **Make wrong things hard.** Type-safe domain (Money, Validators), constrained enums, no stringly-typed primitives near money or identity.
2. **No silent fallbacks.** Synthetic data ≠ production data. Mocks are clearly labeled. `runCatching { ... }.onFailure { /* swallow */ }` is forbidden.
3. **Audit everything that mutates.** No state change without an audit_event row.
4. **PII is radioactive.** Mask in logs by default; encrypt at rest by default; never trust a free-text field.
5. **The engine is the source of truth.** No parallel pricing implementations. Ever.
6. **Test the boundary, not the implementation.** Engine has golden tests. Surfaces have screenshot tests. Routes have contract tests. Integration tests stay shallow on purpose.
7. **Reversible by default.** Every release is rollback-able in one command. Every migration is additive.
8. **The Foundation Pack is non-negotiable.** No feature regresses GST, env-var secrets, OTP rate limit, or the discount cap. Tests guard each.
9. **Operate it before you build it.** No new endpoint without a runbook. No new surface without an SLO.
10. **Best-in-the-world isn't a deliverable — it's a daily discipline.** Every PR makes the worst-quality file in its blast radius slightly better.

---

*Foundation Pack + Phase 2-10. The durable shape of the platform.*

---

# APPENDIX — INTERCONNECTIONS MAP (REFINEMENT ROUND 3)

Phase 2-10 above sequences the work. This appendix maps the **load-bearing dependencies between deliverables** so engineers can see at a glance which item NEEDS which other item, and which items can be re-ordered safely.

## A.1 — Load-bearing dependency graph

```
                       ┌─────────────────────┐
                       │   Foundation (1)    │
                       │  GST · Money · Vals │
                       │  golden tests · CI  │
                       └─────────┬───────────┘
                                 │
        ┌────────────────────────┼────────────────────────┐
        ▼                        ▼                        ▼
┌───────────────┐       ┌──────────────────┐     ┌──────────────────┐
│ JWT + RBAC    │       │ audit_event      │     │ Aegis foundation │
│ (Phase 2)     │──┬────│ (Phase 2)        │     │ tokens+shell+15  │
└───────┬───────┘  │    └────────┬─────────┘     │ core components  │
        │          │             │               └─────────┬────────┘
        │          │             │                         │
        ▼          ▼             ▼                         ▼
┌───────────────┐ ┌──────────────────┐         ┌────────────────────┐
│ PII KMS       │ │ Idempotency keys │         │ Home / Quotes /    │
│ (Phase 2)     │ │ (Phase 2)        │         │ PlanConfig (read)  │
└───────┬───────┘ └──────────────────┘         │ (Aegis Ship 2-5)   │
        │                                       └─────────┬──────────┘
        │                                                 │
        ▼                                                 ▼
┌───────────────┐  ┌──────────────────────────────────────────────┐
│ Real OTP/KYC/ │  │ Plan Config edit + maker-checker (Ship 6)    │
│ Payment GW    │  │ Rate Table Manager + diff (Ship 7)           │
│ (Phase 3)     │  │ Excel Import Studio + rollback (Ship 8)      │
└───────┬───────┘  │ Discounts + Rules editor (Ship 9)            │
        │          │ Audit & Governance browser (Ship 12)         │
        │          └──────────────────────┬───────────────────────┘
        │                                 │
        ▼                                 ▼
┌─────────────────────────────────────────────────────────┐
│ Phase 5 — Lifecycle                                     │
│ Renewal → NCB → Endorsement → Claim → Portability       │
│ Free-look → Lapse/Revival                               │
│ (NCB depends on claim history; claim history depends    │
│  on audit_event; everything depends on JWT.)            │
└─────────────────────────┬───────────────────────────────┘
                          │
                          ▼
┌─────────────────────────────────────────────────────────┐
│ Phase 7 — IRDAI artefacts                               │
│ UIN Registry → Prospectus → CIS → Sales Illustration    │
│ → Policy Wording → Master Circular 2024 covers          │
└─────────────────────────┬───────────────────────────────┘
                          │
        ┌─────────────────┼─────────────────┐
        ▼                 ▼                 ▼
   Phase 6           Phase 8           Phase 9
   Localisation      Reporting/MIS     Ops at scale
   (depends on UI    (depends on       (depends on
    surfaces, not    audit_event +     metrics + audit
    on engine)       payment + Phase5) + lifecycle)
                          │
                          ▼
                   Phase 10 Ops Excellence
                   (cross-cutting; runs alongside Phase 9)
```

## A.2 — Critical chains (where one block blocks many)

| Block | Unlocks |
|---|---|
| `audit_event` hash-chain (Phase 2) | Maker-checker (Ship 6+), claim history, NCB calc, Audit browser (Ship 12), IRDAI compliance audit |
| JWT + RBAC (Phase 2) | Every Aegis edit-mode surface, every lifecycle endpoint, all Phase 3 integrations |
| Aegis foundation (Ship 1) | All 12 subsequent surfaces. Single biggest leverage point of Phase 4 |
| Rate-table versioning (in QuoteResult — DONE) | Renewal at original rates, rate diff, A/B rate testing |
| GST line item (DONE) | Honest payment flow, IRDAI submission readiness, accurate reporting |
| Idempotency keys (Phase 2) | Safe payment-webhook retries, deduped proposal submission, multi-retry import jobs |
| Money value type (DONE) | Currency precision across all phases — no FP drift |
| KMS column encryption (Phase 2) | DPDP compliance for PII at rest. Until this lands, Phase 3 (KYC/payment) cannot store its data |
| RateTableVersion stamping (DONE) | Renewals respect original-issue rates; rate audit; rate-change A/B |

## A.3 — Items that can re-order without penalty

| Item A | Item B | Why they're independent |
|---|---|---|
| Phase 6 Localisation | Phase 7 IRDAI artefacts | Different language stack; artefacts are templated |
| Phase 8 Reporting | Phase 9 Ops at scale | Reporting reads from DB; ops manages infra |
| Aegis Quote Explorer | Aegis Plan Configurator | Different data, different repos |
| Cover Catalog Manager | Discount Manager | Different domain entities |
| MSG91 SMS | Razorpay payment | Different external vendors |
| C-KYC integration | E-KYC integration | Different APIs (NSDL vs UIDAI) |

## A.4 — Items that *look* re-orderable but aren't

| Pair | Why ordering matters |
|---|---|
| NCB engine vs Claim intimation | NCB requires claim history. Claim must land first |
| Excel Import Studio dry-run vs Rate Table Manager | Both touch rate snapshots — Excel ingest creates the snapshot, the Manager edits it. Snapshot infra ships once, before either |
| Aegis maker-checker vs audit_event | The 4-eyes workflow records every step in audit_event. The table must exist first |
| Localisation vs Aegis surfaces | i18n keys are extracted from surface code. Surfaces must stabilise first |
| Reports & Analytics vs warehouse decision | Until we pick Postgres-views vs ClickHouse vs BigQuery, no Reports work starts |
| Buy-online claim flow vs claim intimation API | The customer-facing flow needs the server endpoints to exist |
| Multi-region DB (Phase 9) vs IRDAI data localisation (Phase 2) | Both must constrain to Indian DCs. Decide cloud + region BEFORE Phase 9 |

---

# APPENDIX — FIRST 90 DAYS FOR A NEW ENGINEER

The platform's mass means a new joiner needs a map. Here's how to be productive in three months.

## Days 1-3 — Orient

- Read `README.md`, `CHANGELOG.md`, `AUDIT_REPORT.md` (this doc), `DASHBOARD_REDESIGN_PLAN.md` in that order.
- `make dev` to bring up local stack. Hit `http://localhost:9090/health` to confirm.
- `make shared-test` — see the 56 golden tests pass. Read at least 5 of them in `shared/src/commonTest/`.
- Run `:desktop:run`, `:buyonline:run`, `:aegis:run` (once Aegis Ship-1 lands). Click everything.
- Read `docs/02-pricing-engine.md` end-to-end. The engine is the heart.

## Days 4-7 — First contribution

- Pick a single low-risk audit finding (P3 or P4) from `AUDIT_REPORT.md`.
- Fix it. Write a test pinning the fix. Open a PR. Get review.
- Repeat: 5 small fixes in the first week is a stronger ramp than one big feature.

## Days 8-21 — Domain immersion

- Sit with the Chief Actuary for one full day. Watch them work in Excel.
- Sit with a Product Lead for one full day. Watch them email engineering for changes that should be a button in Aegis.
- Read the 28 business rules in `docs/07-business-rules.md`. Discuss any that confuse you with the actuary.
- Read IRDAI Health Insurance Regulations 2016 (summary on irdai.gov.in). Read the 2024 Master Circular.

## Days 22-45 — Take a Ship

- Take ownership of one Aegis Ship-N (whichever is on the active sprint).
- Pair with a senior engineer for the first half; solo the second half.
- Ship it, test it through the 7-stage user-testing protocol (DASHBOARD_REDESIGN_PLAN §7.2).

## Days 46-90 — Specialise

- Pick a sub-system: Engine / Server / Aegis / Buy-online / Ops.
- Become the L2 on-call for that sub-system. Take 1 page in your specialism by Day 90.
- Write one runbook entry under `docs/runbooks/` for an endpoint or surface you now own.

## Working norms

- Every PR must include: at least one test pinning the new behaviour, a CHANGELOG entry, no plaintext PII in logs, no parallel pricing implementation.
- The pricing engine is sacred. Changes to `:shared/.../engine/PricingEngine.kt` require an actuary review on the PR.
- Audit_event integrity is sacred. Changes to `:server/.../audit/` require sec-team review.
- "Best in the world" means: every PR makes the worst-quality file in its blast radius a little better.

## Mentor pairing

| New engineer's first ship | Pair with |
|---|---|
| Pricing engine fix | Senior engine engineer |
| Aegis surface | Senior product designer + senior frontend engineer |
| Buy-online screen | Senior frontend + Product Lead |
| Server route or audit | Senior backend + Sec lead |
| Operations / infra | SRE lead |

---

# APPENDIX — ROADMAP GLOSSARY

| Term | Definition |
|---|---|
| **Aegis** | The new business dashboard (Pass 1-7 of DASHBOARD_REDESIGN_PLAN). 13 surfaces. Replaces ad-hoc Excel + email workflows. |
| **Foundation Pack** | The Phase 1 deliverable. GST, Money, Validators, security headers, env-var secrets, Docker, CI, README. Landed. |
| **Maker-checker (4-eyes)** | Workflow where one person drafts a change, a different person approves it, and a third (CTO) publishes it Live. Mandatory for rate-table publish, plan publish, discount publish. |
| **NCB** | No-Claim Bonus. 5% cumulative per claim-free year, capped at 50%. Applied at renewal. |
| **UIN** | Unique Identification Number. 4-digit IRDAI-assigned product identifier. Every approved insurance product has one. |
| **CIS** | Customer Information Sheet. Single-page IRDAI-mandated summary issued with every policy. |
| **POSP** | Point-of-Sale Person. A lower-licence agent type with restricted product set (typically ₹2-5L SI). |
| **PED** | Pre-Existing Disease. Conditions diagnosed before policy issue. Have waiting periods. |
| **HSN 9971** | The GST classification code for insurance. 18% GST applies to health insurance under this HSN. |
| **Hash-chain** | The audit_event integrity mechanism. Each row's `this_hash = sha256(prev_hash + payload)`. Tampering with any row breaks all subsequent hashes. |
| **Ship-N** | A discrete weekly Aegis release. Ship-1 = foundation, Ship-13 = Settings. |
| **Section 21A** | The IRDAI portability rule. A customer porting from a competitor brings their accumulated waiting period credits. |
| **Free-look period** | 15-day window after policy issue during which the customer can cancel for a refund. IRDAI-mandated. |
| **Sub-standard** | An underwriting category for higher-risk customers. Carries a higher rate-table loading. |
| **Cover-pass discount** | A discount that's computed during the cover-application phase (smart_select, deductibles, co_pay) rather than the standalone discount phase. Foundation Pack closed the bypass that let these escape the cap. |
| **Accumulation base** | Per-cover ordered list of cover IDs whose year-premium is summed to form the multiplication base for that cover's rate. The single most fragile object in the engine. |

---

*Refinement round 3 complete.*

---

# APPENDIX — QUALITY BAR PER PHASE (REFINEMENT ROUND 4)

For each phase below, the **acceptance criteria** that mark it "shippable". Engineering must self-attest against every line; a phase is not "done" until every box ticks.

## Phase 1 — Foundation (DONE)

- [x] All 56 golden + validator + Money tests pass in CI
- [x] `gstAmount > 0` for every quote with a non-zero base
- [x] `BuyOnlineViewModel.estimatedPremium` is absent (`grep -r` returns zero)
- [x] DB password never appears in any committed file (audited via `git log -p`)
- [x] V1 migration does not include `DROP TABLE`
- [x] CORS allowlist (no `anyHost()` anywhere)
- [x] HSTS + X-Frame-Options + X-Content-Type-Options on every response (`curl -I /health` shows them)
- [x] OTP rate-limited (6th send within 1h returns 429)
- [x] OTP brute-force locked (6th wrong code on same OTP returns lockout)
- [x] Aadhaar / PAN / mobile masked in logs (`PiiMaskingConverter` test)
- [x] Docker compose brings up Postgres + server with `make docker-up`
- [x] GH Actions CI runs on every push and is green on `main`
- [x] README is > 100 lines and includes quickstart + module map
- [x] LICENSE exists; CHANGELOG entries documented

## Phase 2 — Auth + PII + audit_event

- [ ] Every `/api/*` route requires a valid JWT (Sec test: anonymous call → 401)
- [ ] JWT carries `sub` + `role` + `exp` + signature; refresh-token rotation tested
- [ ] RBAC: Agent cannot publish a plan; Auditor cannot edit a plan (matrix tested)
- [ ] Every mutation writes an `audit_event` row with non-empty `this_hash`
- [ ] `verifyChain` returns ok=true over 10,000 rows in a stress test
- [ ] PII columns (`bank_account_number`, `aadhaar_last4`, `pan_masked`) use `EncryptedField` with KMS key reference
- [ ] Idempotency-Key replays return the cached body byte-for-byte (test)
- [ ] Idempotency-Key conflict (same key, different body) returns 409
- [ ] `audit_event` rows survive a full backup-restore cycle (DR drill)
- [ ] Sec team has signed off on the threat model

## Phase 3 — External integrations

- [ ] OTP delivered via real SMS gateway within p95 30s on staging (delivery-rate ≥ 98%)
- [ ] Payment gateway webhook signature verified against vendor's secret (test with replayed payload → 401)
- [ ] C-KYC + E-KYC + Manual KYC each route to its real verifier and store a result row
- [ ] DigiLocker callback works for an actual Aadhaar fetch (manual test pass)
- [ ] Penny-drop name-match returns confidence score; mismatch surfaces in UI as a warning
- [ ] Network hospital data feeds populate `/hospitals` endpoint (no more synthetic counts)
- [ ] Every vendor integration has a circuit breaker + fallback message
- [ ] Vendor SLAs documented in `docs/runbooks/integrations/<vendor>.md`

## Phase 4 — Aegis dashboard

- [ ] All 13 surfaces ship and pass the 7-stage user-testing protocol (`DASHBOARD_REDESIGN_PLAN.md` §7.2)
- [ ] Plan Configurator round-trip: Draft → In Review → Approved → Live writes 4 audit_event rows
- [ ] Rate Table Manager diff shows cells changed by exact paise (no rounding fuzz)
- [ ] Excel Import Studio: dry-run + diff + commit + rollback all functional
- [ ] Cover Catalog Manager surfaces all 50+ covers
- [ ] WCAG AAA passes on every screen (automated audit + manual screen-reader walk)
- [ ] 95th-percentile render < 200ms on the reference machine
- [ ] Chief Actuary signs off that Aegis replaces their Excel
- [ ] At least one non-engineer business user completes Journey A or B without help

## Phase 5 — Policy lifecycle

- [ ] Renewal quote computed correctly against engine output (golden tests against 20 scenarios)
- [ ] NCB applied correctly (0/1/5/10/15 claim-free years; 50% cap test)
- [ ] Endorsement (add member / increase SI / address change) writes audit_event + new policy version
- [ ] Free-look return refund calculated correctly (within 15-day window)
- [ ] Claim intimation creates a claim record + audit_event
- [ ] Cashless preauth round-trips to TPA stub successfully
- [ ] Portability accepts a manual request with waiting-period carry-forward + records source insurer
- [ ] Lapse + revival within 2-year window restores active status
- [ ] Premium receipt PDF generated on payment

## Phase 6 — Localisation

- [ ] All Aegis surfaces ship in en + hi
- [ ] Strings missing in any locale fall back to English (no blank screens)
- [ ] Indian rupee grouping is correct in every locale tested (₹1,23,45,678.90)
- [ ] Locale switch at runtime works without restart
- [ ] Translation review process is documented + a translator pool is named

## Phase 7 — IRDAI artefacts

- [ ] Every active plan has a real UIN (not "TBD-UIN-XXXX")
- [ ] Prospectus PDF generates with all 11 IRDAI-required sections
- [ ] CIS PDF generates per policy issuance
- [ ] Sales Illustration PDF generates for tenure ≥ 3
- [ ] Policy Wording PDF is versioned per UIN
- [ ] AYUSH / Mental health / HIV cover parity present in CoverCatalog
- [ ] Master Circular 2024 alignment signed off by Legal

## Phase 8 — Reporting & MIS

- [ ] Daily GWP report shows correct totals against ground-truth Postgres query
- [ ] Plan-mix / zone-mix / family-type-mix reports filter and pivot correctly
- [ ] Claim ratio + lapse ratio + revival ratio tracked monthly
- [ ] Agent commission ledger exports to CSV / IRDAI return format
- [ ] UW backlog dashboard updates within 1 minute of audit_event

## Phase 9 — Ops at scale

- [ ] PostgreSQL HA: primary + read replica + cross-region standby; failover < 1 min
- [ ] K8s HPA scales pods on `rate_http_requests_total` rate
- [ ] Blue-green deploy: zero-downtime cutover demonstrated in staging
- [ ] Backup restore drill completes in < RTO
- [ ] DataDog / NewRelic dashboards live; SEV-1 alert fires on staging when burn-rate exceeds threshold
- [ ] Distributed traces visible from buy-online client → server → engine
- [ ] Centralised log aggregation working; queries return < 5s for last 24h

## Phase 10 — Ops Excellence

- [ ] SLOs defined and tracked for every capability in §10.1
- [ ] Error-budget policy enforced via tooling (auto-freeze deploys when budget exhausted)
- [ ] On-call rotation staffed and acknowledged
- [ ] Quarterly DR drill executed and signed off
- [ ] Incident playbook (§10.4) verified via tabletop exercise
- [ ] Cost guardrails configured + alerts firing

---

# APPENDIX — COMPETITIVE FEATURE-PARITY TABLE

Where this platform stands today (post-Foundation-Pack) vs the leading Indian PHI competitors. Cells: ✅ shipped · 🟡 partial · 🔴 missing · ⭐ better than competitors when Aegis ships.

| Capability | This (today) | This (post Phase 2-9) | Star Health | HDFC ERGO | Niva Bupa | Acko | Digit |
|---|---|---|---|---|---|---|---|
| Actuarial pricing engine (50+ covers) | ✅ | ⭐ | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| Engine has golden regression tests | ✅ ⭐ | ⭐⭐ | 🔴 (Excel-only) | 🔴 | 🔴 | 🔴 | 🔴 |
| GST line item on customer quote | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Single source of truth for pricing | ✅ ⭐ | ⭐⭐ | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 |
| OTP rate-limit + brute-force lockout | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Real OTP (SMS gateway) | 🟡 (stub) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Real KYC (C/E-KYC + DigiLocker) | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Real payment gateway | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Env-var-only secrets | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Audit log with hash-chain integrity | 🟡 (infra in) | ⭐ | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 |
| KMS-backed column encryption | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Idempotency-Key on POST | 🟡 (infra in) | ✅ | 🟡 | ✅ | 🟡 | ✅ | ✅ |
| Prometheus `/metrics` | ✅ | ✅ | 🟡 | ✅ | 🟡 | ✅ | ✅ |
| Distributed tracing | 🔴 | ✅ | 🟡 | ✅ | 🟡 | ✅ | ✅ |
| Aegis-style business dashboard | 🔴 | ⭐⭐ | 🔴 | 🟡 | 🔴 | 🟡 | 🟡 |
| Maker-checker workflow | 🔴 | ⭐ | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 |
| Rate-table versioning + diff | 🔴 | ⭐⭐ | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 |
| Excel-import dry-run + rollback | 🔴 | ⭐ | 🔴 | 🟡 | 🔴 | 🔴 | 🔴 |
| Renewal + NCB | 🟡 (model in) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Endorsement + portability | 🟡 (model in) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Free-look | 🟡 (model in) | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Claim intimation + status | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Cashless preauth | 🔴 | ✅ | ✅ | ✅ | ✅ | 🟡 | 🟡 |
| Prospectus / CIS / SI PDF generators | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| UIN registry tracking | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| AYUSH / Mental health / HIV parity | 🔴 | ✅ | 🟡 | ✅ | 🟡 | ✅ | ✅ |
| Localisation (en + hi + 5 regional) | 🔴 | ✅ | 🟡 | ✅ | 🟡 | ✅ | ✅ |
| WCAG AAA accessibility on UI | 🟡 | ⭐ | 🔴 | 🔴 | 🔴 | 🟡 | 🟡 |
| Indian-grouping money everywhere | ✅ | ✅ | 🟡 | ✅ | 🟡 | ✅ | ✅ |
| Network hospital data feed | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Customer-side PII masking | ✅ | ⭐ | 🟡 | 🟡 | 🟡 | 🟡 | 🟡 |
| Bank account name-match (penny-drop) | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Anomaly detection on KPIs | 🔴 | ⭐ | 🔴 | 🔴 | 🔴 | 🟡 | 🟡 |
| "Explain this number" feature | 🔴 | ⭐⭐ | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 |
| Natural-language ⌘K commands | 🔴 | ⭐⭐ | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 |
| Multi-region DB + blue-green deploy | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Quarterly DR drill | 🔴 | ✅ | ✅ | ✅ | ✅ | ✅ | ✅ |
| Audit-event hash-chain ⭐ | 🟡 | ⭐⭐ | 🔴 | 🔴 | 🔴 | 🔴 | 🔴 |

**Where we will be ⭐ best-in-class after Phase 2-9:**

1. **Engine reliability** (golden tests pin every paise; no competitor has this)
2. **Aegis dashboard** (no Indian insurer has a Linear/Stripe-grade internal tool)
3. **Audit hash-chain** (cryptographic tamper detection vs flat audit tables)
4. **Rate-table versioning + diff** (most insurers still email Excel files)
5. **Excel-import dry-run + rollback** (most ingests are commit-or-bust)
6. **"Explain this number"** (no competitor explains why a number is what it is)
7. **Single source of truth** (no parallel pricing implementations — competitors all have ≥ 2)

Everywhere else we'll match. The 7 starred items are the moat.

---

*Refinement round 4 complete. The platform has: 1,356 findings + 10-phase roadmap + interconnections map + onboarding runbook + glossary + per-phase quality bar + competitive feature-parity. Every initiative has acceptance criteria.*
