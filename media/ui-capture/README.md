# UI capture — recorded B-roll

Recorded UI navigation clips of the Trove storefront and admin Studio, for use as B-roll in
the walkthrough video (see [`docs/video-script.md`](../../docs/video-script.md), Scenes 2–4).
1440×900 `.webm`, smooth scroll tours.

| Clip | Flow |
|------|------|
| `01-storefront-browse.webm` | home → product detail → search |
| `02-account.webm` | orders → wishlist → addresses → notifications → payments |
| `03-admin-studio.webm` | products → new product → inventory → coupons → activity |

These are generated artifacts (committed here for convenience). To regenerate against the live
app, see [`scripts/ui-capture/`](../../scripts/ui-capture/):

```bash
cd scripts/ui-capture && npm run setup && npm run record
# then copy scripts/ui-capture/output/videos/*.webm here
```
