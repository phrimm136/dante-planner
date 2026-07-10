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
