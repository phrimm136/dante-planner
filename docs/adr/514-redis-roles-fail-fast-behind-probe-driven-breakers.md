# 514 redis-roles-fail-fast-behind-probe-driven-breakers
epic: none · pr: none · supersedes: 175 @rate-limit @redis @degradation

## Decisions
- @redis @degradation @breaker — Each Redis role (auth, auth-local, rate-limit, sse-local) has one circuit breaker that only a one-second PING probe opens and closes, and request-path consumers consult it before touching Redis: while it is open a fail-open read is skipped at once and a fail-closed charge is refused at once. With an unresponsive auth-local replica every read waited the full three-second command bound in turn, so an authenticated request took 6 s (9 s with a tombstone check) instead of 13 ms, and once the shared connection was lost requests queued behind its reconnect lock and 11 of 20 had no answer after 90 s; the rate-limit role cost a flat 3 s per request. Fail-open decided what a failure returns, never how long it takes. Re-check: `rg -q 'boolean isAvailable' backend/src/main/java/org/danteplanner/backend/shared/redis/RedisRoleHealth.java` and the outage test that bounds a request's time while the role is unresponsive.
  REJECTED: shortening the command bound — a bound short enough to hide the wait (tens of milliseconds) fails healthy cross-region commands, and any bound is still paid by every read of every request.
  REJECTED: opening the breaker from request failures — it needs live traffic to detect and to recover, and the read-source breakers already settled on probes with no user request as the trial.
  REJECTED: a service mesh — the dependencies speak Redis and MySQL protocols, where a mesh sees connections, not commands, and cannot apply a per-operation fail-open.
  INVARIANT: no request waits on a Redis role whose breaker is open — enforced by the outage integration test.
  INVARIANT: only the probe changes a role breaker's state — enforced by an architecture test on the transition calls.
  NON-GOAL: auth-Redis writes (rotation, revocation, tombstone writes) — a probe that misjudges would block token refresh; they join after the read paths have run in production.
  NON-GOAL: the requests that arrive between the failure and the breaker opening (two missed probes, each waiting its one-second bound: up to about four seconds) — they still wait the command bound.
- @auth @fail-open @redis — A revocation or tombstone read whose regional replica is unavailable is answered by the auth primary, and fails open only when both are unavailable. Failing open at the first failed source let a revoked token work for its remaining lifetime whenever one replica was down, although the primary still held the answer; reading it costs the region a cross-region round trip per read while the replica is out.
  REJECTED: failing closed on writes while both are unavailable — token refresh is already refused then, so it would add an outage of every user's writes to bound an exposure that ends with the access token's lifetime.
  REJECTED: failing closed — the decision ADR 010 already rejected: a cache outage becomes a total authentication outage.
- @rate-limit @redis — The rate-limit store's connection is made by the probe side, once at startup with the probe's bound and again whenever the role recovers; a request never starts a connect. The lazy shared attempt existed so a pod's first requests would not each run or await a connect; with the breaker closed only after a connection exists, that window is gone, and an unreachable store at startup leaves the breaker open, which the fail-open and fail-closed policies already handle.
  REJECTED: keeping the lazy shared attempt beside the breaker — two mechanisms would decide the same question with different timings (five seconds against one), and their signals would disagree.
- @sse @redis — When the sse-local role's breaker closes and the subscriber is not listening, the subscriber starts again. The listener container retries a lost subscription but not its first one, so a pod that started while Redis was unreachable never received other pods' events.
  REJECTED: holding readiness until the subscription is live — every pod in a region shares that Redis, so an outage during a rollout would leave the region's API unready for the sake of live notifications.
  INVARIANT: the subscription state never changes readiness or liveness — enforced by an integration test.


## Takeaway
- takeaway: a fail-open path needs a time bound as much as a result; decide "how long until we give up" once per dependency and let every caller read that one answer.
