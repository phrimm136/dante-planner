# Phase-2 Backend: Implementation Detail

Companion to `instructions.md`. Implementation-grade mechanics for the stateless refactor + causal-consistency stack, transcribed from the k8s-phase2/phase3 design sessions and the 2026-07-05/06 architecture debate. The full Lua rotation trace (normal chain A→B→C, theft-via-replay) was worked through in session `k8s-phase3`; the contract below is binding, the trace is reference.

## 1. Redis key layouts (auth Redis, Oregon primary)

| Key | Type | Written by | TTL | Replaces |
|---|---|---|---|---|
| `bl:<tokenHash>` | string (`SET … PEX <expiry>`) | logout, invalidation | token remaining life | `TokenBlacklistService.blacklist` map |
| `uinv:<userId>` | string (invalidation timestamp) | logout-everywhere, account delete | refresh-token max life | `userInvalidationTimes` map |
| `rt:fam:{<familyId>}` | hash — one token family per hash; `{}` hash-tag pins cluster slot | rotation Lua only | family max life | `RefreshRotationService` maps + 256 lock stripes |
| `del:<entityType>:<id>` | string tombstone (`SET … PX 3600000`) | delete endpoints, synchronous pre-response | 1h (cleanup, not correctness) | new |
| pub/sub channels (§3) | — | all pods | — | in-process emitter maps |

Rate-limit buckets do NOT live here — they live in the per-region local ephemeral Redis via `bucket4j-redis` `LettuceBasedProxyManager` (manual 5-min eviction + `maxBuckets` LRU deleted; Redis TTL replaces both).

## 2. Refresh rotation contract

- One family = one Redis hash; the multi-key transition (parent + old-successor + new-successor) is a single **Lua script** — Redis single-threaded execution IS the lock; the 256 in-process stripes are deleted, no distributed lock introduced.
- **RSA signing is split out of the critical section**: Java optimistically mints/signs the candidate JWT first, then the Lua script performs the state transition and returns the outcome; the cookie is set only on `ROTATED`, the minted token is discarded on `THEFT`/`REVOKED`/`SUPERSEDED`.
- Theft detection: replay of a spent token marks the family `__revoked__` inside the same script.
- The 5s rotation-grace comparison stays in Java (single clock source), per the original design.
- `jwt_rotation_outcome_total{rotated|theft_revoked|retry_superseded}` metrics are preserved verbatim — they are the cutover correctness check: rates must match pre/post migration.
- Scheduled cleanups: the three TTL-replaced jobs are deleted; `NotificationService` 2AM + `UserCleanupScheduler` 3AM get ShedLock; the two SSE heartbeat/zombie sweeps stay (they manage live local connections).

## 3. SSE over pub/sub — payload-carrying events

- Every pod publishes to the Oregon Redis primary; every pod's subscriber (connected to its local Redis — replica in Seoul; `PUBLISH` propagates through replication) delivers to its local emitters. True fan-out, no sticky sessions.
- **Events carry payloads** (the created/updated entity DTO, or the deleted id) — recipients patch caches via `setQueryData`, never refetch. Event envelope: `{type, entityType, entityId, payload?, deletedId?}` — exact fields designed at implementation, but "notify-then-fetch" event types are forbidden by INV4's spirit: a recipient refetch is a read that races replication.
- Settings-cache invalidation rides a pub/sub control message (from the original design).
- FE migration surface: `useAppSse.ts` (7 invalidations → patches), `usePlannerCommentsSse.ts`. Client reconnect: jitter 0–5s on top of `retry:`; heartbeat < 100s (Cloudflare idle limit).

## 4. Datasource routing + causal read path

- Two pools per Seoul pod (`primary`, `replica`), one in Oregon. `AbstractRoutingDataSource` keyed on the transaction's read-only flag, wrapped in `LazyConnectionDataSourceProxy` so routing resolves at first use, when readOnly is known.
- **GTID gate** (author RYW): on any authenticated write, capture the transaction GTID (`session_track_gtids=OWN_GTID`, from the OK packet — no extra query) → HttpOnly `Secure; SameSite=Lax` cookie. On a cookie-bearing read-only request: `WAIT_FOR_EXECUTED_GTID_SET(<gtid>, 0.05)` on the replica — 0 → replica (clear cookie), 1 (timeout) → route this request to primary. In-band client token by design; never a server-side store (a second async channel re-imports the race).
- **Primary re-check on byId miss**: entity-by-id replica lookups that return empty re-query the primary before 404; found → serve + `replica_miss_promoted_total`. Scope: byId dereferences ONLY, never lists/searches. Runs on a dedicated 2–3-connection pool (bulkhead) so miss-floods cannot starve writes.
- **Tombstone check**: replica-served byId positives check `del:<type>:<id>` on the local Redis; present → 404. Written synchronously in the delete path before the response.
- Read-path order: `replica hit → tombstone → serve | replica miss → primary re-check → serve/404 | gated author → primary until caught up`.
- Pool sizes (ledger, from `max_connections` micro ≈ 85 − 10 reserve): Oregon→primary 15, Seoul→primary 10, Seoul→replica 15, re-check bulkhead 2–3. Assert the ledger in a config test (INV9).

## 5. Degradation & errors

- Spring health groups: liveness + readiness = app-only; DB/Redis indicators excluded from both, exposed on the full `/actuator/health` for alerting. Traefik health route `/healthz-local` targets local-Spring-only (fallback service excluded).
- New error codes: `AUTH_TEMPORARILY_UNAVAILABLE` (Redis-dependent auth writes during outage), `WRITE_TEMPORARILY_UNAVAILABLE` (primary DB unreachable). Lettuce connection exceptions mapped in `GlobalExceptionHandler` **and** `JwtAuthenticationFilter` (filter exceptions bypass `@RestControllerAdvice` — same multi-catch pattern as the DB-outage fix in commit `da57cd78`).
- Blacklist read ladder: local Redis → (Seoul) stale-but-honest replica → all unavailable: **fail-open** + `blacklist_check_skipped_total`. Post-recovery invariant: a pre-outage blacklisted token is still rejected after AOF replay (INV6).
- FE: errorCode switch — planner writes show "saved locally, sync paused" (local-first store already holds the state), auth shows the unavailable toast.

## 6. Test harness

- Testcontainers: `mysql-primary` + `mysql-replica` (GTID, `SOURCE_AUTO_POSITION=1` — same incantation as the RDS migration runbook), `redis`, `toxiproxy`; app wired with both datasources like a Seoul pod.
- `ReplicationControl` utility: `stopReplica()` / `startReplica()` / `awaitCaughtUp()` (polls `SHOW REPLICA STATUS` — condition, not sleep). **No duration parameters in its API** (INV4).
- Toxiproxy profiles: `wan` (130ms latency app→primary), `partition` (timeout toxic = peering loss). Suites: `ReplicaLagIT`, `CausalGateIT`, `DegradationIT`, `BulkheadIT` per instructions.md Test Plan.
- ATDD order per project convention: failing acceptance tests against the harness first, then implementation.

## Pre-implementation gates (empirical, with decision criteria)

1. Backend RSS under `load-test-*.js` ≤ ~1.3GiB → confirms t4g.small app nodes; else medium.
2. Write-path round-trip audit (Hibernate batching, no lazy walks in write txns) → every write transaction single-round-trip before Seoul exists.
3. `ReplicaLag` p99 baseline once the Seoul replica runs → documented justification for tombstone TTL and gate timeout margins.
