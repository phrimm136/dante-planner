# 185 search-rows-change-with-their-planner
epic: none · pr: none · supersedes: 151 @outbox @catalog @filters, 122 @writes @round-trip, 174 @catalog @isolation @locking

## Decisions
- @catalog @filters @transaction — A planner's search rows (`planner_catalog`,
  `planner_keyword_filter`, `planner_entity_filter`) are written, rebuilt or cleared in the same
  transaction as the change that alters them: publish, unpublish, takedown, delete, a visible edit,
  owner deactivation and reactivation, and a keyword backfill correction. The rebuild first locks
  the planner's content row `FOR UPDATE`, so no content change commits between the rebuild's
  statements and two rebuilds of one planner run one after the other. 183 @freshness promises that a
  filtered list reflects an answered write, and a filter row rebuilt a sync pass later is invisible
  to a GTID that the marker names. Measured on a full-size planner (154 entity rows, 3 keyword rows),
  the rebuild adds about 4 to 5 ms to the write.
  REJECTED: keeping the outbox consumer as the rebuild path — the filter rows trail the answered
  write by up to a sync pass, so a filtered list read at the marker's GTID can still be stale.
  REJECTED: masking stale filter rows at read time — a row that newly matches a filter cannot be
  masked into a list that never returned it.
  INVARIANT: after a write commits, the planner's search rows match its committed visibility and
  content — `PlannerPublishFlowIT`, `PlannerCommandFlowIT`, `DeactivatedOwnerVisibilityIT`,
  `PlannerKeywordFacetIT` and `PlannerFilterRebuildIT`.
  NON-GOAL: the outbox consumer for catalog effects — retired; a write that crashes rolls back its
  search rows with it.
  NON-GOAL: keeping the retired event type's dispatched outbox rows — a migration deletes them,
  since no reader is left; rows still undispatched when the release starts stay, because a pod of
  the previous release may still consume them during the rolling deploy.
- @catalog @isolation — A write transaction that rebuilds search rows runs at READ COMMITTED, set
  on that transaction; every other transaction keeps the server default. At REPEATABLE READ the
  rebuild's DELETE takes a gap lock on the filter index when it matches no row, and concurrent
  first rebuilds deadlocked on 1,142 of 2,400 transactions; at READ COMMITTED none of 2,400 did, and
  a mix of clearing and rebuilding writes saw none in 1,170. Account deletion keeps SERIALIZABLE
  for the last-administrator guard (ADR 173), and its clear path inserts nothing.
  REJECTED: READ COMMITTED as the connection default — it changes every transaction in every
  module to fix one table family, and nothing else was measured under it.
  REJECTED: a separate primary pool whose connections default to READ COMMITTED — it saves the
  isolation round trips only for writes that already run beside the primary.
  INVARIANT: every outermost transaction that reaches a search-row rebuild declares READ COMMITTED,
  and account deletion declares SERIALIZABLE — `WriteIsolationDeclarationTest`, which derives the
  outermost callers from the call graph.
- @catalog @rebuild-diff — The search-row rebuild writes only the difference: it computes the
  planner's new key set into a per-connection temporary table, deletes the planner's keys absent
  from it and inserts the keys it lacks, comparing keywords by binary collation. With a
  delete-everything-then-insert rebuild at READ COMMITTED, two neighbouring planners' rebuilds
  deadlocked on record locks of the filter primary key: 135 of 8,000 rounds when content changed
  and 9 of 9,000 right after a clear; the difference rebuild deadlocked on none of 17,000, cost
  about 0.3 to 1.1 ms more per call on a full-size planner, and matched the old rows exactly.
  REJECTED: skipping the delete when the planner has no live rows — it cures only the
  rebuild-after-clear shape; with live rows the delete still runs, 112 of 8,000 rounds deadlocked.
  REJECTED: retrying the deadlocked write — the inversion stays, and a save fails once retries run
  out.
  INVARIANT: concurrent rebuilds of neighbouring planners never deadlock —
  `PlannerFilterRowsConcurrencyIT`.
- @catalog @lock-order — A write that changes a planner's visibility or search rows locks that
  planner's `planner_content` row first, before any other planner row: owner saves, deletes,
  publishes and unpublishes, and moderator takedowns and unpublishes lock it when they load the
  planner; an account deactivation or reactivation locks the user's row and then every content row
  the user owns, in planner id order. The search-row rebuild locks the content row after the write
  has already changed the publication row, and the owner delete and a SERIALIZABLE account read
  lock content before publication, so without one order the rebuild deadlocked on 148 of 150
  unpublish-versus-deactivation rounds and 124 of 150 delete-versus-takedown rounds.
  REJECTED: retrying a write that hit a deadlock — it hides the inversion and still fails a write
  once the retries run out.
  REJECTED: moving the rebuild after the write's commit — the search rows trail the answered write
  again (@catalog @filters @transaction).
  INVARIANT: concurrent planner and account writes on one planner never deadlock —
  `PlannerCatalogRemovalConcurrencyIT`.
- @catalog @removal — A search row is removed by a statement that accepts zero affected rows.
  Unpublish, delete, takedown and account deactivation each remove the same `planner_catalog` row,
  and an entity delete requires exactly one row: the later remover waits on the first one's row
  lock, deletes nothing, and fails with an optimistic-lock error that rolls the whole write back
  (a takedown racing an owner's unpublish lost the takedown in 150 of 150 rounds).
  The statement flushes the caller's pending planner changes first, so every remover locks the
  planner's rows before the catalog row; an account deletion runs at SERIALIZABLE and holds shared
  locks on the publication rows while it withdraws the catalog, and the reverse order deadlocked
  against it on 148 of 150 rounds.
  REJECTED: retrying the losing transaction — the row it wants gone is already gone, so a retry
  only repeats the outcome the first attempt should have accepted.
  REJECTED: clearing the persistence context after the delete — callers keep using the planner
  they changed, and a cleared context detaches it.
  INVARIANT: concurrent removers of one planner's search rows all succeed, with no deadlock against
  an account deletion — `PlannerCatalogRemovalConcurrencyIT`.
- @writes @round-trip — Every round trip a write transaction makes to the primary carries a
  statement the business logic issued, the `COMMIT`, or a per-transaction isolation setting.
  Session-state queries, server-side prepares and the pool's checkout validation ping are
  bookkeeping and are not allowed. Over JDBC each statement costs a round trip; writes run beside
  the primary since ADR 123, where a round trip costs under a millisecond, but a misrouted write
  still pays an ocean crossing per round trip.
  REJECTED: a per-request round-trip budget enforced by a test — every feature that legitimately
  adds a statement must re-tune it, and each re-tune is indistinguishable from waving a regression
  through.
  REJECTED: exempting the server-side prepare and the checkout ping — keeps a round trip per
  transaction plus a prepare per statement on every replaced connection, and a rule stated with
  exceptions is harder to test than one without.
  REJECTED: banning the isolation setting too — the only way to keep it off the wire is a second
  pool or a fleet-wide default (@catalog @isolation), both worse than two local round trips.

## Takeaway
- takeaway: a derived row that a reader can see must change with its source, in one transaction;
  a removal that several writers race must be idempotent, not unique.
