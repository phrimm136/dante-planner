# Execution Plan

## Phase Summary

Execute docs/32 spec in 15 sequential execution phases (spec phases 1-8 decomposed to code-writer-sized units). Strategy: consolidate before relocating; characterization tests before every transformation they protect; mass sweeps late (clean baseline for the static-analysis ratchet); pure relocations (phase 14) executed by the main session directly, never by code-writer agents (agents Read→Write and drift content on pure moves — proven FE-playbook rule).

Cross-cutting considerations (apply to EVERY phase):
- **All subagents launch with `model: opus`** (user decision, 2026-07-02): agents inherit the session model (Fable) by default, which burns session budget; pass the model explicitly on every Agent call. A Fable-inherited phase-2 agent died on the session limit mid-implementation.
- **Concurrent FE session**: docs/31 executes in the main working tree. This build runs in an isolated git worktree. Never touch `frontend/`, root `.gitignore`, root `CLAUDE.md`, or `.claude/rules/frontend/**`. `backend/CLAUDE.md` currently carries an uncommitted FE-session edit in the MAIN tree — phase 15's doc-sync must rebase/merge-check against whatever docs/31 landed by then.
- **Gradle invocation**: `backend/gradlew -p backend <task>` from repo root, output redirected to `/tmp/be-<task>-<session-id>-<suffix>.log`. Never `cd`. Full suite green at every phase boundary (INV1).
- **Verify agent claims**: this task's audits produced 4 confidently-wrong findings. Any claim a phase inherits (counts, file:line) is re-verified against source before acting. Counts measured 2026-07-02.
- **Wire contract (INV2)**: existing Tier-2 jsonPath tests are never modified to make a phase pass. A failing jsonPath test means the phase broke the contract — fix the code.
- **Commits deferred to end of build (user decision, 2026-07-02)**: phases execute and verify in the MAIN tree without committing; the FE session works in different areas concurrently. NO git add/commit/reset/checkout during phases. At build end, commit in logical batches via commit-process with `git commit -- <pathspec>` discipline (the index is shared — plain `git commit` sweeps the FE session's staged files; happened once, recovered). The sweep batches (rename, DI, records, repackaging) still get separate end-commits so `.git-blame-ignore-revs` can list them.

## Phases

### Phase 0: Land loose BE files (main tree, done by main session before worktree)
- Files: `docs/tasks/032-be-pattern-consolidation/*` (spec, plan, status), `backend/src/main/resources/application-loadtest.properties`, `backend/src/test/java/org/danteplanner/backend/exception/PlannerValidationExceptionTest.java`, `backend/.gitignore` (new: `bin/`)
- Tests: none (bookkeeping)
- Considerations: do NOT stage `backend/CLAUDE.md` (FE-session-owned edit) or any `frontend/`/root dirty files. Stage by explicit pathspec only.
- Depends on: none
- Verify: `git status` shows the named files committed; FE session's dirty files untouched

### Phase 1: Hygiene (spec §1)
- Files: delete `scripts/test-auth-endpoints.sh`; create `scripts/lib/load-test-shared.js`; modify `scripts/load-test.js`, `scripts/load-test-sustained.js`
- Tests: none (k6 scripts run only against a live server)
- Considerations: the shared `setup()` + endpoint list is byte-identical between the two scripts (verified) — extraction is data-varying, allowed. `load-test-sse.js` has a different setup; leave it. k6 uses ES-module imports; keep the helper import-compatible with k6's runtime (no Node-only APIs).
- Depends on: Phase 0
- Verify: both scripts import the helper; duplicated block gone; `git diff` shows only the extraction

### Phase 2: SSE characterization tests (spec §2 precondition)
- Files: create `backend/src/test/java/org/danteplanner/backend/service/SseServiceTest.java`, `service/PlannerCommentSseServiceTest.java`, `controller/SseControllerTest.java`, `controller/PlannerCommentSseControllerTest.java`
- Tests: this phase IS tests
- Considerations: pin CURRENT behavior — these tests must pass against unmodified services, then survive phase 3 unmodified (that is their entire purpose). Cover: subscribe lifecycle (emitter registered, initial event), heartbeat send + failure-triggered removal, zombie cleanup probe, per-planner 500-connection cap rejection (PlannerCommentSseService), removeConnection idempotency, JSON serialization error path. Controllers: auth required, `text/event-stream` content type, cap rejection status. Read both services fully first; do not guess constants — use the real ones (heartbeat interval, cap) referenced from their source. Service tests: prefer plain Mockito (emitters are in-memory maps); use Spring only if constructor deps force it.
- Depends on: Phase 0
- Verify: new tests green against UNMODIFIED services; full suite green

### Phase 3: SSE base extraction (spec §2)
- Files: create base class in `backend/src/main/java/org/danteplanner/backend/service/` (name per house conventions, e.g. `SseEmitterLifecycleSupport`); modify `SseService.java`, `PlannerCommentSseService.java`
- Tests: phase 2's tests, UNMODIFIED
- Considerations: ~140 LOC duplicated across `SseService.java:77-298` / `PlannerCommentSseService.java:62-222`: subscribe wiring, heartbeat loop, zombie cleanup, removal, JSON-error handling. Keys differ (`Long userId` vs `UUID plannerId`) → generic type parameter. The capacity cap exists ONLY in PlannerCommentSseService — it stays in the subclass. `@Scheduled` methods don't inherit cleanly; keep the `@Scheduled` entry points in subclasses delegating to base methods.
- Depends on: Phase 2
- Verify: phase-2 tests pass with zero edits; full suite green; duplicated blocks gone

### Phase 4: Consolidation batch — adopt existing primitives (spec §2)
- Files: `CommentService.java` (inject PlannerAccessGuard, delete `checkUserRestrictions` :60-71; `isOwnedBy()` at :90), `PlannerCommandService.java` (ErrorCode enum at :113,190,299,393), `PlannerControllerTest.java` (delete VALID_CONTENT dup :140, use TestDataFactory's), `NotificationService.java` (`createAndPush` helper collapsing :137-259 triplet; move class-level `@Transactional` :35 to methods), `GlobalExceptionHandler.java` (private `warnAndRespond(status, code, msg)` for the 3 handler families), `PlannerRepository.java` (duplicated `p.takenDownAt IS NULL` predicate :89 + countQuery :93 — grep ALL `*WithSearch`/`*ByCategory`/recommended siblings for the same paste), 2 missing service Javadocs
- Tests: extend `NotificationServiceTest` (helper: save/log/push/duplicate-suppression paths)
- Considerations: the predicate fix is semantics-identical (idempotent duplicate) — jsonPath/repo tests must not change. ErrorCode: use the existing enum's accessor; verify the enum value's code string equals the literal being replaced (behavior-preserving). PlannerAccessGuard vs CommentService's copy: diff the two check orders first — if order differs (timeout-before-ban vs reverse), preserve CommentService's observable exception precedence or verify both orders throw identically for dual-state users.
- Depends on: Phase 0
- Verify: full suite green; grep confirms zero remaining magic `"INVALID_CATEGORY"` strings, zero duplicated predicates

### Phase 5: Entity transitions (spec §2)
- Files: `entity/Planner.java` (add `takeDown()`, `hideFromRecommended(Long moderatorId, String reason)`, `unhideFromRecommended()`, `togglePublished()`, `recordSave()`, `unpublish()`); rewire `ModerationService.java:272-273,315,465-468,492-495`, `PlannerPublishingService.java:77,92,107`, `PlannerCommandService.java:220-221,318-319,351`; dedupe the twin field-copy blocks `PlannerCommandService:182-216`/`:290-314` into one private method
- Tests: create `entity/PlannerTransitionTest.java` (Tier-1, no Spring)
- Considerations: THE decision procedure — precondition or multi-field consistency → entity method; value-copy mapping → stays in service. `togglePublished()` owns the taken-down guard (throw the same exception type currently thrown at PlannerPublishingService:77 — check whether entity throwing a service-layer exception type creates a bad dependency; if `PlannerValidationException` lives in `exception/` (neutral package), it's fine). Orchestration (auto-subscribe, first-publish notification, SSE) stays in the service. `firstPublishedAt` stamping is one-time — inside `togglePublished()` only when transitioning to published and field is null. Preserve `:216`'s takedown-preservation guard in the deduped mapping method with a why-comment. Do NOT remove setters yet (phase 9 locks down). Transition tests: taken-down + publish → throws; takeDown implies unpublished; hide sets all 4 fields; unhide clears all 4; recordSave bumps syncVersion and stamps savedAt.
- Depends on: Phase 4 (same files churn)
- Verify: full suite green; PlannerTransitionTest green; grep shows no remaining raw setter pairs at the rewired sites

### Phase 6: Wire-pinning tests — moderation + admin (spec §3 precondition)
- Files: create `controller/ModerationControllerTest.java`; verify `AdminController` coverage in `AdminModerationControllerTest`, create `controller/AdminControllerTest.java` if gaps
- Tests: this phase IS tests
- Considerations: pattern source = existing controller tests (@SpringBootTest + @AutoConfigureMockMvc + TestDataFactory — the chartered Tier 2; do NOT use @WebMvcTest). jsonPath-pin every response field of timeout/ban/hide/unhide flows BEFORE phase 7 converts their DTOs. Explicitly assert `usernameSuffix` present and no raw `userId` in TimeoutResponse (recorded privacy convention). These tests must pass against current @Data DTOs, then survive phase 7 unmodified.
- Depends on: Phase 0
- Verify: new tests green; full suite green

### Phase 7: Records migration (spec §3)
- Files: all 23 `@Data` DTO classes → records; relocate root-level `LoginResponse.java`, `UserDto.java`, `RefreshTokenRequest.java` into `dto/<feature>/`; update mapping call sites
- Tests: existing + phase-6 jsonPath tests, UNMODIFIED; add `Set`-immutability assertions for planner DTOs carrying `selectedKeywords`
- Considerations: wide DTOs (PlannerResponse family, 10+ fields) keep named-field construction via `@Builder` ON the record — mapping call sites stay `.builder()...build()`, minimal churn. Narrow request DTOs: plain canonical constructor; Jackson deserializes records via canonical ctor natively — but VERIFY against the custom JacksonConfig (naming strategy) with the existing wire tests. Bean Validation annotations move to record components. `Set<String>` components get compact-constructor `Set.copyOf` (null-tolerant: guard null before copyOf if the field is optional — check each DTO's nullability against its current usage). Batch by feature (comment → user → moderation/admin → planner last, it's widest); full suite between batches.
- Depends on: Phase 6
- Verify: full suite green per batch, jsonPath tests unmodified; zero `@Data` under `dto/`

### Phase 8: DI sweep + lombok.config (spec §3)
- Files: 37 classes with assign-only explicit constructors → `@RequiredArgsConstructor`; create `backend/lombok.config` (`lombok.addLombokGeneratedAnnotation = true`)
- Tests: existing suite + Jacoco gate (INV8)
- Considerations: skip any constructor with logic, `@Qualifier`, `@Value` params, or Javadoc carrying real information — convert ONLY pure field-assignment constructors (re-verify the 37 count; audit was 2026-07-02). Run Jacoco verification before AND after — the lombok.config must land in the same commit so the coverage gate never sees generated-code distortion.
- Depends on: Phase 0
- Verify: `backend/gradlew -p backend check`-equivalent (test + jacocoTestCoverageVerification) green; zero assign-only explicit constructors remain

### Phase 9: Entity @Data removal + setter lockdown (spec §3)
- Files: `entity/User.java`, `Planner.java`, `UserSettings.java`, `ModerationAction.java`
- Tests: pre-step rewrites in existing test files; suite green after
- Considerations: TWO-STEP, strictly ordered. Step A: grep ALL tests for whole-entity `assertEquals`/`assertThat(x).isEqualTo` on the 4 classes; rewrite each to field-level assertions (silent-weakening hazard: same-id-different-fields entities would start passing equality after the swap — the compiler cannot find these; the grep must). Step B: swap `@Data` → `@Getter` + explicit setters ONLY for fields the mapping method (phase 5) still writes; moderation/publishing fields (`takenDownAt`, `published`, `hiddenFromRecommended`, `hiddenByModeratorId`, `hiddenReason`, `hiddenAt`, `firstPublishedAt`) get NO setters — the phase-5 transitions are the only paths (INV7). `User.java:47`'s `@Setter(AccessLevel.NONE)` contradiction dissolves. Keep `@Builder` (TestDataFactory + services construct via builder). equals/hashCode: entities not used in hash collections in main code (verified); do not add custom equals — reference equality is correct for JPA entities within a session.
- Depends on: Phase 5 (transitions must exist before setters vanish)
- Verify: compile catches every orphaned setter call; full suite green; no `@Data` under `entity/`

### Phase 10: Planner test split + Thread.sleep fixes (spec §3)
- Files: dissolve `service/PlannerServiceTest.java` into `PlannerCommandServiceTest`, `PlannerQueryServiceTest`, `PublishedPlannerQueryServiceTest`, `PlannerPublishingServiceTest`, `PlannerEngagementServiceTest`; fix sleeps in `JwtTokenServiceTest:258,430` (Clock seam), `VoteNotificationFlowTest:298`, `MySQLIntegrationTest:240`, `RefreshRotationServiceTest:368`
- Tests: the split IS tests; coverage mapping documented in commit message
- Considerations: coverage mapping is bidirectional — every assertion in the old aggregate maps to a successor file (forward), and every public method of the 5 services maps to at least one test (reverse; write the missing ones — engagement/publishing methods the aggregate never saw). Clock seam: inject `java.time.Clock` into `JwtTokenService` (default `Clock.systemUTC()` bean or field default — match existing config-class conventions); tests use `Clock.fixed`/offset. Timestamp-based sleeps: replace with explicit `setCreatedAt` staggering (recorded house pattern). `VoteNotificationFlowTest` is `@Disabled` with a documented AFTER_COMMIT reason — leave the disable, still remove the sleep.
- Depends on: Phase 5 (service call-sites stable)
- Verify: full suite green; old test file gone; mapping in commit message

### Phase 11: Mass sweeps — test rename + factory adoption (spec §3)
- Files: ~40 test files (304 method renames to `methodName_WhenCondition_ExpectedBehavior`), 52 test files (hand-rolled `User.builder()`/`Planner.builder()` → `TestDataFactory`)
- Tests: the suite itself
- Considerations: renames are mechanical but MUST re-verify counts first (phases 2,6,10 added/changed test files since the audit). Factory adoption: extend `TestDataFactory` first where a hand-rolled builder sets fields the factory lacks — never lose field coverage silently. `@Nested` class names and `@DisplayName` stay. Record this commit's SHA for `.git-blame-ignore-revs`.
- Depends on: Phase 10 (test tree stable)
- Verify: full suite green; Checkstyle-regex dry-run (phase 12's rule) reports zero violations — proves the sweep is ratchet-ready

### Phase 12: Controller split (spec §4)
- Files: split `controller/PlannerController.java` (538 LOC) along service seams (CRUD/command, publishing, engagement); SSE endpoint placement follows its seam
- Tests: existing controller tests unmodified
- Considerations: zero URL changes — Spring composes mappings across classes; keep every `@RequestMapping` path + method + status identical. The existing `PlannerControllerTest` may split too (mirror), but assertions are untouched. Route-inventory check: grep all mapping annotations before/after into sorted lists and diff.
- Depends on: Phase 7 (DTOs settled — avoids double churn)
- Verify: mapping-annotation inventory diff empty; full suite green

### Phase 13: Static-analysis ratchet (spec §5)
- CARRIED FROM PHASE 11: Checkstyle test MethodName regex MUST be exactly `^[a-z][A-Za-z0-9]*(_[A-Za-z0-9]+){2,}$` with `^test[A-Z]` excluded, scoped to the test source set. Phase 11 swept 281 names to this exact pattern (digit-tolerant segments, lowercase segments allowed for production-method-name prefixes like `shouldNotFilter`). Any other regex breaks the zero-suppression baseline. If scoping to `*Test.java` (excluding `*Tests.java`), the `contextLoads`->`applicationContext_WhenStarted_LoadsSuccessfully` rename was optional.
- Files: `backend/build.gradle.kts` (checkstyle, error-prone plugin, PMD/CPD wired into `check`), `backend/config/checkstyle/checkstyle.xml`, CI `ci-backend.yml` step → `check`
- Tests: `backend/gradlew -p backend check` green with ZERO suppressions
- Considerations: Checkstyle rules scoped to what the sweeps standardized: test `MethodName` regex on test source set only; import order per backend CLAUDE.md; Javadoc on public `@Service` classes; `ImportControl` banning `lombok.Data` in `entity/`+`dto/`. Error Prone: enable with Lombok compatibility (generated code excluded via the phase-8 annotation); start with default ERROR checks only — no experimental. CPD: threshold tuned so current post-consolidation code passes (~100 tokens); it's a ratchet, not an archaeology tool. If any rule fires on existing code, FIX THE CODE, never suppress — a nonzero baseline invalidates the phase's premise. ci-backend.yml is BE-owned; check the FE session hasn't queued edits to it (git log/status) before modifying.
- Depends on: Phases 7, 8, 9, 11 (the sweeps it ratchets)
- Verify: `check` green locally; CI workflow updated; deliberately-introduced violation fails locally (then reverted)

### Phase 14: Package-by-feature (spec §6) — EXECUTED BY MAIN SESSION, NOT code-writer
- Files: every `main/` and `test/` class → `planner/ comment/ user/ auth/ moderation/ admin/ notification/ shared/` with internal layer subpackages; feature exceptions to owners; test tree mirrors
- Tests: full suite per feature batch
- Considerations: pure relocation — `git mv` + package-line + import rewrites ONLY (playbook rule: agents drift content on moves; main session executes with sed/LSP). One feature per batch, suite green between. Batch order: leaf-most first (notification → admin → moderation → comment → auth → user → planner → shared last... verify actual dependency order from imports before starting; shared extracted FIRST may be cleaner — decide from the import graph, document in batch commits). After each batch: INV3 (`git status` shows R/RM; diffs are package/import lines only), INV4 (FQCN grep over resources+workflows — baseline 2 base-package logging lines, unaffected). ArchUnit `..service..` matchers keep working (layer subpackages preserved).
- Depends on: Phase 13 (ratchet active during moves)
- Verify: suite + check green; grep for `org.danteplanner.backend.(controller|service|repository|entity|dto)\.` old-path imports empty; INV3/INV4 clean per batch

### Phase 15: Boundaries, index hygiene, doc truth-sync (spec §7-8)
- Files: create `architecture/FeatureBoundaryTest.java` (edge allowlist: comment→planner; moderation→planner+comment+user; notification→planner+user; *→auth; *→shared; shared-is-sink); `db/migration/V031__drop_redundant_published_index.sql` (EXPLAIN-gated); entity `@Index`↔migration parity sweep (all entities); `backend/CLAUDE.md` truth-sync; `.claude/rules/backend/**` refs; `.git-blame-ignore-revs` (sweep SHAs from phases 7,8,11,14)
- Tests: FeatureBoundaryTest in suite; Tier-3 green with V031
- Considerations: EXPLAIN gate for V031 — stand up the loadtest DB (docker-compose.loadtest.yml), seed volume, `EXPLAIN` the published-list queries + check `sys.schema_unused_indexes`; if the environment can't run, the drop is DECLINED and documented (spec allows). Edge rules encode the CURRENT graph — freeze, don't tighten; run against post-move packages. Doc-sync: backend/CLAUDE.md has an FE-session edit in main tree — pull/rebase latest before editing; remove dead Quick-Reference paths + interface mandate; `unit-tests.md` reference update. Do NOT edit `.claude/rules/deployment/wrangler.md` (FE-owned) — leave a note for the user instead.
- Depends on: Phase 14
- Verify: full suite + check green; all Done-When boxes in requirements.md checkable

## Phase Dependencies
### Phase 14 progress (in-flight, executed directly by main session)
- Feature order: notification [DONE, compile-green] → admin → moderation → auth → comment → user → planner → shared (leaf-first; shared last so features keep importing old config/util/security paths until the end).
- Per-feature mechanic (validated on notification pilot): (1) mkdir feature/<layer> dirs; (2) git mv each file; (3) sed package decl on moved files; (4) global sed rewrite `import ...<oldpkg>.<Class>;`→`import ...<feature>.<layer>.<Class>;` across main+test; (5) compile-fix the THREE straggler classes: (a) same-package refs that had no import (moved class referenced a sibling left behind, or a stayer referenced the moved class), (b) wildcard-import gaps (`entity.*` no longer covers a moved class → add specific import), (c) inline FQCNs in code bodies (phase-4/9 added some) → sed the FQCN too; (6) compileJava+compileTestJava green = checkpoint.
- Gotchas: run seds via `bash -c` (zsh chokes on `${x%.*}`); guard the `Notification\b` word-boundary rewrites against double-application; NEVER `git checkout .` (uncommitted phases 1-13 would be lost) — revert individual HEAD-unchanged files via pathspec `git checkout HEAD -- <file>` only.
- Empirical cost: notification (smallest, 9 files) took ~10 tool calls incl. compile-fix cycles. Planner (~90+ files) will be far larger.

Sequential throughout (shared files + one worktree). Nominal parallelism exists (1‖2‖6, 4→5 vs 8) but is deliberately not used: phases are cheap relative to conflict risk, and one-phase-one-commit discipline requires ordered history.
