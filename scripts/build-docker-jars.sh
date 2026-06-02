#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DEFAULT_MODULES=(
  auth-server
  catalog-service
  customer-service
  order-service
  shopping-cart-service
  payment-service
  inventory-service
  notification-service
  review-service
  wishlist-service
  coupon-service
  api-gateway
  service-discovery
  cloud-config
  spring-server
)

if [[ -z "${JAVA_HOME:-}" && -d /Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home ]]; then
  export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
fi

if ! command -v mvn >/dev/null 2>&1; then
  echo "Missing required command: mvn" >&2
  exit 1
fi

modules=("$@")
if [[ ${#modules[@]} -eq 0 ]]; then
  modules=("${DEFAULT_MODULES[@]}")
fi

printf '\n==> Installing platform commons\n'
mvn -B -f platform-commons/pom.xml install -DskipTests

for module in "${modules[@]}"; do
  if [[ ! -f "$module/pom.xml" ]]; then
    echo "Unknown module or missing pom.xml: $module" >&2
    exit 1
  fi

  printf '\n==> Packaging %s\n' "$module"
  mvn -B -f "$module/pom.xml" package -DskipTests
done

printf '\nDocker jars built successfully\n'
