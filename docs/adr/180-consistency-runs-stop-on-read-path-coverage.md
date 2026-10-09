# 180 consistency-runs-stop-on-read-path-coverage
epic: none · pr: none @consistency-checker @generator

## Decisions
- @consistency-checker @generator — The consistency run chooses its faults to cover the read path,
  not by fixed probabilities. Its targets are the branches that the ADR 008 contract line names:
  the gate pins the primary, the gate serves the replica, a replica miss is promoted to the primary,
  a tombstone hit, and a withdrawal-marker hit. The run reads the application's own counters before
  and after each seed, runs its whole seed budget, and fails unless every target was hit. A calibration step at the start measures the replication latency at which the gate starts
  to pin the primary on every probe. Seeds then draw a latency from zero to that value, and a
  stopped replica stands for the unbounded end.
  REJECTED: fixed fault probabilities and latency values — each constant was a guess. A 50% fault
  draw left default seeds with no stale read, and a seed set that misses a branch passes silently.
  REJECTED: reading the gate's probe timeout from the code instead of measuring — the measured
  boundary is the one that matters. A slower machine, such as a CI runner, moves it.
  INVARIANT: a passing run hit every target at least once — the coverage assertion in the run.
  NON-GOAL: tuning toward rare interleavings beyond branch coverage — the seed budget bounds the run
  time, and branch coverage is the claim the run makes.
- @consistency-checker @observability — The application counts tombstone hits and withdrawal-marker
  hits, beside the existing gate and promotion counters. Two of the five targets had no counter, so
  the run did not see whether those mechanisms worked. The counters also show production whether
  the mechanisms engage.
  REJECTED: inferring the hits from the recorded history — a 404 after a delete looks the same in
  both cases. The tombstone masked a stale replica, or the replica already applied the delete.

## Takeaway
- takeaway: replace a generator's constants with coverage targets taken from the code under test.
  The only numbers left are budgets, and the run reports whether the budget was enough.
