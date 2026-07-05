# Task: OAuth BFF Redesign — server-side flow, minimized JWT claims, double-submit CSRF, clean cutover

> Scope note (`docs/spec.md`): the project's extra spec sections (Data Model Catalog, Normalization
> Layer, Rendering Mode Enumeration) apply only to **data-driven** features that consume raw game
> data. This feature consumes no game data — those sections are **N/A**.

## Decisions

- **D1 — Front channel = stateless BFF.** Replace the popup + browser-PKCE + `postMessage` flow with a server-driven redirect flow: `GET /api/auth/google/start` (server mints `state` + PKCE, 302s to Google) and `GET /api/auth/google/callback` (server verifies, exchanges code, sets cookies, 302s to SPA). Because the planner autosaves to IndexedDB every second, the full-page redirect's in-memory state loss is non-destructive. (evidence: user decision; `Header.tsx:121-162` is the popup flow being replaced)
- **D2 — Minimized JWT claims, `sub`=userId.** Access token claims become `sub`=userId, `role`, `type`, `iat`, `exp`. Refresh token claims become `sub`=userId, `type`, `jti`, `family_id`, `parent_jti`, `iat`, `exp`. **Drop the AES-GCM `enc` claim blob and stop putting email in any token** — fixes the plaintext-email-in-`sub` leak. (evidence: user decision; current leak at `JwtTokenService.java:233` `.subject(email)`)
- **D3 — Keep current refresh-token storage.** `RefreshRotationService` (in-memory lineage maps) and `TokenBlacklistService` stay exactly as-is this cycle; they migrate to Redis at the planned k8s cut. This redesign does not touch rotation/blacklist logic. (evidence: user decision)
- **D4 — Ban latency: accept the access-token window.** No `security_stamp`. Bans are already enforced lazily at the service/write layer, so a banned user holding a still-valid access token cannot perform mutating actions. (evidence: user decision; `LimbusPlanner/gotcha-lazy-ban-timeout-enforcement-checked-at`)
- **D5 — DPoP deferred.** Recorded as `LimbusPlanner/todo-dpop-rfc-9449-sender-constrained-access`. No token binding added now. (evidence: user decision)
- **D6 — Self-enforcing CSRF via double-submit.** Add a readable `csrf` cookie + required `X-CSRF-Token` header on unsafe methods, replacing the unenforced "all GETs are read-only" assumption behind the disabled Spring CSRF. SameSite=Lax stays as belt-and-suspenders. (evidence: user decision; `SecurityConfig.java:70` csrf disabled; no CSRF token mechanism exists today)
- **D7 — Clean cutover (one forced re-login).** Ship all changes together; old-format tokens fail validation → users re-login via BFF. New login issues new-format token + `csrf` cookie atomically, so no transitional dual-format read and no CSRF grace mode. Set `jwt.rotation.legacy-admit-enabled=false` at cutover. Announce the re-login. (evidence: user decision)
- **D8 — Extract the duplicated login trigger.** The three identical `handleGoogleLogin` copies collapse to one shared `startGoogleLogin()` helper (`window.location.assign('/api/auth/google/start')`), paying down the long-deferred `useGoogleLogin` debt. (evidence: CLAUDE.md rule #6 extract-duplicates; `LimbusPlanner/decision-oauth-login-logic-is-intentionally-duplicated`)
- **D9 — `oauth_tx` transient cookie.** The server's stateless scratchpad for `{state, code_verifier}` during the redirect is a 90s server-signed **and AES-GCM-encrypted** cookie, reusing the existing `JwtProperties` key material (the encrypt/decrypt helpers stay in the codebase for exactly this use). HttpOnly, **SameSite=Lax** (must survive Google's cross-site top-level redirect back to the callback). (evidence: DEFAULT — reuses existing crypto; SameSite reasoning per `CookieUtils.java:15` Lax rationale)
- **D10 — Backend redirect_uri.** `oauth.google.redirect-uri` changes from the frontend URL to the backend callback `https://api.dante-planner.com/api/auth/google/callback`; both old and new URIs must be registered in the Google Cloud Console during transition. (evidence: `OAuthProperties.java:27-42`, `GoogleOAuthProvider.java:61` uses it in the exchange)

## Resolved Ambiguities

| Question | Resolution | Source |
|----------|------------|--------|
| Does removing email from tokens break any consumer? | No critical consumer. `getEmailFromToken` (`JwtTokenService.java:189`) is the only direct reader and is unused on the auth/refresh path; the filter, `getCurrentUser`, and refresh all hold `userId` and fetch email from the DB user. Delete `getEmailFromToken` if it has no remaining callers. | Codebase `JwtAuthenticationFilter.java:144-155`, `AuthController.java:120`, `AuthenticationFacade.java:201` |
| After moving userId to `sub`, do downstream consumers still resolve it? | Yes. They call `TokenClaims.userId()`; only `JwtTokenService.validateToken` changes — read userId from `claims.getSubject()` (parse Long) instead of the encrypted `userId` claim. | Codebase `JwtTokenService.java:132`, `RefreshRotationService.java:168-169` |
| Does refresh rotation still work with minimized claims? | Yes. `jti`/`family_id`/`parent_jti` remain as cleartext claims; userId from `sub`. Rotation state is keyed by `jti`, unchanged. | Codebase `RefreshRotationService.java:155-194` |
| Does `generateRefreshToken`/`generateAccessToken` still need the `email` param? | No. With `sub`=userId, the subject is userId; email param is removed from both generators. `RefreshRotationService.performRotation` mints the successor from claims without email. | Codebase `JwtTokenService.java:98-124`, `RefreshRotationService.java:251-252` |
| `TokenClaims` validates email non-null — does that break? | Yes if untouched. Make `email` nullable (drop the non-null constructor check); it is no longer carried in tokens. | Codebase `TokenClaims.java:22,35-36` |
| How many FE call sites build the Google auth URL? | Three: `Header.tsx:121`, `UsernameSection.tsx:68`, `AccountDeleteSection.tsx:34`. All are plain guest sign-in prompts (`if (!user)`), NOT step-up re-auth — sensitive actions use normal authed mutations. All three collapse to `startGoogleLogin()`. | Codebase `UsernameSection.tsx:110-124`, `AccountDeleteSection.tsx:130-144` |
| Exact OAuth scope/params the BFF `/start` must replicate | `response_type=code`, `scope=openid email` (space-separated, no `profile`/`access_type`/`prompt`), `state`, `code_challenge`, `code_challenge_method=S256`. | Codebase `Header.tsx:131-140` |
| Does the rate limit / `@DeviceId` wiring port to GET? | Yes. `checkAuthLimit(String identifier)` is IP/device-keyed, not user-keyed; `@DeviceId` reads a server-set cookie. Move both to `GET /callback` (the expensive exchange). | Codebase `AuthController.java:62-68`, `RateLimitConfig.checkAuthLimit`, `DeviceIdArgumentResolver` |
| Where is the callback route registered? | `router.tsx:445-450` (`googleCallbackRoute`, lazy import) + its entry in `routeTree.addChildren`. Delete both + the route file. | Codebase `frontend/src/lib/router.tsx:445-450` |
| Does a CSRF token mechanism already exist? | No. Only OAuth `state` (sessionStorage) + SameSite=Lax. Double-submit `csrf` cookie/header is net-new. `CookieUtils.setCookie` always sets HttpOnly → needs a readable-cookie variant for `csrf`. | Codebase `SecurityConfig.java:70`, `CookieUtils.java:60-71` |
| First-login sync dialog without `postMessage`? | Re-home: when the auth query transitions guest→authenticated, check `settings.syncEnabled === null` and open the dialog. Derive from server state, not a transient flag. | Codebase `Header.tsx:102-106`; `LimbusPlanner/lesson-when-a-backend-endpoint-is-toggle` |
| Access-token TTL change to 10 min? | **No — keep existing 900s (15 min).** TTL is not a locked decision and tuning it is out of scope (CLAUDE.md rule #1, no unrequested changes). | Default; `application.properties` `jwt.access-token-expiry` |
| Test runner/framework | Backend: JUnit + Gradle, `./gradlew test`. Frontend: Vitest, `yarn --cwd frontend test run`; tests in `__tests__/`. | Convention; backend/frontend CLAUDE.md, `LimbusPlanner/project_test_files_excluded_from_tsconfig` |
| Spec directory for arg `29` | `docs/29-oauth-bff-redesign/` — project convention is `docs/NN-slug` (top-level dirs run 00–28); bare `./29` would violate it. | Convention; `docs/` listing |

## Description

Migrate Google OAuth from a client-driven popup+PKCE flow to a server-side BFF redirect flow, minimize JWT claims to stop leaking email, and add self-enforcing CSRF — shipped as a single clean cutover that forces one re-login. Refresh-token rotation/blacklist logic is untouched.

Functional requirements:
1. `GET /api/auth/google/start` mints `state` + PKCE `code_verifier`/`code_challenge`, stores `{state, code_verifier}` in the encrypted 90s `oauth_tx` cookie, and 302s to Google's authorize URL replicating today's scope/params exactly.
2. `GET /api/auth/google/callback?code&state` verifies `oauth_tx` (HMAC/decrypt + `state` match), rate-limits by IP/device, exchanges the code (existing `GoogleOAuthProvider`), resolves/creates the user (existing `AuthenticationFacade`), mints new-format tokens, sets `accessToken`/`refreshToken`/`csrf` cookies, clears `oauth_tx`, and 302s to the SPA.
3. Access/refresh tokens carry minimized cleartext claims with `sub`=userId; no email, no `enc` blob.
4. A CSRF filter ensures a readable `csrf` cookie exists and requires a matching `X-CSRF-Token` header on POST/PUT/PATCH/DELETE; the SPA's API client attaches it.
5. The three FE auth-URL builders collapse to one `startGoogleLogin()` navigation; popup, `postMessage` listener, `lib/oauth.ts`, the callback route, `useLogin`, and `VITE_GOOGLE_CLIENT_ID` are removed.
6. First-login sync dialog triggers off auth-state transition + `settings.syncEnabled === null`.
7. `jwt.rotation.legacy-admit-enabled=false`; `oauth.google.redirect-uri` repointed to the backend.

## Scope (read for context)

- `backend/.../service/token/{JwtTokenService,TokenClaims,TokenGenerator,TokenValidator,RefreshRotationService}.java`
- `backend/.../security/JwtAuthenticationFilter.java`, `config/SecurityConfig.java`, `util/CookieUtils.java`
- `backend/.../controller/AuthController.java`, `facade/AuthenticationFacade.java`, `service/oauth/GoogleOAuthProvider.java`
- `backend/.../config/{OAuthProperties,JwtProperties,RateLimitConfig,DeviceIdArgumentResolver}.java`, `dto/OAuthCallbackRequest.java`
- `backend/src/main/resources/application.properties`, `application-prod.properties`
- `frontend/src/components/Header.tsx`, `frontend/src/pages/settings/components/{UsernameSection,AccountDeleteSection}.tsx`
- `frontend/src/lib/{oauth.ts,api.ts,env.ts,router.tsx}`, `frontend/src/hooks/useAuthQuery.ts`, `frontend/src/routes/auth/callback/google.tsx`, `frontend/src/stores/useFirstLoginStore.ts`
- `docs/15-auth/` (original auth feature context)

## Target (create / modify)

**Backend — create**
- `OAuthStateService` (mint/verify the encrypted `oauth_tx` cookie) — or fold into the controller per plan
- CSRF double-submit filter (ensure-cookie + validate-header)

**Backend — modify**
- `AuthController.java` — add `GET /start` + `GET /callback`; remove POST `/google/callback` + `OAuthCallbackRequest` usage
- `JwtTokenService.java` — minimize claims, `sub`=userId, drop `enc` for access/refresh, drop email params; reuse cipher helpers only for `oauth_tx`
- `TokenClaims.java` — email nullable; `TokenGenerator.java` — drop email params
- `JwtAuthenticationFilter.java`, `AuthenticationFacade.java`, `RefreshRotationService.java` — update generator calls
- `SecurityConfig.java` — register CSRF filter; permit `GET /api/auth/google/**`
- `CookieUtils.java` — add readable (non-HttpOnly) cookie variant for `csrf`
- `OAuthProperties.java` / `application*.properties` — backend `redirect-uri`, `legacy-admit-enabled=false`
- Delete `dto/OAuthCallbackRequest.java` (after POST callback removed)

**Frontend — create**
- `useGoogleLogin.ts` (or `startGoogleLogin` in `lib/auth.ts`) — shared navigation trigger

**Frontend — modify / delete**
- Modify `Header.tsx` (replace handler, delete `postMessage` listener + `useLogin`), `UsernameSection.tsx`, `AccountDeleteSection.tsx` (use shared trigger), `lib/api.ts` (CSRF header), `lib/router.tsx` (remove route), `hooks/useAuthQuery.ts` (remove `useLogin`), `lib/env.ts` (remove `VITE_GOOGLE_CLIENT_ID`), first-login dialog trigger
- Delete `lib/oauth.ts`, `routes/auth/callback/google.tsx`
- i18n: keep `header.auth.*`; remove now-unused popup/postMessage error keys (`popupBlocked`, `invalidResponse`, `securityFailed`)

## Impact Analysis

- **Modified (high impact):** `JwtTokenService` (token format — every authed request reads it), `SecurityConfig` (filter chain), `AuthController` (login entry).
- **Modified (medium):** generator signature change ripples to `AuthenticationFacade`, `JwtAuthenticationFilter`, `RefreshRotationService`; `api.ts` adds a header to every mutation.
- **Dependencies:** all authenticated endpoints depend on `JwtAuthenticationFilter` reading the new token format; all FE mutations depend on the CSRF header.
- **Ripple effects:** in-flight sessions invalidated at deploy (intended, D7); external Google Console redirect-uri registration is a hard prerequisite; Apple callback stub untouched (still returns 400).

## Risk Assessment

- **Edge cases:** `oauth_tx` expiry mid-consent (>90s at Google) → callback rejects, user restarts; popup-blocker fallback no longer needed (full-page nav); guest hitting a mutating endpoint with no `csrf` cookie (ensure-cookie step covers it); concurrent logins in two tabs (independent `oauth_tx` per tab, Lax-scoped).
- **Performance:** access-path crypto reduced (no per-request AES decrypt); one extra in-process cookie check per mutation (negligible).
- **Security:** email no longer in any token; CSRF self-enforcing; `oauth_tx` verifier encrypted at rest; redirect-uri pinned server-side. `legacy-admit=false` removes the synth-admit gap.

## Boundaries & Invariants

- **Trust boundary:** the browser is an untrusted courier; Google is trusted only via the back-channel TLS exchange; `oauth_tx`, `accessToken`, `refreshToken` are server-issued and server-verified — never trusted as client-authored.
- **INV1:** No JWT (access or refresh) ever contains the user's email in any field. (was violated by `sub`=email)
- **INV2:** A `GET /api/auth/google/callback` is honored only if its `state` equals the `state` inside a valid, unexpired `oauth_tx` cookie on the same browser.
- **INV3:** Every state-changing request (POST/PUT/PATCH/DELETE) carries an `X-CSRF-Token` header equal to the `csrf` cookie, or is rejected 403.
- **INV4:** Refresh rotation/theft-detection behavior is byte-for-byte unchanged from the pre-redesign `RefreshRotationService`.
- **INV5:** `code_verifier` never reaches client JavaScript (server-only in `oauth_tx`).

## Failure Modes

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| INV2 | Replayed/forged `state` with no matching `oauth_tx` (CSRF/login-fixation) | Callback rejects, no cookies set, 4xx | `OAuthBffCallbackTest#rejectsStateWithoutOauthTx` |
| INV2 | `oauth_tx` expired (user idled >90s at Google) | Reject, user restarts `/start` | `OAuthBffCallbackTest#rejectsExpiredOauthTx` |
| INV3 | Mutation arrives with missing/mismatched `X-CSRF-Token` | 403 before controller | `CsrfFilterTest#rejectsMissingAndMismatchedToken` |
| INV3 | Guest (no prior `csrf` cookie) issues a mutation | ensure-cookie sets one; first mismatched attempt 403, retry with echoed cookie passes | `CsrfFilterTest#issuesCookieThenEnforces` |
| INV1 | Token minted then decoded | `sub` is numeric userId; no `email`/`enc` claim present anywhere | `JwtTokenServiceTest#tokenCarriesNoEmail` |
| INV4 | Reuse a rotated refresh token (theft) post-migration | Family revoked, cookies cleared — identical to today | existing `RefreshRotationServiceTest` (must stay green) |
| INV5 | Inspect browser storage/JS after login | No `code_verifier` in sessionStorage/JS; only in HttpOnly `oauth_tx` | `oauth-bff.e2e` / manual verification |

### Visualized Failure (worst row: INV2 login fixation)

1. Attacker starts their own Google login, captures a valid authorization `code` for *their* account.
2. Attacker tricks the victim's browser into hitting `/api/auth/google/callback?code=<attacker_code>&state=<attacker_state>`.
3. **Broken state would be:** victim's browser gets logged in as the attacker (session fixation) → victim's later actions land in the attacker's account.
4. **Response intervenes at step 2:** the victim's browser has no `oauth_tx` cookie matching `<attacker_state>` (it was never issued one for this flow), so INV2 fails the `state` check and the callback rejects before any cookie is set. The attacker cannot plant their `oauth_tx` in the victim's browser (HttpOnly, server-signed, SameSite=Lax).

## Done When

- [ ] `GET /api/auth/google/start` 302s to Google with `scope=openid email`, `code_challenge_method=S256`, and sets an encrypted `oauth_tx` cookie.
- [ ] `GET /api/auth/google/callback` completes login end-to-end, sets `accessToken`/`refreshToken`/`csrf` cookies, clears `oauth_tx`, 302s to the SPA.
- [ ] No JWT contains email or an `enc` claim; `sub` is the numeric userId; `/api/auth/me` still returns the correct user.
- [ ] Mutations without a matching `X-CSRF-Token` are 403; the SPA sends the header and succeeds.
- [ ] FE popup, `postMessage` listener, `lib/oauth.ts`, callback route, `useLogin`, `VITE_GOOGLE_CLIENT_ID` are gone; all three login buttons navigate to `/start`.
- [ ] First-login sync dialog opens for a brand-new user after the redirect-back.
- [ ] `jwt.rotation.legacy-admit-enabled=false`; `oauth.google.redirect-uri` points at the backend.
- [ ] Existing `RefreshRotationService`/rotation tests pass unchanged (INV4).
- [ ] All existing backend and frontend tests pass; `tsc -b` clean.
- [ ] Google Cloud Console has the backend redirect URI registered (manual, documented).

## Test Plan

### Test Runner
- Backend: JUnit via Gradle — `./gradlew test` (scope: `--tests "*OAuth*" "*Csrf*" "*JwtTokenService*" "*RefreshRotation*"`)
- Frontend: Vitest — `yarn --cwd frontend test run` (scope the changed `__tests__/` dirs)

### Tests to Write
- [ ] `/start` mints `oauth_tx` + correct Google redirect: `backend/.../controller/__tests__`-equiv (`AuthControllerOAuthBffTest`)
- [ ] `/callback` happy path sets cookies + clears `oauth_tx`: `OAuthBffCallbackTest`
- [ ] INV2 state-mismatch + expiry rejection: `OAuthBffCallbackTest`
- [ ] INV3 CSRF missing/mismatch/guest-bootstrap: `CsrfFilterTest`
- [ ] INV1 token carries no email, `sub`=userId, validate round-trips: `JwtTokenServiceTest`
- [ ] INV4 rotation unchanged: existing `RefreshRotationServiceTest` stays green (no edits to weaken)
- [ ] FE: `startGoogleLogin` navigates to `/api/auth/google/start`: `__tests__/useGoogleLogin.test.ts`
- [ ] FE: API client attaches `X-CSRF-Token` from cookie on non-GET: `__tests__/api.test.ts`
- [ ] Every `Test` cell in Failure Modes is realized above — no failure mode ships untested

## Verification

### Manual
1. Register the backend redirect URI in Google Cloud Console.
2. Deploy; from a clean browser, click "Sign in with Google" in the Header → full-page redirect → Google → back to SPA, logged in.
3. DevTools: confirm `accessToken`/`refreshToken` are HttpOnly, `csrf` is readable; decode `accessToken` payload → `sub` is numeric, no `email`/`enc`.
4. Confirm no `code_verifier` in sessionStorage at any point.
5. Edit username / open account-delete as a guest → both buttons drive the same redirect login.
6. Brand-new account → first-login sync dialog appears after redirect-back.
7. Force a mutation without the CSRF header (curl with cookies only) → 403.

### Edge Cases
- [ ] Idle >90s at Google then continue: callback rejects gracefully, restart works.
- [ ] Two tabs initiating login simultaneously: each completes independently.
- [ ] Existing pre-cutover session: first request after deploy → token rejected → guest state → re-login works.
