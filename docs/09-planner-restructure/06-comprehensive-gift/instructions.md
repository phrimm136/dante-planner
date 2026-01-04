# Task: Comprehensive Gift Section - Summary + Pane Pattern

## Description
Convert the EGO Gift Comprehensive List section from an inline always-visible layout to a Summary + Pane pattern (like floor-specific gift selection).

**Current behavior:**
- Comprehensive gift section displays inline within the planner page
- Full gift list with filters (keyword + search + sort) always visible
- Takes significant vertical space in the planner

**Target behavior:**
- Summary component shows only selected gifts (clickable area)
- Clicking opens a Dialog pane for gift selection
- Pane contains filters (keyword + search + sort) and full gift list
- Cascade selection logic preserved (selecting a gift with recipe auto-selects ingredients)
- Enhancement selection (0-3 levels) preserved

**Key differences from floor-specific pattern:**
- No theme pack restriction (shows ALL gifts, no `giftIdFilter`)
- Cascade selection: Recipe ingredients auto-select when parent gift is selected
- Silent cascade: Ingredients added without notification (even if hidden by current filters)

**UI/UX behaviors:**
- Empty state: Shows "Select Gifts" placeholder (clickable)
- With selections: Shows grid of gift cards with enhancement levels and tooltips
- Clicking summary opens Dialog with full selection interface
- Filter states are local to pane (not persisted to planner storage)

## Research
- Floor-specific pattern files:
  - `FloorThemeGiftSection.tsx` - Parent orchestration (open state management)
  - `FloorGiftViewer.tsx` - Summary component showing selected gifts
  - `FloorGiftSelectorPane.tsx` - Dialog with filters and selection list
- Observation pattern files:
  - `EGOGiftObservationSummary.tsx` - Alternative summary pattern with PlannerSection wrapper
- Current comprehensive section:
  - `EGOGiftComprehensiveListSection.tsx` - Logic to preserve (cascade selection)
- Shared components:
  - `EGOGiftSelectionList.tsx` - Reusable selection list
  - `EGOGiftKeywordFilter.tsx`, `EGOGiftSearchBar.tsx`, `Sorter.tsx` - Filter components

## Scope
Files to READ for context:
- `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
- `frontend/src/components/floorTheme/FloorGiftViewer.tsx`
- `frontend/src/components/floorTheme/FloorGiftSelectorPane.tsx`
- `frontend/src/components/egoGift/EGOGiftObservationSummary.tsx`
- `frontend/src/components/egoGift/EGOGiftComprehensiveListSection.tsx`
- `frontend/src/routes/PlannerMDNewPage.tsx` (usage context)

## Target Code Area
Files to CREATE or MODIFY:
- `frontend/src/components/egoGift/ComprehensiveGiftSummary.tsx` (CREATE)
- `frontend/src/components/egoGift/ComprehensiveGiftSelectorPane.tsx` (CREATE)
- `frontend/src/components/egoGift/EGOGiftComprehensiveListSection.tsx` (DELETE)
- `frontend/src/routes/PlannerMDNewPage.tsx` (MODIFY - add pane state, wire components)

## System Context (Senior Thinking)
- Feature domain: Planner (MD) - Comprehensive EGO Gift Section
- Core files in this domain:
  - `routes/PlannerMDNewPage.tsx` - Page shell
  - `components/egoGift/EGOGiftComprehensiveListSection.tsx` - Current section
  - `components/egoGift/EGOGiftSelectionList.tsx` - Shared selection list
- Cross-cutting concerns touched:
  - `lib/egoGiftEncoding.ts` - Selection encoding, cascade logic
  - `hooks/useEGOGiftListData.ts` - Data fetching
  - `components/ui/dialog.tsx` - shadcn Dialog component
  - `components/common/PlannerSection.tsx` - Section wrapper

## Impact Analysis
- Files being modified:
  - `PlannerMDNewPage.tsx` (High - page shell, many sections)
  - `EGOGiftComprehensiveListSection.tsx` (Low - being deleted, logic moves to new files)
- What depends on these files:
  - `PlannerMDNewPage` is the main planner page, imports many sections
  - `EGOGiftComprehensiveListSection` is only used by `PlannerMDNewPage`
- Potential ripple effects:
  - None - comprehensive section is isolated to planner page
  - Import path change in `PlannerMDNewPage` (new components)
- High-impact files to watch:
  - `EGOGiftSelectionList.tsx` - DO NOT MODIFY (shared by floor pane)
  - `lib/egoGiftEncoding.ts` - DO NOT MODIFY (cascade logic is correct)

## Risk Assessment
- Edge cases not yet defined:
  - Maximum selection limit (currently Infinity - keep as-is)
  - Very large selection display in summary (wrap behavior)
- Performance concerns:
  - None - same data, just different UI container
- Backward compatibility:
  - Selection data format unchanged (encoded gift IDs)
  - Planner storage format unchanged
- Security considerations:
  - None - no user input beyond selection

## Testing Guidelines

### Manual UI Testing
1. Navigate to planner page (`/planner/md/new`)
2. Scroll to Comprehensive Gift section
3. Verify summary shows "Select Gifts" placeholder when empty
4. Click the summary area
5. Verify Dialog pane opens with full gift list
6. Verify keyword filter is present and functional
7. Select "Combustion" keyword filter
8. Verify only combustion gifts are shown
9. Use search bar to search for a gift name
10. Verify search filters the list correctly
11. Click a gift card's enhancement button (0, 1, 2, or 3)
12. Verify gift is selected with that enhancement level
13. Select a gift that has a recipe (e.g., fusion gifts)
14. Verify cascade selection: recipe ingredients auto-select at level 0
15. Close the dialog (click outside or X button)
16. Verify summary now shows selected gifts with enhancement indicators
17. Verify tooltips work on summary gift cards
18. Click summary again to reopen pane
19. Verify previous selections are preserved
20. Deselect a gift by clicking same enhancement level
21. Verify gift is removed from selection
22. Close dialog and verify summary updates

### Automated Functional Verification
- [ ] Pane opens: Clicking summary opens Dialog
- [ ] Pane closes: Clicking outside/X closes Dialog
- [ ] Filter persistence: Filter states reset when pane reopens
- [ ] Selection persistence: Selected gifts persist after pane close
- [ ] Cascade selection: Recipe gifts auto-select ingredients
- [ ] Enhancement toggle: Click same level deselects, different level changes
- [ ] Summary display: Shows all selected gifts with enhancement levels

### Edge Cases
- [ ] Empty state: Summary shows placeholder, pane shows all gifts
- [ ] Cascade with filters: Ingredients added even if not matching current filter
- [ ] Many selections: Summary wraps gift cards appropriately
- [ ] Recipe ingredient already selected: Cascade doesn't overwrite user's enhancement choice
- [ ] Dialog scroll: Gift list scrolls within dialog, not page

### Integration Points
- [ ] Planner storage: Selection syncs to IndexedDB via existing `usePlannerStorage`
- [ ] Suspense boundary: Components work within existing Suspense wrapper
- [ ] i18n: Uses existing translation keys (check `pages.plannerMD.*`)
