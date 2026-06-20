# Resilience Validation Plan

Prove the system *degrades gracefully and recovers* under failure, instead of
assuming it does. This phase turns the resilience machinery the platform already
has — Resilience4j circuit breakers, the orchestration saga's compensation path,
and the `simulatePaymentFailure` learning lever — into **observable, repeatable,
and asserted** behavior. Failures are injected on a single-host `docker compose`
stack, observed through the existing Prometheus/Grafana/Zipkin stack, and checked
against documented steady-state hypotheses.

## Implementation status

Not started. This is Phase 8, following the completed sales-catalog-admin plan
(Phases 0–7) and the operational-hardening PRs (#8–#14: tracing-on, image slim,
the Metaspace OOM fixes, auth-error UX, Metaspace monitoring).

## What already exists (the foundation)

| Capability | Where |
|------------|-------|
| k6 suite with smoke/load/stress/soak scenarios + pass/fail thresholds | `load-tests/checkout-flow.js`, `load-tests/catalog-browse.js` |
| Circuit breakers on cart / payment / coupon + 15s timelimiter | `order-service/src/main/resources/application.properties` (`resilience4j.*`) |
| Saga compensation (STOCK_RESERVED→PAYMENT_CONFIRMED→COMPLETED / COMPENSATING→COMPENSATED) | `OrderSagaOrchestrator` (order-service) |
| Synchronous failure lever | `POST /order-service/order/saga?simulatePaymentFailure=true` |
| Metrics, dashboards, traces | Prometheus :9090, Grafana `ecommerce-overview`, Zipkin :9411 (tracing on by default since #9) |

## Current gaps this plan closes

| Gap | Where |
|-----|-------|
| No fault-injection harness — can't kill/pause/slow a service on demand | scripts/ |
| Circuit breakers are configured but never proven to open and recover | order-service |
| Saga compensation is only unit-tested with mocks, never end-to-end against a real failing service | order-service |
| `alert-rules.yml` covers only Metaspace — nothing for CB-open, error-rate spikes, Kafka consumer lag, or saga failures | observability/ |
| No documented steady-state hypotheses or recovery runbook | docs/ |

## Scope guardrails

- **In:** order / payment / inventory failure paths (where the saga and circuit
  breakers live), Kafka consumer lag, and the gateway.
- **Out:** multi-node / Kubernetes chaos and real network-partition tooling
  (Pumba, Chaos Mesh). On a single-host compose stack `docker pause` + `docker
  kill` covers the overwhelming majority of the value; heavier tooling is
  out of proportion to the deployment.
- **Validator awareness:** PRs B/C add focused test classes and must update the
  README coverage rows (per `scripts/validate-service-config.js` and the
  README-count checks); any new controller route needs Postman entries in **both**
  the Ecommerce-API and Ecommerce-E2E collections.

## Steady-state hypotheses

These are the assertions every scenario validates. "Steady state" is a healthy
checkout flow: login → cart → order → confirm at the smoke load level.

1. **Fail fast, don't hang.** When payment-service is down, checkout requests fail
   within the circuit-breaker fast-fail window, not after the 15s timelimiter on
   every call.
2. **Circuit recovers.** Once payment-service returns, the breaker transitions
   open → half-open → closed within `waitDurationInOpenState`, and checkout
   succeeds again with no manual intervention.
3. **Saga compensates.** A saga order that fails at payment reaches `COMPENSATED`,
   reserved stock is released back to inventory, and the cart is **not** cleared.
4. **Lag drains.** After a consumer outage, Kafka consumer lag drains to zero
   within seconds of the consumer returning; no events are lost.
5. **Read path survives write-path failure.** Catalog browse stays healthy
   (Redis-served) while the checkout path is failing.

## Phase A — Chaos harness (prerequisite, small)

- `scripts/chaos.sh`: a thin wrapper over Docker with no new dependencies, exposing
  verbs `kill <svc>`, `pause <svc>` (freeze via `docker pause`), `slow <svc>`
  (added latency), and `restore`. Pure compose/Docker primitives so it runs
  anywhere the stack runs.
- This `docs/resilience-plan.md` (the steady-state hypotheses above) is the
  companion deliverable.
- Smallest PR; unblocks B–E.

## Phase B — Circuit-breaker validation *(done — required a fix)*

Validating the hypotheses first surfaced a latent bug: the Feign circuit breakers
never engaged. order-service uses `spring-cloud-circuitbreaker-resilience4j`, whose
breakers are configured by a Java `Customizer` bean — there was none, so they ran on
library defaults (a 100-call window, no effective time limit). The
`resilience4j.circuitbreaker.instances.*` properties were dead config, read only by
the separate resilience4j-spring-boot2 registry that the Feign path never touches. A
hung payment-service therefore blocked every order worker on the 15s Feign read
timeout — no load shedding.

- **Fix**: `CircuitBreakerConfiguration.java` adds the missing
  `Customizer<Resilience4JCircuitBreakerFactory>` — a 4s time limit and a breaker that
  opens on sustained failure (10-call window, 5-call minimum, 50% threshold, 10s open
  state). The 15s read-timeout / time-limiter properties stay as a cold-start backstop
  (and to satisfy config validation).
- **`scripts/validate-circuit-breaker.sh`**: drives the real checkout path through the
  gateway, uses `chaos.sh pause payment-service`, and asserts from order-service's
  `/actuator/circuitbreakerevents` stream that the breaker goes `CLOSED_TO_OPEN`, that
  calls fail fast (<1s) once open instead of riding the time limit (~4s), and that it
  auto-recovers (`OPEN_TO_HALF_OPEN` → `HALF_OPEN_TO_CLOSED`) after payment returns.
  Asserted programmatically, not eyeballed.
- Reading breaker **state from Prometheus** is deferred to Phase D: the lazily-created
  Feign breakers aren't bound to the metrics registry yet, so D adds that binding
  alongside the panels/alerts.

Verified live: before the fix a hung payment hung checkout 15s/call with the breaker
stuck closed; after, the first ~4–5 calls time out at 4s, the breaker opens, and
subsequent calls fail in ~50ms until payment recovers.

## Phase C — Saga compensation under real failure *(done)*

- **`scripts/validate-saga-compensation.sh`**: drives the async checkout saga against
  the live services and Kafka topics. It first runs a healthy saga as a control
  (asserts `COMPLETED` + cart cleared), then a failing one and asserts the
  `SagaInstance` reaches `COMPENSATED`, the reserved stock is released back in
  inventory-service, and the cart is left intact. This is the path previously covered
  only by `OrderSagaOrchestratorTest` against mocks.
- **Lever**: the compensation trigger is `POST /order/saga?simulatePaymentFailure=true`,
  which makes payment-service reply with a `PaymentFailed` event. That is the *correct*
  trigger — compensation is driven by a payment failure *reply*, not by payment being
  unreachable.
- **Finding (documented gap, not fixed here)**: if payment-service is merely *down*,
  the `ChargePaymentCommand` sits unconsumed on `payment-commands` and the saga parks
  in `STOCK_RESERVED` indefinitely — there is no timeout-based compensation. A saga
  timeout/reaper would be a separate feature PR; flagged here so it isn't mistaken for
  validated behaviour.

Verified live: healthy saga `STARTED → STOCK_RESERVED → PAYMENT_CONFIRMED → COMPLETED`
(cart cleared); failing saga `STARTED → STOCK_RESERVED → COMPENSATING → COMPENSATED`
with `lastError="Simulated payment gateway failure"`, reserved stock released, cart kept.

## Phase D — Resilience alert rules + dashboard panels

- Extend `observability/alert-rules.yml` with: `CircuitBreakerOpen`,
  `HighHttpErrorRate` (5xx ratio), `KafkaConsumerLagGrowing`, and
  `SagaCompensationRate`.
- Add matching panels (circuit-breaker state, saga outcomes, consumer lag) to
  `observability/grafana/dashboards/ecommerce-overview.json` — mirrors exactly how
  the Metaspace alert + panel were added in #14.

## Phase E — Wire into verification + soak baseline

- `scripts/chaos-suite.sh`: automate Phases B and C as a gated suite, in the spirit
  of `scripts/verify-local.sh`.
- Capture a documented soak-test baseline (p99 latency, HikariCP active
  connections, consumer-lag drain time) in this doc as the regression reference for
  future changes.

## Recommended approach

Phases **A → C are the core** — the harness plus the two proofs that matter
(circuit breakers genuinely open/recover, and the saga genuinely compensates
end-to-end). Phases **D and E are the polish** that make the validation permanent
and prevent it from rotting: alerts so regressions page, and a soak baseline so
performance drift is caught.

## Suggested PR sequence

1. **PR A** — Phase A (chaos harness + this plan doc).
2. **PR B** — Phase B (circuit-breaker open/recover validation).
3. **PR C** — Phase C (saga compensation end-to-end).
4. **PR D** — Phase D (resilience alert rules + Grafana panels).
5. **PR E** — Phase E (chaos suite gating + soak baseline).

Each PR is independently shippable; A unblocks everything.
