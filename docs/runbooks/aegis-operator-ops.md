# Aegis — Operator Ops Runbook (Phase 1)

Operational procedures for day-to-day administration of the Aegis admin console.
This is **not** an incident runbook — for incidents, see `audit-chain-integrity.md`
(RB-01) and friends. This document covers the routine operator-management,
audit-verification, and outbox-housekeeping tasks Aegis admins perform weekly.

Scope: Phase 1. Phase 2 (API token / JWT auth, federated SSO) is out of scope.

---

## 1. Bootstrap mode

### What it is
When the `operator` table is empty, Aegis runs in **bootstrap mode**: every
request is allowed, every scope check passes, every surface is visible. This is
deliberate so a fresh install can be onboarded without an existing operator.

### Why it's deliberate
A locked-out admin console is worse than a permissive one on day one. The
bootstrap window is meant to be **seconds**, not days — close it the moment
you've added the first admin.

### How to know you're in bootstrap mode
```bash
curl -s http://localhost:9090/api/operators | jq
# []   <-- empty array == bootstrap mode
```

The Aegis "Manage operators" card also displays a yellow `BOOTSTRAP MODE`
banner when the store is empty.

### When the window closes
The instant the first row is inserted into `operator`, scope checks become
authoritative. From that moment on, every request needs an identity that
matches a row with the right scopes (or the `admin` role, which bypasses
scope checks).

---

## 2. Granting yourself admin access (first-time setup)

While in bootstrap mode:

1. **Set your identity.** Settings → Operator identity → enter your work email
   (e.g. `alice@pruhealth.example.in`). Save.
2. **Set default role to admin.** Settings → Default role → `ADMIN`. This is
   what makes the "Manage operators" card render on next page open.
3. **Restart Aegis.** Settings apply on next page open, not in-flight.
4. **Add yourself as an operator.** Manage operators → Add operator:
   - Identity: `alice@pruhealth.example.in` (must exactly match step 1)
   - Role: `admin`
   - Scopes: leave empty or tick all — the `admin` role bypasses scope checks
5. **Verify.**
   ```bash
   curl -s http://localhost:9090/api/operators | jq
   # [ { "identity": "alice@pruhealth.example.in", "role": "admin", ... } ]
   ```

Bootstrap mode is now closed. Subsequent operators must be added by an existing
admin.

---

## 3. Adding a new operator

### The 5 scopes

| Scope | Grants |
|---|---|
| `plans.write` | Create / edit plan definitions and rate tables |
| `plans.delete` | Soft-delete or archive plans |
| `import.upload` | Upload Excel rate tables via the import surface |
| `audit.verify` | Run manual audit-chain re-verify; view chain status |
| `operators.write` | Add / remove operators; modify scopes |

### Recommended scope sets per role

| Role | Scopes |
|---|---|
| **Analyst** (read-only) | none |
| **Underwriter** | `plans.write`, `audit.verify` |
| **Import operator** | `import.upload`, `audit.verify` |
| **Admin** | `operators.write` (and assign role = `admin` so scope checks bypass) |

### Procedure

1. Settings → Manage operators → Add operator
2. Fill in identity (exact email or subject string), role, scopes
3. Save
4. Confirm via API:
   ```bash
   curl -s http://localhost:9090/api/operators | jq '.[] | select(.identity=="bob@pruhealth.example.in")'
   ```

The audit chain records an `operator.added` event automatically.

---

## 4. Revoking access

Two equivalent paths:

### Via Aegis UI
Settings → Manage operators → find the row → click **Remove**. Confirm.

### Via API
```bash
curl -X DELETE http://localhost:9090/api/operators/bob@pruhealth.example.in
```

Both emit an `operator.removed` audit event. Removal is immediate — no token
revocation step is needed in Phase 1 (no tokens exist yet).

### Edge case: removing the last admin
The server refuses to remove the last remaining admin row and returns
`409 Conflict`. Add a replacement admin first.

---

## 5. Audit chain integrity

### What the chain is
Every state-changing action in Aegis writes a row to `audit_event`. Each row
stores `prev_hash` and `this_hash`, forming a hash-linked chain. Tampering with
any row breaks the chain at that point.

### Manual verification
Aegis → Audit surface → **Chain integrity** card → **Re-verify** button.
Equivalent:
```bash
curl -s http://localhost:9090/api/audit/verify | jq
# { "ok": true, "rowsChecked": 1284 }
```

### Automatic verification
The server runs a chain walker every **6 hours** (override with
`AUDIT_VERIFY_INTERVAL_HOURS`). Each run writes one of:
- `audit.chain_verified` — chain is intact
- `audit.chain_broken` — chain failed verification

These events are visible in `GET /api/audit/events` and on the live SSE stream.

### What to do if the chain breaks
**Treat as an incident.** Stop. Follow `audit-chain-integrity.md` (RB-01) —
do not attempt to "repair" the chain from this runbook. The chain is forensic
evidence at that point.

---

## 6. Email outbox

Aegis writes outbound notifications (KYC confirmations, ops alerts, weekly
audit summaries) to a local outbox as `.eml` files. Phase 1 does not ship an
SMTP integration; a separate relay picks these up.

### Where files land
```
~/.aegis/outbox/
```
Each file is named `<yyyymmddThhmmssZ>-<short-id>.eml`.

### Viewing the outbox

**Via Aegis:** Audit surface → "Email outbox" card. Shows last 50 files,
size, age.

**Via shell:**
```bash
ls -lh ~/.aegis/outbox/
```

### Purging old files

**Via Aegis:** "Purge >7 days" button on the Email outbox card.

**Via shell:**
```bash
find ~/.aegis/outbox -name '*.eml' -mtime +7 -delete
```

Run weekly. The outbox is not size-bounded; an unattended Aegis instance will
fill the disk eventually.

---

## 7. Idempotency cache

### What it is
Aegis caches the response of every request that carries an `Idempotency-Key`
header for **24 hours**, keyed by `(operator_identity, idempotency_key)`. A
repeated request with the same key returns the cached response without
re-executing the action.

### Where to view
Audit surface → **Idempotency cache** card. Lists active keys, age, and the
endpoint each was issued against.

### When replays are expected
- Buy-online checkout retried by an impatient customer hitting "Pay" twice
- Aegis publish action where the operator double-clicked
- Mobile clients with flaky networks retrying a POST

In all of these, the cache silently returns the original response — this is
working as intended.

### When replays are concerning
- The same key against **different request bodies** — logged as
  `idempotency.body_mismatch`. Investigate; usually a client bug.
- Cache hit rate suddenly above ~30% of total writes — possibly a stuck
  retry loop on a client.

---

## 8. Common questions / FAQ

### "I can't see the Audit verify button."
Your operator row is missing the `audit.verify` scope, or `operators.write` was
removed and you can no longer modify your own scopes. Have another admin
re-grant them.

### "Customers are complaining about double-charged premiums."
The idempotency cache should prevent this. Steps:
1. Get the customer's request id from support.
2. Check the Idempotency cache card for that key.
3. If two distinct keys hit the same charge, the **client** sent different
   idempotency keys for the same intent — that's a client bug, not Aegis.
4. If only one key exists and one charge appears, the second "charge" is
   probably a delayed bank-side capture, not a double-charge.

### "The audit chain broke right after a DB restore."
Expected. The V1 baseline does not seed any audit rows, so a fresh DB has
zero entries. Run `GET /api/audit/verify`:
- If `rowsChecked == 0`, the chain restarts fresh on the next event. No
  action needed beyond noting this in your restore log.
- If `rowsChecked > 0` and `ok == false`, treat as a real chain break —
  go to RB-01.

### "I added an operator but they still get 403."
Check that the identity string **exactly** matches what the auth layer is
sending. Whitespace, casing, and trailing punctuation all matter in Phase 1.
Confirm with:
```bash
curl -s http://localhost:9090/api/operators | jq '.[].identity'
```

### "How do I temporarily disable an operator without deleting them?"
Phase 1 has no disabled flag. Either remove the operator and re-add later, or
strip all scopes and change role to `analyst` — they keep visibility but lose
write access.

---

## Related documents

- `audit-chain-integrity.md` (RB-01) — chain break incident response
- `audit-service-degraded.md` (RB-07) — audit writer unavailable
- `pii-in-logs.md` (RB-10) — PII leakage incident response

## Last review
2026-05-23
