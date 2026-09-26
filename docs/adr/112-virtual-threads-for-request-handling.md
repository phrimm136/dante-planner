# 112 virtual-threads-for-request-handling

## Decisions
- @threads @tomcat — Request handling runs on virtual threads via `spring.threads.virtual.enabled=true`;
  Tomcat serves each request on its own virtual thread and Boot's default application task executor
  spawns one per task. A request spends most of its life blocked on MySQL through the cross-region
  routing datasource or on a slow client's receive window, and on a pod with a 250m CPU request the
  default platform pool is sized for a machine with far more headroom, each blocked platform thread
  costing a stack.
  REJECTED: tuning `server.tomcat.threads.max` per profile — the right cap differs between the
  primary-only pod and the routing pod, and every fixed cap is wrong on the side that saturates first.
- @threads @pinning — The switch is safe on JDK 21 because every blocking path unmounts: Connector/J
  9.x replaced its I/O monitors with `ReentrantLock` (9.0.0), Lettuce runs on Netty and never blocks a
  request thread, and the only remaining `synchronized` on a blocking path is the one-time lazy Redis
  connect in the rate-limit proxy.
  REJECTED: waiting for the JDK release that lifts monitor pinning — nothing in this codebase waits
  on it.
- @threads @schedulers — The hand-built pools (shared scheduler, view-flush scheduler, SSE heartbeat
  worker, outbox dispatch executor) stay platform-thread pools; the flag does not reach them. Those
  pools exist to bound concurrency, not to provide it: the view-flush scheduler's single thread and
  the heartbeat worker's fixed count are the isolation.
  REJECTED: switching them to virtual-thread schedulers too — drops the bound and lets a stalled
  cross-region write fan out instead of queueing.
- @threads @backpressure — Backpressure moves from Tomcat's thread cap to Hikari's
  `connection-timeout`. A burst that used to queue at the acceptor now queues at the pool and
  surfaces as a pool-timeout after five seconds; `hikari_pool_pending` is the saturation signal.

## Takeaway
- takeaway: A blocking-I/O service gains from virtual threads only when every driver on the request
  path unmounts; audit the drivers' lock primitives before flipping the flag, and keep the pools
  that exist to limit concurrency on platform threads.
