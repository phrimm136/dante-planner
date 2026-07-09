# Plan: Zustand State Management for Planner Editor

## Planning Gaps
**None** - Research is comprehensive. Proceed with implementation.

---

## Execution Overview

Migrate `PlannerMDEditorContent.tsx` from 24 useState hooks to Zustand store:
1. Create instance-scoped store factory with Hot/Warm/Cold slices
2. Replace useState with store hooks in parent component
3. Migrate child components from props to store selectors
4. Ensure SSE batch updates and usePlannerSave integration work

**Approach**: Bottom-up - create store first, migrate parent, then children incrementally.

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `stores/usePlannerEditorStore.ts` | New | Zustand, constants, PlannerTypes | Parent + all children |
| `routes/PlannerMDEditorContent.tsx` | High | New store, usePlannerSave | NewPage, EditPage wrappers |
| `hooks/usePlannerSave.ts` | Medium | PlannerState interface | Parent (unchanged interface) |
| Child components (~15 files) | Low | Store | Parent |
| `hooks/usePlannerSync.ts` | Potential | Store for batch | Parent (if SSE batching) |

### Ripple Effect Map
- Store shape change → All subscribed components need selector updates
- Parent context change → Children need provider access
- usePlannerSave interface → Unchanged per spec

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| PlannerMDEditorContent.tsx | Core (1076 lines) | Incremental migration, test each section |
| usePlannerEditorStore.ts | New pattern (instance-scoped) | Follow Zustand createStore + Context docs |
| FloorThemeGiftSection.tsx | Array prop for usedThemePackIds | Ensure selector provides equivalent data |

---

## Execution Order

### Phase 1: Store Foundation (Steps 1-2)

**Step 1: Create store file structure**
- Create `frontend/src/stores/usePlannerEditorStore.ts`
- Define state interface (Hot/Warm/Cold slices)
- Define actions interface with setters
- Implement `createPlannerEditorStore` factory
- Add DevTools middleware
- Enables: F1 (Store creation)

**Step 2: Create store context and provider**
- Add React Context for store instance
- Create `PlannerEditorStoreProvider` component
- Export `usePlannerEditorStore` hook with context access
- Enables: F2 (Instance scoping)

### Phase 2: Core Component Migration (Steps 3-5)

**Step 3: Migrate PlannerMDEditorContent - State Declarations**
- Wrap render in `PlannerEditorStoreProvider`
- Replace 16 useState hooks with store initialization
- Keep 7 dialog useState local (per spec)
- Wire store to usePlannerSave via composed state
- Enables: F3 (Parent migration)

**Step 4: Migrate PlannerMDEditorContent - Event Handlers**
- Convert setX calls to store actions
- Convert initializeFromPlanner to batch update
- Ensure handleServerReload batches store updates
- Enables: F4 (Handler migration)

**Step 5: Verification Checkpoint - Parent**
- Test new planner: state initializes
- Test edit planner: state loads
- Test auto-save and manual save
- Enables: F5 (Parent verified)

### Phase 3: Child Component Migration (Steps 6-12)

**Step 6: Migrate DeckBuilder components**
- DeckBuilderSummary.tsx: props → selectors
- DeckBuilderPane.tsx: props → selectors + actions
- Enables: F6 (DeckBuilder)

**Step 7: Migrate StartBuff components**
- StartBuffSection.tsx, StartBuffEditPane.tsx
- Enables: F7 (StartBuff)

**Step 8: Migrate StartGift components**
- StartGiftSummary.tsx, StartGiftEditPane.tsx
- Enables: F8 (StartGift)

**Step 9: Migrate EGO Gift Observation components**
- EGOGiftObservationSummary.tsx, EGOGiftObservationEditPane.tsx
- Enables: F9 (Observation)

**Step 10: Migrate Comprehensive Gift components**
- ComprehensiveGiftSummary.tsx, ComprehensiveGiftSelectorPane.tsx
- Enables: F10 (Comprehensive)

**Step 11: Migrate Skill/Floor components**
- SkillReplacementSection.tsx
- FloorThemeGiftSection.tsx (note: floorSelections array)
- Enables: F11 (Skill/Floor)

**Step 12: Migrate NoteEditor components**
- NoteEditor.tsx: keyed by sectionKey
- Enables: F12 (Notes)

### Phase 4: Integration & SSE (Steps 13-14)

**Step 13: SSE Batch Update Integration**
- Review usePlannerSync.ts event handling
- Ensure single set() call batches all mutations
- Verify handleServerReload batches correctly
- Enables: F13 (SSE batch)

**Step 14: Final Integration Verification**
- Test complete editing flow
- Test SSE sync (if applicable)
- Test conflict resolution
- Enables: F14 (Integration verified)

### Phase 5: Tests (Step 15)

**Step 15: Test Store and Integration**
- Unit tests for store creation and selectors
- Verify existing tests pass
- Performance verification (<15ms target)
- Enables: F15 (Tests)

---

## Verification Checkpoints

| After Step | Verification | Pass Criteria |
|------------|--------------|---------------|
| 2 | Store creates | No errors, DevTools shows state |
| 5 | Parent works | Auto-save, manual save, edit mode |
| 6 | DeckBuilder | Equipment changes persist |
| 8 | Buff/Gift | Selections persist |
| 12 | All children | Full editing flow works |
| 14 | SSE batch | Conflict resolution reloads state |
| 15 | Performance | <15ms per equipment mutation |

---

## Risk Mitigation

| Risk | Steps | Mitigation |
|------|-------|------------|
| SSE batch updates | 4, 13 | Single set() call in initializeFromPlanner, handleServerReload |
| Store init timing | 3, 5 | Lazy init in provider; edit mode passes initial state |
| usePlannerSave coupling | 3, 5 | Keep composed state pattern (unchanged interface) |
| Lost updates | 6-12 | Zustand set() is atomic; React 19 batches |
| Unmount leaks | 6-12 | Auto-cleanup via Zustand; Context unmount cleans store |

---

## Pre-Implementation Validation

Before Step 1:
- [ ] Zustand installed in package.json
- [ ] Read Zustand createStore + Context docs for instance-scoping
- [ ] Existing stores compile and work
- [ ] DevTools middleware available (zustand/middleware)

---

## Rollback Strategy

- **Per-phase**: Each child migration is independent; revert individual files
- **Full**: Restore PlannerMDEditorContent.tsx, remove store file
- **Git**: Commit after each phase, tag `pre-zustand-migration` before Phase 2
