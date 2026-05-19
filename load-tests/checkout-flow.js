/**
 * k6 Load Test — Ecommerce Checkout Flow
 *
 * Tests the critical path: login → browse catalog → add to cart → checkout → confirm order
 *
 * Run scenarios:
 *   Smoke    : k6 run --env SCENARIO=smoke    checkout-flow.js
 *   Load     : k6 run --env SCENARIO=load     checkout-flow.js
 *   Stress   : k6 run --env SCENARIO=stress   checkout-flow.js
 *   Soak     : k6 run --env SCENARIO=soak     checkout-flow.js
 *
 * Prerequisites (set once before running load tests):
 *   export PRODUCT_ID=<uuid>      # existing product in catalog
 *   export VARIANT_ID=<uuid>      # existing variant (optional)
 *   k6 run -e PRODUCT_ID=$PRODUCT_ID checkout-flow.js
 */

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend, Counter } from "k6/metrics";
import { randomString } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

// ── Custom metrics ────────────────────────────────────────────────────────────

const loginSuccess   = new Rate("login_success_rate");
const orderSuccess   = new Rate("order_success_rate");
const cartAddSuccess = new Rate("cart_add_success_rate");
const orderDuration  = new Trend("order_create_duration", true);
const ordersPlaced   = new Counter("orders_placed_total");

// ── Configuration ─────────────────────────────────────────────────────────────

const BASE_URL   = __ENV.BASE_URL   || "http://localhost:8081";
const PRODUCT_ID = __ENV.PRODUCT_ID || "00000000-0000-0000-0000-000000000001";
const VARIANT_ID = __ENV.VARIANT_ID || null;
const COUPON     = __ENV.COUPON     || null;

const SCENARIO = __ENV.SCENARIO || "smoke";

const SCENARIOS = {
  smoke: {
    executor: "constant-vus",
    vus: 2,
    duration: "1m",
    gracefulStop: "10s",
  },
  load: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "2m", target: 20 },
      { duration: "5m", target: 20 },
      { duration: "2m", target: 0 },
    ],
    gracefulRampDown: "30s",
  },
  stress: {
    executor: "ramping-vus",
    startVUs: 0,
    stages: [
      { duration: "2m", target: 20 },
      { duration: "2m", target: 50 },
      { duration: "2m", target: 100 },
      { duration: "3m", target: 100 },
      { duration: "2m", target: 0 },
    ],
    gracefulRampDown: "30s",
  },
  soak: {
    executor: "constant-vus",
    vus: 10,
    duration: "30m",
    gracefulStop: "60s",
  },
};

export const options = {
  scenarios: { [SCENARIO]: SCENARIOS[SCENARIO] },
  thresholds: {
    http_req_failed:          ["rate<0.01"],
    http_req_duration:        ["p(95)<2000", "p(99)<5000"],
    order_create_duration:    ["p(95)<3000"],
    login_success_rate:       ["rate>0.99"],
    order_success_rate:       ["rate>0.95"],
    cart_add_success_rate:    ["rate>0.99"],
  },
};

// ── Helpers ───────────────────────────────────────────────────────────────────

const JSON_HEADERS = { "Content-Type": "application/json" };

function authHeaders(token) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${token}` };
}

function jsonBody(obj) {
  return JSON.stringify(obj);
}

function extractData(resp) {
  try {
    return JSON.parse(resp.body).data;
  } catch {
    return null;
  }
}

// ── Setup — register one admin user for catalog seeding ──────────────────────

export function setup() {
  const adminEmail = `loadtest_admin_${randomString(6)}@test.com`;
  http.post(
    `${BASE_URL}/auth-service/signup`,
    jsonBody({ email: adminEmail, password: "Load@1234", firstName: "Load", lastName: "Admin", role: "USER" }),
    { headers: JSON_HEADERS }
  );

  const loginResp = http.post(
    `${BASE_URL}/auth-service/login`,
    jsonBody({ email: adminEmail, password: "Load@1234" }),
    { headers: JSON_HEADERS }
  );

  const loginData = extractData(loginResp);
  const token = loginData ? (loginData.token || loginData) : "";

  let productId = PRODUCT_ID;
  let addressId = null;

  if (token) {
    const headers = authHeaders(token);

    // Create an address for the admin to reuse
    const addrResp = http.post(
      `${BASE_URL}/customer-service/address`,
      jsonBody({ home: "1A", streetLocality: "Load Street", city: "TestCity", state: "TS", country: "IN", zipCode: 560001, landmark: "Gateway" }),
      { headers }
    );
    const addrData = extractData(addrResp);
    addressId = addrData ? addrData.id : null;
  }

  return { adminToken: token, productId, addressId };
}

// ── Main VU scenario ──────────────────────────────────────────────────────────

export default function (data) {
  const email = `loaduser_${randomString(8)}@test.com`;
  const password = "Load@1234";
  let token = "";
  let addressId = data.addressId;
  const productId = data.productId || PRODUCT_ID;

  // ── 1. Register ────────────────────────────────────────────────────────────
  group("auth", function () {
    const regResp = http.post(
      `${BASE_URL}/auth-service/signup`,
      jsonBody({ email, password, firstName: "Load", lastName: "User", role: "USER" }),
      { headers: JSON_HEADERS }
    );
    check(regResp, { "register 201": (r) => r.status === 201 || r.status === 200 });

    // ── 2. Login ───────────────────────────────────────────────────────────
    const loginResp = http.post(
      `${BASE_URL}/auth-service/login`,
      jsonBody({ email, password }),
      { headers: JSON_HEADERS }
    );
    const ok = check(loginResp, {
      "login 200": (r) => r.status === 200,
      "login has token": (r) => {
        const d = extractData(r);
        return d && (d.token || typeof d === "string");
      },
    });
    loginSuccess.add(ok);

    const loginData = extractData(loginResp);
    token = loginData ? (loginData.token || loginData) : "";
  });

  if (!token) return;

  const headers = authHeaders(token);

  // ── 3. Browse catalog ──────────────────────────────────────────────────────
  group("catalog", function () {
    const resp = http.get(`${BASE_URL}/catalog-service/product/getProduct/${productId}`, { headers });
    check(resp, { "get product 200": (r) => r.status === 200 });
    sleep(0.3);
  });

  // ── 4. Create address (per-user) ───────────────────────────────────────────
  if (!addressId) {
    group("address", function () {
      const resp = http.post(
        `${BASE_URL}/customer-service/address`,
        jsonBody({ home: "2B", streetLocality: "Test Ave", city: "LoadCity", state: "LC", country: "IN", zipCode: 400001, landmark: "Near" }),
        { headers }
      );
      check(resp, { "add address 201": (r) => r.status === 201 });
      const d = extractData(resp);
      if (d) addressId = d.id;
    });
  }

  // ── 5. Add to cart ─────────────────────────────────────────────────────────
  group("cart", function () {
    const body = { productId, qty: 1 };
    if (VARIANT_ID) body.variantId = VARIANT_ID;

    const addResp = http.post(
      `${BASE_URL}/cart-service/cart/add`,
      jsonBody(body),
      { headers }
    );
    const ok = check(addResp, { "cart add 200": (r) => r.status === 200 || r.status === 201 });
    cartAddSuccess.add(ok);

    const viewResp = http.get(`${BASE_URL}/cart-service/cart`, { headers });
    check(viewResp, { "view cart 200": (r) => r.status === 200 });
    sleep(0.5);
  });

  if (!addressId) return;

  // ── 6. Place order ─────────────────────────────────────────────────────────
  let orderId = null;
  let paymentId = null;

  group("order", function () {
    const body = { addressId };
    if (COUPON) body.couponCode = COUPON;

    const start = Date.now();
    const resp = http.post(
      `${BASE_URL}/order-service/order`,
      jsonBody(body),
      { headers }
    );
    orderDuration.add(Date.now() - start);

    const ok = check(resp, {
      "place order 201": (r) => r.status === 201 || r.status === 200,
      "order has id": (r) => {
        const d = extractData(r);
        return d && d.id;
      },
    });
    orderSuccess.add(ok);

    if (ok) {
      ordersPlaced.add(1);
      const d = extractData(resp);
      if (d) { orderId = d.id; paymentId = d.paymentId; }
    }
    sleep(0.5);
  });

  if (!orderId) return;

  // ── 7. Confirm order ───────────────────────────────────────────────────────
  group("confirm", function () {
    const resp = http.put(`${BASE_URL}/order-service/order/${orderId}/confirm`, null, { headers });
    check(resp, { "confirm order 200": (r) => r.status === 200 });

    if (paymentId) {
      const pResp = http.get(`${BASE_URL}/payment-service/${paymentId}`, { headers });
      check(pResp, { "payment resolved": (r) => r.status === 200 });
    }
  });

  sleep(1);
}

// ── Teardown ──────────────────────────────────────────────────────────────────

export function teardown(data) {
  console.log("Load test complete.");
  console.log(`Orders placed: ${data.ordersPlaced || "see metrics"}`);
}
