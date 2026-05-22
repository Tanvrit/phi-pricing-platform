# RB-07 — Audit log unwritable (audit service degraded)

## Severity
SEV-1 (regulatory / governance impact)

## Symptom
The `AuditEventService.record(...)` call is failing or timing out. Either the
`audit_event` table is unreachable, write contention is producing serialisation
failures, or the hash-chain computation is breaking. Aegis edit-mode actions either
hang or refuse to save with the banner *"Audit service degraded — your change is
queued, not yet committed."*

## Customer impact
**No direct customer impact** (customer-facing endpoints don't write audit_event in
hot paths). **Internal impact is severe** — every Aegis maker-checker action is
blocked until audit writes are healthy again. The business team cannot publish plans,
rates, or rule changes.

## Detection
- Prometheus alert: `rate_http_requests_total{path=~"/api/audit.*",status="5.."}`
  rate > 1/min sustained.
- Aegis Audit & Governance surface shows "audit-write queue depth > 0" persistent.
- Customer-support / business-team ticket: "I clicked Publish and nothing happened."

## First five minutes
1. Acknowledge.
2. Open `#incident-audit` Slack. Page Sec lead.
3. Confirm the chain itself is still consistent:
   ```bash
   curl -s "$BASE/api/audit/verify" | jq
   # Expect: { "ok": true, "rowsChecked": N }
   # If "ok": false → escalate to RB-01 instead.
   ```
4. Confirm audit_event WRITES are the failing path (not READS):
   ```bash
   curl -sf "$BASE/api/audit/verify" > /dev/null && echo "READ ok"
   # Try a synthetic write:
   curl -X POST "$BASE/api/audit/manual-record" \
     -H 'Content-Type: application/json' \
     -d '{"action":"diagnostic.probe","resourceType":"diagnostic","resourceId":"probe-1"}' \
     -w "\nHTTP %{http_code}\n"
   ```
5. Check Postgres for write contention:
   ```bash
   PSQL=/opt/homebrew/opt/postgresql@17/bin/psql
   $PSQL "$DB_URL" -c "
     SELECT pid, state, wait_event_type, wait_event, query_start, query
     FROM pg_stat_activity
     WHERE query LIKE '%audit_event%' AND state != 'idle'
     ORDER BY query_start;
   "
   ```

## Mitigation (fastest path to restore)

### Step A — Connection / pool exhaustion (most common)
If `rate_db_connections_active` is at max and audit writes are queued behind other
slow queries, **the audit service shares the same pool as everything else**. Mitigate:
```bash
# Recycle the pod to release stuck connections
kubectl delete pod -l app=rate-server,role=writer
# Or restart locally
pkill -f "rate.server" ; ./gradlew :server:run
```
If recurring, isolate audit_event onto its own connection pool (Phase 2 followup
ticket).

### Step B — Table lock / long-running transaction
A long-running cron or analytical query may hold an exclusive lock:
```sql
-- Identify the blocker
SELECT pid, age(query_start, now()) AS age, query
FROM pg_stat_activity
WHERE state = 'active' AND query NOT LIKE 'BEGIN%' AND query NOT LIKE 'COMMIT%'
ORDER BY query_start;
-- Kill the offender if appropriate
SELECT pg_cancel_backend(<pid>);
```

### Step C — Disk full
If the disk is full, audit writes fail silently and Aegis blocks. See RB-09.

### Step D — Hash-chain code regression
If a recent server deploy changed `canonicalJson` or `sha256` helpers, the chain
appends will fail because the prior row's hash can't be reproduced. **Roll back the
deploy**:
```bash
git tag --list 'v*' --sort=-creatordate | head -3
kubectl set image deployment/rate-server server=ghcr.io/pruhealth/server:<previous-tag>
```

### Step E — Surface the degraded state to Aegis users
Even with audit service down, Aegis must NOT lose the user's draft. Confirm the
Pass-9 §F "Severable disable" pattern is honouring this — the user should see the
"Audit service degraded — your change is queued, not yet committed" banner, NOT a
blank screen. If they see anything else, file P0 immediately.

## Diagnostic queries

```sql
-- Recent audit_event write attempts (if successful, the row landed)
SELECT id, event_at, action, resource_type, actor_subject,
       LEFT(this_hash, 12) AS hash
FROM audit_event
ORDER BY id DESC LIMIT 10;

-- Are we still appending, or stuck?
SELECT max(event_at) AS last_audit_at, NOW() - max(event_at) AS gap
FROM audit_event;
-- gap > 5 min during business hours = degraded
```

```promql
# Audit write success rate
sum(rate(rate_http_requests_total{path=~"/api/audit.*",status=~"2.."}[5m]))
/
sum(rate(rate_http_requests_total{path=~"/api/audit.*"}[5m]))
```

## Resolution

1. Identify root cause (pool, lock, disk, code regression).
2. If pool — isolate audit onto its own pool (Phase 2).
3. If lock — review the analytical query that held the lock; move it to a read
   replica.
4. If disk — see RB-09.
5. If code — revert + write a hash-chain integrity test (the kind that would have
   caught the regression in CI).
6. Replay queued audit writes from the application's request log (the request-id
   correlation lets us reconstruct what should have been recorded).

## Postmortem trigger
Mandatory. Even though customer impact is zero, internal impact + regulatory
obligation makes this a postmortem-worthy SEV-1.

## Related runbooks
- `audit-chain-integrity.md` (RB-01) — when reads find a broken chain
- `database-readiness.md` (RB-03)
- `db-disk-pressure.md` (RB-09)
- `latency-burn.md` (RB-08)

## Last drill
Sec team tabletop 2026-05-15 (combined with RB-01). Next: 2026-08-15.
