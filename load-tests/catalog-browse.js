/**
 * k6 Load Test — Catalog Browse (read-heavy, Redis-cached)
 *
 * Validates catalog-service Redis cache performance under concurrent reads.
 *
 * Run: k6 run catalog-browse.js
 */

import http from "k6/http";
import { check, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";
import { randomString } from "https://jslib.k6.io/k6-utils/1.4.0/index.js";

const cacheHitRate   = new Rate("cache_effective_rate");
const searchDuration = new Trend("search_duration_ms", true);

const BASE_URL   = __ENV.BASE_URL || "http://localhost:8081";
const PRODUCT_ID = __ENV.PRODUCT_ID || "00000000-0000-0000-0000-000000000001";

export const options = {
  scenarios: {
    browse: {
      executor: "ramping-arrival-rate",
      startRate: 5,
      timeUnit: "1s",
      preAllocatedVUs: 30,
      maxVUs: 100,
      stages: [
        { duration: "1m", target: 20 },
        { duration: "3m", target: 50 },
        { duration: "1m", target: 0 },
      ],
    },
  },
  thresholds: {
    http_req_failed:   ["rate<0.01"],
    http_req_duration: ["p(95)<500"],
    search_duration_ms: ["p(95)<800"],
  },
};

let token = "";

export function setup() {
  const email = `catalog_load_${randomString(6)}@test.com`;
  http.post(
    `${BASE_URL}/auth-service/signup`,
    JSON.stringify({ email, password: "Load@1234", firstName: "Cat", lastName: "Load", role: "USER" }),
    { headers: { "Content-Type": "application/json" } }
  );
  const resp = http.post(
    `${BASE_URL}/auth-service/login`,
    JSON.stringify({ email, password: "Load@1234" }),
    { headers: { "Content-Type": "application/json" } }
  );
  const data = JSON.parse(resp.body).data;
  return { token: data ? (data.token || data) : "" };
}

export default function (data) {
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${data.token}`,
  };

  group("browse_catalog", function () {
    // Get specific product (cache hit after first request)
    const productResp = http.get(`${BASE_URL}/catalog-service/product/getProduct/${PRODUCT_ID}`, { headers });
    const ok = check(productResp, { "get product 200": (r) => r.status === 200 });
    cacheHitRate.add(ok);
    sleep(0.1);

    // Search products by keyword
    const start = Date.now();
    const searchResp = http.get(
      `${BASE_URL}/catalog-service/product/search?q=phone&page=0&size=10`,
      { headers }
    );
    searchDuration.add(Date.now() - start);
    check(searchResp, { "search 200": (r) => r.status === 200 });
    sleep(0.2);

    // List brands
    const brandsResp = http.get(`${BASE_URL}/catalog-service/getBrands`, { headers });
    check(brandsResp, { "brands 200": (r) => r.status === 200 });
    sleep(0.1);
  });

  sleep(0.5);
}
