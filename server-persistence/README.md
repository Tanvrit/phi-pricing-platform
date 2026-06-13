# server-persistence

The **app-layer persistence shell** of the rate platform. This is the ONLY module that depends
on the **MongoDB driver** (`mongodb-driver-kotlin-coroutine` + `bson-kotlinx`) and **Apache POI**.
It implements every pure-KMP repository PORT declared in `core` and the `sdk-*` feature modules
with concrete MongoDB-backed actuals, loads the active rate-table version into an in-RAM snapshot
at boot, and runs the JVM-only Excel rate importer.

JVM-only by design (no `commonMain`): Mongo + POI are JVM libraries, and secrets/IO live solely
in the app layer per the `core → sdk → sdk-ui → app` acyclic DAG.

## Layout

```
com.rate.persistence/
├── config/      MongoConfig (env), MongoClientProvider (single MongoClient + database)
├── codec/       BsonCodec — kotlinx-serialization BSON registry (classDiscriminator "_class",
│                Instant→BSON-Date / Money→Int64 contextual codecs)
├── base/        MongoRepository (soft-delete-aware CRUD + optimistic concurrency + paging),
│                GenericConfigRepository (ConfigEntity admin-CRUD incl. publishDraft + bulkUpsert),
│                CollectionNames, SeqCounter ($inc), IndexBootstrap (unique / TTL / compound),
│                MongoBootstrap (indexes + cache load at boot)
├── rating/      RateTableCache (active version → immutable maps), MongoRateDataProvider,
│                MongoGroupRateDataProvider
├── repository/  concrete Mongo actuals for EVERY core + sdk PORT
├── tx/          QuoteIdempotencyTx (multi-doc transaction: quote insert + idempotency key)
├── importer/    ExcelRateImporter (POI → RateRowBatch)
└── di/          PersistenceModule (Koin: MongoClient + all repository bindings)
```

## How rating reads work

Rate lookups must be sub-millisecond (50+ covers × up to 5 years per quote), so the **active**
rate-table version's immutable rows are loaded ONCE into `RateTableCache` (at boot via
`MongoBootstrap`, and again on activation) keyed by the engine's exact lookup tuples.
`MongoRateDataProvider` (and `MongoGroupRateDataProvider`) resolve a rate by a single map get —
no per-call DB round-trip. Swapping the active version replaces the whole snapshot atomically.

## Generic admin-CRUD

Almost every config repository is a one-liner over `GenericConfigRepository<T>`:

```kotlin
class CoverRepositoryImpl(db: MongoDatabase) :
    GenericConfigRepository<Cover>(db, CollectionNames.COVERS, Cover::class.java, "Cover"),
    CoverRepository
```

It provides `list / get / create / update(expectedV) / softDelete / restore / publishDraft /
bulkUpsert` with optimistic concurrency on `v`, actor stamping, and an atomic draft→published
swap — so internal users manage any entity from the frontend with identical wiring.

## Money / Instant BSON representation (important)

The SDK entity fields are plain (non-`@Contextual`) `kotlinx.datetime.Instant` and
`com.rate.core.money.Money`, so kotlinx-serialization resolves their **compiled** serializers:

* `Instant` → ISO-8601 **string** (e.g. `2026-06-09T12:34:56Z`).
* `Money`   → sub-document `{ "paise": <Int64> }`.

Both round-trip faithfully, and ISO-8601 UTC strings sort/compare **chronologically** under
lexicographic ordering, so `createdAt: -1` sorts and `$gte`/`$lte` date-range queries are correct
on the string fields.

**TTL indexes require a real BSON `Date`**, which a string is not. So the TTL-bearing repositories
(`SessionRepositoryImpl`, `IdempotencyStoreImpl`, and `QuoteIdempotencyTx`) stamp a dedicated
`_ttlAt` BSON-Date field on write, and the TTL indexes (`IndexBootstrap`) point at `_ttlAt`.

The codec module DOES register contextual `Instant→BSON-Date` and `Money→Int64` serializers, so if
an entity field is ever annotated `@Contextual` it will store as a native BSON scalar with no
further change here.

## Boot

```kotlin
val cfg = MongoConfig.fromEnv()           // reads MONGO_URI (required) + MONGO_DB
startKoin { modules(persistenceModule(cfg), /* sdk modules */) }
MongoBootstrap.run(db, rateCache)         // idempotent: ensure indexes + load active rate version
```

## Multi-document transactions

`QuoteIdempotencyTx` persists a saved quote and reserves its idempotency key in ONE
`ClientSession` transaction (with the standard transient-error retry). Mongo multi-document
transactions require a replica-set / sharded deployment; on a standalone `mongod` callers should
fall back to the separate idempotent upserts.

## Environment

| Var         | Required | Default               | Notes                                |
|-------------|----------|-----------------------|--------------------------------------|
| `MONGO_URI` | yes      | (fail-fast)           | full connection string (with secret) |
| `MONGO_DB`  | no       | `rate`                | logical database name                |
```
