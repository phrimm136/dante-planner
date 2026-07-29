# Execution Plan

## Phase Summary

Five sequential phases, bottom-up: extend the state slice first, then the pure predicate (unit-testable in isolation), then the visual shrink of `EntityToggle`, then the new `DeckFilterBar` component, then wire everything into `DeckBuilderContent`. Each phase is independently verifiable via `yarn --cwd frontend tsc --noEmit` + scoped vitest runs. The predicate carries most of the behavioral risk, so it gets full unit coverage in Phase 2 before any UI wiring.

## Phases

### Phase 1: Extend `DeckFilterState` + store factory

- Files:
  - `frontend/src/types/DeckTypes.ts` (MODIFY — add 8 fields to `DeckFilterState`)
  - `frontend/src/stores/usePlannerEditorStore.tsx` (MODIFY — extend `createDefaultDeckFilterState()` with empty `Set`s for the 8 new fields)
- Tests: none (type/factory only; no behavior)
- Depends on: none
- Verify:
  - `yarn --cwd frontend tsc --noEmit` passes
  - `yarn --cwd frontend test` still passes (no regression in existing consumers)

### Phase 2: Predicate (`matchesDeckFilter`) + `filterUtils` extension

- Files:
  - `frontend/src/lib/deckFilter.ts` (CREATE — `matchesDeckFilter(item, state, mode)` with mode-gated field application)
  - `frontend/src/lib/__tests__/deckFilter.test.ts` (CREATE — all predicate tests from spec)
  - `frontend/src/lib/filterUtils.ts` (MODIFY — extend `calculateActiveFilterCount` to count the new sets; prefer accepting `DeckFilterState` or additive signature)
  - `frontend/src/lib/__tests__/filterUtils.test.ts` (MODIFY — cover new fields)
- Tests:
  - `deckFilter.test.ts`: id-only fields ignored in EGO mode; ego-only field ignored in identity mode; AND across categories; keyword "match all" semantics preserved; empty sets match all; search matches existing fields (mirror `DeckBuilderContent` lines 305–321)
  - `filterUtils.test.ts`: updated count with new fields
- Depends on: Phase 1 (uses extended `DeckFilterState` type)
- Verify:
  - `yarn --cwd frontend test src/lib/__tests__/deckFilter.test.ts` — all green
  - `yarn --cwd frontend test src/lib/__tests__/filterUtils.test.ts` — all green
  - `yarn --cwd frontend tsc --noEmit` passes

### Phase 3: Shrink `EntityToggle` in place

- Files:
  - `frontend/src/components/deckBuilder/EntityToggle.tsx` (MODIFY — drop `flex-1`, reduce `h-14` → match dropdown trigger height, reduce `p-2`; no prop change, no variant)
- Tests: none (visual-only; covered by `DeckFilterBar.test.tsx` later)
- Depends on: none (purely visual; could run first, but grouped logically after state/predicate foundation)
- Verify:
  - `yarn --cwd frontend tsc --noEmit` passes
  - `yarn --cwd frontend test` still passes
  - Manual: toggle still renders and switches modes in existing `DeckBuilderContent` usage before Phase 5 replaces it

### Phase 4: `DeckFilterBar` component

- Files:
  - `frontend/src/components/deckBuilder/DeckFilterBar.tsx` (CREATE — desktop single-row + mobile expandable card; consumes `DeckFilterState` + setters from store; mode-dependent category rendering; primary = Toggle+Sinner+Keyword, secondary = rest; Search + Reset All at end)
  - `frontend/src/components/deckBuilder/__tests__/DeckFilterBar.test.tsx` (CREATE)
- Tests (from spec Test Plan, component section):
  - identity mode shows all 9 categories
  - EGO mode hides Def/Rank/UnitKw, shows EgoType
  - Reset All clears all sets + search, preserves entityMode
  - mode switch preserves inert selections in store
  - mobile chevron toggles secondary visibility
- Depends on: Phase 1 (state), Phase 2 (optional — predicate not used directly by bar; actives count uses `calculateActiveFilterCount`), Phase 3 (shrunken toggle visual)
- Verify:
  - `yarn --cwd frontend test src/components/deckBuilder/__tests__/DeckFilterBar.test.tsx` — all green
  - `yarn --cwd frontend tsc --noEmit` passes

### Phase 5: Wire into `DeckBuilderContent`

- Files:
  - `frontend/src/components/deckBuilder/DeckBuilderContent.tsx` (MODIFY — replace existing toggle+sinner+keyword+search block (~lines 581–603) with `<DeckFilterBar />`; replace inline filter predicate (~lines 292–325) for both identity and EGO memos with calls to `matchesDeckFilter`; add `handleResetAll` that calls `setDeckFilterState` preserving `entityMode`)
- Tests: integration covered by Phase 4 component tests + existing DeckBuilder tests (if any)
- Depends on: Phases 1, 2, 3, 4
- Verify:
  - `yarn --cwd frontend tsc --noEmit` passes
  - `yarn --cwd frontend test` — full suite passes
  - Manual verification per spec's `Verification > Manual` section

## Phase Dependencies

- Group A (serial, foundation): Phase 1 → Phase 2
- Group B (parallelizable after A, but executed serially by default): Phase 3 (independent of A but grouped here for simplicity)
- Group C (after A + B): Phase 4
- Group D (after all): Phase 5

Execution order: 1 → 2 → 3 → 4 → 5
