# Test fixtures

Canonical quote inputs + expected outputs that act as **production canaries** for the
pricing engine. The Excel-import-rollback runbook (`RB-04`) and the engine-zero-premium
runbook (`RB-06`) both verify against these.

Each fixture is two files:

- `canonical-quote-NN.json`     — input QuoteRequest JSON, hits `/api/quotes/calculate`
- `canonical-quote-NN.expected.txt` — expected `totalIncludingGst` to the paise

The canary cron runs every 60 s in production:

```bash
for f in docs/test-fixtures/canonical-quote-*.json; do
  expected=$(cat "${f%.json}.expected.txt")
  actual=$(curl -s -X POST "$BASE/api/quotes/calculate" \
    -H 'Content-Type: application/json' \
    --data @"$f" | jq -r '.totalIncludingGst')
  if [ "$(printf '%.2f' "$actual")" != "$(printf '%.2f' "$expected")" ]; then
    echo "ALERT: $f diverged — expected $expected got $actual"
    # Page on-call
  fi
done
```

These fixtures are NOT the engine's golden tests (those live in `shared/src/commonTest/`).
Golden tests use a `FakeRateDataProvider` with synthetic rates; the fixtures here hit the
**real production rate table** so any rate-import accident is caught in seconds.

## Inventory

| Fixture | Plan | Family | Age | SI | Zone | Tenure | Why this scenario |
|---|---|---|---|---|---|---|---|
| 01 | PHI_BASIC | 1A | 30 | ₹10L | Zone 1 | 1 year | Cheapest baseline path |
| 02 | PHI_FLAGSHIP1 | 2A2C | 35 | ₹25L | Zone 2 | 3 years | Family floater, mid-tier |
| 03 | PHI_SENIOR | 1A | 65 | ₹10L | Pan India | 1 year | Senior age-band boundary |
| 04 | PHI_GLOBAL1 | 2A | 50 | ₹1Cr | Zone 1 | 5 years | High-SI global |
| 05 | PHI_BASIC | 1A | 30 | ₹10L | Zone 1 | 1 year, MONTHLY | Instalment loading + GST |

To add a fixture:

1. Choose a scenario where a rate-table accident would change the output.
2. Hit `POST /api/quotes/calculate` against a known-good production environment.
3. Save the request body as `canonical-quote-NN.json`.
4. Save the response's `totalIncludingGst` as `canonical-quote-NN.expected.txt`.
5. Add a row to the table above. Commit both files in the same change.

Fixtures are tied to a specific `rateTableVersion`. The expected file's first line MUST
include the rate-table version the value was captured at; the canary refuses to compare
when the deployed rate table is a different version.
