# Execution Plan: Planner View Count

## Planning Gaps

**None.** All clarifications resolved in research.md.

---

## Execution Overview

Add view counting with daily deduplication for published planners. Follows existing `PlannerVote`/`PlannerBookmark` composite key pattern.

**Key Patterns:**
- `PlannerVote.java` + `PlannerVoteId.java` - composite key with Persistable<T>
- `PlannerBookmark.java` - simpler entity (no soft-delete)
- `PlannerRepository.incrementViewCount()` - already exists

---

## Execution Order

### Phase 1: Data Layer

1. **V008__add_planner_views.sql**: Create table with 3-column composite PK
   - Depends on: none
   - Enables: F1 (deduplication), E1 (daily reset)

2. **PlannerViewId.java**: Composite key (plannerId, viewerHash, viewDate)
   - Depends on: Step 1
   - Enables: Step 3
   - Pattern: Copy from PlannerVoteId.java

3. **PlannerView.java**: Entity with @IdClass and Persistable<T>
   - Depends on: Step 2
   - Enables: Step 5, F1
   - Pattern: Copy from PlannerBookmark.java

### Phase 2: Infrastructure

4. **ViewerHashUtil.java**: SHA-256 hashing utility
   - Depends on: none (parallel with Steps 1-3)
   - Enables: Step 6
   - Location: org.danteplanner.backend.util

5. **PlannerViewRepository.java**: Repository for view persistence
   - Depends on: Step 3
   - Enables: Step 6
   - Pattern: Copy from PlannerVoteRepository.java

### Phase 3: Logic Layer

6. **PlannerService.recordView()**: View recording with deduplication
   - Depends on: Steps 4, 5
   - Enables: Step 7, F1, F2
   - Logic: verify published → compute hash → check exists → save if new → increment

### Phase 4: Interface Layer

7. **PlannerController POST /{id}/view**: Public endpoint (204)
   - Depends on: Step 6
   - Enables: F3, I1
   - Extract IP from X-Forwarded-For or getRemoteAddr()

### Phase 5: Tests

8. **ViewerHashUtilTest.java**: Unit tests for hashing
   - Depends on: Step 4
   - Verifies: determinism, uniqueness, truncation

9. **PlannerServiceTest additions**: recordView() unit tests
   - Depends on: Step 6
   - Tests: new view increments, duplicate no-ops, unpublished 404

10. **PlannerControllerTest additions**: View endpoint integration tests
    - Depends on: Step 7
    - Tests: 204 response, 404 unpublished, IP extraction

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 1 | Schema applied | ./mvnw flyway:validate |
| 5 | Entity + Repo compile | ./mvnw compile -pl backend |
| 6 | F1, F2 | Unit tests (Step 9) |
| 7 | F3 | Integration tests, Manual curl |
| 10 | All features | ./mvnw test -pl backend |

---

## Rollback Strategy

| Step | Safe to Stop | Recovery |
|------|--------------|----------|
| 1 | Yes | Flyway skips incomplete migration |
| 2-5 | Yes | No runtime impact |
| 6 | Partial | Unused method |
| 7 | Critical | git checkout controller |
| 8-10 | Yes | Tests are additive |

**Safe Stopping Points:** After Step 5, After Step 7
