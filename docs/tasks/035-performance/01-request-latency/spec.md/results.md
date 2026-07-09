# Results: Request-Latency CORS Preflight Fix

Session: 2026-06-20
Spec: ./requirements.md
Learning report: ../../../learning/request-latency-cors-preflight-investigation.md

## What Was Done
- `ApiClient.fetch` now sets request `Content-Type: application/json` only for non-GET/HEAD requests with a JSON body (skips `FormData`, preserves a caller-supplied value). GET/HEAD are CORS-simple → no preflight.
- Added `<link rel="preconnect" href="%VITE_API_BASE_URL%" crossorigin />` to `index.html`.
- Added 5 behavioral regression-guard tests to `api.test.ts`.

## Files Changed
- `frontend/src/lib/api.ts` — conditional request `Content-Type`
- `frontend/index.html` — API `preconnect` hint
- `frontend/src/lib/__tests__/api.test.ts` — +5 regression tests
- `docs/tasks/035-performance/01-request-latency/spec.md/requirements.md` — spec
- `docs/learning/request-latency-cors-preflight-investigation.md` — learning report

## Verification
- Typecheck (`tsc -b`): pass
- Tests (`vitest run`): 6024 passed, 1 skipped (110 files); `api.test.ts` 19 passed (14 existing + 5 new)
- Build (`yarn build`): pass; `%VITE_API_BASE_URL%` substitutes correctly (`http://localhost` locally; CF dashboard var → `https://api.dante-planner.com` in prod)
- Manual (deferred, staging/prod): a cold logged-in load should show no `OPTIONS` before GETs and the ~1.8s "Queueing" collapse. Metric confirmation is staging-only by decision — it cannot run in jsdom.

## Issues & Resolutions
- LSP flagged `global` on `api.test.ts:28` (2304) → pre-existing false positive; FE test files are excluded from tsconfig, so harness LSP can't resolve Node globals there. `tsc -b` + vitest both pass.

## Learnings
- Test the *cause*, not the *symptom*: the suite proves "GETs carry no preflight-triggering header," which is deterministic; the latency itself is unmeasurable in-suite and is a staging check.
- A short "what already exists" research pass before writing the spec would have pre-empted two corrections (below).

## Spec Divergence

### What Changed
- Test file: the spec first said CREATE `api.test.ts`; research found it already exists (~6.7KB) → MODIFY. Spec was corrected before implementation.

### What Was Added (Not in Spec)
- None beyond the spec.

### What Was Dropped
- nginx `OPTIONS` handling, V023 `(published, upvotes)` index, backend-jitter instrumentation, region/HTTP3, staging perf-metric harness — all explicit out-of-scope follow-ups, intentionally not implemented.

### Wrong Assumptions
- The spec's first draft framed the GET/HEAD scoping as a hard CSRF barrier. Research showed auth cookies are `SameSite=Lax` (`CookieUtils.java:46`) and Spring CSRF is disabled because of it (`SecurityConfig.java:66`) — so SameSite, not the preflight, is the CSRF control. Scoping to GET/HEAD is defense-in-depth, not the sole barrier. Corrected in the spec before implementing.

### Prompting Retrospective
- **CSRF/security model**: "What is the project's existing CSRF defense, and are the FE and API origins same-site or cross-site?"
  - Why this would have helped: it surfaces `SameSite=Lax` in brainstorm, before the spec overstates the preflight's security role.
- **Existing test/infra inventory**: "Does an `api.test.ts` and a `FormData` upload path already exist, and how is `VITE_API_BASE_URL` injected in prod?"
  - Why this would have helped: it sets CREATE-vs-MODIFY, the FormData scope, and the preconnect-href approach up front.

### Spec Process Takeaway
This spec systematically under-specified the *existing security and infrastructure context* (CSRF model, test files, env injection); a brief "what already exists" research pass before writing the spec pre-empts that class of correction.

## Next Steps
- Confirm in prod/staging: cold logged-in load → no `OPTIONS` before GETs, "Queueing" collapses, GET reuses the preconnected socket.
- Follow-ups (separate sessions): V023 index recreation; backend-jitter instrumentation; staging perf-metric harness.
