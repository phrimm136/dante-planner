# 199 account-purge-deletes-at-read-committed
epic: none · pr: none

## Decisions
- @accounts @purge @isolation — The hard-delete transaction (`performHardDelete`) runs at READ
  COMMITTED. Its projection deletes are JPQL bulk deletes keyed by a planner-id list, which
  Hibernate renders in the aliased multi-table form; MySQL plans that form as a full scan whenever
  the table's row estimate is three or fewer (a fresh schema, a test database, a table just
  emptied), and under REPEATABLE READ a scanning delete holds next-key locks on every row it
  reads and on the supremum. Measured: the delete deadlocked against a concurrent publish (which
  already runs at READ COMMITTED) on the primary keys of `planner_catalog` and
  `planner_entity_filter`; EXPLAIN shows `type=ALL` for the aliased form at estimate ≤ 3 and
  `range` otherwise, and `range` for the unaliased form at any size. READ COMMITTED releases
  the locks of rows the delete does not match and takes no gap locks, so the lock footprint no
  longer depends on the optimizer's plan. The purge reads its planner ids once and deletes by
  that list while the account is already soft-deleted, so it needs no repeatable read.
  REJECTED: index hints on the eight bulk deletes — fixes today's plan, leaves the ninth bulk
  write to rediscover the rule, and moves the queries to native SQL.
  REJECTED: seeding more rows in the test database — hides the plan that any small table in
  production also gets.
  INVARIANT: every transactional method that reaches a planner-projection bulk delete
  (`deleteAllByPlannerIds`, `deleteViewsByPlannerIds`) declares READ COMMITTED — the
  architecture test's caller walk from those repository methods.
  NON-GOAL: changing the default isolation of the application — the two cases that need READ
  COMMITTED declare it; everything else keeps the server default.
- @tests @deadlocks — The shared test mysqld runs with `innodb_print_all_deadlocks` on and its
  log forwarded to the test JVM's stdout, so a deadlock in the suite leaves both transactions'
  statements and locks in the test report. The report lands in whichever test's output owns
  stdout at that moment, so triage searches every report for `mysqld: TRANSACTION`.
  NON-GOAL: `log_error_verbosity=3` for the deadlock headers — the bodies name the locks, and
  the extra notes would flood every report.

## Takeaway
- takeaway: under REPEATABLE READ a write's lock footprint is the optimizer's plan, not the
  statement's intent; a bulk write that must not block its neighbours declares READ COMMITTED
  rather than trusting the plan.
