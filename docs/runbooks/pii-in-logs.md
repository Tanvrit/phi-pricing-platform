# RB-10 — PII detected in logs

## Severity
SEV-1 (DPDP / IRDAI compliance breach)

## Symptom
A grep against the last 1h of logs returns plaintext mobile / Aadhaar / PAN / bank
account / IFSC. Either an automated PII-detector job alerted, or a sec sweep / customer
complaint surfaced it.

## Customer impact
**Compliance breach.** DPDP §10 requires PII protection; IRDAI Cyber Security Guidelines
require log sanitisation. If a regulator audit catches this, the company faces fines
+ a Data Protection Board notice. Customers themselves are unaware in the immediate term,
but their data is exposed to anyone with log-access.

## Detection
- Automated regex sweep over centralised logs (Loki / ELK):
  ```bash
  loki-query '{job="rate-server"} |~ "[6-9]\\d{9}"' | head    # 10-digit Indian mobiles
  loki-query '{job="rate-server"} |~ "\\d{12}"'  | head        # Aadhaar-like
  loki-query '{job="rate-server"} |~ "[A-Z]{5}\\d{4}[A-Z]"' | head   # PAN
  ```
- Sec team's weekly sweep.
- Customer / journalist tip.

## First five minutes
1. Acknowledge.
2. Open `#incident-pii` Slack. Page Sec + Legal.
3. **Cap log retention to prevent further exposure**: lower the central log retention
   to the minimum needed for forensics (typically 7 days → 24h) until cleanup.
4. Capture a snapshot of the offending logs for forensics:
   ```bash
   loki-query '<query that matched>' --start=24h --end=now > pii-incident-$(date -u +%Y%m%dT%H%M%SZ).log
   ```
   Store this in the encrypted incident folder, NOT in the general logs index.

## Mitigation (fastest path to restore)

### Step A — Identify the source
Find the code path that logged the PII:
```bash
grep -rn "log.info\|log.debug\|log.warn\|println\|System.out" server/src buyonline/src desktop/src shared/src \
    | grep -i "mobile\|aadhaar\|pan\|account"
```
The `PiiMaskingConverter` should mask anything that slips through — if PII is in the
logs at all, either the converter isn't applied OR the log statement doesn't go through
the standard appender.

### Step B — Patch the source
- Replace the offending log statement with a masked variant.
- If the log line is in a third-party library, wrap the library with a `@CheckPii`
  decorator OR push the data through `PiiMaskingConverter.mask()` before logging.
- Deploy the patch immediately (out-of-cycle SEV-1 deploy is acceptable).

### Step C — Purge the leaked logs
```bash
# Loki delete API
curl -X POST -G 'http://loki:3100/loki/api/v1/delete' \
  --data-urlencode 'query={job="rate-server"} |~ "<offending-pattern>"' \
  --data-urlencode 'start=<start-iso>' \
  --data-urlencode 'end=<end-iso>'
```
Confirm the delete completed; re-run the detection query to verify.

### Step D — Verify the masking converter is doing its job
```bash
# Synthetic test on a known PII string
echo "Mobile: 9876543210, Aadhaar: 234123412346, PAN: ABCDE1234F" | \
  curl -X POST https://api.pruhealth.in/api/test/log-echo \
  -H 'Content-Type: text/plain' --data-binary @-
# Then check the resulting log line in Loki — it MUST show masked values.
```
(If `/api/test/log-echo` doesn't exist, deploy a temporary one during Mitigation OR
use a unit test of `PiiMaskingConverter.mask()`.)

## Diagnostic queries

```bash
# What other PII patterns are present in recent logs?
for pattern in "[6-9]\\d{9}" "\\d{12}" "[A-Z]{5}\\d{4}[A-Z]" "\\d{9,18}"; do
  echo "=== $pattern ==="
  loki-query '{job="rate-server"} |~ "'"$pattern"'"' --limit=5
done
```

```sql
-- How many customer records were potentially exposed?
SELECT count(DISTINCT mobile) FROM quotes
WHERE created_at BETWEEN '<incident-window-start>' AND '<incident-window-end>';
```

## Resolution

1. **DPDP §10 notification**: if more than a small handful of customers were affected,
   notify the Data Protection Board within 72 hours. Legal owns this notification.
2. **Customer comms**: in the unlikely case PII left our system (e.g., logs were
   shipped to a third-party provider that doesn't honour deletion), notify affected
   customers per DPDP.
3. **Engineering**: add a CI lint rule that flags any new `log.*` call site whose
   argument contains keywords `mobile`, `aadhaar`, `pan`, `account` without going
   through a masking helper.
4. **Sec**: rerun the sweep over the last 30 days of logs to confirm no other channels
   leaked PII.

## Postmortem trigger
Mandatory. Sec + Legal co-author. DPB notification text must be in the postmortem.

## Related runbooks
- `audit-service-degraded.md` (RB-07)

## Last drill
Sec sweep 2026-05-01. Next: 2026-06-01.
