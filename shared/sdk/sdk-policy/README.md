# sdk-policy

The **in-force policy lifecycle** of the re-architected PHI platform: issuance → renewal →
endorsement → free-look → portability → claims. Pure-KMP (`commonMain` only; JVM + wasmJs +
iOS). Depends on `:shared:core:core`, `:shared:sdk:sdk-catalog` (Plan / UIN catalog) and
`:shared:sdk:sdk-party` (proposer + members). **No Mongo, no Ktor-server, no Compose** —
repository PORTs only; the Mongo-backed actuals live in `:server-persistence`.

```
com.rate.sdk.policy
├── model/
│   ├── Policy.kt          Policy : BaseDataClass  (transactional, NOT admin config)
│   │                      + PolicyStatus FSM (canTransitionTo / terminal) + PolicySeed
│   ├── Nominee.kt         embedded nominee + validateShares (must sum to 100)
│   ├── Renewal.kt         RenewalQuote  (illustrationLines = core RenewalIllustrationLine)
│   ├── Endorsement.kt     sealed Endorsement (AddMember / IncreaseSumInsured / AddressChange /
│   │                      NomineeChange / RemoveMember) + EndorsementResult (priced)
│   ├── Portability.kt     PortabilityRequest + PortabilityResult (Accepted/Rejected)
│   └── Claim.kt           Claim : BaseDataClass + ClaimStatus FSM + ClaimType; ClaimRecord
│                          (port-in history input)
├── handler/
│   ├── RenewalEngine.kt   pure renewal calc over RenewalRateProvider + PlanRepository PORTs
│   │                      (the dead `pricingEngine` ctor param was removed) → RenewalQuote
│   ├── Ncb.kt             5%/yr cap 50% NCB (constants sourced from core Irdai)
│   ├── FreeLookEngine.kt  IRDAI 15-day free-look pro-rata refund (Money)
│   ├── PortabilityEngine.kt  Section-21A accept/reject + waiting-period carry-forward
│   └── EndorsementEngine.kt  prices each Endorsement pro-rata over the unexpired tenure
├── repository/
│   ├── PolicyRepository.kt   PORT: focused CRUD + findRenewalsDue(from,to,statuses) query
│   └── ClaimRepository.kt    PORT: per-policy CRUD + countAgainstNcb + status transition
├── event/PolicyEvents.kt     PolicyEvent (Issued/StatusChanged/RenewalQuoted/Renewed/
│                             EndorsementApplied/FreeLookReturned/PortabilityAccepted/
│                             ClaimIntimated) + PolicyEventSink PORT (NOOP default)
└── di/PolicyModule.kt        Koin `policyModule` (binds RenewalEngine + EndorsementEngine)
```

## Key decisions

- **`Policy` / `Claim` are `BaseDataClass`, not `ConfigEntity`.** Operators never hand-author a
  policy or a claim from the config console; both are produced by transactional flows
  (proposal → issuance; intimation). So `PolicyRepository` / `ClaimRepository` are focused CRUD
  contracts (the sdk-party member/census shape) rather than the generic `ConfigRepository`. The
  renewal-due query (`findRenewalsDue`) and the NCB-reset query (`countAgainstNcb`) live on the
  ports because they need server-side range/aggregation, not a client round-trip.
- **`RenewalEngine` consumes PORTs, not the concrete engine.** As mandated, the dead
  `pricingEngine: PricingEngine` constructor param was dropped and base-rate lookups go through
  the core **`RenewalRateProvider`** PORT (`baseFor(planId, age, SI, familyType, zone)`); plan
  entry/exit ages come from the core **`PlanRepository`** PORT. This inverts the cross-layer edge
  so sdk-policy never depends on sdk-rating.
- **`RenewalIllustrationLine` is imported from `core`** (not redefined here) so sdk-quoting's
  IRDAI sales-illustration document builders can consume it without a same-layer sdk→sdk edge.
  The old richer line (ageBand / basePremium / total) collapsed onto core's
  `(year, age, projectedPremium, ncbPercent, ncbAmount)`; the age-band step-up is still surfaced
  on `RenewalQuote.ageStepUpAmount`.
- **`EndorsementEngine` is net-new pricing.** The monolith's `Endorsement` carried only the
  rationale strings (the rupee math was a server TODO). The engine now prices each variant
  pro-rata `fullTermDelta × daysRemaining/365` over the `RenewalRateProvider`: SI-increase / member-add →
  charge, member-remove → refund (negative `netAmount`), address change → zone-delta only,
  nominee change → zero impact.
- **Double vs Money boundary.** Renewal/illustration figures stay **Double (INR)** for engine
  parity (the `RenewalRateProvider` returns Double); free-look and endorsement *outputs* convert
  to **`Money`** because they are settlement amounts shown to the customer.
- **Events via PORT.** Handlers push to a `PolicyEventSink` (NOOP default); the app binds a real
  sink that fans into sdk-audit. Keeps the SDK transport-agnostic. Both polymorphic sealed
  hierarchies (`Endorsement`, `PolicyEvent`) use `AppJson`'s `_class` discriminator.

## Relocated from the monolith

All from `shared/src/commonMain/kotlin/com/rate/domain/lifecycle/*`, repackaged to
`com.rate.sdk.policy`:

| Old | New |
|-----|-----|
| `Policy.kt` (Policy/PolicyStatus/PolicySeed/ClaimRecord) | `model/Policy.kt` (+ FSM logic) & `model/Claim.kt` (ClaimRecord) |
| `Nominee.kt` | `model/Nominee.kt` (+ `@SerialName`) |
| `Renewal.kt` (RenewalQuote + RenewalIllustrationLine) | `model/Renewal.kt` (line now from core) |
| `Endorsement.kt` | `model/Endorsement.kt` (+ `EndorsementResult`) |
| `Portability.kt` | `model/Portability.kt` (data) + `handler/PortabilityEngine.kt` (logic) |
| `RenewalEngine.kt` | `handler/RenewalEngine.kt` (PORT-driven; `pricingEngine` removed) |
| `Ncb.kt` | `handler/Ncb.kt` (constants from core `Irdai`) |
| `FreeLook.kt` | `handler/FreeLookEngine.kt` |

Net-new: `Claim` (promoted from the `ClaimRecord` stub to a full `BaseDataClass` + `ClaimStatus`
FSM), `EndorsementEngine` + `EndorsementResult`, `PolicyRepository` / `ClaimRepository` PORTs,
`PolicyEvents`, `PolicyModule`.
