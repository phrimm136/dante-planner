# Phase 7 Scenario Ledger: Rate limiter → bucket4j-redis on local ephemeral Redis

Dossiers: ~/.local/state/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/phase-7/

Behavior-preserving refactor (no INV). Contract: per-IP/per-user/per-endpoint 429 parity,
buckets live in the LOCAL ephemeral rate-limit Redis (never the auth Redis) with key-TTL
eviction replacing the hand-rolled ConcurrentHashMap + 5-min @Scheduled eviction + maxBuckets LRU.

## Acceptance
- Test: RateLimitConfigTest::rateLimitBucket_AfterConsume_PersistsInLocalRedisWithTtl — opened compile-red (deferred) [log /tmp/tdd-red-1783770225.log: :compileTestJava FAILED, symbols buildRateLimitProxyManager + RateLimitConfig(ProxyManager) across 3 call sites]; assertion-red logged after scenario 1 green [results XML LocalRedisPersistenceTests: AssertionError "Expecting actual: -1L to be greater than: 0L" — getExpire=-1, TTL strategy unwired]; closed GREEN at scenario 2

## Scenarios
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | Redis-backed 429 parity via injected bucket4j ProxyManager (existing suite adapted) | closed | compile-red (deferred) — part of file compile failure | green: 28 tests, only TTL acceptance red; refillGreedy preserved (line 131); bucket4j-redis:8.10.1 dep added |
| 2 | Bucket keys persist in the local rate-limit Redis with a positive TTL (eviction replaced), absent from auth Redis — ACCEPTANCE | closed | assertion: getExpire=-1 (TTL unwired after S1) | green: ExpirationAfterWriteStrategy.fixedTimeToLive(bucketTtl); TTL 0<t≤2s; auth Redis empty; all 28 green |

## List Revisions
- [after scenario 1 green attempt 1] Rejected the green agent's production change `refillGreedy→refillIntervally`. It contradicts the behavior-preserving contract (original prod = refillGreedy) and weakens abuse protection (intervally permits a full burst right after each interval boundary). Root cause: `checkRateLimit_LargeCapacity_HandlesCorrectly` makes 1000 sequential Redis round-trips (~1-3s), during which greedy refills tokens (1000/60s ≈ 16/s), so the 1001st call succeeds and the exhaustion assertion fails. Under the old in-JVM impl the burst finished in microseconds so greedy never refilled mid-burst — the test carried an accidental speed assumption. Fix: keep prod `refillGreedy` (behavior-preserving); make the large-capacity test robust by widening its refill window (refillDurationSeconds 60 → 86400) so no token refills during the burst. Assertion + capacity unchanged. Re-spawned tdd-red (test) then tdd-green (restore greedy).

## Pipeline (post-burndown)
- independent run (my execution, step 3.2): `./gradlew -p backend test --tests "*RateLimitConfigTest" --tests "*TestNamingConventionTest"` → BUILD SUCCESSFUL in 16s, EXIT=0. RateLimitConfigTest 28 tests / 0 failures+errors; TestNamingConventionTest 1 ArchTest / 0 failures (new method names comply with the frozen regex). Log /tmp/phase7-independent-run.log.
- refactor: done — RateLimitConfig.java: extracted `consumeOrThrow(key, config, Supplier<RateLimitExceededException>)` helper (consolidated the triplicated consume-or-throw guard). RedisConnectionConfig untouched; no dead residue. Suite green + tests unchanged (diff 110/5 = RED agent's net only). BUILD SUCCESSFUL EXIT=0.
- verify: PASS after 1 round — verification.md Phase 7 (R1–R5 all MET; scoped suite RateLimitConfigTest 28 + TestNamingConventionTest 1, 0 failures).
- capture: SKIPPED per user (no meme draft/sweep)
- staged: 8 files, +274/-124 — build.gradle.kts, RateLimitConfig.java, RedisConnectionConfig.java, application.properties, RateLimitConfigTest.java, scenarios-phase-7.md, verification.md, status.json (git diff --cached --stat)
