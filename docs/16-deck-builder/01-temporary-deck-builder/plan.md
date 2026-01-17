# Execution Plan: Standalone Deck Builder Page

## Planning Gaps
None. Research complete. All patterns documented.

## Execution Overview
Extract core deck builder UI from DeckBuilderPane → Create standalone page with ephemeral store → Add route → Refactor original to use extracted component.

**Strategy**: Extract → Create Page → Wire Route → Refactor Original (backward-compatible)

---

## Dependency Analysis

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| router.tsx | High | None | All Link components, navigation |
| DeckBuilderPane.tsx | Low | usePlannerEditorStore, data hooks | PlannerMDEditorContent only |
| DeckBuilderContent.tsx (new) | Low | usePlannerEditorStore, data hooks | DeckBuilderPane, DeckBuilderPage |
| DeckBuilderPage.tsx (new) | Low | DeckBuilderContent, Store provider | router.tsx |

### Ripple Effect Map
- router.tsx → DeckBuilderPage.tsx (new route)
- DeckBuilderPane.tsx → DeckBuilderContent.tsx (imports extracted content)

### High-Risk Modifications
1. **router.tsx**: Route ordering critical - `/planner/deck` MUST appear before `/planner/$id`
2. **DeckBuilderPane.tsx**: Refactor must preserve exact behavior for existing planner editor

---

## Execution Order

### Phase 1: Extract DeckBuilderContent

**Step 1**: Create `DeckBuilderContent.tsx`
- Depends on: none
- Enables: F1 (core UI), F2 (filtering), F3 (progressive rendering)
- Source: Extract lines 46-600 from DeckBuilderPane.tsx
- Move: All useState, useRef, useEffect, useMemo, handlers, JSX content
- Remove: Dialog wrapper, modal backdrop, open prop logic
- Add props: `isDialogMode`, `onImport`, `onExport`, `onResetOrder`

### Phase 2: Refactor DeckBuilderPane

**Step 2**: Refactor `DeckBuilderPane.tsx` to use DeckBuilderContent
- Depends on: Step 1
- Enables: I1 (planner editor unchanged)
- Keep: Dialog wrapper, open/onOpenChange, DialogHeader/Footer, Done button
- Replace: All extracted content with `<DeckBuilderContent isDialogMode={true} />`
- Result: ~50 lines (down from 612)

### Phase 3: Create DeckBuilderPage

**Step 3**: Create `DeckBuilderPage.tsx`
- Depends on: Step 1
- Enables: F4 (standalone access), F5 (ephemeral state)
- Pattern: PlannerMDNewPage.tsx
- Structure: PlannerEditorStoreProvider → Suspense → DeckBuilderPageContent

**Step 4**: Implement `DeckBuilderPageContent` (inner component)
- Depends on: Step 3
- Enables: F6 (import/export)
- Contains: import/export handlers, confirmation dialog state
- Renders: page header, DeckBuilderContent, ImportConfirmDialog

### Phase 4: Wire Route

**Step 5**: Add route to `router.tsx`
- Depends on: Step 3
- Enables: I2 (navigation access)
- Position: AFTER plannerMDNewRoute, BEFORE plannerMDDetailRoute
- Path: `/planner/deck`

### Phase 5: Test

**Step 6**: Manual testing per instructions.md
- Depends on: Steps 1-5
- Verifies: All features and edge cases

---

## Verification Checkpoints

### After Step 2 (Backward Compatibility)
- Existing planner editor opens DeckBuilderPane normally
- All filtering, sorting, selection works in dialog
- Progressive rendering batches 10 cards/frame
- Scroll position preserved on equipment change

### After Step 5 (Full Feature)
- Navigate to `/planner/deck`
- Page shows 12 sinners with default equipment
- Equipment resets on navigation away and back
- Export copies deck code, Import shows confirmation dialog

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Route param capture | Step 5 | Place `/planner/deck` BEFORE `$id` in tree |
| Progressive rendering broken | Step 1 | Copy useEffect + requestAnimationFrame exactly |
| Snapshot timing wrong | Step 2 | Pass `isDialogMode={true}` for `open` dependency |
| Store not reset on revisit | Step 3 | Use PlannerEditorStoreProvider (fresh per mount) |
| Scroll restoration fails | Step 1 | Preserve savedScrollPositionRef pattern exactly |

---

## Pre-Implementation Validation Gate

| Check | Status |
|-------|--------|
| Pattern Source (PlannerMDNewPage.tsx) | PASS |
| createDefaultEquipment() in store | PASS |
| deckCode.ts utilities exist | PASS |
| Sub-components exist | PASS |
| Data hooks exist | PASS |
| Research complete | PASS |

---

## Rollback Strategy

- **Step 1-2 fails**: Revert DeckBuilderPane, delete DeckBuilderContent
- **Step 3-4 fails**: Delete DeckBuilderPage, DeckBuilderContent still works
- **Step 5 fails**: Remove route, page inaccessible but nothing breaks
