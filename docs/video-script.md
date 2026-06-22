# Trove — Walkthrough Video Script & Storyboard

A scene-by-scene script for recording a ~14-minute walkthrough of the **Trove** distributed
e-commerce platform. It is **layered**: Part 1 is high-level (what it is + a live UI demo +
the architecture at a glance) for a general/portfolio audience; Part 2 goes deep on the
internals (saga, circuit breakers, resilience/chaos, observability) for engineers. Skip Part 2
for a short cut.

Each scene gives: **🎬 SCREEN** (what to show), **🎙 NARRATION** (read aloud), and
**📎 REF** (exact files / URLs / commands so you can pause and point).

> I can't record or narrate the video itself — this is the script you (or a screen recorder +
> TTS) follow. Conventions: `file:line` references are clickable in most editors; all URLs
> assume the default local stack.

---

## 0. Pre-flight (do this before recording — not on camera)

```bash
# 1. Build service jars, then start the full stack (21 containers; ~30–60s to register)
scripts/build-docker-jars.sh
docker compose up -d

# 2. Confirm everything is healthy / registered
scripts/verify-local.sh            # or: docker compose ps

# 3. Start the frontend dev server (proxies /api → gateway :8081)
cd frontend && npm install && npm run dev      # → http://localhost:5173
```

**Tabs to pre-open** (so you only switch, never fumble):

| Tab | URL | Used in |
|-----|-----|---------|
| Storefront | http://localhost:5173 | Part 1 demo |
| Admin Studio | http://localhost:5173/studio | Part 1 demo |
| Grafana | http://localhost:3000 (admin/admin) → *ecommerce-overview* | Parts 1 & 2 |
| Zipkin | http://localhost:9411 | Part 2 |
| Kafka UI | http://localhost:8080 | Part 2 |
| Prometheus | http://localhost:9090 | Part 2 |
| Architecture diagram | open `architecture.html` in a browser | Part 1 |
| Editor | repo open at `docs/resilience-plan.md` | Part 2 |

**Seed data**: have one product in the catalog and a normal shopper account ready, plus the
admin account `admin@trove.local` / `admin123` for Studio. A terminal pane is needed for the
chaos demo in Part 2.

---

# PART 1 — The high-level tour (~6 min)

## Scene 1 — Cold open / what is this (0:00–0:45)

**🎙 NARRATION**
> "This is Trove — a production-style e-commerce marketplace built as a Spring Boot
> microservices system. Thirteen backend services, a React storefront, Kafka-driven
> workflows, full observability, and — the part I'm most proud of — resilience that's actually
> *proven*, not assumed. Let me show you the product first, then we'll go under the hood."

**🎬 SCREEN** Storefront home at http://localhost:5173 — scroll the landing page slowly.

**📎 REF** Frontend: `frontend/` (React 18 + Vite + react-router 6). Backend overview:
`README.md`.

---

## Scene 2 — Shopper journey: browse → cart → checkout (0:45–3:00)

**🎙 NARRATION**
> "Everything a real storefront needs. I'll search the catalog — search is backed by
> Elasticsearch with a MySQL fallback — open a product, add it to the cart, and check out."

**🎬 SCREEN** — follow these routes in order, narrating each:
1. `/search` — type a query; show instant results + suggestions.
2. `/p/:productId` — product detail; **Add to cart**.
3. Cart drawer / `/checkout` — show line items, address selection, optional coupon.
4. Place the order → lands on `/order/:orderId` — **order tracking** page.

**🎙 NARRATION (over checkout)**
> "When I place this order it doesn't just write a row — it kicks off a distributed *saga*
> across order, inventory, and payment services over Kafka. The order page polls the saga
> state machine; watch it move from STARTED to STOCK_RESERVED to PAYMENT_CONFIRMED to
> COMPLETED. We'll see exactly how that works in Part 2."

**📎 REF** Routes in `frontend/src/App.jsx` (`/search`, `/p/:productId`, `/checkout`,
`/order/:orderId`). Saga endpoint `POST /order-service/order/saga`; poll
`GET /order-service/order/{id}/saga`.

---

## Scene 3 — Account area (3:00–3:45)

**🎙 NARRATION**
> "Signed-in users get the full account surface — orders, wishlist, saved addresses,
> in-app notifications fired by Kafka events, and payment history."

**🎬 SCREEN** Click through `/account` tabs: **orders → wishlist → addresses → notifications →
payments**. On notifications, call out: "these were produced by the order/notification
services reacting to events — not written synchronously."

**📎 REF** `/account` child routes in `frontend/src/App.jsx`
(`orders`, `wishlist`, `addresses`, `notifications`, `payments`, `settings`).

---

## Scene 4 — Admin Studio (3:45–4:45)

**🎙 NARRATION**
> "There's a separate admin surface — the Studio — for catalog and operations: create
> products and variants, manage inventory stock levels, run coupons, and watch an activity
> feed. Catalog changes flow to Elasticsearch via Debezium change-data-capture, so search
> stays in sync without the catalog service ever calling Elasticsearch directly."

**🎬 SCREEN** Log in as admin, go to `/studio`: **products → products/new** (show the form) →
**inventory** (adjust a stock number) → **coupons** → **activity**.

**📎 REF** Studio routes in `frontend/src/App.jsx` (`/studio/{products, products/new,
products/:id, inventory, coupons, activity}`). CDC: Debezium MySQL connector via Kafka Connect.

---

## Scene 5 — Architecture at a glance (4:45–6:00)

**🎙 NARRATION**
> "So what's behind it. Thirteen Spring Boot services behind a Spring Cloud Gateway, each
> with its own MySQL schema — eleven schemas in total. Services discover each other through
> Eureka and pull config from a Config Server. Synchronous calls go over Feign; workflows that
> must be reliable go over Kafka. Redis caches the catalog and holds cart sessions;
> Elasticsearch powers search; Debezium streams DB changes into it. And the whole thing is
> wired for observability — Prometheus metrics, Grafana dashboards, and Zipkin traces, on by
> default."

**🎬 SCREEN** Open `architecture.html` (the generated live map) — pan across gateway →
services → Kafka topics → infra. Then flash the Grafana *ecommerce-overview* dashboard.

**📎 REF** `architecture.html` (generated by `generate-arch.py`). Service/port matrix:
`implementation.md` → *Service Completion Matrix*. Stack summary: `README.md` top table.

---

# PART 2 — Under the hood (~8 min)

## Scene 6 — The codebase shape (6:00–6:45)

**🎙 NARRATION**
> "Quick orientation in the repo. Each service is its own Maven module. Shared event and model
> types live in `platform-commons` so producers and consumers agree on the wire format.
> Cross-cutting concerns — structured JSON logging, correlation-ID propagation through Feign
> and Kafka headers — are auto-configured from `platform-commons` too. Tests are real: about
> 580 across the services, enforced by a config validator."

**🎬 SCREEN** Editor file tree: show a couple of service modules, `platform-commons/`, and
`scripts/`. Open `README.md` Test Coverage table.

**📎 REF** `platform-commons/`; coverage table in `README.md`; `scripts/validate-service-config.js`.

---

## Scene 7 — The checkout saga (the centerpiece) (6:45–9:00)

**🎙 NARRATION**
> "The checkout is an orchestration saga. One component — the OrderSagaOrchestrator in
> order-service — owns a state machine and drives each step by publishing a Kafka command; the
> inventory and payment services just react and reply with events. Forward path: reserve stock,
> charge payment, confirm. If payment *fails* after stock is reserved, it compensates — it
> publishes an OrderCancelledEvent, inventory releases the reservation, and the order is
> cancelled. Compensation reuses an existing forward action, which is the elegant part."

**🎬 SCREEN**
1. `docs/SAGA.md` — show the state-machine diagram and the topic table.
2. `OrderSagaOrchestrator.java` — scroll the handlers: `startSaga` → `onStockReserved` →
   `onPaymentConfirmed`, and the compensation `onPaymentFailed` → `compensate(...)`.
3. Kafka UI (http://localhost:8080) — show the topics `order-events`, `inventory-events`,
   `payment-commands`, `payment-events` with messages from your demo order.

**🎙 NARRATION (the reaper)**
> "One subtle failure mode: compensation only fires on a payment *failure reply*. If
> payment-service is simply *unreachable*, its command sits unconsumed and the saga would park
> in STOCK_RESERVED forever, leaking reserved stock. So there's a timeout reaper — a scheduled
> sweep that compensates any saga stuck in-flight past a timeout. That's a real distributed-
> systems edge case, handled deliberately."

**📎 REF** `docs/SAGA.md`;
`order-service/src/main/java/com/sanjeevsky/orderservice/service/OrderSagaOrchestrator.java`;
`.../service/SagaTimeoutReaper.java`. Topics in `docs/SAGA.md` table.

---

## Scene 8 — Circuit breakers that actually engage (9:00–10:30)

**🎙 NARRATION**
> "Here's a bug I found by trying to *prove* resilience rather than assume it. The order
> service wraps its Feign calls in Resilience4j circuit breakers — but they weren't engaging.
> The properties everyone configures feed a *different* registry than the Spring Cloud Feign
> path uses, so the breakers ran on library defaults and never tripped. A hung payment-service
> blocked every worker on the read timeout. The fix is a Java Customizer that gives the Feign
> breakers a real time limit and open-on-failure policy."

**🎬 SCREEN** `CircuitBreakerConfiguration.java` — show `feignCircuitBreakerCustomizer()` and
the tuning (4s time limit, 10-call window, 50% threshold, 10s open). Then
`FeignCircuitBreakerStateMetrics.java` — explain it polls the registry to export breaker state
to Prometheus (a lazily-created-breaker quirk meant the standard binding didn't).

**📎 REF**
`order-service/src/main/java/com/sanjeevsky/orderservice/config/CircuitBreakerConfiguration.java`;
`.../config/FeignCircuitBreakerStateMetrics.java`. Backstory: `docs/resilience-plan.md`
Phases B & G.

---

## Scene 9 — Chaos demo: prove it on camera (10:30–12:30) ⭐ showpiece

**🎙 NARRATION**
> "Let's break it live. There's a chaos harness — a thin wrapper over Docker — and asserted
> validation scripts. I'll pause payment-service and drive real orders."

**🎬 SCREEN** Terminal — run, narrating as it goes:
```bash
scripts/chaos.sh pause payment-service     # freeze the dependency
# drive a few checkout orders from the storefront, OR:
scripts/validate-circuit-breaker.sh        # asserts open → fail-fast → recover
```
Split-screen the Grafana *ecommerce-overview* dashboard — point at the **Downstream Feign
Timeouts** panel climbing and the breaker flipping. Then:
```bash
scripts/chaos.sh restore payment-service   # bring it back
```

**🎙 NARRATION**
> "Before the fix, every call hung for 15 seconds. Now the first few ride a 4-second time
> limit, the breaker opens, and subsequent calls fail in milliseconds into a fallback — the
> system sheds load instead of toppling. When payment returns, the breaker half-opens and
> closes on its own. And this isn't a one-off: it's a gated suite — `chaos-suite.sh` — that
> runs the circuit-breaker and saga checks and always restores the stack on exit."

**📎 REF** `scripts/chaos.sh`, `scripts/validate-circuit-breaker.sh`,
`scripts/validate-saga-compensation.sh`, `scripts/validate-saga-timeout.sh`,
`scripts/chaos-suite.sh`. Alerts: `observability/alert-rules.yml` (`resilience` group).

---

## Scene 10 — Observability: traces, metrics, alerts (12:30–13:30)

**🎙 NARRATION**
> "Because tracing is on by default, every request carries a trace ID through Feign and Kafka.
> I'll grab a trace ID from the logs and pull the whole distributed call up in Zipkin — one
> waterfall across gateway, order, inventory, and payment. Prometheus scrapes every service;
> Grafana renders the overview dashboard; and there are alert rules for the things that
> actually page you — breaker-open, downstream timeouts, Kafka consumer lag, HTTP 5xx, and saga
> compensation rate."

**🎬 SCREEN** Zipkin (http://localhost:9411) — search a recent trace, expand the span
waterfall. Prometheus (http://localhost:9090) — run a quick query, e.g.
`resilience4j_circuitbreaker_state{state="open"}`. Grafana — the dashboard panels.

**📎 REF** Zipkin :9411; Prometheus :9090; Grafana :3000 (admin/admin); rules in
`observability/alert-rules.yml`; dashboard `observability/grafana/dashboards/ecommerce-overview.json`.

---

## Scene 11 — Close (13:30–14:00)

**🎙 NARRATION**
> "That's Trove: a full microservices marketplace with a real storefront and admin, a
> Kafka-orchestrated saga with compensation and a timeout reaper, circuit breakers that are
> proven to shed load, change-data-capture search, and end-to-end observability. The interesting
> engineering isn't that it works on the happy path — it's that the failure paths are validated.
> Code's on GitHub; thanks for watching."

**🎬 SCREEN** Back to the storefront home, or the architecture diagram. Fade.

---

## Appendix A — Tight 5-minute cut (portfolio)

Scenes **1 → 2 → 5 → 9 → 11**. (What it is → live shopping demo → architecture glance →
chaos showpiece → close.) Skips the account/studio detail and the code-level deep dives.

## Appendix B — Recording tips

- **Resolution**: record at 1080p; bump editor/browser font so code is legible on small screens.
- **Pace the saga**: the async saga completes in a couple of seconds — pre-place one order just
  before recording Scene 2 so the `/order/:id` page shows progression without dead air.
- **Chaos timing**: in Scene 9, start `validate-circuit-breaker.sh` and let it narrate itself;
  it prints PASS/FAIL lines that make good on-screen beats. It always restores on exit.
- **Don't show secrets**: the demo admin creds (`admin@trove.local`/`admin123`) are fine; avoid
  showing any real tokens/JWTs in the network tab.
- **Fallback if a service is flaky**: `docker compose restart <service>` and re-run
  `scripts/verify-local.sh` before the take.
