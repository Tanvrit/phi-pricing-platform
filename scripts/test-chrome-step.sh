#!/usr/bin/env bash
# scripts/test-chrome-step.sh
#
# Runs the "Provide ChromeHeadless for wasmJsBrowserTest" step of
# .github/workflows/build.yml against synthetic browser caches, under the exact
# shell GitHub gives a `shell: bash` step.
#
# ---------------------------------------------------------------------------
# Why a local harness and not a CI run
# ---------------------------------------------------------------------------
# This repo is PUBLIC and the `tanvrit` self-hosted runner group does not serve
# public repositories, so no job in build.yml can be scheduled at all — every
# run since 2026-09-10T19:29:53Z sits `queued`. That is an org-admin setting,
# not something a commit can change. Two successive fixes to this step were
# therefore written, reasoned about and merged without ever executing, and both
# were wrong:
#
#   * the step exited 1 with ZERO output whenever $HOME diverged from /root —
#     the exact case the second fix (417b53c) was written to remove — because
#     `find A B C D | sort | head -1` exits 1 on an absent start directory,
#     `-o pipefail` carries it through the pipe, and `-e` then kills the step;
#   * as a consequence its npx fallback and its "nothing found" message were
#     both unreachable on any box with no cache directory.
#
# Neither is visible by reading, and neither needs a runner to find. This
# harness extracts the step's `run:` body from the YAML — the shipped text, not
# a copy — rewrites only the literal `/root/.cache` to a sandbox, and executes
# it. A change to the step that breaks a case fails here.
#
# Usage:  ./scripts/test-chrome-step.sh
# Exit:   0 when every case behaves, 1 otherwise.

set -euo pipefail

ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
WORKFLOW="$ROOT/.github/workflows/build.yml"
STEP_NAME="Provide ChromeHeadless for wasmJsBrowserTest"
SANDBOX="$(mktemp -d)"
trap 'rm -rf "$SANDBOX"' EXIT

# ---------------------------------------------------------------------------
# Extract the step body. PyYAML when available, otherwise an indentation scan,
# so the harness does not silently stop running when a box lacks PyYAML.
# ---------------------------------------------------------------------------
extract_step() {
  python3 - "$WORKFLOW" "$STEP_NAME" <<'PY'
import sys
path, want = sys.argv[1], sys.argv[2]
try:
    import yaml
except ImportError:
    yaml = None

if yaml is not None:
    doc = yaml.safe_load(open(path, encoding="utf-8"))
    for job in doc["jobs"].values():
        for step in job.get("steps", []):
            if step.get("name") == want:
                sys.stdout.write(step["run"])
                sys.exit(0)
    sys.exit(f"step {want!r} not found in {path}")

lines = open(path, encoding="utf-8").read().splitlines()
i = 0
while i < len(lines) and want not in lines[i]:
    i += 1
if i == len(lines):
    sys.exit(f"step {want!r} not found in {path}")
while i < len(lines) and not lines[i].rstrip().endswith(("run: |", "run: |-")):
    i += 1
i += 1
indent = len(lines[i]) - len(lines[i].lstrip(" "))
out = []
while i < len(lines) and (not lines[i].strip() or
                          len(lines[i]) - len(lines[i].lstrip(" ")) >= indent):
    out.append(lines[i][indent:])
    i += 1
sys.stdout.write("\n".join(out).rstrip() + "\n")
PY
}

BODY="$SANDBOX/step.sh"
extract_step > "$BODY"

# ---------------------------------------------------------------------------
# Fixtures. Exactly the inventory the fleet probes reported (compute run
# 34544934216 and 34544761627): six Chrome-for-Testing builds across a
# puppeteer and an ms-playwright cache, all executable, and the runner is root
# so both caches live under /root.
# ---------------------------------------------------------------------------
FAKE_ROOT="$SANDBOX/root"

make_bin() {   # make_bin <path> <version string>
  mkdir -p "$(dirname "$1")"
  printf '#!/usr/bin/env bash\nif [ "${1:-}" = "--version" ]; then echo "%s"; fi\nexit 0\n' "$2" > "$1"
  chmod +x "$1"
}

populate() {   # populate <cache-parent>
  local base="$1"
  make_bin "$base/.cache/puppeteer/chrome/linux-131.0.6778.204/chrome-linux64/chrome" "Google Chrome for Testing 131.0.6778.204"
  make_bin "$base/.cache/puppeteer/chrome/linux-147.0.7500.10/chrome-linux64/chrome" "Google Chrome for Testing 147.0.7500.10"
  make_bin "$base/.cache/puppeteer/chrome-headless-shell/linux-131.0.6778.204/chrome-headless-shell-linux64/chrome-headless-shell" "Google Chrome for Testing 131.0.6778.204"
  make_bin "$base/.cache/puppeteer/chrome-headless-shell/linux-149.0.7600.0/chrome-headless-shell-linux64/chrome-headless-shell" "Google Chrome for Testing 149.0.7600.0"
  make_bin "$base/.cache/ms-playwright/chromium-1148/chrome-linux/chrome" "Chromium 131.0.6778.33"
  make_bin "$base/.cache/ms-playwright/chromium_headless_shell-1228/chrome-linux/headless_shell" "Chromium 147.0.7500.10"
}

empty_dirs() { # empty_dirs <cache-parent>
  mkdir -p "$1/.cache/puppeteer" "$1/.cache/ms-playwright"
}

# A PATH with no real browser and a stubbed npx, so a case that reaches the
# fallback exercises it without downloading 130 MB.
STUBS="$SANDBOX/stubs"
mkdir -p "$STUBS"
printf '#!/usr/bin/env bash\necho "npx stub: refusing to download in a test"\nexit 1\n' > "$STUBS/npx"
chmod +x "$STUBS/npx"

FAILURES=0

run_case() {   # run_case <name> <home> <expect-exit> [expect-substring-in-output]
  local name="$1" home="$2" want_rc="$3" want_out="${4:-}"
  local envfile="$SANDBOX/github_env" tmpdir="$SANDBOX/runner_temp" out rc
  : > "$envfile"
  rm -rf "$tmpdir"; mkdir -p "$tmpdir"

  # Only the hard-coded /root is rewritten; everything else runs verbatim.
  sed "s#/root/\\.cache#$FAKE_ROOT/.cache#g" "$BODY" > "$SANDBOX/patched.sh"

  set +e
  out="$(HOME="$home" \
         PATH="$STUBS:/usr/bin:/bin" \
         RUNNER_TEMP="$tmpdir" \
         GITHUB_ENV="$envfile" \
         bash --noprofile --norc -e -o pipefail "$SANDBOX/patched.sh" 2>&1)"
  rc=$?
  set -e

  local ok=1 why=""
  [ "$rc" = "$want_rc" ] || { ok=0; why="exit $rc, wanted $want_rc"; }
  if [ -n "$want_out" ] && ! printf '%s' "$out" | grep -qF -- "$want_out"; then
    ok=0; why="${why:+$why; }output does not contain: $want_out"
  fi
  if [ "$want_rc" = "0" ]; then
    local wrap
    wrap="$(sed -n 's/^CHROME_BIN=//p' "$envfile" | tail -1)"
    if [ -z "$wrap" ]; then
      ok=0; why="${why:+$why; }no CHROME_BIN written to GITHUB_ENV"
    elif [ ! -x "$wrap" ]; then
      ok=0; why="${why:+$why; }CHROME_BIN $wrap is not executable"
    elif ! grep -q -- '--no-sandbox' "$wrap"; then
      ok=0; why="${why:+$why; }wrapper does not inject --no-sandbox"
    fi
  fi

  if [ "$ok" = 1 ]; then
    printf 'PASS  %s\n' "$name"
  else
    FAILURES=$((FAILURES + 1))
    printf 'FAIL  %s\n        %s\n' "$name" "$why"
    printf '%s\n' "$out" | sed 's/^/        | /'
  fi
}

# --- A. the fleet as measured: HOME == /root, full inventory --------------
rm -rf "$FAKE_ROOT"; populate "$FAKE_ROOT"
run_case "A  fleet-shaped (HOME == /root, full cache)" "$FAKE_ROOT" 0 \
         "chrome-headless-shell/linux-149.0.7600.0"

# --- B. the case 417b53c exists for: HOME diverges, /root has Chrome ------
# Before this pass: EXIT=1, zero output, Chrome found and thrown away.
rm -rf "$FAKE_ROOT"; populate "$FAKE_ROOT"
mkdir -p "$SANDBOX/elsewhere"
run_case "B  divergent \$HOME, Chrome only under /root" "$SANDBOX/elsewhere" 0 \
         "chrome-headless-shell/linux-149.0.7600.0"

# --- C. no browser cache anywhere: must reach npx, then fail LOUDLY -------
rm -rf "$FAKE_ROOT"; mkdir -p "$FAKE_ROOT"
run_case "C  nothing anywhere -> npx tried, then ::error:: and exit 1" \
         "$SANDBOX/elsewhere" 1 "::error title=No ChromeHeadless::"

# --- D. cache directories exist but are empty -----------------------------
rm -rf "$FAKE_ROOT"; empty_dirs "$FAKE_ROOT"
run_case "D  empty cache dirs -> npx tried, then ::error:: and exit 1" \
         "$SANDBOX/elsewhere" 1 "npx stub"

# --- E. only \$HOME is populated, /root has nothing ------------------------
rm -rf "$FAKE_ROOT"; mkdir -p "$FAKE_ROOT"
rm -rf "$SANDBOX/homeonly"; populate "$SANDBOX/homeonly"
run_case "E  Chrome only under \$HOME" "$SANDBOX/homeonly" 0 \
         "chrome-headless-shell/linux-149.0.7600.0"

# --- F. headless shell is preferred over ms-playwright's chromium ---------
# The old `sort | head -1` picked ms-playwright/chromium-1148, the one binary
# of the six that no probe ever launched.
rm -rf "$FAKE_ROOT"; populate "$FAKE_ROOT"
rm -rf "$SANDBOX/elsewhere2"; mkdir -p "$SANDBOX/elsewhere2"
set +e
sed "s#/root/\.cache#$FAKE_ROOT/.cache#g" "$BODY" > "$SANDBOX/patched.sh"
PICK="$(HOME="$SANDBOX/elsewhere2" PATH="$STUBS:/usr/bin:/bin" \
        RUNNER_TEMP="$SANDBOX" GITHUB_ENV="$SANDBOX/env_f" \
        bash --noprofile --norc -e -o pipefail "$SANDBOX/patched.sh" 2>&1 \
        | sed -n 's/^Resolved Chrome: \([^ ]*\).*/\1/p')"
set -e
case "$PICK" in
  *ms-playwright/chromium-1148*)
    FAILURES=$((FAILURES + 1))
    printf 'FAIL  F  selection\n        picked %s — the binary no probe ever drove\n' "$PICK" ;;
  *chrome-headless-shell*)
    printf 'PASS  F  prefers chrome-headless-shell over ms-playwright chromium\n' ;;
  *)
    FAILURES=$((FAILURES + 1))
    printf 'FAIL  F  selection\n        picked %s\n' "${PICK:-<nothing>}" ;;
esac

# --- G. a system browser on PATH wins, and needs no cache -----------------
rm -rf "$FAKE_ROOT"; mkdir -p "$FAKE_ROOT"
make_bin "$STUBS/chromium" "Chromium 149.0.7600.0"
run_case "G  system chromium on PATH" "$SANDBOX/elsewhere" 0 "$STUBS/chromium"
rm -f "$STUBS/chromium"

echo
if [ "$FAILURES" -ne 0 ]; then
  echo "FAIL: $FAILURES case(s) in the ChromeHeadless step."
  exit 1
fi
echo "OK: 7 case(s), the ChromeHeadless step resolves or fails loudly in every one."
