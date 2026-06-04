#!/usr/bin/env bash

set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

DEFAULT_MAVEN_JAVA_HOME="/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home"
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

if [[ -n "${JAVA_HOME:-}" ]]; then
  printf '\n==> Using JAVA_HOME=%s for Maven\n' "$JAVA_HOME"
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

  jar_matches=("$module"/target/*.jar)
  if [[ ! -e "${jar_matches[0]}" ]]; then
    echo "No packaged jar found for $module in $module/target" >&2
    exit 1
  fi
  if [[ ${#jar_matches[@]} -ne 1 ]]; then
    echo "Expected exactly one packaged jar for $module, found ${#jar_matches[@]}" >&2
    printf '  %s\n' "${jar_matches[@]}" >&2
    exit 1
  fi
done

printf '\nDocker jars built successfully\n'
