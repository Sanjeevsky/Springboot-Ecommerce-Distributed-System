#!/usr/bin/env bash
#
# chaos-suite.sh — Phase 8 PR E: run the resilience validations as one gated suite.
#
# Runs the circuit-breaker (PR B) and saga-compensation (PR C) validations in sequence
# against the live stack, in the spirit of scripts/verify-local.sh. Always restores any
# injected fault on exit, even on failure or interrupt, so a failed run never leaves
# payment-service paused.
#
#   scripts/chaos-suite.sh                 # run both
#   RUN_CIRCUIT_BREAKER=0 scripts/chaos-suite.sh   # saga only
#   RUN_SAGA=0 scripts/chaos-suite.sh              # circuit breaker only
#
# Requires the stack up (docker compose up -d).
#
set -uo pipefail   # not -e: we want to run every check and report a combined result

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

RUN_CIRCUIT_BREAKER="${RUN_CIRCUIT_BREAKER:-1}"
RUN_SAGA="${RUN_SAGA:-1}"

bold() { printf '\033[1m%s\033[0m\n' "$*"; }
green() { printf '\033[32m%s\033[0m\n' "$*"; }
red() { printf '\033[31m%s\033[0m\n' "$*"; }

# Whatever happens, leave the stack in a clean state.
cleanup() { bold "── restoring all services ──"; "$ROOT_DIR/scripts/chaos.sh" restore >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

FAILED=()

run_check() {
  local label="$1" script="$2"
  bold "── ${label} ──"
  if "$ROOT_DIR/$script"; then
    green "✓ ${label} passed"
  else
    red "✗ ${label} failed"
    FAILED+=("$label")
  fi
  echo
}

bold "Resilience chaos suite — $(date '+%Y-%m-%d %H:%M:%S')"
echo

[ "$RUN_CIRCUIT_BREAKER" = "1" ] && run_check "Circuit-breaker validation" "scripts/validate-circuit-breaker.sh"
[ "$RUN_SAGA" = "1" ]            && run_check "Saga compensation validation" "scripts/validate-saga-compensation.sh"

bold "── suite summary ──"
if [ "${#FAILED[@]}" -eq 0 ]; then
  green "All resilience checks passed."
  exit 0
else
  red "Failed: ${FAILED[*]}"
  exit 1
fi
