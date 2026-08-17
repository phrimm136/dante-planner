# Task: BE Pattern Consolidation — Slop Cleanup, Idiom Sweeps, Package-by-Feature, Ratchets

BE-only. Frontend refactoring and repo-root hygiene are owned by `docs/tasks/031-fe-structure-refactor/` (a parallel session); this task must not touch `frontend/` or root-level files owned by that spec.

Prerequisite: the uncommitted lineage-rotation work must be landed or shelved before phase 1 — mass renames under uncommitted changes produce unrecoverable merge conflicts.

Note: `docs/spec.md` data-driven-feature sections (data catalog, normalization layer, rendering modes) do not apply — this task consumes no raw game data.

Counts in this spec (23 DTOs, 37 constructors, 304 test names, 52 test files, 5 sleeps, 36 setter calls) were measured 2026-07-02 by session audits. Re-verify scope at execution; the parallel FE session and lineage-rotation landing may shift the baseline.

## Decisions

- **Consolidate patterns BEFORE relocating files** — moving N copies into feature packages first means fixing the same duplication across N packages later; repackaging (phase 6) moves survivors, not slop (evidence: docs/31 ratified the same ordering; `useIdentityListData` triplet precedent).
- **Behavior-preserving scope** — no business rule, API contract, or DB schema changes; "invariants not edited" = the rules stay, only their homes and idioms change (user decision).
- **DTOs: migrate all 23 `@Data` classes to records** — immutable requests pin validated state (no post-`@Valid` drift); immutable responses kill aliasing bugs; `@Builder` on records (Lombok ≥1.18.20, Java 21 verified in `build.gradle.kts:15`) keeps named-field construction on wide DTOs; `Set` components get compact-constructor `Set.copyOf` defensive copies (user decision after trade-off review).
- **DI: `@RequiredArgsConstructor` across all 37 explicit-constructor classes** — a constructor that only assigns fields is ceremony, not code; generate ceremony, hand-write only constructors needing logic or `@Qualifier` (user decision).
- **Test naming: mass rename now** — all 304 divergent test methods to `methodName_WhenCondition_ExpectedBehavior` (user decision).
- **TestDataFactory: mass adoption** — all 52 hand-rolling test files converted (user decision).
- **Entities (taste): named state transitions move into the entity; field-copy mapping stays in services.** The decision procedure: a mutation with a precondition or multi-field consistency rule is a state transition — name it, move it in, remove the setters so the transition is the only path; a mutation copying externally-supplied values with no cross-field invariant is mapping — it stays at the boundary, because entities must not consume request DTOs. Entity methods touch own fields only; orchestration (repositories, notifications, SSE) never enters the entity.
- **Entity Lombok: `@Data` off the 4 entities (`User`, `Planner`, `UserSettings`, `ModerationAction`) → `@Getter` house style** (matching `Notification`/`PlannerReport`/`PlannerCommentReport`), setters retained only where mapping needs them — moderation/publishing fields get no setters back (evidence: `@Data` equals/hashCode/toString on JPA entities is a latent bug class; `User.java:47` already contradicts itself with `@Setter(AccessLevel.NONE)` under `@Data`).
- **Characterization before transformation (taste)** — any class a sweep or extraction touches must have its observable behavior pinned at the granularity that change can break, BEFORE the change: SSE services get characterization tests before base extraction; every endpoint whose DTOs convert gets a Tier-2 wire test first; whole-entity `assertEquals` in tests is rewritten to field assertions before entity equality semantics change.
- **Test tiers stay as chartered** — controller tests keep `@SpringBootTest`+MockMvc (Tier 2 owns bean wiring + JSON wire contracts); the audit's `@WebMvcTest`/`@DataJpaTest` conversion recommendation is rejected as contradicting the recorded 3-tier policy.
- **Rejected consolidations (rule of three / structure-varying)**: generic counter-update service and sentinel-query base repo (4 readable one-liners; JPQL cannot parameterize identifiers), report-service and toggle-pattern 2-copy twins (structure-varying, copies stay), `@NoArgsConstructor` removal from `@Data`+`@Builder` DTOs (factually breaks Jackson deserialization), `@Enumerated`/`@Convert` mix (converters are deliberate value mappings).
- **Index principle (taste): indexes serve access paths, not queries; every index is write-path debt on a write-hot (autosave) table.** Few multi-purpose composites over bespoke covering indexes; selectivity decides which column earns a place (near-universal predicates like `takenDownAt IS NULL` earn nothing); EXPLAIN under realistic data is the gate — static analysis only generates candidates; rare-path queries (moderation dashboards) are allowed to scan; the index inventory must stay small enough to narrate (each index namable by its owning access path). Consequences: recommended-query 5-column index withdrawn; hidden-dashboard index skipped (user decision); `DROP INDEX idx_published` proceeds only if EXPLAIN + unused-index stats under the loadtest profile confirm redundancy.
- **Migration filenames are immutable** — `V024__CreateUserSettingsTable.sql` naming inconsistency is a documented non-fix (Flyway records version+description; renaming breaks validation on every applied database). Convention is fix-forward from V031.
- **Static-analysis ratchet after the sweeps, before repackaging** — Checkstyle (idiom), Error Prone (bug patterns), PMD/CPD (duplication regression), all under `./gradlew check`, CI runs the same command (local/CI parity; a gate that doesn't execute is a comment). Empty suppression baseline — feasible only because the sweeps just standardized everything, which is why the phase is ordered there. SpotBugs skipped (overlaps Error Prone). Sonar stays a local dashboard (default); SonarCloud is backlog.
- **Package-by-feature with internal layer subpackages** (`planner/{controller,service,repository,entity,dto,validation,event}`) — keeps the existing ArchUnit `..service..`-style matchers working unchanged (evidence: `LayerBoundaryTest.java:32,43`).
- **Feature set: `planner`, `comment`, `user`, `auth`, `moderation`, `admin`, `notification`, plus `shared/`** — `comment` and `admin` are features by the owns-a-full-stack criterion (default). `shared/` holds config, security filters, `GlobalExceptionHandler` + base exception, util, converter base — infrastructure with no domain meaning.
- **Ownership over usage-count (taste)** — code with one owner lives in that owner and is consumed cross-feature via declared edges; only genuinely co-owned, stable code enters `shared/`; shared is a dependency sink (never imports feature code), enforced by ArchUnit, which is what physically prevents the shared-kernel landfill.
- **Cross-feature repository access is kept and frozen, not banned** — `CommentService → PlannerRepository` etc. is deliberate (avoids circular services); ArchUnit encodes the current edge allowlist (comment→planner; moderation→planner+comment+user; notification→planner+user; *→auth; *→shared) so new edges are declared, not accreted.
- **Execution mechanics**: one phase = one merge = one deploy (deploy.yml deploys on main merges; small revertible deploys), observe dashboards between phases; single focused reviewer per phase (orchestrator overflows >15 files); `.git-blame-ignore-revs` lists the three mass-sweep commits (test renames, DI sweep, records migration) (default).

## Description

Complete the backend's consolidation: eliminate mechanical duplication and adopt existing-but-unadopted primitives, standardize idioms (records, DI, entity Lombok, test naming/data), move state transitions into entities, split the oversized controller, install the static-analysis ratchet, execute package-by-feature (B12), freeze boundaries with ArchUnit, and truth-sync the docs.

Phases, strictly ordered; full BE suite green at every phase boundary:

1. **Hygiene** — gitignore `backend/bin/`; resolve untracked BE files (`application-loadtest.properties`, `PlannerValidationExceptionTest`); delete `scripts/test-auth-endpoints.sh` (all-TODO stub); extract shared k6 setup helper (byte-identical ~60 LOC in `load-test.js`/`load-test-sustained.js`).
2. **Pattern consolidation** —
   - Characterization tests FIRST for `SseService` + `PlannerCommentSseService` (subscribe lifecycle, heartbeat, 500-connection cap, zombie cleanup) and SSE controllers (auth, `text/event-stream`, cap rejection) — both services currently have zero tests.
   - Extract SSE emitter-lifecycle base (~140 LOC across `SseService.java:77-298` / `PlannerCommentSseService.java:62-222`); subclasses keep routing.
   - `NotificationService` `createAndPush` helper (3 copies, `NotificationService.java:137-259`).
   - Adopt `PlannerAccessGuard` in `CommentService` (delete `checkUserRestrictions`, `CommentService.java:60-71`); `isOwnedBy()` at `CommentService.java:90`; `ErrorCode` enum for magic strings (`PlannerCommandService.java:113,190,299,393`); delete `VALID_CONTENT` dup (`PlannerControllerTest.java:140`).
   - Six named entity transitions on `Planner`: `takeDown()` (ModerationService:272-273), `hideFromRecommended(moderatorId, reason)` (:465-468), `unhideFromRecommended()` (:492-495), `togglePublished()` with the takedown guard (PlannerPublishingService:77,92,107), `recordSave()` (PlannerCommandService:220-221,318-319), unpublish (:351, ModerationService:315). Tier-1 unit tests per transition.
   - Dedupe the twin ~20-line field-copy blocks (`PlannerCommandService:182-216` vs `:290-314`) into one private mapping method (preserve the `:216` takedown-preservation guard with a why-comment).
   - `PublishedPlannerDetailResponse` ↔ `PublicPlannerResponse` mapping dedup — JSON shape byte-identical (shared mapper; record-compatible, no composition nesting).
   - `GlobalExceptionHandler` private `warnAndRespond` helper for the 3 handler families (8 handlers).
   - Fix duplicated `p.takenDownAt IS NULL AND p.takenDownAt IS NULL` predicate (`PlannerRepository:89` + countQuery `:93`; check all `*WithSearch`/`*ByCategory` siblings).
   - Normalize `NotificationService.java:35` class-level `@Transactional` to method-level; add the 2 missing service Javadocs.
3. **Idiom sweeps** —
   - Wire-pinning precondition: Tier-2 MockMvc `jsonPath` tests for every endpoint whose DTOs convert and that lacks them — ModerationController (timeout/ban/hide flows; also pins the usernameSuffix-not-userId privacy convention) and AdminController (verify whether AdminModerationControllerTest covers it; add direct tests if not).
   - 23 `@Data` DTOs → records; move root-level `LoginResponse`/`UserDto`/`RefreshTokenRequest` into `dto/<feature>/` while touching them.
   - `@RequiredArgsConstructor` × 37.
   - Entity `@Data` removal precondition: grep tests for whole-entity `assertEquals` on the 4 classes, rewrite to field-level assertions; then swap to `@Getter` + minimal setters; moderation/publishing fields reachable only via the phase-2 transitions.
   - Split `PlannerServiceTest.java` along the 5 successor services with a coverage mapping: every public method of `PlannerCommandService`/`PlannerQueryService`/`PublishedPlannerQueryService`/`PlannerPublishingService`/`PlannerEngagementService` maps to at least the assertions the aggregate test had; write the missing ones.
   - Mass test rename (304 methods); mass `TestDataFactory` adoption (52 files); replace 5 `Thread.sleep`s (`JwtTokenServiceTest:258,430` via Clock seam; `VoteNotificationFlowTest:298`, `MySQLIntegrationTest:240`, `RefreshRotationServiceTest:368` via deterministic timestamps).
   - Add `backend/lombok.config` with `lombok.addLombokGeneratedAnnotation = true` (Jacoco gate active at `build.gradle.kts:98,117`; prevents sweep-induced coverage distortion).
4. **Controller split** — `PlannerController` (538 LOC) into per-seam controllers mirroring the service split; zero URL/status/shape changes.
5. **Static-analysis ratchet** — Checkstyle (test `MethodName` regex on test source set, import order, public-service Javadoc, `ImportControl` banning `lombok.Data` in `entity/`+`dto/`), Error Prone, PMD/CPD; hang off `./gradlew check`; CI step becomes `check`; zero-suppression baseline.
6. **Structure collapse (B12)** — package-by-feature per the Decisions layout; one feature per `git mv` batch; feature exceptions move to owners (handler + base stay `shared/`); resource FQCN grep after each batch (verified low-risk: only 2 base-package `logging.level.org.danteplanner` lines, which survive).
7. **Boundary + index hygiene** — ArchUnit edge-allowlist rules + shared-is-sink rule (add to `LayerBoundaryTest` package or sibling class); V031 migration dropping `idx_published` gated on EXPLAIN + unused-index stats under the loadtest profile; one-time entity `@Index` ↔ migration parity sweep across all entities (Hibernate `validate` never checks indexes; `Planner` verified in-parity already).
8. **Doc truth-sync** — backend `CLAUDE.md`: Quick Reference dead paths (`PlannerService.java`, `PlannerSseService.java`), remove the dead "Interface for Service Layer" mandate, fix the stale import-order line; `.claude/rules/backend/**` path refs (`unit-tests.md` cites `PlannerServiceTest.java`); flag the dead `workers/asset-server` reference in `.claude/rules/deployment/wrangler.md` to the FE session (do not edit — not this task's file).

Recorded follow-up workstreams (NOT this task): DB performance (measurement-first charter: ngram FULLTEXT search rewrite, list-query content projection, Slice/pagination with FE coordination, personalization-split constraint for HTTP caching, query-count coverage rule for touched read paths); cross-stack golden-fixture contract tests; SSE load-behavior tests (k6); Terraform remote state backend; SonarCloud.

## Scope

Read for context:
- `backend/CLAUDE.md`, root `CLAUDE.md`, `.claude/rules/backend/**`
- `docs/tasks/031-fe-structure-refactor/requirements.md` (parallel-session ownership boundary + pattern source)
- `backend/src/test/java/org/danteplanner/backend/architecture/LayerBoundaryTest.java` (ArchUnit baseline to extend)
- `backend/src/main/java/org/danteplanner/backend/service/` (SseService, PlannerCommentSseService, NotificationService, CommentService, ModerationService, PlannerCommandService, PlannerPublishingService)
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java`, `User.java` (transition targets; `@Getter` trio as house-style reference: `Notification.java`)
- `backend/src/test/java/org/danteplanner/backend/support/TestDataFactory.java`
- `backend/src/main/resources/db/migration/` (V001, V002, V020, V023 for index history)

## Target

Create:
- SSE characterization + controller test classes; moderation/admin wire tests; per-service planner test classes (split); Tier-1 transition tests
- SSE emitter-lifecycle base class; notification helper; shared planner mapping method; `warnAndRespond` helper
- `backend/lombok.config`; Checkstyle/Error Prone/CPD gradle config + rule files; `.git-blame-ignore-revs`
- `db/migration/V031__drop_redundant_published_index.sql` (EXPLAIN-gated)
- `scripts/` shared k6 helper
- New ArchUnit boundary test class (feature edges + shared-sink)

Modify:
- All 23 `@Data` DTO files; 37 DI files; 4 entities; `PlannerController` (split); `PlannerRepository` (predicate fix); `GlobalExceptionHandler`; `NotificationService`; `CommentService`; `ModerationService`; `PlannerCommandService`; `PlannerPublishingService`; both SSE services
- 52 test files (factory adoption) + ~40 test files (naming) + `PlannerServiceTest` (dissolved)
- Every `main/` and `test/` file relocated in phase 6 (package lines + imports only)
- `backend/build.gradle.kts` (check task wiring); `ci-backend.yml` (run `check`); `.gitignore` (backend/bin)
- `backend/CLAUDE.md`, `.claude/rules/backend/**`

## Invariants

- INV1 Behavior-preserving: full BE suite green at every phase boundary — test: `backend/gradlew` full run per phase
- INV2 Wire contract frozen: Tier-2 `jsonPath` tests pass unmodified through the records migration and controller split, where phases 2–3 FIRST extend those tests to every endpoint whose DTOs convert (moderation, admin) — test: existing + new controller tests
- INV3 Pure moves: every phase-6 relocation shows `R`/`RM` in `git status`; moved-file diffs contain only package/import lines — test: per-batch `git diff` inspection
- INV4 FQCN strings survive: grep of `src/main/resources`, `src/test/resources`, `.github/workflows` for `org.danteplanner` shows no dead package paths after each phase-6 batch — test: grep sweep (baseline: 2 base-package logging lines, unaffected)
- INV5 ArchUnit monotone: the existing 3 rules never weakened; new edge + sink rules pass — test: `LayerBoundaryTest` + new boundary class in suite
- INV6 Schema untouched except V031: Tier-3 (Testcontainers MySQL, `flyway.enabled=true`, `ddl-auto=validate`) green; V031 is index-only and reversible by a follow-up migration — test: existing Tier-3 suite
- INV7 Transitions are total: after phase 3, moderation/publishing fields on `Planner` have no public setters; the six named transitions are the only mutation paths — test: compilation + Tier-1 transition tests + Checkstyle `ImportControl`
- INV8 Coverage gate undistorted: Jacoco verification passes before and after the Lombok sweeps with `lombok.config` in place — test: `check` task per phase

## Done When

- [ ] Phase gates: full BE suite + (from phase 5 on) `./gradlew check` green at every phase boundary
- [ ] Zero `@Data` in `dto/` and `entity/`; zero assign-only explicit constructors; 23 DTOs are records with wide ones on `@Builder`
- [ ] All test methods convention-named; all entity construction in tests via `TestDataFactory`; zero `Thread.sleep` in tests
- [ ] `PlannerServiceTest` dissolved into 5 per-service test classes with the coverage mapping documented in the split commit
- [ ] Moderation + admin endpoints have Tier-2 wire tests; SSE services + controllers have characterization tests
- [ ] Six `Planner` transitions exist with Tier-1 tests; the corresponding setters are gone
- [ ] `PlannerController` split; URL inventory identical before/after (route dump diff)
- [ ] Checkstyle + Error Prone + CPD wired into `check`, zero suppressions; CI runs `check`
- [ ] Package tree matches the feature layout; ArchUnit edge + sink rules active; grep for old package paths empty
- [ ] `idx_published` dropped only with EXPLAIN evidence attached to the migration PR (or the drop is documented as declined)
- [ ] Backend docs reference only existing classes; dead mandates removed
- [ ] `.git-blame-ignore-revs` lists the three sweep commits

## Test Plan

### Test Runner
- Framework: JUnit 5 + AssertJ/Mockito (Tier 1), Spring Boot Test + MockMvc + H2 (Tier 2), Testcontainers MySQL + Flyway + `ddl-auto=validate` (Tier 3), ArchUnit
- Run command: `backend/gradlew -p backend test > /tmp/be-test-<session-id>-<suffix>.log 2>&1` (full suite per phase; from phase 5: `backend/gradlew -p backend check > /tmp/be-check-<session-id>-<suffix>.log 2>&1`)

### Tests to Write
- [ ] SSE service characterization (subscribe/heartbeat/cap/zombie-cleanup/removal, both services): `service/SseServiceTest.java`, `service/PlannerCommentSseServiceTest.java` — BEFORE base extraction
- [ ] SSE controller subscribe (auth, `text/event-stream`, cap rejection): `controller/SseControllerTest.java`, `controller/PlannerCommentSseControllerTest.java`
- [ ] Moderation wire tests (timeout/ban/hide/unhide; `usernameSuffix` never `userId` in responses): `controller/ModerationControllerTest.java` — BEFORE DTO conversion
- [ ] Admin wire coverage (verify indirect, else direct): `controller/AdminControllerTest.java`
- [ ] Six transition Tier-1 tests incl. guards (`takeDown` implies unpublished; `togglePublished` throws on taken-down; `hideFromRecommended` sets all four fields; `unhide` clears all four; `recordSave` bumps syncVersion + stamps savedAt): `entity/PlannerTransitionTest.java`
- [ ] Notification `createAndPush` helper (save/log/push/duplicate-suppression): `service/NotificationServiceTest.java` (extend)
- [ ] `PlannerServiceTest` split: `service/PlannerCommandServiceTest.java`, `PlannerQueryServiceTest.java`, `PublishedPlannerQueryServiceTest.java`, `PlannerPublishingServiceTest.java`, `PlannerEngagementServiceTest.java` with method-coverage mapping
- [ ] Records migration honesty: existing Tier-2 jsonPath tests unmodified and green after each DTO batch; `Set`-component immutability (compact-constructor `copyOf`) asserted for planner DTOs
- [ ] ArchUnit feature-edge allowlist + shared-sink rules: `architecture/FeatureBoundaryTest.java`
- [ ] Entity `@Index` ↔ migration parity sweep result recorded in the phase-7 commit message
- [ ] Every invariant above has its test realized — no invariant ships untested
