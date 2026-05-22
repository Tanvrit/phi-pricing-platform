# Runbooks

Operational playbooks for on-call and ops engineers. Each runbook follows a fixed
structure so a sleepy responder at 3 a.m. can scan it without reading prose.

> If you wake to a page, **read the runbook that matches the alert title first**,
> then act. Do not improvise unless every step in the runbook has failed.

## Runbook index

| # | Topic | Page name (alert title) | Severity |
|---|---|---|---|
| RB-01 | Audit chain integrity broken | `audit-chain-integrity.md` | SEV-1 |
| RB-02 | OTP delivery rate-limit hit by legitimate traffic | `otp-rate-limit.md` | SEV-2 |
| RB-03 | `/health/ready` returns 503 | `database-readiness.md` | SEV-1 |
| RB-04 | Excel rate-table import wrecked | `excel-import-rollback.md` | SEV-1 |
| RB-05 | Buy-online conversion drop > 30% / hour | `buyonline-conversion-drop.md` | SEV-2 |
| RB-06 | Pricing engine returning ₹0 totals | `engine-zero-premium.md` | SEV-0 |
| RB-07 | Audit log unwritable (audit service degraded) | `audit-service-degraded.md` | SEV-1 |
| RB-08 | High request latency p99 > 1s | `latency-burn.md` | SEV-2 |
| RB-09 | Disk near capacity on Postgres host | `db-disk-pressure.md` | SEV-2 |
| RB-10 | PII detected in logs | `pii-in-logs.md` | SEV-1 |
| RB-13 | SMS gateway degraded | `sms-gateway-degraded.md` | SEV-2 |

## Runbook template

Every runbook MUST follow this skeleton (no exceptions, even if a section is N/A — write "N/A"):

```
# RB-XX — <Alert title>

## Severity

<SEV-0 | SEV-1 | SEV-2 | SEV-3>

## Symptom

<one-line description of what the alert / pager / dashboard shows>

## Customer impact

<what is broken from the customer's perspective>

## Detection

<how this fires — Prometheus alert? Manual report? Audit chain check job?>

## First five minutes

1. Acknowledge in PagerDuty.
2. Open #incident-<id> in Slack.
3. Run <ONE specific command> to confirm the symptom.
4. Check the metric / log query named below.
5. If confirmed, follow Mitigation.

## Mitigation (fastest path to restore)

<step-by-step, ordered>

## Diagnostic queries

<exact Prometheus / SQL / log queries to determine root cause>

## Resolution

<long-form fix once mitigation is in place>

## Postmortem trigger

<conditions under which a postmortem is mandatory, beyond default SEV-0/1>

## Related runbooks

<links>
```

## Authoring rules

- One runbook per alert. Do not bundle.
- Mitigation must come BEFORE diagnostic. Restore the customer first.
- Every command is copy-pasteable. No "you might want to try …".
- Every runbook ends with the date of its last drill.
- If you fix an incident, update the runbook. Stale runbooks kill incidents.
