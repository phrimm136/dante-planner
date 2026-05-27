# Results — Lineage-Based Refresh Token Rotation + Log Out Everywhere

## What Was Done

All 7 planned phases complete and verified.

- **Part 1 — Lineage rotation (backend, dark behind `jwt.rotation.lineage-enabled=false`):**
  - Refresh tokens now carry `jti` / `family_id` / `parent_jti` (encrypted claims); access tokens unchanged.
  - `RefreshRotationService` implements the full `UNUSED_LATEST → PENDING → USED | SUPERSEDED` state machine with symmetric theft detection (USED/SUPERSEDED presentation revokes the whole family), self-healing retry on dropped `Set-Cookie`, hourly scheduled cleanup, and three Micrometer metrics.
  - Wired into both refresh entry points behind the flag: `JwtAuthenticationFilter.attemptAutoRefresh` and `AuthenticationFacade.refreshTokens`; `logout` revokes the current family when the flag is on.
  - `SessionRevokedException` → 401 (reuses existing `TokenRevokedException` wire shape), clears both cookies.
  - Legacy (pre-deploy) tokens admitted gracefully behind `jwt.rotation.legacy-admit-enabled=true` with deterministic family+jti synthesis.
- **Part 2 — Log out everywhere:**
  - `POST /api/auth/logout-all` (auth-gated, 204) → invalidates all user tokens + blacklists current access token + clears cookies.
  - `LogoutEverywhereSection` + `LogoutEverywhereDialog` + `useLogoutEverywhere` in settings, localized EN/KR/JP/CN.
- **Metrics exposure:** added `micrometer-registry-prometheus`; exposed `prometheus` in base + prod actuator config (actuator is internal-only; only `/api/` is public).
- **Post-build review fixes:** lock-eviction race → fixed-size lock striping; `rotate()` access-token guard.

## Files Changed

**Backend — new (main):**
- `service/token/RefreshRotationService.java`, `RotationState.java`, `RotationEntry.java`, `RotationResult.java`
- `exception/SessionRevokedException.java`

**Backend — modified (main):**
- `service/token/TokenGenerator.java` (overload + UUID jti), `JwtTokenService.java` (embed lineage claims, refresh only), `TokenClaims.java` (nullable jti/familyId/parentJti + 6-arg convenience ctor)
- `security/JwtAuthenticationFilter.java` (lineage refresh behind flag), `facade/AuthenticationFacade.java` (refresh/logout/logoutAll), `exception/GlobalExceptionHandler.java` (SessionRevoked → 401 + clear cookies), `controller/AuthController.java` (logout-all), `config/SecurityConfig.java` (logout-all auth matcher)
- `build.gradle.kts` (prometheus registry), `application.properties` + `application-prod.properties` (flags + prometheus exposure)

**Backend — tests:** `RefreshRotationServiceTest`, `JwtAuthenticationFilterLineageTest`, `AuthenticationFacadeLineageTest`, `AuthControllerLogoutAllTest` (new); `JwtTokenServiceTest`, `JwtAuthenticationFilterTest`, `AuthenticationFacadeTest` (extended)

**Frontend — new:** `hooks/useLogoutEverywhere.ts`, `components/settings/LogoutEverywhereSection.tsx`, `LogoutEverywhereDialog.tsx`, `__tests__/LogoutEverywhereSection.test.tsx`
**Frontend — modified:** `routes/SettingsPage.tsx`

**i18n (static submodule):** `static/i18n/{EN,KR,JP,CN}/common.json` — `settings.logoutEverywhere.*` (9 keys each)

**Docs:** `docs/10-backend-revision/15-lineage-based-rotation/{plan,status,review-findings,announcement-draft,results}.md`

## Verification
- Build: pass (`compileJava`/`compileTestJava` clean; `tsc -b` exit 0)
- Tests: pass — backend 840 / 0 fail (15 pre-existing skips); frontend 5948 / 0 fail (108 files)
- Flag default off proven: full backend suite green = existing behavior unchanged on merge
- Manual: not run (staging-only steps in instructions.md §Verification remain for rollout)

## Issues & Resolutions
- **`ConcurrentHashMap.compute` mandated by spec is impossible** (a rotation writes a second key → `IllegalStateException: Recursive update`) → per-family `synchronized` lock.
- **`/api/auth/**` is `permitAll`** so the new endpoint wasn't auth-gated by default → added a precedence `.authenticated()` matcher in `SecurityConfig`.
- **Lock-leak fix introduced an eviction race** (caught in post-build review) → replaced dynamic per-family locks + eviction with 256 fixed lock stripes (no leak, no race).
- **`rotate()` admitted access tokens via the legacy branch** (null jti+familyId) → added `isRefreshToken()` guard before the legacy branch.
- **Prometheus endpoint absent** despite registered metrics → added `micrometer-registry-prometheus` + exposure config.

## Learnings
- **Verify reported success against source of truth, not the agent's word.** Phase 6's LSP diagnostics screamed "module not found" but were stale tsserver state; a filesystem check disproved them. Conversely, Phase 6 + Phase 7 ran fine but the *full* suite (not the auth slice) was the real proof of "flag off = no regression."
- **A self-ratified deviation is a smell worth re-reviewing.** The lock-eviction "benign re-creation" Javadoc argued a bug away in writing; the adversarial reviewer caught it. Author-defended designs don't get revisited later — review them now.
- **Lock striping > dynamic per-key locks + eviction** whenever the key space is unbounded but lock identity must be stable. Eliminates both the leak and the eviction race with no lifecycle to get wrong.
- **Spec-mandated mechanisms can be physically impossible.** `ConcurrentHashMap.compute` for multi-key atomic writes is a documented no-go; the spec should have said "atomic per-family," not named the mechanism.

## Spec Divergence

### What Changed
- **Atomicity mechanism**: spec line 68 mandated `ConcurrentHashMap.compute`; implemented per-family `synchronized`, then 256 fixed lock stripes. `compute` cannot write a second key without `IllegalStateException`.
- **Wire error code**: spec said reuse `TOKEN_REVOKED`; the existing `TokenRevokedException` actually emits code `"UNAUTHORIZED"`, and `api.ts` logs out on 401 *status* (not code). Mirrored the existing handler — behavior identical.

### What Was Added (Not in Spec)
- `SecurityConfig` precedence matcher to enforce auth on `/api/auth/logout-all` (spec assumed the filter alone would gate it, but `/api/auth/**` is `permitAll`).
- `micrometer-registry-prometheus` dependency + actuator exposure in base and prod (spec required `/actuator/prometheus` exposure but the registry wasn't on the classpath and exposure was `health` only).
- `TokenClaims` 6-arg convenience constructor (to keep 11 existing call sites compiling after adding 3 record fields).

### What Was Dropped
- Nothing from the spec's `Done When` list. Manual staging verification (§Verification steps 1–6) and the rollout sequence remain as documented future ops work, not code.

### Wrong Assumptions
- Spec assumed `compute` provides the needed atomicity → false for multi-key writes.
- Spec assumed "authenticated via existing filter" gates logout-all → false; `permitAll` on `/api/auth/**` meant an explicit matcher was required.
- Spec assumed `/actuator/prometheus` would surface the metrics → false without the registry dependency and exposure config.
- Spec's "frontend already handles `TOKEN_REVOKED`" → frontend handles 401 by status, ignores the code string.

### Prompting Retrospective
- **Concurrency mechanism**: "Walk through the exact `ConcurrentHashMap.compute` call for a rotation that mints a successor — does it write more than one key inside the callback?"
  - Why: would have surfaced the recursive-update impossibility at spec time, not phase 2.
- **AuthZ surface**: "Is the new endpoint's path covered by an existing `permitAll`/`authenticated` matcher, and what's the matcher precedence?"
  - Why: would have pre-empted the SecurityConfig addition and the "filter gates it" assumption.
- **Observability plumbing**: "Is `micrometer-registry-prometheus` on the classpath and is `prometheus` in `management.endpoints.web.exposure.include` for each profile?"
  - Why: "expose three metrics via /actuator/prometheus" silently required a dependency + per-profile config the spec didn't list.
- **Wire contract**: "Quote the exact JSON body and status `TokenRevokedException` produces today, and the exact branch in `api.ts` that consumes it."
  - Why: would have corrected the `TOKEN_REVOKED`-vs-`UNAUTHORIZED` and code-vs-status confusion before it became a documented deviation.

### Spec Process Takeaway
This spec systematically missed **integration constraints with existing infrastructure** — the concurrency primitive's real semantics, the existing security matcher topology, the actuator/metrics plumbing, and the actual wire contract the frontend consumes — because each was specified by intent ("use compute", "authenticated", "exposed via prometheus", "handles TOKEN_REVOKED") without verifying the mechanism against the current codebase.

## Session State (pre-compaction)

- **Uncommitted**: entire feature is unstaged (user commits, per decision this session). Backend main+test, frontend, `static` submodule i18n, build/config, and the docs folder. `static` is a submodule — its i18n changes commit separately inside the submodule.
- **Current focus**: feature complete + post-review critical fixes applied; suites green.
- **Next steps**: (1) user decides on `review-findings.md` items #3/#4/#5; (2) commit (note `static` submodule commits separately; no Co-Authored-By line); (3) optional `/review` or `/ultrareview` before PR; (4) staging rollout per instructions.md §Rollout.
- **Blockers**: none for code; #3/#4/#5 are user decisions, not blockers.
