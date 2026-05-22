# RB-05 — Buy-online conversion drop > 30% / hour

## Severity
SEV-2 (no immediate money lost; revenue impact compounds quickly)

## Symptom
The buy-online quote → proposal conversion rate, normalised hourly, drops more than
30% below the 7-day rolling baseline. Dashboard panel "Buyonline funnel" shows a
spike-down. Product / Growth team may also notice via their own dashboard.

## Customer impact
Customers are starting the journey but abandoning before purchasing. This is almost
always a UX or pricing surprise — they look at the quote and decide it's wrong / too
expensive / broken.

## Detection
- Prometheus alert: hourly `rate_quote_calculations_total` (valid=true) holds but
  `rate_http_requests_total{path="/api/buy-online/proposal",status=~"2..",method="POST"}`
  drops > 30% in the same hour.
- Customer-support ticket cluster matching keywords "quote", "price", "wrong",
  "didn't go through".

## First five minutes
1. Acknowledge.
2. Open `#incident-buyonline` Slack; loop in Product + Growth.
3. Run the canary fixtures to rule out an engine breakage:
   ```bash
   for f in docs/test-fixtures/canonical-quote-0[1-5].json; do
     expected=$(grep -v '^#' "${f%.json}.expected.txt" | head -1)
     actual=$(curl -s -X POST "$BASE/api/buy-online/premium" \
       -H 'Content-Type: application/json' \
       --data @"$f" | jq -r '.totalIncludingGst')
     echo "$f  expected=$expected  actual=$actual"
   done
   ```
4. Eyeball the buy-online journey yourself — open `https://phi-buyonline.pages.dev/`
   in an incognito window and walk through Landing → OTP → Quote. Does it match the
   numbers you expect?
5. Branch:
   - Canary diverges → it's an engine / rate-table issue → jump to **RB-04** or **RB-06**.
   - Canary matches but the journey is visibly broken → continue Mitigation.
   - Canary matches and journey looks fine → it's likely a market / campaign factor;
     work with Growth, not engineering.

## Mitigation (fastest path to restore)

### Step A — Recent deploy rollback
The most common cause of a sudden conversion drop is a recent buy-online deploy.
```bash
# List recent Cloudflare Pages deploys
wrangler pages deployment list --project-name=phi-buyonline | head -5

# Roll back to the previous deployment URL by re-running its deploy script,
# or update the production alias:
wrangler pages deployment alias --project-name=phi-buyonline <previous-deployment-id>
```

### Step B — Disable a misbehaving add-on
If add-on pricing is suspect (e.g. the wrong rate landed for one cover):
```bash
# Temporarily hide an add-on from the catalogue via feature flag
BUYONLINE_DISABLED_ADDONS=critical_illness
```
Re-deploy the buy-online build with the flag set; verify the journey doesn't surface
that add-on; watch conversion.

### Step C — Display a status-page banner
If a backend dependency (SMS gateway, payment GW, KYC) is degraded and is silently
failing the journey:
```bash
BUYONLINE_BANNER="We're experiencing delays in OTP delivery. Please bear with us."
```

### Step D — If nothing UI-side is wrong
Compare the new vs old hourly funnel splits (Landing → OTP send → OTP verify → Quote
view → Quote-tier change → Add-ons → Plan summary → Personal details → Lifestyle →
Medical → Payment → Application complete). The biggest drop-off step is where the
problem lives. Bring that step's owner (Product + Eng) into the channel.

## Diagnostic queries

```promql
# Funnel ratio per step (window: 1h vs 7d baseline)
sum(rate(rate_http_requests_total{path="/api/buy-online/proposal",status=~"2.."}[1h]))
/
sum(rate(rate_http_requests_total{path="/api/buy-online/premium"}[1h]))

# OTP outcomes — high `mismatch` or `expired` correlates with delivery delays
sum by (outcome) (rate(rate_otp_verified_total[1h]))
```

```sql
-- Time-of-day funnel
SELECT
  date_trunc('hour', created_at) AS hr,
  count(*) AS quotes_calculated
FROM quotes
WHERE created_at > NOW() - INTERVAL '24 hours'
GROUP BY 1
ORDER BY 1;
```

## Resolution

1. Identify the funnel step that broke (the one with the biggest delta vs baseline).
2. If it's pricing — refer to RB-06 (engine-zero) or RB-04 (Excel-import-rollback).
3. If it's a UX regression — open the offending PR/deploy; revert; ship a fix.
4. If it's external (SMS / KYC / payment vendor) — open the vendor's status page;
   if they confirm an outage, post a status-page banner pointing to it; otherwise
   open a P2 ticket with the vendor.
5. After mitigation, watch funnel for 24h to confirm baseline is restored.

## Postmortem trigger
Mandatory if conversion drop persisted > 4 hours OR if revenue impact estimate >
₹50,000 (Product owns the estimate).

## Related runbooks
- `engine-zero-premium.md` (RB-06)
- `excel-import-rollback.md` (RB-04)
- `otp-rate-limit.md` (RB-02)
- `pii-in-logs.md` (RB-10)

## Last drill
Not yet drilled. Growth + buyonline team scheduled 2026-06-30.
