# RB-02 — OTP rate-limit hit by legitimate traffic

## Severity
SEV-2 (no money lost; customer drop-off rises)

## Symptom
`rate_otp_sent_total` per-mobile sustained at the 5-per-hour ceiling for > 5% of distinct
mobiles in the last 15 minutes. Customer-support tickets mention "I can't get my OTP".

## Customer impact
Customers whose first OTP didn't arrive (carrier issue, fat-fingered number) try to resend
and hit the rate-limit. They abandon the journey. Buy-online conversion takes a real hit.

## Detection
- Prometheus alert: `rate(rate_otp_sent_total[15m])` per mobile bucket sustained at max.
- Support ticket cluster keyword "OTP".

## First five minutes
1. Acknowledge.
2. Open `#incident-otp` Slack.
3. Confirm the symptom:
   ```bash
   curl -s https://api.pruhealth.in/metrics | grep rate_otp_sent_total | tail -20
   ```
4. Check the SMS gateway status page (MSG91 / Gupshup — link in `docs/integrations/sms.md`).
5. If the gateway is delivering normally but our `_sent_total` is firing, the rate-limit
   is acting on retries from frustrated users — proceed to Mitigation.
6. If the gateway is degraded, jump to `RB-13 — sms-gateway-degraded.md` instead.

## Mitigation (fastest path to restore)

### Option 1 — Temporary rate-limit relaxation (preferred)
```bash
# Live-edit the config; OtpService reads via environment, restart not needed if config
# manager is hot-reload (Phase 2). For now, redeploy with the new limit.
OTP_MAX_SENDS_PER_HOUR=15   # up from 5
```
Re-monitor `rate_otp_sent_total` for 15 minutes. If burn rate normalises, escalate to
Resolution.

### Option 2 — Switch to backup gateway
If the primary SMS gateway is delivering slow OTPs:
```bash
SMS_GATEWAY_PRIMARY=fallback   # routes new traffic to Gupshup fallback
```
Drain pending in-flight sends; switch back when primary recovers.

### Option 3 — Customer comms
Status page banner: "We're seeing delays in SMS OTP delivery; please retry after 5 minutes."
Add to in-app banner via feature flag `BUYONLINE_BANNER=otp_delay`.

## Diagnostic queries

```promql
# OTP sent burn rate per mobile in 15-min window
rate(rate_otp_sent_total[15m])

# OTP verify outcomes — high `not_found` correlates with delivery failures
sum by (outcome) (rate(rate_otp_verified_total[15m]))

# SMS gateway latency (if metrics from gateway are scraped)
histogram_quantile(0.99, rate(sms_gateway_send_duration_seconds_bucket[15m]))
```

```bash
# Tail the request log for OTP traffic patterns
journalctl -u rate-server -f | grep "/api/buy-online/otp"
```

## Resolution

1. Root-cause: gateway delays, carrier blackouts, or a campaign spike all cause this.
   Check `docs/incidents/` for similar past events.
2. Long-term:
   - If carrier blackouts in specific regions are the cause, add a per-region SMS routing
     rule in MSG91 dashboard.
   - If campaign traffic, pre-warm rate-limit ceiling proportional to expected concurrency.
3. Revert any temporary `OTP_MAX_SENDS_PER_HOUR` bump once normal.

## Postmortem trigger
Mandatory if mitigation lasted > 30 min OR a single customer reported a permanent OTP
lockout (the 5-attempts-per-OTP cap can lock a real user — confirm in postmortem).

## Related runbooks
- `sms-gateway-degraded.md` (RB-13)
- `buyonline-conversion-drop.md` (RB-05)

## Last drill
Not yet drilled. Scheduled 2026-06-15 with the buy-online team.
