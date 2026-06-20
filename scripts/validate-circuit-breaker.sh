#!/usr/bin/env bash
#
# validate-circuit-breaker.sh — Phase 8 PR B: prove the order-service payment
# circuit breaker degrades gracefully and recovers.
#
# Steady-state hypotheses (see docs/resilience-plan.md):
#   1. Fail fast, don't hang — when payment-service hangs, the breaker trips and
#      checkout requests fail in well under the time limit instead of riding it
#      on every call.
#   2. Circuit recovers — once payment-service is healthy again the breaker goes
#      open -> half-open -> closed on its own and checkout succeeds.
#
# The breaker that Spring Cloud OpenFeign uses is tuned in
# CircuitBreakerConfiguration.java (4s time limit, opens on sustained failure).
# This script drives the real checkout path through the gateway and asserts the
# transitions from order-service's resilience4j actuator event stream.
#
# Requires the stack up (docker compose up -d) and scripts/chaos.sh.
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8081}"
ORDER_ACTUATOR="${ORDER_ACTUATOR:-http://localhost:8092/actuator}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@trove.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
BASE_URL="${BASE_URL%/}"; ORDER_ACTUATOR="${ORDER_ACTUATOR%/}"
CHAOS="$ROOT_DIR/scripts/chaos.sh"

# The Feign payment breaker id is "HardCodedTarget#initiatePayment(PaymentRequest)";
# match on the stable method substring.
BREAKER_MATCH="${BREAKER_MATCH:-initiatePayment}"
SLOW_THRESHOLD_S="${SLOW_THRESHOLD_S:-2.0}"   # a "hung" call rides the ~4s time limit
FAST_THRESHOLD_S="${FAST_THRESHOLD_S:-1.0}"   # an open-breaker call fails ~instantly

pass() { printf '\033[32m[PASS]\033[0m %s\n' "$*"; }
info() { printf '\033[36m[ cb ]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[FAIL]\033[0m %s\n' "$*" >&2; FAILURES=$((FAILURES+1)); }
FAILURES=0

# Extract .data.<field> from an ApiResponse JSON body.
data_field() { python3 -c "import sys,json
try:
    d=json.load(sys.stdin).get('data')
    print(d.get('$1','') if isinstance(d,dict) else '')
except Exception:
    print('')"; }

# Count breaker state transitions matching $1 (e.g. CLOSED_TO_OPEN) for our breaker.
transitions() {
  curl -s "$ORDER_ACTUATOR/circuitbreakerevents" | python3 -c "import sys,json
m='$BREAKER_MATCH'; want='$1'
try: evs=json.load(sys.stdin)['circuitBreakerEvents']
except Exception: evs=[]
print(sum(1 for e in evs if e.get('type')=='STATE_TRANSITION' and m in e.get('circuitBreakerName','') and e.get('stateTransition','')==want))"
}

# A floating-point '>=' without bc.
fge() { python3 -c "import sys;print('1' if float('$1')>=float('$2') else '0')"; }
flt() { python3 -c "import sys;print('1' if float('$1')<float('$2')  else '0')"; }

# ── Setup: admin token + a fresh product/variant/stock + an address ──────────
info "logging in as admin to seed catalog"
ATOK=$(curl -s -X POST "$BASE_URL/auth-service/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | data_field token)
[ -n "$ATOK" ] || { fail "admin login failed — is the stack up?"; exit 1; }
AUTH=(-H "Authorization: Bearer $ATOK" -H "Content-Type: application/json")

S=$RANDOM
BID=$(curl -s -X POST "$BASE_URL/catalog-service/add-brand?name=CBBrand_$S" "${AUTH[@]}" | data_field id)
CID=$(curl -s -X POST "$BASE_URL/catalog-service/addCategory?categoryName=CBCat_$S" "${AUTH[@]}" | data_field id)
SCID=$(curl -s -X POST "$BASE_URL/catalog-service/add-subcategory/$CID?subcategoryName=CBSub_$S" "${AUTH[@]}" | data_field id)
PID=$(curl -s -X POST "$BASE_URL/catalog-service/product/addProduct?brandId=$BID&categoryId=$CID&subCategoryId=$SCID" "${AUTH[@]}" \
  -d "{\"name\":\"CB Phone $S\",\"description\":\"cb\",\"model\":\"CB-$S\",\"mrpPrice\":1000,\"salePrice\":900,\"gstValue\":18,\"status\":1,\"discount\":100,\"images\":[],\"hasVariant\":true}" | data_field id)
VID=$(curl -s -X POST "$BASE_URL/catalog-service/variant/add/$PID" "${AUTH[@]}" \
  -d '{"condition1Name":"Storage","condition1Value":"256GB","condition2Name":"Color","condition2Value":"Black","mrpPrice":1000,"salePrice":900}' | data_field id)
curl -s -o /dev/null -X POST "$BASE_URL/inventory-service/stock" "${AUTH[@]}" \
  -d "{\"productId\":\"$PID\",\"variantId\":\"$VID\",\"quantity\":1000000}"
AID=$(curl -s -X POST "$BASE_URL/customer-service/address" "${AUTH[@]}" \
  -d '{"home":"2B","streetLocality":"Test Ave","city":"LoadCity","state":"LC","country":"IN","zipCode":400001,"landmark":"Near"}' | data_field id)
[ -n "$PID" ] && [ -n "$VID" ] && [ -n "$AID" ] || { fail "seed failed (PID=$PID VID=$VID AID=$AID)"; exit 1; }
info "seeded product=$PID variant=$VID address=$AID"

add_to_cart() { curl -s -o /dev/null -X POST "$BASE_URL/cart-service/cart/add" "${AUTH[@]}" -d "{\"productId\":\"$PID\",\"variantId\":\"$VID\",\"qty\":1}"; }
place_order() { # prints "<http_code> <seconds>"
  add_to_cart
  curl -s -o /dev/null -w "%{http_code} %{time_total}\n" --max-time 20 \
    -X POST "$BASE_URL/order-service/order" "${AUTH[@]}" -d "{\"addressId\":\"$AID\"}"
}

# ── Baseline: payment healthy, order succeeds ────────────────────────────────
read -r code t < <(place_order) || true
if [ "$code" = "201" ] || [ "$code" = "200" ]; then pass "baseline order placed (http=$code, ${t}s)"; else fail "baseline order failed (http=$code)"; fi

OPEN_BEFORE=$(transitions CLOSED_TO_OPEN)

# ── Hypothesis 1: pause payment -> breaker opens -> calls fail fast ──────────
info "pausing payment-service (simulating a hang)"
"$CHAOS" pause payment-service >/dev/null
saw_slow=0; saw_fast=0
for i in $(seq 1 10); do
  read -r code t < <(place_order) || true
  printf '   order %2d: http=%s time=%ss\n' "$i" "$code" "$t"
  [ "$(fge "$t" "$SLOW_THRESHOLD_S")" = 1 ] && saw_slow=1
  [ "$(flt "$t" "$FAST_THRESHOLD_S")" = 1 ] && [ "$code" != "201" ] && saw_fast=1
done

[ "$saw_slow" = 1 ] && pass "early calls rode the time limit (>=${SLOW_THRESHOLD_S}s) — no instant success while payment hung" \
  || fail "expected at least one call to ride the ~4s time limit"
[ "$saw_fast" = 1 ] && pass "later calls failed fast (<${FAST_THRESHOLD_S}s) — breaker shed load instead of hanging" \
  || fail "expected later calls to fail fast once the breaker opened"
OPEN_AFTER=$(transitions CLOSED_TO_OPEN)
[ "$OPEN_AFTER" -gt "$OPEN_BEFORE" ] && pass "breaker transitioned CLOSED_TO_OPEN ($OPEN_BEFORE -> $OPEN_AFTER)" \
  || fail "no CLOSED_TO_OPEN transition recorded for the payment breaker"

# ── Hypothesis 2: restore payment -> breaker recovers -> orders succeed ──────
info "restoring payment-service"
"$CHAOS" restore payment-service >/dev/null
until [ "$(docker compose ps payment-service --format '{{.Health}}')" = "healthy" ]; do sleep 3; done
info "payment healthy; probing recovery (breaker waits ~10s in open state)"
recovered=0
for i in $(seq 1 12); do
  read -r code t < <(place_order) || true
  printf '   recovery probe %2d: http=%s time=%ss\n' "$i" "$code" "$t"
  if [ "$code" = "201" ] || [ "$code" = "200" ]; then recovered=1; break; fi
  sleep 2
done
[ "$recovered" = 1 ] && pass "checkout recovered automatically after payment returned" \
  || fail "checkout did not recover after payment-service came back"
CLOSED=$(transitions HALF_OPEN_TO_CLOSED)
[ "$CLOSED" -gt 0 ] && pass "breaker transitioned HALF_OPEN_TO_CLOSED — full open->half_open->closed cycle observed" \
  || info "note: HALF_OPEN_TO_CLOSED not seen in event window (recovery still confirmed behaviourally)"

# ── Result ───────────────────────────────────────────────────────────────────
echo
if [ "$FAILURES" -eq 0 ]; then
  pass "circuit-breaker validation passed"
  exit 0
else
  fail "circuit-breaker validation failed with $FAILURES error(s)"
  exit 1
fi
