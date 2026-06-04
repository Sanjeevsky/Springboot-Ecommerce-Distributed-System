#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");
const collectionFiles = [
  "postman/Ecommerce-API.postman_collection.json",
  "postman/Ecommerce-DataSeed.postman_collection.json",
  "postman/Ecommerce-E2E-Complete.postman_collection.json",
];
const apiCollectionFile = "postman/Ecommerce-API.postman_collection.json";
const dataSeedCollectionFile = "postman/Ecommerce-DataSeed.postman_collection.json";
const e2eCollectionFile = "postman/Ecommerce-E2E-Complete.postman_collection.json";
const environmentFiles = ["postman/Ecommerce-Local.postman_environment.json"];
const localGatewayBaseUrl = "http://localhost:8081";
const directLocalServiceUrlPattern = /^https?:\/\/(?:localhost|127\.0\.0\.1):(?:8071|8761|808[2-9]|809[0-2])(?:\/|$)/;

const bannedMarkers = [
  { pattern: /\/raw\b/i, reason: "raw endpoints should not be used" },
  { pattern: /raw-service/i, reason: "raw service endpoints should not be used" },
  { pattern: /\/shopping-cart-service\//i, reason: "cart requests must use the standard /cart-service route" },
  { pattern: /\bpinCode\b/, reason: "address contract uses zipCode" },
  { pattern: /\bmaxUses\b/, reason: "coupon contract uses maxUsageCount" },
  { pattern: /2026-12-31T23:59:59/, reason: "coupon expiryDate expects yyyy-MM-dd" },
  { pattern: /"expiryDate"\s*:\s*"2026-12-31"/, reason: "coupon fixtures should not expire during normal runner use" },
  { pattern: /"expiryDate"\s*:\s*"2027-12-31"/, reason: "coupon fixtures should not expire during normal runner use" },
];

let failed = false;

function fail(message) {
  failed = true;
  console.error(`ERROR: ${message}`);
}

function readJson(relativePath) {
  const absolutePath = path.join(root, relativePath);
  try {
    return JSON.parse(fs.readFileSync(absolutePath, "utf8"));
  } catch (error) {
    fail(`${relativePath}: invalid JSON: ${error.message}`);
    return null;
  }
}

function walkItems(items, parent = [], out = []) {
  for (const item of items || []) {
    const currentPath = [...parent, item.name || "<unnamed>"];
    if (item.request) {
      out.push({ path: currentPath.join(" > "), item });
    }
    if (item.item) {
      walkItems(item.item, currentPath, out);
    }
  }
  return out;
}

function scriptLines(script) {
  return Array.isArray(script && script.exec) ? script.exec.join("\n") : "";
}

function validateScript(relativePath, ownerPath, event) {
  const code = scriptLines(event && event.script);
  if (!code.trim()) {
    return;
  }
  try {
    new Function("pm", "console", code);
  } catch (error) {
    fail(`${relativePath}: ${ownerPath}: ${event.listen} script does not compile: ${error.message}`);
  }
  if (/console\.log\([^;]*token\.substring/i.test(code)
      || /console\.log\([^;]*Token saved[^;]*token/i.test(code)) {
    fail(`${relativePath}: ${ownerPath}: ${event.listen} script must not log JWT token prefixes`);
  }
  if (/console\.log\('saved '\s*\+\s*key\s*\+\s*':'\s*,\s*value\)/.test(code)) {
    fail(`${relativePath}: ${ownerPath}: ${event.listen} script must redact sensitive saved runner variables`);
  }
  if (/function saveRunnerVar\(key, value\)/.test(code)
      && !/\/token\|password\/i\.test\(key\)/.test(code)) {
    fail(`${relativePath}: ${ownerPath}: ${event.listen} saveRunnerVar must redact token/password values in logs`);
  }
}

function validateRawJsonBody(relativePath, requestPath, body) {
  const raw = body && typeof body.raw === "string" ? body.raw.trim() : "";
  if (!raw || (!raw.startsWith("{") && !raw.startsWith("["))) {
    return;
  }

  const normalized = raw.replace(/{{\s*[^}\s]+\s*}}/g, "PLACEHOLDER");
  try {
    JSON.parse(normalized);
  } catch (error) {
    fail(`${relativePath}: ${requestPath}: request body is not valid JSON: ${error.message}`);
  }
}

function collectVariablesFromText(text, out) {
  const regex = /{{\s*([^}\s]+)\s*}}/g;
  let match;
  while ((match = regex.exec(text)) !== null) {
    const name = match[1];
    if (!name.startsWith("$")) {
      out.add(name);
    }
  }
}

function collectScriptSetVariables(code, out) {
  const setters = [
    /pm\.(?:collectionVariables|environment)\.set\(\s*['"]([^'"]+)['"]/g,
    /setRunnerVar\(\s*['"]([^'"]+)['"]/g,
  ];
  for (const regex of setters) {
    let match;
    while ((match = regex.exec(code)) !== null) {
      out.add(match[1]);
    }
  }
}

function isInternalRunnerVariable(name) {
  return name.startsWith("_");
}

function collectObjectVariables(value, out) {
  if (value == null) {
    return;
  }
  if (typeof value === "string") {
    collectVariablesFromText(value, out);
    return;
  }
  if (Array.isArray(value)) {
    for (const entry of value) {
      collectObjectVariables(entry, out);
    }
    return;
  }
  if (typeof value === "object") {
    for (const entry of Object.values(value)) {
      collectObjectVariables(entry, out);
    }
  }
}

function walkFiles(directory, out = []) {
  for (const entry of fs.readdirSync(directory, { withFileTypes: true })) {
    const absolutePath = path.join(directory, entry.name);
    if (entry.isDirectory()) {
      if (entry.name !== "target") {
        walkFiles(absolutePath, out);
      }
    } else if (entry.isFile()) {
      out.push(absolutePath);
    }
  }
  return out;
}

function joinRoutePath(basePath, methodPath) {
  return `/${[basePath, methodPath].filter(Boolean).join("/")}`
    .replace(/\/+/g, "/")
    .replace(/\/$/, "") || "/";
}

function normalizeRoutePath(routePath) {
  const withoutBaseUrl = routePath
    .replace(/^{{baseUrl}}/, "")
    .replace(/^https?:\/\/[^/]+/, "")
    .split("?")[0];

  const segments = withoutBaseUrl
    .split("/")
    .filter(Boolean)
    .map((segment) => (/^{{.*}}$/.test(segment) || /^{.*}$/.test(segment) ? "{var}" : segment));

  return `/${segments.join("/")}`;
}

function mappingValue(annotation) {
  const match = annotation.match(/\(\s*(?:value\s*=\s*)?"([^"]*)"/)
    || annotation.match(/\(\s*(?:value\s*=\s*)?'([^']*)'/);
  return match ? match[1] : "";
}

function collectControllerRoutes() {
  const routes = [];
  for (const absolutePath of walkFiles(root)) {
    if (!absolutePath.endsWith(".java") || !absolutePath.includes(`${path.sep}src${path.sep}main${path.sep}java${path.sep}`)) {
      continue;
    }
    if (!absolutePath.includes(`${path.sep}controller${path.sep}`)) {
      continue;
    }

    const code = fs.readFileSync(absolutePath, "utf8");
    if (!code.includes("@RestController")) {
      continue;
    }

    const baseMatch = code.match(/@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"/);
    const basePath = baseMatch ? baseMatch[1] : "";
    const mappingRegex = /@(Get|Post|Put|Delete|Patch)Mapping\s*(\([^)]*\))?/g;
    let match;
    while ((match = mappingRegex.exec(code)) !== null) {
      routes.push({
        method: match[1].toUpperCase(),
        path: normalizeRoutePath(joinRoutePath(basePath, mappingValue(match[0]))),
        file: path.relative(root, absolutePath),
      });
    }
  }
  return routes;
}

function collectPostmanRequests(collection) {
  const requests = new Set();
  for (const { item } of walkItems(collection.item)) {
    const request = item.request || {};
    const method = (request.method || "").toUpperCase();
    const rawUrl = request.url && request.url.raw;
    if (method && rawUrl) {
      requests.add(`${method} ${normalizeRoutePath(rawUrl)}`);
    }
  }
  return requests;
}

function validateRouteCoverage(relativePath, collection) {
  const postmanRequests = collectPostmanRequests(collection);
  for (const route of collectControllerRoutes()) {
    const key = `${route.method} ${route.path}`;
    if (!postmanRequests.has(key)) {
      fail(`${relativePath}: missing ${key} from ${route.file}`);
    }
  }
}

function validateCollectionRunnerSeeding(relativePath, collection) {
  const collectionPreRequestCode = (collection.event || [])
    .filter((event) => event.listen === "prerequest")
    .map((event) => scriptLines(event.script))
    .join("\n");

  if (!collectionPreRequestCode.includes("seedRunnerVariables")) {
    fail(`${relativePath}: collection-level pre-request script must seed runner variables`);
  }
  if (!collectionPreRequestCode.includes("setRunnerVar")
      || !collectionPreRequestCode.includes("pm.collectionVariables.set")
      || !collectionPreRequestCode.includes("pm.environment.set")) {
    fail(`${relativePath}: collection-level pre-request script must save seeded values for runner use`);
  }

  const collectionTestCode = (collection.event || [])
    .filter((event) => event.listen === "test")
    .map((event) => scriptLines(event.script))
    .join("\n");

  if (!collectionTestCode.includes("saveRunnerVar")
      || !collectionTestCode.includes("pm.collectionVariables.set")
      || !collectionTestCode.includes("pm.environment.set")
      || !collectionTestCode.includes("rawUrl")) {
    fail(`${relativePath}: collection-level test script must persist response IDs into collection and environment variables`);
  }

  for (const savedVariable of [
    "token",
    "brandId",
    "categoryId",
    "subCategoryId",
    "productId",
    "variantId",
    "addressId",
    "orderId",
    "paymentId",
    "couponId",
    "reviewId",
    "notificationId",
    "orderStatus",
    "paymentStatus",
  ]) {
    if (!collectionTestCode.includes(`'${savedVariable}'`)
        && !collectionTestCode.includes(`"${savedVariable}"`)) {
      fail(`${relativePath}: collection-level test script must save ${savedVariable} for runner chaining`);
    }
  }
}

function requestUrl(item) {
  return item && item.request && item.request.url && item.request.url.raw || "";
}

function requestMethod(item) {
  return item && item.request && (item.request.method || "").toUpperCase() || "";
}

function requestBodyRaw(item) {
  return item && item.request && item.request.body && item.request.body.raw || "";
}

function requestEventCode(item) {
  return (item.event || [])
    .map((event) => scriptLines(event.script))
    .join("\n");
}

function validateAsyncRunnerRetries(relativePath, collection) {
  for (const { path: requestPath, item } of walkItems(collection.item)) {
    const method = requestMethod(item);
    const url = requestUrl(item);
    const code = requestEventCode(item);

    if (method === "POST" && url.endsWith("/review-service/review")) {
      if (!code.includes("pm.execution.setNextRequest") || !code.includes("_reviewRetryCount")) {
        fail(`${relativePath}: ${requestPath}: review submit must retry while Kafka review eligibility is catching up`);
      }
    }

    if (method === "GET" && url.endsWith("/notification-service/notifications")) {
      if (!code.includes("pm.execution.setNextRequest") || !code.includes("_notificationRetryCount")) {
        fail(`${relativePath}: ${requestPath}: notification list must retry before notificationId is used later`);
      }
    }

    if (method === "POST" && url.endsWith("/cart-service/cart/add")) {
      if (!code.includes("pm.execution.setNextRequest") || !code.includes("_cartAddRetryCount")) {
        fail(`${relativePath}: ${requestPath}: cart add must retry transient catalog/cart cold-start failures`);
      }
    }

    if (method === "POST" && url.endsWith("/order-service/order")) {
      if (!code.includes("pm.execution.setNextRequest")
          || (!code.includes("_orderCreateRetryCount") && !code.includes("_orderConflictRetryCount"))) {
        fail(`${relativePath}: ${requestPath}: order create must retry transient checkout/payment cold-start failures`);
      }
    }

    if (method === "POST" && url.endsWith("/inventory-service/stock")) {
      if (!code.includes("pm.execution.setNextRequest") || !code.includes("_inventoryStockRetryCount")) {
        fail(`${relativePath}: ${requestPath}: inventory stock write must retry transient inventory cold-start failures`);
      }
    }

    if (method === "GET" && url.endsWith("/payment-service/{{paymentId}}") && code.includes("Payment is SUCCESS")) {
      if (!code.includes("pm.execution.setNextRequest") || !code.includes("_paymentSuccessRetryCount")) {
        fail(`${relativePath}: ${requestPath}: payment success verification must retry while order confirmation is becoming visible`);
      }
    }
  }
}

function validateGatewayRoutedRequests(relativePath, collection) {
  for (const { path: requestPath, item } of walkItems(collection.item)) {
    const url = requestUrl(item);
    if (url && !url.startsWith("{{baseUrl}}/")) {
      fail(`${relativePath}: ${requestPath}: request URL must route through {{baseUrl}} instead of ${url}`);
    }
  }
}

function validateBaseUrlDefault(relativePath, ownerPath, value) {
  if (!value) {
    fail(`${relativePath}: ${ownerPath}: baseUrl must default to ${localGatewayBaseUrl}`);
    return;
  }
  if (value !== localGatewayBaseUrl) {
    fail(`${relativePath}: ${ownerPath}: baseUrl must default to gateway ${localGatewayBaseUrl}, found ${value}`);
  }
  if (directLocalServiceUrlPattern.test(value)) {
    fail(`${relativePath}: ${ownerPath}: baseUrl must not point at a direct local service port`);
  }
}

function requestByName(collection, name) {
  const match = walkItems(collection.item).find(({ item }) => item.name === name);
  return match && match.item;
}

function hasRequestHeader(item, key) {
  return ((item && item.request && item.request.header) || [])
    .some((header) => header && header.key === key);
}

function requestHeaderValue(item, key) {
  const header = ((item && item.request && item.request.header) || [])
    .find((entry) => entry && entry.key === key);
  return header && header.value || "";
}

function requestAuthType(item) {
  return item && item.request && item.request.auth && item.request.auth.type || "";
}

function hasBearerAuthorizationHeader(item) {
  const value = requestHeaderValue(item, "Authorization");
  return value && /^Bearer\s+\S+/.test(value.trim());
}

function hasAuthorization(item, collection) {
  return hasBearerAuthorizationHeader(item)
    || requestAuthType(item) === "bearer"
    || (!requestAuthType(item) && collection && collection.auth && collection.auth.type === "bearer");
}

function isPublicGatewayRoute(url) {
  const path = normalizeRoutePath(url);
  return path === "/auth-service/signup"
    || path === "/auth-service/login"
    || path === "/catalog-service/product/list"
    || path === "/catalog-service/product/search"
    || path === "/catalog-service/product/getProduct/{var}";
}

function validateGatewayRequestAuth(relativePath, collection) {
  for (const { path: requestPath, item } of walkItems(collection.item)) {
    const url = requestUrl(item);
    if (!url || isPublicGatewayRoute(url)) {
      continue;
    }
    if (requestAuthType(item) === "noauth") {
      fail(`${relativePath}: ${requestPath}: protected gateway request must not override auth with noauth`);
    }
    if (hasRequestHeader(item, "Authorization") && !hasBearerAuthorizationHeader(item)) {
      fail(`${relativePath}: ${requestPath}: protected gateway request Authorization header must use Bearer <token>`);
    }
    if (!hasAuthorization(item, collection)) {
      fail(`${relativePath}: ${requestPath}: protected gateway request must include Authorization or inherit bearer auth`);
    }
  }
}

function validateProtectedRequestAuth(relativePath, collection, requestName) {
  const item = requestByName(collection, requestName);
  if (!item) {
    fail(`${relativePath}: missing protected request "${requestName}"`);
    return;
  }
  if (requestAuthType(item) === "noauth") {
    fail(`${relativePath}: ${requestName}: protected request must not override auth with noauth`);
  }
  if (hasRequestHeader(item, "Authorization") && !hasBearerAuthorizationHeader(item)) {
    fail(`${relativePath}: ${requestName}: protected request Authorization header must use Bearer <token>`);
  }
  if (!hasAuthorization(item, collection)) {
    fail(`${relativePath}: ${requestName}: protected request must include Authorization or inherit bearer auth`);
  }
}

function validateApiCollectionGuards(relativePath, collection) {
  if (relativePath !== apiCollectionFile) {
    return;
  }

  const collectionTestCode = (collection.event || [])
    .filter((event) => event.listen === "test")
    .map((event) => scriptLines(event.script))
    .join("\n");

  if (!collectionTestCode.includes("Runner request returned 2xx")
      || !collectionTestCode.includes("isTransientRunnerRetry")) {
    fail(`${relativePath}: collection-level test must fail non-2xx API responses while preserving bounded retry flows`);
  }
  if (!collectionTestCode.includes("isExpectedRunnerNon2xx")
      || !collectionTestCode.includes("Reject Order with Insufficient Inventory")
      || !collectionTestCode.includes("Reject Order Idempotency Conflict")
      || !collectionTestCode.includes("Reject Payment Idempotency Conflict")) {
    fail(`${relativePath}: collection-level test must allow only expected negative 400 responses`);
  }

  const protectedRequests = [
    "Update Password",
    "Product Service Status",
    "Get Active Coupons",
    "Get Approved Reviews for Product",
    "Get Review Summary for Product",
  ];
  for (const requestName of protectedRequests) {
    validateProtectedRequestAuth(relativePath, collection, requestName);
  }

  const couponOrder = requestByName(collection, "Place Order (with Coupon)");
  if (!couponOrder || !requestEventCode(couponOrder).includes("Coupon discount applied")) {
    fail(`${relativePath}: Place Order (with Coupon) must assert a non-zero coupon discount`);
  }

  const standaloneCouponCreate = requestByName(collection, "Create Coupon");
  const standaloneCouponBody = standaloneCouponCreate
    && standaloneCouponCreate.request
    && standaloneCouponCreate.request.body
    && standaloneCouponCreate.request.body.raw || "";
  if (!standaloneCouponBody.includes("{{couponCode}}MGMT")) {
    fail(`${relativePath}: standalone Create Coupon must use a distinct management coupon code`);
  }

  const approvedReviews = requestByName(collection, "Get Approved Reviews for Product");
  if (!approvedReviews || !requestEventCode(approvedReviews).includes("Approved review available")) {
    fail(`${relativePath}: approved review request must assert at least one approved review`);
  }

  const reviewSummary = requestByName(collection, "Get Review Summary for Product");
  if (!reviewSummary || !requestEventCode(reviewSummary).includes("Summary includes approved review")) {
    fail(`${relativePath}: review summary request must assert approved review totals`);
  }

  const paymentStatusByOrder = requestByName(collection, "Get Payment Status by Order");
  if (!paymentStatusByOrder || !requestEventCode(paymentStatusByOrder).includes("Cancelled order payment is REFUNDED")) {
    fail(`${relativePath}: payment status by order must assert cancelled order refund state`);
  }
}

function validateE2eCollectionGuards(relativePath, collection) {
  if (relativePath !== e2eCollectionFile) {
    return;
  }

  validateProtectedRequestAuth(relativePath, collection, "03 — Update Password");
}

function validateInsufficientInventoryCoverage(relativePath, collection) {
  const expectations = {
    [apiCollectionFile]: {
      add: "Re-add Item for Insufficient Inventory Order",
      qty: "Set Insufficient Inventory Quantity",
      reject: "Reject Order with Insufficient Inventory",
      clear: "Clear Cart after Insufficient Inventory Check",
    },
    [e2eCollectionFile]: {
      add: "44b — Re-add Item for Insufficient Inventory Check",
      qty: "44c — Set Cart Quantity beyond Stock",
      reject: "44d — Reject Order with Insufficient Inventory",
      clear: "44e — Clear Cart after Insufficient Inventory Check",
    },
  }[relativePath];

  if (!expectations) {
    return;
  }

  const qtyRequest = requestByName(collection, expectations.qty);
  if (!qtyRequest
      || requestMethod(qtyRequest) !== "PUT"
      || !requestUrl(qtyRequest).includes("/cart-service/cart/item/{{productId}}?qty=9999")
      || !requestEventCode(qtyRequest).includes("Cart quantity set to 9999")) {
    fail(`${relativePath}: ${expectations.qty} must set cart quantity beyond seeded inventory`);
  }

  const rejectRequest = requestByName(collection, expectations.reject);
  if (!rejectRequest
      || requestMethod(rejectRequest) !== "POST"
      || requestUrl(rejectRequest) !== "{{baseUrl}}/order-service/order"
      || requestHeaderValue(rejectRequest, "Idempotency-Key") !== "{{insufficientOrderIdempotencyKey}}"
      || !requestEventCode(rejectRequest).includes("status 400")
      || !requestEventCode(rejectRequest).includes("Insufficient stock")) {
    fail(`${relativePath}: ${expectations.reject} must assert order-service rejects insufficient inventory with 400`);
  }

  const clearRequest = requestByName(collection, expectations.clear);
  if (!clearRequest || !requestEventCode(clearRequest).includes("Cart cleared after insufficient inventory check")) {
    fail(`${relativePath}: ${expectations.clear} must clear cart state after the negative inventory check`);
  }

  validateRequestOrder(relativePath, collection, [
    expectations.add,
    expectations.qty,
    expectations.reject,
    expectations.clear,
  ]);
}

function validateIdempotencyConflictCoverage(relativePath, collection) {
  const expectations = {
    [apiCollectionFile]: {
      order: "Reject Order Idempotency Conflict",
      payment: "Reject Payment Idempotency Conflict",
    },
    [e2eCollectionFile]: {
      order: "40c — Reject Order Idempotency Conflict",
      payment: "45a — Reject Payment Idempotency Conflict",
    },
  }[relativePath];

  if (!expectations) {
    return;
  }

  const orderConflict = requestByName(collection, expectations.order);
  if (!orderConflict
      || requestMethod(orderConflict) !== "POST"
      || requestUrl(orderConflict) !== "{{baseUrl}}/order-service/order"
      || requestHeaderValue(orderConflict, "Idempotency-Key") !== "{{orderIdempotencyKey}}"
      || !requestBodyRaw(orderConflict).includes('"addressId": "{{productId}}"')
      || !requestEventCode(orderConflict).includes("different order request")) {
    fail(`${relativePath}: ${expectations.order} must assert conflicting order idempotency-key reuse returns 400`);
  }

  const paymentConflict = requestByName(collection, expectations.payment);
  if (!paymentConflict
      || requestMethod(paymentConflict) !== "POST"
      || requestUrl(paymentConflict) !== "{{baseUrl}}/payment-service/initiate"
      || requestHeaderValue(paymentConflict, "Idempotency-Key") !== "{{paymentIdempotencyKey}}"
      || !requestBodyRaw(paymentConflict).includes('"orderId": "{{productId}}"')
      || !requestEventCode(paymentConflict).includes("different payment request")) {
    fail(`${relativePath}: ${expectations.payment} must assert conflicting payment idempotency-key reuse returns 400`);
  }
}

function validateDataSeedCollectionGuards(relativePath, collection) {
  if (relativePath !== dataSeedCollectionFile) {
    return;
  }

  const submitReview = requestByName(collection, "19 — Submit Product Review");
  if (!submitReview || !requestEventCode(submitReview).includes("Review ID present")) {
    fail(`${relativePath}: Submit Product Review must save reviewId before moderation`);
  }

  const expectedAssertions = [
    ["08 — Seed Inventory (50 units)", "Seed stock quantity available"],
    ["09 — Create Coupon (SAVE10)", "Coupon code matches runner value"],
    ["11 — Add Product to Cart", "Cart has seeded item"],
    ["12 — View Cart", "Cart contains seeded item"],
    ["13 — Validate Coupon", "Coupon is valid"],
    ["15 — Check Payment Status", "Payment starts PENDING"],
    ["18 — Get Order History", "Order history includes seeded order"],
    ["23 — Add to Wishlist", "Wishlist item has productId"],
    ["25 — Check Inventory After Order", "Inventory reserved order quantity"],
  ];

  for (const [requestName, assertionName] of expectedAssertions) {
    const request = requestByName(collection, requestName);
    if (!request || !requestEventCode(request).includes(assertionName)) {
      fail(`${relativePath}: ${requestName} must assert "${assertionName}"`);
    }
  }

  const moderateReview = requestByName(collection, "20 — Moderate Product Review");
  if (!moderateReview || !requestEventCode(moderateReview).includes("Review status is APPROVED")) {
    fail(`${relativePath}: data seed review must be moderated to APPROVED`);
  }

  const approvedReviews = requestByName(collection, "21 — Get Approved Reviews");
  if (!approvedReviews || !requestEventCode(approvedReviews).includes("Seed approved review available")) {
    fail(`${relativePath}: data seed must verify approved review availability`);
  }

  const reviewSummary = requestByName(collection, "22 — Get Review Summary");
  if (!reviewSummary || !requestEventCode(reviewSummary).includes("Seed summary includes approved review")) {
    fail(`${relativePath}: data seed must verify approved review summary`);
  }
}

function requestIndexesByName(collection) {
  const indexes = new Map();
  walkItems(collection.item).forEach(({ item }, index) => {
    if (item.name && !indexes.has(item.name)) {
      indexes.set(item.name, index);
    }
  });
  return indexes;
}

function validateRequestOrder(relativePath, collection, requiredOrder) {
  const indexes = requestIndexesByName(collection);
  for (const requestName of requiredOrder) {
    if (!indexes.has(requestName)) {
      fail(`${relativePath}: missing runner-order request "${requestName}"`);
      return;
    }
  }

  for (let i = 1; i < requiredOrder.length; i += 1) {
    const previous = requiredOrder[i - 1];
    const current = requiredOrder[i];
    if (indexes.get(previous) >= indexes.get(current)) {
      fail(`${relativePath}: "${previous}" must run before "${current}"`);
    }
  }
}

function validateRunnerStateRepairs(relativePath, collection) {
  if (relativePath === apiCollectionFile) {
    validateRequestOrder(relativePath, collection, ["Clear Cart", "Re-add Item for Order", "Place Order"]);
    validateRequestOrder(relativePath, collection, ["Delete Address", "Re-add Address for Order", "Place Order"]);
    validateRequestOrder(relativePath, collection, ["Seed Inventory for Orders", "Place Order"]);
    validateRequestOrder(relativePath, collection, ["Create Coupon for Coupon Order", "Place Order (with Coupon)"]);
    validateRequestOrder(relativePath, collection, ["Place Order", "Re-add Item for Coupon Order", "Place Order (with Coupon)"]);
    validateRequestOrder(relativePath, collection, [
      "Cancel Order",
      "Re-add Item for Insufficient Inventory Order",
      "Set Insufficient Inventory Quantity",
      "Reject Order with Insufficient Inventory",
      "Clear Cart after Insufficient Inventory Check",
      "Initiate Payment",
    ]);
    validateRequestOrder(relativePath, collection, ["Submit Review", "Moderate Review (Admin)", "Get Approved Reviews for Product", "Get Review Summary for Product"]);
    validateRequestOrder(relativePath, collection, ["Add to Wishlist", "Remove from Wishlist", "Re-add to Wishlist for Move", "Move to Cart"]);
  }

  if (relativePath === dataSeedCollectionFile) {
    validateRequestOrder(relativePath, collection, [
      "19 — Submit Product Review",
      "20 — Moderate Product Review",
      "21 — Get Approved Reviews",
      "22 — Get Review Summary",
      "23 — Add to Wishlist",
    ]);
  }

  if (relativePath === e2eCollectionFile) {
    validateRequestOrder(relativePath, collection, [
      "30 — Remove Item from Cart",
      "31 — Re-add Item (before order)",
      "40 — Create Order (plain) → save orderId + paymentId",
    ]);
    validateRequestOrder(relativePath, collection, [
      "34 — Move to Cart",
      "34b — Re-add to Wishlist before delete",
      "35 — Remove from Wishlist",
    ]);
    validateRequestOrder(relativePath, collection, [
      "40 — Create Order (plain) → save orderId + paymentId",
      "40a — Re-add Item for Coupon Order",
      "40b — Create Order with Coupon → verify discount applied",
    ]);
    validateRequestOrder(relativePath, collection, [
      "44 — Cancel Order",
      "44a — Verify Cancel Refund",
      "44b — Re-add Item for Insufficient Inventory Check",
      "44c — Set Cart Quantity beyond Stock",
      "44d — Reject Order with Insufficient Inventory",
      "44e — Clear Cart after Insufficient Inventory Check",
      "45 — Initiate Payment → save paymentId",
    ]);
  }
}

function validateBannedMarkers(relativePath) {
  const text = fs.readFileSync(path.join(root, relativePath), "utf8");
  for (const { pattern, reason } of bannedMarkers) {
    if (pattern.test(text)) {
      fail(`${relativePath}: contains banned marker ${pattern}: ${reason}`);
    }
  }
}

const environmentVariables = new Set();
for (const environmentFile of environmentFiles) {
  const environment = readJson(environmentFile);
  for (const entry of (environment && environment.values) || []) {
    if (entry && entry.key) {
      environmentVariables.add(entry.key);
      if (entry.key === "baseUrl") {
        validateBaseUrlDefault(environmentFile, "environment baseUrl", entry.value);
      }
    }
  }
}

for (const relativePath of collectionFiles) {
  const collection = readJson(relativePath);
  if (!collection) {
    continue;
  }

  validateBannedMarkers(relativePath);
  if (relativePath === apiCollectionFile || relativePath === e2eCollectionFile) {
    validateRouteCoverage(relativePath, collection);
  }
  validateCollectionRunnerSeeding(relativePath, collection);
  validateApiCollectionGuards(relativePath, collection);
  validateE2eCollectionGuards(relativePath, collection);
  validateDataSeedCollectionGuards(relativePath, collection);
  validateInsufficientInventoryCoverage(relativePath, collection);
  validateIdempotencyConflictCoverage(relativePath, collection);
  validateAsyncRunnerRetries(relativePath, collection);
  validateGatewayRoutedRequests(relativePath, collection);
  validateGatewayRequestAuth(relativePath, collection);
  validateRunnerStateRepairs(relativePath, collection);

  const declaredVariables = new Set(environmentVariables);
  const collectionVariables = new Set();
  for (const variable of collection.variable || []) {
    if (variable && variable.key) {
      collectionVariables.add(variable.key);
      declaredVariables.add(variable.key);
      if (variable.key === "baseUrl") {
        validateBaseUrlDefault(relativePath, "collection baseUrl", variable.value);
      }
    }
  }

  const referencedVariables = new Set();
  const scriptSetVariables = new Set();
  const events = [...(collection.event || [])];
  for (const { path: requestPath, item } of walkItems(collection.item)) {
    events.push(...(item.event || []).map((event) => ({ ...event, requestPath })));
    collectObjectVariables(item.request, referencedVariables);
    validateRawJsonBody(relativePath, requestPath, item.request && item.request.body);
  }

  for (const event of events) {
    const ownerPath = event.requestPath || collection.info.name;
    const code = scriptLines(event.script);
    validateScript(relativePath, ownerPath, event);
    collectVariablesFromText(code, referencedVariables);
    collectScriptSetVariables(code, declaredVariables);
    collectScriptSetVariables(code, scriptSetVariables);
  }

  const missingVariables = [...referencedVariables]
    .filter((name) => !declaredVariables.has(name))
    .sort();
  for (const name of missingVariables) {
    fail(`${relativePath}: references {{${name}}} but no collection/environment/script setter defines it`);
  }

  const environmentMissingVariables = [...new Set([...collectionVariables, ...scriptSetVariables])]
    .filter((name) => !isInternalRunnerVariable(name))
    .filter((name) => !environmentVariables.has(name))
    .sort();
  for (const name of environmentMissingVariables) {
    fail(`${relativePath}: defines or sets ${name} but Ecommerce-Local environment does not declare it`);
  }
}

if (failed) {
  process.exit(1);
}

console.log("Postman validation passed");
