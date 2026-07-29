# Execution Plan: API Response Data Leakage Fix

## Confirmed Decisions
- **CommentResponse.java**: Delete (dead code - never instantiated in production)

## Execution Overview

**Simplified scope** - Comment system already uses correct patterns:
- `CommentTreeNode` for GET (already UUID, no user ID)
- `CreateCommentResponse` for POST (already UUID)
- `UpdateCommentResponse` for PUT (already UUID)

Actual work:
1. Remove `userId` from `PlannerResponse` (backend + frontend)
2. Change `UserDto.id` from `Long` to `UUID`
3. Delete dead `CommentResponse` code

**No migration needed. No entity changes needed.**

## Dependency Analysis

### Files Being Modified

| File | Impact Level | Used By |
|------|-------------|---------|
| `PlannerResponse.java` | Medium | `PlannerController`, `usePlannerSyncAdapter.ts` |
| `UserDto.java` | Low | `LoginResponse`, `AuthController` |
| `CommentResponse.java` | None | Nothing (dead code) |
| `ServerPlannerResponseSchema` | Medium | `usePlannerSyncAdapter.ts` |
| `PlannerTypes.ts` | Medium | Multiple planner hooks |

### Ripple Effect Map

- `PlannerResponse.userId` removed → Frontend schema/type/hook must sync
- `UserDto.id` Long→UUID → No frontend impact (id not used from login)
- `CommentResponse` deleted → Remove unused imports only

## Execution Order

### Phase 1: Backend DTO Cleanup

1. **`PlannerResponse.java`**: Remove `userId` field and `fromEntity()` mapping
   - Depends on: none
   - Enables: F1

2. **`UserDto.java`**: Change `id` type from `Long` to `UUID`
   - Depends on: none
   - Enables: F2

3. **`UserService.java`**: Update builder to use `user.getPublicId()`
   - Depends on: Step 2
   - Enables: F2

### Phase 2: Dead Code Cleanup

4. **`CommentResponse.java`**: Delete file
   - Depends on: none
   - Enables: F3

5. **`CommentController.java`**: Remove unused import (if any)
   - Depends on: Step 4

6. **`CommentService.java`**: Remove unused import (if any)
   - Depends on: Step 4

7. **`CommentServiceTest.java`**: Remove unused import (if any)
   - Depends on: Step 4

### Phase 3: Frontend Updates

8. **`PlannerSchemas.ts`**: Remove `userId` from `ServerPlannerResponseSchema`
   - Depends on: Step 1
   - Enables: F4

9. **`PlannerTypes.ts`**: Remove `userId` from `ServerPlannerResponse` interface
   - Depends on: Step 8
   - Enables: F5

10. **`usePlannerSyncAdapter.ts`**: Remove `userId: String(response.userId)` line
    - Depends on: Steps 8-9
    - Enables: F6

### Phase 4: Verification

11. **Backend compile**: `./mvnw compile -DskipTests`
    - Depends on: Steps 1-7

12. **Backend tests**: `./mvnw test`
    - Depends on: Step 11

13. **Frontend build**: `yarn build`
    - Depends on: Steps 8-10

## Verification Checkpoints

- **After Steps 1-7**: Backend compiles, no `CommentResponse` references
- **After Steps 8-10**: Frontend compiles, no type errors
- **After Steps 11-13**: All tests pass, builds succeed

## Risk Mitigation

| Risk | Step | Mitigation |
|------|------|------------|
| Backend compile failure | 4-7 | Verify imports before delete |
| Frontend type mismatch | 8-10 | Update schema→types→hooks in order |
| Sync regression | 10 | Test planner save/load manually |

## Pre-Implementation Validation

- [x] `CommentResponse.fromEntity()` has zero callers (confirmed)
- [x] `response.userId` in frontend is dead code (confirmed)
- [x] `User.getPublicId()` exists (confirmed)
- [x] Comment system already uses correct patterns (confirmed)

## Rollback Strategy

**Safe stopping points**:
- After Step 3: Backend DTO changes complete
- After Step 7: Dead code cleanup complete
- After Step 10: Frontend changes complete

**Rollback**: `git checkout -- .` to revert uncommitted changes
