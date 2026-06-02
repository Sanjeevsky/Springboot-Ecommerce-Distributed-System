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

function applicationNameValues(file) {
  return fs.readFileSync(file, "utf8")
    .split(/\r?\n/)
    .map((line) => line.trim())
    .filter((line) => line && !line.startsWith("#"))
    .map((line) => line.match(/^spring\.application\.name\s*=\s*(.+)$/))
    .filter(Boolean)
    .map((match) => match[1].trim());
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

if (failed) {
  process.exit(1);
}

console.log("Service config validation passed");
