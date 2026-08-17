# Plan: Handle Backend Critical Issues

## Planning Gaps

None. Research covers all requirements with clear mappings.

## Execution Overview

Fix 5 backend issues in dependency order:
1. Repository layer (atomic queries)
2. Service layer (use atomic queries)
3. DTO layer (security fix)
4. Validation layer (externalize config, error codes)
5. Logging (cleanup log level)
6. Tests for all changes

## Execution Order

### Phase 1: Repository Layer

1. **PlannerRepository.java**: Add atomic vote increment/decrement queries
   - Depends on: none
   - Enables: F1 (race condition fix)
   - Add @Modifying + @Query for incrementUpvotes, decrementUpvotes
   - Add @Modifying + @Query for incrementDownvotes, decrementDownvotes
   - Use WHERE clause to prevent negative values

### Phase 2: Service Layer

2. **PlannerService.java**: Refactor castVote to use atomic operations
   - Depends on: Step 1
   - Enables: F1 (race condition fix)
   - Replace read-modify-write with atomic repository calls
   - Re-fetch planner after atomic update for response

### Phase 3: DTO Layer

3. **PublicPlannerResponse.java**: Fix email exposure
   - Depends on: none
   - Enables: F2 (security fix)
   - Replace email extraction logic with hardcoded "Anonymous"

### Phase 4: Validation Layer

4. **application.properties**: Add validation size limits
   - Depends on: none
   - Enables: F3 (configurable limits)
   - Add planner.validation.max-content-size=51200
   - Add planner.validation.max-note-size=1024

5. **application-test.properties**: Add test values
   - Depends on: Step 4
   - Enables: F3 (configurable limits)
   - Mirror production values for consistency

6. **PlannerContentValidator.java**: Externalize constants + granular error codes
   - Depends on: Step 4
   - Enables: F3 (configurable limits), F4 (granular errors)
   - Replace static constants with @Value injection
   - Add constructor for dependency injection
   - Replace single ERROR_CODE with specific codes per validation type

### Phase 5: Logging

7. **PlannerSseService.java**: Change cleanup log level
   - Depends on: none
   - Enables: F5 (clean logs)
   - Change log.info to log.debug at line 180

### Phase 6: Tests

8. **PlannerRepositoryTest.java**: Test atomic queries
   - Depends on: Step 1
   - Test incrementUpvotes returns 1 for existing planner
   - Test decrementUpvotes stops at 0

9. **PlannerServiceTest.java**: Test concurrent votes
   - Depends on: Step 2
   - Add concurrent vote test with CountDownLatch
   - Verify no lost updates

10. **PublicPlannerResponseTest.java**: Test anonymous author
    - Depends on: Step 3
    - Test null email returns "Anonymous"
    - Test empty email returns "Anonymous"
    - Test valid email returns "Anonymous"

11. **PlannerContentValidatorTest.java**: Test error codes
    - Depends on: Step 6
    - Verify EMPTY_CONTENT for null/blank
    - Verify SIZE_EXCEEDED for oversized content
    - Verify MISSING_REQUIRED_FIELD for missing fields
    - Verify UNKNOWN_FIELD for extra fields
    - Verify INVALID_CATEGORY for bad category

## Verification Checkpoints

- After Step 2: Verify F1 - Run PlannerServiceTest, check no race condition
- After Step 3: Verify F2 - Check PublicPlannerResponse always returns "Anonymous"
- After Step 6: Verify F3, F4 - Run PlannerContentValidatorTest with new error codes
- After Step 7: Verify F5 - Check log output at DEBUG level only
- After Step 11: Run full test suite - ./mvnw test

## Rollback Strategy

- Steps 1-2 (atomic votes): Revert both together, they're coupled
- Step 3 (anonymous): Safe to revert independently
- Steps 4-6 (validation): Revert all 3 together, they're coupled
- Step 7 (logging): Safe to revert independently

## Safe Stopping Points

- After Step 2: Race condition fixed, security issues remain
- After Step 3: Security fixed, config issues remain
- After Step 7: All implementation done, tests remain
