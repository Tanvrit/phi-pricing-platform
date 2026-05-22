# PRUHealth Rate Platform

A Kotlin Multiplatform (KMP) platform for an Indian retail health-insurance (PHI) product:
an actuarial pricing engine + Ktor REST API + Compose Desktop calculator (for agents/actuaries)
+ Compose Multiplatform buy-online journey (for customers).

The pricing engine replicates the `Rate_Calculator_v7.0.xlsm` actuarial workbook **row-for-row**:
14 plan tiers, 50+ covers, 7 zones, 11 family types, single-premium tenure discounts, capped
discount stacking, instalment loading, UW loading, and (since this branch) **18% GST** —
all KMP-portable so the same engine runs on JVM, iOS, and WASM.

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
        ┌───────▼────────┐   ┌────────────────┐   ┌────────────▼────────────┐
        │   :server      │   │   :desktop     │   │       :buyonline        │
        │   Ktor + JDBC  │   │ Compose Desktop│   │  Compose Multiplatform  │
        │   port 9090    │   │  agent tool    │   │  customer journey       │
        │                │   │                │   │  (22 screens)           │
        │  RateDataPro-  │   │  ApiClient ──→ │   │  ApiClient ──→ server   │
        │  viderImpl     │   │  server         │   │                         │
        │  → PostgreSQL  │   │                │   │                         │
        └───────┬────────┘   └────────────────┘   └─────────────────────────┘
                │
        ┌───────▼────────┐
        │  PostgreSQL    │   port 5432
        │  rate_calc db  │   Flyway-managed schema (non-destructive after V2)
        └────────────────┘
```

---

## Quickstart

### Prerequisites

| Tool      | Version  | Notes                                                                |
|-----------|----------|----------------------------------------------------------------------|
| JDK       | 21       | `kotlin.jvmToolchain(21)` — Gradle will auto-provision if missing    |
| Gradle    | 8.11.1   | Use the bundled wrapper; do not install globally                     |
| PostgreSQL| 16+      | Required by `:server`; not needed for `:shared` tests or `:desktop`  |
| Docker    | optional | `docker compose up` spins up Postgres + server in one command        |

### 1. Run all tests (no DB needed)

```bash
./gradlew :shared:jvmTest
```

### 2. Run the desktop rate calculator

```bash
./gradlew :desktop:run
```

### 3. Run the buy-online journey (KMP, currently JVM target only)

```bash
./gradlew :buyonline:run
```

### 4. Run the server (needs Postgres)

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

### 5. Full Docker dev stack

```bash
docker compose up --build
# server      → http://localhost:9090
# postgres    → localhost:5432
```

---

## Module map

| Module       | Lang/Tech                    | Purpose                                                 |
|--------------|------------------------------|---------------------------------------------------------|
| `:shared`    | Kotlin KMP (JVM/iOS/WASM)    | Pricing engine, domain, Money, validators               |
| `:server`    | Kotlin JVM + Ktor + Exposed  | REST API, PostgreSQL, Flyway, Excel import              |
| `:desktop`   | Kotlin JVM + Compose Desktop | Actuary / agent calculator + configurator + importer    |
| `:buyonline` | Kotlin KMP + Compose MP      | 22-screen customer purchase journey                     |

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

This branch landed the **Foundation Pack** (golden tests, GST, Money value type, unified
pricing, security baseline, Docker + CI). It does **not** yet ship: real OTP via SMS gateway,
real KYC integrations (DigiLocker / UIDAI / NSDL), real payment gateway (Razorpay / Cashfree),
PII column-level encryption with KMS, real JWT auth + RBAC, full IRDAI artefact generators
(prospectus / CIS / sales illustration), policy lifecycle (renewal / NCB / endorsement /
claim / portability / free-look), or a redesigned 22-screen journey. Those land in Phase 2+.

See [`AUDIT_REPORT.md`](AUDIT_REPORT.md) §"What 'best in the world' looks like after Phase 2+"
for the roadmap.

---

## License

Apache 2.0 — see [`LICENSE`](LICENSE).
