# Reconcile: 044-cloudflare-edge-and-write-hardening

_Range: `task/044-cloudflare-edge-and-write-hardening-base..HEAD` · audited cold against `spec.md`._

## Coverage        (the gate)

### Coverage reconciliation — 044 Cloudflare edge and write hardening

All paths relative to `/home/user/github/LimbusPlanner`. Backend main = `backend/src/main/java/org/danteplanner/backend`, test = `backend/src/test/java/org/danteplanner/backend`.

| row id | verdict | implementing code | test |
|---|---|---|---|
| own-gtid-union-across-commits | drift | `shared/gtid/GtidWriteCapture.java:68,81,128` (accumulate + union); `shared/gtid/GtidCapturingTransactionManager.java:37`; `shared/gtid/GtidCommitSynchronization.java:41`; `shared/gtid/GtidGateConfig.java:38` | `shared/gtid/GtidWriteCaptureTest.java:31` (accumulator only) |
| own-gtid-fallback-on-empty-tracker | match | `shared/gtid/GtidWriteCapture.java:81-99`; `shared/gtid/GtidCommitSynchronization.java:45-64` | `shared/gtid/GtidWriteCaptureTest.java:39,48` |
| publish-fanout-single-statement | match | `notification/repository/NotificationRepository.java:70-94`; `notification/service/NotificationService.java:252-257`; `planner/service/PlannerPublishingService.java:58-62` | `integration/NotificationFanoutIT.java:64`; `service/NotificationServiceTest.java:65` |
| publish-loads-aggregate-once | match | `planner/service/PlannerPublishingService.java:228-236,157`; `planner/service/PlannerCommandService.java:307,269` | `service/PlannerPublishingServiceTest.java:164` |
| create-existence-two-selects | match | `planner/service/PlannerCommandService.java:347-359`; `planner/repository/PlannerRepository.java:84-91`; `planner/repository/PlannerClassification.java:11` | `planner/service/PlannerCommandServiceTest.java:833,856,878`; `integration/PlannerClassificationIT.java:48`; limit probe `planner/service/PlannerCommandServiceTest.java:288` |
| ryw-cookie-on-committed-write | match | `shared/gtid/GtidCookieFilter.java:41-51`; `shared/gtid/GtidCapturingTransactionManager.java:37` | `integration/CausalGateIT.java:130` (real PUT → GTID cookie asserted through the full routing stack) |
| ryw-no-cookie-on-redis-only-write | match | `shared/gtid/GtidWriteCapture.java:81-84` (no commit → empty) | `integration/CausalGateIT.java:191` (real POST /api/auth/logout); `shared/gtid/GtidWriteCaptureTest.java:70` |
| oauth-google-callback-mints-cookie | drift | `shared/gtid/GtidCookieFilter.java:41-51` (safe methods no longer skip capture) | `shared/gtid/GtidCookieFilterTest.java:145` (stubbed capture, no-op chain) |
| stale-read-pins-to-primary | match | `shared/gtid/GtidCookieFilter.java:55-75`; `shared/gtid/GtidReadGate` + `shared/config/ReadOnlyRoutingDataSource` (unchanged) | `integration/CausalGateIT.java:130` (cookie-bearing read serves primary-only title while replica stopped, replica title without cookie, cookie cleared once caught up) |
| write-hang-reads-survive | drift | `shared/config/RoutingDataSourceConfig.java:79,88,100`; `backend/src/main/resources/application-prod.properties:5,16` | `integration/DegradationIT.java:340` (pre-existing, proxy-disable = refuse); `config/DegradeByOperationConfigTest.java:62` (properties-text) |
| scheduler-not-starved-by-cross-region-hang | drift | `planner/config/ViewFlushSchedulerConfig.java:27-44`; `planner/service/PlannerViewRecorder.java:44`; `backend/src/main/resources/application.properties:168` | `config/DegradeByOperationConfigTest.java:39` (bean pool sizes) |
| sse-broadcast-cross-pod | match | `shared/sse/SsePublisher.java:81`; `shared/sse/SseChannels.java:10`; `shared/sse/SseSubscriberConfig.java:46-48`; `shared/sse/SseRedisSubscriber.java:52-54`; `shared/sse/SseEnvelope.java:29-33` | `integration/SseFanoutIT.java:105` (publish → real Redis → subscriber dispatch on a `@MockitoSpyBean` SseService) |
| sse-suspension-cross-pod | match | `shared/sse/SsePublisher.java:94`; `shared/sse/SseEnvelope.java:38`; `shared/entity/SseEventType.java:19`; `shared/sse/SseRedisSubscriber.java:57-58`; call site `moderation/service/ModerationService.java:92,159` | `integration/SseFanoutIT.java:122`; call-site swap `service/ModerationServiceTest.java:460,502` |
| sse-settings-invalidation-cross-pod | drift | `user/controller/UserController.java:166`; `shared/sse/SsePublisher.java:52`; `shared/sse/SseRedisSubscriber.java:56`; `shared/sse/SseService.java:53,211` | `integration/SseFanoutIT.java:134` (publisher-level only); `architecture/SseDispatchBoundaryTest.java:28` |
| publish-idempotent-state-targeted | match | `planner/service/PlannerPublishingService.java:79,98,110`; controller `planner/controller/PlannerPublishingController.java:60-77`; `planner/dto/PublishRequest.java` | `service/PlannerPublishingServiceTest.java:131,147`; legacy handler still 200s: `integration/PlannerPublishFlowIT.java:169` |
| bookmark-idempotent-state-targeted | match | `planner/service/PlannerEngagementService.java:164-188`; controller `planner/controller/PlannerEngagementController.java:88-102`; `planner/dto/BookmarkRequest.java` | `service/PlannerEngagementServiceTest.java:114,131` |
| internal-endpoints-removed | drift | `InternalController.java` deleted; `shared/config/SecurityConfig.java:86` permitAll removed; `shared/security/CsrfDoubleSubmitFilter.java:113` prefix exemption removed; `application.properties` key removed; `.github/workflows/deploy-fleet.yml:18` adds `static/**` | `architecture/InternalSurfaceRemovedTest.java:32` (source-text scan) |
| edge-geo-steers | MISSING | none — no `terraform/cloudflare` root exists (only `terraform/global-accelerator`, `oregon`, `seoul`, …); no cloudflared manifests | none — **deferred by design: live drill / infra** (`[drill]`, Done-When `(live)`) |
| edge-survives-region-loss | MISSING | none | none — **deferred by design: live drill / infra** (`[drill]`, Done-When `(live)`) |
| inbound-closed-post-teardown | MISSING | none — GA module and ingress SG rules still present | none — **deferred by design: infra teardown** (`[drill + tf-plan]`, Done-When R10 `(infra)`, POST-BAKE) |

Justifications for non-`match` rows:

- **own-gtid-union-across-commits** — `GtidWriteCaptureTest:31` calls `recordCommit()` twice by hand and asserts the two GTIDs coalesce to `…:100-101`. The row's claim is that *a publish that commits twice* yields a union: that requires `GtidCapturingTransactionManager` to register a `GtidCommitSynchronization` on the main tx **and** on the `REQUIRES_NEW` filter-rebuild tx (`planner/service/PlannerFilterService.java:54`). Neither class is referenced by any test (`grep` over `backend/src/test` finds zero hits for `GtidCapturingTransactionManager`/`GtidCommitSynchronization`). The union algebra is pinned; the two-commit wiring that makes it meaningful is not.
- **oauth-google-callback-mints-cookie** — the test drives a bare `GtidCookieFilter` on a `GET /api/auth/google/callback` with `writeCapture.pollCapturedGtid()` **stubbed** to return a GTID and a `MockFilterChain` that runs no controller. It proves the filter no longer skips cookie-minting on safe methods. It does not prove the callback commits a MySQL tx, that the commit is captured, or that a following `GET /api/auth/me` sees the row.
- **write-hang-reads-survive** — `DegradationIT:340` (pre-existing, untouched by this range) severs the primary with `PRIMARY_DB_PROXY.disable()`, i.e. a **refused/reset** connection, not the blackhole/connect-hang the row specifies; it asserts read 200 + typed `WRITE_TEMPORARILY_UNAVAILABLE` + readiness UP, but nothing about the ~10s bound or thread exhaustion. The bound itself is asserted only by `DegradeByOperationConfigTest:62`, which parses `application-prod.properties` as text and checks `socketTimeout=` ≤ 15000 — it proves the property string is present, not that the driver applied it or that a hung write returns inside 10s. No test touches the Hikari `connectionTimeout` values set at `RoutingDataSourceConfig.java:79,88,100` (`RoutingDataSourceConfigTest` asserts pool sizes only).
- **scheduler-not-starved-by-cross-region-hang** — `DegradeByOperationConfigTest:39` boots an `ApplicationContextRunner` over `ViewFlushSchedulerConfig` and asserts the primary scheduler's core pool ≥ 4 and that `viewFlushScheduler` is a distinct 1-thread bean. That is configuration-shape, not the row's proposition: no test stalls a scheduled task and observes SSE heartbeats continuing, and nothing verifies that `@Scheduled(scheduler = VIEW_FLUSH_SCHEDULER)` on `PlannerViewRecorder:44` actually resolves to that bean in the real context.
- **sse-settings-invalidation-cross-pod** — `SseFanoutIT:134` calls `ssePublisher.publishSettingsInvalidation(userId)` directly and verifies `sseService.invalidateSettingsCache(userId)` was invoked on the spy. The Redis hop is genuinely exercised, but (a) the row's `given` is *PUT /users/settings handled on pod A*, and no test drives that endpoint — `UserControllerTest` has no `updateSettings` case, and the only other evidence is the ArchUnit sweep, which would stay green if `UserController` dropped the call entirely instead of publishing; (b) the assertion is "the method was called", not "the cache entry is gone" (the spy does run the real method, so the effect happens, but nothing observes the cache afterwards).
- **internal-endpoints-removed** — the removal itself is real and thorough. The test, however, walks `src/main/java` + `src/main/resources` and asserts no file contains the literal `/api/internal`: a static text rule, not a delivered response. No test issues a request to `/api/internal/*`. Worse, the row's stated response is **404**, but with `SecurityConfig.java:110` `anyRequest().authenticated()` and the `permitAll` matcher gone, an unauthenticated request to `/api/internal/x` now returns 401, not 404 — nothing in the suite would catch that discrepancy. The `static/**` trigger clause (`deploy-fleet.yml:18`) that the row's `then` also asserts has no test at all.

#### Done-When commitments

Only `(local-tdd)` items are graded; `(infra)`/`(live)` lines are noted at the end for completeness.

- **R3.1 — session_track_gtids=OWN_GTID: RDS param set; capture reads the tracker with the `@@gtid_executed` fallback intact — PARTIALLY DONE.**
  - Tracker read: `shared/gtid/GtidCommitSynchronization.java:45-60` unwraps to `com.mysql.cj.jdbc.JdbcConnection` and scans `SESSION_TRACK_GTIDS`; driver promoted to `implementation` in `backend/build.gradle.kts:56`. **No test exercises this method.** The IT harness enables the server flag (`integration/CausalHarnessSupport.java:73`), but `CausalGateIT` only asserts a GTID-shaped cookie exists — a value produced by the `@@gtid_executed` fallback is indistinguishable, so the OWN_GTID read could be dead and every test stays green.
  - Union + fallback: done and tested (`GtidWriteCaptureTest:31,39,48`).
  - RDS parameter-group change: **not in this range** (no terraform diff). The "staging spike proving the Hikari + `LazyConnectionDataSourceProxy` unwrap" has no artifact in the repo.
- **R3.2 — one AFTER_COMMIT / REQUIRES_NEW / READ-COMMITTED `INSERT IGNORE … SELECT` — DONE.** `NotificationRepository.java:70-94`, `NotificationService.java:252` (`REQUIRES_NEW` + `Isolation.READ_COMMITTED`), invoked from the `AFTER_COMMIT` listener `PlannerPublishingService.java:58-62`. Pinned by `NotificationFanoutIT:64` against real MySQL (eligible-only insert, author excluded, `deleted_at` excluded, dedup absorbs re-publish) and `NotificationServiceTest:65` (one repo call, `saveAll` never). Caveat: the relocation into the AFTER_COMMIT listener — which means notifications no longer persist when the publish rolls back — is not asserted anywhere (`PlannerPublishEventIT:83` covers only the SSE broadcast).
- **R3.3 — publish loads the aggregate once (internal `togglePublish` overload) — DONE.** `PlannerPublishingService.java:228-236` + overload at `:157`; `PlannerPublishingServiceTest:164` asserts neither `findAggregateForOwner` nor `findAggregate` is called after the upsert.
- **R3.4 — one classifying SELECT + count probe, all four rows preserved — DONE.** 404 own-soft-deleted (`PlannerCommandServiceTest:833`), 403 other-user-active (`:856`), created-vs-updated (`:878` + pre-existing update cases), `PlannerLimitExceededException(count,max)` (`:288`, pre-existing); both old probes asserted `never()` called. Projection validated against the migrated schema by `PlannerClassificationIT:48` (active case only; the soft-deleted projection is not exercised against real MySQL). Note a behavior not in the row: another user's *soft-deleted* row now matches neither branch and falls through to create, surfacing as a PK collision (documented at `PlannerCommandService.java:344-346`, untested).
- **R4 F1 — scheduler pool ≥ 4 + view-flush executor — DONE (config-level).** `ViewFlushSchedulerConfig.java:27,38`, `application.properties:168`, `PlannerViewRecorder.java:44`; `DegradeByOperationConfigTest:39`. See the row verdict for what the test does not prove.
- **R4 F2 — connectionTimeout on all 3 Seoul routing pools PLUS connect/socketTimeout on the APP JDBC url (not Flyway's) — DONE in code, HALF-TESTED.** `RoutingDataSourceConfig.java:79,88,100` (primary 5s, replica 5s, bulkhead 30s) — **no test**. `application-prod.properties:5` (app url) and `:16` (replica url) carry `connectTimeout`/`socketTimeout` defaults of 10000; Flyway url at `:6` left bare. `DegradeByOperationConfigTest:62` asserts exactly these three property strings — the only R4-F2 coverage, and it is text, not behavior.
- **R4 F3 — Lettuce command timeouts on the cross-region auth Redis — DONE in code, NOT TESTED.** `shared/config/RedisConnectionConfig.java:74,90-97` (3s `commandTimeout` on `authRedisConnectionFactory`). Grep over `backend/src/test` finds no assertion on `commandTimeout`/`getCommandTimeout`; `RedisFactoriesIT` checks bean distinctness only.
- **R5 SSE fan-out sweep — MOSTLY DONE.**
  - `publishSettingsInvalidation` wired at `UserController.java:166` — **not tested** (no `updateSettings` test exists).
  - `broadcastToAll` routed through the publisher: `PlannerPublishingService.java:60` → `SsePublisher.java:81` → `SseRedisSubscriber.java:52`; tested `SseFanoutIT:105`.
  - `notifyAccountSuspended` routed through the publisher: `ModerationService.java:92,159` → `SsePublisher.java:94`; tested `SseFanoutIT:122` + `ModerationServiceTest:460,502`.
  - `sse:broadcast` channel (`SseChannels.java:10`, subscribed `SseSubscriberConfig.java:46-48`), `ACCOUNT_SUSPENDED` enum (`SseEventType.java:19`), `excludeUserId` envelope field (`SseEnvelope.java:23,29`) — all present; channel + enum + field exercised end-to-end by the two `SseFanoutIT` cases.
  - `settingsCache` Caffeine TTL: `SseService.java:53-56` (5 min / 10k entries) — **no test** for expiry or bound.
  - D6 sweep assertion: `architecture/SseDispatchBoundaryTest.java:28` exists and matches the four dispatch method names; `grep` confirms no caller of those four outside `shared/sse`.
- **R6 — state-targeted publish + bookmark + deprecated legacy toggle handler with a usage counter — MOSTLY DONE.** State-targeted services + DTOs implemented and tested (rows 15/16); `@Deprecated` legacy handlers retained (`PlannerPublishingService.java:135-140`, `PlannerEngagementService.java:199-205`) and still return 200 (`PlannerPublishFlowIT:169`, both the content-carrying and bodyless legacy shapes). The **usage counter** (`planner.legacy_toggle`, `PlannerPublishingController.java:37,71`, `PlannerEngagementController.java:41,96`) has **no test**, and no test drives either endpoint with a state-naming body, so `PublishRequest.namesState()`/`carriesContent()` routing and `BookmarkRequest.namesState()` are unexercised at the HTTP seam.
- **R7 — `/api/internal` surface removed; `static` added to deploy-fleet trigger; legacy `sync-game-data.yml` retired — DONE.** Controller, test, `permitAll` matcher (`SecurityConfig.java:86`), CSRF prefix exemption (`CsrfDoubleSubmitFilter.java:113`), `internal.api-key` property, `docker-compose.yml` env, and the `setup-env.sh` SSM fetch are all gone; `sync-game-data.yml` deleted; `deploy-fleet.yml:18` gains `static/**`. Also part of this line: `LineageRotationFlag.java:18-25` is now immutable and fed by `JWT_ROTATION_LINEAGE_ENABLED` from both overlays (`deploy/overlays/{oregon,seoul}/configmap-patch.yaml`) — covered by `config/LineageRotationFlagTest.java`. Test quality caveat in the row verdict above; the SSM parameter deletion is an out-of-repo action with no evidence.
- **R8 — C7 capture-on-committed-tx — DONE.** `GtidCapturingTransactionManager.java:37` registers only on `!definition.isReadOnly()`; `GtidCookieFilter.java:41-51` opens/closes the window for every request regardless of method. Pinned by `CausalGateIT:130` (committed write mints) and `CausalGateIT:191` (Redis-only logout does not).
- **All existing backend + frontend suites pass — BACKEND UNIT TIER VERIFIED GREEN; the rest not run.** `backend/gradlew -p backend test -PexcludeTags=containerized` → `BUILD SUCCESSFUL in 45s`, 252 fresh result XMLs, 977 tests, `failures="0"` across all of them (both new ArchUnit tests, `InternalSurfaceRemovedTest` and `SseDispatchBoundaryTest`, among them). The containerized tier (`NotificationFanoutIT`, `PlannerClassificationIT`, `SseFanoutIT`, `CausalGateIT`, `DegradationIT`) needs Docker and was not run here; the frontend suite was not run. Runner quirk confirmed twice during this audit: `/home/user/github/LimbusPlanner/gradlew` does not exist (only `backend/gradlew`), and a wrong wrapper path exits 0 with no fresh XML — trust `BUILD SUCCESSFUL` plus XML timestamps, never the exit code.

Non-`local-tdd` Done-When lines, for the record: R1 terraform/cloudflare + cloudflared Deployments `(infra)` — **absent** (no `terraform/cloudflare` root, no cloudflared manifests); R1 staging E2E, R9 stop-the-world drill `(live)` — absent by design; R10 POST-BAKE teardown `(infra)` — absent by design (GA module still present, as the spec requires until after the bake).

#### Coverage holes

Genuine, non-deferred gaps:

1. **The OWN_GTID read path has zero coverage.** `GtidCommitSynchronization.readOwnGtid()` (the Hikari/`LazyConnectionDataSourceProxy` unwrap to `JdbcConnection` and the `SESSION_TRACK_GTIDS` scan) is never invoked by a test, and its failure mode is a silent `catch (Exception) → null` that degrades to `SELECT @@gtid_executed`. Because both paths mint a valid-looking cookie, no existing assertion can tell a working tracker from a permanently-failing unwrap — the central mechanism of R3.1/R11 is unfalsifiable as tested.
2. **The two-commit union is never exercised through a real transaction.** Nothing pins that `GtidCapturingTransactionManager` registers a synchronization per transaction, that the `REQUIRES_NEW` filter rebuild contributes its GTID, or that a read gated on the resulting cookie sees the rebuilt filter rows. This is INV2's stated verification and it rests on a hand-driven accumulator test.
3. **The connect-hang degradation case is untested.** Every existing degradation test cuts the primary by disabling the Toxiproxy proxy (refuse), not by blackholing it (hang) — precisely the distinction the row draws. The new JDBC/Hikari/Lettuce bounds that exist to make the hang case survivable are asserted only as property text or not at all (Hikari `connectionTimeout` ×3, Lettuce `commandTimeout`).
4. **No HTTP-level coverage of the new write endpoints.** `PublishRequest`/`BookmarkRequest` deserialization, `namesState()`/`carriesContent()` routing, the `@AssertTrue` group-validation on `PublishRequest`, and the `planner.legacy_toggle` counter are all untested; the idempotence proofs sit one layer below at the service. `PlannerPublishFlowIT` covers only the legacy shapes.
5. **`UserController.updateSettings` → `publishSettingsInvalidation` is unpinned.** The one wiring the R5 checklist calls out by name has neither a controller test nor an end-to-end IT; the ArchUnit sweep cannot distinguish "publishes" from "does nothing".
6. **`internal-endpoints-removed` is asserted as source text, and its stated status code looks wrong.** No request-level test; with `anyRequest().authenticated()` the surface now answers 401 rather than the row's 404. The `static/**` deploy trigger in the same row is untested.
7. **The Caffeine settings-cache TTL is untested** — the 5-minute expiry is the stated safety net for a missed invalidation (R5), and nothing asserts entries expire or that the 10k bound holds.
8. **No frontend change accompanies the state-targeted endpoints.** `frontend/src/pages/planner/lib/plannerApi.ts:127` still PUTs `/publish` with no body and `frontend/src/pages/planner/hooks/usePlannerBookmark.ts:52` still POSTs `/bookmark` with no body, so in production 100% of traffic takes the deprecated toggle path and the `legacy_toggle` counter can never fall to zero — D7's retirement condition is unreachable as shipped. Rows 15/16 are backend-scoped, so this is not a row failure, but INV4 ("a retried or failed-over mutation never double-applies") does not hold for real clients yet.

## Intuition

### Intuition audit — architectural drift

Scope note: the range contains **Half B only**. There is no `terraform/cloudflare` root, no cloudflared
manifest, no CF LB/pool/monitor resource, and `terraform/global-accelerator/main.tf` is untouched.
Every edge row (`edge-geo-steers`, `edge-survives-region-loss`, `inbound-closed-post-teardown`) and
D2/D3/D10 have no artifact in this diff. Everything below judges the write-path half.

---

### What the implementation actually does

**GTID capture moved from the filter to the transaction manager.** `GtidGateConfig` now declares its
own `PlatformTransactionManager` bean (`shared/gtid/GtidGateConfig.java:38`) — a
`GtidCapturingTransactionManager extends JpaTransactionManager` that overrides
`prepareSynchronization` and, for every new non-read-only transaction, registers a
`GtidCommitSynchronization` (`shared/gtid/GtidCapturingTransactionManager.java:33`). On `afterCommit`
that synchronization pulls the still-bound connection via `DataSourceUtils`, unwraps to
`com.mysql.cj.jdbc.JdbcConnection`, reads `SESSION_TRACK_GTIDS` off the commit's OK packet, and hands
the value to `GtidWriteCapture.recordCommit` (`shared/gtid/GtidCommitSynchronization.java:38-52`).
`GtidWriteCapture` keeps a per-thread `Accumulator` (committed flag, GTID set, missed flag) opened and
closed by `GtidCookieFilter` around **every** request, read or write
(`shared/gtid/GtidCookieFilter.java:41-52`). At poll time it merges the collected sets by source uuid,
coalescing adjacent ranges (`GtidWriteCapture.unionGtidSets`, `:129`); if any commit produced no
tracker value it discards the union and falls back to `SELECT @@gtid_executed`. Because the filter
polls on GETs too, a mutating `GET /google/callback` mints the cookie and a Redis-only `POST /logout`
does not. The bean is `@ConditionalOnProperty(datasource.routing.enabled)`, so Oregon keeps Boot's
auto-configured transaction manager and mints nothing — consistent with D4.

**Publish.** `PUT /{id}/publish` now binds a new flattened `PublishRequest`
(`planner/dto/PublishRequest.java`) whose `published` field decides the branch: named → the idempotent
`setPublished`/`setPublishedWithContent`; absent → a Micrometer counter tick plus the `@Deprecated`
toggle. `applyPublishedState` (`PlannerPublishingService.java:1086` in-diff) compares current state to
requested, returns untouched when equal (after an ownership check), otherwise delegates to a new
`togglePublish(Long, Planner)` overload that takes an already-loaded aggregate.
`publishWithContent`/`setPublishedWithContent` obtain that aggregate from
`PlannerCommandService.upsertAggregate`, a new public method returning an `UpsertedPlanner(planner,
response, created)` record, so the publish path loads the aggregate once instead of three times.

**Fan-out.** The first-publish path no longer writes notification rows inline. It publishes a
`PlannerPublishedEvent(authorId, plannerId, title, data)`; the `AFTER_COMMIT` listener first calls
`ssePublisher.publishBroadcast(...)` and then `notificationService.notifyPlannerPublished(...)`, which
is `@Transactional(REQUIRES_NEW, READ_COMMITTED)` and issues one
`INSERT IGNORE INTO notifications ... SELECT FROM user_settings JOIN users`
(`NotificationRepository.insertPublishedFanout`). That second commit is the one the GTID union exists
to cover.

**Create classification.** The two existence probes collapse into one
`findClassificationById` projection (`p.user.id`, `c.deletedAt`); owner → 404, other-user-active →
403, anything else falls through to create.

**SSE.** All four local-dispatch methods (`sendToUser`, `broadcastToAll`, `notifyAccountSuspended`,
`invalidateSettingsCache`) are now reachable only from `shared.sse`; call sites publish. A new
`sse:broadcast` channel, `ACCOUNT_SUSPENDED` enum constant, and `excludeUserId` envelope field carry
broadcasts and suspensions across pods; `SseEventType.deliversRawPayload()` decides whether the
subscriber hands the client the envelope or the bare payload, preserving the pre-existing wire shape
for notification events. `settingsCache` becomes a Caffeine cache with a 5-minute write TTL and a
10k cap. An ArchUnit rule freezes the boundary.

**Degradation.** Two `ThreadPoolTaskScheduler` beans (`@Primary` shared, pool 4; a dedicated
single-thread `viewFlushScheduler` named on `PlannerViewRecorder.flush`), `connectionTimeout` on all
three Hikari pools (5s request / 30s bulkhead), a 3s Lettuce command timeout on the auth Redis, and
`connectTimeout`/`socketTimeout=10000` on the app + replica JDBC urls with Flyway's left unbounded.

**Ops.** `InternalController`, its permitAll matcher, the CSRF path exemption, the property, the
compose env var, and the SSM fetch are gone; `LineageRotationFlag` becomes immutable and
config-driven; `sync-game-data.yml` is deleted and `static/**` added to `deploy-fleet.yml` triggers.

---

### Divergences from intent

**1. The idempotent API has no caller — INV4 is not achieved in the running system.**
*Asked:* D7/R6 — state-targeted publish + bookmark, with the deprecated toggle kept only for tabs on a
previously cached bundle and "removed at ~0 measured usage".
*Code:* the range touches **zero frontend files**. `frontend/src/pages/planner/hooks/usePlannerPublish.ts:62`
still sends `ApiClient.put('/api/planner/md/{id}/publish')` with no body;
`frontend/src/pages/planner/hooks/usePlannerBookmark.ts:52` still sends a bodyless POST;
`frontend/src/pages/planner/lib/plannerApi.ts:120-128` is still documented and implemented as a toggle.
Every production request therefore takes the legacy branch, ticks `planner.legacy_toggle`, and
double-applies on retry exactly as before. The retirement signal is inverted: the counter can never
approach zero, so the deprecated handler is permanent by construction.
*Correct version:* ship the FE mutations sending `{published: <target>}` / `{bookmarked: <target>}`
derived from the client's known state in the same change; the counter then measures stale bundles, which
is what D7 assumes it measures. Until that lands, R6 is a new unused surface, not a fix.

**2. `static/**` cannot fire for a submodule bump — D8's replacement shipping path is dead.**
*Asked:* D8/R7 — "game-data updates ship because `static` is in `deploy-fleet.yml` on.push.paths".
*Code:* `static` is a gitlink (`.gitmodules`; `git ls-files -s static` → mode `160000`). A commit that
bumps the pointer changes the path **`static`**, not any path under `static/`. The deleted workflow
used exactly `paths: - 'static'` (diff line 25) for this reason; the replacement uses `- 'static/**'`
(`.github/workflows/deploy-fleet.yml:19`), which matches nothing a pointer bump produces. With
`sync-game-data.yml` deleted in the same commit, a game-data update now ships **only** if a
`backend/**` file happens to change too.
*Correct version:* `- 'static'` (optionally both entries). This is the single highest-value one-line
fix in the diff, and it makes INV7's "behavior-preserving" claim false in the operational sense.

**3. The publish broadcast now fires before the notification rows exist.**
*Asked:* nothing — this is incidental to the fan-out move.
*Code:* `onPlannerPublished` calls `ssePublisher.publishBroadcast(...)` and only then
`notificationService.notifyPlannerPublished(...)` (diff `PlannerPublishingService`, listener body).
Previously the rows were written **inside** the publishing transaction and the broadcast fired after
commit, so a client that reacted to `notify:published` by refetching its inbox always found the row.
Now the broadcast races a not-yet-started `REQUIRES_NEW` transaction that writes cross-region.
*Correct version:* swap the two statements. Cheap, and it restores the prior happens-before.

**4. The OWN_GTID read — the actual point of R3.1 — is untested and fails silently.**
*Asked:* R3.1/D11 — reach ~1 round trip via `session_track_gtids=OWN_GTID`, with the
`@@gtid_executed` fallback intact.
*Code:* `GtidWriteCaptureTest` exercises the accumulator against a **mocked** `JdbcTemplate`;
`GtidCommitSynchronization` — the class that does the Hikari + `LazyConnectionDataSourceProxy` unwrap
and the tracker read — has no test at all. `CausalGateIT` proves registration works (a write that
recorded no commit would mint no cookie), but if the unwrap or the tracker read throws,
`readOwnGtid()` returns null, `missedGtid` flips, and the request falls back to `SELECT
@@gtid_executed` — the pre-change behavior, with the extra round trip R3.1 exists to remove — and
**every test still passes**. The suite cannot distinguish "OWN_GTID works" from "OWN_GTID never works".
*Correct version:* one containerized assertion that a write's cookie is the narrow per-commit set and
not the global one (e.g. compare the cookie against `@@GLOBAL.gtid_executed` after seeding unrelated
transactions), plus one asserting the two-commit publish union spans both. The harness already sets
`--session-track-gtids=OWN_GTID` (`CausalHarnessSupport.java:73`), so the fixture cost is near zero.

Related, smaller: `afterCommit` calls `capture.recordCommit(readOwnGtid())` unconditionally
(`GtidCommitSynchronization.java:39`) — the connection fetch, unwrap, and tracker scan run on every
commit in the process, including scheduled and listener threads where the class doc says "commits on
threads with no open window are ignored". Guarding on an open window would make the doc true and drop
the work from the 500ms flush path.

**5. `PublishRequest` re-encodes `UpsertPlannerRequest`'s constraints — a second validation oracle.**
*Asked:* R6 — a body naming `published`, optionally carrying content.
*Code:* `PublishRequest` flattens all nine upsert fields and reimplements their Jakarta constraints as
a hand-rolled `@AssertTrue isContentPayloadComplete()` (`planner/dto/PublishRequest.java:761`), because
field-level annotations would reject a state-only request. The mirror is currently faithful, but it is
exactly the "fork the oracle" failure mode D11 rejects for the aggregate write, at DTO scale: adding a
constraint to `UpsertPlannerRequest` now silently un-validates the publish path. It also collapses
per-field messages ("ID is required", "Content version must be positive") into one generic
"Content payload is incomplete", changing the error payload the FE sees.
*Correct version:* `record PublishRequest(Boolean published, @Valid UpsertPlannerRequest content)` —
Jakarta cascades through `@Valid` on the nested field, and an absent `content` skips it entirely. One
oracle, no `toUpsertRequest()` copier, field-level messages preserved. Backend CLAUDE.md explicitly
calls out `@Valid` on nested DTO fields as the supported mechanism.

**6. Both schedulers are hand-rolled, bypassing the builder that carries `spring.task.scheduling.*`.**
*Asked:* R4 F1 — scheduler pool ≥ 4 plus a dedicated view-flush executor.
*Code:* `ViewFlushSchedulerConfig` `new ThreadPoolTaskScheduler()` twice and sets pool size from
`@Value("${spring.task.scheduling.pool.size:4}")`. The class doc correctly identifies that declaring
either bean backs `TaskSchedulingAutoConfiguration` off — and then re-reads only one of the properties
that auto-configuration would have applied. `spring.task.scheduling.shutdown.await-termination`,
`await-termination-period`, and `thread-name-prefix` become dead configuration keys, and the pool-size
default is now stated twice (here and `application.properties`), against the "never inline hardcode"
rule.
*Correct version:* inject Boot's `ThreadPoolTaskSchedulerBuilder` and `.build()` both beans, overriding
only pool size and thread prefix on the flush one. Also worth noting: `backend/.../CLAUDE.md` says
"Deliberately NO ... `ThreadPoolTaskExecutor` anywhere ... do not introduce thread pools" — a second
scheduler pool is arguably inside the sanctioned `@Scheduled` model, but the doc now under-describes
the system and should be amended rather than left to contradict the code.

**7. `internal-endpoints-removed` verifies a source grep, not the routed status.**
*Asked:* "given any request to `/api/internal/*`, when routed, then 404".
*Code:* `InternalSurfaceRemovedTest` walks `src/main/java` and `src/main/resources` looking for the
literal string. Nothing exercises the route. And the row's observable is not actually true: with the
`permitAll` matcher gone, `SecurityConfig:110`'s `anyRequest().authenticated()` returns **401** to an
unauthenticated caller — 404 only reaches an authenticated one.
*Correct version:* keep the grep as a ratchet, add a two-line MockMvc assertion for the real status,
and correct the row's expected code (or amend the spec) so the recorded observable matches reality.

**8. Classification join narrows one case, and `INSERT IGNORE` widens another.**
`findClassificationById` inner-joins `p.content` (`PlannerRepository.java:88`), so a planner row whose
content satellite is missing classifies as absent and falls through to create → PK collision (500),
where `existsByIdAndUserId` previously returned 404. Post-043 that should be unreachable, but it is a
narrowing the row does not ask for. Separately, `INSERT IGNORE` downgrades *all* errors to warnings,
not just the dedup constraint: an over-length `plannerTitle` or a truncation now silently fans out to
nobody. `NotificationFanoutIT` covers the happy path and the dedup, not the swallowed-error path.

**9. Residue.** `PlannerRepository.existsByIdAndUserId` and
`UserSettingsRepository.findUserIdsWithNewPublicationsEnabled` now have no callers — dead declarations
left behind by items the diff replaced. `LEGACY_TOGGLE_COUNTER = "planner.legacy_toggle"` is declared
privately in **both** controllers (duplicate constant, should be one shared constant).
`currentUpvotes` is now a third copy of the same three-line lookup, and `publishWithContent`'s tail
still inlines it rather than calling the new private helper. `togglePublish(Long, Planner)` is `public`
though the row asks for an "internal … overload"; package-private would keep the seam closed.
`BookmarkRequest` is bound without `@Valid`. `SseService.notifyAccountSuspended` and
`SsePublisher.publishAccountSuspended` use fully-qualified inline `org.danteplanner...SseEventType` /
`java.util.Map` references instead of imports.

**Approaches I looked for and did not find drifting.** No rejected alternative crept in: there is no
edge write-forward (D1), no capture/read-gate split (D4), no in-process-only SSE path and no publish
inside a dispatch method (D6), no stored procedure for the aggregate write (D11), and Traefik/origin-tls
are untouched (D3). Putting capture on the transaction manager rather than in services is the right
seam — it is the one place every `REQUIRES_NEW` listener transaction must pass, and it wins the union
without a single service edit. The `@Primary` + explicit-`taskScheduler` interaction with
`TaskSchedulingAutoConfiguration` is reasoned correctly (the auto-config bean is
`@ConditionalOnMissingBean(TaskScheduler.class)`, so declaring only the flush scheduler would indeed
have silently single-threaded everything), and `JpaTransactionManager.setDataSource` matches the
`@Primary` `LazyConnectionDataSourceProxy` the EMF is built on, so `DataSourceUtils.getConnection` in
`afterCommit` returns the transaction's own bound connection rather than checking a fresh one out.

> **This last claim is false.** A `ConnectionHolder` is bound and the DataSource does match, but under
> `JpaTransactionManager` Hibernate takes its connection from the `EntityManager`, so the lazy
> handle in that holder is never materialised — unwrapping it checks out a fresh pooled connection
> carrying none of the committed transaction's session state. Capture therefore never reached the
> OWN_GTID tracker and silently ran on the `@@gtid_executed` superset. The mechanism now in the tree
> intercepts `commit()` on the connection itself; see `docs/multi-region-request-paths.md` §4a.

---

### Invariant enforcement

| | Verdict | Basis |
|---|---|---|
| **INV1** cross-region hang never fails reads or exhausts threads | **Config-asserted, not behavior-verified** | `DegradeByOperationConfigTest` parses `application-prod.properties` for `socketTimeout`/`connectTimeout` and asserts pool sizes on an `ApplicationContextRunner` — it proves the strings and bean shapes, not that reads survive a blackhole. Hikari `connectionTimeout` and the Lettuce 3s bound have no assertion at all. The drill remains the only real evidence. New small hazard: the unconditional `DataSourceUtils.getConnection` in `afterCommit`. |
| **INV2** client sees its own just-committed write, including post-main-tx work | **Partially enforced** | Union logic covered by `GtidWriteCaptureTest` against a mocked template; registration + cookie minting covered end-to-end by `CausalGateIT`; the two-commit publish union and the OWN_GTID-vs-global distinction are covered by nothing (divergence 4). The fallback guarantees correctness, so the invariant holds even if the mechanism is inert — but then R3.1 bought nothing. |
| **INV3** no SSE dispatch reaches only the local pod | **Enforced** | `SseDispatchBoundaryTest` (ArchUnit, non-vacuous, fails on empty) plus three cross-pod `SseFanoutIT` cases. Caveat: the rule is a name allowlist — a future `sendToPlanner`/`pushTo…` on `SseService` escapes it silently. A rule keyed on "public methods of `SseService`/`AbstractSseService` that touch emitters" would be tighter. |
| **INV4** a retried or failed-over mutation never double-applies | **Not achieved** | Service-level idempotence is real and unit-tested (`PlannerPublishingServiceTest.SetPublishedTests`, `PlannerEngagementServiceTest.SetBookmarkTests`), but no client sends the state-targeted shape (divergence 1). In production every publish and bookmark still flips. |
| **INV5** no public inbound SG rule post-teardown | **Nothing in range** | No terraform change; GA module still present. |
| **INV6** Seoul publish bounded, no per-recipient multiplier | **Enforced for the SQL, not the placement** | `NotificationFanoutIT` proves one statement, correct eligibility filtering, and dedup against real MySQL; `NotificationServiceTest` proves `saveAll` is gone. Nothing asserts the fan-out runs `AFTER_COMMIT`/`REQUIRES_NEW`/`READ_COMMITTED` — that lives only in annotations. Note the listener runs on the request thread, so the row's "the request thread does not block" is satisfied only in the bounded sense (one statement, not N). |
| **INV7** `/api/internal` removal is behavior-preserving | **Enforced for the code, broken for the operation** | `InternalSurfaceRemovedTest` holds the surface closed; `setup-env.sh`, `docker-compose.yml`, and the property are clean. But the game-data path that was supposed to replace it does not trigger (divergence 2), so removal is not behavior-preserving in prod until `static/**` is fixed. |
| **INV8** redis-auth wipe forces no mass re-login | **Unchanged / pre-existing** | Nothing in range touches `RefreshRotationService` or its rotation-Lua test; the invariant rests on prior work. |
| **INV9** jwt-key rotation reaches both regions | **Operational, nothing to enforce** | Recorded as procedure; no code artifact expected or present. |

## Findings

### Data, transactions, and concurrency

### Correctness audit — SQL, transactions, concurrency, framework wiring

Scope: GTID capture, scheduler wiring, native fan-out, classifying SELECT, caches/pools.
Verified against Spring Framework 6.2.15, Spring Boot 3.5.10, ShedLock 5.16.0 (bytecode inspected in
`~/.gradle/caches`), Flyway migrations V019/V024/V028/V029/V042/V049, and the runtime routing config.

---

#### 1. BLOCKER — a committed transaction that wrote nothing mints a cookie for the *entire* primary's `@@gtid_executed`, pinning that client's subsequent reads to Oregon

`backend/src/main/java/org/danteplanner/backend/shared/gtid/GtidWriteCapture.java:66-77`
(`recordCommit`) and `:82-94` (`pollCapturedGtid`), fed by
`GtidCapturingTransactionManager.java:38-46`.

The predicate the spec asks for (D5, row `ryw-no-cookie-on-redis-only-write`) is "a non-readOnly
**MySQL write** committed". The predicate implemented is "a non-readOnly **transaction** committed":

```java
acc.committed = true;                      // set unconditionally
if (StringUtils.hasText(ownGtid)) { ... } else { acc.missedGtid = true; }
```

InnoDB assigns a GTID only to transactions that *modify* data. A `@Transactional` (not
`readOnly=true`) method that only SELECTs commits with an **empty** `session_track_gtids` tracker →
`recordCommit(null)` → `committed=true, missedGtid=true` → `pollCapturedGtid()` falls through to
`readGlobalGtidExecuted()` → `SELECT @@gtid_executed` **on the primary** → the cookie carries the
whole server's executed set, i.e. every other user's writes.

Concrete failure, entirely inside this diff's own idempotency work:

1. Seoul pod. User has already bookmarked planner P.
2. Client retries `POST /api/planner/md/planners/{P}/bookmark` with `{"bookmarked": true}`
   (`PlannerEngagementService.setBookmark`, `PlannerEngagementService.java:162-186`). The early-return
   branch runs one SELECT (`findPublishedAggregate`) and returns — the transaction commits having
   written nothing.
3. `afterCommit` fires, tracker empty, `missedGtid=true`.
4. Filter polls → extra cross-region `SELECT @@gtid_executed` on the Oregon primary (~1 RTT the
   request did not need) → `Set-Cookie: ryw_gtid=<global set>`.
5. Every subsequent GET runs `GtidReadGate.isCaughtUp(globalSet)` with a **50 ms** probe bound
   (`GtidReadGate.java:28`). A Seoul replica that lags the *global* stream by more than 50 ms fails
   the probe, so the filter pins the whole request to `RoutingKey.PRIMARY` — cross-region — and the
   cookie is **not** cleared, so it re-arms on the next read. Replica routing (R2/D4) is off for that
   client until the replica happens to be within 50 ms of the global position at poll time.

Identical path via `PlannerPublishingService.setPublished` when the planner is already in the
requested state (`PlannerPublishingService.java:113-121`), i.e. exactly the retry case D7/INV4 exists
to make cheap. It is now the most expensive shape of the request.

Fix direction: only mark `committed` when a GTID was actually produced, or gate the fallback on
"this request executed a DML statement", not on "a transaction object committed".

**No test covers this.** `GtidWriteCaptureTest.rywNoCookieOnRedisOnlyWrite_WhenNoTxCommitted_ReturnsEmpty`
(diff line 3271) asserts the *no transaction at all* case; `CausalGateIT`'s logout test likewise only
proves logout opens no transaction. Neither exercises "a transaction committed but wrote nothing" —
the test passes for the wrong reason relative to the row it is named after.

---

#### 2. MAJOR — the ryw cookie on SSE subscribe is written to an already-committed response, from the container thread, after `startAsync`

`GtidCookieFilter.java:40-52`.

`writeCapture.begin()` / `pollCapturedGtid()` now run for **safe methods too**. `GET /api/sse/subscribe`
(`shared/controller/SseController.java:42`) and `GET /api/planner/md/events`
(`planner/controller/PlannerSseController.java:42`) are GETs under the filter's `/api/*` pattern, and
they commit a non-readOnly transaction inside the window:
`SseService.subscribe` → `register` → `afterRegister` (`SseService.java:245`) →
`cacheSettingsIfAbsent` → `userSettingsService.getOrCreateEntity` — `@Transactional`, **not** readOnly
(`UserSettingsService.java:90`).

So every SSE (re)connect trips finding #1 *and* then calls
`response.addHeader(SET_COOKIE, …)` after `ResponseBodyEmitterReturnValueHandler` has already called
`startAsync` and flushed the early `connected` event inside `filterChain.doFilter`. Two outcomes,
both defects:

- the response is already committed → the header is silently dropped (the cookie the code believes
  it minted does not exist); or
- it is not yet committed → the container thread mutates response headers concurrently with the
  async thread that owns the response after `startAsync`, which the Servlet spec does not define.

Which one you get depends on buffer size and event size — it is environment-dependent, and the
`GtidCookieFilterTest` MockMvc-style unit test (`MockFilterChain`, no async) cannot see either.

The filter should skip the poll/`addCookie` when `request.isAsyncStarted()`.

---

#### 3. MAJOR — the AFTER_COMMIT fan-out inverted the SSE/DB ordering, and its failure now 500s a publish that already succeeded

`PlannerPublishingService.java:57-63` (`onPlannerPublished`) and
`NotificationService.java:252-258`.

Before: `notifyPlannerPublished` ran **inside** `togglePublish` (REQUIRES_NEW, committed before the
outer commit); the SSE broadcast fired at AFTER_COMMIT. Rows existed before the event.
After: both run in the AFTER_COMMIT listener, and the broadcast is published **first**:

```java
ssePublisher.publishBroadcast(...);                 // line 58
notificationService.notifyPlannerPublished(...);    // line 60
```

Failure scenario: pod A publishes; a subscriber on pod B receives `notify:published` over Redis
within a few ms and the SPA immediately calls `GET /api/notifications/unread-count`. The fan-out
`INSERT IGNORE … SELECT` (a full scan of `user_settings ⨝ users`, cross-region from Seoul) has not
committed yet → the badge shows 0, and nothing re-fires. Swap the two calls.

Second, worse half: an exception from `insertPublishedFanout` (deadlock, lock-wait, the 10 s
`socketTimeout` from §7 firing on a slow scan) propagates out of the synchronization.
`AbstractPlatformTransactionManager.processCommit` wraps `triggerAfterCommit` and lets the exception
reach the caller *with the transaction already committed*
(`TransactionalApplicationListenerSynchronization` rethrows after its callbacks). The user gets a 500
for a publish that is durably applied and whose SSE broadcast already went out. Previously the same
failure rolled the publish back. Nothing in the diff catches it.

---

#### 4. MAJOR — the 5-minute Caffeine TTL turns SSE dispatch into a periodic cross-region DB read per connected user

`SseService.java:56-58` + `:281-286`.

`settingsCache` gained `expireAfterWrite(5m)`. The loader is
`userSettingsService.getOrCreateEntity(id)` — `@Transactional` **without** `readOnly=true`, so
`ReadOnlyRoutingDataSource.determineCurrentLookupKey()` returns `RoutingKey.PRIMARY`
(`ReadOnlyRoutingDataSource.java:31-36`). On a Seoul pod that is the **Oregon** primary.

Failure scenario: 2 000 users hold SSE streams on a Seoul pod. A publish broadcast arrives on the
Redis channel; `SseService.broadcastToAll` (`SseService.java:152-170`) calls `isEventAllowed` per
user. Any user whose entry aged past 5 minutes reloads → a cross-region `SELECT` on the primary
pool, which is sized `PoolLedger.SEOUL_PRIMARY_POOL` (10) with a 5 s `connectionTimeout`
(`RoutingDataSourceConfig.java:79`). `RedisMessageListenerContainer` dispatches each message on its
own executor thread, so the reloads are not serialized — they contend for the 10-slot primary pool
against real writes, and callers that lose start failing at 5 s with `SQLTransientConnectionException`.

Two things make this avoidable: the read is not marked `readOnly`, so it never uses the local Seoul
replica; and the reload is *unconditional on age* rather than refresh-ahead. `refreshAfterWrite`
plus a `readOnly=true` settings read would keep both properties.

---

#### 5. MAJOR — `socketTimeout=10000` on the shared app JDBC URL also bounds every scheduled bulk statement

`backend/src/main/resources/application-prod.properties:5` (and `:16` for the replica).

The 10 s socket timeout is correct for request-serving writes (R4/D9), but it is a **connection**
property on the datasource every `@Scheduled` job shares. Statements that legitimately exceed 10 s:

- `NotificationService.cleanupOldNotifications` (`NotificationService.java:323`, cron 02:00) — a bulk
  soft-delete/purge over `notifications`, which has no covering index for the retention predicate
  beyond `idx_notifications_created`.
- `PlannerDriftReconciler.runScheduled` (cron 04:00) and `UserCleanupScheduler.cleanupExpiredUsers`
  (cron 03:00).
- the V053 `rebuild_planner_filters` procedure.
- `NotificationRepository.insertPublishedFanout` itself once `user_settings` grows —
  `notify_new_publications` has no index (V024), so it is a full scan joined to `users`.

Failure scenario: `notifications` reaches a few million rows; the 02:00 purge takes 12 s. The driver
aborts the socket at 10 s and throws `CommunicationsException`; MySQL keeps executing server-side
until it notices the dead connection, then rolls back. The job never completes on any night, the
ShedLock `lockAtLeastFor=PT30S` is released, and the table grows without bound — silently, because
the exception is logged and the next tick just repeats it.

`DegradeByOperationConfigTest` (diff 2234-2340) pins the *value* but only checks Flyway is exempt; it
encodes no carve-out for the batch path. Give scheduled/bulk work its own datasource or a
per-statement `queryTimeout` instead of a global socket bound.

Related, same file: `spring.flyway.url` is exempt from `socketTimeout` — correct — but it is also
exempt from `connectTimeout`. If the Oregon primary is blackholed at pod start, Flyway's connect
hangs indefinitely and the pod never reaches readiness. `connectTimeout` and `socketTimeout` are
independent; only the latter needs to be unbounded for a long migration.

---

#### 6. MINOR — `unionGtidSets` crashes the response on any GTID shape it does not expect

`GtidWriteCapture.java:118-133` (`unionGtidSets`) and `:135-142` (`parseInterval`).

`parseInterval` calls `Long.parseLong` on whatever follows a `:`. `pollCapturedGtid` has **no**
try/catch around `unionGtidSets` (unlike `readGlobalGtidExecuted`, which catches `DataAccessException`),
so a `NumberFormatException` escapes `doFilterInternal` after the write has committed → 500 with no
cookie, on a durably applied write.

Concrete trigger: MySQL 8.4 **tagged GTIDs** serialise as `UUID:TAG:NUMBER`. On an RDS 8.4 primary the
OWN_GTID tracker hands back `3e11fa47-…:mytag:100`; `parts[1]` is `"mytag"` and
`Long.parseLong("mytag")` throws. Same for a value like `ANONYMOUS`.

Two lesser holes in the same method:
- a source set with no interval (`"uuid"`, `parts.length == 1`) produces a `TreeMap` entry with an
  empty interval list, and the writer emits a bare uuid with no `:n-m` — an invalid `gtid_set` that
  `WAIT_FOR_EXECUTED_GTID_SET` rejects on the *next* request.
- the uuid is a raw `TreeMap` key, so `3E11FA47-…` and `3e11fa47-…` become two entries for the same
  server and the union is silently wider than it should be.

Adjacency, overlap, multi-uuid and unsorted input are otherwise handled correctly by
`coalesce` (`interval[0] <= last[1] + 1`). `GtidWriteCaptureTest` covers only the two-adjacent-points
case; multi-uuid, overlap, unsorted and malformed input are untested.

---

#### 7. MINOR — `readOwnGtid()` runs on every commit whether or not a window is open, and its first call is outside the guard

`GtidCommitSynchronization.java:44-46` / `:48-51`.

```java
public void afterCommit() { capture.recordCommit(readOwnGtid()); }
```

Java evaluates the argument first, so the `DataSourceUtils.getConnection` → `unwrap` →
`getSessionStateChanges` sequence runs for **every** non-readOnly commit in the JVM — including
`PlannerViewRecorder.flush` twice a second forever, the nightly crons, and the REQUIRES_NEW fan-out —
and the result is then dropped by `recordCommit`'s `if (acc == null) return`. Check the window first.

Separately, `DataSourceUtils.getConnection(dataSource)` sits **outside** the `try` (line 49), so its
`CannotGetJdbcConnectionException` is not covered by the `catch (Exception e)` fallback three lines
below. In the normal case the transaction's `ConnectionHolder` is still bound so it cannot throw, but
the whole point of that catch is "an unexpected pool/driver shape" — the one line that can actually
surface such a shape is not inside it. Move the acquisition into the `try`.

---

#### 8. MINOR — `INSERT IGNORE` silently truncates a long planner title where the previous path errored

`NotificationRepository.java:83-96`.

`planner_content.title` is `VARCHAR(255)` (V049:26); `notifications.planner_title` is `VARCHAR(100)`
(V029). `UpsertPlannerRequest.title` carries no `@Size`. Previously `saveAll` of `Notification`
entities hit "Data too long for column 'planner_title'" in strict mode →
`DataIntegrityViolationException`. `INSERT IGNORE` downgrades that to a warning and writes a
truncated 100-char title. Arguably the better outcome, but it is now silent and unasserted; if the
truncation is intended, `LEFT(:plannerTitle, 100)` states it.

---

#### Areas examined and found correct

**Scheduler wiring (`ViewFlushSchedulerConfig`) — clean; all 7 `@Scheduled` methods resolve as
intended.** Verified in bytecode, not from docs:
`TaskSchedulingAutoConfiguration.taskScheduler` is `@ConditionalOnMissingBean(TaskScheduler.class …)`,
so declaring either bean backs it off — the header comment is accurate.
`TaskSchedulerRouter.determineDefaultScheduler` calls `resolveSchedulerBean(bf, TaskScheduler.class,
false)` → `resolveNamedBean`, which honours `@Primary`, so the default is the 4-thread
`taskScheduler`; `determineQualifiedScheduler` uses
`BeanFactoryAnnotationUtils.qualifiedBeanOfType(bf, TaskScheduler.class, qualifier)`, which matches by
bean name, so `@Scheduled(scheduler = "viewFlushScheduler")` binds. The `@Value("${spring.task.scheduling.pool.size:4}")`
read is honoured (`application.properties` sets 4). Final placement:

| method | scheduler |
|---|---|
| `SseService.sendHeartbeats` (10 s) | `taskScheduler` (pool 4) |
| `SseService.cleanupZombieConnections` (60 s) | `taskScheduler` |
| `PlannerCommentSseService.sendHeartbeats` (10 s) | `taskScheduler` |
| `PlannerCommentSseService.cleanupZombieConnections` (60 s) | `taskScheduler` |
| `NotificationService.cleanupOldNotifications` (cron 02:00) | `taskScheduler` |
| `UserCleanupScheduler.cleanupExpiredUsers` (cron 03:00) | `taskScheduler` |
| `PlannerDriftReconciler.runScheduled` (cron 04:00) | `taskScheduler` |
| `PlannerViewRecorder.flush` (500 ms) | `viewFlushScheduler` (pool 1) |

ShedLock is unaffected: `@EnableSchedulerLock`'s `interceptMode` default is `PROXY_METHOD`
(annotation default read from the class file), not `PROXY_SCHEDULER`, so it proxies the annotated
beans rather than the `TaskScheduler` — the second scheduler does not escape locking.

**Transaction-manager override — correctly wired.** `JpaTransactionManager.afterPropertiesSet()`
copies `jpaDialect` and `dataSource` off the `EntityManagerFactoryInfo` proxy (bytecode confirmed), so
the custom manager gets `HibernateJpaDialect`, not `DefaultJpaDialect` — meaning
`@Transactional(isolation = READ_COMMITTED)` on the fan-out does **not** hit
`InvalidIsolationLevelException`. `HibernateJpaVendorAdapter` forces
`hibernate.connection.handling_mode = DELAYED_ACQUISITION_AND_HOLD` (bytecode confirmed), so the
physical connection is still the committing one when `afterCommit` reads the tracker, and
`isSameConnectionForEntireSession` lets the dialect prepare the isolation level.
`prepareSynchronization` registers correctly: `super` first (so `initSynchronization()` has run),
`isNewSynchronization()` excludes participating REQUIRED transactions, `isActualTransactionActive()`
excludes empty SUPPORTS/NOT_SUPPORTED transactions, and REQUIRES_NEW gets its own registration —
including the one started from inside the outer's `afterCommit`, whose resources are suspended and
rebound correctly. Registration-order + stable sort put the main transaction's GTID before the
listener transaction's, giving commit order.

**Thread-lifecycle / dispatch of the accumulator — no leak.** `OncePerRequestFilter.skipDispatch`
returns true for ASYNC dispatch (`shouldNotFilterAsyncDispatch` default true), so `begin()` is not
called there and nothing is left behind. ERROR dispatch re-enters `doFilterInternal` (the
already-filtered attribute is removed in the first pass's `finally`), so it opens a fresh window and
closes it — the only consequence is that a first-pass accumulation is discarded, and no cookie is
minted from an error dispatch. Commits on scheduler and Redis-subscriber threads find no window.
`handleRead`'s `ReadOnlyRoutingDataSource` pin is cleared before the poll runs, so the fallback
`SELECT @@gtid_executed` correctly resolves to `RoutingKey.PRIMARY` (outside a transaction,
`isCurrentTransactionReadOnly()` is false) rather than reading the replica's own position — which
would have broken the gate outright. Rollback records nothing.

**Fan-out SQL vs. the real schema — correct.** Column list matches V019+V028+V029; `created_at`
falls to the `DEFAULT CURRENT_TIMESTAMP(6)` from V042 and `read` to `DEFAULT FALSE`, both NOT NULL and
both omitted deliberately. `UUID_TO_BIN(UUID())` is non-deterministic and evaluated per row, so
`uk_notification_public_id` is satisfied. `UUID_TO_BIN(:plannerId)` produces the same big-endian 16
bytes Hibernate writes for a `java.util.UUID` on `BINARY(16)`, matching how V028 itself backfilled.
The WHERE clause is a faithful transcription of the retired
`UserSettingsRepository.findUserIdsWithNewPublicationsEnabled` (notify flag, `u.deleted_at IS NULL`,
author excluded). `uk_notification_dedup (user_id, content_id, notification_type)` with
`content_id = plannerId.toString()` absorbs a re-publish, so two concurrent publishes of the same
planner cannot double-insert. `Isolation.READ_COMMITTED` is load-bearing and correct: under the
default REPEATABLE READ, `INSERT … SELECT` takes next-key shared locks across `user_settings` and
`users`, which would block registration and settings updates fleet-wide for the duration of the scan.
(One undocumented dependency: this requires `binlog_format=ROW`. Under `STATEMENT`, MySQL rejects a
READ COMMITTED session outright and the fan-out would throw inside the AFTER_COMMIT listener — see
finding #3. Worth an assertion or a note.)

**Classifying SELECT — all four combinations preserved.** Old flow:
`existsByIdAndUserId` (owner, any delete state) → 404; `existsActiveById` (any owner, not deleted) →
403; else create. New flow over `SELECT p.user.id, c.deletedAt FROM Planner p JOIN p.content c WHERE p.id = :id`:

| case | old | new | same |
|---|---|---|---|
| owner, soft-deleted | 404 `PlannerNotFoundException` | userId matches → 404 | yes |
| owner, active | unreachable (`findAggregateForOwner` already returned) | unreachable | yes |
| other user, active | 403 `PlannerForbiddenException` | userId differs, `deletedAt == null` → 403 | yes |
| other user, soft-deleted | falls through → PK collision on save | falls through → PK collision on save | yes |
| absent | create | `Optional.empty()` → create | yes |

The `PlannerLimitExceededException(count, max)` probe and the created-vs-updated split in
`UpsertResult` are untouched. `c.deletedAt` reads the same `planner_content.deleted_at` the old
`existsActiveById` filtered on. The `JOIN p.content` is an INNER join where
`existsByIdAndUserId` was not, so a planner core without a content row would classify as absent
rather than as the owner's — but `PlannerContent` is `optional = false` with `CascadeType.ALL`, and the
only place content is deleted without the core (`UserAccountLifecycleService.java:195`) deletes both
in one transaction, so no orphan is durable. The other-user-soft-deleted row (a 500, not a typed
error) is preserved-not-introduced but is claimed only in a comment — no test asserts it, and
`PlannerClassificationIT` covers only the owner/active projection.

**Publish load-once, idempotent publish/bookmark, `PublishRequest` validation.**
`publishWithContent` and `setPublishedWithContent` reuse the aggregate from
`upsertAggregate(...).planner()` instead of re-reading, and the internal `togglePublish(Long, Planner)`
overload preserves the owner check (`applyPublishedState` re-checks `isOwnedBy` on the
already-in-state branch, so a non-owner still gets 403 rather than a silent 200).
`PublishRequest.isContentPayloadComplete()` faithfully reproduces every field constraint of
`UpsertPlannerRequest` (`id` not blank, `category` not blank, `contentVersion` non-null and positive,
`plannerType` non-null, `content` non-null) — no validation is lost by routing through the new DTO.

**`RedisConnectionConfig` command timeout.** 3 s on the cross-region auth endpoint is coherent with
the 5 s Hikari wait and the 10 s JDBC socket bound. One gap: `LettuceClientConfiguration.builder()`
sets no `ClientOptions`, so Lettuce's default 10 s **socket connect** timeout still applies — a first
command on a fresh connection to a blackholed Oregon Redis can block ~10 s, not 3 s. Add
`.clientOptions(ClientOptions.builder().socketOptions(SocketOptions.builder().connectTimeout(...)))`
if the 3 s bound is meant to be the ceiling.

**`PlannerViewRecorder` buffer (pre-existing, newly load-bearing).** Isolating the flush onto its own
single thread bounds the blast radius but not the buffer: `buffer` is an unbounded
`CopyOnWriteArrayList`, so under the §5 stall (each flush pinned at the 10 s socket timeout instead of
500 ms) `record()` on request threads pays an O(n) array copy per view and `removeAll` is O(n·m). Not
introduced here, but it is the failure mode INV1 claims to survive.

### Security and API compatibility

### Security / authorization / client-contract audit — task 044

Ranked most severe first. Findings marked *(pre-existing)* were not introduced by this diff, but fall inside the areas this task claims to deliver and are reported because the task's own rows assert the opposite.

---

#### 1. `static/**` never matches a submodule pointer bump — game-data updates silently stop deploying — **blocker**

`/home/user/github/LimbusPlanner/.github/workflows/deploy-fleet.yml:17`

```yaml
    paths:
      - 'backend/**'
      - 'static/**'
      - '.github/workflows/deploy-fleet.yml'
```

`static` is a git **submodule** (`/home/user/github/LimbusPlanner/.gitmodules:1-3`, `path = static`). A submodule update changes exactly one parent-repo path — the gitlink entry `static` — never `static/<anything>`. GitHub's `paths` glob `static/**` requires at least one path segment under `static`, so it does not match. The workflow deleted in the same commit knew this: `sync-game-data.yml` used the bare `- 'static'` (confirmed via `git show task/044-cloudflare-edge-and-write-hardening-base:.github/workflows/sync-game-data.yml`).

**Failure scenario.** An operator bumps the `static` pointer to ship new game data and merges to `main`. `deploy-fleet.yml` does not fire (no `backend/**` change). `sync-game-data.yml` is gone. `POST /api/internal/refresh-game-data` is gone. Nothing reloads the data. New announcements/keywords/season data never reach production until an unrelated backend commit happens to piggyback them, or someone remembers `workflow_dispatch`. This is precisely the escape hatch spec row `internal-endpoints-removed` ("game-data updates ship because `static` is in deploy-fleet.yml on.push.paths") and INV7 claim to preserve.

**Fix:** `- 'static'` (bare), or add both entries.

---

#### 2. The idempotent publish/bookmark API has no client — INV4 is not delivered — **major**

The diff contains **zero frontend files**. The shipped client still sends no body:

- `/home/user/github/LimbusPlanner/frontend/src/pages/planner/hooks/usePlannerPublish.ts:62` — `ApiClient.put(\`/api/planner/md/${plannerId}/publish\`)` (no second arg → `body: undefined`, `/home/user/github/LimbusPlanner/frontend/src/lib/api.ts:296`)
- `/home/user/github/LimbusPlanner/frontend/src/pages/planner/hooks/usePlannerBookmark.ts:52` — `ApiClient.post(\`/api/planner/md/${plannerId}/bookmark\`)`
- `/home/user/github/LimbusPlanner/frontend/src/pages/planner/components/plannerViewer/PlannerDetailHeader.tsx:353` — "Upload current content first (the publish PUT carries none), then toggle" — so `setPublishedWithContent` is unreachable too.

With no body, `request == null` → `namesState()` is never consulted → **100 % of real traffic takes the deprecated toggle branch** (`PlannerPublishingController.java:63-72`, `PlannerEngagementController.java:88-96`), and the `planner.legacy_toggle` counter that is supposed to gate removal will sit at 100 %, never zero.

**Failure scenario.** A user taps Publish. The write commits against the Oregon primary, but the Cloudflare LB fails the region over (or the ~10 s `socketTimeout` introduced by this same task fires) before the 200 reaches the browser. The user sees an error and taps Publish again. The second request read-then-flips the now-`published=true` planner back to `published=false` — the planner is silently unpublished, `onBecameInvisible` runs, and it drops out of the public catalogue. Identically for bookmark: a retry removes the bookmark. INV4 ("a retried or failed-over mutation never double-applies") is unmet in production despite the backend rows being green.

The backend halves (`setPublished`, `setPublishedWithContent`, `setBookmark`) are themselves correct; the defect is that nothing calls them.

---

#### 3. `notify:published` fails client validation on both parse paths — **major** *(pre-existing, but this task's row asserts delivery)*

Delivered shape: the raw payload map built at `/home/user/github/LimbusPlanner/backend/src/main/java/org/danteplanner/backend/planner/service/PlannerPublishingService.java:196-201` —
`{plannerId, plannerTitle, authorEpithet, authorSuffix}` (raw because `NOTIFY_PUBLISHED` has `rawPayloadDelivery = true`, `SseEventType.java:16`, dispatched via `SseRedisSubscriber.java:53-55`).

Client (`/home/user/github/LimbusPlanner/frontend/src/pages/planner/hooks/useAppSse.ts:237-262`) parses the same raw JSON **twice**:

- `useAppSse.ts:246` — `SseEnvelopeSchema.parse(raw)`; that schema requires a top-level `type` (`/home/user/github/LimbusPlanner/frontend/src/shared/sse/schemas/SseEnvelopeSchemas.ts:4-12`). The raw payload has none → **throws** → the list-cache upsert at `:249` never runs.
- `useAppSse.ts:259` — `SsePublishedEventSchema.safeParse(raw)` requires `authorKeyword` (`/home/user/github/LimbusPlanner/frontend/src/shared/notifications/schemas/NotificationSchemas.ts:137-142`); the backend sends `authorEpithet` → **fails** → no toast.

**Failure scenario.** Every user with `notifyNewPublications` enabled and an open SSE stream receives the event, discards it in a `console.error`, and sees nothing. Spec row `sse-broadcast-cross-pod` ("pod B delivers the event") is satisfied on the wire and dead at the client. Sending the *envelope* instead would not help — it would break the toast path too. Either the backend key must become `authorKeyword`, or the schema must accept `authorEpithet`, and the `SseEnvelopeSchema.parse` at `:246` must be dropped for this event.

---

#### 4. `comment:added` is delivered as an envelope whose `type` value the client schema rejects — **major** *(pre-existing)*

`SseRedisSubscriber.java:46-48` hands `plannerCommentSseService.broadcast(plannerId, "comment:added", envelope)` — the whole envelope, with `type: "comment:added"` (`SseEventType.java:13`). `SseEnvelopeSchema`'s `type` enum (`SseEnvelopeSchemas.ts:4-12`) lists only `created | updated | deleted | notify:comment | notify:published | notify:recommended | settings:invalidated`. `"comment:added"` is absent.

**Failure scenario.** Two users read the same published planner. A posts a comment; B's `SseEnvelopeSchema.parse` at `/home/user/github/LimbusPlanner/frontend/src/shared/comment/hooks/usePlannerCommentsSse.ts:93` throws, is swallowed at `:104` as `console.warn`, and the comment tree cache is never patched. The "new comments" badge still increments (it is bumped at `:90`, before the try block), so the UI shows a count that never resolves to visible comments. The existing test (`usePlannerCommentsSse.test.ts:86`) emits `type: 'created'` and therefore cannot catch this.

`comment:added` is the one event type the new `deliversRawPayload` table does not govern (the comment branch bypasses `clientPayload()`), which is why it survived the wire-shape review.

---

#### 5. Planner-sync events reach no listener, and the `syncEnabled` privacy setting is consequently dead — **major** *(pre-existing)*

Wire names emitted: `created` / `updated` / `deleted` (`SseRedisSubscriber.java:64-65` uses `envelope.type().getValue()`; sources at `PlannerCommandService.java:261,341,418,460` via `PlannerSyncEventService.java:38`).
Client listeners registered: `planner-update` and `sync:planner` only (`useAppSse.ts:288-289`). No listener exists for `created`/`updated`/`deleted`.

Two consequences:

- Cross-device planner sync silently delivers nothing.
- `SseService.isEventAllowed`'s `case "sync:planner"` (`/home/user/github/LimbusPlanner/backend/src/main/java/org/danteplanner/backend/shared/sse/SseService.java:273`) is **unreachable** — no event ever carries that string. Every sync event therefore falls to `default -> true`.

**Security angle.** `syncEnabled` is a user-facing privacy switch. A user who turns cross-device sync **off** still has their full planner row (the whole `PlannerResponse` payload, `SseEnvelope.payload`) pushed to every other authenticated session on their account. Today nothing consumes it; the moment anyone adds a listener under the correct wire name — the obvious "fix" for the first bullet — the setting will be silently bypassed. The gate must key on the same enum values the wire uses.

---

#### 6. `excludeUserId` leak to clients is prevented only by an unguarded enum boolean — **minor**

`SseEnvelope.java:23` adds `Long excludeUserId` — an internal user primary key, server-side routing metadata. It reaches a client whenever an envelope is delivered whole. Today it does not: the only producer is `SseEnvelope.broadcast` (`SseEnvelope.java:44`), and the only broadcast type is `NOTIFY_PUBLISHED`, which has `rawPayloadDelivery = true`, so `SseRedisSubscriber.clientPayload` (`:71-73`) strips the envelope. **Verified clean for the current enum table**, including `accountSuspended` (raw) and the user/comment envelopes (`excludeUserId` is always null there — all nine factory argument positions in `SseEnvelope.java:26-53` check out against the component order).

The exposure is one enum literal away: adding any broadcast event type with `rawPayloadDelivery = false` ships the author's internal `users.id` to every connected client. Nothing enforces the coupling — `SseDispatchBoundaryTest` only checks call-site packages, and no test asserts "every type published on `SseChannels.BROADCAST` delivers raw". Consider making it structural (e.g. `broadcast()` rejecting a non-raw type) rather than a convention.

Related: `SseService.broadcastToAll` (`SseService.java:150-159`) skips the excluded user by `equals` on the deserialized `excludeUserId`. If Jackson ever fails to bind that component (record deserialization regression), the author receives their own publish notification — a nuisance, not a disclosure.

---

#### 7. `/api/internal` residue in `nginx/locations.conf`, invisible to the guard test — **minor**

`/home/user/github/LimbusPlanner/nginx/locations.conf:43-46` still carries `location /api/internal/ { return 404; }`. It is **inert and harmless**: it precedes the general `location /api/` at `:48`, so it short-circuits to a bare 404 and forwards nowhere; and the file is baked only into the legacy single-EC2 nginx image driven by `deploy.yml`, which is header-marked SUPERSEDED with its push trigger removed.

The reportable part is the guard: `InternalSurfaceRemovedTest` (`backend/src/test/java/org/danteplanner/backend/architecture/InternalSurfaceRemovedTest.java:26-28`) walks only `src/main/java` and `src/main/resources` **relative to the backend module**. It structurally cannot see `nginx/`, `deploy/`, `scripts/`, or `.github/`. Its DisplayName ("no production source or configuration mentions the internal API surface") overstates its reach; a future proxy-level exemption would pass it green.

On the live k3s fleet the removal is complete: `deploy/base/traefik-gateway.yaml:27-41` is a `PathPrefix: /` catch-all with no `/api/internal` match or rewrite, so the path now falls to `anyRequest().authenticated()`. No workflow curls the endpoint; `scripts/deploy/setup-env.sh` dropped both the SSM fetch and the heredoc line, and no surviving line references `INTERNAL_API_KEY`, so `set -u` has nothing to trip on. Zero `INTERNAL_API_KEY` references remain outside docs.

---

#### 8. Suspension notice is now best-effort and still published pre-commit — **minor**

`ModerationService.java:92,159` call `ssePublisher.publishAccountSuspended(...)` from **inside** the `@Transactional` `timeoutUser` / `banUser` (`:62`, `:133`). `SsePublisher.publish` swallows both serialization failure and `DataAccessException` (`SsePublisher.java:117-122`).

Two mechanisms, both amplified by the move from in-process dispatch to a Redis hop:

- If the auth Redis is unreachable, the ban commits and the suspended user's SSE stream is never told — their tab stays open and functional until it next hits an authenticated endpoint. Previously the local pod's emitters were always notified synchronously.
- The publish now crosses a network boundary before the transaction commits. A subscriber on another pod can dispatch `account_suspended` and close the user's stream for a ban that subsequently rolls back.

Neither grants access the user should not have — the auth path still rejects a banned principal — so this is a UX/consistency defect, not an authorization hole. Publishing from an `AFTER_COMMIT` listener (as `onPlannerPublished` now does, `PlannerPublishingService.java:59-64`) would close both.

---

#### 9. Pre-existing `permitAll` surface, unchanged by the removal — **minor, informational**

Removing `.requestMatchers("/api/internal/**").permitAll()` from `SecurityConfig.java` (between what are now lines 88 and 90) **changed enforcement for no other path** — first-match-wins ordering is preserved, and `/api/internal/**` was disjoint from every other pattern in the chain. Confirmed by reading the full `authorizeHttpRequests` block (`SecurityConfig.java:72-111`).

Worth noting while the block is open, none of it introduced here:

- `SecurityConfig.java:88` — `/actuator/prometheus` is `permitAll` and sits behind Traefik's `PathPrefix: /` catch-all. The full Micrometer scrape (JVM internals, Hikari pool state, per-URI histograms, `jwt_rotation_outcome_total`, the new `planner.legacy_toggle`) is unauthenticated to anyone who reaches the origin. Post-GA-teardown the only thing in front of it is the Cloudflare edge.
- `SecurityConfig.java:84` — `/api/public/**` is `permitAll` and maps to **no controller anywhere**. It is the identical hazard the commit removed `/api/internal/**` to close: a standing exemption for a path a future endpoint can land on unnoticed. Removing it would make the stated rationale consistent.
- `SecurityConfig.java:91-94` — the four public planner matchers are method-agnostic, so `PUT /api/planner/md/published` matches `permitAll` and reaches `PlannerCommandController`'s `@PutMapping("/{id}")`. Impact is closed in practice (the CSRF filter 403s first, and `@PathVariable UUID id` would 400), but lines 97/100/103 already scope by `HttpMethod.GET` and these should too.

---

### Areas verified clean

**Authorization on the changed write paths.** Every reachable path enforces ownership and user restrictions:

- `setPublished` (`PlannerPublishingService.java:78-84`) — `checkUserRestrictions` before the load, `findAggregate` (which filters `c.deletedAt IS NULL`, `PlannerRepository.java:56-57`, so a soft-deleted planner cannot be re-published), then `applyPublishedState`.
- `applyPublishedState` (`:107-115`) — the new idempotent short-circuit is the one branch that could have skipped the guard, and it does **not**: it checks `planner.isOwnedBy(userId)` before returning. A non-owner asking for the state a planner is already in gets `PlannerForbiddenException`, matching the differing-state path (which routes through `togglePublish(userId, planner)` → `checkUserRestrictions` + `isOwnedBy`). Error identity is preserved against the base: 404 for a missing/deleted planner, 403 for a live one owned by someone else — no new existence oracle, and no case where a load-then-guard reordering swaps 403 for 404.
- `setPublishedWithContent` (`:98-104`) and `publishWithContent` (`:222-232`) — the aggregate now comes from `upsertAggregate` rather than a second `findAggregateForOwner`. Owner/deleted scoping is preserved because `upsertAggregate` (`PlannerCommandService.java:307-309`) starts from `findAggregateForOwner(id, userId)` and, on a miss, classifies: another user's *active* row → `PlannerForbiddenException`, the owner's soft-deleted row → `PlannerNotFoundException`. A non-owner can never reach `applyPublishedState` holding someone else's aggregate. Neither method calls `checkUserRestrictions` itself, but both branches of `upsertAggregate` do (`PlannerCommandService.java:315`, and `createAggregate`'s `getUserAndCheckRestrictions`), so a banned user is still rejected.
- `PlannerEngagementService.setBookmark` (`:161-186`) mirrors `toggleBookmark` exactly — `findPublishedAggregate` gate (published + non-deleted) then a bookmark row scoped to `(userId, plannerId)`. Neither has ever had a restriction check; unchanged.
- The classification rewrite (`PlannerCommandService.java:346-359`) preserves both original outcomes and its parameters are bound, not concatenated.

**New request DTOs.** Validation is genuinely enforced and cannot be bypassed:

- `@AssertTrue` on `PublishRequest.isContentPayloadComplete()` (`PublishRequest.java:69-77`) is discovered by Jakarta: the method is `boolean`-returning, no-arg, `is`-prefixed, so it registers as the property `contentPayloadComplete`, and `@Valid` on the `@RequestBody` (`PlannerPublishingController.java:52`) triggers `MethodValidation` on it.
- The predicate is field-for-field equivalent to the `UpsertPlannerRequest` constraints it replaces (`@NotBlank id`, `@NotBlank category`, `@NotNull content`, `@NotNull @Positive contentVersion`, `@NotNull plannerType`) — nothing was weakened. A hostile partial body (`{"published":true,"id":"x"}`, `{"content":"..."}` with no id/category/version/type) fails.
- Failures yield **400** via `GlobalExceptionHandler.handleValidation` (`:270-278`, `MethodArgumentNotValidException`), and a malformed/unparsable body yields 400 via `handleMessageNotReadable` (`:263-268`). Both handlers exist.
- The `Boolean` null-vs-false distinction cannot reach an unintended branch: `published == null` and `bookmarked == null` route to the deprecated toggle, which applies the *same* guards as the state-targeted methods. Old bodyless clients bind to `request == null` correctly (`@RequestBody(required = false)` with no Content-Type and no body resolves to null rather than 415).

**CSRF filter.** `/api/internal/` was the only path-based exemption that ever existed in `CsrfDoubleSubmitFilter`; `requiresEnforcement` is now purely `method ∉ {GET, HEAD, OPTIONS}` plus the unchanged `shouldNotFilter` ASYNC skip for SSE continuations. No endpoint became newly subject to enforcement — every `permitAll` non-GET route either is same-origin SPA traffic that carries the double-submit pair, or has no handler. (One landmine for later, not for now: the stubbed `POST /api/auth/apple/callback` at `AuthController:167` would 403 the day it is implemented, because Apple's `response_mode=form_post` cannot set `X-CSRF-Token`.)

**Rate limiting.** Both changed controller methods keep their check as the first statement, before any body inspection or branch selection: `PlannerPublishingController.java:56` (`checkCrudLimit(userId, "publish")`) and `PlannerEngagementController.java:83` (`checkCrudLimit(userId, "bookmark")`). No path — legacy, state-targeted, or content-carrying — skips it.

**Notification fan-out SQL.** `NotificationRepository.insertPublishedFanout` is fully parameterized (`:authorId`, `:plannerId`, `:plannerTitle`) with no concatenation, and its predicate is a faithful translation of the JPQL it replaces (`notify_new_publications = true`, `u.deleted_at IS NULL`, `user_id <> :authorId`) — no recipient filter was dropped.
