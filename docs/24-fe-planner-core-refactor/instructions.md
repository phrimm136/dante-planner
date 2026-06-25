# Task: Planner-core refactor — characterization tests, validator rename, `plannerHelpers` split, MSW policy

A behavior-preserving cleanup of the planner-core, chosen as the highest-value debt target over a
second feature-first migration. It has four independently-committable phases: (1) build the missing
safety net, (2) rename a misleading validator pair, (3) split the 1084-LOC `plannerHelpers` grab-bag,
(4) resolve the MSW documented-vs-reality contradiction. All runtime behavior is identical before and
after; the only deliberate change is test coverage, file organization, and symbol names.

Behavioral redesign of the save/sync flow (ordering, conflict re-validation, migration
partial-success) is explicitly **out of scope** and routed to a separate spec — it is gated behind
the Phase 1 characterization tests existing.

## Decisions

- **Target = planner-core, not a second feature migration** — because "be proactive / fully analyze /
  no token limit" signals attacking the worst debt over building safe momentum. `abEvent` remains the
  clean parallel leaf migration but is not this spec. (User, this session.)
- **Coverage is inverted relative to risk → safety net comes first** — the split target
  (`plannerHelpers.ts`) is the best-tested file (96 it / 149 expect), while the stateful spine
  (`usePlannerSave.ts` 805 LOC, `usePlannerEditorStore.tsx` 677 LOC) has **zero** tests. Per
  `lesson-phase-risk-stratification`, write characterization tests before any change. (Evidence:
  `find` over `__tests__/` — these two files have no `*.test.*`.)
- **Rename the two top-level validators only** — `validatePlannerForSave` → `validatePlannerForPublish`,
  `validatePlannerUserFriendly` → `validatePlannerForDraftSave`. The names describe *error-message
  style*, not the *publish-vs-draft axis* they actually select (`usePlannerSave.ts:399` strict path is
  "ForSave"; `:409` draft path is "UserFriendly"). Sub-validators like `validateFloorThemePacksForSave`
  keep their names — they are not the hazard; renaming them is scope creep (CLAUDE.md surgical-precision).
- **Split `plannerHelpers.ts` into three modules by responsibility** (SRP, frontend/CLAUDE.md
  principle #1; mirrors the backend `PlannerContentValidator` split, `note-backend-ddd-ddia` B7):
  - `plannerValidation.ts` — the 10 `validate*` functions (incl. the two renamed facades).
  - `plannerValidationErrors.ts` — the error type hierarchy + `toUserFriendlyError`.
  - `plannerRules.ts` — pure domain predicates consumed by both validators and UI.
- **Third module is `plannerRules.ts`, not `plannerGiftRules.ts`** (DEFAULT, deviates from the
  session's tentative name) — because it also holds `canSelectFloorThemePack`, a floor-prerequisite
  rule, not a gift rule. Naming accuracy is the point of this refactor; a "GiftRules" file holding a
  floor rule would re-introduce the mislabeling we are removing.
- **Delete `plannerHelpers.ts` after the split; repoint all consumers directly; no compat barrel** —
  matches the extraction pilot (deleted originals, repointed imports). CLAUDE.md forbids dead barrels.
- **Cast-tightening is selective, not blanket** — eliminate `as unknown as` casts in *valid-fixture
  builders* (they must type-check cleanly); KEEP casts only where a test deliberately constructs
  type-invalid input to exercise a runtime guard (e.g. `{ identity: null }`), each annotated with a
  one-line `// Warning:` comment (comments.md). Blanket casts hide shape drift — fatal for a refactor
  whose thesis is type-correctness (`gotcha-hardcoded-i18n-keys-in-test-mocks` is the same failure mode).
- **MSW resolution = hybrid** (User, this session) — install `msw`; mandate it only at the real network
  boundary; bless `renderHook`/fake-adapter mocking for state & orchestration hooks; amend
  `testing.md` to match. (User-selected option.)
- **MSW deliverable this round = infra + policy + one smoke test; first real consumer (`plannerApi`)
  deferred** (DEFAULT, honors the user's "plannerApi → later" tag) — install, wire the server, amend
  the doc, prove interception with one handler test. Migrating the 18 existing `vi.mock` tests and full
  `plannerApi` coverage belong to the later track.
- **Characterization tests are golden-master of CURRENT behavior** — they pin what *is*, including the
  current validate→sync→local ordering (`usePlannerSave.ts:399-439`). A contributor must not "fix"
  behavior during this refactor; the fix is the deferred track, where reordering shows up as an
  intentional test change.
- **No backend change** — FE validator names are independent of backend `PlannerContentValidator`
  (`arch-backend-plannercontentvalidator`).

## Resolved Ambiguities

| Question | Resolution | Source |
|----------|------------|--------|
| Rename blast radius — how many consumers? | **3 production** + **2 test** files, not 1. Prod: `usePlannerSave.ts:13,399,409,661`, `PlannerDetailHeader.tsx:46,305`, `useMDUserPlannersData.ts:22,418,421`. Tests: `plannerHelpers.test.ts` (imports+labels+comment), `PlannerDetailHeader.test.tsx:116-117` (vi.mock by name). Plus the stale JSDoc example at `plannerHelpers.ts:924`. | Codebase `grep` |
| Exact new validator names | `validatePlannerForPublish`, `validatePlannerForDraftSave` | User |
| Which symbols go in each split module | Validators (10) → `plannerValidation.ts`; types (`ValidationError`+9 sub-interfaces+`PlannerValidationError` union at `:114-220`) + `toUserFriendlyError` (`:222`) → `plannerValidationErrors.ts`; `isGiftAffordableForThemePack` (`:62`), `getUnaffordableGiftIds` (`:74`), `getUnaffordableGiftNames` (`:96`), `canSelectFloorThemePack` (`:637`) → `plannerRules.ts` | Codebase `grep -nE '^export'` |
| Third module name | `plannerRules.ts` (not `plannerGiftRules.ts`) — holds a floor rule, not only gift rules | Default (naming accuracy) |
| Keep a `plannerHelpers.ts` compat barrel? | No — delete, repoint 4 import sites directly | Convention (extraction pilot; no dead barrels) |
| `plannerHelpers.ts:15` other importers to repoint | `FloorThemeGiftSection.tsx:15` (`canSelectFloorThemePack`, `getUnaffordableGiftNames`) + the 3 rename consumers | Codebase `grep` |
| Is `plannerHelpers` re-exported via a barrel? | No — no `lib/index.ts` hit; not in `schemas/index.ts` | Codebase `grep` |
| How to tighten the 25 fixture casts | Tighten valid-fixture builders; keep+document intentional invalid-input casts | Default (comments.md) |
| Does MSW need a consumer this round to avoid dead infra? | Yes — one smoke test proving interception; `plannerApi` real coverage deferred | Default (honors user "later" tag) |
| MSW vs existing `globalThis.fetch = vi.fn()` (`vitest.setup.ts:59`) | Must coexist so all 111 existing tests stay green; exact wiring is a build-time constraint, not an open product question (Invariant 4) | Codebase `vitest.setup.ts:59` |
| Do the Phase 1 hooks need MSW? | No — `usePlannerEditorStore` is pure Zustand (`renderHook`); `usePlannerSave` uses injected adapters (fake them). MSW is irrelevant to both. | Codebase `usePlannerSave.ts:439` deps |
| Test runner / commands | Vitest (`yarn --cwd frontend test`), `tsc -b` (types), `eslint .` (lint); coverage v8, **no thresholds** configured (`vite.config.ts:167`) | Codebase `package.json`, `vite.config.ts` |
| Does this touch the backend? | No — FE-only validator names | Memory `arch-backend-plannercontentvalidator` |
| `extractTier()` collision (`egoGiftFilter.ts:24` vs `egoGiftSort.ts:20`) | Out of scope — not reached by this refactor | User (this session) |
| Data-driven spec sections (`docs/spec.md`) required? | N/A — not a data-driven feature (no `static/data/*.json` consumption) | Convention (`docs/spec.md`) |
| `canSelectFloorThemePack` direct test? | None exists; move is behavior-preserving, a direct test is optional (not required) | Codebase (`grep` of test file) |

## Description

Four phases, each a single atomic behavior-preserving commit, each leaving `tsc -b` + the full suite green.

**Phase 1 — Characterization tests (the safety net).**
- `src/hooks/__tests__/usePlannerSave.test.ts` — drive the hook via `renderHook` with **fake** `saveAdapter`/`syncAdapter`; pin observable behavior: `save()` returns `true` on success / `false` on validation failure; the validate→sync→local call order (assert adapter call sequence); error-state surface (`saveError`, restriction handling); draft vs published path selection. Golden-master of current behavior.
- `src/stores/__tests__/usePlannerEditorStore.test.tsx` — `renderHook`, no mocks; pin state transitions for the public actions (equipment set/clear, floor selection, deployment order, dirty tracking, reset/hydrate). Cover the Set↔array serialization edges noted in analysis.

**Phase 2 — Rename the validator pair.**
- In `plannerHelpers.ts`: rename `validatePlannerForSave` → `validatePlannerForPublish`, `validatePlannerUserFriendly` → `validatePlannerForDraftSave`; fix the stale JSDoc example at `:924` (correct name + correct 3-arg signature).
- Repoint 3 production consumers + 2 test files (see Target). No logic change.

**Phase 3 — Split `plannerHelpers.ts` → 3 modules.**
- Create `plannerValidationErrors.ts`, `plannerRules.ts`, `plannerValidation.ts`; move symbols per the Resolved-Ambiguities mapping; wire internal imports (`plannerValidation` imports from the other two).
- Delete `plannerHelpers.ts`; repoint all 4 import sites.
- Split `plannerHelpers.test.ts` into `plannerValidation.test.ts` / `plannerValidationErrors.test.ts` / `plannerRules.test.ts`; tighten valid-fixture casts; document the intentional invalid-input casts.

**Phase 4 — MSW policy.**
- Install `msw` (dev dep); wire a server in `vitest.setup.ts` (`beforeAll(listen)` / `afterEach(resetHandlers)` / `afterAll(close)`), coexisting with existing module mocks so all current tests stay green.
- Add `src/test-utils/` MSW server + handlers scaffold and one smoke test asserting interception.
- Amend `.claude/rules/frontend/testing.md` with the hybrid policy table (network→MSW; pure state→`renderHook`; orchestration→fake adapter).

## Scope (read for context)

- `frontend/src/lib/plannerHelpers.ts` (+ `lib/__tests__/plannerHelpers.test.ts`)
- `frontend/src/hooks/usePlannerSave.ts`, `frontend/src/hooks/usePlannerSaveAdapter.ts`, `usePlannerSyncAdapter.ts`
- `frontend/src/stores/usePlannerEditorStore.tsx`
- `frontend/src/components/plannerViewer/PlannerDetailHeader.tsx` (+ its test), `hooks/useMDUserPlannersData.ts`, `components/floorTheme/FloorThemeGiftSection.tsx`
- `frontend/vitest.setup.ts`, `frontend/vite.config.ts` (test/coverage block at `:147`)
- `.claude/rules/frontend/testing.md`
- Reference: `docs/23-fe-feature-first-migration/instructions.md` (atomic-commit + delete-and-repoint pattern)

## Target (create / modify)

Create:
- `src/lib/plannerValidation.ts`, `src/lib/plannerValidationErrors.ts`, `src/lib/plannerRules.ts`
- `src/lib/__tests__/plannerValidation.test.ts`, `plannerValidationErrors.test.ts`, `plannerRules.test.ts`
- `src/hooks/__tests__/usePlannerSave.test.ts`
- `src/stores/__tests__/usePlannerEditorStore.test.tsx`
- `src/test-utils/mswServer.ts` (+ handlers) and one smoke test

Modify:
- `src/hooks/usePlannerSave.ts:13` — repoint to `plannerValidation` (validators + `validateNoteSizes`) + `plannerValidationErrors` (`toUserFriendlyError`); update call sites `:399,:409,:661`.
- `src/components/plannerViewer/PlannerDetailHeader.tsx:46,305` — repoint + rename call.
- `src/hooks/useMDUserPlannersData.ts:22,418,421` — repoint + rename calls.
- `src/components/floorTheme/FloorThemeGiftSection.tsx:15` — repoint to `plannerRules`.
- `src/components/plannerViewer/__tests__/PlannerDetailHeader.test.tsx:116-117` — update mocked names.
- `frontend/vitest.setup.ts` — MSW server lifecycle (resolve interaction with `:59` global fetch mock).
- `.claude/rules/frontend/testing.md` — hybrid mocking policy.

Delete (after move):
- `src/lib/plannerHelpers.ts`, `src/lib/__tests__/plannerHelpers.test.ts`.

## Impact Analysis

- **Files modified:** ~16 (3 new lib modules, 3 new lib tests, 2 new hook/store tests, MSW scaffold +
  smoke test, 4 repointed consumers, 1 consumer test, vitest setup, testing.md; 2 deletions). Blast
  radius contained to planner-core + the test harness.
- **Dependencies / ripple:** the renamed validators have **3 production consumers** (`usePlannerSave`,
  `PlannerDetailHeader`, `useMDUserPlannersData`) + **2 test files**; the split touches **4 import
  sites** (those 3 minus the double-count, plus `FloorThemeGiftSection`). `tsc -b` is the backstop for
  any missed reference. No barrel re-exports `plannerHelpers`, so deletion is safe.
- **MSW ripple:** introducing a fetch interceptor can disturb the existing `globalThis.fetch = vi.fn()`
  (`vitest.setup.ts:59`); all 111 existing tests must stay green (Invariant 4).

## Risk Assessment

- **Edge cases:** intentional invalid-input fixtures must remain type-invalid after cast-tightening
  (don't "fix" them into valid data — that deletes the test); `canSelectFloorThemePack` has only
  indirect coverage; the Set↔array serialization edges in the store.
- **Performance:** none — pure structural move + test additions.
- **Security:** none — no data boundary or input-handling change.

## Boundaries & Invariants

- **Trust/ownership boundary:** none crossed at runtime. The only new boundary is the MSW test harness
  intercepting `fetch` inside the test process.
- **Invariant 1 (behavior preservation):** every `validate*` / `toUserFriendlyError` / rule function
  returns output identical to pre-refactor for identical input. This is a refactor, not a rewrite.
- **Invariant 2 (no consumer breaks):** the app and all 3 production consumers compile and behave
  identically; no dangling imports after delete-and-repoint.
- **Invariant 3 (honest types):** valid-fixture builders type-check with no casts; every remaining cast
  is an intentional invalid-input construction with a one-line warning comment.
- **Invariant 4 (test-convention truth):** `testing.md` matches reality; `msw` is installed and wired;
  the full pre-existing suite (111 tests) stays green.
- **Invariant 5 (golden-master fidelity):** Phase 1 tests assert *current* behavior, including the
  validate→sync→local order — they do not encode desired-but-unimplemented behavior.

## Failure Modes

> This is a single-developer local refactor with no new runtime state machine. The concurrency /
> dependency-flap rows are N/A (no shared mutable runtime state is introduced); listed modes are the
> ways the *migration* breaks, each with the test that proves the response.

| Invariant | Trigger (how it breaks) | Response | Test |
|-----------|-------------------------|----------|------|
| Inv 1 | A validator's logic subtly altered during the move | Build/tests go red on mismatched output | split `plannerValidation.test.ts` (golden assertions) + `tsc -b` |
| Inv 2 | An import path missed during rename or split — partial state | `tsc -b` lists every dangling import; land as ONE commit per phase (self-healing via compiler) | `tsc -b` green per phase |
| Inv 2 | Re-running a completed move step (idempotency) | No-op — symbols already at destination | `git status` clean on re-run |
| Inv 3 | A blanket cast left on a valid fixture re-hides shape drift | Tightened fixtures fail to compile if the real type diverges | `tsc -b` over the split test files |
| Inv 4 | MSW interceptor clobbers the global `fetch` mock → existing tests break | Wire MSW to coexist (`onUnhandledRequest` + ordering with `:59`); revert if any of the 111 regress | full `yarn --cwd frontend test` green |
| Inv 5 | Phase 1 test encodes the *intended* (reordered) save/sync flow instead of the current one | Golden-master review: tests must mirror `usePlannerSave.ts:399-439` as-is | code review of Phase 1 diff vs current source |

### Visualized Failure (worst row: Inv 5 — golden-master drift)

1. While writing `usePlannerSave.test.ts`, the author "knows" sync should happen before validation and
   asserts that order.
2. The test passes only because the fake adapters don't enforce real ordering.
3. **Broken state reached:** the safety net now encodes behavior the code does not have. The later
   behavioral track changes the code to match — and the test that should have caught a regression
   instead *blesses* the change silently.
   → **Response intervenes here:** Phase 1 tests are reviewed against `usePlannerSave.ts:399-439`
   line-by-line; they must assert validate→sync→local exactly as written today. The whole point of a
   golden master is to pin reality, so the deferred reorder shows up as a deliberate, visible test edit.

## Done When

- [ ] `usePlannerSave.test.ts` and `usePlannerEditorStore.test.tsx` exist and pass; both assert current
      observable behavior (Inv 5), including the save/sync call order.
- [ ] `validatePlannerForSave` / `validatePlannerUserFriendly` no longer exist; `validatePlannerForPublish`
      / `validatePlannerForDraftSave` are used at all 3 production consumers; JSDoc example at old `:924`
      corrected.
- [ ] `plannerValidation.ts`, `plannerValidationErrors.ts`, `plannerRules.ts` exist; `plannerHelpers.ts`
      and its test are deleted; all 4 import sites repointed; `grep -rn "plannerHelpers" src` returns zero.
- [ ] `plannerHelpers.test.ts` split into three files; valid-fixture builders have zero casts; remaining
      casts each carry a one-line warning comment.
- [ ] `msw` in `package.json` devDependencies; MSW server wired in `vitest.setup.ts`; one smoke test
      proves interception; `testing.md` carries the hybrid policy table.
- [ ] `yarn --cwd frontend typecheck`, `yarn --cwd frontend test`, `yarn --cwd frontend lint` all pass;
      the full 111-test pre-existing suite stays green.
- [ ] No runtime behavior change anywhere (Inv 1).

## Test Plan

### Test Runner
- Framework: **Vitest** (unit/hook/integration), `tsc -b` (types), `eslint .` (lint). `renderHook` from
  `@testing-library/react`. Per `frontend/package.json`.
- Commands (redirect to `/tmp/<prefix>-<session-id>-<suffix>.log`; use `--cwd frontend`, never `cd`):
  - `yarn --cwd frontend typecheck`
  - `yarn --cwd frontend test run` (full suite — proves Inv 2/4)
  - `yarn --cwd frontend test run usePlannerSave usePlannerEditorStore plannerValidation`
  - `yarn --cwd frontend lint`

### Tests to Write
- [ ] `usePlannerSave.test.ts` — fake-adapter golden master: success/failure return, validate→sync→local
      order, error surface, draft-vs-publish selection (realizes Inv 1/5 tests).
- [ ] `usePlannerEditorStore.test.tsx` — `renderHook` state transitions + Set↔array edges.
- [ ] `plannerValidation.test.ts` / `plannerValidationErrors.test.ts` / `plannerRules.test.ts` — the
      existing 96 cases redistributed unchanged in logic (realizes Inv 1 test).
- [ ] MSW smoke test — a registered handler intercepts a `fetch` and returns a fixture (realizes Inv 4).
- [ ] Every `Test` cell in Failure Modes is realized above or is a `tsc -b` / full-suite / review gate.

## Verification

### Manual
1. `yarn --cwd frontend typecheck` → green (rename + split compile).
2. `yarn --cwd frontend test run` → green; test count = prior 111 + new cases, none lost.
3. `grep -rn "validatePlannerForSave\|validatePlannerUserFriendly\|plannerHelpers" frontend/src` → zero hits.
4. Run the app: save a draft and publish a planner via `PlannerDetailHeader` and the MD editor; confirm
   identical validation messages (same i18n keys) pre/post.
5. Temporarily break one MSW handler → smoke test fails; restore.

### Edge Cases
- [ ] Intentional invalid-input fixture (e.g. `{ identity: null }`) still triggers its validator error post-tighten.
- [ ] `FloorThemeGiftSection` (the only `plannerRules` UI consumer) renders and gates pack selection identically.
- [ ] Removing the MSW server lifecycle leaves the 18 existing `vi.mock` tests green (they don't depend on it).
