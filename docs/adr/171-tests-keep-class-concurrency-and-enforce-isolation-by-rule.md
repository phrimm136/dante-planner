# 171 tests-keep-class-concurrency-and-enforce-isolation-by-rule
epic: none · pr: none @tests @concurrency @deletion

## Decisions
- @tests @concurrency — Backend test classes keep running concurrently inside each Gradle fork, three
  at a time in random order, and each hazard that concurrency exposes is closed by a rule that names
  it rather than by serializing the suite. Concurrent classes share one database, one Redis and one
  application context per configuration, so a class that reaches past its own rows and keys breaks a
  neighbour.
  REJECTED: turning in-fork class concurrency off — measured over three runs each, the suite took
  58 s longer on average (+25%), with the wall-clock ranges not overlapping, and peak memory did not
  move outside the run-to-run noise.
  INVARIANT: a test class shares the database only for rows it created: it truncates nothing, deletes
  no table without a WHERE, scans no shared Redis keyspace, and takes no hard-coded identity in a
  shared namespace — the test-isolation convention scan.
  NON-GOAL: lowering memory by running fewer threads, shrinking the context cache or lowering the
  per-fork heap — memory falls by sharing containers and merging context keys instead.
- @tests @deletion — A test is deleted only when one of five criteria holds, with its evidence
  recorded: it kills no mutant in a full mutation-matrix run (unit tests only) and no behavior it
  checks is left unchecked by other tests; it duplicates another test's arrange, act and assert; it
  asserts only what its own stubs return; it covers a case the types or validation can no longer
  produce; or a test at another tier covers the same scenario with stronger assertions.
  REJECTED: deleting by reviewer judgment — coverage then shrinks by taste, and a deletion cannot be
  checked afterwards.
  INVARIANT: a regression test for a real bug and a test that pins a published contract (a response
  shape, an error code the frontend matches on) are never deleted — unenforced.
  NON-GOAL: line coverage as a deletion criterion — it shows that a line ran, not that a test
  protects it.

## Takeaway
- takeaway: when concurrency in a test suite exposes shared state, close each hazard with a rule
  that names it; serializing hides the hazards and costs wall clock without saving memory.
