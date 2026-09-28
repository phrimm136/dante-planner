# 130 oregon-reads-fall-back-to-the-seoul-replica
epic: none · pr: none

## Decisions
- @reads @outage — While the primary is unreachable, Oregon serves read-only transactions from the Seoul replica over the peering link; while it is reachable, Oregon reads the primary as before. Oregon has no replica of its own, so a primary outage failed every Oregon-routed read with 503 while Seoul's readers kept working, and ADR 010 requires reads to survive write-path outages.
  REJECTED: a replica in Oregon — a second database instance billed every month to cover an outage that ADR 092 puts at minutes of restore a few times a year.
  REJECTED: database-aware readiness that steers Oregon's traffic to Seoul at the load balancer — ADR 010 keeps readiness to application health, and the fallback reaches the same replica without overturning it.
- @reads @outage @cost — During the fallback every read statement from Oregon crosses the ocean (about 95 ms each), so Oregon-routed pages load slower but load; writes fail with ADR 010's typed errors as before, so read-your-writes has nothing to guard while the fallback is active.
- @reads @outage @trigger — A circuit breaker on Oregon's primary pool (resilience4j) decides the fallback: while it is open, read-only transactions route to the Seoul replica, and its half-open probes return them to the primary. The Redis roles lack the same protection, so the breaker library is adopted for the codebase rather than hand-rolled for one call site.
  REJECTED: a hand-rolled consecutive-failure counter — reimplements the breaker's open, half-open and closed states, and a second copy would be needed for the Redis roles.
- @reads @gtid — Oregon captures GTIDs and sets the read-your-writes cookie as Seoul does, but a region without a replica never runs the read gate, so the cookie is cleared only by a replica that has applied the write, or once it is proven that none ever can: its value is not GTID-set syntax, or, after the replica wait has timed out, the primary reports it never executed the named transactions (`GTID_SUBSET(token, @@GLOBAL.gtid_executed) = 0`). Forwarded writes (ADR 123) commit in Oregon, and a Korean reader's GET can still reach Oregon when geo steering falls through on an unmapped colo region.
  REJECTED: letting the primary answer the gate — it always reports caught up and clears the cookie before Seoul's replica has the write.
  REJECTED: a Max-Age or pin window on the cookie — a calibrated delay (ADR 008 @correctness @timing), and it would clear a merely lagging token too.
  REJECTED: clearing when the token's server uuid is absent from the replica's gtid_executed — a replica that has not yet received a new primary's first transaction lacks that uuid too, so absence is not proof.
- @reads @gtid @errors — A deterministic database error on the replica wait (a malformed GTID set) clears the cookie and is logged once; only transient failures keep the pin and the warning.
  REJECTED: treating every data-access error as transient — logged a stack trace on every request for the life of the cookie.

## Takeaway
- takeaway: a rare outage is cheapest to survive by borrowing the copy another region already keeps, paying latency only while the outage lasts rather than an instance every month.
