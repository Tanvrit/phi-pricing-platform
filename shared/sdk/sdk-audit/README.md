# sdk-audit

Append-only, **hash-chained** audit log + **idempotency** for the `rate` PHI platform.
Pure-KMP (`commonMain` has only kotlinx + koin) so the **client and server compute
byte-identical hashes** — a wasmJs/iOS client can independently re-derive and verify any
hash the JVM server emitted.

Package: `com.rate.sdk.audit`. Depends only on `:shared:core:core`.

## Why pure-KMP hashing matters

The chain's integrity guarantee is only as good as the determinism of its pre-image.
`Json.encodeToString` does **not** guarantee stable key order or number formatting across
KMP targets, so we never use it for hashing. Instead:

- `handler/AuditCanonicalizer` produces a precisely-specified **canonical JSON**:
  sorted object keys (recursive), no whitespace, integers without `.0`, **never**
  scientific notation, fractions with trailing zeros trimmed, fixed string escaping.
- `crypto/Sha256` is an `expect fun sha256Hex(input)` with actuals: JVM →
  `java.security.MessageDigest`; wasmJs & iOS → the shared pure-Kotlin `Sha256Pure`
  (FIPS 180-4). All three are verified against RFC test vectors **and against each other**.

## Layout

```
com/rate/sdk/audit/
├── model/
│   ├── AuditEvent.kt        : BaseDataClass — seq, action, entity, entityId, actor,
│   │                          payloadJson, prevHash, hash, at  (transactional, immutable)
│   ├── AuditActor.kt        subject / role / requestId (participates in the hash)
│   ├── VerifyResult.kt      ok / eventsChecked / breakAtSeq / reason
│   └── IdempotencyRecord.kt : BaseDataClass — key IS _id; scope, requestHash,
│                              responseJson, statusCode, expiresAt (TTL)
├── crypto/
│   ├── Sha256.kt            expect fun sha256Hex(input): String
│   ├── Sha256Pure.kt        pure-Kotlin SHA-256 (used by wasmJs + iOS actuals)
│   └── (jvm/wasmJs/ios actuals)
├── handler/
│   ├── AuditCanonicalizer.kt  deterministic canonical JSON + canonicalPreimage(event)
│   ├── AuditChain.kt          GENESIS, buildHash(prev, canonical), nextEvent(...),
│   │                          verifyChain(events): VerifyResult   (PURE, no IO)
│   └── AuditRecorder.kt       append orchestration (seq → prevHash → build → store → broadcast)
├── repository/
│   ├── AuditStore.kt          PORT: append / latestHash / getBySeq / listBySeq / range
│   ├── IdempotencyStore.kt    PORT: putIfAbsent / get / complete / purgeExpired (TTL)
│   └── SequenceCounter.kt     PORT: nextSeq (atomic, gap-free; genesis = 1)
├── event/
│   └── AuditBroadcast.kt      SharedFlow live-feed contract + InProcessAuditBroadcast
└── di/
    └── AuditModule.kt         auditModule() — binds AuditBroadcast + AuditRecorder
```

## Chain semantics

- `seq` is strictly increasing & gap-free, allocated atomically by `SequenceCounter`
  (genesis = 1). The single allocation point makes the chain linear.
- `prevHash` = the previous event's `hash`, or `AuditChain.GENESIS` for `seq == 1`.
- `hash = sha256(prevHash + canonicalPreimage(event))`.
- The pre-image binds the **semantic** fields (`action, actor, at, entity, entityId,
  payload, seq`) in sorted key order — **not** the `_id` or the `BaseDataClass` envelope
  — so a client can recompute it without knowing server-assigned ids/timestamps.
- `payloadJson` is stored **already-canonical**, so the persisted bytes ARE the hashed
  bytes (no re-serialization ambiguity).

## What lives here vs. server-persistence

PURE here: all the math (canonicalizer, chain, SHA-256) and the PORT interfaces.
The **Mongo-backed** `AuditStore` / `IdempotencyStore` / `SequenceCounter` actuals are
implemented in `:server-persistence` (the only layer allowed to touch the driver). The
default `auditModule()` binds `AuditBroadcast` + `AuditRecorder`; importing it without
also binding the persistence ports fails fast at resolution — the intended signal.

## Relocated from

`server/src/main/kotlin/com/rate/server/audit/AuditEventService.kt` — the JDBC/Exposed
transaction and JDK `MessageDigest` were replaced by the `AuditStore`/`SequenceCounter`
ports and the portable `sha256Hex` expect/actual; the bespoke canonical serialization
moved into `AuditCanonicalizer`; the `MutableSharedFlow` live feed into `AuditBroadcast`.
