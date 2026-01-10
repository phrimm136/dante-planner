# Research: Planner List Route Restructuring

## Spec Ambiguities

**NO BLOCKING AMBIGUITIES** — Spec is well-defined.

**Minor clarifications (non-blocking):**
- URL param hiding: TanStack Router default behavior (omit empty params) is correct
- "Gesellschaft" is confirmed as button text (game aesthetic)
- Empty states will use existing i18n patterns

---

## Spec-to-Code Mapping

| Requirement | Current Location | Action |
|-------------|------------------|--------|
| Route `/planner/md` (personal) | `router.tsx:72-77` | Update search schema, remove tabs |
| Route `/planner/md/gesellschaft` | N/A | CREATE new route |
| Data: Local + Server merge | `usePlannerStorageAdapter.ts:25` | Wrap in new hook |
| Data: Published API | `usePlannerListData.ts:158-181` | Rename to `useMDGesellschaftData.ts` |
| Data: Recommended API | `usePlannerListData.ts:88-114` | Keep, integrate via `mode=best` |
| Query params cleanup | `router.tsx:21-28` | Create two schemas |
| Navigation buttons | `PlannerListTabs.tsx` | DELETE, CREATE `MDPlannerNavButtons.tsx` |

---

## Pattern Enforcement (MANDATORY)

| New File | MUST Read First | Pattern to Copy |
|----------|-----------------|-----------------|
| `useMDUserPlannersData.ts` | `hooks/useIdentityListData.ts` | useSuspenseQuery + Zod + query keys |
| `useMDUserFilters.ts` | `hooks/usePlannerListFilters.ts` | useSearch + useNavigate URL state |
| `useMDGesellschaftFilters.ts` | `hooks/usePlannerListFilters.ts` | Same pattern, different schema |
| `PlannerMDGesellschaftPage.tsx` | `routes/IdentityPage.tsx` | Suspense → Layout → List |
| `MDPlannerNavButtons.tsx` | `routes/EGODetailPage.tsx` | Link with isActive detection |

---

## Existing Utilities

| Category | Location | Found |
|----------|----------|-------|
| Constants | `lib/constants.ts` | PLANNER_LIST.PAGE_SIZE, MD_CATEGORIES, CARD_GRID.WIDTH.PLANNER |
| Data hooks | `hooks/usePlannerListData.ts` | usePlannerListData (published/recommended) |
| Filter hooks | `hooks/usePlannerListFilters.ts` | usePlannerListFilters (URL state) |
| Storage | `hooks/usePlannerStorageAdapter.ts` | listPlanners() merges local + server |
| API | `lib/plannerApi.ts` | published, recommended endpoints |
| Schemas | `schemas/PlannerListSchemas.ts` | PaginatedPlannersSchema |

---

## Gap Analysis

**Missing:**
- Separate route for `/planner/md/gesellschaft`
- `useMDUserPlannersData.ts` hook wrapping storage adapter
- Route-specific filter hooks
- `MDPlannerNavButtons.tsx` component
- Two distinct search param schemas

**Needs Modification:**
- `router.tsx` — Add route, update schemas
- `PlannerListPage.tsx` → `PlannerMDPage.tsx`
- `usePlannerListData.ts` → `useMDGesellschaftData.ts`
- `PlannerListToolbar.tsx` → `MDPlannerToolbar.tsx` (remove sort)
- `PlannerListTypes.ts` → `MDPlannerListTypes.ts`

**Delete:**
- `PlannerListTabs.tsx`
- `usePlannerListFilters.ts`

**Reuse:**
- `PlannerCard.tsx` (modify for status/votes context)
- `PlannerCardContextMenu.tsx`, `PlannerEmptyState.tsx`, `PlannerListPagination.tsx`

---

## Testing Requirements

### Manual UI Tests
1. Route navigation: Click buttons → URL changes correctly
2. Guest data: Local planners shown with status badges
3. Auth data: Both IndexedDB + server planners merged
4. Default hiding: `page=0` and empty category omitted from URL
5. Mode parameter: `?mode=best` shows recommended only
6. Empty states: Appropriate messages per route

### Automated Tests
- Routes resolve without errors
- Search schemas validate (invalid → defaults)
- Guest: Only IndexedDB queried
- Auth: Both sources queried
- Mode branching: `best` → recommended API, default → published API
- URL params: Defaults hidden correctly

### Edge Cases
- Invalid category param → Zod rejects, defaults
- Missing mode → All published (not recommended)
- Concurrent local+server same ID → Server wins
- Many local planners → Client-side pagination works

---

## Technical Constraints

**Dependencies:**
- TanStack Router v1.x (code-based routes, Zod validation)
- TanStack Query v5.x (useSuspenseQuery, separate query keys)

**Performance:**
- Different routes = different cache entries
- Client-side pagination for local planners (acceptable per spec)

**Pattern Compliance:**
- Suspense boundaries required (outer + inner)
- useSuspenseQuery in data hooks
- Link component for navigation (not `<a>`)
- Zod validation before component use
- i18n keys and constants (no hardcoded strings)
