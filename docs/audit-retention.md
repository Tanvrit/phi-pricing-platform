# Audit Retention & Compliance Summary

> Operational reference for the server-side audit log (`audit_event` table), its hash-chain integrity model, and the project's posture against IRDAI and GDPR/DPDPA requirements.

This is a **Phase 1** document. It describes what exists today and what is explicitly deferred to Phase 2. Anything labelled *recommended* or *Phase 2* is not yet implemented — do not treat it as a guarantee.

---

## 1. What gets audited

Every state-changing API call writes one row to `audit_event` via `auditService.record(...)`. Reads are not audited (the `GET /api/audit/*` family explicitly excludes itself to avoid feedback loops).

### Logged actions

| Action | Source surface | Notes |
|--------|---------------|-------|
| `plan.upserted` | Aegis / Configurator | Maker-checker; payload contains the plan diff |
| `plan.deleted` | Aegis | Soft delete; payload contains plan id + actor |
| `quote.created` | Rate Calculator, Buy Online | Persists the saved quote envelope |
| `quote.calculated` | Rate Calculator | Stateless calc, logged for analytics |
| `session.email_requested` | Buy Online | Email address present in payload (see §4) |
| `outbox.purged` | Outbox sweeper | Bulk row counts only, no PII |
| `rates.imported` | Excel import | File checksum + row counts |
| `import.uploaded` | Excel import | Pre-validation upload event |
| `audit.chain_verified` | Server-side verifier (6h) | Recorded into the chain itself |
| `audit.chain_broken` | Server-side verifier (6h) | See [RB-01](./runbooks/audit-chain-integrity.md) |
| `otp.send`, `otp.verify` | Buy Online OTP | Mobile in payload (see §4) |
| `kyc.otp.send`, `kyc.otp.verify` | KYC E-KYC | Last 4 of Aadhaar only; never full PAN/Aadhaar |

### Row schema

```sql
CREATE TABLE audit_event (
    id          BIGSERIAL PRIMARY KEY,
    event_at    TIMESTAMPTZ NOT NULL,
    actor_subject TEXT NOT NULL,     -- user id, system id, or "anonymous"
    actor_role  TEXT NOT NULL,       -- e.g. maker, checker, customer, system
    action      TEXT NOT NULL,
    resource_type TEXT NOT NULL,
    resource_id TEXT,
    payload     JSONB NOT NULL,      -- canonical, sorted keys
    request_id  TEXT NOT NULL,       -- correlates with the access log
    prev_hash   TEXT NOT NULL,
    this_hash   TEXT NOT NULL
);
```

Example row (truncated payload):

```json
{
  "id": 184221,
  "eventAt": "2026-05-22T09:14:03Z",
  "actor": { "subject": "checker.amita", "role": "checker" },
  "action": "plan.upserted",
  "resourceType": "plan",
  "resourceId": "phi_basic_premier",
  "payload": { "diff": { "tenureDiscount.5yr": [0.140, 0.150] } },
  "requestId": "req_8e3c1b...",
  "prevHash": "9f4c...e10b",
  "thisHash": "2a17...c44d"
}
```

---

## 2. Hash chain

Each row binds itself to its predecessor:

```
this_hash = SHA-256( prev_hash || canonical_json(row_without_this_hash) )
```

- The **genesis row** uses `prev_hash = "GENESIS"`.
- `canonical_json` sorts keys lexicographically and omits whitespace, so the same logical row always produces the same hash regardless of serialization order.
- Any insert, update, or delete to historical rows breaks the chain from that row onward.
- `GET /api/audit/verify` walks the table and returns the first divergent row (or `ok=true` if every link agrees).
- A **periodic verifier** runs every 6 hours (`AUDIT_VERIFY_INTERVAL_HOURS` to override) and writes its outcome back into the chain as `audit.chain_verified` or `audit.chain_broken`. Tamper surfaces in `GET /api/audit/events` even when no operator clicks "Re-verify".

If a break is detected, follow [RB-01 — Audit chain integrity broken](./runbooks/audit-chain-integrity.md).

---

## 3. Retention policy

### Current state (Phase 1)

- **Indefinite retention.** No TTL on `audit_event` rows. No archival job runs.
- The table is append-only at the application layer; only direct DB access can mutate it.

### Recommended Phase 2 policy

| Age | Action | Rationale |
|-----|--------|-----------|
| 0 – 1 year | Hot in Postgres | Routine read traffic from Aegis + dashboards |
| 1 – 7 years | Archive to cold storage (S3/GCS, append-only, object-lock) | IRDAI record-keeping guidance for insurance contracts |
| > 7 years | Purge, except chain anchors | Aligns with IRDAI guidance; preserves a sparse anchor row per quarter so the live chain remains verifiable |

### Operational sizing

At PHI buy-online traffic, the table is expected to grow at roughly **10M rows / year** (dominated by `quote.calculated` and `otp.*`). Plan for **range partitioning by year on `event_at`** once the table crosses ~50M rows; the chain check tolerates partitioned tables provided the verifier walks in `id` order.

---

## 4. PII in audit rows

Some payloads contain customer PII. By row type:

| Action | PII fields in payload |
|--------|----------------------|
| `session.email_requested` | email address |
| `otp.send`, `otp.verify` | mobile number (E.164) |
| `kyc.otp.send`, `kyc.otp.verify` | masked Aadhaar (last 4), DOB |
| `quote.created` | pincode, age, family composition; **no name, mobile, email** |
| `plan.upserted`, `plan.deleted` | actor id only (employee) |

### At-rest encryption

- **Phase 1**: not implemented at the column level. Rely on Postgres-level encryption — TDE on managed providers (RDS, Cloud SQL, Crunchy Bridge), or full-disk encryption on self-hosted nodes.
- **Phase 2 (recommended)**: wrap PII payload fields with `PGP_SYM_ENCRYPT` keyed from a KMS-issued data key. The hash-chain remains valid because `canonical_json` is computed over the encrypted value once it's persisted.

### In-transit

- HTTPS terminates at the edge. The internal hop from edge to server is plaintext today; consider mTLS in Phase 2.

### Right-to-erasure (GDPR Art. 17 / DPDPA §12)

- **Not yet implemented.** No customer-initiated erasure endpoint exists.
- **Recommended Phase 2 procedure**: pseudo-anonymise by replacing `actor.subject` and `resourceId` with `HMAC-SHA-256(secret, original_value)`. The chain stays valid because the canonical JSON is rewritten in place — the verifier only checks that each row's `this_hash` matches its own canonical form and the previous `prev_hash`.
- This procedure is destructive; document the erasure request in a separate, append-only `erasure_log` table outside the audit chain.

---

## 5. Access control

| Endpoint | Method | Today | Phase 2 |
|----------|--------|-------|---------|
| `/api/audit/events` | GET | Open (trust-the-header) | Gated by scope `audit.read` |
| `/api/audit/idempotency` | GET | Open | `audit.read` |
| `/api/audit/verify` | GET | Open | `audit.read` |
| `/api/audit/stream` | GET (SSE) | Open | `audit.read` |
| `auditService.record(...)` | internal | Server-only, no HTTP surface | unchanged |

There is **no HTTP route** that writes to `audit_event`. The only writer is the in-process `auditService`. Phase 2 will add an `audit.export` scope for the CSV export path.

---

## 6. Exports

- The Aegis Audit surface offers a CSV export that streams from memory; it is **not** itself logged as an `audit_event` row.
- For regulator-ready exports, prefer `GET /api/audit/events?limit=500` with pagination via the `after_id` cursor. The response is a JSON array of canonical rows, suitable for re-hashing on the receiver side.

```
GET /api/audit/events?limit=500&after_id=184220
```

---

## 7. Incident response

- For any `audit.chain_broken` event, escalate to **SEV-1** and follow [RB-01](./runbooks/audit-chain-integrity.md).
- Immediate actions (summarised; the runbook is authoritative):
  1. Stop all writers (Aegis maker-checker is already blocked by design when the chain is broken).
  2. Snapshot the database before any remediation.
  3. Contact the SRE on-call and the security lead.
- Do **not** attempt to "repair" the chain by rewriting hashes — that destroys the only evidence of the tamper.

---

## 8. Open items

- Phase 2 retention job (archive + purge).
- Column-level PII encryption.
- Right-to-erasure endpoint and `erasure_log`.
- Scope-based access on the `/api/audit/*` family.
- Partitioning `audit_event` by year once row count crosses 50M.

---

*Owner: Platform / Security. Last reviewed: 2026-05-23.*
