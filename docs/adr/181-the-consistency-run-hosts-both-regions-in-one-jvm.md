# 181 the-consistency-run-hosts-both-regions-in-one-jvm
epic: none · pr: none @consistency-checker @regions

## Decisions
- @consistency-checker @regions — The cross-region consistency run uses the cached Seoul-configured test
  context and starts an Oregon-configured application context beside it with
  `SpringApplicationBuilder`, on the shared causal test containers. The Seoul context reads the replica. The Oregon context has no
  replica and falls back to the same replica. The generator plays the edge rule: every write goes
  to the Oregon context, and a read goes to its session's home region. A dedicated proxy carries
  only the Oregon context's primary connection, so an Oregon primary outage leaves Seoul intact.
  A context built this way stays outside the test context cache and its budget. Both run the `it`
  profile's schedules, which push the background jobs a day out; the Oregon primary probe runs, as
  the fallback needs it. The routing override's thread-local is static and shared by both contexts; every pin clears it
  in a `finally` around the request it pins.
  REJECTED: two application processes from the boot jar — this adds one more JVM on a memory-bound
  machine and starts slower. The run then reads counters over HTTP instead of from the registry.
  REJECTED: one context that switches region settings between operations — the two regions never
  run at the same time, so the boundary under test never forms.
  INVARIANT: a routing override set by one context's request never reaches the next request on the
  same thread, in either context — `TwoRegionHarnessIT`.
  NON-GOAL: modelling geo steering faults or the edge rule's probe mode — the run fixes the rule to
  "writes go to Oregon".
- @consistency-checker @rules — The checker applies one contract to every session, whatever region
  serves it. ADR 123 promises that a misrouted request is slower, never wrong, so a region-specific
  relaxation would contradict a written promise. A violation inside Oregon's read fallback is
  reported as a violation. The owner then decides between fixing the application and recording the
  window as an accepted residue with its own rule exception.
  REJECTED: counting reads during the fallback as anomalies from the start — the run would pass
  over an unrecorded gap, and the checker would hide what it exists to find.

## Takeaway
- takeaway: a cross-region guarantee needs both regions running at once. Separate contexts in one
  JVM give that at the cost of shared static state, which every use must clear where it sets it.
