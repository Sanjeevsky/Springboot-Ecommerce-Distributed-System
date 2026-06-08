# Trove — Product Overview

Trove is a full-stack e-commerce marketplace built on a distributed microservices architecture. It provides a complete shopping experience — from product discovery to checkout — while serving as a reference implementation of production-grade distributed systems patterns.

---

## What It Does

Trove lets customers browse a product catalog, manage a cart and wishlist, place orders with coupon discounts, track order and payment status, and write reviews — all behind a single storefront. On the seller/admin side, it supports product and inventory management, coupon creation, and review moderation.

---

## User-Facing Features

### Discovery

- **Browsing** — products listed by category, brand, and subcategory with pagination
- **Full-text search** — fuzzy multi-field search across name, description, brand, and category; results ranked by relevance
- **Auto-suggest** — as-you-type prefix suggestions powered by Elasticsearch `match_phrase_prefix`; dropdown appears after 2 characters with a 300ms debounce
- **Product detail** — images, specs, sale/MRP price, GST, variants, star rating, and paginated reviews

### Account

- **Registration & login** — email/password sign-up with JWT session
- **Address book** — add, edit, and delete shipping addresses
- **Order history** — list of past orders with status and line items
- **Notifications** — real-time event feed (order confirmed, payment processed, refund issued)
- **Wishlist** — save products for later; one-tap move to cart

### Shopping

- **Cart** — add items, update quantities, remove items; cart persists across sessions (Redis-backed)
- **Coupon codes** — enter a code at checkout; discount validated and applied before payment
- **Checkout** — 4-step guided flow: contact → address selection → shipping → payment confirmation
- **Order tracking** — order confirmation page with line items, totals, and live status polling

### Post-Purchase

- **Payment** — charge, confirm, and refund lifecycle tracked per order
- **Reviews** — submit a star rating and text review; purchase verification gate prevents review fraud; visible after moderation

---

## Admin / Seller Features

- Create and manage products (name, description, brand, category, images, price, GST, discount)
- Manage inventory: add stock, track available/reserved quantities per product and variant
- Create and deactivate coupon codes with discount amount, expiry, and usage limits
- Moderate product reviews (approve / reject)

---

## Pages

| Page | URL | What it does |
|------|-----|--------------|
| Home | `/` | Hero banner, category grid, featured products |
| Listing | `/search` | Search results with keyword, category, and brand filters |
| Product | `/product/:id` | Detail view with reviews and add-to-cart |
| Cart | `/cart` | Cart drawer with item list, quantities, and subtotal |
| Checkout | `/checkout` | 4-step order placement |
| Order Confirmation | `/order/:id` | Post-checkout order summary |
| Orders | `/account/orders` | Order history |
| Notifications | `/account/notifications` | Event notifications |
| Addresses | `/account/addresses` | Address management |
| Wishlist | `/account/wishlist` | Saved items |
| Login / Sign-up | `/login`, `/signup` | Authentication |

---

## Key Properties

**Resilient by design** — every service degrades gracefully. If Elasticsearch is down, search falls back to MySQL `LIKE` queries. If a downstream service is unreachable, circuit breakers return cached or empty responses rather than failing the request.

**Consistent at checkout** — the checkout flow uses a distributed saga with automatic compensation. If payment fails after stock has been reserved, the reservation is released and the order is cancelled — no manual intervention required.

**Real-time catalog sync** — product updates made directly in the database (migrations, bulk imports, admin tools) automatically propagate to the search index via Debezium CDC within seconds.

**Idempotent operations** — order creation and payment initiation accept an `Idempotency-Key` header; retrying a timed-out request will not create a duplicate order or charge.

---

## Supported Scale (Single-Node Dev)

The Docker Compose stack runs all services on a single machine. Elasticsearch is configured with 1 shard and 0 replicas (single-node). Kafka runs in KRaft mode with a single broker. This configuration is suitable for development and integration testing; production deployment would add replicas and a dedicated Kafka cluster.
