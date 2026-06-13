# sdk-proposal

The **buy-online journey** feature of the PHI platform: drive a customer from OTP login through
eligibility, premium, KYC and payment to a submitted, durable `Proposal`, plus the save+resume
session.

Layer: **sdk** (pure-KMP commonMain). Depends on:
`project(":shared:core:core")`, `project(":shared:sdk:sdk-catalog")`,
`project(":shared:sdk:sdk-party")`, `project(":shared:sdk:sdk-quoting")`.

No MongoDB driver, no Ktor-server, no Apache POI, no Compose, no secrets. The `OtpStore`,
`TokenSigner`, `ProposalRepository`, `SessionRepository` and (via sdk-quoting) `RatingPort`
*actuals* are bound by the **app** layer (server-persistence over the Mongo snapshot; the
JWT/HMAC signer with the secret).

## Package layout (`com.rate.sdk.proposal`)

```
model/
  Proposal.kt               Proposal (: BaseDataClass) — proposalNumber + status + members +
                            plan/quote/party refs + payment + kyc. ProposalStatus, ProposalMember.
  BuyOnlineSessionState.kt  RELOCATED save+resume journey snapshot (now a BaseDataClass).
  Kyc.kt                    KycState / KycDocRef over core-auth KycMethod / KycStatus.
  Payment.kt                PaymentInfo + BankDetails + PaymentStatus / PaymentMethod.
  journey/JourneyContracts.kt  Eligibility / Premium / Hospital request+result DTOs.
network/
  BuyOnlineApi.kt           Ktor-client for /api/buy-online/* (paths preserved verbatim).
  BuyOnlineDtos.kt          wire DTOs (monolith field names preserved).
repository/
  ProposalRepository.kt     PORT — getByProposalNumber + listByMobile + admin list/create/update.
  SessionRepository.kt      PORT — save/load/listSessions/purgeExpired (TTL save+resume).
handler/
  OtpHandler.kt             send/verify policy + algorithm over core-auth OtpStore + TokenSigner.
  KycHandler.kt             C-KYC / E-KYC (OTP step) / MANUAL over core-auth Kyc contracts.
  EligibilityHandler.kt     PED / critical-illness exclusion rule.
  PremiumHandler.kt         tier→Plan + add-on→cover resolution, delegates to QuoteHandler.
  ProposalHandler.kt        validate → mint number → persist → track / status changes.
crypto/
  Sha256.kt                 expect sha256Hex (JVM MessageDigest; iOS/WASM pure-Kotlin).
event/
  ProposalEvents.kt         ProposalEvent (Otp/Eligibility/Premium/Kyc/Proposal) + Sink PORT (NOOP).
di/
  ProposalModule.kt         Koin module() binding the handlers from injected PORTs.
```

## The journey, end-to-end

`BuyOnlineApi` preserves the monolith's `/api/buy-online/*` paths so the existing WASM customer
journey + operator console wire contract is unchanged:

```
otp/send → otp/verify → hospitals?pincode= → eligibility → premium →
kyc/otp/send → kyc/otp/verify (E-KYC) → proposal → proposal/{number} (track)
                                         session  (save) / session/{id} (resume)
```

## Relocation notes

- **OTP** — relocated from the server's monolithic `OtpService` (in-memory map + crypto + policy
  in one JVM class). Split cleanly: STORAGE → core `OtpStore` PORT, TOKEN MINTING → core
  `TokenSigner` PORT (secret in the app), HASHING → pure-KMP `sha256Hex` (expect/actual; JVM
  `MessageDigest`, iOS/WASM a pure-Kotlin FIPS-180-4 SHA-256), POLICY (5-min TTL, 5 attempts,
  per-mobile rate limit, constant-time compare, purpose-length codes) → `OtpHandler`. The
  forgeable `"mock-jwt-${mobile}"` token is gone — a signed `JwtClaims` bearer is minted instead.
- **Premium** — relocated from `BuyOnlineRoutes` `/premium`. The hardcoded `BuyOnlineTier.toPlanId()`
  map + `BUYONLINE_ADDONS` constant are replaced by reading the admin-CRUD sdk-catalog `AddOn`
  bundle (`code` == tier, `planRef` == Plan id, `items[].coverCode` == cover selections), and
  pricing is delegated to sdk-quoting's `QuoteHandler` (via the injected `RatingPort`), never a
  concrete engine.
- **Eligibility** — the inline exclusion `filter` in the `/eligibility` route, lifted to a pure
  testable handler (a member is excluded when PED OR critical illness is declared).
- **Proposal** — the monolith's `/proposal` route had **no durable entity** (it only minted a
  number and audit-logged it, so `GET /proposal/{number}` always returned a canned "Under Review").
  This module adds the missing `Proposal` entity + `ProposalRepository`, so the track endpoint and
  the operator funnel see the real stored status. The `PHI-<base36>-<base36>` number shape is kept
  but generated with `Now.epochMillis()` (no `System.currentTimeMillis`) for KMP parity.
- **Session** — `BuyOnlineSessionState` relocated verbatim (now a `BaseDataClass`); the server's
  `BuyOnlineSessionRepository` becomes the TTL'd `SessionRepository` PORT (resume links expire).

## The Money / Double boundary

Per the rating contract, the journey premiums (`PremiumResult`) stay **Double** end-to-end — the
buy-online UI shows them directly. Conversion to `Money` happens **only** at `Proposal` assembly
(`ProposalHandler.create` → `annualPremium.toMoney()` / `totalIncludingGst.toMoney()`).
