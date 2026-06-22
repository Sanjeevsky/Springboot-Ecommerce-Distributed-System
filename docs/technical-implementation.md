# Technical Implementation

This document covers the architecture, technology choices, service design, infrastructure, and patterns used across the Trove distributed system.

---

## Stack Summary

| Layer | Technology | Version |
|-------|-----------|---------|
| Language | Java | 11 |
| Core framework | Spring Boot | 2.5.7 |
| Service mesh | Spring Cloud | 2020.0.4 |
| Service discovery | Netflix Eureka | (Cloud 2020.0.4) |
| API Gateway | Spring Cloud Gateway | (Cloud 2020.0.4) |
| Config server | Spring Cloud Config | (Cloud 2020.0.4) |
| HTTP clients | OpenFeign | (Cloud 2020.0.4) |
| Fault tolerance | Resilience4j | 1.7.x |
| ORM | Spring Data JPA + Hibernate | 5.4.x |
| Messaging | Spring Kafka | 2.7.x (Kafka 2.8) |
| Search | Spring Data Elasticsearch | 4.2.7 |
| Cache | Spring Data Redis | 2.5.x |
| Tracing | Spring Cloud Sleuth + Zipkin | (Cloud 2020.0.4) |
| Metrics | Micrometer + Prometheus | 1.7.x |
| Database | MySQL | 8.0 |
| Message broker | Apache Kafka (KRaft) | 3.5 (Confluent 7.5.0) |
| Search engine | Elasticsearch | 7.13.4 (opensearch container) |
| Cache store | Redis | 7.2 |
| CDC | Debezium MySQL Connector | 2.4 |
| Build | Maven | 3.x |
| Container runtime | Docker Compose | 2.x |
| Frontend | React 18 + Vite 5 | — |

---

## Service Topology

```
                         ┌──────────────────┐
                         │   API Gateway    │ :8081
                         │ Spring Cloud GW  │
                         │  JWT validation  │
                         └────────┬─────────┘
                                  │
          ┌───────────────────────┼──────────────────────┐
          │                       │                      │
   ┌──────▼──────┐        ┌───────▼──────┐      ┌───────▼──────┐
   │auth-server  │        │catalog-service│      │customer-svc  │
   │    :8083    │        │    :8084      │      │    :8082     │
   └─────────────┘        └──────┬───────┘      └─────────────┘
                                 │
                   ┌─────────────┼──────────────┐
                   │             │              │
                MySQL       Elasticsearch     Redis
               product-       products        cart /
              catalog-db        index         catalog
                                              cache
```

```
Browser → Gateway → shopping-cart-service :8086
                  → order-service :8092 ──────────────────────┐
                                          │ Feign calls:       │
                                          ├─ cart-service      │ Kafka
                                          ├─ customer-service  │ Topics
                                          ├─ payment-service   │
                                          ├─ coupon-service    │
                                          └─ inventory-service─┘
```

---

## Services

### service-discovery (port 8761)

Netflix Eureka server. All application services register on startup and discover each other by logical name. Feign clients resolve service names through Eureka — no hardcoded URLs anywhere.

Start period: 180s (all dependent services wait for Eureka before marking themselves healthy).

### cloud-config (port 8071)

Spring Cloud Config Server in `native` mode — reads configuration from packaged classpath resources. Services import configuration at startup via `spring.config.import=optional:configserver:http://cloud-config:8071`. The `optional:` prefix means services fall back to their own `application-dev.properties` if the config server is unreachable.

### api-gateway (port 8081)

Spring Cloud Gateway with a custom `AuthenticationFilter`. Routes are defined by URL prefix and resolved via Eureka (`lb://service-name`).

**Authentication flow:**

1. `RouterValidator` checks whether the request path is in the open-endpoints list.
2. If secured: the filter validates the `Authorization: Bearer <token>` JWT.
3. Claims are extracted and the user's email is forwarded downstream as the `X-User` request header.
4. Downstream services trust `X-User` and never re-validate the JWT.

**Open endpoints** (no auth required):
- `/auth-service/signup`, `/auth-service/login`
- `/catalog-service/product/**`, `/catalog-service/getCategories`, `/catalog-service/getBrands`
- `/catalog-service/product/suggest`
- `/review-service/review/product/**`

### auth-server (port 8083)

Handles user registration and login. On successful login, issues a signed JWT containing the user's email as the `sub` claim. The signing secret (`JWT_SECRET`) is shared with the API gateway.

Passwords are stored as bcrypt hashes.

### catalog-service (port 8084)

The most complex service. Responsibilities:

- **CRUD** for products, categories, brands, and variants backed by MySQL
- **Redis caching** on `getProduct()` and `listProducts()` with `@Cacheable` / `@CacheEvict`
- **Elasticsearch** for full-text search and auto-suggest (see [Search Architecture](search-architecture.md))
- **Debezium CDC consumer** for real-time index sync from MySQL binlog
- **Startup bulk indexer** that pushes all products to ES on `ApplicationReadyEvent`

Key design: `@Document(indexName = "products", createIndex = false)` prevents Spring Data ES from connecting to Elasticsearch during bean initialization, so the service starts even when ES is unavailable.

### shopping-cart-service (port 8086)

Cart is stored in MySQL and optionally cached in Redis. Items hold a snapshot of product price at add-time (not live price) — this prevents price changes from silently affecting an in-progress cart.

`getCheckoutSnapshot()` returns a `CartSnapshot` with line items, per-item totals, and the cart grand total; this is consumed by order-service via Feign.

### order-service (port 8092)

Owns the order lifecycle and the saga orchestrator. Two checkout paths:

| Path | Endpoint | Behavior |
|------|----------|----------|
| Legacy (sync) | `POST /order` | Feign calls for payment inline; blocks until payment returns |
| Saga (async) | `POST /order/saga` | Returns 202 immediately; async saga runs over Kafka |

Feign calls are wrapped in per-method Resilience4j circuit breakers (e.g. `HardCodedTarget#initiatePayment(PaymentRequest)`), tuned in `CircuitBreakerConfiguration`. See [Workflows — Checkout Saga](workflows.md) for the full state machine and [docs/resilience-plan.md](resilience-plan.md) for the breaker tuning/metrics.

Idempotency: pass `Idempotency-Key: <uuid>` header to make order creation safe to retry.

### payment-service (port 8085)

Payment lifecycle: `PENDING → CHARGED → CONFIRMED | FAILED → REFUNDED`.

Listens to the `payment-commands` Kafka topic for saga-driven charge and refund commands. Also exposes REST endpoints for direct (non-saga) payment initiation.

Idempotency key is stored per payment record; a duplicate `ChargePaymentCommand` with the same key is a no-op.

### inventory-service (port 8088)

Tracks stock per product (and per variant). Listens to `order-events` for `StockReservationRequestedEvent` and `OrderCancelledEvent`. Publishes `StockReservedEvent` or `StockInsufficientEvent` to `inventory-events`.

**Reservation model:** `available = total - reserved`. On reservation, `reserved` increases. On order confirmation, `reserved` decreases and `total` decreases. On cancellation, `reserved` decreases only.

### coupon-service (port 8089)

CRUD for coupon codes with discount amount, expiry date, and usage limits. Validates a coupon against an order amount and marks it applied. Idempotent: applying the same coupon twice for the same order is a no-op.

### notification-service (port 8087)

Purely event-driven. Listens to `order-events` and `payment-events` and writes a `Notification` record to its own database. No business logic — only format and store. The frontend polls for unread notifications.

### review-service (port 8090)

Enforces purchase eligibility: a review submission is rejected unless the user has a confirmed order containing that product. This check is done by querying order-service (Feign) or by listening to `OrderConfirmedEvent` to build an eligibility table.

Submitted reviews start in `PENDING` status and require moderation before appearing publicly (`status = APPROVED`).

### wishlist-service (port 8091)

Simple CRUD backed by MySQL. `move-to-cart` calls shopping-cart-service via Feign to add the item and then removes it from the wishlist. Feign call failure rolls back the removal.

### customer-service (port 8082)

Address management. Addresses are referenced by `addressId` in order-service. The gateway injects `X-User` (email) so addresses are always scoped to the authenticated user.

---

## Database Design

Each service owns exactly one MySQL schema (database-per-service pattern). There are no cross-service foreign keys. Services reference each other by ID values carried in events or API responses.

| Schema | Owner service |
|--------|--------------|
| `auth-db` | auth-server |
| `product-catalog-db` | catalog-service |
| `customer-service-db` | customer-service |
| `shopping-cart-db` | shopping-cart-service |
| `order-service-db` | order-service |
| `payment-db` | payment-service |
| `inventory-db` | inventory-service |
| `coupon-db` | coupon-service |
| `notification-db` | notification-service |
| `review-db` | review-service |
| `wishlist-db` | wishlist-service |

Schema management: Hibernate `ddl-auto=update` in dev, `validate` in production.

MySQL is started with `--log-bin=mysql-bin --binlog-format=ROW --server-id=1` to enable Debezium CDC.

---

## Kafka Architecture

Kafka runs in KRaft mode (no Zookeeper) using Confluent Platform 7.5.0 (Kafka 3.5). A fixed `CLUSTER_ID` makes the configuration reproducible across `docker compose down` / `up` cycles.

### Topics

| Topic | Producer | Consumers | Message format |
|-------|----------|-----------|----------------|
| `order-events` | order-service | inventory-service, notification-service, review-service | JSON (Debezium-style op/payload) |
| `inventory-events` | inventory-service | order-service | JSON |
| `payment-commands` | order-service | payment-service | JSON |
| `payment-events` | payment-service | order-service, notification-service | JSON |
| `catalog.product-catalog-db.product` | Debezium (kafka-connect) | catalog-service (DebeziumCdcConsumer) | Debezium JSON (schemas disabled) |

All topics are auto-created. Replication factor: 1 (single-broker dev).

### Error Handling

All Kafka consumers use a `SeekToCurrentErrorHandler` with a `DeadLetterPublishingRecoverer`:

- Retry: 2 attempts with 1s backoff
- On exhaustion: message published to `<topic>-dlt` and original offset committed

Dead-letter topics: `order-events-dlt`, `payment-events-dlt`, `inventory-events-dlt`.

---

## Elasticsearch Integration

The `opensearch` container runs Elasticsearch 7.13.4.

### Index: `products`

| Field | ES Type | Analyzer | Use |
|-------|---------|----------|-----|
| `id` | `_id` | — | UUID string |
| `name` | `text` | standard | Full-text + suggestions |
| `description` | `text` | standard | Full-text |
| `brand` | `text` | standard | Full-text |
| `brandId` | `keyword` | — | Filter |
| `categoryId` | `keyword` | — | Filter |
| `categoryName` | `text` | standard | Full-text |
| `salePrice` | `double` | — | Display |
| `mrpPrice` | `double` | — | Display |
| `status` | `integer` | — | Filter (active = 1) |

1 shard, 0 replicas (single-node dev configuration).

`createIndex = false` on `@Document` prevents repository initialization from contacting ES on startup.

### Search Query

```
bool {
  filter: status = 1
  filter: categoryId = <uuid>     (optional)
  filter: brandId    = <uuid>     (optional)
  must:   multiMatch(q, [name, description, brand, categoryName], fuzziness=AUTO)
}
```

ES hits return a list of product UUIDs. The service then fetches full `Product` entities from MySQL via `findAllById()`.

Fallback: if ES is unreachable, the query degrades to `SELECT ... WHERE name LIKE %q% OR description LIKE %q%`.

### Suggest Query

```
match_phrase_prefix { field: "name", query: "<prefix>", max_expansions: 10 }
```

Returns a `List<String>` of matching product names. No auth required.

---

## Caching (Redis)

Redis 7.2 is used by catalog-service and shopping-cart-service.

| Cache | Key pattern | Eviction |
|-------|------------|---------|
| `product-cache` | `product::{id}` | On `updateProduct()`, `deleteProduct()` |
| `products-list-cache` | `products::list` | On any product write |
| `cart` | Per-user session | On add/update/remove/clear |

Cache is optional: set `SPRING_CACHE_TYPE=none` to disable Redis caching and fall back to database queries.

---

## Resilience Patterns

### Circuit Breaker (order-service)

With `feign.circuitbreaker.enabled=true`, Spring Cloud OpenFeign auto-wraps every Feign call in a Resilience4j breaker named per method (e.g. `HardCodedTarget#initiatePayment(PaymentRequest)`). The breakers are tuned by a Java `Customizer` in `CircuitBreakerConfiguration` (4s time limit + open-on-failure) — without it they ran on library defaults and never engaged. States: `CLOSED → OPEN → HALF_OPEN → CLOSED`; fallback methods return safe defaults (empty list, null payment, etc.). Each breaker's state is exported to Prometheus by `FeignCircuitBreakerStateMetrics` (`resilience4j_circuitbreaker_state`) with a `FeignCircuitBreakerOpen` alert. See [docs/resilience-plan.md](resilience-plan.md).

### Idempotency

| Operation | Key | Enforced by |
|-----------|-----|-------------|
| Order creation | `Idempotency-Key` header | order-service controller |
| Payment initiation | `Idempotency-Key` header | payment-service controller |
| Saga payment charge | `"saga-charge:{orderId}"` | stored in payment record |

### Saga Compensation

If payment fails after stock has been reserved, the saga publishes `OrderCancelledEvent` which triggers `inventory-service.releaseStock()` — the inverse of the reservation step. Orders end in `CANCELLED` status with the saga instance in `COMPENSATED` state. That path is driven by a payment *failure reply*; if a participant is instead *unreachable* (e.g. payment-service down, its command unconsumed), `SagaTimeoutReaper` compensates any saga parked in an in-flight state past `saga.timeout` so reserved stock is never leaked.

---

## Observability

### Structured JSON Logging

Every service outputs JSON lines via `logstash-logback-encoder 7.2`. The format is configured in `platform-commons/src/main/resources/logback-spring.xml` — picked up automatically by all services that have platform-commons on the classpath.

Each log line is a single JSON object:

```json
{
  "@timestamp": "2026-06-08T18:38:23.618Z",
  "level": "INFO",
  "logger": "com.sanjeevsky.catalogservice.controller.ProductCatalogController",
  "thread": "http-nio-8084-exec-10",
  "message": "List products request - page=0, size=20",
  "service": "catalog-service",
  "traceId": "2d963372bc91db2c",
  "spanId": "020ca600851d99ec",
  "correlationId": "validate-mdc-001",
  "userId": "user@example.com"
}
```

`traceId` and `spanId` come from Spring Cloud Sleuth. `correlationId` and `userId` come from the MDC filter (see below). Stack traces are included as a `stack_trace` string field (root cause first).

### Distributed MDC — Correlation ID Propagation

Every request and every async event carries a `correlationId` that links all log lines from the same logical operation across services.

**Components** (all in `platform-commons`, auto-configured via `spring.factories`):

| Component | Type | What it does |
|-----------|------|--------------|
| `CorrelationIdGatewayFilter` | `WebFilter` (api-gateway only) | Generates `X-Correlation-ID` UUID if absent; echoes it in the response header |
| `MdcFilter` | `OncePerRequestFilter` (servlet services) | Reads `X-Correlation-ID` and `X-User` headers; puts `correlationId`/`userId` in MDC for the request lifetime |
| `CorrelationIdFeignInterceptor` | `RequestInterceptor` | Copies `correlationId`/`userId` from MDC into outgoing Feign `X-Correlation-ID`/`X-User` headers |
| `KafkaMdcProducerInterceptor` | `ProducerInterceptor` | Injects `correlationId`/`userId` as Kafka message headers on every published record |
| `KafkaMdcConsumerInterceptor` | `RecordInterceptor` | Extracts those Kafka headers into MDC before `@KafkaListener` executes; clears on `success`/`failure` |

**Auto-configuration** (`MdcLoggingAutoConfiguration`):
- Nested `@Configuration` classes with class-level `@ConditionalOnClass` — only activated on services that have the required dependency (servlet, Feign, Kafka).
- `KafkaMdcConsumerInterceptor` is auto-detected by Spring Kafka 2.7.x's `ConcurrentKafkaListenerContainerFactory`.
- `KafkaMdcProducerInterceptor` is wired into `DefaultKafkaProducerFactory` via a static `BeanPostProcessor`.

**Why Kafka headers?** Sleuth propagates its own B3 trace headers through Kafka automatically. The Kafka MDC components carry `correlationId` (the business-level request ID) and `userId` through async event flows so log lines from an async consumer can be stitched to the HTTP request that originated the event.

**Key files:**
- `platform-commons/.../mdc/MdcConstants.java` — header and MDC key names
- `platform-commons/.../mdc/MdcFilter.java` — servlet MDC setup
- `platform-commons/.../mdc/CorrelationIdFeignInterceptor.java` — Feign propagation
- `platform-commons/.../mdc/KafkaMdcProducerInterceptor.java` — Kafka producer headers
- `platform-commons/.../mdc/KafkaMdcConsumerInterceptor.java` — Kafka consumer MDC
- `platform-commons/.../mdc/MdcLoggingAutoConfiguration.java` — Spring Boot auto-configuration
- `platform-commons/src/main/resources/META-INF/spring.factories` — auto-config registration
- `platform-commons/src/main/resources/logback-spring.xml` — JSON log format
- `api-gateway/.../filter/CorrelationIdGatewayFilter.java` — gateway correlation ID generation

### Distributed Tracing (Sleuth + Zipkin)

Spring Cloud Sleuth propagates `X-B3-TraceId` and `X-B3-SpanId` headers across all service-to-service calls (Feign and Kafka). Trace data is exported to Zipkin on port 9411.

Disabled by default in Docker (`SPRING_ZIPKIN_ENABLED=false`). Enable by setting the env var to `true`.

### Metrics

Every service exposes `/actuator/prometheus`. Prometheus (port 9090) scrapes all services on a 15s interval. Grafana (port 3000, admin/admin) ships pre-built dashboards for JVM heap, HTTP latency by route, and Kafka consumer lag.

### Health & Ops

Spring Boot Actuator `/actuator/health` is used for Docker health checks on every service. Spring Boot Admin (port 9000, optional) provides a web UI for heap, threads, environment, and log levels without SSH access.

---

## Inter-Service Communication

### Synchronous (Feign + Eureka)

Feign clients resolve service names through Eureka and load-balance across instances. All Feign calls in order-service are circuit-breaker wrapped.

```java
@FeignClient(name = "shopping-cart-service")
public interface CartFeignClient {
    @GetMapping("/cart-service/cart/checkout")
    CartSnapshot getCheckoutSnapshot(@RequestHeader("X-User") String user);
}
```

### Asynchronous (Kafka)

Events are plain JSON strings. Consumers use `StringDeserializer` and parse with Jackson. The `DebeziumCdcConsumer` in catalog-service detects the Debezium `schema`/`payload` wrapper and navigates accordingly.

---

## Frontend

React 18 + Vite 5 SPA.

**State:** `StoreContext.jsx` provides global cart, auth, and notification state via React Context + `useReducer`. No external state management library.

**Routing:** React Router DOM 6 with nested layouts. Protected routes redirect unauthenticated users to `/login`.

**API layer:** `src/lib/services.js` wraps all backend calls. Each service function:
- Calls the API gateway at the configured base URL
- Unwraps the standard `{ status, message, data }` envelope
- Returns the `data` payload directly
- Supports mock mode (`VITE_USE_MOCKS=true`) for frontend-only development

**Suggest UX:** `Header.jsx` debounces input (300ms), calls `/catalog-service/product/suggest`, shows a keyboard-navigable dropdown. Blur handling uses a 150ms delay to allow click events to register before the dropdown closes.

---

## Key Files Reference

| File | Purpose |
|------|---------|
| `docker-compose.yml` | Full stack definition (21 containers) |
| `api-gateway/.../filter/AuthenticationFilter.java` | JWT validation, X-User injection |
| `api-gateway/.../filter/RouterValidator.java` | Open endpoint list |
| `order-service/.../service/OrderSagaOrchestrator.java` | Saga state machine |
| `order-service/.../model/SagaInstance.java` | Saga entity |
| `catalog-service/.../search/ProductIndexer.java` | Startup bulk index |
| `catalog-service/.../search/DebeziumCdcConsumer.java` | CDC Kafka listener |
| `catalog-service/.../search/document/ProductDocument.java` | ES index mapping |
| `catalog-service/.../service/impl/ProductServiceImpl.java` | Search + suggest methods |
| `frontend/src/lib/services.js` | API service layer |
| `frontend/src/store/StoreContext.jsx` | Global frontend state |
| `observability/prometheus.yml` | Prometheus scrape config |

---

## Design Patterns in Use

| Pattern | Where applied |
|---------|--------------|
| Database-per-service | 11 separate MySQL schemas |
| API Gateway | JWT auth + routing in Spring Cloud Gateway |
| Service discovery | Eureka-backed Feign clients |
| Saga (orchestration) | Order checkout distributed transaction |
| Event-driven | Kafka topics for order/payment/inventory/notification flows |
| Circuit breaker | Resilience4j on all Feign calls in order-service |
| CQRS (read-model) | Elasticsearch as denormalized read model for catalog search |
| CDC | Debezium for real-time MySQL → Elasticsearch sync |
| Idempotency | Idempotency-Key headers on order and payment APIs |
| Dead-letter queue | `*-dlt` topics for poison message isolation |
| Fallback | MySQL LIKE fallback when Elasticsearch unavailable |
| Graceful degradation | `createIndex=false` + try-catch in `ProductIndexer` |
