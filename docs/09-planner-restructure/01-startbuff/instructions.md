# Task: Start Buff View + Edit Pane Pattern

## Description

Restructure the Start Buff section to follow the "View + Edit Pane" pattern. This prevents accidental buff selection and simplifies the main page view.

### Main Page Behavior (Viewer Mode)

The main page displays all 10 buff cards as **read-only**:
- **No enhancement buttons**: Hide the EnhancementButton components
- **Selection state**: Cards show selected/unselected visually via highlight
- **Enhancement display**: Shows current enhancement level visually (name suffix +/++)
- **Cost display**: Shows current cost based on selection + enhancement
- **Click action**: Clicking anywhere on the section opens the edit pane

### Edit Pane Behavior (Dialog)

A dialog that reuses the existing `StartBuffSection` component with full functionality:
- **Reuse existing components**: Use `StartBuffSection` and `StartBuffCard` as-is
- **Enhancement buttons**: (+) and (++) buttons visible and functional
- **Card click**: Toggles selection (existing behavior)
- **Cost indicator**: Real-time cost calculation (existing behavior)
- **Close/Done button**: Closes dialog, returns to main page

### Component Reuse Strategy

**Do NOT duplicate component logic.** Instead:

1. Add `viewMode` prop to `StartBuffCard`:
   - `viewMode={true}`: Hide enhancement buttons, disable card click selection
   - `viewMode={false}` (default): Full functionality (current behavior)

2. Add `viewMode` and `onClick` props to `StartBuffSection`:
   - `viewMode={true}`: Pass viewMode to cards, handle section click to open pane
   - `viewMode={false}`: Current behavior for use in edit pane

3. Create `StartBuffEditPane` as a thin wrapper:
   - Wraps `StartBuffSection` with `viewMode={false}`
   - Adds Dialog header/footer
   - Handles close callback

### State Management

- No changes to existing `selectedBuffIds` state structure
- Add `activePaneId` state in `PlannerMDNewPage.tsx` to control pane visibility
- Pane receives state + setter callbacks from parent

### UI Pattern Reference

```tsx
// Main page - read-only viewer
<StartBuffSection
  viewMode={true}
  selectedBuffIds={selectedBuffIds}
  onClick={() => setActivePane('buff')}
/>

// Dialog - reuse same component with editing enabled
<Dialog open={activePane === 'buff'} onOpenChange={() => setActivePane(null)}>
  <StartBuffEditPane
    selectedBuffIds={selectedBuffIds}
    onSelectionChange={setSelectedBuffIds}
    onClose={() => setActivePane(null)}
  />
</Dialog>
```

## Research

Before implementation, investigate:
- [ ] `StartBuffCard.tsx` - Where enhancement buttons are rendered, how to conditionally hide
- [ ] `StartBuffSection.tsx` - How to add viewMode prop and pass to children
- [ ] `FloorThemeGiftSection.tsx` - Pattern for view + pane with component reuse
- [ ] `ThemePackSelectorPane.tsx` - Example dialog wrapper
- [ ] shadcn/ui `Dialog` component - Modal dialog structure

## Scope

Files to READ for context:
- `frontend/src/components/startBuff/StartBuffCard.tsx`
- `frontend/src/components/startBuff/StartBuffSection.tsx`
- `frontend/src/components/startBuff/EnhancementButton.tsx`
- `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
- `frontend/src/components/floorTheme/ThemePackSelectorPane.tsx`
- `frontend/src/routes/PlannerMDNewPage.tsx`
- `frontend/src/components/ui/dialog.tsx`

## Target Code Area

Files to CREATE:
- `frontend/src/components/startBuff/StartBuffEditPane.tsx` - Thin dialog wrapper

Files to MODIFY:
- `frontend/src/components/startBuff/StartBuffCard.tsx` - Add `viewMode` prop to hide buttons
- `frontend/src/components/startBuff/StartBuffSection.tsx` - Add `viewMode`, `onClick` props
- `frontend/src/routes/PlannerMDNewPage.tsx` - Add pane state, wire up dialog

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/planner/md/new` or existing planner page
2. Locate the Start Buff section
3. Verify 10 buff cards are displayed WITHOUT enhancement buttons
4. Verify selected buffs show highlight
5. Verify enhancement level is shown in buff name (+ or ++)
6. Click on any buff card
7. Verify the edit pane dialog opens
8. In the dialog, verify enhancement buttons (+) and (++) are visible
9. Click on an unselected buff card in dialog
10. Verify the card becomes selected (highlight appears)
11. Click the (+) enhancement button on the selected buff
12. Verify enhancement level changes and cost updates
13. Click the (++) enhancement button
14. Verify enhancement level changes to 2
15. Click on another buff card to select it
16. Verify multiple buffs can be selected
17. Click a selected buff card again
18. Verify it deselects (highlight removed)
19. Click the close/done button
20. Verify dialog closes
21. Verify main page shows updated selections with correct enhancement levels

### Automated Functional Verification

- [ ] View mode no buttons: Enhancement buttons are NOT visible on main page
- [ ] View mode read-only: Clicking card opens pane, does NOT toggle selection
- [ ] View mode display: Selected buffs show highlight and enhancement suffix in name
- [ ] Edit pane buttons: Enhancement buttons are visible in dialog
- [ ] Edit pane selection: Card clicks toggle selection in dialog
- [ ] Component reuse: Edit pane uses same StartBuffSection/StartBuffCard components
- [ ] Selection persistence: Selected buffs remain selected after dialog close
- [ ] Enhancement persistence: Enhancement levels persist after dialog close
- [ ] Cost calculation: Total cost updates correctly in edit pane
- [ ] Dialog close: Clicking outside or close button closes dialog

### Edge Cases

- [ ] Empty selection: Dialog works with no buffs selected initially
- [ ] All selected: All 10 buffs can be selected simultaneously
- [ ] Enhancement display: Buffs with enhancement show correct suffix (+/++) on main page
- [ ] Cost on main page: Main page shows correct total cost (read-only)
- [ ] Rapid clicks: Multiple rapid clicks don't cause state corruption
- [ ] Dialog re-open: Opening dialog multiple times preserves correct state

### Integration Points

- [ ] Cost indicator: Start buff cost affects other sections (start gift max selection)
- [ ] Auto-save: Selection changes trigger autosave via `usePlannerAutosave`
- [ ] State serialization: Selected buffs correctly saved/loaded from IndexedDB
