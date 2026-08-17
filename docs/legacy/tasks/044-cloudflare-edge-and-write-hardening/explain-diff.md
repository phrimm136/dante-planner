# explain-diff: 044-cloudflare-edge-and-write-hardening

_Range: `task/044-cloudflare-edge-and-write-hardening-base..HEAD` · 71 files · generated cold from the diff._

## Background

### The application

Dante's Planner is a planning tool for the game Limbus Company: users author "planners" (JSON
documents describing a run), keep them synced across their own devices, optionally publish them to a
public browse/search catalog, and comment on and bookmark each other's. The frontend is a React SPA;
the backend is a single Spring Boot service (`org.danteplanner.backend`, package-by-feature —
`admin`, `auth`, `comment`, `moderation`, `notification`, `planner`, `user`, plus `shared`) over
MySQL 8, Redis, and Flyway. Static game data lives in a git submodule at `static/`.

### Deployment topology: two regions, one writable database

The service runs as a **k3s fleet in two AWS regions** — Oregon (`us-west-2`) and Seoul
(`ap-northeast-2`). Each region is a self-contained cluster with its own control plane and its own
ArgoCD, pointed at its own kustomize overlay (`deploy/argocd/root-app-oregon.yaml:21-26`,
`root-app-seoul.yaml`); there is no hub-and-spoke. The backend is a **DaemonSet**, not a Deployment
(`deploy/base/spring-daemonset.yaml:2-5`) — the app auto-scaling group is the only scaling dial, so
node count *is* pod count. Nodes carry roles: `role=app` (backend), `role=data` (Redis, Prometheus),
`role=ingress` (Traefik, `hostNetwork: true`, `deploy/base/traefik-controller.yaml:55-71`).

The database is asymmetric. `terraform/rds/main.tf:149-208` creates a single MySQL 8 primary in
Oregon; `terraform/seoul/data.tf:84-109` creates a **cross-region read replica** in Seoul via
`replicate_source_db`. Both parameter groups pin `gtid-mode=ON` and `enforce_gtid_consistency=ON`
(`terraform/rds/main.tf:78-87`, `terraform/seoul/data.tf:66-75`) with `binlog_format=ROW`. There is
deliberately **no automatic promotion** (`terraform/seoul/data.tf:6-7`). Consequently:

- **Oregon pods** talk only to the primary — no routing, no replica.
- **Seoul pods** read locally off the replica and write cross-region to Oregon's primary. That is
  switched on purely by config: `DATASOURCE_ROUTING_ENABLED: "true"`,
  `DATASOURCE_REPLICA_ENABLED: "true"`, `MYSQL_REPLICA_HOST` are present in
  `deploy/overlays/seoul/configmap-patch.yaml:30-32` and absent from Oregon's.

Deploys are GitOps: `.github/workflows/deploy-fleet.yml` builds one arm64 image, pushes it to both
regional ECRs, surges each region's ASG, `kustomize edit set image` in both overlays, commits to
`main`, and lets ArgoCD roll the DaemonSet; SSM is used only as a remote-exec transport to run
`k3s kubectl` on each control plane.

<pre>
                     client
                       |
        [ AWS Global Accelerator — anycast IPs, TCP:443 ]
                 /                        \
       Oregon ingress EC2            Seoul ingress EC2
       (Traefik, hostNetwork)        (Traefik, hostNetwork)
              |                              |
       backend DaemonSet             backend DaemonSet
              |                          /        \
              |                    reads /          \ writes
              v                         v            v
     RDS primary (us-west-2)  <---  replica    ...cross-region...
                              GTID     (ap-northeast-2)
                           replication
</pre>

### Redis: three logical roles

`RedisConnectionConfig` (`backend/src/main/java/org/danteplanner/backend/shared/config/RedisConnectionConfig.java:36-64`)
wires four named `LettuceConnectionFactory` beans over three logical roles:

- **`auth`** — the durable Oregon store: refresh-token rotation families, the JWT blacklist, delete
  tombstones, ShedLock locks, and **SSE pub/sub publishing**. Marked `@Primary`, so the `@Primary`
  `StringRedisTemplate` is the auth *write* template. Seoul reaches it **cross-region** over VPC
  peering via a NodePort (`deploy/overlays/seoul/configmap-patch.yaml:12-13`).
- **`rate-limit`** — per-region, ephemeral, `emptyDir`, never replicated (`deploy/base/redis-ratelimit.yaml`).
- **`sse-local`** / **`auth-local`** — the region-local Redis a pod *subscribes* to and reads from.
  In Seoul this is a read-only Redis replica of Oregon's auth store
  (`deploy/overlays/seoul/redis-replicaof-patch.yaml:25-62`); in Oregon it resolves to the same host
  as `auth`.

So the SSE fan-out shape is **publish global, subscribe local**: a pod publishes to the Oregon auth
Redis, replication carries the message to Seoul's local replica, and every pod's subscriber sees it.

### Read-your-writes: GTIDs, replica lag, and the cookie

A **GTID** (Global Transaction Identifier) is MySQL's per-transaction name, `<source-uuid>:<n>`, and
a *GTID set* is a compacted range list like `uuid:1-40,uuid2:5`. Because Seoul's replica applies the
primary's binlog asynchronously, there is **replica lag**: a user in Seoul who writes to Oregon and
immediately re-reads locally can see the pre-write state. **Read-your-writes** is the guarantee that
this cannot happen; the mechanism here is a cookie.

Three pieces, all under `shared/gtid/` and all conditional on `datasource.routing.enabled=true`
(`GtidGateConfig.java:23-24`) — i.e. **Seoul pods only**:

- `GtidWriteCapture` records the GTID a request's write committed. In the base state it did one
  thing on the way out of a write: `SELECT @@gtid_executed` on the primary, a *conservative superset*
  of everything committed anywhere, then handed that back.
- `GtidCookie` (`GtidCookie.java:19-27`) Base64url-encodes the set into the `ryw_gtid` cookie —
  encoded because a multi-source GTID set contains commas and RFC 6265 forbids commas in cookie
  values.
- `GtidReadGate` (`GtidReadGate.java:41-51`) runs `WAIT_FOR_EXECUTED_GTID_SET(<set>, 0.05)` inside a
  read-only transaction, so the probe lands on the replica. It is a **bounded probe, not a sleep**:
  50 ms, then give up.

`GtidCookieFilter` (registered at `LOWEST_PRECEDENCE` over `/api/*`, `GtidGateConfig.java:44-52`)
joins them. On a GET/HEAD carrying the cookie: if the gate says the replica has caught up, clear the
cookie and proceed normally; otherwise **pin this one request to the primary** for the duration of
the chain (`GtidCookieFilter.java:64-74`). On an unsafe method, mint the cookie from whatever the
capture holds.

### The routing datasource and the pools

`ReadOnlyRoutingDataSource` (`shared/config/ReadOnlyRoutingDataSource.java:30-39`) is an
`AbstractRoutingDataSource` whose lookup key is `REPLICA` when
`TransactionSynchronizationManager.isCurrentTransactionReadOnly()` and `PRIMARY` otherwise, wrapped
in a `LazyConnectionDataSourceProxy` so the read-only flag is already set when the physical
connection is acquired. A **static `ThreadLocal` override** (`pinTo`/`clear`, lines 20-28) beats the
read-only rule — that is the hook the GTID filter and the re-check use, since the routing datasource
is otherwise unreachable beneath the lazy proxy.

`RoutingDataSourceConfig` builds three HikariCP pools sized from `PoolLedger`, the single source of
truth shared with the INV9 config assertion: `OREGON_PRIMARY_POOL = 15`, `SEOUL_PRIMARY_POOL = 10`,
`SEOUL_REPLICA_POOL = 15`, `BULKHEAD_POOL = 3` (`PoolLedger.java:11-16`). The sizes exist to keep
total fleet connections inside a `db.t4g.micro`'s 85-connection budget minus a 10-connection reserve.

The **bulkhead** is a third, deliberately tiny pool against the primary whose purpose is isolation
rather than throughput. `shared/readpath/PrimaryReCheck.java:51-69` is what uses it: a read-only
by-id dereference that misses on the stale replica pins the thread to `BULKHEAD` and re-runs the same
dereference, which now reaches the primary; a hit increments `replica_miss_promoted_total`, a miss
re-throws the 404. A replica *hit* is separately checked against `ContentTombstoneStore` — a
short-TTL Redis marker written on delete — so a just-deleted row still present on the replica is
masked as absent (`PrimaryReCheck.java:21-25`). `ByIdReadGuard` is the seam call sites go through; it
is a pure pass-through wherever `PrimaryReCheck` is absent, i.e. Oregon.

### SSE fan-out across pods

Cross-pod delivery is a problem because emitters are process-local. `AbstractSseService<K>`
(`shared/sse/AbstractSseService.java:30`) holds a `ConcurrentHashMap<K, CopyOnWriteArrayList<EmitterEntry>>`
— **this pod's** open `SseEmitter`s, keyed by user id (`SseService`) or planner id
(`PlannerCommentSseService`), with per-device de-dup and a 1-hour timeout. A user's browser is
connected to exactly one pod in one region. So calling `SseService.sendToUser(...)` directly reaches
only the clients that happen to share the caller's JVM; everyone else silently gets nothing.

The fan-out fixes that: `SsePublisher` serializes an `SseEnvelope` and `convertAndSend`s it to a
Redis channel — `sse:user` or `sse:comment` (`SseChannels.java`) — on the **auth (global) Redis**;
`SseRedisSubscriber`, wired by `SseSubscriberConfig` against the **sse-local** connection factory,
deserializes it on every pod and calls the local dispatch method. Publishing is best-effort by
design: a serialization failure or unreachable Redis is logged and swallowed so the triggering write
survives (`SsePublisher.java:103-127`). `SseService` additionally gates delivery on per-user
notification settings, cached per node (`SseService.isEventAllowed`, lines 269-286), with a
`settings:invalidated` control message riding the same user channel to drop stale entries.

In the base state, several call sites bypassed the publisher and hit the pod-local methods directly:
the first-publish broadcast (`broadcastToAll`), moderation's `notifyAccountSuspended`, and
`UserController`'s `invalidateSettingsCache`. There was no `sse:broadcast` channel and no
`ACCOUNT_SUSPENDED` event type.

### The planner write path

`PlannerCommandService` owns owner CRUD. `upsertPlanner(userId, deviceId, id, req, force)` loads the
aggregate with `plannerRepository.findAggregateForOwner(id, userId)`; on a hit it applies the request
to the content row, checks `syncVersion` for optimistic-lock conflicts, saves, and emits a sync
event. On a miss it must distinguish three cases before creating, and in the base state did so with
**two extra existence probes** — `existsByIdAndUserId` (the owner's own soft-deleted planner →
`PlannerNotFoundException`) then `existsActiveById` (someone else's planner → `PlannerForbiddenException`).

The `Planner` aggregate is decomposed: an immutable core row (`planner/entity/Planner.java:22-32` —
id, owner, type, createdAt) plus three satellites sharing its PK, `PlannerContent`,
`PlannerPublication`, `PlannerModeration`. Every single-row load therefore goes through
`PlannerRepository.AGGREGATE_LOAD` (`PlannerRepository.java:29-31`), a `JOIN FETCH` over all three
satellites and the user, specialized into `findAggregateForOwner`, `findAggregate`, and
`findPublishedAggregate` by their where-clauses.

`PlannerPublishingService.togglePublish` flips `published`, validates content in strict mode when
publishing, saves, updates the catalog projection, auto-subscribes the owner, and — **on first
publish only** — publishes a `PlannerPublishedEvent`. Its `@TransactionalEventListener(AFTER_COMMIT)`
handler emitted the SSE broadcast (`PlannerPublishingService.java:58-63`). Two entry points existed:
`togglePublish` (no body) and `publishWithContent` (body = `UpsertPlannerRequest`), which upserted
then re-loaded the aggregate before toggling. Bookmarks worked the same way — `POST /{id}/bookmark`
with no body, `PlannerEngagementService.toggleBookmark`. Both are **toggles**: the outcome depends on
current state, so a retry after a timeout or a cross-region failover flips the value back.

### Notifications and the `user_settings` fan-out

`Notification` (`notification/entity/Notification.java:16-24`) carries a `public_id` UUID plus
denormalized display fields, with a `uk_notification_dedup` unique constraint on
`(user_id, content_id, notification_type)` — the dedup mechanism for every notification type.
`UserSettings` (`user/entity/UserSettings.java`) holds four booleans, of which
`notify_new_publications` (default **false**) is the opt-in for "someone published a new planner".

A first publish therefore fans out to every opted-in user. In the base state
`NotificationService.notifyPlannerPublished` did that in Java: `findUserIdsWithNewPublicationsEnabled`
to pull the id list, map each to a `new Notification(...)`, then `saveAll`. It carried
`@Transactional(REQUIRES_NEW)` but was called from *inside* the publishing transaction, before
commit — the SSE broadcast waited for `AFTER_COMMIT`, the DB rows did not.

### Scheduling

There is deliberately **no `@Async`** anywhere in the backend
(`backend/src/main/java/org/danteplanner/backend/CLAUDE.md:13`); the entire async model is
`@Scheduled` + `@TransactionalEventListener(AFTER_COMMIT)` + Redis pub/sub.
`@EnableScheduling` sits on `BackendApplication`; `ShedLockConfig` (`@EnableSchedulerLock`) makes
fleet-duplicated cron jobs fire once across pods using the durable auth Redis as the lock store.

The scheduled work is heterogeneous: SSE heartbeats every 10 s and zombie cleanup every 60 s
(`SseService.java:228-242`), the same pair on the comment SSE service, nightly cron jobs
(`NotificationService:323`, `UserCleanupScheduler:36`, `PlannerDriftReconciler:63`), and
`PlannerViewRecorder.flush()` — a **500 ms** drain of the per-pod view buffer that writes to the
primary on every tick (`PlannerViewRecorder.java:28,44-55`). On a Seoul pod that write is
cross-region.

The relevant Spring Boot behaviour: `TaskSchedulingAutoConfiguration` supplies a `TaskScheduler`
**only when no `TaskScheduler` bean is already defined**, and its `spring.task.scheduling.pool.size`
defaults to **1**. In the base state no such bean was declared, so every `@Scheduled` method above
shared a single thread — and any one of them blocking on a cross-region write stalls the rest,
heartbeats included. The corollary matters equally: declaring *any* `TaskScheduler` bean makes the
auto-configuration back off entirely, taking `spring.task.scheduling.pool.size` with it.

### Conventions that constrain all of the above

From `backend/src/main/java/org/danteplanner/backend/CLAUDE.md`:

- **`@Transactional(readOnly = true)` is the replica-routing signal, not a free optimization**
  (line 7). Adding it to a read-after-write path silently sends the read to a stale replica.
- **No `@Async`**, no thread pools (line 13).
- **`AFTER_COMMIT` listeners run with no live transaction**, so any write they perform needs
  `@Transactional(propagation = REQUIRES_NEW)` or it silently never commits (line 14). Two such
  listeners already fire on a publish: `PlannerFilterService.onFilterRebuildRequested`
  (`AFTER_COMMIT` + `REQUIRES_NEW`, `PlannerFilterService.java:53-61`) rebuilds the search-filter
  index, and the publish broadcast listener. A single publish request therefore commits **more than
  one transaction** on the same request thread — which is exactly the case the single-shot GTID
  capture had to cover.

### The security filter chain

`SecurityConfig` disables Spring's built-in CSRF and runs a hand-rolled
`CsrfDoubleSubmitFilter` before `JwtAuthenticationFilter`
(`SecurityConfig.java:114-118`). **CSRF double-submit**: the filter mints a 256-bit random token into
a JS-readable `csrf` cookie when one is absent, and for unsafe methods requires an `X-CSRF-Token`
header equal to that cookie under a constant-time compare, rejecting with 403 otherwise
(`CsrfDoubleSubmitFilter.java:27-43`, `85-124`). It skips `ASYNC` dispatch, because SSE continuations
run on an already-committed response.

The base chain carried two exemptions for a machine-to-machine surface: `/api/internal/**` was
`permitAll()` in the authorization rules, and the CSRF filter short-circuited on the
`/api/internal/` path prefix. Behind them sat `InternalController` — `POST /refresh-game-data`
(reload the `static/` game data into `GameDataRegistry` without restarting) and
`POST /feature-flags/lineage-rotation` (flip `LineageRotationFlag`'s `AtomicBoolean` at runtime) —
gated only by a constant-time comparison against an `X-Internal-Api-Key` header sourced from SSM.
Both mutated **one pod's** memory: with a DaemonSet across two regions the call landed on whichever
pod answered, survived no restart, and left no audit trail. The `.github/workflows/sync-game-data.yml`
workflow drove the first of these over SSM + `docker exec`, a leftover from the pre-fleet single-EC2
deployment.

### The edge today: Global Accelerator in front of Traefik

The public front door is **AWS Global Accelerator** (`terraform/global-accelerator/main.tf`), a
global stack applied once, outside the per-region fleet stacks. It owns:

- one accelerator with static **anycast** IPs — addresses announced from many AWS edge locations at
  once, so a client's packets enter the AWS backbone at the nearest PoP (`main.tf:8-12`, output at
  `60-68`);
- a single TCP:443 listener with `client_affinity = SOURCE_IP` (`main.tf:14-23`);
- one endpoint group per region, each targeting the region's **ingress EC2 instance id directly**
  (no ALB/NLB) with `client_ip_preservation_enabled = true`, health-checked over HTTPS:443 every
  10 s with a threshold of 3 (`main.tf:26-58`).

Traffic lands on the ingress node's `hostNetwork` Traefik on :443. Traefik terminates TLS with the
`origin-tls` certificate and enforces Cloudflare's mTLS Authenticated Origin Pull — but as
`VerifyClientCertIfGiven`, **not** `RequireAndVerifyClientCert`, precisely because GA's health
prober presents no client certificate and a hard requirement would blackhole the region
(`deploy/base/traefik-gateway.yaml:68-76`). Two Gateway API `HTTPRoute`s attach: `backend`, a `/`
prefix route that also carries the cross-region failover path, and `healthz-local`, an exact-match
route rewritten to `/actuator/health/readiness`. GA probes only the latter, deliberately, so a region
that can serve *only* by hopping to the other region reads as unhealthy and clients are steered
direct rather than chained (`traefik-gateway.yaml:43-46`, `terraform/global-accelerator/main.tf:1-6`).

Security groups allow HTTPS from Cloudflare's edge ranges plus the Route 53 health-checker prefix
list (`terraform/modules/fleet/network.tf:99-130`) — because client-IP preservation means the real
client IP, not GA's, reaches the SG. The cluster therefore accepts **public inbound** on the ingress
nodes; the mTLS pull is the real gate and the CIDR allowlist is defense in depth.

## What is implemented

The deployment this change targets is two regions of k3s pods sharing one database. Oregon (`us-west-2`) holds the RDS primary and the primary auth Redis; Seoul (`ap-northeast-2`) runs the same image with `DATASOURCE_ROUTING_ENABLED=true` and reads from a local RDS replica while every write travels the peering link to Oregon (`deploy/overlays/seoul/configmap-patch.yaml`). So in Seoul a read is a LAN call and a write is a WAN call, a user's SSE stream is held by whichever pod accepted it (possibly in the other region), and a mutation can be retried by an edge that has no idea what already committed. Nearly every capability below follows from one of those three facts.

### The read-your-writes cookie now reflects what actually committed

Before, `GtidCookieFilter` treated "unsafe HTTP method" as a proxy for "wrote something". Any non-GET/HEAD request ran `SELECT @@gtid_executed` on the primary at the end of the chain and minted `ryw_gtid` from it — a global superset naming every transaction the server had ever committed, including other users' — and safe methods never minted a cookie at all.

After, capture is bound to transactions instead of verbs. `GtidCapturingTransactionManager` (a `JpaTransactionManager` subclass wired only where `datasource.routing.enabled=true`, i.e. Seoul) registers a `GtidCommitSynchronization` for every *non-read-only* transaction it begins. In `afterCommit` that synchronization unwraps the still-bound connection to `com.mysql.cj.jdbc.JdbcConnection` and reads the `session_track_gtids=OWN_GTID` tracker off the commit's OK packet. `GtidWriteCapture` accumulates those per request in a thread-local window opened and closed by the filter, and `unionGtidSets` merges them into one canonical `gtid_set`, coalescing adjacent ranges from the same source uuid.

<pre>
request thread
  |-- filter: writeCapture.begin()
  |     |-- main @Transactional  --commit--> afterCommit -> recordCommit("uuid:100", true)
  |     |-- AFTER_COMMIT listener, REQUIRES_NEW (filter rebuild)
  |     |                          --commit--> afterCommit -> recordCommit("uuid:101", true)
  |     |-- AFTER_COMMIT listener, REQUIRES_NEW (notification fan-out)
  |-- filter: pollCapturedGtid() -> "uuid:100-101" -> Set-Cookie: ryw_gtid=<base64url>
  |-- filter: writeCapture.clear()
</pre>

Three behaviours fall out. A request that commits nothing to MySQL — logout, which only touches Redis — mints no cookie, so the client's next read is not needlessly pinned to the Oregon primary across the WAN (`CausalGateIT.rywNoCookieOnRedisOnlyWrite_*`). A *GET* that does commit — the OAuth callback at `/api/auth/google/callback` — now mints one (`GtidCookieFilterTest.oauthGoogleCallbackMintsCookie_*`); previously the freshly created session was invisible to the caller's own follow-up replica read. And the cookie is the exact union of this request's commits rather than the whole server's history, so `WAIT_FOR_EXECUTED_GTID_SET` on the Seoul replica waits only on writes that actually belong to the caller.

Fallback is deliberate and conservative. If the tracker read throws, or the tracker is silent *before* this process has ever seen a real OWN_GTID, the accumulator marks `missedGtid` and the whole request falls back to `SELECT @@gtid_executed` — never to "nothing happened". Only after `ownGtidObserved` is set does a silent tracker get read as "this transaction wrote nothing" (`GtidWriteCaptureTest`). Positive evidence, not configuration: a server claiming to track is not proof the driver surfaces it.

**Worked example (a) — a first publish commits twice.**

```
PUT /api/planner/md/9f0b2c31-4b8e-4a2e-a1f2-6c58a3f0b7d1/publish
{"published": true}

  main tx        commits  3e11fa47-71ca-11e1-9e33-c80aa9429562:100
  filter rebuild commits  3e11fa47-71ca-11e1-9e33-c80aa9429562:101   (AFTER_COMMIT, REQUIRES_NEW)

  union      -> 3e11fa47-71ca-11e1-9e33-c80aa9429562:100-101
  base64url  -> M2UxMWZhNDctNzFjYS0xMWUxLTllMzMtYzgwYWE5NDI5NTYyOjEwMC0xMDE

Set-Cookie: ryw_gtid=M2UxMWZhNDctNzFjYS0xMWUxLTllMzMtYzgwYWE5NDI5NTYyOjEwMC0xMDE;
            Path=/; Secure; HttpOnly; SameSite=Lax

next GET carrying it ->
  SELECT WAIT_FOR_EXECUTED_GTID_SET('3e11...:100-101', 0.05)  on the Seoul replica
    returns 0 -> serve from the replica, Set-Cookie clears ryw_gtid
    returns 1 -> pin this one request to the Oregon primary
```

`CausalGateIT.ownGtidUnionAcrossCommits_*` pins this against real MySQL with the server arbitrating: it takes `@@GLOBAL.gtid_executed` before and after the publish, uses `GTID_SUBTRACT` to isolate exactly what the request committed, asserts that is ≥2 transactions, asserts the transaction manager registered one `recordCommit` per committed GTID (via a recording subclass, since the production accumulator is thread-local and already cleared), and asserts `GTID_SUBSET(requestCommits, cookieValue) = 1`. The invariant it protects: the cookie must cover *every* commit the request made. Miss the listener transaction and a client can publish, immediately re-read from the replica, and not see its own planner in the filtered list.

### Mutations that survive a retry

`PUT /{id}/publish` and `POST /{id}/bookmark` were toggles: the outcome depended on the server's current state, so replaying the same request inverted it. Behind a load balancer that can retry, and a WAN write that can time out after the commit landed, a toggle is a hazard — the classic shape is a user double-tapping publish and ending up unpublished.

Both endpoints now accept a body naming the target state: `{"published": true}` / `{"bookmarked": true}`. `PlannerPublishingService.setPublished` and `PlannerEngagementService.setBookmark` return early when the entity is already in the requested state (after the ownership check), so a replay is a no-op that returns the same 200 body. `PublishRequest` can also carry the document, so "create this local draft and publish it" is still one round trip; its content fields are validated as a group by an `@AssertTrue` (`isContentPayloadComplete`) because a state-only request legitimately omits all of them.

Backward compatibility is explicit, not accidental. A request with no body, or a body that omits the state field, still takes the old toggle path; `togglePublish`/`toggleBookmark` survive as `@Deprecated`; and each legacy call increments the Micrometer counter `planner.legacy_toggle{operation=publish|bookmark}` so the old path can be retired once cached bundles have aged out.

**Worked example (b) — the same publish arrives twice.**

```
1st  PUT .../publish {"published":true}   -> planner.published false->true, row saved,
                                             ryw_gtid minted (see (a))
2nd  PUT .../publish {"published":true}   -> already true: ownership checked, response
                                             rebuilt from the loaded aggregate, no UPDATE
                                             the request's tx commits nothing ->
                                             recordCommit(null, trackerReadable=true) ->
                                             no Set-Cookie, next read stays on the replica
```

Under the pre-change code the second call unpublished the planner. `PlannerPublishingServiceTest.publishIdempotentStateTargeted_*` and `PlannerEngagementServiceTest.bookmarkIdempotentStateTargeted_*` encode the rule as "exactly one `save`, never a `delete`"; `GtidWriteCaptureTest.publishIdempotentStateTargeted_WhenCommitWroteNothing_MintsNoCookie` encodes the tail of it — an idempotent no-op leaves no trace, so it must gate no read.

### Every SSE dispatch goes out through Redis

`SseService.sendToUser` / `broadcastToAll` / `notifyAccountSuspended` / `invalidateSettingsCache` only touch emitters held by *this* JVM. Before, four call sites reached them directly: the first-publish broadcast, ban/timeout notices from `ModerationService`, the settings-cache invalidation in `UserController`, and every notification push in `NotificationService`. In a multi-pod, two-region fleet that means the event reached the subset of users who happened to be streaming from the pod that served the request, and silently vanished for everyone else.

Now every one of those call sites publishes an `SseEnvelope` to Redis and the per-pod `SseRedisSubscriber` performs the dispatch. A third channel `sse:broadcast` was added (alongside `sse:user`, `sse:comment`) and is subscribed in `SseSubscriberConfig`; the envelope gained `excludeUserId` so "everyone except the author" survives the hop, since the dispatching pod is not the publishing pod; and `ACCOUNT_SUSPENDED("account_suspended", …)` became a real enum constant instead of a string literal.

Wire compatibility is preserved by a flag on the enum. `SseEventType.deliversRawPayload()` is true for `NOTIFY_COMMENT`, `NOTIFY_PUBLISHED`, `NOTIFY_RECOMMENDED`, `ACCOUNT_SUSPENDED` and false for `CREATED`/`UPDATED`/`DELETED`/`COMMENT_ADDED`/`SETTINGS_INVALIDATED`. The subscriber's `clientPayload()` therefore hands notification-style events the payload alone — matching the existing client schemas `SsePublishedEventSchema` and `SseNotificationEventSchema`, which read `plannerId`/`plannerTitle`/`suspensionType` at the top level — while sync events keep the envelope the client's `SseEnvelopeSchema` parses. The envelope becomes a server-side transport concern only.

**Worked example (c) — a ban issued in Oregon, delivered in Seoul.**

```
Oregon pod: ModerationService.banUser(4242, "spam")
  -> ssePublisher.publishAccountSuspended(4242, "spam", "BAN", null)
  -> PUBLISH sse:user
     {"type":"account_suspended","userId":4242,
      "payload":{"suspensionType":"BAN","reason":"spam","durationMinutes":0}}
                                        (null envelope fields are omitted: @JsonInclude NON_NULL)

Seoul pod holding user 4242's stream: SseRedisSubscriber
  -> type == ACCOUNT_SUSPENDED, deliversRawPayload -> sseService.notifyAccountSuspended(4242, payload)
  -> on the wire, to every device of user 4242:

     event: account_suspended
     data: {"suspensionType":"BAN","reason":"spam","durationMinutes":0}
```

That is byte-identical to what `useAppSse`'s `handleAccountSuspended` already parses. `SseFanoutIT` drives both new paths through a real Redis and asserts the delivered object is a `Map` carrying `suspensionType`, and for the broadcast that it carries `plannerId` and *not* `excludeUserId` — i.e. the routing field did not leak onto the client.

`SseDispatchBoundaryTest` (ArchUnit) freezes the seam: those four dispatch methods may only be called from `..shared.sse..`. It catches the regression class "new feature calls `sseService.sendToUser(...)` directly and the event reaches one pod", and its javadoc notes the inverse constraint that makes publishing belong at the call site — a dispatch method that published would re-publish whatever the subscriber handed it and loop between pods.

Alongside this, the per-pod settings cache that gates delivery moved from an unbounded `ConcurrentHashMap` to Caffeine with `expireAfterWrite(5m)` and `maximumSize(10_000)`. A pod that misses an invalidation — its Redis hop dropped, or it joined after the change — now serves stale settings for at most five minutes instead of until restart, and the cache cannot grow without bound.

### Publish notifications: one statement, and only after the commit

`notifyPlannerPublished` used to `SELECT` the ids of every user with new-publication notifications enabled, build a `Notification` entity each, and `saveAll` them — N round trips from Seoul to Oregon on a hot path — and it ran *inside* the publishing flow, so a rollback after that point left notification rows for a planner that never published.

It is now a single `INSERT IGNORE … SELECT` (`NotificationRepository.insertPublishedFanout`) that projects straight from `user_settings`, generates `public_id` with `UUID_TO_BIN(UUID())`, and leans on the existing `uk_notification_dedup (user_id, content_id, notification_type)` to absorb a re-publish without a per-recipient existence check. It runs at `Isolation.READ_COMMITTED` (an `INSERT … SELECT` under the default repeatable read locks the rows it scans in `user_settings`). And both it and the SSE broadcast now fire from the `AFTER_COMMIT` listener, so neither happens if the publish rolls back.

**Worked example (d) — five users, one publish.**

```
author(7) enabled | a(8) enabled | b(9) enabled | c(10) disabled | d(11) enabled but users.deleted_at set

insertPublishedFanout(7, "9f0b2c31-4b8e-4a2e-a1f2-6c58a3f0b7d1", "Fan Build")  -> 2
   rows for 8 and 9 only; 7 excluded as author, 10 by setting, 11 by soft delete
re-publish, same planner                                                       -> 0
   uk_notification_dedup absorbs it; user 8 still has exactly 1 unread
```

`NotificationFanoutIT` asserts precisely that against real MySQL — the SQL is native, so nothing but a container proves the predicate set and the dedup behaviour. `NotificationServiceTest` adds the complementary unit rule: the service calls `insertPublishedFanout` and *never* `saveAll`.

### Bounded waits on everything that can cross the WAN

The failure this addresses is a blackholed peering link: packets are accepted and never answered, so a call hangs rather than fails. Every default in the pre-change stack was effectively unbounded on that path — Connector/J's connect/socket timeouts are infinite, Lettuce's command timeout is 60s, Hikari's connection timeout 30s — and a single `TaskScheduler` thread (Spring's default pool size 1) carried all eight `@Scheduled` tasks, including SSE heartbeats every 10s and the planner view-buffer flush that writes cross-region every 500ms.

Now:

- `application-prod.properties` adds `connectTimeout=${DB_CONNECT_TIMEOUT_MS:10000}&socketTimeout=${DB_SOCKET_TIMEOUT_MS:10000}&trackSessionState=true` to both the application and replica JDBC urls. Flyway's url is deliberately left unbounded — a long migration must not abort at the socket timeout. (`trackSessionState=true` is also what makes the OWN_GTID tracker visible to the driver at all.)
- `RoutingDataSourceConfig` sets Hikari `connectionTimeout` to 5s on the request-serving pools (primary, replica) and 30s on the bulkhead pool, which absorbs slow re-checks by design.
- `RedisConnectionConfig` gives the cross-region auth Redis a 3s Lettuce command timeout.
- `ViewFlushSchedulerConfig` declares two schedulers: a `@Primary` shared one sized from `spring.task.scheduling.pool.size` (now 4), and a dedicated single-thread `viewFlushScheduler` that `PlannerViewRecorder.flush` is pinned to via `@Scheduled(scheduler = …)`. Both must be declared here because declaring either one makes Boot's auto-configuration back off, which would otherwise leave the pool-size property unread and route every task onto the single-threaded flush scheduler.

Two tests hold this. `DegradeByOperationConfigTest` parses the real `application-prod.properties` and asserts the app and replica urls bound the socket wait at ≤15s while Flyway's carries no `socketTimeout` at all, then boots a context to assert the shared scheduler has ≥4 threads and the view-flush scheduler is a *different* bean with exactly 1 — the regression it catches is someone adding a `TaskScheduler` bean (or dropping the property) and silently collapsing the fleet back to one scheduling thread. `DegradationIT` adds the behavioural half: Toxiproxy `timeout` toxics in both directions blackhole the app→primary path (connections accepted, nothing forwarded), and a write must fail `503 WRITE_TEMPORARILY_UNAVAILABLE` in more than 1s but less than 25s while a read still returns 200 from the healthy replica. The floor matters as much as the ceiling — a sub-second failure would mean the connection was refused, so the timeout path was never exercised.

### Fewer round trips on the planner write path

Two WAN round trips were removed from paths that took them for no new information:

- The upsert create branch used to probe twice — `existsByIdAndUserId` (own soft-deleted row?) then `existsActiveById` (someone else's row?). One `findClassificationById` projection (`PlannerClassification`: owner id + `deletedAt`) now answers both. The resulting behaviour is identical, including the edge case: another user's *soft-deleted* row matches neither branch and falls through to create, surfacing as a primary-key collision on save, exactly as before.
- `publishWithContent` used to upsert and then re-read the aggregate. `PlannerCommandService` now exposes `upsertAggregate`/`createAggregate` returning an `UpsertedPlanner(planner, response, created)` record, and the publishing service publishes the entity it was handed. `UpsertResult` and `upsertPlanner` are unchanged for existing callers — the old methods delegate.

The unit tests assert the *absence* of the old calls (`verify(plannerRepository, never()).existsByIdAndUserId(...)`, `never()).findAggregateForOwner(...)`), which is what stops the extra round trip creeping back. `PlannerClassificationIT` exists because the projection's aliased getters bind against the real schema and a mock can't prove that mapping.

### The internal API surface is gone

`/api/internal/refresh-game-data` and `/api/internal/feature-flags/lineage-rotation` are deleted, along with the `internal.api-key` property, its `permitAll` entry in `SecurityConfig`, the path-prefix CSRF exemption in `CsrfDoubleSubmitFilter`, the SSM fetch in `scripts/deploy/setup-env.sh`, the docker-compose env var, and the `sync-game-data.yml` workflow that drove it over SSM. Those endpoints mutated one pod's memory in place: in a DaemonSet fleet the change landed on whichever replica answered, survived no restart, and left no audit trail.

Their two jobs are re-homed. Game data now arrives via deploy — `deploy-fleet.yml` gained `static` and `static/**` to its path filter (the submodule pointer bump records the path as exactly `static`, which `static/**` cannot match), and a rollout reloads the registry through its existing `@PostConstruct`. The rotation flag is now configuration: `LineageRotationFlag` holds a plain final `boolean` read from `jwt.rotation.lineage-enabled`, which resolves `${JWT_ROTATION_LINEAGE_ENABLED:true}` and is set to `"true"` in both region ConfigMaps, so every pod in a region agrees and a restart preserves the value.

Two tests keep it closed from different angles. `InternalSurfaceRemovedTest` scans `src/main/java` and `src/main/resources` for the literal `/api/internal` and fails on any hit — it catches a re-introduced endpoint *and* a re-introduced security exemption, including one added in a properties file. `InternalEndpointsRemovedIT` asks the running app: GET and POST to both old paths must return 404. It sends them authenticated and CSRF-cleared on purpose — the old `permitAll` is gone, so an anonymous probe would stop at 401 and prove nothing about routing.

### The Cloudflare edge stack

`terraform/cloudflare/` is a new, self-contained root module (provider `cloudflare/cloudflare ~> 5.22`, lockfile committed) applied once and globally, like the `terraform/global-accelerator` stack it is meant to replace. It creates, per region in `var.regions`:

- a `random_password` (64 chars) as the tunnel secret and a `cloudflare_zero_trust_tunnel_cloudflared` named `danteplanner-<region>` with `config_src = "cloudflare"`, so routing lives in this state rather than in a cluster ConfigMap;
- a `cloudflare_zero_trust_tunnel_cloudflared_config` whose ingress sends `api.dante-planner.com` to `https://traefik.kube-system.svc.cluster.local:443` with `origin_server_name` (SNI matching the `origin-tls` cert), `ca_pool` (a path *inside* the cloudflared container), and `http_host_header`, plus the mandatory `http_status:404` catch-all;
- a `cloudflare_load_balancer_pool` whose single origin is addressed synthetically as `<tunnel-id>.cfargotunnel.com` with the real `Host` carried in a header override — a tunnel origin has no routable address of its own.

One account-level `cloudflare_load_balancer_monitor` probes `/healthz-local` over HTTPS every 60s with 2 retries, expecting 200 and carrying the app hostname as a `Host` header. `/healthz-local` is the region's *local* readiness (it rewrites to `/actuator/health/readiness` and excludes the cross-region fallback route), so a region that can only serve by hopping to the other region reports unhealthy and clients are steered to the healthy region directly instead of chaining a hop. Steering is `steering_policy = "geo"` with `region_pools` mapping `NEAS -> [seoul, oregon]`, `WNAM`/`ENAM` -> `[oregon, seoul]`; failover is expressed as the tail of each preference list plus a `fallback_pool`, so losing a region degrades to a cross-region hop rather than an error page. `proxied = true` is load-bearing — a DNS-only record resolves past the edge and never enters the tunnel. `session_affinity = "cookie"` is set but the code comments it as best-effort and explicitly not relied on: read-your-writes is enforced at the application seam above, not by pinning a client to a region.

Cutover is expressed as data: point `api_hostname` at a throwaway hostname for the first apply, verify each tunnel reports ≥4 edge connections, then move it to the real hostname. Outputs expose `tunnel_ids`, `load_balancer_hostname`, and the `tunnel_tokens` (marked sensitive, with instructions to route them through Secrets Manager and `ExternalSecret` rather than a hand-authored Kubernetes Secret).

### What this change does not do

- **No cloudflared runtime.** Nothing under `deploy/` references cloudflared — no Deployment, no `ExternalSecret`, no CA-bundle mount. The stack emits tokens for a workload that does not yet exist in this repo, and `terraform/global-accelerator` is untouched, so the accelerator remains the live front door until both are added.
- **No RDS parameter change.** The OWN_GTID fast path needs `session_track_gtids=OWN_GTID` on the primary. The test containers set it (`CausalHarnessSupport`), the JDBC url now asks the driver to track session state, but nothing in the diff sets the RDS parameter group; the runbook treats it as a manual step. Until it is set, every capture legitimately takes the `@@gtid_executed` superset fallback — correct, just wider than necessary.
- **No client uses the new request shapes.** `plannerApi.togglePublish`, `usePlannerPublish`, and `usePlannerBookmark` all still issue a bodyless `PUT`/`POST`, so today every real request takes the legacy toggle branch and `planner.legacy_toggle` will read 100%. The idempotency guarantee exists server-side and is unused end-to-end.
- **A stale note in the new IT.** `CausalGateIT`'s javadoc still claims the app's URL does not set `trackSessionState`, which the harness now does. The assertion itself is coverage-based (`GTID_SUBSET`) and holds either way, so the test is sound; the prose is not.
- **`UserSettingsRepository.findUserIdsWithNewPublicationsEnabled` is now dead** — the fan-out no longer calls it and nothing else does.

## The real diff

Reading order below follows a request inwards: the Cloudflare edge that now fronts both regions, then the servlet filters it lands on, then the controllers and services behind them, then the fan-out paths that fire after a write commits, and finally the configuration that bounds a cross-region hang. Tests come last, mostly summarized.

### 1. The front door: `terraform/cloudflare/`

Eight new files, all additive — there is no prior Cloudflare state. The stack replaces `terraform/global-accelerator`, but the accelerator is still standing: the cutover primitive is which hostname the load balancer answers on.

Start with the provider pin, because the major version choice is a decision, not a default:

```diff
+terraform {
+  required_version = ">= 1.6"
+  required_providers {
+    cloudflare = {
+      source = "cloudflare/cloudflare"
+      # Current major. This stack is greenfield, so it starts here rather than on 4.x:
+      # the 4-to-5 schema break is a migration cost, and there is no state to migrate.
+      version = "~> 5.22"
+    }
```

One named tunnel per region. The direction of the connection is the whole point — cloudflared dials *out* of the cluster, which is what eventually allows the fleet's public inbound rules to be deleted:

```diff
+resource "cloudflare_zero_trust_tunnel_cloudflared" "region" {
+  for_each = var.regions
+
+  account_id    = var.account_id
+  name          = "${var.name_prefix}-${each.key}"
+  tunnel_secret = base64encode(random_password.tunnel_secret[each.key].result)
+
+  # Remotely managed: the ingress rules below live in this state, and the pods carry only a
+  # token. A locally-configured tunnel would put routing in a ConfigMap and split the source
+  # of truth across two repos.
+  config_src = "cloudflare"
+}
```

`config_src = "cloudflare"` is load-bearing for the next resource: a locally-configured tunnel ignores the remote config object entirely, so the ingress rules below would be silently inert.

```diff
+resource "cloudflare_zero_trust_tunnel_cloudflared_config" "region" {
+  for_each = var.regions
+
+  account_id = var.account_id
+  tunnel_id  = cloudflare_zero_trust_tunnel_cloudflared.region[each.key].id
+
+  config = {
+    ingress = [
+      {
+        hostname = var.api_hostname
+        service  = each.value.origin_service
+
+        origin_request = {
+          origin_server_name = var.origin_server_name
+          ca_pool            = var.origin_ca_pool_path
+          http_host_header   = var.api_hostname
+        }
+      },
+      # cloudflared requires a final catch-all. Anything not matching the hostname above is
+      # not ours to serve.
+      {
+        service = "http_status:404"
+      },
+    ]
+  }
+}
```

The origin is Traefik, not the backend Service — the tunnel replaces the accelerator, not the ingress. `origin_server_name` must match the `origin-tls` certificate subject or every route answers 502; `ca_pool` is a path inside the cloudflared container, not a file this stack owns.

The load balancer, monitor, and pools:

```diff
+resource "cloudflare_load_balancer_monitor" "through_tunnel" {
+  account_id  = var.account_id
+  description = "${var.name_prefix} regional readiness, probed through the tunnel"
+
+  type             = "https"
+  method           = "GET"
+  path             = var.health_check_path
+  expected_codes   = "200"
+  interval         = var.monitor_interval_seconds
+  retries          = var.monitor_retries
+  timeout          = 5
+  follow_redirects = false
+  allow_insecure   = false
+
+  # The probe reaches a tunnel origin, so it must carry the app hostname explicitly.
+  header = { Host = [var.api_hostname] }
+}
```

```diff
+resource "cloudflare_load_balancer_pool" "region" {
+  for_each = var.regions
+
+  account_id      = var.account_id
+  name            = "${var.name_prefix}-${each.key}"
+  monitor         = cloudflare_load_balancer_monitor.through_tunnel.id
+  enabled         = true
+  minimum_origins = 1
+
+  origins = [{
+    name    = each.key
+    address = "${cloudflare_zero_trust_tunnel_cloudflared.region[each.key].id}.cfargotunnel.com"
+    enabled = true
+
+    # Required: the app hostname cannot itself be a load-balancer endpoint behind a tunnel,
+    # so the origin is addressed synthetically and the real Host travels in this header.
+    header = { Host = [var.api_hostname] }
+  }]
+}
```

The `Host` header override appears three times (monitor, pool origin, tunnel `http_host_header`) for one reason: a tunnel origin is addressed as `<uuid>.cfargotunnel.com`, and without the override that synthetic name is what Traefik's router would try to match.

```diff
+resource "cloudflare_load_balancer" "api" {
+  zone_id = var.zone_id
+  name    = var.api_hostname
+  enabled = true
+
+  # Must stay proxied. A DNS-only record resolves past the edge and never enters the tunnel.
+  proxied = true
+
+  steering_policy = "geo"
+  default_pools = [
+    for region in var.default_pool_order : cloudflare_load_balancer_pool.region[region].id
+  ]
+  fallback_pool = cloudflare_load_balancer_pool.region[var.default_pool_order[0]].id
+
+  # Best-effort only, and deliberately not relied upon: read-your-writes is enforced at the
+  # application seam, not by pinning a client to a region.
+  session_affinity = "cookie"
+
+  region_pools = {
+    for code, preference in var.steering_region_pools :
+    code => [for region in preference : cloudflare_load_balancer_pool.region[region].id]
+  }
+}
```

`session_affinity = "cookie"` is explicitly *not* the correctness mechanism — the GTID cookie in §3 is. If affinity were load-bearing, everything in §3 would be dead code.

Two variable defaults carry reasoning that is invisible at the call site:

```diff
+variable "health_check_path" {
+  description = <<-EOT
+    Monitor path, probed through the tunnel. /healthz-local is the region's LOCAL readiness
+    through Traefik: it deliberately excludes the cross-region fallback route, so a region that
+    can only serve via the other region reports unhealthy and the load balancer steers clients
+    to the healthy region directly instead of chaining a hop.
+  EOT
+  type        = string
+  default     = "/healthz-local"
+}
+
+variable "monitor_interval_seconds" {
+  description = <<-EOT
+    Probe interval. 60s is the assumed floor on this plan; confirm the actual minimum the
+    subscription offers before relying on the failover window this implies.
+  EOT
+  type        = number
+  default     = 60
+}
```

A region that is *only* serving via its neighbour must report unhealthy — otherwise the edge keeps sending traffic to it and every request pays two hops. `monitor_interval_seconds` is an assumption, not a measurement; the README's pre-apply table makes that a stop condition.

`outputs.tf` exposes `tunnel_tokens` as `sensitive`, with an explicit instruction against pasting them into a manifest (they go via Secrets Manager + `ExternalSecret`). `terraform.tfvars.example` and `README.md` are documentation; the README's operational note is worth surfacing because it contradicts an obvious instinct: never health-check through the proxied public hostname, since bot protection answers 521/403 and you will chase a phantom outage.

### 2. Deploy triggers: the submodule pointer problem

```diff
     branches: [main]
     paths:
       - 'backend/**'
+      # 'static' is a submodule: a pointer bump records the path as exactly 'static',
+      # which 'static/**' cannot match. Both are listed so the deploy fires whether the
+      # data arrives as a pointer bump or as vendored files.
+      - 'static'
+      - 'static/**'
       - '.github/workflows/deploy-fleet.yml'
```

Both entries are needed. GitHub's path filter matches the literal changed path; a submodule pointer bump changes exactly `static`, never `static/anything`. Listing only the glob would leave data-only updates undeployed.

That trigger is what replaces the entire `sync-game-data.yml` workflow, deleted here (117 lines). It used to SSM into the EC2 host, `git pull`, read `INTERNAL_API_KEY` out of SSM Parameter Store, and `docker exec curl -X POST /api/internal/refresh-game-data`. Its deletion is the reason the whole of §3 is possible.

### 3. The `/api/internal` surface, closed

`InternalController.java` is deleted (101 lines). It served two endpoints behind an API-key header compared with `MessageDigest.isEqual`: `POST /refresh-game-data` (called `GameDataRegistry.refresh()`) and `POST /feature-flags/lineage-rotation` (flipped a runtime flag). Game data now arrives through a deploy (§2); the rotation flag now arrives through configuration (below).

The deletion removes more than a controller. It removes two security exemptions that were keyed on the path prefix, and both are deleted explicitly rather than left orphaned. First the authentication bypass:

```diff
                 .requestMatchers("/actuator/prometheus").permitAll()
-                .requestMatchers("/api/internal/**").permitAll()
```

Then the CSRF exemption:

```diff
-    private static final String INTERNAL_PATH_PREFIX = "/api/internal/";
```

```diff
     private boolean requiresEnforcement(HttpServletRequest request) {
-        if (SAFE_METHODS.contains(request.getMethod())) {
-            return false;
-        }
-        String path = request.getRequestURI();
-        return path == null || !path.startsWith(INTERNAL_PATH_PREFIX);
+        return !SAFE_METHODS.contains(request.getMethod());
     }
```

Leaving either in place would have been harmless today and a trap tomorrow — a future endpoint mounted under `/api/internal` would silently inherit "no auth, no CSRF". `InternalSurfaceRemovedTest` (new, §8) greps `src/main/java` and `src/main/resources` for the literal string to keep it that way.

The runtime-mutable flag collapses to an immutable one, because nothing can flip it any more:

```diff
-import java.util.concurrent.atomic.AtomicBoolean;
 
 /**
  * Runtime-mutable feature flag gating lineage-based refresh token rotation.
  *
- * <p>Seeded at startup from {@code jwt.rotation.lineage-enabled} (default {@code false})
- * and flippable at runtime via {@code POST /api/internal/feature-flags/lineage-rotation}.
- * Backed by an {@link AtomicBoolean} for cross-thread visibility: the flag is read on the
- * authentication hot path from request threads while the toggle is set from a separate
- * internal-API request thread.</p>
+ * <p>Read from {@code jwt.rotation.lineage-enabled} (default {@code false}), which the deployment
+ * supplies, so every pod in a region agrees on the value and a restart preserves it.</p>
  */
 @Component
 public class LineageRotationFlag {
 
-    private final AtomicBoolean enabled;
+    private final boolean enabled;
 
-    public LineageRotationFlag(@Value("${jwt.rotation.lineage-enabled:false}") boolean initial) {
-        this.enabled = new AtomicBoolean(initial);
+    public LineageRotationFlag(@Value("${jwt.rotation.lineage-enabled:false}") boolean enabled) {
+        this.enabled = enabled;
     }
 
     public boolean isEnabled() {
-        return enabled.get();
-    }
-
-    public void setEnabled(boolean value) {
-        enabled.set(value);
+        return enabled;
     }
 }
```

The old endpoint mutated *one pod's* memory: whichever replica the request happened to reach, lost on restart, invisible to the others. The property replaces it, so the flag has to become environment-supplied:

```diff
-jwt.rotation.lineage-enabled=true
+jwt.rotation.lineage-enabled=${JWT_ROTATION_LINEAGE_ENABLED:true}
```

```diff
-# Internal API (CI/CD automation)
-internal.api-key=${INTERNAL_API_KEY:}
-
```

with both overlays setting it, so the value is a per-region deploy edit:

```diff
   PLANNER_STATS_READS_ENABLED: "false"
+  # Lineage-based refresh rotation. Deployment-controlled so a region can be flipped
+  # without a rebuild and every pod in it agrees across restarts.
+  JWT_ROTATION_LINEAGE_ENABLED: "true"
```

(identical hunk in `deploy/overlays/oregon/configmap-patch.yaml` and `deploy/overlays/seoul/configmap-patch.yaml`)

The key's supply chain is torn out at both ends — `docker-compose.yml` drops `INTERNAL_API_KEY: ${INTERNAL_API_KEY:-}`, and `scripts/deploy/setup-env.sh` drops both the SSM fetch and the `.env` line:

```diff
-INTERNAL_API_KEY=$(aws ssm get-parameter --name "INTERNAL_API_KEY" --with-decryption --query "Parameter.Value" --output text --region "$AWS_REGION")
```
```diff
-INTERNAL_API_KEY=$INTERNAL_API_KEY
```

### 4. Read-your-writes: capture every commit, not just the first

The gate filter is the entry point. Previously it opened a capture only for unsafe methods; now it opens one for *every* request and mints the cookie on whatever committed:

```diff
     protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
             FilterChain filterChain) throws ServletException, IOException {
-        if (isSafeMethod(request)) {
-            handleRead(request, response, filterChain);
-        } else {
-            handleWrite(request, response, filterChain);
+        writeCapture.begin();
+        try {
+            if (isSafeMethod(request)) {
+                handleRead(request, response, filterChain);
+            } else {
+                filterChain.doFilter(request, response);
+            }
+            writeCapture.pollCapturedGtid()
+                    .ifPresent(gtid -> addCookie(response, GtidCookie.of(gtid)));
+        } finally {
+            writeCapture.clear();
         }
     }
```

```diff
-    private void handleWrite(HttpServletRequest request, HttpServletResponse response,
-            FilterChain filterChain) throws ServletException, IOException {
-        filterChain.doFilter(request, response);
-        Optional<String> captured = writeCapture.pollCapturedGtid();
-        captured.ifPresent(gtid -> addCookie(response, GtidCookie.of(gtid)));
-    }
-
```

The asymmetry is deliberate and non-obvious: HTTP method no longer decides whether a cookie is minted — *whether a transaction committed* does. `GET /api/auth/google/callback` writes to MySQL and now gets a cookie; a `POST /api/auth/logout` that only touches Redis now gets none. Both are pinned by tests (§8). `handleRead` is untouched and still does the pin-to-primary work.

What feeds the capture is a custom transaction manager, registered as a bean in the gate config:

```diff
+    @Bean
+    public PlatformTransactionManager transactionManager(
+            EntityManagerFactory entityManagerFactory, GtidWriteCapture writeCapture, DataSource dataSource) {
+        return new GtidCapturingTransactionManager(entityManagerFactory, writeCapture, dataSource);
+    }
+
```

`GtidGateConfig` is `@ConditionalOnProperty(name = "datasource.routing.enabled", havingValue = "true")`, so this overrides Boot's default `JpaTransactionManager` only in routing-enabled (Seoul) contexts. Everywhere else the whole gate, capture included, does not exist.

```diff
+public class GtidCapturingTransactionManager extends JpaTransactionManager {
+
+    private final transient GtidWriteCapture capture;
+    private final transient DataSource captureDataSource;
+
+    public GtidCapturingTransactionManager(
+            EntityManagerFactory entityManagerFactory, GtidWriteCapture capture, DataSource dataSource) {
+        super(entityManagerFactory);
+        this.capture = capture;
+        this.captureDataSource = dataSource;
+        setDataSource(dataSource);
+    }
+
+    @Override
+    protected void prepareSynchronization(DefaultTransactionStatus status, TransactionDefinition definition) {
+        super.prepareSynchronization(status, definition);
+        if (status.isNewSynchronization()
+                && !definition.isReadOnly()
+                && TransactionSynchronizationManager.isActualTransactionActive()) {
+            TransactionSynchronizationManager.registerSynchronization(
+                    new GtidCommitSynchronization(capture, captureDataSource));
+        }
+    }
+}
```

The transaction manager is chosen as the seam precisely because *every* transaction passes through it, including each `REQUIRES_NEW` transaction opened by an `AFTER_COMMIT` listener. Registering in a service would have caught only the main transaction. `!definition.isReadOnly()` skips replica-routed reads, which commit no GTID. `setDataSource` is not decorative: it is what lets the synchronization reach the transaction's bound connection via `DataSourceUtils`.

```diff
+    @Override
+    public void afterCommit() {
+        Connection connection = DataSourceUtils.getConnection(dataSource);
+        try {
+            capture.recordCommit(readOwnGtid(connection), true);
+        } catch (Exception e) {
+            log.debug("OWN_GTID tracker read failed; falling back to @@gtid_executed", e);
+            capture.recordCommit(null, false);
+        } finally {
+            DataSourceUtils.releaseConnection(connection, dataSource);
+        }
+    }
+
+    /**
+     * The GTID the server attributed to this transaction, or null when it named none — which means
+     * the transaction wrote nothing, provided the server is tracking at all. Throws when the tracker
+     * cannot be reached, which the caller reads as "unknown" rather than "nothing".
+     */
+    private String readOwnGtid(Connection connection) throws java.sql.SQLException {
+        ServerSessionStateController controller =
+                connection.unwrap(JdbcConnection.class).getServerSessionStateController();
+        List<SessionStateChange> changes =
+                controller.getSessionStateChanges().getSessionStateChangesList();
+        for (SessionStateChange change : changes) {
+            if (change.getType() == ServerSessionStateController.SESSION_TRACK_GTIDS
+                    && !change.getValues().isEmpty()) {
+                return change.getValues().get(0);
+            }
+        }
+        return null;
+    }
```

Note the two-argument `recordCommit`: the second flag distinguishes "tracker said nothing" from "tracker could not be read". Collapsing them would be a correctness bug — one means the transaction wrote nothing, the other means we have no idea.

`GtidWriteCapture` turns from a single `SELECT @@gtid_executed` into a per-thread accumulator:

```diff
+    private volatile boolean ownGtidObserved;
 
     private final JdbcTemplate jdbcTemplate;
+    private final ThreadLocal<Accumulator> accumulator = new ThreadLocal<>();
 
     public GtidWriteCapture(DataSource dataSource) {
-        this.jdbcTemplate = new JdbcTemplate(dataSource);
+        this(new JdbcTemplate(dataSource));
+    }
+
+    GtidWriteCapture(JdbcTemplate jdbcTemplate) {
+        this.jdbcTemplate = jdbcTemplate;
+    }
+
+    public void begin() {
+        accumulator.set(new Accumulator());
+    }
```

```diff
+    public void recordCommit(String ownGtid, boolean trackerReadable) {
+        Accumulator acc = accumulator.get();
+        if (acc == null) {
+            return;
+        }
+        if (StringUtils.hasText(ownGtid)) {
+            ownGtidObserved = true;
+            acc.committed = true;
+            acc.ownGtids.add(ownGtid.replaceAll("\\s+", ""));
+            return;
+        }
+        if (trackerReadable && ownGtidObserved) {
+            // The server would have named a GTID had this transaction written anything, so it wrote
+            // nothing: an idempotent no-op leaves no trace and gates no read.
+            return;
+        }
+        acc.committed = true;
+        acc.missedGtid = true;
     }
```

`ownGtidObserved` is the subtle one, and its Javadoc in the diff spells out why: it is *positive evidence* that this process has seen the tracker work at least once. Before that, an empty tracker is indistinguishable from a tracker that is off, misconfigured, or unreachable, so silence must be read conservatively. Configuration claiming `session_track_gtids=OWN_GTID` is not proof the driver surfaces it.

```diff
     public Optional<String> pollCapturedGtid() {
+        Accumulator acc = accumulator.get();
+        if (acc == null || !acc.committed) {
+            return Optional.empty();
+        }
+        // A commit whose tracker yielded nothing is not covered by the union, so the whole request
+        // falls back to the global superset rather than handing back a set that gates only part of
+        // the work it claims to.
+        if (!acc.missedGtid && !acc.ownGtids.isEmpty()) {
+            return Optional.of(unionGtidSets(acc.ownGtids));
+        }
+        return readGlobalGtidExecuted();
+    }
+
+    public void clear() {
+        accumulator.remove();
+    }
```

The all-or-nothing rule matters: a partial union would be *worse* than the superset, because it would claim to gate a request's writes while silently omitting one. `ThreadLocal` state is only touched inside the filter's begin/clear window, so commits on scheduler threads accumulate nothing and leave nothing behind.

The union itself has to produce a canonical MySQL `gtid_set`, because `WAIT_FOR_EXECUTED_GTID_SET` consumes it:

```diff
+    static String unionGtidSets(Set<String> ownGtids) {
+        Map<String, List<long[]>> intervalsByUuid = new TreeMap<>();
+        for (String gtidSet : ownGtids) {
+            for (String sourceSet : gtidSet.split(",")) {
+                String[] parts = sourceSet.split(":");
+                List<long[]> intervals =
+                        intervalsByUuid.computeIfAbsent(parts[0], uuid -> new ArrayList<>());
+                for (int i = 1; i < parts.length; i++) {
+                    intervals.add(parseInterval(parts[i]));
+                }
+            }
+        }
```

```diff
+    private static List<long[]> coalesce(List<long[]> intervals) {
+        intervals.sort(Comparator.comparingLong(interval -> interval[0]));
+        List<long[]> merged = new ArrayList<>();
+        for (long[] interval : intervals) {
+            long[] last = merged.isEmpty() ? null : merged.get(merged.size() - 1);
+            if (last != null && interval[0] <= last[1] + 1) {
+                last[1] = Math.max(last[1], interval[1]);
+            } else {
+                merged.add(new long[] {interval[0], interval[1]});
+            }
+        }
+        return merged;
+    }
```

`interval[0] <= last[1] + 1` — the `+ 1` merges *adjacent* ranges, not just overlapping ones, which is what turns `…:100` ∪ `…:101` into `…:100-101` rather than an illegal two-interval encoding.

For any of this to fire, the driver has to be told to surface session state, which is a JDBC URL flag in production (see §7's `trackSessionState=true`) — and the MySQL driver classes have to be on the compile classpath, which is the real reason for this one-word build change:

```diff
-    runtimeOnly("com.mysql:mysql-connector-j")
+    implementation("com.mysql:mysql-connector-j")
+    implementation("com.github.ben-manes.caffeine:caffeine")
```

`GtidCommitSynchronization` imports `com.mysql.cj.jdbc.JdbcConnection`; at `runtimeOnly` scope it would not compile. Caffeine is for §6.

### 5. Idempotent writes: state-targeted instead of toggling

A retried or failed-over `POST /bookmark` used to un-bookmark. Same for publish. Both endpoints grow a body that names the target state, keeping the old toggle behind a counter.

Publishing controller — the entry point:

```diff
+    /** Counts calls still using the pre-state-targeted toggle shape, tagged by operation. */
+    private static final String LEGACY_TOGGLE_COUNTER = "planner.legacy_toggle";
+
     private final PlannerPublishingService plannerPublishingService;
     private final RateLimitConfig rateLimitConfig;
+    private final MeterRegistry meterRegistry;
```

```diff
     @PutMapping("/{id}/publish")
-    public ResponseEntity<PlannerResponse> togglePublish(
+    public ResponseEntity<PlannerResponse> setPublished(
             @AuthenticationPrincipal Long userId,
             @PathVariable UUID id,
-            @RequestBody(required = false) @Valid UpsertPlannerRequest request) {
+            @RequestBody(required = false) @Valid PublishRequest request) {
 
         rateLimitConfig.checkCrudLimit(userId, "publish");
-        if (request != null) {
+
+        if (request != null && request.namesState()) {
+            log.info("Setting planner {} published={} by user {}", id, request.published(), userId);
+            return ResponseEntity.ok(request.carriesContent()
+                    ? plannerPublishingService.setPublishedWithContent(
+                            userId, id, request.toUpsertRequest(), request.published())
+                    : plannerPublishingService.setPublished(userId, id, request.published()));
+        }
+
+        meterRegistry.counter(LEGACY_TOGGLE_COUNTER, "operation", "publish").increment();
+        if (request != null && request.carriesContent()) {
             log.info("Publishing planner {} with content by user {}", id, userId);
-            return ResponseEntity.ok(plannerPublishingService.publishWithContent(userId, id, request));
+            return ResponseEntity.ok(
+                    plannerPublishingService.publishWithContent(userId, id, request.toUpsertRequest()));
         }
         log.info("Toggling publish status for planner {} by user {}", id, userId);
         return ResponseEntity.ok(plannerPublishingService.togglePublish(userId, id));
```

The counter is the retirement gate: the legacy branch exists only for tabs holding a previously cached JS bundle, and `planner.legacy_toggle` reaching zero is what licenses deleting it. Note the discrimination is `namesState()` (is `published` present?), not "is there a body?" — a legacy client sends a body full of content with no `published` field, and must still land on the old path.

`PublishRequest` is a new record. It cannot simply reuse `UpsertPlannerRequest`'s field-level constraints, because a state-only request legitimately has none of those fields:

```diff
+    /**
+     * Holds when the request carries either no content at all or a complete content payload,
+     * mirroring the field-level constraints of {@link UpsertPlannerRequest}, which cannot be
+     * applied directly here because a state-only request legitimately omits every content field.
+     */
+    @AssertTrue(message = "Content payload is incomplete")
+    public boolean isContentPayloadComplete() {
+        if (!carriesContent()) {
+            return id == null && category == null && contentVersion == null && plannerType == null;
+        }
+        return id != null && !id.isBlank()
+                && category != null && !category.isBlank()
+                && contentVersion != null && contentVersion > 0
+                && plannerType != null;
+    }
```

A class-level `@AssertTrue` is the only way to express "all or none" here; per-field `@NotNull` would reject every state-only request. `carriesContent()` keys on `content != null`, so `content` is the discriminator for the whole group.

`BookmarkRequest` is the trivial counterpart — a single `Boolean bookmarked` with `namesState()`.

The service side. `setPublished` and `setPublishedWithContent` are new; both funnel into one private method:

```diff
+    @Transactional
+    public PlannerResponse setPublished(Long userId, UUID plannerId, boolean published) {
+        accessGuard.checkUserRestrictions(userId);
+
+        Planner planner = plannerRepository.findAggregate(plannerId)
+                .orElseThrow(() -> new PlannerNotFoundException(plannerId));
+        return applyPublishedState(userId, planner, published);
+    }
```

```diff
+    private PlannerResponse applyPublishedState(Long userId, Planner planner, boolean published) {
+        if (Boolean.TRUE.equals(planner.getPublished()) == published) {
+            if (!planner.isOwnedBy(userId)) {
+                throw new PlannerForbiddenException(planner.getId());
+            }
+            return PlannerResponse.fromEntity(planner, currentUpvotes(planner.getId()));
+        }
+        return togglePublish(userId, planner);
+    }
```

The explicit ownership check in the no-op branch is easy to read past. Without it, "publish an already-published planner you do not own" would return 200 with someone else's planner instead of 403 — the ownership check normally lives inside `togglePublish`, which the no-op branch never reaches.

`togglePublish` splits into an id-taking deprecated form and an aggregate-taking form, so callers that already loaded the planner do not reload it:

```diff
+    @Deprecated
     @Transactional
     public PlannerResponse togglePublish(Long userId, UUID plannerId) {
-        // Check if user has any restrictions
         accessGuard.checkUserRestrictions(userId);
 
         Planner planner = plannerRepository.findAggregate(plannerId)
                 .orElseThrow(() -> new PlannerNotFoundException(plannerId));
+        return togglePublish(userId, planner);
+    }
+
+    @Transactional
+    public PlannerResponse togglePublish(Long userId, Planner planner) {
+        UUID plannerId = planner.getId();
+
+        // Check if user has any restrictions
+        accessGuard.checkUserRestrictions(userId);
```

`publishWithContent` gets the same treatment — it used to upsert and then re-read the aggregate it had just written:

```diff
     public PlannerResponse publishWithContent(Long userId, UUID plannerId, UpsertPlannerRequest req) {
-        plannerCommandService.upsertPlanner(userId, null, plannerId, req, false);
-        Planner planner = plannerRepository.findAggregateForOwner(plannerId, userId)
-                .orElseThrow(() -> new PlannerNotFoundException(plannerId));
+        Planner planner = plannerCommandService
+                .upsertAggregate(userId, null, plannerId, req, false)
+                .planner();
         if (!Boolean.TRUE.equals(planner.getPublished())) {
-            return togglePublish(userId, plannerId);
+            return togglePublish(userId, planner);
         }
```

Which requires `PlannerCommandService` to hand back the entity, not just the response. That is what `UpsertedPlanner` is for:

```diff
+    /**
+     * The persisted aggregate of an upsert with its response and whether the planner was created.
+     */
+    public record UpsertedPlanner(Planner planner, PlannerResponse response, boolean created) {
     }
```

```diff
     public UpsertResult upsertPlanner(Long userId, UUID deviceId, UUID id, UpsertPlannerRequest req, boolean force) {
+        UpsertedPlanner upserted = upsertAggregate(userId, deviceId, id, req, force);
+        return upserted.created()
+                ? UpsertResult.created(upserted.response())
+                : UpsertResult.updated(upserted.response());
+    }
```

The existing `upsertPlanner`/`createPlanner` signatures stay as thin wrappers, so no existing caller changes.

Inside `upsertAggregate`, the two existence probes on the create path collapse to one classifying SELECT:

```diff
-        // Check if user's own planner was soft-deleted (prevents PRIMARY KEY collision)
-        if (plannerRepository.existsByIdAndUserId(id, userId)) {
-            log.warn("Planner {} is soft-deleted for user {} - cannot recreate", id, userId);
-            throw new PlannerNotFoundException(id);
-        }
-
-        // Check if planner exists for another user (prevents ID collision)
-        if (plannerRepository.existsActiveById(id)) {
-            log.warn("Planner {} exists but belongs to another user (ID collision)", id);
-            throw new PlannerForbiddenException(id);
+        // One classifying SELECT covers both non-owned-active cases. Another user's soft-deleted
+        // row matches neither branch and falls through to create, surfacing as a PK collision on save.
+        var classification = plannerRepository.findClassificationById(id);
+        if (classification.isPresent()) {
+            PlannerClassification existing = classification.get();
+            if (existing.getUserId().equals(userId)) {
+                log.warn("Planner {} is soft-deleted for user {} - cannot recreate", id, userId);
+                throw new PlannerNotFoundException(id);
+            }
+            if (existing.getDeletedAt() == null) {
+                log.warn("Planner {} exists but belongs to another user (ID collision)", id);
+                throw new PlannerForbiddenException(id);
+            }
         }
```

The comment names the one case that changes shape: another user's *soft-deleted* row matches neither branch and falls through to create, where it hits a PK collision. That case was already unreachable-in-practice under the old code too (`existsActiveById` excludes it), so this is a preserved behaviour, not a new hole — the comment exists so the next reader does not "fix" it.

Backing projection and query:

```diff
+public interface PlannerClassification {
+
+    Long getUserId();
+
+    Instant getDeletedAt();
+}
```

```diff
+    @Query("SELECT p.user.id AS userId, c.deletedAt AS deletedAt "
+            + "FROM Planner p JOIN p.content c WHERE p.id = :id")
+    Optional<PlannerClassification> findClassificationById(@Param("id") UUID id);
```

The `AS userId` / `AS deletedAt` aliases are mandatory — Spring Data matches projection getters by alias name.

Engagement side is the same pattern, smaller. Controller:

```diff
     @PostMapping("/{id}/bookmark")
-    public ResponseEntity<BookmarkResponse> toggleBookmark(
+    public ResponseEntity<BookmarkResponse> setBookmark(
             @AuthenticationPrincipal Long userId,
-            @PathVariable UUID id) {
+            @PathVariable UUID id,
+            @RequestBody(required = false) BookmarkRequest request) {
 
         rateLimitConfig.checkCrudLimit(userId, "bookmark");
+
+        if (request != null && request.namesState()) {
+            log.info("User {} setting bookmark={} on planner {}", userId, request.bookmarked(), id);
+            return ResponseEntity.ok(
+                    plannerEngagementService.setBookmark(userId, id, request.bookmarked()));
+        }
+
+        meterRegistry.counter(LEGACY_TOGGLE_COUNTER, "operation", "bookmark").increment();
         log.info("User {} toggling bookmark on planner {}", userId, id);
-        BookmarkResponse response = plannerEngagementService.toggleBookmark(userId, id);
-        return ResponseEntity.ok(response);
+        return ResponseEntity.ok(plannerEngagementService.toggleBookmark(userId, id));
     }
```

Service, with the presence-equals-desired short circuit:

```diff
+    @Transactional
+    public BookmarkResponse setBookmark(Long userId, UUID plannerId, boolean bookmarked) {
+        // Verify planner exists and is published (fail-fast)
+        if (plannerRepository.findPublishedAggregate(plannerId).isEmpty()) {
+            throw new PlannerNotFoundException(plannerId);
+        }
+
+        var existingBookmark = plannerBookmarkRepository.findByUserIdAndPlannerId(userId, plannerId);
+        if (existingBookmark.isPresent() == bookmarked) {
+            return BookmarkResponse.builder()
+                    .plannerId(plannerId)
+                    .bookmarked(bookmarked)
+                    .build();
+        }
+
+        if (bookmarked) {
+            plannerBookmarkRepository.save(new PlannerBookmark(userId, plannerId));
+            log.debug("User {} bookmarked planner {}", userId, plannerId);
+        } else {
+            plannerBookmarkRepository.delete(existingBookmark.get());
+            log.debug("User {} removed bookmark from planner {}", userId, plannerId);
+        }
```

Both old methods gain the same `@Deprecated` with a Javadoc naming the retirement condition ("Remove once its usage counter reaches zero"), linking the code to the metric in the controller.

### 6. SSE dispatch moves behind Redis

The defect being closed: several call sites reached `SseService` directly, which only knows about emitters held by *this* pod. On a multi-pod fleet the event silently missed everyone else. The fix routes every dispatch through Redis pub/sub.

Event types gain a delivery-shape flag:

```diff
 public enum SseEventType {
-    CREATED("created"),
-    UPDATED("updated"),
-    DELETED("deleted"),
-    COMMENT_ADDED("comment:added"),
-    NOTIFY_COMMENT("notify:comment"),
-    NOTIFY_PUBLISHED("notify:published"),
-    NOTIFY_RECOMMENDED("notify:recommended"),
-    SETTINGS_INVALIDATED("settings:invalidated");
+    CREATED("created", false),
+    UPDATED("updated", false),
+    DELETED("deleted", false),
+    COMMENT_ADDED("comment:added", false),
+    NOTIFY_COMMENT("notify:comment", true),
+    NOTIFY_PUBLISHED("notify:published", true),
+    NOTIFY_RECOMMENDED("notify:recommended", true),
+    SETTINGS_INVALIDATED("settings:invalidated", false),
+    ACCOUNT_SUSPENDED("account_suspended", true);
```

```diff
+    /**
+     * Whether clients receive this event's payload directly rather than the fan-out envelope.
+     *
+     * <p>Sync events carry the envelope, because the client reads its routing fields alongside the
+     * payload. Notification-style events predate the envelope on the wire and their client schemas
+     * require the payload's own fields at the top level, so the envelope stays server-side.</p>
+     */
+    public boolean deliversRawPayload() {
+        return rawPayloadDelivery;
     }
```

This flag exists because the envelope is new *server-side plumbing* for events whose wire format is already fixed by shipped clients. Getting it backwards on a notification event delivers `{type, payload:{…}}` where the client expects `{plannerId, plannerTitle, …}` and the notification silently does nothing.

The envelope grows a field and two factories:

```diff
         String excludeDeviceId,
+        Long excludeUserId,
         Object payload
 ) {
```

```diff
+    /**
+     * Event for every connected client except the user named by {@code excludeUserId}, whose own
+     * action raised it. The exclusion must survive the Redis hop, since the pod that dispatches is
+     * not the pod that published.
+     */
+    public static SseEnvelope broadcast(Long excludeUserId, SseEventType type, Object payload) {
+        return new SseEnvelope(type, null, null, null, null, null, null, excludeUserId, payload);
+    }
+
+    /**
+     * Suspension notice addressed to the suspended user, delivered wherever their stream is held.
+     */
+    public static SseEnvelope accountSuspended(Long userId, Object payload) {
+        return new SseEnvelope(
+                SseEventType.ACCOUNT_SUSPENDED, null, userId, null, null, null, null, null, payload);
+    }
```

`excludeUserId` has to be *in the envelope* rather than applied at the publisher, because the publishing pod is not the dispatching pod — filtering locally before publishing would exclude nobody anywhere else. The three pre-existing factories are updated for the new arity (mechanical).

New broadcast channel, and the subscriber container has to be told about it:

```diff
     public static final String USER = "sse:user";
     public static final String COMMENT = "sse:comment";
+    public static final String BROADCAST = "sse:broadcast";
```

```diff
         container.addMessageListener(sseRedisSubscriber,
-                List.of(new ChannelTopic(SseChannels.USER), new ChannelTopic(SseChannels.COMMENT)));
+                List.of(new ChannelTopic(SseChannels.USER),
+                        new ChannelTopic(SseChannels.COMMENT),
+                        new ChannelTopic(SseChannels.BROADCAST)));
```

Without that second hunk the publisher would publish into a channel nobody listens on and every broadcast would vanish — the classic "flag whose absence makes surrounding code inert".

Publisher gains the two new entry points:

```diff
+    public void publishBroadcast(Long excludeUserId, SseEventType type, Object payload) {
+        publish(SseChannels.BROADCAST, SseEnvelope.broadcast(excludeUserId, type, payload));
+    }
```

```diff
+    public void publishAccountSuspended(
+            Long userId, String reason, String suspensionType, Integer durationMinutes) {
+        Object payload = java.util.Map.of(
+                "suspensionType", suspensionType,
+                "reason", reason != null ? reason : "",
+                "durationMinutes", durationMinutes != null ? durationMinutes : 0);
+        publish(SseChannels.USER, SseEnvelope.accountSuspended(userId, payload));
+    }
```

Payload construction moved *up* into the publisher from `SseService.notifyAccountSuspended` — it now has to happen before the Redis hop, since the dispatching pod receives a payload, not four arguments.

Subscriber dispatches the new channel and applies the delivery-shape rule:

```diff
+        } else if (SseChannels.BROADCAST.equals(channel)) {
+            sseService.broadcastToAll(
+                    envelope.excludeUserId(), envelope.type().getValue(), clientPayload(envelope));
         } else if (SseChannels.USER.equals(channel)) {
             if (envelope.type() == SseEventType.SETTINGS_INVALIDATED) {
                 sseService.invalidateSettingsCache(envelope.userId());
+            } else if (envelope.type() == SseEventType.ACCOUNT_SUSPENDED) {
+                sseService.notifyAccountSuspended(envelope.userId(), clientPayload(envelope));
             } else {
                 UUID excludeDeviceId = envelope.excludeDeviceId() != null
                         ? UUID.fromString(envelope.excludeDeviceId())
                         : null;
-                sseService.sendToUser(envelope.userId(), excludeDeviceId, envelope.type().getValue(), envelope);
+                sseService.sendToUser(envelope.userId(), excludeDeviceId,
+                        envelope.type().getValue(), clientPayload(envelope));
             }
         }
     }
+
+    private static Object clientPayload(SseEnvelope envelope) {
+        return envelope.type().deliversRawPayload() ? envelope.payload() : envelope;
+    }
```

`SseService.notifyAccountSuspended` is reduced to pure dispatch:

```diff
-    public void notifyAccountSuspended(Long userId, String reason, String suspensionType, Integer durationMinutes) {
-        Map<String, Object> data = Map.of(
-                "suspensionType", suspensionType,
-                "reason", reason != null ? reason : "",
-                "durationMinutes", durationMinutes != null ? durationMinutes : 0
-        );
-        sendToUser(userId, "account_suspended", data);
-        log.info("Sent account_suspended notification to user {} (type: {})", userId, suspensionType);
+    public void notifyAccountSuspended(Long userId, Object payload) {
+        sendToUser(userId, org.danteplanner.backend.shared.entity.SseEventType.ACCOUNT_SUSPENDED.getValue(), payload);
+        log.info("Sent account_suspended notification to user {}", userId);
     }
```

Note the literal `"account_suspended"` is gone; the enum constant added above is now the single source of that string.

The call-site swaps are all the same shape: `SseService` → `SsePublisher`. `ModerationService` (two call sites, timeout and ban), `UserController` (`invalidateSettingsCache` → `publishSettingsInvalidation`), `NotificationService`, `PlannerPublishingService`. The `UserController` one is the most consequential in a fleet — settings invalidation that only cleared the local pod's cache left every other pod serving stale gating settings:

```diff
         UserSettingsResponse settings = userSettingsService.updateSettings(userId, request);
-        sseService.invalidateSettingsCache(userId);
+        ssePublisher.publishSettingsInvalidation(userId);
```

And the settings cache itself gets a TTL, because a missed invalidation is no longer hypothetical:

```diff
-    private final ConcurrentHashMap<Long, CachedSettings> settingsCache = new ConcurrentHashMap<>();
+    /**
+     * Per-node cache of the settings that gate event delivery. Entries expire on their own so a node
+     * that misses an invalidation — its Redis hop dropped, or it joined after the change — serves
+     * stale settings for a bounded time instead of until restart.
+     */
+    private final Cache<Long, CachedSettings> settingsCache = Caffeine.newBuilder()
+            .expireAfterWrite(SETTINGS_CACHE_TTL)
+            .maximumSize(SETTINGS_CACHE_MAX_ENTRIES)
+            .build();
```

with `SETTINGS_CACHE_TTL = Duration.ofMinutes(5)` and `SETTINGS_CACHE_MAX_ENTRIES = 10_000`. `remove` → `invalidate` at two sites, and the get path simplifies now that Caffeine's loader handles absence:

```diff
     private boolean isEventAllowed(Long userId, String eventType) {
-        CachedSettings settings = settingsCache.get(userId);
-        if (settings == null) {
-            settings = cacheSettingsIfAbsent(userId);
-        }
```

```diff
     private CachedSettings cacheSettingsIfAbsent(Long userId) {
-        return settingsCache.computeIfAbsent(userId, id -> {
+        return settingsCache.get(userId, id -> {
```

The unbounded `ConcurrentHashMap` also had no eviction at all — an entry per user who ever connected, held until restart. `maximumSize` closes that separately from the staleness fix.

The whole seam is then frozen by `SseDispatchBoundaryTest` (§8), an ArchUnit rule restricting `sendToUser|broadcastToAll|notifyAccountSuspended|invalidateSettingsCache` to callers inside `..shared.sse..`. Its Javadoc names the inverse constraint, which is the reason publishing sits at the call site rather than inside the dispatch methods: a dispatch method that published would re-publish whatever the subscriber handed it and loop between pods forever.

### 7. Published-notification fan-out in one statement

Previously: load every eligible user id, build a `Notification` per user in Java, `saveAll`. Now one `INSERT … SELECT`:

```diff
+    @Modifying
+    @Query(value = """
+            INSERT IGNORE INTO notifications
+                (user_id, content_id, notification_type, public_id, planner_id, planner_title)
+            SELECT s.user_id, :plannerId, 'PLANNER_PUBLISHED', UUID_TO_BIN(UUID()),
+                   UUID_TO_BIN(:plannerId), :plannerTitle
+            FROM user_settings s
+            JOIN users u ON u.id = s.user_id
+            WHERE s.notify_new_publications = true
+              AND u.deleted_at IS NULL
+              AND s.user_id <> :authorId
+            """, nativeQuery = true)
+    int insertPublishedFanout(
+            @Param("authorId") Long authorId,
+            @Param("plannerId") String plannerId,
+            @Param("plannerTitle") String plannerTitle);
```

`INSERT IGNORE` delegates re-publish dedup to the existing `uk_notification_dedup` constraint instead of a per-recipient existence check, and `UUID_TO_BIN(UUID())` generates the `public_id` server-side so nothing needs constructing in Java.

```diff
-    @Transactional(propagation = Propagation.REQUIRES_NEW)
+    @Transactional(propagation = Propagation.REQUIRES_NEW, isolation = Isolation.READ_COMMITTED)
     public void notifyPlannerPublished(Long authorId, UUID plannerId, String plannerTitle) {
-        // Only notify users who have notifyNewPublications enabled
-        List<Long> userIds = userSettingsRepository.findUserIdsWithNewPublicationsEnabled(authorId);
-
-        if (userIds.isEmpty()) {
-            log.debug("No users to notify for planner publish {}", plannerId);
-            return;
-        }
-
-        // Create notifications for all users
-        List<Notification> notifications = userIds.stream()
-                .map(userId -> new Notification(
-                        userId,
-                        plannerId.toString(),
-                        NotificationType.PLANNER_PUBLISHED,
-                        plannerId,
-                        plannerTitle,
-                        null, // no comment snippet
-                        null  // no comment public ID
-                ))
-                .toList();
-
-        List<Notification> saved = notificationRepository.saveAll(notifications);
-        log.info("Created {} PLANNER_PUBLISHED notifications for planner {} by author {}",
-                saved.size(), plannerId, authorId);
+        int inserted = notificationRepository.insertPublishedFanout(
+                authorId, plannerId.toString(), plannerTitle);
+        log.info("Fanned out {} PLANNER_PUBLISHED notifications for planner {} by author {}",
+                inserted, plannerId, authorId);
     }
```

`Isolation.READ_COMMITTED` is not decorative. Under the default REPEATABLE READ, `INSERT … SELECT` takes gap/next-key locks across the scanned `user_settings` range — that is a table-wide lock footprint on a table every login touches. Dropping to READ_COMMITTED narrows it to the rows actually inserted.

The call also *moves*. It used to run inside the publishing transaction; it now runs from the AFTER_COMMIT listener alongside the SSE broadcast:

```diff
     @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
     public void onPlannerPublished(PlannerPublishedEvent event) {
-        notificationSseService.broadcastToAll(event.excludeUserId(), event.eventType(), event.data());
+        ssePublisher.publishBroadcast(event.authorId(), SseEventType.NOTIFY_PUBLISHED, event.data());
+        notificationService.notifyPlannerPublished(
+                event.authorId(), event.plannerId(), event.plannerTitle());
+    }
```

```diff
-            // First-time publish notification (one-time only)
+            // First-time publish notification (one-time only). The DB fan-out and the SSE
+            // broadcast both run from the AFTER_COMMIT listener, so neither persists nor fires
+            // when the publish rolls back.
             if (firstPublish) {
-                // Create DB notifications for users with setting enabled
-                notificationService.notifyPlannerPublished(userId, plannerId, saved.getTitle());
-
-                // Broadcast SSE to all connected users except author, only after commit
                 User author = saved.getUser();
                 eventPublisher.publishEvent(new PlannerPublishedEvent(
-                        userId, SseEventType.NOTIFY_PUBLISHED.getValue(), Map.of(
+                        userId, plannerId, saved.getTitle(), Map.of(
```

Which is why the event record changes shape — it now carries what the listener needs to do both jobs, not just the SSE arguments:

```diff
-    public record PlannerPublishedEvent(Long excludeUserId, String eventType, Map<String, Object> data) {
+    public record PlannerPublishedEvent(
+            Long authorId, UUID plannerId, String plannerTitle, Map<String, Object> data) {
     }
```

`eventType` disappearing from the record is safe because the listener now hardcodes `SseEventType.NOTIFY_PUBLISHED` — there was only ever one value.

Notification push is retyped from `String` to `SseEventType` throughout (`createAndPush`, `pushNotification`, three call sites) and routed through the publisher:

```diff
-        sseService.sendToUser(userId, eventType, data);
+        ssePublisher.publishUserEvent(
+                userId, null, eventType, notification.getPublicId().toString(), data);
```

### 8. Degrade by operation: bound every cross-region wait

Four unrelated-looking hunks with one shared premise — a hung cross-region dependency must fail an *operation*, not consume the resource that serves everything else.

JDBC, in `application-prod.properties`:

```diff
-spring.datasource.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT:3306}/${MYSQL_DATABASE}?sslMode=VERIFY_CA&serverTimezone=UTC&trustCertificateKeyStoreUrl=file:/app/certs/rds-truststore.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit
+# The application urls bound connect and socket waits so a blackholed cross-region primary fails
+# the write instead of pinning a request thread until the operating system gives up. Flyway's url
+# below is deliberately left unbounded: a long migration must not abort at the socket timeout.
+spring.datasource.url=jdbc:mysql://${MYSQL_HOST}:${MYSQL_PORT:3306}/${MYSQL_DATABASE}?sslMode=VERIFY_CA&serverTimezone=UTC&connectTimeout=${DB_CONNECT_TIMEOUT_MS:10000}&socketTimeout=${DB_SOCKET_TIMEOUT_MS:10000}&trackSessionState=true&trustCertificateKeyStoreUrl=file:/app/certs/rds-truststore.p12&trustCertificateKeyStoreType=PKCS12&trustCertificateKeyStorePassword=changeit
```

Three parameters, two purposes. `connectTimeout`/`socketTimeout` bound the hang; `trackSessionState=true` is what makes §4's OWN_GTID read work at all — without it Connector/J never surfaces the session-state changes and every capture silently takes the `@@gtid_executed` fallback. The `spring.flyway.url` line directly below is deliberately left untouched, and the asymmetry is the point: a long migration must not abort at 10 seconds. `datasource.replica.url` gets the identical treatment to the app url.

Hikari:

```diff
+    /**
+     * Bound on waiting for a connection on a request-serving pool, so a caller gives up rather than
+     * holding a request thread while a cross-region endpoint hangs.
+     */
+    private static final long REQUEST_CONNECTION_TIMEOUT_MS = 5_000L;
+
+    /**
+     * The bulkhead absorbs slow re-checks by design, so its queue legitimately outlasts the
+     * request-pool bound; it stays bounded, just far more patiently.
+     */
+    private static final long BULKHEAD_CONNECTION_TIMEOUT_MS = 30_000L;
```

applied as `config.setConnectionTimeout(REQUEST_CONNECTION_TIMEOUT_MS)` to the primary and replica pools and `BULKHEAD_CONNECTION_TIMEOUT_MS` to the bulkhead pool. The 6× gap is intentional: the bulkhead exists to absorb slow primary re-checks, so holding it to the request bound would defeat it.

Lettuce, for the cross-region auth Redis:

```diff
+    /** Bound on a cross-region auth Redis command, well below Lettuce's minute-long default. */
+    private static final long AUTH_COMMAND_TIMEOUT_MS = 3_000L;
```

```diff
     public LettuceConnectionFactory authRedisConnectionFactory() {
-        return new LettuceConnectionFactory(standaloneConfiguration(auth));
+        // The auth endpoint is reached cross-region, so a command must give up well before
+        // Lettuce's minute-long default and let the caller degrade instead of holding its thread.
+        return new LettuceConnectionFactory(
+                standaloneConfiguration(auth),
+                LettuceClientConfiguration.builder()
+                        .commandTimeout(Duration.ofMillis(AUTH_COMMAND_TIMEOUT_MS))
+                        .build());
     }
```

3s < 5s (Hikari) < 10s (socket) — each layer gives up before the one it sits inside, so a failure surfaces at the layer that can classify it rather than as an outer timeout.

Scheduler isolation. A new config declares *two* schedulers, and the Javadoc explains why one of them cannot be omitted:

```diff
+/**
+ * Schedulers for the application's periodic work.
+ *
+ * <p>Both are declared here because declaring either one suppresses the framework's own scheduler:
+ * its auto-configuration backs off as soon as any {@code TaskScheduler} bean exists, which would
+ * otherwise leave {@code spring.task.scheduling.pool.size} unread and route every scheduled task
+ * onto the single-threaded flush scheduler below.</p>
+ *
+ * <p>The shared scheduler carries more than one thread so a task blocked on a cross-region write
+ * cannot stop SSE heartbeats. The planner view-buffer flush runs every 500ms and writes
+ * cross-region on every tick, so it is isolated onto its own thread: it can stall without reaching
+ * the others, and a slow neighbour cannot delay it.</p>
+ */
+@Configuration
+public class ViewFlushSchedulerConfig {
+
+    public static final String VIEW_FLUSH_SCHEDULER = "viewFlushScheduler";
+
+    @Bean
+    @Primary
+    public ThreadPoolTaskScheduler taskScheduler(
+            @Value("${spring.task.scheduling.pool.size:4}") int poolSize) {
+        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
+        scheduler.setPoolSize(poolSize);
+        scheduler.setThreadNamePrefix("scheduling-");
+        scheduler.setRemoveOnCancelPolicy(true);
+        return scheduler;
+    }
+
+    @Bean(VIEW_FLUSH_SCHEDULER)
+    public ThreadPoolTaskScheduler viewFlushScheduler() {
+        ThreadPoolTaskScheduler scheduler = new ThreadPoolTaskScheduler();
+        scheduler.setPoolSize(1);
+        scheduler.setThreadNamePrefix("view-flush-");
+        scheduler.setRemoveOnCancelPolicy(true);
+        return scheduler;
+    }
+}
```

The trap is real: adding only the single-threaded `viewFlushScheduler` would suppress Boot's auto-configured scheduler, and every `@Scheduled` task in the app — SSE heartbeats included — would end up serialized on one thread behind a 500ms cross-region flush. Declaring the shared one explicitly with `@Primary` is what prevents that.

The flush opts into the dedicated scheduler by name:

```diff
-    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS)
+    @Scheduled(fixedDelay = FLUSH_INTERVAL_MS, scheduler = ViewFlushSchedulerConfig.VIEW_FLUSH_SCHEDULER)
     @Transactional
     public void flush() {
```

and the pool size is a property, not a literal:

```diff
+# Scheduling
+# More than one thread so a task blocked on a cross-region write cannot stop SSE heartbeats.
+spring.task.scheduling.pool.size=4
+
```

### 9. Tests

Mostly summarized. Roughly 1,700 diff lines across 20 test files; the bulk is mechanical — constructor-argument swaps from `SseService` to `SsePublisher` in `ModerationServiceTest`, `NotificationServiceTest`, `PlannerPublishingServiceTest`, `PlannerEngagementServiceTest` (~150 lines), `MeterRegistry` wiring into the two controller tests, and `PlannerCommandServiceTest` restubbing `existsByIdAndUserId`/`existsActiveById` onto `findClassificationById` (~100 lines). Deletions: `InternalControllerTest` (107 lines), the `setEnabled` case in `LineageRotationFlagTest`, and the CSRF-exempt `/api/internal/**` case in `CsrfDoubleSubmitFilterTest` — all following code that no longer exists.

Six new files carry the load-bearing assertions:

- **`InternalSurfaceRemovedTest`** — walks `src/main/java` and `src/main/resources` and fails if any file contains the string `/api/internal`. Crude on purpose: it catches a re-introduced security exemption as readily as a re-introduced controller.
- **`SseDispatchBoundaryTest`** — the ArchUnit rule from §6.
- **`DegradeByOperationConfigTest`** — asserts the shared scheduler has ≥4 core threads, that `viewFlushScheduler` is a *different* bean with exactly 1, that both app JDBC urls carry `connectTimeout`/`socketTimeout` ≤15s, and that `spring.flyway.url` does **not** — the deliberate asymmetry from §8, pinned so nobody "fixes" it later. It parses `${VAR:default}` placeholders to reach the literal.
- **`GtidWriteCaptureTest`** — unit coverage of the accumulator and `unionGtidSets` interval coalescing.
- **`NotificationFanoutIT`**, **`PlannerClassificationIT`**, **`InternalEndpointsRemovedIT`** — integration coverage of §7, §5's classification query, and a live 404 on the removed endpoints.

Two additions to existing tests are worth reading in full, because they pin behaviour that is otherwise invisible.

`CausalGateIT` gains a recording decorator over `GtidWriteCapture` — necessary because the production accumulator is thread-local and already cleared by the time `mockMvc.perform` returns:

```diff
+    static class RecordingGtidWriteCapture extends GtidWriteCapture {
+
+        private final AtomicInteger commitRecordings = new AtomicInteger();
```

and uses the primary's own `GTID_SUBTRACT`/`GTID_SUBSET` as arbiter rather than string comparison:

```diff
+        assertThat(committedTransactions)
+                .as("the publish request must commit MORE THAN ONE transaction on the primary "
+                        + "(main tx + the AFTER_COMMIT REQUIRES_NEW listener txs); committed: %s",
+                        requestCommits)
+                .isGreaterThanOrEqualTo(2);
```
```diff
+        assertThat(gtidSubset(requestCommits, cookieGtidSet))
+                .as("the ryw cookie (%s) must cover EVERY GTID the request committed (%s), "
+                        + "so a replica read gates past the filter rebuild, not only the main commit",
+                        cookieGtidSet, requestCommits)
+                .isTrue();
```

It asserts *coverage*, not equality, and the diff says why: the harness takes the `@@gtid_executed` fallback, and demanding exact equality would force a production URL change to satisfy a test. The companion negative case — logout commits no MySQL transaction, so no cookie is minted — is what proves the §4 filter change did not degenerate into "mint a cookie on every request".

The harness itself has to enable the tracker on both the server and the URL, or the capture path is never exercised:

```diff
                     "--enforce-gtid-consistency=ON",
+                    "--session-track-gtids=OWN_GTID",
```
```diff
-        registry.add("spring.datasource.url", PRIMARY::getJdbcUrl);
+        // The driver surfaces OWN_GTID session-state changes only when asked to track them,
+        // so the app url mirrors production and the capture path is exercised for real.
+        registry.add("spring.datasource.url", () -> withSessionStateTracking(PRIMARY.getJdbcUrl()));
```

`DegradationIT` adds the blackhole case, and its comment names the distinction that makes it a separate test from the existing sever case:

```diff
+    private void blackholePrimaryDb() throws IOException {
+        PRIMARY_DB_PROXY.toxics().timeout("primary-blackhole-upstream", ToxicDirection.UPSTREAM, 0);
+        PRIMARY_DB_PROXY.toxics().timeout("primary-blackhole-downstream", ToxicDirection.DOWNSTREAM, 0);
+    }
```

Disabling the proxy makes connections *refuse* — sub-millisecond failure that exercises no timeout at all. `timeout` toxics keep accepting and forward nothing, which is what a peering loss looks like. Hence the two-sided wall-clock assertion: a floor of 1s proving the connection really hung, a ceiling of 25s proving no request outlived the socket timeout plus pool headroom.
