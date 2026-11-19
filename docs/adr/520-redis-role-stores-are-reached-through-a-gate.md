# 520 redis-role-stores-are-reached-through-a-gate
epic: none · pr: none

## Decisions
- @redis @degradation @breaker — A request-path consumer reaches a Redis role's store only through a gate that holds the store and takes, in the same call, what to do while the role's breaker is open; the gate counts each call it skips. Consulting the breaker was a branch every caller had to remember, and nothing stopped a new caller from holding the store and waiting the three-second command bound; a caller that skipped and a caller that timed out and failed open raised the same fail-open counters, so only elapsed time told them apart, and one three-second read is inside the five seconds a loaded test runner is allowed.
  REJECTED: keeping the availability branch at each call site — the rule lives in the author's memory, and a missing branch still returns the right answer three seconds late.
  REJECTED: an integration-test time bound on every request path — it separates the two paths only where two or more reads wait in turn, and it measures the runner as much as the code.
  REJECTED: the breaker library's own permission call and its not-permitted counter — a permission request on an open breaker moves it to half-open once its wait has passed, and only the probe may change a role breaker's state.
  INVARIANT: outside the gate, no production class reads a role breaker's state or holds a Redis store handle, except the classes that define the stores and the classes on a frozen list — enforced by an architecture test whose lists fail when an entry no longer needs its exemption.
  INVARIANT: a call through the gate of an open role does not invoke the store and adds one to that role's skip count — enforced by the gate's unit test.
  INVARIANT: a read that goes through a gate does not wait on a role whose breaker is open — enforced by the outage integration test reading the gate's skip count for each request; a read added outside the gate by a class on the frozen list is unenforced.
  NON-GOAL: handling a store failure inside the gate — a command that fails behind a closed breaker stays the caller's fail-open or fail-closed decision.

## Superseded
- @redis @degradation @breaker → 522 — NON-GOAL: gating the auth-Redis writes, the SSE publish and subscription, the scheduler lock, view recording and the freshness marker store — each needs its own answer for an open role; they stay on the frozen list until they have one.

## Takeaway
- takeaway: a rule every caller must remember becomes a parameter no caller can omit; then one unit test proves it for all of them and a counter makes it observable.
