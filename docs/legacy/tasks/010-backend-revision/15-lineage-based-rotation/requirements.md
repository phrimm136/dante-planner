# Task: Lineage-Based Refresh Token Rotation with Theft Detection

## Decisions

- **Lineage-based rotation over mint-then-blacklist** — simpler fix only addresses the race window, doesn't detect or recover from stolen tokens. Lineage gives stronger security (family revocation on theft) AND self-heals stuck users (retry re-mints the successor).
- **No retry time window** — state machine binds transitions to concrete events ("successor first used"), not wall-clock time. Eliminates arbitrary `N`-second tuning and corresponding edge cases.
- **`SUPERSEDED` state on retry (fresh mint each retry), not re-return stored** — re-returning the same successor to every retry presentation lets attacker+victim walk the chain in lockstep and defeats theft detection. Fresh mint + supersede makes theft detection symmetric regardless of who rotates first.
- **Family-per-login, not family-per-device** — each OAuth callback creates a fresh `family_id`; no `deviceId → familyId` map. The scenarios that would need device-scoped revocation on re-login (account switch, compromise recovery) are better served by an explicit "log out everywhere" user action than by inferring security intent from a login event.
- **In-memory state (`ConcurrentHashMap`), single-server** — matches the existing `TokenBlacklistService` model. Multi-server is out of scope; migration target is Redis with TTL entries when that lands.
- **Reuse `TOKEN_REVOKED` error code (not introduce `SESSION_REVOKED`)** — frontend already handles `TOKEN_REVOKED`; no client branch needed. Internal exception type is `SessionRevokedException` for semantic clarity in code/logs, but it maps to the existing wire code.
- **Legacy token graceful handling** — tokens issued before deploy have no `jti`/`family_id` claims; synthesize `family_id` on first encounter and admit as `UNUSED_LATEST`. Legacy phase retires within 7 days (refresh TTL).
- **Feature flag `jwt.rotation.lineage-enabled`** — old and new paths coexist behind a toggle for safe rollout.
- **No hotfix PR for stuck users** — existing stuck users recover via re-login; a user-facing announcement that "a relogin may be required" is sufficient.
- **Observability via Micrometer counters + gauges** — catches production bugs (false-positive theft, runaway map growth, Set-Cookie delivery failures) on dashboards instead of via user reports.
- **"Log out everywhere" button in settings** — the explicit user-facing affordance for compromise recovery and multi-device sign-out. Backed by `TokenBlacklistService.invalidateUserTokens(userId)` (already exists); new endpoint + new UI section + i18n strings in all four locales.
- **`TokenBlacklistService` keeps access-token blacklist + user-wide invalidation** — only the refresh-token rotation concern moves to a new service.

## Description

### Part 1 — Lineage-based refresh rotation

Replace the current blacklist-on-rotation refresh token scheme with lineage-tracked rotation plus theft detection via family revocation. Each refresh token carries `jti`, `family_id`, and optionally `parent_jti` claims. A `RefreshRotationService` maintains per-token rotation state (`UNUSED_LATEST` → `PENDING` → `USED` | `SUPERSEDED`) and a per-family revocation map. Any use of a `USED` or `SUPERSEDED` token revokes the entire family.

The scheme self-heals stuck users (retry after dropped `Set-Cookie` re-mints a fresh successor) and detects token theft symmetrically: whether attacker or victim rotates first, the other party's later presentation of a rotated token trips detection and revokes the family, forcing both to re-login.

### Part 2 — "Log out everywhere" user action

Add a settings-page section and button that invalidates *all* tokens for the current user across *all* devices, backed by the existing `TokenBlacklistService.invalidateUserTokens(userId)` facility. Fully localized across the four supported locales (EN, KR, JP, CN).

### State Machine (Part 1)

| From state | Event | Transition | Side effect |
|---|---|---|---|
| (none) | Fresh login | → `UNUSED_LATEST` | Mint refresh with fresh `jti`, `family_id`, no `parent_jti` |
| `UNUSED_LATEST` | Token presented for rotation | → `PENDING(successor_jti, pending_jwt)` | Mint successor with `parent_jti`; set `Set-Cookie` |
| `PENDING(R_old, _)` | Parent presented again (retry) | `R_old` → `SUPERSEDED`; mint `R_new`; parent → `PENDING(R_new, …)` | Re-issue fresh successor; set `Set-Cookie` with `R_new` |
| `PENDING(R, _)` | `R` first used | Parent → `USED`; drop `pending_jwt` | Normal rotation continues from R |
| `USED` | Any presentation | → theft | Revoke family: `revokedFamilies[family_id] = now` |
| `SUPERSEDED` | Any presentation | → theft | Revoke family |
| Any | Token's `family_id` in `revokedFamilies` | → reject | Return 401 `TOKEN_REVOKED`, clear cookies |

### Token Claims (Part 1)

Added to refresh tokens only (access tokens unchanged):

- `jti` — UUID, unique per refresh token
- `family_id` — UUID, stable across all rotations from the same login event
- `parent_jti` — UUID referencing parent's `jti`; absent on initial-login tokens

### Data Structures (Part 1, in-memory)

```
rotationState:   ConcurrentHashMap<String /*jti*/, RotationEntry>
revokedFamilies: ConcurrentHashMap<String /*family_id*/, Long /*revokedAt epoch ms*/>
```

```
record RotationEntry(
    State state,                // UNUSED_LATEST | PENDING | USED | SUPERSEDED
    String successorJti,        // non-null when PENDING
    String pendingJwt,          // the full R_new JWT, non-null when PENDING, dropped on USED
    String familyId,
    long issuedAt,
    long expiryMs               // for scheduled cleanup
)
```

Atomicity: all state transitions go through `ConcurrentHashMap.compute(jti, (k, v) -> …)` so read-modify-write on a single key is atomic under concurrent rotation.

### Theft Detection Scenarios (Part 1)

| # | Scenario | Outcome |
|---|---|---|
| A | Attacker rotates first, victim uses R1 later | Victim's use of `R1` finds state `PENDING(R2_att)` → `R2_att` → `SUPERSEDED`, mint `R2_vic`. Attacker's next use of `R2_att` → `SUPERSEDED` → theft → revoke family. |
| B | Victim rotates first, attacker uses R1 later | Attacker's use of `R1` finds state `PENDING(R2_vic)` → `R2_vic` → `SUPERSEDED`, mint `R2_att`. Victim's use of `R2_vic` → `SUPERSEDED` → theft → revoke family. |
| C | Legitimate retry after dropped Set-Cookie | `R1` `PENDING(R2_1)`; client retries `R1` → `R2_1` → `SUPERSEDED`, mint `R2_2`, client gets `R2_2`, uses it normally. No theft because `R2_1` was never delivered or used. |
| D | Attacker reuses a `USED` token | Chain has advanced past `R_n`; attacker presents `R_n` → `USED` → theft → revoke family. |
| E | Same-browser XSS shares cookie jar | Not detectable by rotation. Out of scope; relies on HttpOnly + CSRF layers. |

### Family Revocation (Part 1)

When a family is revoked:

1. `revokedFamilies[family_id] = now`
2. Response clears both access and refresh cookies (`CookieUtils.clearCookie`)
3. Response status 401 with error code `TOKEN_REVOKED`
4. Subsequent requests carrying any token from that family are rejected at the filter level before rotation is attempted

### Legacy Token Handling (Part 1)

Tokens issued before deploy lack `jti` and `family_id`. On first encounter (when `jwt.rotation.legacy-admit-enabled=true`):

1. Detect missing claim
2. Synthesize `family_id` from a deterministic hash of `(userId, issuedAt)` so the same legacy token always maps to the same family
3. Treat as `UNUSED_LATEST` with the synthesized `family_id`; rotation proceeds normally, new tokens get proper claims
4. After 7 days (refresh TTL) all tokens carry lineage claims; flag can be set `false`

### Metrics (Part 1, Micrometer)

Exposed via `/actuator/prometheus`:

- `jwt_rotation_outcome_total` — counter tagged `{outcome=rotated | retry_superseded | theft_revoked | legacy_admitted | rejected_revoked_family | rejected_invalid}`
- `jwt_rotation_state_size` — gauge, current size of `rotationState` map
- `jwt_rotation_revoked_families_size` — gauge, current size of `revokedFamilies` map

Alerting intent (not part of code scope, documented here for ops):

- `theft_revoked` sustained > 0 after rollout → false-positive detection bug; flip flag off
- `retry_superseded` rate high → Set-Cookie delivery is flaky at an intermediary
- `rotationState` size growing unboundedly → cleanup job broken

### "Log out everywhere" (Part 2)

**Backend endpoint:**

```
POST /api/auth/logout-all
→ 204 No Content on success
```

- Authenticated endpoint (requires valid access token via existing filter)
- Calls `tokenBlacklistService.invalidateUserTokens(userId)` for the current user
- Additionally blacklists the current request's access token (immediate, no grace)
- Clears both access and refresh cookies on the response
- Does *not* touch `rotationState` — existing entries for that user's tokens become irrelevant because `isUserTokenInvalidated` rejects them before rotation

**Frontend section:**

- New `LogoutEverywhereSection.tsx` in `frontend/src/components/settings/`
- Follows the pattern of `AccountDeleteSection.tsx` (confirmation dialog, `useMutation`, toast on success/error, invalidate `['auth', 'me']` query on success)
- Confirmation dialog explains: "You will be signed out of every device, including this one."
- On success: toast + invalidate auth query + redirect to `/`

**i18n keys (added to `static/i18n/{LOCALE}/common.json` under `settings.logoutEverywhere`):**

- `settings.logoutEverywhere.title`
- `settings.logoutEverywhere.description`
- `settings.logoutEverywhere.button`
- `settings.logoutEverywhere.confirmTitle`
- `settings.logoutEverywhere.confirmDescription`
- `settings.logoutEverywhere.confirmButton`
- `settings.logoutEverywhere.cancelButton`
- `settings.logoutEverywhere.success`
- `settings.logoutEverywhere.error`

All four locales (EN, KR, JP, CN) must be populated; use the existing translation conventions (check `announcement` skill if unsure about tone/style).

## Scope

Files to READ for context:

- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenBlacklistService.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/JwtTokenService.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenGenerator.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenValidator.java`
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenClaims.java`
- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java`
- `backend/src/main/java/org/danteplanner/backend/util/CookieUtils.java`
- `backend/src/test/java/org/danteplanner/backend/service/token/TokenBlacklistServiceTest.java` (test patterns)
- `backend/src/test/java/org/danteplanner/backend/facade/AuthenticationFacadeTest.java` (test patterns)
- `frontend/src/components/settings/AccountDeleteSection.tsx` (UI pattern)
- `frontend/src/components/settings/AccountDeleteDialog.tsx` (confirmation dialog pattern)
- `static/i18n/EN/common.json` (i18n conventions)
- `static/i18n/KR/common.json`, `static/i18n/JP/common.json`, `static/i18n/CN/common.json`

## Target

### Part 1 — Lineage rotation (backend)

**New files:**

```
backend/src/main/java/org/danteplanner/backend/service/token/
├── RefreshRotationService.java          # lineage rotation orchestrator + metrics
├── RotationEntry.java                   # record
├── RotationState.java                   # enum
└── RotationResult.java                  # sealed: Rotated | Revoked | Rejected

backend/src/main/java/org/danteplanner/backend/exception/
└── SessionRevokedException.java         # maps to 401 TOKEN_REVOKED

backend/src/test/java/org/danteplanner/backend/service/token/
└── RefreshRotationServiceTest.java

backend/src/test/java/org/danteplanner/backend/security/
└── JwtAuthenticationFilterLineageTest.java
```

**Files to modify:**

- `backend/src/main/java/org/danteplanner/backend/service/token/TokenGenerator.java` — add `generateRefreshToken(userId, email, familyId, parentJti)` overload; generate UUID `jti`
- `backend/src/main/java/org/danteplanner/backend/service/token/JwtTokenService.java` — embed `jti`, `family_id`, `parent_jti` in refresh tokens
- `backend/src/main/java/org/danteplanner/backend/service/token/TokenClaims.java` — expose `jti()`, `familyId()`, `parentJti()` (nullable for access/legacy)
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java` — `attemptAutoRefresh` delegates to `RefreshRotationService` when flag on
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java` — `refreshTokens` delegates; `logout` revokes current family when flag on
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java` — map `SessionRevokedException` to 401 `TOKEN_REVOKED`, clear cookies
- `backend/src/main/resources/application.properties` — add `jwt.rotation.lineage-enabled=false`, `jwt.rotation.legacy-admit-enabled=true`

### Part 2 — "Log out everywhere" (backend + frontend + i18n)

**New files (backend):**

- `backend/src/test/java/org/danteplanner/backend/controller/AuthControllerLogoutAllTest.java`

**New files (frontend):**

- `frontend/src/components/settings/LogoutEverywhereSection.tsx`
- `frontend/src/components/settings/LogoutEverywhereDialog.tsx`
- `frontend/src/hooks/useLogoutEverywhere.ts`
- `frontend/src/components/settings/__tests__/LogoutEverywhereSection.test.tsx`

**Files to modify (backend):**

- `backend/src/main/java/org/danteplanner/backend/controller/AuthController.java` — add `POST /api/auth/logout-all` endpoint
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java` — add `logoutAll(Long userId, String accessToken)` method

**Files to modify (frontend):**

- `frontend/src/routes/SettingsPage.tsx` — add `<LogoutEverywhereSection />` inside the authenticated-only block, between `NotificationSection` (line 54–57) and the Danger Zone (line 60–67). Wrap in the standard `<section className="mt-8 rounded-lg border bg-card p-6">` pattern.

**Files to modify (i18n, all four locales):**

- `static/i18n/EN/common.json` — add `settings.logoutEverywhere.*` keys
- `static/i18n/KR/common.json` — add same keys, Korean strings
- `static/i18n/JP/common.json` — add same keys, Japanese strings
- `static/i18n/CN/common.json` — add same keys, Chinese strings

### Files NOT Modified

- `TokenBlacklistService` — retains role for access-token blacklist and `invalidateUserTokens`
- Frontend `ApiClient.fetch` — existing 401 handling already turns `TOKEN_REVOKED` into a logged-out state

## Impact Analysis

- **High-impact files:**
  - `JwtAuthenticationFilter.java` — auth hot path; any bug breaks every authenticated request
  - `AuthenticationFacade.java` — refresh and logout both flow through
  - `JwtTokenService.java` — bad claim handling breaks all new tokens

- **Medium-impact files:**
  - `TokenGenerator.java` / `TokenClaims.java` — interface changes; legacy signature must keep working
  - `GlobalExceptionHandler.java` — one new exception mapping
  - `AuthController.java` — one new endpoint

- **Low-impact files:**
  - Frontend settings section — additive, one section among many
  - i18n files — additive keys; no existing keys touched

- **Dependencies:**
  - `JwtAuthenticationFilter` → `RefreshRotationService`, `TokenValidator`, `CookieUtils`
  - `AuthenticationFacade` → `RefreshRotationService`, `TokenBlacklistService`, `TokenGenerator`
  - `RefreshRotationService` → `TokenGenerator`, `TokenValidator`, `CookieUtils`, `MeterRegistry`

- **Ripple effects:**
  - All existing refresh-token tests must continue to pass with flag off
  - OAuth callback path unchanged (uses `TokenGenerator.generateRefreshToken`; new overload takes optional family/parent)
  - Frontend auth state — existing 401 handling in `frontend/src/lib/api.ts:139` handles `TOKEN_REVOKED` already

## Risk Assessment

### Edge cases

- **Concurrent rotation (two requests, same parent):** `ConcurrentHashMap.compute` serializes; one wins, the other goes through supersede branch. Both responses set valid cookies.
- **Clock skew:** rotation uses server `System.currentTimeMillis()`; no client-provided timestamps trusted.
- **Server restart mid-session:** both in-memory maps wiped. Previously rotated tokens admitted as `UNUSED_LATEST` once (same as today — current blacklist is also wiped). Bounded by refresh TTL.
- **Legacy token mid-deploy:** admitted as `UNUSED_LATEST` with synthesized `family_id`; upgraded to lineage-claim token on rotation.
- **Family revocation during in-flight request:** filter checks `revokedFamilies` before rotation; in-flight requests that started rotation before revocation may complete. Next request in family is rejected.
- **Long-lived `pendingJwt`:** capped by refresh TTL (7 days); hourly scheduled cleanup removes expired entries.
- **"Log out everywhere" during active use:** all access tokens still cached client-side become invalid on next filter pass (filter checks `isUserTokenInvalidated`); users with long-running SSE connections get kicked on next heartbeat.

### Performance

- `ConcurrentHashMap.compute` is O(1) amortized, same class as current blacklist lookup
- Memory: `RotationEntry` per active refresh token; `pendingJwt` ~1 KB per in-flight rotation, dropped on successor use
- Scheduled cleanup hourly, precedented by `TokenBlacklistService.cleanupExpired`

### Security

- **Stronger than today:** family revocation cascades on detected theft
- **Weaker than today briefly:** during 7-day legacy admission window, tokens without `jti` are admitted without lineage tracking
- **Symmetric theft detection:** both orderings trip detection (A and B in scenarios table)
- **"Log out everywhere" is user-gated:** requires active valid access token; no unauthenticated invocation

## Done When

- [ ] `RefreshRotationService` implemented with all five state transitions and family revocation
- [ ] Refresh tokens issued by `JwtTokenService` carry `jti`, `family_id`, and `parent_jti` (when rotated) claims
- [ ] `JwtAuthenticationFilter.attemptAutoRefresh` delegates to `RefreshRotationService` when flag on; falls back to existing path when off
- [ ] `AuthenticationFacade.refreshTokens` delegates when flag on; `logout` revokes current family
- [ ] `SessionRevokedException` maps to 401 `TOKEN_REVOKED` with cookies cleared
- [ ] Legacy tokens admitted gracefully as `UNUSED_LATEST` with synthesized `family_id`
- [ ] Scheduled hourly cleanup of expired `RotationEntry` and `revokedFamilies` entries
- [ ] Three Micrometer metrics exposed via `/actuator/prometheus`
- [ ] `POST /api/auth/logout-all` endpoint implemented; returns 204; clears cookies; invalidates all user tokens
- [ ] `LogoutEverywhereSection` rendered in settings page with confirmation dialog
- [ ] i18n keys populated in all four locales (EN, KR, JP, CN)
- [ ] All existing tests pass with flag off
- [ ] All new tests pass (see Test Plan)
- [ ] `application.properties` documents both flags with defaults

## Test Plan

### Test Runner

- Backend: JUnit 5 + Mockito — `./gradlew -p backend test --tests "org.danteplanner.backend.service.token.*" --tests "org.danteplanner.backend.security.*" --tests "org.danteplanner.backend.facade.*" --tests "org.danteplanner.backend.controller.AuthControllerLogoutAllTest"`
- Frontend: Vitest — `yarn --cwd frontend test src/components/settings/__tests__/LogoutEverywhereSection.test.tsx`
- Redirect output: `> /tmp/lineage-rotation-<session-id>-<suffix>.log`

### Tests to Write

**Backend unit — `RefreshRotationServiceTest.java`:**

- [ ] Fresh login: token minted with `UNUSED_LATEST` state
- [ ] Happy rotation: `UNUSED_LATEST` → `PENDING`; parent → `USED` on successor's first use
- [ ] Retry: `PENDING` → new `PENDING` with superseded predecessor; stored `pendingJwt` replaced
- [ ] Theft via `USED`: presenting `USED` token triggers family revocation
- [ ] Theft via `SUPERSEDED`: presenting `SUPERSEDED` token triggers family revocation
- [ ] Family revocation: all tokens in revoked family rejected
- [ ] Concurrent rotation race: two threads calling `rotate` on same parent produce one transition, one supersede
- [ ] Cleanup: `RotationEntry` past `expiryMs` removed
- [ ] Cleanup: revoked families past retention removed
- [ ] Metrics: each outcome increments the correct counter tag

**Backend unit — `JwtTokenServiceTest.java` additions:**

- [ ] Refresh token carries `jti`, `family_id`, `parent_jti`
- [ ] Access token does NOT carry lineage claims
- [ ] `TokenClaims` returns null for access-token lineage fields

**Backend integration — `JwtAuthenticationFilterLineageTest.java`:**

- [ ] Flag off: behavior identical to current filter
- [ ] Flag on: auto-refresh with `UNUSED_LATEST` succeeds, issues new cookies
- [ ] Flag on: auto-refresh with `USED` returns 401 `TOKEN_REVOKED`, clears cookies
- [ ] Flag on: auto-refresh with `SUPERSEDED` returns 401 `TOKEN_REVOKED`, clears cookies
- [ ] Flag on: revoked-family token rejected at filter, no rotation attempted
- [ ] Flag on: legacy token admitted once when `legacy-admit-enabled=true`
- [ ] Flag on + `legacy-admit-enabled=false`: legacy token rejected

**Backend integration — `AuthenticationFacadeLineageTest.java`:**

- [ ] `refreshTokens` produces lineage claims when flag on
- [ ] `logout` revokes current family when flag on; subsequent refresh in same family returns 401
- [ ] `logout` still blacklists access token immediately (unchanged)

**Backend integration — `AuthControllerLogoutAllTest.java`:**

- [ ] Unauthenticated: returns 401
- [ ] Authenticated: returns 204; invalidates all user tokens; clears cookies
- [ ] After logout-all: subsequent request with pre-action access token returns 401 `TOKEN_REVOKED`
- [ ] After logout-all: attempt to refresh with pre-action refresh token fails (user invalidated)

**Frontend unit — `LogoutEverywhereSection.test.tsx`:**

- [ ] Renders section title and button from i18n
- [ ] Button opens confirmation dialog
- [ ] Confirm triggers mutation; shows loading state; toast on success; redirects to `/`
- [ ] Error path shows error toast, does not redirect
- [ ] All four locales render without missing-key warnings

## Verification

### Manual (backend)

1. **Happy rotation** (flag on, staging):
   - Log in, confirm refresh cookie carries `jti`/`family_id`
   - Force access expiry; make authenticated request
   - Verify Set-Cookie for new refresh with same `family_id`, new `jti`, `parent_jti` = old `jti`

2. **Retry recovery** (flag on, staging):
   - Log in; intercept and drop next refresh Set-Cookie
   - Retry with old refresh cookie → verify fresh successor minted (different `jti` than the dropped one)

3. **Theft simulation** (flag on, staging):
   - Log in on browser; copy refresh cookie
   - Use curl with same cookie to rotate → attacker has R2
   - Browser makes request → gets different R2 via supersede branch
   - Curl uses R2 → 401 `TOKEN_REVOKED`
   - Browser makes another request → 401 `TOKEN_REVOKED`

4. **Per-session logout** (flag on, staging):
   - Log in on two browsers (two families)
   - Log out on browser A
   - Browser B still functions normally (different family, not affected)

### Manual ("log out everywhere")

5. **Log out everywhere** (staging):
   - Log in on three devices (or three browsers)
   - On device 1, go to settings, click "Log out everywhere"
   - Confirm dialog; confirm
   - Verify device 1 redirects to `/`, logged out
   - Verify device 2 and device 3 get 401 `TOKEN_REVOKED` on their next request, redirect to logged-out state
   - Re-login on any device; confirm fresh session

6. **i18n smoke test:**
   - Switch language to each of EN/KR/JP/CN
   - Confirm settings section renders correctly in each; no placeholder keys visible

### Edge Cases

- [ ] Server restart mid-session: rotated tokens admitted once as `UNUSED_LATEST`, then rotate normally
- [ ] Concurrent rotation (two tabs, both expired access): both resolve with valid tokens, last-used wins on browser side
- [ ] `revokedFamilies` entry during in-flight: next request in family rejected
- [ ] Access-token blacklist unaffected: logout still invalidates access tokens immediately
- [ ] "Log out everywhere" while on long-running SSE: SSE connection drops on next heartbeat

## Rollout

1. **Merge with flags off:** zero behavior change; new code dormant.
2. **Staging validation:** flip `jwt.rotation.lineage-enabled=true` in staging; run all manual verification steps; monitor metrics for 1 week:
   - `theft_revoked` should be near zero (spikes = false-positive bug)
   - `rotationState` size stabilizes near active-user count
   - `retry_superseded` rate identifies any persistent Set-Cookie delivery issues
3. **User announcement:** post notice that "a relogin may be required following the auth system update."
4. **Production flip:** `jwt.rotation.lineage-enabled=true`. Keep `legacy-admit-enabled=true` for 7 days.
5. **"Log out everywhere" launch:** ship in same deploy or follow-up deploy; no flag (purely additive).
6. **Legacy disable:** after 7 days, `jwt.rotation.legacy-admit-enabled=false`. All tokens now carry lineage claims.
7. **Cleanup PR:** remove the old blacklist-on-rotation code path; remove the feature flag.

## Implementation Phases

### Phase 1: Data model + token claims (45 min)

- `RotationState`, `RotationEntry`, `RotationResult`
- `TokenGenerator` overload with `familyId`/`parentJti`; UUID `jti`
- `JwtTokenService` embeds new claims; `TokenClaims` exposes them
- Unit tests for claim round-trip

### Phase 2: `RefreshRotationService` core (1.5 hours)

- State machine: `rotate(refreshToken, response) → RotationResult`
- `revokeFamily(familyId)`
- Atomic transitions via `ConcurrentHashMap.compute`
- Scheduled cleanup
- Micrometer metrics (counter + two gauges)
- Exhaustive unit tests for all transitions and theft scenarios

### Phase 3: Filter + facade integration (1 hour)

- `JwtAuthenticationFilter.attemptAutoRefresh` behind flag
- `AuthenticationFacade.refreshTokens` / `logout` behind flag
- `SessionRevokedException` + `GlobalExceptionHandler` mapping to `TOKEN_REVOKED`
- Integration tests for both flag states

### Phase 4: Legacy admission + config (30 min)

- Legacy branch with synthesized `family_id`
- `application.properties` entries
- Tests for legacy admit on/off

### Phase 5: "Log out everywhere" backend (30 min)

- `POST /api/auth/logout-all` in `AuthController`
- `AuthenticationFacade.logoutAll(userId, accessToken)`
- Controller test

### Phase 6: "Log out everywhere" frontend + i18n (45 min)

- `useLogoutEverywhere` hook (mutation + success handling)
- `LogoutEverywhereDialog` (confirmation)
- `LogoutEverywhereSection` (settings section)
- Wire into settings page
- Add i18n keys in all four locales (EN, KR, JP, CN)
- Frontend test

### Phase 7: Verification + rollout prep (30 min)

- Run full test suites (backend + frontend); redirect to `/tmp/lineage-rotation-<session-id>-full.log`
- Manual verification in staging
- Draft user announcement copy for re-login notice
