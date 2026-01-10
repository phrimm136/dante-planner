# Execution Plan: Planner List Route Restructuring

## Planning Gaps

**None** — Research is comprehensive, spec is clear.

---

## Execution Overview

Restructure planner list from tab-based to route-based:
- `/planner/md` → Personal planners (IndexedDB + Server)
- `/planner/md/gesellschaft` → Community planners (Published API)

**Strategy:** Phase-based (types → hooks → components → pages → router → cleanup)

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| `router.tsx` | **High** | Root | All Link components |
| `PlannerListTypes.ts` | Medium | constants | 6 components/hooks |
| `usePlannerListData.ts` | Medium | schemas, api | PlannerListPage |
| `usePlannerListFilters.ts` | Medium | router | PlannerListPage, Toolbar |
| `PlannerListPage.tsx` | Low | all hooks | router only |
| `PlannerListToolbar.tsx` | Low | types | PlannerListPage |

### Ripple Effect Map

- `router.tsx` search schema change → useSearch consumers need verification
- `PlannerListTypes.ts` removes `PlannerListView` → 6 imports will fail
- `usePlannerListFilters.ts` deleted → Page and Toolbar imports fail

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| `router.tsx` | Breaking navigation | Add new route first, verify, then modify old |
| `usePlannerStorageAdapter.ts` | Planner edit depends on it | Wrap in new hook, do NOT modify |

---

## Execution Order

### Phase 1: Types (Data Layer)

1. **CREATE `types/MDPlannerListTypes.ts`**
   - Depends on: none
   - Enables: F1-F6
   - Action: Copy structure, remove `PlannerListView`, `PlannerSortOption`

### Phase 2: Hooks (Logic Layer)

2. **CREATE `hooks/useMDUserPlannersData.ts`**
   - Depends on: Step 1, `usePlannerStorageAdapter.ts`
   - Enables: F2, F4
   - Pattern: `useIdentityListData.ts`

3. **CREATE `hooks/useMDUserFilters.ts`**
   - Depends on: Step 1
   - Enables: F3, F5
   - Pattern: `usePlannerListFilters.ts`

4. **CREATE `hooks/useMDGesellschaftFilters.ts`**
   - Depends on: Step 1
   - Enables: F3, F5
   - Pattern: `usePlannerListFilters.ts`

5. **RENAME `usePlannerListData.ts` → `useMDGesellschaftData.ts`**
   - Depends on: Step 1, Step 4
   - Enables: F2
   - Changes: Remove sort, add mode param

### Phase 3: Components (Interface Layer)

6. **CREATE `components/plannerList/MDPlannerNavButtons.tsx`**
   - Depends on: Step 10 (router)
   - Enables: F1, F6
   - Pattern: Link with useMatchRoute

7. **RENAME `PlannerListToolbar.tsx` → `MDPlannerToolbar.tsx`**
   - Depends on: Step 1
   - Enables: F3
   - Changes: Remove sort dropdown

### Phase 4: Pages & Router (Integration)

8. **RENAME `PlannerListPage.tsx` → `PlannerMDPage.tsx`**
   - Depends on: Steps 2, 3, 6, 7
   - Enables: F4
   - Changes: Remove tabs, use nav buttons, use `useMDUserPlannersData`

9. **CREATE `routes/PlannerMDGesellschaftPage.tsx`**
   - Depends on: Steps 4, 5, 6, 7
   - Enables: F2, F3
   - Pattern: PlannerListPage structure

10. **UPDATE `lib/router.tsx`**
    - Depends on: Steps 8, 9
    - Enables: F1, F5, F6
    - Changes: Add gesellschaft route, update search schemas

### Phase 5: Cleanup

11. **DELETE `PlannerListTabs.tsx`**
    - Depends on: Steps 8, 10

12. **DELETE `usePlannerListFilters.ts`**
    - Depends on: Steps 3, 4, 8, 9

13. **DEPRECATE `PlannerListTypes.ts`**
    - Depends on: Step 1
    - Note: Check other consumers first

### Phase 6: Verification

14. **RUN `yarn build`**
    - Depends on: All previous
    - Verify: No compilation errors

15. **MANUAL testing**
    - Depends on: Step 14
    - Follow instructions.md testing guidelines

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 2 | F4 (personal data) | Console log in dev |
| 5 | F2 (community data) | Network tab API calls |
| 8 | F4 (personal page) | Navigate `/planner/md` |
| 9 | F2, F3 (community page) | Navigate `/planner/md/gesellschaft` |
| 10 | F1 (routing) | Click nav buttons |
| 10 | F5 (URL hiding) | Check URL bar |
| 10 | F6 (mode param) | Add `?mode=best` |
| 14 | All | Build passes |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Guest no local planners | 8, 15 | Empty state handles view context |
| Auth only local planners | 8, 15 | Adapter merge logic works |
| Invalid URL params | 10 | Zod `.optional().catch()` fallback |
| Old `?view=` URLs | 10 | Ignored by new schema (no redirect needed) |
| Type import failures | 13 | Search all imports before delete |

---

## Rollback Strategy

- **Safe points:** After Phase 2, After Phase 4
- **Git:** Commit after each phase
- **If Phase 4 fails:** Pages isolated, revert to original
- **If Phase 5 fails:** Old files unused but harmless
