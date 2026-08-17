# Multi-region request paths

How a request actually travels through this system when the database primary lives in one region
and the pod serving the request lives in another. Written at code level: which endpoint enters
which function, what that triggers, and what breaks if it is removed.

Package prefix `org.danteplanner.backend` is omitted from paths below.

---

## 1. What lives where

```
                 Oregon (us-west-2)                 Seoul (ap-northeast-2)
                 ------------------                 ----------------------
  MySQL          RDS PRIMARY  ...................>  read replica
                 (all writes, both regions)         (async GTID replication)

  Redis auth     PRIMARY (durable)  .............>  regional replica
                 rotation families,                 read-local for blacklist
                 blacklist, tombstones,             checks; SSE subscribe
                 ShedLock, SSE publish

  Redis          local ephemeral                    local ephemeral
  rate-limit     (never cross-region)                (never cross-region)

  Pods           k3s DaemonSet                      k3s DaemonSet
```

Two consequences follow from this picture, and most of the rest of the document is their
elaboration:

- **Every write crosses the WAN when served from Seoul.** There is no write forwarding. A Seoul pod
  opens a connection to the Oregon primary and pays the round trip.
- **Reads are served locally where possible**, which means a Seoul read can be *stale* relative to
  a write the same user just made.

Configuration for the split lives in `shared/config/RoutingDataSourceConfig` (pools),
`shared/config/RedisConnectionConfig` (the four Redis roles), and the per-region ConfigMaps under
`deploy/overlays/{oregon,seoul}/configmap-patch.yaml`. Seoul turns on
`datasource.routing.enabled` and `datasource.replica.enabled`; Oregon leaves both off and every
`RoutingKey` resolves to the primary.

---

## 2. The routing decision

One class decides which physical pool a query reaches:
`shared/config/ReadOnlyRoutingDataSource.determineCurrentLookupKey()`.

```java
RoutingKey override = OVERRIDE.get();          // a ThreadLocal pin
if (override != null) return override;
return TransactionSynchronizationManager.isCurrentTransactionReadOnly()
        ? RoutingKey.REPLICA
        : RoutingKey.PRIMARY;
```

That is the whole rule, and it has one large implication:

> **`@Transactional(readOnly = true)` is a routing instruction, not an optimization hint.**
> Adding it to a service method moves that method's reads to another continent's replica.
> Removing it moves them back to the primary and adds WAN latency to a read.

Three keys exist (`shared/config/RoutingKey`): `PRIMARY`, `REPLICA`, `BULKHEAD`. The bulkhead is a
small separate pool to the primary, used only by the replica-miss re-check (§5) so that slow
re-checks cannot starve ordinary writes. Pool sizes are in `shared/config/PoolLedger`.

The datasource bean is a `LazyConnectionDataSourceProxy` wrapping the router, so the routing
decision is deferred until the first statement — a transaction that opens and never queries
borrows no physical connection.

---

## 3. Filter chain: what wraps every `/api/**` request

```
 servlet container
   └─ CsrfDoubleSubmitFilter        shared/security/    unsafe methods need header==cookie
        └─ JwtAuthenticationFilter  shared/security/    reads access token from COOKIE,
        │                                               consults TokenBlacklistService (auth Redis)
        └─ MdcLoggingFilter
             └─ (Spring Security authorization)
                  └─ GtidCookieFilter   shared/gtid/    registered separately, LOWEST_PRECEDENCE
                       └─ DispatcherServlet → controller
```

`GtidCookieFilter` is registered in `shared/gtid/GtidGateConfig` as a `FilterRegistrationBean` on
`/api/*` with `LOWEST_PRECEDENCE`, so it is the **innermost** filter — it wraps the controller and
therefore wraps the transaction. It is registered only when `datasource.routing.enabled=true`,
i.e. only in Seoul. Oregon reads its own primary and needs no gate.

---

## 4. Read-your-writes: the GTID cookie

The problem: a user publishes a planner (write → Oregon primary), then immediately lists their
planners (read → Seoul replica). Replication has not caught up. The planner appears to have
vanished.

The mechanism, in the order it executes:

### 4a. Capture, on the way out

| Step | Code | What happens |
|---|---|---|
| 1 | `GtidCookieFilter.doFilterInternal` | calls `writeCapture.begin()` — opens a capture window on this thread |
| 2 | `GtidCapturingDataSource` | wraps the PRIMARY pool; every connection it lends is a proxy that intercepts `commit()` |
| 3 | `GtidCapturingDataSource.captureCommittedGtid` | on that same physical connection, reads the OWN_GTID the server attributed to the transaction; falls back to `SELECT @@gtid_executed` when the tracker names none |
| 4 | `GtidWriteCapture.recordCommit(gtid, fromTracker)` | accumulates into the request-scoped `Accumulator` and counts `gtid.capture{source}` |
| 5 | `GtidCookieFilter` (after the chain) | `pollCapturedGtid()` → `Set-Cookie: ryw_gtid=<base64url>` |
| 6 | `GtidCookieFilter` finally | `writeCapture.clear()` |

Why the datasource and not a transaction-manager synchronization: the GTID lives as session state on
the **physical connection that committed**, and an `afterCommit` callback can only look that
connection up again. Under `JpaTransactionManager` the lookup answers wrongly — Hibernate commits on
a connection taken from the `EntityManager`, while the `ConnectionHolder` bound for the
`LazyConnectionDataSourceProxy` is never materialised, so unwrapping it borrows a *fresh* pooled
connection carrying no session state. Intercepting `commit()` holds the right connection by
construction rather than by inference. It also covers every transaction the request commits,
including the `REQUIRES_NEW` transaction an `AFTER_COMMIT` listener opens: a publish commits twice —
the main transaction and the filter-index rebuild — and the cookie must cover both or a follow-up
read gates past only the first.

Why a window rather than a bare ThreadLocal: scheduled tasks and Flyway commit transactions too, on
threads that never enter the filter. `isWindowOpen()` gates accumulation, so those commits cost
nothing and leave no state behind.

**Two switches must both be on** for OWN_GTID to work:

- server: `session_track_gtids=OWN_GTID` (`aws_db_parameter_group` in `terraform/rds/main.tf`).
  It seeds a SESSION variable at connect time, so pooled connections keep the old value until they
  rotate — a parameter change is not visible until pods restart or `maxLifetime` cycles.
- driver: `trackSessionState=true` on the **application** JDBC url — see
  `application-prod.properties`. Not on Flyway's url.

**And the capture must be the first call on the connection after `commit()` returns.** Connector/J
replaces the stored session-state changes on *every* OK packet with no emptiness guard, so any
intervening round trip destroys the GTID — including `Connection.isReadOnly()`, which queries
`@@session.transaction_read_only` unless `useLocalSessionState` is on. This is why `readOwnGtid`
runs before the read-only check rather than after it.

If any of the three is wrong, every commit takes the fallback below: correct, because the primary's
executed set is a superset, but maximally wide, so the read gate almost never finds the replica
caught up. It degrades silently — the `gtid.capture{source=tracker|fallback}` counter is what makes
it visible.

### 4b. The fallback, and what it costs

When the tracker names no GTID for a commit, `GtidCapturingDataSource` reads
`SELECT @@gtid_executed` on that same connection — the entire primary's GTID set, every transaction
from every client. That is *correct* (it is a superset of the user's write) but *wide*: the read
gate below will almost never consider the replica caught up, so the next read pins to the primary
and pays the WAN round trip.

"The transaction wrote nothing" and "the tracker is off or was clobbered" both present as a
tracker that names no GTID. The capture tells them apart with one `SELECT @@session_track_gtids`
probe per physical connection, run strictly AFTER the tracker read (the probe's own OK packet
would wipe the state it judges): tracker verified active means the commit wrote nothing and no
cookie is minted — a write-shaped request that commits an empty transaction must not pin its
follow-up read — while a tracker that is genuinely off takes the superset, since a wide gate
costs latency but a missing one costs correctness. `gtid.capture{source=fallback}` is the metric
that says which world you are in; a ratio near 1 means the precise path is not working.

### 4c. The gate, on the way in

`GtidCookieFilter.handleRead` runs for GET/HEAD carrying `ryw_gtid`:

```
decode cookie → GtidReadGate.isCaughtUp(gtid)
    │              runs SELECT WAIT_FOR_EXECUTED_GTID_SET(?, 0.05) in a READ-ONLY tx,
    │              so the probe itself resolves to the REPLICA
    ├─ true  → clear the cookie (Max-Age=0), serve normally from the replica
    └─ false → ReadOnlyRoutingDataSource.pinTo(PRIMARY) for this request only,
               keep the cookie, clear the pin in finally
```

The 50ms bound is a probe timeout, not a correctness window: a `false` answer routes to the
primary, which is always correct, just slower.

---

## 5. The other read-consistency path: replica-miss re-check

The GTID cookie only helps a client that just wrote. A different case: a by-id read for a row that
exists on the primary but has not replicated, from a client with no cookie.

```
PublishedPlannerController / PlannerQueryController
   └─ ByIdReadGuard.read(entityType, id, dereference)
        └─ dereference on the REPLICA → empty?
             └─ PrimaryReCheck.readWithReCheck
                  ├─ ContentTombstoneStore.isTombstoned(entityType, id)?   (auth Redis)
                  │     └─ yes → genuinely deleted, return empty. No re-check.
                  └─ no  → pinTo(BULKHEAD), re-run the same dereference against the primary
```

Tombstones are written by `PlannerCommandService` on delete. Their purpose is to stop a *deleted*
row from generating a primary re-check on every subsequent request — without them, a popular
deleted planner would send every miss across the WAN. The bulkhead pool exists so a flood of such
re-checks queues in its own pool instead of exhausting the pool writes need.

---

## 6. Write path: publish, end to end

`POST /api/planner/md/{id}/publish` → `PlannerPublishingController.publishPlanner`
`POST /api/planner/md/{id}/unpublish` → `PlannerPublishingController.unpublishPlanner`

`PUT /api/planner/md/{id}/publish` → `PlannerPublishingController.setPublished` is a deprecated
delegate, kept for one release so tabs holding the previous bundle keep working. It resolves the
body's `published` flag to one of the two handlers above and is charged against the same
`CRUD:publish` bucket, so a client crossing over between them draws from one allowance.

```
1. RateLimitInterceptor charges CRUD:publish              rate-limit Redis (LOCAL, never cross-region)
2. body carries a document?
   ├─ yes → PlannerPublishingService.publish(userId, id, content)
   │         └─ plannerCommandService.upsertAggregate(...) ONE aggregate load, reused below
   │                                                        → PRIMARY (write tx), cross-region
   └─ no  → PlannerPublishingService.publish(userId, id)
             └─ accessGuard.requireExisting(id)            → PRIMARY (write tx), cross-region
3. applyPublish(userId, planner)
     ├─ title and content validated                        ← before any mutation; a refusal writes nothing
     ├─ planner.publish() reports NONE → return, NO write   ← idempotent; mints no cookie
     ├─ plannerRepository.save
     ├─ plannerCatalogService.onBecameVisible
     ├─ subscriptionService.createSubscription
     └─ first publish? publishEvent(PlannerPublishedEvent)
4. transaction commits  → GtidCapturingDataSource intercepts commit() → capture (§4a)
5. AFTER_COMMIT: PlannerPublishingService.onPlannerPublished
     ├─ ssePublisher.publishBroadcast(...)                Redis, cross-region (§7)
     └─ notificationService.notifyPlannerPublished(...)   REQUIRES_NEW, READ COMMITTED (§8)
6. GtidCookieFilter mints ryw_gtid covering every commit in steps 3–5
```

Unpublish runs the same shape through `applyUnpublish`, charged against `CRUD:unpublish`: no
validation, and `plannerCatalogService.onBecameInvisible` in place of the visibility and
subscription calls. It publishes no event, so step 5 does not run.

Three design points that are load-bearing:

- **The aggregate is loaded once.** `upsertAggregate` returns the persisted entity so `applyPublish`
  reuses it. Reading it back by id would be three cross-region round trips for one publish.
- **Publishability is decided before the aggregate moves.** A planner that fails validation is
  refused without a write, so a rejected publish mints no `ryw_gtid` cookie. The rollback is not
  what keeps the stored state intact.
- **The fan-out runs after commit, not inside.** An `INSERT ... SELECT` nested inside the publish
  transaction takes locks on rows that transaction already holds and self-deadlocks. Running it
  from the `AFTER_COMMIT` listener also means notifications never persist for a publish that rolled
  back.

---

## 7. SSE: why every dispatch goes through Redis

An SSE emitter lives in the memory of exactly one pod. `shared/sse/SseService.sendToUser` /
`broadcastToAll` / `notifyAccountSuspended` / `invalidateSettingsCache` write to *that pod's*
emitters only. A user connected to a different pod — or a different region — sees nothing.

So call sites never dispatch. They publish:

```
call site                          publishes to           channel
---------                          ------------           -------
PlannerPublishingService           SsePublisher           sse:broadcast
  .onPlannerPublished              .publishBroadcast
ModerationService.banUser          .publishAccountSuspended   sse:user
UserController.updateSettings      .publishSettingsInvalidation sse:user
NotificationService.pushNotification .publishUserEvent      sse:user
PlannerCommentService              .publishCommentEvent     sse:comment
```

Publish goes to the **auth Redis primary** (Oregon, cross-region from Seoul).
`shared/sse/SseSubscriberConfig` subscribes each pod to its **local** Redis (`redis.sse-local.*`,
a regional replica). `SseRedisSubscriber.onMessage` then performs the local dispatch on every pod.

Two rules keep this from breaking:

1. **Publishing never happens inside a dispatch method.** If `broadcastToAll` published, the
   subscriber that received an event would re-publish it, and pods would ping-pong forever.
   `architecture/SseDispatchBoundaryTest` fails the build if a dispatch method is called from
   outside `shared/sse`.
2. **The delivered shape depends on the event type.** `shared/entity/SseEventType` carries
   `deliversRawPayload()`. Sync events (`created`/`updated`/`deleted`/`comment:added`) hand the
   client the whole envelope, because the client reads its routing fields. Notification-style
   events (`notify:*`, `account_suspended`) hand over the payload alone, because their client
   schemas require the payload's fields at the top level. `SseRedisSubscriber.clientPayload()`
   applies this. Getting it wrong does not fail any backend test — it fails client-side validation.

`excludeUserId` rides the envelope so the *dispatching* pod can exclude the author, since that pod
is not the one that published. It must not reach clients; delivering the raw payload for broadcast
events is what keeps it server-side.

---

## 8. Notification fan-out

`NotificationService.notifyPlannerPublished` is one statement:

```sql
INSERT IGNORE INTO notifications (user_id, content_id, notification_type, public_id, planner_id, planner_title)
SELECT s.user_id, :plannerId, 'PLANNER_PUBLISHED', UUID_TO_BIN(UUID()), UUID_TO_BIN(:plannerId), :plannerTitle
FROM user_settings s JOIN users u ON u.id = s.user_id
WHERE s.notify_new_publications = true AND u.deleted_at IS NULL AND s.user_id <> :authorId
```

The recipient filter runs in the database, so the request pays one round trip regardless of
subscriber count. `INSERT IGNORE` against the `uk_notification_dedup` unique key
`(user_id, content_id, notification_type)` makes a re-publish idempotent without a per-recipient
probe. Annotated `REQUIRES_NEW` + `READ_COMMITTED` and invoked from the `AFTER_COMMIT` listener —
see §6 for why all three matter.

---

## 9. Scheduled work

`planner/config/ViewFlushSchedulerConfig` declares **two** schedulers:

- `taskScheduler` (`@Primary`, pool from `spring.task.scheduling.pool.size`, default 4) — every
  `@Scheduled` method that does not name a scheduler, including SSE heartbeats.
- `viewFlushScheduler` (pool 1) — only `PlannerViewRecorder.flush`, which runs every 500ms and
  writes cross-region on every tick.

Both are declared explicitly because Spring Boot's `TaskSchedulingAutoConfiguration` is
`@ConditionalOnMissingBean(TaskScheduler.class)`: declaring *either* scheduler suppresses the
framework's, leaving `spring.task.scheduling.pool.size` unread and binding every scheduled task to
whichever single scheduler exists. The isolation matters because a task blocked on a hung
cross-region write must not stop heartbeats.

Jobs that must run once per *fleet* rather than once per pod take a `@SchedulerLock` over the
shared auth Redis: `UserCleanupScheduler.cleanupExpiredUsers`,
`NotificationService.cleanupOldNotifications`.

---

## 10. Degradation ladder

What happens when the far region is unreachable, and which knob governs it:

| Failure | Bound | Where set | Client sees |
|---|---|---|---|
| Primary blackholed (connect hangs) | `connectTimeout` / `socketTimeout` 10s | app + replica JDBC urls (`application-prod.properties`) | write → 503 `WRITE_TEMPORARILY_UNAVAILABLE`; reads keep serving from the replica |
| Request pool exhausted | Hikari `connectionTimeout` 5s | `RoutingDataSourceConfig` (primary, replica) | 503 rather than a pinned thread |
| Bulkhead saturated by re-checks | Hikari `connectionTimeout` 30s | `RoutingDataSourceConfig` (bulkhead) | re-checks queue; writes unaffected |
| Auth Redis unreachable | Lettuce command timeout 3s | `RedisConnectionConfig.authRedisConnectionFactory` | blacklist check fails **open**; `blacklist_check_skipped_total` increments |
| Rate-limit Redis unreachable | — | — | 503 `RATE_LIMIT_TEMPORARILY_UNAVAILABLE` |
| Flyway migration | deliberately **unbounded** | `spring.flyway.url` has no timeouts | a long migration is not aborted mid-change |

Typed codes live in `shared/exception/DegradationErrorConstants`; mapping is in
`shared/exception/GlobalExceptionHandler`. These are deliberately not reported to Sentry — they are
expected states, not defects.

---

## 11. Endpoint map

| Base path | Controller | Region-relevant behavior |
|---|---|---|
| `/api/planner/md` | `PlannerCommandController` (`PUT /{id}` upsert) | write → primary; mints `ryw_gtid` |
| | `PlannerPublishingController` (`POST /{id}/publish`, `POST /{id}/unpublish`; `PUT /{id}/publish` a deprecated one-release delegate) | §6; publish publishes SSE + notifications after commit |
| | `PlannerEngagementController` (`POST /{id}/bookmark`, vote, subscribe) | writes → primary; bookmark is state-targeted and idempotent |
| | `PlannerQueryController`, `PublishedPlannerController` | reads → replica; by-id reads go through `ByIdReadGuard` (§5) |
| | `PlannerSseController` (`GET /events`) | subscribes to local Redis fan-out |
| `/api/sse` | `SseController` (`GET /subscribe`) | as above |
| `/api/auth` | `AuthController` (`GET /google/callback`) | a **mutating GET** — commits a user row, so it mints `ryw_gtid` (the capture is transaction-driven, not method-driven) |
| `/api/user` | `UserController` (settings) | write → primary; publishes settings invalidation so other pods drop cached settings |
| `/api/notifications` | `NotificationController` | inbox reads → replica |
| `/api/moderation`, `/api/admin` | `ModerationController`, `AdminController` | writes → primary; suspension publishes SSE |
| `/api` | `CommentController` | writes → primary; comment events fan out on `sse:comment` |

---

## 12. If you change one of these, check the others

- Adding `@Transactional(readOnly = true)` to a service method **moves its reads to the replica**.
  If any caller depends on reading its own recent write, it needs the GTID cookie, `ByIdReadGuard`,
  or the primary.
- Adding a new SSE dispatch call site requires publishing, not dispatching — the boundary test will
  fail the build otherwise — and requires deciding `deliversRawPayload` for the event type.
- Adding a `@Scheduled` method puts it on the shared scheduler. If it can block on a cross-region
  call for a long time, consider whether it needs its own, and remember that declaring a scheduler
  bean has fleet-wide consequences (§9).
- Adding a JDBC url parameter must be applied to the application and replica urls but not Flyway's.
