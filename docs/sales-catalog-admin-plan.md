# Trove Studio — Sales-Catalog Admin UI & Analytics Dashboard Plan

A seller/admin console for managing the catalog (products, variants, stock) with an
in-app business-analytics dashboard. Lives in the existing `frontend/` app under
`/studio`, talking to the existing services through the gateway.

## Implementation status

Phases 0 through 7 are implemented. The admin role is signed into JWTs and propagated
by the gateway, catalog and inventory writes are admin-gated, the Studio management
surface is live, order analytics power the overview dashboard, coupon creation,
listing, activation, and deactivation are available from the Studio, and every admin
mutation to the sales catalog (product price/status) and inventory (stock) is recorded
in an audit log surfaced on the Studio Activity page.

## Current gaps this plan closes

| Gap | Where |
|-----|-------|
| Catalog is create/read only — no product/variant/brand/category update or delete | catalog-service |
| No endpoint to adjust stock quantities (only seed `POST /stock` and read endpoints) | inventory-service |
| JWT carries no role — anyone logged in could call admin endpoints if they existed | auth-server, api-gateway |
| No business analytics API (Grafana covers ops metrics only) | order-service |
| No admin surface in the frontend | frontend |

## Phase 0 — Admin role (prerequisite, small)

1. `auth-server`: add `role` column to the user entity (`CUSTOMER` default, `ADMIN`),
   include `role` claim in the JWT. Seed one admin user via `init-db.sql`.
2. `api-gateway`: propagate the claim as `X-User-Role` header downstream
   (same pattern as the existing `X-User` header).
3. Write endpoints in catalog-service / inventory-service / analytics endpoints check
   `X-User-Role == ADMIN` (shared `@AdminOnly` interceptor; 403 otherwise).
4. Frontend: decode role from the JWT into `trove_user`; add `RequireAdmin`
   route guard (mirrors `RequireAuth`); Studio nav link rendered only for admins.

## Phase 1 — Catalog & inventory write APIs

catalog-service:
- `PUT /catalog-service/product/{id}` — partial update (name, prices, description, images, brand, category).
- `DELETE /catalog-service/product/{id}` — soft delete (`active=false`); list/search filter on it.
- `PUT /catalog-service/variant/{variantId}`, `DELETE /catalog-service/variant/{variantId}`.
- Debezium CDC already streams the `product` table to Elasticsearch, so updates/soft-deletes
  reach search automatically — verify tombstone/update handling in the sync consumer.

inventory-service:
- `PUT /inventory-service/stock/{productId}/variant/{variantId}` — set `totalQty`
  (and a product-level variant-less form), rejecting below current `reservedQty`.

Testing (repo conventions): unit + controller-contract tests per service, new requests in
both Postman collections, `validate-postman.js` route lists, smoke-test steps.

## Phase 2 — Studio UI (frontend `/studio`)

Routes (all behind `RequireAdmin`, separate layout with its own sidebar):
- `/studio` — analytics dashboard (Phase 3 fills it; ships with stock + catalog tiles first).
- `/studio/products` — table with search/category/brand filters, price, stock badge, status.
- `/studio/products/new` and `/studio/products/:id` — product form: details, pricing
  (sale/MRP), images (URL list now; upload later), variant rows (label + qty) with
  inline stock editing that writes to inventory-service.
- `/studio/inventory` — flat quantities grid (product/variant, available, reserved, total)
  with quick +/- adjustments and a low-stock filter.

Reuses the existing design system (`components/`), services-layer pattern with mock
fallback, and the `lib/auth.js` helpers.

## Phase 3 — Analytics

order-service (admin-gated, simple JPA aggregations — no new service needed at this scale):
- `GET /order-service/analytics/summary?from=&to=` → revenue, order count, AOV, status breakdown.
- `GET /order-service/analytics/daily?days=30` → per-day revenue/orders for the line chart.
- `GET /order-service/analytics/top-products?limit=10` → by quantity and revenue.

Dashboard (`/studio`): revenue + orders + AOV stat tiles (today / 7d / 30d), daily revenue
line chart, orders-by-status donut, top-products table, low-stock alert list (inventory
below threshold). Chart library: recharts (small, fits React 18).

If event-sourced analytics is ever wanted, a dedicated consumer on `order-events` /
`payment-events` can replace the JPA queries without touching the UI contract.

## Phase 4 — Coupon management

- `POST /coupon-service/coupon` is admin-gated.
- `GET /coupon-service/admin/coupons` lists active and inactive coupons.
- `PUT /coupon-service/coupon/{couponId}/active?active=` controls redemption availability.
- `/studio/coupons` creates, searches, filters, activates, and deactivates offers.
- Coupon application rechecks expiry, active state, and usage limits at redemption time.

## Phase 5 — CSV exports

- The overview exports daily revenue and order counts.
- Product, inventory, and coupon screens export the current filtered view.
- A shared serializer handles quoting, UTF-8 output, and spreadsheet formula injection.

## Phase 6 — Price/stock audit log

- catalog-service and inventory-service each persist an append-only `AuditLog`
  (`catalog_audit_log`, `inventory_audit_log`): entity type, id, action, actor, summary.
- Product create/update/retire records price and status changes; stock set/restock
  records the old → new quantity. The actor is the admin email resolved from the
  request MDC (`X-User`); unattributed changes record `system`.
- `@AdminOnly GET /catalog-service/audit` and `GET /inventory-service/audit` (paged,
  optional `entityId` filter).
- `/studio/activity` merges both sources newest-first with a source filter and CSV export.

## Phase 7 — Product image upload

- A **MinIO** container (S3-compatible object storage) holds a public-read
  `product-images` bucket; the bucket is created and its read policy applied on
  catalog-service startup.
- `@AdminOnly POST /catalog-service/media/upload` accepts a base64 JSON body
  (`filename`, `contentType`, `dataBase64`), validates type/size (≤ 5 MB), stores the
  object, and returns its public URL. Base64 (not multipart) keeps the existing JSON
  API client and the Postman/newman flow simple.
- The Studio product editor adds an **Upload image** button (with thumbnails) that
  appends the returned URL to the product's image list; pasting URLs still works.
- Stored URLs use `MEDIA_PUBLIC_BASE_URL` (browser-reachable `localhost:9000`), while
  the service uploads over the internal `minio:9000` endpoint.

## Later

Image deletion/garbage-collection and presigned direct-to-storage uploads for large files.

## Suggested PR sequence

1. **PR A** — Phase 0 (role claim + gateway header + admin guard + seed admin).
2. **PR B** — Phase 1 (catalog/inventory write APIs + tests + Postman).
3. **PR C** — Phase 2 (Studio shell, product table/form, inventory grid).
4. **PR D** — Phase 3 (analytics endpoints + dashboard).
5. **PR E** — Phase 4 (coupon lifecycle APIs + Studio management page).
6. **PR F** — Phase 5 (shared CSV serializer + Studio exports).
7. **PR G** — Phase 6 (catalog/inventory audit log + Studio activity page).
8. **PR H** — Phase 7 (MinIO object storage + product image upload).

Each PR is independently shippable; B unblocks C, A unblocks everything.
