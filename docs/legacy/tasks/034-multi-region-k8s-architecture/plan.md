# Execution Plan

## Phase Summary

Two disjoint bodies of work share this spec:

1. **Locally-buildable Phase-2 backend + FE (Phases 1–12)** — the stateless refactor,
   causal-consistency read path, typed degradation, and payload SSE migration. Every one is
   TDD-able against the Testcontainers harness; each carries an external contract and its
   assigned INV test. This is the executor's core loop: red acceptance test against the
   harness first (ATDD, `mechanics.md §8`), then implementation.
2. **Cloud-provisioning work (Phases 13–15)** — Terraform k3s fleet, ArgoCD, Global
   Accelerator, Seoul region, drills. **These cannot be verified by the local test suite.**
   They are planned as distinct phases whose Verify step is an operational proof
   (rebuild-from-scratch, region-kill stopwatch, portfolio write-up), explicitly flagged as
   outside the TDD loop. They must NOT be routed to a test-driven executor. If the executing
   environment has no AWS access, Phases 13–15 are a reported boundary, not a failure of the
   plan — the local phases stand on their own.

### Cross-cutting decisions (resolve once, before the phases that depend on them)

- **Read-path interception point — decide once, in Phase 2.** The `mechanics.md §5` order
  (`replica hit → tombstone → serve | replica miss → primary re-check → serve/404 | cookie-gated
  author → primary until caught up`) is a single BINDING sequence realized across three phases:
  tombstone (Phase 4), re-check (Phase 3), author gate (Phase 8). The *wiring* (interceptor vs
  repository decorator vs AOP) is PLAN-TIME (`mechanics.md §9.4`) but must be chosen ONCE in
  Phase 2 so the three phases extend one mechanism, not three incompatible ones. The *order* is
  not negotiable.
- **byId dereference scope (enumerated, not invented).** The entity-by-id endpoints the
  re-check + tombstone apply to are `PlannerQueryController.getPlanner` (`GET /api/planner/md/{id}`,
  author's own) and `PublishedPlannerController.getPublishedPlanner` (`GET .../published/{id}`,
  the out-of-band-link discovery channel named in `requirements.md`). Lists/searches are OUT of
  scope (`mechanics.md §0` FORBIDDEN: no re-check on list/search results). No other authenticated
  UUID-byId dereference exists today; if one is added it inherits the same wiring.
- **Redis connection topology.** Two logical Redis roles exist: auth Redis (Oregon primary —
  blacklist, rotation families, tombstones, SSE pub/sub) and the per-region local ephemeral
  rate-limit Redis (`requirements.md` Data layer; `mechanics.md §1` note + §3). The two
  `LettuceConnectionFactory` beans with distinct `@Qualifier`s (`mechanics.md §9.5`) are a
  **Phase 1 foundation deliverable** — Phases 4/5/6/7/10 all depend on them existing, which is
  what makes Group B parallelizable.
- **No timing constants in any correctness path** (`mechanics.md §0` FORBIDDEN, INV4) — a
  system-wide invariant every phase upholds. Every "has X happened" is a checkable condition.
- **`maximumPoolSize=50` must NOT survive into multi-pod** (`mechanics.md §0`, §6) — the pool
  ledger replaces it (Phase 2).
- **Metrics parity is the cutover correctness check** — `jwt_rotation_outcome_total` (Phase 6)
  and the new counters `replica_miss_promoted_total` (Phase 3), `blacklist_check_skipped_total`
  (Phase 5) must match pre/post migration where applicable.

### Already landed (not a phase)

The PIPA overseas-transfer disclosure (`frontend/src/pages/legal/PrivacyPage.tsx`,
`static/i18n/EN/common.json`) landed 2026-07-06 with this spec (`requirements.md` PIPA line;
shows as modified in git). Full KR translation is a named follow-up task, out of scope here.

## Phases

### Phase 1: Testcontainers causal harness + Redis wiring
- Files: `backend/src/test/java/.../integration/` harness base (Testcontainers config,
  `ReplicationControl` utility, Toxiproxy profile setup); `backend/src/main/java/.../shared/config/`
  Redis connection config (two `LettuceConnectionFactory` beans, distinct `@Qualifier`s);
  `backend/build.gradle.kts` (Testcontainers mysql/toxiproxy/redis modules if absent).
- Tests: the harness itself + INV4 review gate. `ReplicationControl` API has NO duration
  parameters (`stopReplica()` / `startReplica()` / `awaitCaughtUp()` — the last polls
  `SHOW REPLICA STATUS`, a condition not a sleep).
- External contract: `ReplicationControl.{stopReplica,startReplica,awaitCaughtUp}` (no duration
  params); harness boots `mysql-primary` + `mysql-replica` (GTID,
  `CHANGE REPLICATION SOURCE TO … SOURCE_AUTO_POSITION=1`), `redis`, `toxiproxy`, app wired with
  both datasources like a Seoul pod; the two Redis connection-factory beans exist.
- Mechanics sections: `mechanics.md §8` (harness shape), §1 (Redis roles), §9.5 (bootstrap +
  qualifier naming — PLAN-TIME). Topology choice to name: one `redis` container may serve both
  auth and rate-limit roles in tests (primary-vs-local is topology, not logic).
- Considerations: ATDD order (`mechanics.md §8` last line) — this phase must land before any
  red test can run. INV4 forbids timing constants in the harness; lag is `STOP/START REPLICA`
  awaited via status conditions. Toxiproxy profiles: `wan` (130ms latency on app→primary),
  `partition` (timeout toxic = peering loss).
- Depends on: none.
- Verify: `./gradlew -p backend test` (integration tag) — harness boots, `ReplicationControl`
  pauses/resumes replication, Toxiproxy toxics apply; review confirms no duration params (INV4).

### Phase 2: Datasource routing + pool ledger + read-path interception point
- Files: `backend/src/main/java/.../shared/config/` routing datasource config
  (`AbstractRoutingDataSource` + `LazyConnectionDataSourceProxy`, keyed on tx read-only flag);
  pool config (HikariCP pool sizes per ledger); the chosen read-path interception seam
  (interceptor/decorator/AOP) — a skeleton the byId phases extend.
- Tests: Pool ledger config assertion test (INV9) —
  `backend/src/test/java/.../config/` (alongside existing `RateLimitConfigTest`).
- External contract: **substrate phase — no standalone acceptance contract.** Routing behavior
  (`@Transactional(readOnly=true)` → replica, write → primary; Oregon has no replica pool) is
  validated transitively by INV1/INV3 downstream. Its own asserted contract is the INV9 config
  invariant: Σ(max pool sizes across max pods) ≤ `max_connections` − 10.
- Mechanics sections: `mechanics.md §5` (routing), §6 (pool ledger, BINDING sizes), §9.4
  (interception wiring — PLAN-TIME).
- Considerations: pools = two per Seoul pod (`primary`, `replica`) + one Oregon (no replica);
  `LazyConnectionDataSourceProxy` so the route resolves at first use when `readOnly` is known.
  Ledger constants are BINDING (`mechanics.md §6`); do NOT carry `maximumPoolSize=50` forward.
  This phase fixes the single read-path interception seam for Phases 3/4/8 (see Phase Summary).
- Depends on: Phase 1.
- Verify: INV9 config test green; a read-only IT against the harness observes replica routing
  (stale read while replication paused) — proven fully in Phase 3.

### Phase 3: Primary re-check on byId miss + bulkhead pool
- Files: read-path re-check logic at the Phase-2 seam; bulkhead datasource/pool (2–3 conns);
  `replica_miss_promoted_total` counter; applies to both byId endpoints (see Phase Summary scope).
- Tests: `ReplicaLagIT` (INV1) — replication paused, create on primary, GET via replica path →
  200 + counter; `BulkheadIT` (INV7) — 130ms latency toxic + junk-UUID flood, concurrent write
  latency SLA asserted.
- External contract: a replica byId miss re-checks the primary before answering; found → serve +
  `replica_miss_promoted_total`; a miss-flood on the bulkhead never delays writes.
- Mechanics sections: `mechanics.md §5` (primary re-check, byId ONLY), §6 (bulkhead pool 2–3).
- Considerations: absence on a replica is not authoritative; scoped to byId dereferences ONLY
  (`mechanics.md §0` FORBIDDEN: never lists/searches). Bulkhead isolation math: a 10-conn pool ÷
  140ms ≈ 71 junk-RPS saturation without it (`requirements.md`). Bucket4j per-IP already fronts it.
- Depends on: Phase 2 (routing + seam), Phase 1.
- Verify: `ReplicaLagIT` INV1 case + `BulkheadIT` green.

### Phase 4: Redis content tombstones for deletes + byId-positive tombstone check
- Files: delete-path tombstone write (`SET del:<type>:<id> PX 3600000`, synchronous
  pre-response) in the planner delete service/controller; tombstone check on replica-served byId
  positives at the Phase-2 seam.
- Tests: `ReplicaLagIT` (INV2) — replication paused, delete on primary, GET via replica → 404
  even while the replica still holds the row.
- External contract: delete issues the tombstone synchronously **before** the HTTP response; a
  replica-served byId positive whose `del:<type>:<id>` is present returns 404.
- Mechanics sections: `mechanics.md §5` (tombstone), §1 (`del:<entityType>:<id>` key, 1h TTL =
  cleanup not correctness gate).
- Considerations: shrinks the deleted-ghost window from MySQL lag (tens of seconds) to Redis lag
  (~ms). 1h TTL is cleanup, not the gate. Accepted residue: ghost readable ~ms, every interaction
  fails cleanly at the primary (`requirements.md`). Entity type is `planner` for both byId
  endpoints in scope; the `<entityType>` key shape generalizes if more are added.
- Depends on: Phase 2 (seam), Phase 1 (auth Redis wiring).
- Verify: `ReplicaLagIT` INV2 case green.

### Phase 5: Blacklist + logout-everywhere externalization to Redis (fail-open ladder)
- Files: `backend/src/main/java/.../auth/token/TokenBlacklistService.java` (swap in-memory maps
  for `bl:<tokenHash>` + `uinv:<userId>` Redis keys with TTL); read-side grace compare
  (5s grace stays Java); delete the hourly `@Scheduled` cleanup; fail-open read ladder +
  `blacklist_check_skipped_total` counter.
- Tests: `DegradationIT` (INV6) — auth Redis unreachable → authed reads proceed, counter
  increments; a pre-outage blacklisted token still rejected after AOF replay (Redis restart
  assertion). **Migrate the existing blacklist coverage onto the harness Redis** — these all touch
  the blacklist and, once it moves to Redis, hit Redis on every authenticated path:
  `TokenBlacklistServiceTest`, `JwtAuthenticationFilterTest`, `JwtAuthenticationFilterLineageTest`,
  `AuthControllerLogoutAllTest`, `AuthenticationFacadeTest` (+ `AuthenticationFacadeLineageTest`),
  `UserControllerTest`, `UserAccountLifecycleServiceTest`, `AdminServiceTest`. At least one
  blacklist-**rejection** test must assert a real reject with Redis UP — a rejection test against
  an absent Redis silently passes through the fail-open ladder (passes for the wrong reason).
- External contract: logout → `SET bl:<hash> PEX <remaining>`; logout-everywhere/delete →
  `SET uinv:<userId> <epochMs>`; read path rejects blacklisted tokens honoring the 5s Java-side
  grace; all-Redis-down → fail-open (read proceeds) + counter.
- Mechanics sections: `mechanics.md §1` (key layouts), §3 (blacklist commands, PLAN-TIME
  wiring/payloads §9.1), §7 (blacklist read ladder, AOF everysec + RDB preamble).
- Considerations: fail-open bounded by 15-min access-token life (`requirements.md` Decision-A
  posture). Blacklist read ladder: local Redis → (Seoul) stale-but-honest replica → all
  unavailable = fail-open. Post-recovery integrity after AOF replay is the INV6 second assertion.
  Value payloads + read-side compare are PLAN-TIME (`mechanics.md §9.1`), within the stated
  constraint.
- Depends on: Phase 1 (auth Redis wiring).
- Verify: `DegradationIT` INV6 case green incl. Redis-restart/AOF-replay assertion; the migrated
  auth-path suites pass against the harness Redis (existing-tests-pass done-when).

### Phase 6: Refresh rotation Lua externalization + scheduled-job multi-pod safety
- Files: `backend/src/main/java/.../auth/token/RefreshRotationService.java` (replace
  ConcurrentHashMaps + 256 lock stripes with the `rt:fam:{<familyId>}` hash + the transition Lua
  via `SCRIPT LOAD`/`EVALSHA`; keep the 5s rotation-grace compare in Java); **scheduled-job
  changes (all in this phase so no background job is silently left per-pod):** delete the three
  TTL-replaced cleanups (rotation hourly sweep, blacklist hourly sweep already handled in Phase 5,
  and the rotation-state sweep); add ShedLock to
  `backend/src/main/java/.../notification/service/NotificationService.java` (2AM) and
  `backend/src/main/java/.../user/scheduler/UserCleanupScheduler.java` (3AM); the two SSE
  heartbeat/zombie sweeps STAY (they manage live local connections, not shared state).
- Tests: existing rotation concurrency suite re-run against the Redis-Lua implementation (INV8);
  `jwt_rotation_outcome_total{rotated|theft_revoked|retry_superseded}` parity pre/post migration;
  ShedLock test — under two application contexts sharing one Redis/lock store, assert each guarded
  job body executes once, not once per context.
- External contract: rotation verdicts (`ROTATED` / `THEFT` / `REVOKED`) and cookie behavior
  (cookie set ONLY on `ROTATED`; successor minted before transition, dropped on THEFT/REVOKED)
  identical to today; family transitions atomic under concurrent rotation from two app processes;
  the 2AM/3AM jobs fire once across the fleet, not once per pod.
- Mechanics sections: `mechanics.md §2` (BINDING — the full Lua script, implement verbatim;
  §2.3 split-signing call order; §2.5 rotation residue + ShedLock + which sweeps stay/go). §2.4
  traced scenarios are REFERENCE.
- Considerations: the Lua script's single-threaded execution IS the lock — no Redisson/distributed
  locks (`mechanics.md §0` FORBIDDEN). The `{…}` hash-tag pins the family key to one slot.
  Metrics preserved verbatim — they are the cutover correctness check. ShedLock is the direct
  multi-pod correctness fix this epic exists for: without it, `max=2` pods each fire the 2AM
  notification / 3AM cleanup (duplicate notifications, concurrent cleanup).
- Depends on: Phase 1 (auth Redis wiring; ShedLock lock store).
- Verify: rotation concurrency suite green against Lua impl; metric-name parity check; ShedLock
  two-context test green.

### Phase 7: Rate limiter → bucket4j-redis on local ephemeral Redis
- Files: `backend/src/main/java/.../shared/config/RateLimitConfig.java` (swap hand-rolled
  `ConcurrentHashMap` for `bucket4j-redis` `proxyManager` against the local Redis); delete the
  manual 5-min eviction + `maxBuckets` LRU (key TTL replaces both).
- Tests: existing `backend/src/test/java/.../config/RateLimitConfigTest.java` adapted to the
  Redis-backed impl (behavior-preserving: same 429 semantics). No INV assigned — stateless-refactor
  work; contract is behavior parity.
- External contract: rate-limit buckets live in the local ephemeral Redis with TTL eviction;
  per-IP 429 behavior unchanged.
- Mechanics sections: `mechanics.md §3` (rate limiter), §9.2 (`LettuceBasedProxyManager` wiring +
  `ExpirationAfterWriteStrategy` — PLAN-TIME).
- Considerations: rate-limit buckets deserve zero durability and must be writable locally — they
  live in the local Redis, never the auth Redis (`mechanics.md §1` note, §3).
- Depends on: Phase 1 (local Redis wiring).
- Verify: adapted `RateLimitConfigTest` green; buckets observed in the local Redis, not auth Redis.

### Phase 8: GTID cookie gate (author read-your-own-write)
- Files: write-path GTID capture (`session_track_gtids=OWN_GTID` from the OK packet, or the
  `@@gtid_executed` fallback) → set HttpOnly `Secure; SameSite=Lax` cookie; read-path gate at the
  Phase-2 seam (`WAIT_FOR_EXECUTED_GTID_SET('<gtid>', 0.05)` on the replica; 0 → replica + clear
  cookie; 1 → primary for this request).
- Tests: `CausalGateIT` (INV3) — cookie set on write; with replica paused the author's post-write
  reads route to primary; after `awaitCaughtUp`, they route to replica (both branches).
- External contract: an authenticated write sets the GTID cookie; a cookie-bearing read-only
  request routes to primary until the replica has applied that GTID, then to replica (cookie
  cleared).
- Mechanics sections: `mechanics.md §5` (GTID gate, BINDING; the 0.05 is a probe bound not a
  correctness window), §0 (FORBIDDEN: token must ride the request cookie, never a server-side
  Redis store).
- Considerations: **Gate-0 (RDS probe) selects the production capture mechanism, it does NOT
  block this phase's local build.** Both branches are specified — `session_track_gtids` primary,
  `@@gtid_executed` on the same connection post-commit as the verified fallback (`mechanics.md §5`
  RDS caveat). `CausalGateIT` goes red→green against the Testcontainers MySQL on either branch;
  the live-RDS probe (`mechanics.md` Pre-impl gate 0) is recorded before shipping to prod, not
  before writing the red test. The gate is not replaceable by the re-check (list refetches are
  invisible to miss-triggered repair — `requirements.md`).
- Depends on: Phase 2 (routing + seam), Phase 1.
- Verify: `CausalGateIT` green (both routing branches).

### Phase 9: Typed degradation + health semantics + Lettuce mapping
- Files: `backend/src/main/java/.../shared/exception/GlobalExceptionHandler.java` and
  `.../shared/security/JwtAuthenticationFilter.java` (map Lettuce connection exceptions in BOTH —
  filter-thrown bypasses `@RestControllerAdvice`, same multi-catch pattern as commit `da57cd78`);
  new error codes `AUTH_TEMPORARILY_UNAVAILABLE`, `WRITE_TEMPORARILY_UNAVAILABLE`; Spring health
  groups (liveness + readiness = app-only; DB/Redis excluded, visible on full `/actuator/health`);
  `/healthz-local` route target.
- Tests: `DegradationIT` (INV5) — Toxiproxy cut on primary-DB route → readiness stays UP,
  write returns `WRITE_TEMPORARILY_UNAVAILABLE` while reads still serve.
- External contract: primary DB unreachable → typed `WRITE_TEMPORARILY_UNAVAILABLE`, reads
  survive; Redis-dependent auth writes during outage → `AUTH_TEMPORARILY_UNAVAILABLE`;
  DB/Redis outage never flips liveness/readiness.
- Mechanics sections: `mechanics.md §7` (degradation & errors, BINDING), §0 (FORBIDDEN: no
  dependency checks in liveness/readiness probes).
- Considerations: degrade by operation, not by service — a shared-fate outage must not deroute
  every pod (routing away only helps private failures). `/healthz-local` targets local Spring
  app-only readiness, fallback service excluded, so a region serving via fallback looks unhealthy
  to GA. FE consumes these codes in Phase 11.
- Depends on: Phase 2 (routing, for write-path detection), Phase 5 (blacklist ladder for the AUTH
  code), Phase 1.
- Verify: `DegradationIT` INV5 case green.

### Phase 10: SSE over Redis pub/sub (payload-carrying events) — backend
- Files: `backend/src/main/java/.../shared/sse/` and the two SSE services (publish to auth Redis
  primary, subscribe local Redis, deliver to local emitters); payload-carrying event envelope
  (created/updated entity DTO or deleted id); settings-cache-invalidation control message on the
  same mechanism; `SseEventType` extension as needed.
- Tests: SSE pub/sub fan-out IT (publish on one node's Redis → delivered to another node's local
  emitters; event carries payload). No INV assigned — INV set does not cover SSE fan-out; contract
  is the payload envelope + cross-node delivery.
- External contract: every pod publishes to the Oregon Redis primary; every pod subscribes to its
  local Redis and delivers to local emitters (true fan-out, no sticky sessions); events carry
  payloads so recipients patch caches (never refetch).
- Mechanics sections: `mechanics.md §4` (SSE over pub/sub, BINDING decisions; Pub/Sub NOT
  Streams; §9.3 envelope fields + channel naming PLAN-TIME), §0 (FORBIDDEN: no notify-then-fetch).
- Considerations: at-most-once matches the app — neither SSE service sets `.id()`, so
  `Last-Event-ID` replay has nothing to consume; missed events recovered by the `usePlannerSync`
  reconciliation backstop. Envelope `{type, entityType, entityId, payload?, deletedId?}` is a
  starting shape, not a contract (PLAN-TIME). Heartbeat < 100s (Cloudflare idle limit).
- Depends on: Phase 1 (auth Redis pub/sub wiring).
- Verify: fan-out IT green (cross-node delivery with payload).

### Phase 11: FE SSE invalidate→patch migration + errorCode degradation UX
- Files: `frontend/src/pages/planner/hooks/useAppSse.ts` (7 invalidations → `setQueryData`
  payload patches), `frontend/src/shared/comment/hooks/usePlannerCommentsSse.ts`;
  errorCode-driven UX (planner writes → "saved locally, sync paused"; auth → toast); reconnect
  jitter 0–5s on top of `retry:`; matching Zod schemas for the payload envelope.
- Tests: FE SSE patch handler unit tests (payload → `setQueryData`); errorCode UX tests — in
  `__tests__/` per convention (`yarn --cwd frontend vitest run`).
- External contract: SSE handlers patch caches from the event payload (no refetch); a
  planner-write errorCode drives the local-first "saved locally, sync paused" path; an auth
  errorCode drives an honest toast; reconnect jitter 0–5s.
- Mechanics sections: `mechanics.md §4` (FE surface), §7 (FE errorCode switch).
- Considerations: FE is invalidate-heavy (51 `invalidateQueries` vs 21 `setQueryData`); comment
  create discards the POST response, so client-side masking alone cannot cover RYW — the patch
  migration is the recipient-update half of the causal stack (`requirements.md`). Payload schema
  must match the Phase-10 envelope; validate with Zod (FE rule #5).
- Depends on: Phase 10 (payload event schema), Phase 9 (error codes).
- Verify: FE SSE patch + errorCode UX unit tests green; existing FE tests pass.

### Phase 12: Pre-Seoul gates — recorded measurements (partial local)
- Files: `docs/portfolio/` write-ups; no production code.
- Tests: none (measurement/audit phase).
- External contract: three gates recorded with numbers (`requirements.md` Done-When).
- Considerations: **Gate 2 (write-path round-trip audit — Hibernate batching, no lazy walks in
  write txns) is locally doable now and should precede Seoul.** **Gate 1 (backend RSS under
  `load-test-*.js` ≤ ~1.3GiB)** needs load-test infra — partial local. **Gate 3 (ReplicaLag p99
  baseline)** requires the live Seoul replica — cloud, defer to after Phase 14. Decision criteria
  per `mechanics.md` Pre-implementation gates §1–3.
- Depends on: Phase 2 (write paths stable); Gate 3 depends on Phase 14.
- Verify: numbers recorded in `docs/portfolio/`; Gate 2 audit shows every write txn
  single-round-trip.

### Phase 13: Oregon k3s fleet + ArgoCD core + CI arm64 build — INFRA, not local-testable
- Files: `terraform/` (Oregon k3s: 1× CP, 1× ingress/Traefik, app ASG min=1/desired=1/max=2,
  1× data node); `deploy/` kustomize base + `overlays/oregon`; ArgoCD core Application manifests;
  `.github/workflows/` arm64 image build → ECR → kustomize tag bump.
- Tests: **none in the local suite.** Verification is operational (see Verify).
- External contract: the Oregon cluster is GitOps-managed; a from-scratch rebuild reproduces it
  unattended (Terraform + user-data + SSM join token).
- Mechanics sections: none (design lives in `requirements.md` Regions/fleet, Entry plane,
  Observability/ops).
- Named requirements decisions homed here (each must land, none has an INV): Traefik Gateway API
  + native SSE streaming; ArgoCD core per cluster (hub-spoke rejected); one repo, kustomize base +
  overlays; CI arm64 image → ECR → tag bump; `LimitNOFILE=65536` on app-node user-data; External
  Secrets Operator + AWS Secrets Manager for the RS256 private key (instance-profile boundary,
  documented no-IRSA deviation); Prometheus per region on the data node + Grafana Cloud two
  datasources; CloudWatch billing alarm ~$200; external synthetic probe + dead-man's-switch;
  N/N−1 compatibility + expand-contract Flyway becomes mandatory before the second region serves;
  Spring as a DaemonSet on `role=app` nodes; no autonomous writer promotion anywhere
  (`mechanics.md §0` FORBIDDEN; `requirements.md` taste lines).
- Depends on: Phases 1–11 (a deployable stateless backend) + Phase 12 gates 1–2.
- Verify: **operational, outside the test suite** — tear down and rebuild Oregon from Terraform +
  user-data; ArgoCD syncs the app; unattended bootstrap proven by the rebuild.

### Phase 14: Seoul region + Global Accelerator + peering + ECR replication — INFRA, not local-testable
- Files: `terraform/` Seoul (CP, ingress, app ASG, data node, cross-region RDS read replica with
  region-local parity parameter group, Redis `REPLICAOF` over peering); `deploy/overlays/seoul`;
  GA (proximity routing, endpoints = ingress EC2s, client-IP preserved); VPC peering; ECR
  cross-region replication.
- Tests: **none in the local suite.**
- External contract: Seoul is provisioned entirely by automation; GA regional failover is
  observed under a region-kill drill.
- Mechanics sections: none (design in `requirements.md`).
- Named requirements decisions homed here: Seoul RDS replica carries the primary's hardened
  posture (`gtid_mode=ON`, `enforce_gtid_consistency=ON`, `require_secure_transport=1`) so the
  someday-flip can promote it; Traefik cross-region `failover` service triggered by connection
  failure ONLY (never latency/5xx — cascade prevention), alert when sustained > 0; GA health check
  probes `/healthz-local` (fallback route excluded); Cloudflare single entry with mTLS
  Authenticated Origin Pulls as the real gate, SG allowlist = Cloudflare + GA-health ranges only;
  ECR replicated so Seoul self-heals without Oregon; RS256 key via multi-region secret replication;
  RDS Multi-AZ enabled after cutover (not during seed).
- Depends on: Phase 13.
- Verify: **operational** — provision Seoul via automation only (provisioning through the
  automation IS the test); region-kill drill, stopwatch GA failover (~30s); Gate 3 ReplicaLag
  p99 baseline recorded (closes Phase 12).

### Phase 15: Drills + portfolio write-ups — INFRA, not local-testable
- Files: `docs/portfolio/` (drill write-ups, deviation table from the reference-architecture
  roast, write-scaling ladder with hot-row vote counters as the first escalation).
- Tests: **none in the local suite.**
- External contract: three drills executed and documented (`requirements.md` Done-When).
- Mechanics sections: none.
- Considerations: a failover never observed failing over is a comment (`requirements.md`). RDS
  promote rehearsal on a throwaway replica (pre-enable Multi-AZ before promote); Redis outage
  drill (fail-open + alert + post-AOF-replay blacklist integrity — the production analogue of
  INV6); app-node kill (stopwatch the ASG loop). Alert set verified live: sustained Traefik
  fallback > 0, RDS `ReplicaLag` + Redis offset delta, `CPUCreditBalance`, JVM memory vs limit,
  `jwt_rotation_outcome_total{theft_revoked}` spike, cert expiry incl. origin-pull client cert,
  `blacklist_check_skipped_total`, `replica_miss_promoted_total`, billing ~$200.
- Depends on: Phase 14.
- Verify: **operational** — drills run against the live fleet, results written to `docs/portfolio/`.

## Phase Dependencies

Group A (foundation): Phase 1.
Group B (after A, parallel): Phase 2, Phase 5, Phase 6, Phase 7, Phase 10 — each depends only on
Phase 1.
Group C (after B): Phase 3 (needs 2), Phase 4 (needs 2), Phase 8 (needs 2), Phase 9 (needs 2+5),
Phase 11 (needs 10+9).
Group D (gates): Phase 12 — Gate 2 after Phase 2; Gate 1 alongside; Gate 3 after Phase 14.
Group E (infra, sequential, NOT local-testable): Phase 13 → Phase 14 → Phase 15.
