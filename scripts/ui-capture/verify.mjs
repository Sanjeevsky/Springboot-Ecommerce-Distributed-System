// Trove — UI capture verification / poster stills.
//
// Screenshots the key routes (same auth injection as record.mjs) so you can confirm pages
// render real content before recording, and to use as thumbnails / scene cards.
//
// Run:  npm run verify   →  ./output/shots/*.png  (gitignored)

import { chromium } from 'playwright';
import fs from 'fs';
import path from 'path';
import { fileURLToPath } from 'url';

const HERE = path.dirname(fileURLToPath(import.meta.url));
const BASE = process.env.FRONTEND_URL || 'http://localhost:5173';
const GATEWAY = process.env.GATEWAY_URL || 'http://localhost:8081';
const ADMIN_EMAIL = process.env.ADMIN_EMAIL || 'admin@trove.local';
const ADMIN_PASSWORD = process.env.ADMIN_PASSWORD || 'admin123';
const OUT = path.join(HERE, 'output', 'shots');
const VIEWPORT = { width: 1440, height: 900 };
const sleep = (ms) => new Promise((r) => setTimeout(r, ms));

const login = await fetch(`${GATEWAY}/auth-service/login`, {
  method: 'POST',
  headers: { 'Content-Type': 'application/json' },
  body: JSON.stringify({ email: ADMIN_EMAIL, password: ADMIN_PASSWORD }),
});
if (!login.ok) throw new Error(`login failed (${login.status}) — is the stack up?`);
const d = (await login.json()).data;

const products = await fetch(`${GATEWAY}/catalog-service/product/list`);
const productId = process.env.PRODUCT_ID || (await products.json())?.data?.content?.[0]?.id;

fs.rmSync(OUT, { recursive: true, force: true });
fs.mkdirSync(OUT, { recursive: true });

const browser = await chromium.launch();
const ctx = await browser.newContext({ viewport: VIEWPORT });
await ctx.addInitScript(
  ([t, u]) => {
    localStorage.setItem('trove_token', t);
    localStorage.setItem('trove_user', u);
  },
  [d.token, JSON.stringify({ email: d.email, role: d.role || 'ADMIN' })]
);
const page = await ctx.newPage();

const routes = [
  ['home', '/'],
  ['product', `/p/${productId}`],
  ['search', '/search?q=adidas'],
  ['account-orders', '/account/orders'],
  ['studio-products', '/studio/products'],
  ['studio-inventory', '/studio/inventory'],
];
for (const [name, r] of routes) {
  await page.goto(BASE + r, { waitUntil: 'domcontentloaded', timeout: 20000 }).catch(() => {});
  await sleep(1800);
  await page.screenshot({ path: path.join(OUT, `${name}.png`) });
  console.log('✔', name, '->', r);
}
await browser.close();
console.log('DONE →', path.relative(HERE, OUT));
