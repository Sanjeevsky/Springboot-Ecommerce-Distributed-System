# Search Architecture

This document describes how full-text search and auto-suggestions are implemented in the Trove Storefront using Elasticsearch (running as the `opensearch` container), with real-time index synchronization via Debezium CDC.

---

## Overview

```
┌──────────────────────────────────────────────────────────────────────────────┐
│                          Search Data Flow                                    │
│                                                                              │
│  User types in search bar                                                    │
│         │                                                                    │
│         ▼                                                                    │
│  ┌─────────────┐  /suggest?q=  ┌───────────────────┐                        │
│  │   Browser   │ ───────────► │  catalog-service   │  match_phrase_prefix   │
│  │  (Header)   │ ◄─────────── │  (suggest API)     │ ──────────────────►   │
│  └─────────────┘  name list   └───────────────────┘        Elasticsearch    │
│         │                                                         │          │
│         │ user submits search                                     │          │
│         ▼                                                         │          │
│  ┌─────────────┐  /search?q=  ┌───────────────────┐              │          │
│  │  Listing    │ ───────────► │  catalog-service   │  bool query  │          │
│  │   Page      │ ◄─────────── │  (search API)      │ ──────────► │          │
│  └─────────────┘  products    └───────────────────┘              │          │
│                                        │                          │          │
│                                        │ fallback                 │          │
│                                        ▼                          │          │
│                               ┌─────────────────┐                │          │
│                               │  MySQL (LIKE %)  │                │          │
│                               └─────────────────┘                │          │
└──────────────────────────────────────────────────────────────────────────────┘

┌──────────────────────────────────────────────────────────────────────────────┐
│                          Index Synchronization                               │
│                                                                              │
│  ┌─────────┐  binlog  ┌──────────────┐  Kafka topic                        │
│  │  MySQL  │ ───────► │   Debezium   │  catalog.product-catalog-db.product  │
│  │         │          │ Kafka Connect│ ─────────────────────────────────►   │
│  └─────────┘          └──────────────┘                                      │
│                                                              │               │
│                                                              ▼               │
│  On startup:                                     ┌───────────────────┐      │
│  ┌──────────────────┐                            │  catalog-service  │      │
│  │  ProductIndexer  │ ──── saveAll() ───────────►│  KafkaListener    │      │
│  │  (bulk load)     │                            │  @DebeziumCdc...  │      │
│  └──────────────────┘                            └────────┬──────────┘      │
│                                                           │                 │
│  On addProduct():                                         ▼                 │
│  ┌──────────────────┐                            ┌───────────────────┐      │
│  │  ProductService  │ ─── searchRepo.save() ────►│  Elasticsearch    │      │
│  │  (inline sync)   │                            │  (products index) │      │
│  └──────────────────┘                            └───────────────────┘      │
└──────────────────────────────────────────────────────────────────────────────┘
```

---

## Elasticsearch Document

Index name: `products`  
Single shard, zero replicas (optimized for single-node dev).

| Field         | ES Type   | Analyzer  | Purpose                                 |
|---------------|-----------|-----------|-----------------------------------------|
| `id`          | `_id`     | —         | UUID string (maps to MySQL `product.id`)|
| `name`        | `text`    | standard  | Full-text search, suggestions           |
| `description` | `text`    | standard  | Full-text search                        |
| `brand`       | `text`    | standard  | Full-text search                        |
| `brandId`     | `keyword` | —         | Filter by brand UUID                    |
| `categoryId`  | `keyword` | —         | Filter by category UUID                 |
| `categoryName`| `text`    | standard  | Full-text search                        |
| `salePrice`   | `double`  | —         | Display only                            |
| `mrpPrice`    | `double`  | —         | Display only                            |
| `status`      | `integer` | —         | Filter active (`status=1`) only         |

---

## Search API

### Full-text search

```
GET /catalog-service/product/search
    ?q=<keyword>         (optional, fuzzy multi-field match)
    &categoryId=<uuid>   (optional, keyword filter)
    &brandId=<uuid>      (optional, keyword filter)
    &page=0&size=20
```

**ES query strategy:**

```
bool {
  filter: status = 1
  filter: categoryId = <uuid>     (if provided)
  filter: brandId    = <uuid>     (if provided)
  must:   multiMatch(q, [name, description, brand, categoryName], fuzziness=AUTO)
}
```

Results are sorted by ES relevance score (not by field — text fields do not support `fielddata` sorting without explicit mapping).  
Product UUIDs from ES hits are fetched in bulk from MySQL to return full `Product` entities.

**Fallback:** If Elasticsearch is unavailable, the query degrades to a MySQL `LIKE %keyword%` query transparently.

### Auto-suggestions

```
GET /catalog-service/product/suggest
    ?q=<prefix>    (min 2 chars, required)
    &size=5        (default 5, max 20)
```

Returns `List<String>` — product names that start with the given prefix.

**ES query:**

```
match_phrase_prefix {
  field: "name",
  query: "<prefix>",
  max_expansions: 10
}
```

No auth required (open endpoint in API Gateway `RouterValidator`).

---

## Index Synchronization

There are three synchronization paths, all writing to the same Elasticsearch index:

### 1. Startup bulk index (`ProductIndexer`)

On `ApplicationReadyEvent`, all MySQL products are loaded and indexed via `searchRepository.saveAll()`. This ensures Elasticsearch is populated even after a fresh container start.  
Wrapped in try-catch — if ES is unavailable, the service still starts and falls back to MySQL for search.

### 2. Inline sync on write (`ProductServiceImpl.addProduct`)

When a product is created via the API, it is immediately indexed in Elasticsearch after the MySQL save. Failures are swallowed with a warning log so the API call succeeds regardless of ES availability.

### 3. Debezium CDC (`DebeziumCdcConsumer`)

Real-time sync for any MySQL change that bypasses the application (admin tools, migrations, bulk imports).

**Pipeline:**

```
MySQL (binlog) → Debezium Kafka Connect → Kafka topic → catalog-service consumer → Elasticsearch
```

**Topic:** `catalog.product-catalog-db.product`  
(format: `{topic.prefix}.{database}.{table}`)

**CDC event structure** (Debezium JSON, schemas disabled):

```json
{
  "before": { "id": "...", ... },
  "after":  { "id": "...", ... },
  "op": "c" | "u" | "d" | "r"
}
```

| `op` | Meaning | Action |
|------|---------|--------|
| `c`  | INSERT  | Fetch product from MySQL, upsert in ES |
| `u`  | UPDATE  | Fetch product from MySQL, upsert in ES |
| `r`  | Snapshot read | Fetch product from MySQL, upsert in ES |
| `d`  | DELETE  | Delete from ES by `before.id` |

**Snapshot mode:** `schema_only` — Debezium captures the schema but not historical rows (startup indexer already handles that). Only changes from the time the connector is registered are streamed.

**Consumer group:** `catalog-es-sync`

---

## Debezium Setup

### Docker services

| Service          | Image                  | Port  | Role                                    |
|------------------|------------------------|-------|-----------------------------------------|
| `kafka-connect`  | `debezium/connect:2.4` | 8093  | Kafka Connect with Debezium MySQL plugin|
| `connector-init` | `curlimages/curl:8.4.0`| —     | One-shot connector registration via REST|

### MySQL binlog configuration

MySQL starts with:
```
--log-bin=mysql-bin --binlog-format=ROW --server-id=1
```

`ROW` format is required by Debezium (captures each row change, not the SQL statement).

### Connector registration

The `connector-init` service POSTs the connector config to Kafka Connect's REST API on first run. The connector is idempotent — re-running `docker compose up` will detect the existing connector and skip re-registration.

---

## Sequence Diagram: Search with Suggestion

```
User           Browser         catalog-service      Elasticsearch      MySQL
 │                │                  │                    │               │
 │  types "head"  │                  │                    │               │
 │ ─────────────► │                  │                    │               │
 │                │ GET /suggest?q=  │                    │               │
 │                │ ───────────────► │                    │               │
 │                │                  │ match_phrase_prefix│               │
 │                │                  │ ─────────────────► │               │
 │                │                  │ ["Headphones XB9", │               │
 │                │                  │  "Headset Pro 7"]  │               │
 │                │                  │ ◄───────────────── │               │
 │   dropdown     │ ◄─────────────── │                    │               │
 │ ◄───────────── │                  │                    │               │
 │                │                  │                    │               │
 │  selects item  │                  │                    │               │
 │ ─────────────► │                  │                    │               │
 │                │ GET /search?q=   │                    │               │
 │                │ ───────────────► │                    │               │
 │                │                  │ bool query (fuzzy) │               │
 │                │                  │ ─────────────────► │               │
 │                │                  │ [ids...]           │               │
 │                │                  │ ◄───────────────── │               │
 │                │                  │ findAllById(ids)   │               │
 │                │                  │ ─────────────────────────────────► │
 │                │                  │ [Product entities] │               │
 │                │                  │ ◄───────────────────────────────── │
 │   results      │ ◄─────────────── │                    │               │
 │ ◄───────────── │                  │                    │               │
```

---

## Key Files

| File | Role |
|------|------|
| `catalog-service/.../search/document/ProductDocument.java` | ES index mapping |
| `catalog-service/.../search/ProductDocumentMapper.java` | Product → ES document converter |
| `catalog-service/.../search/ProductIndexer.java` | Startup bulk indexer |
| `catalog-service/.../search/DebeziumCdcConsumer.java` | Kafka CDC listener |
| `catalog-service/.../search/repository/ProductSearchRepository.java` | Spring Data ES repository |
| `catalog-service/.../service/impl/ProductServiceImpl.java` | Search + suggest service methods |
| `catalog-service/.../controller/ProductCatalogController.java` | `/search` and `/suggest` endpoints |
| `api-gateway/.../filter/RouterValidator.java` | Open (no-auth) endpoint list |
| `frontend/src/lib/services.js` | `catalog.search()` and `catalog.suggest()` |
| `frontend/src/components/storefront/Header.jsx` | Search bar with suggestion dropdown |
| `docker-compose.yml` | `opensearch`, `kafka-connect`, `connector-init` services |
