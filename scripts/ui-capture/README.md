# UI capture — storefront/Studio B-roll recorder

Records smooth scroll tours of the Trove UI as `.webm` clips, for use as B-roll in the
walkthrough video (see [`docs/video-script.md`](../../docs/video-script.md), Scenes 2–4).
It drives the **running** app with Playwright/Chromium — it does not build or mock anything.

## Prerequisites

1. Backend stack up — gateway reachable at `http://localhost:8081`:
   ```bash
   docker compose up -d        # from the repo root
   ```
2. Frontend dev server up at `http://localhost:5173`:
   ```bash
   cd frontend && npm run dev
   ```
3. A seeded catalog (the recorder uses the first product unless `PRODUCT_ID` is set) and the
   admin account (`admin@trove.local` / `admin123` by default).

## Run

```bash
cd scripts/ui-capture
npm run setup     # once: installs Playwright + Chromium (~150MB download)
npm run record    # writes ./output/videos/*.webm
npm run verify    # optional: ./output/shots/*.png poster stills / sanity check
```

Output (`output/`) and `node_modules/` are gitignored — the clips are build artifacts, not
source. The three clips produced:

| Clip | Flow |
|------|------|
| `01-storefront-browse.webm` | home → product detail → search |
| `02-account.webm` | orders → wishlist → addresses → notifications → payments |
| `03-admin-studio.webm` | products → new product → inventory → coupons → activity |

## How it reaches authenticated pages

`/account/*` and `/studio/*` are guarded by `RequireAuth` / `RequireAdmin`. The recorder logs
in via `POST /auth-service/login`, then injects the JWT into `localStorage`
(`trove_token` + `trove_user` with `role: ADMIN`) via Playwright `addInitScript` before each
navigation — so protected routes render real content instead of bouncing to `/login`.

## Config (env vars, all optional)

| Var | Default |
|-----|---------|
| `FRONTEND_URL` | `http://localhost:5173` |
| `GATEWAY_URL` | `http://localhost:8081` |
| `ADMIN_EMAIL` / `ADMIN_PASSWORD` | `admin@trove.local` / `admin123` |
| `PRODUCT_ID` | first product from the catalog API |

Tweak the routes/scroll counts in `record.mjs` (the `clip(...)` calls) to change the tour.
