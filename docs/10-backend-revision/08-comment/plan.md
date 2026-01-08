# Execution Plan: Planner Comment System (Backend)

> **Status:** READY FOR IMPLEMENTATION
> **Generated:** 2026-01-08
> **Total Steps:** 25

---

## Planning Gaps

**NONE** - Research complete, all patterns documented.

---

## Execution Overview

Build threaded comment system following existing voting pattern (PlannerVote). Strategy: data layer → migration → repository → DTO → exception → service → controller → integration → tests.

---

## Dependency Analysis

### Files Being Modified

| File | Impact | Depends On | Used By |
|------|--------|------------|---------|
| UserAccountLifecycleService.java | Medium | Comment repos | UserCleanupScheduler |
| ModerationService.java | Medium | CommentRepository | ModerationController |
| ModerationController.java | Medium | ModerationService | REST clients |
| RateLimitConfig.java | Medium | BucketConfig | CommentController |
| GlobalExceptionHandler.java | Low | Comment exceptions | All controllers |
| SecurityConfig.java | High | - | All auth requests |

### High-Risk Modifications

| File | Risk | Mitigation |
|------|------|------------|
| SecurityConfig.java | Affects all auth | Minimal changes, test existing endpoints |
| UserAccountLifecycleService | Could break user deletion | Add after existing logic, integration test |

---

## Execution Order

### Phase 1: Data Layer

1. **CommentVoteType.java** - Create enum (UP only)
   - Depends on: none
   - Enables: F6
   - Pattern: VoteType.java

2. **PlannerCommentVoteId.java** - Composite key class
   - Depends on: none
   - Enables: F6, F7
   - Pattern: PlannerVoteId.java

3. **PlannerComment.java** - Comment entity with soft-delete
   - Depends on: none
   - Enables: F1-F5, F8
   - Pattern: PlannerVote.java

4. **PlannerCommentVote.java** - Vote entity with Persistable
   - Depends on: Steps 1, 2
   - Enables: F6, F7
   - Pattern: PlannerVote.java

### Phase 2: Database

5. **V015__add_planner_comments.sql** - Migration
   - Depends on: none
   - Enables: All features
   - Verify: `./mvnw flyway:migrate`

### Phase 3: Repository Layer

6. **PlannerCommentRepository.java** - CRUD + atomic counters
   - Depends on: Step 3
   - Enables: F1-F5, F7, F8, I1
   - Pattern: PlannerRepository.java

7. **PlannerCommentVoteRepository.java** - Vote queries
   - Depends on: Steps 2, 4
   - Enables: F6, F7, I2
   - Pattern: PlannerVoteRepository.java

### Phase 4: DTO Layer

8. **CreateCommentRequest.java** - Create/reply validation
   - Depends on: none
   - Enables: F1, F2

9. **UpdateCommentRequest.java** - Edit validation
   - Depends on: none
   - Enables: F3

10. **CommentResponse.java** - Response with AuthorInfo
    - Depends on: none
    - Enables: F1-F8
    - Pattern: PublicPlannerResponse.java

11. **CommentVoteResponse.java** - Vote result
    - Depends on: none
    - Enables: F6

### Phase 5: Exception Layer

12. **CommentNotFoundException.java**
    - Depends on: none
    - Pattern: PlannerNotFoundException.java

13. **CommentForbiddenException.java**
    - Depends on: none
    - Pattern: PlannerForbiddenException.java

### Phase 6: Service Layer

14. **CommentService.java** - All business logic
    - Depends on: Steps 3, 4, 6, 7, 12, 13
    - Enables: F1-F8, E1-E5
    - Pattern: ModerationService.java, PlannerService.java

### Phase 7: Controller Layer

15. **CommentController.java** - REST endpoints
    - Depends on: Steps 8-11, 14
    - Pattern: ModerationController.java
    - Verify: `./mvnw compile`

### Phase 8: Integration

16. **GlobalExceptionHandler.java** - Add 2 handlers
    - Depends on: Steps 12, 13

17. **RateLimitConfig.java** - Add comment bucket
    - Depends on: none
    - Enables: I3

18. **application.properties** - Rate limit values
    - Depends on: Step 17

19. **UserAccountLifecycleService.java** - Add reassignment
    - Depends on: Steps 6, 7
    - Enables: I1, I2

20. **ModerationService.java** - Add deleteComment
    - Depends on: Steps 6, 12, 13
    - Enables: F5

21. **ModerationController.java** - Add moderation endpoint
    - Depends on: Step 20
    - Enables: F5

22. **SecurityConfig.java** - Configure permissions
    - Depends on: Step 15
    - Verify: Test existing auth

### Phase 9: Tests

23. **CommentServiceTest.java** - Unit tests
    - Tests: UT1, UT2, UT3

24. **CommentVoteTypeTest.java** - Enum tests
    - Tests: UT4

25. **CommentControllerIntegrationTest.java** - API tests
    - Tests: IT1-IT5
    - Verify: `./mvnw test`

---

## Verification Checkpoints

| After Step | Verify | Method |
|------------|--------|--------|
| 5 | Migration runs | `./mvnw flyway:migrate` |
| 7 | Repos compile | `./mvnw compile` |
| 14 | Service compiles | `./mvnw compile` |
| 15 | Controller compiles | `./mvnw compile` |
| 22 | Full backend compiles | `./mvnw compile` |
| 25 | All tests pass | `./mvnw test` |

---

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| N+1 queries | 6 | Denormalized upvote_count |
| Race condition | 6 | Atomic counter with WHERE |
| Negative count | 6 | WHERE upvoteCount > 0 |
| Break user deletion | 19 | Add after existing; integration test |
| Break auth | 22 | Minimal changes; test existing |
| Content length attack | 8 | @Size(max=10000) |

---

## Rollback Strategy

**Safe stopping points:**
- After Phase 2: Migration only, no code
- After Phase 7: Complete feature, isolated
- After Phase 8: Fully integrated

**If step fails:**
- Steps 1-4: Delete files
- Step 5: Create V016 to drop tables
- Steps 6-15: Delete new files
- Steps 16-22: `git checkout` reverts
- Steps 23-25: Delete test files
