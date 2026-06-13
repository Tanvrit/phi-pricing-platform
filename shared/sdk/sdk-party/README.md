# sdk-party

Parties for **RETAIL + GROUP** — the proposer / employer / member identity layer of the
re-architected PHI platform. Pure-KMP (`commonMain` only; JVM + wasmJs + iOS). Depends on
exactly `:shared:core:core`. **No Mongo, no Ktor-server, no Compose** — repository PORTs
only; the Mongo-backed actuals live in `:server-persistence`.

```
com.rate.sdk.party
├── model/
│   ├── PolicyHolder.kt        sealed PolicyHolder : ConfigEntity
│   │                          ├ RetailProposer(name, mobile, email, pan, dob, gender,
│   │                          │   address, bankAccount, kyc, memberRefs)
│   │                          └ GroupEmployer(companyName, gstin, pan, industryCode,
│   │                              mobile, contactPerson, address, estimatedLives)
│   │                          + Address / BankAccount / KycInfo / Sex value types
│   ├── Member.kt              PartyMember : BaseDataClass (persisted retail floater member)
│   │                          + Relationship enum; toRatingMember() → core Member
│   └── group/
│       ├── Census.kt              Census : BaseDataClass (employerPartyRef, lives, members)
│       │                          + CensusMember(empId, age, gender, grade, relation, SI)
│       └── CensusAggregation.kt   grade × age-band roll-up for group rating
│                                  (AgeBandBucket / GradeAggregation / CensusAggregation)
├── repository/
│   ├── PartyRepository.kt     PORT: ConfigRepository<PolicyHolder> + getByMobile/getByPan
│   │                          (+ PartyMemberRepository for floater members)
│   └── CensusRepository.kt    PORT: paged-by-employer + latest + aggregate + bulkUpsert
├── handler/
│   ├── PartyHandler.kt        validate (mobile/PAN/Aadhaar/IFSC/pincode/GSTIN/email),
│   │                          dedup-by-mobile/PAN, create/update, emit events
│   └── group/CensusHandler.kt census ingest + append + grade/age-band roll-up
├── event/PartyEvents.kt       PartyEvent (PartyCreated/Updated, CensusIngested) + PartyEventSink PORT
└── di/PartyModule.kt          Koin `partyModule` (binds the two handlers)
```

## Key decisions

- **Sealed `PolicyHolder : ConfigEntity`** — RetailProposer & GroupEmployer are operator-
  CRUD config entities sharing the standard envelope, polymorphic via `AppJson`'s
  `_class` discriminator (`@SerialName` on the subclasses).
- **`PartyMember` / `Census` are `BaseDataClass`** (transactional), not `ConfigEntity` —
  they are party data, not admin-managed catalog config. `PartyMember.toRatingMember()`
  projects to the engine's `com.rate.core.rating.ports.model.Member` for QuoteRequest
  assembly (the durable record stays here; the Double/engine-parity shape stays in core).
- **`CensusAggregation.from(...)`** is the pure (grade × age-band) roll-up the group
  rating path consumes over `GroupRateDataProvider`. Banding reuses
  `core-regulatory.getAgeBand`; grade SI is the modal SI in the grade.
- **Validation relocation.** The foundational, dependency-free field validators
  (`mobile/PAN/Aadhaar+Verhoeff/IFSC/pincode/account/email/GSTIN`) were moved from the old
  `com.rate.domain.validation.Validators` into **core-base** at
  `com.rate.core.base.util.Validators` (reused by the server too). The old plan-aware
  `quoteRequest(plan, req)` check was intentionally **not** moved into core-base — it
  depends on the rating model, so it belongs in the rating/quoting layer and keeping it
  out preserves core-base's zero-project-dependency invariant. `PartyHandler` consumes the
  relocated `Validators`.
- **Events via PORT.** Handlers push to a `PartyEventSink` (no-op default); the app binds a
  real sink that fans into the sdk-audit log. Keeps the SDK transport-agnostic.

## Relocated from the monolith

- `shared/src/.../com/rate/domain/validation/Validators.kt`
  → `shared/core/core-base/.../com/rate/core/base/util/Validators.kt`
  (field validators + Verhoeff; `quoteRequest` dropped — see above).
- Party shapes (proposer / employer / member / census) are net-new structured models for
  the retail + group identity layer, consolidating the ad-hoc buy-online proposer/KYC/bank
  fields and the group census concept.
