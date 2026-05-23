# PRUHealth Aegis Platform — Documentation

> **Prudential Health Insurance** actuarial pricing engine, REST API, desktop calculator, and buy-online journey — built with Kotlin Multiplatform.

This index is the single entry point for the platform's docs. **Reference** documents explain how the system is built and how to operate the day-to-day surfaces (pricing engine, REST API, desktop calculator, Excel import, business rules, audit retention). **Runbooks** are incident-response playbooks keyed to alert titles — when a page fires at 3 a.m., open the matching runbook before improvising.

If you are new to the platform, start with [`01-architecture.md`](01-architecture.md) and [`08-development.md`](08-development.md). If you are on-call, jump straight to [`runbooks/`](runbooks/) and find the row matching your alert.

---

## Reference

| Doc | Covers |
|---|---|
| [01-architecture.md](01-architecture.md) | Module layout (`:shared`/`:server`/`:desktop`/`:buyonline`), data flow, provider model, JVM + WASM/iOS targets |
| [02-pricing-engine.md](02-pricing-engine.md) | Pricing engine internals — row-by-row replication of the actuarial Excel formulas |
| [03-rate-tables.md](03-rate-tables.md) | Actuarial rate-table format, all 52 covers + 7 discounts, parameter options, Excel mapping |
| [04-api-reference.md](04-api-reference.md) | REST API endpoints + request/response DTOs with curl examples |
| [05-desktop-guide.md](05-desktop-guide.md) | Aegis desktop binary operator guide — UI walkthrough, pincode detection, result tables |
| [06-excel-import.md](06-excel-import.md) | Excel rate-table import workflow (dry-run, commit, rollback) |
| [07-business-rules.md](07-business-rules.md) | 28 business rules — validation, mutual exclusions, member constraints |
| [08-development.md](08-development.md) | Developer setup — build, run, extend (add covers, plans, importers) |
| [audit-retention.md](audit-retention.md) | Audit log retention, hash-chain integrity model, IRDAI + GDPR/DPDPA compliance posture |

---

## Runbooks (incident response + ops procedures)

Each runbook follows a fixed structure (symptom, customer impact, detection, first five minutes, full remediation) so a responder can scan it under pressure. The full index with alert titles also lives in [`runbooks/README.md`](runbooks/README.md).

| Runbook | Severity | When to use |
|---|---|---|
| [aegis-operator-ops.md](runbooks/aegis-operator-ops.md) | n/a (ops) | Bootstrap mode, add/remove operators, weekly audit verification, outbox housekeeping |
| [audit-chain-integrity.md](runbooks/audit-chain-integrity.md) | SEV-1 | Hourly `/api/audit/verify` reports `ok=false`; `audit_chain_break` alert fires |
| [audit-service-degraded.md](runbooks/audit-service-degraded.md) | SEV-1 | `AuditEventService.record(...)` is failing/timing out; Aegis edits cannot commit |
| [buyonline-conversion-drop.md](runbooks/buyonline-conversion-drop.md) | SEV-2 | Hourly quote → proposal conversion drops > 30% below 7-day baseline |
| [database-readiness.md](runbooks/database-readiness.md) | SEV-1 | `/health/ready` returns 503; pods being pulled from the load balancer |
| [db-disk-pressure.md](runbooks/db-disk-pressure.md) | SEV-2 (escalates to SEV-1) | Postgres host disk > 80% full; writes at risk of failing |
| [engine-zero-premium.md](runbooks/engine-zero-premium.md) | SEV-0 | Pricing engine returns ₹0 totals; canonical-quote canaries diverging |
| [excel-import-rollback.md](runbooks/excel-import-rollback.md) | SEV-1 | Bad Excel import committed — wrong premiums in production, rollback needed |
| [latency-burn.md](runbooks/latency-burn.md) | SEV-2 | p99 latency on `/api/quotes/calculate` or `/api/buy-online/premium` > 1s for 5+ min |
| [otp-rate-limit.md](runbooks/otp-rate-limit.md) | SEV-2 | OTP 5/hour ceiling hit by > 5% of distinct mobiles in the last 15 min |
| [pii-in-logs.md](runbooks/pii-in-logs.md) | SEV-1 | Plaintext mobile / Aadhaar / PAN / bank / IFSC detected in centralised logs |
| [sms-gateway-degraded.md](runbooks/sms-gateway-degraded.md) | SEV-2 | SMS vendor degraded; customers not receiving OTPs (Phase 3+) |

---

## System Summary

The **PRUHealth Aegis Platform** is a health insurance pricing system that digitises the actuarial Excel workbook `Rate_Calculator_v7.0.xlsm`. It provides:

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
│   └── runbooks/            ← incident playbooks (RB-01 … RB-13)
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

*Last updated: 2026-05-23. Source: Rate_Calculator_v7.0.xlsm*
