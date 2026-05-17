# Ecommerce Distributed System — Implementation Design

## Platform Commons (Shared Library)

A separate Maven module `platform-commons` (`com.sanjeevsky:platform-commons:1.0.0`) contains all POJOs shared across service boundaries. Services declare it as a dependency — no duplicated DTO classes.

```
platform-commons/src/main/java/com/sanjeevsky/platform/
  response/
    ApiResponse<T>                   — standard {success, message, data} envelope
  model/
    product/ProductResponse          — catalog product data for Feign consumers
    cart/CartSnapshot                — full cart for checkout (shopping-cart → customer)
    cart/CartItemSnapshot            — individual cart line item
    payment/PaymentStatus            — enum PENDING|SUCCESS|FAILED|REFUNDED
    payment/PaymentRequest           — initiation DTO (customer → payment)
    payment/PaymentResponse          — payment result (payment → customer via Feign)
    order/OrderStatus                — enum PENDING|CONFIRMED|SHIPPED|DELIVERED|CANCELLED
```

**Build once, use everywhere:**
```bash
cd platform-commons && mvn install   # installs to ~/.m2 — run before any service build
```

Services that depend on it: `shopping-cart-service`, `customer-service`, `payment-service`.

---

## Why Redesign

The original structure conflates three bounded contexts inside `customer-service` (cart + orders + addresses) and leaves `shopping-cart-service` as a dead skeleton. This makes checkout a single-service blob with broken stream logic, no payment wiring, and no clean ownership boundary. The redesign separates concerns properly so each service owns exactly one domain.

---

## Target Architecture

```
                          ┌─────────────────────────────────────────┐
                          │              API Gateway :8081           │
                          │   JWT filter on all routes except /auth  │
                          └──┬──────────┬──────────┬────────────────┘
                             │          │          │          │
               ┌─────────────┘  ┌───────┘  ┌──────┘  ┌──────┘
               ▼                ▼          ▼          ▼
        ┌─────────────┐  ┌───────────┐ ┌────────┐ ┌──────────────────┐
        │ auth-server │  │ catalog-  │ │ cart-  │ │ customer-service │
        │    :8083    │  │  service  │ │service │ │      :8082       │
        │             │  │   :8084   │ │ :8086  │ │                  │
        │ signup      │  │           │ │        │ │ addresses        │
        │ login       │  │ products  │ │ cart   │ │ orders           │
        │ updatePwd   │  │ brands    │ │ items  │ │                  │
        └──────┬──────┘  │ categories│ │ totals │ └────────┬─────────┘
               │         │ variants  │ └───┬────┘          │
               │         └─────┬─────┘     │ Feign         │ Feign
               │               │           ▼               ▼
               │               │    ┌─────────────┐  ┌─────────────┐
               │               └───►│ cart-service│  │  payment-   │
               │     Feign (price)  │  (catalog   │  │   service   │
               │                    │   Feign)    │  │    :8085    │
               │                    └─────────────┘  └─────────────┘
               │
        ┌──────┴──────┐   ┌──────────────┐   ┌──────────────┐
        │   auth-db   │   │ catalog-db   │   │  cart-db     │
        │ (MySQL)     │   │ (MySQL)      │   │ (MySQL)      │
        └─────────────┘   └──────────────┘   └──────────────┘

        ┌────────────────┐   ┌────────────────┐
        │ customer-db    │   │  payment-db    │
        │ (MySQL)        │   │  (MySQL)       │
        └────────────────┘   └────────────────┘

        ┌────────────────────────────────────────────────────┐
        │         Infrastructure (unchanged)                 │
        │  service-discovery :8761  cloud-config :8071       │
        │  spring-server (Boot Admin) :9000                  │
        └────────────────────────────────────────────────────┘
```

---

## Bounded Context Map

| Service | Owns | Talks To |
|---------|------|----------|
| auth-server | User identity, JWT tokens | — |
| catalog-service | Products, Variants, Brands, Categories, SubCategories | — |
| shopping-cart-service | Cart lifecycle, CartItems, totals | catalog-service (Feign, product price) |
| customer-service | Addresses, Orders, OrderItems (snapshot) | shopping-cart-service (Feign, get+clear cart), payment-service (Feign, initiate) |
| payment-service | Payments, PaymentStatus | — |

---

## Checkout Sequence

```
Client
  │── POST /customer-service/order {addressId}
  │     API Gateway (JWT → X-User header)
  │       customer-service
  │         ├── CartFeignClient.getCart(userId)       → shopping-cart-service
  │         ├── Validate cart non-empty
  │         ├── Map CartItems → OrderItems (price snapshot)
  │         ├── Persist Order (status=PENDING)
  │         ├── PaymentFeignClient.initiate(orderId, userId, total) → payment-service
  │         └── CartFeignClient.clearCart(userId)     → shopping-cart-service
  └── Order{id, status=PENDING, paymentId}
```

---

## Service Data Models

### shopping-cart-service

```java
// CartItem — stores price snapshot at add-to-cart time
@Entity CartItem {
    UUID id
    UUID cartId          // FK to Cart
    UUID productId
    UUID variantId       // nullable
    String productName
    double unitPrice     // snapshot from catalog at add time
    int qty
    LocalDateTime addedAt
}

// Cart — one per user
@Entity Cart {
    UUID id
    String userId        // email from JWT X-User header
    List<CartItem> items // OneToMany CASCADE ALL
    double totalAmount   // recomputed on every mutation
    LocalDateTime createdAt
    LocalDateTime updatedAt
}
```

**API** (`/cart-service/**`):
```
GET    /cart-service/cart              → get or create cart
POST   /cart-service/cart/add          → add item {productId, variantId?, qty}
PUT    /cart-service/cart/item/{productId}?qty=N   → update qty (0 = remove)
DELETE /cart-service/cart/item/{productId}         → remove item
DELETE /cart-service/cart/clear        → clear all items
GET    /cart-service/cart/checkout     → snapshot for order creation (internal Feign)
```

**Feign to catalog-service:**
```java
@FeignClient("catalog-service")
CatalogFeignClient {
    GET /catalog-service/product/getProduct/{id} → ProductResponse{id, name, salePrice}
}
```

---

### customer-service (trimmed)

Cart domain is removed. Service owns addresses and orders only.

```java
// OrderStatus
enum OrderStatus { PENDING, CONFIRMED, SHIPPED, DELIVERED, CANCELLED }

// OrderItem — price snapshot at checkout time, no FK to Cart
@Entity OrderItem {
    UUID id
    UUID productId
    UUID variantId       // nullable
    String productName
    double unitPrice     // frozen at checkout
    int qty
}

// Order — updated: status field + orderItems
@Entity Order {
    UUID id
    UUID userId
    Address address      // OneToOne
    List<OrderItem> orderItems  // OneToMany CASCADE ALL
    OrderStatus status   // PENDING → CONFIRMED etc.
    UUID paymentId       // returned from payment-service
    double orderTotal
    double discount
    double shippingCharges
    LocalDateTime createdAt
    LocalDateTime updatedAt
}

// Address — unchanged
@Entity Address { id, city, state, country, zipCode, home, streetLocality, landmark, user }
```

**API** (`/customer-service/**`):
```
POST   /customer-service/address               → add address
GET    /customer-service/address/{id}          → get address
GET    /customer-service/addresses             → list addresses
PUT    /customer-service/address/{id}          → update address
DELETE /customer-service/address/{id}          → delete address

POST   /customer-service/order {addressId}     → checkout (Feign cart + payment)
GET    /customer-service/order/{id}            → get order
GET    /customer-service/orders                → order history for user
```

**Feign clients added:**
```java
@FeignClient("shopping-cart-service")  CartFeignClient
@FeignClient("payment-service")         PaymentFeignClient
```

**Cart domain removed:** CartController, CartService, CartServiceImpl, CartRepository, Cart entity, CartController all stripped. ProductItem → OrderItem renamed conceptually (class kept as OrderItem).

---

### payment-service

```java
enum PaymentStatus { PENDING, SUCCESS, FAILED, REFUNDED }

@Entity Payment {
    UUID id
    UUID orderId
    String userId
    double amount
    String currency      // default "INR"
    PaymentStatus status
    LocalDateTime createdAt
    LocalDateTime updatedAt
}
```

**API** (`/payment-service/**`):
```
POST /payment-service/initiate {orderId, userId, amount}  → Payment{id, status=PENDING}
PUT  /payment-service/confirm/{paymentId}                 → Payment{status=SUCCESS}
PUT  /payment-service/fail/{paymentId}                    → Payment{status=FAILED}
GET  /payment-service/status/{orderId}                    → PaymentStatus
GET  /payment-service/{paymentId}                         → Payment
```

---

### auth-server (fix only)

```java
// updatePassword — currently returns null
updatePassword(String email, String oldPassword, String newPassword):
    1. find user by email → throw NoSuchUserExistsException if absent
    2. decode stored password → compare with oldPassword → throw CredentialsMismatchException if mismatch
    3. encode newPassword → user.setPassword → repository.save
    4. return "Password updated successfully"
```

---

### api-gateway (add routes)

Add to `GatewayConfig.java`:
```java
.route("cart-service",    r -> r.path("/cart-service/**")    .filters(f -> f.filter(filter)).uri("lb://shopping-cart-service"))
.route("payment-service", r -> r.path("/payment-service/**") .filters(f -> f.filter(filter)).uri("lb://payment-service"))
.route("catalog-service", r -> r.path("/catalog-service/**") .filters(f -> f.filter(filter)).uri("lb://catalog-service"))
```

---

## Implementation Phases

### Phase 1 — shopping-cart-service (full rebuild)
1. `CartItem.java` — entity with price snapshot
2. `Cart.java` — entity with OneToMany CartItems
3. `CartRepository.java`, `CartItemRepository.java`
4. `CatalogFeignClient.java` + `ProductResponse.java` (feign model)
5. `CartService.java` interface (add, update, remove, get, clear, checkout)
6. `CartServiceImpl.java` — full impl with total recalculation
7. `CartController.java` — full REST endpoints
8. `exceptions/` — CartNotFoundException, EmptyCartException
9. `GlobalExceptionHandler.java`
10. `pom.xml` — add spring-cloud-starter-openfeign
11. `ShoppingCartServiceApplication.java` — @EnableFeignClients

### Phase 2 — payment-service (full build)
1. `PaymentStatus.java` enum
2. `Payment.java` entity
3. `PaymentRepository.java`
4. `PaymentService.java` + `PaymentServiceImpl.java`
5. `PaymentController.java`
6. `PaymentRequest.java` DTO
7. `GlobalExceptionHandler.java`
8. `pom.xml` — add JPA + MySQL (already present)

### Phase 3 — customer-service refactoring
1. Remove cart domain: gut `CartController`, `CartService`, `CartServiceImpl`, `CartRepository`
2. Add `OrderStatus.java` enum to Order
3. Add `OrderItem.java` entity (replaces ProductItem for order context, stores price)
4. Add `CartFeignClient.java` + `CartSnapshot.java` (feign response)
5. Add `PaymentFeignClient.java` + `PaymentResponse.java`
6. Implement `OrderService.createOrder(userId, addressId)`
7. Wire `OrderController` POST /order
8. Add GET /customer-service/orders (order history)
9. Add PUT /customer-service/address/{id} and DELETE /customer-service/address/{id}
10. Fix `getAddress` path variable bug (uuid vs id mismatch)

### Phase 4 — auth-server fix
1. Implement `updatePassword(email, oldPwd, newPwd)` in `UserServiceImp`
2. Update `UserService` interface + `UserAuthController` endpoint

### Phase 5 — api-gateway routes
1. Add cart-service, payment-service, catalog-service routes in `GatewayConfig`

---

## File Change Map

```
shopping-cart-service/
  pom.xml                                              [modify — add openfeign]
  src/main/java/com/sanjeevsky/shoppingcartservice/
    ShoppingCartServiceApplication.java                [modify — @EnableFeignClients]
    model/
      Cart.java                                        [rewrite — full entity]
      CartItem.java                                    [CREATE NEW]
    repository/
      CartRepository.java                              [rewrite — JpaRepository]
      CartItemRepository.java                          [CREATE NEW]
    clients/
      CatalogFeignClient.java                          [CREATE NEW]
      model/ProductResponse.java                       [CREATE NEW]
    services/
      CartService.java                                 [rewrite — full interface]
      impl/CartServiceImpl.java                        [rewrite — full impl]
    controller/
      CartController.java                              [rewrite — full REST]
    exceptions/
      CartNotFoundException.java                       [CREATE NEW]
      GlobalExceptionHandler.java                      [CREATE NEW]
    config/
      FeignConfig.java                                 [CREATE NEW]

payment-service/
  src/main/java/com/sanjeevsky/paymentservice/
    model/
      Payment.java                                     [CREATE NEW]
      PaymentStatus.java                               [CREATE NEW]
      PaymentRequest.java                              [CREATE NEW]
    repository/
      PaymentRepository.java                           [CREATE NEW]
    service/
      PaymentService.java                              [CREATE NEW]
      impl/PaymentServiceImpl.java                     [CREATE NEW]
    controller/
      PaymentController.java                           [CREATE NEW]
    exceptions/
      PaymentNotFoundException.java                    [CREATE NEW]
      GlobalExceptionHandler.java                      [CREATE NEW]
    config/
      SwaggerConfig.java                               [CREATE NEW]

customer-service/
  src/main/java/com/sanjeevsky/customerservice/
    model/
      Order.java                                       [modify — add status, paymentId]
      OrderItem.java                                   [CREATE NEW — price snapshot]
      Cart.java                                        [gut — empty class, keep for DB compat]
    service/
      CartService.java                                 [gut — empty interface]
      OrderService.java                                [modify — add createOrder, listOrders]
      AddressService.java                              [modify — add updateAddress, deleteAddress]
      impl/CartServiceImpl.java                        [gut — no-op]
      impl/OrderServiceImpl.java                       [rewrite — implement createOrder]
      impl/AddressServiceImpl.java                     [modify — add update/delete]
    controller/
      CartController.java                              [gut — remove all endpoints]
      OrderController.java                             [modify — wire POST /order, add GET /orders]
      CustomerServiceController.java                   [modify — fix path var bug, add update/delete]
    clients/
      ProductFeignClient.java                          [keep — catalog calls]
      CartFeignClient.java                             [CREATE NEW]
      PaymentFeignClient.java                          [CREATE NEW]
      model/
        CartSnapshot.java                              [CREATE NEW]
        CartItemSnapshot.java                          [CREATE NEW]
        PaymentResponse.java                           [CREATE NEW]
    repository/
      CartRepository.java                              [keep — leave for now, no endpoints call it]

auth-server/
  src/main/java/com/sanjeevsky/authserver/
    service/UserService.java                           [modify — updatePassword signature]
    service/UserServiceImp.java                        [modify — implement updatePassword]
    controller/UserAuthController.java                 [modify — add updatePassword endpoint]
    modal/UpdatePasswordRequest.java                   [CREATE NEW]

api-gateway/
  src/main/java/com/sanjeevsky/apigateway/config/
    GatewayConfig.java                                 [modify — add 3 routes]
```

---

## Verification Checklist

```
docker-compose up -d   # MySQL instances

# Start order: service-discovery → cloud-config → auth-server → catalog-service
#              → shopping-cart-service → payment-service → customer-service → api-gateway

# 1. Auth
POST /auth-service/signup  {email, password}              → 200 + User
POST /auth-service/login   {email, password}              → 200 + {email, jwt}
PUT  /auth-service/updatePassword {email, old, new}       → 200 + message

# 2. Catalog (existing, verify still works)
POST /catalog-service/add-brand     → 200
POST /catalog-service/product/addProduct → 200

# 3. Cart (JWT required)
GET  /cart-service/cart             → empty cart created
POST /cart-service/cart/add         → cart with item + total computed
PUT  /cart-service/cart/item/{id}?qty=3 → qty updated, total recalculated
DELETE /cart-service/cart/item/{id} → item removed

# 4. Address
POST /customer-service/address      → address created
GET  /customer-service/addresses    → list
PUT  /customer-service/address/{id} → updated
DELETE /customer-service/address/{id} → deleted

# 5. Checkout
POST /customer-service/order {addressId} →
     Order{id, status=PENDING, paymentId}
     Payment record created in payment-service
     Cart cleared in shopping-cart-service

# 6. Order history
GET  /customer-service/orders       → list of orders for user

# 7. Payment
GET  /payment-service/status/{orderId} → PENDING
PUT  /payment-service/confirm/{paymentId} → SUCCESS
```
