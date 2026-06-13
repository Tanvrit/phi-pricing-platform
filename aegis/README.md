# :aegis — the thin role shell

`:aegis` is the **app-layer Compose Multiplatform shell** for the rate / PHI
platform. After the re-architecture it owns **no surfaces, no widgets, no
buy-online screens, no i18n catalog** — all of that was relocated into the
pure-KMP `sdk-ui-*` feature modules. Aegis is now just:

1. the **platform entry points** (`jvmMain/Main.kt` desktop window +
   `wasmJsMain/Main.kt` Compose viewport),
2. a **role switch** (`AegisRoot`), and
3. the one place that builds the **shared `TanvritClient`** from device-local
   settings.

## Role dispatch

`AegisRoot(role)` is a thin `when`:

| Role       | Renders                                  | From module          |
|------------|------------------------------------------|----------------------|
| `CUSTOMER` | `BuyOnlineApp(client, locale, …)`        | `sdk-ui-buyonline`   |
| `BUSINESS` | `OperatorConsole(client, BUSINESS, …)`   | `sdk-ui-operator`    |
| `ADMIN`    | `OperatorConsole(client, ADMIN, …)`      | `sdk-ui-operator`    |

Everything is wrapped in the kit `AegisTheme` so `LocalAegisLocale` and the
design-system color tokens resolve below. `AegisRole.toOperatorRole()` maps the
shell's role token onto the operator module's `OperatorRole`.

## Role resolution

- **JVM (desktop):** `-Daegis.role=CUSTOMER|BUSINESS|ADMIN` → saved
  `AegisSettings.defaultRole` → `BUSINESS`. A corrupt value degrades to BUSINESS.
- **WASM (web):** `?role=customer|business|admin` → `CUSTOMER` (the bare public
  URL is **always** the buy-online journey; we deliberately never seed the
  implicit role from the persisted `defaultRole`, which would leak the operator
  console to `phi-buyonline.pages.dev`).

The `?session=` / `?quote=` launch context is owned by the buy-online module's
own platform seam (it reads `window.location` directly), so this shell only
resolves the role.

## Layout

```
com/rate/aegis/
├── AegisRole.kt          role enum + AegisRole.parse() + toOperatorRole()
├── AegisRoot.kt          the role switch (builds client, owns locale, AegisTheme)
├── di/
│   ├── AppClientFactory.kt  builds the ONE TanvritClient (baseUrl + actor header + auth)
│   ├── AegisModule.kt       Koin wiring (aegisModule / aegisModules / buildClient)
│   └── PlatformClient.kt    expect platformName / platformLogLevel (+ jvm/wasmJs actuals)
└── settings/
    └── AegisSettings.kt     expect AegisSettingsStore (+ jvm file / wasmJs localStorage actuals)
```

## Transport

The whole shell shares **one** `com.rate.core.network.client.TanvritClient`,
built by `AppClientFactory.create(settings, authProvider)`:

- `baseUrl` from `AegisSettings.serverBaseUrl` (default `http://localhost:9090`);
- `authProvider` is the credential seam — Phase-1 is `NoAuthProvider`;
- the operator/admin identity (`AegisSettings.operatorIdentity`) is forwarded as
  the `X-Aegis-Actor` header (`com.rate.core.network.auth.ACTOR_HEADER`) via the
  client's dynamic-headers hook, so the server can attribute audit rows;
- request logging is on (HEADERS) for the JVM desktop tool, **off** for the
  public WASM build (`PlatformClient` seam) so we don't spray PII into the
  browser console.

The Ktor *engine* itself is a `core-network` concern (CIO on JVM, Js on WASM);
this module declares `ktor-client-cio` / `ktor-client-js` per target so that
engine actual links.

## Dependencies

`commonMain`: `sdk-ui-kit`, `sdk-ui-buyonline`, `sdk-ui-operator`,
`core-network`, `core-auth`, `core` + Compose (runtime/foundation/material3) +
serialization-json + coroutines-core + koin-core.

NO MongoDB driver, NO Ktor-server, NO Apache POI here — every rupee and config
row comes back over the wire from the server via the SDK network clients.

## Build / run

- Desktop: `./gradlew :aegis:run` (`mainClass = com.rate.aegis.MainKt`,
  default BUSINESS).
- Web dev: `./gradlew :aegis:wasmJsBrowserDevelopmentRun` — served on **:9092**
  (`webpack.config.d/devServerPort.js`; :8080 is taken on the dev machine). The
  server's `CORS_ALLOWED_ORIGINS` must include `http://localhost:9092`.
- Web prod bundle: `injectAegisFavicon` post-processes the dist to wire the
  shield favicon (`src/wasmJsMain/resources/{index.html, aegis-icon.svg}`).
