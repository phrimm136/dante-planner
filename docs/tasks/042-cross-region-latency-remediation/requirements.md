# Task: Cross-Region Latency & Slow-Query Remediation

Archives the design debate of the 2026-07-18 session: Grafana fleet-snapshot analysis (slowest-endpoints p50/p90/p99 per cluster + fleet health) → `/brainstorm` on the Seoul/Oregon endpoint latency asymmetry, the primary's slow queries, and the `ObjectOptimisticLockingFailureException` incident on planner upsert. Implementation-grade mechanics live in `mechanics.md` — read it before building any part of this task; it binds the migration DDL, projection contract, view-recording pipeline, Lua revocation contract, 409 body shape, and the stats-cutover choreography. N/A declarations: the `docs/spec.md` Data-Driven Features sections do not apply (this task consumes no raw game data files); the metered-sink budget invariant does not apply (no new metric series — the one eagerly-registered counter is already in the Prometheus keep-list).

Delivery: **one PR** containing all items below (user ruling). The legacy-counter column drop is physically a follow-up deploy and is Deferred.

## Decisions

- D1: Read endpoints carry `@Transactional(readOnly = true)`; any write they performed is extracted — because `readOnly` is the replica-routing signal, and a non-readOnly "read" from Seoul pays ~130ms × statement-count against the Oregon primary (evidence: `ReadOnlyRoutingDataSource.determineCurrentLookupKey`; control group `/api/planner/md/{id}` p50 0.8ms on replica vs `published/{id}` p50 1.25s on primary).
- D2 (taste): When a database constraint (PK/unique key) can own an invariant, prefer constraint-plus-handled-violation over `PESSIMISTIC_WRITE` serialization; reserve pessimistic locks for invariants no constraint can express. Amend the check-then-act line in `backend/src/main/java/org/danteplanner/backend/CLAUDE.md` with this qualifier. Supersedes the prior blanket lock rule.
- D3: Delete `PlannerRepository.findByIdForUpdate` and the `existsBy → save` view-dedup pair; replace with `INSERT IGNORE` + increment-iff-inserted — because the `planner_views` composite PK (V008) already enforces one-view-per-viewer-per-day atomically, and the counter update is an in-engine atomic increment (evidence: `V008__add_planner_views.sql` PK; Seoul slow-log entries with `Lock_time ≈ Query_time` on 1-row reads).
- D4 (taste): Engagement counters (views) are best-effort telemetry — prefer read latency and availability over write durability; bounded loss (one flush window per pod death) is acceptable, with the client rendering count+1 optimistically.
- D5: View recording = per-pod in-memory buffer drained by `@Scheduled` batched flush — because the codebase bans executors (code-verified: zero `@Async`/`ExecutorService`/`CompletableFuture` hits in `src/main`) and AFTER_COMMIT listeners run synchronously on the request thread; the flush job is per-pod and must NOT be ShedLocked (evidence: `ShedLockConfig` exists for singleton jobs only).
- D6 (taste): Prefer creation-time invariants over lazy-create branches: create the `user_settings` row inside the same transaction that creates the user, plus a backfill migration — "every active user has a settings row" replaces "row may not exist" handling in every read path. `getSettings` becomes `readOnly` with a defensive-read fallback (absent row = logged anomaly + defaults, never 500).
- D7: OAuth callback user resolution: cheap read outside any transaction (returning users stay at one round trip), `@Transactional createOrRecover` only on miss (user + settings row atomic); `uk_provider_provider_id` (V000) referees the concurrent-first-login race; the loser's transaction dies whole and the facade retries once in a fresh transaction — never catch-and-continue inside a poisoned Hibernate session.
- D8: Logout revocation = one atomic Lua script (EVALSHA) replacing three sequential Oregon Redis writes — 3 RTTs → 1, and it closes the crash window where the access token is blacklisted but the refresh token (the dangerous one) survives (exemplar: `RefreshRotationService` ROTATE_SCRIPT).
- D9: `RestTemplate` gets connect/read timeouts — today `HttpClientConfig` builds it bare, so a hung Google endpoint pins servlet threads indefinitely. Values 3s/5s (default).
- D10 (taste): Secondary indexes evolve by replacement per query-catalog epoch, not accretion: one wide index `(published, deleted_at, created_at, category)` — equalities, then sort, then covering residual — created before dropping `idx_v006_published_views` and `idx_published` in the same migration. When planner-type filtering ships, the index is swapped, not joined by a sibling.
- D11: Remove the dead `sort=popular`/`sort=votes` branches together with their index — the FE never sends the parameter (grep: zero production senders; user ruled the feature dead); a live-but-unindexed public sort path is an unmonitored trap.
- D12: Published list uses a DTO projection (`PlannerSummaryRow`, record constructor expression) — `PublicPlannerResponse` is content-free (verified field-by-field), so the list stops dragging 20 JSON blobs per page through the buffer pool and a spilling filesort.
- D13: Keep `binlog_row_image=FULL` — CDC and flashback capability preserved; the amplification is tens of MB/day at current traffic, and the `planner_stats` extraction fixes it structurally without a global semantic change.
- D14: Map `ObjectOptimisticLockingFailureException` → 409 using the existing `serverVersion` contract plus a `reason` discriminator (`STALE_CLIENT` | `CONCURRENT_WRITE`) — by the time the version check fails, divergence exists; a server-side retry provably converges to the same 409 (concurrent writer bumped `syncVersion`), so retry machinery adds complexity for an identical outcome.
- D15: `@Column(updatable = false)` on `Planner.viewCount`/`upvotes` as interim guard — full-column entity UPDATEs restore stale counter snapshots over version-blind bulk increments (the counter-regression race); removed again when the columns migrate to `planner_stats`.
- D16: `planner_stats(planner_id, view_count, upvotes)` extraction, flag-gated cutover in this PR (user ruling: all fixes, one PR) — resolves counter clobber, binlog amplification, and 409 semantics at once. Dual-write is unconditional from this version; reads flip via flag after reconciliation; the column drop is a follow-up PR.
- D17 (default): The cutover flag rides the existing Kustomize overlay ConfigMap mechanism (exemplar: `DATASOURCE_ROUTING_ENABLED`, `deploy/overlays/seoul/configmap-patch.yaml:30`) — Terraform in this repo owns the AWS platform plane (`terraform/` — VPCs, ASGs, RDS peering, Route53, secrets/IAM), never k8s app config; giving one resource class two owners invites drift. The user's Terraform preference was redirected on this ownership evidence.
- D18: `takenDownAt IS NULL` predicate joins the rewritten list queries — no production hole exists (the `takeDown()` entity invariant unpublishes, `togglePublished()` blocks republish), so this is defense-in-depth aligned with the JPQL siblings, riding the projection rewrite at zero extra surface.
- D19: Eagerly register `replica_miss_promoted_total` at construction — a lazily-registered counter renders *No data* instead of a truthful zero, indistinguishable from a broken scrape.

## Description

Seoul-region users experience 60–94x latency penalties on specific endpoints because read paths execute as write transactions against the Oregon primary — multiplying one unavoidable ~130ms RTT by statement count while holding a needless exclusive row lock that converts latency into lock-wait contention for all viewers. Separately, the Oregon primary logs slow queries because no index covers the published-list predicate + sort, forcing wide-row filesorts that spill to disk. Fix both families, harden the incidental findings (unbounded HTTP client, 500-on-conflict, counter clobber, phantom SSE events), and extract counters to `planner_stats` behind a cutover flag.

Endpoints and targets: `GET /api/planner/md/published/{id}` Seoul p50 1.25s → <100ms; `GET /api/user/settings` 0.59s → <50ms; `POST /api/auth/logout` p90 0.61s → <300ms; both slow-query families eliminated.

## Scope

- `backend/src/main/java/org/danteplanner/backend/planner/` — `PublishedPlannerQueryService`, `PlannerRepository`, `PublishedPlannerController`, `PublicPlannerResponse`, `Planner`, `PlannerCommandService`, `PlannerPublishingService`
- `backend/src/main/java/org/danteplanner/backend/user/` — `UserSettingsService`, `UserController`
- `backend/src/main/java/org/danteplanner/backend/auth/` — `AuthenticationFacade`, `TokenBlacklistService`, `RefreshRotationService`
- `backend/src/main/java/org/danteplanner/backend/comment/` — `CommentService` (Redis publish inside tx)
- `backend/src/main/java/org/danteplanner/backend/shared/` — `ReadOnlyRoutingDataSource`, `PrimaryReCheck`, `GlobalExceptionHandler`, `HttpClientConfig`
- `backend/src/main/resources/db/migration/` + its `CLAUDE.md` (seed rule), `deploy/overlays/*/configmap-patch.yaml`
- Exemplars: `PlannerEngagementService.castVote` (AFTER_COMMIT), `NotificationEventListener`, `UserCleanupScheduler`, `RefreshRotationService` (Lua)

## Target

Create: `PlannerSummaryRow` (planner/dto), view-recorder service (buffer + `@Scheduled` flush), revocation Lua resource, `PlannerStats` entity + repository, three migrations (index swap; `user_settings` backfill; `planner_stats` create + backfill), cutover flag property.
Modify: `PlannerRepository` (projection queries, delete `findByIdForUpdate`), `PublishedPlannerQueryService` (readOnly + view extraction), `PublishedPlannerController` (`createPageable` collapse), `PublicPlannerResponse` (`from(row)`), `UserSettingsService` (readOnly + fallback), `AuthenticationFacade` (resolution seam, Lua logout), `HttpClientConfig` (timeouts), `GlobalExceptionHandler` (OOLFE→409+reason), `Planner` (`updatable=false`), `PrimaryReCheck` (eager counter), `PlannerPublishingService`/`PlannerCommandService`/`CommentService` (AFTER_COMMIT moves), `backend/src/main/java/.../CLAUDE.md` (D2 qualifier), both overlay ConfigMaps (flag, default false), `migration-test-seed.sql` (data-modifying backfills).

## Invariants

- INV1: At most one `planner_views` row per (planner, viewer, day) — verify: containerized IT, concurrent double-record → 1 row.
- INV2: `view_count` (and its `planner_stats` twin) increments iff a view row was inserted; replayed flush causes no double count — verify: IT with replayed batch.
- INV3: A taken-down planner never appears in any published list and cannot be republished — verify: characterization IT (4-planner seed) + entity unit test on `togglePublished()`.
- INV4: Published-list responses carry the same fields as today (field parity, incl. `sync_enabled` NULL semantics on settings) — verify: characterization IT snapshot.
- INV5: Logout revocation is atomic — access blacklist, refresh blacklist, family revoke all-or-nothing — verify: IT on the Lua script.
- INV6: Concurrent planner upserts yield 409 (with `reason`), never 500 — verify: two-thread IT race.
- INV7: Every active user has a `user_settings` row; a missing row degrades to defaults + anomaly log, never 500 — verify: backfill IT + fallback IT.
- INV8: Entity saves never overwrite counter columns — verify: IT, save-after-bulk-increment preserves value.
- INV9: A fresh publish is visible from Seoul (replica miss promotes to primary) — verify: metric `replica_miss_promoted_total` present (zero or more) on `/actuator/prometheus`; live: panel renders a number, not *No data*.
- INV10: With the cutover flag off, reads serve legacy columns; with it on, `planner_stats`; dual-write keeps both consistent (reconciliation checksum = 0 diff) — verify: IT both flag states + checksum query.

## Behavior Inventory

| # | Seam | Observable behavior (as-is) | Verdict |
|---|------|-----------------------------|---------|
| B1 | `GET published/{id}` | Full detail + per-viewer engagement flags | preserved |
| B2 | `GET published/{id}` | View counted once per viewer per UTC day | preserved (async, ≤1 flush window delay) |
| B3 | `GET published/{id}` | Response `viewCount` reflects all prior views synchronously | dropped — see D4 (client renders count+1 optimistically) |
| B4 | `GET published/{id}` | Viewers of the same planner serialize on a row lock | dropped — see D3 |
| B5 | Published list | Excludes deleted, unpublished, taken-down planners | preserved (+ predicate defense, D18) |
| B6 | Published list | `sort=popular`/`votes` honored | dropped — see D11 |
| B7 | Published list | Unknown `sort` falls back to `recent` | preserved |
| B8 | `GET /settings` | Creates row on first access | dropped — see D6 (created with user; backfill) |
| B9 | `GET /settings` | `sync_enabled` NULL drives first-login dialog | preserved |
| B10 | Logout | Revokes access + refresh + family; absent/invalid tokens → fast silent success | preserved (atomicity strengthened, D8) |
| B11 | OAuth callback | Returning user: single lookup round trip | preserved (read stays outside tx, D7) |
| B12 | Upsert conflict | Concurrent write surfaces as 500 "Unexpected error" | dropped — see D14 |
| B13 | Publish/comment/sync | SSE/Redis events fire inside the transaction (phantom on rollback) | dropped — see AFTER_COMMIT moves; event *emission* preserved |
| B14 | Upsert flush | Full-column UPDATE restores stale counter snapshots | dropped — see D15/D16 |

## Done When

- [ ] Characterization + invariant ITs green (INV1–INV8, INV10) (local-tdd)
- [ ] EXPLAIN on Flyway-migrated MySQL: page query uses `idx_v0NN_published_recent`, no filesort; count query `Using index` (infra)
- [ ] `sort=popular|votes` removed; unknown sort still falls back to `recent` (local-tdd)
- [ ] Logout path executes exactly one Redis round trip (script), verified by IT (local-tdd)
- [ ] `RestTemplate` refuses to hang: timeout IT or config assertion (local-tdd)
- [ ] Flag off: legacy reads; flag on: stats reads; dual-write checksum clean (local-tdd + infra)
- [ ] All existing tests pass; `migration-test-seed.sql` updated for data-modifying backfills (local-tdd)
- [ ] Seoul p50: `published/{id}` <100ms, `/settings` <50ms; logout p90 <300ms — 24h Grafana soak (live-only)
- [ ] Both slow-query families absent from the slow log over 48h (live-only)
- [ ] Promotion panel renders 0, not *No data* (live-only)

## Deferred

- Legacy counter column drop + flag removal — follow-up PR after flag flip + clean reconciliation; until then: `planners.view_count`/`upvotes` linger with the `updatable=false` guard (harmless, invisible to users).
- Cutover flag flip — operational step after merge + reconciliation; until then: reads serve legacy columns (correct, just pre-cutover).
- FE `reason`-aware conflict handling (auto-resolve when contents identical) — until then: a `CONCURRENT_WRITE` 409 shows the normal conflict dialog even when both copies are identical.

## Test Plan

### Test Runner
- Framework: JUnit 5 + Testcontainers (MySQL/Redis), tag `containerized`
- Run command: `./gradlew -p backend test` (from repo root; `-PexcludeTags=containerized` for the fast tier)

### Tests to Write
- [ ] Characterization: 4-planner seed list membership + field parity + sort fallback: `backend/src/test/java/.../integration/`
- [ ] View pipeline: PK dedup, conditional increment, replayed-flush idempotency (INV1, INV2)
- [ ] Entity: `takeDown()` unpublishes; `togglePublished()` throws when taken down (INV3, fast tier)
- [ ] Logout Lua atomicity (INV5); upsert race → 409 + `reason` (INV6)
- [ ] Settings backfill + defensive fallback (INV7); counter clobber guard (INV8)
- [ ] Flag-state matrix + reconciliation checksum (INV10)
- [ ] Every invariant above realized; every preserved Behavior Inventory row pinned by a characterization test
