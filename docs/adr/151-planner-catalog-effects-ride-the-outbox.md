# 151 planner-catalog-effects-ride-the-outbox
epic: none · pr: none

## Decisions
- @outbox @catalog @filters — Every change that can alter a planner's catalog presence (publish,
  unpublish, takedown, delete, a visible edit that changes content, keywords or category, owner
  deactivation and reactivation, and a keyword backfill correction) records one
  planner-catalog-changed event per planner in the transaction of its cause, and one scheduled
  consumer, held by a single instance at a time, drains those events every second: it groups the
  pending rows by planner, and for each planner reads the committed state, rebuilds or clears the
  filter rows, writes or clears the withdrawal marker or writes the delete tombstone, and marks
  that planner's rows up to the highest id it read as done, in one transaction. The filter rebuild
  ran in an after-commit listener in its own transaction, so a throw or a crash after the commit
  lost it with nothing to retry from, and the reconciler only reports drift. A single consumer
  writes the filter tables one planner at a time, so no two syncs contend for their gap locks and
  none finishes out of order; a crash leaves the rows pending and the next pass repeats a sync
  that yields the same result, and a burst of changes to one planner costs one sync.
  REJECTED: syncing on the request thread and again from the event's eager dispatch — the two ran
  at once and deadlocked on the filter rows' shared gap, and row locks strong enough to order them
  deadlocked with account reactivation.
  REJECTED: per-event dispatch with a per-planner advisory lock — syncs of different planners
  still deadlocked on the filter indexes' gap locks.
  REJECTED: per-event dispatch under one global lock — the same serialization without the
  coalescing, so a save burst queues one sync per change.
  REJECTED: separate rebuild and clear event types — a retried or reordered pair can finish on the
  wrong one, where a re-derived effect always converges on the current state.
  REJECTED: repairing drift from the reconciler — the silent auto-repair 016 rejected, and repair
  arrives a reconcile period late.
  INVARIANT: after the relay grace window, a planner's filter rows and withdrawal marker match its
  committed visibility — the catalog sync integration tests (a failed sync leaves its rows pending
  and the next pass repairs it; filter rows, markers and tombstones). A pending row older than the
  alert threshold pages — the catalog sync backlog alert.
  NON-GOAL: the author's own filtered read right after a change is not guaranteed to see it; it
  sees it after the next pass, within about a second plus replication.
  NON-GOAL: the event carries no instruction; its payload is not read to decide the effect.
- @outbox @tombstone @readpath — A delete still writes its tombstone on the request thread after
  the commit, before the response returns, and the catalog sync writes it again; every other
  marker write belongs to the catalog sync alone. A delete must be masked before its response,
  which only the synchronous write provides; the sync covers the crash and store-error windows,
  failing that planner's pass on a store error so its rows stay pending. The sync may write a
  tombstone inside its transaction because the value it writes follows from state already
  committed, so a rollback cannot leave it masking a live row.
  REJECTED: moving the delete tombstone to the outbox alone — the eager dispatch is asynchronous,
  so a read right after a delete response could be served the deleted row from a lagging replica.
  INVARIANT: no transactional method other than the catalog sync reaches a tombstone write before
  its commit — the effect placement rule, which admits that one method.
- @comments @visibility — A comment thread follows its planner's public view: a reader other than
  the owner gets the planner-not-found answer for an unpublished planner, a thread read that hits a
  withdrawal marker re-reads on the primary, and the thread's public push fires only while the
  planner is still published. The owner's own comment notification ignores publication; a reply
  notification to any other parent author fires only while the planner is published, since it
  names the planner and quotes the reply to someone who would otherwise get not-found.
  REJECTED: a forbidden answer for another reader — it confirms that a private planner exists,
  which the planner read itself refuses to do.
  INVARIANT: an unpublished or withdrawn planner's thread is unreadable to non-owners on every read
  path — the thread contract test and the replica-lag test.

## Takeaway
- takeaway: an effect that re-derives from committed state can be retried, reordered and repeated
  freely; one that carries its instruction cannot. Running the same effect twice for a faster
  read bought a race the single run never had.
