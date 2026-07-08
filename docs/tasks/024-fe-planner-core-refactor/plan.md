# Execution Plan

## Phase Summary

Behavior-preserving planner-core cleanup in 4 atomic, independently-committable phases. Each phase
leaves `tsc -b` + the full Vitest suite green. Baseline (verified this session): **112 test files,
6040 passed + 1 skipped**. The spec's "111" is stale — the real invariant is "no test lost, suite
stays green."

Strategy: build the missing safety net first (Phase 1), then make the two name/structure changes the
net protects (Phases 2→3, sequential because 3 moves the symbols 2 renames), then resolve the MSW
policy contradiction (Phase 4, independent).

### Facts discovered this session (propagated into agent prompts)

- **Adapters are internal hooks, NOT injected via options.** `usePlannerSave` calls
  `usePlannerSaveAdapter()` and `usePlannerSyncAdapter()` internally. Phase 1 fakes them via
  `vi.mock('./usePlannerSaveAdapter')` / `vi.mock('./usePlannerSyncAdapter')`. Return shapes:
  `saveToLocal(saveable) → { success: boolean, errorCode?: string }`;
  `syncToServer(saveable, force) → { metadata: { syncVersion } }`.
- **Validation only fires for `plannerType === 'MIRROR_DUNGEON'`** (`usePlannerSave.ts:391`).
  Strict (`validatePlannerForSave`) vs non-strict (`validatePlannerUserFriendly`) is selected by
  `isCurrentlyPublished` (derived from the `published` option).
- **Golden-master save order** (`usePlannerSave.ts:391-438`): note-size check → strict/non-strict
  validate → (if authed & syncEnabled/force) `syncToServer` → `saveToLocal` → return `true`.
- **5 import sites of `plannerHelpers`, not 4.** The spec missed
  `src/schemas/__tests__/PlannerSchemas.test.ts:18` (imports `validateNoteSizes`). Repoint it to
  `plannerValidation` in Phase 3.
- **Current `plannerHelpers.test.ts` counts: 96 `it` / 149 `expect` / 19 `describe`.** Phase 3's three
  new test files must sum to exactly these.

## Phases

### Phase 1: Characterization tests (safety net)
- Files (create):
  - `src/hooks/__tests__/usePlannerSave.test.ts`
  - `src/stores/__tests__/usePlannerEditorStore.test.tsx`
- Tests: the two files above.
- Depends on: none.
- Verify: `yarn --cwd frontend test run usePlannerSave usePlannerEditorStore` green; both assert
  CURRENT observable behavior including save→sync→local order; full suite still green.
- Hazard (Inv 5): tests must mirror `usePlannerSave.ts:391-438` as-written. Mock ONLY the two
  adapters — do NOT `vi.mock('@/lib/plannerHelpers')` — so Phase 2/3 leave these tests untouched.

### Phase 2: Rename the validator pair
- Files (modify):
  - `src/lib/plannerHelpers.ts` — `validatePlannerForSave` → `validatePlannerForPublish`,
    `validatePlannerUserFriendly` → `validatePlannerForDraftSave`; fix stale JSDoc example at `:924`
    (correct name + correct 3-arg signature).
  - `src/hooks/usePlannerSave.ts:13,399,409` — repoint + rename.
  - `src/components/plannerViewer/PlannerDetailHeader.tsx:46,305` — repoint + rename.
  - `src/hooks/useMDUserPlannersData.ts:22,418,421` — repoint + rename.
  - `src/components/plannerViewer/__tests__/PlannerDetailHeader.test.tsx:116-117` — update mocked names.
  - `src/lib/__tests__/plannerHelpers.test.ts` — imports, describe labels, comment at `:226`.
- Tests: existing tests updated to new names (no new behavior).
- Depends on: Phase 1.
- Verify: `grep -rn "validatePlannerForSave\|validatePlannerUserFriendly" frontend/src` → zero;
  `tsc -b` green; full suite green. NO logic change.

### Phase 3: Split `plannerHelpers.ts` → 3 modules
- Files (create):
  - `src/lib/plannerValidationErrors.ts` — `ValidationError` + 9 sub-interfaces +
    `PlannerValidationError` union (`:114-220`) + `toUserFriendlyError` (`:222`).
  - `src/lib/plannerRules.ts` — `isGiftAffordableForThemePack` (`:62`), `getUnaffordableGiftIds`
    (`:74`), `getUnaffordableGiftNames` (`:96`), `canSelectFloorThemePack` (`:637`).
  - `src/lib/plannerValidation.ts` — the 10 `validate*` functions (incl. the two renamed facades);
    imports from the other two modules.
  - `src/lib/__tests__/plannerValidation.test.ts`, `plannerValidationErrors.test.ts`,
    `plannerRules.test.ts` — the 96 cases redistributed, logic unchanged.
- Files (modify — repoint imports, all 5 sites):
  - `src/hooks/usePlannerSave.ts:13` — validators + `validateNoteSizes` → `plannerValidation`;
    `toUserFriendlyError` → `plannerValidationErrors`.
  - `src/hooks/useMDUserPlannersData.ts:22`
  - `src/components/plannerViewer/PlannerDetailHeader.tsx:46`
  - `src/components/floorTheme/FloorThemeGiftSection.tsx:15` → `plannerRules`
  - `src/schemas/__tests__/PlannerSchemas.test.ts:18` → `plannerValidation` (the missed 5th site)
  - `src/components/plannerViewer/__tests__/PlannerDetailHeader.test.tsx:115` — `vi.mock` target.
- Files (delete after move): `src/lib/plannerHelpers.ts`, `src/lib/__tests__/plannerHelpers.test.ts`.
- Depends on: Phase 2 (moves the renamed symbols).
- Verify: `grep -rn "plannerHelpers" frontend/src` → zero; three new test files sum to **96 it /
  149 expect**; valid-fixture builders have zero casts; intentional invalid-input casts each carry a
  one-line `// Warning:` comment; `tsc -b` + full suite green.

### Phase 4: MSW policy (the blocker)
- Files (create):
  - `src/test-utils/mswServer.ts` (+ handlers scaffold)
  - one MSW smoke test asserting interception.
- Files (modify):
  - `frontend/package.json` — add `msw` devDependency.
  - `frontend/vitest.setup.ts` — `beforeAll(listen)` / `afterEach(resetHandlers)` / `afterAll(close)`,
    coexisting with the existing `globalThis.fetch = vi.fn()` (`:59`).
  - `.claude/rules/frontend/testing.md` — hybrid policy table.
- Depends on: none (independent; can run anytime).
- Verify: smoke test genuinely intercepts a `fetch` AND all prior 6040 tests stay green.
- KNOWN HAZARD: `globalThis.fetch = vi.fn()` and MSW interception compete for the same global —
  whoever patches last wins; per-test fetch reassignment can re-clobber MSW after `listen()`. Tune:
  `listen()` ordering vs. line 59, and whether the smoke test restores real fetch in its own scope.
  Acceptance requires BOTH proofs (interception + green), not just green.

## Phase Dependencies
- Group A (first): Phase 1
- Group B (after Phase 1, sequential): Phase 2 → Phase 3
- Independent (any time): Phase 4

Execution order chosen: 1 → 2 → 3 → 4 (sequential, one verified phase at a time).
