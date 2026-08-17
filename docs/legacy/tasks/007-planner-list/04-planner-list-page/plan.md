# Execution Plan: Planner List Page

## Execution Overview

This task creates a Planner List Page with two views (My Plans / Community), filtering, pagination, and CRUD actions. Implementation follows a bottom-up approach:

1. **Backend first** - Schema migrations, entities, DTOs, service methods, endpoints
2. **Frontend data layer** - Types, schemas, hooks, API queries
3. **Frontend UI layer** - Components, page composition
4. **Integration** - Wire frontend to backend, test end-to-end
5. **Tests** - Unit tests for services/hooks, integration tests

---

## Execution Order

### Phase 1: Backend Data Layer (Schema + Entities)

1. **V006__add_view_count_and_bookmarks.sql**: Create migration for viewCount column and bookmarks table
   - Depends on: none
   - Enables: F3, F5

2. **entity/Planner.java**: Add viewCount column with default 0
   - Depends on: Step 1
   - Enables: F3

3. **entity/PlannerBookmarkId.java**: Create composite key class for bookmark
   - Depends on: Step 1
   - Enables: F5

4. **entity/PlannerBookmark.java**: Create bookmark entity following PlannerVote pattern
   - Depends on: Step 3
   - Enables: F5

5. **repository/PlannerBookmarkRepository.java**: Create JPA repository
   - Depends on: Step 4
   - Enables: F5

### Phase 2: Backend DTOs and Response Updates

6. **dto/planner/PublicPlannerResponse.java**: Add viewCount, lastModifiedAt, userVote, isBookmarked fields
   - Depends on: Step 2
   - Enables: F1, F3, F5

7. **dto/planner/BookmarkResponse.java**: Create response DTO for bookmark toggle
   - Depends on: none
   - Enables: F5

8. **dto/planner/ForkResponse.java**: Create response DTO for fork action
   - Depends on: none
   - Enables: F6

### Phase 3: Backend Service Methods

9. **service/PlannerService.java**: Add bookmark(), unbookmark(), isBookmarked() methods
   - Depends on: Steps 5, 7
   - Enables: F5

10. **service/PlannerService.java**: Add forkPlanner() method
    - Depends on: Step 8
    - Enables: F6

11. **service/PlannerService.java**: Add incrementViewCount() method
    - Depends on: Step 2
    - Enables: F3

12. **service/PlannerService.java**: Update getPublishedPlanners() to include user context
    - Depends on: Steps 6, 9
    - Enables: F1

### Phase 4: Backend Controller Endpoints

13. **controller/PlannerController.java**: Add POST /{id}/bookmark endpoint
    - Depends on: Step 9
    - Enables: F5

14. **controller/PlannerController.java**: Add POST /{id}/fork endpoint
    - Depends on: Step 10
    - Enables: F6

15. **controller/PlannerController.java**: Update GET /published to accept authenticated user
    - Depends on: Step 12
    - Enables: F1

### Phase 5: Frontend Data Layer (Types + Schemas)

16. **types/PlannerListTypes.ts**: Create types for list page
    - Depends on: none
    - Enables: F1, F2, F4

17. **schemas/PlannerListSchemas.ts**: Create Zod schemas for API responses
    - Depends on: Step 16
    - Enables: F1

18. **lib/formatDate.ts**: Create date formatting utility (timezone-aware)
    - Depends on: none
    - Enables: F1

19. **lib/constants.ts**: Add PLANNER_LIST constants (page size, sort options)
    - Depends on: none
    - Enables: F2, F4

### Phase 6: Frontend Hooks

20. **lib/plannerListQueries.ts**: Create query key factory
    - Depends on: none
    - Enables: F1

21. **hooks/usePlannerListData.ts**: Create hook (IndexedDB vs API based on auth)
    - Depends on: Steps 17, 20
    - Enables: F1

22. **hooks/usePlannerListFilters.ts**: Create hook for URL state management
    - Depends on: Step 19
    - Enables: F2, F4

23. **hooks/usePlannerVote.ts**: Create vote mutation hook
    - Depends on: Step 17
    - Enables: F5

24. **hooks/usePlannerBookmark.ts**: Create bookmark mutation hook
    - Depends on: Step 17
    - Enables: F5

25. **hooks/usePlannerFork.ts**: Create fork mutation hook
    - Depends on: Step 17
    - Enables: F6

26. **hooks/usePlannerPublish.ts**: Create publish/unpublish mutation hook
    - Depends on: none
    - Enables: F7

### Phase 7: Frontend UI Components

27. **Add shadcn pagination**: Run `npx shadcn@latest add pagination`
    - Depends on: none
    - Enables: F4

28. **components/plannerList/PlannerCard.tsx**: Create plan card component
    - Depends on: Steps 16, 18
    - Enables: F1

29. **components/plannerList/PlannerCardContextMenu.tsx**: Create context menu
    - Depends on: Step 28
    - Enables: F5-F7

30. **components/plannerList/PlannerListTabs.tsx**: Create tab switcher
    - Depends on: Step 22
    - Enables: F1

31. **components/plannerList/PlannerListToolbar.tsx**: Create search + sort
    - Depends on: Step 22
    - Enables: F2

32. **components/plannerList/PlannerListFilterPills.tsx**: Create category pills
    - Depends on: Step 22
    - Enables: F2

33. **components/plannerList/PlannerEmptyState.tsx**: Create empty messaging
    - Depends on: none
    - Enables: E1

34. **components/plannerList/PlannerListPagination.tsx**: Create pagination wrapper
    - Depends on: Steps 22, 27
    - Enables: F4

### Phase 8: Frontend Page Integration

35. **routes/PlannerListPage.tsx**: Compose all components with Suspense
    - Depends on: Steps 21-34
    - Enables: All features

36. **lib/router.tsx**: Add route for /planner/md
    - Depends on: Step 35
    - Enables: Page navigation

### Phase 9: i18n

37. **static/i18n/EN/plannerList.json**: Add English translations
38. **static/i18n/JP/plannerList.json**: Add Japanese translations
39. **static/i18n/KR/plannerList.json**: Add Korean translations
40. **static/i18n/CN/plannerList.json**: Add Chinese translations

### Phase 10: Backend Tests

41. **PlannerServiceTest.java**: Add tests for bookmark, fork, viewCount
    - Depends on: Steps 9-12
    - Enables: UT1-UT3

42. **PlannerControllerTest.java**: Add tests for bookmark, fork endpoints
    - Depends on: Steps 13-15
    - Enables: IT1-IT2

### Phase 11: Frontend Tests

43. **usePlannerListData.test.ts**: Test data source switching
44. **formatDate.test.ts**: Test date formatting logic
45. **usePlannerVote.test.ts**: Test vote mutation

---

## Verification Checkpoints

- **After step 15**: Verify backend API via curl/Postman
  - GET /published returns planners with viewCount, lastModifiedAt
  - POST /bookmark toggles bookmark state
  - POST /fork creates new draft

- **After step 26**: Verify all hooks compile without TypeScript errors

- **After step 35**: Verify page renders with mock data
  - Tab switching works
  - Cards display correctly
  - Context menu opens on right-click

- **After step 40**: Verify i18n keys resolve in all locales (EN, JP, KR, CN)

- **After step 45**: All tests pass (`yarn test`, `mvn test`)

---

## Rollback Strategy

**Safe stopping points:**
- After Phase 4 (backend complete) - Frontend can proceed independently
- After Phase 6 (hooks complete) - UI can be built with working data layer
- After Phase 8 (page complete) - Tests can be added incrementally

**If migration fails (Step 1):**
1. Create DOWN migration to rollback
2. Fix schema issues
3. Rerun migration

**If step N fails:**
1. Revert uncommitted changes
2. Analyze error
3. Fix and retry before proceeding
