# Task: Planner List Route Restructuring

## Description
Restructure the planner list feature from tab-based view switching to route-based separation, with cleaner URL parameters.

### Route Changes
- `/planner/md` → Personal planners (My Plans) - shows user's local + server planners
- `/planner/md/gesellschaft` → Community planners (Published) - shows all published planners

### Query Parameter Changes
- Remove `view` parameter (determined by route now)
- Remove `sort` parameter (no sorting supported; recommended planners have dedicated mode)
- Replace `filter` parameter with:
  - `category` for floor filter (5F, 10F, 15F)
  - `mode=best` for recommended/featured planners (gesellschaft only)
- Hide default values in URL:
  - Hide `category` when not filtering by floor
  - Hide `page` when on first page (page=0)
  - Hide `mode` when showing all published (not best)

### Navigation
- Replace tabs component with Link buttons
- Button text: "My Plans" and "Gesellschaft" (game aesthetic alignment)

### Data Sources
- `/planner/md`: Merge IndexedDB (local) + Server API planners
  - Guests see local planners only
  - Authenticated users see both local and server planners
- `/planner/md/gesellschaft`: Published API only
  - `mode=best` → `GET /api/planner/md/recommended`
  - Default → `GET /api/planner/md/published`

### Card Display
- Personal planners: Show status badge (draft/saved/published)
- Community planners: Show vote counts, bookmark option

## Research
- Existing `usePlannerStorageAdapter.listPlanners()` - already merges local + server
- `plannerApi.list()` in `lib/plannerApi.ts` - calls `GET /api/planner/md`
- Current tab component pattern in `PlannerListTabs.tsx`
- URL state management in `usePlannerListFilters.ts`
- TanStack Router search param validation patterns

## Scope
Read for context:
- `frontend/src/routes/PlannerListPage.tsx`
- `frontend/src/hooks/usePlannerListData.ts`
- `frontend/src/hooks/usePlannerListFilters.ts`
- `frontend/src/hooks/usePlannerStorageAdapter.ts`
- `frontend/src/hooks/usePlannerStorage.ts`
- `frontend/src/lib/router.tsx`
- `frontend/src/types/PlannerListTypes.ts`
- `frontend/src/components/plannerList/PlannerListTabs.tsx`
- `frontend/src/components/plannerList/PlannerListToolbar.tsx`

## Target Code Area

### Create
- `frontend/src/hooks/useMDUserPlannersData.ts` - personal planners data hook
- `frontend/src/hooks/useMDUserFilters.ts` - URL state for /planner/md
- `frontend/src/hooks/useMDGesellschaftFilters.ts` - URL state for /planner/md/gesellschaft
- `frontend/src/routes/PlannerMDGesellschaftPage.tsx` - community planners page
- `frontend/src/components/plannerList/MDPlannerNavButtons.tsx` - navigation component

### Modify
- `frontend/src/lib/router.tsx` - add gesellschaft route, update search schemas
- `frontend/src/hooks/usePlannerListData.ts` → rename to `useMDGesellschaftData.ts`
- `frontend/src/routes/PlannerListPage.tsx` → rename to `PlannerMDPage.tsx`
- `frontend/src/components/plannerList/PlannerListToolbar.tsx` → rename to `MDPlannerToolbar.tsx`
- `frontend/src/types/PlannerListTypes.ts` → rename to `MDPlannerListTypes.ts`

### Delete
- `frontend/src/components/plannerList/PlannerListTabs.tsx`
- `frontend/src/hooks/usePlannerListFilters.ts`

## System Context (Senior Thinking)
- Feature domain: Planner (MD)
- Core files: `routes/PlannerMDNewPage.tsx`, `hooks/usePlannerStorage.ts`, `hooks/usePlannerSync.ts`
- Cross-cutting concerns:
  - Authentication (useAuthQuery for determining data source)
  - IndexedDB storage (usePlannerStorage)
  - TanStack Router (search param validation)
  - i18n (button text translations)

## Impact Analysis
- Files being modified:
  - `router.tsx` (High impact - affects all navigation)
  - `PlannerListTypes.ts` (Medium impact - type changes cascade)
  - `PlannerListPage.tsx` (Low impact - page isolated)
- What depends on these files:
  - Router: All navigation, Link components
  - Types: Any component importing `PlannerListView`, `PlannerSortOption`
- Potential ripple effects:
  - Existing bookmarks with `?view=` params will need redirect handling
  - Components importing removed types will fail to compile
- High-impact files to watch: `router.tsx`

## Risk Assessment
- Edge cases:
  - Guest users with no local planners (empty state)
  - Authenticated users with only local planners (no server planners)
  - Invalid URL params (handled by Zod schema)
- Backward compatibility:
  - Old URLs with `?view=community` will 404 or show wrong data
  - Consider redirect from old URL pattern
- Performance:
  - Client-side filtering/pagination for local planners (may be slow with many planners)
- Security: None (no auth changes)

## Testing Guidelines

### Manual UI Testing
1. Navigate to `/planner/md` as guest
2. Verify local planners from IndexedDB are shown
3. Create a new planner and save it
4. Verify the new planner appears in the list with correct status badge
5. Click "Gesellschaft" navigation button
6. Verify URL changes to `/planner/md/gesellschaft`
7. Verify community planners are shown
8. Click "Best" toggle button
9. Verify URL shows `?mode=best`
10. Verify only recommended planners are shown
11. Select category filter "5F"
12. Verify URL shows `?category=5F&mode=best`
13. Navigate to page 2
14. Verify URL shows `?category=5F&mode=best&page=1`
15. Clear all filters
16. Verify URL returns to clean `/planner/md/gesellschaft`
17. Log in with authenticated account
18. Navigate to `/planner/md`
19. Verify both local and server planners are shown
20. Click "My Plans" navigation button
21. Verify button shows active state

### Automated Functional Verification
- [ ] Route separation: `/planner/md` shows personal, `/planner/md/gesellschaft` shows community
- [ ] Guest data source: Only IndexedDB planners shown for unauthenticated users
- [ ] Auth data source: Both IndexedDB + server planners shown for authenticated users
- [ ] Mode parameter: `?mode=best` triggers recommended API endpoint
- [ ] Category filter: `?category=5F` filters to 5F planners
- [ ] URL hiding: Default values (page=0, no category, no mode) are hidden from URL
- [ ] Navigation buttons: Correct button shows active state based on current route

### Edge Cases
- [ ] Empty local storage: Shows appropriate empty state message
- [ ] Empty server response: Shows empty state without error
- [ ] Invalid category param: Falls back to no filter (Zod validation)
- [ ] Invalid mode param: Falls back to all published (Zod validation)
- [ ] Concurrent local + server same ID: Server wins (existing merge logic)

### Integration Points
- [ ] IndexedDB: Local planners load correctly from storage
- [ ] Server API: Authenticated planners merge with local
- [ ] TanStack Router: Search params validated and typed correctly
- [ ] i18n: Button text displays in current language
