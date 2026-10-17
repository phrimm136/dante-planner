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
  INVARIANT: a passing run hit every target at least once — `ConsistencyRunIT`, through `Coverage`.
  NON-GOAL: tuning toward rare interleavings beyond branch coverage — the seed budget bounds the run
  time, and branch coverage is the claim the run makes.
- @consistency-checker @generator — When the base seed budget ends with a target unhit, the run
  continues seed by seed (the next seeds after the base range) until every target is hit or an
  extension cap is spent, and fails only at the cap. The plans stay random: a key draw resolves
  against the planner pool at run time and the pool changes under creates and deletes, so a
  plan-level condition ("publish, unpublish and read the same planner") cannot be stated
  without a different key model, and a fixed directed round would narrow the random mix the
  run exists to exercise. The withdrawal-marker target was hit 0, 1 and 3 times in three runs
  of the same fixed plans, so the hit depends on timing, and more seeds is the honest lever.
  REJECTED: a directed round per target — covers by construction but replaces the random mix
  with a script for exactly the branches the random mix is meant to reach.
  REJECTED: plan conditioning with named keys — guarantees the sequence, costs a key model and
  a resampling loop, and still leaves the hit to timing unless the sequence is pinned to the
  stopped-replication phase.
  REJECTED: a larger fixed budget — pays every run for the rare case.
  INVARIANT: a passing run hit every target at least once, within the base budget plus at most
  the extension cap of extra seeds — `ConsistencyRunIT`, through `Coverage` and `SeedLoop`.
  NON-GOAL: a per-target hit minimum above one — coverage is the claim, not frequency.
- @consistency-checker @observability — The application counts tombstone hits and withdrawal-marker
  hits, beside the existing gate and promotion counters. Two of the five targets had no counter, so
  the run did not see whether those mechanisms worked. The counters also show production whether
  the mechanisms engage.
  REJECTED: inferring the hits from the recorded history — a 404 after a delete looks the same in
  both cases. The tombstone masked a stale replica, or the replica already applied the delete.

## Takeaway
- takeaway: replace a generator's constants with coverage targets taken from the code under test.
  The only numbers left are budgets, and the run reports whether the budget was enough.
