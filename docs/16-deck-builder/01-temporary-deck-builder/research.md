# Research: Standalone Deck Builder Page

## Spec Ambiguities

**NONE FOUND** - All requirements are clear and testable.

---

## Spec-to-Code Mapping

- Default equipment → `usePlannerEditorStore::createDefaultEquipment()` (exists)
- State reset on visit → `PlannerEditorStoreProvider` creates fresh store per page mount
- Import/Export → `PlannerMDEditorContent.tsx:386-423` has working implementation
- Toggle deployment → `DeckBuilderPane.tsx:348-359` handleToggleDeploy
- Identity/EGO tabs → `DeckBuilderPane.tsx` EntityToggle + filterState.entityMode
- Filtering → `DeckBuilderPane.tsx:211-304` sinner/keyword/search with sorted results
- Card selection → `DeckBuilderPane.tsx:539-593` TierLevelSelector wrapper
- Scroll preservation → `DeckBuilderPane.tsx:136-149` savedScrollPositionRef pattern
- Progressive rendering → `DeckBuilderPane.tsx:118-329` batched loading (10 cards/frame)

---

## Pattern Enforcement

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| DeckBuilderPage.tsx | PlannerMDNewPage.tsx:1-66 | Store provider + Suspense + skeleton |
| DeckBuilderContent.tsx | DeckBuilderPane.tsx:1-612 | All filtering, sorting, handlers, rendering |
| router.tsx (modify) | router.tsx:141-189 | Route definition with lazyRouteComponent |

---

## Pattern Copy Deep Analysis

### DeckBuilderPane.tsx Reference Inventory

- **Lines**: 612
- **Store subscriptions**: 6 (equipment, deploymentOrder, filterState + setters)
- **useState hooks**: 8 (sortingIdentityIds, sortingEgoIds, contentReady, visibleCount, etc.)
- **useRef**: 4 (identityScrollRef, egoScrollRef, savedScrollPositionRef, snapshotEntityModeRef)
- **Handlers**: 8 (handleToggleDeploy, handleEquipIdentity, handleEquipEgo, handleUnequipEgo, handleEntityModeChange, handleSinnersChange, handleKeywordsChange, handleSearchChange)
- **useMemo**: 11 computations (filtering, sorting, derived state)
- **useEffect**: 6 (snapshot, deferred load, progressive rendering, scroll restoration)

### Extraction Split

**Stays in DeckBuilderPane** (dialog wrapper):
- Dialog/DialogContent/DialogHeader/DialogFooter
- Modal backdrop (lines 465-471)
- `open`/`onOpenChange` props
- Done button → onOpenChange(false)

**Moves to DeckBuilderContent** (core logic):
- All useState + useRef hooks
- All useEffect operations
- All useMemo computations
- All handlers
- Store subscriptions
- Data hooks (useIdentityListData, useEGOListData, useSearchMappings)
- Content: SinnerGrid + StatusViewer + ActionBar + EntityToggle + Filters + Card grid

### Key Difference
- DeckBuilderPane: receives `open` prop, renders inside Dialog
- DeckBuilderContent: always visible, used standalone or wrapped by DeckBuilderPane

---

## Existing Utilities (Reuse)

| Category | Location | Status |
|----------|----------|--------|
| Deck encoding/decoding | lib/deckCode.ts | encodeDeckCode, decodeDeckCode, validateDeckCode |
| Default equipment | usePlannerEditorStore::createDefaultEquipment() | Returns Record<string, SinnerEquipment> |
| Entity filtering | DeckBuilderPane.tsx | filteredAndSortedIdentities/Egos memos |
| Progressive rendering | DeckBuilderPane.tsx | visibleCount + requestAnimationFrame |
| Scroll preservation | DeckBuilderPane.tsx | savedScrollPositionRef pattern |
| Sub-components | components/deckBuilder/* | SinnerGrid, StatusViewer, DeckBuilderActionBar, EntityToggle |

---

## Gap Analysis

**Missing (create)**:
- DeckBuilderContent.tsx - extracted reusable component
- DeckBuilderPage.tsx - route page with store provider

**Needs modification**:
- DeckBuilderPane.tsx - refactor to use DeckBuilderContent
- router.tsx - add /planner/deck route

**Can reuse 100%**:
- All sub-components (SinnerGrid, StatusViewer, etc.)
- All utility functions (deckCode.ts)
- All types and constants

---

## Testing Requirements

### Manual UI Tests
- Page loads with 12 sinners, default equipment (base identity + ZAYIN)
- Sinner click toggles deployment order
- Reset Order clears all deployment
- Tab switch shows Identity/EGO cards
- Filters work (sinner, keyword, search)
- Tier selector appears on card hover
- Export copies deck code to clipboard
- Import shows confirmation dialog
- Navigation away/back resets to defaults

### Automated Tests
- Default equipment: identity pattern 1XX01, EGO pattern 2XX01
- Store isolation: temp page changes don't affect main planner
- Progressive rendering: no UI freeze with 100+ cards
- Filter persistence: tabs preserve filter state
- Scroll restoration: position preserved after equipment change

### Edge Cases
- Empty/invalid clipboard on import → error toast
- Partial deck code → warnings in dialog
- Deploy all 12 sinners → shows "12 / 12"
- ZAYIN unequip attempt → blocked

---

## Technical Constraints

- Route ordering: /planner/deck before /planner/$id to avoid param capture
- Store lifecycle: fresh instance per page mount (no persistence)
- Progressive rendering: must preserve requestAnimationFrame batching
- Scroll timing: savedScrollPositionRef must be set before state update
