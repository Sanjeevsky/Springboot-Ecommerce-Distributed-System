#!/usr/bin/env bash
# Compatibility wrapper for the maintained Postman-backed smoke flow.
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
cd "$ROOT_DIR"

export RUN_MAVEN_TESTS="${RUN_MAVEN_TESTS:-0}"
exec scripts/verify-local.sh
