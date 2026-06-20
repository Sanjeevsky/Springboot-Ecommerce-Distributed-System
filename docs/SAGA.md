# Order Checkout Saga (Orchestration)

A walkthrough of the **orchestration-based saga** that coordinates the distributed checkout
across `order-service`, `inventory-service`, and `payment-service`. Includes a **live run
transcript** captured against the running docker-compose stack.

> For the full system end-to-end (all services and methods), see [WORKFLOW.md](./WORKFLOW.md).

---

## 1. Why a saga?

A checkout spans three databases that no single ACID transaction can cover: orders, stock, and
payments. The **Saga pattern** replaces the impossible distributed transaction with a sequence of
**local transactions**, each followed by a published event. If a later step fails, the saga runs
**compensating transactions** (in reverse) to undo the earlier ones.

This project uses the **orchestration** flavour: one component — `OrderSagaOrchestrator` in
`order-service` — owns the state machine, decides the next step, and triggers compensation. The
participant services (`inventory`, `payment`) just react to its commands and reply with events.
(The alternative, *choreography*, has no central owner; logic is spread across services and is
harder to follow.)

### The bug this fixed

The original synchronous checkout charged payment **before** stock was confirmed, and on
insufficient stock it cancelled the order but **never refunded the payment**. The saga makes the
ordering correct (reserve → charge → confirm) and guarantees compensation.

---

## 2. Participants, topics, and state

| Service | Role | Entry point |
|---|---|---|
| `order-service` | **Orchestrator** (owns the saga state machine) | `service/OrderSagaOrchestrator.java` |
| `inventory-service` | Participant — reserve / release stock | `events/OrderEventConsumer.java` |
| `payment-service` | Participant — charge / refund | `events/PaymentCommandConsumer.java`, `PaymentServiceImpl.charge()` |

**Kafka topics**

| Topic | Producer | Consumer | Messages |
|---|---|---|---|
| `order-events` | order | inventory, notification, review | `StockReservationRequestedEvent`, `OrderConfirmedEvent`, `OrderCancelledEvent` |
| `inventory-events` | inventory | order | `StockReservedEvent`, `StockInsufficientEvent` |
| `payment-commands` *(new)* | order | payment | `ChargePaymentCommand`, `RefundPaymentCommand` |
| `payment-events` | payment | order, notification | `PaymentConfirmedEvent`, `PaymentFailedEvent`, `PaymentRefundedEvent` |

**Saga state** — persisted in table `order_saga` (entity `SagaInstance`, enum `SagaStatus`). This
is the saga's source of truth and is what makes the event handlers **idempotent**: every handler
guards on the current status, so a redelivered Kafka message is a no-op.

```
STARTED ─► STOCK_RESERVED ─► PAYMENT_CONFIRMED ─► COMPLETED      (happy path)
   │
   ├─► FAILED                                                    (insufficient stock)
   ├─ (STOCK_RESERVED) ─► COMPENSATING ─► COMPENSATED            (payment failed reply → release stock)
   └─ (STARTED | STOCK_RESERVED, timed out) ─► COMPENSATING ─► COMPENSATED  (reaper: participant unreachable)
```

> The shared `OrderStatus` enum is left untouched — `Order.status` stays `PENDING` during the saga
> and flips to `CONFIRMED`/`CANCELLED` only at the end. Saga progress lives in `SagaInstance`.

> **Timeout reaper.** Compensation above is driven by a downstream *reply* (`PaymentFailed`). If a
> participant is instead *unreachable* — payment-service down, its `ChargePaymentCommand` unconsumed —
> the saga would park in `STOCK_RESERVED` forever and leak reserved stock. `SagaTimeoutReaper` is a
> scheduled sweep that compensates any saga stuck in an in-flight state past `saga.timeout` (default
> 2m), driving it down the same `COMPENSATING → COMPENSATED` path. It re-guards on status inside its
> own transaction, so a reply that arrives mid-sweep is a no-op. See `docs/resilience-plan.md` Phase F.

---

## 3. The flow (forward + compensation)

```
POST /order-service/order/saga            OrderController.createOrderSaga  ──► 202 Accepted
  └─ OrderServiceImpl.createOrderSaga      build PENDING order (cart+address+coupon, inventory pre-check)
       └─ OrderSagaOrchestrator.startSaga  SagaInstance(STARTED)
            │  publishStockReservationRequested ──────────────► order-events
  STEP 1   inventory  OrderEventConsumer.handleStockReservationRequested → reserveStock()
            │  ◄── StockReservedEvent / StockInsufficientEvent  (inventory-events)
  order  InventoryEventConsumer  ──► orchestrator.onStockReserved | onStockInsufficient
            │  onStockReserved: SagaInstance STOCK_RESERVED
            │  publishChargePayment ──────────────────────────► payment-commands
  STEP 2   payment  PaymentCommandConsumer → PaymentServiceImpl.charge()
            │  ◄── PaymentConfirmedEvent / PaymentFailedEvent   (payment-events)
  order  PaymentEventConsumer  ──► orchestrator.onPaymentConfirmed | onPaymentFailed
  STEP 3   onPaymentConfirmed: order CONFIRMED + clear cart, SagaInstance COMPLETED
            │  publishOrderConfirmed ─────────────────────────► order-events (notification/review)
            │
  COMPENSATION (payment failed, stock already reserved):
            onPaymentFailed: SagaInstance COMPENSATING
            │  publishOrderCancelled ─────────────────────────► order-events
            │     inventory  OrderEventConsumer.handleOrderCancelled → releaseStock()  ← compensation
            └─ order CANCELLED, SagaInstance COMPENSATED
```

**Key idea:** compensation reuses an existing forward action. Releasing stock is just
`OrderCancelledEvent`, which inventory-service already knew how to handle.

---

## 4. HTTP API

| Method | Path | Purpose |
|---|---|---|
| `POST` | `/order-service/order/saga` | Start the saga. Returns **202** with the `PENDING` order. Optional `?simulatePaymentFailure=true` to force the compensation path. Honours `Idempotency-Key`. |
| `GET` | `/order-service/order/{id}/saga` | Poll the `SagaInstance` (watch the state machine advance). |
| `GET` | `/order-service/order/{id}` | The order itself (`status`, `paymentId`). |

The legacy synchronous `POST /order-service/order` is unchanged. The two flows coexist on the same
topics; consumers tell them apart by *"does a `SagaInstance` exist for this orderId?"*

---

## 5. Live run transcript

Captured `2026-06-07` against `docker compose up` (product priced at 50, stock seeded to 100).
Driver script: signup → seed catalog/inventory/address → add to cart → run each scenario.

### Scenario A — happy path (COMPLETED)

```
stock before saga: 100
POST /order/saga -> 202, orderId=967ab1f3-..., initial order.status=PENDING
   t=1 saga.status=STARTED
   t=2 saga.status=STOCK_RESERVED
   t=3 saga.status=COMPLETED
FINAL: order.status=CONFIRMED, paymentId=075569a1-...
stock after happy saga: 99
```

Service logs — note the **single trace id `a2b5b7e9487c2be7`** spanning all three services
(this is the Zipkin correlation id):

```
order      Saga STARTED for orderId=967ab1f3-... (simulatePaymentFailure=false)
inventory  Handling StockReservationRequestedEvent for orderId=967ab1f3-...
inventory  StockReservedEvent published for orderId=967ab1f3-...
order      Saga STOCK_RESERVED for orderId=967ab1f3-..., requesting payment charge
payment    Saga charge for orderId=967ab1f3-..., amount=50.0, simulateFailure=false
payment    Payment charged successfully, paymentId=075569a1-...
order      Saga COMPLETED for orderId=967ab1f3-..., order CONFIRMED
```

### Scenario B — payment failure → compensation (COMPENSATED)

```
stock before saga: 99
POST /order/saga?simulatePaymentFailure=true -> 202, orderId=e578fa7a-...
   t=1 saga.status=STARTED
   t=2 saga.status=COMPENSATED
FINAL: order.status=CANCELLED
stock after compensation: 99   ← reservation released, stock restored
```

Service logs (trace `b4508a193ccd17b5`):

```
order      Saga STARTED for orderId=e578fa7a-... (simulatePaymentFailure=true)
inventory  Handling StockReservationRequestedEvent ...
inventory  StockReservedEvent published ...
order      Saga STOCK_RESERVED, requesting payment charge
payment    Saga charge ... simulateFailure=true
payment    Simulated payment failure for orderId=e578fa7a-..., paymentId=b26439b9-...
order      Saga COMPENSATING (payment failed): releasing reserved stock
inventory  Handling OrderCancelledEvent for orderId=e578fa7a-...
inventory  Stock released for productId=c2894a12-..., reservedQty=1
order      Saga COMPENSATED, order CANCELLED
```

The reserved unit is returned to available stock — this is the compensating transaction in action,
and the exact bug the saga was built to fix.

### Scenario C — insufficient stock (fail fast, **before** the saga)

```
available=5, cart qty=9999
POST /order-service/order/saga
HTTP 400
{"success":false,"message":"Insufficient stock for productId=... Available=5, requested=9999"}
```

Obvious shortages are caught **synchronously** by the inventory pre-check in
`buildAndSavePendingOrder` — no order is created, no saga starts (a cheap fail-fast). The saga's
*async* `onStockInsufficient` → `FAILED` path is **defense-in-depth** for the race where stock is
depleted by another order *after* the pre-check passes but *before* the reservation lands. To
observe that route, two orders must compete for the last unit(s) concurrently.

---

## 6. How to reproduce

```bash
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
scripts/build-docker-jars.sh          # installs platform-commons + packages all jars
docker compose up -d --build          # topic payment-commands & table order_saga auto-create
```

Then drive it (a token from `/auth-service/login`, then seed catalog/inventory/address/cart) and:

```bash
# happy path
curl -X POST localhost:8081/order-service/order/saga \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"addressId":"<id>"}'
# watch it advance
curl localhost:8081/order-service/order/<orderId>/saga -H "Authorization: Bearer $TOKEN"

# force compensation
curl -X POST 'localhost:8081/order-service/order/saga?simulatePaymentFailure=true' \
  -H "Authorization: Bearer $TOKEN" -H 'Content-Type: application/json' \
  -d '{"addressId":"<id>"}'
```

**Observe it running:**
- **Zipkin** http://localhost:9411 — one trace spans order → inventory → payment → order.
- **Kafka UI** http://localhost:8080 — messages on `order-events`, `inventory-events`,
  `payment-commands`, `payment-events`.
- `docker compose logs -f order-service inventory-service payment-service | grep -i saga`

---

## 7. Files

| Concern | File |
|---|---|
| Orchestrator (state machine) | `order-service/.../service/OrderSagaOrchestrator.java` |
| Saga entity / status / repo | `order-service/.../model/SagaInstance.java`, `model/SagaStatus.java`, `repository/SagaInstanceRepository.java` |
| Saga entry point | `order-service/.../service/impl/OrderServiceImpl.java` (`createOrderSaga`), `controller/OrderController.java` |
| Reply listeners (order side) | `order-service/.../events/InventoryEventConsumer.java`, `events/PaymentEventConsumer.java` |
| Stock reserve/release | `inventory-service/.../events/OrderEventConsumer.java` |
| Charge/refund participant | `payment-service/.../events/PaymentCommandConsumer.java`, `service/impl/PaymentServiceImpl.java` |
| Commands & events | `platform-commons/.../events/` (`ChargePaymentCommand`, `RefundPaymentCommand`, `PaymentFailedEvent`, `StockReservationRequestedEvent`, …) |
| Unit tests | `order-service/.../service/OrderSagaOrchestratorTest.java` (7 cases) |
