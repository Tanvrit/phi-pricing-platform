# RB-06 — Pricing engine returning ₹0 totals

## Severity
SEV-0 (highest — quote calculator is the platform's primary user-facing function)

## Symptom
Customers see `Total payable: ₹0` on the buy-online quote screen, or the desktop
calculator shows ₹0 base premium for valid inputs. The canonical-quote canaries
(`docs/test-fixtures/canonical-quote-*.json`) start diverging by 100% from their
expected values.

## Customer impact
**Customers cannot purchase.** They see an obviously broken quote, abandon the journey,
and lose trust. Agents using the desktop calculator can't quote any prospect.

## Detection
- Canonical-quote canary: diff from `.expected.txt` exceeds tolerance.
- Prometheus alert: `rate_quote_calculations_total{is_valid="true"}` rate drops > 90%
  in 5 min compared to 1h baseline.
- Customer-support tickets with keyword "₹0", "zero premium", "blank quote".
- Buy-online conversion alert (RB-05) likely co-fires.

## First five minutes
1. Acknowledge — this is SEV-0, page CTO + Chief Actuary immediately.
2. Open `#incident-engine-zero` Slack.
3. **Stop new traffic** to the quote calculator so the customer doesn't see broken
   numbers:
   ```bash
   BUYONLINE_QUOTE_DISABLED=true
   ```
4. Run the canary against staging + prod to confirm the scope:
   ```bash
   for f in docs/test-fixtures/canonical-quote-0[1-5].json; do
     expected=$(grep -v '^#' "${f%.json}.expected.txt" | head -1)
     actual=$(curl -s -X POST "$BASE/api/quotes/calculate" \
       -H 'Content-Type: application/json' --data @"$f" | jq -r '.totalIncludingGst')
     echo "$f  expected=$expected  actual=$actual"
   done
   ```

## Mitigation (fastest path to restore)

The engine itself is the most-tested code in the platform — if it's returning ₹0,
the most likely cause is a **rate-table corruption**, NOT a code bug. So mitigate in
this order:

### Step A — Roll back the most recent rate-table import (most common cause)
Same procedure as `RB-04 — excel-import-rollback.md` Step A. If a rate-table import
landed in the last 24h, this is almost certainly the cause.

### Step B — Roll back the most recent server deploy
If no rate-table import happened recently, the engine code may have regressed:
```bash
# Identify the previous tag
git tag --list 'v*' --sort=-creatordate | head -3

# Roll back to the previous release
kubectl set image deployment/rate-server server=ghcr.io/pruhealth/server:<previous-tag>
kubectl rollout status deployment/rate-server
```

Re-run the canary. If green, hold; the engineering team will identify the regression
in the new deploy.

### Step C — Database hardware / replication issue
Rare. If neither A nor B helps, the underlying DB may have replication lag or
hardware corruption causing rate-table reads to return null/zero. Failover to the
standby per `RB-03 — database-readiness.md`.

### Step D — Re-enable buy-online once canaries are green
```bash
unset BUYONLINE_QUOTE_DISABLED
```
Watch the conversion rate for 15 min to confirm.

## Diagnostic queries

```sql
-- Are the rate tables actually populated?
SELECT 'base_rates' AS t, count(*) FROM base_rates UNION ALL
SELECT 'cover_rate_lookup', count(*) FROM cover_rate_lookup UNION ALL
SELECT 'member_level_rates', count(*) FROM member_level_rates UNION ALL
SELECT 'discount_rates', count(*) FROM discount_rates UNION ALL
SELECT 'instalment_config', count(*) FROM instalment_config;

-- For a specific quote that returned ₹0, what does the engine see?
SELECT plan_id, family_type, zone, age_band_min, sum_insured, annual_premium
FROM base_rates
WHERE plan_id = '<plan-id>'
  AND family_type = '<family-type>'
  AND zone = '<zone>'
ORDER BY age_band_min, sum_insured;
```

```bash
# Server logs around the time of the first ₹0 quote
journalctl -u rate-server --since '15 min ago' | grep -E 'engine|rateTable|getBasePremium'

# Check the rate_table_version stamped in recent quote results
psql "$DB_URL" -c "SELECT id, created_at,
  (result_json::jsonb->>'rateTableVersion')   AS rate_ver,
  (result_json::jsonb->>'basePremiumTotal')   AS base
  FROM quotes ORDER BY created_at DESC LIMIT 10;"
```

## Resolution

1. Identify the root cause:
   - Bad rate-table import → see `RB-04` for the full post-resolution actions.
   - Engine regression → revert the offending commit; add a golden test for the
     scenario that would have caught it; back-port the test to `:shared:commonTest`.
   - DB issue → see `RB-03`.
2. **Customer comms**: any customer who saw a ₹0 quote was likely confused, not
   defrauded — apology email is appropriate; no financial follow-up required.
3. **Add a server-side guard** that rejects any quote returning `basePremiumTotal == 0`
   from the API with HTTP 503 + a friendly message, so customers see "try again in a
   minute" rather than ₹0. (This guard ships post-resolution; CR linked from the
   postmortem.)
4. **Improve canary**: if the existing canary didn't catch this within the SLO window,
   add a more frequent (60s → 30s) cron OR an in-engine zero-guard that auto-alerts.

## Postmortem trigger
Mandatory. SEV-0. Postmortem must include:
- Time-to-detect (canary should catch within 60s; if longer, why?)
- Time-to-mitigate
- A "five-whys" trace from the ₹0 quote back to the root cause
- Why golden tests didn't catch it (synthetic rates, real-rate divergence?)
- Action item: ensure golden tests cover the failure mode going forward

## Related runbooks
- `excel-import-rollback.md` (RB-04) — most likely root cause
- `database-readiness.md` (RB-03)
- `latency-burn.md` (RB-08)

## Last drill
Engine-zero tabletop 2026-04-15. Next: 2026-07-15.
