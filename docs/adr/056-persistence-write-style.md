# 056 persistence-write-style

## Decisions

- @persistence @convention — `repository.save()` is banned outside repository
  interfaces. New rows go through an `insert()` default method on the repository that
  rejects entities with an assigned id, and a frozen ArchUnit rule enforces the ban
  (existing violations recorded in the store, burned down over time, no new ones
  admitted). Dirty checking inside the transaction is the stated persistence mechanism
  for mutations of managed entities — most existing `save()` calls were redundant
  merges over it.
  REJECTED: explicit `save()` after each mutation — dead code advertising a mechanism
  that is not the one doing the work, and unenforceable as a "only when new" rule.
  REJECTED: converting entity mutations to direct SQL — loses transition reports
  (`PublicationChange`), multi-field invariants, and lifecycle callbacks, and
  multiplies persistence-context clearing.
- @persistence @convention — Multi-field state transitions run as entity methods,
  returning a report type when a caller branches on the outcome; single-field
  concurrent arithmetic and CAS flags stay `@Modifying` SQL, wrapped by a service only
  where an invariant needs a contract — the stats service's MANDATORY propagation,
  which the vote counter path now also routes through.
- @persistence @convention — In a method that runs a context-clearing `@Modifying`
  query, entity mutation completes before the query runs, or the entity is re-read
  afterward; each such site carries a warning comment, since the detachment is
  invisible at the call site.

## Takeaway

- takeaway: a convention that cannot be machine-checked decays; rename the operation
  until the rule becomes a fact about call sites, then ratchet it with a frozen rule.
