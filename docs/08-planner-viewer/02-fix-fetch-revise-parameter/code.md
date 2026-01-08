# Code Documentation: Planner List Route Restructuring

## What Was Done

- Restructured planner list from tab-based to route-based (`/planner/md` vs `/planner/md/gesellschaft`)
- Created new types, hooks, and pages for separated personal/community planner views
- Implemented URL-based search functionality across both routes (schema → hooks → pages)
- Added `PlannerGridSkeleton` for better loading states with card shapes
- Improved mode toggle UX with two-button design (All / Best) instead of single toggle
- Cleaned up deprecated exports (`usePlannerListData`, `plannerListQueryKeys`, `PlannerListToolbar`)
- Updated all mutation hooks to use new `gesellschaftQueryKeys` naming

## Files Changed

**Created:**
- `src/types/MDPlannerListTypes.ts`
- `src/hooks/useMDUserPlannersData.ts`
- `src/hooks/useMDUserFilters.ts`
- `src/hooks/useMDGesellschaftFilters.ts`
- `src/hooks/useMDGesellschaftData.ts`
- `src/components/plannerList/MDPlannerNavButtons.tsx`
- `src/components/plannerList/MDPlannerToolbar.tsx`
- `src/routes/PlannerMDPage.tsx`
- `src/routes/PlannerMDGesellschaftPage.tsx`

**Modified:**
- `src/lib/router.tsx` - Added search schemas with `q` param
- `src/components/common/ListPageSkeleton.tsx` - Added `PlannerGridSkeleton`
- `src/hooks/usePlannerVote.ts` - Updated to `gesellschaftQueryKeys`
- `src/hooks/usePlannerFork.ts` - Updated to `gesellschaftQueryKeys`
- `src/hooks/usePlannerPublish.ts` - Updated to `gesellschaftQueryKeys`
- `src/hooks/usePlannerBookmark.ts` - Updated to `gesellschaftQueryKeys`

**Deleted:**
- `src/hooks/usePlannerListData.ts`
- `src/components/plannerList/PlannerListTabs.tsx`
- `src/hooks/usePlannerListFilters.ts`

**Renamed:**
- `usePlannerListData.test.tsx` → `useMDGesellschaftData.test.tsx`

## Verification Results

- TypeScript check (planner files): **Pass**
- Tests (21 tests): **Pass**
- Manual browser testing: **Pass** (all routes verified)
- Code review verdict: **ACCEPTABLE**

## Issues & Resolutions

- Search bar was non-functional (TODO placeholders) → Implemented full search flow through 5 layers
- Mode toggle was confusing single button → Replaced with two explicit buttons
- Generic `LoadingState` for data loading → Created `PlannerGridSkeleton` with card shapes
- Deprecated wrappers lingering → Removed after updating all consumers to new names
- Test expected old query key name → Updated test assertion to match `'gesellschaft'` key
