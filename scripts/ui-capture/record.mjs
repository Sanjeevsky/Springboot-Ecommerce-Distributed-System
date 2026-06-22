// Trove — UI navigation B-roll recorder.
//
// Drives the running storefront with Playwright/Chromium and records smooth scroll
// tours of the shopper, account, and admin-Studio flows as .webm clips. Auth-gated
// routes are reached by logging in via the API and injecting the JWT into localStorage
// (the app reads `trove_token` / `trove_user`), so protected pages record real content
// instead of redirecting to /login.
//
// Prereqs: the backend stack up (gateway :8081) and the frontend dev server (:5173).
// Run:  npm run setup   (once)   then   npm run record
// Output: ./output/videos/*.webm  (gitignored)
//
// Config via env (all optional):
//   FRONTEND_URL (default http://localhost:5173)
//   GATEWAY_URL  (default http://localhost:8081)
//   ADMIN_EMAIL / ADMIN_PASSWORD (default admin@trove.local / admin123)
//   PRODUCT_ID   (default: first product from the catalog API)

import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.env.FRONTEND_URL || 'http://localhost:5173';
const GATEWAY = process.env.GATEWAY_URL || 'http://localhost:8081';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@trove.local';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123';
const OUT = path.join(HERE, 'output', 'videos');
const VIEWPORT = { width: 1440, height: 900 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

async function getAuth() {
  const res = await fetch(`${GATEWAY}/auth-service/login`, {
    method: 'POST',
    headers: { 'Content-Type': 'application/json' },
    body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
  });
  if (!res.ok) throw new Error(`login failed (${res.status}) — is the stack up?`);
  const d = (await res.json()).data || {};
  return { token: d.token, email: d.email, role: d.role || 'ADMIN' };
}

async function firstProductId() {
  if (process.env.PRODUCT_ID) return process.env.PRODUCT_ID;
  const res = await fetch(`${GATEWAY}/catalog-service/product/list`);
  const content = (await res.json())?.data?.content || [];
  if (!content.length) throw new Error('no products in catalog — seed one first');
  return content[0].id;
}

async function tour(page, route, scrolls = 4) {
  try {
    await page.goto(BASE + route, { waitUntil: 'domcontentloaded', timeout: 20000 });
  } catch (e) {
    console.log('   goto warn', route, e.message);
  }
  await sleep(1600);
  for (let i = 0; i < scrolls; i++) {
    await page.mouse.wheel(0, 520);
    await sleep(850);
  }
  await sleep(500);
  await page.evaluate(() => window.scrollTo({ top: 0, behavior: 'smooth' })).catch(() => {});
  await sleep(1100);
}

async function clip(browser, name, auth, steps) {
  const dir = path.join(OUT, name);
  fs.mkdirSync(dir, { recursive: true });
  const context = await browser.newContext({
    viewport: VIEWPORT,
    recordVideo: { dir, size: VIEWPORT },
  });
  await context.addInitScript(
    ([token, user]) => {
      localStorage.setItem('trove_token', token);
      localStorage.setItem('trove_user', user);
    },
    [auth.token, JSON.stringify({ email: auth.email, role: auth.role })]
  );
  const page = await context.newPage();
  console.log('▶', name);
  for (const s of steps) {
    console.log('   →', s.route);
    await tour(page, s.route, s.scrolls ?? 4);
  }
  await page.close();
  await context.close(); // finalizes the .webm
  const webm = fs.readdirSync(dir).filter((f) => f.endsWith('.webm'));
  if (webm.length) {
    const out = path.join(OUT, `${name}.webm`);
    fs.renameSync(path.join(dir, webm[0]), out);
    fs.rmSync(dir, { recursive: true, force: true });
    console.log(`   ✔ ${path.relative(HERE, out)} (${(fs.statSync(out).size / 1024).toFixed(0)} KB)`);
  } else {
    console.log('   ✗ no video produced for', name);
  }
}

const auth = await getAuth();
const productId = await firstProductId();
console.log('auth token?', !!auth.token, '| role', auth.role, '| product', productId);
fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();

await clip(browser, '01-storefront-browse', auth, [
  { route: '/', scrolls: 5 },
  { route: `/p/${productId}`, scrolls: 5 },
  { route: '/search?q=adidas', scrolls: 4 },
]);

await clip(browser, '02-account', auth, [
  { route: '/account/orders', scrolls: 3 },
  { route: '/account/wishlist', scrolls: 3 },
  { route: '/account/addresses', scrolls: 3 },
  { route: '/account/notifications', scrolls: 3 },
  { route: '/account/payments', scrolls: 3 },
]);

await clip(browser, '03-admin-studio', auth, [
  { route: '/studio/products', scrolls: 4 },
  { route: '/studio/products/new', scrolls: 4 },
  { route: '/studio/inventory', scrolls: 3 },
  { route: '/studio/coupons', scrolls: 3 },
  { route: '/studio/activity', scrolls: 3 },
]);

await browser.close();
console.log('DONE →', path.relative(HERE, OUT));
