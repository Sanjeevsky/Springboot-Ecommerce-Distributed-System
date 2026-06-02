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
const e2eCollectionFile = "postman/Ecommerce-E2E-Complete.postman_collection.json";
const environmentFiles = ["postman/Ecommerce-Local.postman_environment.json"];

const bannedMarkers = [
  { pattern: /\/raw\b/i, reason: "raw endpoints should not be used" },
  { pattern: /raw-service/i, reason: "raw service endpoints should not be used" },
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
  const collectionEventCode = (collection.event || [])
    .filter((event) => event.listen === "prerequest")
    .map((event) => scriptLines(event.script))
    .join("\n");

  if (!collectionEventCode.includes("seedRunnerVariables")) {
    fail(`${relativePath}: collection-level pre-request script must seed runner variables`);
  }
  if (!collectionEventCode.includes("setRunnerVar")) {
    fail(`${relativePath}: collection-level pre-request script must save seeded values for runner use`);
  }
}

function requestUrl(item) {
  return item && item.request && item.request.url && item.request.url.raw || "";
}

function requestMethod(item) {
  return item && item.request && (item.request.method || "").toUpperCase() || "";
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
      if (!code.includes("pm.execution.setNextRequest") || !code.includes("_orderCreateRetryCount")) {
        fail(`${relativePath}: ${requestPath}: order create must retry transient checkout/payment cold-start failures`);
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
    validateRequestOrder(relativePath, collection, ["Place Order", "Re-add Item for Coupon Order", "Place Order (with Coupon)"]);
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
  validateAsyncRunnerRetries(relativePath, collection);
  validateGatewayRoutedRequests(relativePath, collection);
  validateRunnerStateRepairs(relativePath, collection);

  const declaredVariables = new Set(environmentVariables);
  const collectionVariables = new Set();
  for (const variable of collection.variable || []) {
    if (variable && variable.key) {
      collectionVariables.add(variable.key);
      declaredVariables.add(variable.key);
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
