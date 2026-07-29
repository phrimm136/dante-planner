# Phase 1 Scenario Ledger: Testcontainers causal harness + Redis wiring

## STATUS: DONE — all 6 scenarios closed, acceptance GREEN, existing suite unbroken.

## Topology decision (named per Phase 1 consideration)
Two `redis` containers in the harness — one auth Redis, one local ephemeral rate-limit Redis
(5 containers total: mysql-primary, mysql-replica, toxiproxy, auth-redis, ratelimit-redis).
Rationale: makes the "five containers" contract literally true and forward-fits INV6 (Phase 5
restarts the auth Redis to prove AOF-replay blacklist integrity while the rate-limit Redis is
"no AOF" by contract) without a Phase-5 re-plumb. Container COUNT is deliberately NOT asserted by
the acceptance test, so this stays reversible (collapsible to one container if boot cost hurts).

## Acceptance
- Test: `integration/CausalHarnessIT` (`replicationRoundTrip...` + `redisFactories_authAndRateLimit...`)
  — opened RED as a greenfield COMPILE failure (`cannot find symbol: ReplicationControl`,
  `package ...lettuce does not exist`). Ledger status: **compile-red (deferred)**. No assertion fabricated.
- **Deferred-red DISCHARGED at scenario 2**: `CausalHarnessIT > Replication round-trip ... FAILED —
  UnsupportedOperationException: replication pause is not yet wired`, while its redis-beans assertion
  PASSED in the same run — the acceptance test genuinely executing its round-trip and failing on the
  unbuilt `stopReplica` stub (real post-compile machinery-tied failure, NOT compile-red→green vacuity).
- **CLOSED GREEN at scenario 4** (/tmp/p1gate-37cf8c17-s4.log): `CausalHarnessIT: tests="2"
  failures="0" errors="0"`. Re-confirmed green at scenarios 5 and 6.

## Scenarios (reordered — beans moved to #2 to defeat the acceptance vacuity trap; see List Revisions)
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | basic replication: primary write visible on replica-qualified datasource after catch-up | closed | greenfield compile-red: `cannot find symbol CausalHarnessSupport/replicationControl` (ReplicationBasicIT) | green: ReplicationBasicIT PASS — write→awaitCaughtUp(GTID_SUBSET condition)→visible on replica-qualified JdbcTemplate |
| 2 | two LettuceConnectionFactory beans injectable by distinct qualifiers (loads acceptance context) | closed | post-compile `NoSuchBeanDefinitionException` for `@Qualifier("authRedisConnectionFactory")` LettuceConnectionFactory (RedisFactoriesIT) | green: RedisFactoriesIT PASS — two distinct beans in shared/config/RedisConnectionConfig (auth @Primary, rate-limit); harness boots 2 redis:7-alpine |
| 3 | stopReplica() halts propagation: post-stop primary write NOT visible on replica | closed | method-tied red: ReplicaStopIT baseline assertion PASSES (live link), then `stopReplica()` throws `UnsupportedOperationException` at line 52 | green: ReplicaStopIT PASS — stopReplica()→`STOP REPLICA` |
| 4 | startReplica()+awaitCaughtUp() resumes: withheld row appears on replica — flips acceptance GREEN | closed | method-tied red: ReplicaResumeIT baseline+stop+withheld-invisible all PASS, then `startReplica()` throws at line 57 | green: ReplicaResumeIT PASS + CausalHarnessIT acceptance GREEN (tests=2,failures=0) — startReplica()→`START REPLICA` |
| 5 | Toxiproxy `wan` (130ms latency on app→primary) applies and is removable | closed | greenfield compile-red: `cannot find symbol: toxiproxyControl` / `ToxiproxyControl` (ToxiproxyWanIT) | green: ToxiproxyWanIT PASS — ToxiproxyControl.applyWan()/removeWan() (130ms UPSTREAM latency toxic), asserted via `proxy.toxics().getAll()` present→absent |
| 6 | Toxiproxy `partition` (timeout toxic) applies and is removable | closed | greenfield compile-red: `cannot find symbol: applyPartition()/removePartition()` (ToxiproxyPartitionIT) | green: ToxiproxyPartitionIT PASS — applyPartition()/removePartition() (TIMEOUT toxic, UPSTREAM, 0ms black-hole), asserted present→absent; full IT suite regression GREEN |

## Final scoped suite (run by orchestrator; /tmp/p1gate-37cf8c17-s6.log)
```
BUILD SUCCESSFUL in 2m 2s
```
Per-class (0 failures, 0 errors, 0 skipped throughout):
- CausalHarnessIT: tests=2  (acceptance)
- ReplicationBasicIT: tests=1
- RedisFactoriesIT: tests=1
- ReplicaStopIT: tests=1
- ReplicaResumeIT: tests=1
- ToxiproxyWanIT: tests=1
- ToxiproxyPartitionIT: tests=1
Total: 8 tests, all green.

## Existing-suite regression (main added spring-data-redis + 2 Redis beans; /tmp/p1gate-37cf8c17-existing*.log)
Full-context boot + auth/security representative set, all green (0 failures):
- BackendApplicationTests: tests=1 (definitive full-context-load smoke test — proves the two new
  LettuceConnectionFactory beans + @Primary + spring-data-redis do not break context startup)
- AuthControllerTest (nested): 4 tests
- SecurityIntegrationTest (nested): 14 tests

## Cross-cutting constraints (as implemented)
- INV4: `ReplicationControl` API has NO duration params; `awaitCaughtUp()` = GTID_SUBSET status
  condition (never a wait); `stopReplica`=`STOP REPLICA`, `startReplica`=`START REPLICA`. Toxiproxy
  latency/timeout values are internal test-fixture toxic constants (mechanics §8/§9.6), kept OUT of
  public API signatures; profiles asserted via the Toxiproxy control API (toxic present/absent),
  never wall-clock. No Thread.sleep anywhere in the harness correctness paths.
- Replica observed through a replica-QUALIFIED datasource, never routing (Phase 2, absent).
- The Toxiproxy proxy fronts app→primary as an ADDITIVE path; the primary datasource is NOT rerouted
  through it, so the round-trip tests are unaffected (downstream phases opt in via appToPrimaryProxy).
- Build: `tasks.withType<Test>` filter includes `*IT` alongside `*Test`/`*Tests`/`*IntegrationTest`.
- Deps: `com.redis:testcontainers-redis:2.2.2`, `org.testcontainers:testcontainers-toxiproxy:2.0.4`
  (+ transitive `eu.rekawek.toxiproxy:toxiproxy-java:2.1.11`), `spring-boot-starter-data-redis`.
  `management.health.redis.enabled=false` in it/test profiles guards RedisAutoConfiguration.

## Deliverables on disk
- MAIN: `backend/src/main/java/org/danteplanner/backend/shared/config/RedisConnectionConfig.java`
  (two LettuceConnectionFactory beans, auth `@Primary`; props `redis.auth.{host,port}` /
  `redis.rate-limit.{host,port}`, lazy). `backend/src/main/resources/application.properties` (redis props).
- BUILD: `backend/build.gradle.kts` (redis/toxiproxy testcontainers modules, spring-data-redis, `*IT` filter).
- TEST harness UTILITIES: `integration/CausalHarnessSupport.java`, `integration/ReplicationControl.java`,
  `integration/ToxiproxyControl.java`; profiles `application-{it,test}.properties` (redis health guard).
- TEST classes (red-owned): `integration/CausalHarnessIT.java`, `ReplicationBasicIT.java`,
  `RedisFactoriesIT.java`, `ReplicaStopIT.java`, `ReplicaResumeIT.java`, `ToxiproxyWanIT.java`,
  `ToxiproxyPartitionIT.java`.

## Operational notes / send-backs
- scenario-1 green first FAILED the gate with `NoSuchBeanDefinitionException` (nested
  `@TestConfiguration` in a superclass is not auto-detected) — fixed by `@Import` on the base, re-gated green.
- Two green runs stalled and one hit a session limit BEFORE the no-advisor rule; gate mechanics then
  shifted to "agents verify compile only, orchestrator runs the containerized gate" — stable thereafter.
- Minor known duplication (not refactored — below interlude threshold): `ToxiproxyControl.removePartition()`
  body == `removeWan()` (both clear all toxics via `getAll()`); a shared `clearToxics()` is a later
  refactor-agent call.

## List Revisions
- [after scenario 1]: moved "two Redis beans" from #6 to #2 (stop/start → #3/#4, toxiproxy → #5/#6).
  Trigger: `CausalHarnessIT`'s class-level Redis autowiring couples the beans contract to the
  round-trip contract — beans-last would let the acceptance test go compile-red → green with its
  round-trip never executed (vacuity trap). Beans-early loads the context so the round-trip is
  genuinely exercised and observed failing before green. Validated at scenario 2 (deferred-red discharged).

## Refactor Interludes (if any)
- (none — only a single minor strain flag at scenario 6; two consecutive flags never occurred)
