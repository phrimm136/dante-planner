# Execution Plan: Unified Planner Save Hook

## Planning Gaps
**None identified.** Research is comprehensive.

---

## Execution Overview

Refactor planner saving into unified `usePlannerSave` hook that:
1. Fixes critical `syncVersion: 1` hardcoding bug
2. Shows error toasts for save failures
3. Displays conflict resolution dialog on 409 errors
4. Unifies auto-save (2s debounce) and manual save logic

**Strategy:** Data Layer → Logic Layer → Interface Layer → Cleanup → Tests

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `types/PlannerTypes.ts` | Low | None | New hook, page |
| `lib/api.ts` | Medium | None | All API consumers |
| `hooks/usePlannerSave.ts` (NEW) | N/A | api.ts, types, adapter | Page |
| `components/planner/ConflictResolutionDialog.tsx` (NEW) | N/A | Types, Dialog UI | Page |
| `routes/PlannerMDNewPage.tsx` | Low | New hook, dialog | Route isolated |
| `hooks/usePlannerAutosave.ts` (DELETE) | N/A | - | Only page |

### Ripple Effect Map
- `api.ts` change: Extends error handling, doesn't break existing
- Delete `usePlannerAutosave.ts`: Page must use new hook first
- `PlannerTypes.ts` additions: Backward compatible

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `lib/api.ts` | Medium | Add new class, don't modify existing throws |
| `PlannerMDNewPage.tsx` | Low | Minimal changes: swap hook, add dialog |

---

## Execution Order

### Phase 1: Data Layer

1. **`types/PlannerTypes.ts`**: Add conflict types
   - Depends on: None
   - Enables: F3, Step 3
   - Add: `ConflictState`, `ConflictResolutionChoice`

2. **`lib/api.ts`**: Add ConflictError class
   - Depends on: None
   - Enables: F3, Step 3
   - Add: `ConflictError extends Error` with `serverVersion`
   - Modify: Throw `ConflictError` on 409

### Phase 2: Logic Layer

3. **`hooks/usePlannerSave.ts`**: Create unified hook
   - Depends on: Steps 1-2
   - Enables: F1, F2, F3
   - Pattern: Copy from `usePlannerAutosave.ts`
   - API: `save()`, `onConflict`, `resolveConflict()`, `isAutoSaving`, `errorCode`

### Phase 3: Interface Layer

4. **`components/planner/ConflictResolutionDialog.tsx`**: Create dialog
   - Depends on: Step 1
   - Enables: F3
   - Pattern: Copy from `LinkDialog.tsx`
   - Props: `open`, `onChoice`, `lastSavedTime`

5. **`routes/PlannerMDNewPage.tsx`**: Integrate hook and dialog
   - Depends on: Steps 3-4
   - Enables: All features in context
   - Changes: Swap hook, add dialog, remove manual save handler

### Phase 4: Cleanup

6. **`hooks/usePlannerAutosave.ts`**: Delete file
   - Depends on: Step 5 verified

### Phase 5: Tests

7. **`hooks/usePlannerSave.test.ts`**: Unit tests
   - Depends on: Step 3

8. **`components/planner/ConflictResolutionDialog.test.tsx`**: Component tests
   - Depends on: Step 4

### Phase 6: i18n

9. **`static/i18n/EN/common.json`**: Add conflict translations
   - Depends on: None
   - Keys: `pages.plannerMD.conflict.*`

---

## Verification Checkpoints

| After Step | Verify |
|------------|--------|
| Step 2 | `ConflictError` throws with `serverVersion` |
| Step 3 | Hook compiles, auto-save triggers, manual save works |
| Step 5 | Full integration working |
| Step 6 | App builds, no import errors |
| Step 8 | All tests pass |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Browser close during conflict dialog | 4-5 | Document as limitation |
| Server unreachable during resolution | 3 | Show network error, keep dialog open |
| Guest mode conflicts | 3 | Skip conflict handling for guests |
| Breaking existing API consumers | 2 | Extend, don't modify existing behavior |

---

## Dependency Verification

| After Modifying | Verify |
|-----------------|--------|
| `api.ts` (step 2) | Existing API calls work |
| `PlannerTypes.ts` (step 1) | TypeScript compiles |
| Delete `usePlannerAutosave.ts` (step 6) | No import errors |

---

## Rollback Strategy

- **Safe stops:** After Step 2, After Step 5
- **Step 3 fails:** Revert, keep old hook
- **Step 5 fails:** Revert page changes
- **Step 6 fails:** Git restore deleted file
