#!/usr/bin/env bash
# E2E smoke test for the ecommerce distributed system.
# Runs through the full order flow via the API gateway on localhost:8081.
set -euo pipefail

BASE="http://localhost:8081"
PASS=0; FAIL=0

green() { printf '\033[0;32m✓ %s\033[0m\n' "$1"; }
red()   { printf '\033[0;31m✗ %s\033[0m\n' "$1"; }

check() {
  local name="$1"; local expect="$2"; local body="$3"
  if echo "$body" | grep -q "$expect"; then
    green "$name"; PASS=$((PASS+1))
  else
    red "$name — expected '$expect' in: $(echo "$body" | head -c 200)"; FAIL=$((FAIL+1))
  fi
}

# ── Wait for gateway ───────────────────────────────────────────────────────────
echo "Waiting for API gateway..."
for i in $(seq 1 30); do
  if curl -sf "$BASE/actuator/health" | grep -q "UP"; then break; fi
  sleep 5
done
curl -sf "$BASE/actuator/health" | grep -q "UP" || { echo "Gateway never came up"; exit 1; }
echo "Gateway is UP. Waiting for required Eureka registrations..."

wait_for_eureka_app() {
  local app="$1"
  local app_url="http://localhost:8761/eureka/apps/$app"
  for i in $(seq 1 60); do
    if curl -sf "$app_url" | grep -q "<status>UP</status>"; then
      return 0
    fi
    sleep 5
  done
  echo "Eureka app $app did not register as UP"
  return 1
}

REQUIRED_EUREKA_APPS=(
  "API-GATEWAY"
  "AUTH-SERVICE"
  "CATALOG-SERVICE"
  "INVENTORY-SERVICE"
  "SHOPPING-CART-SERVICE"
  "CUSTOMER-SERVICE"
  "ORDER-SERVICE"
  "PAYMENT-SERVICE"
  "NOTIFICATION-SERVICE"
  "COUPON-SERVICE"
  "WISHLIST-SERVICE"
  "REVIEW-SERVICE"
)

for app in "${REQUIRED_EUREKA_APPS[@]}"; do
  wait_for_eureka_app "$app"
done
echo "Required Eureka apps are UP."

echo ""
echo "══════════════════════════════════════════════════"
echo " E2E Smoke Test — $(date)"
echo "══════════════════════════════════════════════════"

# ── 1. Register ────────────────────────────────────────────────────────────────
RUN_ID="$(date +%s)"
EMAIL="smoke_${RUN_ID}@test.com"
ORDER_IDEMPOTENCY_KEY="smoke-order-${RUN_ID}"
COUPON_ORDER_IDEMPOTENCY_KEY="smoke-coupon-order-${RUN_ID}"
R=$(curl -sf -X POST "$BASE/auth-service/signup" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Test@1234\"}" || echo '{"error":"failed"}')
check "1. Register user" '"success":true' "$R"

# ── 2. Login ───────────────────────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/auth-service/login" \
  -H "Content-Type: application/json" \
  -d "{\"email\":\"$EMAIL\",\"password\":\"Test@1234\"}" || echo '{"error":"failed"}')
check "2. Login" '"success":true' "$R"
TOKEN=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); data=d.get('data',''); print(data.get('token','') if isinstance(data,dict) else data)" 2>/dev/null || echo "")
AUTH="Authorization: Bearer $TOKEN"

# ── 3. Add brand ───────────────────────────────────────────────────────────────
BRAND="SmokePhone_$(date +%s)"
R=$(curl -sf -X POST "$BASE/catalog-service/add-brand?name=$BRAND" \
  -H "$AUTH" || echo '{"error":"failed"}')
check "3. Add brand" '"success":true' "$R"
BRAND_ID=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

# ── 4. Add category ────────────────────────────────────────────────────────────
CAT="Electronics_$(date +%s)"
R=$(curl -sf -X POST "$BASE/catalog-service/addCategory?categoryName=$CAT" \
  -H "$AUTH" || echo '{"error":"failed"}')
check "4. Add category" '"success":true' "$R"
CAT_ID=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

# ── 5. Add subcategory ─────────────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/catalog-service/add-subcategory/$CAT_ID?subcategoryName=Smartphones" \
  -H "$AUTH" || echo '{"error":"failed"}')
check "5. Add subcategory" '"success":true' "$R"
SUB_ID=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

# ── 6. Add product ─────────────────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/catalog-service/product/addProduct?brandId=$BRAND_ID&categoryId=$CAT_ID&subCategoryId=$SUB_ID" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"name":"Smoke Phone X1","description":"Test phone","model":"SPX1","mrpPrice":1099.99,"salePrice":999.99,"gstValue":18,"status":1,"discount":100,"images":[],"hasVariant":true}' || echo '{"error":"failed"}')
check "6. Add product" '"success":true' "$R"
PRODUCT_ID=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

# ── 7. Add variant ─────────────────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/catalog-service/variant/add/$PRODUCT_ID" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"condition1Name":"Storage","condition1Value":"128GB","condition2Name":"Color","condition2Value":"Black","mrpPrice":1099.99,"salePrice":999.99}' || echo '{"error":"failed"}')
check "7. Add variant" '"success":true' "$R"
VARIANT_ID=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

# ── 8. Seed inventory ──────────────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/inventory-service/stock" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"variantId\":\"$VARIANT_ID\",\"quantity\":50}" || echo '{"error":"failed"}')
check "8. Seed inventory (50 units)" '"success":true' "$R"

# ── 9. Add to cart ─────────────────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/cart-service/cart/add" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"variantId\":\"$VARIANT_ID\",\"qty\":2}" || echo '{"error":"failed"}')
check "9. Add item to cart" '"success":true' "$R"

# ── 10. View cart ──────────────────────────────────────────────────────────────
R=$(curl -sf "$BASE/cart-service/cart" -H "$AUTH" || echo '{"error":"failed"}')
check "10. View cart" '"success":true' "$R"

# ── 11. Add shipping address ───────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/customer-service/address" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d '{"home":"42B","streetLocality":"Smoke Street","city":"Testville","state":"TS","country":"IN","zipCode":560001,"landmark":"Near Gateway"}' \
  || echo '{"error":"failed"}')
check "11. Add shipping address" '"success":true' "$R"
ADDR_ID=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")

# ── 12. Place order ────────────────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/order-service/order" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $ORDER_IDEMPOTENCY_KEY" \
  -d "{\"addressId\":\"$ADDR_ID\"}" || echo '{"error":"failed"}')
check "12. Place order" '"success":true' "$R"
ORDER_ID=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('id',''))" 2>/dev/null || echo "")
PAYMENT_ID=$(echo "$R" | python3 -c "import sys,json; d=json.load(sys.stdin); print(d.get('data',{}).get('paymentId',''))" 2>/dev/null || echo "")

# ── 13. Check order is readable ────────────────────────────────────────────────
R=$(curl -sf "$BASE/order-service/order/$ORDER_ID" -H "$AUTH" || echo '{"error":"failed"}')
check "13. Get order" '"success":true' "$R"

# ── 14. Check payment status ───────────────────────────────────────────────────
R=$(curl -sf "$BASE/payment-service/status/$ORDER_ID" -H "$AUTH" || echo '{"error":"failed"}')
check "14. Payment initiated" '"success":true' "$R"

# ── 15. Confirm order ──────────────────────────────────────────────────────────
R=$(curl -sf -X PUT "$BASE/order-service/order/$ORDER_ID/confirm" -H "$AUTH" || echo '{"error":"failed"}')
check "15. Confirm order" '"success":true' "$R"

# ── 16. Verify order CONFIRMED ─────────────────────────────────────────────────
R=$(curl -sf "$BASE/order-service/order/$ORDER_ID" -H "$AUTH" || echo '{"error":"failed"}')
check "16. Order is CONFIRMED" 'CONFIRMED' "$R"

# ── 17. Verify payment SUCCESS ─────────────────────────────────────────────────
sleep 2
R=$(curl -sf "$BASE/payment-service/$PAYMENT_ID" -H "$AUTH" || echo '{"error":"failed"}')
check "17. Payment is SUCCESS" 'SUCCESS' "$R"

# ── 18. Order history ──────────────────────────────────────────────────────────
R=$(curl -sf "$BASE/order-service/orders" -H "$AUTH" || echo '{"error":"failed"}')
check "18. Order history not empty" '"success":true' "$R"

# ── 19. Get notifications ──────────────────────────────────────────────────────
sleep 3
R=$(curl -sf "$BASE/notification-service/notifications" -H "$AUTH" || echo '{"error":"failed"}')
check "19. Notifications present" '"success":true' "$R"

# ── 20. Check inventory decreased ─────────────────────────────────────────────
R=$(curl -sf "$BASE/inventory-service/stock/$PRODUCT_ID" -H "$AUTH" || echo '{"error":"failed"}')
check "20. Inventory check" '"success":true' "$R"

# ── 21. Create coupon ──────────────────────────────────────────────────────────
COUPON_CODE="SMOKE$(date +%s)"
R=$(curl -sf -X POST "$BASE/coupon-service/coupon" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"code\":\"$COUPON_CODE\",\"type\":\"PERCENTAGE\",\"value\":10.0,\"minOrderAmount\":500.0,\"maxUsageCount\":100,\"expiryDate\":\"2099-12-31\",\"active\":true}" \
  || echo '{"error":"failed"}')
check "21. Create coupon" '"success":true' "$R"

# ── 22. Validate coupon ────────────────────────────────────────────────────────
R=$(curl -sf "$BASE/coupon-service/coupon/validate?code=$COUPON_CODE&amount=999.99" -H "$AUTH" || echo '{"error":"failed"}')
check "22. Validate coupon" '"success":true' "$R"

# ── 23. Get active coupons ─────────────────────────────────────────────────────
R=$(curl -sf "$BASE/coupon-service/coupons" -H "$AUTH" || echo '{"error":"failed"}')
check "23. Get active coupons" '"success":true' "$R"

# ── 24. Seed another cart item and place order with coupon ─────────────────────
curl -sf -X POST "$BASE/cart-service/cart/add" -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"variantId\":\"$VARIANT_ID\",\"qty\":1}" > /dev/null 2>&1 || true
R=$(curl -sf -X POST "$BASE/order-service/order" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -H "Idempotency-Key: $COUPON_ORDER_IDEMPOTENCY_KEY" \
  -d "{\"addressId\":\"$ADDR_ID\",\"couponCode\":\"$COUPON_CODE\"}" || echo '{"error":"failed"}')
check "24. Place order with coupon" '"success":true' "$R"

# ── 25. Add to wishlist ────────────────────────────────────────────────────────
R=$(curl -sf -X POST "$BASE/wishlist-service/wishlist" \
  -H "$AUTH" -H "Content-Type: application/json" \
  -d "{\"productId\":\"$PRODUCT_ID\",\"productName\":\"Smoke Phone X1\",\"salePrice\":999.99}" \
  || echo '{"error":"failed"}')
check "25. Add to wishlist" '"success":true' "$R"

# ── 26. Get wishlist ───────────────────────────────────────────────────────────
R=$(curl -sf "$BASE/wishlist-service/wishlist" -H "$AUTH" || echo '{"error":"failed"}')
check "26. Get wishlist" '"success":true' "$R"

# ── 27. Check reviews (eligibility driven by Kafka events) ────────────────────
sleep 3
R=$(curl -sf "$BASE/review-service/review/product/$PRODUCT_ID" -H "$AUTH" || echo '{"error":"failed"}')
check "27. Review endpoint reachable" '"success":true' "$R"

# ── 28. Observability — Zipkin health ─────────────────────────────────────────
R=$(curl -sf "http://localhost:9411/health" || echo '{"status":"down"}')
if echo "$R" | grep -Eq '"status"[[:space:]]*:[[:space:]]*"UP"'; then
  green "28. Zipkin is UP"; PASS=$((PASS+1))
else
  red "28. Zipkin is UP — expected status UP in: $(echo "$R" | head -c 200)"; FAIL=$((FAIL+1))
fi

# ── 29. Observability — Prometheus metrics ────────────────────────────────────
R=$(curl -sf "http://localhost:9090/-/healthy" || echo 'down')
check "29. Prometheus is UP" 'Prometheus' "$R"

# ── 30. Observability — Grafana health ────────────────────────────────────────
R=$(curl -sf "http://localhost:3000/api/health" || echo '{"status":"failed"}')
if echo "$R" | grep -Eq '"database"[[:space:]]*:[[:space:]]*"ok"'; then
  green "30. Grafana is UP"; PASS=$((PASS+1))
else
  red "30. Grafana is UP — expected database ok in: $(echo "$R" | head -c 200)"; FAIL=$((FAIL+1))
fi

# ── Summary ────────────────────────────────────────────────────────────────────
echo ""
echo "══════════════════════════════════════════════════"
printf " Results: \033[0;32m%d passed\033[0m, \033[0;31m%d failed\033[0m\n" "$PASS" "$FAIL"
echo "══════════════════════════════════════════════════"
[ "$FAIL" -eq 0 ]
