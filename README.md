# Springboot Ecommerce Distributed System

A production-grade ecommerce platform built as a microservices architecture using Spring Boot 2.5.7, Spring Cloud 2020.0.4, Kafka, Redis, and MySQL.

[![Architecture](https://img.shields.io/badge/view-architecture-blue)](https://sanjeevsky.github.io/Springboot-Ecommerce-Distributed-System/architecture.html)

---

## Documentation

| Doc | What it covers |
|-----|---------------|
| **[Product Overview](docs/product-overview.md)** | What Trove does — user-facing features, admin capabilities, pages, and key product properties |
| **[Technical Implementation](docs/technical-implementation.md)** | Full stack, service-by-service design, Kafka topics, Elasticsearch, Redis, resilience patterns, observability |
| **[Workflows](docs/workflows.md)** | Step-by-step sequence diagrams for every major flow: auth, search, cart, saga checkout, payment, CDC sync, notifications, reviews |
| **[Checkout Saga](docs/SAGA.md)** | Orchestration-based saga deep-dive: state machine, compensation path, and a live run transcript |
| **[Search Architecture](docs/search-architecture.md)** | Elasticsearch index, search/suggest queries, and three CDC sync paths (startup, inline, Debezium) |
| **[End-to-End Workflow](docs/WORKFLOW.md)** | Legacy narrative walkthrough: login → browse → cart → checkout → fulfillment |

---

## Architecture

[Live Architecture Diagram](https://sanjeevsky.github.io/Springboot-Ecommerce-Distributed-System/architecture.html)

**15 Spring services** plus platform infrastructure across 3 tiers:

| Tier | Services |
|------|----------|
| Infrastructure | MySQL 8.0, Redis 7.2, Kafka 3.5 (KRaft), Kafka Connect (Debezium CDC), Elasticsearch 7.13, Kafka UI, Zipkin, Prometheus, Grafana |
| Spring Cloud | Eureka (service-discovery), Config Server (cloud-config), Spring Boot Admin (spring-server), API Gateway |
| Business | auth, catalog, customer, order, shopping-cart, payment, inventory, notification, review, wishlist, coupon |

---

## Services

| Service | Port | Description |
|---------|------|-------------|
| frontend | 4173 | Trove storefront and admin Studio — React (Vite) SPA served by nginx, proxies `/api/*` to the gateway |
| service-discovery | 8761 | Eureka server — service registry |
| cloud-config | 8071 | Spring Cloud Config Server |
| spring-server | 9000 | Spring Boot Admin UI |
| kafka-ui | 8080 | Kafka topics, brokers, and consumer groups UI |
| api-gateway | 8081 | JWT auth + explicit standard-prefix routing to all services |
| auth-server | 8083 | Register / login / JWT issuance |
| catalog-service | 8084 | Products, categories, brands (Redis cached); full-text search + auto-suggest (Elasticsearch); real-time index sync via Debezium CDC |
| customer-service | 8082 | Customer profiles, address management |
| order-service | 8092 | Order lifecycle — create / confirm / cancel |
| shopping-cart-service | 8086 | Cart CRUD + checkout snapshot |
| payment-service | 8085 | Initiate / confirm / refund payments |
| inventory-service | 8088 | Stock management + reservation via Kafka |
| notification-service | 8087 | Persists order & payment events as notifications |
| review-service | 8090 | Reviews gated by purchase eligibility |
| wishlist-service | 8091 | Wishlist + move-to-cart |
| coupon-service | 8089 | Coupon create / validate / apply |

---

## Kafka Event Flows

```
order-service    ──► order-events ──► inventory-service
                                  ──► notification-service
                                  ──► review-service

order-service    ──► payment-commands ──► payment-service          (saga charge/refund)

payment-service  ──► payment-events ──► notification-service
                                    ──► order-service              (saga reply)

inventory-service ─► inventory-events ─► order-service
```

The distributed checkout is coordinated by an orchestration-based **[saga](docs/SAGA.md)**
(`reserve stock → charge payment → confirm order`, with compensation on failure).

Kafka consumers retry failed records twice with a 1 second backoff, then publish the original record to a dead-letter topic:

| Source topic | Dead-letter topic |
|--------------|-------------------|
| `order-events` | `order-events-dlt` |
| `payment-events` | `payment-events-dlt` |
| `inventory-events` | `inventory-events-dlt` |

---

## Tech Stack

- **Java 11**, Spring Boot 2.5.7, Spring Cloud 2020.0.4
- **API Gateway**: Spring Cloud Gateway + JWT authentication
- **Service Discovery**: Netflix Eureka
- **Config**: Spring Cloud Config Server
- **Messaging**: Apache Kafka 3.5 — KRaft mode (no ZooKeeper), Confluent CP 7.5.0
- **CDC**: Debezium MySQL Connector 2.4 via Kafka Connect
- **Search**: Elasticsearch 7.13 — full-text search + auto-suggest + Spring Data Elasticsearch
- **Caching**: Redis 7.2 — catalog product cache + cart session
- **Database**: MySQL 8.0 (one schema per service, 11 total)
- **Resilience**: Resilience4j circuit breakers + Feign fallbacks + DLT retry
- **Logging**: Logstash JSON (logstash-logback-encoder 7.2) — structured JSON on every service; MDC fields: `traceId`, `spanId`, `correlationId`, `userId`, `service`
- **MDC propagation**: `X-Correlation-ID` generated at gateway, forwarded through Feign headers and Kafka message headers; auto-configured via `platform-commons` spring.factories
- **Tracing**: Spring Cloud Sleuth + Zipkin
- **Metrics**: Micrometer + Prometheus + Grafana
- **Monitoring**: Spring Boot Admin

---

## Quick Start

### Prerequisites
- Docker + Docker Compose
- Java 11 (`/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` on Mac)
- Maven 3.8+

The local build and verification scripts prefer that Java 11 path automatically when it exists, even if your shell default is a newer JDK. Set `MAVEN_JAVA_HOME` to override the Maven runtime explicitly.

### 1. Build all services

```bash
scripts/build-docker-jars.sh
```

For a focused rebuild before Docker, pass module names:

```bash
scripts/build-docker-jars.sh api-gateway order-service
```

### 2. Run the full stack

```bash
docker compose up -d
```

### 3. Access services

| UI | URL |
|----|-----|
| Trove Storefront | http://localhost:4173 |
| Trove Studio | http://localhost:4173/studio |
| Eureka Dashboard | http://localhost:8761 |
| Spring Boot Admin | http://localhost:9000 (client / client; optional `platform-tools` profile) |
| Kafka UI | http://localhost:8080 |
| Zipkin Traces | http://localhost:9411 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |
| API Gateway | http://localhost:8081 |

The default `docker compose up -d` stack starts the application services plus Kafka UI, Zipkin, Prometheus, and Grafana. Spring Boot Admin is optional; start it with admin client registration enabled when you need the UI:

```bash
SPRING_BOOT_ADMIN_CLIENT_ENABLED=true docker compose --profile platform-tools up -d
```

Zipkin runs in the Docker stack and service tracing is **enabled by default** — every service reports B3 spans (100% sampling), so a `traceId` from the logs is searchable in Zipkin at http://localhost:9411. If you need to trim memory for a constrained machine, disable it with `SPRING_ZIPKIN_ENABLED=false docker compose up -d`.

Auth tokens are signed by `auth-server` and verified by `api-gateway`; both read the same `JWT_SECRET` value. Docker Compose defaults it to `local-dev-secret` for local runs, and you can override it before starting the stack.
Docker Compose also enables an idempotent local Studio administrator by default: `admin@trove.local` / `admin123`. Override these with `ADMIN_SEED_EMAIL` and `ADMIN_SEED_PASSWORD`, or set `ADMIN_SEED_ENABLED=false` on `auth-server` to disable seeding. Change the default password outside disposable local environments.
Local infrastructure credentials keep development defaults but are overridable: `MYSQL_ROOT_PASSWORD` controls Docker MySQL and service datasource passwords, `MYSQL_PASSWORD` controls direct local service properties, and Spring Boot Admin uses `SPRING_SECURITY_USER_NAME` / `SPRING_SECURITY_USER_PASSWORD` for the server login plus `SPRING_BOOT_ADMIN_CLIENT_USERNAME` / `SPRING_BOOT_ADMIN_CLIENT_PASSWORD` for service registration. When overriding Spring Boot Admin in Docker, set the server and client username variables to the same value, and set the server and client password variables to the same value, so service registration continues to authenticate.
Docker Compose disables the Spring Cloud Config client for app containers so the local stack uses packaged service properties and does not try to resolve `localhost:8071` from inside containers. The `cloud-config` service still runs for manual config-server checks in native local mode, without cloning the remote config repository.
Application containers wait for `service-discovery` and `cloud-config` health before startup so Eureka registration is less race-prone during cold starts.
Application services default to the `dev` profile for Eureka-backed local runs; Docker Compose also sets `SPRING_PROFILES_ACTIVE=dev` explicitly.
`customer-service/src/main/resources/application-local.properties` is an opt-in isolated profile for direct customer-service debugging; it disables Eureka and is not used by Docker Compose or smoke verification.

The API Gateway uses explicit standard-prefix routes such as `/cart-service/**`, `/order-service/**`, and `/catalog-service/**`. Spring Cloud Gateway discovery-locator routes are disabled so raw service-id paths such as `/shopping-cart-service/**` are not exposed alongside the standard application routes.

**Frontend development:** the dockerized storefront on :4173 serves the production build. For hot-reload development run `cd frontend && npm install && npm run dev` — the Vite dev server on :5173 proxies `/api` to the gateway on :8081 (see `frontend/.env.example` for config).

Kafka broker endpoints:

| Use case | Endpoint |
|----------|----------|
| Docker-internal services and Kafka UI | `kafka:29092` |
| Host machine clients and CLI tools | `localhost:9092` |

---

## Testing Guide

This is the full runbook to bring the platform up and exercise **every business flow** end to end. Follow it top to bottom.

### Step 0 — Prerequisites (one time)

- Docker Desktop running (needs ~6–8 GB free for the full stack)
- Java 11 at `/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` (auto-detected; set `MAVEN_JAVA_HOME` to override)
- `node` (for the validators) and `newman` (`npm i -g newman`, for the Postman flows)

### Step 1 — Build the service jars

The Dockerfiles copy pre-built `target/*.jar`, so package them first. Re-run this whenever you change Java code.

```bash
scripts/build-docker-jars.sh
```

### Step 2 — Start the full stack

```bash
docker compose up -d
```

This starts 21 containers: all 13 business services, Eureka, Config Server, API Gateway, plus MySQL, Redis, Kafka, Kafka UI, Zipkin, Prometheus, and Grafana. Services take ~30–60s to register with Eureka before routes are live.

### Step 3 — Run every flow (one command)

```bash
scripts/verify-local.sh
```

This is the authoritative end-to-end check. It waits for readiness (per-service health, Eureka registry, gateway route table, auth guards), then runs all three Postman collections through the gateway, and finally re-runs the Maven test suites. **A clean pass looks like this:**

| Phase | Expected result |
|-------|-----------------|
| Health + Eureka + gateway checks | all pass, no timeouts |
| API reference flow | 99 requests · **0 failed assertions** |
| Data seed flow | 35 requests · **0 failed assertions** |
| Complete E2E flow | 97 requests · **0 failed assertions** |
| Maven module tests | ~544 tests · **0 failures** |
| Exit code | `0` |

To run the live flows **without** re-running the unit tests (much faster — the live Postman flows are what actually exercise the running stack):

```bash
RUN_MAVEN_TESTS=0 scripts/verify-local.sh
```

### What the E2E flow covers

The complete E2E collection walks the real cross-service path through the gateway, asserting behavior at each step:

| # | Flow | What it verifies |
|---|------|------------------|
| 00 | **Auth** | Signup → customer login → password update → admin login |
| 01 | **Catalog** | Admin create/update/list brand, category, subcategory, product, and variant; public list/search/get-by-id |
| 02 | **Inventory** | Admin seed and set product + variant stock, list inventory, read availability |
| 03 | **Customer** | Address create / read / list / update / delete |
| 04 | **Cart** | Add items, update qty, checkout snapshot, remove, clear |
| 05 | **Wishlist** | Add, list, **move-to-cart** (cross-service Feign call), remove |
| 06 | **Coupon** | Create, list, validate, apply discount |
| 07 | **Order** | Create from cart, coupon discount applied, confirm, cancel, **insufficient-stock rejection (400)**, idempotency, admin analytics |
| 08 | **Payment** | Initiate, confirm, status, refund, **fail lifecycle**, idempotency |
| 09 | **Review** | Submit (purchase-gated), moderate → APPROVED, read approved reviews |

Kafka event propagation (`order-events`, `payment-events`, `inventory-events`) is exercised implicitly — the inventory, notification, and review consumers react to the orders and payments created above.

### Running a single flow / subset

```bash
# Skip the Maven suites, keep the live Postman flows
RUN_MAVEN_TESTS=0 scripts/verify-local.sh

# Run just the complete E2E flow (skip the others)
RUN_MAVEN_TESTS=0 RUN_API_COLLECTION=0 RUN_DATA_SEED_COLLECTION=0 scripts/verify-local.sh

# Run one collection directly against the running stack
newman run postman/Ecommerce-E2E-Complete.postman_collection.json \
  -e postman/Ecommerce-Local.postman_environment.json
```

For the full list of toggles (`RUN_POSTMAN`, `RUN_PLATFORM_ENDPOINT_CHECKS`, `GATEWAY_DISCOVERY_STABILIZE_SECONDS`, …) see [Verification](#verification) below.

### Manual spot-check (curl)

To sanity-check the gateway and auth by hand:

```bash
# Public route — product list (no token needed)
curl -s http://localhost:8081/catalog-service/product/list

# Register + login, capture the JWT
curl -s -X POST http://localhost:8081/auth-service/signup \
  -H 'Content-Type: application/json' \
  -d '{"name":"Test","email":"test@example.com","password":"pass1234"}'

TOKEN=$(curl -s -X POST http://localhost:8081/auth-service/login \
  -H 'Content-Type: application/json' \
  -d '{"email":"test@example.com","password":"pass1234"}' \
  | node -e 'process.stdin.on("data",d=>{const j=JSON.parse(d);console.log(j.data?.token||j.token)})')

# Authenticated route
curl -s http://localhost:8081/cart-service/cart -H "Authorization: Bearer $TOKEN"
```

A protected route called **without** a token must return `401`; the raw `/shopping-cart-service/**` service-id path must return `404` (only the standard `/cart-service/**` prefix is exposed).

### Step 4 — Tear down

```bash
docker compose down                          # stops the default stack
docker compose --profile platform-tools down # also removes the optional Boot Admin container + network
```

---

## API Reference

Import the Postman collection from `postman/` for all endpoints.

Postman collections:

| Collection | Purpose |
|------------|---------|
| `Ecommerce-API.postman_collection.json` | Runner-safe endpoint reference/application collection with global non-2xx checks |
| `Ecommerce-DataSeed.postman_collection.json` | Runner-safe seed flow for local catalog, order, payment, approved review, wishlist, notification, and inventory data |
| `Ecommerce-E2E-Complete.postman_collection.json` | Runner-safe application E2E flow |
| `Ecommerce-Local.postman_environment.json` | Local gateway environment with only `baseUrl` |

The collections use collection-level scripts to generate and save run values such as token, IDs, coupon code, admin credentials, and idempotency keys into collection variables and the active runner environment. The local environment file intentionally provides only the gateway `baseUrl`; runtime state belongs to the collection scripts so Collection Runner and Newman can execute from a clean environment. Each full runner logs in as the seeded admin for catalog/inventory setup, seeds both product-level and variant-level stock, then restores the generated customer login before cart, address, order, review, and wishlist flows. Kafka-backed review eligibility and notification requests include runner retries so asynchronous consumers can catch up before later requests use `reviewId` or `notificationId`. The DataSeed collection moderates and verifies an approved review, and the application collections fail unexpected non-2xx responses while asserting key semantics such as coupon discounts, cancelled-order refunds, insufficient-inventory checkout rejection, and review moderation before approved-review reads.

### Authentication

```
POST /auth-service/signup
POST /auth-service/login         → returns JWT token and CUSTOMER/ADMIN role
```

Signup, login, and catalog product browsing routes are public:

```
GET /catalog-service/product/list
GET /catalog-service/product/search
GET /catalog-service/product/getProduct/{id}
```

All other standard gateway routes, including `/auth-service/updatePassword`, require `Authorization: Bearer <token>`.

The gateway derives `X-User` and `X-User-Role` from the signed JWT and removes client-supplied identity headers. Catalog writes and inventory management require an `ADMIN` token:

```
GET    /catalog-service/product/admin/list
PUT    /catalog-service/product/{id}
DELETE /catalog-service/product/{id}
PUT    /catalog-service/variant/{variantId}
DELETE /catalog-service/variant/{variantId}
GET    /inventory-service/stock
PUT    /inventory-service/stock/{productId}
PUT    /inventory-service/stock/{productId}/variant/{variantId}
POST   /coupon-service/coupon
GET    /coupon-service/admin/coupons
PUT    /coupon-service/coupon/{couponId}/active?active=true|false
```

The Studio at `http://localhost:4173/studio` uses these routes for catalog editing, variant management, product retirement, absolute stock updates, and coupon lifecycle management. Analytics, product, inventory, and coupon views can export their current data as formula-safe CSV files.

Admin analytics are exposed through the same standard gateway prefix:

```
GET /order-service/analytics/summary?from=yyyy-MM-dd&to=yyyy-MM-dd
GET /order-service/analytics/daily?days=30
GET /order-service/analytics/top-products?limit=10
```

The summary range is inclusive. Revenue and top-product sales include `CONFIRMED`, `SHIPPED`, and `DELIVERED` orders; pending and cancelled orders remain visible in order counts and the status breakdown without inflating recognized revenue. Top-product revenue allocates each order's net total across its line items, so discounts reconcile with summary revenue. The Studio overview provides Today, 7 days, and 30 days ranges plus daily revenue, status mix, top products, and low-stock alerts.

### Order flow

```
POST   /order-service/order           → create order from cart
PUT    /order-service/order/{id}/confirm
PUT    /order-service/order/{id}/cancel
GET    /order-service/order/{id}
GET    /order-service/orders
```

`POST /order-service/order` and `POST /payment-service/initiate` accept an optional `Idempotency-Key` header. Retries with the same key for the same user return the existing order/payment and do not replay side effects such as payment initiation, cart clearing, coupon application, or Kafka event publication. Reusing a key for a different order address/coupon, payment order, or payment amount returns `400 Bad Request`. The Postman runner collections generate per-run idempotency keys automatically.

Order creation pre-checks the current inventory entry for the exact product variant in the cart and returns `400 Bad Request` with an insufficient-stock message when requested quantity exceeds available stock.

---

## Verification

Static validation for Postman collections and service configuration:

```bash
node scripts/validate-postman.js
node scripts/validate-service-config.js
python3 generate-arch.py --check
bash -n scripts/verify-local.sh scripts/build-docker-jars.sh e2e-smoke-test.sh
docker compose config --quiet
docker compose --profile platform-tools config --quiet
```

Full local verification:

```bash
scripts/verify-local.sh
```

By default this runs the API reference collection, the DataSeed collection, and the complete E2E collection after local health, platform endpoint, per-app and aggregate Eureka registry, live gateway route-table, gateway route readiness, and gateway auth-guard checks across all standard service prefixes. Readiness includes `cloud-config` health, `CONFIGSERVER` Eureka registration, and Kafka UI, Zipkin, Prometheus, and Grafana endpoints.
The Maven phase runs `clean test` to avoid stale deleted classes; run `scripts/build-docker-jars.sh` again before any Docker image rebuild that needs fresh `target/*.jar` files.
If a health or Eureka readiness check times out, the script prints the last HTTP response or a compact Eureka registry snapshot so missing registrations are visible without opening the dashboard manually.

Legacy smoke entrypoint:

```bash
./e2e-smoke-test.sh
```

`e2e-smoke-test.sh` delegates to the Postman-backed local verifier with Maven tests skipped by default.

Useful switches:

```bash
RUN_POSTMAN=0 scripts/verify-local.sh       # skip Docker-stack Postman runner checks
RUN_API_COLLECTION=0 scripts/verify-local.sh # skip API reference collection, keep DataSeed + E2E
RUN_DATA_SEED_COLLECTION=0 scripts/verify-local.sh # skip DataSeed collection
RUN_E2E_COLLECTION=0 scripts/verify-local.sh # skip complete E2E collection
RUN_PLATFORM_ENDPOINT_CHECKS=0 scripts/verify-local.sh # skip Kafka UI, Zipkin, Prometheus, and Grafana endpoint checks
RUN_MAVEN_TESTS=0 scripts/verify-local.sh   # skip Maven module tests
MAVEN_JAVA_HOME=/path/to/jdk11 scripts/verify-local.sh # override Maven Java runtime
GATEWAY_DISCOVERY_STABILIZE_SECONDS=0 scripts/verify-local.sh  # disable post-Eureka gateway cache wait
```

Local runner checks after the Docker stack is up:

```bash
newman run postman/Ecommerce-API.postman_collection.json \
  -e postman/Ecommerce-Local.postman_environment.json \
  --bail failure \
  --timeout-request 30000 \
  --delay-request 100

newman run postman/Ecommerce-DataSeed.postman_collection.json \
  -e postman/Ecommerce-Local.postman_environment.json

newman run postman/Ecommerce-E2E-Complete.postman_collection.json \
  -e postman/Ecommerce-Local.postman_environment.json
```

Targeted Java test example:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -B -f auth-server/pom.xml clean test -DfailIfNoTests=false "-Dtest=!*ApplicationTests"
```

GitHub Actions runs static validation and Java 11 module tests on pushes and pull requests.

### Resilience / chaos testing

`scripts/chaos.sh` injects faults into the running Docker stack to validate that the
system degrades gracefully and recovers — circuit breakers (order-service → cart /
payment / coupon) and the orchestration saga's compensation path. Faults are applied
by docker-compose service name, so the harness needs no container-name bookkeeping.

```bash
scripts/chaos.sh kill payment-service    # SIGKILL — prove the circuit breaker opens
scripts/chaos.sh pause inventory-service # freeze the process — prove the saga compensates
scripts/chaos.sh slow catalog-service 400 # 400ms egress latency via a tc/netem sidecar
scripts/chaos.sh status                  # current state of the business services
scripts/chaos.sh restore                 # undo every fault on all known services
```

Run the `load-tests/` k6 scenarios alongside it to observe the blast radius in Grafana.
See [docs/resilience-plan.md](docs/resilience-plan.md) for the steady-state hypotheses
each fault probes and the phased rollout (PR A here is the harness).

---

## Project Structure

```
.
├── platform-commons/          # Shared DTOs, events, ApiResponse
├── api-gateway/               # JWT filter + explicit routes backed by Eureka load balancing
├── service-discovery/         # Eureka server
├── cloud-config/              # Config server
├── spring-server/             # Boot Admin
├── auth-server/               # Auth (port 8083)
├── catalog-service/           # Products (port 8084)
├── customer-service/          # Profiles + addresses (port 8082)
├── order-service/             # Orders (port 8092)
├── shopping-cart-service/     # Cart (port 8086)
├── payment-service/           # Payments (port 8085)
├── inventory-service/         # Stock (port 8088)
├── notification-service/      # Notifications (port 8087)
├── review-service/            # Reviews (port 8090)
├── wishlist-service/          # Wishlists (port 8091)
├── coupon-service/            # Coupons (port 8089)
├── observability/             # prometheus.yml
├── postman/                   # API collections + environment
├── architecture.html          # Interactive architecture diagram
└── docker-compose.yml
```

---

## Test Coverage

| Service | Unit Tests | Integration Tests |
|---------|-----------|-------------------|
| api-gateway | 19 | — |
| auth-server | 27 | 10 |
| catalog-service | 114 | — |
| customer-service | 27 | — |
| order-service | 72 | — |
| payment-service | 42 | 15 |
| shopping-cart-service | 31 | 8 |
| coupon-service | 37 | 12 |
| review-service | 36 | 10 |
| wishlist-service | 17 | 10 |
| inventory-service | 51 | 8 |
| notification-service | 16 | 8 |
| **Total** | **489** | **81** |
