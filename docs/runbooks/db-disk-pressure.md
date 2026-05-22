# RB-09 — Disk near capacity on Postgres host

## Severity
SEV-2 (escalates to SEV-1 fast when writes start failing)

## Symptom
PostgreSQL host disk is > 80% full. Cloud-provider disk alarm fires. If writes start
failing, audit writes, quote saves, and proposal submits will start returning 5xx —
at which point this becomes SEV-1.

## Customer impact
- ≤ 80% disk: none directly. SREs intervene preemptively.
- > 95%: write errors begin → quote saves fail → SEV-1 customer-facing breakage.
- 100%: DB is read-only. Buy-online proposals fail. Aegis edits all fail.

## Detection
- Cloud-provider disk-usage alarm (RDS / Cloud SQL / etc.).
- Postgres `pg_database_size('rate_calculator')` trend.
- Prometheus alert: `node_filesystem_avail_bytes / node_filesystem_size_bytes < 0.20`.

## First five minutes
1. Acknowledge. Page DBA + SRE.
2. Confirm disk pressure:
   ```bash
   # Local:
   df -h | grep -E "postgres|var/lib"
   # Cloud-managed:
   aws rds describe-db-instances --db-instance-identifier prod-postgres \
     | jq '.DBInstances[0].AllocatedStorage,.DBInstances[0].FreeStorageSpaceMB'
   ```
3. Top tables by size:
   ```sql
   SELECT relname, pg_size_pretty(pg_total_relation_size(c.oid)) AS total_size
   FROM pg_class c
   LEFT JOIN pg_namespace n ON n.oid = c.relnamespace
   WHERE n.nspname NOT IN ('pg_catalog', 'information_schema')
     AND c.relkind = 'r'
   ORDER BY pg_total_relation_size(c.oid) DESC
   LIMIT 15;
   ```

## Mitigation (fastest path to restore)

### Step A — Buy time by extending the disk
On a managed DB, the fastest mitigation is just to grow the disk:
```bash
# AWS RDS — non-disruptive online resize (takes ~5 min)
aws rds modify-db-instance \
  --db-instance-identifier prod-postgres \
  --allocated-storage <new-size-GB> \
  --apply-immediately
```
Costs money; do it first, optimise after.

### Step B — Cleanup that's safe to do during an incident
```sql
-- 1. VACUUM FULL the biggest table to reclaim dead-tuple bloat. Locks the table —
--    do off-hours OR target an obviously bloated table (check pg_stat_user_tables).
VACUUM (VERBOSE, ANALYZE) audit_event;

-- 2. Old idempotency_key rows past their 24h TTL (the background job may be
--    behind):
DELETE FROM idempotency_key WHERE created_at < NOW() - INTERVAL '24 hours';

-- 3. Old WAL files (managed DB usually handles this; on self-host:
SELECT pg_switch_wal();
-- Then re-checkpoint to free WAL segments
CHECKPOINT;
```

### Step C — Truncate non-essential tables (with sign-off)
- `idempotency_key` is safe to clear (callers will re-process).
- Old `flyway_schema_history` rows are not safe (Flyway needs them).
- Old `quotes` rows ARE customer/regulatory data — DO NOT truncate without
  Legal + Compliance approval.

### Step D — Promote replica if primary writes are failing
If we're at 100% and the primary is write-locked, promote a replica that has more
free disk. See **RB-03 Step B**.

### Step E — Status-page banner
If writes are failing customer-facing, add a banner:
```bash
BUYONLINE_BANNER="We're temporarily limiting new proposals. Existing policies and quotes continue to work."
```

## Diagnostic queries

```sql
-- Live database size
SELECT pg_size_pretty(pg_database_size('rate_calculator'));

-- Bloat per table (pgstattuple if available; otherwise approximate)
SELECT relname, pg_size_pretty(pg_total_relation_size(c.oid))
FROM pg_class c
WHERE c.relkind = 'r'
ORDER BY pg_total_relation_size(c.oid) DESC LIMIT 20;

-- WAL size on disk (self-host only)
SELECT pg_size_pretty(sum(size)) FROM pg_ls_waldir();
```

```bash
# Index sizes — large indexes hide most of the bloat
$PSQL "$DB_URL" -c "
  SELECT indexrelname AS idx, pg_size_pretty(pg_relation_size(indexrelid)) AS sz
  FROM pg_stat_user_indexes
  ORDER BY pg_relation_size(indexrelid) DESC LIMIT 20;
"
```

## Resolution

1. Identify the heaviest table. Three possible root causes:
   - **Audit log growth**: `audit_event` and `idempotency_key` can grow fast under
     traffic. Tier audit to a cold-storage table after 6 months (Phase 9 ticket).
   - **Quote JSON bloat**: `quotes.request_json` + `quotes.result_json` are TEXT
     columns. Compress to TOAST'd JSONB (Phase 9 ticket).
   - **Index bloat**: REINDEX CONCURRENTLY the worst-offender indexes.
2. Adjust the auto-grow / monitoring threshold so the alert fires at 70% not 80%.
3. Schedule an offline VACUUM FULL during the next maintenance window if disk
   pressure was due to row bloat.
4. Long-term: implement table partitioning on `audit_event` (monthly partitions),
   archive old partitions to S3.

## Postmortem trigger
Mandatory if any write errors were customer-visible OR if disk hit > 95%.

## Related runbooks
- `database-readiness.md` (RB-03)
- `audit-service-degraded.md` (RB-07)
- `pii-in-logs.md` (RB-10) — log retention can also fill disk

## Last drill
DBA disk-cleanup drill 2026-04-20. Next: 2026-07-20.
