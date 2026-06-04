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
RUN_PLATFORM_ENDPOINT_CHECKS="${RUN_PLATFORM_ENDPOINT_CHECKS:-1}"
RUN_API_COLLECTION="${RUN_API_COLLECTION:-1}"
RUN_DATA_SEED_COLLECTION="${RUN_DATA_SEED_COLLECTION:-1}"
RUN_E2E_COLLECTION="${RUN_E2E_COLLECTION:-1}"
WAIT_RETRIES="${WAIT_RETRIES:-60}"
WAIT_SLEEP_SECONDS="${WAIT_SLEEP_SECONDS:-5}"
GATEWAY_DISCOVERY_STABILIZE_SECONDS="${GATEWAY_DISCOVERY_STABILIZE_SECONDS:-10}"
DEFAULT_MAVEN_JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home"
RUN_ANY_POSTMAN_COLLECTION=0
if [[ "$RUN_API_COLLECTION" == "1" || "$RUN_DATA_SEED_COLLECTION" == "1" || "$RUN_E2E_COLLECTION" == "1" ]]; then
  RUN_ANY_POSTMAN_COLLECTION=1
fi
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

PLATFORM_ENDPOINT_CHECKS=(
  "Kafka UI|http://$LOCAL_SERVICE_HOST:${KAFKA_UI_PORT:-8080}"
  "Zipkin|http://$LOCAL_SERVICE_HOST:${ZIPKIN_PORT:-9411}/health"
  "Prometheus|http://$LOCAL_SERVICE_HOST:${PROMETHEUS_PORT:-9090}/-/healthy"
  "Grafana|http://$LOCAL_SERVICE_HOST:${GRAFANA_PORT:-3000}/api/health"
)

GATEWAY_ROUTE_CHECKS=(
  "catalog-service route|$BASE_URL/catalog-service/product/list"
)

GATEWAY_AUTH_GUARD_CHECKS=(
  "auth-service protected route|$BASE_URL/auth-service/updatePassword"
  "catalog-service protected route|$BASE_URL/catalog-service/getBrands"
  "cart-service protected route|$BASE_URL/cart-service/cart"
  "customer-service protected route|$BASE_URL/customer-service/address"
  "payment-service protected route|$BASE_URL/payment-service/initiate"
  "inventory-service protected route|$BASE_URL/inventory-service/stock"
  "notification-service protected route|$BASE_URL/notification-service/notifications"
  "order-service protected route|$BASE_URL/order-service/order"
  "coupon-service protected route|$BASE_URL/coupon-service/coupon"
  "review-service protected route|$BASE_URL/review-service/review"
  "wishlist-service protected route|$BASE_URL/wishlist-service/wishlist"
)

RAW_GATEWAY_ROUTE_CHECKS=(
  "raw shopping-cart service route|$BASE_URL/shopping-cart-service/cart"
)

GATEWAY_ROUTE_TABLE_CHECKS=(
  "auth-service|lb://auth-service|/auth-service/**"
  "catalog-service|lb://catalog-service|/catalog-service/**"
  "cart-service|lb://shopping-cart-service|/cart-service/**"
  "customer-service|lb://customer-service|/customer-service/**"
  "payment-service|lb://payment-service|/payment-service/**"
  "inventory-service|lb://inventory-service|/inventory-service/**"
  "notification-service|lb://notification-service|/notification-service/**"
  "order-service|lb://order-service|/order-service/**"
  "coupon-service|lb://coupon-service|/coupon-service/**"
  "review-service|lb://review-service|/review-service/**"
  "wishlist-service|lb://wishlist-service|/wishlist-service/**"
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

eureka_registry_has_clean_required_apps() {
  local registry
  local required_apps
  local print_errors="${1:-0}"

  if ! registry="$(curl -fs "$EUREKA_URL/eureka/apps" 2>/dev/null)"; then
    return 1
  fi

  printf -v required_apps '%s\n' "${REQUIRED_EUREKA_APPS[@]}"
  EUREKA_REGISTRY_XML="$registry" REQUIRED_EUREKA_APPS_TEXT="$required_apps" PRINT_EUREKA_ERRORS="$print_errors" node <<'NODE'
const xml = process.env.EUREKA_REGISTRY_XML || "";
const requiredApps = (process.env.REQUIRED_EUREKA_APPS_TEXT || "")
  .split(/\n/)
  .map((app) => app.trim())
  .filter(Boolean);
const printErrors = process.env.PRINT_EUREKA_ERRORS === "1";

const apps = new Map();
for (const [, appXml] of xml.matchAll(/<application>([\s\S]*?)<\/application>/g)) {
  const name = (appXml.match(/<name>([^<]+)<\/name>/) || [null, ""])[1];
  const statuses = [...appXml.matchAll(/<status>([^<]+)<\/status>/g)].map((match) => match[1]);
  apps.set(name, statuses);
}

const errors = [];
for (const app of requiredApps) {
  const statuses = apps.get(app);
  if (!statuses || statuses.length === 0) {
    errors.push(`${app}: missing from aggregate registry`);
  } else if (!statuses.some((status) => status === "UP")) {
    errors.push(`${app}: no UP instances (${statuses.join(", ")})`);
  } else if (statuses.some((status) => status !== "UP")) {
    errors.push(`${app}: non-UP instances still visible (${statuses.join(", ")})`);
  }
}

if (errors.length > 0) {
  if (printErrors) {
    for (const error of errors) {
      console.error(error);
    }
  }
  process.exit(1);
}
NODE
}

wait_for_eureka_registry_clean() {
  for _ in $(seq 1 "$WAIT_RETRIES"); do
    if eureka_registry_has_clean_required_apps 0; then
      return 0
    fi
    sleep "$WAIT_SLEEP_SECONDS"
  done

  echo "Eureka aggregate registry did not converge to only UP required app instances" >&2
  eureka_registry_has_clean_required_apps 1 || true
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

  actual="$(curl -sS -o /dev/null -w "%{http_code}" "$url" || true)"
  if [[ "$actual" != "$expected" ]]; then
    echo "$name returned HTTP $actual at $url; expected $expected" >&2
    print_url_diagnostics "$name" "$url"
    return 1
  fi
}

expect_http_status_check() {
  local check="$1"
  local expected="$2"
  local name="${check%%|*}"
  local url="${check#*|}"

  expect_http_status "$name" "$url" "$expected"
}

verify_gateway_standard_routes() {
  log "Verifying gateway standard routes"
  for check in "${GATEWAY_AUTH_GUARD_CHECKS[@]}"; do
    expect_http_status_check "$check" "401"
  done
  for check in "${RAW_GATEWAY_ROUTE_CHECKS[@]}"; do
    expect_http_status_check "$check" "404"
  done
}

verify_gateway_route_table() {
  local routes_json
  local route_specs

  if ! routes_json="$(curl -fs "$BASE_URL/actuator/gateway/routes")"; then
    echo "Gateway route table did not become readable at $BASE_URL/actuator/gateway/routes" >&2
    print_url_diagnostics "API gateway route table" "$BASE_URL/actuator/gateway/routes"
    return 1
  fi

  printf -v route_specs '%s\n' "${GATEWAY_ROUTE_TABLE_CHECKS[@]}"
  GATEWAY_ROUTES_JSON="$routes_json" GATEWAY_ROUTE_SPECS="$route_specs" node <<'NODE'
const routes = JSON.parse(process.env.GATEWAY_ROUTES_JSON || "[]");
const specs = (process.env.GATEWAY_ROUTE_SPECS || "")
  .split(/\n/)
  .map((line) => line.trim())
  .filter(Boolean)
  .map((line) => {
    const [id, uri, path] = line.split("|");
    return { id, uri, path };
  });

const expectedIds = new Set(specs.map((spec) => spec.id));
const routesById = new Map(routes.map((route) => [route.route_id, route]));
const errors = [];

for (const spec of specs) {
  const route = routesById.get(spec.id);
  if (!route) {
    errors.push(`missing route ${spec.id}`);
    continue;
  }
  if (route.uri !== spec.uri) {
    errors.push(`${spec.id} uri ${route.uri} did not match ${spec.uri}`);
  }
  if (!String(route.predicate || "").includes(spec.path)) {
    errors.push(`${spec.id} predicate ${route.predicate} did not include ${spec.path}`);
  }
}

for (const route of routes) {
  if (!expectedIds.has(route.route_id)) {
    errors.push(`unexpected route ${route.route_id}`);
  }
  if (route.route_id === "shopping-cart-service"
      || String(route.predicate || "").includes("/shopping-cart-service/**")) {
    errors.push("raw shopping-cart-service route is exposed");
  }
}

if (errors.length) {
  for (const error of errors) {
    console.error(error);
  }
  process.exit(1);
}
NODE
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
  if [[ "$RUN_ANY_POSTMAN_COLLECTION" == "1" ]]; then
    require_command newman
  fi
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

  if [[ "$RUN_PLATFORM_ENDPOINT_CHECKS" == "1" ]]; then
    log "Waiting for platform endpoints"
    for check in "${PLATFORM_ENDPOINT_CHECKS[@]}"; do
      wait_for_health_check "$check"
    done
  fi

  log "Waiting for Eureka registrations"
  for app in "${REQUIRED_EUREKA_APPS[@]}"; do
    wait_for_eureka_app "$app"
  done
  log "Verifying Eureka aggregate registry"
  wait_for_eureka_registry_clean
  if [[ "$GATEWAY_DISCOVERY_STABILIZE_SECONDS" -gt 0 ]]; then
    log "Allowing gateway discovery cache to refresh"
    sleep "$GATEWAY_DISCOVERY_STABILIZE_SECONDS"
  fi
  log "Waiting for API gateway service routes"
  for check in "${GATEWAY_ROUTE_CHECKS[@]}"; do
    wait_for_gateway_route "$check"
  done
  log "Verifying API gateway route table"
  verify_gateway_route_table
  verify_gateway_standard_routes

  if [[ "$RUN_API_COLLECTION" == "1" ]]; then
    log "Running Postman API reference flow"
    newman run postman/Ecommerce-API.postman_collection.json \
      -e "$POSTMAN_ENV" \
      --bail failure \
      --timeout-request 30000 \
      --delay-request 100
  fi

  if [[ "$RUN_DATA_SEED_COLLECTION" == "1" ]]; then
    log "Running Postman data seed flow"
    newman run postman/Ecommerce-DataSeed.postman_collection.json \
      -e "$POSTMAN_ENV" \
      --bail failure \
      --timeout-request 30000 \
      --delay-request 100
  fi

  if [[ "$RUN_E2E_COLLECTION" == "1" ]]; then
    log "Running Postman application E2E flow"
    newman run postman/Ecommerce-E2E-Complete.postman_collection.json \
      -e "$POSTMAN_ENV" \
      --bail failure \
      --timeout-request 30000 \
      --delay-request 100
  fi
fi

if [[ "$RUN_MAVEN_TESTS" == "1" ]]; then
  log "Installing platform commons"
  mvn -B -f platform-commons/pom.xml clean install -DskipTests

  for module in "${MAVEN_TEST_MODULES[@]}"; do
    log "Running Maven tests for $module"
    mvn -B -f "$module/pom.xml" clean test \
      -DfailIfNoTests=false \
      "${MAVEN_TEST_SYSTEM_PROPS[@]}" \
      "-Dtest=!*ApplicationTests"
  done
fi

log "Local verification completed"
