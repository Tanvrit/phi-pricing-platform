#!/usr/bin/env bash
# Aegis smoke test — sanity-check every documented endpoint against a running server.
#
# Usage: scripts/smoke.sh [host]   (default: http://localhost:9090)
#
# Expectations assume a freshly-booted server in "bootstrap" mode:
#   - operators store is empty -> RBAC is permissive
#   - in-memory caches are empty
#   - no quotes / sessions / proposals seeded
# When the operators store has entries, several 200 expectations below flip to
# 401/403 — adjust per-call as needed once you've decided who can run smoke.
#
# This script never modifies persistent state in a way that other tests rely on.
# It uses obviously-fake ids (e.g. __nonexistent__) for not-found assertions.
set -uo pipefail

HOST="${1:-http://localhost:9090}"
PASS=0
FAIL=0

check() {
    local method="$1"
    local path="$2"
    local expected_status="$3"
    local body="${4:-}"
    local actual_status

    if [ -n "$body" ]; then
        actual_status=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" \
            -H "Content-Type: application/json" \
            --data "$body" \
            "$HOST$path")
    else
        actual_status=$(curl -s -o /dev/null -w "%{http_code}" -X "$method" "$HOST$path")
    fi

    if [ "$actual_status" = "$expected_status" ]; then
        echo "PASS  $method $path -> $actual_status"
        PASS=$((PASS + 1))
    else
        echo "FAIL  $method $path -> got $actual_status, expected $expected_status"
        FAIL=$((FAIL + 1))
    fi
}

echo "Aegis smoke test against $HOST"
echo "-----------------------------------------"

# ── Ops ─────────────────────────────────────────────────────────
check GET /health 200
check GET /health/live 200
check GET /health/ready 200
check GET /metrics 200

# ── Plans (GET reads are open; mutations are scope-gated) ───────
check GET /api/plans 200
check GET /api/plans/PHI_BASIC 200
# Bootstrap mode -> permissive, so an upsert with a valid body would 200.
# An empty body trips the deserializer first -> 400.
check POST /api/plans 400 '{}'
check DELETE /api/plans/__nonexistent__ 404

# ── Quotes ──────────────────────────────────────────────────────
# Empty body fails request-model deserialization -> 400.
check POST /api/quotes/calculate 400 '{}'
check GET /api/quotes 200
check GET /api/quotes/__nonexistent__ 404
check GET /api/quotes/by-plan/PHI_BASIC 200

# ── Covers / Discounts ──────────────────────────────────────────
check GET /api/covers 200
check GET /api/covers/age-bands 200
check GET /api/covers/family-types 200
check GET /api/covers/sum-insureds 200
check GET /api/discounts 200

# ── Buy-online (customer journey) ───────────────────────────────
check POST /api/buy-online/otp/send 400 '{}'
check GET /api/buy-online/session/__nonexistent__ 404
# sessions list is scope-gated (`sessions.read`) — 200 in bootstrap mode.
check GET /api/buy-online/sessions 200
check "GET" "/api/buy-online/hospitals?pincode=560001" 200

# ── Audit ───────────────────────────────────────────────────────
check GET /api/audit/events 200
# audit.verify scope — 200 in bootstrap mode.
check GET /api/audit/verify 200
check GET /api/audit/idempotency 200

# ── Operators ───────────────────────────────────────────────────
check GET /api/operators 200

# ── Prospectus ──────────────────────────────────────────────────
check GET /api/plans/PHI_BASIC/prospectus.html 200
check GET /api/plans/__nonexistent__/prospectus.html 404

echo ""
echo "-----------------------------------------"
echo "PASS: $PASS"
echo "FAIL: $FAIL"
if [ "$FAIL" -gt 0 ]; then
    exit 1
fi
exit 0
