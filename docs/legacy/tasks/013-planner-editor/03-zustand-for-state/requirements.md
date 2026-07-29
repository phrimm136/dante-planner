# Task: Zustand State Management for Planner Editor

## Description
Migrate the planner MD editor from 24 useState hooks in a god component to Zustand store with granular subscriptions to eliminate cascading rerenders.

Current problem:
- `PlannerMDEditorContent.tsx` (1,076 lines) has 24 useState hooks
- Every state mutation triggers full parent render + all children re-evaluate
- Hot state (`equipment`, `comprehensiveGiftIds`, `floorSelections`) changes frequently
- `usePlannerSave` receives entire PlannerState object, causing debounce resets on every mutation
- Performance cost: ~70ms per mutation (50ms React render + 20ms serialization)

Target architecture:
- Single Zustand store with slices for granular subscriptions
- Instance-scoped store (created per component mount, not global)
- Only components subscribed to changed slices rerender
- Expected improvement: 85-90% reduction in render time (~5-10ms per mutation)

Key decisions made:
1. Each component instance gets its own store (no cross-contamination between tabs)
2. Dialog states (`isDeckPaneOpen`, `isStartBuffPaneOpen`, etc.) remain as local useState
3. `usePlannerSave` continues receiving composed state object from parent (decoupled)
4. Zustand DevTools middleware enabled for debugging
5. No backward compatibility testing needed - trust existing `deserializeSets()` path

## Research
- Existing Zustand patterns: `frontend/src/hooks/useSseStore.ts`, `frontend/src/hooks/useFirstLoginStore.ts`
- State slice boundaries: Hot vs Warm vs Cold state categorization
- SSE sync batching: How to batch 15+ mutations in single `set()` call
- Store initialization timing: Lazy-init on mount vs module load

## Scope
Read for context:
- `frontend/src/routes/PlannerMDEditorContent.tsx` - Current god component with 24 useState hooks
- `frontend/src/routes/PlannerMDEditPage.tsx` - Edit mode wrapper
- `frontend/src/routes/PlannerMDNewPage.tsx` - New mode wrapper
- `frontend/src/hooks/usePlannerSave.ts` - Save orchestration (consumes entire state)
- `frontend/src/hooks/usePlannerStorage.ts` - IndexedDB persistence
- `frontend/src/hooks/usePlannerSync.ts` - SSE sync event handling
- `frontend/src/hooks/useSseStore.ts` - Existing Zustand pattern
- `frontend/src/hooks/useFirstLoginStore.ts` - Existing Zustand pattern
- `frontend/src/types/PlannerTypes.ts` - State type definitions
- `frontend/src/schemas/PlannerSchemas.ts` - Validation schemas

## Target Code Area
New files:
- `frontend/src/stores/usePlannerEditorStore.ts` - New Zustand store

Modified files:
- `frontend/src/routes/PlannerMDEditorContent.tsx` - Replace useState with store subscriptions
- Child components that receive state as props (will use store hooks instead):
  - `frontend/src/components/deckBuilder/DeckBuilderSummary.tsx`
  - `frontend/src/components/deckBuilder/DeckBuilderPane.tsx`
  - `frontend/src/components/startBuff/StartBuffSection.tsx`
  - `frontend/src/components/startBuff/StartBuffEditPane.tsx`
  - `frontend/src/components/startGift/StartGiftSummary.tsx`
  - `frontend/src/components/startGift/StartGiftEditPane.tsx`
  - `frontend/src/components/egoGift/EGOGiftObservationSummary.tsx`
  - `frontend/src/components/egoGift/EGOGiftObservationEditPane.tsx`
  - `frontend/src/components/egoGift/ComprehensiveGiftSummary.tsx`
  - `frontend/src/components/egoGift/ComprehensiveGiftSelectorPane.tsx`
  - `frontend/src/components/skillReplacement/SkillReplacementSection.tsx`
  - `frontend/src/components/floorTheme/FloorThemeGiftSection.tsx`
  - `frontend/src/components/noteEditor/NoteEditor.tsx`

## System Context (Senior Thinking)
- Feature domain: Planner (MD)
- Core files in this domain (from architecture-map):
  - `routes/PlannerMDEditorContent.tsx` (shared)
  - `routes/PlannerMDNewPage.tsx` (wrapper)
  - `routes/PlannerMDEditPage.tsx` (wrapper)
  - `hooks/usePlannerSave.ts`, `hooks/usePlannerStorage.ts`
- Cross-cutting concerns touched:
  - State management (new Zustand store)
  - Save/sync hooks (usePlannerSave integration)
  - SSE sync (batch updates during server sync)

## Impact Analysis
Files being modified with impact level:
- `PlannerMDEditorContent.tsx` - High (1,076 line god component, core planner logic)
- `usePlannerSave.ts` - Medium (receives state, but interface unchanged)
- Child components (~15 files) - Low (isolated, prop → hook conversion)

What depends on these files:
- `PlannerMDNewPage.tsx` and `PlannerMDEditPage.tsx` depend on `PlannerMDEditorContent`
- `usePlannerSave` depends on state shape (unchanged)
- Child components are leaf nodes (no dependents)

Potential ripple effects:
- SSE sync must batch updates or cause 15 rerender cascades
- Store initialization timing affects edit mode planner loading
- DevTools middleware adds ~2KB to bundle

High-impact files to watch:
- `lib/constants.ts` - If state defaults need constants
- `hooks/usePlannerSync.ts` - If sync logic needs store awareness

## Risk Assessment
Risks identified:
| Risk | Severity | Mitigation |
|------|----------|------------|
| SSE sync batch updates | High | Single `set((state) => ({...allUpdates}))` call |
| Store initialization timing | Medium | Lazy-init on mount, not module load |
| usePlannerSave coupling | Medium | Keep receiving state object from parent |
| Lost updates during rapid mutation | Low | Zustand's `set()` is atomic per dispatch |

Edge cases:
- Concurrent mutations (rapid equipment + title changes): React 19 batches, Zustand applies synchronously
- Multi-tab editing: Each component instance creates own store (no cross-contamination)
- SSE conflict resolution: Store mutation order matters - batch all server updates

Operational semantics:
- **Concurrency**: Zustand's `set()` is atomic, no race conditions
- **Boundaries**: Hot state (equipment, gifts, floors) vs Warm (buffs, keywords) vs Cold (title, category)
- **Ordering**: SSE sync must batch 15+ mutations in single set() call
- **Errors**: Invalid state caught by Zod validation in usePlannerSave
- **Defaults**: Store initialized with defaults, override on edit mode mount

## Testing Guidelines

### Manual UI Testing
1. Navigate to `/planner/md/new`
2. Change category dropdown from 5F to 4F
3. Verify floor theme sections update (15 → 12 floors)
4. Add an identity to a sinner slot
5. Verify only DeckBuilder section visually updates (not all sections flash)
6. Change identity tier using slider
7. Verify tier change is reflected immediately
8. Open Comprehensive Gift selector pane
9. Select a gift with recipe (e.g., gift 9088)
10. Verify cascade ingredients are auto-selected
11. Close pane and verify summary shows all selected gifts
12. Type in a note editor
13. Verify typing is responsive (no lag)
14. Click Save button
15. Verify save completes without error
16. Refresh page
17. Navigate back to the planner
18. Verify all state persisted correctly

### Performance Verification
1. Open browser DevTools Performance tab
2. Start recording
3. Rapidly change equipment tier 10 times
4. Stop recording
5. Verify render time per mutation is <15ms (target: 5-10ms)
6. Compare against baseline (current: ~70ms)

### Automated Functional Verification
- [ ] Store initialization: Default state matches `createDefaultEquipment()` output
- [ ] Equipment update: Only DeckBuilder components rerender on equipment change
- [ ] Floor selection update: Only affected FloorThemeGiftSection rerenders
- [ ] Note update: Only affected NoteEditor rerenders
- [ ] Save integration: `usePlannerSave` receives complete state object
- [ ] Edit mode: Planner data correctly initializes store on mount
- [ ] DevTools: Zustand DevTools shows state transitions

### Edge Cases
- [ ] Rapid mutations: 10 equipment changes in 1 second don't cause lost updates
- [ ] SSE sync: Server update batches all mutations in single store update
- [ ] Dialog close: Dialog state (local useState) doesn't affect store
- [ ] Component unmount: Store subscriptions cleaned up properly
- [ ] Multi-tab: Two planner tabs have independent state

### Integration Points
- [ ] usePlannerSave: Receives composed state, debounce works correctly
- [ ] usePlannerSync: SSE events trigger batched store updates
- [ ] IndexedDB: Saved planner loads correctly into store
- [ ] Zod validation: Invalid state caught before save
