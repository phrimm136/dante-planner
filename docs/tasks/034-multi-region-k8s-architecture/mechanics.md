# Mechanics: Phase-2 Backend + Causal-Consistency Stack

Companion to `requirements.md`. Transcribed from the `k8s-phase2`/`k8s-phase3` design sessions and the 2026-07-05/06 architecture debate. **Binding vs. reference vs. plan-time**: sections marked BINDING transcribe contracts the debate settled — implement them exactly as written. REFERENCE material (traces, derivations) explains the contract but does not constrain beyond it. PLAN-TIME items were deliberately left to implementation — design them at plan/build time *within the stated constraints*; do not treat their absence as license to redesign a BINDING contract.

## 0. Executor boundary — read this first

| Class | Meaning | Where |
|---|---|---|
| **BINDING** | Settled in debate. Deviating requires a new design session, not executor judgment | §1–§8 unless marked otherwise |
| **PLAN-TIME** | Executor designs it, within the constraint given | §9 register |
| **FORBIDDEN** | Explicitly rejected in debate. Never reintroduce, even if it looks simpler | list below |

**FORBIDDEN (each was debated and rejected):**
- Timing constants in any correctness path — no sleeps, no delay windows, no "wait 3s then read". Every "has X happened" is a checkable condition (GTID applied, tombstone present, replication caught up).
- Notify-then-fetch SSE events — an event that triggers a recipient refetch is a read racing replication.
- Storing the RYW causal token server-side (Redis session key) — the token must ride the request (cookie); a server-side store is a second async channel that re-imports the race.
- Re-check on list/search results — the primary re-check applies to entity-by-id dereferences ONLY.
- Distributed locks for rotation (Redisson etc.) — the Lua script's single-threaded execution IS the lock.
- Autonomous promotion of any writer (Redis Sentinel auto-failover, RDS emergency promote) — human runbooks only.
- Dependency checks inside liveness/readiness probes.
- Carrying today's `maximumPoolSize=50` into any multi-pod deployment.

## 1. Redis key layouts (auth Redis, Oregon primary) — BINDING

| Key | Type | Written by | TTL | Replaces |
|---|---|---|---|---|
| `bl:<tokenHash>` | string (`SET … PEX <remaining-expiry>`) | logout, invalidation | token remaining life | `TokenBlacklistService.blacklist` map |
| `uinv:<userId>` | string (invalidation epoch-ms) | logout-everywhere, account delete | refresh-token max life | `userInvalidationTimes` map |
| `rt:fam:{<familyId>}` | hash, one per token family (§2) | rotation Lua only | sliding, refresh expiry (7d), `PEXPIRE` on each rotation | `RefreshRotationService` maps + 256 lock stripes |
| `del:<entityType>:<id>` | string tombstone (`SET … PX 3600000`) | delete endpoints, synchronous pre-response | 1h — cleanup, not the correctness gate | new |
| pub/sub channels | — (§4) | all pods | — | in-process emitter maps |

Rate-limit buckets live in the **per-region local ephemeral Redis** (no AOF, no replication), not here.

## 2. Refresh rotation — BINDING contract, verbatim from k8s-phase3 (also k8s-phase2)

### 2.1 Family hash layout

```
Key:   rt:fam:{<family_id>}          ← Redis hash, one per token family
Fields:
  <jti>        → "<STATE>|<successorJti>|<expiryMs>"
  __revoked__  → "<epochMs>"          (present only if the family was revoked)
TTL on the key: refresh-token expiry (7d), refreshed on each rotation
```

States: `UNUSED_LATEST` (head of chain), `PENDING` (rotation issued, successor not yet used), `USED` (successor confirmed), `SUPERSEDED` (retry replaced this successor). The `{…}` hash-tag pins the key to one cluster slot — the design stays correct if Redis is ever sharded.

### 2.2 The transition Lua script (full text — implement exactly; register via `SCRIPT LOAD`, call via `EVALSHA`)

```lua
-- KEYS[1] = rt:fam:{F}
-- ARGV   = jti, parentJti, successorJti, succExpiryMs, nowMs, ttlMs
local fkey = KEYS[1]
local jti, parent, succ = ARGV[1], ARGV[2], ARGV[3]

if redis.call('HGET', fkey, '__revoked__') then return 'REVOKED' end

local cur = redis.call('HGET', fkey, jti)            -- "STATE|succ|exp" or false
local state = cur and string.match(cur, '^[^|]+') or 'UNUSED_LATEST'

if state == 'USED' or state == 'SUPERSEDED' then     -- replay of a spent token
  redis.call('HSET', fkey, '__revoked__', ARGV[5])   -- THEFT: revoke whole family
  return 'THEFT'
end

if parent ~= '' then                                 -- mark parent USED on successor's first use
  local p = redis.call('HGET', fkey, parent)
  if p and string.match(p,'^[^|]+')=='PENDING' then
    redis.call('HSET', fkey, parent, 'USED||'..ARGV[4])
  end
end

if state == 'PENDING' then                           -- legit retry: supersede the old successor
  local oldSucc = string.match(cur, '|([^|]*)|')
  if oldSucc and oldSucc ~= '' then
    redis.call('HSET', fkey, oldSucc, 'SUPERSEDED||'..ARGV[4])
  end
end

redis.call('HSET', fkey, succ, 'UNUSED_LATEST||'..ARGV[4])
redis.call('HSET', fkey, jti,  'PENDING|'..succ..'|'..ARGV[4])
redis.call('PEXPIRE', fkey, ARGV[6])                 -- sliding TTL = the cleanup job
return 'ROTATED'
```

### 2.3 Split-signing call shape (Java side — BINDING order: mint before transition)

```java
String succJti = UUID.randomUUID().toString();
String succJwt = tokenGenerator.generateRefreshToken(userId, email, familyId, jti, succJti);
String verdict = redis.evalsha(SHA, key("rt:fam:{"+familyId+"}"),
                               jti, parentJti, succJti, succExpiryMs, now, ttlMs);
switch (verdict) {
  case "ROTATED" -> cookieUtils.setCookie(response, REFRESH_TOKEN, succJwt, ttlSec);
  case "THEFT", "REVOKED" -> clearAuthCookies(response);   // succJwt discarded, never sent
}
```

RSA signing cannot run in Lua, so the successor is minted **optimistically before** the theft check; on `THEFT`/`REVOKED` the signed JWT is dropped — it was never written into the family hash and never left the server. Cookie is set **only** on `ROTATED`.

### 2.4 Traced scenarios — REFERENCE

Normal chain: `A UNUSED_LATEST` → rotate(A, succ=B) → `A PENDING|B` + `B UNUSED_LATEST` → rotate(B, parent=A, succ=C) → `A USED` + `B PENDING|C` + `C UNUSED_LATEST`. Each prior link ends `USED`.

Theft: attacker replays stolen `A` after the client advanced to `B` (`A` is `USED`) → theft branch sets `__revoked__ <now>` → `THEFT`, cookies cleared. Every subsequent rotation in family F — attacker or real client — hits the `__revoked__` guard → `REVOKED`. The lineage is dead.

### 2.5 Rotation residue — BINDING notes

- The **5s rotation-grace comparison stays in Java** (single clock source); the Lua `PENDING`→supersede branch tolerates the legit retry, the blacklist grace covers the in-flight duplicate.
- `jwt_rotation_outcome_total{rotated|theft_revoked|retry_superseded}` metrics preserved verbatim — they are the cutover correctness check (rates must match pre/post migration).
- Spent `USED`/`SUPERSEDED` fields accumulate until the family TTLs out (bounded by the 7d window); opportunistic `HDEL` is optional, not required.
- Scheduled jobs: the three TTL-replaced cleanups are **deleted**; `NotificationService` 2AM + `UserCleanupScheduler` 3AM get **ShedLock**; the two SSE heartbeat/zombie sweeps **stay** (they manage live local connections).

## 3. Blacklist & rate limiter — BINDING commands, PLAN-TIME wiring

- Blacklist: `SET bl:<tokenHash> <marker> PEX <remaining-expiry>` on logout/invalidation; `SET uinv:<userId> <epochMs>` on logout-everywhere/account-delete. Read path compares token-issued-at against `uinv:` and membership in `bl:` — the 5s grace compare stays Java-side. The hourly cleanup `@Scheduled` job is **deleted** (TTL replaces it). *(PLAN-TIME: exact value payloads and the read-side compare code — the sessions specified the commands elliptically.)*
- Rate limiter: swap the hand-rolled `ConcurrentHashMap` for `bucket4j-redis` — `proxyManager.builder().build(key, config)` against the **local** ephemeral Redis. Manual 5-min eviction + `maxBuckets` LRU **delete entirely** (key TTL replaces both). *(PLAN-TIME: `LettuceBasedProxyManager` instantiation, connection wiring, `ExpirationAfterWriteStrategy` — never written out in the sessions.)*

## 4. SSE over pub/sub — BINDING decisions, PLAN-TIME naming

- **Redis Pub/Sub, not Streams** — at-most-once matches the app: neither SSE service sets `.id()` (only `.name().data()`), so `Last-Event-ID` replay has nothing to consume; missed events are recovered by the `usePlannerSync` reconciliation backstop. Streams would add replay/ordering machinery nothing consumes.
- Every pod **publishes to the Oregon Redis primary**; every pod's subscriber connects to its **local** Redis (replica in Seoul — `PUBLISH` propagates through replication) and delivers to its local emitters. True fan-out, no sticky sessions.
- **Events carry payloads** (created/updated entity DTO, or the deleted id) — recipients patch caches via `setQueryData`, never refetch. Envelope sketch `{type, entityType, entityId, payload?, deletedId?}` is a starting shape, not a contract. *(PLAN-TIME: exact envelope fields and channel naming. The sessions floated `sse:user:<id>` and a `settings-changed:<userId>` control message as examples only — non-binding.)*
- Settings-cache invalidation rides a pub/sub control message on the same mechanism.
- FE surface: `useAppSse.ts` + `usePlannerCommentsSse.ts` migrate the causal-stack recipient updates from invalidate → `setQueryData` payload patches — the row-patchable caches on the RYW stack: planner list + detail (`handlePlannerUpdate`), published-planner list (`handlePublished`), comment tree (`usePlannerCommentsSse`). The remaining `useAppSse` invalidations stay invalidations **by design, for correctness**: the notification inbox is a *paginated* `NotificationInboxResponse` keyed by `(page,size)` (a single-notification prepend corrupts pagination totals — not row-patchable), and auth `/me` restriction state is authoritative server-side (the `account_suspended` payload is a partial notice, missing the computed restriction/expiry fields). Both are outside the RYW causal stack — `requirements.md` L50/L51 bound the migration to planner+comment. `userPlanners` invalidate is dropped to the `usePlannerSync` reconciliation backstop. Client reconnect jitter 0–5s on top of `retry:`; heartbeat < 100s (Cloudflare idle limit).

## 5. Datasource routing + causal read path — BINDING

- Pools: two per Seoul pod (`primary`, `replica`), one in Oregon (no replica pool there). `AbstractRoutingDataSource` keyed on the transaction read-only flag, wrapped in `LazyConnectionDataSourceProxy` so the route resolves at first use, when `readOnly` is known.
- **GTID gate** (author RYW): on any authenticated write, capture the transaction's GTID via `session_track_gtids=OWN_GTID` (returned in the OK packet — no extra query) → set HttpOnly `Secure; SameSite=Lax` cookie. On a cookie-bearing read-only request: `SELECT WAIT_FOR_EXECUTED_GTID_SET('<gtid>', 0.05)` on the replica — returns 0 (applied) → read replica and clear the cookie; returns 1 (timeout) → route **this request** to primary. The 0.05 is a probe bound, not a correctness window: a longer lag simply keeps routing to primary.
  - **RDS caveat (from 030 execution, 2026-07):** RDS's restricted privilege posture is demonstrated fact — `SET @@GLOBAL.GTID_PURGED` failed with ERROR 1227 during the migration, forcing `--set-gtid-purged=COMMENTED` + `mysql.rds_set_external_source_gtid_purged`. `session_track_gtids` is session-scoped and should be unprivileged, but verify on the live RDS before building the gate (030 runbook §0.9b — unrecorded as of 2026-07-10). **Verified fallback if unsettable/unsurfaced by Connector/J:** `SELECT @@gtid_executed` on the same connection immediately after commit — one extra round trip, same causal token, gate contract otherwise unchanged.
- **Primary re-check on byId miss**: entity-by-id replica lookups that return empty re-query the primary before answering 404; found → serve + increment `replica_miss_promoted_total`. byId dereferences ONLY. Runs on a dedicated bulkhead pool (§6 ledger) so miss-floods cannot starve writes.
- **Tombstone check**: replica-served byId **positives** check `del:<type>:<id>` on the local Redis; present → 404. The tombstone `SET` is issued synchronously in the delete path **before** the HTTP response.
- Read-path order (one line, load-bearing): `replica hit → tombstone → serve | replica miss → primary re-check → serve/404 | cookie-gated author → primary until caught up`.

## 6. Pools, limits, and client behavior — BINDING ledger

| Pool | Size | Basis |
|---|---|---|
| Oregon pod → primary | 15 | ~40 RPS × 5ms (Little's law) + headroom |
| Seoul pod → primary (writes) | 10 | ~5 write-RPS × 0.2s WAN txn |
| Seoul pod → replica (reads) | 15 | ~30 RPS × 5ms |
| Re-check bulkhead → primary | 2–3 | isolation, not throughput |

Constraint (INV9, config-asserted): Σ(max pool sizes across max pods) ≤ `max_connections` − 10 reserve (db.t4g.micro ≈ 85, small ≈ 170). Node/client limits: `LimitNOFILE` 65536 on app nodes; SSE heartbeat < 100s; FE reconnect jitter 0–5s.

## 7. Degradation & errors — BINDING

- Spring health groups: liveness + readiness = **app-only**; DB/Redis indicators excluded from both, visible on full `/actuator/health` for alerting. Traefik health route `/healthz-local` targets local Spring only, fallback service excluded.
- Error codes: `AUTH_TEMPORARILY_UNAVAILABLE` (Redis-dependent auth writes during outage), `WRITE_TEMPORARILY_UNAVAILABLE` (primary DB unreachable). Lettuce connection exceptions mapped in `GlobalExceptionHandler` **and** `JwtAuthenticationFilter` (filter-thrown bypasses `@RestControllerAdvice` — same multi-catch pattern as the DB-outage fix, commit `da57cd78`).
- Blacklist read ladder: local Redis → (Seoul) stale-but-honest replica → all unavailable: **fail-open** + `blacklist_check_skipped_total`. Post-recovery: a pre-outage blacklisted token is still rejected after AOF replay (INV6).
- FE errorCode switch: planner writes → "saved locally, sync paused" (local-first store already holds state); auth → unavailable toast.
- Redis persistence: AOF `everysec` + RDB preamble (hybrid) on the auth Redis; none on the rate-limit Redis.

## 8. Test harness — BINDING shape

- Testcontainers: `mysql-primary` + `mysql-replica` (GTID, `CHANGE REPLICATION SOURCE TO … SOURCE_AUTO_POSITION=1` — same incantation as the RDS migration runbook), `redis`, `toxiproxy`; the app under test wired with both datasources like a Seoul pod.
- `ReplicationControl` utility: `stopReplica()` / `startReplica()` / `awaitCaughtUp()` (polls `SHOW REPLICA STATUS` — a condition, not a sleep). **The API has no duration parameters** (INV4).
- Toxiproxy profiles: `wan` (130ms latency toxic on app→primary), `partition` (timeout toxic = peering loss).
- Suites map to invariants per `requirements.md` Test Plan: `ReplicaLagIT` (INV1–2), `CausalGateIT` (INV3), `DegradationIT` (INV5–6), `BulkheadIT` (INV7), rotation concurrency vs Lua (INV8), pool-ledger config test (INV9).
- ATDD order per project convention: failing acceptance tests against the harness first, then implementation.

## 9. PLAN-TIME register — the executor designs these, within constraints

1. Blacklist value payloads + read-side grace compare (constraint: 5s grace stays Java; TTL replaces cleanup jobs).
2. `LettuceBasedProxyManager` wiring + bucket expiration strategy (constraint: local Redis, TTL-based eviction).
3. SSE envelope fields + channel naming (constraints: payload-carrying, no notify-then-fetch, Pub/Sub not Streams).
4. Gate/re-check/tombstone code placement (interceptor vs repository decorator vs AOP) — the read-path *order* in §5 is binding, the wiring is not.
5. Spring config classes, `@Qualifier` names, Testcontainers bootstrap code.
6. Exact Toxiproxy toxic parameters beyond `wan`=130ms.

## Pre-implementation gates (empirical, with decision criteria)

0. GTID-gate probe on the live production RDS (030 runbook §0.9b): `SET SESSION session_track_gtids=OWN_GTID` + confirm Connector/J surfaces the tracked GTID → record yes/no here **before** writing the gate's red tests; "no" switches the gate to the `@@gtid_executed` fallback (§5).
1. Backend RSS under `load-test-*.js` ≤ ~1.3GiB → confirms t4g.small app nodes; else medium.
2. Write-path round-trip audit (Hibernate batching, no lazy walks in write txns) → every write transaction single-round-trip before Seoul exists.
3. `ReplicaLag` p99 baseline once the Seoul replica runs → documented justification for tombstone TTL and gate probe margins.
