> **BEFORE COMMIT**: The implementing agent MUST censor this file before committing:
> 1. Remove all security rationale and bot-blocking strategy details
> 2. Keep only the Turnstile integration steps
> 3. Delete this notice itself after censoring

# Task: Phase 4 — Cloudflare Turnstile

## Decisions
- **Managed widget type** — invisible for most users, challenges when suspicious
- **Asset Worker validates Turnstile token** — image requests require valid Turnstile token in header. Without token → 403.
- **Referer + Turnstile double gate** — Asset Worker checks both Referer (must match main domain) and Turnstile token. Blocks curl, bots, and hotlinking.
- **SSR Worker does NOT require Turnstile** — SSR renders HTML server-side, no need for browser verification at that layer.

## Description

Add Cloudflare Turnstile invisible browser verification to the Asset Worker. Blocks automated tools (curl, Playwright, scrapers) from accessing images on `assets.dante-planner.com`.

1. Register Turnstile site in CF Dashboard
2. Add `@marsidev/react-turnstile` to frontend
3. Invisible widget in app layout — stores token in module-level variable
4. Asset Worker validates Turnstile token on image requests
5. Frontend includes token in image request headers

## Scope

Read:
- `workers/asset-server/src/index.ts` — Asset Worker (Phase 3)
- `frontend/src/components/common/ScrambledImage.tsx` — image loading
- `frontend/src/lib/assetPaths.ts` — cross-origin URL

## Target

Create:
- `frontend/src/lib/turnstile.ts` — token management

Modify:
- `workers/asset-server/src/index.ts` — add Turnstile token validation for `/images/*`
- `workers/asset-server/wrangler.toml` — add `TURNSTILE_SECRET` env var
- `frontend/src/components/common/ScrambledImage.tsx` — include Turnstile token header in image fetch
- Frontend app layout — mount Turnstile widget
- `frontend/package.json` — add `@marsidev/react-turnstile`

## Done When

- [ ] Turnstile invisible widget loads on page
- [ ] Asset Worker validates Turnstile token for image requests
- [ ] Missing/invalid token → 403
- [ ] Valid browser with token → images load normally
- [ ] `curl https://assets.dante-planner.com/images/...` → 403
- [ ] Site fully functional with Turnstile active

## Test Plan

### Manual
1. Open site → Network tab shows Turnstile challenge
2. ThemePack images load normally (token auto-included)
3. `curl -I https://assets.dante-planner.com/images/themePack/1001.webp` → 403
4. Disable Turnstile (ad blocker) → images fail → graceful fallback
5. Deploy to staging → verify
