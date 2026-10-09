# 179 the-causal-harness-replicates-through-a-proxy
epic: none · pr: none @causal-harness @replication

## Decisions
- @causal-harness @replication — The shared causal test setup points its replica at a Toxiproxy
  proxy in front of the primary, so every causal integration test replicates through it. A latency
  toxic on that proxy gives a continuous replication delay. In a spike with MySQL 8.0 and Toxiproxy
  2.5.0, the delay was the toxic value plus about 100 ms. Stopping and starting the replica alone
  gives only two states, stopped and caught up. Only the consistency run adds toxics, and it
  removes them in a `finally` at the end of every seed, as it restarts replication there.
  REJECTED: a second primary and replica pair for the consistency run — it adds two containers per
  JVM. The test context budget is also full, so a new base class cannot get a context.
  REJECTED: pointing the replica at the proxy only during the consistency run — a switch back can fail.
  The next test class then runs on a replication topology that it did not set up.
  INVARIANT: no toxic stays on the replication proxy after a test class ends — the `CausalHarnessSupport` after-all hook, pinned by `ReplicationProxyIT`.
  NON-GOAL: blocking the replication stream with a timeout toxic — the replica raises no error
  before the 60 s heartbeat timeout. The stall then looks the same as a stop and adds nothing.

## Takeaway
- takeaway: a fault injector belongs on the link that it degrades. Stopping a component gives two
  states, and a proxy on the link gives the range in between.
