# Resilience Validation Plan

Prove the system *degrades gracefully and recovers* under failure, instead of
assuming it does. This phase turns the resilience machinery the platform already
has — Resilience4j circuit breakers, the orchestration saga's compensation path,
and the `simulatePaymentFailure` learning lever — into **observable, repeatable,
and asserted** behavior. Failures are injected on a single-host `docker compose`
stack, observed through the existing Prometheus/Grafana/Zipkin stack, and checked
against documented steady-state hypotheses.

## Implementation status

Phases A–F complete. This is Phase 8, following the completed sales-catalog-admin plan
(Phases 0–7) and the operational-hardening PRs (#8–#14: tracing-on, image slim,
the Metaspace OOM fixes, auth-error UX, Metaspace monitoring). Phase B turned up — and
fixed — a real bug (the Feign circuit breakers never engaged). The saga timeout/reaper that
Phase C flagged as a gap is now built out in **[Phase F](#phase-f--saga-timeout-reaper-done)**.
One item remains **deferred by design**, not skipped — exporting per-Feign-client breaker
state to Prometheus; its rationale is in [Deferred by design](#deferred-by-design) below.

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
- **Finding (gap surfaced here, closed in Phase F)**: if payment-service is merely *down*,
  the `ChargePaymentCommand` sits unconsumed on `payment-commands` and the saga parks
  in `STOCK_RESERVED` indefinitely — the orchestrator only compensates on a payment *reply*.
  This phase validates the reply-driven path; the timeout-driven path is a separate feature,
  now built in **[Phase F](#phase-f--saga-timeout-reaper-done)**.

Verified live: healthy saga `STARTED → STOCK_RESERVED → PAYMENT_CONFIRMED → COMPLETED`
(cart cleared); failing saga `STARTED → STOCK_RESERVED → COMPENSATING → COMPENSATED`
with `lastError="Simulated payment gateway failure"`, reserved stock released, cart kept.

## Phase D — Resilience alert rules + dashboard panels *(done)*

- **`observability/alert-rules.yml`** gains a `resilience` group with four alerts, each
  backed by a metric that is actually scraped:
  - `DownstreamCallTimeoutsHigh` — `rate(resilience4j_timelimiter_calls_total{kind="timeout"})`
    by Feign method; a sustained stream of 4s time-limiter timeouts is the "downstream is
    hanging" signal the circuit breaker responds to.
  - `HighHttpErrorRate` — 5xx ÷ total `http_server_requests_seconds_count` per service.
  - `KafkaConsumerLagGrowing` — `kafka_consumer_fetch_manager_records_lag` sustained per topic.
  - `SagaCompensationRateHigh` — `rate(order_saga_terminal_total{outcome="COMPENSATED"})`,
    a new Micrometer counter added to `OrderSagaOrchestrator` (tagged by outcome).
- **Grafana** (`ecommerce-overview.json`) gains two panels — *Checkout Saga Outcomes* and
  *Downstream Feign Timeouts*. The existing dashboard already had HTTP-5xx and Kafka-lag
  panels, so those weren't duplicated. Mirrors how the Metaspace alert + panel were added in #14.
- **Circuit-breaker state caveat**: the per-Feign-client breaker *state*
  (`resilience4j_circuitbreaker_state` for `HardCodedTarget#…`) is **not** exported to
  Prometheus — Spring Cloud's factory keeps the breakers in a registry the
  resilience4j-spring-boot2 Micrometer binder doesn't track at runtime (only the time-limiter
  side is metered, hence the timeout-based alert). Breaker **open/close transitions remain
  observable** via order-service's `/actuator/circuitbreakerevents` (which Phase B's
  validation asserts against). Wiring breaker state into Prometheus is a follow-up — see
  [Deferred by design](#deferred-by-design).

Verified live: `promtool check rules` passes (6 rules), all four alerts load in Prometheus
(`/api/v1/rules`), and Grafana provisions the 11-panel dashboard.

## Phase E — Wire into verification + soak baseline *(done)*

- **`scripts/chaos-suite.sh`**: runs the Phase B and Phase C validations as one gated
  suite (in the spirit of `scripts/verify-local.sh`), reports a combined pass/fail, and
  always `chaos.sh restore`s on exit — even on failure or Ctrl-C — so a failed run never
  leaves a service paused. Skippable with `RUN_CIRCUIT_BREAKER=0` / `RUN_SAGA=0`; the slower
  Phase F saga-timeout check is opt-in via `RUN_SAGA_TIMEOUT=1`.
- **Soak baseline** — the regression reference below.

### Soak / steady-state baseline

A full 30-minute soak is driven by k6 (`k6 run --env SCENARIO=soak load-tests/checkout-flow.js`)
and watched on the Grafana `ecommerce-overview` dashboard. k6 is not installed in every
environment, so the reference snapshot below was captured under a light synthetic
checkout load (≈40 orders + browse) — re-run the k6 soak before trusting these as
production figures, but they pin the right order of magnitude:

| Signal | Baseline | Where to watch |
|--------|----------|----------------|
| Healthy `POST /order-service/order` latency | mean ≈ 0.12s, max ≈ 0.28s (light load) | HTTP P99 Latency panel |
| HikariCP pool size (per service) | max 3 connections | Active DB Connections panel — should stay below 3 under load |
| Kafka consumer lag at rest | drains to 0 | Kafka Consumer Lag panel (hypothesis #4) |
| Saga outcomes | COMPLETED dominates; COMPENSATED only on injected payment failure | Checkout Saga Outcomes panel |

A P99 spike on the latency panel with HikariCP pinned at 3 indicates DB connection-pool
exhaustion; consumer lag that does not drain within seconds indicates a stuck consumer.

## Phase F — Saga timeout reaper *(done)*

Closes the gap Phase C surfaced: the orchestrator compensates only on a payment *failure
reply*, so a saga whose participant is *unreachable* (payment-service down, its
`ChargePaymentCommand` unconsumed) parks in `STOCK_RESERVED` forever and the reserved stock
leaks. Phase C validated the reply-driven path; this phase adds the timeout-driven sibling.

- **`SagaTimeoutReaper`** (order-service) — a `@Scheduled` sweep (gated by
  `saga.reaper.enabled`, default on; `@EnableScheduling` added to the app) that every
  `saga.reaper.interval-ms` (30s) finds sagas parked in an in-flight state
  (`STARTED` / `STOCK_RESERVED`) whose last transition is older than `saga.timeout` (2m) via
  `SagaInstanceRepository.findByStatusInAndUpdatedAtBefore`, and compensates each. The two
  transient states (`PAYMENT_CONFIRMED` / `COMPENSATING`) flip terminal inside one
  transaction, so they can't park and aren't swept.
- **`OrderSagaOrchestrator.compensateStuckSaga`** — the timeout-driven sibling of
  `onPaymentFailed`, sharing the same private `compensate(...)` step (mark `COMPENSATING` →
  `cancelOrder` releases reserved stock via `OrderCancelledEvent` → mark `COMPENSATED`). It
  re-guards on the current status inside its own transaction, so a real reply landing between
  the reaper's scan and the compensation is a no-op (no double-compensation). The release is
  idempotent for a `STARTED` saga whose stock was never actually reserved.
- **Metrics**: each reaped saga increments `order_saga_reaped_total{from="<state>"}` *and* the
  existing `order_saga_terminal_total{outcome="COMPENSATED"}` — so the Phase D
  `SagaCompensationRateHigh` alert already fires when the reaper is doing work (a participant
  is down), and the new counter distinguishes timeout-driven from decline-driven compensation.
- **Tests**: `SagaTimeoutReaperTest` (compensates every stuck saga; no-op when none stuck;
  one failure doesn't abort the batch) and three new `OrderSagaOrchestratorTest` cases
  (compensates a timed-out `STOCK_RESERVED` and `STARTED` saga; no-op when already terminal).
- **End-to-end**: `scripts/validate-saga-timeout.sh` proves it against the live stack —
  `chaos.sh pause payment-service`, start a real saga, assert it parks in `STOCK_RESERVED`,
  then assert the reaper drives it to `COMPENSATED` with a timeout `lastError`, releases the
  reserved stock, and preserves the cart. It always restores payment-service on exit. Because
  the default reaper waits `saga.timeout` (2m), it is **opt-in** in `chaos-suite.sh`
  (`RUN_SAGA_TIMEOUT=1`); run order-service with `SAGA_TIMEOUT=PT15S` /
  `SAGA_REAPER_INTERVAL_MS=5000` and `REAP_WAIT=40` for a fast run.

Each stuck saga is compensated in its own transaction, so one failure doesn't abort the rest
of the sweep. The reaper is idempotent run-to-run: once a saga is `COMPENSATED` it leaves the
in-flight set and is never re-scanned.

## Deferred by design

One resilience item was considered and **intentionally left out** of this phase. It is
documented here so it isn't mistaken for validated behaviour, and so a future contributor
doesn't "fix" it as if it were a bug — it is a separate feature, not a gap in this work.
(The saga timeout/reaper that previously sat here is now implemented — see
[Phase F](#phase-f--saga-timeout-reaper-done).)

1. **Per-Feign-client breaker state → Prometheus.** `resilience4j_circuitbreaker_state` for
   the `HardCodedTarget#…` Feign breakers is not scraped: Spring Cloud's factory keeps those
   lazily-created breakers in a registry the resilience4j-spring-boot2 Micrometer binder
   doesn't track at runtime (only the time-limiter side is metered). A speculative metrics
   binding was **reverted rather than shipped unconfirmed**. Breaker open/close transitions
   stay observable via order-service `/actuator/circuitbreakerevents` (asserted by Phase B),
   and the Phase D alerts key off the exported time-limiter timeout metric
   (`resilience4j_timelimiter_calls_total{kind="timeout"}`) instead. Wiring breaker state
   into Prometheus is a follow-up.

## Recommended approach

Phases **A → C are the core** — the harness plus the two proofs that matter
(circuit breakers genuinely open/recover, and the saga genuinely compensates
end-to-end). Phases **D and E are the polish** that make the validation permanent
and prevent it from rotting: alerts so regressions page, and a soak baseline so
performance drift is caught. **Phase F** then hardens the saga itself — turning the
unreachable-participant gap C documented into bounded, auto-compensated behaviour.

## Suggested PR sequence

1. **PR A** — Phase A (chaos harness + this plan doc).
2. **PR B** — Phase B (circuit-breaker open/recover validation).
3. **PR C** — Phase C (saga compensation end-to-end).
4. **PR D** — Phase D (resilience alert rules + Grafana panels).
5. **PR E** — Phase E (chaos suite gating + soak baseline).
6. **PR F** — Phase F (saga timeout reaper for the unreachable-participant case).

Each PR is independently shippable; A unblocks everything.
