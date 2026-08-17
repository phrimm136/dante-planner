> **BEFORE COMMIT**: The implementing agent MUST censor this file before committing:
> 1. Remove all descriptions of validation logic, header checks, blocking mechanisms
> 2. Remove security architecture rationale
> 3. Keep only generic descriptions (e.g., "images served from separate origin")
> 4. Delete this notice itself after censoring

# Task: Phase 3 — Asset Worker

## Decisions
- **Asset Worker serves images without CORS** — `/images/*` paths: no `Access-Control-Allow-Origin`. Canvas tainting blocks `toDataURL()`.
- **Referer/Origin validation** — Asset Worker only serves images when the `Referer` or `Origin` header matches `dante-planner.com`. Direct access (curl, browser address bar, other sites) returns 403. Prevents hotlinking and direct URL scraping.

## Description

Deploy a Cloudflare Worker at `assets.dante-planner.com` that serves images from R2 without CORS headers.

1. Create Worker project with R2 binding and env configs (prod/staging)
2. Serve R2 objects on `/images/*` — no `Access-Control-Allow-Origin` header
3. Validate `Referer`/`Origin` header — reject requests not from `dante-planner.com`
4. Return 404 for non-existent R2 keys
5. Add Asset Worker deploy step to CI

Depends on Phase 1 (R2 buckets) and Phase 2 (SSR Worker at main domain, so the asset Worker is a second Worker).

## Scope

Read:
- `workers/` — existing Worker structure (if any)
- `.github/workflows/deploy-frontend.yml` — current deploy pipeline

## Target

Create:
- `workers/asset-server/wrangler.toml` — Worker config with R2 binding, env configs (prod/staging)
- `workers/asset-server/src/index.ts` — serves R2 objects, no CORS on `/images/*`

Modify:
- `.github/workflows/deploy-frontend.yml` — add Asset Worker deploy step

## Done When

- [ ] Asset Worker deployed at `assets.dante-planner.com`, serves images without CORS
- [ ] Referer/Origin validation returns 403 for non-matching requests
- [ ] Non-existent image ID → Worker returns 404
- [ ] Dev mode: images served from same origin (no Worker needed)
- [ ] CI deploys Asset Worker alongside frontend

## Test Plan

### Manual
1. Deploy Asset Worker → `curl -I https://assets.dante-planner.com/images/themePack/1001.webp` → 403 (no Referer)
2. Same curl with `-H "Referer: https://dante-planner.com"` → 200, no CORS headers
3. `curl -I https://assets.dante-planner.com/images/nonexistent.webp -H "Referer: https://dante-planner.com"` → 404

### Edge Cases
- [ ] R2 unavailable → Worker returns 503
- [ ] Missing Referer AND Origin → 403
- [ ] Referer from staging domain → allowed in staging env
