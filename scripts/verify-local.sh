#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

BASE_URL="${BASE_URL:-http://localhost:8081}"
EUREKA_URL="${EUREKA_URL:-http://localhost:8761}"
POSTMAN_ENV="${POSTMAN_ENV:-postman/Ecommerce-Local.postman_environment.json}"
RUN_POSTMAN="${RUN_POSTMAN:-1}"
RUN_MAVEN_TESTS="${RUN_MAVEN_TESTS:-1}"
WAIT_RETRIES="${WAIT_RETRIES:-60}"
WAIT_SLEEP_SECONDS="${WAIT_SLEEP_SECONDS:-5}"
GATEWAY_DISCOVERY_STABILIZE_SECONDS="${GATEWAY_DISCOVERY_STABILIZE_SECONDS:-10}"
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

log() {
  printf '\n==> %s\n' "$1"
}

require_command() {
  if ! command -v "$1" >/dev/null 2>&1; then
    echo "Missing required command: $1" >&2
    exit 1
  fi
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

  echo "Eureka app $app did not register as UP" >&2
  return 1
}

if [[ -z "${JAVA_HOME:-}" && -d /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home ]]; then
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
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
  log "Waiting for gateway and Eureka registrations"
  wait_for_url "API gateway" "$BASE_URL/actuator/health"
  for app in "${REQUIRED_EUREKA_APPS[@]}"; do
    wait_for_eureka_app "$app"
  done
  if [[ "$GATEWAY_DISCOVERY_STABILIZE_SECONDS" -gt 0 ]]; then
    log "Allowing gateway discovery cache to refresh"
    sleep "$GATEWAY_DISCOVERY_STABILIZE_SECONDS"
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
