# Local Save Execution Plan

## Planning Gaps

None identified. Research has mapped all specs to code patterns.

## Execution Overview

Build from foundation to UI in 4 phases:
1. **Data Layer**: Types and schemas define structure and validation
2. **Logic Layer**: Storage hook for IndexedDB CRUD, autosave hook for debounced persistence
3. **Interface Layer**: Integrate hooks into PlannerMDNewPage with Save button and recovery dialog
4. **Edge Cases**: Error handling and state restoration

Dependencies flow strictly upward: types → schemas → hooks → page integration.

## Execution Order

### Phase 1: Data Layer

1. **`lib/constants.ts`**: Add storage constants
   - Depends on: none
   - Enables: F4, F5
   - Add: AUTO_SAVE_DEBOUNCE_MS, MAX_GUEST_DRAFTS, PLANNER_SCHEMA_VERSION

2. **`types/PlannerTypes.ts`**: Create planner types
   - Depends on: step 1
   - Enables: F1, F2, F3
   - Define: PlannerMetadata, PlannerContent, SaveablePlanner, SerializedFloorSelection

3. **`schemas/PlannerSchemas.ts`**: Create Zod validation schemas
   - Depends on: step 2
   - Enables: F6, F7
   - Include: Set serialization helpers

### Phase 2: Logic Layer

4. **`hooks/usePlannerStorage.ts`**: Create storage hook
   - Depends on: steps 1-3
   - Enables: F1, F3, F5
   - Implement: savePlanner, loadPlanner, listPlanners, deletePlanner, getOrCreateDeviceId

5. **`hooks/usePlannerAutosave.ts`**: Create autosave hook
   - Depends on: step 4
   - Enables: F4
   - Implement: Debounced save with useRef, cleanup on unmount

### Phase 3: Interface Layer

6. **`routes/PlannerMDNewPage.tsx`**: Add draft recovery dialog
   - Depends on: step 4
   - Enables: F3
   - Add: useState for dialog, useEffect for mount check, Dialog component

7. **`routes/PlannerMDNewPage.tsx`**: Add Save button with toast
   - Depends on: step 4
   - Enables: F1, F8
   - Add: Save button, call savePlanner, show toast.success

8. **`routes/PlannerMDNewPage.tsx`**: Integrate autosave hook
   - Depends on: steps 5-7
   - Enables: F4, F2
   - Pass all state to usePlannerAutosave, serialize Sets to arrays

### Phase 4: Edge Cases

9. **`hooks/usePlannerStorage.ts`**: Add error handling
   - Depends on: step 4
   - Enables: E1, E2
   - Add: safeParse with fallback, quota error handling, error toast

10. **`routes/PlannerMDNewPage.tsx`**: Add state restoration
    - Depends on: steps 6, 8, 9
    - Enables: F3 complete, I1
    - Implement: Load draft, restore state, convert arrays to Sets

## Verification Checkpoints

- After step 3: Import schemas in test file, validate sample data
- After step 4: Save test data to IndexedDB, check DevTools
- After step 5: Change state, wait 3s, check IndexedDB for draft
- After step 6: Create draft, refresh, verify dialog appears
- After step 7: Click Save, verify success toast
- After step 10: Full manual test per Testing Guidelines

## Rollback Strategy

- Steps 1-3: Safe to remove new files, no side effects
- Steps 4-5: Delete hook files, no page integration yet
- Steps 6-10: Revert PlannerMDNewPage.tsx to git HEAD
- Safe stopping points: After step 3, After step 5
- Emergency: `git checkout frontend/src/routes/PlannerMDNewPage.tsx`
