# Load Tests

k6 load tests for the Ecommerce Distributed System.

## Prerequisites

```bash
# Install k6
brew install k6          # macOS
# or: https://k6.io/docs/get-started/installation/

# System must be running
docker compose up -d

# Optional: use existing catalog data instead of setup-time seed data
export PRODUCT_ID=<uuid-from-catalog>
export VARIANT_ID=<uuid-from-catalog>
```

## Test Files

| File | Purpose | Default Load |
|------|---------|-------------|
| `checkout-flow.js` | Full checkout: login → cart → order → confirm | 2 VUs, 1 min (smoke) |
| `catalog-browse.js` | Catalog reads — validates Redis cache effectiveness | 50 req/s, 5 min |

## Running

```bash
cd load-tests

# Smoke test (quick sanity check)
k6 run --env SCENARIO=smoke checkout-flow.js

# Load test (20 VUs sustained)
k6 run --env SCENARIO=load checkout-flow.js

# Stress test (ramps to 100 VUs)
k6 run --env SCENARIO=stress checkout-flow.js

# Soak test (10 VUs for 30 min)
k6 run --env SCENARIO=soak checkout-flow.js

# With an existing coupon code
k6 run --env COUPON=SAVE10 --env SCENARIO=load checkout-flow.js

# With a specific existing product
k6 run --env PRODUCT_ID=$PRODUCT_ID --env VARIANT_ID=$VARIANT_ID --env SCENARIO=smoke checkout-flow.js

# Catalog browse
k6 run catalog-browse.js

# Catalog browse with a specific existing product
k6 run --env PRODUCT_ID=$PRODUCT_ID catalog-browse.js

# Output to JSON for analysis
k6 run --out json=results.json --env SCENARIO=load checkout-flow.js
```

## Thresholds

The `checkout-flow.js` test fails if:
- HTTP error rate > 1%
- p95 response time > 2s
- p99 response time > 5s
- Order create p95 > 3s
- Login success rate < 99%
- Order success rate < 95%

## Interpreting Results

Key metrics to watch in Grafana (`http://localhost:3000`):
- **HTTP request rate** — should increase linearly with VUs
- **P99 latency** — spike indicates DB connection pool exhaustion
- **HikariCP active connections** — should stay below pool max (3 per service)
- **Kafka consumer lag** — should drain within seconds after load spike
- **JVM heap** — watch for memory pressure under sustained load
