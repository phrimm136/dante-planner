# Results — Backend DDD/DDIA Refactor (B0–B12)

## What Was Done

Executed STEP 0 → B3+B4+B5 → B1+B9+B10 → B7 → B6 → B2. **B12 (package-by-feature) skipped** by user decision (cosmetic 282-file reorg, deferred). Code review run and one regression fixed. Full `./gradlew test` green throughout. Nothing committed (user commits manually after all phases).

- **Step 0 — schema-truth tier:** flipped `application-it.properties` to `flyway.enabled=true` + `ddl-auto=validate`; wired `spring.flyway.*` to the Testcontainer in `MySQLIntegrationTest`; resolved all surfaced entity↔migration drift; added query-count (N+1 guard), a real concurrent `castVote()`-through-service test (red-green-red verified), ArchUnit layer-boundary rules, and a ratcheted `jacocoTestCoverageVerification` gate (INSTRUCTION ≥0.70, BRANCH ≥0.57; baseline 70.48/57.85).
- **B3:** `ValuedEnum` + `EnumLookup`; 7 enums delegate; removed `VALID_CATEGORIES` duplicate → `MDCategory`.
- **B4:** `SseEventType` enum; 8 emit sites use `getValue()` (wire strings byte-identical).
- **B5:** `Planner.isOwnedBy(Long)`; 3 inline checks replaced.
- **B1:** `PlannerStatus{DRAFT,SAVED}` end-to-end + `PlannerStatusConverter` (lowercase, `@JdbcTypeCode(CHAR)` retained); `@ExceptionHandler(HttpMessageNotReadableException)`→400 generic; reconciled ~20 status test sites; invalid status→400 (no echo).
- **B9:** removed `@Pattern` on `Planner.category`; `isValidCategory` is the authority.
- **B10:** deduped `PublicPlannerResponse.fromEntity` overloads (no owner-field leak).
- **B7:** split 1073-LOC validator → orchestrator + 6 validators + `ValidationErrors`/`ErrorCode` + `ValidationContext` (both ThreadLocals removed); error codes/messages/order byte-identical.
- **B6:** split 1135-LOC `PlannerService` → `PlannerCommandService`, `PlannerQueryService`, `PlannerPublishingService`, `PublishedPlannerQueryService`, `PlannerEngagementService` + `PlannerAccessGuard`; atomic `incrementUpvotes` + `trySetRecommendedNotified` CAS + `@Version` + AFTER_COMMIT event preserved.
- **B2:** `AuthProviderType{GOOGLE,APPLE,SYSTEM}` + converter; convert String↔enum only at the `User` edge.

## Two production bugs surfaced & fixed (not in spec)
1. **`NotificationService.notifyPlannerRecommended` lacked `@Transactional(REQUIRES_NEW)`** (its 3 siblings have it). The AFTER_COMMIT listener ran with no live transaction under plain REQUIRED, so the notification insert never committed — recommended-threshold notifications were **never delivered in production**. Fixed; proven by the sequential + concurrent castVote tests.
2. **`planner_comments.depth` TINYINT vs entity `int MAX_DEPTH=Integer.MAX_VALUE`** ("unlimited depth"). Production caps at 127 but the code believed unbounded. Resolved per user choice: `@JdbcTypeCode(TINYINT)` + `MAX_DEPTH=127`.

## Documented, not fixed (out of scope)
- **`UserSettings` N+1** on the published-list read path (author's LAZY `@OneToOne` loaded once per row). Query-count test locks the slope so B6/B10 can't add a *new* per-row query.
- Pre-existing security findings in untouched handlers: `GlobalExceptionHandler:235` logs full request body at WARN (CWE-532); `MethodArgumentNotValid` echoes field names; `OAuthException`/`RateLimitExceeded` echo internal detail; `PublishedPlannerDetailResponse` exposes `status`+`syncVersion` publicly.

## Files Changed
82 backend files (220 main + 62 test scope). Key: 5 new services + guard; 6 new validators + support; PlannerStatus/AuthProviderType/SseEventType/ValuedEnum/EnumLookup enums; 2 converters; `V042__widen_notification_timestamps_to_microseconds.sql`; `PlannerController` rewired (dead `PlannerSyncEventService` injection removed in review); `build.gradle.kts` (archunit dep + jacoco gate); `application-it.properties` (flyway+validate); new tests `PlannerQueryCountTest`, `LayerBoundaryTest`, converter tests, concurrent-castVote, status round-trip. `PlannerService.java` deleted.

## Verification
- Build: **pass**. Tests: **pass** (`./gradlew test` BUILD SUCCESSFUL, Docker up, Tier 3 ran).
- Keystones explicitly green: ConcurrentCastVoteTests (1), PlannerQueryCountTest (3), LayerBoundaryTest (3), it-tier UNIQUE-provider (3). Coverage gate active and passing.
- Red-green-red verified on the concurrent castVote test (read-modify-write break → RED via @Version optimistic-lock failure → restored).

## Issues & Resolutions
- `ddl-auto=validate` failed first on a Flyway connection error → wired `spring.flyway.*` to the container.
- 5 MySQL ENUM/SET columns + depth TINYINT failed `validate` → `@JdbcTypeCode(CHAR/TINYINT)` (user chose keep-DB-adapt-entity).
- `status="published"` test value rejected by `ENUM('draft','saved')` → `"saved"` (B1 reconciliation pulled forward).
- Notification-miss under concurrency was misdiagnosed as a threshold-gate race; empirical bisection (sequential crossing also failed; `recommendedNotifiedAt` flag DID set) proved it was the missing REQUIRES_NEW.

## Learnings
- **`ddl-auto=validate` is the highest-leverage test change here.** It surfaced 3 real production issues (missing REQUIRES_NEW via the concurrent test it enabled, depth TINYINT, timestamp precision) that every prior test profile (create-drop) masked.
- **Gradle does not capture application stdout/logs in this project's test output** — log-based debugging was a dead end; assertion-based bisection (the `recommendedNotifiedAt` probe) found the real cause.
- **Diff every "behavior changed" review finding against `git show HEAD`.** 3 reviewers guessed the original was better (EntityGraph fetch, return-null-on-null, restriction ordering); all 3 were wrong. Applying them would have *changed* behavior.
- MySQL `ENUM`/`SET` report to JDBC as `CHAR`; entity String/@Enumerated map to `VARCHAR` → `@JdbcTypeCode(SqlTypes.CHAR)` reconciles without a schema change. Length is not strictly validated.
- AFTER_COMMIT listeners need `REQUIRES_NEW` to actually commit their writes.

## Spec Divergence

### What Changed
- **Step 0 drift scope** → spec implied `validate` mainly catches *accidental* drift and B1 needs "no migration." Reality: `validate` flags **every** deliberate MySQL ENUM/SET column (`status`, `planner_type`, `vote_type`, `entity_type`, `selected_keywords`) + `depth` TINYINT + notification timestamp precision. Resolved with `@JdbcTypeCode` annotations (keep-DB) + one corrective migration (V042).
- **B2 invalid provider → 400** → stays **403** (pre-existing). `provider` is never deserialized as the enum (it stays a String for the OAuth registry), so the B1 `HttpMessageNotReadableException`→400 handler doesn't apply; forcing 400 would break `AuthenticationFacadeTest`. Behavior-neutral won.

### What Was Added (Not in Spec)
- `AuthProviderType.SYSTEM("system")` — the `provider` column has a `system` sentinel row (V009 deleted-user). A converter must be total over the column or it throws on every query materializing that row. Not foreseeable without running `validate` against the real schema.
- `V042` migration (timestamp precision 6) and the Flyway-on-container test wiring — both emerged from the schema-truth tier.
- Two production bug fixes (REQUIRES_NEW; depth cap).

### What Was Dropped
- **B12 package-by-feature** — user decision: cosmetic 282-file reorg, deferred to a dedicated session; package-by-layer retained. All functional smell-fixes (B1–B7) landed.

### Wrong Assumptions
- Spec: "`status` column unchanged → no Flyway migration" (B1) — true for `status`, but the schema-truth tier required `@JdbcTypeCode(CHAR)` on it and 4 sibling columns.
- Spec: provider is a closed set `{GOOGLE, APPLE}` — the persisted domain is `{google, apple, system}`.
- Spec: the concurrent test would assert "many cross at once → CAS picks one winner" cleanly — the real blocker was a missing-REQUIRES_NEW notification-commit bug, not the CAS.

### Prompting Retrospective
- **Schema truth**: "Before assuming any column needs no migration, what does `ddl-auto=validate` against the real Flyway schema actually report for every ENUM/SET/special-typed column and every sentinel/seed row?"
  - Why: would have surfaced the 6 ENUM/SET columns, depth, timestamp precision, and the `system` sentinel up front instead of one-at-a-time during execution.
- **Notification delivery**: "Does each `notify*` method that runs in an AFTER_COMMIT listener use `REQUIRES_NEW`? Is recommended-notification delivery actually exercised by a committing (non-rollback) test?"
  - Why: would have caught the never-delivered recommended notification before the refactor.
- **Decision direction for drift**: "If `validate` surfaces drift, is the intended fix to adapt the entity (keep DB) or migrate the column — per column?"
  - Why: this was the recurring product decision; pre-deciding the policy would have removed mid-execution stalls.

### Spec Process Takeaway
The spec systematically under-modeled **runtime schema truth** — what the real migrated MySQL schema (special column types, sentinel/seed rows) and the real transaction/event lifecycle (AFTER_COMMIT commit semantics) actually do, versus what the entities and create-drop tests implied.

## Session State (for continuity)
- **Uncommitted:** all 82 backend changes + docs (plan.md/status.json/results.md). Working tree green, nothing committed.
- **Current focus:** review complete, one fix applied, suite green — ready for the user's manual commit.
- **Next steps:** (1) user commits Phases 0–5; (2) optional: B12 package-by-feature in a fresh session; (3) optional: scoped security-hardening pass on the flagged pre-existing handler/DTO disclosures (prioritize the WARN-body log).
- **Blockers:** none.
