# End-to-End Project Workflow

How a request travels through the system, service by service and **method by method** — from login
to a fulfilled order. For the distributed checkout saga in depth, see [SAGA.md](./SAGA.md).

---

## 1. The services

**Platform / infrastructure**

| Service | Port | Responsibility |
|---|---|---|
| `service-discovery` (Eureka) | 8761 | Service registry; everyone registers and looks up by name |
| `cloud-config` | 8071 | Centralised configuration |
| `api-gateway` | 8081 | Single entry point; JWT auth; routes `/<svc>/**` to each service |
| `spring-server` (Boot Admin) | 9000 | Admin UI (optional `platform-tools` profile) |

**Business services** (each owns its own MySQL schema)

| Service | Port | Core responsibility |
|---|---|---|
| `auth-server` | 8083 | Signup, login, JWT issuance |
| `catalog-service` | 8084 | Brands, categories, products, variants (Redis-cached) |
| `inventory-service` | 8088 | Stock levels; reserve/release for orders |
| `customer-service` | 8082 | Customer addresses |
| `shopping-cart-service` | 8086 | Cart; checkout snapshot (Feign → catalog) |
| `coupon-service` | 8089 | Coupon validation / application |
| `order-service` | 8092 | Order lifecycle; **saga orchestrator** |
| `payment-service` | 8085 | Payments: initiate / charge / confirm / refund |
| `notification-service` | 8087 | Listens to order/payment events, creates notifications |
| `review-service` | 8090 | Product reviews; listens to order events |
| `wishlist-service` | 8091 | Wishlist; move-to-cart (Feign → cart) |

**Observability:** Zipkin :9411 (traces), Prometheus :9090, Grafana :3000, Kafka UI :8080.

---

## 2. How every request enters: the gateway

```
client ──► api-gateway (8081)
             AuthenticationFilter:
               • RouterValidator → is this an open path? (/auth-service/** = yes)
               • else validate JWT (Bearer token)
               • extract subject (user email) → inject as  X-User  header
             route by path prefix → downstream service (resolved via Eureka)
```

- Downstream services **trust the `X-User` header** (set by the gateway) as the authenticated
  identity — they never re-validate the JWT.
- All responses are wrapped in `ApiResponse<T>` (`{success, message, data}`). Feign callers unwrap
  `.data` transparently via `ApiResponseFeignDecoder`.

Files: `api-gateway/.../filter/AuthenticationFilter.java`, `filter/RouterValidator.java`.

---

## 3. The user journey (controller → service → collaborators)

Each step lists the HTTP entry, the service method that does the work, and any cross-service calls.

### Step 1 — Authenticate
```
POST /auth-service/signup   → UserAuthController   → UserService.registerUser(user)
POST /auth-service/login    → UserAuthController   → UserService.authenticateUser(userDTO)  → returns JWT
```
Client stores the token and sends `Authorization: Bearer <token>` on every later call.

### Step 2 — Browse the catalog
```
GET  /catalog-service/product/list      → ProductCatalogController → ProductService.listProducts(page,size,sort)
GET  /catalog-service/product/search    → ProductCatalogController → ProductService.searchProducts(q,cat,brand,page,size)
GET  /catalog-service/product/getProduct/{id} → ProductCatalogController → ProductService.getProduct(uuid)  [Redis-cached]
```
(Catalog is seeded via `add-brand`, `addCategory`, `add-subcategory`, `product/addProduct`,
`variant/add/{productId}` → `ProductService.addProduct(...)`, etc.)

### Step 3 — Stock exists (inventory)
```
POST /inventory-service/stock                       → InventoryController → InventoryService.addStock(productId,variantId,qty)
GET  /inventory-service/stock/{productId}/variant/{variantId} →           → InventoryService.getStock(productId,variantId)
```

### Step 4 — Add a shipping address
```
POST /customer-service/address   → CustomerServiceController → AddressService.addAddress(address, user)
GET  /customer-service/addresses →                           → AddressService.getAddresses(user)
```

### Step 5 — Build the cart
```
POST /cart-service/cart/add          → CartController → CartService.addItem(user,productId,variantId,qty)
                                                          └─ CatalogFeignClient.getProduct(productId)   (price/name)
PUT  /cart-service/cart/item/{id}?qty=N →             → CartService.updateItem(user,productId,qty)
GET  /cart-service/cart/checkout      →               → CartService.getCheckoutSnapshot(user)  → CartSnapshot
```
`CartSnapshot` (items + totals) is the shared DTO the order service reads at checkout.

### Step 6 — (optional) Coupon
```
GET  /coupon-service/coupon/validate?code=&amount= → CouponController → CouponService.validateCoupon(code, amount)
POST /coupon-service/coupon/apply?code=            →                  → CouponService.applyCoupon(code)
```

### Step 7 — Checkout (two paths)

**(a) Legacy synchronous** — blocking, payment taken inline:
```
POST /order-service/order → OrderController.createOrder → OrderService.createOrder(user,addressId,coupon,idemKey)
  OrderServiceImpl.createNewOrder:
    CartFeignClient.getCheckoutSnapshot(user)         ← cart
    CustomerFeignClient.getAddress(user,addressId)    ← address
    InventoryFeignClient.getStockByProduct(...)       ← pre-check stock
    CouponFeignClient.validateCoupon/applyCoupon(...) ← discount
    save Order(PENDING)
  completeOrderCheckout:
    PaymentFeignClient.initiatePayment(PaymentRequest)  ← charge inline
    CartFeignClient.clearCart(user)
    publish OrderPlacedEvent → order-events             → inventory reserves stock (async)
```

**(b) Saga (recommended)** — async, correct ordering, compensatable:
```
POST /order-service/order/saga → OrderController.createOrderSaga → OrderService.createOrderSaga(...)
  OrderServiceImpl.createOrderSaga:
    buildAndSavePendingOrder(...)        (same cart/address/coupon/pre-check, save PENDING)
    OrderSagaOrchestrator.startSaga(order, simulatePaymentFailure)
  → returns 202 immediately; the rest runs over Kafka (see SAGA.md)
GET /order-service/order/{id}/saga → OrderService.getSaga(...)   poll the state machine
```

Both paths share `buildAndSavePendingOrder`; only the *settlement* differs (inline Feign vs
event-driven saga).

### Step 8 — Fulfillment side-effects (event-driven)
When the order is placed/confirmed/cancelled, events fan out:
```
order-events   → notification-service  OrderEventConsumer    → create order notifications
               → review-service        OrderEventConsumer    → enable review eligibility
               → inventory-service     OrderEventConsumer    → reserve / release stock
payment-events → notification-service  PaymentEventConsumer  → payment notifications
```
```
GET /notification-service/notifications        → NotificationController (list user notifications)
POST /review-service/review                     → ReviewController → ReviewService.addReview(user,review)
```

### Step 9 — Post-order
```
PUT  /order-service/order/{id}/confirm → OrderService.confirmOrder(user,orderId)  (legacy path)
PUT  /order-service/order/{id}/cancel  → OrderService.cancelOrder(user,orderId)   → PaymentFeignClient.refundPayment
GET  /payment-service/{paymentId}      → PaymentService.getByPaymentId(paymentId)
```

---

## 4. Event-driven backbone (Kafka)

| Topic | Produced by | Consumed by |
|---|---|---|
| `order-events` | order-service | inventory, notification, review |
| `inventory-events` | inventory-service | order-service |
| `payment-commands` | order-service | payment-service |
| `payment-events` | payment-service | order-service, notification |

DLT topics (`*-dlt`) capture poison messages after retries (`SeekToCurrentErrorHandler` +
`DeadLetterPublishingRecoverer`). The checkout saga's flow over these topics is detailed in
[SAGA.md](./SAGA.md).

---

## 5. Cross-cutting concerns

- **Service discovery:** Feign clients target logical names (`@FeignClient(name="payment-service")`),
  resolved by Eureka — no hard-coded hosts.
- **Resilience:** `order-service` wraps Feign calls (cart/payment/coupon) with Resilience4j circuit
  breakers + fallbacks (`clients/fallback/*`), so a downstream outage degrades gracefully.
- **Idempotency:** order & payment accept an `Idempotency-Key`; replays return the existing record
  instead of double-charging. The saga reuses this (`saga-charge:<orderId>`).
- **Tracing:** Spring Sleuth propagates one trace id across services; view end-to-end traces in
  Zipkin (:9411). (Seen live in the saga transcript — one id spans order→inventory→payment.)
- **Caching:** catalog reads are Redis-cached.

---

## 6. End-to-end at a glance

```
        ┌─────────────┐   JWT     ┌──────────────────────────── business services ──────────────────────────────┐
client ─►  api-gateway ─X-User──► auth · catalog · inventory · customer · cart · coupon · order · payment · ...   │
        └─────────────┘           │                                                                               │
                                  │  Feign (sync)                Kafka (async / saga)                             │
                                  │  cart→catalog                order→inventory→payment→order                    │
                                  │  order→cart/customer/         order→notification/review                       │
                                  │        coupon/payment/inv                                                      │
                                  └───────────────────────────────────────────────────────────────────────────  ┘
                          discovery (Eureka) · config (Cloud Config) · tracing (Zipkin) · metrics (Prometheus/Grafana)
```

1. **Authenticate** at the gateway → JWT → `X-User` downstream.
2. **Browse** catalog, ensure **stock**, add **address**.
3. **Cart** assembles items (pulls live price from catalog).
4. **Checkout** → order-service: sync (legacy) or **saga** (async, compensatable).
5. Saga coordinates **reserve stock → charge payment → confirm order**, compensating on failure.
6. **Events** drive notifications, reviews, and inventory; **Zipkin** ties the whole trace together.
