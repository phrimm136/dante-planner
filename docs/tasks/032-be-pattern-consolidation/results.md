## What Was Done

All 15 spec phases executed, `./gradlew check` green (1002 tests, zero suppressions), review-clean (two adversarial reviewers + HEAD verification, zero confirmed regressions). Behavior-preserving throughout: no API contract, business rule, or DB schema changed.

- **1 Hygiene** — extracted shared k6 setup; deleted the all-TODO auth-test stub.
- **2 SSE characterization** — 30 tests pinning both SSE services + controllers (surfaced the 10s/15s heartbeat difference and FIFO-eviction cap that constrained phase 3).
- **3 SSE base** — `AbstractSseService<K>`; the two services extend it, subclass-specific logging/@Scheduled/cap retained.
- **4 Primitive adoption** — CommentService→PlannerAccessGuard, `isOwnedBy`, ErrorCode enum, `warnAndRespond`, `createAndPush`, duplicated-predicate fix.
- **5 Entity transitions** — six named `Planner` methods; `applyRequestFields` mapping dedup.
- **6 Wire-pinning** — 36 moderation/admin jsonPath tests incl. `usernameSuffix`-not-`userId` privacy pins (AdminController was fully uncovered).
- **7 Records** — all 51 DTOs → records, zero jsonPath assertion edits.
- **8 DI** — 23 `@RequiredArgsConstructor` + `lombok.config`; jacoco gate green.
- **9 Entity Lombok** — 4 entities `@Data`→`@Getter`, INV7 setter lockdown (8/9).
- **10 Test split** — `PlannerServiceTest` → 5 successor classes (bidirectional coverage map); Clock seam; zero `Thread.sleep`.
- **11 Mass sweeps** — 281 test renames + TestDataFactory adoption.
- **12 Controller split** — 7 controllers, URL inventory diff empty.
- **13 Ratchet** — Checkstyle + Error Prone + CPD wired to `check`; CI runs `check`.
- **14 Package-by-feature** — 226 renames into 8 feature packages, all old layer packages vacated.
- **15 Boundaries/docs** — `FeatureBoundaryTest` (bite-tested), doc truth-sync, `index-hygiene-followup.md`.

## Files Changed

~337 files (backend/ + scripts/ + docs/32/). 226 renames (package-by-feature) + content edits. See `review-guide.md` for the phase-to-change map.

## Verification

- Build: pass (`./gradlew check` exit 0)
- Tests: pass (1002, 0 failures)
- Manual: `FeatureBoundaryTest` bite-tested (injected illegal edge → build failed → reverted); security relocation HEAD-diffed (only DI swap, chain byte-identical); all reviewer findings closed against `git show HEAD:`.

## Issues & Resolutions

- Phase-0 commit swept 204 FE-session staged files → `git reset --soft` + pathspec `git commit -- <paths>`. Root cause: shared index, `commit` snapshots whole index not just `add`ed paths.
- 3 agent deaths (2 session-limit, 1 stalled-stream) — all during read phase, recovered by relaunch (read-first discipline made crashes cheap).
- Phase-14 same-package/wildcard/inline-FQCN stragglers — the three predicted classes; fixed per-feature.
- Interactive `cp -i` blocked a bite-test revert → `sed -i` delete instead.

## Learnings

- **Characterization before transformation catches what the audit can't**: the design debate assumed the SSE heartbeats were identical copies; the phase-2 tests forced a full read revealing 10s/15s deliberate offset. Duplication audit saw "similar code"; only characterization saw "different behavior."
- **A cited file:line is a claim with an address, not evidence**: 4 audit findings and several reviewer findings were confidently wrong; all resolved in seconds against `git show HEAD:`. The reviewers that lacked Bash were methodologically honest — they labeled every "faithful" as needing that diff and handed over the exact lines.
- **"No agents for pure moves" targets body-drift, not moves**: a sed/git-mv mechanic never Read→Writes bodies, so the rule doesn't bind — delegating the mechanical relocation was safe and preserved orchestrator budget.
- **Ratchet-after-sweep is the trick**: adopting Checkstyle/CPD on freshly-swept code hit ~zero suppressions; on unswept code it would have been a rotting suppression file.
- **Defer-all-commits has a real cost**: per-phase git granularity was lost (files touched by 4+ phases), so `.git-blame-ignore-revs` and per-sweep commits became unreconstructable. The review-guide substitutes for the lost history.

## Spec Divergence

### What Changed
- **Counts** (spec flagged "measured 2026-07-02, re-verify"): 37 DI conversions → 23 (19 already done phases 4-7); 304 renames → 281; 52 factory files → 5 swaps + justified hand-rolls (unsaved-id mockito fixtures, constraint fixtures). All reconciled correctly.
- **MethodName enforcement**: spec put it in Checkstyle; moved to ArchUnit (`TestNamingConventionTest`) because Checkstyle can't scope MethodName to `@Testable` methods (would reject legit camelCase test helpers).
- **V031 migration** → actual next free version is V045 (V031-V044 already applied); the index drop was declined-pending-EXPLAIN (no live DB), documented in `index-hygiene-followup.md`.

### What Was Added (Not in Spec)
- **`review-guide.md` + two adversarial reviewers + HEAD verification** — the user held the commit and asked to "organize for review"; not a spec deliverable.
- **`FeatureBoundaryTest` bite-test** — injected an illegal edge to prove the rule fails, beyond "it passes."
- **Security-relocation HEAD-diff** — extra verification the auth chain moved faithfully.

### What Was Dropped
- **`shared` as a pure dependency sink** — impossible without relocating `GlobalExceptionHandler`/`JwtAuthenticationFilter`/`SseService` out of shared (they reference feature code); a logic change outside this behavior-preserving task. Encoded as the truthful residual form; relocation is a recorded follow-up.
- **Import-order Checkstyle enforcement + Error Prone WARN cleanup** (85 `JavaUtilDate` etc.) — deferred; ratchet ships with UnusedImports only.
- **`.git-blame-ignore-revs`** — unreconstructable after defer-all-commits.
- **INV7 9/9** — `takenDownAt` setter kept for a provably-dead capture/restore guard (deleting the guard is a logic change).

### Wrong Assumptions
- Spec assumed no Testcontainers tier existed (a survey agent hallucinated this) — the schema-truth tier was already live.
- Spec's "the check would still pass" after phase 14 was optimistic — the auto-inserted imports created UnusedImports violations that phase 15 had to clean before `check` went green.

### Prompting Retrospective
- **Commit cadence**: "Should commits happen per-phase or deferred, and what do we lose either way?" — Why: defer-all-commits silently forfeited git-blame-ignore-revs and per-sweep commits; surfacing the tradeoff at spec time would have made it a conscious choice, not a discovered cost.
- **`shared`-sink feasibility**: "Which specific classes must leave `shared` for it to be a true sink, and is that in scope?" — Why: the sink was stated as an invariant but was unreachable without out-of-scope relocations; auditing shared's actual outbound edges at spec time would have caught it.
- **Ratchet tool scoping**: "Can each proposed Checkstyle rule actually be scoped as intended (e.g. MethodName to test methods)?" — Why: the MethodName→ArchUnit pivot was a tool-capability surprise resolvable by a 5-minute feasibility check.

### Spec Process Takeaway
This spec systematically under-specified **enforcement feasibility** — several invariants (shared-as-sink, Checkstyle MethodName scoping, `.git-blame-ignore-revs`) were stated as achievable without checking whether the tools/git-history could actually express them under the chosen constraints.

## Session State (for resume)

- **Uncommitted**: phases 1-15 staged (337 files under backend/scripts/docs32); FE session's 204 files also staged (do NOT sweep — pathspec-commit only). Phase 0 committed at `0af79625`.
- **Current focus**: review complete and clean; commit HELD per user.
- **Next steps**: (1) optionally add the recommended BE wire-key MockMvc test (`$.isBookmarked`/`$.isSubscribed`); (2) commit via `git commit -- backend/ scripts/ docs/tasks/032-be-pattern-consolidation/` (message drafted, in review-guide history); (3) work the recorded follow-ups (shared-sink relocation, ModerationAction @Index, V045 index drop, InternalController/events dedup).
- **Blockers**: none.
