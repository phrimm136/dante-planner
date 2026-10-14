# 185 search-rows-change-with-their-planner
epic: none · pr: none

## Decisions
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

## Takeaway
- takeaway: a removal that several writers race must be idempotent, not unique, and it must lock in
  the order the other writers of the same rows already use.
