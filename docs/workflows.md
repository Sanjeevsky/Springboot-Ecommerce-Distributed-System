# Workflows

This document describes every significant workflow in the Trove system — the sequence of service calls, Kafka events, database writes, and state transitions that make each operation happen.

---

## Table of Contents

1. [Correlation ID — Cross-Service Stitching](#0-correlation-id--cross-service-stitching)
1. [Authentication](#1-authentication)
2. [Catalog & Search](#2-catalog--search)
3. [Cart Management](#3-cart-management)
4. [Order Checkout — Saga (Async)](#4-order-checkout--saga-async)
5. [Order Checkout — Legacy (Sync)](#5-order-checkout--legacy-sync)
6. [Payment Lifecycle](#6-payment-lifecycle)
7. [Order Cancellation & Refund](#7-order-cancellation--refund)
8. [Inventory Management](#8-inventory-management)
9. [Product Catalog Sync (CDC)](#9-product-catalog-sync-cdc)
10. [Notifications](#10-notifications)
11. [Review Submission](#11-review-submission)
12. [Wishlist & Move-to-Cart](#12-wishlist--move-to-cart)
13. [Error Recovery Paths](#13-error-recovery-paths)

---

## 0. Correlation ID — Cross-Service Stitching

Every request that enters the system gets a `correlationId` that travels through every log line, every Feign call, and every Kafka message belonging to that logical operation.

### HTTP path (synchronous)

```
Client
  │ GET /catalog-service/product/list
  │ (no X-Correlation-ID header)
  ▼
api-gateway — CorrelationIdGatewayFilter  (WebFilter, Ordered.HIGHEST_PRECEDENCE)
  │  Detect: X-Correlation-ID absent
  │  Generate: UUID "355b4f6c-..."
  │  Mutate request: add X-Correlation-ID: 355b4f6c-...
  │  Mutate response: add X-Correlation-ID: 355b4f6c-...
  │
  ▼ (downstream request now carries X-Correlation-ID)
api-gateway — AuthenticationFilter
  │  JWT validation, inject X-User: user@example.com
  │
  ▼
catalog-service — MdcFilter  (OncePerRequestFilter, runs first)
  │  Read X-Correlation-ID: "355b4f6c-..."  →  MDC.put("correlationId", "355b4f6c-...")
  │  Read X-User: "user@example.com"         →  MDC.put("userId", "user@example.com")
  │
  │  [every log line in this thread now includes correlationId + userId]
  │
  │  ProductCatalogController.listProducts()
  │    LOG: {"message":"List products","correlationId":"355b4f6c-...","userId":"user@example.com","traceId":"..."}
  │
  │  [response complete]
  │  MDC.remove("correlationId")
  │  MDC.remove("userId")
  │
Response → client: X-Correlation-ID: 355b4f6c-...
```

If the client provides its own `X-Correlation-ID`, the gateway forwards it unchanged.

### Feign propagation (service-to-service)

```
order-service (handling POST /order)
  │  MDC: correlationId="355b4f6c-..."
  │
  │  CartFeignClient.getCheckoutSnapshot()
  │    → CorrelationIdFeignInterceptor runs before the request:
  │       template.header("X-Correlation-ID", MDC.get("correlationId"))
  │       template.header("X-User",           MDC.get("userId"))
  │
  ▼
shopping-cart-service
  │  MdcFilter reads X-Correlation-ID from Feign request
  │  MDC: correlationId="355b4f6c-..."  (same ID as the originating request)
  │
  │  [log lines in cart-service share the same correlationId]
```

### Kafka propagation (async event flows)

```
order-service (same request, correlationId still in MDC)
  │
  │  kafkaTemplate.send("order-events", StockReservationRequestedEvent)
  │    → KafkaMdcProducerInterceptor.onSend():
  │         record.headers().add("X-Correlation-ID", "355b4f6c-...")
  │         record.headers().add("X-User",           "user@example.com")
  │
  ▼  [async, different thread, possibly different container restart]
inventory-service — @KafkaListener
  │
  │  KafkaMdcConsumerInterceptor.intercept(record):
  │    MDC.put("correlationId", header("X-Correlation-ID"))  →  "355b4f6c-..."
  │    MDC.put("userId",        header("X-User"))            →  "user@example.com"
  │
  │  InventoryService.reserveStock()
  │    LOG: {"message":"Stock reserved","correlationId":"355b4f6c-...","traceId":"..."}
  │
  │  KafkaMdcConsumerInterceptor.success():
  │    MDC.remove("correlationId")
  │    MDC.remove("userId")
```

**Result:** The log line in inventory-service (async consumer) shares the same `correlationId` as the log line in order-service (HTTP handler). A `grep correlationId=355b4f6c` in any log aggregator returns the complete trace of the operation across all services.

### Log format

All services output JSON lines. Example:

```json
{
  "@timestamp": "2026-06-08T18:38:23.618Z",
  "level": "INFO",
  "logger": "c.s.c.controller.ProductCatalogController",
  "message": "List products request - page=0, size=20",
  "service": "catalog-service",
  "traceId": "2d963372bc91db2c",
  "spanId": "020ca600851d99ec",
  "correlationId": "355b4f6c-d162-42dd-8e66-e80a038a268b",
  "userId": "user@example.com"
}
```

`traceId`/`spanId` — Spring Cloud Sleuth (distributed tracing, Zipkin-exportable)  
`correlationId` — business-level request ID (survives async hops, visible to API clients)  
`userId` — authenticated user from the gateway `X-User` header

---

## 1. Authentication

### Sign-up

```
Client
  │ POST /auth-service/signup
  │ { email, password, firstName, lastName }
  ▼
API Gateway  (open endpoint — no JWT check)
  │
  ▼
auth-server
  │  bcrypt(password)
  │  INSERT INTO auth-db.user (email, password_hash, ...)
  └─► 201 Created { userId, email }
```

### Login

```
Client
  │ POST /auth-service/login
  │ { email, password }
  ▼
API Gateway  (open endpoint)
  │
  ▼
auth-server
  │  SELECT user WHERE email = ?
  │  bcrypt.matches(raw, hash)
  │  JWT.sign({ sub: email }, JWT_SECRET)
  └─► 200 OK { token: "eyJ..." }

Client stores token in localStorage.
All subsequent requests include: Authorization: Bearer <token>
```

### JWT Validation (every secured request)

```
Client
  │ GET /cart-service/cart
  │ Authorization: Bearer <token>
  ▼
API Gateway — AuthenticationFilter
  │  RouterValidator.isSecured(path) → true
  │  JWT.verify(token, JWT_SECRET)
  │  Extract claims.sub → user email
  │  Add header: X-User: user@example.com
  │
  ▼
shopping-cart-service
  │  Read X-User header → scope all data to this user
  └─► 200 OK { cart items }
```

---

## 2. Catalog & Search

### List Products (with Redis cache)

```
Client
  │ GET /catalog-service/product/list?page=0&size=20
  ▼
catalog-service — ProductCatalogController
  │  @Cacheable("products-list-cache")
  │  Check Redis: key "products::list"
  │
  ├─ Cache HIT  → return cached Page<Product>
  │
  └─ Cache MISS
       │  SELECT * FROM product WHERE status=1 LIMIT 20
       │  Store result in Redis
       └─► 200 OK { products: [...], totalPages: N }
```

### Full-Text Search

```
Client
  │ GET /catalog-service/product/search?q=headphone&categoryId=<uuid>&page=0&size=20
  ▼
catalog-service — ProductServiceImpl.searchProducts()
  │
  ├─ Elasticsearch AVAILABLE
  │    bool query {
  │      filter: status = 1
  │      filter: categoryId = <uuid>   (if provided)
  │      must:   multiMatch(q, [name, description, brand, categoryName], fuzziness=AUTO)
  │    }
  │    → [uuid1, uuid2, uuid3, ...]   (IDs sorted by relevance score)
  │    productRepository.findAllById(ids)
  │    → full Product entities from MySQL
  │    └─► 200 OK { products: [...] }
  │
  └─ Elasticsearch UNAVAILABLE (ConnectException)
       productRepository.findByNameContaining(q)  (LIKE %q%)
       └─► 200 OK { products: [...] }   (degraded — lower relevance)
```

### Auto-Suggest

```
Client (typing in search bar, query ≥ 2 chars, 300ms debounce)
  │ GET /catalog-service/product/suggest?q=head&size=5
  ▼
API Gateway  (open endpoint)
  │
  ▼
catalog-service — ProductServiceImpl.suggestProducts()
  │  match_phrase_prefix {
  │    field: "name"
  │    query: "head"
  │    max_expansions: 10
  │  }
  │  → ["Headphones XB9", "Headset Pro 7", ...]
  └─► 200 OK ["Headphones XB9", "Headset Pro 7"]

Frontend: shows dropdown; arrow keys to navigate; Enter or click to search
```

### Get Product Detail (with Redis cache)

```
Client
  │ GET /catalog-service/product/getProduct/{productId}
  ▼
catalog-service
  │  @Cacheable("product-cache")
  │  Check Redis: key "product::{productId}"
  │
  ├─ Cache HIT  → return Product
  └─ Cache MISS
       SELECT * FROM product WHERE id = ?
       JOIN brand, category, sub_category
       Store in Redis
       └─► 200 OK { product: {...} }
```

---

## 3. Cart Management

### Add Item

```
Client
  │ POST /cart-service/cart/add
  │ { productId, quantity }
  │ Authorization: Bearer <token>
  ▼
API Gateway → injects X-User header
  │
  ▼
shopping-cart-service
  │  Read X-User → identify cart
  │  Feign: GET /catalog-service/product/getProduct/{productId}  → price snapshot
  │  SELECT cart WHERE userId = X-User
  │    if no cart → INSERT new cart
  │  INSERT cart_item (cartId, productId, quantity, priceAtAdd)
  └─► 200 OK { cart }
```

### Checkout Snapshot

```
order-service (Feign call during checkout)
  │ GET /cart-service/cart/checkout
  │ X-User: user@example.com
  ▼
shopping-cart-service
  │  SELECT cart + cart_items WHERE userId = X-User
  │  Calculate: lineTotal = qty × priceAtAdd (per item)
  │  Calculate: grandTotal = Σ lineTotals
  └─► CartSnapshot { items: [{productId, qty, price, lineTotal}], total }
```

---

## 4. Order Checkout — Saga (Async)

The saga is the recommended checkout path. It uses Kafka for all inter-service coordination and automatically compensates on failure.

### Happy Path

```
Client
  │ POST /order-service/order/saga
  │ { addressId, couponCode? }
  ▼
order-service — OrderController
  │
  │  [1] Build order
  │  CartFeignClient.getCheckoutSnapshot()      → CartSnapshot
  │  CustomerFeignClient.getAddress(addressId)  → Address
  │  InventoryFeignClient.getStock(productIds)  → pre-check (fail fast if 0)
  │  CouponFeignClient.validateCoupon(code, total) → discount (optional)
  │  INSERT order (status=PENDING, total after discount)
  │
  │  [2] Start saga
  │  INSERT saga_instance (orderId, status=STARTED)
  │  PUBLISH → order-events: StockReservationRequestedEvent { orderId, items }
  │
  └─► 202 Accepted { orderId, sagaStatus: "STARTED" }

─── async ─────────────────────────────────────────────────────────

inventory-service — OrderEventConsumer
  │  CONSUME order-events: StockReservationRequestedEvent
  │  For each item: UPDATE inventory SET reserved = reserved + qty WHERE available > 0
  │  All reserved → PUBLISH inventory-events: StockReservedEvent { orderId }
  │  Any insufficient → PUBLISH inventory-events: StockInsufficientEvent { orderId }

order-service — InventoryEventConsumer
  │  CONSUME inventory-events: StockReservedEvent
  │  UPDATE saga_instance SET status = STOCK_RESERVED
  │  PUBLISH payment-commands: ChargePaymentCommand {
  │    orderId, amount, idempotencyKey: "saga-charge:{orderId}"
  │  }

payment-service — PaymentCommandConsumer
  │  CONSUME payment-commands: ChargePaymentCommand
  │  Idempotency check: SELECT payment WHERE idempotency_key = "saga-charge:{orderId}"
  │    if exists → skip (already charged)
  │    if not  → INSERT payment (status=CHARGED)
  │  PUBLISH payment-events: PaymentConfirmedEvent { orderId, paymentId, amount }

order-service — PaymentEventConsumer
  │  CONSUME payment-events: PaymentConfirmedEvent
  │  UPDATE order SET status = CONFIRMED
  │  UPDATE saga_instance SET status = COMPLETED
  │  CartFeignClient.clearCart()
  │  PUBLISH order-events: OrderConfirmedEvent { orderId, userId, items }

notification-service — (listens to order-events)
  │  CONSUME order-events: OrderConfirmedEvent
  │  INSERT notification { userId, "Your order #X has been confirmed", type=ORDER }

review-service — (listens to order-events)
  │  CONSUME order-events: OrderConfirmedEvent
  │  INSERT review_eligibility { userId, productId } for each item
  │  (user can now write a review for these products)
```

### State Machine

```
STARTED
  │
  ├─ StockReservedEvent received
  │    └─► STOCK_RESERVED
  │              │
  │              ├─ PaymentConfirmedEvent received
  │              │    └─► PAYMENT_CONFIRMED → COMPLETED  ✅
  │              │
  │              └─ PaymentFailedEvent received
  │                   └─► COMPENSATING
  │                             │
  │                             └─ OrderCancelledEvent → inventory releases stock
  │                                  └─► COMPENSATED  ❌ (order CANCELLED)
  │
  └─ StockInsufficientEvent received
       └─► FAILED  ❌ (order CANCELLED immediately)
```

### Compensation Path (Payment Failure)

```
payment-service
  │  PUBLISH payment-events: PaymentFailedEvent { orderId, reason }

order-service — PaymentEventConsumer
  │  CONSUME PaymentFailedEvent
  │  UPDATE saga_instance SET status = COMPENSATING
  │  UPDATE order SET status = CANCELLED
  │  PUBLISH order-events: OrderCancelledEvent { orderId, items }

inventory-service — OrderEventConsumer
  │  CONSUME order-events: OrderCancelledEvent
  │  For each item: UPDATE inventory SET reserved = reserved - qty
  │  (stock is released back to available)
  │  UPDATE saga_instance SET status = COMPENSATED

notification-service
  │  CONSUME order-events: OrderCancelledEvent
  │  INSERT notification { userId, "Your order #X was cancelled", type=ORDER }
```

### Polling Saga Status

```
Client (polling until COMPLETED or FAILED/COMPENSATED)
  │ GET /order-service/order/{orderId}/saga
  ▼
order-service
  │  SELECT saga_instance WHERE orderId = ?
  └─► { sagaStatus: "STOCK_RESERVED" | "COMPLETED" | "COMPENSATED" | "FAILED" }
```

---

## 5. Order Checkout — Legacy (Sync)

The legacy path performs all operations inline. Every step must succeed for the order to go through.

```
Client
  │ POST /order-service/order
  │ { addressId, couponCode? }
  │ Idempotency-Key: <uuid>
  ▼
order-service
  │
  ├─ [1] CartFeignClient.getCheckoutSnapshot()         → CartSnapshot
  ├─ [2] CustomerFeignClient.getAddress(addressId)     → Address
  ├─ [3] InventoryFeignClient.checkStock(items)        → fail if insufficient
  ├─ [4] CouponFeignClient.validateCoupon(code, total) → discount
  ├─ [5] INSERT order (status=PENDING)
  ├─ [6] PaymentFeignClient.initiatePayment(total)     → Payment { paymentId }
  │         └─ payment-service: INSERT payment (CHARGED)
  ├─ [7] UPDATE order SET status = CONFIRMED
  ├─ [8] CartFeignClient.clearCart()
  └─ [9] PUBLISH order-events: OrderPlacedEvent

  └─► 201 Created { orderId }
```

Any Feign call failure triggers Resilience4j circuit breaker fallback. If the circuit is open, subsequent requests immediately return an error response.

---

## 6. Payment Lifecycle

### States

```
PENDING → CHARGED → CONFIRMED → REFUNDED
                 └─► FAILED
```

### Initiate (direct REST)

```
Client
  │ POST /payment-service/initiate
  │ { orderId, amount }
  │ Idempotency-Key: <uuid>
  ▼
payment-service
  │  Idempotency check: SELECT payment WHERE idempotency_key = ?
  │  INSERT payment (orderId, amount, status=CHARGED, idempotency_key)
  └─► 201 Created { paymentId }
```

### Confirm

```
Client
  │ PUT /payment-service/confirm/{paymentId}
  ▼
payment-service
  │  UPDATE payment SET status = CONFIRMED
  └─► 200 OK
```

### Refund

```
order-service (on order cancellation)  OR  Client directly
  │ PUT /payment-service/refund/{paymentId}
  ▼
payment-service
  │  UPDATE payment SET status = REFUNDED
  │  PUBLISH payment-events: PaymentRefundedEvent { orderId, paymentId }
  └─► 200 OK

notification-service
  │  CONSUME PaymentRefundedEvent
  │  INSERT notification { userId, "Refund of ₹X processed", type=PAYMENT }
```

---

## 7. Order Cancellation & Refund

```
Client
  │ PUT /order-service/order/{orderId}/cancel
  ▼
order-service
  │  SELECT order WHERE id = ? AND userId = X-User
  │  Guard: order.status must be PENDING or CONFIRMED (not CANCELLED)
  │  UPDATE order SET status = CANCELLED
  │  PaymentFeignClient.refundPayment(order.paymentId)
  │    └─► payment-service: UPDATE payment SET status=REFUNDED
  │        PUBLISH payment-events: PaymentRefundedEvent
  └─► 200 OK

notification-service
  │  CONSUME PaymentRefundedEvent
  │  INSERT notification { userId, "Refund processed for order #X" }
```

---

## 8. Inventory Management

### Add Stock (admin)

```
POST /inventory-service/stock
{ productId, variantId?, quantity }
  ▼
inventory-service
  │  INSERT inventory (productId, variantId, total=qty, reserved=0)
  └─► 201 Created { inventoryId }
```

### Stock Reservation (saga step)

```
CONSUME order-events: StockReservationRequestedEvent { orderId, items: [{productId, qty}] }
  │
  │  BEGIN TRANSACTION
  │  For each item:
  │    SELECT inventory WHERE productId = ? FOR UPDATE
  │    IF total - reserved >= qty:
  │      UPDATE inventory SET reserved = reserved + qty
  │    ELSE:
  │      ROLLBACK → PUBLISH inventory-events: StockInsufficientEvent
  │  COMMIT
  │
  └─ All reserved → PUBLISH inventory-events: StockReservedEvent
```

### Stock Release (saga compensation)

```
CONSUME order-events: OrderCancelledEvent { orderId, items }
  │
  │  For each item:
  │    UPDATE inventory SET reserved = reserved - qty
  │  (reserved goes back to available — total unchanged)
```

### Stock Deduction (on order confirmation)

When order is confirmed, reserved stock becomes sold:
```
  UPDATE inventory SET total = total - qty, reserved = reserved - qty
```

---

## 9. Product Catalog Sync (CDC)

Three paths all write to the same `products` Elasticsearch index. See [Search Architecture](search-architecture.md) for full detail.

### Path 1 — Startup Bulk Index

```
catalog-service starts
  │
  │  ApplicationReadyEvent fires
  │  ProductIndexer.indexAllProducts()
  │    ensureIndex(): create ES index with mapping if it doesn't exist
  │    productRepository.findAll()  → all MySQL products
  │    mapper.toDocument(product)   → ProductDocument
  │    searchRepository.saveAll(docs)
  │    LOG: "Indexed 40 products into OpenSearch"
  │
  └─ If ES unreachable: LOG WARN, service starts anyway
```

### Path 2 — Inline Sync on Write

```
POST /catalog-service/product/addProduct
  │
  │  product = productRepository.save(product)  (MySQL)
  │  doc = mapper.toDocument(product)
  │  try:
  │    searchRepository.save(doc)                 (Elasticsearch)
  │  catch:
  │    LOG WARN "ES indexing failed: ..."         (API call still succeeds)
  │
  └─► 201 Created { product }
```

### Path 3 — Debezium CDC

```
MySQL (binlog, ROW format)
  │  Any INSERT / UPDATE / DELETE on product-catalog-db.product
  ▼
Debezium Kafka Connect (debezium/connect:2.4)
  │  Reads MySQL binlog via JDBC+Debezium protocol
  │  Serializes change as JSON (schemas disabled):
  │  {
  │    "before": { "id": "...", ... } | null,
  │    "after":  { "id": "...", ... } | null,
  │    "op": "c" | "u" | "r" | "d"
  │  }
  │
  ▼
Kafka topic: catalog.product-catalog-db.product
  │
  ▼
catalog-service — DebeziumCdcConsumer
  │  @KafkaListener(topics = "catalog.product-catalog-db.product", groupId = "catalog-es-sync")
  │
  │  Parse message:
  │    if root.has("schema") → payload = root.payload
  │    else                  → payload = root
  │
  │  Switch on payload.op:
  │
  │  "c" / "u" / "r"  (create / update / snapshot):
  │    id = payload.after.id
  │    product = productRepository.findById(UUID.fromString(id))
  │    doc = mapper.toDocument(product)
  │    searchRepository.save(doc)    ← upsert in ES
  │
  │  "d"  (delete):
  │    id = payload.before.id
  │    searchRepository.deleteById(id)  ← remove from ES
```

**Connector config summary:**
- Snapshot mode: `schema_only` (history captured but not rows — startup indexer handles existing rows)
- Topic prefix: `catalog`
- Included tables: `product-catalog-db.product`
- Schema history topic: `schema-changes.catalog`

---

## 10. Notifications

Notification-service is purely reactive — it only listens to Kafka and writes records. No business logic.

### Trigger Sources

| Event | Topic | Notification message |
|-------|-------|---------------------|
| `OrderConfirmedEvent` | `order-events` | "Your order #X has been confirmed" |
| `OrderCancelledEvent` | `order-events` | "Your order #X was cancelled" |
| `PaymentConfirmedEvent` | `payment-events` | "Payment of ₹X confirmed for order #X" |
| `PaymentRefundedEvent` | `payment-events` | "Refund of ₹X processed for order #X" |
| `PaymentFailedEvent` | `payment-events` | "Payment failed for order #X" |

### Read Notifications (polling)

```
Client (periodic poll or on notification bell click)
  │ GET /notification-service/notifications
  │ Authorization: Bearer <token>
  ▼
notification-service
  │  SELECT notifications WHERE userId = X-User ORDER BY createdAt DESC
  └─► [ { id, message, type, read, createdAt }, ... ]

Client
  │ PUT /notification-service/notifications/{id}/read
  ▼
notification-service
  │  UPDATE notification SET read = true WHERE id = ?
  └─► 200 OK
```

---

## 11. Review Submission

Reviews are purchase-gated: a user can only review a product they have purchased and whose order is confirmed.

### Submit Review

```
Client
  │ POST /review-service/review
  │ { productId, rating, text }
  ▼
review-service
  │  Check eligibility:
  │    SELECT review_eligibility WHERE userId=X-User AND productId=?
  │    (record inserted when OrderConfirmedEvent consumed)
  │
  │  if NOT eligible → 403 Forbidden "Purchase required to review"
  │
  │  if eligible:
  │    INSERT review (userId, productId, rating, text, status=PENDING)
  └─► 201 Created { reviewId, status: "PENDING" }
```

### Moderation

```
Admin
  │ PUT /review-service/review/{reviewId}/moderate?status=APPROVED
  ▼
review-service
  │  UPDATE review SET status = APPROVED | REJECTED
  └─► 200 OK

Public read:
  │ GET /review-service/review/product/{productId}
  ▼
review-service
  │  SELECT reviews WHERE productId=? AND status=APPROVED
  └─► [ { rating, text, userName, createdAt }, ... ]
```

### Review Summary

```
GET /review-service/review/product/{productId}/summary
  ▼
review-service
  │  SELECT AVG(rating), COUNT(*) FROM review WHERE productId=? AND status=APPROVED
  └─► { averageRating: 4.2, totalReviews: 28, distribution: {5:12, 4:10, 3:4, 2:1, 1:1} }
```

---

## 12. Wishlist & Move-to-Cart

### Add to Wishlist

```
Client
  │ POST /wishlist-service/wishlist
  │ { productId }
  ▼
wishlist-service
  │  INSERT wishlist_item (userId=X-User, productId)
  │  (duplicate check: upsert or return existing)
  └─► 201 Created { wishlistItemId }
```

### Move to Cart

```
Client
  │ POST /wishlist-service/wishlist/{wishlistItemId}/move-to-cart
  ▼
wishlist-service
  │  SELECT wishlist_item WHERE id = ? AND userId = X-User
  │
  │  CartFeignClient.addItem(productId, qty=1)
  │    → shopping-cart-service: add item (price snapshot from catalog)
  │    → 200 OK
  │
  │  if CartFeignClient succeeds:
  │    DELETE wishlist_item WHERE id = ?
  │    └─► 200 OK { "moved to cart" }
  │
  │  if CartFeignClient fails (circuit open):
  │    wishlist_item NOT deleted
  │    └─► 503 Service Unavailable
```

---

## 13. Error Recovery Paths

### Kafka Dead-Letter Queue

All consumers retry failed messages 2 times with 1s backoff. On the third failure, the message is published to the corresponding dead-letter topic and the original partition offset is committed (preventing infinite reprocessing).

| Original topic | Dead-letter topic |
|---------------|-------------------|
| `order-events` | `order-events-dlt` |
| `payment-events` | `payment-events-dlt` |
| `inventory-events` | `inventory-events-dlt` |

DLT messages must be processed manually (inspect, fix root cause, replay or discard).

### Saga Stuck States

If a saga instance stays in `STOCK_RESERVED` for an unusual length of time (e.g., payment-service is down), a scheduled job (or manual trigger) can re-publish the `ChargePaymentCommand`. The payment idempotency key prevents double-charging.

### Idempotent Retry

Any operation that accepts an `Idempotency-Key` header is safe to retry:

| Operation | Behaviour on duplicate key |
|-----------|---------------------------|
| `POST /order-service/order` | Returns existing order (no new INSERT) |
| `POST /payment-service/initiate` | Returns existing payment (no new charge) |
| `ChargePaymentCommand` (Kafka) | Skipped if payment with same key already exists |

### Elasticsearch Unavailability

| Component | Behaviour |
|-----------|-----------|
| catalog-service startup | `ProductIndexer` logs WARN, service starts normally |
| Search endpoint | Falls back to MySQL LIKE query |
| Suggest endpoint | Returns empty list |
| `addProduct()` inline sync | ES failure logged as WARN; API call succeeds |
| CDC consumer | CDC events queued in Kafka; processed when ES recovers |

### Circuit Breaker (order-service)

When a downstream service (cart, payment, coupon, inventory) fails the circuit threshold, Resilience4j opens the circuit:
- Open circuit: requests immediately return the fallback response (no Feign call attempted)
- After `waitDurationInOpenState`: circuit moves to HALF_OPEN and probes the downstream service
- If probe succeeds: circuit closes and normal operation resumes
