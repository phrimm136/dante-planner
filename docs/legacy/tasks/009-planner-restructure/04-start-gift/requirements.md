# Task: Reorganize Start Gift Section (Summary + EditPane Pattern)

## Description

Restructure the Start Gift section in the Planner Editor to match the established Summary + EditPane pattern used by DeckBuilder and StartBuff sections.

**Main Page (Summary View)**:
- Show only the selected keyword and gift cards when selection exists
- Display EA counter (e.g., "2/3") showing current selection count vs maximum
- Use existing `EGOGiftCard` component (96x96) to display selected gifts
- Keep current keyword layout (icon from StartGiftRow)
- When no selection exists, show dashed border placeholder (like ThemePackPlaceholder)
- Clicking the section opens the EditPane dialog

**Edit Pane (Dialog)**:
- Move the full 10-row keyword selection UI into a Dialog
- Display EA counter at the top
- All selection logic preserved:
  - Single keyword selection at a time
  - Gift selection gated by keyword selection
  - Selection count = 1 + ADDITIONAL_START_EGO_GIFT_SELECT effects
  - Resets gift selection when EA changes
- Done button closes the dialog

**State Management**:
- Add `isStartGiftPaneOpen` state to PlannerMDNewPage
- Existing state (`selectedGiftKeyword`, `selectedGiftIds`) remains lifted in parent
- Autosave continues to work unchanged

## Research

- [ ] `StartBuffSection.tsx` + `StartBuffEditPane.tsx`: Pattern for Summary + EditPane split
- [ ] `ThemePackPlaceholder`: Dashed border empty state styling
- [ ] `StartGiftSection.tsx`: Current implementation to split
- [ ] `StartGiftRow.tsx`: Component to reuse unchanged
- [ ] `EGOGiftCard.tsx`: Existing 96x96 card component for gift display
- [ ] `PlannerMDNewPage.tsx` lines 740-747: Current integration point

## Scope

Read for context:
- `frontend/src/components/startBuff/StartBuffSection.tsx`
- `frontend/src/components/startBuff/StartBuffEditPane.tsx`
- `frontend/src/components/floorTheme/ThemePackViewer.tsx` (ThemePackPlaceholder)
- `frontend/src/components/startGift/StartGiftSection.tsx`
- `frontend/src/components/startGift/StartGiftRow.tsx`
- `frontend/src/components/egoGift/EGOGiftCard.tsx`
- `frontend/src/routes/PlannerMDNewPage.tsx`

## Target Code Area

Create:
- `frontend/src/components/startGift/StartGiftSummary.tsx`
- `frontend/src/components/startGift/StartGiftEditPane.tsx`

Modify:
- `frontend/src/routes/PlannerMDNewPage.tsx` (import swap, add pane state, replace JSX)
- `static/i18n/EN/common.json` (add `selectStartGift` key)

Delete:
- `frontend/src/components/startGift/StartGiftSection.tsx` (after verification)

Keep unchanged:
- `frontend/src/components/startGift/StartGiftRow.tsx`

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/planner` page
2. Scroll to the Start Gift section
3. **Empty State Test**:
   - Verify dashed border placeholder is shown
   - Verify text "Click to select start gift" appears
   - Verify EA counter is visible (shows "0/1" with no buffs selected)
4. Click the placeholder
5. Verify EditPane dialog opens
6. **Dialog Content Test**:
   - Verify all 10 keyword rows are displayed
   - Verify EA counter shows at top (e.g., "0/1")
7. Click a keyword row (e.g., Burn)
8. Verify the row becomes selected (highlighted)
9. Click one of the 3 gift cards in that row
10. Verify the gift card shows selected state
11. Verify EA counter updates (e.g., "1/1")
12. Try clicking another gift (should be blocked at limit)
13. Click Done button
14. Verify dialog closes
15. **Summary View Test**:
    - Verify Summary shows keyword icon
    - Verify Summary shows selected gift card
    - Verify EA counter shows "1/1"
16. Click the Summary again
17. Verify dialog reopens with previous selection intact
18. **Keyword Change Test**:
    - Select a different keyword row
    - Verify previous gift selection is cleared
    - Select new gifts
    - Click Done
    - Verify Summary updates with new keyword and gifts
19. **EA Buff Interaction Test**:
    - Go to Start Buff section and select a buff with ADDITIONAL_START_EGO_GIFT_SELECT effect
    - Return to Start Gift section
    - Verify EA counter updates (e.g., "1/2" or "1/3")
    - Open EditPane and select additional gifts up to new limit
    - Verify selection respects new limit
20. Refresh the page
21. Verify selection persists (autosave working)

### Automated Functional Verification

- [ ] Summary shows keyword icon when selection exists
- [ ] Summary shows selected gift cards using EGOGiftCard component
- [ ] Summary shows EA counter in format "{selected}/{max}"
- [ ] Empty state shows dashed border placeholder
- [ ] Empty state shows correct i18n text
- [ ] EditPane dialog opens on Summary/Placeholder click
- [ ] EditPane contains all 10 keyword rows
- [ ] EditPane shows EA counter
- [ ] Single keyword selection enforced
- [ ] Gift selection gated by keyword selection
- [ ] Gift selection respects max EA limit
- [ ] Done button closes dialog
- [ ] Selection state persists after dialog close
- [ ] Autosave triggers on selection change

### Edge Cases

- [ ] No buffs selected: EA = 1 (base), only 1 gift selectable
- [ ] EA decreases (buff deselected): Excess gifts automatically trimmed
- [ ] Keyword deselected: Gift selection cleared
- [ ] New keyword selected: Previous gift selection cleared
- [ ] Dialog closed via X button: Selection preserved
- [ ] Dialog closed via backdrop click: Selection preserved
- [ ] Page refresh: Selection restored from IndexedDB

### Integration Points

- [ ] StartBuff section: ADDITIONAL_START_EGO_GIFT_SELECT effects update EA limit
- [ ] Autosave hook: Selection changes trigger save
- [ ] Planner serialization: `selectedGiftKeyword` and `selectedGiftIds` included
