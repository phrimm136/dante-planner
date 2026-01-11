# Planner Viewer Research

## Clarifications Resolved

1. **Gift highlighting sort stability**: Sort by gift ID (stable key) ✓
2. **Current skill count UI**: Follow existing edit pane pattern (reference StartBuffEditPane, StartGiftEditPane increment/decrement controls) ✓
3. **Mark as done visual cascade**: Cascade dimming to BOTH theme pack card AND highlighted gifts (when pack is marked done, its gifts in comprehensive grid also dim) ✓
4. **View Notes button visibility**: Always show on hover (Dialog shows empty state if no notes exist) ✓
5. **Mode switch state preservation**: Session state preserved between guide ↔ tracker switches (no prompt, no reset). State only resets on page refresh/unmount ✓

## Spec-to-Code Mapping

### Entry Point & Routing
- `routes/PlannerMDDetailPage.tsx`: Load planner via usePlannerStorage, add mode switch UI, route to PlannerViewer component
- URL structure: `/planner/md/$id` with mode stored in component state (not URL params per spec)

### Core Viewer Components (NEW)
- `components/plannerViewer/PlannerViewer.tsx`: Container with mode switcher, receives planner data
- `components/plannerViewer/GuideModeViewer.tsx`: Read-only clone of editor, all interactions disabled
- `components/plannerViewer/TrackerModeViewer.tsx`: Single column layout, same section order as editor, selective editability

### Tracker-Specific Components (NEW)
- `components/plannerViewer/DeckTrackerPanel.tsx`: Equipment read-only, deployment order editable
- `components/plannerViewer/SkillTrackerPanel.tsx`: Current count (left, editable) | Planned count (right, from planner)
- `components/plannerViewer/FloorTrackerSection.tsx`: Two-section layout - comprehensive gifts FIRST, theme packs SECOND
- `components/plannerViewer/ComprehensiveGiftGridTracker.tsx`: Gift grid with highlight on hover, sort to top, dim others
- `components/plannerViewer/HorizontalThemePackGallery.tsx`: Horizontal ScrollArea for theme pack cards
- `components/plannerViewer/ThemePackTrackerCard.tsx`: Hover shows "Mark as Done" + "View Notes" buttons
- `components/plannerViewer/FloorNoteDialog.tsx`: Dialog wrapper for read-only NoteEditor

### State Management (NEW)
- `hooks/useTrackerState.ts`: Session state for deployment, current skill counts, done marks, hover state
  - State preserved between guide ↔ tracker mode switches
  - State only resets on page refresh or component unmount

### Data Loading (REUSE)
- `hooks/usePlannerStorage.ts`: Load planner from IndexedDB/API
- `hooks/useEGOGiftListData.ts`: Fetch comprehensive gift specs
- `hooks/useThemePackListData.ts`: Fetch theme pack metadata with egoGifts field

## Spec-to-Pattern Mapping

### Guide Mode Patterns
- **Read-only layout**: Copy PlannerMDNewPage component tree, add `disabled={true}` to all interactive components
- **Section wrapper**: Reuse `PlannerSection.tsx` + `SECTION_STYLES` from constants
- **Section order**: DeckBuilder → StartBuff → StartGift → EGOGiftObservation → ComprehensiveGift → SkillReplacement → Floor1-15
- **No edit panes**: Reference IdentityDetailPage pattern (display only, no click handlers)

### Tracker Mode Patterns
- **Single column layout**: Same wrapper as editor, but sections have tracker-specific internal layouts
- **Section order**: Identical to guide mode (preserve editor order)
- **Horizontal scroll**: Use ScrollArea from ui/scroll-area.tsx with orientation="horizontal"
- **Gift highlighting**: Reference egoGiftFilter.ts operations, sort by gift ID (stable key), memoize with gift list dependency
- **Dialog for notes**: Use Dialog component (not Sheet per spec), wrap NoteEditor with disabled={true}, always show View Notes button on hover
- **Hover interactions**: Standard onMouseEnter/onMouseLeave, show/hide action buttons
- **Mark as done**: useState toggle for dimmed state (opacity-50 CSS class), cascades to BOTH theme pack card AND its highlighted gifts in comprehensive grid
- **Skill counter UI**: Follow existing edit pane pattern (reference StartBuffEditPane/StartGiftEditPane increment/decrement controls)

### Data Flow Patterns
- **Planner loading**: PlannerMDDetailPage → usePlannerStorage → SaveablePlanner type
- **Gift data**: useEGOGiftListData → comprehensive gift specs → filter by floor's theme pack egoGifts
- **Theme pack data**: useThemePackListData → theme pack metadata → egoGifts array for highlighting
- **Tracker state**: useTrackerState → local useState → no persistence → reset on unmount/refresh

## Pattern Enforcement

### Must Read Before Writing

| NewFile | MUST Read First | Pattern to Copy |
|---------|-----------------|-----------------|
| PlannerViewer.tsx | PlannerMDNewPage.tsx | Full section order, PlannerSection wrapper usage |
| GuideModeViewer.tsx | PlannerMDNewPage.tsx + IdentityDetailPage.tsx | Read-only component tree, disabled props |
| TrackerModeViewer.tsx | PlannerMDNewPage.tsx | Single column wrapper, section order |
| SkillTrackerPanel.tsx | SkillReplacementSection.tsx | Skill display structure, EA values format |
| ComprehensiveGiftGridTracker.tsx | ComprehensiveGiftSelectorPane.tsx | Grid layout, card rendering, memoization |
| HorizontalThemePackGallery.tsx | scroll-area.tsx + FloorThemeGiftSection.tsx | ScrollArea orientation, card layout |
| ThemePackTrackerCard.tsx | ThemePackViewer.tsx | Card structure, hover state |
| FloorTrackerSection.tsx | FloorThemeGiftSection.tsx | Floor structure, two-section split |
| useTrackerState.ts | usePlannerSave.ts | useState patterns, no persistence |

### Pattern Copy Analysis: TrackerModeViewer from PlannerMDNewPage

**Reference Inventory**:
- Total lines: ~720
- Dependencies: 40+ imports (hooks, components, types, utils)
- State: 15 useState hooks for equipment, deployment, filters, section data
- Data contract: SaveablePlanner.content (MDPlannerContent structure)

**Cross-Reference Validation**:
- Data schema: SaveablePlanner.content matches tracker read needs ✓
- Section order: DeckBuilder → StartBuff → StartGift → EGOGift* → SkillReplacement → Floor1-15 ✓
- Equipment display: equipment Record<string, SinnerEquipment> ✓
- Skill EA state: skillEAState Record<string, SkillEAState> ✓
- Floor selections: floorSelections SerializableFloorSelection[] ✓

**Structural Differences Justified**:
- Editor: Equipment-centric, all sections editable
- Tracker: Single column, equipment read-only, deployment + skills editable, floor layout reordered (gifts first)
- Justification: Tracker UX optimized for gameplay tracking, not planning

## Existing Utilities

### UI Components (REUSE)
- `components/ui/scroll-area.tsx`: ScrollArea with orientation="horizontal"
- `components/ui/dialog.tsx`: Dialog for note viewing
- `components/common/PlannerSection.tsx`: Section wrapper
- `components/common/ResponsiveCardGrid.tsx`: Card grid layout

### Data Hooks (REUSE)
- `hooks/usePlannerStorage.ts`: Load planner from IndexedDB/API
- `hooks/usePlannerConfig.ts`: Version config
- `hooks/useEGOGiftListData.ts`: Comprehensive gift specs
- `hooks/useThemePackListData.ts`: Theme pack metadata

### Utilities (REUSE)
- `lib/constants.ts`: SECTION_STYLES, EMPTY_STATE, FLOOR_COUNTS, DUNGEON_IDX
- `lib/egoGiftEncoding.ts`: Gift operations (getCascadeIngredients)
- `lib/egoGiftFilter.ts`: Gift filtering logic
- `lib/plannerHelpers.ts`: validateFloorThemePacksForSave, canSelectFloorThemePack
- `lib/entitySort.ts`: sortByReleaseDate, sortEGOByDate (reference for gift sorting)
- `lib/utils.ts`: cn (classname merge)

### Section Components (REUSE with disabled props)
- `components/deckBuilder/DeckBuilderSummary.tsx`: Deck display
- `components/startBuff/StartBuffSection.tsx`: StartBuff summary
- `components/startGift/StartGiftSummary.tsx`: StartGift summary
- `components/egoGift/EGOGiftObservationSummary.tsx`: EGO Gift Observation summary
- `components/egoGift/ComprehensiveGiftSummary.tsx`: Comprehensive Gift summary
- `components/noteEditor/NoteEditor.tsx`: Notes in read-only mode

## Gap Analysis

### Must Create
- useTrackerState hook (session state management)
- PlannerViewer container (mode switcher)
- GuideModeViewer (read-only editor clone)
- TrackerModeViewer (single column with tracker sections)
- SkillTrackerPanel (Current | Planned display)
- ComprehensiveGiftGridTracker (highlight + sort variant)
- HorizontalThemePackGallery (horizontal scroll wrapper)
- ThemePackTrackerCard (hover actions variant)
- FloorTrackerSection (two-section layout: gifts first, packs second)
- FloorNoteDialog (Dialog wrapper for notes)

### Must Modify
- `routes/PlannerMDDetailPage.tsx`: Add mode switch, load planner, render PlannerViewer

### Can Reuse As-Is
- All data hooks (planner, gifts, theme packs)
- All UI primitives (ScrollArea, Dialog, PlannerSection)
- All utility functions (constants, helpers, filters)
- Section summary components (with disabled props)
- NoteEditor (with disabled prop)

## Testing Requirements

### Manual UI Tests
- **Guide mode**: Load planner → Verify read-only (no click interactions) → Verify section order matches editor
- **Tracker mode**: Switch mode → Verify layout switches → Adjust deployment → Refresh → Verify reset
- **Skill tracking**: Increment current skill count → Verify update → Refresh → Verify reset to 3/2/1
- **Floor interaction**: Hover theme pack → Verify gifts highlight + sort top → Unhover → Verify unhighlight
- **Mark as done**: Click button → Verify dim → Click again → Verify undim → Refresh → Verify reset
- **View notes**: Click button → Verify Dialog opens (not Sheet) → Verify read-only notes → Close → Verify closes
- **Horizontal scroll**: Verify 10+ theme packs scroll horizontally
- **Mobile**: Touch scroll theme packs, tap interactions work

### Unit Tests
- useTrackerState: Initial state, update handlers, reset on unmount
- ComprehensiveGiftGridTracker: Gift sorting logic (ID-based stability), highlight filtering
- SkillTrackerPanel: Current/Planned counter updates, input validation (0-5 range)
- ThemePackTrackerCard: Hover state toggles buttons, click handlers fire correctly
- HorizontalThemePackGallery: ScrollArea renders, no layout shift

### Integration Tests
- PlannerViewer: Planner loads, mode switch works, no state corruption
- TrackerModeViewer: Section order matches editor, all sections render
- FloorTrackerSection: Gifts above packs, hover highlights gifts from current floor only (not other floors)

## Technical Constraints

### Performance
- Gift sorting on hover: Memoize by gift list (not hover state) to prevent re-sort thrashing
- Stable sort key: Use gift ID, not array index or hover state
- Avoid full re-render: Isolate hover state to theme pack cards, not parent component

### Mobile
- Horizontal scroll: Touch devices may have awkward UX (accepted per spec)
- Hover actions: Convert to tap/long-press on mobile devices
- Single column: Significant vertical scrolling on mobile (accepted per spec)

### Pattern Compliance
- Must use PlannerSection wrapper for all sections
- Must follow editor's exact section order
- Must use Dialog (not Sheet) for note viewing
- Must maintain UI position consistency (Planned skill count on right)

### Data Flow
- Tracker state is separate from planner data (no mutation of SaveablePlanner)
- Session state preserved between mode switches (guide ↔ tracker)
- State resets only on page refresh or component unmount (not on mode switch)
- No IndexedDB persistence, no server sync

### Type Safety
- Use SaveablePlanner type for loaded planner data
- Create TrackerState type for temporary tracker changes
- Ensure type safety for deployment order mutations (array operations)
- Validate skill count ranges (0-5 or similar reasonable limits)
