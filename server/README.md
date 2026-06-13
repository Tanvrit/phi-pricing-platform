# :server — Ktor app shell (Mongo-only)

The HTTP backend. **APP layer** of the `core → sdk → sdk-ui → app` DAG. It mounts the
pure-KMP **sdk feature handlers** as routes and wires them to the Mongo-backed PORT actuals
from `:server-persistence` (the only module that may touch the Mongo driver). **Secrets**
(`OTP_TOKEN_SECRET` / `JWT_SIGNING_SECRET` / `MONGO_URI`) are read **here and nowhere else**.

No Exposed / Flyway / Hikari / Postgres / POI — **MongoDB only**. The Excel rate importer
(POI) lives in `:server-persistence`; CSV ingestion lives in `:sdk-ingestion`.

## Boot sequence (`Application.module`)
1. read `AppSecrets.fromEnv()` + `MongoConfig.fromEnv()` — **fail-fast** in production;
2. `install(Koin)` over `serverModule + persistenceModule + authModule + every sdk feature module`;
3. `MongoBootstrap.run(...)` — idempotent indexes + load the ACTIVE rate-table snapshot into the
   in-RAM engine (first quote is warm);
4. install plugins: serialization (`AppJson`), HTTP (CORS + security headers + `StatusPages`
   mapping `DomainException` → HTTP), PII-masked request log;
5. `configureRouting(...)` — per-feature route groups + the generic admin-CRUD factory;
6. start the background audit chain-verifier; register graceful shutdown.

## Package layout
```
com.rate.server
├── Application.kt              EngineMain + module() boot
├── di/ServerModule.kt         app-layer Koin: secrets, JWT signer, OTP/session stores, audit facade, email
├── security/
│   ├── AppSecrets.kt          env-loaded secrets, fail-fast (the ONLY home for keys)
│   ├── JwtTokenSigner.kt      HS256 TokenSigner actual (core-auth PORT)
│   ├── HmacIdempotencyHasher.kt  keyed request-body hash for idempotency
│   ├── InMemoryOtpStore.kt    core-auth OtpStore actual (Mongo later)
│   └── InMemorySessionStore.kt core-auth SessionStore actual (Mongo later)
├── plugins/{Serialization,HTTP,RequestLog,Idempotency,Routing}.kt
├── auth/Rbac.kt               requireScope/auditActor over core-auth Authorization + TokenSigner
├── audit/ServerAuditService.kt facade over sdk-audit AuditRecorder + AuditBroadcast + periodic verify
├── metrics/Metrics.kt         dependency-free Prometheus (gauge suppliers, no Hikari)
├── email/EmailSender.kt       Phase-1 filesystem .eml outbox
├── logging/PiiMaskingConverter.kt  logback PII mask (%piimsg)
└── routes/                    per-feature groups + adminCrudRoutes<T> factory + RouteSupport
```

## Endpoints (paths kept backward-compatible)
| Group | Paths |
|-------|-------|
| Health/metrics | `GET /health`, `/health/live`, `/health/ready` (Mongo ping), `GET /metrics` |
| Quotes | `POST /api/quotes/calculate`, `POST /api/quotes` (idempotent), `GET /api/quotes`, `GET /api/quotes/{id}`, `GET /api/quotes/by-plan/{planId}` |
| Buy-online | `POST /api/buy-online/otp/{send,verify}`, `/kyc/otp/{send,verify}`, `/kyc`, `GET /hospitals`, `POST /eligibility`, `POST /premium`, `POST /proposal` (idempotent), `GET /proposal/{n}`, `POST/GET /session`, `GET /sessions` (redacted, scope-gated), `POST /session/email` |
| Policy | `GET /api/policies`, `/{id}`, `/by-mobile/{m}`, `/{id}/claims`, `POST /{id}/renewal-illustration` |
| Catalog | `GET /api/catalog/{age-bands,family-types,zone?pincode=}` |
| Import | `POST /api/import/upload` (multipart Excel, idempotent) |
| Audit | `GET /api/audit/events`, `/api/audit/verify`, `/api/audit/stream` (SSE) |
| Admin | `GET /api/admin/{config,log,outbox}`, `DELETE /api/admin/outbox` |
| **Admin CRUD** | `GET/POST /api/config/<entity>`, `GET/PUT/DELETE /api/config/<entity>/{id}`, `POST /{id}/{restore,publish}`, `POST /import`, `GET /export` — for **every** config entity (products, sections, covers, addons, tenures, pincode-zones, critical-illness lists, annexures, and all group config) |

### The generic admin-CRUD factory
`adminCrudRoutes<T : ConfigEntity>(path, entityName, serializer, repo, audit)` collapses the
monolith's per-entity route files into ONE wiring over the core `ConfigRepository` PORT —
identical list (paged/filtered) / get / create / optimistic-update (`?expectedV=`) / soft-delete /
restore / publish-draft / bulk-import / export semantics, **every mutation audited** and
**scope-gated** by `config.<entity>.<verb>`.

## RBAC
Decisions are the pure core-auth `Authorization`/`Scope`; the route glue (`auth/Rbac.kt`) verifies
the `Authorization: Bearer <jwt>` via the `JwtTokenSigner` actual. During the auth-migration window
a bare `X-Aegis-Actor` header (no bearer) is trusted as a BUSINESS operator (preserves the
monolith's "trust the header" RBAC) and becomes the audit subject.

## Run
```bash
MONGO_URI=mongodb://localhost:27017 MONGO_DB=rate \
OTP_TOKEN_SECRET=... JWT_SIGNING_SECRET=... \
PORT=9090 ./gradlew :server:run
# Local dev shortcut (relaxes secret fail-fast + surfaces dev OTP codes):
AEGIS_DEV_PROFILE=true PORT=9090 ./gradlew :server:run
```
Build a fat jar with the Shadow plugin: `./gradlew :server:shadowJar`.
