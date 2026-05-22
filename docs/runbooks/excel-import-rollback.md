# RB-04 — Excel rate-table import wrecked

## Severity
SEV-1 (rate calculations are wrong → financial impact)

## Symptom
After an Excel import, customer quotes return wildly different numbers (often ₹0 or
implausibly high). Aegis Rate Table Manager shows blank/empty cells. Buy-online "GST
applied" is suddenly 0% or 99%. Ops engineer reports they hit "Commit" on an import
that hadn't been dry-run.

## Customer impact
**Direct money risk.** Customers see wrong premiums. Some abandon; some pay the wrong
amount and we owe them a refund / correction.

## Detection
- Manual report from Ops or Product.
- Synthetic-quote canary alert: 10 canonical inputs run every minute; if any deviates >
  5% from the golden value, page fires.
- Buy-online conversion drop alert (RB-05) often co-fires.

## First five minutes
1. Acknowledge.
2. Open `#incident-rate-table` Slack. Page Chief Actuary + Product Lead.
3. **Stop the bleed first** — pause new buy-online traffic:
   ```bash
   BUYONLINE_BANNER=maintenance
   BUYONLINE_QUOTE_DISABLED=true
   ```
4. Confirm the breakage with the canary:
   ```bash
   curl -X POST https://api.pruhealth.in/api/quotes/calculate \
     -H 'Content-Type: application/json' \
     -d @docs/test-fixtures/canonical-quote-01.json | jq .totalIncludingGst
   ```
   Compare against the value in `docs/test-fixtures/canonical-quote-01.expected.txt`.

## Mitigation (fastest path to restore)

### Step A — Roll back the rate snapshot
Aegis Excel Import Studio is the supported way. If Aegis is unavailable, use the
emergency CLI:

```bash
./scripts/rate-snapshot-restore.sh <snapshot-id>
# Lists available snapshots; type a fresh confirmation phrase to commit.
```

If `scripts/rate-snapshot-restore.sh` doesn't yet exist (Phase 4 deliverable), use the
manual DB restore:

```bash
# 1. Identify the snapshot taken just before the bad import
psql "$DB_URL" -c "SELECT id, created_at, source_filename FROM rate_table_snapshots ORDER BY created_at DESC LIMIT 10;"

# 2. Apply the snapshot back to the live tables (TRANSACTIONAL)
psql "$DB_URL" <<SQL
BEGIN;
DELETE FROM base_rates;
DELETE FROM cover_rate_lookup;
DELETE FROM member_level_rates;
DELETE FROM discount_rates;
DELETE FROM cover_availability;
INSERT INTO base_rates SELECT * FROM rate_table_snapshot_base_rates WHERE snapshot_id = <SNAPSHOT_ID>;
INSERT INTO cover_rate_lookup SELECT * FROM rate_table_snapshot_cover_rates WHERE snapshot_id = <SNAPSHOT_ID>;
INSERT INTO member_level_rates SELECT * FROM rate_table_snapshot_member_rates WHERE snapshot_id = <SNAPSHOT_ID>;
INSERT INTO discount_rates SELECT * FROM rate_table_snapshot_discount_rates WHERE snapshot_id = <SNAPSHOT_ID>;
INSERT INTO cover_availability SELECT * FROM rate_table_snapshot_cover_availability WHERE snapshot_id = <SNAPSHOT_ID>;
COMMIT;
SQL
```

### Step B — Verify with canary
Re-run the canary from First Five Minutes. The expected value should now match.

### Step C — Resume buy-online traffic
```bash
unset BUYONLINE_QUOTE_DISABLED
unset BUYONLINE_BANNER
```
Watch buy-online conversion for 15 minutes. If conversion returns to baseline, we're
clean.

### Step D — Write the audit_event row
The rollback itself MUST be audited:
```bash
curl -X POST https://api.pruhealth.in/api/audit/manual-record \
  -H 'Content-Type: application/json' \
  -d '{"action":"rates.rollback","resourceType":"rate_table","resourceId":"<snapshot-id>","reason":"<incident-id>"}'
```

## Diagnostic queries

```sql
-- What got changed in the bad import?
SELECT * FROM rate_table_snapshot_meta
WHERE imported_at > NOW() - INTERVAL '24 hours'
ORDER BY imported_at DESC;

-- Quotes generated against the bad rates (will need a customer-comms exercise)
SELECT id, created_at, plan_id, primary_age, sum_insured, final_premium
FROM quotes
WHERE created_at BETWEEN '<bad-import-time>' AND NOW()
ORDER BY created_at DESC;
```

## Resolution

1. Identify which customer quotes were generated during the bad-rate window and price
   them at the corrected rate. Refund / collect-additional as needed (Product owns the
   customer comms).
2. **Reproduce the issue in staging** with the original Excel file. Confirm what made
   it slip past dry-run (was dry-run skipped? did it not catch the issue?).
3. If dry-run was skipped, file a P0 Aegis ticket: "Excel Import Studio must require
   dry-run before Commit; no override allowed without typed reason + audit_event."
4. If dry-run was run but didn't catch the issue, the validation set is incomplete.
   File: "Add a property-test to the dry-run that confirms 50 canonical quotes are
   within ±5% of their prior values."

## Postmortem trigger
Mandatory. Customer-money was affected.

## Related runbooks
- `engine-zero-premium.md` (RB-06)
- `audit-chain-integrity.md` (RB-01)

## Last drill
Quarterly Excel-import drill 2026-04-08. Next: 2026-07-08.
