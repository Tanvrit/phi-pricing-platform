# Architecture Overview

## Table of Contents
1. [Module Structure](#1-module-structure)
2. [Tech Stack](#2-tech-stack)
3. [Data Flow](#3-data-flow)
4. [Rate Data Provider Model](#4-rate-data-provider-model)
5. [Port and Network Layout](#5-port-and-network-layout)
6. [Database Schema Overview](#6-database-schema-overview)
7. [Shared Module — KMP Targets](#7-shared-module--kmp-targets)

---

## 1. Module Structure

The project is organised as four Gradle modules. Each module is independently buildable and has a clearly defined responsibility.

### Module: `:shared`

**Purpose:** Kotlin Multiplatform (KMP) library that contains every piece of business logic. It has zero platform-specific dependencies.

| Sub-package | Contents |
|-------------|----------|
| `com.rate.domain.model` | Domain models (`Plan`, `Member`, `QuoteRequest`, `QuoteResult`), enums (`Tenure`, `Zone`, `PaymentMode`, `PlanType`), age bands, family types |
| `com.rate.domain.engine` | `PricingEngine` — the core calculation engine replicating Excel row-by-row |
| `com.rate.domain.data` | `RateTables` (hard-coded actuarial tables), `CoverCatalog` (UI metadata), `LocalRateDataProvider` (offline implementation) |
| `com.rate.domain.repository` | `RateDataProvider` interface — the seam between pricing engine and data layer |

**Supported KMP targets:**
- `jvm` — used by `:server` and `:desktop`
- `iosArm64`, `iosX64`, `iosSimulatorArm64` — for future iOS app
- `wasmJs` — for future web frontend

**Key design principle:** The pricing engine only depends on the `RateDataProvider` interface. Any implementation — local tables, database, remote API — can be substituted without touching the engine.

---

### Module: `:server`

**Purpose:** Ktor HTTP server exposing a REST API. Persists quotes to PostgreSQL via Exposed ORM.

| Layer | Technology |
|-------|-----------|
| HTTP framework | Ktor 3.0.3 |
| Serialisation | Kotlin Serialization (Content Negotiation) |
| ORM | JetBrains Exposed 0.57.0 |
| Database | PostgreSQL (JDBC) |
| Rate data | `LocalRateDataProvider` (in-process, no DB round-trip for calculations) |

**Entry point:** `com.rate.server.ApplicationKt.module` (configured in `application.conf`)

**Route groups:**

| File | Routes |
|------|--------|
| `QuoteRoutes.kt` | `POST /api/quotes/calculate`, `POST /api/quotes`, `GET /api/quotes/{id}`, `GET /api/quotes` |
| `PlanRoutes.kt` | `GET /api/plans`, `GET /api/plans/{id}`, `POST /api/plans`, `DELETE /api/plans/{id}` |
| `CoverRoutes.kt` | `GET /api/covers`, `GET /api/covers/age-bands`, `GET /api/covers/family-types`, `GET /api/covers/sum-insureds` |
| `ImportRoutes.kt` | `POST /api/import/seed`, `POST /api/import/upload` |
| `BuyOnlineRoutes.kt` | 9 buy-online journey endpoints |

---

### Module: `:desktop`

**Purpose:** Compose Desktop application providing the actuary-facing rate calculator UI. Runs on JVM.

**Entry point:** `./gradlew :desktop:run`

**Navigation screens:**
- Calculator — main pricing form and results
- Configurator — plan and cover management
- Import — Excel upload interface

**ViewModel pattern:** `CalculatorViewModel` manages all state and calls `LocalRateDataProvider` directly. The desktop app does NOT require the server to be running for calculations; it only needs the server for saving quotes to the database.

---

### Module: `:buyonline`

**Purpose:** Compose Multiplatform customer-facing buy-online journey with 22 screens.

**Entry point:** `./gradlew :buyonline:run` (JVM desktop for development)

**Key files:**
```
buyonline/src/commonMain/kotlin/com/rate/buyonline/
├── App.kt                          — root BuyOnlineApp() composable
├── navigation/Navigation.kt        — 22 sealed class screens
├── model/BuyOnlineModels.kt        — all data models + MEDICAL_QUESTIONS
├── api/BuyOnlineApiClient.kt       — Ktor HTTP client (9 endpoints)
├── viewmodel/BuyOnlineViewModel.kt — full state + real API calls + fallbacks
└── ui/[screen]/...                 — 22 screen composables
```

**Flow:** Landing → OTP → GetStarted → PED → CriticalIllness → PlanLoading → Eligibility → Quote → AddOns → PlanSummary → PersonalDetails → Lifestyle → MedicalQuestions → Payment → PaymentSuccess → KycMethod → KycDetails → KycOtp → BankDetails → KycSubmitted → ApplicationComplete → Satisfaction

---

## 2. Tech Stack

```
┌─────────────────────────────────────────────────────────────────────┐
│                     Tech Stack Summary                              │
├─────────────────────────┬───────────────────────────────────────────┤
│ Kotlin                  │ 2.1.0                                     │
│ Compose Multiplatform   │ 1.7.3                                     │
│ Ktor (server)           │ 3.0.3                                     │
│ Ktor (client)           │ 3.0.3                                     │
│ Exposed ORM             │ 0.57.0                                    │
│ Kotlin Serialization    │ 1.7.3                                     │
│ Kotlin Coroutines       │ 1.9.0                                     │
│ Kotlin DateTime         │ 0.6.x                                     │
│ PostgreSQL Driver       │ 42.x                                      │
│ Gradle                  │ 8.11.1                                    │
│ JVM Target              │ 17 / 21                                   │
└─────────────────────────┴───────────────────────────────────────────┘
```

---

## 3. Data Flow

### Calculation Flow (Desktop — Offline)

```
User fills form
      │
      ▼
CalculatorViewModel.calculate()
      │
      ▼
PricingEngine.calculate(QuoteRequest)
      │   (in-process, no network)
      ▼
LocalRateDataProvider
  ├── getBasePremium(planId, familyType, zone, ageBand, SI)
  │     └── RateTables.PHI_BASIC_FT[ft][band][si] × zoneFactor × planFactor
  ├── getCoverRate(coverId, param1, param2, ageBand, si, planKey)
  │     └── RateTables.[COVER_RATES]
  └── getMemberLevelRate(coverId, memberAgeBand, param1)
        └── RateTables.[MEMBER_RATES]
      │
      ▼
QuoteResult (basePremiumTotal, coverBreakdown, discountBreakdown, ...)
      │
      ▼
CalculatorScreen renders PremiumResultPanel + TenureComparisonTable
```

### Calculation Flow (API)

```
Client POST /api/quotes/calculate  {QuoteRequest JSON}
      │
      ▼
Ktor routing → QuoteRoutes.kt
      │
      ▼
PricingEngine.calculate(QuoteRequest)
  (uses LocalRateDataProvider — no DB call for rates)
      │
      ▼
QuoteResult JSON → Client

      — or —

POST /api/quotes  (calculate + persist)
      │
      ▼
PricingEngine.calculate() → QuoteResult
      │
      ▼
QuoteRepositoryImpl.saveQuote(request, result)  → PostgreSQL
      │
      ▼
201 Created  {id, result}
```

### Buy Online Flow

```
BuyOnlineViewModel
      │
      ├── OTP send/verify → POST /api/buy-online/otp/send
      ├── Eligibility check → POST /api/buy-online/eligibility
      ├── Premium calculation → POST /api/buy-online/premium
      ├── KYC → POST /api/buy-online/kyc/otp/send + /verify
      └── Proposal → POST /api/buy-online/proposal
                         │
                         ▼
                  GET /api/buy-online/proposal/{id}
```

---

## 4. Rate Data Provider Model

The pricing engine is decoupled from the data layer via the `RateDataProvider` interface:

```kotlin
interface RateDataProvider {
    suspend fun getAllPlans(): List<Plan>
    suspend fun getPlan(planId: String): Plan?
    suspend fun getBasePremium(planId: String, familyType: String, zone: String,
                               ageBandMinAge: Int, sumInsured: Long): Double
    suspend fun getCoverRate(coverId: String, param1: String? = null,
                             param2: String? = null, ageBandMinAge: Int? = null,
                             sumInsured: Long? = null,
                             planOrTenureKey: String? = null): Double
    suspend fun getMemberLevelRate(coverId: String, memberAgeBandMin: Int,
                                   param1: String? = null, sumInsured: Long? = null): Double
    suspend fun getInstalmentCount(tenure: Tenure, paymentTenure: Tenure,
                                   paymentMode: PaymentMode): Int
}
```

### Implementations

| Implementation | Location | Description |
|----------------|----------|-------------|
| `LocalRateDataProvider` | `:shared` `data/` | Hard-coded tables from Excel. No DB, no network. Used by desktop and API server. |
| `RateDataProviderImpl` | `:server` `database/` | PostgreSQL-backed implementation using Exposed. Used when rates have been imported via the Excel importer. |

**Current production choice:** Both the desktop app and the API server use `LocalRateDataProvider`. The database-backed implementation is available for environments where dynamic rate updates are required.

---

## 5. Port and Network Layout

```
┌──────────────────────────────────────────────┐
│  Development Machine                         │
│                                              │
│  :server (Ktor)          port 9090           │
│  :desktop (Compose)      no port (local JVM) │
│  :buyonline (Compose)    no port (local JVM) │
│  PostgreSQL              port 5432           │
│  Other services (Deen)   port 8080  ← TAKEN │
└──────────────────────────────────────────────┘
```

**Server startup:**
```bash
PORT=9090 ./gradlew :server:run
```

The `application.conf` file specifies:
```hocon
ktor {
    deployment {
        port = 8080          # default (do not use — occupied)
        port = ${?PORT}      # override with PORT env var
    }
}
```

The `BuyOnlineApiClient` defaults to `http://localhost:9090`. If you change the port, update the client's `baseUrl`.

---

## 6. Database Schema Overview

The PostgreSQL database `rate_calculator` contains the following tables:

| Table | Purpose |
|-------|---------|
| `quotes` | Persisted quote requests and calculated results (JSONB) |
| `plans` | Plan catalogue (can be updated via API without redeployment) |
| `rate_entries` | Base premium rates imported from Excel |
| `cover_rates` | Cover-level rates imported from Excel |
| `member_rates` | Member-level rates imported from Excel |

**Connection details:**
```
Host:     localhost
Port:     5432
Database: rate_calculator
User:     rate_admin
Password: rate123
MaxPool:  10
```

---

## 7. Shared Module — KMP Targets

The `:shared` module is configured for the following Kotlin Multiplatform targets:

```kotlin
kotlin {
    jvm()                   // server + desktop
    iosArm64()              // physical iPhone
    iosX64()                // Intel Mac simulator
    iosSimulatorArm64()     // Apple Silicon Mac simulator
    wasmJs { browser() }    // web frontend (future)
}
```

The pricing engine has **no platform-specific code** in `commonMain`. All targets share the identical calculation logic, ensuring actuarial consistency across all delivery channels.

**Dependencies (commonMain only):**
- `kotlinx.serialization.json` — JSON encode/decode of domain models
- `kotlinx.coroutines.core` — suspending data access patterns
- `kotlinx.datetime` — quote ID timestamp generation

---

*Next: [Pricing Engine](./02-pricing-engine.md)*
