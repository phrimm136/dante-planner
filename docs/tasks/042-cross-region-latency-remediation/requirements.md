# Task: Cross-Region Latency & Slow-Query Remediation

Archives the design debate of the 2026-07-18 session: Grafana fleet-snapshot analysis (slowest-endpoints p50/p90/p99 per cluster + fleet health) through `/brainstorm` on the Seoul/Oregon endpoint latency asymmetry, the primary's slow queries, and the `ObjectOptimisticLockingFailureException` upsert incident. Ratified intent rows live in `tests.manifest.json` (the enforced artifact); implementation-grade prose lives in `mechanics.md` — read both before building any part of this task. N/A declarations: `docs/spec.md` Data-Driven Features sections (no raw game data consumed); metered-sink budget invariant (no new metric series — the one eagerly-registered counter is already in the Prometheus keep-list).

Delivery: one PR, phases 01→02→03 (user ruling). The legacy-counter column drop is physically a follow-up deploy and is Deferred.

## Decisions

- D1: Read endpoints carry `@Transactional(readOnly = true)`; any write they performed is extracted — `readOnly` is the replica-routing signal, and a non-readOnly "read" from Seoul pays ~130ms per statement against the Oregon primary (evidence: `ReadOnlyRoutingDataSource.determineCurrentLookupKey`; control group `/api/planner/md/{id}` p50 0.8ms on replica vs `published/{id}` 1.25s on primary).
- D2 (taste): When a database constraint (PK/unique key) can own an invariant, prefer constraint-plus-handled-violation over `PESSIMISTIC_WRITE` serialization; reserve pessimistic locks for invariants no constraint can express. Amends the check-then-act line in `backend/src/main/java/org/danteplanner/backend/CLAUDE.md` (promoted: `global/lesson-when-a-database-constraint-pk-unique`).
- D3: Delete `findByIdForUpdate` and the `existsBy → save` dedup pair; `INSERT IGNORE` + increment-iff-inserted replaces them — the `planner_views` composite PK (V008) already enforces one-view-per-viewer-per-day, and the counter update is an in-engine atomic increment (evidence: Seoul slow-log entries with `Lock_time ≈ Query_time` on 1-row reads).
- D4 (taste, adjudicated: draft rejected — stays task-local): view recording is best-effort; bounded loss (one flush window per pod death) accepted, client renders count+1 optimistically.
- D5: View recording = per-pod in-memory buffer drained by `@Scheduled` batched flush, never ShedLocked — the codebase bans executors (code-verified: zero `@Async`/`ExecutorService` hits in `src/main`) and AFTER_COMMIT listeners run synchronously on the request thread.
- D6: `user_settings` row created inside the user-creation transaction plus a backfill migration; `getSettings` becomes readOnly with a defensive-read fallback (absent row = anomaly log + defaults, never 500).
- D7: OAuth resolution: lookup outside any transaction; `@Transactional createOrRecover` on miss; `uk_provider_provider_id` (V000) referees the race; the loser's transaction dies whole and the facade retries once in a fresh transaction.
- D8: Logout revocation = one atomic Lua script (EVALSHA), closing the crash window where the refresh token outlives the access token (exemplar: `RefreshRotationService` ROTATE_SCRIPT).
- D9: `RestTemplate` gets connect/read timeouts, 3s/5s (default).
- D10: One wide catalog-driven index `(published, deleted_at, created_at, category)`, created before dropping `idx_v006_published_views` and `idx_published`; indexes evolve by replacement per catalog epoch, not accretion (evidence: no query filters `published` alone; no index hints exist).
- D11: Dead `sort=popular|votes` branches removed together with their index — FE grep shows zero production senders; user ruled the feature dead.
- D12: Published list uses a DTO projection (record constructor expression) — `PublicPlannerResponse` is content-free, verified field-by-field.
- D13: `binlog_row_image` stays FULL — CDC/flashback preserved; `planner_stats` fixes the amplification structurally.
- D14: `ObjectOptimisticLockingFailureException` maps to 409 with `reason` discriminator (`STALE_CLIENT` | `CONCURRENT_WRITE`) — divergence exists by failure time; a server-side retry provably converges to the same 409.
- D15: `@Column(updatable = false)` on counter columns as interim guard until they migrate (full-column entity UPDATEs restore stale snapshots over version-blind bulk increments).
- D16: `planner_stats` extraction ships in this PR flag-gated (user ruling); dual-write unconditional, reads flip post-reconciliation, column drop deferred.
- D17 (default): Cutover flag rides the Kustomize overlay ConfigMap mechanism (exemplar: `DATASOURCE_ROUTING_ENABLED`); Terraform owns the AWS platform plane (`terraform/` — VPCs, ASGs, RDS peering, Route53, secrets/IAM), never k8s app config.
- D18: `takenDownAt IS NULL` joins the rewritten list queries as defense-in-depth — no hole exists (entity invariant: `takeDown()` unpublishes, `togglePublished()` blocks republish).
- D19: `replica_miss_promoted_total` registered eagerly at construction — lazy registration renders *No data* instead of a truthful zero.

## Requirements

- R1: `GET /api/planner/md/published/{id}` serves from the replica in a readOnly transaction, holds no row lock, and records views asynchronously (best-effort, PK-idempotent).
- R2: The published list and count run index-only/ordered on the composite index via a content-free projection; `sort=popular|votes` is gone; taken-down planners are predicate-excluded.
- R3: Every active user has a `user_settings` row (creation-time + backfill); `GET /settings` is a pure replica read with defensive defaults.
- R4: OAuth user resolution is atomic on the miss path (user + settings, one tx), race-safe via the unique key, and write-free for returning users.
- R5: Logout revokes access token, refresh token, and token family atomically in one Redis round trip.
- R6: Outbound Google HTTP calls are bounded by connect/read timeouts.
- R7: Concurrent-write conflicts surface as 409 with `reason`, never 500; counter columns are immune to entity-save clobber.
- R8: `replica_miss_promoted_total` is scrapeable from boot.
- R9: `planner_stats` receives dual writes from day one; reads are flag-gated; the two stores reconcile to zero diff.
- R10: SSE broadcasts and Redis publishes fire only after commit — no phantom events on rollback.
- R11: Live targets — Seoul p50 `published/{id}` <100ms, `/settings` <50ms, logout p90 <300ms; both slow-query families absent 48h; promotion panel renders 0, not *No data*.

## Invariants

- INV1: ≤1 `planner_views` row per (planner, viewer, day) — verify: row `view-dedup-composite-pk`.
- INV2: Counter increments iff a view row inserted; replay is a no-op — verify: row `view-flush-replay-idempotent`.
- INV3: Taken-down planners are never listed and cannot republish — verify: rows `list-excludes-nonpublic`, `republish-blocked-while-taken-down`.
- INV4: List responses keep field parity — verify: row `list-field-parity`.
- INV5: Revocation is all-or-nothing — verify: row `logout-revocations-atomic`.
- INV6: Concurrent upserts yield 409, never 500 — verify: row `oolfe-maps-to-409-concurrent`.
- INV7: Settings row exists for every active user; absence degrades, never 500s — verify: rows `settings-created-with-user`, `settings-backfill-covers-legacy`, `settings-get-absent-row-defaults`.
- INV8: Entity saves never overwrite counters — verify: row `counters-immune-to-entity-save`.
- INV9: Fresh publishes are visible from Seoul via the promotion gate — verify: metric presence row `promotion-counter-registered-at-boot`; live: panel renders a number.
- INV10: Flag off = legacy reads, flag on = stats reads, dual-write keeps both equal — verify: rows `stats-dual-write-consistent`, `stats-flag-on-reads-stats`, `stats-flag-off-reads-legacy`.

## Behavior Inventory

| # | Seam | Observable behavior (as-is) | Verdict |
|---|------|-----------------------------|---------|
| B1 | `GET published/{id}` | Full detail + per-viewer engagement flags | preserved |
| B2 | `GET published/{id}` | View counted once per viewer per UTC day | preserved (async, ≤1 flush window) |
| B3 | `GET published/{id}` | Response `viewCount` synchronous with all prior views | dropped — D4 |
| B4 | `GET published/{id}` | Same-planner viewers serialize on a row lock | dropped — D3 |
| B5 | Published list | Excludes deleted/unpublished/taken-down | preserved (+D18) |
| B6 | Published list | `sort=popular|votes` honored | dropped — D11 |
| B7 | Published list | Unknown `sort` falls back to `recent` | preserved |
| B8 | `GET /settings` | Creates row on first access | dropped — D6 |
| B9 | `GET /settings` | `sync_enabled` NULL drives first-login dialog | preserved |
| B10 | Logout | Absent/invalid tokens: fast silent success | preserved |
| B11 | OAuth callback | Returning user: lookup only, no writes | preserved |
| B12 | Upsert conflict | Concurrent write surfaces as 500 | dropped — D14 |
| B13 | Publish/comment/sync | Events fire inside the tx (phantom on rollback) | dropped — R10; emission itself preserved |
| B14 | Upsert flush | Full-column UPDATE restores stale counters | dropped — D15/D16 |

## Done When

- [ ] All ratified manifest rows pass at their phases (local-tdd) — R1-R10
- [ ] EXPLAIN on Flyway-migrated MySQL: page query on `idx_v0NN_published_recent`, no filesort; count `Using index` (infra, spike `probe-explain-composite-index`) — R2
- [ ] Existing suite green against baseline dev@4ac8946b; `migration-test-seed.sql` updated for data-modifying backfills (local-tdd)
- [ ] Flag-state matrix + reconciliation checksum clean (local-tdd + infra) — R9
- [ ] Seoul latency targets hold over a 24h Grafana soak; slow log clean 48h; panel renders 0 (live-only) — R11

## Deferred

- Legacy counter column drop + flag removal — follow-up PR after flag flip + clean reconciliation; until then legacy columns linger under the `updatable=false` guard (harmless, invisible).
- Cutover flag flip — operational, post-merge; until then reads serve legacy columns (correct, pre-cutover).
- FE `reason`-aware conflict handling — until then a `CONCURRENT_WRITE` 409 shows the normal conflict dialog even when contents are identical.

## Test Plan

### Runner
- JUnit 5 + Testcontainers (MySQL/Redis), tag `containerized`; `/home/user/github/LimbusPlanner/gradlew -p backend test` (`-PexcludeTags=containerized` for the fast tier).

### Task-level requirements
- R11 — live-only soak; verified against Grafana after deploy, no phase row compiles to it.
