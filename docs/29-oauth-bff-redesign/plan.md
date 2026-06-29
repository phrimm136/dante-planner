# Execution Plan — OAuth BFF Redesign

## Phase Summary

Dependency-ordered, each phase a clean rollback point. The token format is the root dependency
(BFF endpoints mint new-format tokens), so it goes first. CSRF is independent of the token change.
Frontend goes last because it consumes the BFF endpoints + CSRF contract. Refresh rotation/blacklist
logic is NOT touched in any phase (D3) — its existing tests are a regression gate throughout.

Security-critical surgery is concentrated in Phase 1; it is verified green (including the untouched
rotation tests) before anything builds on it.

## Phases

### Phase 1: Backend token claim minimization
- Files: `JwtTokenService.java`, `TokenClaims.java`, `TokenGenerator.java`, `JwtAuthenticationFilter.java`, `AuthenticationFacade.java`, `RefreshRotationService.java`
- Change: access `sub`=userId + `role`/`type`/`iat`/`exp`; refresh `sub`=userId + `type`/`jti`/`family_id`/`parent_jti`; drop the AES-GCM `enc` blob and all email-in-token; `validateToken` reads userId from `sub`; email param removed from generators; `TokenClaims.email` nullable; keep AES-GCM cipher helpers (reused in Phase 3 for `oauth_tx`).
- Tests: `JwtTokenServiceTest` (no email, `sub`=userId, round-trip); `RefreshRotationServiceTest` MUST stay green unchanged (INV4).
- Depends on: none
- Verify: `./gradlew test --tests "*JwtTokenService*" "*RefreshRotation*" "*JwtAuthenticationFilter*"` green; full `./gradlew test` green.

### Phase 2: Backend CSRF double-submit
- Files: new CSRF filter, `CookieUtils.java` (readable-cookie variant), `SecurityConfig.java` (register filter, permit `GET /api/auth/google/**`)
- Change: ensure-`csrf`-cookie on responses + require matching `X-CSRF-Token` on POST/PUT/PATCH/DELETE → 403 on mismatch/missing.
- Tests: `CsrfFilterTest` (missing/mismatch/guest-bootstrap → INV3).
- Depends on: none (parallelizable with P1; run after for safety)
- Verify: `./gradlew test --tests "*Csrf*"` green; full suite green; existing authed-endpoint tests still pass with header.

### Phase 3: Backend BFF endpoints + config cutover
- Files: new `OAuthStateService` (encrypted `oauth_tx`), `AuthController.java` (`GET /start`, `GET /callback`; remove POST `/google/callback`), delete `OAuthCallbackRequest.java`, `OAuthProperties`/`application*.properties` (backend `redirect-uri`, `legacy-admit-enabled=false`)
- Change: `/start` mints state+PKCE → `oauth_tx` → 302 to Google; `/callback` verifies `oauth_tx`+state, rate-limits by IP/device, exchanges code, mints tokens (Phase 1 format), sets `accessToken`/`refreshToken`/`csrf` (Phase 2), clears `oauth_tx`, 302 to SPA.
- Tests: `AuthControllerOAuthBffTest` (`/start` mint+redirect), `OAuthBffCallbackTest` (happy path, INV2 state-mismatch + expiry).
- Depends on: Phase 1 (token format), Phase 2 (csrf cookie)
- Verify: `./gradlew test --tests "*OAuth*"` green; full suite green.

### Phase 4: Frontend BFF migration + CSRF header
- Files: new `useGoogleLogin.ts` (`startGoogleLogin`); modify `Header.tsx`, `UsernameSection.tsx`, `AccountDeleteSection.tsx`, `lib/api.ts` (CSRF header on non-GET), `lib/router.tsx` (remove route), `hooks/useAuthQuery.ts` (remove `useLogin`), `lib/env.ts` (remove `VITE_GOOGLE_CLIENT_ID`), first-login dialog trigger; delete `lib/oauth.ts`, `routes/auth/callback/google.tsx`; i18n key cleanup (static submodule — commit separately)
- Tests: `__tests__/useGoogleLogin.test.ts` (navigates to `/start`), `__tests__/api.test.ts` (attaches `X-CSRF-Token` on non-GET).
- Depends on: Phase 2 + Phase 3
- Verify: `yarn --cwd frontend test run` (changed dirs) green; `yarn --cwd frontend typecheck` (`tsc -b`) clean; production `vite build` resolves the removed lazy route.

## Phase Dependencies

- Group A (sequential, backend): Phase 1 → Phase 2 → Phase 3
- Group B (after A): Phase 4 (frontend)

## Out-of-build manual step (Done-When prerequisite)
Register the backend redirect URI (`https://api.dante-planner.com/api/auth/google/callback`) in the
Google Cloud Console before the cutover deploy. Not code; tracked here so it is not lost.
