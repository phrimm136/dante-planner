# Task: Planner Viewer with Guide and Tracker Modes

## Description

Create a planner viewer system that allows users to view and interact with planners in two distinct modes:

### Guide Mode
A read-only viewing mode that displays the planner exactly as it appears in the editor, but without any editing capabilities:
- All sections are displayed in the same order as PlannerMDNewPage
- No interactive elements (cannot click to open edit panes)
- All inputs are disabled/read-only
- Notes are not clickable (no note editors open)

### Tracker Mode
An active tracking mode for users playing through Mirror Dungeon runs, with selective editing capabilities:
- **Single column layout** following the exact same section order as the editor
- **DeckBuilder Section**: Equipment display is read-only, but deployment order is editable (temporary changes, not saved)
- **StartBuff, StartGift, EGO Gift Observation, Comprehensive Gift Sections**: All read-only
- **Skill Replacement Section**: Displays two values side-by-side for each skill:
  - "Current: X" (left side, editable counter - user tracks during MD run)
  - "Planned: Y" (right side, from planner data - preserves original position for consistency)
  - NOT a comparison of EA values, just showing both skill counts simultaneously
- **Floor Theme Sections (×15)**: Modified layout for each floor:
  1. **Comprehensive EGO Gift Grid** (displayed FIRST, above theme packs):
     - When user hovers over a theme pack below, highlight the corresponding gifts in this grid
     - Highlighted gifts are sorted to the top of the grid (stable sort)
     - Non-highlighted gifts are dimmed (opacity-50)
  2. **Theme Pack Gallery** (displayed SECOND, below comprehensive gifts):
     - Horizontal scrollable layout instead of vertical stacked sections
     - On hover, show two action buttons:
       - "Mark as Done" button (toggles dimmed state on the theme pack)
       - "View Notes" button (opens Dialog showing the floor's notes)
- **All tracker state is session-only**: Refresh page = all tracker changes reset
- **No persistence**: Deployment order changes, skill counts, and "mark as done" states are temporary

### User Interactions
- **Navigation**: Use `/planner/md/$id` route, mode switching handled by component state (not URL params)
- **Permissions**: Anyone can view published planners in both guide and tracker modes
- **Theme Pack Hover**: Hovering over a theme pack card highlights its corresponding gifts in the comprehensive grid above and sorts them to the top
- **Mark as Done**: Clicking the "Mark as Done" button on a theme pack toggles its dimmed visual state
- **View Notes**: Clicking "View Notes" opens an integrated Dialog (not Sheet) displaying the floor's notes in read-only mode

### Data Model Clarifications
- **Skill EA Starting Values**: Always default to 3/2/1 for skills 1/2/3
- **Current Skill Counts**: User manually tracks and edits during MD run (simple counter, not from external API)
- **Tracker State**: All stored in React component state (useState), no IndexedDB or server sync
- **Deployment Order**: Editable in tracker, but changes are temporary preview only

### UI Consistency Requirements
- **Section Order**: Tracker mode MUST follow the exact same section order as PlannerMDNewPage
- **Skill Display Position**: "Planned" count stays on the right (same position as editor) to avoid user confusion
- **Single Column Layout**: Tracker uses single column, not multi-column
- **Mobile Experience**: Accept potentially awkward UX on mobile for horizontal theme pack scrolling

## Research

Before implementation, investigate:
- **PlannerMDNewPage.tsx structure**: Understand the complete section order and layout
  - DeckBuilder section pattern
  - StartBuff/StartGift summary components
  - EGO Gift sections architecture
  - Floor theme section rendering
  - Note editor integration (currently within sections, not separate)
- **Summary + Pane UI Pattern**: Study how existing sections handle read-only vs editable states
  - StartBuffEditPane disabled prop pattern
  - Dialog vs Sheet usage patterns
  - How to reuse components with different interaction modes
- **Horizontal Scrolling Implementation**: Review existing ScrollArea usage
  - FloorThemeGiftSection.tsx horizontal scroll patterns
  - ScrollArea orientation="horizontal" configuration
- **Gift Highlighting Logic**: Study comprehensive gift data structure
  - useEGOGiftListData hook for gift data
  - ThemePackListData structure and egoGifts field
  - How to efficiently map theme pack → gift IDs
- **Stable Sort Pattern**: Research how to sort with stable keys
  - Prevent layout thrashing on hover state changes
  - Use gift ID as stable sort key, not hover state
- **Deployment Order Editing**: Understand current deployment editor
  - How deployment order is stored in planner data
  - Existing UI components for order manipulation
  - Whether to reuse or create simplified version for tracker

## Scope

Files to READ for context and patterns:

### Core Planner Files
- `/frontend/src/routes/PlannerMDNewPage.tsx` - Main editor, all sections
- `/frontend/src/routes/PlannerMDDetailPage.tsx` - Current stub, will be modified
- `/frontend/src/components/common/PlannerSection.tsx` - Section wrapper pattern
- `/frontend/src/lib/constants.ts` - SECTION_STYLES, EMPTY_STATE constants

### Section Components (for reuse patterns)
- `/frontend/src/components/deckBuilder/*` - Deck and deployment UI
- `/frontend/src/components/startBuff/*` - Summary + EditPane pattern
- `/frontend/src/components/startGift/*` - Summary + EditPane pattern
- `/frontend/src/components/egoGift/EGOGiftObservation*` - Summary + EditPane pattern
- `/frontend/src/components/egoGift/ComprehensiveGiftSelectorPane.tsx` - Gift grid rendering
- `/frontend/src/components/floorTheme/*` - Floor theme sections

### Data Hooks
- `/frontend/src/hooks/usePlannerStorage.ts` - Loading planner data
- `/frontend/src/hooks/useEGOGiftListData.ts` - Comprehensive gift data
- `/frontend/src/hooks/useThemePackListData.ts` - Theme pack data structure

### UI Components
- `/frontend/src/components/ui/scroll-area.tsx` - Horizontal scroll implementation
- `/frontend/src/components/ui/dialog.tsx` - For note viewing
- `/frontend/src/components/noteEditor/NoteEditor.tsx` - Read-only mode

### Related Patterns
- `/frontend/src/components/egoGift/EGOGiftCard.tsx` - Card rendering with overlay
- `/frontend/src/lib/entitySort.ts` - Sorting utilities

## Target Code Area

Files to CREATE:

### Core Viewer Components
- `/frontend/src/components/plannerViewer/PlannerViewer.tsx` - Main container, mode switcher
- `/frontend/src/components/plannerViewer/GuideModeViewer.tsx` - Read-only editor layout
- `/frontend/src/components/plannerViewer/TrackerModeViewer.tsx` - Tracker-specific layout

### Tracker-Specific Components
- `/frontend/src/components/plannerViewer/DeckTrackerPanel.tsx` - Deck with editable deployment
- `/frontend/src/components/plannerViewer/FloorTrackerSection.tsx` - Single floor tracker (comprehensive gifts + theme packs)
- `/frontend/src/components/plannerViewer/HorizontalThemePackGallery.tsx` - Scrollable theme pack cards
- `/frontend/src/components/plannerViewer/ThemePackTrackerCard.tsx` - Individual pack with hover actions
- `/frontend/src/components/plannerViewer/ComprehensiveGiftGridTracker.tsx` - Gift grid with highlight/sort
- `/frontend/src/components/plannerViewer/SkillTrackerPanel.tsx` - Current vs Planned skill counts
- `/frontend/src/components/plannerViewer/FloorNoteDialog.tsx` - Integrated note viewer

### State Management
- `/frontend/src/hooks/useTrackerState.ts` - Session state for tracker mode
  - Deck changes (temporary)
  - Deployment order (temporary)
  - Theme pack "done" marks (temporary)
  - Current skill counts (user input)
  - Currently highlighted theme pack (hover state)

Files to MODIFY:
- `/frontend/src/routes/PlannerMDDetailPage.tsx` - Entry point, load planner and route to viewer

## System Context (Senior Thinking)

### Feature Domain
**Planner (MD)** - Mirror Dungeon planner viewing and tracking

### Core Files in This Domain
From architecture-map.md:
- **Core**: `routes/PlannerMDNewPage.tsx` (editor pattern to follow)
- **Supporting**:
  - `hooks/usePlannerStorage.ts` (data loading)
  - `hooks/usePlannerConfig.ts` (version config)
  - `components/deckBuilder/*` (Summary+Pane pattern)
  - `components/startBuff/*`, `components/startGift/*`, `components/egoGift/*` (section patterns)
  - `components/floorTheme/*` (floor theme sections)
  - `components/noteEditor/*` (note viewing)

### Cross-Cutting Concerns Touched
- **Section Layout**: Uses `components/common/PlannerSection.tsx` for all sections
- **Constants**: Uses `lib/constants.ts` for SECTION_STYLES, EMPTY_STATE
- **i18n**: Uses `lib/i18n.ts` for UI strings in common.json
- **EGO Gift Filtering**: Uses `lib/egoGiftFilter.ts` for gift operations
- **Card Grid Layout**: Uses `components/common/ResponsiveCardGrid.tsx` for gift grids
- **Error Handling**: Uses `components/common/ErrorBoundary.tsx` for error states

## Impact Analysis

### Files Being Modified
- **PlannerMDDetailPage.tsx** (Low impact - currently stub, page-isolated)

### New Component Dependencies
All new components under `components/plannerViewer/`:
- Low impact initially (isolated to new feature)
- May become medium impact if viewer becomes primary planner access method

### What Depends on Referenced Files
- **PlannerSection.tsx** (Medium impact - shared by all planner sections)
- **usePlannerStorage.ts** (High impact - core data loading hook)
- **EGOGiftCard.tsx** (Low impact - isolated card component)
- **ScrollArea** (Low impact - UI primitive)

### Potential Ripple Effects
- **If PlannerSection changes**: All planner sections (editor + viewer) affected
- **If usePlannerStorage changes**: All planner data loading affected
- **Gift highlighting performance**: Heavy re-renders on hover could impact UX

### High-Impact Files to Watch
From architecture-map "High-Impact Files (Modify with Care)":
- **lib/constants.ts** (High - if adding new SECTION_STYLES)
- **hooks/usePlannerStorage.ts** (High - core data loading)

## Risk Assessment

### Edge Cases Not Yet Defined
- **Empty theme pack selection**: User spec says "each floor must have theme pack" - need validation or error state if missing
- **Skill count limits**: What's the maximum current skill count? Does it validate against planner's max?
- **Hover performance**: 200+ gifts re-rendering on every theme pack hover could cause lag
- **Concurrent mode switching**: What happens if user switches guide ↔ tracker rapidly?

### Performance Concerns
- **Gift sorting on hover**: Sorting 200+ gifts on every hover event
  - Mitigation: Use stable sort key (gift IDs), memoize with gift list as dependency (not hover state)
  - Mitigation: Debounce hover events if lag occurs
- **Horizontal scroll with many theme packs**: 10+ theme pack cards in horizontal ScrollArea
  - Mitigation: Virtual scrolling if performance issues arise
- **Re-renders from tracker state**: Multiple useState hooks could cause cascade re-renders
  - Mitigation: React Compiler should optimize, but monitor in practice

### Backward Compatibility
- No backward compatibility concerns (new feature, no existing behavior changed)
- Planner data structure remains unchanged

### Security Considerations
- **Published planner access**: Anyone can view published planners - verify this is intentional
- **Session-only state**: No data persistence = no security concerns for tracker state
- **No server writes**: Tracker mode doesn't modify server data, only local state

### Mobile UX Concerns
- **Horizontal scroll on mobile**: Touch scrolling theme packs is awkward but accepted per user requirement
- **Small viewport**: Single column may require significant vertical scrolling on mobile
- **Touch targets**: Ensure hover actions are accessible via tap on mobile (convert hover to tap/long-press)

## Testing Guidelines

### Manual UI Testing - Guide Mode

1. Navigate to a published planner URL: `/planner/md/123`
2. Verify viewer loads and displays in guide mode by default
3. Verify all sections match the editor layout order:
   - DeckBuilder → StartBuff → StartGift → EGO Gift Observation → Comprehensive Gift → Skill Replacement → Floor Themes (×15)
4. Click on DeckBuilder sinner cards
5. Verify no deck edit panes open (read-only)
6. Attempt to click on StartBuff/StartGift summary cards
7. Verify no edit panes open (read-only)
8. Scroll through floor theme sections
9. Verify theme packs are displayed in vertical stacked layout (same as editor)
10. Attempt to click on notes within sections
11. Verify note editors do NOT open (read-only)
12. Verify all inputs and buttons are visually disabled

### Manual UI Testing - Tracker Mode

1. From guide mode, click "Switch to Tracker Mode" button (or equivalent UI)
2. Verify layout switches to tracker mode
3. Verify section order remains identical to guide mode
4. **DeckBuilder Section**:
   - Verify equipment displays are read-only
   - Click on deployment order controls
   - Verify deployment order can be adjusted
   - Refresh page and verify deployment changes are reset
5. **Skill Replacement Section**:
   - Verify each skill shows two numbers: "Current: X | Planned: Y"
   - Verify "Planned" is on the right side (same position as editor)
   - Click on "Current" counter controls
   - Verify current skill count can be incremented/decremented
   - Refresh page and verify current counts reset to default (3/2/1)
6. **Floor 1 Section**:
   - Verify comprehensive EGO gift grid appears FIRST (above theme packs)
   - Verify theme pack gallery appears SECOND (below comprehensive gifts)
   - Verify theme pack gallery has horizontal scroll
7. **Theme Pack Hover Interaction**:
   - Hover over first theme pack card
   - Verify corresponding gifts in comprehensive grid above are highlighted
   - Verify highlighted gifts are sorted to the top of the grid
   - Verify non-highlighted gifts are dimmed (opacity-50)
   - Verify two action buttons appear on hover:
     - "Mark as Done" button
     - "View Notes" button
   - Move mouse away from theme pack
   - Verify highlights and buttons disappear
8. **Mark as Done**:
   - Hover over a theme pack and click "Mark as Done"
   - Verify theme pack card becomes dimmed
   - Click "Mark as Done" again
   - Verify theme pack returns to normal state (toggle behavior)
   - Refresh page and verify done marks are reset
9. **View Notes**:
   - Hover over a theme pack with notes and click "View Notes"
   - Verify Dialog (not Sheet) opens in center of screen
   - Verify floor notes are displayed in read-only format
   - Verify note editor is disabled (no editing)
   - Close Dialog and verify it closes properly
10. **Repeat for Floor 2-15**: Spot-check that all 15 floors have the same tracker layout
11. **Mobile Testing** (if applicable):
    - Open tracker on mobile device
    - Verify horizontal theme pack scroll works with touch
    - Verify hover actions convert to tap interactions
    - Verify single column layout fits on mobile viewport

### Automated Functional Verification

- [ ] **Guide Mode - Read-Only Enforcement**: All interactive elements are disabled (no edit panes, no note editors, no selection changes)
- [ ] **Tracker Mode - Section Order**: All sections render in the same order as PlannerMDNewPage
- [ ] **Tracker Mode - Deployment Editing**: Deployment order can be temporarily adjusted in tracker mode
- [ ] **Tracker Mode - Session State**: All tracker changes (deployment, skills, done marks) reset on page refresh
- [ ] **Skill Display - Position Consistency**: "Planned" skill count appears on the right side (same as editor position)
- [ ] **Skill Display - Dual Values**: Each skill shows both Current and Planned counts simultaneously
- [ ] **Floor Layout - Comprehensive Gifts First**: Comprehensive gift grid renders above theme pack gallery in each floor
- [ ] **Floor Layout - Theme Packs Second**: Theme pack gallery with horizontal scroll renders below comprehensive gifts
- [ ] **Gift Highlighting - Hover Trigger**: Hovering theme pack highlights corresponding gifts in grid above
- [ ] **Gift Highlighting - Sort to Top**: Highlighted gifts are sorted to the top of the comprehensive grid
- [ ] **Gift Highlighting - Dimming**: Non-highlighted gifts have reduced opacity (opacity-50)
- [ ] **Theme Pack Actions - Hover Display**: "Mark as Done" and "View Notes" buttons appear on theme pack hover
- [ ] **Mark as Done - Toggle Behavior**: Clicking "Mark as Done" toggles the dimmed state on the theme pack
- [ ] **View Notes - Dialog Display**: Clicking "View Notes" opens an integrated Dialog (not Sheet)
- [ ] **Note Display - Read-Only**: Notes are displayed in disabled/read-only mode in the Dialog
- [ ] **Mode Switching**: User can switch between guide and tracker modes without data loss

### Edge Cases

- [ ] **Empty Planner State**: Gracefully handles planner with minimal data (no gifts, no theme packs)
- [ ] **Missing Theme Pack Data**: Each floor MUST have theme pack per spec - show error state if missing
- [ ] **Invalid Planner ID**: Shows 404 error state if planner doesn't exist
- [ ] **Unpublished Planner Access**: Verify published status check (if applicable)
- [ ] **Rapid Hover Events**: Gift highlighting doesn't cause performance lag when quickly hovering multiple theme packs
- [ ] **Maximum Skill Counts**: Current skill counter respects reasonable limits (e.g., 0-5)
- [ ] **Concurrent Mode Switching**: Switching guide ↔ tracker rapidly doesn't cause state corruption
- [ ] **Long Theme Pack Lists**: Horizontal scroll handles 10+ theme packs without layout breaking
- [ ] **Empty Notes**: "View Notes" button handles floors with no notes (empty state in Dialog)

### Integration Points

- [ ] **Planner Storage Integration**: Viewer correctly loads planner data via usePlannerStorage hook
- [ ] **EGO Gift Data Integration**: Comprehensive gift grid correctly fetches and displays gift data via useEGOGiftListData
- [ ] **Theme Pack Data Integration**: Theme pack gallery correctly fetches theme pack metadata via useThemePackListData
- [ ] **i18n Integration**: All UI strings use i18n keys from common.json (no hardcoded text)
- [ ] **Error Boundary**: Viewer is wrapped in ErrorBoundary and handles errors gracefully
- [ ] **Suspense Boundaries**: Data loading triggers appropriate Suspense fallbacks
