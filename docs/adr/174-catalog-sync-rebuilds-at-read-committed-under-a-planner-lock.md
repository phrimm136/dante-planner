# 174 catalog-sync-rebuilds-at-read-committed-under-a-planner-lock
epic: none · pr: none @catalog @isolation @locking

## Decisions
- @catalog @isolation @locking — A planner's catalog sync runs at READ COMMITTED and first locks that
  planner's `planner_content` row `FOR UPDATE`, on both the rebuild and the clear path. Under
  REPEATABLE READ the rebuild's DELETE, when it matched no row, took a gap lock on the filter index,
  so two first syncs of planners in the same gap deadlocked on each other's insert intention (1,629
  deadlocks in 12,600 rebuilds on MySQL 8.4). READ COMMITTED takes no gap lock. Without a lock on the
  content row, READ COMMITTED lets a content change commit between a sync's statements and leave a
  superseded filter row beside the current ones; the row lock keeps that write out and serializes
  two syncs of the same planner.
  REJECTED: skipping the DELETE when the planner has no rows — an `IF EXISTS` check inside the
  procedure is itself a gap-locking read with binary logging on, and a `SELECT ... INTO` check reads
  the transaction's earlier snapshot, so it skips a DELETE another sync made necessary.
  REJECTED: READ COMMITTED with the scope read's existing shared lock — the shared lock already keeps a
  content write out while a sync runs, but lets two syncs of one planner rebuild side by side; the
  exclusive lock makes the per-planner order explicit at the cost of one sync waiting for the other.
  INVARIANT: `syncPlanner` runs at READ COMMITTED and its first statement locks the planner's content
  row exclusively — `PlannerCatalogSyncServiceTest` (declaration and lock order) and
  `PlannerCatalogSyncConcurrencyIT` (no deadlock across a shared gap; no superseded row across a
  content change).
  NON-GOAL: changing `rebuild_planner_filters_scoped` — the procedure is unchanged, so no migration
  and no rollout window.

## Takeaway
- takeaway: a delete-then-insert rebuild under REPEATABLE READ deadlocks on empty gaps; drop the gap
  locks with READ COMMITTED and buy back per-key ordering with an explicit row lock.
