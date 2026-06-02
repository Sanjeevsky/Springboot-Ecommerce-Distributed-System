# Springboot Ecommerce Distributed System

A production-grade ecommerce platform built as a microservices architecture using Spring Boot 2.5.7, Spring Cloud 2020.0.4, Kafka, Redis, and MySQL.

[![Architecture](https://img.shields.io/badge/view-architecture-blue)](https://sanjeevsky.github.io/Springboot-Ecommerce-Distributed-System/architecture.html)

---

## Architecture

[Live Architecture Diagram](https://sanjeevsky.github.io/Springboot-Ecommerce-Distributed-System/architecture.html)

**15 services** across 3 tiers:

| Tier | Services |
|------|----------|
| Infrastructure | MySQL, Redis, Kafka + Zookeeper, Kafka UI, Zipkin, Prometheus, Grafana |
| Spring Cloud | Eureka (service-discovery), Config Server (cloud-config), Spring Boot Admin (spring-server), API Gateway |
| Business | auth, catalog, customer, order, shopping-cart, payment, inventory, notification, review, wishlist, coupon |

---

## Services

| Service | Port | Description |
|---------|------|-------------|
| service-discovery | 8761 | Eureka server — service registry |
| cloud-config | 8071 | Spring Cloud Config Server |
| spring-server | 9000 | Spring Boot Admin UI |
| kafka-ui | 8080 | Kafka topics, brokers, and consumer groups UI |
| api-gateway | 8081 | JWT auth + routing to all services |
| auth-server | 8083 | Register / login / JWT issuance |
| catalog-service | 8084 | Products, categories, brands (Redis cached) |
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
customer-service ──► order-events ──► inventory-service
                                   ──► notification-service
                                   ──► review-service

payment-service  ──► payment-events ──► notification-service
```

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
- **Messaging**: Apache Kafka (Confluent CP 7.5.0)
- **Caching**: Redis 7.2 (catalog-service)
- **Database**: MySQL 8.0 (one DB per service)
- **Resilience**: Resilience4j circuit breakers + Feign fallbacks
- **Tracing**: Spring Cloud Sleuth + Zipkin
- **Metrics**: Micrometer + Prometheus + Grafana
- **Monitoring**: Spring Boot Admin
- **Docs**: Springfox Swagger

---

## Quick Start

### Prerequisites
- Docker + Docker Compose
- Java 11 (`JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home` on Mac)
- Maven 3.8+

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
docker-compose up -d
```

### 3. Access services

| UI | URL |
|----|-----|
| Eureka Dashboard | http://localhost:8761 |
| Spring Boot Admin | http://localhost:9000 |
| Kafka UI | http://localhost:8080 |
| Zipkin Traces | http://localhost:9411 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |
| API Gateway | http://localhost:8081 |

Zipkin runs in the Docker stack, but service tracing is disabled by default to keep full-stack local verification within typical Docker Desktop memory limits. Set `SPRING_ZIPKIN_ENABLED=true` before `docker compose up` to emit spans.

Kafka broker endpoints:

| Use case | Endpoint |
|----------|----------|
| Docker-internal services and Kafka UI | `kafka:29092` |
| Host machine clients and CLI tools | `localhost:9092` |

---

## API Reference

Import the Postman collection from `postman/` for all endpoints.

Postman collections:

| Collection | Purpose |
|------------|---------|
| `Ecommerce-API.postman_collection.json` | Endpoint reference collection |
| `Ecommerce-DataSeed.postman_collection.json` | Runner-safe seed flow for local data |
| `Ecommerce-E2E-Complete.postman_collection.json` | Runner-safe application E2E flow |
| `Ecommerce-Local.postman_environment.json` | Local gateway environment |

The runner-safe collections use collection-level scripts to generate and save run values such as token, IDs, coupon code, and idempotency keys. The environment file is only the local value store. Kafka-backed review eligibility and notification requests include runner retries so asynchronous consumers can catch up before later requests use `reviewId` or `notificationId`.

### Authentication

```
POST /auth-service/signup
POST /auth-service/login         → returns JWT token
```

All other endpoints require `Authorization: Bearer <token>`.

### Order flow

```
POST   /order-service/order           → create order from cart
PUT    /order-service/order/{id}/confirm
PUT    /order-service/order/{id}/cancel
GET    /order-service/order/{id}
GET    /order-service/orders
```

`POST /order-service/order` and `POST /payment-service/initiate` accept an optional `Idempotency-Key` header. Retries with the same key for the same user return the existing order/payment and do not replay side effects such as payment initiation, cart clearing, coupon application, or Kafka event publication. The Postman runner collections generate per-run idempotency keys automatically.

---

## Verification

Static validation for Postman collections and service configuration:

```bash
node scripts/validate-postman.js
node scripts/validate-service-config.js
```

Full local verification:

```bash
scripts/verify-local.sh
```

Legacy smoke entrypoint:

```bash
./e2e-smoke-test.sh
```

`e2e-smoke-test.sh` delegates to the Postman-backed local verifier with Maven tests skipped by default.

Useful switches:

```bash
RUN_POSTMAN=0 scripts/verify-local.sh       # skip Docker-stack Postman runner checks
RUN_MAVEN_TESTS=0 scripts/verify-local.sh   # skip Maven module tests
GATEWAY_DISCOVERY_STABILIZE_SECONDS=0 scripts/verify-local.sh  # disable post-Eureka gateway cache wait
```

Local runner checks after the Docker stack is up:

```bash
newman run postman/Ecommerce-DataSeed.postman_collection.json \
  -e postman/Ecommerce-Local.postman_environment.json

newman run postman/Ecommerce-E2E-Complete.postman_collection.json \
  -e postman/Ecommerce-Local.postman_environment.json
```

Targeted Java test example:

```bash
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn -B -f auth-server/pom.xml test -DfailIfNoTests=false "-Dtest=!*ApplicationTests"
```

GitHub Actions runs Postman static validation and Java 11 module tests on pushes and pull requests.

---

## Project Structure

```
.
├── platform-commons/          # Shared DTOs, events, ApiResponse
├── api-gateway/               # JWT filter + Eureka-based routing
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
| auth-server | 8 | 8 |
| catalog-service | 25 | — |
| customer-service | 39 | — |
| order-service | 10 | — |
| payment-service | 11 | 8 |
| shopping-cart-service | 13 | 5 |
| coupon-service | 13 | 8 |
| review-service | 17 | 8 |
| wishlist-service | 8 | 8 |
| inventory-service | 15 | 8 |
| notification-service | 8 | 5 |
| **Total** | **167** | **58** |
