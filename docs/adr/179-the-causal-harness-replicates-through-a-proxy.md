# 179 the-causal-harness-replicates-through-a-proxy
epic: none · pr: none @causal-harness @replication

## Decisions
- @causal-harness @replication — The shared causal test setup points its replica at a Toxiproxy
  proxy in front of the primary, so every causal integration test replicates through it. A latency
  toxic on that proxy gives a continuous replication delay. Stopping and starting the replica alone
  gives only two states, stopped and caught up. A test that adds a toxic removes it itself, and every
  test that uses the shared containers starts by clearing the replication proxy.
  REJECTED: a second primary and replica pair for the consistency run — it adds two containers per
  JVM. The test context budget is also full, so a new base class cannot get a context.
  REJECTED: pointing the replica at the proxy only during the consistency run — a switch back can fail.
  The next test class then runs on a replication topology that it did not set up.
  INVARIANT: every test that uses the shared containers starts on a replication proxy with no toxics —
  the per-test reset in `CausalHarnessSupport` and `OregonReadFallbackIT`, pinned by `ReplicationProxyIT`.
  NON-GOAL: blocking the replication stream with a timeout toxic — the replica raises no error
  before the 60 s heartbeat timeout. The stall then looks the same as a stop and adds nothing.

## Takeaway
- takeaway: a fault injector belongs on the link that it degrades. Stopping a component gives two
  states, and a proxy on the link gives the range in between.
