# 168 a-per-pod-probe-decides-the-primary-read-fallback
epic: none · pr: none · supersedes: 130 @reads @outage @trigger

## Decisions
- @reads @outage @trigger — A probe in each Oregon pod opens a fresh connection to the primary
  every second with a one-second bound, outside the Hikari pool. Two consecutive failures open the
  primary-read breaker, and one success closes it from any other state. User reads never act as
  half-open probes, because the breaker waits an hour in the open state. The breaker still records
  connection failures from user reads, so heavy traffic can trip it before the probe does. In
  Oregon the primary is also the same-region read database.
  REJECTED: user reads as half-open probes (ADR 130) — a failed probe is a failed user read. A
  primary that flaps right after it returns fails the reads that were sent to test it.
  REJECTED: one probe for the cluster behind ShedLock — each pod needs its own view of its network
  path to the primary. A lock holder that dies also leaves the other pods blind.
  REJECTED: the Hikari pool for the probe connection — a pool acquire waits the pool's 5-second
  timeout. An idle pooled connection also proves nothing about a new connect.
  REJECTED: the probe's own failure counter in place of the breaker — it drops the trip from user
  traffic and the breaker state metric that the alert reads.
  INVARIANT: the probe runs on its own single-thread scheduler. A long job on the shared pool of
  four threads cannot delay detection — PrimaryHealthProbeTest and OregonReadFallbackIT.
  NON-GOAL: a shared breaker state across pods — each pod opens and closes on its own evidence.
- @reads @outage @acquire — When a read cannot get a connection from the primary, the breaker
  records the failure and the same request reads from the Seoul replica. Oregon has no replica of
  its own, so the Seoul replica is the only other source for that read. Errors other than
  connection failures still propagate. If the replica also fails, the replica's error propagates
  with the primary's error attached as suppressed.
  REJECTED: rethrowing the connection failure — the reader gets 503 while the replica could serve
  the read.
  REJECTED: retry the primary once before falling back — it doubles the wait on a dead primary,
  which already costs the pool's connection timeout.
  INVARIANT: a read that fails to acquire a primary connection is served from the replica —
  PrimaryReadFallbackDataSourceTest and the OregonReadFallbackIT regression test.
  NON-GOAL: fallback for a failure after the connection is lent — a statement that fails mid-query
  cannot move to the replica, because the transaction already holds the primary connection.
- @reads @outage @alert — An alert fires when the primary-read breaker stays open on any pod for
  five minutes. ADR 092 puts a primary restore at minutes, so a longer open state means the probe or
  the primary needs a person.
  REJECTED: alert on each open transition — a short outage that the fallback absorbs pages nobody
  usefully.

## Takeaway
- takeaway: a health check that rides on user requests charges its failures to users; probe on a
  path of your own and let requests only read the verdict.
