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
