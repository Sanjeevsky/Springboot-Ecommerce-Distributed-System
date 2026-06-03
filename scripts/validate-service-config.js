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

function javaMainFiles(service) {
  const mainRoot = path.join(root, service, "src", "main", "java");
  if (!fs.existsSync(mainRoot)) {
    return [];
  }

  const files = [];
  const stack = [mainRoot];
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

function requestMappingVisibilityIssues(text) {
  const lines = text.split(/\r?\n/);
  const issues = [];
  for (let i = 0; i < lines.length; i += 1) {
    if (!/@(Get|Post|Put|Delete|Patch)Mapping\b/.test(lines[i])) {
      continue;
    }
    let signature = "";
    let signatureLine = 0;
    let parenDepth = 0;
    for (let j = i + 1; j < lines.length; j += 1) {
      const line = lines[j].trim();
      if (!line || line.startsWith("@")) {
        continue;
      }
      if (!signatureLine) {
        signatureLine = j + 1;
      }
      signature += `${signature ? " " : ""}${line}`;
      for (const char of line) {
        if (char === "(") {
          parenDepth += 1;
        } else if (char === ")") {
          parenDepth -= 1;
        }
      }
      if (line.includes("{") && parenDepth <= 0) {
        if (!/^public\b/.test(signature.trim())) {
          issues.push(signatureLine);
        }
        break;
      }
    }
  }
  return issues;
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

  for (const file of javaMainFiles(service)) {
    const text = fs.readFileSync(file, "utf8");
    const relativeFile = path.relative(root, file);
    if (text.includes("@Autowired")) {
      fail(`${relativeFile}: production dependencies must use constructor injection without @Autowired`);
    }
    if (text.includes("@RestController")) {
      for (const line of requestMappingVisibilityIssues(text)) {
        fail(`${relativeFile}:${line}: request mapping methods must be public`);
      }
    }
    if (path.basename(file) === "OpenApiConfig.java") {
      if (text.includes('description = "Direct"')
          || text.includes('description = "Via API Gateway"')
          || /@Server\(url = "http:\/\/localhost:(?!8081\b)\d+"/.test(text)) {
        fail(`${relativeFile}: OpenAPI servers must advertise only the API Gateway`);
      }
      if (!text.includes('@Server(url = "http://localhost:8081", description = "API Gateway")')) {
        fail(`${relativeFile}: OpenAPI config must include the API Gateway server`);
      }
    }
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

  const sleuthSamplerPercentageValues = files.flatMap((file) => propertyValues(file, "spring.sleuth.sampler.percentage"));
  if (sleuthSamplerPercentageValues.length) {
    fail(`${service}: use spring.sleuth.sampler.probability=1.0 instead of deprecated spring.sleuth.sampler.percentage`);
  }

  const zipkinEnabledValues = files.flatMap((file) => propertyValues(file, "spring.zipkin.enabled"));
  const sleuthSamplerProbabilityValues = files.flatMap((file) => propertyValues(file, "spring.sleuth.sampler.probability"));
  if (zipkinEnabledValues.length && !sleuthSamplerProbabilityValues.includes("1.0")) {
    fail(`${service}: services with Zipkin config must set spring.sleuth.sampler.probability=1.0`);
  }

  for (const file of files) {
    const text = fs.readFileSync(file, "utf8");
    const relativeFile = path.relative(root, file);
    for (const configImport of propertyValues(file, "spring.config.import")
      .filter((value) => value.includes("configserver:"))) {
      if (!configImport.endsWith("/")) {
        fail(`${relativeFile}: configserver spring.config.import URLs must end with / for Spring Config Data resolution`);
      }
    }
    if (/^spring\.datasource\.password\s*=\s*123456\s*$/m.test(text)) {
      fail(`${relativeFile}: datasource password must read from MYSQL_PASSWORD with a local fallback`);
    }
    if (/^spring\.boot\.admin\.client\.password\s*=\s*client\s*$/m.test(text)) {
      fail(`${relativeFile}: Spring Boot Admin client password must read from SPRING_BOOT_ADMIN_CLIENT_PASSWORD`);
    }
  }

  const datasourcePasswordValues = files.flatMap((file) => propertyValues(file, "spring.datasource.password"));
  if (datasourcePasswordValues.length
      && !datasourcePasswordValues.every((value) => value.startsWith("${MYSQL_PASSWORD:"))) {
    fail(`${service}: spring.datasource.password must use \${MYSQL_PASSWORD:...} in application properties`);
  }

  const adminClientPasswordValues = files.flatMap((file) => propertyValues(file, "spring.boot.admin.client.password"));
  if (adminClientPasswordValues.length
      && !adminClientPasswordValues.every((value) => value.startsWith("${SPRING_BOOT_ADMIN_CLIENT_PASSWORD:"))) {
    fail(`${service}: spring.boot.admin.client.password must use \${SPRING_BOOT_ADMIN_CLIENT_PASSWORD:...}`);
  }
}

const springServerSecurityPasswords = propertiesFiles("spring-server")
  .flatMap((file) => propertyValues(file, "spring.security.user.password"));
if (!springServerSecurityPasswords.length
    || !springServerSecurityPasswords.every((value) => value.startsWith("${SPRING_SECURITY_USER_PASSWORD:"))) {
  fail("spring-server: spring.security.user.password must use ${SPRING_SECURITY_USER_PASSWORD:...}");
}

const readmeCoverageServices = [
  "api-gateway",
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
const implementationText = fs.readFileSync(path.join(root, "implementation.md"), "utf8");
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

for (const service of Object.keys(expectedApplicationNames)) {
  const applicationTests = javaTestFiles(service)
    .filter((file) => /ApplicationTests\.java$/.test(path.basename(file)));
  for (const file of applicationTests) {
    const text = fs.readFileSync(file, "utf8");
    const isolatedContextTest = text.includes("spring.cloud.config.enabled=false")
      && text.includes("spring.config.import=")
      && text.includes("eureka.client.enabled=false");
    if (text.includes("@SpringBootTest")
        && !text.includes('@Disabled("Requires running infrastructure")')
        && !isolatedContextTest) {
      fail(`${path.relative(root, file)}: infrastructure-dependent ApplicationTests must be disabled or explicitly isolated`);
    }
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
if (composeText.includes("MYSQL_ROOT_PASSWORD: 123456")
    || composeText.includes("SPRING_DATASOURCE_PASSWORD=123456")) {
  fail("docker-compose.yml: MySQL passwords must use overridable local defaults");
}
if (!composeText.includes("MYSQL_ROOT_PASSWORD: ${MYSQL_ROOT_PASSWORD:-123456}")
    || !composeText.includes("-p$${MYSQL_ROOT_PASSWORD}")) {
  fail("docker-compose.yml: mysql service and healthcheck must share MYSQL_ROOT_PASSWORD");
}
if ((composeText.match(/SPRING_DATASOURCE_PASSWORD=\$\{MYSQL_ROOT_PASSWORD:-123456\}/g) || []).length !== 11) {
  fail("docker-compose.yml: database-backed services must use SPRING_DATASOURCE_PASSWORD=${MYSQL_ROOT_PASSWORD:-123456}");
}

const gatewayYamlText = fs.readFileSync(path.join(root, "api-gateway", "src", "main", "resources", "application.yml"), "utf8");
if (!/discovery:\s*\n\s+locator:\s*\n\s+enabled:\s*false\b/.test(gatewayYamlText)) {
  fail("api-gateway application.yml: discovery locator must stay disabled so raw service-id routes are not exposed");
}

const gatewayConfigText = fs.readFileSync(
  path.join(root, "api-gateway", "src", "main", "java", "com", "sanjeevsky", "apigateway", "config", "GatewayConfig.java"),
  "utf8"
);
if (gatewayConfigText.includes("@Autowired")) {
  fail("api-gateway GatewayConfig: route filter dependency must use constructor injection");
}
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
    || routerValidatorText.includes('path.startsWith("/auth-service/")')
    || routerValidatorText.includes("/coupon-service/active")
    || routerValidatorText.includes("/review-service/product/")) {
  fail("api-gateway RouterValidator: open route allowlist must use exact standard paths without stale raw entries");
}
if (!routerValidatorText.includes('path.equals("/auth-service/signup")')
    || !routerValidatorText.includes('path.equals("/auth-service/login")')) {
  fail("api-gateway RouterValidator: only signup and login auth routes may be public");
}

const gatewayJwtUtilText = fs.readFileSync(
  path.join(root, "api-gateway", "src", "main", "java", "com", "sanjeevsky", "apigateway", "filter", "JwtUtil.java"),
  "utf8"
);
if (gatewayJwtUtilText.includes("printStackTrace")) {
  fail("api-gateway JwtUtil: JWT validation failures must use structured logging, not printStackTrace");
}

const gatewayConstantsPath = path.join(
  root,
  "api-gateway",
  "src",
  "main",
  "java",
  "com",
  "sanjeevsky",
  "apigateway",
  "utils",
  "Constants.java"
);
if (fs.existsSync(gatewayConstantsPath)
    && fs.readFileSync(gatewayConstantsPath, "utf8").includes('SECRET = "secret"')) {
  fail("api-gateway Constants: JWT secrets must come from configuration, not hard-coded constants");
}

const gatewayAuthFilterText = fs.readFileSync(
  path.join(root, "api-gateway", "src", "main", "java", "com", "sanjeevsky", "apigateway", "filter", "AuthenticationFilter.java"),
  "utf8"
);
if (gatewayAuthFilterText.includes("jwtUtil.isInvalid(")
    || !gatewayAuthFilterText.includes('getFirst("Authorization")')) {
  fail("api-gateway AuthenticationFilter: JWT validation must parse once and read Authorization safely");
}

for (const service of ["api-gateway", "auth-server"]) {
  const jwtProperties = propertiesFiles(service);
  if (!jwtProperties.some((file) => propertyValues(file, "jwt.secret")
    .some((value) => value.startsWith("${JWT_SECRET:")))) {
    fail(`${service}: jwt.secret must read from JWT_SECRET with a local fallback`);
  }
  for (const file of jwtProperties) {
    if (/^jwt\.secret\s*=\s*secret\s*$/m.test(fs.readFileSync(file, "utf8"))) {
      fail(`${service}: jwt.secret must not be hard-coded to secret`);
    }
  }
}

if (composeText.includes("JWT_SECRET=secret")) {
  fail("docker-compose.yml: JWT_SECRET must use an overridable local default, not a hard-coded secret");
}
if ((composeText.match(/JWT_SECRET=\$\{JWT_SECRET:-local-dev-secret\}/g) || []).length !== 2) {
  fail("docker-compose.yml: auth-server and api-gateway must share JWT_SECRET=${JWT_SECRET:-local-dev-secret}");
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
  } else {
    const importsConfigServer = propertiesFiles(service)
      .some((file) => propertyValues(file, "spring.config.import")
        .some((value) => value.includes("configserver:")));
    if (importsConfigServer
        && (!block.includes("SPRING_CLOUD_CONFIG_ENABLED=false")
          || !block.includes("SPRING_CLOUD_CONFIG_IMPORT_CHECK_ENABLED=false"))) {
      fail(`docker-compose.yml: ${service} must disable Spring Cloud Config client for Docker startup stability`);
    }
  }
}

const customerServicePomText = fs.readFileSync(path.join(root, "customer-service", "pom.xml"), "utf8");
const customerKafkaConfigPath = path.join(
  root,
  "customer-service",
  "src",
  "main",
  "java",
  "com",
  "sanjeevsky",
  "customerservice",
  "config",
  "KafkaConfig.java"
);
const customerComposeBlock = composeServiceBlock("customer-service");
if (customerServicePomText.includes("<artifactId>spring-kafka</artifactId>")
    || fs.existsSync(customerKafkaConfigPath)
    || propertiesFiles("customer-service").some((file) => fs.readFileSync(file, "utf8").includes("spring.kafka."))
    || customerComposeBlock.includes("SPRING_KAFKA_BOOTSTRAP_SERVERS")
    || /kafka:\s*\n\s+condition:\s+service_healthy/.test(customerComposeBlock)) {
  fail("customer-service: Kafka wiring must stay removed because the service has no Kafka publisher or listener");
}

if (readmeText.includes("customer-service ──► order-events")
    || !readmeText.includes("order-service    ──► order-events")
    || !readmeText.includes("inventory-service ─► inventory-events ─► order-service")) {
  fail("README.md: Kafka event flow must document order-service and inventory-service as event publishers");
}
if (!implementationText.includes("| order-service | 8092 | ✅ | ✅ | ✅ | pub/cons | feign | ✅ |")
    || !implementationText.includes("| inventory-service | 8088 | ✅ | ✅ | ✅ | pub/cons | — | ✅ |")) {
  fail("implementation.md: service matrix must document order-service and inventory-service as Kafka pub/cons services");
}

const orderServiceImplText = fs.readFileSync(
  path.join(root, "order-service", "src", "main", "java", "com", "sanjeevsky", "orderservice", "service", "impl", "OrderServiceImpl.java"),
  "utf8"
);
const orderServiceTestText = fs.readFileSync(
  path.join(root, "order-service", "src", "test", "java", "com", "sanjeevsky", "orderservice", "service", "OrderServiceImplTest.java"),
  "utf8"
);
if (!orderServiceImplText.includes("validateIdempotentReplay")
    || !orderServiceImplText.includes("getOriginalAddressId")
    || !orderServiceImplText.includes("getCouponCode")) {
  fail("order-service: idempotent order replays must reject conflicting address/coupon payloads");
}
if (!orderServiceTestText.includes("createOrder_withSameIdempotencyKeyDifferentAddress_throwsInvalidRequestException")
    || !orderServiceTestText.includes("createOrder_withSameIdempotencyKeyDifferentCoupon_throwsInvalidRequestException")) {
  fail("order-service: unit tests must cover conflicting idempotency-key order creation");
}
const orderControllerText = fs.readFileSync(
  path.join(root, "order-service", "src", "main", "java", "com", "sanjeevsky", "orderservice", "controller", "OrderController.java"),
  "utf8"
);
const orderControllerTestText = fs.readFileSync(
  path.join(root, "order-service", "src", "test", "java", "com", "sanjeevsky", "orderservice", "controller", "OrderControllerTest.java"),
  "utf8"
);
if (!orderServiceImplText.includes("validateCreateOrderRequest(addressId)")
    || !orderServiceImplText.includes("Order addressId is required")
    || !orderControllerText.includes("@RequestBody @Valid CreateOrderRequest request")
    || !orderControllerText.includes("@NotNull(message = \"addressId is required\")")) {
  fail("order-service: order creation must validate required addressId at controller and service boundaries");
}
if (!orderServiceTestText.includes("createOrder_missingAddressId_throwsInvalidRequestException")
    || !orderControllerTestText.includes("createOrder_missingAddressId_returns400")) {
  fail("order-service: tests must cover missing addressId validation for order creation");
}

const couponControllerText = fs.readFileSync(
  path.join(root, "coupon-service", "src", "main", "java", "com", "sanjeevsky", "couponservice", "controller", "CouponController.java"),
  "utf8"
);
const couponServiceImplText = fs.readFileSync(
  path.join(root, "coupon-service", "src", "main", "java", "com", "sanjeevsky", "couponservice", "service", "impl", "CouponServiceImpl.java"),
  "utf8"
);
const couponServiceTestText = fs.readFileSync(
  path.join(root, "coupon-service", "src", "test", "java", "com", "sanjeevsky", "couponservice", "service", "CouponServiceImplTest.java"),
  "utf8"
);
const couponIntegrationTestText = fs.readFileSync(
  path.join(root, "coupon-service", "src", "test", "java", "com", "sanjeevsky", "couponservice", "CouponIntegrationTest.java"),
  "utf8"
);
if (!couponControllerText.includes("@RequestBody @Valid Coupon coupon")
    || !couponServiceImplText.includes("validateCouponForCreate(coupon)")
    || !couponServiceImplText.includes("Percentage coupon value must not exceed 100")
    || !couponServiceImplText.includes("Order amount must not be negative")) {
  fail("coupon-service: coupon creation and validation must reject invalid coupon economics");
}
for (const testName of [
  "createCoupon_percentageAbove100_throwsInvalidCouponException",
  "createCoupon_negativeValue_throwsInvalidCouponException",
  "validateCoupon_negativeOrderAmount_throwsInvalidCouponException",
  "applyCoupon_blankCode_throwsInvalidCouponException",
]) {
  if (!couponServiceTestText.includes(testName)) {
    fail(`coupon-service: missing service test ${testName}`);
  }
}
for (const testName of [
  "createCoupon_percentageAbove100_returns400",
  "validateCoupon_negativeAmount_returns400",
]) {
  if (!couponIntegrationTestText.includes(testName)) {
    fail(`coupon-service: missing integration test ${testName}`);
  }
}

const variantControllerText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "controller", "VariantController.java"),
  "utf8"
);
const productDtoText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "model", "dto", "ProductDTO.java"),
  "utf8"
);
const variantDtoText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "model", "dto", "VariantDTO.java"),
  "utf8"
);
const catalogGlobalExceptionHandlerText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "exceptions", "GlobalExceptionHandler.java"),
  "utf8"
);
const variantServiceImplText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "service", "impl", "VariantServiceImpl.java"),
  "utf8"
);
const productServiceImplText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "service", "impl", "ProductServiceImpl.java"),
  "utf8"
);
const brandServiceImplText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "service", "impl", "BrandServiceImpl.java"),
  "utf8"
);
const categoryServiceImplText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "service", "impl", "CategoryServiceImpl.java"),
  "utf8"
);
const subCategoryServiceImplText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "service", "impl", "SubCategoryServiceImpl.java"),
  "utf8"
);
const variantServiceTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "service", "VariantServiceImplTest.java"),
  "utf8"
);
const productServiceTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "service", "ProductServiceImplTest.java"),
  "utf8"
);
const brandServiceTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "service", "BrandServiceImplTest.java"),
  "utf8"
);
const categoryServiceTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "service", "CategoryServiceImplTest.java"),
  "utf8"
);
const subCategoryServiceTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "service", "SubCategoryServiceImplTest.java"),
  "utf8"
);
const variantControllerTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "controller", "VariantControllerTest.java"),
  "utf8"
);
const productCatalogControllerTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "controller", "ProductCatalogControllerTest.java"),
  "utf8"
);
if (!productDtoText.includes("@PositiveOrZero(message = \"GST value must not be negative\")")
    || !productDtoText.includes("@PositiveOrZero(message = \"Discount must not be negative\")")
    || !productDtoText.includes("@Max(value = 1, message = \"Status must be 0 or 1\")")
    || !productServiceImplText.includes("validateProductRequest(product)")
    || !productServiceImplText.includes("Sale price cannot exceed MRP price")
    || !productServiceImplText.includes("Status must be 0 or 1")
    || !productServiceImplText.includes("validatePagination(page, size)")
    || !productServiceImplText.includes("normalizeProductSort(sort)")
    || !productServiceImplText.includes("MAX_PAGE_SIZE = 100")
    || !productServiceImplText.includes("Product sort must be one of")
    || !catalogGlobalExceptionHandlerText.includes("InvalidProductRequestException.class")) {
  fail("catalog-service: product creation and listing must validate product economics, active status, and pagination");
}
if (!brandServiceImplText.includes("normalizeName(name, \"Brand name is required\")")
    || !categoryServiceImplText.includes("normalizeName(categoryName, \"Category name is required\")")
    || !subCategoryServiceImplText.includes("normalizeName(subcategoryName, \"Subcategory name is required\")")
    || !subCategoryServiceImplText.includes("Category id is required")
    || !catalogGlobalExceptionHandlerText.includes("InvalidCatalogRequestException.class")) {
  fail("catalog-service: brand/category/subcategory names must be trimmed and reject blank values");
}
if (!variantControllerText.includes("@Valid @RequestBody VariantDTO variantDTO")
    || !variantDtoText.includes("@NotBlank(message = \"Primary condition name is required\")")
    || !variantDtoText.includes("@Positive(message = \"Sale price must be positive\")")
    || !variantServiceImplText.includes("validateVariantRequest(variant)")
    || !variantServiceImplText.includes("Sale price cannot exceed MRP price")
    || !catalogGlobalExceptionHandlerText.includes("InvalidVariantRequestException.class")) {
  fail("catalog-service: variant creation must validate required conditions and prices");
}
for (const testName of [
  "addProduct_blankName_throwsInvalidProductRequestException",
  "addProduct_salePriceAboveMrp_throwsInvalidProductRequestException",
  "addProduct_negativeDiscount_throwsInvalidProductRequestException",
  "addProduct_invalidStatus_throwsInvalidProductRequestException",
  "listProducts_blankSort_defaultsToName",
  "listProducts_negativePage_throwsInvalidProductRequestException",
  "listProducts_sizeAboveMaximum_throwsInvalidProductRequestException",
  "listProducts_invalidSort_throwsInvalidProductRequestException",
  "searchProducts_zeroSize_throwsInvalidProductRequestException",
]) {
  if (!productServiceTestText.includes(testName)) {
    fail(`catalog-service: missing service test ${testName}`);
  }
}
for (const testName of [
  "addProduct_negativeGstValue_returns400",
  "addProduct_serviceInvalidRequest_returns400",
]) {
  if (!productCatalogControllerTestText.includes(testName)) {
    fail(`catalog-service: missing controller test ${testName}`);
  }
}
for (const testName of [
  "addBrand_blankName_throwsInvalidCatalogRequestException",
  "addBrand_trimsNameBeforeLookupAndSave",
]) {
  if (!brandServiceTestText.includes(testName)) {
    fail(`catalog-service: missing brand service test ${testName}`);
  }
}
for (const testName of [
  "addCategory_blankName_throwsInvalidCatalogRequestException",
  "addCategory_trimsNameBeforeLookupAndSave",
]) {
  if (!categoryServiceTestText.includes(testName)) {
    fail(`catalog-service: missing category service test ${testName}`);
  }
}
for (const testName of [
  "getSubCategory_exists_returnsSubCategory",
  "getSubCategory_notFound_throwsSubCategoryListEmptyException",
  "getAllSubCategory_empty_throwsSubCategoryListEmptyException",
  "addSubCategory_categoryExists_trimsNameAndSaves",
  "addSubCategory_blankName_throwsInvalidCatalogRequestException",
  "addSubCategory_missingCategory_throwsCategoryNotExistsException",
]) {
  if (!subCategoryServiceTestText.includes(testName)) {
    fail(`catalog-service: missing subcategory service test ${testName}`);
  }
}
for (const testName of [
  "addVariant_blankPrimaryConditionName_throwsInvalidVariantRequestException",
  "addVariant_nonPositiveSalePrice_throwsInvalidVariantRequestException",
  "addVariant_salePriceAboveMrp_throwsInvalidVariantRequestException",
]) {
  if (!variantServiceTestText.includes(testName)) {
    fail(`catalog-service: missing service test ${testName}`);
  }
}
for (const testName of [
  "addVariant_missingPrimaryCondition_returns400",
  "addVariant_serviceInvalidRequest_returns400",
]) {
  if (!variantControllerTestText.includes(testName)) {
    fail(`catalog-service: missing controller test ${testName}`);
  }
}

const reviewControllerText = fs.readFileSync(
  path.join(root, "review-service", "src", "main", "java", "com", "sanjeevsky", "reviewservice", "controller", "ReviewController.java"),
  "utf8"
);
const reviewModelText = fs.readFileSync(
  path.join(root, "review-service", "src", "main", "java", "com", "sanjeevsky", "reviewservice", "model", "Review.java"),
  "utf8"
);
const reviewServiceImplText = fs.readFileSync(
  path.join(root, "review-service", "src", "main", "java", "com", "sanjeevsky", "reviewservice", "service", "impl", "ReviewServiceImpl.java"),
  "utf8"
);
const reviewServiceTestText = fs.readFileSync(
  path.join(root, "review-service", "src", "test", "java", "com", "sanjeevsky", "reviewservice", "service", "ReviewServiceImplTest.java"),
  "utf8"
);
const reviewIntegrationTestText = fs.readFileSync(
  path.join(root, "review-service", "src", "test", "java", "com", "sanjeevsky", "reviewservice", "ReviewIntegrationTest.java"),
  "utf8"
);
if (!reviewControllerText.includes("@RequestBody @Valid Review review")
    || !reviewModelText.includes("@NotNull(message = \"productId is required\")")
    || !reviewServiceImplText.includes("validateReviewRequest(review)")
    || !reviewServiceImplText.includes("normalizeModerationStatus(status)")
    || !reviewServiceImplText.includes("Review status must be APPROVED or REJECTED")) {
  fail("review-service: reviews must validate product/rating/title and moderation statuses");
}
for (const testName of [
  "addReview_missingProductId_throwsInvalidReviewRequestException",
  "addReview_invalidRating_throwsInvalidReviewRequestException",
  "addReview_blankTitle_throwsInvalidReviewRequestException",
  "moderateReview_invalidStatus_throwsInvalidReviewRequestException",
]) {
  if (!reviewServiceTestText.includes(testName)) {
    fail(`review-service: missing service test ${testName}`);
  }
}
for (const testName of [
  "addReview_invalidRating_returns400",
  "moderateReview_invalidStatus_returns400",
]) {
  if (!reviewIntegrationTestText.includes(testName)) {
    fail(`review-service: missing integration test ${testName}`);
  }
}

const wishlistDtoText = fs.readFileSync(
  path.join(root, "wishlist-service", "src", "main", "java", "com", "sanjeevsky", "wishlistservice", "dto", "AddToWishlistRequest.java"),
  "utf8"
);
const wishlistServiceImplText = fs.readFileSync(
  path.join(root, "wishlist-service", "src", "main", "java", "com", "sanjeevsky", "wishlistservice", "service", "impl", "WishlistServiceImpl.java"),
  "utf8"
);
const wishlistExceptionHandlerText = fs.readFileSync(
  path.join(root, "wishlist-service", "src", "main", "java", "com", "sanjeevsky", "wishlistservice", "exceptions", "GlobalExceptionHandler.java"),
  "utf8"
);
const wishlistServiceTestText = fs.readFileSync(
  path.join(root, "wishlist-service", "src", "test", "java", "com", "sanjeevsky", "wishlistservice", "service", "WishlistServiceImplTest.java"),
  "utf8"
);
const wishlistIntegrationTestText = fs.readFileSync(
  path.join(root, "wishlist-service", "src", "test", "java", "com", "sanjeevsky", "wishlistservice", "WishlistIntegrationTest.java"),
  "utf8"
);
if (!wishlistDtoText.includes("@NotBlank(message = \"productName is required\")")
    || !wishlistDtoText.includes("@Positive(message = \"salePrice must be greater than zero\")")
    || !wishlistServiceImplText.includes("validateAddToWishlistRequest(userId, request)")
    || !wishlistServiceImplText.includes("Wishlist userId is required")
    || !wishlistServiceImplText.includes("Wishlist salePrice must be greater than zero")
    || !wishlistExceptionHandlerText.includes("InvalidWishlistRequestException.class")) {
  fail("wishlist-service: wishlist requests must validate product name, price, productId, and userId");
}
for (const testName of [
  "addToWishlist_blankProductName_throwsInvalidWishlistRequestException",
  "addToWishlist_nonPositiveSalePrice_throwsInvalidWishlistRequestException",
  "addToWishlist_blankUserId_throwsInvalidWishlistRequestException",
  "removeFromWishlist_nullProductId_throwsInvalidWishlistRequestException",
]) {
  if (!wishlistServiceTestText.includes(testName)) {
    fail(`wishlist-service: missing service test ${testName}`);
  }
}
for (const testName of [
  "addToWishlist_blankProductName_returns400",
  "addToWishlist_nonPositiveSalePrice_returns400",
]) {
  if (!wishlistIntegrationTestText.includes(testName)) {
    fail(`wishlist-service: missing integration test ${testName}`);
  }
}

const paymentServiceImplText = fs.readFileSync(
  path.join(root, "payment-service", "src", "main", "java", "com", "sanjeevsky", "paymentservice", "service", "impl", "PaymentServiceImpl.java"),
  "utf8"
);
const paymentIntegrationTestText = fs.readFileSync(
  path.join(root, "payment-service", "src", "test", "java", "com", "sanjeevsky", "paymentservice", "PaymentIntegrationTest.java"),
  "utf8"
);
const paymentServiceImplTestText = fs.readFileSync(
  path.join(root, "payment-service", "src", "test", "java", "com", "sanjeevsky", "paymentservice", "service", "PaymentServiceImplTest.java"),
  "utf8"
);
if (!paymentServiceImplText.includes("validateIdempotentReplay")
    || !paymentServiceImplText.includes("InvalidPaymentRequestException")
    || !paymentServiceImplText.includes("Double.compare")) {
  fail("payment-service: idempotent payment replays must reject conflicting orderId/amount payloads");
}
if (!paymentIntegrationTestText.includes("initiatePayment_withSameIdempotencyKeyDifferentPayload_returns400")) {
  fail("payment-service: integration tests must cover conflicting idempotency-key payment initiation");
}
if (!paymentServiceImplText.includes("validateInitiationRequest(request)")
    || !paymentServiceImplText.includes("Payment orderId is required")
    || !paymentServiceImplText.includes("Payment userId is required")
    || !paymentServiceImplText.includes("Payment amount must be greater than zero")) {
  fail("payment-service: payment initiation must validate orderId, userId, and positive amount at the service boundary");
}
for (const testName of [
  "initiatePayment_missingOrderId_throwsInvalidPaymentRequestException",
  "initiatePayment_blankUserId_throwsInvalidPaymentRequestException",
  "initiatePayment_nonPositiveAmount_throwsInvalidPaymentRequestException",
]) {
  if (!paymentServiceImplTestText.includes(testName)) {
    fail(`payment-service: missing service test ${testName}`);
  }
}
for (const testName of [
  "initiatePayment_missingOrderId_returns400",
  "initiatePayment_blankUserId_returns400",
  "initiatePayment_nonPositiveAmount_returns400",
]) {
  if (!paymentIntegrationTestText.includes(testName)) {
    fail(`payment-service: missing integration test ${testName}`);
  }
}

const inventoryServiceImplText = fs.readFileSync(
  path.join(root, "inventory-service", "src", "main", "java", "com", "sanjeevsky", "inventoryservice", "service", "impl", "InventoryServiceImpl.java"),
  "utf8"
);
const inventoryServiceImplTestText = fs.readFileSync(
  path.join(root, "inventory-service", "src", "test", "java", "com", "sanjeevsky", "inventoryservice", "service", "InventoryServiceImplTest.java"),
  "utf8"
);
if (!inventoryServiceImplText.includes("validatePositiveQuantity(quantity, \"Stock quantity\")")
    || !inventoryServiceImplText.includes("validatePositiveQuantity(qty, \"Reservation quantity\")")
    || !inventoryServiceImplText.includes("validatePositiveQuantity(qty, \"Release quantity\")")
    || !inventoryServiceImplText.includes("validateProductId(productId)")
    || !inventoryServiceImplText.includes("validateOrderId(orderId)")
    || !inventoryServiceImplText.includes("Inventory id is required")
    || !inventoryServiceImplText.includes("InvalidInventoryRequestException")) {
  fail("inventory-service: stock operations must reject non-positive quantities and missing ids at the service boundary");
}
for (const testName of [
  "addStock_nonPositiveQuantity_throwsInvalidInventoryRequestException",
  "addStock_nullProductId_throwsInvalidInventoryRequestException",
  "getStockById_nullInventoryId_throwsInvalidInventoryRequestException",
  "reserveStock_nonPositiveQuantity_throwsInvalidInventoryRequestException",
  "reserveStock_nullOrderId_throwsInvalidInventoryRequestException",
  "releaseStock_nonPositiveQuantity_throwsInvalidInventoryRequestException",
  "getStockByProduct_nullProductId_throwsInvalidInventoryRequestException",
]) {
  if (!inventoryServiceImplTestText.includes(testName)) {
    fail(`inventory-service: missing service test ${testName}`);
  }
}

const prometheusText = fs.readFileSync(path.join(root, "observability", "prometheus.yml"), "utf8");
const grafanaOverviewText = fs.readFileSync(
  path.join(root, "observability", "grafana", "dashboards", "ecommerce-overview.json"),
  "utf8"
);
for (const [service, target] of [
  ["service-discovery", "service-discovery:8761"],
  ["cloud-config", "cloud-config:8071"],
]) {
  if (!prometheusText.includes(`job_name: '${service}'`) || !prometheusText.includes(`targets: ['${target}']`)) {
    fail(`observability/prometheus.yml: missing ${service} scrape target ${target}`);
  }
  if (!grafanaOverviewText.includes(service)) {
    fail(`observability/grafana/dashboards/ecommerce-overview.json: dashboard service totals must include ${service}`);
  }
}
if (!propertiesFiles("service-discovery").some((file) => hasProperty(file, "management.endpoints.web.exposure.include"))) {
  fail("service-discovery: Prometheus scrape requires management.endpoints.web.exposure.include=*");
}
if (!implementationText.includes("| service-discovery | 8761 | ✅ | ✅ | ✅ | — | — | ✅ |")
    || !implementationText.includes("| cloud-config | 8071 | ✅ | ✅ | ✅ | — | — | ✅ |")) {
  fail("implementation.md: service matrix must reflect observability coverage for service-discovery and cloud-config");
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

const shoppingCartServiceImplText = fs.readFileSync(
  path.join(root, "shopping-cart-service", "src", "main", "java", "com", "sanjeevsky", "shoppingcartservice", "services", "impl", "CartServiceImpl.java"),
  "utf8"
);
const shoppingCartServiceTestText = fs.readFileSync(
  path.join(root, "shopping-cart-service", "src", "test", "java", "com", "sanjeevsky", "shoppingcartservice", "service", "CartServiceImplTest.java"),
  "utf8"
);
const shoppingCartIntegrationTestText = fs.readFileSync(
  path.join(root, "shopping-cart-service", "src", "test", "java", "com", "sanjeevsky", "shoppingcartservice", "CartIntegrationTest.java"),
  "utf8"
);
if (!shoppingCartServiceImplText.includes("validateAddQuantity(qty)")
    || !shoppingCartServiceImplText.includes("validateUpdateQuantity(qty)")
    || !shoppingCartServiceImplText.includes("InvalidCartRequestException")) {
  fail("shopping-cart-service: cart mutations must reject non-positive adds and negative updates at the service boundary");
}
for (const testName of [
  "addItem_nonPositiveQty_throwsInvalidCartRequestException",
  "updateItem_negativeQty_throwsInvalidCartRequestException",
]) {
  if (!shoppingCartServiceTestText.includes(testName)) {
    fail(`shopping-cart-service: missing service test ${testName}`);
  }
}
if (!shoppingCartIntegrationTestText.includes("updateItem_negativeQty_returnsBadRequest")) {
  fail("shopping-cart-service: integration tests must reject negative cart update quantities");
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
const buildDockerJarsText = fs.readFileSync(path.join(root, "scripts", "build-docker-jars.sh"), "utf8");
requireMavenTestFlags("scripts/verify-local.sh", verifyLocalText);
requireMavenTestFlags(
  ".github/workflows/ci.yml",
  fs.readFileSync(path.join(root, ".github", "workflows", "ci.yml"), "utf8")
);

if (!verifyLocalText.includes("MAVEN_JAVA_HOME")
    || !buildDockerJarsText.includes("MAVEN_JAVA_HOME")
    || !verifyLocalText.includes('export JAVA_HOME="$preferred_java_home"')
    || !buildDockerJarsText.includes('export JAVA_HOME="$preferred_java_home"')) {
  fail("local Maven scripts must prefer Java 11 through MAVEN_JAVA_HOME for Lombok-compatible builds");
}

if (!verifyLocalText.includes("RUN_DIRECT_HEALTH_CHECKS")
    || !verifyLocalText.includes("SERVICE_HEALTH_CHECKS")
    || !verifyLocalText.includes("INVENTORY_SERVICE_PORT")) {
  fail("scripts/verify-local.sh: local smoke verifier must wait for direct service actuator health before Postman runs");
}

if (!verifyLocalText.includes("GATEWAY_ROUTE_CHECKS")
    || !verifyLocalText.includes("wait_for_gateway_route")
    || !verifyLocalText.includes("/catalog-service/product/list")) {
  fail("scripts/verify-local.sh: local smoke verifier must wait for gateway route readiness before Postman runs");
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
