# Task: DeckBuilder Popup Pane Refactor

## Description

Refactor the DeckBuilder section in PlannerMDNewPage to use a Summary + EditPane popup pattern (following the established StartBuff pattern). The main page will show a compact summary view while the full entity selection interface moves to a Dialog popup.

### Main Page (DeckBuilderSummary)
The summary view displays:
- **SinnerGrid**: Shows all 12 sinners with their equipped identity/EGO and deployment badges
- **StatusViewer**: Shows attribute/keyword counts derived from current equipment
- **Action buttons**: Import, Export, Edit Deck, Reset Order (positioned consistently)
- Clicking "Edit Deck" opens the popup pane

### Popup Pane (DeckBuilderPane)
The Dialog popup contains the full editing interface:
- **SinnerGrid**: Same component, interactive for equipment management
- **StatusViewer**: Real-time updates as user selects entities
- **Action buttons**: Import, Export, Reset Order - **MUST be positioned identically to main page** for visual consistency
- **Filter controls**: Sinner filter, keyword filter, search bar
- **Entity grids**: Identity and EGO selection lists with TierLevelSelector

### Button Positioning Requirement
Action buttons (Import, Export, Reset Order) must appear in the **same visual position** in both the main page summary and the popup pane. This ensures users don't have to hunt for buttons when switching contexts. Consider extracting a shared `DeckBuilderActionBar` component to guarantee consistency.

### State Management
- **Filter state persistence**: When user closes and reopens the popup, their filter selections (selected sinners, keywords, search query, entity mode) should persist
- **Lifted state**: Move filter state from DeckBuilder's internal state to PlannerMDNewPage
- **Equipment state**: Already lifted to parent, no changes needed

### Component Reuse
- SinnerGrid and StatusViewer appear in BOTH locations (main page and popup)
- Both render the same components with same props - no state sync needed
- Import/Export/Reset buttons appear in BOTH locations with identical positioning

## Research

- [ ] Read `components/startBuff/StartBuffSection.tsx` - Pattern for Summary component structure
- [ ] Read `components/startBuff/StartBuffEditPane.tsx` - Pattern for Dialog popup structure
- [ ] Read `components/deckBuilder/DeckBuilder.tsx` - Current implementation to split; identify button layout
- [ ] Read `routes/PlannerMDNewPage.tsx` - Integration point, understand current state management
- [ ] Read `components/deckBuilder/SinnerGrid.tsx` - Verify it's a pure component (no internal state to lift)
- [ ] Read `components/deckBuilder/StatusViewer.tsx` - Verify it's a pure component
- [ ] Check `types/DeckTypes.ts` - Existing types to extend
- [ ] Check `static/i18n/EN/common.json` - Existing deckBuilder i18n keys

## Scope

### Read for Context
- `frontend/src/components/startBuff/StartBuffSection.tsx` - Summary pattern reference
- `frontend/src/components/startBuff/StartBuffEditPane.tsx` - EditPane pattern reference
- `frontend/src/components/deckBuilder/DeckBuilder.tsx` - Source component to refactor
- `frontend/src/components/deckBuilder/SinnerGrid.tsx` - Reusable component
- `frontend/src/components/deckBuilder/StatusViewer.tsx` - Reusable component
- `frontend/src/components/deckBuilder/EntityToggle.tsx` - Pane-only component
- `frontend/src/components/deckBuilder/TierLevelSelector.tsx` - Pane-only component
- `frontend/src/routes/PlannerMDNewPage.tsx` - Integration point
- `frontend/src/types/DeckTypes.ts` - Type definitions
- `frontend/src/hooks/usePlannerAutosave.ts` - Autosave integration

## Target Code Area

### New Files
- `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx` - Summary component for main page
- `frontend/src/components/deckBuilder/DeckBuilderPane.tsx` - Dialog popup component
- `frontend/src/components/deckBuilder/DeckBuilderActionBar.tsx` - Shared action buttons component (Import/Export/Reset Order)

### Modified Files
- `frontend/src/types/DeckTypes.ts` - Add `DeckFilterState` interface
- `frontend/src/routes/PlannerMDNewPage.tsx` - Add lifted filter state, replace DeckBuilder with Summary+Pane
- `static/i18n/EN/common.json` - Add "editDeck" and "paneTitle" keys

### Files to Delete (after migration)
- `frontend/src/components/deckBuilder/DeckBuilder.tsx` - Replaced by Summary+Pane

## Testing Guidelines

### Manual UI Testing

1. Navigate to `/planner/md/new` (or existing planner)
2. Verify DeckBuilderSummary displays on main page:
   - SinnerGrid shows all 12 sinners with current equipment
   - StatusViewer shows attribute/keyword counts
   - Import, Export, Edit Deck, Reset Order buttons are visible
3. **Note the exact position of action buttons** (Import/Export/Reset Order)
4. Click "Edit Deck" button
5. Verify DeckBuilderPane Dialog opens:
   - SinnerGrid is visible at top
   - StatusViewer shows current counts
   - **Action buttons (Import/Export/Reset Order) are in the SAME position as main page**
   - Filter controls (sinner filter, keyword filter, search) are visible
   - Identity/EGO entity grids are visible
6. Select a sinner filter (e.g., "Yi Sang")
7. Verify entity grids filter to show only Yi Sang's identities/EGOs
8. Type a search query (e.g., "Pequod")
9. Verify entity grids filter by search
10. Close the Dialog (click outside or press Escape)
11. Verify main page summary updates with any equipment changes
12. Click "Edit Deck" again
13. **Verify filter state persisted**: Yi Sang filter and "Pequod" search should still be active
14. Clear filters, select some entities
15. Close Dialog
16. Verify equipment changes reflected in SinnerGrid and StatusViewer on main page
17. Test Import button from main page summary
18. Test Export button from main page summary
19. Test Reset Order button from both main page and popup
20. **Compare button positions**: Take screenshots of main page and popup to verify identical button placement

### Automated Functional Verification

- [ ] Filter persistence: selectedSinners, selectedKeywords, searchQuery persist across popup open/close
- [ ] Equipment sync: Changes in popup immediately reflect in summary's SinnerGrid
- [ ] StatusViewer sync: Attribute/keyword counts update in both locations simultaneously
- [ ] Dialog open/close: Opens on "Edit Deck" click, closes on outside click or Escape
- [ ] Entity mode toggle: Identity/EGO mode switch works in popup
- [ ] Import function: Works from both summary and popup
- [ ] Export function: Works from both summary and popup
- [ ] Reset Order function: Works from both summary and popup
- [ ] Autosave integration: Equipment changes trigger debounced autosave
- [ ] Button position consistency: Action buttons render in identical positions in both views

### Edge Cases

- [ ] Empty equipment: Summary shows empty SinnerGrid gracefully
- [ ] No filters selected: Entity grids show all items
- [ ] All filters cleared: Resetting filters shows full list
- [ ] Mobile view: Dialog is usable on 375px width screens; button positions still consistent
- [ ] Keyboard navigation: Tab navigation works within Dialog, Escape closes
- [ ] Rapid open/close: No state corruption when quickly opening/closing popup
- [ ] Large screen: Dialog doesn't overflow on wide screens

### Integration Points

- [ ] Autosave: Equipment changes in popup trigger `usePlannerAutosave` debounce
- [ ] React Query: Identity/EGO spec data cached, no refetch on popup open
- [ ] i18n: "Edit Deck" button uses translation key
- [ ] Theme: Dialog follows dark/light theme
- [ ] PlannerSection: Summary wrapped in PlannerSection for consistent styling

## Implementation Phases

### Phase 1: Type Definitions & State Setup
- Add `DeckFilterState` interface to `DeckTypes.ts`
- Add `isDeckPaneOpen` and `deckFilterState` state to PlannerMDNewPage
- Keep existing DeckBuilder rendering (no breaking changes)

### Phase 2: Create DeckBuilderActionBar
- Extract Import/Export/Reset Order buttons into shared component
- Ensure consistent layout/styling for reuse in both Summary and Pane
- Include "Edit Deck" button option (only shown in Summary)

### Phase 3: Create DeckBuilderSummary
- Extract SinnerGrid, StatusViewer into new component
- Use DeckBuilderActionBar with "Edit Deck" button enabled
- Follow StartBuffSection pattern

### Phase 4: Create DeckBuilderPane
- Create Dialog-wrapped component
- Include DeckBuilderActionBar at **same position as Summary** (no "Edit Deck" button)
- Include full editing interface (filters, entity grids)
- Use lifted filterState props instead of internal state
- Follow StartBuffEditPane pattern

### Phase 5: Integration
- Replace `<DeckBuilder>` with `<DeckBuilderSummary>` + `<DeckBuilderPane>` in PlannerMDNewPage
- Wire up state and callbacks
- Test filter persistence

### Phase 6: Cleanup & i18n
- Delete old DeckBuilder.tsx
- Add i18n keys to common.json
- Final testing

## Type Definitions

```typescript
// Add to frontend/src/types/DeckTypes.ts

export interface DeckFilterState {
  selectedSinners: Set<string>
  selectedKeywords: Set<string>
  searchQuery: string
  entityMode: 'identity' | 'ego'
}
```

## i18n Keys

Add to `static/i18n/EN/common.json`:
```json
{
  "deckBuilder": {
    "editDeck": "Edit Deck",
    "paneTitle": "Deck Configuration"
  }
}
```

## Design Notes

### Button Position Consistency
The action buttons (Import, Export, Reset Order) must appear in visually identical positions in both:
1. Main page DeckBuilderSummary
2. Popup DeckBuilderPane

This is achieved by:
1. Creating a shared `DeckBuilderActionBar` component
2. Placing it at the same structural position in both Summary and Pane layouts
3. Using identical CSS classes/spacing

The "Edit Deck" button is the only button unique to the Summary view (opens the pane).
