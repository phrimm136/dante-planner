# Task: EGO Gift Observation Summary + EditPane Refactor

## Description

Refactor the EGO Gift Observation section from an inline all-in-one component to the Summary + EditPane pattern used by StartBuff and StartGift sections. This improves architectural consistency, mobile responsiveness, and page load performance.

### Summary Component (Main Page)
- Display starlight cost on the RIGHT side (justify-end), following StartBuff pattern
- Show selected gifts in a HORIZONTAL row (flex-wrap)
- Entire content area is clickable to open the EditPane (no explicit "Edit" button)
- Keyboard accessible: role="button", tabIndex={0}, Enter/Space triggers click
- Empty state: Centered placeholder text "Select EGO Gifts" with muted styling
- Wrap in PlannerSection for consistent header styling

### EditPane Component (Dialog)
- Dialog wrapper with responsive sizing (max-w-[95vw] lg:max-w-[1440px])
- Cost display at top-right (same as Summary)
- Filter row: keyword filter, sorter, search bar (same controls as current section)
- **Desktop layout (lg+):** 9:1 grid columns
  - Left (9 cols): Selection grid using EGOGiftSelectionList
  - Right (1 col): Selected gifts in VERTICAL layout using EGOGiftObservationSelection
- **Mobile layout:** Stacked vertically
  - Selection grid on top
  - Selected gifts section BELOW the grid
- Filter state (selectedKeywords, searchQuery, sortMode) is LOCAL to EditPane
- Filters reset when dialog closes (accepted UX trade-off)
- Max 3 gifts selection enforced via useEffect

### State Management
- `observationGiftIds: Set<string>` - lifted to PlannerMDNewPage (existing)
- `isObservationPaneOpen: boolean` - new state in PlannerMDNewPage
- Filter state stays local to EditPane (not lifted)

## Research

- `StartBuffSection.tsx` - Summary pattern: cost right, clickable area, empty state handling
- `StartBuffEditPane.tsx` - Dialog wrapper pattern, internal filter state
- `StartGiftSummary.tsx` - Alternative Summary pattern reference
- `StartGiftEditPane.tsx` - Alternative EditPane pattern reference
- Current `EGOGiftObservationSection.tsx` - Logic to extract into EditPane
- `EGOGiftObservationSelection.tsx` - Reusable vertical selected display
- `MAX_OBSERVABLE_GIFTS` constant usage
- Cost calculation: `observationEgoGiftCostDataList.find(c => c.egogiftCount === size)`

## Scope

Files to READ for context:
- `frontend/src/components/startBuff/StartBuffSection.tsx` - Summary pattern
- `frontend/src/components/startBuff/StartBuffEditPane.tsx` - EditPane pattern
- `frontend/src/components/egoGift/EGOGiftObservationSection.tsx` - Current implementation
- `frontend/src/components/egoGift/EGOGiftObservationSelection.tsx` - Vertical display (reuse)
- `frontend/src/components/egoGift/EGOGiftSelectionList.tsx` - Selection grid (reuse)
- `frontend/src/routes/PlannerMDNewPage.tsx` - Integration point
- `frontend/src/hooks/useEGOGiftObservationData.ts` - Data hook
- `frontend/src/lib/constants.ts` - MAX_OBSERVABLE_GIFTS

## Target Code Area

Files to CREATE:
- `frontend/src/components/egoGift/EGOGiftObservationSummary.tsx`
- `frontend/src/components/egoGift/EGOGiftObservationEditPane.tsx`

Files to MODIFY:
- `frontend/src/routes/PlannerMDNewPage.tsx` - Replace section, add pane state

Files to DELETE:
- `frontend/src/components/egoGift/EGOGiftObservationSection.tsx`

## System Context (Senior Thinking)

- **Feature domain:** Planner Feature (Mirror Dungeon)
- **Core files in this domain:** `routes/PlannerMDNewPage.tsx`, `components/deckBuilder/*`, `components/startBuff/*`, `components/startGift/*`, `components/egoGift/*`
- **Cross-cutting concerns touched:**
  - i18n: Existing keys (`pages.plannerMD.egoGiftObservation`, `pages.plannerMD.selectGifts`, `pages.plannerMD.selectedGifts`)
  - Constants: `MAX_OBSERVABLE_GIFTS` from `lib/constants.ts`
  - Shared components: `PlannerSection`, `StarlightCostDisplay`, `Dialog`
  - Data hooks: `useEGOGiftObservationData`, `useEGOGiftListData`

## Impact Analysis

- **Files being modified:**
  - `PlannerMDNewPage.tsx` (High impact - orchestrates all planner sections)
- **What depends on these files:**
  - Planner autosave reads `observationGiftIds` from state (no change to data shape)
  - Draft serialization already handles Set<string> format
- **Potential ripple effects:**
  - None - data format unchanged, only UI restructuring
- **High-impact files to watch:**
  - `EGOGiftObservationSelection.tsx` - must NOT modify, only reuse
  - `EGOGiftSelectionList.tsx` - must NOT modify, only reuse

## Risk Assessment

- **Edge cases not yet defined:**
  - All edge cases defined: 0/1/2/3 gifts, max enforcement, filter combinations
- **Performance concerns:**
  - IMPROVEMENT: EditPane suspends on demand (dialog open) instead of page load
- **Backward compatibility:**
  - Full compatibility: `observationGiftIds` data format unchanged
  - Draft loading will work without migration
- **Security considerations:**
  - None - client-side UI refactor only

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/planner/md/new`
2. Scroll to "EGO Gift Observation" section
3. Verify cost shows "0" on the RIGHT side
4. Verify placeholder text "Select EGO Gifts" is centered
5. Click anywhere in the section area
6. Verify EditPane dialog opens
7. Verify cost display shows "0" at top-right of dialog
8. Verify filter controls are visible (keyword, sort, search)
9. **Desktop only:** Verify 9:1 grid layout (selection left, selected right)
10. Click a gift card in the selection grid
11. Verify the gift appears in the "Selected" area (right column on desktop)
12. Verify cost updates (should show 70 for 1 gift)
13. Select 2 more gifts (total 3)
14. Verify cost updates (160 for 2, 270 for 3)
15. Try to select a 4th gift
16. Verify selection is blocked (max 3 enforced)
17. Click a gift in the "Selected" area
18. Verify the gift is deselected
19. Apply a keyword filter
20. Verify selection grid filters correctly
21. Close dialog (click outside or X button)
22. Verify Summary shows the selected gifts horizontally
23. Verify cost in Summary matches selection
24. Reopen dialog
25. Verify filters have reset (no keywords selected, no search query)
26. Verify selected gifts are still selected
27. Close dialog
28. **Mobile only (resize browser < lg breakpoint):**
29. Open dialog
30. Verify selection grid is full width
31. Verify "Selected" area appears BELOW the selection grid
32. Close dialog
33. Verify Summary displays correctly on mobile

### Automated Functional Verification

- [ ] Summary renders cost on right: `justify-end` class applied to cost container
- [ ] Summary clickable area: Entire content div has `cursor-pointer` and `role="button"`
- [ ] Empty state: Shows placeholder when `selectedGiftIds.size === 0`
- [ ] Horizontal layout: Selected gifts use `flex flex-wrap gap-2`
- [ ] EditPane dialog opens: `open` state controls dialog visibility
- [ ] Cost calculation: Matches `observationEgoGiftCostDataList` lookup
- [ ] Max selection: Cannot exceed `MAX_OBSERVABLE_GIFTS` (3)
- [ ] Filter state local: Filters reset when dialog closes and reopens
- [ ] Responsive layout: Grid columns change at `lg` breakpoint
- [ ] Keyboard accessibility: Enter/Space on Summary triggers `onClick`

### Edge Cases

- [ ] 0 gifts selected: Summary shows placeholder, EditPane shows empty "Selected" section
- [ ] 3 gifts selected: 4th selection attempt is silently ignored
- [ ] Invalid gift ID in selection: Gracefully skipped (existing behavior in EGOGiftObservationSelection)
- [ ] Language switch while dialog open: Gift names update via TanStack Query cache
- [ ] Cost lookup miss: Fallback to 0 (defensive coding)
- [ ] Filter returns no results: Empty grid displayed (not an error state)

### Integration Points

- [ ] Planner autosave: `observationGiftIds` continues to save/restore correctly
- [ ] Draft loading: Existing drafts load with selected observation gifts intact
- [ ] Suspense boundaries: Both Summary and EditPane suspend correctly within existing Suspense
- [ ] PlannerSection wrapper: Summary integrates with section header styling
