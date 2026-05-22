# RB-13 — SMS gateway degraded

## Severity
SEV-2 (no money lost; customer onboarding stalls)

## Symptom
Customers report not receiving OTPs after clicking *Send OTP* on the buy-online
journey. The SMS gateway vendor's status page may show degradation; SLA dashboards
show p95 SMS delivery latency > 30s for sustained period.

> **Note:** As of the Foundation Pack, real SMS delivery is NOT yet wired. The OTP
> is generated server-side and printed to logs (`grep "OTP issued" /tmp/rate-server.log`).
> This runbook is for Phase 3 onwards when MSG91 / Gupshup / Karix is integrated.

## Customer impact
- New customers cannot complete OTP verification → drop off the buy-online funnel
  before Quote screen.
- Existing customers cannot perform KYC re-verification.
- Renewal flow (Phase 5) breaks for any renewal requiring OTP re-confirm.

## Detection
- Prometheus: `rate_otp_verified_total{outcome="not_found"}` rate climbs (customers
  give up while waiting for the SMS, OTP expires before verify).
- Vendor status page (subscribe via PagerDuty integration in Phase 3).
- Customer-support ticket cluster matching keywords "OTP", "not received", "SMS".

## First five minutes
1. Acknowledge.
2. Open `#incident-sms` Slack.
3. Confirm the vendor outage:
   ```bash
   # MSG91 (placeholder URL — replace with real status endpoint in Phase 3)
   curl -s https://status.msg91.com/api/v2/status.json | jq '.status.description'
   ```
4. Confirm with internal metric:
   ```promql
   sum(rate(rate_otp_sent_total[5m]))         # we're sending
   sum(rate(rate_otp_verified_total{outcome="ok"}[5m]))  # but verifies dropped
   ```
   A growing send-to-verify gap = customers not receiving the SMS.
5. Reach out to vendor contact (in `docs/integrations/sms.md` once Phase 3 lands).

## Mitigation (fastest path to restore)

### Step A — Switch to backup SMS gateway
We maintain a primary + fallback in Phase 3 onwards. Flip via env var:
```bash
SMS_GATEWAY_PRIMARY=fallback   # routes new sends to the backup vendor
kubectl rollout restart deployment/rate-server
```
Existing OTPs in flight stay with the original gateway (they'll either succeed or
expire); new sends from this point use the backup.

### Step B — Extend OTP TTL temporarily
If both gateways are slow, customers may be experiencing legitimate delivery delays
> 5 min. Extend the TTL so they have more time to enter the OTP they eventually
receive:
```bash
OTP_TTL_SECONDS=900   # bump from 300 → 900 (15 minutes)
```
Restart. Watch metrics — verify ratio should improve.

### Step C — Surface the degradation to customers
```bash
BUYONLINE_BANNER="SMS delivery is delayed. Your OTP may take up to 10 minutes to arrive. Please be patient."
```
Set the banner via the existing feature-flag mechanism. Buy-online customer-side
banner uses the in-app `OtpScreen` slot.

### Step D — Add a workaround path
For high-value customers blocked by SMS, manual-approve at the support desk:
```bash
# Internal-only endpoint, requires auth (Phase 2 JWT + Auditor role)
curl -X POST $BASE/api/buy-online/otp/manual-approve \
  -H "Authorization: Bearer $SUPPORT_TOKEN" \
  -H 'Content-Type: application/json' \
  -d '{"mobile":"9876543210","approver":"support-agent-X","reason":"sms-vendor-outage"}'
```
This writes an `audit_event` with `action = otp.manually_approved` so the incident
post-mortem can review the approvals.

## Diagnostic queries

```promql
# Delivery success ratio
sum(rate(rate_otp_verified_total{outcome="ok"}[5m]))
/
sum(rate(rate_otp_sent_total[5m]))

# Per-purpose breakdown — is KYC OTP also affected?
sum by (purpose) (rate(rate_otp_sent_total[5m]))
sum by (purpose, outcome) (rate(rate_otp_verified_total[5m]))

# OTP-expired rate
sum(rate(rate_otp_verified_total{outcome="expired"}[5m]))
```

```sql
-- Customers actively retrying SMS during the window (Phase 2+)
SELECT mobile, attempts, expires_at, created_at
FROM otp_records
WHERE created_at > NOW() - INTERVAL '30 minutes'
  AND purpose = 'login'
ORDER BY attempts DESC
LIMIT 20;
```

## Resolution

1. Confirm vendor incident resolved.
2. Flip back to primary gateway:
   ```bash
   unset SMS_GATEWAY_PRIMARY   # or set explicitly back to msg91
   ```
3. Reset OTP TTL to the standard 5 minutes:
   ```bash
   unset OTP_TTL_SECONDS
   ```
4. Remove status-page banner.
5. If a manual-approve workaround was used, review every audit_event entry from
   the window. Confirm each approval has a legitimate support ticket attached.
6. Update vendor SLA report. If breaches > monthly threshold, trigger vendor
   review.

## Postmortem trigger
Mandatory if mitigation lasted > 30 min OR a manual-approve workaround was used
(those need legal review per IRDAI's "no-OTP issuance" boundary).

## Related runbooks
- `otp-rate-limit.md` (RB-02) — when our internal rate limit is the culprit, not vendor
- `buyonline-conversion-drop.md` (RB-05) — co-fires when SMS outage tanks conversion

## Last drill
Not yet drilled. Buy-online + ops team scheduled with Phase 3 vendor cutover.
