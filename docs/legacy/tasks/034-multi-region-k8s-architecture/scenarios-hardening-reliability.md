# Hardening Ledger: Reliability (phase 4-11 review remediation)

Ad-hoc remediation phase (NOT in the numbered plan; status.json untouched).
Dossiers: ~/.local/state/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/phase-hardening-reliability/

Three independent verified findings, each RED->GREEN with a test, backend suite kept green.
Production changes land in three disjoint files (PlannerCommandService / GlobalExceptionHandler /
PlannerSyncEventService+CommentService); test changes in three disjoint files (new TombstoneRollbackIT /
DegradationIT / SseFanoutIT) — so the three burn down in parallel with no collisions.

## Scenarios
| # | Slug | Scenario (one line) | Status | Red proof | Green proof |
|---|------|--------------------|--------|-----------|-------------|
| 1 | f1-tombstone | deletePlanner whose tx rolls back leaves NO del:planner:<id> tombstone (write must be afterCommit) | closed | assertion: AssertionFailedError TombstoneRollbackIT:80 (tombstone expected false but was true; live-check :76 passed first). /tmp/tdd-red-f1-1783820692.log | green: TombstoneRollbackIT tests=1 fail=0; PlannerCommandServiceTest 31 fail=0; BUILD SUCCESSFUL. PlannerCommandService.java +12/-1 (afterCommit hook, isSynchronizationActive guard + direct-write else for non-tx unit path). /tmp/tdd-green-f1-1783823337.log |
| 2 | f2-ratelimit | rate-limit Redis outage on a rate-limited endpoint returns typed 503 RATE_LIMIT_TEMPORARILY_UNAVAILABLE, not 500 | closed | assertion: AssertionError DegradationIT:395 Status expected:<503> but was:<500> (body INTERNAL_ERROR). Escaping exc = io.lettuce.core.RedisCommandTimeoutException (raw, UNWRAPPED). /tmp/tdd-red-f2-1783820880.log | green: DegradationIT tests=4 fail=0 (F2 503 + INV5/(a)/(b)); BUILD SUCCESSFUL. GlobalExceptionHandler.java +25 (@ExceptionHandler(io.lettuce.core.RedisException) -> 503 RATE_LIMIT_TEMPORARILY_UNAVAILABLE, no Sentry). Filter untouched. /tmp/tdd-green-f2-1783823889.log |
| 3 | f3-ssefanout | a REAL planner write publishes through SsePublisher to Redis, delivered cross-node (comment path descoped) | closed | assertion: planner ArgumentsAreDifferent (0 invocations of 3-arg sendToUser "updated"; got in-process 4-arg "sync:planner"). /tmp/tdd-red-f3b-1783822175.log | green: real updatePlanner now delivers via Redis round-trip — SseFanoutIT verify(sseService,timeout(5000)).sendToUser(userId,"updated",envelope) passes; SseFanoutIT tests=4 fail=0 + PlannerCommandServiceTest 31/0 + CommentServiceTest 17/0 + PlannerCommentSseServiceTest 12/0. PlannerSyncEventService.java +5 (body->ssePublisher.publishUserEvent), SseEventType.java +15 (fromValue). /tmp/tdd-green-f3-1783824734.log |

## Settled decisions
- F1: move `tombstoneStore.writeTombstone(...)` out of the in-tx body into a
  `TransactionSynchronizationManager.registerSynchronization` afterCommit hook, so it fires ONLY on
  commit (still synchronous, pre-HTTP-response per mechanics §5). PrimaryReCheck's replica-hit
  tombstone-check location is LEFT UNCHANGED (correct per its Javadoc).
- F2: map raw `io.lettuce.core.RedisException` (supertype of RedisConnectionException /
  RedisCommandTimeoutException / RedisSystemException — the rate limiter is the ONLY raw-Lettuce
  client, in RedisConnectionConfig) to 503 `RATE_LIMIT_TEMPORARILY_UNAVAILABLE`, NO Sentry, in
  `GlobalExceptionHandler` — mirroring `handleDatabaseUnavailable`/`handleRedisUnavailable`.
  DECISION (evidence-backed deviation from the task's "BOTH handler AND filter"): the mapping goes
  in GlobalExceptionHandler ONLY. Evidence: `grep io.lettuce.core` over backend/src/main matches
  exactly one file (RedisConnectionConfig, the rate-limit proxy); rate-limit checks
  (RateLimitConfig.checkCrudLimit/…) execute only at controller entry, inside the DispatcherServlet,
  so @RestControllerAdvice catches them; JwtAuthenticationFilter invokes NO rate-limit check and its
  only Redis access is via Spring Data StringRedisTemplate (-> RedisConnectionFailureException,
  already mapped in both places). A rate-limit Lettuce catch in the filter would be unreachable dead
  code (violates CLAUDE.md rule #1/#9). mechanics §7's "filter too" intent for auth-Redis Lettuce
  failures is already satisfied by the existing writeAuthUnavailable path.
- F3: route the write path through SsePublisher WITHOUT touching PlannerCommandService/CommentService
  call-site signatures (keeps 7 PlannerCommandServiceTest verify(...notifyPlannerUpdate) assertions
  green):
  - Planner: change the BODY of `PlannerSyncEventService.notifyPlannerUpdate` to call
    `ssePublisher.publishUserEvent(...)` instead of `sseService.sendToUser(...)`. Delivery becomes
    write -> SsePublisher -> Redis -> SseRedisSubscriber -> SseService.sendToUser (all pods, single
    delivery). Payload preserved (the {plannerId,type} map rides the envelope payload). Full-entity-DTO
    enrichment is deferred (it would require call-site signature changes that break green unit tests).
  - Comment: change CommentService's two `plannerCommentSseService.broadcastCommentAdded(...)` call
    sites to `ssePublisher.publishCommentEvent(...)` (CommentServiceTest does NOT verify that call, so
    it stays green; PlannerCommentSseServiceTest tests broadcastCommentAdded directly and stays green).
    NOTE residue: broadcastCommentAdded loses its production caller — cannot be removed without
    editing its characterization tests; flagged as a follow-up, not fixed here.

## List Revisions
- F3 COMMENT-path DESCOPED (green-to-green infeasible; brief-authoring miss corrected here). Evidence:
  `CommentServiceTest.setUp()` MANUALLY constructs `new CommentService(...)` with all 7
  @RequiredArgsConstructor args — injecting SsePublisher (an 8th) breaks that test's COMPILE; the
  alternative (routing `PlannerCommentSseService.broadcastCommentAdded`'s body through SsePublisher)
  breaks 3 PlannerCommentSseServiceTest characterization tests (dead-emitter removal, serialization).
  Both require editing an existing test — forbidden under green-to-green. F3 is therefore delivered as
  the PLANNER write path only (which alone gives SsePublisher a real production caller and makes the
  core "dead publisher" finding false). Comment-path fan-out is a documented FOLLOW-UP requiring its
  own scenario that also updates CommentServiceTest's constructor. F3 red re-tasked to drop the comment
  test method. PLANNER path is clean: PlannerSyncEventService is never manually constructed in tests.
- COMMENT-path gap assessment (coordinator ask): `PlannerCommentSseService`'s cross-pod publish IS
  also dead — `SsePublisher.publishCommentEvent` has NO production caller (SsePublisher was entirely
  unwired; that is the whole F3 finding), and `CommentService.createComment` (lines ~253/318) calls the
  in-process single-pod `plannerCommentSseService.broadcastCommentAdded(...)`. The DELIVERY side is
  wired (`SseRedisSubscriber` -> `plannerCommentSseService.broadcast(...)`), but nothing PUBLISHES a
  comment envelope, so multi-pod comment fan-out is dead — the SAME F3 gap as the planner path.
  Safe to DEFER (not ignore): (1) impact is notification-latency, not correctness/data — a comment
  posted on pod A is missed live by comment-SSE subscribers on pod B; mechanics §4's client
  reconnect/reconciliation backstops recover it; single-region (current) it is a non-issue (one pod);
  (2) fixing it green-to-green is impossible here — both routes (inject SsePublisher into CommentService,
  or reroute broadcastCommentAdded's body) break an existing test, and tdd-green cannot edit tests. It
  therefore NEEDS its own remediation scenario that deliberately updates CommentServiceTest's 7-arg
  constructor (and/or the 3 PlannerCommentSseServiceTest characterization tests) as a reviewed change.
  RECOMMEND scheduling it as a sibling finding (F3b) before the multi-region cutover.
- gradlew wrapper lives at `backend/gradlew`, NOT repo-root `./gradlew`; scoped run command is
  `backend/gradlew -p /home/user/github/LimbusPlanner/backend test --tests "..."`. (F3 red divergence.)
- Parallel RED across 3 spawns shared one backend Gradle build dir; Gradle's build lock serialized
  them and build/test-results got clobbered by later runs (per-agent /tmp logs persist and are the
  durable gate artifact). DECISION: run the GREEN legs SEQUENTIALLY — three green edits land in one
  shared working tree, so a concurrent green would test against another's half-applied change.

## Pipeline (post-burndown)
- refactor: nothing warranted (in-scope decision). Only strain is the F1 2-line duplicated
  `tombstoneStore.ifPresent(...)` across the afterCommit/else branches and the three sibling
  503 *_TEMPORARILY_UNAVAILABLE handlers (kept separate by design — distinct javadoc). Neither
  justifies re-running the slow containerized suite against surgical precision (CLAUDE.md #1).
- verify: verification.md "Reliability Hardening" note written directly (task-directed; ad-hoc
  remediation has no numbered-phase spec items for spec-verifier to certify).
- capture: SKIPPED per task.
- staged: 9 files, +391/-4 — 4 prod (PlannerCommandService, PlannerSyncEventService, SseEventType,
  GlobalExceptionHandler), 3 test (DegradationIT, SseFanoutIT, new TombstoneRollbackIT), 2 docs (this
  ledger, verification.md). Independent suite BUILD SUCCESSFUL 5m54s, sum_failures=0. Commit is the
  user's (proposal returned; not run). status.json intentionally untouched (ad-hoc, non-numbered).
