# Pre-Seoul Perf + Comment-SSE Hardening — Scenario Ledger

Two pre-Seoul Gate hardening fixes from the Phase-12 audit (`docs/portfolio/pre-seoul-gates.md`), each RED->GREEN. Not a numbered plan phase — status.json untouched.

Dossiers: /home/user/.local/state/claude-build/LimbusPlanner/034-multi-region-k8s-architecture/hardening-perf-comment/

Base HEAD at open: 16f3e5b0 (tree green). Sandbox has Docker (Testcontainers run); no localhost Redis, so containerized ITs are gated, not full-context localhost @SpringBootTest.

## Scenarios
| # | Scenario (one line) | Status | Red proof | Green proof |
|---|--------------------|--------|-----------|-------------|
| 1 | Gate 2 — Hibernate write batching: importPlanners issues a BOUNDED prepared-statement count (~ceil(N/batch_size)), not N | closed | assertion: HibernateInsertBatchingIT.java:158 — observed 43 prepares (N=40) > threshold 12; JUnit XML tests=1 failures=1 errors=0 | green: scoped --tests run BUILD SUCCESSFUL (RED was BUILD FAILED); 4 hibernate.* batching props added to application.properties; config-only, no service change |
| 2 | F3b — comment SSE cross-pod fan-out: a real CommentService.createComment publishes a comment envelope to Redis, delivered cross-node to comment subscribers | closed | assertion: SseFanoutIT.java:167 WantedButNotInvoked broadcast(plannerId,"comment:added",..); write path called broadcastCommentAdded (CommentService:253); JUnit XML tests=5 failures=1 errors=0 (4 others green) | green: SseEventType.COMMENT_ADDED added; CommentService.createComment/createReply reroute via ssePublisher.publishCommentEvent(COMMENT_ADDED, publicId, CommentTreeNode payload); broadcastCommentAdded removed; SseFanoutIT 5/0, retargeted char trio 3/0 |

## Settled decisions
- **S1 driver:** `PlannerCommandService.importPlanners` (audit Finding 2). Same single config also fixes `reindex` (Finding 1, hot path) — config is global, so one batching assertion proves it active. Test = new containerized IT modeled on `PlannerQueryCountTest` (SessionFactory Statistics, `generate_statistics=true`, `getPrepareStatementCount()`).
- **S1 fix:** add to `backend/src/main/resources/application.properties` the four `spring.jpa.properties.hibernate.*` batching props (jdbc.batch_size=30, order_inserts=true, order_updates=true, batch_versioned_data=true). No service code change expected (assigned keys, isNew=true → clean persist, no interleaved read/flush).
- **S2 wire contract:** FE `usePlannerCommentsSse` ALREADY parses a full `SseEnvelope` on event name `comment:added` and patches `envelope.payload as CommentNode`. Backend never fed it (dead in-process path). Reroute must preserve event name `comment:added` and carry a full `CommentTreeNode` payload.
- **S2 event type:** add `SseEventType.COMMENT_ADDED("comment:added")` so the subscriber's `broadcast(plannerId, type.getValue(), envelope)` emits event name `comment:added` unchanged.
- **S2 fix:** inject `SsePublisher` into `CommentService`; in `createComment` and `createReply` replace `plannerCommentSseService.broadcastCommentAdded(plannerId, deviceId)` with `ssePublisher.publishCommentEvent(plannerId, SseEventType.COMMENT_ADDED, <comment publicId>, <CommentTreeNode payload>)`. Remove the in-process `broadcastCommentAdded` method (do NOT keep both — writer pod would double-deliver). Keep the `deviceId` params (unused by SSE now) to avoid controller-signature ripple.
- **S2 test coverage:** retarget the 3 `PlannerCommentSseServiceTest` characterization tests (dead-emitter removal, no-subscribers no-op, serialization-failure swallow) from the removed `broadcastCommentAdded` onto the surviving `broadcast(plannerId, eventName, payload)` — coverage preserved, testing the live cross-node delivery method. Update `CommentServiceTest`'s manual `new CommentService(...)` call for the added `SsePublisher` arg.

## List Revisions
- (none — implementation matched the two-scenario plan)

## Brief-authoring gap (S2 green)
- CommentService's constructor has 4 test-side callers, but the S2 GREEN Brief named only CommentServiceTest. The green spawn had to mechanically add the `SsePublisher` ctor arg to CommentControllerTest and CommentServiceNotificationTest too (compile-forced, no assertions changed). CommentControllerTest is a full-context test that is environmentally RED in this sandbox (needs localhost Redis) — pre-existing, verified identical at pristine HEAD, not a regression; excluded from the independent green run per the Testcontainers/scoped gating constraint.

## Independent burndown-close run (my own execution)
Command: backend/gradlew -p backend test --tests {CommentServiceTest, PlannerCommentSseServiceTest, CommentServiceNotificationTest, HibernateInsertBatchingIT, SseFanoutIT, PlannerCommandServiceTest, TestNamingConventionTest, PlannerIndexServiceTest}
Result: BUILD SUCCESSFUL in 1m 38s — TOTALS tests=82 failures=0 errors=0. SseFanoutIT 5/0, HibernateInsertBatchingIT 1/0, PlannerCommentSseServiceTest 12/0 (BroadcastCommentAdded trio 3/0), CommentServiceTest 17/0, CommentServiceNotificationTest 6/0, PlannerCommandServiceTest 31/0, PlannerIndexServiceTest 9/0, TestNamingConventionTest 1/0.

## Pipeline (post-burndown)
- refactor: done — CommentService.java only (+19/-4): extracted verbatim-duplicated cross-node fan-out block from createComment/createReply into private publishCommentCreated(...) helper; suite green (4 classes), test files byte-unchanged (diffs match green-phase counts).
- verify: PASS — verification.md "## Pre-Seoul Perf + Comment-SSE Hardening — PASS"; both items MET; scoped Testcontainers suite green 82/0/0. Closes Gate 2 Findings 1&2 (REMEDIATED) and F3 comment leg (DEFERRED→MET).
- capture: skipped (task constraint: no meme).
- staged: 12 files, 305 insertions(+), 44 deletions(-) — 4 production (CommentService, PlannerCommentSseService, SseEventType, application.properties), 6 test (HibernateInsertBatchingIT new, SseFanoutIT, CommentServiceTest, CommentServiceNotificationTest, PlannerCommentSseServiceTest, CommentControllerTest), 2 docs (this ledger, verification.md). status.json deliberately NOT touched (extra hardening, not a numbered phase). Commit is the user's.

## Cosmetic residue (not fixed — out of phase-manager write scope)
- PlannerCommentSseServiceTest.java:83 @Nested @DisplayName still reads "broadcastCommentAdded" though its 3 tests now exercise broadcast(...). Test code, not a doc; cosmetic; coverage intact. Candidate follow-up.
