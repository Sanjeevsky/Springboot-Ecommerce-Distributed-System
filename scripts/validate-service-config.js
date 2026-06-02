#!/usr/bin/env node

const fs = require("fs");
const path = require("path");

const root = path.resolve(__dirname, "..");

const expectedApplicationNames = {
  "api-gateway": "api-gateway",
  "auth-server": "auth-service",
  "catalog-service": "catalog-service",
  "cloud-config": "configserver",
  "coupon-service": "coupon-service",
  "customer-service": "customer-service",
  "inventory-service": "inventory-service",
  "notification-service": "notification-service",
  "order-service": "order-service",
  "payment-service": "payment-service",
  "review-service": "review-service",
  "service-discovery": "service-discovery",
  "shopping-cart-service": "shopping-cart-service",
  "spring-server": "spring-server",
  "wishlist-service": "wishlist-service",
};

let failed = false;

function fail(message) {
  failed = true;
  console.error(`ERROR: ${message}`);
}

function propertiesFiles(service) {
  const resourceDir = path.join(root, service, "src", "main", "resources");
  if (!fs.existsSync(resourceDir)) {
    return [];
  }
  return fs.readdirSync(resourceDir)
    .filter((file) => /^application.*\.properties$/.test(file))
    .map((file) => path.join(resourceDir, file));
}

function dockerfile(service) {
  const file = path.join(root, service, "Dockerfile");
  return fs.existsSync(file) ? file : null;
}

function integrationTestFiles(service) {
  const testRoot = path.join(root, service, "src", "test", "java");
  if (!fs.existsSync(testRoot)) {
    return [];
  }

  const files = [];
  const stack = [testRoot];
  while (stack.length > 0) {
    const current = stack.pop();
    for (const entry of fs.readdirSync(current, { withFileTypes: true })) {
      const fullPath = path.join(current, entry.name);
      if (entry.isDirectory()) {
        stack.push(fullPath);
      } else if (/IntegrationTest\.java$/.test(entry.name)) {
        files.push(fullPath);
      }
    }
  }
  return files;
}

function applicationNameValues(file) {
  return fs.readFileSync(file, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => line.match(/^spring\.application\.name\s*=\s*(.+)$/))
    .filter(Boolean)
    .map((match) => match[1].trim());
}

function propertyValues(file, propertyName) {
  const escapedName = propertyName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`^${escapedName}\\s*=\\s*(.+)$`);
  return fs.readFileSync(file, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => line.match(pattern))
    .filter(Boolean)
    .map((match) => match[1].trim());
}

function durationMillis(value) {
  const match = String(value).trim().match(/^(\d+)(ms|s)$/);
  if (!match) {
    return NaN;
  }
  const amount = Number(match[1]);
  return match[2] === "s" ? amount * 1000 : amount;
}

for (const [service, expectedName] of Object.entries(expectedApplicationNames)) {
  const files = propertiesFiles(service);
  if (files.length === 0) {
    fail(`${service}: no application*.properties files found`);
    continue;
  }

  const names = files.flatMap(applicationNameValues);
  if (!names.includes(expectedName)) {
    const found = names.length ? names.join(", ") : "<none>";
    fail(`${service}: expected spring.application.name=${expectedName}, found ${found}`);
  }
}

const gatewayFetchIntervals = propertiesFiles("api-gateway")
  .flatMap((file) => propertyValues(file, "eureka.client.registryFetchIntervalSeconds"))
  .map((value) => Number(value))
  .filter((value) => Number.isFinite(value));

if (!gatewayFetchIntervals.some((value) => value > 0 && value <= 10)) {
  const found = gatewayFetchIntervals.length ? gatewayFetchIntervals.join(", ") : "<none>";
  fail(`api-gateway: expected eureka.client.registryFetchIntervalSeconds <= 10 for Docker cold-start runner stability, found ${found}`);
}

for (const service of ["shopping-cart-service", "order-service"]) {
  const files = propertiesFiles(service);
  const feignReadTimeouts = files
    .flatMap((file) => propertyValues(file, "feign.client.config.default.readTimeout"))
    .map((value) => Number(value))
    .filter((value) => Number.isFinite(value));
  const timeLimiterTimeouts = files
    .flatMap((file) => propertyValues(file, "resilience4j.timelimiter.configs.default.timeoutDuration"));

  if (!feignReadTimeouts.some((value) => value >= 15000)) {
    const found = feignReadTimeouts.length ? feignReadTimeouts.join(", ") : "<none>";
    fail(`${service}: expected Feign readTimeout >= 15000ms for Docker cold-start downstream lookups, found ${found}`);
  }

  if (!timeLimiterTimeouts.some((value) => durationMillis(value) >= 15000)) {
    const found = timeLimiterTimeouts.length ? timeLimiterTimeouts.join(", ") : "<none>";
    fail(`${service}: expected Resilience4j default time limiter >= 15s for Docker cold-start downstream lookups, found ${found}`);
  }
}

for (const service of Object.keys(expectedApplicationNames)) {
  const file = dockerfile(service);
  if (!file) {
    fail(`${service}: Dockerfile is missing`);
    continue;
  }
  const text = fs.readFileSync(file, "utf8");
  if (!text.includes("-Xmx96m") || !text.includes("-XX:MaxMetaspaceSize=128m") || !text.includes("-XX:MaxDirectMemorySize=32m")) {
    fail(`${service}: Dockerfile must use the low-memory JVM profile for full-stack local verification`);
  }
}

const composeText = fs.readFileSync(path.join(root, "docker-compose.yml"), "utf8");
if (/SPRING_ZIPKIN_ENABLED=true/.test(composeText)) {
  fail("docker-compose.yml: tracing must default to opt-in with SPRING_ZIPKIN_ENABLED=${SPRING_ZIPKIN_ENABLED:-false}");
}

function requireMavenTestFlags(relativePath, text) {
  for (const requiredFlag of mavenTestConfigFlags) {
    if (!text.includes(requiredFlag)) {
      fail(`${relativePath}: Maven tests must pass ${requiredFlag} to avoid Config Server lookups`);
    }
  }
}

const mavenTestConfigFlags = [
  "-Dspring.config.name=application-test",
  "-Dspring.cloud.config.enabled=false",
  "-Dspring.cloud.config.import-check.enabled=false",
  "-Dspring.config.import=",
];

const verifyLocalText = fs.readFileSync(path.join(root, "scripts", "verify-local.sh"), "utf8");
requireMavenTestFlags("scripts/verify-local.sh", verifyLocalText);
requireMavenTestFlags(
  ".github/workflows/ci.yml",
  fs.readFileSync(path.join(root, ".github", "workflows", "ci.yml"), "utf8")
);

if (!verifyLocalText.includes("RUN_DIRECT_HEALTH_CHECKS")
    || !verifyLocalText.includes("SERVICE_HEALTH_CHECKS")
    || !verifyLocalText.includes("INVENTORY_SERVICE_PORT")) {
  fail("scripts/verify-local.sh: local smoke verifier must wait for direct service actuator health before Postman runs");
}

const smokeScriptText = fs.readFileSync(path.join(root, "e2e-smoke-test.sh"), "utf8");
if (!smokeScriptText.includes("exec scripts/verify-local.sh") || !smokeScriptText.includes("RUN_MAVEN_TESTS")) {
  fail("e2e-smoke-test.sh: legacy smoke entrypoint must delegate to scripts/verify-local.sh");
}

for (const service of Object.keys(expectedApplicationNames)) {
  for (const file of integrationTestFiles(service)) {
    const text = fs.readFileSync(file, "utf8");
    const relativeFile = path.relative(root, file);
    if (!text.includes('"spring.cloud.config.enabled=false"')) {
      fail(`${relativeFile}: integration tests must disable Spring Cloud Config`);
    }
    if (!text.includes('"spring.cloud.config.import-check.enabled=false"')) {
      fail(`${relativeFile}: integration tests must disable Spring Cloud Config import checks`);
    }
    if (service === "auth-server" && !text.includes('"jwt.secret=')) {
      fail(`${relativeFile}: auth integration tests must define jwt.secret when production config is not loaded`);
    }
    if (["inventory-service", "notification-service", "review-service"].includes(service)
        && !text.includes('"spring.kafka.bootstrap-servers=')) {
      fail(`${relativeFile}: Kafka-backed integration tests must define spring.kafka.bootstrap-servers when production config is not loaded`);
    }
    if (/DB_CLOSE_DELAY=-1;MODE=MySQL/.test(text)) {
      fail(`${relativeFile}: H2 integration database URL must include DB_CLOSE_ON_EXIT=FALSE`);
    }
  }
}

if (failed) {
  process.exit(1);
}

console.log("Service config validation passed");
