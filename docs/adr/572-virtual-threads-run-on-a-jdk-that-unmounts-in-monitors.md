# 572 virtual-threads-run-on-a-jdk-that-unmounts-in-monitors
epic: none · pr: none · supersedes: 112 @threads @pinning

## Decisions
- @threads @pinning @jdk — The backend runs on JDK 25, a release in which a virtual thread blocked inside a `synchronized` block or method unmounts from its carrier (JEP 491, JDK 24). On JDK 21 such a thread keeps its carrier, and the carriers number the vCPUs, two on an app node. Caffeine runs a cache loader inside `ConcurrentHashMap.compute`, which holds a bin monitor, and the SSE subscribe path loads a user's notification settings that way: the loader waits for a pooled MySQL connection while it holds the monitor. When pinned waiters fill both carriers, the requests that hold the pool's connections cannot run again, and the pool empties only through its five-second acquire timeout. A reconnect storm against a cold cache on two carriers produced that stall on JDK 21, with pinned parks under the settings load, connections held for 15 s and acquire timeouts, and passed on JDK 25 with no code change. A production stall after a pod switch matched it: connections held for up to 29 s while MySQL ran three threads and the pod used a fifth of a core.
  REJECTED: keeping JDK 21 and moving the one loader out of the compute call (`getIfPresent`, then `put`) — it fixes this site, but every later monitor around blocking work, in this code or in a library, pins again, and no check finds such a site before production does.
  REJECTED: raising the carrier count with `jdk.virtualThreadScheduler.parallelism` — it only raises the number of pinned waiters a storm needs.
  INVARIANT: the toolchain, the build image and the runtime image name the same JDK major — a runtime older than the toolchain fails at startup on the class file version; that the major is at least 24 is unenforced.
  INVARIANT: a reconnect storm of logged-in SSE clients against a cold settings cache, on two carriers, records no pinned park in application code and answers every read with 200 — ReconnectStormIT.
- @threads @pinning @tests — Every test JVM, the mutation-testing workers included, runs with two virtual-thread carriers, the app node's vCPU count. A pinning site starves the scheduler only when the pinned waiters reach the carrier count, and the default count is the machine's CPU count, several times production's on a developer machine or a CI runner.
  REJECTED: the default carrier count in tests — the storm passed on JDK 21 with as many carriers as the machine has CPUs.
  INVARIANT: the test task and the mutation-testing workers pass `-Djdk.virtualThreadScheduler.parallelism=2` — unenforced; each sets it in its own JVM arguments.

## Takeaway
- takeaway: an audit of the drivers' locks does not prove that no blocking path pins, because a monitor can wrap blocking work one layer up, in a cache or a map's compute call. A JDK that unmounts inside monitors removes the whole class, and a test on the production carrier count shows what the audit missed.
