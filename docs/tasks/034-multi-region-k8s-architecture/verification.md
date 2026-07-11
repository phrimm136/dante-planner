# Verification Report

## Overall: IN PROGRESS   ← Final scope sets PASS/FAIL; phase runs leave IN PROGRESS

## Phase 1: Testcontainers causal harness + Redis wiring — PASS

### Suite
Scoped command:
```
backend/gradlew -p backend test --tests 'org.danteplanner.backend.integration.*'
```
Verbatim tail (`/tmp/build-37cf8c17-793c-45ff-8912-6938db0e313f-p1-verify.log`):
```
> Task :test UP-TO-DATE
> Task :jacocoTestReport UP-TO-DATE

BUILD SUCCESSFUL in 1s
6 actionable tasks: 6 up-to-date
```
The `test` task reported UP-TO-DATE (inputs unchanged since the orchestrator's containerized gate
run earlier today), so the load-bearing evidence is the per-class result XML from that real run
(`backend/build/test-results/test/`, timestamps 2026-07-10T11:25–11:27, 100+s per IT class = live
containers). All Phase-1 harness classes: 0 failures, 0 errors, 0 skipped.

| Class | tests | fail | err |
|-------|-------|------|-----|
| CausalHarnessIT (acceptance) | 2 | 0 | 0 |
| ReplicationBasicIT | 1 | 0 | 0 |
| RedisFactoriesIT | 1 | 0 | 0 |
| ReplicaStopIT | 1 | 0 | 0 |
| ReplicaResumeIT | 1 | 0 | 0 |
| ToxiproxyWanIT | 1 | 0 | 0 |
| ToxiproxyPartitionIT | 1 | 0 | 0 |

Total Phase-1 harness: 8 tests, all green. Pre-existing integration classes in the same package
(MySQLIntegrationTest, BanEnforcementIntegrationTest, PlannerQueryCountTest, VoteNotificationFlowTest)
also green/skipped — no regression from the two new production Redis beans.

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| Harness boots mysql-primary + mysql-replica with GTID replication (`SOURCE_AUTO_POSITION=1`), redis, toxiproxy, app wired like a Seoul pod | plan.md Phase 1 external contract; mechanics.md §8 | MET | `CausalHarnessSupport.java:58-100` boots PRIMARY (`server-id=1`, `--gtid-mode=ON`, `--enforce-gtid-consistency=ON`), REPLICA (`server-id=2`), AUTH_REDIS, RATE_LIMIT_REDIS, TOXIPROXY; `wireReplication` `:176-181` issues `CHANGE REPLICATION SOURCE TO … SOURCE_AUTO_POSITION=1`; app wired via `@DynamicPropertySource:114-126` (binds `spring.datasource.*` to primary + `redis.auth/rate-limit` props) under full `@SpringBootTest` context | `CausalHarnessIT.replicationRoundTrip_…` (2 tests green) exercises the live replicating pair end-to-end |
| `ReplicationControl.{stopReplica,startReplica,awaitCaughtUp}` — NO duration params; `awaitCaughtUp` is a replication-status condition (INV4 gate) | requirements.md INV4; mechanics.md §8 | MET | `ReplicationControl.java:37` `awaitCaughtUp()` no params — terminates on `GTID_SUBSET(target, @@GLOBAL.gtid_executed)==1` (`:44-46`), `target` captured at entry (`:38`); `:79` `stopReplica()`→`STOP REPLICA`; `:83` `startReplica()`→`START REPLICA`. INV4 grep gate over all harness files: NO `Thread.sleep`/`Awaitility`/`await()`; the only `parkNanos` (`:49`) is an inter-poll busy-spin guard inside the status-condition loop, plus a `MAX_POLLS` liveness cap — neither is a fixed correctness delay nor a public-API duration param | `ReplicaStopIT` (stop halts propagation), `ReplicaResumeIT` (start+catch-up resumes), `CausalHarnessIT` — all green |
| Toxiproxy `wan` (130ms latency) and `partition` (timeout) profiles apply and remove | plan.md Phase 1 considerations; mechanics.md §8 | MET | `ToxiproxyControl.java:35-49` `applyWan/removeWan/applyPartition/removePartition` — no duration params; `applyWan` = `latency("wan", UPSTREAM, 130)`, `applyPartition` = `timeout("partition", UPSTREAM, 0)`; the 130ms/0ms are private fixture constants (`:24,:27`) | `ToxiproxyWanIT` + `ToxiproxyPartitionIT` assert `toxics().getAll()` non-empty after apply, empty after remove (control-API assertion, no wall-clock) — both green |
| Two `LettuceConnectionFactory` beans with distinct qualifiers exist and are injectable | mechanics.md §1, §9.5; plan.md "Redis connection topology" | MET | `RedisConnectionConfig.java:48-57` — `authRedisConnectionFactory` (`@Primary`) + `rateLimitRedisConnectionFactory`, bound from `redis.auth.*` / `redis.rate-limit.*` (`:42-46`), lazy connect | `RedisFactoriesIT` + `CausalHarnessIT.redisFactories_…` autowire both by `@Qualifier` and assert non-null + `isNotSameAs` — green |
| write→stop→invisible→start→awaitCaughtUp→visible round-trip proven by a test | plan.md Phase 1 external contract | MET | Sequence realized in `CausalHarnessIT.java:57-70`: insert+awaitCaughtUp→visible; `stopReplica`→insert→`countOnReplica==0`; `startReplica`+`awaitCaughtUp`→`countOnReplica==1` | `CausalHarnessIT` (2/2 green); complement `ReplicaResumeIT` pins the resume half in isolation |

### Gaps
- (none) — every in-scope item is MET; scoped suite green.

### Notes
- The acceptance test observes the replica through a replica-QUALIFIED `JdbcTemplate`, not through
  `AbstractRoutingDataSource` routing — routing is Phase 2 and correctly out of Phase-1 scope.
- Topology choice recorded in `scenarios-phase-1.md`: two `redis` containers (auth + rate-limit),
  container COUNT deliberately not asserted, so it stays reversible. Consistent with mechanics.md §1.
- Minor known duplication `ToxiproxyControl.removePartition()` body == `removeWan()` (both clear all
  toxics) — cosmetic, does not affect any contract.

## Phase 2: Datasource routing + pool ledger + read-path interception point — PASS

### Suite
Scoped command (re-run independently by the verifier):
```
backend/gradlew -p backend test \
  --tests 'org.danteplanner.backend.config.PoolLedgerConfigTest' \
  --tests 'org.danteplanner.backend.config.ReadOnlyRoutingDataSourceTest' \
  --tests 'org.danteplanner.backend.config.RoutingDataSourceConfigTest' \
  --tests 'org.danteplanner.backend.readpath.ByIdReadGuardTest' \
  --tests 'org.danteplanner.backend.controller.ByIdReadSeamTest' \
  --tests 'org.danteplanner.backend.integration.RoutingSeoulIT'
```
Verbatim tail:
```
> Task :test

> Task :jacocoTestReport

BUILD SUCCESSFUL in 1m 25s
6 actionable tasks: 2 executed, 4 up-to-date
```
EXIT=0. Per-class from `build/test-results/test/` XML (tests/skipped/failures/errors):

| Class | tests | skip | fail | err |
|-------|-------|------|------|-----|
| PoolLedgerConfigTest | 2 | 0 | 0 | 0 |
| ReadOnlyRoutingDataSourceTest | 2 | 0 | 0 | 0 |
| RoutingDataSourceConfigTest$SeoulPod | 2 | 0 | 0 | 0 |
| RoutingDataSourceConfigTest$OregonPod | 1 | 0 | 0 | 0 |
| ByIdReadGuardTest | 2 | 0 | 0 | 0 |
| ByIdReadSeamTest | 2 | 0 | 0 | 0 |
| RoutingSeoulIT (containerized) | 1 | 0 | 0 | 0 |

`PlannerControllerTest` (byId no-regression evidence for items 4/6) re-run separately →
BUILD SUCCESSFUL, EXIT=0, 84 tests all green including nested `GET /api/planner/md/{id}` (5),
`GET .../published/{id}` (5), and the list/search groups `GET /api/planner/md` List (4),
`.../published` (3), `.../recommended` (2) — unaffected by the seam wiring.

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| 1. INV9 config invariant fed by production `PoolLedger` constants (not copies); Σ(primary-hitting pools) × max pods ≤ max_connections − reserve | plan.md Phase 2 "own asserted contract"; mechanics §6; requirements INV9 | MET | `PoolLedger.java:11-16` constants; `PoolLedgerConfigTest.java:22-28` computes `(OREGON_PRIMARY_POOL + SEOUL_PRIMARY_POOL) * MAX_PODS_PER_ASG ≤ RDS_MICRO_MAX_CONNECTIONS − CONNECTION_RESERVE` → 50 ≤ 75, reading the same `PoolLedger` symbols `RoutingDataSourceConfig.java:44-56` reads (Seoul replica pool excluded — hits the separate Seoul replica RDS, not the shared Oregon primary) | `PoolLedgerConfigTest` 2/2 green |
| 2. §6 BINDING pool sizes: Oregon→primary 15, Seoul→primary 10, Seoul→replica 15; re-check bulkhead 2–3 correctly ABSENT (Phase 3) | mechanics §6 | MET | `PoolLedger.java:11-13` = 15/10/15; `RoutingDataSourceConfig.buildPrimaryHikariConfig` `:44-47` selects Seoul-10 when replica enabled else Oregon-15; `buildReplicaHikariConfig` `:55` = replica-15; no bulkhead constant present | `RoutingDataSourceConfigTest` SeoulPod×2 (`:49-60`) + OregonPod×1 (`:71-75`) assert sizes against ledger constants → 3/3 green; `PoolLedgerConfigTest.poolLedger_whenRead_matchesBindingPoolSizes` pins 15/10/15 |
| 3. §5 routing datasource: `AbstractRoutingDataSource` keyed on `isCurrentTransactionReadOnly()`, in `LazyConnectionDataSourceProxy`, `@Primary` gated on `datasource.routing.enabled`; Seoul has replica pool, Oregon both keys → primary | mechanics §5; plan.md Phase 2 | MET | `ReadOnlyRoutingDataSource.java:15-19` key = REPLICA when read-only else PRIMARY; `RoutingDataSourceConfig.java:26-28` `@Configuration`+`@ConditionalOnProperty(datasource.routing.enabled=true)`+`@EnableConfigurationProperties`; `:65-82` `@Bean @Primary DataSource` = `LazyConnectionDataSourceProxy` over the routing DS; `:71-76` replica-enabled → distinct REPLICA pool, else REPLICA→primary fallback | `ReadOnlyRoutingDataSourceTest` 2/2 (key by flag); `RoutingSeoulIT` (containerized, `:60-85`) — replication paused: read-only tx reads stale replica (`REPLICATED_VALUE`), read-write tx reads fresh primary (`PRIMARY_ONLY_VALUE`) → 1/1 green |
| 3b. Oregon no-replica routing path (REPLICA→primary target map) | plan.md Phase 2 (deferred to transitive INV1/INV3, Phase 3) | MET (plan-deferred) | `RoutingDataSourceConfig.java:74-76` maps REPLICA→primary when replica disabled | Unit-level pool-sizing coverage `RoutingDataSourceConfigTest$OregonPod`; dedicated behavioral IT deferred to Phase 3 per plan.md — NOT a Phase-2 gap (see Notes) |
| 4. §9.4 read-path seam chosen ONCE (`ByIdReadGuard.read(entityType,id,supplier)`, pass-through), wired on `getPlanner` + `getPublishedPlanner` byId ONLY; lists/searches NOT wrapped | mechanics §9.4; plan.md cross-cutting; mechanics §0 FORBIDDEN | MET | Single seam `ByIdReadGuard.java:20-22` pass-through; wired `PlannerQueryController.java:69-70` (getPlanner byId) + `PublishedPlannerController.java:162-163` (getPublishedPlanner byId), both via `PLANNER_ENTITY_TYPE`. Grep of `byIdReadGuard` in `planner/controller/` returns ONLY these two call sites — `getPlanners`, `getPublishedPlanners`, `getRecommendedPlanners` do NOT call it (confirmed by inspection: `PlannerQueryController.java:44-53`, `PublishedPlannerController.java:65-138`) | `ByIdReadSeamTest` `:64-93` verifies `byIdReadGuard.read("planner", id, any())` invoked on both endpoints, service value flows through (spy over real guard) → 2/2 green; `PlannerControllerTest` 84/84 (byId + list/search groups) no regression |
| 5. FORBIDDEN: no `maximumPoolSize=50` carried forward; pool sizing only from `PoolLedger` | mechanics §0; plan.md cross-cutting | MET | Grep `maximumPoolSize`/`setMaximumPoolSize` across `backend/src/main` → only `RoutingDataSourceConfig.java:44` (ledger-fed) and `:55` (`PoolLedger.SEOUL_REPLICA_POOL`); no literal `50` as a pool size anywhere in production | Covered transitively by `RoutingDataSourceConfigTest` (asserts sizes equal ledger constants, not literals) |
| 6. Seam is provably pass-through today (skeleton, no behavior change) | plan.md Phase 2; scenarios-phase-2.md s4 | MET | `ByIdReadGuard.java:21` body = `return dereference.get();` — no routing/re-check/tombstone | `ByIdReadGuardTest` `:22-40`: value-pin `isSameAs(sentinel)` + supplier invoked exactly once → 2/2 green; `PlannerControllerTest` 84/84 no-regression confirms unchanged external behavior on the two byId endpoints |

### Gaps
- (none) — every in-scope Phase-2 item is MET; scoped suite green; forbidden literal absent.

### Notes
- Oregon no-replica routing (item 3b): the plan explicitly scopes Phase 2's own asserted contract to
  INV9 and defers the routing behavioral proof to "transitive validation by INV1/INV3 downstream"
  (Phase 3, which adds the byId re-check across both regions). The review's Medium NEEDS-SCENARIO on
  this path was recorded as a Phase-3 coverage note, not a Phase-2 gap. Classified PASS-with-deferral
  per the brief's item-3 instruction — not UNMET.
- Re-check bulkhead pool (2–3 conns) is Phase 3 and correctly ABSENT from `PoolLedger` here.
- Production Phase-2 files under `shared/config/` + `shared/readpath/ByIdReadGuard` were untracked
  (`??`) at implementation time (per scenarios-phase-2.md) — the commit boundary is the orchestrator's
  concern, not a verification defect; the files exist on disk and compile/test green.

## Phase 3: Primary re-check on byId miss + bulkhead pool — PASS

### Suite
Scoped command (re-run once by the verifier with the ledger test added):
```
backend/gradlew -p backend test \
  --tests 'org.danteplanner.backend.integration.ReplicaLagIT' \
  --tests 'org.danteplanner.backend.integration.BulkheadIT' \
  --tests 'org.danteplanner.backend.config.PoolLedgerConfigTest'
```
Verbatim tail (`/tmp/build-8603a2f4-p3-verify.log`):
```
> Task :test

> Task :jacocoTestReport

BUILD SUCCESSFUL in 1m 39s
6 actionable tasks: 2 executed, 4 up-to-date
```
EXIT=0. Per-class from `build/test-results/test/` XML (tests/skipped/failures/errors):

| Class | tests | skip | fail | err |
|-------|-------|------|------|-----|
| ReplicaLagIT (containerized, INV1) | 3 | 0 | 0 | 0 |
| BulkheadIT (containerized, INV7) | 1 | 0 | 0 | 0 |
| PoolLedgerConfigTest (INV9, bulkhead-extended) | 4 | 0 | 0 | 0 |
| ByIdReadGuardTest (seam unit, re-run this phase) | 2 | 0 | 0 | 0 |
| ByIdReadSeamTest (seam wiring, re-run this phase) | 2 | 0 | 0 | 0 |

`ByIdReadGuardTest` + `ByIdReadSeamTest` were re-run this phase (`/tmp/build-8603a2f4-p3-seam.log`,
EXIT=0) because `ByIdReadGuard.java` was modified in Phase 3 (the `Optional<PrimaryReCheck>` branch
replaced the pure pass-through body) — the pass-through/seam-wiring rows now rest on a this-phase result.

The two ITs each ran ~90–100s against live Testcontainers MySQL primary+replica pair (real
replication pause/resume + Toxiproxy `wan` toxic), not a mocked substitute. The phase gate
(`ReplicaLagIT` + `BulkheadIT`) also ran green earlier in the session (EXIT=0,
`/tmp/build-8603a2f4-p3-gate.log`).

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| INV1.1 — replica byId miss re-checks primary, serves entity, `replica_miss_promoted_total` +1 | requirements INV1; plan Phase 3; mechanics §5 | MET | `PrimaryReCheck.java:39-52` — replica `dereference.get()` throws → `pinTo(BULKHEAD)` → re-run dereference on primary → `Counter(replica_miss_promoted_total).increment()` (`:46`) → return promoted; `ByIdReadGuard.java:35-40` routes to `readWithReCheck` when `PrimaryReCheck` present | `ReplicaLagIT.byIdMissOnPausedReplica_reChecksPrimary_servesEntityAndIncrementsCounter` (`:90-126`): replication paused, row on primary only, seam read → no throw, served id == plannerId, `promotedCount() - before == 1.0` — green |
| INV1.2 — absent on both → `PlannerNotFoundException` (404), counter untouched, BULKHEAD pin cleared (no leak) | requirements INV1; Brief negative case | MET | `PrimaryReCheck.java:44-50` — still-missing primary re-throws (no `increment()` reached); `finally { ReadOnlyRoutingDataSource.clear() }` (`:48-50`) removes the ThreadLocal `OVERRIDE` (`ReadOnlyRoutingDataSource.java:20,26-28`) so a follow-on read-only tx routes REPLICA again (`:36-38`) | `ReplicaLagIT.byIdMissOnBothReplicaAndPrimary_propagatesNotFound_doesNotPromote_andClearsPin` (`:128-167`): double-miss → `PlannerNotFoundException`, `promotedCount()-before == 0.0`, follow-on read-only probe returns STALE replica value (`:160-162`) — pin proven cleared — green |
| INV1.3 — replica hit served without re-check, counter unchanged (no false promotion) | requirements INV1; Brief case 3 | MET | `PrimaryReCheck.java:41` — first `dereference.get()` returns on a hit; no `catch` branch entered, counter not touched | `ReplicaLagIT.byIdHitOnReplica_servesWithoutReCheck_andDoesNotPromote` (`:169-199`): replicated row, seam read → served, `promotedCount()-before == 0.0` — green |
| INV7 — re-check flood on bulkhead does not delay writes (all four load-bearing assertions inspected individually) | requirements INV7; plan Phase 3; mechanics §6 | MET | Bulkhead is a distinct `HikariDataSource` (size `PoolLedger.BULKHEAD_POOL=3`) keyed `RoutingKey.BULKHEAD` (`RoutingDataSourceConfig.java:103-104,77-86`); re-check pins to it (`PrimaryReCheck.java:43`); write path draws PRIMARY pool via read-write tx (`ReadOnlyRoutingDataSource.java:36-38`) | `BulkheadIT.reCheckFloodOnBulkhead_doesNotDelay_concurrentWrite` (`:100-171`) — all four asserted and green: (1) `floodOutcomes.hasSize(24).containsOnly(PlannerNotFoundException)` anti-vacuity (`:140-144`); (2) `floodDrainMs > singleReCheckMs` bulkhead saturated (`:146-150`); (3) `writeElapsedMs < singleReCheckMs` write isolated (`:156-160`); (4) `stillInFlight > 0` non-serialization (`:162-164`); plus write-persisted probe (`:152-154`) |
| §5 primary re-check is byId-ONLY (never list/search) | mechanics §5, §0 FORBIDDEN; plan cross-cutting | MET | Grep of `ByIdReadGuard.read` across `src/main` returns exactly two call sites, both byId; list/search methods `getPlanners`, `getPublishedPlanners`, `getRecommendedPlanners` do NOT call the seam (confirmed by inspection); re-check lives only inside `PrimaryReCheck` reached only via `ByIdReadGuard` | Covered by `ByIdReadSeamTest` (Phase 2, seam wiring) + `ReplicaLagIT`/`BulkheadIT` exercising byId only; no list/search re-check test exists because no such wiring exists (correct) |
| Both-endpoints scope — seam wraps `getPlanner` (`GET /api/planner/md/{id}`) AND `getPublishedPlanner` (`GET .../published/{id}`); published tx semantics unchanged | plan cross-cutting; Brief | MET | `PlannerQueryController.java:69-70` wraps `getPlanner` byId; `PublishedPlannerController.java:162-163` wraps `getPublishedPlanner` byId, both via `PLANNER_ENTITY_TYPE`. Published byId performs a view-count write (`PublishedPlannerQueryService.incrementViewCount` `:120` `@Transactional`) → primary-routed, so re-check holds trivially; both controllers are git-unmodified this phase (seam committed Phase 2) — tx semantics untouched (CLAUDE.md rule #1) | `ByIdReadSeamTest` (re-run this phase, 2/2) verifies `byIdReadGuard.read("planner", id, …)` invoked on BOTH endpoints; `ReplicaLagIT` drives the `getPlanner` seam directly; `PlannerControllerTest` (Phase 2) 84/84 no-regression on both byId groups |
| §6 bulkhead pool sized 2–3 from `PoolLedger.BULKHEAD_POOL` (Seoul-only, primary-hitting, isolation) | mechanics §6 | MET | `PoolLedger.java:16` `BULKHEAD_POOL = 3`; `RoutingDataSourceConfig.java:84` `setMaximumPoolSize(PoolLedger.BULKHEAD_POOL)`; bulkhead target only created when `replicaProperties.isEnabled()` i.e. Seoul (`:100-104`); `BulkheadDataSourceProperties.java:19-24` defaults endpoint to primary — no hardcoded size | `PoolLedgerConfigTest.poolLedger_whenReadBulkhead_matchesBindingPoolSize` (`:53-57`) pins == 3 — green |
| INV4 — no timing constants in correctness/routing production code | requirements INV4; mechanics §0 FORBIDDEN | MET | Grep over `shared/readpath/*` + the six routing/config files for `Thread.sleep`/`parkNanos`/`TimeUnit`/`Duration`/`await(`/`.SECONDS`/`setConnectionTimeout`/`setIdleTimeout` → NO match. Branch is causal: miss = `PlannerNotFoundException` drives the re-check (`PrimaryReCheck.java:42`), not time. The only wall-clock number lives in `BulkheadIT` (`singleReCheckMs` reference), framed as comparative isolation (`:156-160`) | INV7 asserts a ratio (`writeElapsedMs < singleReCheckMs`), not a tuned SLA — no production timing constant exercised |
| Seoul-only gating — routing/replica disabled → `ByIdReadGuard` pure pass-through, no behavior change | Brief; plan | MET | `ByIdReadGuard.java:26-40` — `Optional<PrimaryReCheck>` empty (no-arg ctor / no bean) → `return dereference.get()`; `PrimaryReCheck` bean is `@ConditionalOnProperty(datasource.replica.enabled=true)` (`RoutingDataSourceConfig.java:115-119`) so it exists only on Seoul pods | `ByIdReadGuardTest` (re-run this phase, 2/2) pins pass-through against the current `Optional<PrimaryReCheck>` body with an empty Optional; existing Oregon/default-profile suites unaffected (no `PrimaryReCheck` bean) |
| INV9 (Phase-2 owned) — Phase 3 extended the ledger test with `BULKHEAD_POOL`; bulkhead-inclusive sum still ≤ budget | Brief secondary check; requirements INV9 | MET | `PoolLedger.java:16` adds `BULKHEAD_POOL=3`; `PoolLedgerConfigTest.java:39-51` computes `(OREGON_PRIMARY 15 + SEOUL_PRIMARY 10 + BULKHEAD 3) × MAX_PODS 2 = 56 ≤ RDS_MICRO 85 − RESERVE 10 = 75`, reading the same production constants | `PoolLedgerConfigTest.poolLedger_whenBulkheadIncluded_staysWithinConnectionBudget` (`:39-51`) + `poolLedger_whenSummedAcrossMaxPods_...` (non-bulkhead 50 ≤ 75) — both green |

### Gaps
- (none) — every in-scope Phase-3 item is MET; scoped suite green (ReplicaLagIT 3/3, BulkheadIT 1/1,
  PoolLedgerConfigTest 4/4).

### Notes
- INV2 (tombstone) is Phase 4's deliverable and is correctly ABSENT from `ReplicaLagIT` here — not
  reported against Phase 3 per the Brief's scope carve-out.
- INV7 `INV7 magnitudes:` log line is emitted at INFO via slf4j (`BulkheadIT.java:137`) and is not
  captured into the surefire XML; the four assertions themselves are the durable evidence and all
  passed. This test closed green-on-arrival (compile-gated at phase open, never observed red), so
  the individual assertion inspection above substitutes for a missing red proof, per the Brief.
- Bulkhead endpoint indirection (`BulkheadDataSourceProperties`) lets `BulkheadIT` route the bulkhead
  through the Toxiproxy `wan` proxy while the primary write pool stays un-proxied — the mechanism that
  makes INV7's comparative-isolation assertion observable without any production timing constant.

## Phase 4: Redis content tombstones for deletes + byId-positive tombstone check — PASS

### Suite
Scoped command (per Brief; NO root gradlew, `-p backend` required):
```
backend/gradlew -p backend test --tests "org.danteplanner.backend.integration.ReplicaLagIT"
```
Verbatim tail (`/tmp/spec-verify-phase4.log`, EXIT=0):
```
> Task :test

OpenJDK 64-Bit Server VM warning: Sharing is only supported for boot loader classes because bootstrap classpath has been appended

> Task :jacocoTestReport

BUILD SUCCESSFUL in 1m 44s
6 actionable tasks: 2 executed, 4 up-to-date
```
Per-class from `build/test-results/test/TEST-…ReplicaLagIT.xml`: `tests="5" skipped="0" failures="0" errors="0"`.
The `test` task reported `2 executed` (not UP-TO-DATE), so this is a live containerized run
(~1m44s = real Testcontainers MySQL primary+replica pair with GTID replication + pause/resume).
Two of the five cases are the Phase-4 INV2 deliverables (both green):
- `deleteTombstonesGhost_replicaPositiveReturns404` (INV2 read half)
- `deleteWritesTombstoneKeyWithBoundedTtl` (INV2 write half)
The other three (INV1 miss/negative/hit) are Phase-3-owned and re-confirmed green here.

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| Redis content tombstone on delete: `SET del:planner:{id} PX 1h` written synchronously before the delete response; TTL is cleanup not the correctness gate; fail-open write (Blacklist pattern applied to entities) | req §Phase-2 backend line 54; mechanics §1 | MET | Write issued in the delete flow: `PlannerCommandService.deletePlanner` `:366-368` — `planner.softDelete()` → `plannerRepository.save(planner)` → `tombstoneStore.ifPresent(store -> store.writeTombstone(ByIdReadGuard.PLANNER_ENTITY_TYPE, id))`, synchronous, before the `return`/response. Store: `ContentTombstoneStore.writeTombstone` `:41-48` = `stringRedisTemplate.opsForValue().set(key, "1", Duration.ofHours(1))`; key builder `:69-71` = `"del:" + entityType + ":" + id`; TTL const `:25` = `Duration.ofHours(1)` (= PX 3600000). Fail-open: catch `DataAccessException` `:45-47` logs+swallows so the delete still succeeds behind the primary re-check gate | `ReplicaLagIT.deleteWritesTombstoneKeyWithBoundedTtl` (`:250-271`): after `deletePlanner`, `stringRedisTemplate.hasKey("del:planner:"+id)` is `true`, and `getExpire(..SECONDS)` is `>0 and ≤3600` — proves the synchronous write with a bounded ~1h TTL — green |
| Read-path contract — tombstone-check limb: replica-served byId **positives** check `del:<type>:<id>`; present → 404. (GTID-gate limb = later phase → verify only tombstone limb) | req line 55; mechanics §5 tombstone check + read-path order | MET | Check runs on the replica-HIT positive branch only: `PrimaryReCheck.readWithReCheck` `:51-69` — `hit = dereference.get()` succeeds (positive), then `:65-67` `if (tombstoneStore.isTombstoned(entityType, id)) throw new PlannerNotFoundException(id)`. The check sits OUTSIDE the miss `catch` (`:55-64`), so the tombstone throw does NOT trigger a primary re-check; the promoted-primary return path (`:58-60`) is not tombstone-checked (a promoted read already reflects the delete). `isTombstoned` `:59-67` = `hasKey`, fail-open (returns `false` on `DataAccessException`, `:63-65`). Threaded via `ByIdReadGuard.read` (Phase-3 wiring, store injected into the `PrimaryReCheck` bean) | `ReplicaLagIT.deleteTombstonesGhost_replicaPositiveReturns404` (`:213-248`): replication paused, `deletePlanner` on primary, replica STILL holds the un-soft-deleted row (asserted directly: `replicaJdbcTemplate` `deleted_at IS NULL`, `:228-234`), then `byIdReadGuard.read(...getPlanner...)` → `PlannerNotFoundException` — proves the 404 comes from the tombstone, not replication catch-up — green |
| INV2 — Tombstone: a deleted entity 404s on replica-served byId even while the replica still holds the row (test: pause, delete on primary, GET → 404) | req §Invariants line 102; Test Plan line 130 (`ReplicaLagIT` INV2 case) | MET | Composite of the two rows above: synchronous tombstone write (`PlannerCommandService.java:368` + `ContentTombstoneStore.java:41-48`) + replica-positive check (`PrimaryReCheck.java:65-67`) | `ReplicaLagIT` INV2 read-half `deleteTombstonesGhost_replicaPositiveReturns404` (pause→delete→replica-positive→404) + write-half `deleteWritesTombstoneKeyWithBoundedTtl` (synchronous `del:planner:<id>`, bounded TTL) — both green in the live containerized run above |

### Scoped-out (later phase / infra — NOT gaps)
- **GTID-gate limb** of the read-path contract (`author with cookie → GTID gate` / `cookie-gated author → primary until caught up`) — the Brief explicitly assigns Phase 4 only the tombstone-check limb. SCOPED-OUT.
- **Oregon `REPLICAOF` / no-replica topology** — infra/later phase, not exercised by this phase's byId tombstone. SCOPED-OUT.
- **N+1 write-path audit** — not a Phase-4 clause. SCOPED-OUT.

### Gaps
- (none) — every in-scope Phase-4 clause is MET; scoped suite green (ReplicaLagIT 5/5, both INV2 cases 0 fail / 0 error).

### Notes
- Fail-open on BOTH tombstone paths (write and check) is by design and consistent with the spec's
  "1h TTL = cleanup, not the correctness gate" + accepted-residue clauses: the Phase-3 primary
  re-check remains the correctness gate; the tombstone only shrinks the ghost-readable window from
  MySQL lag (tens of seconds) to Redis lag (~ms). Class Javadoc `ContentTombstoneStore.java:12-20`
  and `PrimaryReCheck.java:22-25` state this contract.
- The INV2 read-half test asserts the replica still holds the row un-soft-deleted (`deleted_at IS NULL`
  on the replica) BEFORE the byId — this is the load-bearing anti-vacuity guard proving the 404 is the
  tombstone, not the replica having caught up.

## Phase 5: Blacklist + logout-everywhere externalization to Redis (fail-open ladder) — PASS

### Suite
Scoped command (cwd = task dir → absolute gradlew path):
```
backend/gradlew -p backend test --tests "*DegradationIT" --tests "*TokenBlacklistServiceTest" \
  --tests "*AuthControllerLogoutAllTest" --tests "*JwtAuthenticationFilterTest" \
  --tests "*JwtAuthenticationFilterLineageTest" --tests "*AuthenticationFacadeTest" \
  --tests "*AuthenticationFacadeLineageTest" --tests "*UserControllerTest" \
  --tests "*UserAccountLifecycleServiceTest" --tests "*AdminServiceTest"
```
Verbatim result tail:
```
> Task :test
TestNamingConventionTest > test_methods_follow_naming_convention FAILED
    java.lang.AssertionError at ArchRule.java:94
110 tests completed, 1 failed
> Task :test FAILED
BUILD FAILED in 2m 8s
```
Gate is the per-suite JUnit XML, NOT the aggregate exit. The single aggregate failure is
`architecture.TestNamingConventionTest` — a DOCUMENTED PRE-EXISTING out-of-scope ArchUnit
failure (ReplicaLagIT method names, committed in a prior phase; ArchUnit runs ungated by
`--tests`). It is not a Phase-5 regression and not in scope (Brief §Verification run).

Per-suite XML gate (`backend/build/test-results/test/`): across the 10 target suites (28 XML
files incl. `@Nested` splits) — **98 testcases, 0 `<failure>`/`<error>` children, 0 skipped**.
`DegradationIT.xml`: tests="2" failures="0" errors="0" skipped="0" (both INV6 legs green).

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| INV6 fail-open + `blacklist_check_skipped_total` counter (auth Redis unreachable → authed read proceeds, counter increments) | requirements.md:106 (INV6); degrade-by-operation taste requirements.md:60,62 | MET | `TokenBlacklistService.isBlacklisted` `try/catch DataAccessException`→`failOpen(...)` returns `false` (`TokenBlacklistService.java:140-142`); `failOpen` logs + `Counter.builder("blacklist_check_skipped_total").register(meterRegistry).increment()` (`:253-257`); mirrored on the user-invalidation read path `:179-181` | Acceptance: `DegradationIT::blacklistCheck_WhenAuthRedisUnreachable_FailsOpenAndIncrementsSkipCounter` (Toxiproxy timeout cut on dedicated auth-Redis route, asserts `isBlacklisted==false` AND counter delta `==1.0`) — DegradationIT.xml 2/2 green. Unit mirror: `TokenBlacklistServiceTest$FailOpenTests` (both read paths throw→false+counter==1.0) — 0 fail |
| AOF-replay blacklist integrity (pre-outage blacklisted token still rejected after AOF replay) | requirements.md:106 (INV6); Test Plan requirements.md:132; mechanics §7 | MET | Redis-native durability: `bl:` written with TTL via `opsForValue().set(key, …, Duration.ofMillis(remaining))` (`TokenBlacklistService.java:110`); AOF persistence is the auth-Redis property (mechanics §7 `everysec`) | Acceptance: `DegradationIT::blacklistedToken_WhenAuthRedisReloadsFromAof_StaysRejected` — blacklist with Redis UP (precondition `isBlacklisted==true`), `DEBUG LOADAOF` on the dedicated `--appendonly yes --appendfsync always` Redis, re-assert `isBlacklisted==true` (`DegradationIT.java:167-183,194-206`) — green |
| `bl:<tokenHash>` key layout = string SET … PEX `<remaining-expiry>`; TTL = token remaining life; on logout/invalidation | mechanics §1, §3, §9.1 | MET | `BLACKLIST_KEY_PREFIX="bl:"` (`:49`), `blacklistKey`=prefix+SHA-256 hash (`:259-261,282-291`); `remaining=expiry.getTime()-now`, guard `remaining<=0`, `set(key, marker, Duration.ofMillis(remaining))` (`:103-110`); logout→`AuthenticationFacade.logout` `blacklistToken(access/refresh…)` (`AuthenticationFacade.java:249,260`); immediate marker via `encode(true,…)` | `TokenBlacklistServiceTest$BlacklistTokenTests` (add/overwrite/expiry/null), `$IsBlacklistedTests` (incl. `WhenEntryExpired_ReturnsFalse` = TTL expiry), `$CrossInstanceTests` (real Redis, second service sees `bl:`) — 0 fail |
| `uinv:<userId>` key layout = string epoch-ms; TTL = refresh-token max life; on logout-everywhere/account-delete | mechanics §1, §3 | MET | `USER_INVALIDATION_KEY_PREFIX="uinv:"` (`:51`), `invalidateUserTokens`→`set(uinv:<id>, String.valueOf(now), Duration.ofMillis(refreshTokenExpiry))` (`:152-159`); callers: `AuthenticationFacade.logoutEverywhere` (`:287`), `UserAccountLifecycleService` account-delete (`:88`), `AdminService` demotion (`:71`) | `AuthControllerLogoutAllTest` (full boot, real auth Redis via `@DynamicPropertySource`): `$SuccessTests.logoutAll_…_InvalidatesUserTokens`, `$PostActionTests.…_PreActionAccessToken_Rejected`; `TokenBlacklistServiceTest$UserInvalidationRedisTests`; `UserAccountLifecycleServiceTest$DeleteAccountTests`; `AdminServiceTest$ChangeRoleTests` — all 0 fail |
| Read path rejects blacklisted tokens honoring the 5s grace — **the 5s grace compare stays Java-side** | mechanics §3, §9.1; external contract | MET | `ROTATION_GRACE_PERIOD_MS=5_000` (`:47`); Java compare `if (!immediate && now - blacklistedAt < ROTATION_GRACE_PERIOD_MS) return false;` (`:131-137`) — no Redis-side eval; logout uses immediate path (bypasses grace), rotation uses grace | `TokenBlacklistServiceTest$RotationGracePeriodTests` (`WhenWithinGracePeriod_Allowed`, `WhenAfterGracePeriod_Rejected`, `WhenBlacklisted_RejectedImmediately`, `WhenRotationThenLogout_OverridesToImmediate`) — 0 fail |
| Hourly cleanup `@Scheduled` job deleted (TTL replaces it) | mechanics §3 | MET | Current `auth/token/TokenBlacklistService.java` has NO `@Scheduled` and relies on Redis TTL (`Duration.ofMillis` at `:110,:158`). Pre-Redis in-memory version (`a595cec4:…/service/token/TokenBlacklistService.java:39,55,116`) held a `ConcurrentHashMap` + `@Scheduled(fixedRate=3600000) cleanupExpired()` — that job is gone. (The surviving `@Scheduled` in `RefreshRotationService.java:315` prunes in-memory rotation-state maps, an unrelated concern.) | Covered transitively: `$IsBlacklistedTests.…WhenEntryExpired_ReturnsFalse` proves TTL-driven expiry works without a sweep — 0 fail |
| Real reject with Redis UP (blacklisted token → rejected) | external contract | MET | `isBlacklisted` returns `true` for a live `bl:` entry outside grace (`:120-139`) | `TokenBlacklistServiceTest$IsBlacklistedTests.isBlacklisted_WhenBlacklisted_ReturnsTrue`; `DegradationIT` precondition assert `isBlacklisted==true` (Redis UP) at `DegradationIT.java:174-176`; `AuthControllerLogoutAllTest$PostActionTests` end-to-end post-logout rejection — 0 fail |
| Auth Redis persistence config: AOF `everysec` + RDB preamble (production §7) | mechanics §7 | DEFERRED-out-of-phase | No production auth-Redis persistence config (`appendfsync everysec` / `aof-use-rdb-preamble`) exists in-repo — repo-wide grep over `*.yml/*.yaml/*.conf/*.tf/*.jsonc/*.properties` (excl. tests) returns 0 matches. The production auth-Redis instance + its persistence tuning is provisioned by the later managed-Redis/K8s infra phase (same family as the Seoul `REPLICAOF` rung → Phase 14), not Phase 5. Phase 5's AOF-replay leg proves the *mechanism* on a test Redis (`--appendfsync always`, Brief-blessed test-determinism); the prod persistence flags are out-of-phase | DegradationIT proves the replay mechanism; the prod config has no in-repo artifact to pin yet |
| Existing-tests-pass migration (auth-path suites still green after externalization) | Brief §Deliverable (existing-tests-pass migration) | MET | Migration to Redis-backed store is complete; consumers unchanged in contract | 9 existing auth-path suites re-run green: TokenBlacklistServiceTest, AuthControllerLogoutAllTest, JwtAuthenticationFilterTest, JwtAuthenticationFilterLineageTest, AuthenticationFacadeTest, AuthenticationFacadeLineageTest, UserControllerTest, UserAccountLifecycleServiceTest, AdminServiceTest — 96 testcases (excl. DegradationIT's 2), 0 fail / 0 error |

### Scoped-out (later phase / infra — NOT gaps)
- **Middle rung "(Seoul) stale-but-honest replica" of the read ladder** (mechanics §7) — requires a
  second region + a `REPLICAOF` auth Redis that does not exist until the Seoul infra phase (Phase 14).
  Phase 5 implements/tests only the local-Redis rung and the all-unavailable→fail-open rung.
  DEFERRED-out-of-phase.
- **Typed `AUTH/WRITE_TEMPORARILY_UNAVAILABLE` codes, GlobalExceptionHandler, JwtAuthenticationFilter
  Lettuce→typed-code mapping** — Phase 9 (advisor-pinned scope boundary). NOT in Phase 5. DEFERRED.
- **INV5 / primary-DB toxics / typed codes legs of the `DegradationIT` Test Plan line** (requirements.md:132)
  — other phases; Phase 5 owns only the INV6 fail-open-counter + AOF-replay legs, both MET above. SCOPED-OUT.

### Gaps
- (none) — every in-scope Phase-5 item is MET or justifiably DEFERRED-out-of-phase; target-suite XML
  green (98 testcases, 0 fail / 0 error; DegradationIT 2/2).

### Notes
- The aggregate `BUILD FAILED` is solely `TestNamingConventionTest` (pre-existing, out-of-scope per
  Brief); the phase gate is per-suite XML, all target suites green.
- `DegradationIT` uses `--appendfsync always` for deterministic AOF-replay (production §7 is `everysec`)
  and stands up its own isolated MySQL+auth-Redis+Toxiproxy harness (Option B) so sibling ITs' shared
  auth Redis is never toxic-ed/AOF-corrupted — intentional test-determinism choice, not a contract
  violation.

## Phase 6: Refresh rotation Lua externalization + scheduled-job multi-pod safety — PASS

### Suite
Scoped command (cwd = repo root → absolute gradlew path):
```
backend/gradlew -p backend test --tests "*RefreshRotationServiceTest" \
  --tests "*ShedLockMultiPodTest" --tests "*TestNamingConventionTest"
```
Verbatim result tail (`/tmp/phase6-verify-run.log`):
```
> Task :test
> Task :jacocoTestReport
BUILD SUCCESSFUL in 23s
6 actionable tasks: 2 executed, 4 up-to-date
```
Per-suite JUnit XML gate (`backend/build/test-results/test/`) after this run:
- `RefreshRotationServiceTest$*` (7 `@Nested` XML: HappyPath 3, Retry 2, TheftDetection 2,
  FamilyRevocation 3, ConcurrentRotation 1, CrossInstanceAtomicity 1, LegacyAdmission 3)
  — **15 testcases, 0 failures / 0 errors / 0 skipped**.
- `scheduling.ShedLockMultiPodTest.xml`: tests="3" failures="0" errors="0" skipped="0"
  (lock-contention + both `@SchedulerLock`-presence assertions).
- `architecture.TestNamingConventionTest.xml`: tests="1" failures="0" errors="0" skipped="0".
(Note: the ShedLock XML was absent on arrival; I re-ran the scoped suite to regenerate it — now green.)

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| INV8 — rotation atomicity survives multi-instance; RefreshRotationService is Redis-Lua backed (per-family hash `rt:fam:{familyId}` + transition script via `execute(rotateScript,…)`); atomicity = single-threaded Lua, NO Redisson/distributed lock | requirements.md INV8 (Brief) | MET | Per-family key `FAMILY_KEY_PREFIX="rt:fam:"` + `familyKey`=`rt:fam:{familyId}` (`RefreshRotationService.java:52,317-319`); `ROTATE_SCRIPT` compiled to `DefaultRedisScript` (`:82-116,144-146`) and run via `authRedisTemplate.execute(rotateScript, List.of(familyKey), …)` (`:204-208`); no Redisson/lock import anywhere in the file | ConcurrentRotation: `rotate_WhenConcurrentSameParent_OneTransitionOneSupersede` (2 threads same parent → asserts one `UNUSED_LATEST` winner + one `SUPERSEDED` loser, both valid distinct JWTs) — `$ConcurrentRotation.xml` 1/1 green. CrossInstanceAtomicity: `rotate_WhenTokenUsedOnInstanceA_ReplayOnInstanceB_DetectsTheftOverSharedRedis` (USED on A, replay on B over shared Redis → Revoked + family revoked) — `$CrossInstanceAtomicity.xml` 1/1 green |
| Test Plan — rotation concurrency suite re-run vs Lua + `jwt_rotation_outcome_total` NAME parity pre/post migration | requirements.md Test Plan (Brief) | MET | Metric `METRIC_OUTCOME="jwt_rotation_outcome_total"`, `TAG_OUTCOME="outcome"` (`:61-62`); outcome tags byte-identical: `rotated`, `retry_superseded`, `theft_revoked`, `legacy_admitted`, `rejected_revoked_family`, `rejected_invalid` (`:64-69`) | Every tag asserted: `OUTCOME_ROTATED` (test `:143,161,198`), `RETRY_SUPERSEDED` (`:199`), `THEFT_REVOKED` (`:237,254`), `LEGACY_ADMITTED` (`:429,461`), `REJECTED_REVOKED_FAMILY` (`:279`), `REJECTED_INVALID` (`:290,304,445`) — all across the 15 green testcases |
| mechanics §2.2 (BINDING) — transition Lua script present character-for-character | mechanics §2.2 (Brief) | MET | `ROTATE_SCRIPT` (`:82-116`) matches verbatim: KEYS[1]=`rt:fam:{F}`; ARGV=jti,parentJti,successorJti,succExpiryMs,nowMs,ttlMs; `HGET __revoked__`→`'REVOKED'` (`:88`); USED/SUPERSEDED replay→`HSET __revoked__ ARGV[5]`+`'THEFT'` (`:93-96`); parent PENDING→`'USED||'..ARGV[4]` (`:98-103`); PENDING→supersede oldSucc `'SUPERSEDED||'..ARGV[4]` (`:105-110`); write succ `'UNUSED_LATEST||'..ARGV[4]` (`:112`); write jti `'PENDING|'..succ..'|'..ARGV[4]` (`:113`); `PEXPIRE ARGV[6]` (`:114`); `'ROTATED'` (`:115`) | Behaviorally pinned by the full 15-case suite (state-machine + theft + retry + concurrency all exercise the script branches) — 0 fail |
| mechanics §2.3 (BINDING) — successor JWT minted BEFORE EVALSHA; cookie set ONLY on ROTATED; dropped on THEFT/REVOKED | mechanics §2.3 (Brief) | MET | Mint+validate successor at `:192-195` BEFORE `execute(...)` at `:204`; `switch(result)`: ROTATED→`cookieUtils.setCookie(REFRESH_TOKEN, successorJwt,…)` (`:221-222`); THEFT→`clearAuthCookies` (no set) (`:211-215`); REVOKED→`clearAuthCookies` (`:216-220`) — signed successor never set nor written into the hash on THEFT/REVOKED | HappyPath `rotate_WhenRotated_SetsRefreshCookie` asserts cookie value=new JWT + maxAge>0 (`:166-176`); TheftDetection + FamilyRevocation assert refresh+access cookie maxAge 0 (`:235-236,278`) — 0 fail |
| mechanics §2.5 (BINDING) — 5s grace stays Java (no grace/blacklist in rotation svc); metric preserved; hourly `@Scheduled cleanupExpired` sweep DELETED (no `@Scheduled`/`ConcurrentHashMap` remain); Notification 2AM + UserCleanup 3AM get `@SchedulerLock`; SSE heartbeat/zombie sweeps STAY without `@SchedulerLock` | mechanics §2.5 (Brief) | MET | `grep grace\|blacklist` over `RefreshRotationService.java` → 0 matches; `grep @Scheduled\|ConcurrentHashMap` over same → 0 matches (hourly sweep gone). `NotificationService.cleanupOldNotifications`: `@Scheduled(cron="0 0 2 * * *")`+`@SchedulerLock(name="cleanupOldNotifications",…)` (`NotificationService.java:343-346`); `UserCleanupScheduler.cleanupExpiredUsers`: `@Scheduled(cron=…3…)`+`@SchedulerLock(name="cleanupExpiredUsers",…)` (`UserCleanupScheduler.java:36-38`). SSE sweeps remain `@Scheduled` WITHOUT `@SchedulerLock`: `SseService.sendHeartbeats/cleanupZombieConnections` (`:220,228`), `PlannerCommentSseService.sendHeartbeats/cleanupZombieConnections` (`:129,137`) | ShedLock presence pinned by `ShedLockMultiPodTest.cleanupOldNotifications_…IsAnnotatedWithSchedulerLock` + `cleanupExpiredUsers_…IsAnnotatedWithSchedulerLock` (reflection) — 2/2 green. Grace/deletion checks are structural (grep) — no positive test needed; deleted code cannot be tested |
| Plan external contract — verdict→result mapping (ROTATED→Rotated, THEFT→Revoked, REVOKED→Rejected(REVOKED_FAMILY)); cookie cleared on THEFT/REVOKED; ShedLock over durable auth Redis (`authRedisConnectionFactory`, not ephemeral rate-limit Redis); jobs fleet-once | plan.md Phase 6 (Brief) | MET | `switch(result)`: THEFT→`new RotationResult.Revoked(familyId)` (`:214`), REVOKED→`Rejected(Reason.REVOKED_FAMILY)` (`:219`), ROTATED→`Rotated(successorJwt,…)` (`:226`). `ShedLockConfig.lockProvider` binds `@Qualifier("authRedisConnectionFactory")` → `new RedisLockProvider(authRedisConnectionFactory)` (`ShedLockConfig.java:23-27`); `@EnableSchedulerLock(defaultLockAtMostFor="PT10M")` (`:20`); shedlock deps `shedlock-spring:5.16.0` + `shedlock-provider-redis-spring:5.16.0` (`build.gradle.kts:66-67`) | Verdict mapping + cookie-clear pinned by TheftDetection (maxAge 0 both cookies `:235-236`), FamilyRevocation (`:275-279`), CrossInstanceAtomicity (`:391-394`). Fleet-once mutual exclusion: `ShedLockMultiPodTest.lock_WhenTwoPodsContendSameLockName_OnlyFirstAcquires` — two `RedisLockProvider`s over one Redis: first `isPresent()`, concurrent second `isEmpty()` — green |

### Deletion checks (structural)
- `RotationEntry.java` DELETED — `ls .../auth/token/RotationEntry.java` → "No such file or directory".
- `RefreshRotationService.java` carries NO `@Scheduled` and NO `ConcurrentHashMap` (grep 0 matches);
  the Phase-5 note about a surviving `@Scheduled` in this file is now stale — that sweep is gone.
- No `grace`/`blacklist` logic in `RefreshRotationService.java` (grep 0 matches) — the 5s grace
  comparison stays in `TokenBlacklistService` (single clock source), untouched by Phase 6.

### Gaps
- (none) — every in-scope Phase-6 item is MET; scoped suite green (rotation 15, shedlock 3, naming 1;
  0 failures / 0 errors / 0 skipped).

## Phase 7: Rate limiter → bucket4j-redis on local ephemeral Redis (behavior-preserving, no INV) — PASS

### Suite
Scoped command (cwd = `backend`; gradlew is `backend/gradlew`, forced rerun via `cleanTest`):
```
./gradlew -p . cleanTest test --tests "*RateLimitConfigTest" --tests "*TestNamingConventionTest"
```
Verbatim result tail:
```
> Task :cleanTest
> Task :test
> Task :jacocoTestReport

BUILD SUCCESSFUL in 20s
7 actionable tasks: 3 executed, 4 up-to-date
```
Per-suite JUnit XML gate (`backend/build/test-results/test/`) after the rerun:
- `RateLimitConfigTest$*` (9 `@Nested` XML): AllowRequestsWithinLimit 4, Exceeded 4, SeparateBucketsPerEndpoint 3,
  SeparateBucketsPerUser 4, BucketKey 1, BucketConfig 3, AuthLimitIdentifier 4, EdgeCase 3,
  LocalRedisPersistence 2 — **28 testcases, 0 failures / 0 errors / 0 skipped**.
- `architecture.TestNamingConventionTest.xml`: tests="1" failures="0" errors="0" skipped="0".

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| R1 — rate-limit buckets live in local ephemeral Redis with TTL eviction; per-IP 429 behavior unchanged; buckets observed in local Redis, not auth Redis | plan/phase-7 external contract (Brief) | MET | `RateLimitConfig.tryConsume` consumes via injected `ProxyManager<byte[]>` (`RateLimitConfig.java:121-125`); the proxy manager is built from the `rateLimit` endpoint (`RedisConnectionConfig.java:70-73`), separate from `@Primary` `authRedisConnectionFactory` (`:59-62`) | `LocalRedisPersistenceTests.rateLimitBucket_AfterConsume_PersistsInLocalRedisWithTtl` (key present in rate-limit Redis, TTL >0 and ≤2s) + `rateLimitBucket_AfterConsume_AbsentFromAuthRedis` (present in rate-limit, absent from a separate auth Redis) — `$LocalRedisPersistence.xml` 2/2 green; 429 parity pinned by the other 26 cases |
| R2 — swap hand-rolled `ConcurrentHashMap` for `bucket4j-redis`; `proxyManager.builder().build(key,config)` against local Redis; manual 5-min eviction + `maxBuckets` LRU deleted entirely | mechanics §3 (BINDING, Brief) | MET | `private final ProxyManager<byte[]> proxyManager` constructor-injected (`RateLimitConfig.java:24,34`); `proxyManager.builder().build(keyBytes, () -> bucketConfiguration).tryConsume(1)` (`:124`); grep over `RateLimitConfig.java` for `ConcurrentHashMap\|BucketEntry\|@Scheduled\|maxBuckets` → 0 matches (old eviction/LRU gone); dep `com.bucket4j:bucket4j-redis:8.10.1` (`build.gradle.kts:70`) | Consume-path exercised by all 28 cases (reuse/isolation/exhaustion) — 0 fail; deleted eviction code is structural (grep) — no positive test possible |
| R3 — `LettuceBasedProxyManager` wiring + bucket expiration strategy (local Redis, TTL-based eviction) | mechanics §9.2 / §9(2) (PLAN-TIME, Brief) | MET | `buildRateLimitProxyManager` builds `LettuceBasedProxyManager.builderFor(connection).withExpirationStrategy(ExpirationAfterWriteStrategy.fixedTimeToLive(bucketTtl)).build()` over `RedisClient.create("redis://"+host+":"+port)` with `ByteArrayCodec` (`RedisConnectionConfig.java:84-90`); TTL bound from `redis.rate-limit.bucket-ttl-seconds` (`application.properties:56`, default 3600 at `Endpoint.bucketTtlSeconds=3600` `:104`) | `rateLimitBucket_AfterConsume_PersistsInLocalRedisWithTtl` asserts a positive Redis TTL ≤ configured 2s on the bucket key — direct evidence the `fixedTimeToLive` strategy is applied — green |
| R4 — separate local ephemeral rate-limit Redis; rate-limiter wired against rate-limit endpoint, NEVER the auth/@Primary endpoint | requirements.md L41/L34 (verifiable slice, Brief) | MET | `@Bean rateLimitProxyManager()` built from `rateLimit.getHost()/getPort()` (`RedisConnectionConfig.java:70-73`); `rateLimit` is a distinct `Endpoint` (`:55-56`) bound from `redis.rate-limit.host/port` (`application.properties:54-55`); auth endpoint is the separate `@Primary` bean (`:59-62`) | `rateLimitBucket_AfterConsume_AbsentFromAuthRedis` — after consume, key present in rate-limit Redis and absent from a physically separate auth Redis container — green |
| R5 — 429 semantics preserved: per-user / per-endpoint / per-identifier isolation and `RateLimitExceededException` (same args) unchanged | 429 parity (Brief) | MET | `checkRateLimit`/`checkCrud/Import/Sse/Auth/Comment/Report/Moderation/PlannerCommentSse` signatures + key formats unchanged; throws `new RateLimitExceededException(userId, endpoint)` (`RateLimitConfig.java:47`), `(null,"auth")` (`:69`), `(null,"planner-comment-sse")` (`:112`) | Isolation: `SeparateBucketsPerUserTests` 4/4, `SeparateBucketsPerEndpointTests` 3/3, `AuthLimitIdentifierTests` 4/4, `BucketKeyTests` 1/1. Exception contract: `ExceededLimitTests` 4/4 assert `getUserId()`/`getEndpoint()`/message content — all green |

### Before-state evidence (`git diff HEAD`, Phase 7 is uncommitted working-tree `M`)
Diff of `RateLimitConfig.java` vs `HEAD` confirms parity, not just current-state:
- R2 "deleted entirely": the removed side shows `ConcurrentHashMap<String,BucketEntry> buckets`,
  the `BucketEntry` inner class, `bucketTtlSeconds`, `maxBuckets`, `@Scheduled(fixedRate=300000)
  evictExpiredBuckets()` (with its LRU-by-`lastAccess` eviction), and `getBucketCount()` — all
  deleted; added side is the injected `ProxyManager<byte[]>` + `proxyManager.builder().build(...)`.
  `grep -rn "BucketEntry\|maxBuckets\|getBucketCount\|evictExpiredBuckets" backend/src` → 0 matches
  (no orphaned artifact survived elsewhere).
- R5 "same args / unchanged": the diff shows the key formats (`userId+":"+endpoint`,
  `identifier+":auth"`, `"device:"+deviceId+":planner-comment-sse"`) and the
  `RateLimitExceededException(userId,endpoint)` / `(null,"auth")` / `(null,"planner-comment-sse")`
  args are byte-identical across the change; every `checkX` signature is untouched; the token-bucket
  math (`Bandwidth…capacity/refillGreedy`, `tryConsume(1)`) is unchanged — only the bucket *store*
  moved from in-memory `Bucket` to the Redis proxy manager.
- Test diff (110+/5−): the 5 deletions are 3 Javadoc lines, the `new RateLimitConfig()` →
  constructor-injection rewire, and one edge-test refill window widened 60s→86400s in
  `checkRateLimit_LargeCapacity_HandlesCorrectly` (keeps greedy refill from leaking a token during
  the slower real Redis round-trips; the "1001st throws" assertion is unchanged). NO exception-contract
  or isolation assertion (`getUserId`/`getEndpoint`/message/`assertThrows`) was removed or weakened.

### Deletion / structural checks
- `RateLimitConfig.java` carries NO `ConcurrentHashMap`, `BucketEntry`, `@Scheduled`, or `maxBuckets`
  (grep 0 matches) — manual 5-min eviction and LRU cap deleted; key TTL replaces both.
- `bucket4j-redis:8.10.1` present alongside `bucket4j-core:8.10.1` (`build.gradle.kts:69-70`).
- `redis.rate-limit.host/port/bucket-ttl-seconds` bound in `application.properties:54-56`.

### Gaps
- (none) — R1–R5 all MET; scoped suite green (RateLimitConfig 28, naming 1; 0 failures / 0 errors / 0 skipped).

## Phase 8: GTID cookie gate (author read-your-own-write, INV3) — PASS

### Suite
Scoped command (cwd = repo root; gradlew is `backend/gradlew`, `-p backend` required):
```
./backend/gradlew -p backend test --tests "*CausalGateIT" --tests "*TestNamingConventionTest" \
  --tests "*GtidCookieFilterTest" --tests "*GtidCookieTest"
```
Verbatim tail:
```
> Task :test UP-TO-DATE
> Task :jacocoTestReport UP-TO-DATE

BUILD SUCCESSFUL in 915ms
6 actionable tasks: 6 up-to-date
```
The `test` task reported UP-TO-DATE (inputs unchanged since the burndown-close run), so the
load-bearing evidence is the per-class JUnit XML (`backend/build/test-results/test/`, timestamps
**2026-07-11T13:45Z** = today, real run). All four in-scope classes green:

| Class | tests | skip | fail | err |
|-------|-------|------|------|-----|
| CausalGateIT (acceptance, containerized) | 1 | 0 | 0 | 0 |
| GtidCookieFilterTest (filter routing unit) | 5 | 0 | 0 | 0 |
| GtidCookieTest (cookie attributes unit) | 2 | 0 | 0 | 0 |
| TestNamingConventionTest (naming ArchUnit) | 1 | 0 | 0 | 0 |

### FORBIDDEN §0 check (token rides cookie ONLY — no server-side/Redis token store)
`grep -rniE "redis|session|store|opsForValue|template"` over
`backend/src/main/java/org/danteplanner/backend/shared/gtid/` returns ZERO Redis/session/store
matches — the only hits are `JdbcTemplate`/`TransactionTemplate` (the WAIT-probe and capture
queries). The RYW causal token is minted from `@@gtid_executed` and travels ONLY in the
`Set-Cookie` header (`GtidCookieFilter.java:72`, `GtidCookie.of()`); it is never written to a
server-side key. FORBIDDEN §0(a) HOLDS.

FORBIDDEN §0(b) — no timing constant on a correctness path (the `0.05` is a probe bound, not a
correctness window): verified by branch reasoning, not by the source comment. Both WAIT outcomes are
safe independent of the timeout value — timeout (`→1`) → `isCaughtUp` false → pin PRIMARY, which is
unconditionally correct (author reads own write); applied (`→0`) → serve REPLICA, correct because the
replica has provably executed the GTID. So `PROBE_TIMEOUT_SECONDS` (`GtidReadGate.java:28,45`) only
trades fallback-to-primary latency vs replica-hit rate — NO correctness branch depends on its value.
§0(b) HOLDS.

### Trace

| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| 1. GTID cookie gate = mechanics §5 **verified fallback** (`SELECT @@gtid_executed` on primary post-commit), full contract: HttpOnly/Secure/SameSite=Lax cookie; WAIT probe on replica; 0→replica+clear, 1→primary; NO server-side store | requirements.md:49; mechanics §5 (Brief) | MET | Fallback branch: `GtidWriteCapture.java:26` `SELECT @@gtid_executed`, plain (non-read-only) `JdbcTemplate` over the `@Primary` routing DS → routes PRIMARY key (`RoutingDataSource` read-write → PRIMARY); `:36-40` returns whitespace-stripped superset. Cookie: `GtidCookie.java:30-36` `httpOnly(true).secure(true).sameSite("Lax").path("/")`, name `ryw_gtid` (`:14`); cleared `Max-Age=0` (`:26-28`). Read gate: `GtidReadGate.java:25,43-46` `SELECT WAIT_FOR_EXECUTED_GTID_SET(?,0.05)` inside a read-only `TransactionTemplate` (`:37-38`) → REPLICA key; `result==0`(APPLIED)→`isCaughtUp` true. Filter mapping: `GtidCookieFilter.java:55-65` caught-up→clear cookie+serve(replica), NOT caught-up→`pinTo(PRIMARY)` + `clear()` in finally; write branch `:68-73` sets cookie from captured GTID. NO server-side store (§0 grep above) | `GtidCookieTest.of_WithGtid_CarriesHttpOnlySecureSameSiteLaxValue` + `cleared_ForCaughtUp_HasMaxAgeZero` (2/2); `GtidCookieFilterTest.write_WhenCaptureHasGtid_SetsGtidCookieAndSkipsReadGate` (HttpOnly/Secure/SameSite=Lax on write), `readGet_WhenCookieCaughtUp_ClearsCookieAndDoesNotPin`, `readGet_WhenCookieNotCaughtUp_PinsPrimaryRetainsCookieAndClearsPin`, `readGet_WhenNoCookie_PassesThroughWithoutGateOrPin` (5/5); `CausalGateIT` end-to-end (1/1) |
| 2. Gate is a DISTINCT mechanism from the Phase-3 byId re-check (PrimaryReCheck), not a duplicate — list refetches (positive-but-incomplete) invisible to miss-triggered repair | requirements.md:51 (Brief) | MET | Gate lives in `shared/gtid/` and pins the WHOLE cookie-bearing request to PRIMARY via `ReadOnlyRoutingDataSource.pinTo(RoutingKey.PRIMARY)` (`GtidCookieFilter.java:60`) — covers list/search GETs too, not only byId. `PrimaryReCheck` (`shared/readpath/PrimaryReCheck.java`) fires ONLY inside `ByIdReadGuard` on a byId replica MISS (Phase-3 evidence); a positive-but-stale list read never throws, so the re-check cannot repair it — the gate can. Separate packages, separate triggers (cookie-present time-gate vs byId not-found), no shared code path | Distinct-mechanism structure is code-evident; `CausalGateIT` exercises the gate on the byId GET path (`/api/planner/md/{id}`) independent of any miss; `GtidCookieFilterTest` proves the gate pins on cookie+not-caught-up with no byId-miss involvement |
| 3. INV3 — replica paused → author's post-write reads route to primary; after `awaitCaughtUp`, route to replica (test: `CausalGateIT`) | requirements.md:103 INV3 (Brief) | MET | `GtidCookieFilter.java:55-65` — pins PRIMARY while `isCaughtUp` false, serves replica + clears cookie when true; `GtidReadGate.isCaughtUp` drives the branch off `WAIT_FOR_EXECUTED_GTID_SET` | `CausalGateIT.causalGate_authorWriteSetsGtidCookie_readYourOwnWriteRoutesPrimaryThenClearsCookieWhenCaughtUp` (`:125-184`): replication stopped, author writes `PRIMARY_ONLY_TITLE`; cookie-bearing read observes fresh primary value (`:160-163`) while ungated read observes stale replica `REPLICATED_TITLE` (`:165-168`); after `startReplica()`+`awaitCaughtUp()` cookie-bearing read serves replica value + cookie cleared (`:173-179`). Containerized, 1/1 green |
| 4. Test Plan — `CausalGateIT`: cookie set on write, `WAIT_FOR_EXECUTED_GTID_SET` routing both branches | requirements.md:131 Test Plan (Brief) | MET | Write-sets-cookie: `GtidCookieFilter.java:71-72`; both WAIT branches: `GtidReadGate.java:46` result mapping + `GtidCookieFilter.java:55-65` | `CausalGateIT` asserts (a) write returns `Set-Cookie` with GTID-shaped value + HttpOnly/Secure/SameSite=Lax (`assertGtidCookie` `:191-207`), (b) not-caught-up branch → primary value, (c) caught-up branch → replica value + cleared cookie (`assertGateCookieCleared` `:213-225`). Both WAIT branches driven by real stop/await replication control. 1/1 green |

### Wiring / structural checks
- `GtidGateConfig.java:19-21` — `@Configuration @ConditionalOnProperty("datasource.routing.enabled"="true")`
  so the gate loads only on Seoul-shape (routing) contexts; single `FilterRegistrationBean` (`:35-43`)
  over `/api/*` at `LOWEST_PRECEDENCE` (after security chain). One filter registration, no duplicate.
- `GtidCookieFilter` extends `OncePerRequestFilter`; safe methods = GET/HEAD (`:75-78`), all others
  treated as write (cookie-set branch).

### Gaps
- (none) — every in-scope Phase-8 item is MET; scoped suite green (CausalGateIT 1, GtidCookieFilterTest 5,
  GtidCookieTest 2, TestNamingConventionTest 1; 0 failures / 0 errors / 0 skipped). FORBIDDEN §0 holds.

### Notes
- **Divergence from mechanics §5 wording (does NOT break contract):** the fallback captures the GTID via
  a FRESH `JdbcTemplate` connection reading `@@gtid_executed` in the filter after the chain completes,
  not literally "the same connection immediately after commit". `@@gtid_executed` is the GLOBAL executed
  set — a monotonic superset that already includes the just-committed write's GTID — so any post-commit
  primary read yields a causally-sufficient token for RYW. The class Javadoc (`GtidWriteCapture.java:16-21`)
  states this "conservative superset" contract explicitly. Recorded in the dossier `## Divergences`.
- `GtidWriteCapture.pollCapturedGtid()` runs on EVERY non-safe request (captures on any write attempt),
  matching the §5 "on any authenticated write" wording; over-capture on a no-op write only sets a
  redundant cookie, never a correctness risk.
- `CausalGateIT` drives the external HTTP contract name-agnostically (matches the cookie by GTID-shaped
  value, not the `ryw_gtid` literal), so it survives any gate implementation — the unit `GtidCookieTest`
  pins the concrete `ryw_gtid` name/attributes.

## Phase 9: Typed degradation + health semantics + Lettuce mapping — PASS

### Suite
Scoped tests only (per Brief; the FULL backend suite is NOT run here — the sandbox has no standalone
Redis, so ~294 Redis-context @SpringBootTest/repository tests fail environmentally, proven independent
of phase 9. The three phase-9 tests carry their own Testcontainers / no external Redis and are green.)

Fast:
`backend/gradlew -p backend test --tests "*GlobalExceptionHandlerDbUnavailableTest" --tests "*JwtAuthenticationFilterTest"`
```
> Task :test
BUILD SUCCESSFUL in 20s
```
- `GlobalExceptionHandlerDbUnavailableTest`: tests="3" skipped="0" failures="0" errors="0"
- `JwtAuthenticationFilterTest$DbUnavailableDuringRefreshTests`: tests="5" skipped="0" failures="0" errors="0"
  (whole `JwtAuthenticationFilterTest` also green)

Slow (Docker/Testcontainers): `backend/gradlew -p backend test --tests "*DegradationIT"`
```
> Task :test
BUILD SUCCESSFUL in 2m 55s
```
- `DegradationIT`: tests="3" skipped="0" failures="0" errors="0" — cases:
  - INV5: primary DB cut → readiness stays UP, write returns WRITE_TEMPORARILY_UNAVAILABLE, list read still serves the replica
  - (a) Auth Redis unreachable: blacklist check fails open + skip counter increments (INV6, Phase-5, present in same IT)
  - (b) AOF-replay integrity: blacklisted token survives DEBUG LOADAOF (INV6)

### Trace
| Item | Source | Status | Code evidence | Test evidence |
|------|--------|--------|---------------|---------------|
| R-typed-codes: `WRITE_TEMPORARILY_UNAVAILABLE` (primary unreachable) | req L63 / mech §7 | MET | `GlobalExceptionHandler.java:354-364` `handleDatabaseUnavailable` (`DataAccessResourceFailureException` + `CannotCreateTransactionException` → 503, code `WRITE_TEMPORARILY_UNAVAILABLE`) | `GlobalExceptionHandlerDbUnavailableTest.java:64-78` (tx-begin + query-time paths, both 503 + code, via real `@ExceptionHandler` dispatch) |
| R-typed-codes: `AUTH_TEMPORARILY_UNAVAILABLE` (Redis outage) mapped in `GlobalExceptionHandler` | req L63 / mech §7 | MET | `GlobalExceptionHandler.java:376-383` `handleRedisUnavailable` (`RedisConnectionFailureException` → 503, code `AUTH_TEMPORARILY_UNAVAILABLE`, more specific than DB handler) | `GlobalExceptionHandlerDbUnavailableTest.java:82-86` (`/boom/redis` → 503 + `AUTH_TEMPORARILY_UNAVAILABLE`) |
| R-typed-codes: Lettuce exceptions mapped ALSO in `JwtAuthenticationFilter` (filter bypasses `@RestControllerAdvice`) | req L63 / mech §7 | MET | `JwtAuthenticationFilter.java:112-118` and `173-179` (Redis catch BEFORE DB catch at both `doFilterInternal` refresh sites) → `writeAuthUnavailable` (`:409-413`, `AUTH_TEMPORARILY_UNAVAILABLE`) / `writeDbUnavailable` (`:399-403`, `WRITE_TEMPORARILY_UNAVAILABLE`) | `JwtAuthenticationFilterTest.java:290-311` (Redis-write→AUTH body) + `:316-334` (DB→WRITE body) + `:219,242,265` (503 status at both sites, tx-begin + query-time) |
| R-degrade-by-operation: reads survive write-path outage; writes surface typed errors | req L60 | MET | Write path 503-typed via handler above; read path routed to replica (Phase-2 routing, exercised) | `DegradationIT.java:316-358` — primary cut: list read returns 200 non-empty from replica, write returns 503 `WRITE_TEMPORARILY_UNAVAILABLE` |
| R-health-app-only: liveness/readiness app-only (never deroute); dependency failures still visible for alerting | req L61 / mech §7 | MET | never-deroute: `application.properties:144-146` (`probes.enabled=true`; `group.readiness.include=readinessState`, `group.liveness.include=livenessState` — DB/Redis excluded); `SecurityConfig.java:85-87` permits readiness/liveness. alert-visible: no `management.health.db.enabled` / `management.health.redis.enabled` opt-out anywhere in `src/main/resources` (grep empty), so both indicators auto-register on the aggregate `/actuator/health` per Spring default | `DegradationIT.java:350-357` — primary-DB outage: `/actuator/health/readiness` stays 200 + "UP" (never-deroute clause pinned; alert-visible clause is config-verified, not IT-asserted) |
| INV5: DB outage never flips readiness; writes fail typed while reads serve | req L105 / Test Plan L132 | MET | (composite of above) | `DegradationIT.java:318` INV5 acceptance test, green |
| R-healthz-local: GA probe `/healthz-local` through Traefik to app-only readiness | req L31 | design-satisfied (infra out of code scope) | Backend deliverable it targets — app-only `/actuator/health/readiness` group — implemented at `application.properties:145` + permitted `SecurityConfig.java:86`; Traefik route has no manifest in this docs-only task dir (per phase-manager note) | Readiness group app-only behavior pinned by `DegradationIT.java:350-357` |
| §0 FORBIDDEN: no dependency checks inside liveness/readiness probes | mech §0 | MET (absence verified) | `application.properties:145-146` include ONLY `readinessState`/`livenessState`; no custom `HealthIndicator`/`HealthContributor` class in `src/main/java`; no group adds db/redis | Enforced behaviorally by `DegradationIT.java:350-357` (readiness UP under DB outage) |

### Gaps
None. All in-scope items MET (R-healthz-local design-satisfied per Brief note).
