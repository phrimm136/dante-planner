# 566 a-write-is-an-explicit-command
epic: none · pr: none · supersedes: 056 @persistence @convention

## Decisions
- @persistence @convention — A write is an insert (an `INSERT IGNORE` when a unique key owns the invariant) or a conditional `UPDATE` or `DELETE` whose WHERE holds the race's precondition, and the matched-row count, read as `WriteOutcome`, says whether it happened. An entity is `@org.hibernate.annotations.Immutable`, so a field changed on a loaded one is never written. Dirty checking writes the whole row as it was read, so a stale read reverts a concurrent change to any column of that row; and a pending insert is dropped by a query that clears the persistence context: production held 0 `planner_comment_votes` rows against counters that summed to 187. Re-check: `ops sql "SELECT COUNT(*) FROM planner_comment_votes"`.
  REJECTED: dirty checking with `@DynamicUpdate` and `@Version` — the write stays implicit, a row that mixes a counter with user text still conflicts, and neither keeps a pending insert from being cleared.
  REJECTED: Spring Data JDBC — its update also writes the whole row from what was read.
  REJECTED: jOOQ or JdbcClient for every write — jOOQ adds a code generator and a dependency, JdbcClient loses the startup check that a query's names resolve; neither is needed once nothing is written implicitly.
  INVARIANT: every entity is `@Immutable` except the lines of `backend/config/mutable-entities-MODULE.txt`, which only shrink — `ImmutableEntityRuleTest`.
  INVARIANT: no field of an immutable entity is written outside its own constructors and persist or load callbacks — `ImmutableEntityRuleTest`.
  INVARIANT: no `@Modifying` query clears the persistence context except the lines of `backend/config/context-clearing-MODULE.txt`, which only shrink — `PersistenceContextClearingTest`.
  INVARIANT: a JPQL `UPDATE` may name an immutable entity (`hibernate.query.immutable_entity_update_query_handling_mode=allow`) — `UserProfileImmutableIT`.
  NON-GOAL: converting the listed entities — each leaves its list in the unit that converts it.
  NON-GOAL: a field written by reflection, as Spring Data auditing does — the rule reads bytecode field writes.
- @persistence @lists — The lists are one file per module, because one shared list is edited by every unit that converts an entity and conflicts between units that run at the same time.
  REJECTED: one list for every module — its adjacent lines are deleted by concurrent units, which then conflict on each merge.

## Takeaway
- takeaway: a persistence layer that writes what it happens to hold turns every stale read into a write; make the write a statement that names what it changes and when.
