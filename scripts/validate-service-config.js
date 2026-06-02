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

  return javaTestFiles(service)
    .filter((file) => /IntegrationTest\.java$/.test(path.basename(file)));
}

function javaTestFiles(service) {
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
      } else if (entry.name.endsWith(".java")) {
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

function hasProperty(file, propertyName) {
  const escapedName = propertyName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const pattern = new RegExp(`^${escapedName}\\s*=`);
  return fs.readFileSync(file, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .some((line) => pattern.test(line));
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

  const showSqlValues = files.flatMap((file) => propertyValues(file, "spring.jpa.show-sql"));
  if (showSqlValues.some((value) => value.toLowerCase() === "true")) {
    fail(`${service}: spring.jpa.show-sql must stay disabled for full-stack Docker smoke stability`);
  }
}

const readmeCoverageServices = [
  "auth-server",
  "catalog-service",
  "customer-service",
  "order-service",
  "payment-service",
  "shopping-cart-service",
  "coupon-service",
  "review-service",
  "wishlist-service",
  "inventory-service",
  "notification-service",
];
const readmeText = fs.readFileSync(path.join(root, "README.md"), "utf8");
const readmeCoverageStart = readmeText.indexOf("## Test Coverage");
const readmeCoverageText = readmeCoverageStart === -1 ? "" : readmeText.slice(readmeCoverageStart);
if (!readmeCoverageText) {
  fail("README.md: missing Test Coverage section");
}

function junitTestCount(file) {
  return (fs.readFileSync(file, "utf8").match(/@Test\b/g) || []).length;
}

function coverageCounts(service) {
  let unit = 0;
  let integration = 0;
  for (const file of javaTestFiles(service)) {
    const count = junitTestCount(file);
    if (/IntegrationTest\.java$/.test(path.basename(file))) {
      integration += count;
    } else if (!/ApplicationTests\.java$/.test(path.basename(file))) {
      unit += count;
    }
  }
  return { unit, integration };
}

function readmeCoverageRow(service) {
  const escapedService = service.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = readmeCoverageText.match(new RegExp(`\\| ${escapedService} \\| ([^|]+) \\| ([^|]+) \\|`));
  if (!match) {
    fail(`README.md: missing Test Coverage row for ${service}`);
    return null;
  }
  return {
    unit: Number(match[1].trim()),
    integration: match[2].trim() === "—" ? 0 : Number(match[2].trim()),
  };
}

let totalReadmeUnitTests = 0;
let totalReadmeIntegrationTests = 0;
for (const service of readmeCoverageServices) {
  const actual = coverageCounts(service);
  const documented = readmeCoverageRow(service);
  totalReadmeUnitTests += actual.unit;
  totalReadmeIntegrationTests += actual.integration;
  if (documented && (documented.unit !== actual.unit || documented.integration !== actual.integration)) {
    fail(`README.md: ${service} Test Coverage row must be ${actual.unit} unit and ${actual.integration || "—"} integration tests`);
  }
}

const totalCoverageMatch = readmeCoverageText.match(/\| \*\*Total\*\* \| \*\*(\d+)\*\* \| \*\*(\d+)\*\* \|/);
if (!totalCoverageMatch) {
  fail("README.md: missing Test Coverage total row");
} else if (Number(totalCoverageMatch[1]) !== totalReadmeUnitTests
    || Number(totalCoverageMatch[2]) !== totalReadmeIntegrationTests) {
  fail(`README.md: Test Coverage total must be ${totalReadmeUnitTests} unit and ${totalReadmeIntegrationTests} integration tests`);
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

const gatewayYamlText = fs.readFileSync(path.join(root, "api-gateway", "src", "main", "resources", "application.yml"), "utf8");
if (!/discovery:\s*\n\s+locator:\s*\n\s+enabled:\s*false\b/.test(gatewayYamlText)) {
  fail("api-gateway application.yml: discovery locator must stay disabled so raw service-id routes are not exposed");
}

const gatewayConfigText = fs.readFileSync(
  path.join(root, "api-gateway", "src", "main", "java", "com", "sanjeevsky", "apigateway", "config", "GatewayConfig.java"),
  "utf8"
);
if (!gatewayConfigText.includes('.path("/cart-service/**")')
    || !gatewayConfigText.includes('.uri("lb://shopping-cart-service")')) {
  fail("api-gateway GatewayConfig: cart must use the standard /cart-service/** route to shopping-cart-service");
}
if (gatewayConfigText.includes('.path("/shopping-cart-service/**")')) {
  fail("api-gateway GatewayConfig: raw /shopping-cart-service/** route must not be exposed");
}

const routerValidatorText = fs.readFileSync(
  path.join(root, "api-gateway", "src", "main", "java", "com", "sanjeevsky", "apigateway", "filter", "RouterValidator.java"),
  "utf8"
);
if (routerValidatorText.includes(".contains(")
    || routerValidatorText.includes("/coupon-service/active")
    || routerValidatorText.includes("/review-service/product/")) {
  fail("api-gateway RouterValidator: open route allowlist must use exact standard paths without stale raw entries");
}

function composeServiceBlock(service) {
  const pattern = new RegExp(`^  ${service}:\\n([\\s\\S]*?)(?=^  [a-zA-Z0-9_-]+:|^volumes:|^networks:|(?![\\s\\S]))`, "m");
  const match = composeText.match(pattern);
  return match ? match[0] : "";
}

for (const service of Object.keys(expectedApplicationNames)) {
  const block = composeServiceBlock(service);
  if (!block) {
    fail(`docker-compose.yml: missing service block for ${service}`);
  } else if (!block.includes("restart: unless-stopped")) {
    fail(`docker-compose.yml: ${service} must use restart: unless-stopped for local smoke stability`);
  }
}

const shoppingCartProperties = propertiesFiles("shopping-cart-service");
if (!shoppingCartProperties.some((file) => hasProperty(file, "clients.catalog.url"))) {
  fail("shopping-cart-service: expected clients.catalog.url property for Docker catalog dependency override");
}

const shoppingCartClientText = fs.readFileSync(
  path.join(root, "shopping-cart-service", "src", "main", "java", "com", "sanjeevsky", "shoppingcartservice", "clients", "CatalogFeignClient.java"),
  "utf8"
);
if (!shoppingCartClientText.includes('url = "${clients.catalog.url:}"')) {
  fail("shopping-cart-service: CatalogFeignClient must use clients.catalog.url for Docker catalog dependency override");
}

const shoppingCartComposeBlock = composeServiceBlock("shopping-cart-service");
if (!shoppingCartComposeBlock.includes("CLIENTS_CATALOG_URL=http://catalog-service:8084")) {
  fail("docker-compose.yml: shopping-cart-service must define CLIENTS_CATALOG_URL=http://catalog-service:8084");
}

const wishlistProperties = propertiesFiles("wishlist-service");
if (!wishlistProperties.some((file) => hasProperty(file, "clients.cart.url"))) {
  fail("wishlist-service: expected clients.cart.url property for Docker cart dependency override");
}

const wishlistCartClientText = fs.readFileSync(
  path.join(root, "wishlist-service", "src", "main", "java", "com", "sanjeevsky", "wishlistservice", "clients", "CartFeignClient.java"),
  "utf8"
);
if (!wishlistCartClientText.includes('url = "${clients.cart.url:}"')) {
  fail("wishlist-service: CartFeignClient must use clients.cart.url for Docker cart dependency override");
}

const wishlistComposeBlock = composeServiceBlock("wishlist-service");
if (!wishlistComposeBlock.includes("CLIENTS_CART_URL=http://shopping-cart-service:8086")) {
  fail("docker-compose.yml: wishlist-service must define CLIENTS_CART_URL=http://shopping-cart-service:8086");
}

const orderProperties = propertiesFiles("order-service");
const orderComposeBlock = composeServiceBlock("order-service");
const orderClientOverrides = [
  ["cart", "CartFeignClient.java", "CLIENTS_CART_URL=http://shopping-cart-service:8086"],
  ["customer", "CustomerFeignClient.java", "CLIENTS_CUSTOMER_URL=http://customer-service:8082"],
  ["payment", "PaymentFeignClient.java", "CLIENTS_PAYMENT_URL=http://payment-service:8085"],
  ["coupon", "CouponFeignClient.java", "CLIENTS_COUPON_URL=http://coupon-service:8089"],
  ["inventory", "InventoryFeignClient.java", "CLIENTS_INVENTORY_URL=http://inventory-service:8088"],
];
for (const [client, feignClientFile, composeEnv] of orderClientOverrides) {
  const propertyName = `clients.${client}.url`;
  if (!orderProperties.some((file) => hasProperty(file, propertyName))) {
    fail(`order-service: expected ${propertyName} property for Docker dependency override`);
  }

  const feignClientText = fs.readFileSync(
    path.join(root, "order-service", "src", "main", "java", "com", "sanjeevsky", "orderservice", "clients", feignClientFile),
    "utf8"
  );
  if (!feignClientText.includes(`url = "\${${propertyName}:}"`)) {
    fail(`order-service: ${feignClientFile} must use ${propertyName} for Docker dependency override`);
  }

  if (!orderComposeBlock.includes(composeEnv)) {
    fail(`docker-compose.yml: order-service must define ${composeEnv}`);
  }
}

const kafkaRetryConfigs = [
  {
    service: "inventory-service",
    configPath: ["src", "main", "java", "com", "sanjeevsky", "inventoryservice", "config", "KafkaRetryConfig.java"],
    consumerPaths: [
      ["src", "main", "java", "com", "sanjeevsky", "inventoryservice", "events", "OrderEventConsumer.java"],
    ],
    dltTopics: ["order-events-dlt"],
  },
  {
    service: "notification-service",
    configPath: ["src", "main", "java", "com", "sanjeevsky", "notificationservice", "config", "KafkaRetryConfig.java"],
    consumerPaths: [
      ["src", "main", "java", "com", "sanjeevsky", "notificationservice", "consumer", "OrderEventConsumer.java"],
      ["src", "main", "java", "com", "sanjeevsky", "notificationservice", "consumer", "PaymentEventConsumer.java"],
    ],
    dltTopics: ["order-events-dlt", "payment-events-dlt"],
  },
  {
    service: "review-service",
    configPath: ["src", "main", "java", "com", "sanjeevsky", "reviewservice", "config", "KafkaRetryConfig.java"],
    consumerPaths: [
      ["src", "main", "java", "com", "sanjeevsky", "reviewservice", "kafka", "OrderEventConsumer.java"],
    ],
    dltTopics: ["order-events-dlt"],
  },
  {
    service: "order-service",
    configPath: ["src", "main", "java", "com", "sanjeevsky", "orderservice", "config", "KafkaRetryConfig.java"],
    consumerPaths: [
      ["src", "main", "java", "com", "sanjeevsky", "orderservice", "events", "InventoryEventConsumer.java"],
    ],
    dltTopics: ["inventory-events-dlt"],
  },
];

for (const kafkaRetryConfig of kafkaRetryConfigs) {
  const relativeConfigPath = path.join(kafkaRetryConfig.service, ...kafkaRetryConfig.configPath);
  const absoluteConfigPath = path.join(root, relativeConfigPath);
  if (!fs.existsSync(absoluteConfigPath)) {
    fail(`${relativeConfigPath}: Kafka retry/DLT config is missing`);
    continue;
  }

  const configText = fs.readFileSync(absoluteConfigPath, "utf8");
  const requiredConfigSnippets = [
    "DeadLetterPublishingRecoverer",
    "SeekToCurrentErrorHandler",
    "FixedBackOff",
    'record.topic() + "-dlt"',
    "MAX_RETRY_ATTEMPTS = 2L",
  ];
  for (const snippet of requiredConfigSnippets) {
    if (!configText.includes(snippet)) {
      fail(`${relativeConfigPath}: Kafka retry/DLT config must include ${snippet}`);
    }
  }

  for (const dltTopic of kafkaRetryConfig.dltTopics) {
    if (!configText.includes(`TopicBuilder.name("${dltTopic}")`)) {
      fail(`${relativeConfigPath}: expected DLT topic ${dltTopic}`);
    }
  }

  for (const consumerPathParts of kafkaRetryConfig.consumerPaths) {
    const relativeConsumerPath = path.join(kafkaRetryConfig.service, ...consumerPathParts);
    const absoluteConsumerPath = path.join(root, relativeConsumerPath);
    if (!fs.existsSync(absoluteConsumerPath)) {
      fail(`${relativeConsumerPath}: Kafka consumer is missing`);
      continue;
    }

    const consumerText = fs.readFileSync(absoluteConsumerPath, "utf8");
    if (!consumerText.includes("@KafkaListener")) {
      fail(`${relativeConsumerPath}: expected Kafka listener annotation`);
    }
    if (!consumerText.includes("throw new IllegalStateException")) {
      fail(`${relativeConsumerPath}: Kafka consumer must rethrow processing failures so retry/DLT handling can run`);
    }
  }
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

if (!verifyLocalText.includes("RUN_API_COLLECTION")
    || !verifyLocalText.includes("Ecommerce-API.postman_collection.json")) {
  fail("scripts/verify-local.sh: local smoke verifier must include the API Postman collection");
}

if (!verifyLocalText.includes("verify_gateway_standard_routes")
    || !verifyLocalText.includes("/cart-service/cart")
    || !verifyLocalText.includes("/shopping-cart-service/cart")) {
  fail("scripts/verify-local.sh: local smoke verifier must assert standard gateway routes and reject raw service-id routes");
}

if (!verifyLocalText.includes("print_url_diagnostics")
    || !verifyLocalText.includes("print_eureka_registry_snapshot")
    || !verifyLocalText.includes("Eureka registry snapshot")) {
  fail("scripts/verify-local.sh: local smoke verifier must print HTTP and Eureka diagnostics when readiness checks fail");
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
