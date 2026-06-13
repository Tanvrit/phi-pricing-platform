# core-network

Pure-KMP HTTP **client** base, shared by BOTH the backend (service-to-service
calls) and the frontend (Compose). It owns the *transport*; feature SDKs build
their `network/` clients (routes + DTOs) on top of it.

Layer: **core** (`core → sdk → sdk-ui → app`). Package `com.rate.core.network`.

> No Mongo, no Ktor-**server**, no Compose, no POI. The only platform seam is the
> Ktor *engine*, supplied via an `expect/actual`. Depends only on `core-base`.

## What's inside

| File | Role |
|------|------|
| `client/TanvritClient.kt` | Wraps a Ktor `HttpClient`: `ContentNegotiation(AppJson.json)`, `HttpTimeout`, optional `Logging`, per-request `Authorization: Bearer` from the `AuthProvider`, exponential-backoff retry, non-2xx → status-aware `NetworkError`. Typed helpers: `getJson` / `postJson` / `putJson` / `postEmpty` / `delete` / `getText` (HTML/Prometheus) / `postMultipart` (byte upload). Supports query params and per-call headers (e.g. `X-Aegis-Actor`). |
| `client/TanvritClientConfig.kt` | `baseUrl`, `Timeouts`, `RetryConfig`, cert pins, logging flags, default/user-agent headers; `resolve(path)` joins base + path. |
| `client/HttpClientFactory.kt` | `expect fun httpClientEngineFactory()` — the one platform seam. `buildHttpClient { }` helper. |
| `auth/AuthProvider.kt` | Port: `suspend fun token()` + `fun onUnauthorized()`. `NoAuthProvider`, `StaticTokenAuthProvider`. `ACTOR_HEADER` constant. |
| `retry/RetryPolicy.kt` | `computeDelayMillis(attempt)` (exponential backoff + additive jitter, capped), `shouldRetry`, and a `retry { }` driver. Retries 5xx/429/timeout/connectivity; never 4xx. |
| `security/CertPin.kt` | Platform-free pinning descriptor (`host`, `pinSha256`, `includeSubdomains`, `enforced`). Enforced by JVM/Darwin engines; advisory on the browser. |
| `error/NetworkError.kt` | Sealed transport-failure taxonomy + `NetworkException`. |
| `error/ErrorMapping.kt` | `fromResponse` / `fromStatus` / `fromThrowable`, plus `toDomainError` bridging to core-base `DomainError`. Best-effort mines `{scope, message, errors}` from the server error envelope. |
| `ApiResponse.kt` | `ApiResponse<T>` (Success/Failure) + `suspend fun safeCall(block)`. `map` / `onSuccess` / `onFailure` / `getOrElse` / `errorAsDomain`. |
| `di/NetworkModule.kt` | `networkModule(config): Module` — Koin wiring (config + AuthProvider + RetryPolicy + TanvritClient). |

## Platform actuals (`httpClientEngineFactory()`)

| Target | Engine | Artifact |
|--------|--------|----------|
| jvm | `CIO` | `ktor-client-cio` |
| wasmJs | `Js` (browser `fetch`) | `ktor-client-js` |
| iosArm64/X64/SimArm64 | `Darwin` (NSURLSession) | `ktor-client-darwin` |

## Usage

```kotlin
val client = TanvritClient(
    config = TanvritClientConfig(baseUrl = "http://localhost:9090"),
    auth = NoAuthProvider,
    dynamicHeaders = { mapOf(ACTOR_HEADER to settings.operatorIdentity) },
)

// value-style
val plans: ApiResponse<List<Plan>> = safeCall { client.getJson<List<Plan>>("/api/plans") }
plans.onSuccess { render(it) }.onFailure { showBanner(it.message) }

// throwing-style
val quote = client.postJson<QuoteRequest, QuoteResult>("/api/quotes/calculate", request)

// raw + upload
val html = client.getText("/api/plans/$id/prospectus.html")
client.postMultipart<ImportResult>("/api/import/upload", bytes, "rates.xlsx")
```

## Relocation note

This module is **new**, but it is the generalised, platform-clean replacement for
the bespoke `HttpClient` setups in the old monolith:
`aegis/.../business/calculator/api/ApiClient.kt` (+ its JVM `uploadExcel` ext) and
`aegis/.../customer/buyonline/api/BuyOnlineApiClient.kt`. Those manually installed
`ContentNegotiation` + `DefaultRequest` and hand-rolled non-2xx → thrown-error
handling per call (`error("HTTP $status …")`); all of that is now centralised here
with a typed `NetworkError` taxonomy, retry, auth, and the actor-header contract
(`X-Aegis-Actor`) preserved via `dynamicHeaders`. Feature SDK network clients in
`shared/sdk/*` will be rebuilt on top of `TanvritClient`.
```
