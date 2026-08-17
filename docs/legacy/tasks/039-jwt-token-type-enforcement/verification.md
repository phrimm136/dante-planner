# Verification Report

Task: 039-jwt-token-type-enforcement · Scope: Final · Branch: build/039-jwt-token-type-enforcement
(commits 34a7e8b9, 0d23b8a7, 5c2fbfa3; branch point d20b052a)

## Overall: FAIL

Every in-scope item is **MET** — code and tests both present and green, including the two
containerized classes the task flagged as false-green traps. The **sole blocker is
environmental**: the full suite as commanded cannot produce a green single-invocation run in
this environment because of a JVM CodeCache exhaustion in an out-of-scope architecture test.
There is nothing in task 039 to fix. See the `## Full Suite` attribution and the `## Gaps`
UNTESTABLE line — the orchestrator decides re-run tuning vs. environment-defect bounce.

## Full Suite

Command (from repo root, no `-PexcludeTags`, containerized tier included, Docker up):

```
backend/gradlew -p /home/user/github/LimbusPlanner/backend test
```

Verbatim tail:

```
1100 tests completed, 9 failed, 15 skipped

> Task :test FAILED
BUILD FAILED in 5m 6s
```

**All 9 failures are identical and out of scope.** Parsed from the fresh result XMLs
(`backend/build/test-results/test`, all 299 files at a single mtime 21:08:01 — one run):

- The only class with failures is `org.danteplanner.backend.architecture.FeatureBoundaryTest`
  (9 failures, 0 errors). Every one is `java.lang.VirtualMachineError: Out of space in
  CodeCache for adapters` — a JVM resource exhaustion, not an ArchUnit rule violation.

Attribution (why this is not a 039 regression):
1. All 9 failures are the identical `VirtualMachineError: Out of space in CodeCache` in
   `FeatureBoundaryTest` — a package-dependency ArchUnit test that does not exercise auth/JWT.
2. `FeatureBoundaryTest` **passes in isolation** — scoped rerun
   `test --tests …FeatureBoundaryTest` → `BUILD SUCCESSFUL`. The failure is an artifact of
   piling all 1100 tests through the parallel forks in one invocation.
3. The test-JVM tuning that starves CodeCache (`jvmArgs("-XX:TieredStopAtLevel=1",
   "-XX:+UseParallelGC")` with no `ReservedCodeCacheSize`) is a **pre-existing working-tree
   modification of `backend/build.gradle.kts`** — baseline dirt recorded in all three phase
   ledgers, in **none** of the three 039 commits.
4. A 039-introduced boundary breach would surface as an ArchUnit **rule-violation assertion**,
   never as CodeCache exhaustion. The failure mode alone proves it is not a 039 regression.

**In-scope certification (from this run's XMLs):** every in-scope class executed with non-zero
counts and 0 failures/0 errors — no false green from the containerized trap (manifest followUp #3):

| Class (nested) | tests | fail | err |
|---|---|---|---|
| JwtTokenServiceTest$TypedValidatorTypeEnforcementTests | 6 | 0 | 0 |
| JwtTokenServiceTest$NeutralValidateTokenTypeAgnosticTests | 2 | 0 | 0 |
| JwtAuthenticationFilterTest$TokenTypeEnforcementTests | 2 | 0 | 0 |
| JwtAuthenticationFilterTest$RefreshPathTokenTypeEnforcementTests | 1 | 0 | 0 |
| RefreshRotationServiceTest$FamilyRevocation (containerized) | 4 | 0 | 0 |
| AuthenticationFacadeTest$TypedParserRealTokenTests | 4 | 0 | 0 |
| AuthControllerLogoutAllTest$SuccessTests (containerized) | 2 | 0 | 0 |

**Skips:** the 15 skips are `VoteNotificationFlowTest` (9) + `PlannerRepositoryConstraintTest`
(6) — out-of-scope, pre-existing, and not crash artifacts. **No in-scope test was skipped.**

## Static Set

Command (forced fresh, first-hand):

```
backend/gradlew -p backend checkstyleMain checkstyleTest checkstyleServiceJavadoc --rerun-tasks
```

Verdict: `BUILD SUCCESSFUL` — all three tasks executed (not UP-TO-DATE), 0 errors. (95 checkstyle
**warnings** are pre-existing project-wide and non-failing; checkstyle fails only on errors.)

**errorprone**: NOT run first-hand this pass — `compileJava`/`compileTestJava` were `UP-TO-DATE`
on every invocation this session, so errorprone emitted no fresh output. Its clean verdict is
**inherited** from the phase-01/02/03 closes (each ledger records the errorprone-bearing test
build green), not certified first-hand here.

## Benchmark

None named.

## Trace

Routing table (mechanics §2) — all nine external `.validateToken(` sites confirmed correct by
grep of `backend/src/main`:

| # | Call site | Handles | Routes to | OK |
|---|---|---|---|---|
| 1 | JwtAuthenticationFilter.java:126 | access | validateAccessToken | ✓ |
| 2 | JwtAuthenticationFilter.java:237 | refresh | validateRefreshToken | ✓ |
| 3 | AuthenticationFacade.java:159 | refresh | validateRefreshToken | ✓ |
| 4 | AuthenticationFacade.java:248 | access | validateAccessToken | ✓ |
| 5 | AuthenticationFacade.java:259 | refresh | validateRefreshToken | ✓ |
| 6 | AuthenticationFacade.java:291 | access | validateAccessToken | ✓ |
| 7 | RefreshRotationService.java:188 | presented refresh | validateRefreshToken | ✓ |
| 8 | RefreshRotationService.java:219 | successor refresh | validateRefreshToken | ✓ |
| 9 | RefreshRotationService.java:241 | stored refresh | validateRefreshToken | ✓ |
| — | JwtTokenService.java:200 (getTokenType) | reads type | validateToken (neutral) | ✓ |

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| Refresh JWT in access cookie → no auth, guest | INV1 / DoneWhen#1 | MET | JwtAuthenticationFilter.java:126 `validateAccessToken` | JwtAuthenticationFilterTest$TokenTypeEnforcementTests:436 `..RefreshTypedTokenInAccessCookie_DoesNotAuthenticate` (pass) |
| Wrong-type token → `INVALID_TYPE` log `TOKEN_INVALID (INVALID_TYPE)` | INV5 / DoneWhen#1 | MET | JwtAuthenticationFilter.java:189 `logSecurityEvent(errorCode + " (" + reason + ")")` | JwtAuthenticationFilterTest$TokenTypeEnforcementTests:453 `..LogsInvalidTypeReason` (pass) |
| Access JWT on rotation → rejected, no successor, family untouched | INV2 (rotation) / DoneWhen#2 | MET | RefreshRotationService.java:188 `validateRefreshToken` + :196 `!isRefreshToken()` guard | RefreshRotationServiceTest$FamilyRevocation:359 `rotate_WhenAccessTokenClaims_RejectedAndFamilyUntouched` (pass, containerized executed) |
| Access JWT on flag-off auto-refresh → validated as refresh, guest, no rotation | INV2 (filter) / DoneWhen#2 | MET | JwtAuthenticationFilter.java:237 `validateRefreshToken` | JwtAuthenticationFilterTest$RefreshPathTokenTypeEnforcementTests:475 (interaction-verify `validateRefreshToken` + guest/no-rotation anchors — reroute is externally identical since the pre-existing `!isRefreshToken()` guard already rejected; legitimate MET, phase-02 S3) (pass) |
| Typed parsers reject opposite type; neutral accepts both | INV3 / DoneWhen#3 | MET | JwtTokenService.java:64-73 access/refresh/neutral parsers; :127/:132 typed validators | JwtTokenServiceTest$TypedValidatorTypeEnforcementTests S1-S4 (:395-438) + $NeutralValidateTokenTypeAgnosticTests (pass) |
| `IncorrectClaim/MissingClaim` → `INVALID_TYPE` before generic JwtException→MALFORMED | Decision 4 / mechanics §3 / DoneWhen#3 | MET | JwtTokenService.java:236-239 catch order | JwtTokenServiceTest$TypedValidatorTypeEnforcementTests:458 `acceptance_WhenWrongTypeToken_MapsToInvalidTypeNotMalformed` (pass) |
| Migration round-trip: generate→typed-validate per type; successor/stored still validate | INV4 / DoneWhen#4 | MET | JwtTokenService generators :88/:109 + typed parsers | JwtTokenServiceTest$TypedValidatorTypeEnforcementTests:443 `..RoundTripWithCorrectClaims` + RefreshRotationServiceTest$Retry/$HappyPath (successor :219 / stored :241, containerized pass) |
| `TOKEN_INVALID` WARN carries specific Reason (delivered) | INV6 / DoneWhen#5 | MET | JwtAuthenticationFilter.java:189 | JwtAuthenticationFilterTest$InvalidTokenHandlingTests (C-B3 malformed/bad-sig reason pin, pass) |
| Each facade site rejects wrong type, real tokens (not stubs) | DoneWhen#2 / TW6 | MET | AuthenticationFacade.java:159/248/259/291 typed validators | AuthenticationFacadeTest$TypedParserRealTokenTests:614/629/640/650 (TW6a-d, uses `realTokenService.generate*Token`, confirmed real tokens; pass) |
| All existing tests pass (esp. SecurityIntegrationTest) | DoneWhen#6 | MET* | — | SecurityIntegrationTest$Rbac/Cors/RateLimit executed 0-fail; all in-scope green. *Full-suite gate itself blocked by out-of-scope CodeCache — see Gaps |
| B1 valid access → ROLE_NORMAL | Behavior Inv. | MET | filter :126-161 | JwtAuthenticationFilterTest$ActiveUserAuthenticationTests (char, pass) |
| B2 expired access → silent auto-refresh | Behavior Inv. | MET | filter :167-185 | JwtAuthenticationFilterTest `doFilterInternal_WhenExpiredToken_AttemptsAutoRefresh`:366 (pass) |
| B3 malformed/bad-sig → WARN + guest (now with reason) | Behavior Inv. | MET | filter :186-191 | JwtAuthenticationFilterTest$InvalidTokenHandlingTests (pass) |
| B4 refresh JWT → authenticate NORMAL | Behavior Inv. (dropped) | MET | dropped by :126 `validateAccessToken` | No test asserts the old B4 authenticate; replaced by INV1 S1:436 (dropped behavior genuinely gone) |
| B5 null access cookie → auto-refresh/guest | Behavior Inv. | MET | filter :106-122 | JwtAuthenticationFilterTest$NoTokenTests (pass; followUp #2: `verify(never()).validateToken`@:231 now vacuous post-reroute but the guest assertion is intact — noted, not a defect) |
| B6 revoked → TOKEN_REVOKED, guest | Behavior Inv. | MET | filter :163-166 | JwtAuthenticationFilterTest$InvalidatedUserTests (pass) |
| B7 sentinel id=0 → blocked | Behavior Inv. | MET | filter :142-146 | JwtAuthenticationFilterTest$SentinelUserProtectionTests (pass) |
| B8 neutral parser type-agnostic | Behavior Inv. | MET | JwtTokenService.java:122 `validateToken(neutralParser)` | JwtTokenServiceTest$NeutralValidateTokenTypeAgnosticTests (pass) |
| B9 catch-order EXPIRED/MALFORMED/INVALID_SIGNATURE/MISSING_CLAIMS | Behavior Inv. | MET | JwtTokenService.java:230-239 | JwtTokenServiceTest$ValidateTokenTests (pass) |
| B10 successor/stored self-minted refresh validate | Behavior Inv. | MET | RefreshRotationService.java:219/241 | RefreshRotationServiceTest$Retry/$HappyPath (containerized pass) |
| B11 presented refresh + `isRefreshToken` belt-and-suspenders | Behavior Inv. | MET | RefreshRotationService.java:188/196 | RefreshRotationServiceTest$FamilyRevocation (pass) |

## Coverage Audit

Every requirement, Done-When item, invariant, and Behavior-Inventory row traces to implementing
code pinned by a phase's test (spot-checked against the ledgers and re-run this pass — the two
containerized classes were independently confirmed executed with non-zero counts). No
requirement is untraced. The Deferred item (self-healing cookie clear / WARN-spam) is disclosed
scope-out, not a gap.

## Gaps

- UNTESTABLE — Full suite (`backend/gradlew -p backend test`) as commanded cannot produce a
  green single-invocation run in this environment: `VirtualMachineError: Out of space in
  CodeCache for adapters` (×9) in the out-of-scope `FeatureBoundaryTest` ArchUnit class. NOT a
  039 defect — the class passes in isolation, the CodeCache-starving `build.gradle.kts` tuning
  is pre-existing baseline dirt in none of the three 039 commits, and a real boundary breach
  would be a rule-violation assertion, not a VM error. Smallest closing action is a harness/env
  decision (raise `ReservedCodeCacheSize`, lower forks, or accept the isolated-pass evidence),
  not any change to task 039 source or tests. Orchestrator's call.

## Behavioral

**Verdict: PASS** — all four changed flows driven end-to-end at the real HTTP surface against
the branch binary (image built from `build/039-jwt-token-type-enforcement` at 5c2fbfa3, run as a
side-by-side container `dp-backend-039verify` on the local compose network at 127.0.0.1:18080,
sharing the stack's MySQL/Redis/test-keys; the long-running `danteplanner-backend:local`
container predates the branch and was not touched). Tokens minted with the stack's own test
RS256 keypair, exact `buildToken` claim shape, subject = test user 4.

| # | Step | Observed |
|---|------|----------|
| 1 | ✅ access JWT in `accessToken` cookie → `GET /api/auth/me` | HTTP 200, user-4 profile JSON (authenticated) |
| 2 | ✅ **refresh JWT in `accessToken` cookie** (the closed B4 hole) | HTTP 200 **empty body** (guest, never authenticated) + WARN `Security event: TOKEN_INVALID (INVALID_TYPE)` |
| 3 | 🔍 garbage in `accessToken` cookie | guest + WARN `TOKEN_INVALID (MALFORMED)` (B3/INV6 preserved live) |
| 4 | 🔍 expired access + **access JWT in `refreshToken` slot** | no `accessToken`/`refreshToken` Set-Cookie minted — wrong-type token cannot drive rotation (INV2 live) |
| 5 | ✅ expired access + valid refresh → auto-refresh | HTTP 200 user JSON + fresh server-minted `accessToken`(900s)/`refreshToken`(7d) cookie pair — full rotation, unknown-family refresh accepted via the legacy branch (INV4 live) |
| 6 | 🔍 `POST /api/auth/logout` (204) then replay the access token | guest + WARN `Security event: TOKEN_REVOKED` (B6 blacklist live) |

Log captures (container stdout, verbatim):
```
12:26:36.579 WARN JwtAuthenticationFilter : Security event: TOKEN_INVALID (INVALID_TYPE) - IP: 172.18.0.1, URI: /api/auth/me, UA: curl/8.21.0
12:26:36.595 WARN JwtAuthenticationFilter : Security event: TOKEN_INVALID (MALFORMED) - IP: 172.18.0.1, URI: /api/auth/me, UA: curl/8.21.0
12:27:57.206 WARN JwtAuthenticationFilter : Security event: TOKEN_REVOKED - IP: 172.18.0.1, URI: /api/auth/me, UA: curl/8.21.0
```

Findings from the drive:
- `/api/auth/me` answers guests with HTTP 200 + empty body rather than 401 — status-blind
  clients cannot distinguish guest from authenticated; pre-existing design, noted only.
- The `.claude/skills/route-tester` skill documents a different codebase entirely (Node/Keycloak,
  `test-auth-route.js`, foreign paths) — unusable rot for this repo's cold start.
- Residue: the drive left a rotation family + logout blacklist entries for test user 4 in the
  shared auth Redis (TTL-bound, ≤7 days) and one used refresh family; no schema or data changes.
- Verify-instance bring-up needed three values absent from `.env` interpolation (`JWT_PRIVATE/
  PUBLIC_KEY_PATH`, `JWT_ENCRYPTION_KEY`) — the running stack receives them out-of-band.
