# Execution Plan: Planner List Backend Infrastructure

## Confirmed Decisions
- **Migration version**: V002 (next sequential after V001)
- **Author name**: Extract username from email (before @)
- **Composite key**: @IdClass with PlannerVoteId class
- **API path**: `/planner/md/*` (migrate all existing + new endpoints)

---

## Execution Overview

Bottom-up backend implementation with API migration:
1. API path migration (existing endpoints)
2. Database schema (migration)
3. Entity layer (new columns, new entity, converter)
4. Repository layer (new queries)
5. DTO layer (request/response objects)
6. Service layer (business logic)
7. Controller layer (new endpoints)
8. Tests (unit + integration)

**Backend-only task. Frontend API path updates are follow-up work.**

---

## Execution Order

### Phase 0: API Path Migration (Step 1)

1. **PlannerController.java (modify)**: Change base path
   - Depends on: none
   - Change: `@RequestMapping("/api/planners")` → `@RequestMapping("/planner/md")`
   - Affects: All existing endpoints (CRUD, import, SSE)
   - Note: Frontend will need corresponding updates (separate task)

### Phase 1: Data Layer (Steps 2-7)

2. **V002__add_planner_publishing.sql**: Flyway migration
   - Depends on: none
   - Enables: F1, F2, F3
   - Add columns: published, upvotes, downvotes, selected_keywords
   - Create planner_votes table
   - Add indexes

3. **KeywordSetConverter.java**: JPA AttributeConverter for MySQL SET
   - Depends on: Step 2
   - Enables: F3
   - Location: `converter/KeywordSetConverter.java`

4. **VoteType.java**: Enum for UP/DOWN
   - Depends on: none
   - Enables: F2
   - Location: `entity/VoteType.java`

5. **PlannerVoteId.java**: Composite key class
   - Depends on: none
   - Enables: F2
   - Location: `entity/PlannerVoteId.java`

6. **PlannerVote.java**: Vote entity
   - Depends on: Steps 4, 5
   - Enables: F2
   - Pattern source: `Planner.java`

7. **Planner.java (modify)**: Add new fields
   - Depends on: Step 3
   - Enables: F1, F2, F3
   - Fields: published, upvotes, downvotes, selectedKeywords

### Phase 2: Repository Layer (Steps 8-9)

8. **PlannerVoteRepository.java**: Vote repository
   - Depends on: Step 6
   - Enables: F2
   - Methods: findByUserIdAndPlannerId, deleteByUserIdAndPlannerId

9. **PlannerRepository.java (modify)**: Add public queries
   - Depends on: Step 7
   - Enables: F1, F4
   - Methods: findPublished, findRecommended (net votes >= 10)

### Phase 3: DTO Layer (Steps 10-12)

10. **VoteRequest.java**: Vote request DTO
    - Depends on: Step 4
    - Enables: F2
    - Pattern source: `UpdatePlannerRequest.java`

11. **VoteResponse.java**: Vote response DTO
    - Depends on: none
    - Enables: F2

12. **PublicPlannerResponse.java**: Public planner list DTO
    - Depends on: none
    - Enables: F1, F4
    - Pattern source: `PlannerSummaryResponse.java`
    - authorName: Extract from user.email (before @)

### Phase 4: Service Layer (Step 13)

13. **PlannerService.java (modify)**: Add methods
    - Depends on: Steps 8, 9, 10, 11, 12
    - Enables: F1, F2, F4, F5
    - Methods:
      - getPublishedPlanners(Pageable, MDCategory)
      - getRecommendedPlanners(Pageable)
      - togglePublish(Long userId, UUID plannerId)
      - castVote(Long userId, UUID plannerId, VoteType)

### Phase 5: Controller Layer (Step 14)

14. **PlannerController.java (modify)**: Add new endpoints
    - Depends on: Step 13
    - Enables: All features
    - New endpoints (under /planner/md):
      - GET /published (public)
      - GET /recommended (public)
      - PUT /{id}/publish (owner only)
      - POST /{id}/vote (authenticated)

### Phase 6: Exception Handling (Steps 15-16)

15. **PlannerForbiddenException.java**: Non-owner publish
    - Depends on: none
    - Enables: E5

16. **GlobalExceptionHandler.java (modify)**: Add handler
    - Depends on: Step 15
    - Enables: E5

### Phase 7: Tests (Steps 17-19)

17. **PlannerServiceTest.java (modify)**: Unit tests
    - Depends on: Step 13
    - Tests: togglePublish, castVote, getPublishedPlanners, getRecommendedPlanners

18. **PlannerVoteRepositoryTest.java**: Repository tests
    - Depends on: Step 8
    - Tests: CRUD, unique constraint

19. **PlannerControllerTest.java (modify)**: Integration tests
    - Depends on: Step 14
    - Tests: All endpoints (new path /planner/md/*), auth scenarios

---

## API Path Summary

| Old Path | New Path |
|----------|----------|
| POST /api/planners | POST /planner/md |
| GET /api/planners | GET /planner/md |
| GET /api/planners/{id} | GET /planner/md/{id} |
| PUT /api/planners/{id} | PUT /planner/md/{id} |
| DELETE /api/planners/{id} | DELETE /planner/md/{id} |
| POST /api/planners/import | POST /planner/md/import |
| GET /api/planners/events | GET /planner/md/events |
| **NEW** | GET /planner/md/published |
| **NEW** | GET /planner/md/recommended |
| **NEW** | PUT /planner/md/{id}/publish |
| **NEW** | POST /planner/md/{id}/vote |

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| Step 1 | API path | Existing tests pass with new path |
| Step 2 | Schema | Run migration, check columns exist |
| Step 9 | Queries | Repository test queries |
| Step 13 | Service | Run unit tests (Step 17) |
| Step 14 | Endpoints | Run integration tests (Step 19) |
| Step 19 | Full | Manual API testing |

---

## Rollback Strategy

**Safe stopping points:**
- After Step 1: API path is isolated change
- After Step 2: Schema isolated, can rollback with new migration
- After Step 9: Repository layer complete
- After Step 14: Full implementation before tests

**If step fails:**
- Step 1: Revert RequestMapping path
- Step 2: Create V003 to reverse changes
- Steps 3-6: Delete new files
- Steps 7, 9, 13, 14, 16: Revert file changes

---

## File Inventory

### New Files (9)
- `db/migration/V002__add_planner_publishing.sql`
- `converter/KeywordSetConverter.java`
- `entity/VoteType.java`
- `entity/PlannerVoteId.java`
- `entity/PlannerVote.java`
- `dto/planner/VoteRequest.java`
- `dto/planner/VoteResponse.java`
- `dto/planner/PublicPlannerResponse.java`
- `exception/PlannerForbiddenException.java`

### Modified Files (6)
- `controller/PlannerController.java` (path + new endpoints)
- `entity/Planner.java`
- `repository/PlannerRepository.java`
- `repository/PlannerVoteRepository.java` (new)
- `service/PlannerService.java`
- `exception/GlobalExceptionHandler.java`

### Test Files (3)
- `PlannerServiceTest.java` (modify)
- `PlannerVoteRepositoryTest.java` (new)
- `PlannerControllerTest.java` (modify - new path)

---

## Follow-up Tasks (Out of Scope)

- [ ] Frontend: Update API base URL from `/api/planners` to `/planner/md`
- [ ] Frontend: Update all fetch/mutation hooks with new paths
