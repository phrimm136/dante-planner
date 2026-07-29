# Task: Multi-Region k8s Architecture & Causal-Consistency Read Path

Target cloud architecture for the backend infra epic (learning project: k8s/AWS for Korean BE/SRE roles), plus the Phase-2 backend backlog it generates. Archives the design debate of 2026-07-05/06. The `docs/spec.md` data-driven-feature sections (data catalog, normalization, rendering modes) are not applicable — this is an infrastructure/backend spec with no game-data surface.

Implementation-grade mechanics for the Phase-2 backend work (Redis key layouts, the full rotation Lua script, SSE event rules, routing/gate/re-check/tombstone semantics, pool ledger, test harness) live in the companion file **`mechanics.md`** in this directory — read it before building any Phase-2 item, starting with its §0 executor boundary (BINDING / PLAN-TIME / FORBIDDEN).

Phase ordering (unchanged from the original arc, extended):
RDS cutover (in-flight, `docs/tasks/030-rds-migration/`) → Phase-2 stateless refactor + causal stack (this spec's backend backlog) → k3s single region (Oregon) + ArgoCD → Seoul region + Global Accelerator → someday: Seoul-primary flip (promote runbook).

## Decisions

### Regions & fleet

- Two regions, Oregon `us-west-2` (primary) + Seoul `ap-northeast-2`: Asia/SEA-dominant users get local reads; second region is **latency + read redundancy + warm DR**, explicitly not active-active (evidence: single-writer availability math — Seoul writes require Seoul ∧ peering ∧ Oregon).
- Seoul becomes primary someday; the flip is the cross-region replica promote runbook, executed deliberately — also the PIPA-friendly direction.
- k3s on EC2, not EKS — EKS dropped by user ruling; learning value + cost (evidence: EKS ≈ $73/mo/region control plane alone).
- Two independent clusters, one k3s server (CP) each; never stretch etcd across regions (evidence: Raft election budgets vs 130ms RTT). Oregon's 3-AZ CP was bought, then reverted to 1 by user ruling. A third-region etcd witness solves nothing here — split-brain is quorum-layer, and clusters don't share one.
- Per region: 1× CP `t4g.small` (k3s server + ArgoCD core, `NoSchedule`, snapshots → S3), 1× ingress `t4g.small` (Traefik only, GA endpoint), app ASG min=1/desired=1/max=2 × `t4g.small` (Spring), 1× data `t4g.small` (auth Redis, local rate-limit Redis, Prometheus). CP on micro rejected (evidence: k3s server ~600–800MB + ArgoCD core ~500MB > 1GiB).
- (taste) Instance-level redundancy is spent only on the tier that serves user requests (the app tier); singleton infra nodes (ingress, CP, data) fail over at region granularity via the global router — redundancy budget follows user-facing impact, not component count.
- Spring runs as a DaemonSet on `role=app` nodes: the ASG is the only scaling dial; node count = pod count. `max=2` doubles as deploy surge and load headroom. Default deploy = fallback-absorbed rolling (cross-region fallback carries ~30s of Spring boot); optional `desired=2` bump for surge deploys.
- App nodes are cattle (ASG + user-data join via SSM token); CP/ingress/data are pets (EC2 auto-recovery alarms). Gate before provisioning: backend RSS under `load-test-*.js` must fit ≤ ~1.3GiB on the 2GiB node.
- t4g (Graviton) over t3: ~20% cheaper, faster cores; prerequisite task: arm64 image builds in CI (current prod is x86 — images are amd64-only today).
- Steady-state bill ≈ $145–190/mo; CloudWatch billing alarm at ~$200 (bought).

### Entry plane

- Cloudflare is the single entry (web + API): WAF, CDN, SSR Workers, **Authenticated Origin Pulls (mTLS)**; origin = GA's two static anycast IPs. SG allowlist: Cloudflare ranges + GA health-check ranges only, 443. IP allowlist alone is spoofable-by-tenant; the mTLS pull is the real gate.
- Global Accelerator: proximity routing, ~30s regional failover, traffic dials; endpoints = the ingress EC2 instances directly, client IP preserved `(default)` — no NLB in between.
- Traefik (k3s default) with Gateway API (`Gateway`/`HTTPRoute`) — ingress-nginx is retired/archived since 2026-03 (evidence: kubernetes.io retirement announcement). Traefik streams SSE natively; the nginx `proxy_buffering off` gotcha retires after one curl verification.
- Traefik cross-region `failover` service: local Spring main, other region's Spring fallback, triggered by **connection failure only** (never latency/5xx — cascade prevention). Alert when `fallback` traffic is sustained > 0.
- GA health check probes `/healthz-local`: through Traefik to **local** Spring app-only readiness, fallback route excluded — a region serving via fallback must look unhealthy so GA routes clients direct.
- SSE through Cloudflare: heartbeat < ~100s (CF idle timeout); client reconnect jittered 0–5s; `LimitNOFILE` 65536 on app nodes (stampede math: ~500 conns ≈ 15MB, trivial except fds).

### Data layer

- RDS: Multi-AZ primary in Oregon (bought — automatic ~60s writer failover, RPO 0). Enable Multi-AZ **after** migration cutover, not during seed (sync standby slows catch-up, doubles cost while serving no one). Seoul = single-AZ cross-region **read replica** — a replica object, not a standalone instance. Check micro→small on the primary before cutover (buffer pool, not connections, is the constraint). Seoul's replica gets a **region-local parameter group with parity**: `gtid_mode=ON`, `enforce_gtid_consistency=ON`, `require_secure_transport=1` — the someday-flip promotes it, so it must already carry the primary's posture (evidence: prod parameter group hardened in commits `27ab8d8c`/`1dbb1df9` during the 030 execution).
- (taste) No autonomous writer-authority transitions anywhere: automatic failover of a stateful primary requires quorum machinery; below the scale that justifies it, use synchronous same-site failover (Multi-AZ) for the mundane case and human-executed promotion runbooks for the geographic case — every cross-region authority change is a deliberate act.
  - Redis: no Sentinel, no auto-promote. Outage = typed 503 + wait for auto-recovery + AOF replay. Sentinel + third-region nano witness documented as a later optional exercise only.
  - RDS: no emergency promotion; promote runbook retained for multi-day region loss (break-glass) and the planned Seoul flip. Pre-enable Multi-AZ on the Seoul replica before promoting it.
- Redis (auth state: blacklist, rotation families, tombstones, SSE pub/sub): self-hosted StatefulSet on the data node (StatefulSet/PVC learning vehicle), primary in Oregon — AOF everysec + RDB preamble, EBS gp3, AZ-pinned; Seoul runs a read-only `REPLICAOF` over peering. Durability tiering: blacklist/rotation = AOF-protected; rate buckets = none; pub/sub = N/A.
- Separate tiny **local ephemeral rate-limit Redis** per region (no AOF, no replication) — buckets must be writable locally and deserve zero durability.
- Write scaling explicitly deferred: replicas amplify writes, never absorb them; the ladder (write-less → scale-up → hot-counter Redis offload → shard) is documented in the portfolio, with hot-row vote counters named as the first realistic escalation.

### Read/write path — the causal-consistency stack

- Read-local/write-global: Seoul reads its local RDS replica + Redis replica; all writes (DB, rotation Lua, tombstones, SSE publish) go to Oregon over VPC peering (~130ms once per write). Write transactions must be single-round-trip — N+1 audit of write paths is a pre-Seoul gate (evidence: 130ms × N compounding).
- Replica-aware routing in Spring: `AbstractRoutingDataSource` + `LazyConnectionDataSourceProxy`; `@Transactional(readOnly=true)` → local replica (Seoul only; Oregon has no replica pool).
- (taste) Correctness paths must not contain timing constants: encode "has X happened" as a checkable causal condition — a GTID applied, a tombstone present, replication paused or caught up — never as a calibrated delay. Timing constants are environment-calibrated assumptions that rot silently; time is for performance decisions, causality is for correctness decisions.
  - Author read-your-own-write: **GTID cookie gate** (replaces the rejected 3s pin window). Capture the write's GTID via `session_track_gtids=OWN_GTID`, set HttpOnly cookie; gated reads run `WAIT_FOR_EXECUTED_GTID_SET(gtid, ~0.05)` on the replica — caught up → replica, else → primary. Cookie, not a Redis session key: the causal token must travel **in-band** with the request it guarantees; a server-side store is a second async channel that re-imports the race (evidence: gate lookup on the Redis replica can itself lose the replication race).
  - Recipient updates: **payload-carrying SSE events** (entity in the event) + FE `setQueryData` patch — no refetch, nothing to race (replaces the rejected 1.5s delayed invalidation). FE SSE handlers migrate from invalidate to patch (bounded: `useAppSse.ts`, comments SSE hook).
  - Necessity is code-verified, not theoretical: the FE is invalidate-heavy — 51 `invalidateQueries` vs 21 `setQueryData`; comment create discards the POST response entirely (evidence: `useCommentMutations.ts:33-39`), so client-side masking alone cannot cover RYW, and list refetches (positive-but-incomplete results) are invisible to miss-triggered repair — the gate is not replaceable by the re-check.
- **Primary re-check on replica byId miss** (bought, mandatory): absence on a replica is not authoritative; a miss re-checks the primary before 404 (counter: `replica_miss_promoted_total`). Scoped to entity-by-id dereferences ONLY — never lists/searches (absence-from-a-list is accepted eventual consistency). Covers all discovery channels including out-of-band links, which token piggybacking cannot. Event-carried GTID deferred as optional optimization.
- Re-check runs on a **bulkhead**: dedicated 2–3-connection pool so a miss-flood (junk UUIDs) can never starve the write pool (evidence: 10-conn pool ÷ 140ms ≈ 71 junk-RPS saturation without it). Bucket4j per-IP already fronts it; UUID space is unenumerable.
- **Redis content tombstones for deletes**: `SET del:planner:{id} PX 1h` synchronously before the delete response (the `TokenBlacklistService` pattern applied to entities); replica-served byId **positives** check the tombstone set. Shrinks the deleted-ghost window from MySQL lag (spikes to tens of seconds) to Redis lag (~ms). The 1h TTL is cleanup, not the correctness gate. Full determinism is impossible — invisibility to causally-unconnected readers is linearizability; accepted residue: ghost readable for ~ms, every interaction with it fails cleanly at the primary.
- Read-path contract in one line: **replica hit → tombstone check → serve; replica miss → primary re-check → serve or 404; author with cookie → GTID gate.** The three mechanisms partition the space (my-recent-writes / everyone's misses / everyone's ghosts); none subsumes another.
- Pool ledger (Little's law at ~500 peak concurrent, 80/20 Seoul/Oregon): Oregon pods → primary **15**; Seoul pods → primary **10** (WAN txns ~40× longer per connection-second), → replica **15**. Worst case (both ASGs at 2): 50 ≤ 75 usable on db.t4g.micro (`max_connections` ≈ 85 − 10 reserve). Do NOT carry today's `maximumPoolSize=50` into multi-pod.

### Degradation contract & health semantics

- (taste) Degrade by operation, not by service: reads must survive write-path outages; security reads with bounded blast radius fail open with a loud metric rather than failing the request; write outages surface as typed, user-explainable errors with client-side state preserved.
  - Liveness/readiness = app-only (Spring health groups); dependency failures alert but never deroute — a shared-fate outage must not pull every pod everywhere (routing away only helps private failures).
  - Blacklist checks: local replica read (stale-but-honest, bounded by lag); all Redis unavailable → **fail-open** + `blacklist_check_skipped_total` alert. Bounded by 15-min access-token life; consistent with existing Decision A risk posture.
  - Typed codes: `AUTH_TEMPORARILY_UNAVAILABLE` (login/logout/rotation during Redis outage), `WRITE_TEMPORARILY_UNAVAILABLE` (primary unreachable). Lettuce exceptions mapped in `GlobalExceptionHandler` AND `JwtAuthenticationFilter` (filter-thrown bypasses `@RestControllerAdvice`).
  - FE: errorCode-driven UX — planner writes fall back to local-first storage ("saved locally, sync paused"), auth gets an honest toast.
- Contract rows include: SSE fan-out pauses globally during Oregon-Redis outage (reconciliation refetch is the backstop); deleted content may remain readable remotely up to lag, all interactions fail cleanly.

### Observability & ops

- Prometheus per region on the data node, local scrape only; Grafana Cloud free tier (survives Oregon's death) with two datasources; CloudWatch keeps the AWS plane. External synthetic probe + dead-man's-switch alert on metric absence (silence must not look like health).
- Alert set: sustained Traefik fallback > 0, RDS `ReplicaLag` + Redis offset delta, `CPUCreditBalance`, JVM memory vs limit, `jwt_rotation_outcome_total{theft_revoked}` spike, cert expiry (incl. origin-pull client cert), `blacklist_check_skipped_total`, `replica_miss_promoted_total`, billing ~$200.
- GitOps: ArgoCD **core** per cluster on the CP node (hub-spoke rejected — no cross-region deploy dependency); one repo, kustomize base + `overlays/{oregon,seoul}`; CI builds arm64 image → ECR → bumps kustomize tag `(default; argocd-image-updater later if wanted)`.
- ECR cross-region replication to Seoul (bought) — Seoul's self-healing must not depend on Oregon's registry.
- Secrets: External Secrets Operator + AWS Secrets Manager; RS256 private key via multi-region secret replication. No IRSA without EKS — instance profile is the (coarser) boundary, documented as a known deviation.
- N/N−1 compatibility + expand-contract Flyway become mandatory before the second region serves traffic (minutes of version skew, shared DB). Release checklist question: "can the previous version run against this change?"
- Region bootstrap must be unattended (Terraform + user-data + SSM join token); provisioning Seoul through the automation IS the test.
- Drills (results → `docs/portfolio/`): RDS promote rehearsal on a throwaway replica; Redis outage drill (fail-open + alert + post-AOF-replay blacklist integrity); app-node kill (stopwatch the ASG loop). A failover never observed failing over is a comment.
- PIPA: overseas-transfer disclosure added to the privacy policy (EN, done 2026-07-06, trap-street suffix preserved); follow-up task: full KR policy translation. GTID cookie is strictly-necessary class — category-style cookie section already covers it.

## Description

Evolve the single-EC2 deployment into the two-region k3s architecture above, in phases, with the Phase-2 backend work implementing the stateless refactor (existing design: Redis externalization of blacklist/rotation/rate-limit/SSE) **plus** the causal-consistency read path and degradation contract this spec adds. Every mechanism must be testable at any replication lag without a second region existing (Testcontainers harness below).

## Scope

- `docs/tasks/030-rds-migration/` — in-flight migration this sequences after; GTID infrastructure it reuses
- `backend/src/main/java/org/danteplanner/backend/auth/token/{TokenBlacklistService,RefreshRotationService}.java`, `RateLimitConfig`, SSE services — the four state holders being externalized
- `backend/.../exception/GlobalExceptionHandler.java`, `security/JwtAuthenticationFilter.java` — 503 mapping precedent and filter-bypass gotcha
- `frontend/src/pages/planner/hooks/{useAppSse,usePlannerSave,useMDUserPlannersData}.ts`, `frontend/src/shared/comment/hooks/{useCommentMutations,usePlannerCommentsSse}.ts` — invalidate-vs-patch evidence and migration surface
- `frontend/src/pages/legal/PrivacyPage.tsx`, `static/i18n/EN/common.json` — policy edits landed with this spec

## Target

- Phase-2 (BE): routing datasource + GTID gate interceptor + re-check-with-bulkhead + tombstone check; typed error codes + Lettuce mapping (handler + filter); payload-carrying SSE event schema; pool configs per ledger; fd limits — detail in `mechanics.md`
- Phase-2 (FE): SSE handlers invalidate→patch; errorCode-driven degradation UX; reconnect jitter
- Phase-2 (test): Testcontainers primary+replica harness + `ReplicationControl` + Toxiproxy profiles
- Infra phases: `terraform/` (k3s fleet, GA, peering, ECR replication, Seoul), `deploy/` kustomize base + overlays, ArgoCD apps, CI arm64 build
- `docs/portfolio/` — drill evidence, deviation table (from the reference-architecture roast), write-scaling ladder

## Invariants

- INV1 — Absence authority: a replica byId miss is never surfaced as 404 without primary confirmation — test: `ReplicaLagIT` (replication paused, create on primary, GET through replica path → 200 + counter)
- INV2 — Tombstone: a deleted entity 404s on replica-served byId even while the replica still holds the row — test: `ReplicaLagIT` (pause, delete on primary, GET → 404)
- INV3 — GTID gate: with replica paused, the author's post-write reads route to primary; after `awaitCaughtUp`, they route to replica — test: `CausalGateIT`
- INV4 — No timing constants: the harness and correctness code contain no sleeps/delay windows; lag is injected via `STOP/START REPLICA` and awaited via replication status conditions — test: review gate + harness design (`ReplicationControl` API has no duration parameters)
- INV5 — Health semantics: DB/Redis outage never flips liveness/readiness; writes fail typed while reads serve — test: `DegradationIT` (Toxiproxy cut → readiness UP, `WRITE_TEMPORARILY_UNAVAILABLE`)
- INV6 — Blacklist fail-open: auth Redis unreachable → authed reads proceed, `blacklist_check_skipped_total` increments; a pre-outage blacklisted token is still rejected after AOF replay — test: `DegradationIT` + Redis restart assertion
- INV7 — Bulkhead: saturating the re-check path does not delay writes — test: `BulkheadIT` (latency toxic + miss flood, concurrent write latency asserted)
- INV8 — Rotation atomicity survives multi-instance: family transitions remain atomic under concurrent rotation from two app processes — test: existing rotation concurrency tests against Redis-Lua implementation + `jwt_rotation_outcome_total` parity pre/post migration
- INV9 — Pool ledger: Σ(max pool sizes across max pods) ≤ `max_connections` − 10 — test: config assertion unit test fed by the ledger constants

## Done When

- [ ] Phase-2 backend: causal stack (gate, re-check+bulkhead, tombstones), typed degradation, payload SSE events — all INV tests green in CI via the Testcontainers harness
- [ ] Phase-2 frontend: SSE patch migration + degradation UX; existing FE tests pass
- [ ] Pre-Seoul gates recorded with numbers: backend RSS under load test; write-path round-trip audit; ReplicaLag p99 baseline
- [ ] Oregon k3s cluster GitOps-managed (ArgoCD core), unattended bootstrap proven by rebuild
- [ ] Seoul region provisioned entirely by automation; GA failover observed (region kill drill)
- [ ] Drill write-ups in `docs/portfolio/` (promote rehearsal, Redis outage, node kill)
- [ ] All existing tests pass (`./gradlew -p backend test`, `yarn --cwd frontend vitest run`) — output redirected per project convention

## Test Plan

> Runner from CLAUDE.md: Gradle (backend), Vitest (frontend); build/test output redirected to `/tmp/<prefix>-<session-id>-<suffix>.log`.

### Test Runner
- Backend: `./gradlew -p backend test` (integration tag for Testcontainers suite)
- Frontend: `yarn --cwd frontend vitest run` (tests in `__tests__/` subdirectories)

### Tests to Write
- [ ] `ReplicaLagIT` — INV1, INV2: Testcontainers MySQL primary+replica (GTID `AUTO_POSITION=1`), `ReplicationControl.{stopReplica,startReplica,awaitCaughtUp}`
- [ ] `CausalGateIT` — INV3: cookie set on write, `WAIT_FOR_EXECUTED_GTID_SET` routing both branches
- [ ] `DegradationIT` — INV5, INV6: Toxiproxy cut/timeout toxics on primary-DB and Redis routes; typed codes; fail-open counter; AOF-replay blacklist integrity
- [ ] `BulkheadIT` — INV7: 130ms latency toxic + junk-UUID flood, concurrent write SLA assertion
- [ ] Rotation concurrency suite re-run against Lua implementation — INV8; metrics parity checked in the Redis-migration cutover checklist
- [ ] Pool ledger config test — INV9
- [ ] FE: SSE patch handlers (payload → `setQueryData`) unit tests; errorCode UX tests — in `__tests__/` per convention
- [ ] Every invariant above has its test realized — no invariant ships untested
