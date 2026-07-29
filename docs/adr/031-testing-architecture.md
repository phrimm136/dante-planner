# 031 testing-architecture
epic: none · pr: none

## Decisions
- @tests @network-mocking — Network-level mocking is mandated only at the real network boundary; rendering a hook against a fake adapter is the blessed approach for state and orchestration. REJECTED: mandating network mocking everywhere — it inserts a transport layer into tests of code that never touches transport, and the setup cost is paid by every test to benefit the few that cross the boundary.
- @tests @characterization — Any class a sweep or an extraction touches has its observable behavior pinned first, at the granularity the change could break it: whole-object equality assertions are rewritten to field assertions before equality semantics change, and endpoints whose payload types convert get a wire-level test before the conversion. Characterization tests are a golden master of *current* behavior, including orderings that look wrong. Fixing behavior during a refactor is forbidden; the fix ships separately, where it appears as an intentional test change rather than as noise inside a mechanical diff.

## Takeaway
- takeaway: coverage is usually inverted relative to risk, because the well-tested code is the code that was easy to test. Checking which files a change touches against which files have tests, before starting, tends to find that the stateful spine has none and the pure helpers have hundreds.
