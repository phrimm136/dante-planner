# Planner Viewer Implementation Plan

## Execution Overview

This task creates a **planner viewer system** with two modes: **Guide Mode** (read-only display) and **Tracker Mode** (active gameplay tracking). The implementation follows a **phased architecture**:

1. **Phase 1**: Data layer (types, state management, utilities)
2. **Phase 2**: Core viewer container with mode switching
3. **Phase 3**: Guide Mode (read-only clone of editor)
4. **Phase 4**: Tracker Mode (interactive sections with selective editability)
5. **Phase 5**: Integration (entry point, routing, error boundaries)
6. **Phase 6**: Tests (unit + integration)

**Strategy**: Build from inner layers outward. Start with state management and utilities, then build components bottom-up (leaf components → composite sections → full viewer → page integration). This prevents dependency cascades and enables incremental testing.

## Dependency Analysis (Senior Thinking)

### Files Being Modified

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `routes/PlannerMDDetailPage.tsx` | Low (currently stub) | usePlannerStorage, PlannerViewer | TanStack Router |

### Files Being Created

| File | Impact Level | Depends On | Used By |
|------|--------------|------------|---------|
| `hooks/useTrackerState.ts` | Medium (session state) | React useState, PlannerTypes | TrackerModeViewer, tracker components |
| `components/plannerViewer/PlannerViewer.tsx` | Medium (mode switcher) | GuideModeViewer, TrackerModeViewer, PlannerTypes | PlannerMDDetailPage |
| `components/plannerViewer/GuideModeViewer.tsx` | Medium (full layout) | All section components, PlannerSection | PlannerViewer |
| `components/plannerViewer/TrackerModeViewer.tsx` | High (complex layout) | Tracker components, useTrackerState, data hooks | PlannerViewer |
| `components/plannerViewer/SkillTrackerPanel.tsx` | Low (isolated section) | PlannerSection, SkillInfo types | TrackerModeViewer |
| `components/plannerViewer/DeckTrackerPanel.tsx` | Medium (deck display) | DeckBuilderSummary, useTrackerState | TrackerModeViewer |
| `components/plannerViewer/FloorTrackerSection.tsx` | Medium (floor layout) | ComprehensiveGiftGridTracker, HorizontalThemePackGallery | TrackerModeViewer |
| `components/plannerViewer/ComprehensiveGiftGridTracker.tsx` | Medium (gift grid) | ResponsiveCardGrid, EGOGiftCard, useEGOGiftListData | FloorTrackerSection |
| `components/plannerViewer/HorizontalThemePackGallery.tsx` | Low (scroll wrapper) | ScrollArea, ThemePackTrackerCard | FloorTrackerSection |
| `components/plannerViewer/ThemePackTrackerCard.tsx` | Medium (hover actions) | ThemePackViewer, Dialog, useThemePackListData | HorizontalThemePackGallery |
| `components/plannerViewer/FloorNoteDialog.tsx` | Low (note viewer) | Dialog, NoteEditor | ThemePackTrackerCard |

### Ripple Effect Map

- If usePlannerStorage changes → PlannerMDDetailPage needs verification (planner loading may break)
- If PlannerSection changes → ALL sections (guide + tracker) need visual verification
- If SaveablePlanner type changes → useTrackerState, PlannerViewer, both mode viewers need type updates
- If ResponsiveCardGrid changes → ComprehensiveGiftGridTracker grid layout may break
- If NoteEditor changes → FloorNoteDialog read-only mode may break
- If theme pack data structure changes → ThemePackTrackerCard hover highlighting may break

### High-Risk Modifications

**TrackerModeViewer.tsx** (High complexity):
- Risk: 15 floor sections + multiple data dependencies could cause rendering performance issues
- Mitigation: Memoize floor sections, lazy-load floor data, use React Compiler optimizations

**ComprehensiveGiftGridTracker.tsx** (Performance risk):
- Risk: Sorting 200+ gifts on every hover event could cause UI lag
- Mitigation: Memoize sort function with stable gift list dependency (NOT hover state), use gift ID as stable sort key

**useTrackerState.ts** (State management risk):
- Risk: Multiple useState hooks (deployment, skills, done marks, hover) could cause cascade re-renders
- Mitigation: React Compiler should optimize automatically, but monitor in practice

## Execution Order

### Phase 1: Data Layer (Types, Schemas, State Management)

**1. Create TrackerState type definition**
   - Depends on: None
   - Enables: F-State (tracker state management)
   - Action: Define TrackerState interface in useTrackerState.ts (deployment order, skill counts, done marks, hover state)

**2. Create useTrackerState hook**
   - Depends on: Step 1
   - Enables: F-State
   - Action: Implement session state management with useState hooks, reset on unmount, preserve between mode switches

### Phase 2: Core Viewer Container

**3. Create PlannerViewer container**
   - Depends on: None (will have pending imports)
   - Enables: F1 (mode switching)
   - Action: Create mode switcher UI, receive planner data prop, conditional rendering for guide/tracker modes

### Phase 3: Guide Mode (Read-Only Display)

**4. Create GuideModeViewer component**
   - Depends on: Step 3
   - Enables: F2 (read-only display)
   - Action: Clone PlannerMDNewPage structure, wrap all sections in PlannerSection, add disabled={true} to all interactive components, same section order as editor

### Phase 4: Tracker Mode Components (Bottom-Up)

**5. Create FloorNoteDialog component**
   - Depends on: None (leaf component)
   - Enables: F8 (view notes)
   - Action: Dialog wrapper for NoteEditor with disabled={true}, empty state handling

**6. Create SkillTrackerPanel component**
   - Depends on: None (leaf component)
   - Enables: F5 (skill tracking)
   - Action: Display Current (left, editable counter) | Planned (right, from planner), default EA values 3/2/1, input validation 0-5 range

**7. Create DeckTrackerPanel component**
   - Depends on: Step 2 (useTrackerState)
   - Enables: F4 (deployment editing)
   - Action: Reuse DeckBuilderSummary for equipment display, add editable deployment order controls, equipment read-only

**8. Create ThemePackTrackerCard component**
   - Depends on: Step 5 (FloorNoteDialog)
   - Enables: F6, F7, F8 (hover actions, mark done, view notes)
   - Action: Extend ThemePackViewer with hover state, show "Mark as Done" + "View Notes" buttons on hover, toggle dimmed state on click

**9. Create ComprehensiveGiftGridTracker component**
   - Depends on: None (uses ResponsiveCardGrid)
   - Enables: F6 (gift highlighting)
   - Action: Render gift grid, accept highlightedGiftIds prop, sort highlighted gifts to top (stable sort by gift ID), dim non-highlighted gifts (opacity-50), cascade dimming for done packs

**10. Create HorizontalThemePackGallery component**
   - Depends on: Step 8 (ThemePackTrackerCard)
   - Enables: F6, F7 (horizontal scroll, hover actions)
   - Action: ScrollArea with orientation="horizontal", render ThemePackTrackerCard array, handle hover state propagation

**11. Create FloorTrackerSection component**
   - Depends on: Step 9, Step 10 (ComprehensiveGiftGridTracker, HorizontalThemePackGallery)
   - Enables: F6 (two-section floor layout)
   - Action: Wrap in PlannerSection, render ComprehensiveGiftGridTracker FIRST, HorizontalThemePackGallery SECOND, connect hover highlighting between pack gallery and gift grid

**12. Create TrackerModeViewer component**
   - Depends on: Step 2 (useTrackerState), Step 6-11 (all tracker components)
   - Enables: F3, F4, F5, F6 (tracker layout, deployment editing, skill tracking, floor interaction)
   - Action: Single column layout, same section order as editor (DeckBuilder → StartBuff → StartGift → EGOGiftObservation → ComprehensiveGift → SkillReplacement → Floor1-15), integrate all tracker components, connect state management

### Phase 5: Integration (Entry Point, Routing)

**13. Modify PlannerMDDetailPage entry point**
   - Depends on: Step 3, Step 4, Step 12 (PlannerViewer, GuideModeViewer, TrackerModeViewer)
   - Enables: F1, F2 (route integration, mode switching)
   - Action: Load planner via usePlannerStorage, add Suspense boundary, add ErrorBoundary wrapper, render PlannerViewer with loaded data, handle 404/error states

### Phase 6: Tests (Unit + Integration)

**14. Write unit tests for useTrackerState**
   - Depends on: Step 2
   - Enables: Test coverage
   - Action: Test initial state, update handlers (deployment, skills, done marks, hover), state preservation between mode switches, reset on unmount

**15. Write unit tests for ComprehensiveGiftGridTracker**
   - Depends on: Step 9
   - Enables: Test coverage
   - Action: Test gift sorting logic (ID-based stability), highlight filtering, cascading dimming for done packs, performance with 200+ gifts

**16. Write unit tests for SkillTrackerPanel**
   - Depends on: Step 6
   - Enables: Test coverage
   - Action: Test Current/Planned counter updates, input validation (0-5 range), default EA values (3/2/1), reset behavior

**17. Write unit tests for ThemePackTrackerCard**
   - Depends on: Step 8
   - Enables: Test coverage
   - Action: Test hover state toggles buttons, click handlers fire correctly (mark done, view notes), dimming cascade

**18. Write integration test for PlannerViewer**
   - Depends on: Step 13
   - Enables: Test coverage
   - Action: Test planner loads, mode switch works (guide ↔ tracker), state preserved between switches, no state corruption

**19. Write integration test for TrackerModeViewer**
   - Depends on: Step 12
   - Enables: Test coverage
   - Action: Test section order matches editor, all sections render, floor hover highlights gifts from current floor only (not other floors)

## Verification Checkpoints

- After Step 4: Verify F2 - Guide mode displays all sections in read-only state (click interactions disabled)
- After Step 7: Verify F4 - Deployment order editable in tracker mode, changes temporary (reset on refresh)
- After Step 6: Verify F5 - Skill counts show Current | Planned, counters update, reset to 3/2/1 on refresh
- After Step 11: Verify F6 - Hover theme pack highlights gifts in grid above, gifts sort to top, non-highlighted gifts dim, comprehensive gifts render FIRST (above packs)
- After Step 8: Verify F7 - Mark as Done toggles dimmed state on theme pack AND highlighted gifts
- After Step 5: Verify F8 - View Notes opens Dialog (not Sheet), shows read-only notes, empty state handled
- After Step 12: Verify F3 - Tracker mode uses single column layout, section order matches editor
- After Step 13: Verify F1 - Mode switch toggles guide ↔ tracker, state preserved between switches
- After Step 19: Verify E1 - Empty planner state handled gracefully (no gifts, minimal data)
- After Step 19: Verify E2 - Rapid hover events don't cause performance lag

## Risk Mitigation (from instructions.md Risk Assessment)

| Risk | Step Affected | Mitigation |
|------|---------------|------------|
| Empty theme pack selection | Step 11, 13 | Validate each floor has theme pack per spec, show error state if missing |
| Skill count limits undefined | Step 6 | Validate 0-5 range in SkillTrackerPanel input handlers |
| Hover performance with 200+ gifts | Step 9 | Memoize sort function with gift list dependency (NOT hover state), use stable gift ID key |
| Concurrent mode switching | Step 3, 13 | Use React transitions for mode switch, preserve state in parent component |
| Horizontal scroll on mobile | Step 10 | Accept awkward UX per spec, ensure touch scrolling works, convert hover to tap |
| Published planner access | Step 13 | Verify published status check in usePlannerStorage or backend API |
| Session-only state reset | Step 2 | Document clearly in UI that tracker changes are temporary, reset on refresh |
| Long theme pack lists (10+) | Step 10 | Test horizontal scroll with 15+ packs, consider virtual scrolling if lag occurs |

## Pre-Implementation Validation Gate

**BEFORE Step 1 execution, verify research completed:**

| Validation Category | Check | Blocker if Missing |
|---------------------|-------|-------------------|
| **Reference Completeness** | Read all lines of PlannerMDNewPage.tsx (~720 lines)? | YES |
| **Contract Alignment** | SaveablePlanner.content matches tracker read needs? | YES |
| **Dependency Resolution** | All reference dependencies available (PlannerSection, ScrollArea, Dialog)? | YES |
| **Structure Documentation** | Section order documented (DeckBuilder → ... → Floor1-15)? | YES |
| **Difference Justification** | Tracker layout differences justified (single column, gifts first)? | NO (documented in research) |

**Execution Rule**: Research validation passed. All critical blockers resolved.

## Dependency Verification Steps

- After Step 4: Test GuideModeViewer renders without errors, all sections display
- After Step 12: Test TrackerModeViewer renders without errors, all tracker sections display
- After Step 13: Test PlannerMDDetailPage loads planner data, viewer renders both modes
- After Step 19: Test full integration - planner loads, mode switch works, tracker state functions, no console errors

## Rollback Strategy

**If Step 9 fails (gift sorting performance)**:
- Safe stop: Revert to static gift grid without sorting
- Resume: Implement virtual scrolling or debounce hover events

**If Step 12 fails (TrackerModeViewer complexity)**:
- Safe stop: Use simplified tracker layout with vertical theme pack scroll (same as editor)
- Resume: Refactor floor sections into smaller components

**If Step 13 fails (planner loading)**:
- Safe stop: Revert PlannerMDDetailPage to stub state
- Resume: Debug usePlannerStorage integration, verify planner data format

**General rollback points**:
- Phase 1 complete: State management works, can pause implementation
- Phase 3 complete: Guide mode works, can ship read-only viewer first
- Phase 4 complete: Tracker components work, can integrate incrementally

## Critical Reference Files

- `/frontend/src/routes/PlannerMDNewPage.tsx` - Core pattern reference (~720 lines): all section structures, state management, full editor layout to clone for guide mode and adapt for tracker mode
- `/frontend/src/types/PlannerTypes.ts` - Data contracts: SaveablePlanner, MDPlannerContent, SerializableFloorSelection structures
- `/frontend/src/components/floorTheme/FloorThemeGiftSection.tsx` - Floor section pattern: theme pack + gift coordination to adapt for tracker's horizontal layout
- `/frontend/src/components/common/PlannerSection.tsx` - Section wrapper: SECTION_STYLES usage that all viewer sections must follow
- `/frontend/src/hooks/usePlannerStorage.ts` - Data loading: planner data loading hook for entry point
