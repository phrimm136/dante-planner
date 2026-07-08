# Execution Plan

## Phase Summary

Lineage-based refresh token rotation with theft detection (Part 1, backend) plus a
"log out everywhere" user action (Part 2, backend + frontend + i18n). All Part 1
behavior ships behind `jwt.rotation.lineage-enabled` (default `false`), so a merge is
a zero-behavior-change event. Execution is **sequential** — the auth hot path
(`JwtAuthenticationFilter`, `AuthenticationFacade`, `JwtTokenService`) is touched and
each phase must verify before the next begins.

## Cross-Cutting Constraints (apply to EVERY phase)

1. **Access tokens stay unchanged** — no `jti`/`family_id`/`parent_jti`. Lineage claims
   are added to refresh tokens ONLY (spec line 44). Do not add them to
   `generateAccessToken` "for symmetry."
2. **`generateRefreshToken(userId, email)` keeps working** — OAuth callback
   (`AuthenticationFacade.authenticateWithOAuth:118`) calls it. New overload
   `generateRefreshToken(userId, email, familyId, parentJti)` is additive; the old
   signature delegates with `familyId = UUID.randomUUID(), parentJti = null`.
3. **Flag-off path is byte-identical to today** — current `blacklistTokenForRotation`
   (5s grace) stays on the flag-off branch. The branch point exists in BOTH
   `JwtAuthenticationFilter.attemptAutoRefresh` AND `AuthenticationFacade.refreshTokens`;
   never collapse them to one.
4. **New `TokenClaims` fields are nullable** — `jti`, `familyId`, `parentJti` are all
   nullable; constructor validation must NOT reject null on them (access tokens, legacy
   refresh tokens, and pre-flag tokens lack them).
5. **Claims are AES-GCM encrypted inside the JWT** — read lineage claims via
   `tokenValidator.validateToken()`, never raw JWT parsing.
6. **No Learn-by-Doing / TODO(human)** — the spec is fully decided; author directly.

## Phases

### Phase 1: Data model + token claims
- Files (new): `service/token/RotationState.java` (enum), `service/token/RotationEntry.java` (record), `service/token/RotationResult.java` (sealed: Rotated | Revoked | Rejected)
- Files (modify): `service/token/TokenGenerator.java` (overload + UUID jti), `service/token/JwtTokenService.java` (embed jti/family_id/parent_jti in refresh only), `service/token/TokenClaims.java` (nullable jti/familyId/parentJti accessors)
- Tests: `service/token/JwtTokenServiceTest.java` additions — refresh carries jti/family_id/parent_jti; access carries none; `TokenClaims` returns null for access lineage fields
- Depends on: none
- Verify: `./gradlew -p backend test --tests "org.danteplanner.backend.service.token.JwtTokenServiceTest"` green; compile clean; existing token tests pass

### Phase 2: RefreshRotationService core + metrics + cleanup
- Files (new): `service/token/RefreshRotationService.java`
- Files (modify): none (filter/facade wiring is Phase 3)
- Implements: `rotate(refreshToken, response) → RotationResult` state machine (UNUSED_LATEST → PENDING → USED | SUPERSEDED), `revokeFamily(familyId)`, atomic transitions via `ConcurrentHashMap.compute`, scheduled hourly cleanup of expired `RotationEntry` + `revokedFamilies`, three Micrometer metrics (`jwt_rotation_outcome_total` counter, `jwt_rotation_state_size` + `jwt_rotation_revoked_families_size` gauges)
- Tests: `service/token/RefreshRotationServiceTest.java` — fresh login UNUSED_LATEST; happy rotation; retry supersede; theft via USED; theft via SUPERSEDED; family revocation rejects all; concurrent rotation race (two threads, one transition + one supersede); cleanup of expired entries; cleanup of revoked families; metrics per outcome tag
- Depends on: Phase 1
- Verify: `./gradlew -p backend test --tests "org.danteplanner.backend.service.token.RefreshRotationServiceTest"` green; all theft scenarios A–D from spec covered

### Phase 3: Filter + facade integration (NON-legacy)
- Files (new): `exception/SessionRevokedException.java`, `security/JwtAuthenticationFilterLineageTest.java`, `facade/AuthenticationFacadeLineageTest.java`
- Files (modify): `security/JwtAuthenticationFilter.java` (`attemptAutoRefresh` delegates to `RefreshRotationService` when flag on, else existing path), `facade/AuthenticationFacade.java` (`refreshTokens` delegates when flag on; `logout` revokes current family when flag on), `exception/GlobalExceptionHandler.java` (map `SessionRevokedException` → 401 `TOKEN_REVOKED`, clear cookies)
- Tests (this phase, NON-legacy only): flag off → behavior identical to current filter; flag on → auto-refresh with UNUSED_LATEST succeeds; flag on → USED returns 401 TOKEN_REVOKED + clears cookies; flag on → SUPERSEDED returns 401 + clears cookies; flag on → revoked-family token rejected at filter, no rotation attempted. Facade: refresh produces lineage claims when flag on; logout revokes family when flag on; logout still blacklists access token immediately.
- Depends on: Phase 1, Phase 2
- Verify: both flag states tested; existing filter/facade tests pass with flag off

### Phase 4: Legacy admission + config
- Files (modify): `service/token/RefreshRotationService.java` (legacy branch — synthesize `family_id` from deterministic hash of `(userId, issuedAt)`, admit as UNUSED_LATEST when `legacy-admit-enabled=true`), `src/main/resources/application.properties` (add `jwt.rotation.lineage-enabled=false`, `jwt.rotation.legacy-admit-enabled=true` with doc comments)
- Tests (extends Phase 3 file `JwtAuthenticationFilterLineageTest.java`): flag on + legacy-admit on → legacy token admitted once; flag on + legacy-admit off → legacy token rejected
- Depends on: Phase 2, Phase 3
- Verify: legacy admit on/off tests pass; properties documented

### Phase 5: "Log out everywhere" backend
- Files (new): `controller/AuthControllerLogoutAllTest.java`
- Files (modify): `controller/AuthController.java` (`POST /api/auth/logout-all` → 204, authenticated), `facade/AuthenticationFacade.java` (`logoutAll(userId, accessToken)` — `invalidateUserTokens` + blacklist current access token immediate + clear both cookies)
- Tests: unauthenticated → 401; authenticated → 204 + invalidates all user tokens + clears cookies; post-action access token → 401 TOKEN_REVOKED; post-action refresh with pre-action token fails
- Depends on: none on the lineage chain (uses existing `TokenBlacklistService.invalidateUserTokens`). Sequenced after Phase 4 only to keep one-phase-at-a-time on shared `AuthenticationFacade`.
- Verify: `--tests "org.danteplanner.backend.controller.AuthControllerLogoutAllTest"` green

### Phase 6: "Log out everywhere" frontend + i18n
- Files (new): `frontend/src/hooks/useLogoutEverywhere.ts`, `frontend/src/components/settings/LogoutEverywhereDialog.tsx`, `frontend/src/components/settings/LogoutEverywhereSection.tsx`, `frontend/src/components/settings/__tests__/LogoutEverywhereSection.test.tsx`
- Files (modify): `frontend/src/routes/SettingsPage.tsx` (insert `<LogoutEverywhereSection />` between NotificationSection and Danger Zone, standard `<section className="mt-8 rounded-lg border bg-card p-6">`), `static/i18n/{EN,KR,JP,CN}/common.json` (add `settings.logoutEverywhere.*` keys)
- Pattern source: `AccountDeleteSection.tsx` / `AccountDeleteDialog.tsx`
- Tests: renders title+button from i18n; button opens dialog; confirm → mutation + loading + success toast + redirect to `/`; error → error toast, no redirect; all four locales render without missing-key warnings
- Depends on: Phase 5 (endpoint contract)
- Verify: `yarn --cwd frontend test src/components/settings/__tests__/LogoutEverywhereSection.test.tsx` green; typecheck clean

### Phase 7: Full verification
- Run full backend suite (token + security + facade + AuthControllerLogoutAll) and frontend test; redirect to `/tmp/lineage-rotation-<session-id>-full.log`
- Confirm every `## Done When` checkbox from requirements.md
- Draft user announcement copy for the re-login notice
- Depends on: Phases 1–6

## Phase Dependencies
- Group A (sequential, Part 1 backend): Phase 1 → Phase 2 → Phase 3 → Phase 4
- Group B (sequential, Part 2): Phase 5 → Phase 6  (Phase 5 has no lineage dependency; sequenced after Phase 4 to avoid concurrent edits to AuthenticationFacade)
- Group C: Phase 7 after A and B
