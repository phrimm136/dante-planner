# Results: OAuth BFF Redesign

## What Was Done
Migrated Google OAuth from a client-side popup+PKCE flow to a server-side BFF redirect flow, as a clean cutover. Delivered in 4 phases plus a post-build debugging/hardening loop and two code reviews.

- **Phase 1 — Token claim minimization**: access `sub`=userId + `role`; refresh `sub`=userId + lineage claims; dropped the AES-GCM `enc` claim blob and all email-in-token (fixed the email-in-`sub` leak). Cipher logic extracted to `AesGcmCipher`.
- **Phase 2 — CSRF double-submit**: `CsrfDoubleSubmitFilter` (ensure readable `csrf` cookie + require matching `X-CSRF-Token` on unsafe methods), constant-time compare, `/api/internal/**` exempt. 134 existing test sites migrated via a shared `withCsrf()` helper.
- **Phase 3 — BFF endpoints + cutover**: `GET /api/auth/google/start` + `GET /api/auth/google/callback`; `OAuthStateService` seals `{state, codeVerifier, returnTo}` into an encrypted+signed 90s `oauth_tx` cookie; removed POST callback + DTO; `legacy-admit-enabled=false`; redirect-uri repointed to backend.
- **Phase 4 — Frontend migration**: collapsed 3 duplicated login buttons to one `startGoogleLogin()`; CSRF header in `api.ts`; deleted `oauth.ts`, the callback route, `useLogin`, `VITE_GOOGLE_CLIENT_ID`.
- **Post-build (debugging loop)**: login button absolute-URL fix; redirect-uri / CORS / `VITE_API_BASE_URL` aligned to the nginx port-80 origin; `X-CSRF-Token` added to CORS allow-headers (Spring + nginx); **`returnTo` feature** (return user to the page they started from, with an open-redirect guard).
- **Reviews**: initial 3-reviewer pass (REJECT→fixed: callback JSON-on-error, duplicate first-login trigger, etc.); new-code security review (ACCEPTABLE; CORS-trim fix + bypass-regression tests).

## Files Changed
**Backend (new):** `OAuthStateService`, `AesGcmCipher`, `CsrfDoubleSubmitFilter`, `FrontendProperties` + tests `OAuthStateServiceTest`, `AuthControllerBffTest`, `OAuthMitmResistanceTest`, `FrontendPropertiesTest`, `CsrfDoubleSubmitFilterTest`, `CsrfMockMvcSupport`.
**Backend (modified):** `AuthController`, `JwtTokenService`, `TokenClaims`, `TokenGenerator`, `AuthenticationFacade`, `JwtAuthenticationFilter`, `RefreshRotationService` (1-line), `GoogleOAuthProvider`/`OAuthProvider` (buildAuthorizationUrl), `CorsConfig`, `SecurityConfig`, `CookieUtils`, `CookieConstants`, `application*.properties`. **Deleted:** `OAuthCallbackRequest`.
**Frontend (new):** `useGoogleLogin.ts` + test. **Modified:** `Header.tsx`, `UsernameSection`, `AccountDeleteSection`, `api.ts`, `useAuthQuery.ts`, `env.ts`, `router.tsx` + tests. **Deleted:** `lib/oauth.ts`, `routes/auth/callback/google.tsx`.
**Infra:** `nginx/locations.conf`, `nginx/nginx.dev.conf` (CORS allow-headers), `docker-compose.yml` (redirect-uri default).

Net: 621 insertions / 1251 deletions.

## Verification
- **Build**: backend `./gradlew test` PASS (full suite); frontend `vite build` PASS.
- **Tests**: backend full suite green (incl. `OAuthStateServiceTest`, `AuthControllerBffTest`, `OAuthMitmResistanceTest`, `FrontendPropertiesTest`, `CsrfDoubleSubmitFilterTest`); frontend vitest 6109 pass / 1 skip; `tsc -b` clean.
- **Manual (by user)**: login + logout work end-to-end after config fixes; `returnTo` round-trips to the start page.

## Issues & Resolutions
- **Content-filter false-positive killed the Phase-3 code-writer agent** before it wrote tests → recovered by inspecting the working tree directly (`git status` + read source) and writing the missing tests by hand.
- **Login button 404** → `startGoogleLogin` used a relative `/api/...` (resolved to the SPA origin); fixed to `${VITE_API_BASE_URL}/api/...`.
- **Login redirected to deleted `/auth/callback/google`** → `GOOGLE_OAUTH_REDIRECT_URI` still pointed at the old frontend route; repointed defaults to the backend `/api/auth/google/callback`.
- **Logout CORS error** → `X-CSRF-Token` (non-simple header) triggered a preflight that wasn't allowlisted; added it to CORS allow-headers in Spring + both nginx files.
- **Post-login landed on `localhost` not `:5173`** → redirect target was `cors.allowed-origins[0]`; replaced with the `returnTo` feature (validated against the origin allowlist).

## Learnings
- **Split-origin SPA**: any browser navigation to the API must use the absolute API base URL; a relative path resolves to the SPA origin (404). `ApiClient` already prepended it — `window.location.assign` didn't.
- **Custom request headers need CORS allow-headers in 4 places** here: Spring `CorsConfig` + `nginx/locations.conf` + `nginx/nginx.dev.conf` (preflight is answered by nginx in the dockerized flow, so the nginx list is the one the browser actually sees).
- **BFF login full-reload is inherent** (chain of top-level navigations) and matches Stack Overflow/GitHub; **logout reload is a deliberate `window.location.reload()`** that's removable.
- **Open-redirect guard via `URI.getScheme()`/`getHost()` + exact allowlist `Set.contains`** defeats parser-differential bypasses (userinfo `@`, host-suffix, protocol-relative, `javascript:`).
- **Clean cutover simplified the rollout**: forcing one re-login made new-format tokens + `csrf` cookie + `legacy-admit=false` land atomically, deleting dual-read + CSRF-grace transitional code.

## Spec Divergence

### What Changed
- **`startGoogleLogin` URL**: spec wrote `window.location.assign('/api/auth/google/start')` (relative) → must be `${VITE_API_BASE_URL}/api/auth/google/start` (absolute backend origin), because SPA and API are different origins.
- **First-login dialog re-home**: spec said move the trigger into `AuthSection` → it already existed in `GlobalLayout` (which also renders the dialog). Removed Header's duplicate instead.
- **Dev redirect-uri**: spec/Phase-3 default `http://localhost:8080/...` → corrected to `http://localhost/api/auth/google/callback` (port 80, nginx; backend not browser-reachable on 8080).

### What Was Added (Not in Spec)
- **`returnTo` feature** — return the user to the page they started auth from, carried through the encrypted `oauth_tx`, validated against the origin allowlist (`FrontendProperties.resolveReturnTo`). Emerged from a UX requirement that only surfaced in live testing ("redirect must be where the user started").
- **`X-CSRF-Token` in CORS allow-headers** (Spring + nginx) — the double-submit header is non-simple, so it triggers a preflight that the spec didn't account for.
- **`OAuthMitmResistanceTest`** — adversarial code-interception tests added on user request.

### What Was Dropped
- Nothing from the spec was dropped. DPoP remained the recorded deferred TODO (as specced).

### Wrong Assumptions
- Spec assumed the redirect/origin URLs without modeling the **nginx-port-80-only** topology (backend not externally reachable on 8080). All browser-facing URLs (SPA, `VITE_API_BASE_URL`, redirect-uri, post-login redirect) must be the nginx origin.
- Spec treated `cors.allowed-origins[0]` as a fine post-login redirect target; live testing showed it conflates "allowed to call the API" with "where to send the user back."

### Prompting Retrospective
- **Deployment topology**: "Is the app behind a reverse proxy, and what origin/port does the browser actually use for the SPA vs the API?" — would have caught the relative-URL and redirect-uri/port-80 issues at spec time (this is a recurring lesson; see meme `lesson-is-the-app-behind-a-reverse-proxy`).
- **CORS contract for new headers**: "Does any new feature add a request header? If so, which CORS allow-headers lists (Spring + nginx) must be updated?" — would have surfaced the logout preflight break.
- **Post-auth UX**: "After login, where exactly should the user land — a fixed origin or the page they started from?" — would have specced `returnTo` up front instead of discovering it in testing.

### Spec Process Takeaway
This spec systematically missed **deployment-topology and cross-origin/CORS integration constraints** — the failures clustered at the browser↔nginx↔backend boundary, not in the auth logic itself.

## Session State (uncommitted)
- **All changes are UNCOMMITTED** in the working tree (user commits manually; `static` submodule i18n commits separately; no Co-Authored-By line).
- **Status**: feature-complete and fully verified (backend + frontend green). Both review lenses ACCEPTABLE.
- **Next steps before deploy**: (1) set `GOOGLE_OAUTH_REDIRECT_URI=http://localhost/api/auth/google/callback` in `.env` (prod: `https://api.dante-planner.com/...`); (2) register that URI in Google Cloud Console; (3) announce the one-time re-login (clean cutover invalidates existing sessions); (4) optional: smooth-logout (`queryClient.clear()` instead of `window.location.reload()`), the `NullRequestCache` JSESSIONID hardening, and the doc sweep (README-DOCKER / environment-setup / cloudflare still cite the old `/auth/callback/google`).
- **Blockers**: none (the redirect-uri/Console steps are deploy-time config, not code).
