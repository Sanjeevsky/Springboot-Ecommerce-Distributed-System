#!/usr/bin/env bash
#
# validate-saga-compensation.sh — Phase 8 PR C: prove the checkout saga compensates
# end-to-end against the live services and Kafka topics.
#
# Steady-state hypothesis (see docs/resilience-plan.md):
#   3. Saga compensates — a saga order that fails at payment reaches COMPENSATED,
#      the reserved stock is released back to inventory, and the cart is NOT cleared.
#
# The saga's compensation is triggered by a payment *failure reply*, so the correct
# lever is POST /order/saga?simulatePaymentFailure=true (payment-service replies with
# a PaymentFailed event). A payment-service that is merely *down* leaves the charge
# command unconsumed and the saga parked in STOCK_RESERVED — there is no timeout-based
# compensation — so this script does not kill payment for the compensation case.
#
# As a control it first runs a healthy saga and asserts it COMPLETES and clears the
# cart, so a COMPENSATED result in the failure case can't be a false positive.
#
# Requires the stack up (docker compose up -d).
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8081}"
ADMIN_EMAIL="${ADMIN_EMAIL:-admin@trove.local}"
ADMIN_PASSWORD="${ADMIN_PASSWORD:-admin123}"
BASE_URL="${BASE_URL%/}"
POLL_ATTEMPTS="${POLL_ATTEMPTS:-30}"

pass() { printf '\033[32m[PASS]\033[0m %s\n' "$*"; }
info() { printf '\033[36m[saga]\033[0m %s\n' "$*"; }
fail() { printf '\033[31m[FAIL]\033[0m %s\n' "$*" >&2; FAILURES=$((FAILURES+1)); }
FAILURES=0

jdata() { python3 -c "import sys,json
try:
    d=json.load(sys.stdin).get('data')
    print(d.get('$1','') if isinstance(d,dict) else '')
except Exception:
    print('')"; }

# Reserved quantity for the seeded variant.
reserved_qty() {
  curl -s "$BASE_URL/inventory-service/stock/$PID/variant/$VID" "${AUTH[@]}" | python3 -c "import sys,json
try: print(json.load(sys.stdin)['data']['reservedQty'])
except Exception: print('?')"
}

# Number of line items currently in the cart.
cart_count() {
  curl -s "$BASE_URL/cart-service/cart" "${AUTH[@]}" | python3 -c "import sys,json
try:
    d=json.load(sys.stdin).get('data') or {}
    items=d.get('items') or d.get('cartItems') or []
    print(len(items))
except Exception: print(0)"
}

add_to_cart() { curl -s -o /dev/null -X POST "$BASE_URL/cart-service/cart/add" "${AUTH[@]}" -d "{\"productId\":\"$PID\",\"variantId\":\"$VID\",\"qty\":$1}"; }

# Start a saga and echo the order id. $1 = simulatePaymentFailure (true|false).
start_saga() {
  curl -s -X POST "$BASE_URL/order-service/order/saga?simulatePaymentFailure=$1" "${AUTH[@]}" \
    -d "{\"addressId\":\"$AID\"}" | jdata id
}

# Poll a saga to a terminal status and echo it.
poll_saga() {
  local oid="$1" st
  for _ in $(seq 1 "$POLL_ATTEMPTS"); do
    st=$(curl -s "$BASE_URL/order-service/order/$oid/saga" "${AUTH[@]}" | jdata status)
    case "$st" in COMPLETED|COMPENSATED|FAILED) echo "$st"; return;; esac
    sleep 1
  done
  echo "$st"
}

# ── Setup ────────────────────────────────────────────────────────────────────
info "logging in as admin to seed catalog"
ATOK=$(curl -s -X POST "$BASE_URL/auth-service/login" -H 'Content-Type: application/json' \
  -d "{\"email\":\"$ADMIN_EMAIL\",\"password\":\"$ADMIN_PASSWORD\"}" | jdata token)
[ -n "$ATOK" ] || { fail "admin login failed — is the stack up?"; exit 1; }
AUTH=(-H "Authorization: Bearer $ATOK" -H "Content-Type: application/json")

S=$RANDOM
BID=$(curl -s -X POST "$BASE_URL/catalog-service/add-brand?name=SagaBrand_$S" "${AUTH[@]}" | jdata id)
CID=$(curl -s -X POST "$BASE_URL/catalog-service/addCategory?categoryName=SagaCat_$S" "${AUTH[@]}" | jdata id)
SCID=$(curl -s -X POST "$BASE_URL/catalog-service/add-subcategory/$CID?subcategoryName=SagaSub_$S" "${AUTH[@]}" | jdata id)
PID=$(curl -s -X POST "$BASE_URL/catalog-service/product/addProduct?brandId=$BID&categoryId=$CID&subCategoryId=$SCID" "${AUTH[@]}" \
  -d "{\"name\":\"Saga $S\",\"description\":\"x\",\"model\":\"SG-$S\",\"mrpPrice\":1000,\"salePrice\":900,\"gstValue\":18,\"status\":1,\"discount\":0,\"images\":[],\"hasVariant\":true}" | jdata id)
VID=$(curl -s -X POST "$BASE_URL/catalog-service/variant/add/$PID" "${AUTH[@]}" \
  -d '{"condition1Name":"Storage","condition1Value":"256GB","condition2Name":"Color","condition2Value":"Black","mrpPrice":1000,"salePrice":900}' | jdata id)
curl -s -o /dev/null -X POST "$BASE_URL/inventory-service/stock" "${AUTH[@]}" \
  -d "{\"productId\":\"$PID\",\"variantId\":\"$VID\",\"quantity\":100}"
AID=$(curl -s -X POST "$BASE_URL/customer-service/address" "${AUTH[@]}" \
  -d '{"home":"2B","streetLocality":"Test Ave","city":"LoadCity","state":"LC","country":"IN","zipCode":400001,"landmark":"Near"}' | jdata id)
[ -n "$PID" ] && [ -n "$VID" ] && [ -n "$AID" ] || { fail "seed failed (PID=$PID VID=$VID AID=$AID)"; exit 1; }
info "seeded product=$PID variant=$VID stock=100 address=$AID"

# ── Control: a healthy saga completes and clears the cart ────────────────────
info "control — running a healthy saga (simulatePaymentFailure=false)"
add_to_cart 1
OID=$(start_saga false)
ST=$(poll_saga "$OID")
[ "$ST" = "COMPLETED" ] && pass "healthy saga reached COMPLETED" || fail "healthy saga ended in '$ST' (expected COMPLETED)"
[ "$(cart_count)" = "0" ] && pass "cart cleared after a completed saga" || fail "cart not cleared after COMPLETED (count=$(cart_count))"

# ── Compensation: a failing saga compensates, releases stock, keeps the cart ──
info "compensation — running a saga with simulatePaymentFailure=true"
RES_BEFORE=$(reserved_qty)
add_to_cart 2
CART_BEFORE=$(cart_count)
OID=$(start_saga true)
info "saga orderId=$OID (reserved before=$RES_BEFORE, cart before=$CART_BEFORE)"
ST=$(poll_saga "$OID")
[ "$ST" = "COMPENSATED" ] && pass "failing saga reached COMPENSATED" || fail "failing saga ended in '$ST' (expected COMPENSATED)"

RES_AFTER=$(reserved_qty)
[ "$RES_AFTER" = "$RES_BEFORE" ] && pass "reserved stock released back to inventory ($RES_BEFORE -> $RES_AFTER)" \
  || fail "reserved stock not released (before=$RES_BEFORE after=$RES_AFTER)"

CART_AFTER=$(cart_count)
[ "$CART_AFTER" = "$CART_BEFORE" ] && [ "$CART_AFTER" -gt 0 ] && pass "cart preserved after compensation (count=$CART_AFTER)" \
  || fail "cart not preserved after compensation (before=$CART_BEFORE after=$CART_AFTER)"

# ── Result ───────────────────────────────────────────────────────────────────
echo
if [ "$FAILURES" -eq 0 ]; then
  pass "saga compensation validation passed"
  exit 0
else
  fail "saga compensation validation failed with $FAILURES error(s)"
  exit 1
fi
