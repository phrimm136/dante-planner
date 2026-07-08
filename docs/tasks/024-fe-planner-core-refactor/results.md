# Results — Planner-core refactor

Behavior-preserving cleanup of the planner-core, executed in 4 atomic phases via `/build`, then
reviewed via `/review`. All runtime behavior identical pre/post; only test coverage, file
organization, and symbol names changed.

## What Was Done

- **Phase 1 — Characterization tests (safety net).** Added `usePlannerSave.test.ts` and
  `usePlannerEditorStore.test.tsx` pinning current observable behavior, including the
  validate→sync→local save order (Inv 5).
- **Phase 2 — Validator rename.** `validatePlannerForSave`→`validatePlannerForPublish` (strict),
  `validatePlannerUserFriendly`→`validatePlannerForDraftSave` (non-strict); repointed all consumers;
  fixed the stale JSDoc example.
- **Phase 3 — Split `plannerHelpers.ts`** (1084 LOC) into `plannerValidation.ts`,
  `plannerValidationErrors.ts`, `plannerRules.ts`; deleted the original + its test; repointed 5 import
  sites; split the test file preserving exactly 96 `it` / 149 `expect`.
- **Phase 4 — MSW policy.** Installed `msw@^2.14.6`; added `src/test-utils/` server scaffold + a smoke
  test proving interception; amended `testing.md` with the hybrid mocking policy. Wired smoke-scoped
  (not global) to coexist with the existing `globalThis.fetch = vi.fn()`.
- **Review fixes (+6 golden-master tests).** `resolveConflict` adapter ordering pinned per branch
  (overwrite/discard/both-auth/both-unauth); `initializeFromPlanner` `deckFilterState` reset pinned;
  `getPlannerState` `floorSelections[i].giftIds` Set round-trip pinned. Plus 2 cleanups (dangling
  change-history comment removed; MSW template URL made absolute).

## Files Changed

Created (lib + tests + MSW):
- `src/lib/plannerValidation.ts`, `plannerValidationErrors.ts`, `plannerRules.ts`
- `src/lib/__tests__/plannerValidation.test.ts`, `plannerValidationErrors.test.ts`, `plannerRules.test.ts`
- `src/hooks/__tests__/usePlannerSave.test.ts`
- `src/stores/__tests__/usePlannerEditorStore.test.tsx`
- `src/test-utils/mswServer.ts`, `handlers.ts`, `__tests__/mswServer.test.ts`

Modified (repointed consumers + infra):
- `src/hooks/usePlannerSave.ts`, `src/hooks/useMDUserPlannersData.ts`
- `src/components/plannerViewer/PlannerDetailHeader.tsx` (+ its test)
- `src/components/floorTheme/FloorThemeGiftSection.tsx`
- `src/schemas/__tests__/PlannerSchemas.test.ts` (the 5th import site the spec missed)
- `frontend/package.json`, `frontend/yarn.lock`, `.claude/rules/frontend/testing.md`

Deleted: `src/lib/plannerHelpers.ts`, `src/lib/__tests__/plannerHelpers.test.ts`.

Note: `frontend/vitest.setup.ts` deliberately **unchanged** (smoke-scoped MSW; see divergence).

## Verification

- **Build / typecheck:** `tsc -b` exit 0.
- **Tests:** 117 files / **6080 passed + 1 skipped**, vitest exit 0 (baseline 6040 → +33 Phase 1, +1
  MSW smoke, +6 review = 6080).
- **Grep gates:** `validatePlannerForSave|validatePlannerUserFriendly` → 0; `plannerHelpers` → 0.
- **Lint:** **not runnable** — `eslint .` crashes (pre-existing config error, see Issues).
- **Manual:** not performed in-app; covered by characterization + preserved unit tests.

## Issues & Resolutions

- **Spec missed a 5th import site** (`PlannerSchemas.test.ts` imports `validateNoteSizes`) → caught in
  pre-build verification; repointed in Phase 3. `tsc -b` would have caught it regardless.
- **MSW vs. global fetch mock** → global `server.listen()` mass-regresses the suite (replaces the
  `vi.fn()` many tests drive). Resolved by smoke-scoping MSW to its own test file with file-local
  `listen`/`close` + fetch restore; `vitest.setup.ts:59` left intact.
- **`eslint .` crashes** (`@typescript-eslint/await-thenable` needs `parserOptions` not in the flat
  config), dying on untouched files (`scripts/detect-hardcoded-text.ts`). Pre-existing; eslint config
  not in changeset. Not fixed (out of scope). `tsc -b` is the type backstop.
- **Intermittent test flake** (`PlannerDetailHeader > delete with local cleanup`; `StartBuffCardMD6`
  teardown) → confirmed non-deterministic: passes in isolation and on rerun, logic untouched. Not a
  regression; not fixed (pre-existing, out of scope).

## Learnings

- **`git show HEAD:<deleted-file>` is the faithful-move oracle.** For a behavior-preserving refactor,
  "regression or pre-existing?" is answerable by diffing review findings against the committed
  original. This turned 3 confident reviewer "High"s into 0 production edits (difficulty-validation
  absence confirmed preserved; two import-order disorders confirmed pre-existing).
- **A golden master is worth what it pins, not its green count.** The review's top catch wasn't a bug
  — it was that the most-likely-to-change flow (conflict re-validation, named in the deferred track)
  was unprotected. Adding `resolveConflict` ordering tests was the highest-value fix.
- **Case-count assertion (96/149) catches what "green" cannot** — a split can silently drop a
  `describe` and stay green; summing `it`/`expect` across the new files proves redistribution.
- **MSW v2 and a global `fetch` vi.fn() are mutually exclusive at global scope** in this suite; smoke-
  scoping is the coexistence path that keeps existing tests untouched (Inv 4).
- **Excluded-test-file LSP false positives are noise here** — `tsc -b` + vitest are the authority; the
  harness flagged 2307/2345/2558 on test files and a phantom `plannerHelpers.ts` (deleted) throughout.

## Spec Divergence

### What Changed
- **MSW wiring location.** Spec/Done-When said "MSW server wired in `vitest.setup.ts`." Actually
  smoke-scoped in its own test file; `vitest.setup.ts` unchanged — global `listen()` regresses the
  suite and editing existing tests is forbidden (Inv 4). Right engineering call; deviates from literal text.
- **Cast invariant (Inv 3) interpretation.** Literal Done-When: "remaining casts each carry a one-line
  warning comment." Implemented: valid-fixture builders cast-free ✓, all invalid-input casts carry
  `// Warning:` ✓, but ~12 benign output-narrowing (`as FloorValidationError` after a `.code` match)
  and `delete`-idiom casts left bare — annotating them would be noise per comments.md.

### What Was Added (Not in Spec)
- **+6 golden-master tests beyond the spec's explicit Phase-1 list** (resolveConflict ordering ×4,
  deckFilterState reset, floorSelections Set round-trip). Surfaced by review; within the safety net's
  *purpose* (gating the deferred conflict-re-validation track) though not in the enumerated list.
- **MSW template hardening** — absolute URL + warning in `handlers.ts` (Node `fetch` rejects relative).

### What Was Dropped
- **`eslint .` passing** (a Done-When item) — not satisfiable: lint is pre-existing-broken at the
  config level, independent of this work.

### Wrong Assumptions
- **"4 import sites"** → actually **5** (`PlannerSchemas.test.ts` missed).
- **"111 existing tests"** → actually **112 files / 6040 tests** (stale count; invariant "suite stays
  green" still held).

### Prompting Retrospective
- **Pre-existing-broken tooling**: "Before listing `lint passes` in Done-When, is `eslint .` currently
  green on a clean checkout?" — would have flagged the config crash as a separate prerequisite, not a
  refactor gate.
- **Coexistence feasibility**: "Has anyone confirmed MSW can be wired globally in `vitest.setup.ts`
  without regressing the tests that drive `globalThis.fetch = vi.fn()`?" — would have pre-empted the
  literal "wire in vitest.setup.ts" instruction that proved infeasible.
- **Import-site enumeration**: "Did the grep for consumers include `**/__tests__/**`, or only `src`
  production files?" — would have caught the 5th site at spec time.
- **Cast-rule scope**: "Does 'every remaining cast carries a comment' apply to output-narrowing and
  `delete`-idiom casts, or only to invalid-input fixtures?" — would have resolved the Inv 3 ambiguity.

### Spec Process Takeaway
This spec systematically missed **environment/tooling preconditions and feasibility of literally-named
wiring** — it specified exact file targets (`vitest.setup.ts`) and exact counts (4 sites, 111 tests)
without verifying them against a clean run, so the gaps surfaced only at build time.

## Session State (for continuity)

- **Status:** All 4 phases + review fixes COMPLETE and verified. `status.json` reflects `currentPhase:
  4`, all phases `done`.
- **Uncommitted:** Entire changeset is uncommitted (per workflow — commit only on explicit request).
  See `git status` list above.
- **Next steps:** (1) `commit-process` skill to land the 4 phases (consider one commit per phase for
  atomic history, per the spec's atomic-commit intent). (2) Optionally fix the pre-existing eslint
  config crash as a separate change. (3) The deferred behavioral track (save/sync reordering, conflict
  re-validation, migration partial-success) is now unblocked — the safety net exists.
- **Blockers:** none for this task. Eslint config crash blocks `lint` repo-wide but is independent.
