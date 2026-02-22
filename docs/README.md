# Rate Calculator Platform — Documentation

> **Prudential Health Insurance** actuarial pricing engine, REST API, desktop calculator, and buy-online journey — built with Kotlin Multiplatform.

---

## Quick Links

| Document | Description |
|----------|-------------|
| [Architecture Overview](./01-architecture.md) | System design, modules, data flow, provider model |
| [Pricing Engine](./02-pricing-engine.md) | How premiums are calculated, Excel row-by-row mapping |
| [Rate Tables Reference](./03-rate-tables.md) | All covers, rates, Excel mapping, parameter options |
| [API Reference](./04-api-reference.md) | All server endpoints with request/response schemas and curl examples |
| [Desktop Calculator Guide](./05-desktop-guide.md) | UI walkthrough: all fields, pincode detection, result tables |
| [Excel Import Guide](./06-excel-import.md) | How to import new actuarial rate tables from Excel |
| [Business Rules](./07-business-rules.md) | Validation rules, mutual exclusions, constraints |
| [Development Guide](./08-development.md) | Build, run, extend: add covers, plans, importers |

---

## System Summary

The **Rate Calculator Platform** is a health insurance pricing system that digitises the Excel workbook `Rate_Calculator_v7.0.xlsm`. It provides:

- **Exact parity** with the actuarial Excel model — every row, every formula, reproduced in Kotlin
- **Multi-plan support** — 15 plan variants across Domestic, Senior, Sub-Standard, and Global tiers
- **Full add-on ecosystem** — 52 optional covers and 7 standalone discounts
- **Multi-year tenure pricing** — single-premium, 1–5 year policy periods with stepped discounts
- **Zone-based rating** — 5 geographic zones (Zone 1 = metro, Zone 4 = rural, Pan India)
- **Family floater underwriting** — 11 family-type combinations with per-member risk capture

### Platform Components

```
┌─────────────────────────────────────────────────────────────┐
│  :shared  — Kotlin Multiplatform pricing engine + models    │
├──────────────┬──────────────────────┬───────────────────────┤
│  :server     │  :desktop            │  :buyonline           │
│  Ktor REST   │  Compose Desktop     │  Compose Multiplatform│
│  API + DB    │  Rate Calculator UI  │  Buy Online Journey   │
└──────────────┴──────────────────────┴───────────────────────┘
```

### Technology Stack

| Layer | Technology |
|-------|-----------|
| Language | Kotlin 2.1.0 |
| UI Framework | Compose Multiplatform 1.7.3 |
| HTTP Server | Ktor 3.0.3 |
| HTTP Client | Ktor Client 3.0.3 |
| Database ORM | Exposed 0.57.0 |
| Database | PostgreSQL 15+ |
| Serialization | Kotlin Serialization 1.7.3 |
| Coroutines | Kotlin Coroutines 1.9.0 |
| Build Tool | Gradle 8.11.1 |
| JVM Target | JVM 17 / 21 |

### Key Facts

- Server runs on **port 9090** (`PORT=9090`) — port 8080 is reserved for other services
- Database: `localhost:5432/rate_calculator`, user `rate_admin`
- The pricing engine runs **fully offline** using `LocalRateDataProvider` — no database required for calculations
- All 52 cover definitions and 7 discount types are defined in `CoverCatalog.kt`
- Rate tables are hard-coded from the Excel source in `RateTables.kt`

---

## File Layout

```
rate/
├── docs/                    ← this documentation
├── shared/                  ← KMP pricing engine + domain models
│   └── src/commonMain/kotlin/com/rate/domain/
│       ├── model/           ← Models.kt, CoverDefinitions.kt
│       ├── engine/          ← PricingEngine.kt
│       ├── data/            ← RateTables.kt, CoverCatalog.kt, LocalRateDataProvider.kt
│       └── repository/      ← RateDataProvider interface
├── server/                  ← Ktor REST API
│   └── src/main/kotlin/com/rate/server/
│       ├── plugins/         ← Routing.kt
│       ├── routes/          ← Quote, Plan, Cover, Import, BuyOnline routes
│       ├── database/        ← Exposed tables + repositories
│       └── import/          ← ExcelImporter.kt
├── desktop/                 ← Compose Desktop calculator
│   └── src/main/kotlin/com/rate/desktop/
│       └── ui/calculator/   ← CalculatorScreen.kt, CalculatorViewModel.kt
└── buyonline/               ← Compose Multiplatform buy-online journey
    └── src/commonMain/kotlin/com/rate/buyonline/
        ├── App.kt
        ├── navigation/
        ├── api/
        ├── viewmodel/
        └── ui/
```

---

*Generated: 2026-02-22. Source: Rate_Calculator_v7.0.xlsm*
