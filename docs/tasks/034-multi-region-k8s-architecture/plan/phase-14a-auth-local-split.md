# Phase 14a: Auth-Redis read-local / write-global split

- Files:
  - `backend/src/main/java/org/danteplanner/backend/shared/config/RedisConnectionConfig.java` — add `@Valid Endpoint authLocal`, `authLocalRedisConnectionFactory()` bean (mirror of `sseLocalRedisConnectionFactory()`), and a NON-`@Primary` qualified `StringRedisTemplate` bean `authLocalStringRedisTemplate` bound to that factory.
  - `backend/src/main/resources/application.properties` — add `redis.auth-local.host` / `redis.auth-local.port` with the nested-default aliasing below (after the existing `redis.sse-local.*` block, lines 62-63).
  - `backend/src/main/java/org/danteplanner/backend/auth/token/TokenBlacklistService.java` — inject the qualified auth-local template as a SECOND constructor arg; reroute the two READ methods (`isBlacklisted`, `isUserTokenInvalidated`) to it. Keep the existing `@Primary` `stringRedisTemplate` for every write and for the scan/size/clear helpers.
  - `backend/src/main/java/org/danteplanner/backend/shared/readpath/ContentTombstoneStore.java` — inject the qualified auth-local template as a SECOND constructor arg; reroute the READ method (`isTombstoned`) to it. Keep `@Primary` `stringRedisTemplate` for `writeTombstone`.
- Tests:
  - `backend/src/test/java/org/danteplanner/backend/integration/CausalHarnessSupport.java` — add a second `RedisContainer` (`AUTH_LOCAL_REDIS`, the read-local/replica stand-in), start it in the static block, and bind `redis.auth-local.host` / `redis.auth-local.port` to it in `primaryDatasourceProperties`.
  - `backend/src/test/java/org/danteplanner/backend/integration/RedisFactoriesIT.java` — extend with the scenario-1 auth-local factory-distinctness assertion.
  - `backend/src/test/java/org/danteplanner/backend/integration/AuthLocalReadSplitIT.java` — NEW: scenarios 2, 3, 4 (blacklist split, tombstone split, fail-open on both read paths).
  - `backend/src/test/java/org/danteplanner/backend/shared/config/AuthLocalAliasingTest.java` — NEW: scenario 5 (single-region default aliasing; no containers, a scoped property context).

- External contract:
  1. A `LettuceConnectionFactory` bean `authLocalRedisConnectionFactory` exists and is a DISTINCT instance from `authRedisConnectionFactory`.
  2. The blacklist READ (`isBlacklisted`, and the `uinv:` read `isUserTokenInvalidated`) and the tombstone READ (`isTombstoned`) are served from the **auth-local** endpoint; their corresponding WRITES (`blacklistToken`/`blacklistTokenForRotation`/`invalidateUserTokens`; `writeTombstone`) land on the **auth** (`@Primary`) endpoint. Observable end-to-end: a value written through the service is present on the auth container yet invisible via the service's read path until the same key also exists on auth-local ("caught up"), at which point the read reflects it.
  3. Both rerouted read paths preserve fail-open: when auth-local is unreachable, `isBlacklisted` returns not-blacklisted (and increments `blacklist_check_skipped_total`) and `isTombstoned` returns not-tombstoned.
  4. Single-region no-op: with `AUTH_LOCAL_REDIS_HOST`/`AUTH_LOCAL_REDIS_PORT` unset, the resolved auth-local endpoint equals the resolved auth endpoint (aliasing), so behavior is unchanged from today.
  These are the four assertion clusters the acceptance tests bind to; internals (template bean names, container wiring) are not part of the contract.

- Mechanics sections:
  - `mechanics.md §7` — "Blacklist read ladder: local Redis → (Seoul) stale-but-honest replica → all unavailable: fail-open + `blacklist_check_skipped_total`". This phase adds the `local replica` rung as the auth-local endpoint; the fail-open terminus is unchanged.
  - `mechanics.md §4` — SSE split is the pattern being followed: publish/write on the `@Primary` factory, subscribe/read on a `local` factory. Auth-local mirrors `sseLocal`.
  - `mechanics.md §1` — the `bl:` / `uinv:` / `del:` key layouts whose reads move (rows 1, 2, 4).
  - `mechanics.md §9.5` — `@Qualifier` names + Testcontainers bootstrap are explicitly PLAN-TIME; `authLocalStringRedisTemplate` and the `AUTH_LOCAL_REDIS` container wiring are chosen here within that latitude.

- Considerations:
  - `requirements.md` L46 (read-local/write-global) + L62 (blacklist local-replica read, fail-open bounded by lag) — the binding decisions this phase realizes; the read must hit the local replica while the write stays global, and the staleness window is a deliberate tradeoff already covered by fail-open, so the test must make the window EXPLICIT (stale read returns false before catch-up), not hide it.
  - `phase-14-notes.md §"#3 the auth-Redis read/write split"` — the fourth endpoint (`auth-local`) mirroring `sseLocal`; this is a no-op in single-region Oregon (auth-local == auth) and only becomes semantically live behind the Seoul replica.
  - `requirements.md` INV6 (blacklist fail-open) — the invariant the rerouted read must still uphold; it breaks if a rerouted read propagates a `DataAccessException` instead of failing open, which would 500 authed reads during an auth-local outage. Both read methods already catch `DataAccessException` → `failOpen`; the reroute must not narrow that catch.
  - `requirements.md` INV4 / `mechanics.md §8` (no timing constants) — the split proof must use a checkable condition, never a sleep: "not caught up" = the key is absent on auth-local; "caught up" = the test SETs the key on auth-local. No duration anywhere. REPLICAOF is the production catch-up mechanic; the test simulates catch-up by mirroring the key (the endpoint split is this phase's contract, not Redis replication fidelity — that is out-of-scope infra).
  - Aliasing encoding (load-bearing): `redis.auth-local.host=${AUTH_LOCAL_REDIS_HOST:${AUTH_REDIS_HOST:localhost}}` and `redis.auth-local.port=${AUTH_LOCAL_REDIS_PORT:${AUTH_REDIS_PORT:6379}}`. A flat `:localhost` default would silently break the single-region no-op the instant `AUTH_REDIS_HOST` is set alone (auth points at the data node, auth-local at localhost). The nested default is what makes scenario 5's safe-no-op guarantee true. Spring's placeholder resolver supports nested defaults.
  - Deterministic write-injection wiring: the auto-configured `@Primary` `StringRedisTemplate` bean (Spring Boot `RedisAutoConfiguration`, guarded by `@ConditionalOnMissingBean(name="stringRedisTemplate")`) stays bound to the auth factory and continues to serve writes. Name the new bean `authLocalStringRedisTemplate` (NOT `stringRedisTemplate`, or it would suppress the auto bean) and mark it NON-`@Primary`; inject it into the two services via an explicit `@Qualifier`, leaving the existing by-type write injection resolving to the `@Primary` auth bean. Constructor injection only (no field injection, per backend conventions).
  - `isUserTokenInvalidated` shares the one injected auth-local template with `isBlacklisted` (same `bl:`/`uinv:` read store), so the blacklist split proof (scenario 2) covers its wiring by construction; scenario 2 asserts both the `bl:` and `uinv:` read paths.
  - Why scan/size/clear stay on auth: `size()`, `userInvalidationSize()`, `clear()` (via `RedisKeyScanner`) and `clearUserInvalidation` are maintenance/monitoring against the AUTHORITATIVE write store; moving them to the lagging read replica would report/clear a stale view. Do not "helpfully" reroute them.

- Depends on: none (all four current-surface classes and the `CausalHarnessSupport` harness already exist and are mapped verbatim above).

- Verify (ATDD: write the failing scenarios first, then implement):

  Scenario 1 — factory distinctness (extend `RedisFactoriesIT`):
  autowire `@Qualifier("authLocalRedisConnectionFactory") LettuceConnectionFactory`; assert non-null and `isNotSameAs(authRedisConnectionFactory)` and `isNotSameAs(rateLimitRedisConnectionFactory)`.

  Scenario 2 — blacklist read/write split (`AuthLocalReadSplitIT`):
  call `tokenBlacklistService.blacklistToken(token, futureExpiry)` (→ auth write); assert the `bl:<hash>` key IS present on the auth container (scan/read via the `@Primary` template) — write landed on auth; assert `isBlacklisted(token)` is `false` — auth-local has not seen it (staleness window made explicit); mirror the live `bl:*` keys from auth to auth-local (SET them via the auth-local template = "caught up"); assert `isBlacklisted(token)` is now `true`. Repeat the same write-here/read-there proof for the `uinv:` pair via `invalidateUserTokens(userId)` + `isUserTokenInvalidated(userId, olderIssuedAt)`.

  Scenario 3 — tombstone read/write split (`AuthLocalReadSplitIT`):
  call `contentTombstoneStore.writeTombstone("planner", id)` (→ auth write); assert `del:planner:<id>` present on auth; assert `isTombstoned("planner", id)` is `false` (auth-local not caught up); mirror the key to auth-local; assert `isTombstoned("planner", id)` is now `true`.

  Scenario 4 — fail-open on both read paths when auth-local is unreachable (`AuthLocalReadSplitIT`):
  point the auth-local endpoint at an unreachable target (PLAN-TIME wiring: a Toxiproxy timeout toxic fronting auth-local, mirroring the harness's `app-to-primary` proxy shape, OR a scoped context whose `redis.auth-local.port` is a dead port); assert `isBlacklisted(token)` returns `false` and `blacklist_check_skipped_total` incremented, and `isTombstoned("planner", id)` returns `false` — neither read propagates the `DataAccessException`.

  Scenario 5 — single-region aliasing (`AuthLocalAliasingTest`, no containers):
  boot a context with `redis.auth.host`/`redis.auth.port` set (via `AUTH_REDIS_*` or the properties) and `redis.auth-local.*` UNSET; assert the resolved `authLocalRedisConnectionFactory` host/port equal the `authRedisConnectionFactory` host/port. (Reads the factories' `RedisStandaloneConfiguration`.)

  Scoped run command:
  `./gradlew -p backend test --tests 'org.danteplanner.backend.integration.RedisFactoriesIT' --tests 'org.danteplanner.backend.integration.AuthLocalReadSplitIT' --tests 'org.danteplanner.backend.shared.config.AuthLocalAliasingTest'`
  (redirect output per CLAUDE.md: `> /tmp/backend-<session-id>-authlocal.log 2>&1`). Then the full suite `./gradlew -p backend test` must stay green — the reroute must not regress existing `TokenBlacklistService` / tombstone / `ReplicaLagIT` tests (the auth-local container defaults to a live empty Redis, so pre-existing reads that expect a hit must be re-examined: any existing test that writes via the service then reads back now needs the value on auth-local too — audit `RefreshRotationServiceTest`, `JwtAuthenticationFilterLineageTest`, and any blacklist read test, and update their harness wiring to mirror or to alias auth-local==auth).
