# Execution Plan: Planner Server Sync

## Execution Overview

Build backend bottom-up (DB → Entity → Repository → Service → Controller), then frontend top-down (Types → API → Hooks → Integration). SSE infrastructure built alongside CRUD, not after. Migration hook built last.

**Total Steps:** 26
**Phases:** 5

---

## Execution Order

### Phase 1: Backend Core (Steps 1-9)

1. **V001__create_planners_table.sql**: Flyway migration
   - Depends on: none
   - Enables: F1 (Server Persistence)

2. **MDCategory.java**: Enum (5F, 10F, 15F)
   - Depends on: none
   - Enables: F1

3. **Planner.java**: JPA entity with UUID, JSON content, soft delete, syncVersion
   - Depends on: Step 2
   - Enables: F1, F3, F4

4. **PlannerRepository.java**: JPA repository with custom queries
   - Depends on: Step 3
   - Enables: F1, F2

5. **PlannerNotFoundException.java**: 404 exception
   - Depends on: none
   - Enables: F2

6. **PlannerConflictException.java**: 409 exception for sync conflicts
   - Depends on: none
   - Enables: F3

7. **PlannerLimitExceededException.java**: 409 exception for 100-planner limit
   - Depends on: none
   - Enables: F6

8. **PlannerValidationException.java**: 400 exception for size limits
   - Depends on: none
   - Enables: F5

9. **GlobalExceptionHandler.java**: @RestControllerAdvice
   - Depends on: Steps 5-8
   - Enables: All error handling

### Phase 2: Backend DTOs & Service (Steps 10-16)

10. **CreatePlannerRequest.java**: Request DTO with validation
    - Enables: F2 (Create)

11. **UpdatePlannerRequest.java**: Request DTO with syncVersion
    - Enables: F2 (Update), F3

12. **PlannerResponse.java**: Full response DTO
    - Enables: F2

13. **PlannerSummaryResponse.java**: List response DTO
    - Enables: F2

14. **ImportPlannersRequest.java**: Bulk import request
    - Enables: F7

15. **ImportPlannersResponse.java**: Import result response
    - Enables: F7

16. **PlannerService.java**: CRUD, validation, limits
    - Depends on: Steps 3, 4, 5-8, 10-15
    - Enables: F1, F2, F3, F5, F6, F7

### Phase 3: Backend SSE & Controller (Steps 17-18)

17. **PlannerSseService.java**: SSE emitter management with device filtering
    - Depends on: none
    - Enables: F4

18. **PlannerController.java**: REST + SSE endpoints + rate limiting
    - Depends on: Steps 16, 17
    - Enables: F1-F7

### Phase 4: Frontend Types & API (Steps 19-22)

19. **PlannerTypes.ts** (MODIFY): Add syncVersion, change userId to number
    - Enables: F3

20. **PlannerSchemas.ts** (MODIFY): Add syncVersion to schema
    - Depends on: Step 19
    - Enables: F3

21. **api.ts** (MODIFY): Add put(), delete(), createEventSource()
    - Enables: F2, F4

22. **plannerApi.ts**: Planner-specific API functions
    - Depends on: Step 21
    - Enables: F1, F2, F7

### Phase 5: Frontend Hooks (Steps 23-26)

23. **usePlannerSync.ts**: Server storage + SSE subscription
    - Depends on: Steps 19-22
    - Enables: F1, F3, F4

24. **usePlannerStorageAdapter.ts**: Routes to local/server based on auth
    - Depends on: Step 23
    - Enables: F8

25. **usePlannerAutosave.ts** (MODIFY): Use adapter
    - Depends on: Step 24
    - Enables: Unified auto-save

26. **usePlannerMigration.ts**: First-login IndexedDB to server migration
    - Depends on: Steps 22, 24
    - Enables: F7

---

## Verification Checkpoints

**After Phase 1 (Step 9):**
- Run `./mvnw compile` - verify all entities compile
- Verify Flyway migration applies cleanly

**After Phase 2 (Step 16):**
- Unit test PlannerService CRUD methods
- Unit test optimistic locking (syncVersion increment)
- Unit test size validation (50KB, 1KB limits)
- Unit test 100-planner limit

**After Phase 3 (Step 18):**
- Integration test all REST endpoints
- Test 409 on version conflict
- Manual test SSE with `curl`
- Test rate limiting (11 requests → 429)

**After Phase 4 (Step 22):**
- `yarn type-check` passes
- Zod schema validates syncVersion

**After Phase 5 (Step 26):**
- Manual test authenticated save to server
- Manual test multi-tab SSE notifications
- Manual test migration flow
- Manual test guest mode
- Run full E2E test suite

---

## Rollback Strategy

**Safe Stopping Points:**
- After Phase 1: Backend compiles, DB ready, no frontend impact
- After Phase 3: Backend functional, frontend unchanged
- After Phase 5: Full feature complete

**If step fails:**

| Phase | Rollback Action |
|-------|-----------------|
| 1 | Drop planners table, delete migration file |
| 2-3 | Delete new backend files |
| 4 | Revert type/schema changes |
| 5 | Revert usePlannerAutosave, delete new hooks |

**Critical:** If Phase 5 fails, ensure usePlannerAutosave.ts uses usePlannerStorage directly (not adapter)
