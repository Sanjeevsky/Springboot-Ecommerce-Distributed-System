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
| service-discovery | 8761 | ✅ | ✅ | ✅ | — | — | ✅ |
| cloud-config | 8071 | ✅ | ✅ | ✅ | — | — | ✅ |
| spring-server | 9000 | — | — | — | — | — | ✅ |
| api-gateway | 8081 | ✅ | ✅ | ✅ | — | — | ✅ |
| auth-server | 8083 | ✅ | ✅ | ✅ | — | — | ✅ |
| catalog-service | 8084 | ✅ | ✅ | ✅ | — | — | ✅ |
| customer-service | 8082 | ✅ | ✅ | ✅ | — | feign | ✅ |
| shopping-cart-service | 8086 | ✅ | ✅ | ✅ | — | feign | ✅ |
| payment-service | 8085 | ✅ | ✅ | ✅ | pub | — | ✅ |
| order-service | 8092 | ✅ | ✅ | ✅ | pub/cons | feign | ✅ |
| inventory-service | 8088 | ✅ | ✅ | ✅ | pub/cons | — | ✅ |
| notification-service | 8087 | ✅ | ✅ | ✅ | cons | — | ✅ |
| coupon-service | 8089 | ✅ | ✅ | ✅ | — | — | ✅ |
| review-service | 8090 | ✅ | ✅ | ✅ | cons | — | ✅ |
| wishlist-service | 8091 | ✅ | ✅ | ✅ | — | ✅ | ✅ |

---

## Advanced Implementation Phases

### Phase 1 — Complete Observability

**1.1 Add observability Maven deps (completed for all business services)**

All business services include:
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

**1.2 Standardize application.properties (completed)**

Business services include:
```properties
spring.zipkin.baseUrl=http://localhost:9411/
spring.zipkin.enabled=false            # overridden to true in docker
spring.sleuth.sampler.probability=1.0
management.endpoint.health.show-details=always
management.endpoints.web.exposure.include=*
```

**1.3 Docker-compose (completed)**
- Observability starts with the default stack.
- Business services run by default.
- Docker keeps Zipkin available but leaves service tracing disabled by default; set `SPRING_ZIPKIN_ENABLED=true` to emit spans.
- Docker disables Spring Cloud Config clients for app containers so local startup uses packaged service properties instead of resolving `localhost:8071` inside containers.
- Docker overrides point Kafka clients at `kafka:29092`.
- Kafka UI is available at `http://localhost:8080`.
- Grafana provisioning is mounted from `observability/grafana`.

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

Inventory, order, notification, and review consumers use retry + dead-letter handling. Consumer processing failures are rethrown to the listener container and published to `{topic}-dlt` after bounded retries.

**3.3 Idempotency Keys**

Order-service: unique constraint on `(userId, idempotencyKey)` to prevent duplicate orders

---

### Phase 4 — E2E Verification

Full smoke verification uses `scripts/verify-local.sh`; `e2e-smoke-test.sh` is a compatibility wrapper for the same Postman-backed runner flow. By default it runs the API reference collection, the DataSeed collection, and the complete E2E collection. It covers:
1. Auth: register → login → get JWT
2. Catalog: add brand → category → product
3. Cart: add item → update qty → verify total
4. Coupon: create → validate (active coupons)
5. Wishlist: add → move-to-cart
6. Order: create (with coupon) → confirm → verify payment
7. Inventory: verify stock reserved after order
8. Notification: verify notifications created
9. Review: verify purchase eligibility, moderation, and approved-review reads

The DataSeed collection leaves an approved review available for the seeded product. The API Postman collection is runner-safe for targeted application checks and is included in the default verifier. It asserts that all non-retry requests return 2xx and verifies business outcomes for coupon orders, cancelled-order refunds, and review moderation before approved-review reads.

---

## File Change Map

### Phase 1 — Observability

```
inventory-service/pom.xml                            [done: sleuth-zipkin + micrometer]
inventory-service/src/main/resources/application.properties  [done: zipkin config]

notification-service/pom.xml                         [done: sleuth-zipkin + micrometer]
notification-service/src/main/resources/application.properties [done: zipkin config]

coupon-service/pom.xml                               [done: sleuth-zipkin + micrometer]
coupon-service/src/main/resources/application.properties [done: zipkin config]

review-service/pom.xml                               [done: sleuth-zipkin + micrometer]
review-service/src/main/resources/application.properties [done: zipkin config]

wishlist-service/pom.xml                             [done: sleuth-zipkin + micrometer]
wishlist-service/src/main/resources/application.properties [done: zipkin config]

docker-compose.yml                                   [done: default observability, zipkin, kafka-ui]

observability/grafana/provisioning/datasources/prometheus.yaml  [done]
observability/grafana/provisioning/dashboards/dashboard.yaml    [done]
observability/grafana/dashboards/ecommerce-overview.json        [done]
```

### Phase 2 — Advanced Integration

```
order-service/pom.xml                                [done: openfeign for coupon/inventory]
order-service/src/main/java/.../clients/
  CouponFeignClient.java                             [done]
  InventoryFeignClient.java                          [done]
  fallback/CouponFeignClientFallback.java            [done]
order-service/src/main/java/.../controller/
  OrderController.java                               [done: couponCode request support]
order-service/src/main/java/.../service/impl/
  OrderServiceImpl.java                              [done: coupon validation + inventory check]

wishlist-service/src/main/java/.../clients/
  CartFeignClient.java                               [done]
wishlist-service/src/main/java/.../service/impl/
  WishlistServiceImpl.java                           [done: moveToCart calls cart Feign]
wishlist-service/pom.xml                             [done: openfeign]
```

### Phase 3 — Production Hardening

```
order-service/src/main/resources/application.properties  [done: resilience4j config]
inventory-service/src/main/java/.../events/
  OrderEventConsumer.java                            [done: retry/DLQ handler]
order-service/src/main/java/.../events/
  InventoryEventConsumer.java                        [done: retry/DLQ handler]
notification-service/src/main/java/.../events/
  *Consumer.java                                     [done: retry/DLQ handler]
review-service/src/main/java/.../kafka/
  OrderEventConsumer.java                            [done: retry/DLQ handler]
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
  │     └── reject insufficient exact-variant stock; warn and continue if unavailable
  ├── Persist Order (status=PENDING, discount applied)
  ├── PaymentFeignClient.initiate(orderId, userId, total) → payment-service
  ├── CartFeignClient.clearCart(userId)
  └── Publish OrderPlacedEvent → Kafka (order-events)
        ├── inventory-service: reserve stock
        └── notification-service: send order placed notification
```

---

## Kafka Event Flow

```
order-service      →  order-events topic
  OrderPlacedEvent    → inventory-service (reserve), notification-service
  OrderConfirmedEvent → notification-service, review-service (purchase eligibility)
  OrderCancelledEvent → inventory-service (release), notification-service

payment-service    →  payment-events topic
  PaymentConfirmedEvent → notification-service
  PaymentFailedEvent    → notification-service

inventory-service  →  inventory-events topic
  StockReservedEvent     → order-service (leave order PENDING until explicit confirmation)
  StockInsufficientEvent → order-service (cancel order)
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

Each service hosts Swagger UI and an OpenAPI spec on its local port. The OpenAPI server metadata advertises only the API Gateway (`http://localhost:8081`), so Swagger "Try it out" requests use the standard gateway routes instead of direct service-port endpoints.

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
