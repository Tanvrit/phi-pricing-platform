# Aegis — PRUHealth Rate Platform

Aegis is **one** Kotlin Multiplatform app — a single Compose Multiplatform module that
renders the customer buy-online journey on the web (WASM) and the operator / admin
console on the desktop (JVM), backed by a Ktor server that wraps the shared actuarial
pricing engine.

The pricing engine replicates the `Rate_Calculator_v7.0.xlsm` actuarial workbook **row-for-row**:
14 plan tiers, 50+ covers, 7 zones, 11 family types, single-premium tenure discounts, capped
discount stacking, instalment loading, UW loading, and **18% GST** — all KMP-portable so
the same engine runs on JVM, iOS, and WASM.

> 📋 **For a deep audit of the platform's state**, read [`AUDIT_REPORT.md`](AUDIT_REPORT.md)
> — 1,356 findings across architecture, security, UX, build, regulatory fit.

---

## Architecture (text diagram)

```
                ┌──────────────────────────────────────────────┐
                │                  :shared                     │   KMP — JVM, iOS, WASM
                │                                              │
                │  • domain.engine.PricingEngine               │   ← single source of truth
                │  • domain.model.*  (Plan, QuoteRequest, …)   │
                │  • domain.money.Money (Long paise)           │
                │  • domain.validation.Validators              │   ← PAN / IFSC / Aadhaar / pincode
                │  • domain.repository.RateDataProvider iface  │
                │  • domain.data.CoverCatalog, PincodeZoneMap  │
                └────────────────────┬─────────────────────────┘
                                     │ used by
                ┌────────────────────┴─────────────────────────┐
                │                                              │
        ┌───────▼────────┐                          ┌──────────▼──────────────┐
        │   :server      │                          │        :aegis           │
        │   Ktor + JDBC  │                          │  Compose Multiplatform  │
        │   port 9090    │                          │  (JVM + WASM)           │
        │                │                          │                         │
        │  RateDataPro-  │ ◀── ApiClient ── role ─▶ │  CUSTOMER  → buyonline  │
        │  viderImpl     │                          │  BUSINESS  → operator   │
        │  → PostgreSQL  │                          │  ADMIN     → audit/RBAC │
        └───────┬────────┘                          └─────────────────────────┘
                │                                       jvm    → desktop binary
        ┌───────▼────────┐                              wasmJs → Cloudflare Pages
        │  PostgreSQL    │   port 5432
        │  rate_calc db  │   Flyway-managed schema (non-destructive after V2)
        └────────────────┘
```

The `:aegis` shell renders one of 14 **operator surfaces** (left rail) when started in
BUSINESS or ADMIN role, and the 22-screen customer journey when started in CUSTOMER role.
Surface selection is in-process — there is no client-side router. Deep links arrive as
URL params (`?role=`, `?session=`, `?quote=`) on the WASM build.

---

## Quickstart

### Prerequisites

| Tool      | Version  | Notes                                                                |
|-----------|----------|----------------------------------------------------------------------|
| JDK       | 21       | `kotlin.jvmToolchain(21)` — Gradle will auto-provision if missing    |
| Gradle    | 8.11.1   | Use the bundled wrapper; do not install globally                     |
| PostgreSQL| 16+      | Required by `:server`; not needed for `:shared` tests or `:aegis`    |
| Docker    | optional | `docker compose up` spins up Postgres + server in one command        |

### 1. Run all tests (no DB needed)

```bash
./gradlew :shared:jvmTest
```

### 2. Run Aegis desktop (operator / BUSINESS role — default)

```bash
./gradlew :aegis:run -Daegis.role=BUSINESS
```

### 3. Run Aegis desktop in CUSTOMER role (buyonline preview)

```bash
./gradlew :aegis:run -Daegis.role=CUSTOMER
# convenience alias also wired in :aegis:
./gradlew :aegis:runCustomer   # alias for the above where defined
```

### 4. Build the Aegis WASM bundle (what Cloudflare Pages serves)

```bash
./gradlew :aegis:wasmJsBrowserDistribution
# Output: aegis/build/dist/wasmJs/productionExecutable/
# Deployed to: https://phi-buyonline.pages.dev/
```

### 5. Run the server (needs Postgres)

```bash
# 1. Bring up Postgres locally OR via Docker:
docker compose up -d postgres
# 2. Set required env vars (no plaintext credentials in app config any more):
export DB_URL=jdbc:postgresql://localhost:5432/rate_calculator
export DB_USER=rate_admin
export DB_PASSWORD=replace_me_with_real_password
export CORS_ALLOWED_ORIGINS=http://localhost:9090,http://localhost:8080
export OTP_TOKEN_SECRET=$(openssl rand -hex 32)
# 3. Start the server:
PORT=9090 ./gradlew :server:run
# 4. Smoke test:
curl http://localhost:9090/health
```

### 6. Full Docker dev stack

```bash
docker compose up --build
# server      → http://localhost:9090
# postgres    → localhost:5432
```

---

## Operator surfaces inventory

The BUSINESS / ADMIN shell exposes the following surfaces from the left rail.
"Path" is the in-app surface key — these are not URLs; the shell is a single-page
Compose app and surface selection is in-process. "Source" is the primary data feed.

| Surface              | Path                | What it does                                                                                  | Source                                          |
|----------------------|---------------------|-----------------------------------------------------------------------------------------------|-------------------------------------------------|
| Home                 | `surface://home`    | KPI tiles + sparklines + activity feed + recent quotes                                        | `rememberDashboardData`                         |
| Calculator           | `surface://calc`    | Embedded rate calculator + configurator + import drawer                                       | `ConfiguratorBody`                              |
| Quotes               | `surface://quotes`  | Search + filter + drawer over the quote stream                                                | `rememberDashboardData`                         |
| Product Catalog      | `surface://products`| Read-only plan inventory grouped by family                                                    | `client.getPlans`                               |
| Plan Configurator    | `surface://plans`   | CRUD on plans + per-plan quote history dialog                                                 | `client.savePlan`, `client.getQuotesByPlan`     |
| Cover Catalog        | `surface://covers`  | Catalogue of 47+ covers (server-rendered metadata)                                            | `CoverCatalog.ALL`, `/api/covers`               |
| Discounts            | `surface://disc`    | Catalogue of discount rates resolved against `RateDataProvider`                               | `/api/discounts`                                |
| Prospectus           | `surface://rules`   | IRDAI-style printable disclosure per plan (12-section, print-to-PDF)                          | `client.getPlans` + `/api/plans/{id}/prospectus.html` |
| Reports              | `surface://reports` | Bucketed time series + distributions + buyonline funnel + CSV export                          | `rememberDashboardData` + redacted sessions     |
| UW Queue             | `surface://uw`      | Quotes flagged for underwriter review (derived view, no separate persistence)                 | derived from `dashboard.quotes`                 |
| Audit                | `surface://audit`   | Hash-chain audit log + integrity check + idempotency cache + SSE live stream                  | `/api/audit/events`, `/api/audit/stream`        |
| Server Health        | `surface://rates`*  | `/health` + `/metrics` + OTP + idempotency telemetry                                          | `/health`, `/metrics`                           |
| Settings             | `surface://settings`| Server URL + role + theme + operator identity + RBAC + system info                            | `AegisSettingsStore` + `/api/operators`         |
| Import               | `surface://import`  | Excel upload to repopulate rate tables (gated by `import.upload`)                             | `POST /api/import/upload`                       |

\* `RATE_TABLES` is the legacy enum name; the displayed label is **Server health**.

---

## Customer journey (buyonline)

CUSTOMER role renders a 22-screen flow — Landing → OTP → GetStarted → PED → CI →
PlanLoading → Eligibility → Quote → AddOns → PlanSummary → PersonalDetails → Lifestyle →
Medical → Payment → PaymentSuccess → KycMethod → KycDetails → KycOtp → BankDetails →
KycSubmitted → ApplicationComplete → Satisfaction.

A 5-stage `StepIndicator` collapses the screens into **Eligibility → Quote → Proposal →
KYC → Done**. State is snapshotted to `POST /api/buy-online/session` on every meaningful
change so resume-via-`?session=<id>` works from any device. Shareable quote previews
arrive via `?quote=<id>` and render the frozen quote behind the same step indicator.

---

## API endpoints

All routes return JSON unless noted. Mutations honour `Idempotency-Key` headers where
applicable (quotes, proposals, imports). Scope gating per route is in
[Routing.kt](server/src/main/kotlin/com/rate/server/plugins/Routing.kt) and per-resource
route files under `server/src/main/kotlin/com/rate/server/routes/`.

### Plans

| Method | Path                                | Purpose                                              | Scope          |
|--------|-------------------------------------|------------------------------------------------------|----------------|
| GET    | `/api/plans`                        | List all plans                                       | —              |
| GET    | `/api/plans/{id}`                   | Fetch a single plan                                  | —              |
| POST   | `/api/plans`                        | Upsert a plan (creates audit diff payload)           | `plans.write`  |
| DELETE | `/api/plans/{id}`                   | Delete a plan                                        | `plans.delete` |
| GET    | `/api/plans/{id}/prospectus.html`   | Printable IRDAI-style prospectus (HTML)              | —              |

### Quotes

| Method | Path                              | Purpose                                                |
|--------|-----------------------------------|--------------------------------------------------------|
| POST   | `/api/quotes/calculate`           | Calculate without persisting (audits as `quote.calculated`) |
| POST   | `/api/quotes`                     | Calculate and persist (idempotent on `Idempotency-Key`) |
| GET    | `/api/quotes`                     | List recent quote summaries (`?limit=`)                 |
| GET    | `/api/quotes/{id}`                | Fetch full request + result by id                       |
| GET    | `/api/quotes/by-plan/{planId}`    | Per-plan quote history (configurator drill-down)        |

### Covers

| Method | Path                          | Purpose                                                       |
|--------|-------------------------------|---------------------------------------------------------------|
| GET    | `/api/covers`                 | All cover definitions with parameter options                  |
| GET    | `/api/covers/age-bands`       | Actuarial age bands                                           |
| GET    | `/api/covers/family-types`    | Family type codes (adult/child counts, floater flag)          |
| GET    | `/api/covers/sum-insureds`    | `{ domestic: [...], global: [...] }` SI grids                 |

### Discounts

| Method | Path             | Purpose                                                              |
|--------|------------------|----------------------------------------------------------------------|
| GET    | `/api/discounts` | Discount catalogue with live rates resolved by the rate provider     |

### Buy-online (customer journey backend)

| Method | Path                                          | Purpose                                                                    | Scope            |
|--------|-----------------------------------------------|----------------------------------------------------------------------------|------------------|
| POST   | `/api/buy-online/otp/send`                    | Send login OTP (rate-limited)                                              | —                |
| POST   | `/api/buy-online/otp/verify`                  | Verify login OTP → issue session token                                     | —                |
| GET    | `/api/buy-online/hospitals?pincode=`          | Deterministic hospital count per pincode (placeholder)                     | —                |
| POST   | `/api/buy-online/eligibility`                 | Filter members by PED / critical-illness declarations                      | —                |
| POST   | `/api/buy-online/premium`                     | Real `PricingEngine` quote for buyonline tiers/add-ons                     | —                |
| POST   | `/api/buy-online/kyc/otp/send`                | Send KYC OTP                                                               | —                |
| POST   | `/api/buy-online/kyc/otp/verify`              | Verify KYC OTP                                                             | —                |
| POST   | `/api/buy-online/proposal`                    | Submit proposal (idempotent on `Idempotency-Key`)                          | —                |
| GET    | `/api/buy-online/proposal/{proposalNumber}`   | Track proposal status                                                      | —                |
| POST   | `/api/buy-online/session`                     | Save session snapshot (resume key)                                         | —                |
| GET    | `/api/buy-online/session/{id}`                | Load session snapshot                                                      | —                |
| GET    | `/api/buy-online/sessions`                    | Aggregated operator funnel (PII-redacted, `?limit=`)                       | `sessions.read`  |

### Audit

| Method | Path                          | Purpose                                                                       | Scope          |
|--------|-------------------------------|-------------------------------------------------------------------------------|----------------|
| GET    | `/api/audit/events`           | Newest-first audit log (`?limit=` 1..500, default 100)                        | —              |
| GET    | `/api/audit/verify`           | Verify hash chain (`?fromId=`, `?toId=`)                                      | `audit.verify` |
| GET    | `/api/audit/idempotency`      | Inspect recent idempotency cache entries                                      | `audit.verify` |
| GET    | `/api/audit/stream`           | Server-Sent Events live feed of new audit rows (`text/event-stream`)          | —              |

### Operators (RBAC allowlist)

| Method | Path                          | Purpose                                                       | Scope              |
|--------|-------------------------------|---------------------------------------------------------------|--------------------|
| GET    | `/api/operators`              | List operator allowlist (open for Settings bootstrap)         | —                  |
| POST   | `/api/operators`              | Upsert an operator entry                                      | `operators.write`  |
| DELETE | `/api/operators/{identity}`   | Remove an operator entry                                      | `operators.write`  |

### Import

| Method | Path                     | Purpose                                                                          | Scope           |
|--------|--------------------------|----------------------------------------------------------------------------------|-----------------|
| POST   | `/api/import/upload`     | Multipart Excel upload; SHA-256 of bytes participates in idempotency replay key  | `import.upload` |

### Ops

| Method | Path             | Purpose                                                                |
|--------|------------------|------------------------------------------------------------------------|
| GET    | `/health`        | Liveness — cheap, no dependencies                                      |
| GET    | `/health/live`   | Same as `/health` (explicit liveness probe alias)                      |
| GET    | `/health/ready`  | Readiness — runs `SELECT 1` against Postgres; gates K8s traffic        |
| GET    | `/metrics`       | Prometheus exposition format (`text/plain; version=0.0.4`)             |

---

## RBAC scopes

Mutations and sensitive reads are gated by scopes carried in the operator allowlist
(`/api/operators`). Current scopes:

- `plans.write` — upsert plans (`POST /api/plans`)
- `plans.delete` — delete plans (`DELETE /api/plans/{id}`)
- `import.upload` — Excel rate-table import (`POST /api/import/upload`)
- `audit.verify` — chain verification + idempotency cache inspection
- `operators.write` — allowlist CRUD (`POST/DELETE /api/operators`)
- `sessions.read` — redacted buy-online funnel (`GET /api/buy-online/sessions`)

**Bootstrap mode:** when the operators store is empty, the server runs in a permissive
mode so a first admin can grant themselves `operators.write` and seed the allowlist —
see `OperatorsStore`. Once any operator exists, RBAC is enforced normally.

---

## Deployments

- **WASM (web)** — `./gradlew :aegis:wasmJsBrowserDistribution` builds the static bundle
  served at <https://phi-buyonline.pages.dev/> (Cloudflare Pages). Deep-link URL params:
  - `?role=customer|business|admin` — pick the shell variant
  - `?session=<id>` — resume a buyonline journey
  - `?quote=<id>` — render a shared, read-only quote
- **JVM (desktop)** — `./gradlew :aegis:run -Daegis.role=BUSINESS` for the operator
  console; `-Daegis.role=CUSTOMER` for the buyonline preview. The packaged distribution
  ships as `aegis-business` / `aegis-customer` launchers depending on the role baked in.

---

## Module map

| Module    | Lang/Tech                       | Purpose                                                  |
|-----------|---------------------------------|----------------------------------------------------------|
| `:shared` | Kotlin KMP (JVM/iOS/WASM)       | Pricing engine, domain, Money, validators                |
| `:server` | Kotlin JVM + Ktor + Exposed     | REST API, PostgreSQL, Flyway, Excel import, audit chain  |
| `:aegis`  | Kotlin KMP (JVM + WASM)         | Single Compose MP app — role-routed (Customer/Business/Admin) |

The `:aegis` module subsumes the retired `:desktop` (calculator + configurator) and
`:buyonline` (22-screen journey) modules — see `CHANGELOG.md` Unreleased entry.

---

## Key docs

- [`AUDIT_REPORT.md`](AUDIT_REPORT.md) — 1,356-finding end-to-end audit
- [`DASHBOARD_REDESIGN_PLAN.md`](DASHBOARD_REDESIGN_PLAN.md) — 5-pass redesign of the business dashboard / configurator
- [`docs/01-architecture.md`](docs/01-architecture.md) — module layout, data flow
- [`docs/02-pricing-engine.md`](docs/02-pricing-engine.md) — pricing engine row-by-row
- [`docs/03-rate-tables.md`](docs/03-rate-tables.md) — actuarial rate tables
- [`docs/04-api-reference.md`](docs/04-api-reference.md) — REST API
- [`docs/05-desktop-guide.md`](docs/05-desktop-guide.md) — desktop app guide
- [`docs/06-excel-import.md`](docs/06-excel-import.md) — Excel import workflow
- [`docs/07-business-rules.md`](docs/07-business-rules.md) — 28 business rules catalogue
- [`docs/08-development.md`](docs/08-development.md) — developer setup
- [`CHANGELOG.md`](CHANGELOG.md) — release notes
- [`CONTRIBUTING.md`](CONTRIBUTING.md) — branch model, PR process

---

## Status & limitations (read before shipping)

This branch ships the Foundation Pack (golden tests, GST, Money value type, unified
pricing engine, security baseline, Docker + CI), the unified `:aegis` Compose shell
with 14 operator surfaces, the hash-chained audit log + SSE stream, the operator
allowlist + scope gating, the IRDAI-style server-rendered prospectus, and the
save+resume / shareable-quote URL deep links.

It does **not** yet ship: real JWT auth (the actor subject is currently advisory),
real SMS OTP gateway (codes are in-memory and logged in dev), real KYC integrations
(DigiLocker / UIDAI / NSDL), real payment gateway (Razorpay / Cashfree), IRDAI
artefact generators beyond Prospectus (CIS, sales illustration, KFD), PII column-level
encryption with KMS, full localization, a structured medical questionnaire, or policy
lifecycle flows (renewal / NCB / endorsement / claim / portability / free-look).
Those land in Phase 2+.

See [`AUDIT_REPORT.md`](AUDIT_REPORT.md) §"What 'best in the world' looks like after Phase 2+"
for the roadmap.

---

## License

Apache 2.0 — see [`LICENSE`](LICENSE).
