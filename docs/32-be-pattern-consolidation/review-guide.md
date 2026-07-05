# Review Guide — BE Pattern Consolidation (docs/32)

A single uncommitted change of ~337 files. This guide is the map: what changed, where, what to scrutinize, and what is mechanical enough to skim. Commits were deferred to the end of the build, so per-phase git granularity is not available — this document substitutes for it.

**Bottom line:** 15 phases, behavior-preserving. Full suite green (1002 tests), `./gradlew check` green (Checkstyle + Error Prone + CPD + Jacoco + ArchUnit). No API contract, business rule, or DB schema changed. The wire contract is pinned by jsonPath tests that were not modified.

## How to read this diff

The change has two natures. Review them differently:

1. **Semantic changes (phases 2–13)** — real logic edits. These deserve line-by-line attention. Concentrated in a handful of files listed under "Hotspots" below.
2. **Mechanical relocation (phase 14)** — 226 `git mv` renames + package/import line rewrites. Every file's `package` line and its importers changed; no bodies changed. Compile + the green suite are the proof. Skim these for import correctness, don't line-read them.

To separate the two when reading a moved file: `git show HEAD:<old-layer-path>` gives the pre-refactor original (the file was under `controller/`, `service/`, `entity/`, `dto/<x>/` etc. at HEAD; it is now under `<feature>/<layer>/`). Diff findings against that original — a wart carried forward faithfully is not a regression.

## Risk-ranked hotspots (read these closely)

| Rank | Area | File(s) | Why it's risky |
|---|---|---|---|
| 1 | Upsert/update mapping dedup | `planner/service/PlannerCommandService.java` (`applyRequestFields`, `skipUnchangedCategory`) | Two paths with subtle behavioral differences were merged behind one boolean. An inverted condition silently changes create-or-update behavior. Most likely place for a real bug. |
| 2 | Entity state transitions | `planner/entity/Planner.java` (`togglePublished`, `takeDown`, `hideFromRecommended`, `unhideFromRecommended`, `recordSave`, `unpublish`) | Six mutation sequences moved from services into the aggregate. `togglePublished` owns the taken-down guard + one-time `firstPublishedAt` stamp — order and one-time-ness must match the original. |
| 3 | Records: null Sets | `planner/dto/{UpsertPlannerRequest,UpdatePlannerRequest,PublishedPlannerDetailResponse,PublicPlannerResponse}.java` | `Set.copyOf(null)` throws NPE; the compact constructors must guard null to preserve the old nullable-Set behavior. |
| 4 | SSE base extraction | `shared/sse/AbstractSseService.java` + `shared/sse/SseService.java` + `comment/service/PlannerCommentSseService.java` | ~140 LOC of duplicated lifecycle unified. The 500-connection FIFO eviction (comment-only) and the differing heartbeat intervals (10s vs 15s) must not have been homogenized. |
| 5 | Entity `@Data`→`@Getter` | `{planner,user,...}/entity/{Planner,User,UserSettings,ModerationAction}.java` | Removing `@Data` changes `equals`/`hashCode`. Guarded by a test-grep for whole-entity equality assertions (none found needing rewrite), but a missed runtime observer could make a test pass for the wrong reason. |
| 6 | Guard adoption | `comment/service/CommentService.java` | Now delegates restriction checks to `PlannerAccessGuard`; the timeout-before-ban exception order must match the deleted inline version. |

## Phase-to-change map (navigation)

- **1 Hygiene** — `scripts/lib/load-test-shared.js` (extracted k6 setup), `scripts/load-test*.js`; deleted `test-auth-endpoints.sh`.
- **2 SSE characterization tests** — `{service,controller}` SSE test classes (pin behavior for phase 3).
- **3 SSE base** — `shared/sse/AbstractSseService.java` (new); the two SSE services extend it.
- **4 Primitive adoption** — CommentService (guard, `isOwnedBy`), PlannerCommandService (`ErrorCode` enum), GlobalExceptionHandler (`warnAndRespond`), NotificationService (`createAndPush`, method-level `@Transactional`), PlannerRepository (duplicated predicate fix).
- **5 Entity transitions** — Planner.java (6 methods), rewired ModerationService/PlannerPublishingService/PlannerCommandService.
- **6 Wire-pinning tests** — `moderation`/`admin` controller tests (jsonPath, incl. the `usernameSuffix`-not-`userId` privacy pins).
- **7 Records** — all 51 DTOs → records.
- **8 DI** — 23 `@RequiredArgsConstructor`; `backend/lombok.config`.
- **9 Entity Lombok** — 4 entities `@Data`→`@Getter`; setter lockdown (INV7).
- **10 Test split + Clock** — `PlannerServiceTest` → 5 successor classes; `JwtTokenService` Clock seam; zero `Thread.sleep`.
- **11 Mass sweeps** — 281 test-method renames; TestDataFactory adoption. (Pure-mechanical; skim.)
- **12 Controller split** — PlannerController → 7 controllers, URLs frozen.
- **13 Ratchet** — `build.gradle.kts`, `config/checkstyle/*`, `TestNamingConventionTest`, CI `check`.
- **14 Package-by-feature** — 226 renames into 8 feature packages. (Pure-mechanical; skim for imports.)
- **15 Boundaries/docs** — `FeatureBoundaryTest`, doc truth-sync, `index-hygiene-followup.md`.

## Known deviations (already flagged, not defects)

1. **`shared` is not a pure dependency sink.** `GlobalExceptionHandler`, `JwtAuthenticationFilter`, and `SseService` live in `shared` but reference feature code, so `FeatureBoundaryTest`'s sink rule is encoded in its truthful residual form (`shared !→ {admin, notification}`), not a literal pure sink. Making it pure requires relocating those classes — a follow-up, outside this behavior-preserving task.
2. **`PlannerServiceTest` `times(2).save` → `times(1)`** (phase 5): the assertion pinned an implementation mechanic (two saves in one transaction, one Hibernate UPDATE) not a contract; adjudicated as a faithful change.
3. **`takenDownAt` retains a setter** (INV7 is 8/9): its only caller is a capture/restore guard in upsert that is provably dead (`applyRequestFields` never writes `takenDownAt`). Follow-up: delete the guard, then drop the setter.
4. **`MySQLIntegrationTest` uses `LockSupport.parkNanos`** not `Thread.sleep` (phase 10): that site is a deadlock-retry backoff, not an ordering sleep — a delay is genuinely needed.

## Follow-ups recorded (not in scope, see `index-hygiene-followup.md`)

- Relocate `GlobalExceptionHandler`/`JwtAuthenticationFilter`/`SseService` out of `shared` to make it a true sink; relocate `SseService`'s `UserSettingsService` dependency.
- `ModerationAction` `@Index` annotation is stale (V034 dropped `target_id`); annotation-only fix.
- EXPLAIN-gated drop of redundant `idx_published` (next free migration is V045, not the spec's placeholder V031).
- Import-order enforcement + Error Prone's 85 advisory WARN findings (`JavaUtilDate` ×81 etc.) — deferred ratchet tightening.
- `GET /api/planner/md/events` appears to be a legacy duplicate of `/api/sse/subscribe`.

## Verification already run

- `./gradlew check` green: 1002 tests, Checkstyle/Error Prone/CPD/Jacoco/ArchUnit all pass, zero suppressions.
- INV2 (wire contract): jsonPath tests unchanged and green through the records migration.
- INV3 (pure moves): 226 renames show as `R` in git.
- INV4 (FQCN strings): resource grep clean except the 2 baseline `logging.level` base-package lines.
- INV7 (setter lockdown): 8/9 Planner moderation/publishing fields have no setter.

---

## Review results (two focused adversarial reviewers + HEAD verification)

**Verdict: ACCEPTABLE — zero confirmed regressions.** Every finding was validated against the pre-refactor original via `git show HEAD:<old-path>` (the reviewers lacked Bash and correctly flagged each "faithful" as needing that diff; the orchestrator resolved all against HEAD).

Reviewer 1 (entity transitions + mapping dedup):
- Upsert category-change + new-content validation ordering — FAITHFUL. HEAD is the identical `if (content != null) {...} else if (categoryChanged) {...}` shape; new `&& skipUnchangedCategory` reproduces it exactly.
- `togglePublished` taken-down guard vs title validation — FAITHFUL. HEAD runs the guard first too; `PlannerForbiddenException` precedence preserved.
- `updatePlanner` schemaVersion — FAITHFUL. HEAD update path never set it; the upsert/update asymmetry is pre-existing.
- `recordSave`, `takeDown`, `hide/unhide`, `unpublish`, dead takenDownAt guard — all faithful.

Reviewer 2 (records + SSE):
- Null-`Set.copyOf` NPE (headline risk) — FAITHFUL. All four Set DTOs guard `== null ? null : Set.copyOf(...)`.
- Boolean JSON wire keys (`isBookmarked`/`isSubscribed`) — FAITHFUL. Jackson 2.19 (Boot 3.5.10) names by record component; FE Zod requires the prefixed keys; live `CommentTreeNode` primitive-boolean precedent proves no is-stripping.
- `PublicPlannerResponse.toBuilder` commentCount mutation — FAITHFUL.
- SSE eviction order (dedup→evict→add), catch breadth (`IOException | IllegalStateException`), heartbeat intervals (10s/15s), FIFO cap — all FAITHFUL vs HEAD.

**One recommendation (hardening, not a defect):** the boolean wire keys `$.isBookmarked` (list) and `$.isSubscribed` (detail) have no backend jsonPath assertion — they're guaranteed only by the FE Zod schema and the CommentTreeNode precedent. A BE MockMvc snapshot test would make the record→JSON contract self-defending against a future Jackson bump. Optional pre-commit add.
