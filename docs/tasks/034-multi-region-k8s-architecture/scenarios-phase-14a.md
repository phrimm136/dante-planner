# Phase 14a Scenario Ledger: Auth-Redis read-local / write-global split

Dossiers: /home/user/.local/state/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/phase-14a/

## Topology decision (list revision, pre-burndown)
Option B (advisor-endorsed): `CausalHarnessSupport` **aliases** `redis.auth-local` to the SAME
`AUTH_REDIS` container (one added binding), so harness IT subclasses (e.g. `TombstoneRollbackIT`)
that write-then-read through the service stay green with zero edits. The split is exercised by a
STANDALONE `AuthLocalReadSplitIT` (own auth + auth-local containers), NOT by putting a second
container in the harness (which would break `TombstoneRollbackIT` and force a fragile same-key
`@DynamicPropertySource` re-registration). Falls under mechanics §9.5 (Testcontainers bootstrap =
plan-time latitude) and the plan's sanctioned "alias auth-local==auth" option.

Load-bearing insight: the aliasing nested default `${AUTH_LOCAL_REDIS_HOST:${AUTH_REDIS_HOST:localhost}}`
resolves against ENV VARS, not the Spring `redis.auth.host` PROPERTY. Tests that set `redis.auth.*`
via `@DynamicPropertySource` do NOT get free auth-local aliasing — auth-local falls to localhost:6379.
So every container test that binds `redis.auth.*` and reads back through the service must also bind
`redis.auth-local.*` to the same endpoint (DegradationIT, AuthControllerLogoutAllTest).

## Acceptance
- Test: `integration/AuthLocalReadSplitIT::blacklistReadWriteSplit_bl_and_uinv` — the blacklist
  read/write split (bl: + uinv:). Opened RED (assertion, `AuthLocalReadSplitIT.java:115`,
  `AssertionFailedError "[auth-local has not caught up ...] Expecting value to be false but was true"`,
  errors=0). Closes GREEN when the blacklist reads reroute to `authLocalStringRedisTemplate` (S2 green).

## Scenarios
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | authLocalRedisConnectionFactory bean is a distinct instance (RedisFactoriesIT) | closed | assertion: RedisFactoriesIT:59 not-null | green: RedisFactoriesIT 2/0/0 |
| 2 | blacklist bl:+uinv: write→auth / read→auth-local, stale-then-caught-up (ACCEPTANCE) | closed | assertion: AuthLocalReadSplitIT:115 stale-read-false | green: AuthLocalReadSplitIT 1/0/0 + consumer set (TokenBlacklistServiceTest 22/0/0, AuthControllerLogoutAllTest 6/0/0, RefreshRotationServiceTest 15/0/0, DegradationIT 4/0/0, SsePublisherTest, TombstoneRollbackIT, ReplicaLagIT 5/0/0, ShedLockMultiPodTest 3/0/0, CausalHarnessIT 2/0/0) |
| 3 | tombstone del: write→auth / read→auth-local, stale-then-caught-up | closed | assertion: AuthLocalReadSplitIT:168 stale-read-false | green: AuthLocalReadSplitIT 2/0/0 + TombstoneRollbackIT 1/0/0 |
| 4 | fail-open on both read paths when auth-local unreachable (dead-port direct-construct) | closed | bite: AuthLocalReadSplitIT:207 isFalse-fails-when-aliased-to-auth | green: AuthLocalReadSplitIT 3/0/0 (regression-guard, non-vacuous by construction: token on auth returns false only via auth-local fail-open) |
| 5 | single-region aliasing: non-default AUTH_REDIS_*, auth-local unset → auth-local follows auth | closed | bite: AuthLocalAliasingTest:61 flat-default→33355≠6379 | green: AuthLocalAliasingTest 1/0/0 |

## Burndown order (dependency-driven)
S1 (config foundation: authLocal Endpoint + factory bean + non-@Primary authLocalStringRedisTemplate
+ application.properties nested-default aliasing) → S2/acceptance (blacklist reroute; signature
migration) → S3 (tombstone reroute) → S4 (fail-open) → S5 (aliasing).

## Signature-migration mechanic (S2)
Adding the auth-local template as a 2nd constructor arg to `TokenBlacklistService` breaks 5 direct
`new TokenBlacklistService(...)` sites in `TokenBlacklistServiceTest` (compile) and needs auth-local
bindings in standalone read-back ITs. Split across agents: tdd-green makes the production change and
verifies `compileJava` (main) only; a tdd-red legacy-fixup batch updates the existing test files
(same template both args incl. the mock at 413/433; DegradationIT + AuthControllerLogoutAllTest
add `redis.auth-local.*` = their auth endpoint; audit RefreshRotationServiceTest). Scoped suite green
gates the pair.

## List Revisions
- pre-burndown: topology option B chosen (see above).
- S2 (empirical): the plan assumed Spring Boot's auto `stringRedisTemplate` (name-guarded) survives adding
  `authLocalStringRedisTemplate`. In Spring Boot 4.0.0-SNAPSHOT it does NOT — adding a `StringRedisTemplate`
  bean SUPPRESSES the auto one (guard is effectively by-type), COLLAPSING every unqualified
  `StringRedisTemplate` consumer (blacklist writes/scan, ContentTombstoneStore write, RefreshRotationService,
  SsePublisher) onto the sole remaining template = auth-local. Proven by AuthLocalReadSplitIT: line 108
  (write visible) passes AND line 115 (auth-local read sees it) fails → single-store collapse. FIX: define an
  EXPLICIT `@Primary StringRedisTemplate stringRedisTemplate()` bound to `authRedisConnectionFactory()` in
  RedisConnectionConfig (deterministic write-injection, the plan's stated intent). Verify the FULL Redis-consumer
  set (RefreshRotationServiceTest, SsePublisherTest, TombstoneRollbackIT, ReplicaLagIT, ShedLockMultiPodTest,
  CausalHarnessIT + scoped 3 + Degradation/LogoutAll), not just the scoped 3.

## Pipeline (post-burndown)
- burndown close (independent full run by phase manager): `./gradlew -p backend test` = 1070 tests, 1 failure
  (`TestNamingConventionTest` — 4 new methods missed the `name_When_Expected` rule); fixed by a mechanical
  rename fixup (RedisFactoriesIT/AuthLocalReadSplitIT/AuthLocalAliasingTest), re-verified green
  (TestNamingConventionTest 1/0/0 + renamed suites green). Post-fix full suite = 1070/1070 (rename is
  call-site-free, so isolated). Acceptance closed GREEN at S2.
- refactor: nothing to consolidate — the burndown code is already idiomatic (authLocal mirrors sseLocal; each
  service reroute is a minimal qualified-read change; the explicit @Primary write template is the required
  remedy, not debt). Extracting the 4-factory duplication would modify pre-existing auth/rateLimit/sseLocal
  beans = scope creep (CLAUDE.md rule #1). No tdd-refactor spawn.
- verify: PASS after 1 round — verification.md Phase 14a. All 4 contract clusters + read-local/write-global
  (L46) + fail-open + INV4/INV6 MET with cited tests/code; scoped suite 6/0/0.
- capture: meme draft --from-phase invoked for task path + phase-14a dossier dir (gitignored drafts, no
  stdout); sweep classified surfaced candidates — all still-true (this phase is additive; no documented fact
  invalidated), 0 stale docs, 0 obsolete facts.
- staged: 15 files (git add explicit paths) — TokenBlacklistService, RedisConnectionConfig, ContentTombstoneStore,
  application.properties; AuthLocalReadSplitIT (new), RedisFactoriesIT, CausalHarnessSupport, DegradationIT,
  AuthControllerLogoutAllTest, TokenBlacklistServiceTest, AuthLocalAliasingTest (new); plan/phase-14a, ledger,
  verification.md, status.json. 538 insertions(+), 12 deletions(-). Pre-existing unrelated working-tree edits
  (mechanics.md, requirements.md, RefreshRotation*, JwtAuthenticationFilterLineageTest, repo-wide) NOT staged.
