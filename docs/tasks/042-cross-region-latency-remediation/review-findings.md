# Task-tier review findings (042)

Base `1fd050cb` .. phase HEAD `0d3330ba`. Four risk-routed lanes over the 22 production files,
plus a follow-up fix commit. Security lane verdict: ACCEPTABLE (no new exploitable defect).

## Fixed in the follow-up commit

- **REL-1 (reliability, real data-loss bug) — FIXED.** `PlannerViewRecorder.flush()` caught
  `DataIntegrityViolationException` and continued, but a failed Hibernate flush marks the tx
  rollback-only, so on a multi-pod dedup race the whole drained batch was lost at commit and the
  up-front `buffer.clear()` made it permanent. Fix: `PlannerViewRepository.insertIgnore` (idempotent
  `INSERT IGNORE` on the composite key) removes the violation entirely; the counter increments are
  gated on rows-affected, and the drain uses `removeAll(drained)` so records buffered during the
  flush survive (also closes REL-4).
- **REL-2 (reliability, replica-lag login 500) — FIXED.** `findOrCreateUser`'s race-recovery
  re-lookup ran in `SimpleJpaRepository`'s readOnly tx → replica, which may lag behind the winner's
  primary commit → `orElseThrow` → login 500. Fix: wrap the re-lookup in `transactionTemplate.execute`
  so it routes read-write to the primary.
- **PERF-1 (performance, index not serving the primary listing) — FIXED per user choice.** The base
  listing queries now filter `taken_down_at IS NULL`
  (`findByPublishedTrueAndDeletedAtIsNullAndTakenDownAtIsNull` etc.), so the V046 index prefix is
  fully equality-constrained before `created_at` and the `ORDER BY created_at` is index-served
  instead of filesorted. (Also excludes taken-down planners from the base listing, matching the
  recommended path.)
- **ARCH-1 (architecture, over-promising flag doc) — FIXED.** `StatsReadsFlag` Javadoc narrowed to
  the detail view-count scope it actually gates.
- **Stale Javadoc (architecture note) — FIXED.** `getPublishedPlanner` no longer claims it records
  the view in-transaction / returns the updated count; it buffers and returns the pre-request count.

## Deferred (follow-up rows — surfaced at the user gate)

- **REL-2 residual / REL-5 (ACCEPTABLE, pre-existing):** concurrent duplicate `castVote` returns 500
  instead of 409 (check-then-act on `existsById` predates this task). Catch the PK violation and
  translate to `VoteAlreadyExistsException`.
- **REL-1 residual:** a transient DB error mid-flush still loses the drained batch (buffer cleared
  before commit). Fully robust fix = re-queue on rollback via `TransactionSynchronization`; new
  untested behavior, warrants its own row.
- **PERF-2 (performance) — FIXED (10615b8d, tested 31b8a539):** the flush now issues one multi-row
  `INSERT IGNORE` per planner and one delta increment per store, and the flush window dropped to
  500ms. Was: O(n) round trips per buffered view.
- **ARCH-2 (architecture, ACCEPTABLE):** `StatsReadsFlag` lives in `planner/service` with no runtime
  toggle endpoint (only env-seeded + test `setEnabled`); the exemplar `LineageRotationFlag` has an
  internal toggle. Either wire a toggle or drop the AtomicBoolean pretense; move to `planner/config`.
- **Dead code (architecture note):** `PublishedPlannerQueryService.incrementViewCount` (legacy-only
  write, no callers) — remove or it will silently under-count `planner_stats` if ever wired.
- **Security observation (pre-existing, not this diff):** lineage-ON logout of a *legacy* refresh
  token (no `family_id`) blacklists but doesn't revoke a family; `rotate()` doesn't consult the
  blacklist for legacy-admitted tokens. Bounded to pre-deploy legacy tokens within the refresh TTL.

## Not a defect

- **REL-3 (stats read collapse):** cannot occur — V048 backfills every existing planner and new ones
  start 0/0 in lockstep via dual-write; the flip is gated on the reconciliation checksum reading zero.
- **PERF-3:** the extra `planner_stats.findById` on the flag-on detail read is the intended cutover
  tradeoff.
- Seam D1 (read-only replica routing across phases 01/03): reviewed clean — reads stay readOnly,
  writes live in non-readOnly transactional methods.
