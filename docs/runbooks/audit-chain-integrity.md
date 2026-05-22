# RB-01 — Audit chain integrity broken

## Severity
SEV-1

## Symptom
The hourly audit-chain verifier job reports `ok=false` for the `audit_event` table.
Aegis dashboard shows a red banner on every surface: "Audit integrity check failed at row #N".
On-call gets paged via `audit_chain_break` alert.

## Customer impact
**No direct customer impact.** Maker-checker workflows in Aegis are blocked (by design)
until the chain is verified. Customer-facing reads (quote calculation, buy-online flow)
continue to function. **Internal impact is severe**: until the chain is fixed, no plan,
rate, discount, or rule edit can be approved or published.

## Detection
- Cron job runs every hour: `GET /api/audit/verify`.
- Prometheus alert fires when the response payload's `ok` field is `false`.
- Sec team also receives an email from the daily integrity report.

## First five minutes
1. Acknowledge the page.
2. Open `#incident-audit-chain` in Slack. Page sec lead + CTO.
3. Confirm the break:
   ```bash
   curl -s https://api.pruhealth.in/api/audit/verify | jq
   # Expect: { "ok": false, "rowsChecked": N, "breakAtId": ID, "reason": "..." }
   ```
4. **Do not delete or modify any audit_event rows.** Treat them as evidence.
5. Read the `reason` field carefully. Common causes:
   - `prev_hash mismatch at row N` — a row was modified after insertion (deliberate or via DB tool)
   - `this_hash mismatch at row N` — the row's stored hash doesn't match its content (bit-flip / encoding bug)
   - `missing row N` — a row was deleted

## Mitigation (fastest path to restore)

Until sec confirms tamper vs corruption, the chain stays broken — DO NOT auto-repair.

1. Block Aegis publish actions globally:
   ```bash
   # Toggle feature flag (LaunchDarkly or local config):
   AEGIS_PUBLISH_DISABLED=true   # all maker-checker publishes return 503
   ```
2. Notify the business: "Aegis edits remain available; publishing is paused while we investigate an audit-log integrity check."
3. Take a snapshot of `audit_event` for forensics:
   ```bash
   pg_dump --table=audit_event "$DB_URL" > audit_event_$(date -u +%Y%m%dT%H%M%SZ).sql.gz
   ```
4. Continue to Diagnostic.

## Diagnostic queries

```sql
-- The break point and its neighbours
SELECT id, event_at, actor_subject, action, resource_type, resource_id,
       LEFT(prev_hash, 12) AS prev_hash_short,
       LEFT(this_hash, 12) AS this_hash_short
FROM audit_event
WHERE id BETWEEN <breakAtId - 3> AND <breakAtId + 3>
ORDER BY id;

-- Recent admin / DBA activity around the break time
SELECT * FROM pg_stat_activity
WHERE state_change > NOW() - INTERVAL '24 hours'
  AND query ILIKE '%audit_event%'
ORDER BY state_change DESC;

-- Database-level audit (if pgAudit is enabled)
SELECT * FROM pg_audit_log
WHERE timestamp > NOW() - INTERVAL '24 hours'
  AND object_name = 'audit_event';
```

## Resolution

### Case A — Deliberate tamper detected
- Lock the offending DB user account immediately.
- Restore `audit_event` from the most recent verified-clean backup.
- Replay events from the application's request log into the restored chain (the
  request-id correlation lets you reconstruct which events to re-record).
- File a sec incident with full forensic trail.

### Case B — Corruption (bit-flip / encoding bug)
- Confirm with `verify_chain` that a single row is the break point and rows before
  are clean.
- Identify the cause (DB hardware? a deploy that changed `canonicalJson`?).
- Roll back any related deploy. Restore the affected row(s) from backup.

### Case C — Application bug
- A change to `canonicalJson` or hash computation can break the chain silently.
- Roll back the deploy. Re-deploy the prior version. Re-run verify.

## Postmortem trigger
Mandatory for any SEV-1. Postmortem must include:
- Root cause (tamper, corruption, or bug)
- Time-to-detect
- Time-to-mitigate
- Action items to prevent recurrence (e.g., DB user permissions tightening, additional
  CI check on `canonicalJson` changes, redundant integrity log)

## Related runbooks
- `audit-service-degraded.md` — when the audit service is unreachable (rather than tampered)
- `db-disk-pressure.md` — if disk pressure has caused row corruption

## Last drill
2026-05-15 (sec team tabletop)
