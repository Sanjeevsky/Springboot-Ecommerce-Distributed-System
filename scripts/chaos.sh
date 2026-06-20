#!/usr/bin/env bash
#
# chaos.sh — fault-injection harness for resilience validation (Phase 8, PR A).
#
# A thin wrapper over `docker compose` with no code dependencies. It injects the
# faults the resilience plan's steady-state hypotheses are checked against:
#
#   kill <svc>        ungraceful stop (SIGKILL) — container exits, simulates a crash
#   stop <svc>        graceful stop (SIGTERM)   — clean shutdown
#   pause <svc>       freeze the process        — simulates a hang / unresponsive peer
#   slow <svc> [ms]   add network latency       — egress delay via a tc/netem sidecar
#   restore [svc]     undo every fault on a service (or all known services)
#   status            show the current state of the business services
#   list              print the services this harness targets
#
# Faults are applied by docker-compose SERVICE NAME (e.g. payment-service), so the
# script does not depend on the compose project prefix or container naming.
#
# See docs/resilience-plan.md for the steady-state hypotheses these faults probe.
#
# Examples:
#   scripts/chaos.sh kill payment-service       # prove the circuit breaker opens
#   scripts/chaos.sh pause inventory-service    # prove the saga compensates on hang
#   scripts/chaos.sh slow catalog-service 400   # 400ms egress latency
#   scripts/chaos.sh restore                    # undo everything
#
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$ROOT_DIR"

# Sidecar image used for tc/netem latency injection. It shares the target
# container's network namespace, so the target image needs no tc/NET_ADMIN of
# its own (the alpine JRE images have neither). Override if you mirror it.
CHAOS_TC_IMAGE="${CHAOS_TC_IMAGE:-nicolaka/netshoot}"
CHAOS_IFACE="${CHAOS_IFACE:-eth0}"
CHAOS_DEFAULT_DELAY_MS="${CHAOS_DEFAULT_DELAY_MS:-300}"

# Business services whose failure modes the resilience phase exercises. `restore`
# and `status` iterate this set; `kill/stop/pause/slow` accept any compose service.
TARGETS=(
  api-gateway
  auth-server
  catalog-service
  customer-service
  shopping-cart-service
  payment-service
  inventory-service
  order-service
  coupon-service
  review-service
  wishlist-service
  notification-service
)

# Resolve the compose CLI (v2 plugin preferred, legacy binary as fallback).
if docker compose version >/dev/null 2>&1; then
  DC=(docker compose)
elif command -v docker-compose >/dev/null 2>&1; then
  DC=(docker-compose)
else
  echo "error: neither 'docker compose' nor 'docker-compose' is available" >&2
  exit 1
fi

log()  { printf '\033[36m[chaos]\033[0m %s\n' "$*"; }
warn() { printf '\033[33m[chaos]\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[31m[chaos]\033[0m %s\n' "$*" >&2; exit 1; }

usage() {
  # Print the leading comment block (from the title line to the end of the header),
  # stripping the leading "# ", without depending on hardcoded line numbers.
  awk 'NR<3{next} /^#/{sub(/^# ?/,""); print; next} {exit}' "${BASH_SOURCE[0]}"
  exit "${1:-0}"
}

# Real container id for a compose service, or empty if it has none running.
container_id() {
  "${DC[@]}" ps -q "$1" 2>/dev/null | head -n1
}

require_service() {
  [[ -n "$1" ]] || die "this verb needs a service name (see: chaos.sh list)"
}

# --- fault verbs ------------------------------------------------------------

do_kill() {
  require_service "${1:-}"
  log "killing $1 (SIGKILL — simulating a crash)"
  "${DC[@]}" kill "$1"
}

do_stop() {
  require_service "${1:-}"
  log "stopping $1 (SIGTERM — graceful shutdown)"
  "${DC[@]}" stop "$1"
}

do_pause() {
  require_service "${1:-}"
  local cid; cid="$(container_id "$1")"
  [[ -n "$cid" ]] || die "$1 has no running container to pause"
  log "pausing $1 (freezing process — simulating a hang)"
  "${DC[@]}" pause "$1"
}

do_slow() {
  require_service "${1:-}"
  local svc="$1" delay="${2:-$CHAOS_DEFAULT_DELAY_MS}"
  local cid; cid="$(container_id "$svc")"
  [[ -n "$cid" ]] || die "$svc has no running container to slow"
  log "adding ${delay}ms egress latency on $svc ($CHAOS_IFACE) via $CHAOS_TC_IMAGE"
  # Reset any prior qdisc first so repeated calls don't stack/error, then apply.
  docker run --rm --net "container:${cid}" --cap-add NET_ADMIN "$CHAOS_TC_IMAGE" \
    tc qdisc del dev "$CHAOS_IFACE" root 2>/dev/null || true
  docker run --rm --net "container:${cid}" --cap-add NET_ADMIN "$CHAOS_TC_IMAGE" \
    tc qdisc add dev "$CHAOS_IFACE" root netem delay "${delay}ms"
  log "latency applied — remove with: scripts/chaos.sh restore $svc"
}

# Best-effort removal of a netem qdisc from a still-running container.
clear_latency() {
  local svc="$1" cid; cid="$(container_id "$svc")"
  [[ -n "$cid" ]] || return 0
  docker run --rm --net "container:${cid}" --cap-add NET_ADMIN "$CHAOS_TC_IMAGE" \
    tc qdisc del dev "$CHAOS_IFACE" root >/dev/null 2>&1 || true
}

restore_one() {
  local svc="$1"
  # Order matters: a paused container must be unpaused before it can be started,
  # and latency can only be cleared while the container is up.
  "${DC[@]}" unpause "$svc" >/dev/null 2>&1 || true
  clear_latency "$svc"
  "${DC[@]}" start "$svc"   >/dev/null 2>&1 || true
}

do_restore() {
  if [[ -n "${1:-}" ]]; then
    log "restoring $1"
    restore_one "$1"
  else
    log "restoring all known services"
    for svc in "${TARGETS[@]}"; do restore_one "$svc"; done
  fi
  log "restore complete — verify with: scripts/chaos.sh status"
}

do_status() {
  printf '%-26s %-12s\n' "SERVICE" "STATE"
  printf '%-26s %-12s\n' "-------" "-----"
  for svc in "${TARGETS[@]}"; do
    local cid state
    cid="$(container_id "$svc")"
    if [[ -z "$cid" ]]; then
      state="absent/stopped"
    else
      state="$(docker inspect -f '{{.State.Status}}' "$cid" 2>/dev/null || echo unknown)"
    fi
    printf '%-26s %-12s\n' "$svc" "$state"
  done
}

do_list() {
  printf '%s\n' "${TARGETS[@]}"
}

# --- dispatch ---------------------------------------------------------------

cmd="${1:-}"; shift || true
case "$cmd" in
  kill)    do_kill "$@";;
  stop)    do_stop "$@";;
  pause)   do_pause "$@";;
  slow)    do_slow "$@";;
  restore) do_restore "$@";;
  status)  do_status;;
  list)    do_list;;
  -h|--help|help|"") usage 0;;
  *) warn "unknown command: $cmd"; usage 1;;
esac
