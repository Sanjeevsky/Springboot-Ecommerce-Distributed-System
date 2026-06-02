# Ecommerce Distributed System — Advanced Implementation Plan

## Architecture Overview

```
                        ┌───────────────────────────────────────────┐
                        │          API Gateway :8081                 │
                        │  JWT filter · Sleuth tracing · Prometheus  │
                        └─┬──────┬──────┬──────┬──────┬────────────┘
                          │      │      │      │      │
        ┌─────────────────┘  ┌───┘  ┌───┘  ┌───┘  ┌───┘
        ▼                    ▼      ▼      ▼      ▼
 ┌────────────┐  ┌──────────────┐  ┌────────────┐  ┌──────────────────┐
 │ auth-server│  │catalog-svc   │  │ cart-svc   │  │  order-service   │
 │  :8083     │  │  :8084       │  │  :8086     │  │    :8092         │
 │            │  │ Redis cache  │  │            │  │ Feign → cart,    │
 └────────────┘  └──────────────┘  └────────────┘  │ customer, pay,  │
                                                    │ coupon, inv     │
 ┌──────────────┐  ┌──────────────┐  ┌────────────┐└──────────────────┘
 │customer-svc  │  │ payment-svc  │  │coupon-svc  │
 │  :8082       │  │   :8085      │  │  :8089     │
 │ addresses    │  │ Kafka pub    │  │ validate   │
 └──────────────┘  └──────────────┘  └────────────┘

 ┌──────────────┐  ┌──────────────┐  ┌────────────┐  ┌──────────────┐
 │inventory-svc │  │notification  │  │review-svc  │  │wishlist-svc  │
 │  :8088       │  │  -svc :8087  │  │  :8090     │  │  :8091       │
 │ Kafka cons   │  │ Kafka cons   │  │ Kafka cons │  │ Feign→cart   │
 └──────────────┘  └──────────────┘  └────────────┘  └──────────────┘

 ─────────────────── Kafka Event Bus ──────────────────────
   order-events: order-service → inventory, notification, review
   payment-events: payment-service → notification
   inventory-events: inventory-service → order-service

 ─────────────────── Observability Stack ──────────────────
   Zipkin :9411       — distributed traces (Sleuth B3 propagation)
   Prometheus :9090   — metrics scrape from all /actuator/prometheus
   Grafana :3000      — dashboards (provisioned automatically)
   Kafka UI :8080     — topic / consumer group monitoring
```

---

## Service Completion Matrix

| Service | Port | Sleuth | Zipkin | Prometheus | Kafka | Feign | Status |
|---------|------|--------|--------|------------|-------|-------|--------|
| service-discovery | 8761 | — | — | — | — | — | ✅ |
| cloud-config | 8071 | — | — | — | — | — | ✅ |
| spring-server | 9000 | — | — | — | — | — | ✅ |
| api-gateway | 8081 | ✅ | ✅ | ✅ | — | — | ✅ |
| auth-server | 8083 | ✅ | ✅ | ✅ | — | — | ✅ |
| catalog-service | 8084 | ✅ | ✅ | ✅ | — | — | ✅ |
| customer-service | 8082 | ✅ | ✅ | ✅ | pub | feign | ✅ |
| shopping-cart-service | 8086 | ✅ | ✅ | ✅ | — | feign | ✅ |
| payment-service | 8085 | ✅ | ✅ | ✅ | pub | — | ✅ |
| order-service | 8092 | ✅ | ✅ | ✅ | pub | feign | ✅ |
| inventory-service | 8088 | ✅ | ⬛add | ⬛add | cons | — | 🔧 |
| notification-service | 8087 | ✅ | ⬛add | ⬛add | cons | — | 🔧 |
| coupon-service | 8089 | ✅ | ⬛add | ⬛add | — | — | 🔧 |
| review-service | 8090 | ✅ | ⬛add | ⬛add | cons | — | 🔧 |
| wishlist-service | 8091 | ✅ | ⬛add | ⬛add | — | ⬛cart | 🔧 |

---

## Advanced Implementation Phases

### Phase 1 — Complete Observability (all 12 business services)

**1.1 Add missing Maven deps (5 services)**

All 5 services (inventory, notification, coupon, review, wishlist) need:
```xml
<dependency>
    <groupId>org.springframework.cloud</groupId>
    <artifactId>spring-cloud-sleuth-zipkin</artifactId>
</dependency>
<dependency>
    <groupId>io.micrometer</groupId>
    <artifactId>micrometer-registry-prometheus</artifactId>
</dependency>
```

**1.2 Fix application.properties (all services)**

All services need:
```properties
spring.zipkin.baseUrl=http://localhost:9411/
spring.zipkin.enabled=false            # overridden to true in docker
spring.sleuth.sampler.probability=1.0
management.endpoint.health.show-details=always
management.endpoints.web.exposure.include=*
```

**1.3 Docker-compose**
- Remove `profiles: ["observability"]` — observability always starts
- Remove `profiles: ["optional-services"]` — all services start by default
- Enable `SPRING_ZIPKIN_ENABLED=true` for all services in docker env
- Add Kafka UI container
- Add Grafana volume mounts for provisioning

**1.4 Grafana provisioning**
- `observability/grafana/provisioning/datasources/prometheus.yaml`
- `observability/grafana/provisioning/dashboards/dashboard.yaml`
- `observability/grafana/dashboards/ecommerce-overview.json`

---

### Phase 2 — Advanced Service Integration

**2.1 Coupon Integration in Order-Service**

`CouponFeignClient` → `coupon-service` validate endpoint  
`CreateOrderRequest` extended with optional `couponCode`  
In `createOrder()`: if couponCode present → validate → apply discount

**2.2 Wishlist → Cart Move**

`CartFeignClient` in wishlist-service  
`moveToCart(userId, productId)` calls cart-service add endpoint

**2.3 Inventory Check before Order**

`InventoryFeignClient` in order-service  
Stock validation before creating order (non-blocking — continue with warning if inventory-service down)

---

### Phase 3 — Production Hardening

**3.1 Resilience4j in Order-Service**

Circuit breakers on all Feign clients with graceful fallbacks:
- `CartFeignClientFallback`, `PaymentFeignClientFallback`, `CustomerFeignClientFallback`

**3.2 Kafka Dead Letter Queue**

Error handler in inventory, notification, review consumers → DLQ topic `{topic}-dlt`

**3.3 Idempotency Keys**

Order-service: unique constraint on `(userId, idempotencyKey)` to prevent duplicate orders

---

### Phase 4 — E2E Verification

Full smoke verification uses `scripts/verify-local.sh`; `e2e-smoke-test.sh` is a compatibility wrapper for the same Postman-backed runner flow. It covers:
1. Auth: register → login → get JWT
2. Catalog: add brand → category → product
3. Cart: add item → update qty → verify total
4. Coupon: create → validate (active coupons)
5. Wishlist: add → move-to-cart
6. Order: create (with coupon) → confirm → verify payment
7. Inventory: verify stock reserved after order
8. Notification: verify notifications created
9. Review: verify purchase eligibility

The API Postman collection is also runner-safe for targeted application checks. It asserts that all non-retry requests return 2xx and verifies business outcomes for coupon orders, cancelled-order refunds, and review moderation before approved-review reads.

---

## File Change Map

### Phase 1 — Observability

```
inventory-service/pom.xml                            [add sleuth-zipkin + micrometer]
inventory-service/src/main/resources/application.properties  [add zipkin config]

notification-service/pom.xml                         [add sleuth-zipkin + micrometer]
notification-service/src/main/resources/application.properties [add zipkin config]

coupon-service/pom.xml                               [add sleuth-zipkin + micrometer]
coupon-service/src/main/resources/application.properties [add zipkin config]

review-service/pom.xml                               [add sleuth-zipkin + micrometer]
review-service/src/main/resources/application.properties [add zipkin config]

wishlist-service/pom.xml                             [add sleuth-zipkin + micrometer]
wishlist-service/src/main/resources/application.properties [add zipkin config]

docker-compose.yml                                   [remove profiles, enable zipkin, add kafka-ui]

observability/grafana/provisioning/datasources/prometheus.yaml  [CREATE]
observability/grafana/provisioning/dashboards/dashboard.yaml    [CREATE]
observability/grafana/dashboards/ecommerce-overview.json        [CREATE]
```

### Phase 2 — Advanced Integration

```
order-service/pom.xml                                [add openfeign for coupon/inventory]
order-service/src/main/java/.../clients/
  CouponFeignClient.java                             [CREATE]
  InventoryFeignClient.java                          [CREATE]
  fallback/CouponFeignClientFallback.java            [CREATE]
order-service/src/main/java/.../controller/
  OrderController.java                               [modify — add couponCode to request]
order-service/src/main/java/.../service/impl/
  OrderServiceImpl.java                              [modify — coupon validation + inventory check]

wishlist-service/src/main/java/.../clients/
  CartFeignClient.java                               [CREATE]
wishlist-service/src/main/java/.../service/impl/
  WishlistServiceImpl.java                           [modify — moveToCart calls cart Feign]
wishlist-service/pom.xml                             [add openfeign]
```

### Phase 3 — Production Hardening

```
order-service/src/main/resources/application.properties  [add resilience4j config]
inventory-service/src/main/java/.../events/
  OrderEventConsumer.java                            [modify — add DLQ error handler]
notification-service/src/main/java/.../events/
  *Consumer.java                                     [modify — add DLQ error handler]
```

---

## Checkout Flow (Advanced)

```
POST /order-service/order {addressId, couponCode?}
  │
  ├── CartFeignClient.getCheckoutSnapshot(userId)        → cart-service
  │     └── validate non-empty
  ├── CustomerFeignClient.getAddress(userId, addressId)  → customer-service
  ├── [if couponCode] CouponFeignClient.validate(code)   → coupon-service
  │     └── apply discount to orderTotal
  ├── [optional] InventoryFeignClient.checkStock(items)  → inventory-service
  │     └── warn if unavailable (non-blocking CB fallback)
  ├── Persist Order (status=PENDING, discount applied)
  ├── PaymentFeignClient.initiate(orderId, userId, total) → payment-service
  ├── CartFeignClient.clearCart(userId)
  └── Publish OrderPlacedEvent → Kafka (order-events)
        ├── inventory-service: reserve stock
        ├── notification-service: send order confirmation
        └── review-service: track purchase eligibility
```

---

## Kafka Event Flow

```
order-service      →  order-events topic
  OrderPlacedEvent    → inventory-service (reserve), notification-service, review-service
  OrderConfirmedEvent → notification-service
  OrderCancelledEvent → inventory-service (release), notification-service

payment-service    →  payment-events topic
  PaymentConfirmedEvent → notification-service
  PaymentFailedEvent    → notification-service

inventory-service  →  inventory-events topic
  StockReservedEvent    → order-service (update order status)
  StockUnavailableEvent → order-service (cancel order)
```

---

## Observability Access

| Tool | URL | Credentials |
|------|-----|-------------|
| Eureka Dashboard | http://localhost:8761 | — |
| Spring Boot Admin | http://localhost:9000 | admin/admin |
| Zipkin (traces) | http://localhost:9411 | — |
| Prometheus | http://localhost:9090 | — |
| Grafana | http://localhost:3000 | admin/admin |
| Kafka UI | http://localhost:8080 | — |
| API Gateway | http://localhost:8081 | JWT token |

---

## API Documentation (Swagger UI)

Each service exposes Swagger UI and OpenAPI spec:

| Service | Swagger UI | API Docs |
|---------|-----------|---------|
| auth-server | http://localhost:8083/swagger-ui.html | /v3/api-docs |
| catalog-service | http://localhost:8084/swagger-ui.html | /v3/api-docs |
| customer-service | http://localhost:8082/swagger-ui.html | /v3/api-docs |
| shopping-cart-service | http://localhost:8086/swagger-ui.html | /v3/api-docs |
| payment-service | http://localhost:8085/swagger-ui.html | /v3/api-docs |
| order-service | http://localhost:8092/swagger-ui.html | /v3/api-docs |
| inventory-service | http://localhost:8088/swagger-ui.html | /v3/api-docs |
| notification-service | http://localhost:8087/swagger-ui.html | /v3/api-docs |
| coupon-service | http://localhost:8089/swagger-ui.html | /v3/api-docs |
| review-service | http://localhost:8090/swagger-ui.html | /v3/api-docs |
| wishlist-service | http://localhost:8091/swagger-ui.html | /v3/api-docs |

All UIs include JWT Bearer auth — click **Authorize**, paste Bearer token from `/auth-service/login`.

---

## Load Tests (k6)

```bash
cd load-tests

# Smoke (quick sanity — 2 VUs, 1 min)
k6 run --env PRODUCT_ID=$PRODUCT_ID --env SCENARIO=smoke checkout-flow.js

# Load (20 VUs sustained)
k6 run --env PRODUCT_ID=$PRODUCT_ID --env SCENARIO=load checkout-flow.js

# Stress (ramps to 100 VUs)
k6 run --env PRODUCT_ID=$PRODUCT_ID --env SCENARIO=stress checkout-flow.js

# Catalog reads (Redis cache)
k6 run --env PRODUCT_ID=$PRODUCT_ID catalog-browse.js
```

Thresholds: p95 < 2s, error rate < 1%, order success > 95%.

---

## Build & Run

```bash
# 1. Build shared library
export JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home
cd platform-commons && mvn install -q && cd ..

# 2. Start full stack
docker-compose up -d --build

# 3. Verify all services registered in Eureka
curl http://localhost:8761/eureka/apps | grep -o '<app>.*</app>'

# 4. Run local verification
RUN_MAVEN_TESTS=0 scripts/verify-local.sh
```
