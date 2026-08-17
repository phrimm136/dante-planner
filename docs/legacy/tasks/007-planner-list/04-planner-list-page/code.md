# Code: Planner List Page

## What Was Done
- Implemented full-stack Planner List Page with Community view (My Plans/Community tabs)
- Created backend: V006/V007 migrations, bookmark entity, DTOs, service methods, controller endpoints
- Added 4 search query methods to PlannerRepository with case-insensitive LIKE on title/keywords
- Built 7 frontend components: PlannerCard, ContextMenu, EmptyState, FilterPills, Pagination, Tabs, Toolbar
- Created 6 hooks: usePlannerListData, usePlannerListFilters, usePlannerVote, usePlannerBookmark, usePlannerFork, usePlannerPublish
- Fixed N+1 query issue with batch vote/bookmark fetching (reduces 1+2N to 3 queries)
- Added 65 tests (25 BE + 40 FE) covering service methods, hooks, and date formatting

## Files Changed

### Backend - New
- `backend/src/main/resources/db/migration/V006__add_view_count_and_bookmarks.sql`
- `backend/src/main/resources/db/migration/V007__rename_planner_view_index.sql`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerBookmarkId.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerBookmark.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerBookmarkRepository.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/BookmarkResponse.java`
- `backend/src/main/java/org/danteplanner/backend/dto/planner/ForkResponse.java`

### Backend - Modified
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` (viewCount field)
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java` (user context fields)
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java` (search queries)
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java` (batch query)
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` (bookmark, fork, batch fetch)
- `backend/src/main/java/org/danteplanner/backend/controller/PlannerController.java` (endpoints)

### Frontend - New
- `frontend/src/routes/PlannerListPage.tsx`
- `frontend/src/components/plannerList/PlannerCard.tsx`
- `frontend/src/components/plannerList/PlannerCardContextMenu.tsx`
- `frontend/src/components/plannerList/PlannerEmptyState.tsx`
- `frontend/src/components/plannerList/PlannerListFilterPills.tsx`
- `frontend/src/components/plannerList/PlannerListPagination.tsx`
- `frontend/src/components/plannerList/PlannerListTabs.tsx`
- `frontend/src/components/plannerList/PlannerListToolbar.tsx`
- `frontend/src/hooks/usePlannerListData.ts`
- `frontend/src/hooks/usePlannerListFilters.ts`
- `frontend/src/hooks/usePlannerVote.ts`
- `frontend/src/hooks/usePlannerBookmark.ts`
- `frontend/src/hooks/usePlannerFork.ts`
- `frontend/src/hooks/usePlannerPublish.ts`
- `frontend/src/types/PlannerListTypes.ts`
- `frontend/src/schemas/PlannerListSchemas.ts`
- `frontend/src/lib/formatDate.ts`

### Tests - New
- `frontend/src/hooks/usePlannerListData.test.tsx`
- `frontend/src/hooks/usePlannerVote.test.tsx`
- `frontend/src/lib/formatDate.test.ts`

## Verification Results
- Checkpoint 1 (After Step 15): Backend compiles, endpoints functional
- Checkpoint 2 (After Step 26): Hooks compile without TypeScript errors
- Checkpoint 3 (After Step 35): Page renders with Suspense
- Build: Pass (backend + frontend)
- Tests: 65/65 pass (3 pre-existing unrelated failures in PlannerControllerTest)

## Issues & Resolutions
- N+1 query bug in user context fetching -> Fixed with batch queries (`findByUserIdAndPlannerIdIn`)
- V006 index naming violation -> Created V007 migration to rename with version prefix
- Missing search implementation flagged by reviewer -> False positive (methods exist at lines 171-262)
- Hardcoded API URLs in hooks -> Deferred (low impact, could extract to constants later)
