# Task: Eliminate CORS-preflight latency on the authenticated cold-load request burst

## Decisions
Diagnosis was reached by layer-by-layer measurement (decompose-before-optimize). Each decision below is backed by a measurement, not a guess.

- **Root cause: the client sends `Content-Type: application/json` on every request, including bodyless GETs** (`frontend/src/lib/api.ts:127`). That header is not CORS-safelisted, so every cross-origin GET becomes a "non-simple" request and triggers a CORS preflight `OPTIONS`. On a cold trans-Pacific HTTP/2 connection the first preflight (`/auth/me`) takes ~1.4–2.1s and **blocks the entire authenticated GET burst** queued behind it on the shared connection — because evidence: nginx `upstream_response_time` ≈ 0.2s (backend is not the cost), netlog showed a 2151ms gap between `CORS_PREFLIGHT_URL_REQUEST` and `CORS_PREFLIGHT_RESULT`, and a direct `OPTIONS` curl measured `total=1.409s` (`tls=0.694s`).
- **Primary fix: omit the request `Content-Type` for GET/HEAD only** — because a bodyless GET has no payload to describe; removing it makes the request "simple" → no preflight. Scoped to **GET/HEAD specifically (not "when no body")** as minimalism + defense-in-depth: it avoids needlessly changing write-request semantics. (CSRF on writes is already covered by `SameSite=Lax` auth cookies — `CookieUtils.java:46`, with Spring CSRF disabled in `SecurityConfig.java:66` for that reason — so this scoping is not the sole CSRF barrier; see Resolved Ambiguities #4.)
- **Secondary fix: `preconnect` to the API origin in `index.html`** — because even with the preflight gone, the first cold GET still pays ~0.7s DNS+TCP+TLS; warming the connection during HTML parse removes that from the critical path.
- **Keep `Content-Type: application/json` on POST/PUT/PATCH that carry a JSON body** (they preflight, correctly) and **never force `application/json` on a `FormData` body** — because writes need the header and the preflight is a legitimate CORS/CSRF gate, while multipart uploads must set their own boundary.
- **`access-control-max-age: 3600` is already set server-side → no action.** It becomes moot for GETs once they no longer preflight.
- **Test strategy splits by type:** the **behavioral** regression guard (assert GET/HEAD send no preflight-triggering header) ships **with this fix** — it is deterministic and runs in the existing Vitest suite. **Metric** regression tests (TTFB / LCP / bundle-size thresholds) require a **staging environment** and a stored baseline; they cannot run meaningfully in jsdom/local — because the latency lives in network/CORS/cold-connection machinery absent from unit tests, and prod numbers are too noisy for a local threshold. Deferred to a separate effort (see Out of Scope).
- **Out of scope (documented follow-ups, see section below):** nginx `OPTIONS` short-circuit, V023 `(published, upvotes)` index recreation, backend response-time jitter (300ms–1s) instrumentation, region / HTTP/3, and a staging-based performance-metric regression harness. The duplicate-GET (retry) issue was diagnosed separately and is already resolved.

## Description
The published-plan list feels slow on a cold, logged-in production load. The felt latency is a CORS preflight blocking the authenticated GET burst — not the database, JS bundle, or raw network distance.

1. In `ApiClient.fetch` (`frontend/src/lib/api.ts`), set the request `Content-Type: application/json` header **only when the request sends a JSON body**, and **never for GET/HEAD**. GET/HEAD must carry only CORS-safelisted headers so they are "simple" cross-origin requests and skip the preflight.
2. Do not force `Content-Type` when the body is `FormData` (let the browser set the multipart boundary).
3. Preserve a caller's explicit `Content-Type` passed via `options.headers`.
4. Add a `preconnect` resource hint to the API origin in `frontend/index.html`, using Vite's `%VITE_API_BASE_URL%` HTML substitution to avoid hardcoding the origin.
5. Audit complete (Resolved Ambiguities #1): no GET carries a non-simple custom header today, so removing `Content-Type` is sufficient. Keep this in mind if new GET call sites add headers later.

WHAT, not HOW: the contract is "GET/HEAD produce no preflight; writes keep theirs; uploads still work."

## Scope
Files/folders to READ for context:
- `frontend/src/lib/api.ts` — the central `ApiClient.fetch` wrapper and `get/post/put/patch/delete` convenience methods (lines 121–240).
- `frontend/src/lib/env.ts` — `VITE_API_BASE_URL` resolution (default `http://localhost:8080`).
- `frontend/index.html` — `<head>` (lines 3–36); no resource hints today.
- `frontend/vite.config.ts` — confirm `%VITE_*%` HTML env substitution is available and the dev proxy config.
- Callers of `ApiClient.get(...)` that pass a second `options.headers` argument (audit for non-simple headers).

## Target
Files to CREATE or MODIFY:
- **MODIFY** `frontend/src/lib/api.ts` — conditional request `Content-Type`.
- **MODIFY** `frontend/index.html` — add `<link rel="preconnect" href="%VITE_API_BASE_URL%" crossorigin />` in `<head>`.
- **MODIFY (extend)** `frontend/src/lib/__tests__/api.test.ts` — already exists (~6.7KB); add the header-behavior cases below.

## Impact Analysis
- **Files being modified:**
  - `frontend/src/lib/api.ts` — HIGH blast radius (single central client; every backend request flows through it), but the change is a narrow, additive condition on one header.
  - `frontend/index.html` — LOW (additive hint; no behavior change if it fails).
- **Dependencies:** every data hook (`useAuthQuery`, `useUserSettings`, `useMDGesellschaftData`, `usePublishedPlannerQuery`, notifications, moderation, comment mutations) calls through `ApiClient`. None inspect request headers, so the change is transparent to them.
- **Ripple effects:**
  - If the condition wrongly strips `Content-Type` from a JSON write → backend returns 415/400 and the write fails.
  - If any upload path sends `FormData`, the *current* unconditional `application/json` already mis-labels it; the fix should also correct that — verify upload call sites.
  - `preconnect` with `crossorigin` must match the credentialed CORS connection so the warmed socket is actually reused; verify in netlog that the first GET reuses the preconnected connection.

## Risk Assessment
- **Edge cases:**
  - Bodyless POST/PUT/PATCH (upvote, bookmark, subscribe, publish-toggle) — must KEEP current behavior (still preflight); must NOT become "simple."
  - `FormData` / multipart bodies — must not be forced to `application/json`.
  - Caller-supplied `Content-Type` via `options.headers` — must be respected (override preserved).
  - A GET that carries a non-simple custom header (`Cache-Control`, `X-Device-ID`) would still preflight even after this fix — audit required.
  - `%VITE_API_BASE_URL%` empty in dev → `href=""` (harmless no-op); confirm it doesn't error the build.
- **Performance:** target — cold-load GET burst from ~1.8–2.1s down to roughly connection + server time (~0.3–0.5s warm).
- **Security:** the central constraint — **do not remove the preflight from any state-changing request.** Scope the omission to GET/HEAD. POST/PUT/PATCH keep `Content-Type` (and their preflight); DELETE is non-simple by method and preflights regardless.

## Boundaries & Invariants
- **Trust/ownership boundary:** the browser origin `https://dante-planner.com` ↔ the API origin `https://api.dante-planner.com`. These are **cross-origin but same-site** (shared registrable domain `dante-planner.com`): CORS treats them as different origins (so the preflight fires), while `SameSite` cookies flow between them (so auth works). The HttpOnly, `SameSite=Lax` auth cookie is the credential that crosses this boundary; a cross-*site* attacker (`evil.com`) is blocked at the cookie layer, not the preflight.
- **Invariant 1:** A GET or HEAD request carries only CORS-safelisted headers (no request `Content-Type`, no custom headers) → it is a simple cross-origin request → it produces no preflight.
- **Invariant 2:** Any request that sends a JSON body includes `Content-Type: application/json`.
- **Invariant 3:** A `FormData`/multipart body never receives a forced `Content-Type` (the browser sets the boundary).
- **Invariant 4:** State-changing methods (POST/PUT/PATCH/DELETE) remain non-simple and continue to preflight — their cross-origin request semantics are unchanged by this task. (Primary CSRF protection is `SameSite=Lax`, not the preflight; keeping writes non-simple is defense-in-depth.)
- **Invariant 5:** Response parsing is unaffected — the server sets the response `Content-Type` independently of the request, and `response.json()` parses the response bytes.

## Failure Modes
> One row per invariant. "Trigger" is the exact condition that violates it; "Response" is the design that prevents it; "Test" proves the response.

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| Inv 1 (GET simple) | A GET still carries a non-simple header (CT not removed, or a caller adds `Cache-Control`/`X-Device-ID`) → preflight persists, latency unfixed | Omit CT for GET/HEAD; audit callers; assert GET headers ⊆ safelist | `GET sends no Content-Type / custom header` |
| Inv 2 (JSON body labeled) | Condition strips CT from a POST/PUT with a JSON body → backend 415/can't parse → write fails | Include CT whenever a JSON body is present | `post(data) / put(data) includes application/json` |
| Inv 3 (FormData) | FormData body forced to `application/json` → multipart boundary lost → upload corrupt | Skip CT when body is `FormData` | `FormData request carries no forced Content-Type` |
| Inv 4 (writes still gated) | Change keyed on body-presence makes a bodyless POST "simple" → forgeable cross-origin (CSRF) | Scope omission to GET/HEAD only; POST/PUT/PATCH/DELETE untouched | `bodyless POST still non-simple (keeps Content-Type)` |
| Inv 1/2 (retry/idempotency) | TanStack Query retries a failed request → headers recomputed | Header logic is pure & deterministic; no shared state → identical headers on every attempt | `same request computed twice yields identical headers` |
| Inv 1 (preconnect dependency) | The `preconnect` hint fails or is ignored | Graceful degradation — `fetch` establishes the connection normally; only the warm-up optimization is lost | manual: netlog shows GET reuses preconnected socket when present |

### Visualized Failure
> Worst row: Inv 2 (a write loses its `Content-Type`).

1. A developer scopes the change too broadly — e.g. strips `Content-Type` whenever `options.body` is falsy.
2. A `PUT` planner-sync with a JSON body hits a code path where the body is built after the header decision → `Content-Type` is dropped.
3. The backend receives `application/json` content with no `Content-Type` → Spring can't bind the body → `415 Unsupported Media Type` → the user's planner fails to save, silently retried.
→ Inv 2's test (`put(data)` includes `Content-Type`) catches this before merge. The condition must key on "a JSON body is present," computed at the same point the body is serialized.

> Note on the CSRF angle (previously the worst case): making a bodyless POST "simple" would *not* be exploitable here, because `SameSite=Lax` strips the auth cookie from any cross-*site* forged request (`evil.com`). The preflight is not the CSRF barrier. Scoping to GET/HEAD is still correct (minimalism + defense-in-depth), but the real correctness risk is Inv 2, above.

## Done When
- [ ] On a cold, logged-in production load, GET/HEAD requests to `api.dante-planner.com` produce **no `OPTIONS` preflight** (verified in the Network tab / `chrome://net-export`).
- [ ] The authenticated cold-load GET burst no longer shows ~1.8s "Queueing"; the published-list request's TTFB drops to roughly connection + server time.
- [ ] `preconnect` to the API origin is present in `index.html` and the first GET reuses the warmed connection (netlog).
- [ ] POST/PUT/PATCH with a JSON body still send `Content-Type: application/json`, and writes (publish, vote, comment) still succeed.
- [ ] `FormData` uploads (if any exist) still function.
- [ ] All existing tests pass.
- [ ] `yarn --cwd frontend tsc -b` is clean; no new lint errors.

## Test Plan
### Test Runner
- Framework: **Vitest** (frontend).
- Run command: `yarn --cwd frontend vitest run src/lib/__tests__/api.test.ts` (and full suite `yarn --cwd frontend vitest run` before completion). Redirect output to `/tmp/fe-test-<session>-api.log` per project convention.

### Tests to Write
> These behavioral assertions ARE the regression guard for this fix: if a future change re-adds an unconditional `Content-Type` (re-introducing the preflight), they go red. They run in the existing Vitest suite — no environment needed. (Metric/timing regression is staging-only; see Out of Scope.)

- [ ] GET sends no `Content-Type` and no custom header: `frontend/src/lib/__tests__/api.test.ts`
- [ ] HEAD sends no `Content-Type`: same file
- [ ] `post(data)` / `put(data)` / `patch(data)` with a body include `Content-Type: application/json`: same file
- [ ] Bodyless POST remains non-simple (still sends `Content-Type`) — guards Inv 4: same file
- [ ] `FormData` body carries no forced `application/json`: same file
- [ ] Caller-supplied `Content-Type` via `options.headers` is preserved: same file
- [ ] Deterministic headers across two identical calls (retry safety): same file
- [ ] Every `Test` cell in the Failure Modes table is realized here — no failure mode ships untested.

## Verification
### Manual
1. Build with the real API origin and serve: `VITE_API_BASE_URL=https://api.dante-planner.com yarn --cwd frontend build && yarn --cwd frontend preview`.
2. Authenticated, cold (fresh window / cleared cache) load of the published-list page in production; open DevTools → Network.
3. Confirm there is **no `OPTIONS`** before the GET requests to the API, and the list request's "Waiting (TTFB)" is in the hundreds of ms, not ~2s.
4. Capture `chrome://net-export`; confirm no `CORS_PREFLIGHT_*` events for the GETs and that the GET reuses the preconnected socket.

### Edge Cases
- [ ] Bodyless POST (upvote/bookmark): still shows an `OPTIONS` preflight (expected — write path unchanged).
- [ ] A write action (publish / comment) succeeds end-to-end with `Content-Type: application/json`.
- [ ] Image/asset upload via `FormData` (if present): succeeds with a browser-generated multipart boundary.

## Project Conventions (docs/spec.md)
- **Data-Driven Feature sections (Data Model Catalog / Normalization Layer / Rendering Modes / Reference Per Mode / Implementation Order): N/A.** This task modifies the API client and CORS request shape; it does not consume raw game-data files.

## Resolved Ambiguities
Researched against the codebase before implementation:

1. **Do GETs carry other non-simple headers?** No. `Content-Type` is the only one. No `Cache-Control` is sent anywhere in the FE; the device ID travels as a cookie (`usePlannerCommentsSse.ts:71`, backend `DeviceIdArgumentResolver`), not an `X-Device-ID` header; no caller passes custom headers to `ApiClient.get`. → Removing `Content-Type` for GET/HEAD is sufficient to eliminate the preflight.
2. **Does any upload send `FormData` through `ApiClient`?** No. There is no `new FormData()` in `frontend/src`; `handleImageUpload` (`tiptap-utils.ts:362`) is the tiptap demo stub. → Invariant 3 (FormData) is a **defensive guard** for future uploads, not a fix for current behavior.
3. **Is `VITE_API_BASE_URL` set at build for the `preconnect` href?** Committed `.env` sets `http://localhost`; prod's `https://api.dante-planner.com` is injected at Cloudflare Pages build (dashboard env var, external to repo — proven set since prod calls the real API). Vite gives shell/dashboard vars precedence over `.env`, so `%VITE_API_BASE_URL%` resolves to the real origin in prod and a harmless `http://localhost` no-op locally; the committed `.env` guarantees it is never empty. → The `%VITE_API_BASE_URL%` approach is sound; the only external dependency is the CF dashboard var (already present).
4. **CSRF posture / SameSite.** Auth cookies are `SameSite=Lax` (`CookieUtils.java:46`); Spring CSRF is disabled *because* of it (`SecurityConfig.java:66`); the device cookie is `SameSite=Strict`. The FE origin and API origin are cross-origin but **same-site** (registrable domain `dante-planner.com`), so Lax cookies flow between them while CORS still preflights. A cross-*site* attacker is blocked by Lax at the cookie layer, independent of the preflight. → GET/HEAD scoping is minimalism + defense-in-depth, not the sole CSRF barrier; the security framing in this spec was corrected accordingly.

## Out of Scope (documented follow-ups)
- **nginx `OPTIONS` short-circuit** — answer preflights with `204` + CORS headers at the proxy so the *unavoidable* POST/PUT preflights don't traverse to cold Spring. Off the cold-load path → low priority.
- **V023 index regression** — `V023__remove_downvotes.sql:14` dropped `idx_published_votes (published, upvotes, downvotes)` and no later migration recreated a `(published, upvotes)` index. Recreate it (new Flyway migration). Minor for baseline latency; relevant to backend *variance* under cold buffer pool.
- **Backend response-time jitter (300ms–1s)** — instrument and investigate; lead suspects are GC pauses (768MB heap on a 2GB t3.small) and the V023 cache-dependent scan. No Prometheus scraper exists; read `/actuator/metrics/http.server.requests` ad hoc and `jvm_gc_pause_seconds`, plus the MySQL slow log + `EXPLAIN`.
- **Structural / region** — `us-west-2` origin vs APAC users; consider `ap-northeast`, Cloudflare Argo, and HTTP/3 (already advertised via `alt-svc`). Large effort; separate epic.
- **Performance-metric regression harness (staging-only)** — a separate session. Metric guards cannot run in jsdom/local; they need a staging deploy + baseline. Candidate mechanisms, by leverage: **bundle-size budgets** (`size-limit` / CI chunk check; leverages the existing `rollup-plugin-visualizer`) to catch JS-critical-path regressions; a **Playwright assertion** against staging that the list page issues **zero `OPTIONS` preflights and ≤ N API requests** (the deterministic bridge closest to this fix); **Lighthouse CI** thresholds (LCP/TBT) on a preview URL; and the existing **k6** p95/p99 thresholds for the backend. Decide metrics, baselines, and flakiness budget there — do not bolt onto this fix.
