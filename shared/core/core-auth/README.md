# core-auth

Pure-KMP **auth contract**: wire models + PORT interfaces for OTP, JWT-style tokens,
sessions, KYC and RBAC. **No crypto secret, no JWT/HMAC key, no JWT library, no Mongo
driver** — every actual (signing, hashing, persistence) is implemented in the **server
app** (`server-persistence`, `server-security`). This module declares only *shapes* and
*ports*.

```
com.rate.core.auth
├── otp/
│   ├── OtpPurpose.kt   enum LOGIN(4) / KYC(6)  — code length is the contract
│   ├── OtpModels.kt    OtpRequest / OtpVerifyRequest / OtpResult (+ OtpStatus)
│   ├── OtpRecord.kt    at-rest model (codeHash, expiresAt, attempts) : BaseDataClass
│   └── OtpStore.kt     PORT — put/get/update/delete + rate-limit send counters
├── token/
│   ├── JwtClaims.kt    @Serializable sub, role, scopes, deviceId, exp, keyId
│   ├── TokenSigner.kt  PORT — sign(claims):String / verify(token):JwtClaims?
│   └── TokenModels.kt  SessionToken / RefreshToken / TokenPair
├── session/
│   ├── SessionRecord.kt  : BaseDataClass (refreshTokenHash, expiresAt, revoked)
│   └── SessionStore.kt   PORT — TTL'd put/get/findByRefreshHash/revoke/purgeExpired
├── kyc/
│   ├── KycMethod.kt    enum C_KYC / E_KYC / MANUAL (+ KycStatus)
│   └── KycModels.kt    KycRequest / KycOtpVerifyRequest / KycResult
├── rbac/
│   ├── AuthRole.kt     enum CUSTOMER / BUSINESS / ADMIN (+ defaultScopes)
│   ├── Scope.kt        value class `config.<entity>.<verb>`, wildcards, role bundles
│   └── Authorization.kt  PURE requireScope / hasScope checks (no transport)
└── di/AuthModule.kt    Koin module (empty — actuals bound by the app layer)
```

## Dependencies
`project(":shared:core:core-base")`, `project(":shared:core:core-network")`,
kotlinx-serialization, kotlinx-datetime, kotlinx-coroutines-core, koin-core.

## Design notes
- **`@SerialName` on every field** = the Mongo/JSON field name. `OtpRecord` and
  `SessionRecord` are transactional, so they implement `BaseDataClass` (not
  `ConfigEntity` — they are not admin-managed config).
- **Ports, not actuals.** `OtpStore`, `SessionStore` and `TokenSigner` are `suspend`/
  pure interfaces. The HMAC/JWT secret and the Mongo collections live ONLY in the app.
- **RBAC is data + a pure decision.** `Scope` is a typed `config.<entity>.<verb>`
  string with wildcard matching (`config.*.read`, global `*` for admin). Role → scope
  bundles live in `Scope.bundleFor`. `Authorization` makes the *decision*; the route /
  UI maps the resulting `DomainError.Forbidden` to its transport.

## Relocated from the monolith
- `server/security/OtpService.kt` → the OTP **policy** (TTL, max-attempts, rate limit,
  SHA-256 hashing, constant-time compare, code minting) moves to a server handler; the
  **storage** concern becomes `OtpStore`, the **at-rest shape** becomes `OtpRecord`,
  and the forgeable HMAC-nonce token becomes the `TokenSigner` port + `JwtClaims`.
- `server/auth/Rbac.kt` (`RoutingContext.requireScope`) → pure `Authorization`.
- `server/auth/Operators.kt` (free-string scopes like `"plans.write"`) → typed `Scope`
  value class with role bundles.
- buy-online `KycMethod` (CKYC/EKYC/MANUAL) → `kyc/KycMethod.kt` with canonical names.
- `BuyOnlineSessionRepository` save/load → generalised to the TTL'd `SessionStore`.
