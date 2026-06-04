/**
 * k6 Load Test — Catalog Browse (read-heavy, Redis-cached)
 *
 * Validates catalog-service Redis cache performance under concurrent reads.
 *
 * Product data is seeded during setup when PRODUCT_ID is not supplied.
 *
 * Run: k6 run catalog-browse.js
 * To reuse existing catalog data:
 *   k6 run -e PRODUCT_ID=<uuid> catalog-browse.js
 */

import http from "k6/http";
import { check, fail, group, sleep } from "k6";
import { Rate, Trend } from "k6/metrics";

const cacheHitRate   = new Rate("cache_effective_rate");
const searchDuration = new Trend("search_duration_ms", true);

const BASE_URL   = __ENV.BASE_URL || "http://localhost:8081";
const PRODUCT_ID = __ENV.PRODUCT_ID || null;

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

const JSON_HEADERS = { "Content-Type": "application/json" };

function authHeaders(authToken) {
  return { "Content-Type": "application/json", Authorization: `Bearer ${authToken}` };
}

function jsonBody(obj) {
  return JSON.stringify(obj);
}

function randomString(length) {
  const alphabet = "abcdefghijklmnopqrstuvwxyz0123456789";
  let value = "";
  for (let i = 0; i < length; i += 1) {
    value += alphabet[Math.floor(Math.random() * alphabet.length)];
  }
  return value;
}

function extractData(resp) {
  try {
    return JSON.parse(resp.body).data;
  } catch {
    return null;
  }
}

function requireSeedData(resp, label) {
  const data = extractData(resp);
  if (!data || !data.id) {
    fail(`Unable to seed catalog ${label} for browse load test`);
  }
  return data;
}

function createCatalogSeed(headers) {
  const suffix = randomString(8);
  const brandResp = http.post(
    `${BASE_URL}/catalog-service/add-brand?name=${encodeURIComponent(`BrowseBrand_${suffix}`)}`,
    null,
    { headers }
  );
  const brand = requireSeedData(brandResp, "brand");

  const categoryResp = http.post(
    `${BASE_URL}/catalog-service/addCategory?categoryName=${encodeURIComponent(`BrowseCategory_${suffix}`)}`,
    null,
    { headers }
  );
  const category = requireSeedData(categoryResp, "category");

  const subCategoryResp = http.post(
    `${BASE_URL}/catalog-service/add-subcategory/${category.id}?subcategoryName=${encodeURIComponent(`BrowsePhones_${suffix}`)}`,
    null,
    { headers }
  );
  const subCategory = requireSeedData(subCategoryResp, "subcategory");

  const productResp = http.post(
    `${BASE_URL}/catalog-service/product/addProduct?brandId=${brand.id}&categoryId=${category.id}&subCategoryId=${subCategory.id}`,
    jsonBody({
      name: `Browse Phone ${suffix}`,
      description: "Catalog browse load-test seeded product",
      model: `BROWSE-${suffix}`,
      mrpPrice: 99999,
      salePrice: 89999,
      gstValue: 18,
      status: 1,
      discount: 10000,
      images: [],
      hasVariant: false,
    }),
    { headers }
  );
  const product = requireSeedData(productResp, "product");

  return { productId: product.id };
}

export function setup() {
  const email = `catalog_load_${randomString(6)}@test.com`;
  http.post(
    `${BASE_URL}/auth-service/signup`,
    jsonBody({ email, password: "Load@1234", firstName: "Cat", lastName: "Load", role: "USER" }),
    { headers: JSON_HEADERS }
  );
  const resp = http.post(
    `${BASE_URL}/auth-service/login`,
    jsonBody({ email, password: "Load@1234" }),
    { headers: JSON_HEADERS }
  );
  const data = extractData(resp);
  const token = data ? (data.token || data) : "";
  if (!token) {
    fail("Unable to authenticate catalog browse load-test user");
  }

  let productId = PRODUCT_ID;
  if (!productId) {
    const seed = createCatalogSeed(authHeaders(token));
    productId = seed.productId;
  }

  return { token, productId };
}

export default function (data) {
  const productId = data.productId || PRODUCT_ID;
  const headers = {
    "Content-Type": "application/json",
    Authorization: `Bearer ${data.token}`,
  };

  group("browse_catalog", function () {
    // Get specific product (cache hit after first request)
    const productResp = http.get(`${BASE_URL}/catalog-service/product/getProduct/${productId}`, { headers });
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
