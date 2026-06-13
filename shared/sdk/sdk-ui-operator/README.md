# sdk-ui-operator

The **operator + admin console** of the rate PHI platform — the top Compose-Multiplatform `-ui`
module. It composes the whole internal workbench: the relocated operational surfaces from the old
`aegis` app **plus** a generic, metadata-driven **ADMIN-CRUD config editor** wired to every
admin-managed entity in the catalog (+ rate tables) and a GROUP product / census-upload surface.

Package root: `com.rate.sdk.ui.operator`.

## Layer & dependencies

`core → sdk → sdk-ui → app`. This is a `sdk-ui` module: pure-KMP **commonMain** with Compose
(no Mongo driver, no Ktor-server, no Apache POI). It depends on:

- `:shared:sdk:sdk-ui-kit` — the Aegis design system (shell, table, drawer, inputs, theme).
- ALL feature SDKs — `sdk-catalog`, `sdk-party`, `sdk-rating`, `sdk-quoting`, `sdk-policy`,
  `sdk-audit`, `sdk-ingestion`, `sdk-proposal` — so ViewModels speak each feature's wire DTOs.
- `:shared:core:core-network` — the shared `TanvritClient` transport (auth + retry + AppJson).
- `:shared:core:core` — Money / regulatory tokens / rating contract.

All backend access goes over the **ktor client** to the server's admin/feature REST — there is no
direct persistence here.

## What lives here

```
OperatorConsole(client, role, …)     ← THE entry composable (also takes a prebuilt OperatorContext)
OperatorContext                      ← bundles TanvritClient → ConfigAdminApi/CatalogApi/QuoteApi/IngestionApi + role gating
OperatorRole                         ← ADMIN | BUSINESS | CUSTOMER (drives surface visibility)
OperatorRoute / OperatorNav          ← sealed nav: fixed Surfaces + generated Config(entityId) routes

network/
  ConfigAdminApi      ← the ONE generic admin-CRUD REST client (mirrors ConfigRepository over HTTP)
  AuditReadApi        ← read-only tail of the hash-chained audit log
  GroupQuotingApi     ← census upload + grade×age-band aggregation

model/
  FormSpec            ← FieldKind, FormField, FormBuffer, EntityDescriptor (the metadata contract)

registry/
  ConfigEntityRegistry        ← maps EVERY admin entity → descriptor (resource id + form + serializer)
  TypedEntityDescriptor<T>    ← typed bridge (captures T + serializer + read/applyEdit closures)
  RetailDescriptors           ← Plan, Product, Section, Cover, CI list, Annexure, Tenure, PincodeZone, AddOn
  GroupDescriptors            ← GroupProductConfig, Grade, BenefitSchedule, WaitingPeriod, Eligibility,
                                PpdPtdTable, DayCare, Consumables, HealthCheckup, ChronicOpd
  FormCodec                   ← pure buffer↔typed parse/format helpers

screen/
  ConfigEntityScreen          ← the generic list + create/edit/delete/restore/publish surface
  ConfigEntityForm            ← renders a form purely from FieldKind metadata

surface/  (relocated from aegis)
  DashboardSurface, QuoteExplorerSurface, CalculatorSurface, ImportSurface, AuditSurface,
  GroupQuotingSurface

viewmodel/
  ConfigEntityViewModel       ← the ONE generic CRUD VM (optimistic v, actor header)
  DashboardViewModel, CalculatorViewModel

di/
  operatorUiModule(role, actor)   ← Koin wiring
```

## The generic admin-CRUD config editor

The headline of this module: **one** `ConfigEntityScreen<T>` + `ConfigEntityViewModel` drive CRUD
for ~19 admin entities. Each entity contributes a `TypedEntityDescriptor` (form fields, list
columns, factory, `read`/`applyEdit` buffer closures, validation, serializer). Adding a new admin
entity is a single descriptor in the registry — no new screen, no new ViewModel, no new route.

Entities covered (registry ids):

- **Retail**: `plans`, `products`, `sections`, `covers`, `ci-lists`, `annexures`, `tenures`,
  `pincode-zones`, `addons`.
- **Group**: `group-products`, `group-grades`, `benefit-schedules`, `waiting-periods`,
  `eligibility`, `ppd-ptd`, `day-care`, `consumables`, `health-checkup`, `chronic-opd`.

Each descriptor `id` doubles as the admin REST resource segment:

```
POST   /api/admin/{id}/query    PageRequest        → Page<T>
GET    /api/admin/{id}/{eid}                        → T
POST   /api/admin/{id}          T                   → T
PUT    /api/admin/{id}/{eid}    T, ?expectedV=      → T   (optimistic)
DELETE /api/admin/{id}/{eid}                        → soft-delete
POST   /api/admin/{id}/{eid}/restore               → restore
POST   /api/admin/{id}/{eid}/publish               → publish draft
POST   /api/admin/{id}/bulk     List<T>            → count
```

The Mongo-backed actuals for these routes are implemented in the **server** over the generic
`ConfigRepository<T>`; this module only owns the client + the metadata-driven UI.

Nested list-of-object fields (CoverOption, CIItem, AddOnItem, schedule lines, PPD/PTD rows) are
surfaced as read-only summaries in this pass; their scalar + id-list rating/availability fields are
fully editable. Dedicated nested-row editors are a follow-up.

## Role gating

| Role | Surfaces |
|------|----------|
| `ADMIN` | dashboard, quotes, calculator, group, imports, audit + **all** config editors |
| `BUSINESS` | dashboard, quotes, calculator, group, audit (no config CRUD, no ingestion) |
| `CUSTOMER` | dashboard only (the real customer journey is `sdk-ui-buyonline`) |

## Relocated from the monolith

| Old (`aegis/.../surfaces`, `business`) | New |
|---|---|
| `HomeSurface` (FakeAegisRepo) | `surface/DashboardSurface` (real `QuoteApi` ledger) |
| `QuoteExplorerSurface` | `surface/QuoteExplorerSurface` |
| `CalculatorScreen` / `CalculatorViewModel` | `surface/CalculatorSurface` + `viewmodel/CalculatorViewModel` (server engine via `QuoteApi`) |
| `ImportScreen` / `ImportSurface` | `surface/ImportSurface` (`IngestionApi` version lifecycle) |
| `AuditEventsSurface` | `surface/AuditSurface` (`AuditReadApi`) |
| `PlanConfiguratorSurface` / `ProductCatalogSurface` / `CoverCatalogSurface` / `DiscountsSurface` | folded into the generic `ConfigEntityScreen` per entity |

## Usage

```kotlin
OperatorConsole(
    client = tanvritClient,          // wired by the app shell
    role = OperatorRole.ADMIN,
    actor = "ops.jane",              // stamped into admin mutations for the audit chain
    dark = false,
    locale = AegisLocale.EN,
)
```
