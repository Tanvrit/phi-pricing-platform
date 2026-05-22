# RB-08 — High request latency p99 > 1s

## Severity
SEV-2 (no outage; user experience degraded; conversion at risk)

## Symptom
p99 request latency on `/api/quotes/calculate` or `/api/buy-online/premium` exceeds
1 second for 5+ consecutive minutes. The SLO from `AUDIT_REPORT.md` §10.1 says p99
should be < 250 ms in steady state. Users may complain of "slow quote calculator".

## Customer impact
- Buy-online journey "Calculating…" loader stays up longer than expected.
- Desktop calculator agent gets visibly slower; quote-rate per hour drops.
- If latency spikes high enough (>30s), upstream client requests start timing out.

## Detection
- Prometheus alert: `histogram_quantile(0.99, rate(rate_http_request_duration_seconds_bucket[5m])) > 1.0`
  for any (method, path).
- APM (Phase 9) trace overview shows spike in trace duration.

## First five minutes
1. Acknowledge.
2. Open `#incident-latency` Slack.
3. Identify which endpoint is slow:
   ```promql
   topk(5, histogram_quantile(0.99, sum by (method, path, le) (rate(rate_http_request_duration_seconds_bucket[5m]))))
   ```
4. Read recent log lines for that path:
   ```bash
   journalctl -u rate-server --since '10 min ago' | grep "<path>" | head -50
   ```
5. Determine: is the JVM busy, is the DB busy, or is an external dep slow?

## Mitigation (fastest path to restore)

### Step A — DB is the bottleneck (most common)
Symptoms: `pg_stat_activity` shows long-running queries; HikariCP active near max.
```sql
-- Top time-sucking active queries
SELECT pid, age(query_start, now()) AS age, state, wait_event, query
FROM pg_stat_activity
WHERE state != 'idle'
ORDER BY query_start LIMIT 10;
-- Kill the worst offender if it's truly stuck:
SELECT pg_cancel_backend(<pid>);
```
If a particular query path is hot — e.g. cover_rate_lookup or member_level_rates —
the index may be missing or stale:
```sql
REINDEX TABLE CONCURRENTLY cover_rate_lookup;
ANALYZE cover_rate_lookup;
```

### Step B — JVM GC pressure
Symptoms: `jstat -gc <pid>` shows long Old-gen pauses. Indicates a memory leak or
under-provisioned heap.
```bash
# Capture a heap dump for postmortem
jcmd <pid> GC.heap_dump /tmp/rate-server-heap-$(date -u +%Y%m%dT%H%M%SZ).hprof
# Rolling restart the pod
kubectl delete pod -l app=rate-server
```

### Step C — Engine cold cache
Right after a cold start, the first ~50 quote calculations are slow because
LocalRateDataProvider has no cached lookups. If we just deployed, expect the warm-up.
```bash
# Run the canary to warm the JVM + lookups
for f in docs/test-fixtures/canonical-quote-0[1-5].json; do
  curl -s -X POST "$BASE/api/quotes/calculate" \
    -H 'Content-Type: application/json' --data @"$f" > /dev/null
done
```

### Step D — Scale out
If steady-state load is exceeding single-pod capacity:
```bash
kubectl scale deployment/rate-server --replicas=3
```

### Step E — Reduce work in the request path
Quick wins, applied via feature flag:
```bash
QUOTE_SKIP_AUDIT_FAST_PATH=true   # don't audit /api/quotes/calculate (only /api/quotes save)
QUOTE_SKIP_METRICS_HISTOGRAM=true  # disable histogram only — counters keep going
```
These are deliberate "degrade by removing observability" knobs that exist for
emergencies. Re-enable them once the underlying issue is fixed.

## Diagnostic queries

```promql
# Per-path latency distribution
histogram_quantile(0.99, sum by (method, path, le) (rate(rate_http_request_duration_seconds_bucket[5m])))
histogram_quantile(0.50, sum by (method, path, le) (rate(rate_http_request_duration_seconds_bucket[5m])))

# DB connection pressure
rate_db_connections_active / rate_db_connections_total

# Heap (when APM is wired)
process_resident_memory_bytes
jvm_memory_used_bytes{area="heap"}
```

```bash
# JVM internals
jstat -gcutil <pid> 1s 10            # GC behaviour
jstack <pid> | head -200             # thread dump
```

## Resolution

1. Identify root cause (DB / GC / cold cache / load / regression).
2. If DB — add or rebuild the missing index; consider read-only replica for
   reporting queries.
3. If GC — bump heap; consider a memory profiler run; eliminate hot leaks.
4. If load — add the HPA rule that ought to have scaled us automatically.
5. If a code regression caused it — bisect the deploys; revert the offending one.
6. After mitigation, add a property test or load-test that would catch the
   regression in CI (e.g. an assertion that p99 of 500 sequential canary calls stays
   < 250 ms).

## Postmortem trigger
Mandatory if latency violated SLO for > 30 minutes OR if conversion dropped > 10%
during the window.

## Related runbooks
- `database-readiness.md` (RB-03)
- `db-disk-pressure.md` (RB-09)
- `engine-zero-premium.md` (RB-06)
- `buyonline-conversion-drop.md` (RB-05)

## Last drill
Load-test rehearsal 2026-04-01. Next: 2026-07-01.
