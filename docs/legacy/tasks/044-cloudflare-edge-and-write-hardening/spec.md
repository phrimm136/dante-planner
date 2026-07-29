---
base: 67f23fcf535cd4200272e3ffc8bc4388cd0c6f56   # mirror of task/044-cloudflare-edge-and-write-hardening-base
requirements: [R1, R2, R3, R4, R5, R6, R7, R8, R9, R10]
supersedes: 041-ga-to-cloudflare-tunnel-lb        # 041 is dead; its cloudflared-direct/no-Traefik design is reversed
---

<!--
Two half-tasks under one window. HALF A (edge): replace GA with Cloudflare Tunnel + LB,
KEEPING Traefik as a plain ingress. HALF B (write-path finish): the app-layer fixes that make
Seoul's cross-region writes correct, bounded, and few-round-trip once GA's backbone is gone.
043 (god-table decomposition) is COMMITTED but not prod-deployed; its non-backward-compatible
schema swap forces a stop-the-world prod-deploy window (R9) that HALF A rides.
-->

## Rows

### Write path (R3) — few round-trips, correctness preserved
- id: own-gtid-union-across-commits
  drives: GtidCookieFilter capture -> Set-Cookie ryw_gtid
  given: a publish that commits twice (main tx + the AFTER_COMMIT filter-rebuild REQUIRES_NEW tx)
  when: the write response is produced
  then: ryw_gtid carries the UNION of both commits' GTIDs (request-scoped accumulator), so a
        follow-up replica read gates past the filter rebuild, not only the main commit
  requirement: R3
- id: own-gtid-fallback-on-empty-tracker
  drives: GtidWriteCapture
  given: session_track_gtids OK-packet tracker is empty or the Hikari+LazyConnectionDataSourceProxy
         unwrap fails
  when: capture runs in afterCommit
  then: it falls back to SELECT @@gtid_executed (the current superset), no cookie regression
  requirement: R3
- id: publish-fanout-single-statement
  drives: NotificationService.notifyPlannerPublished
  given: a publish with N subscribers who have notifyNewPublications enabled
  when: the fan-out runs (AFTER_COMMIT, REQUIRES_NEW, READ COMMITTED)
  then: exactly one INSERT IGNORE ... SELECT FROM user_settings (honoring the notify flag,
        deletedAt IS NULL, exclude-author, uk_notification_dedup), NOT N inserts; the request
        thread does not block on N recipients
  requirement: R3
- id: publish-loads-aggregate-once
  drives: PlannerPublishingService.publishWithContent
  given: a one-request publish of an existing planner
  when: it executes
  then: the aggregate is loaded once and reused (internal togglePublish(Planner) overload),
        not 3x; owner/deleted scoping from findAggregateForOwner is preserved
  requirement: R3
- id: create-existence-two-selects
  drives: PlannerCommandService.upsertPlanner (create branch)
  given: an upsert whose id collides
  when: the existence classification runs
  then: one classifying SELECT (user_id, deleted_at WHERE id=?) yields 404 own-soft-deleted /
        403 other-user-active, and the count probe yields PlannerLimitExceededException(count,max);
        UpsertResult still distinguishes 201-created vs 200-updated
  requirement: R3

### Read-your-writes (R2, R8)
- id: ryw-cookie-on-committed-write
  drives: GtidCookieFilter
  given: a Seoul-served request that commits a non-readOnly MySQL tx
  when: the response returns
  then: Set-Cookie ryw_gtid is present
  requirement: R8
- id: ryw-no-cookie-on-redis-only-write
  drives: GtidCookieFilter
  given: POST /logout (Redis-only, no MySQL tx commits)
  when: it returns
  then: no ryw_gtid is minted
  requirement: R8
- id: oauth-google-callback-mints-cookie
  drives: AuthController GET /google/callback
  given: a first Google login committing a user row on a Seoul pod
  when: it 302s
  then: ryw_gtid is set; the SPA's immediate GET /api/auth/me finds the new row (no logged-out flash)
  requirement: R8
- id: stale-read-pins-to-primary
  drives: read path (GtidReadGate + ReadOnlyRoutingDataSource)
  given: a Seoul read carrying ryw_gtid while the local replica lags
  when: served
  then: it pins to the primary pool (existing CausalHarnessIT contract, now the steady state)
  requirement: R2

### Degrade by operation (R4) — bounded cross-region timeouts
- id: write-hang-reads-survive
  drives: routing datasource + JDBC/Lettuce timeouts + GlobalExceptionHandler
  given: the Oregon primary is blackholed (connect hangs, not refuses)
  when: a Seoul pod serves traffic
  then: reads return 200 from the local replica; writes return typed 503 WRITE_UNAVAILABLE within
        the app JDBC socketTimeout (~10s); Tomcat threads are not exhausted
  requirement: R4
- id: scheduler-not-starved-by-cross-region-hang
  drives: TaskScheduler pool + PlannerViewRecorder flush executor
  given: a hung cross-region scheduled write or ShedLock acquisition on the Oregon auth Redis
  when: it stalls
  then: SSE heartbeats still fire (scheduler pool >= 4; the 500ms view-flush runs on its own executor)
  requirement: R4

### SSE cross-pod / cross-region (R5)
- id: sse-broadcast-cross-pod
  drives: SsePublisher (sse:broadcast) / SseRedisSubscriber
  given: notify:published emitted on pod A
  when: a subscriber is connected to pod B in the other region
  then: pod B delivers the event
  requirement: R5
- id: sse-suspension-cross-pod
  drives: SsePublisher / SseRedisSubscriber
  given: account_suspended raised on pod A
  when: the suspended user's SSE stream is on pod B
  then: pod B delivers it (ACCOUNT_SUSPENDED on the wire enum, excludeUserId on the envelope)
  requirement: R5
- id: sse-settings-invalidation-cross-pod
  drives: publishSettingsInvalidation wired at UserController.updateSettings
  given: PUT /users/settings handled on pod A
  when: the user's SSE stream is on pod B
  then: pod B drops its stale settingsCache entry for that user
  requirement: R5

### Idempotent mutations (R6)
- id: publish-idempotent-state-targeted
  drives: PlannerPublishingService (state-targeted PUT published=true|false)
  given: publish {published:true} sent twice
  when: processed
  then: published stays true (no flip); a deprecated legacy toggle handler still 200s for stale tabs
  requirement: R6
- id: bookmark-idempotent-state-targeted
  drives: PlannerEngagementService
  given: bookmark {bookmarked:true} sent twice
  when: processed
  then: bookmarked stays true (no un-bookmark on retry)
  requirement: R6

### Ops surface (R7)
- id: internal-endpoints-removed
  drives: routing + SecurityConfig
  given: any request to /api/internal/*
  when: routed
  then: 404 (InternalController deleted; permitAll + CSRF bypass removed); game-data updates ship
        because `static` is in deploy-fleet.yml on.push.paths
  requirement: R7

### Edge (R1, R10) — verified by drills
- id: edge-geo-steers
  drives: Cloudflare LB geo steering
  given: a JP vantage and a US vantage
  when: each hits api.dante-planner.com
  then: JP lands Seoul, US lands Oregon (per-region Prometheus request-rate delta) [drill]
  requirement: R1
- id: edge-survives-region-loss
  drives: CF LB health + cross-region failover pool
  given: one region's cloudflared killed
  when: traffic arrives
  then: the surviving region serves 200s [drill]
  requirement: R1
- id: inbound-closed-post-teardown
  drives: fleet security groups
  given: post-teardown state
  when: an external port scan runs
  then: no public inbound rule except cluster_self and redis_auth_cross_region [drill + tf-plan]
  requirement: R10

## Decisions
- D1: @edge @planner @gtid — Drop edge write-routing; Seoul writes cross-region to the Oregon
  primary via the existing parameterized JPA path. because RYW is already at the app seam (ryw_gtid
  cookie) and writes are rare/explicit (local-first), so steering them to the primary buys ~1s on a
  rare action. rejected: Traefik method-forward to an Oregon backend — needs a 2nd cross-region trust
  surface (backend_cross_region SG rule + stable remote target + Host/trust), a GTID capture/read-gate
  filter split, and OAuth-callback path-routing; the write-forward network plumbing is greenfield
  (no backend_cross_region rule exists, unlike redis_auth_cross_region). (taste)
- D2: @edge @traefik — Keep Traefik as a plain pass-through ingress (cloudflared -> Traefik -> backend).
  because the role=ingress EC2 node is retained for a future microservices/chatbot seam regardless, so
  Traefik's marginal cost is one sub-ms in-cluster hop + manifest upkeep, not an instance. rejected:
  cloudflared -> backend Service direct (041 Decision 3) — technically leaner but discards the future
  routing seam and CF-less local dev. enforcement: accepted-cost note tied to the retained-node decision.
- D3: @edge — Remove GA the accelerator only; RETAIN the role=ingress EC2 nodes, Traefik, and the
  origin-tls secret (Traefik's HTTPS :443 listener depends on origin-tls; without it every route 404s;
  the node is cloudflared's private origin). rejected: 041's "delete GA + ingress nodes + origin-tls +
  Traefik" — deletes cloudflared's origin, 404s both regions. Terminating the pet nodes is a SEPARATE
  later re-platform (Traefik -> in-cluster Deployment + ClusterIP), never folded into this teardown.
- D4: @gtid — RYW stays the existing ryw_gtid cookie + read-gate + replica routing, UNCHANGED
  (Seoul routing on; Oregon has no replica, reads its own primary). rejected: a GTID capture/read-gate
  filter SPLIT (capture-only both regions) — only needed for the edge write-forward D1 drops.
- D5: @gtid @auth — Capture-on-committed-tx (C7): mint ryw_gtid only when a non-readOnly MySQL tx
  commits (afterCommit flag on !isCurrentTransactionReadOnly), so the Seoul-served GET /google/callback
  sets the cookie and Redis-only writes (logout) / 429s / 403s do not. rejected: HTTP-method-gated
  capture — misses the mutating GET callback, mints on non-DB writes. (evidence: AuthController:115 GET)
- D6: @sse — ALL SSE dispatch fans out through Redis: publish at the call sites; sendToUser /
  broadcastToAll / notifyAccountSuspended / invalidateSettingsCache stay as subscriber-only local
  dispatch. Add an sse:broadcast channel + ACCOUNT_SUSPENDED enum + excludeUserId envelope field.
  rejected: any in-process-only broadcast path (violates shared/sse/CLAUDE.md); publishing inside the
  dispatch method (the subscriber calls it -> infinite cross-pod Redis loop). enforcement: sweep
  assertion — no dispatch/invalidation method has callers outside shared/sse + the subscriber.
- D7: @planner — State-targeted (idempotent) publish + bookmark; retain a DEPRECATED legacy toggle
  handler for stale hashed-bundle tabs, removed at ~0 measured usage. because a hard toggle->state
  cutover at FE-deploy 404s long-lived local-first tabs still on the old bundle (deploy time != adoption).
  rejected: hard removal at FE deploy (dead publish/bookmark button until reload).
- D8: @ops — Remove all /api/internal (InternalController, permitAll, CSRF bypass, tests, property,
  setup-env, SSM param); game data reloads on the stop-the-world restart; lineage flag -> ConfigMap;
  add `static` to deploy-fleet.yml trigger paths. because imperative per-pod mutation is a GitOps
  anti-pattern (non-durable, non-uniform, unaudited) and removal is behavior-preserving in prod
  (no INTERNAL_API_KEY wired on the fleet -> endpoints already reject everything). rejected: keep +
  carve /api/internal out of any edge route.
- D9: @reliability — Degrade by operation: writes fail as typed WRITE_UNAVAILABLE with client state
  preserved (drafts are local-first); reads survive; bounded cross-region timeouts make it real.
  rejected: exception-mapping alone — Tomcat/Hikari threads exhaust on infinite socketTimeout before
  the typed exception ever throws, taking reads down with writes.
- D10: @migration — Stop-the-world migration riding 043's prod deploy (mandatory: god-table ->
  satellites, no legacy/dual-write control -> all clusters down). One window: schema + edge. Rollback =
  full commit-set revert + terraform + DB reverse-migration/snapshot. redis-auth data node: wipe
  ACCEPTED this window (rotation Lua self-bootstraps absent families -> NO mass re-login; only pre-window
  revocations/blacklist/tombstones lost, already accepted). rejected: keep GA dormant as a live rollback
  target (the commit set removes GA); terraform-recreate the data node (would compound the schema
  rollback with mass logout). Bring-back order: Oregon first (RDS primary + redis master), then Seoul.
- D11: @planner @gtid — Reach ~1 round trip via session_track_gtids=OWN_GTID (union across a request's
  commits) + targeted fixes, NOT a stored procedure for the core aggregate write. because a core-aggregate
  CALL would re-encode the @Version optimistic-lock CAS and the keyword rename map (AccelBullet->9828, ...)
  in SQL — forking the exact Java oracle the drift reconciler exists to police — to save only ~390-520ms on
  rare create/publish while leaving the dominant costs (fan-out, redundant reloads, GTID capture) untouched.
  rejected: full stored proc for the aggregate. The logic-free bulk (rebuild_planner_filters) is ALREADY a
  proc (V053) — do not re-create it.

## Invariants
- INV1: A cross-region write-path hang never fails reads or exhausts Seoul's threads/scheduler.
  verify: row write-hang-reads-survive + row scheduler-not-starved-by-cross-region-hang + a drill
  (blackhole the Oregon primary, assert reads 200 and heartbeats continue).
- INV2: A client sees its own just-committed write, including work committed after the main tx.
  verify: row own-gtid-union-across-commits + CausalHarnessIT. The geo-flap gap (an Oregon-served write
  sets no cookie, a flap to Seoul reads a lagging replica) is pre-existing and UNCHANGED — accepted-risk.
- INV3: No SSE dispatch or settings-invalidation reaches only the local pod.
  verify: the D6 sweep assertion + a cross-pod drill (rows sse-broadcast-cross-pod, sse-suspension-cross-pod,
  sse-settings-invalidation-cross-pod).
- INV4: A retried or failed-over mutation never double-applies.
  verify: rows publish-idempotent-state-targeted, bookmark-idempotent-state-targeted.
- INV5: After teardown, no public inbound SG rule except cluster_self and redis_auth_cross_region.
  verify: row inbound-closed-post-teardown (terraform plan assertion + external port scan).
- INV6: A Seoul-served publish completes in bounded time (no per-recipient multiplier).
  verify: row publish-fanout-single-statement + a latency metric during the staging drill.
- INV7: /api/internal removal is behavior-preserving in prod.
  verify: grep shows no INTERNAL_API_KEY wired on the fleet + row internal-endpoints-removed + suite green.
- INV8: A redis-auth data-node wipe forces no mass re-login (rotation families self-bootstrap).
  verify: RefreshRotationService rotation-Lua test.
- INV9: A jwt-key rotation reaches both regions before the auth path uses the new key.
  verify: the secrets are AWS-managed cross-region replicas (values auto-replicated, region-invariant) —
  recorded as an operational invariant (rotate -> confirm replicated -> use); no cutover step.

## Behavior Inventory        (edge seam — brownfield)
| # | Seam (as-is) | Observable behavior | Verdict |
|---|---|---|---|
| B1 | CF proxy -> GA -> EC2 hostNetwork Traefik :443 | L4 anycast front door to per-region Traefik | replace: CF -> CF LB -> cloudflared -> Traefik (GA gone; nodes+Traefik kept) |
| B2 | GA proximity + /healthz-local health drain | nearest healthy region, ~30s drain | replace: CF LB geo + through-tunnel /healthz-local monitor, ~1-3min drain |
| B3 | GA SOURCE_IP affinity | CF edge IP pins to one region | drop as guarantee — best-effort LB cookie (RYW is at the app seam) |
| B4 | mTLS Authenticated Origin Pull (VerifyClientCertIfGiven) | admits certless callers; never gated | change: tunnel auth is the origin gate; TLSOption stays VerifyClientCertIfGiven, NEVER hardened to RequireAndVerify (would brick the certless tunnel) |
| B5 | Traefik TLS termination via origin-tls | edge->origin encrypted | preserve: origin-tls RETAINED (cloudflared originServerName + Origin CA in caPool) |
| B6 | client IP via CF-Connecting-IP | rate-limit identity = real client IP | preserved — ClientIpResolver unchanged |
| B7 | Seoul-served write -> Oregon primary DB | cross-region parameterized JPA write | preserved — unchanged from today (no redirect) |
| B8 | in-process SSE broadcasts (notify:published, suspension, settings-invalidation) | reach same pod only | fix — Redis fan-out (D6) |
| B9 | publish/bookmark toggle | read-then-flip, non-idempotent | fix — state-targeted (D7) |
| B10 | /api/internal per-pod mutation (refresh-game-data, lineage flag) | one pod, non-durable | drop — game data on restart; flag -> ConfigMap (D8) |

## Done When
- [ ] R3.1 session_track_gtids=OWN_GTID: RDS param set; GtidWriteCapture reads the server session-state
      tracker (union across the request's commits) with the SELECT @@gtid_executed fallback intact
      (local-tdd + a staging spike proving the Hikari+LazyConnectionDataSourceProxy unwrap) — R3
- [ ] R3.2 notification fan-out is one AFTER_COMMIT/REQUIRES_NEW/READ-COMMITTED INSERT IGNORE...SELECT (local-tdd) — R3
- [ ] R3.3 publish loads the aggregate once (internal togglePublish overload) (local-tdd) — R3
- [ ] R3.4 create existence classification is one SELECT + count probe, all four rows preserved (local-tdd) — R3
- [ ] R4 F1 scheduler pool>=4 + view-flush executor; F2 connectionTimeout on all 3 Seoul routing pools
      PLUS connectTimeout/socketTimeout (~10s) on the APP JDBC URL (not Flyway's); F3 Lettuce command
      timeouts on the cross-region auth Redis — deployed via GitOps BEFORE the window (local-tdd) — R4
- [ ] R5 SSE fan-out sweep: publishSettingsInvalidation wired at UserController; broadcastToAll and
      notifyAccountSuspended routed through SsePublisher; sse:broadcast channel + ACCOUNT_SUSPENDED enum
      + excludeUserId field; settingsCache Caffeine TTL; the D6 sweep assertion green (local-tdd) — R5
- [ ] R6 state-targeted publish + bookmark + deprecated legacy toggle handler with a usage counter (local-tdd) — R6
- [ ] R7 InternalController + all /api/internal surface removed; `static` added to deploy-fleet.yml trigger;
      legacy sync-game-data.yml retired (local-tdd + workflow) — R7
- [ ] R8 C7 capture-on-committed-tx in place (local-tdd) — R8
- [ ] R1 terraform/cloudflare applies: 2 tunnels (>=4 edge conns each), CF LB + pools + through-tunnel
      monitors + geo steering; cloudflared Deployments (HA, --no-autoupdate) + trust config (infra) — R1
- [ ] R1 staging-E2E on a terraform-built staging cluster: geo steer (edge-geo-steers), region-loss
      failover (edge-survives-region-loss), OAuth callback across the split, SSE through the tunnel,
      cloudflared HA roll (live) — R1
- [ ] R9 stop-the-world drill on staging: detach edge -> drain view buffers (>=5s) -> stop Seoul ->
      stop Oregon -> verify RDS GTID + redis master_repl_offset parity -> restart Oregon -> smoke ->
      restart Seoul -> attach edge; rollback = full revert (live) — R9
- [ ] R10 POST-BAKE teardown: GA module, ingress SG CIDR rules, R53 health rules,
      update-cloudflare-ips.sh cron, ga-preflight.sh, CloudflareIpSilence alarm; INV5 re-verified.
      NOT origin-tls, NOT the ingress nodes, NOT Traefik (infra) — R10
- [ ] All existing backend + frontend suites pass — untouched and green (local-tdd)

## Deferred
- RDS Multi-AZ — until then an Oregon instance failure is Layer-3 (manual, minutes) not Layer-2 (auto ~90s).
- redis-auth cross-region HA (Sentinel/promotion) — Phase-2 stateless-refactor scope.
- Comment idempotency keys — a failover-rare duplicate comment stays possible until then.
- SSR edge Worker; intra-region multi-AZ hardening (control-plane 3-node + node AZ-spread).
- Stored proc for the core aggregate write — rejected (D11); revisit only if the publish-drill latency
  proves painful, at the cost of a second SQL oracle.
- Argo Smart Routing — revisit only on measured US/EU latency regression after the bake.

## Amendments
<!-- append-only, empty at first. A row found wrong mid-build is amended HERE, re-gated by the user
     via the Edit(spec.md) ask-rule. Never rewrite a row in place; append old -> new -> why. -->

## Runner
- backend: `/home/user/github/LimbusPlanner/backend/gradlew -p backend test` (unit-only:
  `-PexcludeTags=containerized`; `--tests "<pattern>"` to scope). Full task = the containerized
  Testcontainers tier too (Docker required) — the RYW/fan-out/decomposition ITs live there.
- frontend: `yarn --cwd frontend vitest run`
- infra: `terraform -chdir=terraform/cloudflare validate|plan` (never `cd`; per-root `-chdir`);
  `kubectl kustomize deploy/overlays/{oregon,seoul}` renders clean.
- live: staging drills — edge geo/failover, OAuth-across-split, stop-the-world, SSE-through-tunnel.
- Runner quirk: gradle/vitest are regression rails for the infra half; the app-code rows above are
  TDD'd. Confirm BUILD SUCCESSFUL + fresh test XML, not the exit code (a wrong wrapper path exits 0).
