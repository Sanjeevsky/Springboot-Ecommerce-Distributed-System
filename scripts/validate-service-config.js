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

const expectedControllerPrefixes = {
  "api-gateway": [],
  "auth-server": ["/auth-service"],
  "catalog-service": ["/catalog-service"],
  "coupon-service": ["/coupon-service"],
  "customer-service": ["/customer-service"],
  "inventory-service": ["/inventory-service"],
  "notification-service": ["/notification-service"],
  "order-service": ["/order-service"],
  "payment-service": ["/payment-service"],
  "review-service": ["/review-service"],
  "shopping-cart-service": ["/cart-service"],
  "wishlist-service": ["/wishlist-service"],
};

const expectedComposeHealthPorts = {
  "api-gateway": 8081,
  "auth-server": 8083,
  "catalog-service": 8084,
  "coupon-service": 8089,
  "customer-service": 8082,
  "inventory-service": 8088,
  "notification-service": 8087,
  "order-service": 8092,
  "payment-service": 8085,
  "review-service": 8090,
  "shopping-cart-service": 8086,
  "wishlist-service": 8091,
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

function controllerBaseMappings(text) {
  const classPrefix = text.split(/\bclass\b/)[0] || text;
  const mappings = [];
  const mappingRegex = /@RequestMapping\s*\(\s*(?:value\s*=\s*)?"([^"]*)"/g;
  let match;
  while ((match = mappingRegex.exec(classPrefix)) !== null) {
    mappings.push(match[1].replace(/\/+$/, "") || "/");
  }
  return mappings;
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

function shellArrayValues(text, arrayName, relativePath = "scripts/verify-local.sh") {
  const escapedName = arrayName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = text.match(new RegExp(`^${escapedName}=\\(\\n([\\s\\S]*?)^\\)`, "m"));
  if (!match) {
    fail(`${relativePath}: missing ${arrayName} array`);
    return [];
  }

  return match[1]
    .split(/\r?\n/)
    .map((line) => line.replace(/#.*/, "").trim())
    .filter(Boolean)
    .map((line) => line.replace(/^["']|["']$/g, ""));
}

function githubActionsMatrixValues(text, matrixName) {
  const escapedName = matrixName.replace(/[.*+?^${}()|[\]\\]/g, "\\$&");
  const match = text.match(new RegExp(`^\\s*${escapedName}:\\s*\\n((?:\\s+-\\s+[^\\n]+\\n)+)`, "m"));
  if (!match) {
    fail(`.github/workflows/ci.yml: missing ${matrixName} matrix`);
    return [];
  }

  return match[1]
    .split(/\r?\n/)
    .map((line) => {
      const item = line.match(/^\s+-\s+(.+?)\s*$/);
      return item ? item[1].replace(/^["']|["']$/g, "") : "";
    })
    .filter(Boolean);
}

function duplicateValues(values) {
  const seen = new Set();
  const duplicates = new Set();
  for (const value of values) {
    if (seen.has(value)) {
      duplicates.add(value);
    }
    seen.add(value);
  }
  return [...duplicates];
}

function requireSameValues(label, expectedValues, actualValues, actualDescription) {
  const duplicates = duplicateValues(actualValues);
  if (duplicates.length) {
    fail(`${actualDescription}: duplicate ${label} entries: ${duplicates.join(", ")}`);
  }

  const missing = expectedValues.filter((value) => !actualValues.includes(value));
  const extra = actualValues.filter((value) => !expectedValues.includes(value));
  if (missing.length || extra.length) {
    fail(`${actualDescription}: ${label} entries must match expected services; missing [${missing.join(", ") || "none"}], extra [${extra.join(", ") || "none"}]`);
    return false;
  }
  return duplicates.length === 0;
}

function requireSameOrderedValues(label, expectedValues, actualValues, actualDescription) {
  if (requireSameValues(label, expectedValues, actualValues, actualDescription)
      && expectedValues.join("\n") !== actualValues.join("\n")) {
    fail(`${actualDescription}: ${label} entries must stay in the same order as expected services`);
  }
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
    if (text.includes("springfox.") || text.includes("@EnableSwagger2")) {
      fail(`${relativeFile}: Springfox Swagger 2 config must not be used; use springdoc OpenAPI config`);
    }
    if (text.includes("@RestController")) {
      for (const line of requestMappingVisibilityIssues(text)) {
        fail(`${relativeFile}:${line}: request mapping methods must be public`);
      }
      const allowedPrefixes = expectedControllerPrefixes[service] || [];
      for (const baseMapping of controllerBaseMappings(text)) {
        if (!allowedPrefixes.some((prefix) => baseMapping === prefix || baseMapping.startsWith(`${prefix}/`))) {
          fail(`${relativeFile}: controller base mapping ${baseMapping} must use standard gateway prefixes [${allowedPrefixes.join(", ")}]`);
        }
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

  const servicePom = fs.readFileSync(path.join(root, service, "pom.xml"), "utf8");
  if (servicePom.includes("<groupId>io.springfox</groupId>")
      || servicePom.includes("springfox-swagger")) {
    fail(`${service}/pom.xml: Springfox dependencies must not be used; use springdoc-openapi-ui`);
  }

  const names = files.flatMap(applicationNameValues);
  if (!names.includes(expectedName)) {
    const found = names.length ? names.join(", ") : "<none>";
    fail(`${service}: expected spring.application.name=${expectedName}, found ${found}`);
  }

  if (service !== "service-discovery") {
    const preferIpAddressValues = files.flatMap((file) => propertyValues(file, "eureka.instance.preferIpAddress"));
    if (!preferIpAddressValues.some((value) => value.toLowerCase() === "true")) {
      fail(`${service}: Eureka clients must set eureka.instance.preferIpAddress=true for Docker-network registrations`);
    }
  }

  const mainPropertiesFile = path.join(root, service, "src", "main", "resources", "application.properties");
  if (service !== "cloud-config" && fs.existsSync(mainPropertiesFile)) {
    const defaultProfiles = propertyValues(mainPropertiesFile, "spring.profiles.active");
    if (!defaultProfiles.includes("dev")) {
      const found = defaultProfiles.length ? defaultProfiles.join(", ") : "<none>";
      fail(`${service}: application.properties must default to spring.profiles.active=dev for Eureka-backed local runs, found ${found}`);
    }
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
    if (/^spring\.boot\.admin\.client\.username\s*=\s*client\s*$/m.test(text)) {
      fail(`${relativeFile}: Spring Boot Admin client username must read from SPRING_BOOT_ADMIN_CLIENT_USERNAME`);
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

  const adminClientUsernameValues = files.flatMap((file) => propertyValues(file, "spring.boot.admin.client.username"));
  if (adminClientUsernameValues.length
      && !adminClientUsernameValues.every((value) => value.startsWith("${SPRING_BOOT_ADMIN_CLIENT_USERNAME:"))) {
    fail(`${service}: spring.boot.admin.client.username must use \${SPRING_BOOT_ADMIN_CLIENT_USERNAME:...}`);
  }
}

const springServerSecurityPasswords = propertiesFiles("spring-server")
  .flatMap((file) => propertyValues(file, "spring.security.user.password"));
if (!springServerSecurityPasswords.length
    || !springServerSecurityPasswords.every((value) => value.startsWith("${SPRING_SECURITY_USER_PASSWORD:"))) {
  fail("spring-server: spring.security.user.password must use ${SPRING_SECURITY_USER_PASSWORD:...}");
}

const springServerSecurityUsernames = propertiesFiles("spring-server")
  .flatMap((file) => propertyValues(file, "spring.security.user.name"));
if (!springServerSecurityUsernames.length
    || !springServerSecurityUsernames.every((value) => value.startsWith("${SPRING_SECURITY_USER_NAME:"))) {
  fail("spring-server: spring.security.user.name must use ${SPRING_SECURITY_USER_NAME:...}");
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
const loadTestsReadmeText = fs.readFileSync(path.join(root, "load-tests", "README.md"), "utf8");
const generateArchText = fs.readFileSync(path.join(root, "generate-arch.py"), "utf8");
const architectureText = fs.readFileSync(path.join(root, "architecture.html"), "utf8");
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
  if (!text.includes("COPY target/*.jar app.jar") || text.includes("ADD target/*.jar")) {
    fail(`${service}: Dockerfile must COPY the packaged jar instead of using ADD`);
  }
  if (!text.includes("-Xmx96m") || !text.includes("-XX:MaxMetaspaceSize=128m") || !text.includes("-XX:MaxDirectMemorySize=32m")) {
    fail(`${service}: Dockerfile must use the low-memory JVM profile for full-stack local verification`);
  }
  const dockerignoreFile = path.join(root, service, ".dockerignore");
  if (!fs.existsSync(dockerignoreFile)) {
    fail(`${service}: .dockerignore is missing`);
  } else {
    const dockerignoreText = fs.readFileSync(dockerignoreFile, "utf8");
    for (const requiredDockerignoreLine of ["*", "!Dockerfile", "!target/", "!target/*.jar"]) {
      if (!dockerignoreText.split(/\r?\n/).includes(requiredDockerignoreLine)) {
        fail(`${service}: .dockerignore must keep Docker build context limited to Dockerfile and target/*.jar`);
      }
    }
  }
}

const composeText = fs.readFileSync(path.join(root, "docker-compose.yml"), "utf8");
const springServerComposeBlock = composeServiceBlock("spring-server");
if (!springServerComposeBlock.includes('profiles: ["platform-tools"]')) {
  fail("docker-compose.yml: spring-server must stay in the optional platform-tools profile");
}
if (!springServerComposeBlock.includes("SPRING_SECURITY_USER_PASSWORD=${SPRING_SECURITY_USER_PASSWORD:-client}")) {
  fail("docker-compose.yml: spring-server must expose SPRING_SECURITY_USER_PASSWORD for optional admin login overrides");
}
if (!springServerComposeBlock.includes("SPRING_SECURITY_USER_NAME=${SPRING_SECURITY_USER_NAME:-client}")) {
  fail("docker-compose.yml: spring-server must expose SPRING_SECURITY_USER_NAME for optional admin login overrides");
}
if (!readmeText.includes("optional `platform-tools` profile")
    || !readmeText.includes("client / client; optional `platform-tools` profile")
    || !readmeText.includes("SPRING_BOOT_ADMIN_CLIENT_ENABLED=true docker compose --profile platform-tools up -d")
    || !readmeText.includes("SPRING_SECURITY_USER_NAME")
    || !readmeText.includes("SPRING_BOOT_ADMIN_CLIENT_USERNAME")
    || !implementationText.includes("optional `platform-tools` profile")
    || !implementationText.includes("client/client; optional `platform-tools` profile")
    || !implementationText.includes("SPRING_BOOT_ADMIN_CLIENT_ENABLED=true docker compose --profile platform-tools up -d")
    || !implementationText.includes("SPRING_SECURITY_USER_NAME")
    || !implementationText.includes("SPRING_BOOT_ADMIN_CLIENT_USERNAME")) {
  fail("README.md and implementation.md must document the optional Spring Boot Admin platform-tools profile");
}
if (readmeText.includes("Spring Boot Admin | http://localhost:9000 (optional `platform-tools` profile)")
    || implementationText.includes("Spring Boot Admin | http://localhost:9000 | admin/admin")) {
  fail("README.md and implementation.md must document actual Spring Boot Admin default credentials");
}
if (/\bdocker-compose\s+up\b/.test(readmeText)
    || /\bdocker-compose\s+up\b/.test(implementationText)
    || /\bdocker-compose\s+up\b/.test(loadTestsReadmeText)) {
  fail("Runnable Docker Compose commands in docs must use `docker compose`, not `docker-compose`");
}
if (!readmeText.includes("Application services default to the `dev` profile")
    || !implementationText.includes("spring.profiles.active=dev")) {
  fail("README.md and implementation.md must document the default dev profile for Eureka-backed local runs");
}
const customerLocalProfileText = fs.readFileSync(path.join(root, "customer-service", "src", "main", "resources", "application-local.properties"), "utf8");
if (!customerLocalProfileText.includes("eureka.client.registerWithEureka = false")
    || !customerLocalProfileText.includes("eureka.client.fetchRegistry = false")
    || !readmeText.includes("application-local.properties` is an opt-in isolated profile")
    || !implementationText.includes("opt-in `application-local.properties` profile")) {
  fail("customer-service local profile must remain documented as an opt-in isolated profile with Eureka disabled");
}
if (implementationText.includes("grep -o '<app>.*</app>'")
    || implementationText.includes("cd platform-commons && mvn install -q && cd ..")
    || !implementationText.includes("scripts/build-docker-jars.sh")
    || !implementationText.includes("grep -o '<name>[^<]*</name>'")) {
  fail("implementation.md: Build & Run commands must use maintained Docker jar build and Eureka name checks");
}
if (composeText.includes("SPRING_ZIPKIN_ENABLED=${SPRING_ZIPKIN_ENABLED:-false}")) {
  fail("docker-compose.yml: tracing is enabled by default; use SPRING_ZIPKIN_ENABLED=${SPRING_ZIPKIN_ENABLED:-true}");
}
if (implementationText.includes("defaults tracing off")
    || !implementationText.includes("Docker defaults tracing on; disable with SPRING_ZIPKIN_ENABLED=false")) {
  fail("implementation.md: Zipkin snippet must document Docker tracing as on by default");
}
if (generateArchText.includes("Receives B3 traces from all 12 business services")
    || !generateArchText.includes("Services emit B3 traces when SPRING_ZIPKIN_ENABLED=true")) {
  fail("generate-arch.py: Zipkin tooltip must document tracing as opt-in");
}
if (generateArchText.includes("<div class=\"lbl\">Unit Tests</div>")
    || !generateArchText.includes("<div class=\"lbl\">Tests</div>")) {
  fail("generate-arch.py: architecture stats must label the aggregate test count as Tests");
}
if (!architectureText.includes("Services emit B3 traces when SPRING_ZIPKIN_ENABLED=true")
    || !architectureText.includes("<div class=\"lbl\">Tests</div>")
    || !architectureText.includes("inventory-service, payment-service")) {
  fail("architecture.html must be regenerated from current architecture metadata");
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
const gatewayConfigTestText = fs.readFileSync(
  path.join(root, "api-gateway", "src", "test", "java", "com", "sanjeevsky", "apigateway", "config", "GatewayConfigTest.java"),
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
if (!gatewayConfigTestText.includes('assertRoute(routes, "cart-service", "lb://shopping-cart-service", "/cart-service/cart")')) {
  fail("api-gateway GatewayConfigTest: all-prefix route coverage must assert the standard cart route");
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
if (!gatewayAuthFilterText.includes('regionMatches(true, 0, "Bearer ", 0, 7)')
    || !gatewayAuthFilterText.includes("token.isEmpty() ? null : token")) {
  fail("api-gateway AuthenticationFilter: protected requests must require Authorization: Bearer <token>");
}
if (!gatewayAuthFilterText.includes('headers.set("X-User", claims.getSubject())')) {
  fail("api-gateway AuthenticationFilter: JWT subject must replace any client-supplied X-User header");
}
if (!gatewayAuthFilterText.includes('claims.get("role", String.class)')
    || !gatewayAuthFilterText.includes('headers.set("X-User-Role"')
    || !gatewayAuthFilterText.includes('headers.remove("X-User-Role")')) {
  fail("api-gateway AuthenticationFilter: JWT role must replace client-supplied X-User-Role and public routes must strip it");
}
const gatewayAuthFilterTestText = fs.readFileSync(
  path.join(root, "api-gateway", "src", "test", "java", "com", "sanjeevsky", "apigateway", "filter", "AuthenticationFilterTest.java"),
  "utf8"
);
if (!gatewayAuthFilterTestText.includes("filter_securedRouteValidBearerAuth_replacesClientUserHeader")
    || !gatewayAuthFilterTestText.includes('"spoofed@example.com"')
    || !gatewayAuthFilterTestText.includes('List.of("buyer@example.com")')) {
  fail("api-gateway AuthenticationFilterTest: must cover replacement of client-supplied X-User headers");
}
if (!gatewayAuthFilterTestText.includes("filter_securedRouteAdminToken_addsAdminRoleHeader")
    || !gatewayAuthFilterTestText.includes("filter_securedRouteTokenWithoutRoleClaim_defaultsRoleHeaderToCustomer")
    || !gatewayAuthFilterTestText.includes("filter_openRoute_stripsClientIdentityHeaders")) {
  fail("api-gateway AuthenticationFilterTest: must cover admin roles, legacy customer fallback, and public identity-header stripping");
}

const adminAuthorizationInterceptorText = fs.readFileSync(
  path.join(root, "platform-commons", "src", "main", "java", "com", "sanjeevsky", "platform", "security", "AdminAuthorizationInterceptor.java"),
  "utf8"
);
const platformSpringFactoriesText = fs.readFileSync(
  path.join(root, "platform-commons", "src", "main", "resources", "META-INF", "spring.factories"),
  "utf8"
);
if (!adminAuthorizationInterceptorText.includes("method.hasMethodAnnotation(AdminOnly.class)")
    || !adminAuthorizationInterceptorText.includes("MdcConstants.HEADER_USER_ROLE")
    || !adminAuthorizationInterceptorText.includes('"ADMIN".equalsIgnoreCase')
    || !platformSpringFactoriesText.includes("AdminAuthorizationAutoConfiguration")) {
  fail("platform-commons: shared @AdminOnly authorization must be auto-configured and require X-User-Role=ADMIN");
}

const adminSeederText = fs.readFileSync(
  path.join(root, "auth-server", "src", "main", "java", "com", "sanjeevsky", "authserver", "config", "AdminUserSeeder.java"),
  "utf8"
);
if (!adminSeederText.includes('@ConditionalOnProperty(name = "admin.seed.enabled", havingValue = "true")')
    || !adminSeederText.includes('@Value("${admin.seed.email}")')
    || !adminSeederText.includes('@Value("${admin.seed.password}")')
    || !composeText.includes("ADMIN_SEED_ENABLED=true")
    || !composeText.includes("ADMIN_SEED_EMAIL=${ADMIN_SEED_EMAIL:-admin@trove.local}")
    || !composeText.includes("ADMIN_SEED_PASSWORD=${ADMIN_SEED_PASSWORD:-admin123}")) {
  fail("auth-server: local admin seeding must be explicit, configurable, and enabled by Docker Compose");
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

const userAuthControllerTestText = fs.readFileSync(
  path.join(root, "auth-server", "src", "test", "java", "com", "sanjeevsky", "authserver", "controller", "UserAuthControllerTest.java"),
  "utf8"
);
for (const testName of [
  "signup_validRequest_returns201AndForwardsUser",
  "login_validRequest_returnsToken",
  "login_invalidEmail_returns400BeforeServiceCall",
  "updatePassword_validRequest_forwardsRequest",
  "updatePassword_shortNewPassword_returns400BeforeServiceCall",
]) {
  if (!userAuthControllerTestText.includes(testName)) {
    fail(`auth-server: missing controller test ${testName}`);
  }
}
if (!userAuthControllerTestText.includes('post("/auth-service/signup")')
    || !userAuthControllerTestText.includes('post("/auth-service/login")')
    || !userAuthControllerTestText.includes('put("/auth-service/updatePassword")')
    || !userAuthControllerTestText.includes("verifyNoInteractions(userService)")) {
  fail("auth-server: controller tests must cover standard auth routes, DTO validation, and service forwarding");
}

function composeServiceBlock(service) {
  const pattern = new RegExp(`^  ${service}:\\n([\\s\\S]*?)(?=^  [a-zA-Z0-9_-]+:|^volumes:|^networks:|(?![\\s\\S]))`, "m");
  const match = composeText.match(pattern);
  return match ? match[0] : "";
}

if (composeServiceBlock("zookeeper")) {
  fail("docker-compose.yml: zookeeper must not exist — kafka runs in single-node KRaft mode");
}

const kafkaBlock = composeServiceBlock("kafka");
if (!kafkaBlock.includes("KAFKA_PROCESS_ROLES: broker,controller")
    || !kafkaBlock.includes("KAFKA_CONTROLLER_QUORUM_VOTERS: 1@kafka:29093")) {
  fail("docker-compose.yml: kafka must run in single-node KRaft mode (broker,controller roles)");
}
if (!kafkaBlock.includes('test: ["CMD", "kafka-broker-api-versions", "--bootstrap-server", "localhost:9092"]')) {
  fail("docker-compose.yml: kafka must define a broker healthcheck for startup sequencing");
}

const kafkaUiBlock = composeServiceBlock("kafka-ui");
if (!kafkaUiBlock.includes('image: provectuslabs/kafka-ui')
    || !kafkaUiBlock.includes('"8080:8080"')
    || !kafkaUiBlock.includes("KAFKA_CLUSTERS_0_BOOTSTRAPSERVERS: kafka:29092")) {
  fail("docker-compose.yml: kafka-ui must expose http://localhost:8080 and connect to kafka:29092");
}
if (!kafkaUiBlock.includes('test: ["CMD", "wget", "-qO-", "http://localhost:8080/actuator/health"]')
    || !kafkaUiBlock.includes("start_period: 120s")) {
  fail("docker-compose.yml: kafka-ui must define a native actuator healthcheck");
}
if (!readmeText.includes("| Kafka UI | http://localhost:8080 |")
    || !readmeText.includes("| Docker-internal services and Kafka UI | `kafka:29092` |")
    || !implementationText.includes("| Kafka UI | http://localhost:8080 |")) {
  fail("README.md and implementation.md must document Kafka UI at http://localhost:8080");
}
if (!readmeText.includes("**15 Spring services** plus platform infrastructure across 3 tiers:")
    || readmeText.includes("**15 services** across 3 tiers:")) {
  fail("README.md: architecture summary must distinguish Spring services from platform infrastructure");
}

const prometheusBlock = composeServiceBlock("prometheus");
if (!prometheusBlock.includes('test: ["CMD", "wget", "-qO-", "http://localhost:9090/-/healthy"]')) {
  fail("docker-compose.yml: prometheus must define a native healthcheck");
}

const grafanaBlock = composeServiceBlock("grafana");
if (!grafanaBlock.includes('test: ["CMD", "wget", "-qO-", "http://localhost:3000/api/health"]')) {
  fail("docker-compose.yml: grafana must define a native healthcheck");
}

const serviceDiscoveryBlock = composeServiceBlock("service-discovery");
if (!serviceDiscoveryBlock.includes('test: ["CMD", "curl", "-f", "http://localhost:8761/actuator/health"]')
    || !serviceDiscoveryBlock.includes("start_period: 180s")) {
  fail("docker-compose.yml: service-discovery must define an actuator healthcheck with Spring startup grace");
}

const cloudConfigBlock = composeServiceBlock("cloud-config");
const cloudConfigNativeRepoPath = path.join(root, "cloud-config", "src", "main", "resources", "config-repo", "application.properties");
if (!cloudConfigBlock.includes("SPRING_PROFILES_ACTIVE=native")
    || !cloudConfigBlock.includes("SPRING_CLOUD_CONFIG_SERVER_NATIVE_SEARCH_LOCATIONS=classpath:/config-repo")
    || !cloudConfigBlock.includes('test: ["CMD", "curl", "-f", "http://localhost:8071/actuator/health"]')
    || !cloudConfigBlock.includes("start_period: 180s")
    || !fs.existsSync(cloudConfigNativeRepoPath)
    || !fs.readFileSync(cloudConfigNativeRepoPath, "utf8").includes("configserver.local.mode=true")
    || !readmeText.includes("native local mode, without cloning the remote config repository")
    || !implementationText.includes("avoiding remote Git clone dependency during local startup")) {
  fail("docker-compose.yml and docs must run cloud-config in native local mode for Docker startup stability");
}
if (!readmeText.includes("wait for `service-discovery` and `cloud-config` health")
    || !implementationText.includes("wait for `service-discovery` and `cloud-config` health")) {
  fail("README.md and implementation.md must document service-discovery/cloud-config health-gated startup");
}

for (const service of Object.keys(expectedApplicationNames)) {
  const block = composeServiceBlock(service);
  if (!block) {
    fail(`docker-compose.yml: missing service block for ${service}`);
  } else if (!block.includes("restart: unless-stopped")) {
    fail(`docker-compose.yml: ${service} must use restart: unless-stopped for local smoke stability`);
  } else {
    if (service !== "cloud-config"
        && block.includes("cloud-config:")
        && !/cloud-config:\n\s+condition:\s+service_healthy/.test(block)) {
      fail(`docker-compose.yml: ${service} must wait for cloud-config service_healthy`);
    }
    const importsConfigServer = propertiesFiles(service)
      .some((file) => propertyValues(file, "spring.config.import")
        .some((value) => value.includes("configserver:")));
    if (importsConfigServer
        && (!block.includes("SPRING_CLOUD_CONFIG_ENABLED=false")
          || !block.includes("SPRING_CLOUD_CONFIG_IMPORT_CHECK_ENABLED=false"))) {
      fail(`docker-compose.yml: ${service} must disable Spring Cloud Config client for Docker startup stability`);
    }
    const usesKafkaBootstrap = propertiesFiles(service)
      .some((file) => hasProperty(file, "spring.kafka.bootstrap-servers"));
    if (usesKafkaBootstrap && !block.includes("SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092")) {
      fail(`docker-compose.yml: ${service} must define SPRING_KAFKA_BOOTSTRAP_SERVERS=kafka:29092`);
    }
    if (block.includes("SPRING_BOOT_ADMIN_CLIENT_URL=http://spring-server:9000")
        && !block.includes("SPRING_BOOT_ADMIN_CLIENT_PASSWORD=${SPRING_BOOT_ADMIN_CLIENT_PASSWORD:-client}")) {
      fail(`docker-compose.yml: ${service} must expose SPRING_BOOT_ADMIN_CLIENT_PASSWORD for optional admin client registration`);
    }
    if (block.includes("SPRING_BOOT_ADMIN_CLIENT_URL=http://spring-server:9000")
        && !block.includes("SPRING_BOOT_ADMIN_CLIENT_USERNAME=${SPRING_BOOT_ADMIN_CLIENT_USERNAME:-client}")) {
      fail(`docker-compose.yml: ${service} must expose SPRING_BOOT_ADMIN_CLIENT_USERNAME for optional admin client registration`);
    }
    const expectedHealthPort = expectedComposeHealthPorts[service];
    if (expectedHealthPort
        && (!block.includes(`test: ["CMD", "curl", "-f", "http://localhost:${expectedHealthPort}/actuator/health"]`)
          || !block.includes("start_period: 180s"))) {
      fail(`docker-compose.yml: ${service} must define an actuator healthcheck on port ${expectedHealthPort} with Spring startup grace`);
    }
    if (expectedHealthPort && !block.includes("SPRING_PROFILES_ACTIVE=dev")) {
      fail(`docker-compose.yml: ${service} must explicitly run with SPRING_PROFILES_ACTIVE=dev`);
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
const customerControllerText = fs.readFileSync(
  path.join(root, "customer-service", "src", "main", "java", "com", "sanjeevsky", "customerservice", "controller", "CustomerServiceController.java"),
  "utf8"
);
const customerControllerTestText = fs.readFileSync(
  path.join(root, "customer-service", "src", "test", "java", "com", "sanjeevsky", "customerservice", "controller", "CustomerServiceControllerTest.java"),
  "utf8"
);
if (customerControllerText.includes("@Valid @RequestBody Address address")
    || !customerControllerTestText.includes("updateAddress_validPatch_returnsUpdatedAddressAndPassesUserHeader")) {
  fail("customer-service: address update endpoint must allow partial patch bodies and cover X-User propagation");
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
if (!orderControllerTestText.includes("createOrder_withCouponAndIdempotencyKey_passesHeadersAndReturns201")
    || !orderControllerTestText.includes('header("Idempotency-Key", "order-1")')
    || !orderControllerTestText.includes('verify(orderService).createOrder(USER, ADDRESS_ID, "SAVE10", "order-1")')) {
  fail("order-service: controller tests must cover X-User and Idempotency-Key propagation");
}

const orderAnalyticsControllerText = fs.readFileSync(
  path.join(root, "order-service", "src", "main", "java", "com", "sanjeevsky", "orderservice", "controller", "OrderAnalyticsController.java"),
  "utf8"
);
const orderAnalyticsServiceText = fs.readFileSync(
  path.join(root, "order-service", "src", "main", "java", "com", "sanjeevsky", "orderservice", "service", "impl", "OrderAnalyticsServiceImpl.java"),
  "utf8"
);
const orderAnalyticsControllerTestText = fs.readFileSync(
  path.join(root, "order-service", "src", "test", "java", "com", "sanjeevsky", "orderservice", "controller", "OrderAnalyticsControllerTest.java"),
  "utf8"
);
const orderAnalyticsAuthorizationTestText = fs.readFileSync(
  path.join(root, "order-service", "src", "test", "java", "com", "sanjeevsky", "orderservice", "controller", "OrderAnalyticsAuthorizationTest.java"),
  "utf8"
);
const studioHomeText = fs.readFileSync(
  path.join(root, "frontend", "src", "pages", "studio", "StudioHome.jsx"),
  "utf8"
);
if (!orderAnalyticsControllerText.includes('@RequestMapping("/order-service/analytics")')
    || !orderAnalyticsControllerText.includes("@AdminOnly")
    || !orderAnalyticsControllerText.includes('@GetMapping("/summary")')
    || !orderAnalyticsControllerText.includes('@GetMapping("/daily")')
    || !orderAnalyticsControllerText.includes('@GetMapping("/top-products")')) {
  fail("order-service: analytics endpoints must use the standard prefix and require the shared admin guard");
}
if (!orderAnalyticsServiceText.includes("REVENUE_STATUSES")
    || !orderAnalyticsServiceText.includes("OrderStatus.CONFIRMED")
    || !orderAnalyticsServiceText.includes("OrderStatus.SHIPPED")
    || !orderAnalyticsServiceText.includes("OrderStatus.DELIVERED")
    || !orderAnalyticsServiceText.includes("Analytics from date must be on or before to date")) {
  fail("order-service: analytics must validate date ranges and exclude pending/cancelled orders from recognized revenue");
}
if (!orderAnalyticsControllerTestText.includes("getSummary_passesDateRangeAndReturnsContract")
    || !orderAnalyticsControllerTestText.includes("getDaily_passesDaysAndReturnsPoints")
    || !orderAnalyticsControllerTestText.includes("getTopProducts_passesLimitAndReturnsRows")
    || !orderAnalyticsAuthorizationTestText.includes("summary_customerRoleReturns403")
    || !orderAnalyticsAuthorizationTestText.includes("summary_adminRoleReachesService")) {
  fail("order-service: analytics tests must cover endpoint contracts and admin authorization");
}
if (!studioHomeText.includes("ResponsiveContainer")
    || !studioHomeText.includes("analytics.summary")
    || !studioHomeText.includes("analytics.daily")
    || !studioHomeText.includes("analytics.topProducts")
    || !studioHomeText.includes("Low stock")) {
  fail("frontend: Studio overview must render sales analytics and low-stock alerts");
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
const couponControllerTestText = fs.readFileSync(
  path.join(root, "coupon-service", "src", "test", "java", "com", "sanjeevsky", "couponservice", "controller", "CouponControllerTest.java"),
  "utf8"
);
const couponAuthorizationTestText = fs.readFileSync(
  path.join(root, "coupon-service", "src", "test", "java", "com", "sanjeevsky", "couponservice", "controller", "CouponAdminAuthorizationTest.java"),
  "utf8"
);
const studioCouponsText = fs.readFileSync(
  path.join(root, "frontend", "src", "pages", "studio", "StudioCoupons.jsx"),
  "utf8"
);
const studioLayoutText = fs.readFileSync(
  path.join(root, "frontend", "src", "pages", "studio", "StudioLayout.jsx"),
  "utf8"
);
const frontendAppText = fs.readFileSync(
  path.join(root, "frontend", "src", "App.jsx"),
  "utf8"
);
const frontendServicesText = fs.readFileSync(
  path.join(root, "frontend", "src", "lib", "services.js"),
  "utf8"
);
const studioProductsText = fs.readFileSync(
  path.join(root, "frontend", "src", "pages", "studio", "StudioProducts.jsx"),
  "utf8"
);
const studioInventoryText = fs.readFileSync(
  path.join(root, "frontend", "src", "pages", "studio", "StudioInventory.jsx"),
  "utf8"
);
const csvExportText = fs.readFileSync(
  path.join(root, "frontend", "src", "lib", "csv.js"),
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
for (const testName of [
  "createCoupon_validRequest_returns201AndForwardsCoupon",
  "createCoupon_invalidRequest_returns400BeforeServiceCall",
  "validateCoupon_forwardsCodeAndAmount",
  "applyCoupon_forwardsCodeAndReturnsUpdatedCoupon",
  "getActiveCoupons_returnsServiceList",
]) {
  if (!couponControllerTestText.includes(testName)) {
    fail(`coupon-service: missing controller test ${testName}`);
  }
}
if (!couponControllerTestText.includes('post("/coupon-service/coupon")')
    || !couponControllerTestText.includes('get("/coupon-service/coupon/validate")')
    || !couponControllerTestText.includes('verify(couponService).validateCoupon("SAVE10", 200.0)')
    || !couponControllerTestText.includes("verifyNoInteractions(couponService)")) {
  fail("coupon-service: controller tests must cover standard routes, service forwarding, and validation short-circuit");
}
if (!couponControllerText.includes('@GetMapping("/admin/coupons")')
    || !couponControllerText.includes('@PutMapping("/coupon/{couponId}/active")')
    || (couponControllerText.match(/@AdminOnly/g) || []).length < 3
    || !couponServiceImplText.includes("findAllByOrderByCreatedAtDesc()")
    || !couponServiceImplText.includes("validateCouponCanBeApplied(coupon)")) {
  fail("coupon-service: coupon creation, administration, and activation lifecycle must use admin-gated standard routes");
}
for (const testName of [
  "applyCoupon_inactive_throwsInvalidCouponException",
  "applyCoupon_usageLimitReached_throwsInvalidCouponException",
  "getAllCoupons_returnsAdministrativeList",
  "setCouponActive_updatesAndSavesCoupon",
  "setCouponActive_missingCouponThrowsNotFound",
]) {
  if (!couponServiceTestText.includes(testName)) {
    fail(`coupon-service: missing lifecycle service test ${testName}`);
  }
}
for (const testName of [
  "getAllCoupons_returnsAdminServiceList",
  "setCouponActive_forwardsIdAndState",
]) {
  if (!couponControllerTestText.includes(testName)) {
    fail(`coupon-service: missing lifecycle controller test ${testName}`);
  }
}
for (const testName of [
  "createCoupon_customerRoleReturns403",
  "getAllCoupons_adminRoleReachesService",
  "setCouponActive_customerRoleReturns403",
  "setCouponActive_adminRoleReachesService",
]) {
  if (!couponAuthorizationTestText.includes(testName)) {
    fail(`coupon-service: missing admin authorization test ${testName}`);
  }
}
if (!couponIntegrationTestText.includes("adminListAndDeactivateCoupon")) {
  fail("coupon-service: integration tests must cover the admin list and activation lifecycle");
}
if (!studioCouponsText.includes("coupons.adminList")
    || !studioCouponsText.includes("coupons.create")
    || !studioCouponsText.includes("coupons.setActive")
    || !studioLayoutText.includes('to: "/studio/coupons"')
    || !frontendAppText.includes('path="coupons"')
    || !frontendServicesText.includes('"/coupon-service/admin/coupons"')
    || !frontendServicesText.includes("/coupon-service/coupon/${couponId}/active")) {
  fail("frontend: Studio coupon management must list, create, and activate coupons through standard gateway routes");
}
if (!csvExportText.includes("export function toCsv")
    || !csvExportText.includes("export function downloadCsv")
    || !csvExportText.includes('/^[=+\\-@]/')
    || !studioHomeText.includes('downloadCsv("trove-daily-sales.csv"')
    || !studioProductsText.includes('downloadCsv("trove-products.csv"')
    || !studioInventoryText.includes('downloadCsv("trove-inventory.csv"')
    || !studioCouponsText.includes('downloadCsv("trove-coupons.csv"')) {
  fail("frontend: Studio CSV exports must share a formula-safe serializer across analytics, products, inventory, and coupons");
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
const brandControllerTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "controller", "BrandControllerTest.java"),
  "utf8"
);
const categoryControllerTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "controller", "CategoryControllerTest.java"),
  "utf8"
);
const subCategoryControllerTestText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "test", "java", "com", "sanjeevsky", "catalogservice", "controller", "SubCategoryControllerTest.java"),
  "utf8"
);
const productCatalogControllerText = fs.readFileSync(
  path.join(root, "catalog-service", "src", "main", "java", "com", "sanjeevsky", "catalogservice", "controller", "ProductCatalogController.java"),
  "utf8"
);
if (!productCatalogControllerText.includes('@GetMapping("/admin/list")')
    || (productCatalogControllerText.match(/@AdminOnly/g) || []).length < 4
    || (variantControllerText.match(/@AdminOnly/g) || []).length < 3) {
  fail("catalog-service: product administration and all catalog write endpoints must remain admin-only");
}
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
  "getBrand_forwardsId",
  "getBrandByName_forwardsName",
  "getBrands_returnsServiceList",
  "addBrand_forwardsNameAndReturns201",
]) {
  if (!brandControllerTestText.includes(testName)) {
    fail(`catalog-service: missing brand controller test ${testName}`);
  }
}
for (const testName of [
  "getCategory_forwardsId",
  "getCategoryByName_forwardsName",
  "getCategories_returnsServiceList",
  "addCategory_forwardsNameAndReturns201",
]) {
  if (!categoryControllerTestText.includes(testName)) {
    fail(`catalog-service: missing category controller test ${testName}`);
  }
}
if (!subCategoryControllerTestText.includes("addSubCategory_forwardsCategoryIdAndName")
    || !brandControllerTestText.includes('post("/catalog-service/add-brand")')
    || !categoryControllerTestText.includes('post("/catalog-service/addCategory")')
    || !subCategoryControllerTestText.includes('post("/catalog-service/add-subcategory/{category-id}", CATEGORY_ID)')
    || !subCategoryControllerTestText.includes('verify(subCategoryService).addSubCategory(CATEGORY_ID, "Sneakers")')) {
  fail("catalog-service: lookup controller tests must cover standard brand/category/subcategory routes and service forwarding");
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
const reviewControllerTestText = fs.readFileSync(
  path.join(root, "review-service", "src", "test", "java", "com", "sanjeevsky", "reviewservice", "controller", "ReviewControllerTest.java"),
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
for (const testName of [
  "addReview_withXUser_returns201AndForwardsReview",
  "addReview_invalidReview_returns400BeforeServiceCall",
  "getApprovedReviews_forwardsProductId",
  "getProductSummary_forwardsProductId",
  "moderateReview_forwardsIdAndStatus",
  "getUserReviews_forwardsXUser",
]) {
  if (!reviewControllerTestText.includes(testName)) {
    fail(`review-service: missing controller test ${testName}`);
  }
}
if (!reviewControllerTestText.includes('post("/review-service/review")')
    || !reviewControllerTestText.includes('get("/review-service/review/my")')
    || !reviewControllerTestText.includes('verify(reviewService).moderateReview(REVIEW_ID, "APPROVED")')
    || !reviewControllerTestText.includes("verifyNoInteractions(reviewService)")) {
  fail("review-service: controller tests must cover standard routes, X-User forwarding, moderation params, and validation short-circuit");
}

const notificationControllerTestText = fs.readFileSync(
  path.join(root, "notification-service", "src", "test", "java", "com", "sanjeevsky", "notificationservice", "controller", "NotificationControllerTest.java"),
  "utf8"
);
for (const testName of [
  "getAllForUser_trimsXUserAndReturnsNotifications",
  "getUnreadForUser_trimsXUserAndQueriesUnreadOnly",
  "getAllForUser_blankXUser_returns400BeforeRepositoryCall",
  "markAsRead_marksNotificationAndReturnsSavedEntity",
  "markAsRead_missingNotification_returns404BeforeSave",
]) {
  if (!notificationControllerTestText.includes(testName)) {
    fail(`notification-service: missing controller test ${testName}`);
  }
}
if (!notificationControllerTestText.includes('header("X-User", " " + USER_ID + " ")')
    || !notificationControllerTestText.includes("findByUserIdAndRead(USER_ID, false)")
    || !notificationControllerTestText.includes("verify(notificationRepository, never()).save(any())")) {
  fail("notification-service: controller tests must cover X-User normalization, unread filtering, and mark-read not-found behavior");
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
const wishlistControllerTestText = fs.readFileSync(
  path.join(root, "wishlist-service", "src", "test", "java", "com", "sanjeevsky", "wishlistservice", "controller", "WishlistControllerTest.java"),
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
for (const testName of [
  "addToWishlist_withXUser_returns201AndForwardsRequest",
  "addToWishlist_invalidRequest_returns400BeforeServiceCall",
  "getWishlist_forwardsXUserAndReturnsItems",
  "removeFromWishlist_forwardsXUserAndProductId",
  "moveToCart_forwardsXUserAndProductId",
]) {
  if (!wishlistControllerTestText.includes(testName)) {
    fail(`wishlist-service: missing controller test ${testName}`);
  }
}
if (!wishlistControllerTestText.includes('post("/wishlist-service/wishlist")')
    || !wishlistControllerTestText.includes('post("/wishlist-service/wishlist/{productId}/move-to-cart", PRODUCT_ID)')
    || !wishlistControllerTestText.includes('header("X-User", USER_ID)')) {
  fail("wishlist-service: controller tests must cover standard wishlist routes and X-User forwarding");
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
const paymentControllerTestText = fs.readFileSync(
  path.join(root, "payment-service", "src", "test", "java", "com", "sanjeevsky", "paymentservice", "controller", "PaymentControllerTest.java"),
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
if (!paymentControllerTestText.includes("initiatePayment_withIdempotencyKey_trimsHeaderAndReturns201")
    || !paymentControllerTestText.includes('header("Idempotency-Key", " payment-1 ")')
    || !paymentControllerTestText.includes('assertThat(captor.getValue().getIdempotencyKey()).isEqualTo("payment-1")')) {
  fail("payment-service: controller tests must cover Idempotency-Key trimming before payment initiation");
}

const inventoryServiceImplText = fs.readFileSync(
  path.join(root, "inventory-service", "src", "main", "java", "com", "sanjeevsky", "inventoryservice", "service", "impl", "InventoryServiceImpl.java"),
  "utf8"
);
const inventoryServiceImplTestText = fs.readFileSync(
  path.join(root, "inventory-service", "src", "test", "java", "com", "sanjeevsky", "inventoryservice", "service", "InventoryServiceImplTest.java"),
  "utf8"
);
const inventoryControllerTestText = fs.readFileSync(
  path.join(root, "inventory-service", "src", "test", "java", "com", "sanjeevsky", "inventoryservice", "controller", "InventoryControllerTest.java"),
  "utf8"
);
const inventoryControllerText = fs.readFileSync(
  path.join(root, "inventory-service", "src", "main", "java", "com", "sanjeevsky", "inventoryservice", "controller", "InventoryController.java"),
  "utf8"
);
if ((inventoryControllerText.match(/@AdminOnly/g) || []).length < 4
    || !inventoryControllerText.includes('@PutMapping("/stock/{productId}")')
    || !inventoryControllerText.includes('@PutMapping("/stock/{productId}/variant/{variantId}")')
    || !inventoryControllerText.includes('@GetMapping("/stock")')) {
  fail("inventory-service: stock list, additive writes, and absolute product/variant updates must remain admin-only");
}
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
for (const testName of [
  "addStock_validRequest_returnsUpdatedStockAndForwardsValues",
  "addStock_invalidQuantity_returns400BeforeServiceCall",
  "getStockByProduct_forwardsProductId",
  "getVariantStock_forwardsProductAndVariantIds",
  "getAvailableQty_queriesProductStockWithoutVariant",
]) {
  if (!inventoryControllerTestText.includes(testName)) {
    fail(`inventory-service: missing controller test ${testName}`);
  }
}
if (!inventoryControllerTestText.includes('post("/inventory-service/stock")')
    || !inventoryControllerTestText.includes('get("/inventory-service/stock/{productId}/variant/{variantId}", PRODUCT_ID, VARIANT_ID)')
    || !inventoryControllerTestText.includes("verify(inventoryService).getStock(PRODUCT_ID, null)")
    || !inventoryControllerTestText.includes("verifyNoInteractions(inventoryService)")) {
  fail("inventory-service: controller tests must cover standard stock routes, service forwarding, and validation short-circuit");
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
const shoppingCartControllerTestText = fs.readFileSync(
  path.join(root, "shopping-cart-service", "src", "test", "java", "com", "sanjeevsky", "shoppingcartservice", "controller", "CartControllerTest.java"),
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
for (const testName of [
  "getCart_forwardsXUser",
  "addItem_forwardsXUserAndRequestValues",
  "addItem_invalidRequest_returns400BeforeServiceCall",
  "updateItem_forwardsXUserProductAndQuantity",
  "removeItem_forwardsXUserAndProduct",
  "clearCart_forwardsXUser",
  "getCheckoutSnapshot_returnsSnapshotResponse",
]) {
  if (!shoppingCartControllerTestText.includes(testName)) {
    fail(`shopping-cart-service: missing controller test ${testName}`);
  }
}
if (!shoppingCartControllerTestText.includes('post("/cart-service/cart/add")')
    || !shoppingCartControllerTestText.includes('get("/cart-service/cart/checkout")')
    || !shoppingCartControllerTestText.includes("verify(cartService).addItem(USER_ID, PRODUCT_ID, VARIANT_ID, 2)")
    || !shoppingCartControllerTestText.includes("verifyNoInteractions(cartService)")) {
  fail("shopping-cart-service: controller tests must cover standard cart routes, X-User forwarding, checkout snapshots, and validation short-circuit");
}

const catalogBrowseLoadTestText = fs.readFileSync(path.join(root, "load-tests", "catalog-browse.js"), "utf8");
const loadTestTexts = [
  ["load-tests/catalog-browse.js", catalogBrowseLoadTestText],
  ["load-tests/checkout-flow.js", fs.readFileSync(path.join(root, "load-tests", "checkout-flow.js"), "utf8")],
];
for (const [fileName, text] of loadTestTexts) {
  if (!text.includes('const BASE_URL   = __ENV.BASE_URL || "http://localhost:8081"')) {
    fail(`${fileName}: k6 scripts must default to the API Gateway at http://localhost:8081`);
  }
  for (const forbiddenEndpoint of [
    "http://localhost:8082",
    "http://localhost:8083",
    "http://localhost:8084",
    "http://localhost:8085",
    "http://localhost:8086",
    "http://localhost:8087",
    "http://localhost:8088",
    "http://localhost:8089",
    "http://localhost:8090",
    "http://localhost:8091",
    "http://localhost:8092",
    "/shopping-cart-service/",
  ]) {
    if (text.includes(forbiddenEndpoint)) {
      fail(`${fileName}: load tests must use standard gateway routes, not ${forbiddenEndpoint}`);
    }
  }
}
if (catalogBrowseLoadTestText.includes("https://")
    || catalogBrowseLoadTestText.includes("jslib.k6.io")) {
  fail("load-tests/catalog-browse.js: k6 scripts must not require remote runtime imports");
}
if (catalogBrowseLoadTestText.includes("keyword=phone")
    || catalogBrowseLoadTestText.includes("/catalog-service/brands")
    || !catalogBrowseLoadTestText.includes("/catalog-service/product/search?q=phone&page=0&size=10")
    || !catalogBrowseLoadTestText.includes("/catalog-service/getBrands")) {
  fail("load-tests/catalog-browse.js: catalog browse load test must use current gateway catalog routes");
}
if (catalogBrowseLoadTestText.includes('PRODUCT_ID || "00000000-0000-0000-0000-000000000001"')
    || !catalogBrowseLoadTestText.includes("function createCatalogSeed(headers)")
    || !catalogBrowseLoadTestText.includes("/catalog-service/product/addProduct")
    || !catalogBrowseLoadTestText.includes("hasVariant: false")) {
  fail("load-tests/catalog-browse.js: catalog browse load test must self-seed product data when PRODUCT_ID is absent");
}

const checkoutFlowLoadTestText = loadTestTexts[1][1];
if (checkoutFlowLoadTestText.includes("https://")
    || checkoutFlowLoadTestText.includes("jslib.k6.io")) {
  fail("load-tests/checkout-flow.js: k6 scripts must not require remote runtime imports");
}
if (checkoutFlowLoadTestText.includes('PRODUCT_ID || "00000000-0000-0000-0000-000000000001"')
    || checkoutFlowLoadTestText.includes("let addressId = data.addressId")
    || !checkoutFlowLoadTestText.includes('import { check, fail, group, sleep } from "k6"')
    || !checkoutFlowLoadTestText.includes("function requireSeedData(resp, label)")
    || !checkoutFlowLoadTestText.includes("function createCatalogSeed(headers)")
    || !checkoutFlowLoadTestText.includes("Unable to seed checkout inventory for load test")
    || !checkoutFlowLoadTestText.includes("Checkout load test requires PRODUCT_ID and VARIANT_ID")
    || !checkoutFlowLoadTestText.includes('Unknown SCENARIO "${SCENARIO}"')
    || !checkoutFlowLoadTestText.includes("/inventory-service/stock")
    || !checkoutFlowLoadTestText.includes("quantity: 100000")) {
  fail("load-tests/checkout-flow.js: checkout load test must self-seed product inventory and create per-user addresses");
}
if (!loadTestsReadmeText.includes("Optional: use existing catalog data")
    || !loadTestsReadmeText.includes("does not depend on downloading remote JavaScript modules")
    || !loadTestsReadmeText.includes("Load tests default to the API Gateway at `http://localhost:8081`")
    || !loadTestsReadmeText.includes("standard gateway prefixes such as `/catalog-service/**` and `/cart-service/**`")
    || !loadTestsReadmeText.includes("k6 run --env SCENARIO=smoke checkout-flow.js")
    || !loadTestsReadmeText.includes("k6 run --env SCENARIO=load checkout-flow.js")
    || !loadTestsReadmeText.includes("k6 run --env SCENARIO=stress checkout-flow.js")
    || !implementationText.includes("Load tests default to the API Gateway at `http://localhost:8081`")
    || !implementationText.includes("standard gateway prefixes, including `/cart-service/**`")
    || !implementationText.includes("k6 run --env SCENARIO=smoke checkout-flow.js")
    || !implementationText.includes("k6 run --env SCENARIO=load checkout-flow.js")
    || !implementationText.includes("k6 run --env SCENARIO=stress checkout-flow.js")) {
  fail("load-test docs must document setup-time seeding and no-PRODUCT_ID checkout runs");
}
if (!loadTestsReadmeText.includes("k6 run catalog-browse.js")
    || !implementationText.includes("k6 run catalog-browse.js")) {
  fail("load-test docs must document no-PRODUCT_ID catalog browse runs");
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

const inventoryFallbackTestText = fs.readFileSync(
  path.join(root, "order-service", "src", "test", "java", "com", "sanjeevsky", "orderservice", "clients", "fallback", "InventoryFeignClientFallbackTest.java"),
  "utf8"
);
if (!inventoryFallbackTestText.includes("getStockByProduct_returnsNullToAllowKafkaReservationFallback")
    || !inventoryFallbackTestText.includes("isNull()")) {
  fail("order-service: InventoryFeignClient fallback must document non-blocking inventory pre-check behavior");
}

const couponFallbackTestText = fs.readFileSync(
  path.join(root, "order-service", "src", "test", "java", "com", "sanjeevsky", "orderservice", "clients", "fallback", "CouponFeignClientFallbackTest.java"),
  "utf8"
);
if (!couponFallbackTestText.includes("validateCoupon_returnsInvalidResultWithoutDiscount")
    || !couponFallbackTestText.includes("applyCoupon_noopsWhenCouponServiceUnavailable")) {
  fail("order-service: CouponFeignClient fallback must document non-fatal coupon validation/apply behavior");
}

for (const service of Object.keys(expectedApplicationNames)) {
  for (const controllerFile of javaMainFiles(service)
    .filter((file) => file.includes(`${path.sep}controller${path.sep}`)
      && /Controller\.java$/.test(path.basename(file)))) {
    const expectedTestFile = controllerFile
      .replace(`${path.sep}src${path.sep}main${path.sep}java${path.sep}`, `${path.sep}src${path.sep}test${path.sep}java${path.sep}`)
      .replace(/\.java$/, "Test.java");
    if (!fs.existsSync(expectedTestFile)) {
      fail(`${path.relative(root, controllerFile)}: missing focused controller test ${path.relative(root, expectedTestFile)}`);
    }
  }
}

for (const service of Object.keys(expectedApplicationNames)) {
  for (const publisherFile of javaMainFiles(service)
    .filter((file) => /Publisher\.java$/.test(path.basename(file)))) {
    const expectedTestFile = publisherFile
      .replace(`${path.sep}src${path.sep}main${path.sep}java${path.sep}`, `${path.sep}src${path.sep}test${path.sep}java${path.sep}`)
      .replace(/\.java$/, "Test.java");
    if (!fs.existsSync(expectedTestFile)) {
      fail(`${path.relative(root, publisherFile)}: missing focused publisher test ${path.relative(root, expectedTestFile)}`);
    }
  }
}

for (const service of Object.keys(expectedApplicationNames)) {
  for (const fallbackFile of javaMainFiles(service)
    .filter((file) => file.includes(`${path.sep}clients${path.sep}fallback${path.sep}`)
      && /Fallback\.java$/.test(path.basename(file)))) {
    const expectedTestFile = fallbackFile
      .replace(`${path.sep}src${path.sep}main${path.sep}java${path.sep}`, `${path.sep}src${path.sep}test${path.sep}java${path.sep}`)
      .replace(/\.java$/, "Test.java");
    if (!fs.existsSync(expectedTestFile)) {
      fail(`${path.relative(root, fallbackFile)}: missing focused fallback test ${path.relative(root, expectedTestFile)}`);
    }
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
const ciWorkflowText = fs.readFileSync(path.join(root, ".github", "workflows", "ci.yml"), "utf8");
const platformCommonsPomText = fs.readFileSync(path.join(root, "platform-commons", "pom.xml"), "utf8");
const expectedServiceModules = Object.keys(expectedApplicationNames);
const expectedRequiredEurekaApps = Object.entries(expectedApplicationNames)
  .filter(([service]) => !["service-discovery", "spring-server"].includes(service))
  .map(([, applicationName]) => applicationName.toUpperCase());
requireMavenTestFlags("scripts/verify-local.sh", verifyLocalText);
requireMavenTestFlags(".github/workflows/ci.yml", ciWorkflowText);
if (!ciWorkflowText.includes("static-validation:")
    || !ciWorkflowText.includes("name: Static validation")
    || ciWorkflowText.includes("Postman static validation")) {
  fail(".github/workflows/ci.yml: CI validation job must be named Static validation");
}
if (!ciWorkflowText.includes("python3 generate-arch.py --check")) {
  fail(".github/workflows/ci.yml: CI must validate generated architecture.html");
}
if (!ciWorkflowText.includes("bash -n scripts/verify-local.sh scripts/build-docker-jars.sh e2e-smoke-test.sh")) {
  fail(".github/workflows/ci.yml: CI must validate shell script syntax");
}
if (!ciWorkflowText.includes("docker compose config --quiet")) {
  fail(".github/workflows/ci.yml: CI must validate Docker Compose config");
}
if (!ciWorkflowText.includes("docker compose --profile platform-tools config --quiet")) {
  fail(".github/workflows/ci.yml: CI must validate optional platform-tools Compose profile");
}
if (!ciWorkflowText.includes("mvn -B -f platform-commons/pom.xml clean install -DskipTests")
    || !ciWorkflowText.includes('mvn -B -f "$MODULE/pom.xml" clean test')) {
  fail(".github/workflows/ci.yml: CI Maven commands must run clean to avoid stale deleted classes");
}
if (!readmeText.includes("bash -n scripts/verify-local.sh scripts/build-docker-jars.sh e2e-smoke-test.sh")
    || !readmeText.includes("python3 generate-arch.py --check")
    || !readmeText.includes("docker compose config --quiet")
    || !readmeText.includes("docker compose --profile platform-tools config --quiet")) {
  fail("README.md: static verification docs must include generated architecture, shell syntax, and default/profile Docker Compose config checks");
}
if (!readmeText.includes("mvn -B -f auth-server/pom.xml clean test")) {
  fail("README.md: targeted Java test example must run clean test to avoid stale deleted classes");
}
if (!readmeText.includes("The Maven phase runs `clean test`")
    || !implementationText.includes("runs Maven module checks with `clean test`")) {
  fail("README.md and implementation.md must document that Maven verification uses clean test before Docker jar rebuilds");
}
if (!verifyLocalText.includes("mvn -B -f platform-commons/pom.xml clean install -DskipTests")
    || !verifyLocalText.includes('mvn -B -f "$module/pom.xml" clean test')) {
  fail("scripts/verify-local.sh: Maven verification must run clean to avoid stale deleted classes");
}
if (!buildDockerJarsText.includes("mvn -B -f platform-commons/pom.xml clean install -DskipTests")
    || !buildDockerJarsText.includes('mvn -B -f "$module/pom.xml" clean package -DskipTests')) {
  fail("scripts/build-docker-jars.sh: Docker jar builds must run clean package to avoid stale deleted classes");
}
if (!platformCommonsPomText.includes("<project.build.sourceEncoding>UTF-8</project.build.sourceEncoding>")) {
  fail("platform-commons/pom.xml: shared module must set UTF-8 source encoding for reproducible builds");
}
requireSameOrderedValues(
  "Maven test module",
  expectedServiceModules,
  shellArrayValues(verifyLocalText, "MAVEN_TEST_MODULES"),
  "scripts/verify-local.sh"
);
requireSameOrderedValues(
  "Maven test module",
  expectedServiceModules,
  githubActionsMatrixValues(ciWorkflowText, "module"),
  ".github/workflows/ci.yml"
);
requireSameValues(
  "Docker build module",
  expectedServiceModules,
  shellArrayValues(buildDockerJarsText, "DEFAULT_MODULES", "scripts/build-docker-jars.sh"),
  "scripts/build-docker-jars.sh"
);
requireSameValues(
  "required Eureka app",
  expectedRequiredEurekaApps,
  shellArrayValues(verifyLocalText, "REQUIRED_EUREKA_APPS"),
  "scripts/verify-local.sh"
);

if (!verifyLocalText.includes("MAVEN_JAVA_HOME")
    || !buildDockerJarsText.includes("MAVEN_JAVA_HOME")
    || !verifyLocalText.includes('export JAVA_HOME="$preferred_java_home"')
    || !buildDockerJarsText.includes('export JAVA_HOME="$preferred_java_home"')) {
  fail("local Maven scripts must prefer Java 11 through MAVEN_JAVA_HOME for Lombok-compatible builds");
}
if (!buildDockerJarsText.includes('jar_matches=("$module"/target/*.jar)')
    || !buildDockerJarsText.includes("Expected exactly one packaged jar")) {
  fail("scripts/build-docker-jars.sh: Docker jar build must verify each module produces exactly one target/*.jar");
}

if (!verifyLocalText.includes("RUN_DIRECT_HEALTH_CHECKS")
    || !verifyLocalText.includes("SERVICE_HEALTH_CHECKS")
    || !verifyLocalText.includes("CLOUD_CONFIG_PORT")
    || !verifyLocalText.includes("INVENTORY_SERVICE_PORT")) {
  fail("scripts/verify-local.sh: local smoke verifier must wait for direct service actuator health before Postman runs");
}
if (!verifyLocalText.includes("RUN_PLATFORM_ENDPOINT_CHECKS")
    || !verifyLocalText.includes("PLATFORM_ENDPOINT_CHECKS")
    || !verifyLocalText.includes("Waiting for platform endpoints")) {
  fail("scripts/verify-local.sh: local smoke verifier must wait for platform endpoints before Postman runs");
}
for (const requiredPlatformEndpointMarker of [
  "${KAFKA_UI_PORT:-8080}",
  "${ZIPKIN_PORT:-9411}/health",
  "${PROMETHEUS_PORT:-9090}/-/healthy",
  "${GRAFANA_PORT:-3000}/api/health",
]) {
  if (!verifyLocalText.includes(requiredPlatformEndpointMarker)) {
    fail(`scripts/verify-local.sh: missing platform endpoint check marker ${requiredPlatformEndpointMarker}`);
  }
}
if (!readmeText.includes("Kafka UI, Zipkin, Prometheus, and Grafana endpoints")
    || !implementationText.includes("Smoke verification waits for Kafka UI, Zipkin, Prometheus, and Grafana endpoints")) {
  fail("README.md and implementation.md must document smoke platform endpoint checks");
}
if (!readmeText.includes("RUN_PLATFORM_ENDPOINT_CHECKS=0 scripts/verify-local.sh # skip Kafka UI, Zipkin, Prometheus, and Grafana endpoint checks")) {
  fail("README.md: RUN_PLATFORM_ENDPOINT_CHECKS docs must list every platform endpoint family");
}
for (const requiredHealthCheckMarker of [
  "$BASE_URL/actuator/health",
  "${CLOUD_CONFIG_PORT:-8071}",
  "${AUTH_SERVICE_PORT:-8083}",
  "${CUSTOMER_SERVICE_PORT:-8082}",
  "${CATALOG_SERVICE_PORT:-8084}",
  "${SHOPPING_CART_SERVICE_PORT:-8086}",
  "${PAYMENT_SERVICE_PORT:-8085}",
  "${INVENTORY_SERVICE_PORT:-8088}",
  "${ORDER_SERVICE_PORT:-8092}",
  "${NOTIFICATION_SERVICE_PORT:-8087}",
  "${COUPON_SERVICE_PORT:-8089}",
  "${WISHLIST_SERVICE_PORT:-8091}",
  "${REVIEW_SERVICE_PORT:-8090}",
]) {
  if (!verifyLocalText.includes(requiredHealthCheckMarker)) {
    fail(`scripts/verify-local.sh: missing direct health check marker ${requiredHealthCheckMarker}`);
  }
}

if (!verifyLocalText.includes("CONFIGSERVER")) {
  fail("scripts/verify-local.sh: local smoke verifier must wait for cloud-config Eureka registration");
}
if (!verifyLocalText.includes("wait_for_eureka_registry_clean")
    || !verifyLocalText.includes("Eureka aggregate registry did not converge")
    || !verifyLocalText.includes("non-UP instances still visible")) {
  fail("scripts/verify-local.sh: local smoke verifier must validate aggregate Eureka registry convergence");
}

if (!verifyLocalText.includes("GATEWAY_ROUTE_CHECKS")
    || !verifyLocalText.includes("wait_for_gateway_route")
    || !verifyLocalText.includes("/catalog-service/product/list")) {
  fail("scripts/verify-local.sh: local smoke verifier must wait for gateway route readiness before Postman runs");
}
if (!verifyLocalText.includes("GATEWAY_ROUTE_TABLE_CHECKS")
    || !verifyLocalText.includes("verify_gateway_route_table")
    || !verifyLocalText.includes("/actuator/gateway/routes")
    || !verifyLocalText.includes("raw shopping-cart-service route is exposed")) {
  fail("scripts/verify-local.sh: local smoke verifier must inspect live gateway route table and reject raw service-id routes");
}
if (!readmeText.includes("gateway auth-guard checks across all standard service prefixes")
    || !implementationText.includes("gateway auth-guard checks across all standard service prefixes")
    || !readmeText.includes("aggregate Eureka registry")
    || !implementationText.includes("aggregate Eureka registrations")
    || !readmeText.includes("live gateway route-table")
    || !implementationText.includes("live gateway route table")) {
  fail("README.md and implementation.md must document smoke Eureka, gateway route-table, and auth-guard checks");
}

if (!verifyLocalText.includes("RUN_API_COLLECTION")
    || !verifyLocalText.includes("Ecommerce-API.postman_collection.json")) {
  fail("scripts/verify-local.sh: local smoke verifier must include the API Postman collection");
}
if (!verifyLocalText.includes("RUN_DATA_SEED_COLLECTION")
    || !verifyLocalText.includes("Ecommerce-DataSeed.postman_collection.json")) {
  fail("scripts/verify-local.sh: local smoke verifier must allow toggling the DataSeed Postman collection");
}
if (!verifyLocalText.includes("RUN_E2E_COLLECTION")
    || !verifyLocalText.includes("Ecommerce-E2E-Complete.postman_collection.json")) {
  fail("scripts/verify-local.sh: local smoke verifier must allow toggling the E2E Postman collection");
}
if (!verifyLocalText.includes("RUN_ANY_POSTMAN_COLLECTION")
    || !verifyLocalText.includes('if [[ "$RUN_ANY_POSTMAN_COLLECTION" == "1" ]]')
    || !verifyLocalText.includes("require_command newman")) {
  fail("scripts/verify-local.sh: readiness-only smoke must not require newman when all collections are disabled");
}
if (!readmeText.includes("RUN_DATA_SEED_COLLECTION=0 scripts/verify-local.sh")
    || !readmeText.includes("RUN_E2E_COLLECTION=0 scripts/verify-local.sh")) {
  fail("README.md: verification options must document DataSeed and E2E collection toggles");
}

if (!verifyLocalText.includes("verify_gateway_standard_routes")
    || !verifyLocalText.includes("GATEWAY_AUTH_GUARD_CHECKS")
    || !verifyLocalText.includes("RAW_GATEWAY_ROUTE_CHECKS")
    || !verifyLocalText.includes("expect_http_status_check")
    || !verifyLocalText.includes("/shopping-cart-service/cart")) {
  fail("scripts/verify-local.sh: local smoke verifier must assert standard gateway routes and reject raw service-id routes");
}
for (const expectedGatewayAuthGuardMarker of [
  "/auth-service/updatePassword",
  "/catalog-service/getBrand/",
  "/cart-service/cart",
  "/customer-service/address",
  "/payment-service/initiate",
  "/inventory-service/stock",
  "/notification-service/notifications",
  "/order-service/order",
  "/coupon-service/coupon",
  "/review-service/review",
  "/wishlist-service/wishlist",
]) {
  if (!verifyLocalText.includes(expectedGatewayAuthGuardMarker)) {
    fail(`scripts/verify-local.sh: missing gateway auth guard route check ${expectedGatewayAuthGuardMarker}`);
  }
}

if (!verifyLocalText.includes("print_url_diagnostics")
    || !verifyLocalText.includes("print_eureka_registry_snapshot")
    || !verifyLocalText.includes("Eureka registry snapshot")) {
  fail("scripts/verify-local.sh: local smoke verifier must print HTTP and Eureka diagnostics when readiness checks fail");
}
if (!verifyLocalText.includes('actual="$(curl -sS -o /dev/null -w "%{http_code}" "$url" || true)"')
    || !verifyLocalText.includes('print_url_diagnostics "$name" "$url"')) {
  fail("scripts/verify-local.sh: HTTP status assertions must print diagnostics instead of exiting on curl failure");
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
