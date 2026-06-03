#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8081}"
EUREKA_URL="${EUREKA_URL:-http://localhost:8761}"
BASE_URL="${BASE_URL%/}"
EUREKA_URL="${EUREKA_URL%/}"
LOCAL_SERVICE_HOST="${LOCAL_SERVICE_HOST:-localhost}"
POSTMAN_ENV="${POSTMAN_ENV:-postman/Ecommerce-Local.postman_environment.json}"
RUN_POSTMAN="${RUN_POSTMAN:-1}"
RUN_MAVEN_TESTS="${RUN_MAVEN_TESTS:-1}"
RUN_DIRECT_HEALTH_CHECKS="${RUN_DIRECT_HEALTH_CHECKS:-1}"
RUN_API_COLLECTION="${RUN_API_COLLECTION:-1}"
WAIT_RETRIES="${WAIT_RETRIES:-60}"
WAIT_SLEEP_SECONDS="${WAIT_SLEEP_SECONDS:-5}"
GATEWAY_DISCOVERY_STABILIZE_SECONDS="${GATEWAY_DISCOVERY_STABILIZE_SECONDS:-10}"
DEFAULT_MAVEN_JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home"
MAVEN_TEST_SYSTEM_PROPS=(
  "-Dspring.config.name=application-test"
  "-Dspring.cloud.config.enabled=false"
  "-Dspring.cloud.config.import-check.enabled=false"
  "-Dspring.config.import="
)

MAVEN_TEST_MODULES=(
  api-gateway
  auth-server
  catalog-service
  cloud-config
  coupon-service
  customer-service
  inventory-service
  notification-service
  order-service
  payment-service
  review-service
  service-discovery
  shopping-cart-service
  spring-server
  wishlist-service
)

REQUIRED_EUREKA_APPS=(
  API-GATEWAY
  CONFIGSERVER
  AUTH-SERVICE
  CATALOG-SERVICE
  INVENTORY-SERVICE
  SHOPPING-CART-SERVICE
  CUSTOMER-SERVICE
  ORDER-SERVICE
  PAYMENT-SERVICE
  NOTIFICATION-SERVICE
  COUPON-SERVICE
  WISHLIST-SERVICE
  REVIEW-SERVICE
)

SERVICE_HEALTH_CHECKS=(
  "API gateway|$BASE_URL/actuator/health"
  "cloud-config|http://$LOCAL_SERVICE_HOST:${CLOUD_CONFIG_PORT:-8071}/actuator/health"
  "auth-service|http://$LOCAL_SERVICE_HOST:${AUTH_SERVICE_PORT:-8083}/actuator/health"
  "customer-service|http://$LOCAL_SERVICE_HOST:${CUSTOMER_SERVICE_PORT:-8082}/actuator/health"
  "catalog-service|http://$LOCAL_SERVICE_HOST:${CATALOG_SERVICE_PORT:-8084}/actuator/health"
  "shopping-cart-service|http://$LOCAL_SERVICE_HOST:${SHOPPING_CART_SERVICE_PORT:-8086}/actuator/health"
  "payment-service|http://$LOCAL_SERVICE_HOST:${PAYMENT_SERVICE_PORT:-8085}/actuator/health"
  "inventory-service|http://$LOCAL_SERVICE_HOST:${INVENTORY_SERVICE_PORT:-8088}/actuator/health"
  "order-service|http://$LOCAL_SERVICE_HOST:${ORDER_SERVICE_PORT:-8092}/actuator/health"
  "notification-service|http://$LOCAL_SERVICE_HOST:${NOTIFICATION_SERVICE_PORT:-8087}/actuator/health"
  "coupon-service|http://$LOCAL_SERVICE_HOST:${COUPON_SERVICE_PORT:-8089}/actuator/health"
  "wishlist-service|http://$LOCAL_SERVICE_HOST:${WISHLIST_SERVICE_PORT:-8091}/actuator/health"
  "review-service|http://$LOCAL_SERVICE_HOST:${REVIEW_SERVICE_PORT:-8090}/actuator/health"
)

GATEWAY_ROUTE_CHECKS=(
  "catalog-service route|$BASE_URL/catalog-service/product/list"
)

log() {
  printf '\n==> %s\n' "$1"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
}

print_url_diagnostics() {
  local name="$1"
  local url="$2"

  echo "Last $name response from $url:" >&2
  if ! curl -sS -i --max-time 10 "$url" >&2; then
    echo "  <unreachable>" >&2
  fi
}

print_eureka_registry_snapshot() {
  local registry

  echo "Eureka registry snapshot from $EUREKA_URL/eureka/apps:" >&2
  if ! registry="$(curl -fs "$EUREKA_URL/eureka/apps" 2>/dev/null)"; then
    echo "  <unavailable>" >&2
    return 0
  fi

  printf '%s\n' "$registry" | node -e '
const fs = require("fs");
const xml = fs.readFileSync(0, "utf8");
const apps = [...xml.matchAll(/<application>([\s\S]*?)<\/application>/g)]
  .map(([, appXml]) => {
    const name = (appXml.match(/<name>([^<]+)<\/name>/) || [null, "<unknown>"])[1];
    const statuses = [...appXml.matchAll(/<status>([^<]+)<\/status>/g)].map((match) => match[1]);
    return `${name}: ${statuses.length ? statuses.join(", ") : "NO_INSTANCES"}`;
  })
  .sort();

if (apps.length === 0) {
  console.error("  <none>");
} else {
  for (const app of apps) {
    console.error(`  ${app}`);
  }
}
' || echo "  <failed to parse registry snapshot>" >&2
}

wait_for_url() {
  local name="$1"
  local url="$2"

  for _ in $(seq 1 "$WAIT_RETRIES"); do
    if curl -sf "$url" >/dev/null; then
      return 0
    fi
    sleep "$WAIT_SLEEP_SECONDS"
  done

  echo "$name did not become reachable at $url" >&2
  print_url_diagnostics "$name" "$url"
  return 1
}

wait_for_eureka_app() {
  local app="$1"
  local app_url="$EUREKA_URL/eureka/apps/$app"

  for _ in $(seq 1 "$WAIT_RETRIES"); do
    if curl -sf "$app_url" | grep -q "<status>UP</status>"; then
      return 0
    fi
    sleep "$WAIT_SLEEP_SECONDS"
  done

  echo "Eureka app $app did not register as UP at $app_url" >&2
  print_eureka_registry_snapshot
  return 1
}

wait_for_health_check() {
  local check="$1"
  local name="${check%%|*}"
  local url="${check#*|}"

  wait_for_url "$name" "$url"
}

wait_for_gateway_route() {
  local check="$1"
  local name="${check%%|*}"
  local url="${check#*|}"
  local status

  for _ in $(seq 1 "$WAIT_RETRIES"); do
    status="$(curl -sS -o /dev/null -w "%{http_code}" "$url" || true)"
    if [[ "$status" == "200" ]]; then
      return 0
    fi
    sleep "$WAIT_SLEEP_SECONDS"
  done

  echo "Gateway route $name did not become ready at $url; last status was ${status:-<none>}" >&2
  print_url_diagnostics "$name" "$url"
  print_eureka_registry_snapshot
  return 1
}

expect_http_status() {
  local name="$1"
  local url="$2"
  local expected="$3"
  local actual

  actual="$(curl -sS -o /dev/null -w "%{http_code}" "$url")"
  if [[ "$actual" != "$expected" ]]; then
    echo "$name returned HTTP $actual at $url; expected $expected" >&2
    return 1
  fi
}

verify_gateway_standard_routes() {
  log "Verifying gateway standard routes"
  expect_http_status "Standard cart route auth guard" "$BASE_URL/cart-service/cart" "401"
  expect_http_status "Raw shopping-cart service route" "$BASE_URL/shopping-cart-service/cart" "404"
}

configure_maven_java() {
  local preferred_java_home="${MAVEN_JAVA_HOME:-$DEFAULT_MAVEN_JAVA_HOME}"

  if [[ -n "${MAVEN_JAVA_HOME:-}" && ! -d "$MAVEN_JAVA_HOME" ]]; then
    echo "MAVEN_JAVA_HOME does not exist: $MAVEN_JAVA_HOME" >&2
    exit 1
  fi

  if [[ -d "$preferred_java_home" ]]; then
    export JAVA_HOME="$preferred_java_home"
  fi
}

configure_maven_java

if [[ "$RUN_MAVEN_TESTS" == "1" && -n "${JAVA_HOME:-}" ]]; then
  log "Using JAVA_HOME=$JAVA_HOME for Maven"
fi

log "Checking required local tools"
require_command node
if [[ "$RUN_POSTMAN" == "1" ]]; then
  require_command curl
  require_command grep
  require_command newman
fi
if [[ "$RUN_MAVEN_TESTS" == "1" ]]; then
  require_command mvn
fi

log "Validating Postman collections"
node scripts/validate-postman.js

log "Validating service configuration"
node scripts/validate-service-config.js

if [[ "$RUN_POSTMAN" == "1" ]]; then
  if [[ "$RUN_DIRECT_HEALTH_CHECKS" == "1" ]]; then
    log "Waiting for local service health checks"
    for check in "${SERVICE_HEALTH_CHECKS[@]}"; do
      wait_for_health_check "$check"
    done
  else
    log "Waiting for gateway health"
    wait_for_url "API gateway" "$BASE_URL/actuator/health"
  fi

  log "Waiting for Eureka registrations"
  for app in "${REQUIRED_EUREKA_APPS[@]}"; do
    wait_for_eureka_app "$app"
  done
  if [[ "$GATEWAY_DISCOVERY_STABILIZE_SECONDS" -gt 0 ]]; then
    log "Allowing gateway discovery cache to refresh"
    sleep "$GATEWAY_DISCOVERY_STABILIZE_SECONDS"
  fi
  log "Waiting for API gateway service routes"
  for check in "${GATEWAY_ROUTE_CHECKS[@]}"; do
    wait_for_gateway_route "$check"
  done
  verify_gateway_standard_routes

  if [[ "$RUN_API_COLLECTION" == "1" ]]; then
    log "Running Postman API reference flow"
    newman run postman/Ecommerce-API.postman_collection.json \
      -e "$POSTMAN_ENV" \
      --bail failure \
      --timeout-request 30000 \
      --delay-request 100
  fi

  log "Running Postman data seed flow"
  newman run postman/Ecommerce-DataSeed.postman_collection.json \
    -e "$POSTMAN_ENV" \
    --bail failure \
    --timeout-request 30000 \
    --delay-request 100

  log "Running Postman application E2E flow"
  newman run postman/Ecommerce-E2E-Complete.postman_collection.json \
    -e "$POSTMAN_ENV" \
    --bail failure \
    --timeout-request 30000 \
    --delay-request 100
fi

if [[ "$RUN_MAVEN_TESTS" == "1" ]]; then
  log "Installing platform commons"
  mvn -B -f platform-commons/pom.xml install -DskipTests

  for module in "${MAVEN_TEST_MODULES[@]}"; do
    log "Running Maven tests for $module"
    mvn -B -f "$module/pom.xml" test \
      -DfailIfNoTests=false \
      "${MAVEN_TEST_SYSTEM_PROPS[@]}" \
      "-Dtest=!*ApplicationTests"
  done
fi

log "Local verification completed"
