#!/usr/bin/env bash
#
# validate-saga-timeout.sh — Phase 8 PR F: prove the saga timeout reaper compensates a
# saga whose participant is UNREACHABLE, end-to-end against the live stack.
#
# Companion to validate-saga-compensation.sh. That script exercises the *reply-driven*
# path (payment-service replies with a PaymentFailed event). This one exercises the gap
# that path leaves: when payment-service is merely *down*, its ChargePaymentCommand sits
# unconsumed and the saga parks in STOCK_RESERVED forever. SagaTimeoutReaper closes that
# gap — it sweeps sagas parked past `saga.timeout` and drives them to COMPENSATED.
#
# Flow:
#   1. seed a product + stock + address, add to cart
#   2. chaos.sh pause payment-service       (payment unreachable; charge command unconsumed)
#   3. start a real saga (simulatePaymentFailure=false) and confirm it PARKS in STOCK_RESERVED
#   4. wait for the reaper to fire, then assert the saga reached COMPENSATED with a
#      timeout lastError, the reserved stock was released, and the cart was preserved
#   5. restore payment-service (also on any early exit / Ctrl-C)
#
# The default reaper waits saga.timeout (2m) + one interval (30s), so this script waits up
# to REAP_WAIT=180s by default. To run it fast, start order-service with a short reaper —
#   SAGA_TIMEOUT=PT15S SAGA_REAPER_INTERVAL_MS=5000   (env → saga.timeout / saga.reaper.interval-ms)
# — and set REAP_WAIT=40.
#
# Requires the stack up (docker compose up -d) and scripts/chaos.sh.
#
set -uo pipefail   # not -e: we always want to reach the restore step and report a result

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

CHAOS="$ROOT_DIR/scripts/chaos.sh"
BASE_URL="${BASE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@trove.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
BASE_URL="${BASE_URL%/}"
RESERVE_ATTEMPTS="${RESERVE_ATTEMPTS:-30}"   # seconds to wait for the saga to reach STOCK_RESERVED
PARK_CONFIRM_S="${PARK_CONFIRM_S:-6}"        # confirm it stays parked (no reply can move it)
REAP_WAIT="${REAP_WAIT:-180}"                # max seconds to wait for the reaper to compensate

pass() { printf '\033[32m[PASS]\033[0m %s\n' "$*"; }
info() { printf '\033[36m[reap]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[FAIL]\033[0m %s\n' "$*" >&2; FAILURES=$((FAILURES+1)); }
FAILURES=0

# Always un-pause payment-service, even on failure or Ctrl-C, so a failed run never
# leaves the stack degraded.
cleanup() { info "restoring payment-service"; "$CHAOS" restore payment-service >/dev/null 2>&1 || true; }
trap cleanup EXIT INT TERM

jdata() { python3 -c "import sys,json
try:
    d=json.load(sys.stdin).get('data')
    print(d.get('$1','') if isinstance(d,dict) else '')
except Exception:
    print('')"; }

reserved_qty() {
  curl -s "$BASE_URL/inventory-service/stock/$PID/variant/$VID" "${AUTH[@]}" | python3 -c "import sys,json
try: print(json.load(sys.stdin)['data']['reservedQty'])
except Exception: print('?')"
}

cart_count() {
  curl -s "$BASE_URL/cart-service/cart" "${AUTH[@]}" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin).get('data') or {}
    items=d.get('items') or d.get('cartItems') or []
    print(len(items))
except Exception: print(0)"
}

add_to_cart() { curl -s -o /dev/null -X POST "$BASE_URL/cart-service/cart/add" "${AUTH[@]}" -d "{\"productId\":\"$PID\",\"variantId\":\"$VID\",\"qty\":$1}"; }

start_saga() {
  curl -s -X POST "$BASE_URL/order-service/order/saga?simulatePaymentFailure=false" "${AUTH[@]}" \
    -d "{\"addressId\":\"$AID\"}" | jdata id
}

saga_field() { curl -s "$BASE_URL/order-service/order/$1/saga" "${AUTH[@]}" | jdata "$2"; }

# ── Setup ────────────────────────────────────────────────────────────────────
info "logging in as admin to seed catalog"
ATOK=$(curl -s -X POST "$BASE_URL/auth-service/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jdata token)
[ -n "$ATOK" ] || { fail "admin login failed — is the stack up?"; exit 1; }
AUTH=(-H "Authorization: Bearer $ATOK" -H "Content-Type: application/json")

S=$RANDOM
BID=$(curl -s -X POST "$BASE_URL/catalog-service/add-brand?name=ReapBrand_$S" "${AUTH[@]}" | jdata id)
CID=$(curl -s -X POST "$BASE_URL/catalog-service/addCategory?categoryName=ReapCat_$S" "${AUTH[@]}" | jdata id)
SCID=$(curl -s -X POST "$BASE_URL/catalog-service/add-subcategory/$CID?subcategoryName=ReapSub_$S" "${AUTH[@]}" | jdata id)
PID=$(curl -s -X POST "$BASE_URL/catalog-service/product/addProduct?brandId=$BID&categoryId=$CID&subCategoryId=$SCID" "${AUTH[@]}" \
  -d "{\"name\":\"Reap $S\",\"description\":\"x\",\"model\":\"RP-$S\",\"mrpPrice\":1000,\"salePrice\":900,\"gstValue\":18,\"status\":1,\"discount\":0,\"images\":[],\"hasVariant\":true}" | jdata id)
VID=$(curl -s -X POST "$BASE_URL/catalog-service/variant/add/$PID" "${AUTH[@]}" \
  -d '{"condition1Name":"Storage","condition1Value":"256GB","condition2Name":"Color","condition2Value":"Black","mrpPrice":1000,"salePrice":900}' | jdata id)
curl -s -o /dev/null -X POST "$BASE_URL/inventory-service/stock" "${AUTH[@]}" \
  -d "{\"productId\":\"$PID\",\"variantId\":\"$VID\",\"quantity\":100}"
AID=$(curl -s -X POST "$BASE_URL/customer-service/address" "${AUTH[@]}" \
  -d '{"home":"2B","streetLocality":"Test Ave","city":"LoadCity","state":"LC","country":"IN","zipCode":400001,"landmark":"Near"}' | jdata id)
[ -n "$PID" ] && [ -n "$VID" ] && [ -n "$AID" ] || { fail "seed failed (PID=$PID VID=$VID AID=$AID)"; exit 1; }
info "seeded product=$PID variant=$VID stock=100 address=$AID"

# ── Inject the fault: payment-service unreachable ────────────────────────────
info "pausing payment-service (charge command will sit unconsumed)"
"$CHAOS" pause payment-service >/dev/null

RES_BEFORE=$(reserved_qty)
add_to_cart 2
CART_BEFORE=$(cart_count)
OID=$(start_saga)
[ -n "$OID" ] || { fail "saga did not start (no order id)"; exit 1; }
info "saga orderId=$OID (reserved before=$RES_BEFORE, cart before=$CART_BEFORE)"

# ── It must PARK in STOCK_RESERVED (payment never replies) ────────────────────
ST=""
for _ in $(seq 1 "$RESERVE_ATTEMPTS"); do
  ST=$(saga_field "$OID" status)
  [ "$ST" = "STOCK_RESERVED" ] && break
  case "$ST" in COMPLETED|COMPENSATED|FAILED) break;; esac
  sleep 1
done
[ "$ST" = "STOCK_RESERVED" ] && pass "saga reserved stock and is waiting on payment (STOCK_RESERVED)" \
  || fail "saga did not reach STOCK_RESERVED (status='$ST')"

# Confirm it stays parked — with payment down, no reply can advance it, so only the reaper can.
sleep "$PARK_CONFIRM_S"
ST=$(saga_field "$OID" status)
[ "$ST" = "STOCK_RESERVED" ] && pass "saga parked in STOCK_RESERVED with payment unreachable (no reply path)" \
  || fail "saga left STOCK_RESERVED without the reaper (status='$ST')"

# ── The reaper must compensate it ────────────────────────────────────────────
info "waiting up to ${REAP_WAIT}s for the timeout reaper to compensate the parked saga"
ST=""
waited=0
while [ "$waited" -lt "$REAP_WAIT" ]; do
  ST=$(saga_field "$OID" status)
  case "$ST" in COMPENSATED|COMPLETED|FAILED) break;; esac
  sleep 5; waited=$((waited+5))
  printf '   ...%ss elapsed (status=%s)\n' "$waited" "$ST"
done
[ "$ST" = "COMPENSATED" ] && pass "reaper compensated the parked saga (COMPENSATED after ~${waited}s)" \
  || fail "saga not compensated within ${REAP_WAIT}s (status='$ST') — is the reaper enabled / saga.timeout short enough?"

ERR=$(saga_field "$OID" lastError)
case "$ERR" in
  *"timed out"*) pass "compensation was timeout-driven, not a payment reply (lastError: $ERR)";;
  *) fail "lastError does not indicate a timeout (got: '$ERR')";;
esac

RES_AFTER=$(reserved_qty)
[ "$RES_AFTER" = "$RES_BEFORE" ] && pass "reserved stock released back to inventory ($RES_BEFORE -> $RES_AFTER)" \
  || fail "reserved stock not released (before=$RES_BEFORE after=$RES_AFTER)"

CART_AFTER=$(cart_count)
[ "$CART_AFTER" = "$CART_BEFORE" ] && [ "$CART_AFTER" -gt 0 ] && pass "cart preserved after compensation (count=$CART_AFTER)" \
  || fail "cart not preserved after compensation (before=$CART_BEFORE after=$CART_AFTER)"

# ── Result ───────────────────────────────────────────────────────────────────
echo
if [ "$FAILURES" -eq 0 ]; then
  pass "saga timeout reaper validation passed"
  exit 0
else
  fail "saga timeout reaper validation failed with $FAILURES error(s)"
  exit 1
fi
