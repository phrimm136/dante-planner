# Task: Deck Builder Filter Bar Expansion

Expand the deck builder's identity/EGO selection section from 2 filter categories (Sinner, Keyword) to the full filter set used on the identity/EGO browser sidebars, packed into a single horizontal row with a mobile expandable fallback.

## Decisions

- **Mode-dependent filter set** — identity mode uses the IdentityPage sidebar order; EGO mode uses the EGOPage sidebar order. Def/Rank/UnitKw render only in identity mode; EgoType renders only in EGO mode. Reason: avoids inert UI and matches data shape — `EGOListItem` has no `defenseTypes` and uses `egoType` (ZAYIN–ALEPH) in place of numeric `rank`.
- **Extract `DeckFilterBar` component** — new file under `components/deckBuilder/`, owns desktop single-row + mobile expandable layouts. Reason: `FilterSidebar` cannot be reused directly (its desktop half is a `sticky aside` meant for list pages, not dialog content); the deck builder lives inside `DeckBuilderPane`'s `Dialog`.
- **Unified `DeckFilterState` slice** — one store slice with all fields, id-only / ego-only fields inert (not cleared) when in the opposite mode. Reason: selections preserve across mode toggles; single source of truth matches the existing slice in `usePlannerEditorStore`.
- **Centralized predicate** — new `matchesDeckFilter(item, state, mode)` helper that short-circuits id-only fields when `mode === 'ego'` and ego-only fields when `mode === 'identity'`. Reason: replaces the ad-hoc inline filter loop in `DeckBuilderContent` (lines 292–325) with testable logic; follows precedent of `lib/defenseTypeFilter.ts`, `lib/egoGiftFilter.ts`, `lib/themePackFilter.ts`.
- **Shrink `EntityToggle` in place (no `size` prop)** — the component has exactly one caller, which is the one being refactored. A `size="default"` variant would have zero consumers. Reason: YAGNI + CLAUDE.md rule #1 (surgical precision).
- **Toggle inside the filter row, first position** — not above or beside it. Reason: `DeckBuilderPane` is a `Dialog` with capped viewport height; every additional row eats scroll budget for the grid below.
- **Single "Reset All"** — clears all 9 filter sets and `searchQuery`. Preserves `entityMode`. No per-category reset. Reason: matches `IdentityPage.handleResetAll` precedent; keeps one reset path.
- **Mobile pattern mirrors `FilterSidebar`** — primary (Toggle, Sinner, Keyword) always visible; secondary (Attr, Atk, Def|EgoType, Rank, Season, UnitKw, BattleKw) behind a centered chevron-collapse; Search + Reset All at bottom. Reason: users meet the same interaction on the browser sidebar and the deck builder.
- **Desktop first pass is literally one row with flex-wrap** — we'll iterate after rendering. Reason: 10 slots in identity mode is tight; empirical tuning beats speculative layout.

## Description

**Goal:** Let users narrow the identity/EGO list inside the deck builder by the same filters available on the browser pages, in one horizontal bar that doesn't grow the dialog vertically.

**Filter categories (order matches sidebar):**

| Order | Identity mode | EGO mode | Component |
|---|---|---|---|
| 1 | EntityToggle | EntityToggle | `EntityToggle` (shrunk) |
| 2 | Sinner | Sinner | `CompactSinnerFilter` |
| 3 | Keyword | Keyword | `CompactKeywordFilter` |
| 4 | Skill Attribute | Skill Attribute | `CompactSkillAttributeFilter` |
| 5 | Attack Type | Attack Type | `CompactAttackTypeFilter` |
| 6 | Defense Type | EGO Type | `CompactDefenseTypeFilter` \| `CompactEGOTypeFilter` |
| 7 | Rank | — | `CompactRarityFilter` (id only) |
| 8 | Season | Season | `SeasonDropdown` |
| 9 | Unit Keywords | — | `UnitKeywordDropdown` (id only) |
| 10 | Additional Keywords | Additional Keywords | `BattleKeywordDropdown` |
| 11 | Search | Search | `SearchBar` |
| 12 | Reset All | Reset All | `Button` (variant=outline) |

**Behavior:**

- All existing Sinner + Keyword filter behavior preserved; only visual layout and the filter set size change.
- Filter state lives in `DeckFilterState` (Zustand slice), persists across mode toggles.
- Mode switch swaps which filter chips render but does not reset their selections.
- `Reset All` clears all filter sets + `searchQuery`, preserves `entityMode`.
- Desktop (≥lg): single horizontal row, flex-wrap on overflow.
- Mobile (<lg): expandable card — Toggle+Sinner+Keyword always visible, chevron toggles the rest; Search + Reset All at card bottom.
- Active-filter count badge visible on mobile collapsed state (uses `calculateActiveFilterCount` extended with new fields).

## Scope

### Files to READ for context

- `frontend/src/routes/IdentityPage.tsx` — filter order + handler pattern
- `frontend/src/routes/EGOPage.tsx` — EGO filter subset (no Def/Rank/UnitKw; has EgoType)
- `frontend/src/components/filter/FilterSidebar.tsx` — mobile expandable pattern (chevron, primary/secondary split)
- `frontend/src/components/filter/FilterPageLayout.tsx` — named-slots composition precedent
- `frontend/src/components/filter/CompactIconFilter.tsx` — base icon-filter primitive
- `frontend/src/components/filter/CompactSinnerFilter.tsx`
- `frontend/src/components/filter/CompactKeywordFilter.tsx`
- `frontend/src/components/filter/CompactSkillAttributeFilter.tsx`
- `frontend/src/components/filter/CompactAttackTypeFilter.tsx`
- `frontend/src/components/filter/CompactDefenseTypeFilter.tsx`
- `frontend/src/components/filter/CompactRarityFilter.tsx`
- `frontend/src/components/filter/CompactEGOTypeFilter.tsx`
- `frontend/src/components/filter/SeasonDropdown.tsx`
- `frontend/src/components/filter/UnitKeywordDropdown.tsx`
- `frontend/src/components/filter/BattleKeywordDropdown.tsx`
- `frontend/src/components/filter/FilterSection.tsx`
- `frontend/src/components/deckBuilder/EntityToggle.tsx`
- `frontend/src/components/deckBuilder/DeckBuilderContent.tsx` — current filter wiring (lines 292–325 predicate, lines 555–689 layout)
- `frontend/src/components/deckBuilder/DeckBuilderPane.tsx` — dialog host
- `frontend/src/stores/usePlannerEditorStore.tsx` — `DeckFilterState` slice (lines 96–103 factory, 131 WarmState, 175/302–308 setter, 610 selector)
- `frontend/src/types/DeckTypes.ts` — `DeckFilterState` interface (lines 101–110)
- `frontend/src/types/IdentityTypes.ts` — `IdentityListItem` (lines 8–25)
- `frontend/src/types/EGOTypes.ts` — `EGOListItem` (lines 19–30)
- `frontend/src/lib/constants.ts` — `SINNERS`, `AFFINITIES`, `ATK_TYPES`, `DEF_TYPES`, `RANKS`, `SEASONS`, `STATUS_EFFECTS`, `EGO_TYPES`
- `frontend/src/lib/filterUtils.ts` — `calculateActiveFilterCount`
- `frontend/src/lib/__tests__/filterUtils.test.ts` — test pattern for count logic
- `frontend/src/lib/__tests__/defenseTypeFilter.test.ts` — test pattern for predicate
- `frontend/src/lib/__tests__/egoGiftFilter.test.ts` — test pattern for mode-dependent predicate

## Target

### Files to CREATE

- `frontend/src/components/deckBuilder/DeckFilterBar.tsx` — desktop single-row + mobile expandable bar
- `frontend/src/lib/deckFilter.ts` — `matchesDeckFilter(item, state, mode)` predicate
- `frontend/src/lib/__tests__/deckFilter.test.ts` — unit tests for predicate
- `frontend/src/components/deckBuilder/__tests__/DeckFilterBar.test.tsx` — interaction tests (mode swap visibility, reset behavior)

### Files to MODIFY

- `frontend/src/types/DeckTypes.ts` — extend `DeckFilterState` with 8 new fields:
  - `selectedAttributes: Set<SkillAttributeType>`
  - `selectedAtkTypes: Set<AtkType>`
  - `selectedDefTypes: Set<DefType>` (identity-only application)
  - `selectedRaritys: Set<number>` (identity-only application)
  - `selectedEgoTypes: Set<EGOType>` (ego-only application)
  - `selectedSeasons: Set<Season>`
  - `selectedUnitKeywords: Set<string>` (identity-only application)
  - `selectedBattleKeywords: Set<string>`
- `frontend/src/stores/usePlannerEditorStore.tsx`:
  - Extend `createDefaultDeckFilterState()` with empty `Set`s for the 8 new fields
  - Verify setter `setDeckFilterState` (lines 175, 302–308) accepts partial updates (should already)
  - Extend `useDeckFilterState` selector if needed
- `frontend/src/components/deckBuilder/EntityToggle.tsx`:
  - Drop `flex-1` on buttons → intrinsic/min width
  - Reduce wrapper height from `h-14` to match dropdown trigger height (`h-9` or equivalent)
  - Reduce `p-2` to tighter padding
  - No prop changes
- `frontend/src/components/deckBuilder/DeckBuilderContent.tsx`:
  - Replace current toggle + sinner + keyword + search block (lines ~581–603) with `<DeckFilterBar />`
  - Replace inline filter predicate (lines 292–325) with calls to `matchesDeckFilter` for both identity and EGO memos
  - Add `handleResetAll` that calls `setDeckFilterState` with reset sets + empty search, preserving `entityMode`
- `frontend/src/lib/filterUtils.ts`:
  - Extend `calculateActiveFilterCount` signature to accept the new filter sets (or accept a single `DeckFilterState` / union and branch)
- `frontend/src/lib/__tests__/filterUtils.test.ts`:
  - Extend tests to cover new fields

## Impact Analysis

- **Files being modified:**
  - `DeckBuilderContent.tsx` (medium impact) — core UI surface of the deck builder dialog and `/planner/deck` route
  - `DeckTypes.ts` (medium impact) — type is imported across the planner editor slice
  - `usePlannerEditorStore.tsx` (medium impact) — shared state for planner editor and standalone deck builder
  - `EntityToggle.tsx` (low impact) — exactly one caller, visual-only change
  - `filterUtils.ts` (low impact) — utility, signature change affects `calculateActiveFilterCount` callers
- **Dependencies:**
  - `DeckBuilderPane.tsx` consumes `DeckBuilderContent` → no prop changes expected
  - `PlannerMDEditorContent.tsx` consumes `DeckBuilderPane` via planner editor → no prop changes
  - `DeckBuilderPage.tsx` (`/planner/deck`) uses same content → inherits changes
  - Existing `deckCode.ts` encode/decode does not touch filter state → unaffected
- **Ripple effects:**
  - If a Zustand selector re-runs on every store change due to new fields, consumers may re-render more often — mitigate by keeping atomic selectors.
  - `createDefaultDeckFilterState()` must stay stable-referenced or wrapped properly; any caller doing `=== DEFAULT` equality must be audited.
  - `calculateActiveFilterCount` callers in `IdentityPage` and `EGOPage` must not break if signature changes — prefer additive changes or new overload.
- **High-impact files to watch:** None. Store structure is extended, not restructured.

## Risk Assessment

### Edge cases

- **Mode switch with active filters in the inert set** — e.g., user selects DefType=GUARD in identity mode, switches to EGO. Expected: GUARD stays selected in store, predicate ignores it, UI hides the DefType chip. Switching back restores visibility.
- **Mode switch with EgoType selected** — e.g., user selects EgoType=ALEPH, switches to identity mode. Expected: ALEPH stays in store, predicate ignores it, UI hides the chip.
- **Reset All in identity mode** — clears DefType, Rank, UnitKw (and all others) even though they'd also be cleared on EGO mode. Store is shared.
- **Active filter count on mobile** — count must reflect only *applicable* filter fields for the current mode (e.g., EgoType should not contribute to count in identity mode), otherwise the badge misleads. **Design call needed.** Default: count all non-empty sets regardless of mode (simpler, matches IdentityPage behavior). Flag if user wants mode-aware count.
- **Empty state after filtering** — all filters applied, no identities/EGOs match. Existing empty-state handling in `DeckBuilderContent` must still render.
- **Overflow on desktop** — 10 slots in identity mode may exceed dialog width. Flex-wrap falls to second row; acceptable per user's "iterate after seeing result."
- **Keyboard/focus order** — expanding mobile secondary filters should move focus sensibly (no trap).

### Performance

- Filtering uses CSS-hidden pattern (`className={cn(!matches && 'hidden')}`) — no re-mount on filter change. Predicate must stay O(n) per render.
- `matchesDeckFilter` runs inside `useMemo` dependency loop already established — same cost profile as current inline filter.
- New state fields are `Set<T>`; set-equality checks in Zustand selectors must be shallow, not deep. Atomic selectors per field mitigate unnecessary re-renders.

### Security

Not applicable — no user input crosses trust boundaries; filters are client-side only.

## Done When

- [ ] `DeckFilterBar` renders inside `DeckBuilderContent` in place of the current toggle+sinner+keyword+search block
- [ ] Desktop (≥lg) shows all active-mode filter categories in a single horizontal row (flex-wrap allowed)
- [ ] Mobile (<lg) shows Toggle+Sinner+Keyword always; chevron expands to reveal secondary filters; Search + Reset All at bottom
- [ ] Identity mode shows Sinner, Keyword, Skill Attr, Atk, Def, Rank, Season, Unit Kw, Battle Kw in sidebar order
- [ ] EGO mode shows Sinner, Keyword, Skill Attr, Atk, EGO Type, Season, Battle Kw (no Def, no Rank, no Unit Kw)
- [ ] All filters narrow the grid below using CSS-hidden (no re-mount on filter change)
- [ ] Switching mode preserves filter selections; inert fields are ignored by predicate but kept in state
- [ ] Reset All clears all 9 filter sets + `searchQuery`; preserves `entityMode`
- [ ] `EntityToggle` visual size reduced in place, no prop added
- [ ] `matchesDeckFilter(item, state, mode)` unit-tested for: id-only fields ignored in EGO mode, ego-only field ignored in identity mode, search/sinner/keyword behave as before, empty filter sets match all
- [ ] All existing tests pass (`yarn --cwd frontend test`)
- [ ] No TypeScript errors (`yarn --cwd frontend tsc --noEmit`)

## Test Plan

### Test Runner

- **Framework:** Vitest (`frontend/package.json: "test": "vitest"`)
- **Run command (scoped):**
  - Predicate: `yarn --cwd frontend test src/lib/__tests__/deckFilter.test.ts`
  - Component: `yarn --cwd frontend test src/components/deckBuilder/__tests__/DeckFilterBar.test.tsx`
  - Full check: `yarn --cwd frontend test`

### Tests to Write

- [ ] **Predicate — id-only fields ignored in EGO mode:** `deckFilter.test.ts` — assert `matchesDeckFilter(ego, { selectedDefTypes: Set(['GUARD']) }, 'ego')` returns true despite EGO having no `defenseTypes`
- [ ] **Predicate — ego-only field ignored in identity mode:** `deckFilter.test.ts` — assert `matchesDeckFilter(identity, { selectedEgoTypes: Set(['ALEPH']) }, 'identity')` returns true
- [ ] **Predicate — AND across categories:** `deckFilter.test.ts` — item must match all non-empty selected sets
- [ ] **Predicate — all keywords match (not any):** `deckFilter.test.ts` — preserve existing `hasAllKeywords` semantics for `selectedKeywords`
- [ ] **Predicate — empty sets match all:** `deckFilter.test.ts`
- [ ] **Predicate — search matches name + id-variant fields:** `deckFilter.test.ts` — mirror existing `DeckBuilderContent` lines 305–321 search behavior
- [ ] **Component — identity mode shows all 9 categories:** `DeckFilterBar.test.tsx`
- [ ] **Component — EGO mode hides Def/Rank/UnitKw, shows EgoType:** `DeckFilterBar.test.tsx`
- [ ] **Component — Reset All clears all sets + search, preserves entityMode:** `DeckFilterBar.test.tsx`
- [ ] **Component — mode switch preserves inert selections in store:** `DeckFilterBar.test.tsx`
- [ ] **Component — mobile chevron toggles secondary visibility:** `DeckFilterBar.test.tsx` (simulate narrow viewport)

## Verification

### Manual

1. Navigate to `/planner/deck` (or open deck builder from planner editor)
2. Identity mode: verify 9 filter categories + Toggle + Search + Reset All render in the filter row (desktop viewport)
3. Select Sinner=Yi Sang, DefType=GUARD, Season=5 → grid narrows accordingly
4. Toggle to EGO mode: verify Def/Rank/UnitKw chips disappear, EgoType chip appears, Yi Sang and Season=5 selections persist visually, EGO grid filters accordingly
5. Select EgoType=ZAYIN → grid narrows
6. Toggle back to identity mode → EgoType chip hidden but state retained; identity grid re-filtered by Yi Sang + Season=5 + GUARD
7. Click Reset All → all sets clear, search empties, entityMode stays as identity
8. Resize viewport below `lg` (1024px): verify filter bar collapses to mobile card; Toggle+Sinner+Keyword visible, chevron expands rest
9. Expand chevron → secondary filters render inline; Search + Reset All visible at bottom
10. Active filter count badge reflects current selections

### Edge Cases

- [ ] **All filters empty:** grid shows full list (no filtering)
- [ ] **No matches:** grid empty-state renders, no crash
- [ ] **Filter row overflow on identity mode desktop:** flex-wrap falls gracefully to second row
- [ ] **Mode switch with EgoType selected while in identity mode:** no runtime error; chip hidden; predicate ignores field
- [ ] **Reset All with entityMode=ego:** mode stays at ego; all 9 sets clear; search clears
- [ ] **Existing planner editor (`/planner/plan/:id`) deck builder dialog:** same `DeckFilterBar` renders, same behavior — verify no regression

## Ambiguities

- **Active filter count on mobile (mode-aware?):** currently unspecified. Default assumption: count every non-empty set regardless of mode. If user wants mode-aware count (EgoType contributes only in EGO mode; Def/Rank/UnitKw contribute only in identity mode), flag and adjust during implementation.
- **Desktop filter row width:** first pass is literal one row with flex-wrap. Tuning (e.g., icon-only labels, collapsible sections, horizontal scroll) deferred to post-render iteration per user direction.
- **`docs/spec.md` Data-Driven Features sections:** not applicable — this task does not consume new raw game data. All fields used (`rank`, `season`, `attributeTypes`, `atkTypes`, `defenseTypes`, `unitKeywordList`, `skillKeywordList`, `battleKeywordList`, `egoType`) already flow through existing Zod schemas and `IdentityListItem` / `EGOListItem` types.
