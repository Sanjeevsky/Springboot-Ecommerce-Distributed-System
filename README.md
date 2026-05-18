# Springboot Ecommerce Distributed System

A production-grade ecommerce platform built as a microservices architecture using Spring Boot 2.5.7, Spring Cloud 2020.0.4, Kafka, Redis, and MySQL.

[![Architecture](https://img.shields.io/badge/view-architecture-blue)](https://sanjeevsky.github.io/Springboot-Ecommerce-Distributed-System/architecture.html)

---

## Architecture

[Live Architecture Diagram](https://sanjeevsky.github.io/Springboot-Ecommerce-Distributed-System/architecture.html)

**15 services** across 3 tiers:

| Tier | Services |
|------|----------|
| Infrastructure | MySQL, Redis, Kafka + Zookeeper, Zipkin, Prometheus, Grafana |
| Spring Cloud | Eureka (service-discovery), Config Server (cloud-config), Spring Boot Admin (spring-server), API Gateway |
| Business | auth, catalog, customer, order, shopping-cart, payment, inventory, notification, review, wishlist, coupon |

---

## Services

| Service | Port | Description |
|---------|------|-------------|
| service-discovery | 8761 | Eureka server — service registry |
| cloud-config | 8071 | Spring Cloud Config Server |
| spring-server | 9000 | Spring Boot Admin UI |
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
JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
  mvn clean package -DskipTests --file platform-commons/pom.xml

for svc in auth-server catalog-service customer-service order-service \
           shopping-cart-service payment-service inventory-service \
           notification-service review-service wishlist-service coupon-service \
           api-gateway service-discovery cloud-config spring-server; do
  JAVA_HOME=/Library/Java/JavaVirtualMachines/zulu-11.jdk/Contents/Home \
    mvn clean package -DskipTests -f $svc/pom.xml
done
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
| Zipkin Traces | http://localhost:9411 |
| Prometheus | http://localhost:9090 |
| Grafana | http://localhost:3000 (admin / admin) |
| API Gateway | http://localhost:8081 |

---

## API Reference

Import the Postman collection from `postman/` for all endpoints.

### Authentication

```
POST /auth-service/register
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
