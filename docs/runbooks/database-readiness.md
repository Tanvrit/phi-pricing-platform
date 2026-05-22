# RB-03 — `/health/ready` returns 503

## Severity
SEV-1

## Symptom
Kubernetes / load balancer health-check fails. `/health/ready` returns
`{"status":"down","check":"db: <message>"}`. New traffic stops being routed to the
affected pod. If all pods fail readiness simultaneously, customer-facing API is down.

## Customer impact
- One pod failing: brief blip; K8s reroutes to siblings.
- All pods failing: **buy-online + Aegis + quote-API all return 503**. Customer cannot
  purchase. Aegis cannot edit. Quote calculator returns errors.

## Detection
- K8s readiness probe failure → pod removed from service endpoints.
- Prometheus alert: any pod with `kube_pod_status_ready{condition="false"} > 30s`.
- The `/health/ready` endpoint's last failure message is in the response and the logs.

## First five minutes
1. Acknowledge.
2. Open `#incident-db` Slack. If all pods are failing, page SRE + DBA.
3. Confirm scope:
   ```bash
   kubectl get pods -l app=rate-server -o wide
   kubectl describe pod <pod-name> | grep -A5 Readiness
   ```
4. Check the failure message: `curl -sk https://<pod-ip>/health/ready | jq`.
5. Determine: is the DB really down, or is it a pool exhaustion / network issue?

## Mitigation (fastest path to restore)

### Step A — Confirm DB is actually unreachable
```bash
# From a healthy bastion or sidecar:
psql "$DB_URL" -c 'SELECT 1;'
# If this also fails, the DB itself is down → jump to Step B.
# If this succeeds, the issue is pool exhaustion or auth in the pod → Step C.
```

### Step B — DB is genuinely down
1. Check the managed DB console (RDS / Cloud SQL) for instance health.
2. If primary failed and a read replica is healthy, promote it:
   ```bash
   # AWS RDS example
   aws rds promote-read-replica --db-instance-identifier pruhealth-replica-1
   ```
3. Update `DB_URL` env var to point at the new primary.
4. Re-roll the deployment:
   ```bash
   kubectl rollout restart deployment/rate-server
   ```
5. Confirm `/health/ready` passes on all pods.
6. Notify business: "Brief read-only window during DB failover; service restored."

### Step C — Connection pool exhaustion
```bash
# Check HikariCP active vs total
curl -s https://api.pruhealth.in/metrics | grep rate_db_connections
# rate_db_connections_active near max + rate_db_connections_idle 0 → exhaustion
```
1. Restart the affected pod (releases stuck connections):
   ```bash
   kubectl delete pod <pod-name>
   ```
2. If recurring, bump `DB_POOL_SIZE` env var (default 20 → try 40) and re-deploy.

### Step D — DB credentials / auth
- If `/health/ready` reports an auth error, the DB password was rotated without the
  secrets manager being updated. Re-fetch from Vault and re-deploy.

## Diagnostic queries

```sql
-- Active connections + state distribution
SELECT state, count(*) FROM pg_stat_activity GROUP BY state;

-- Long-running queries (likely culprit for pool exhaustion)
SELECT pid, now()-query_start AS duration, state, query
FROM pg_stat_activity
WHERE state != 'idle' AND query_start < now() - INTERVAL '30 seconds'
ORDER BY duration DESC;

-- Locks
SELECT * FROM pg_locks WHERE NOT granted;
```

```promql
rate_db_connections_active
rate_db_connections_idle
rate_db_connections_total
```

## Resolution

1. After mitigation, identify the long-running query that exhausted the pool (if Step C).
2. Add an explicit timeout to that query path or refactor it to batch.
3. Add a Prometheus alert on
   `rate_db_connections_active / rate_db_connections_total > 0.8 for 5m` so we get a
   warning before the SEV-1 fires next time.
4. Update post-mitigation: any temporary pool-size bump should be reviewed within
   24h to decide if it's permanent.

## Postmortem trigger
Mandatory for any SEV-1.

## Related runbooks
- `db-disk-pressure.md` (RB-09)
- `latency-burn.md` (RB-08)

## Last drill
Failover drill 2026-04-20 (quarterly). Next: 2026-07-20.
