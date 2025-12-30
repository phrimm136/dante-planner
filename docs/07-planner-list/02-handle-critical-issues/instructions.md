# Task: Handle Backend Critical Issues

## Description

Address critical security and data integrity issues identified in code reviews from recent planner-related tasks. These issues were flagged in `docs/07-planner-list/01-list-initialization/review.md` and `docs/06-planner-creation/13-backend-finalize-implementation/review.md`.

**API Path Convention**: Always use `/api/planner/md/*` as the canonical API path. Frontend must be updated to match this path if inconsistent.

### Critical Issues (Must Fix)

1. **Vote Count Race Condition** - `PlannerService.castVote()` uses read-modify-write pattern for vote counts. Concurrent votes can lose data. Fix using atomic database increment operations.

2. **Email Exposure Security Vulnerability** - `PublicPlannerResponse.fromEntity()` extracts username from email prefix (e.g., `user@gmail.com` → `user`). This leaks personal data. Return "Anonymous" until `User.displayName` is implemented.

### Medium Issues (Technical Debt)

3. **Magic Constants in Validator** - `PlannerContentValidator` has hardcoded size limits (`MAX_CONTENT_SIZE_BYTES = 50KB`, `MAX_NOTE_SIZE_BYTES = 1KB`). Move to `application.properties` for configurability.

4. **Single Validation Error Code** - All validation failures return `INVALID_JSON` error code. Clients cannot distinguish failure types. Add granular error codes: `MISSING_REQUIRED_FIELD`, `INVALID_CATEGORY`, `UNKNOWN_FIELD`, `SIZE_EXCEEDED`, `INVALID_ID`.

5. **SSE Cleanup Log Level** - `PlannerSseService.cleanupZombieConnections()` logs at INFO level every 60 seconds. This pollutes logs. Change to DEBUG level.

## Research

- **Atomic increment pattern**: Check if `@Modifying` + `@Query("UPDATE ... SET x = x + 1")` is the project pattern
- **Existing validation error codes**: Check `PlannerValidationException` constructor for error code handling
- **Configuration pattern**: Check existing `@Value` usage in `PlannerContentValidator` or similar validators
- **Log level conventions**: Check existing `@Scheduled` methods for log level patterns

## Scope

Read for context:
- `backend/src/main/java/org/danteplanner/backend/service/PlannerService.java` - castVote method
- `backend/src/main/java/org/danteplanner/backend/dto/planner/PublicPlannerResponse.java` - fromEntity method
- `backend/src/main/java/org/danteplanner/backend/validation/PlannerContentValidator.java` - size constants
- `backend/src/main/java/org/danteplanner/backend/service/PlannerSseService.java` - cleanup method
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerRepository.java` - for atomic queries
- `backend/src/main/java/org/danteplanner/backend/exception/PlannerValidationException.java` - error code pattern

## Target Code Area

| File | Action | Change |
|------|--------|--------|
| `PlannerRepository.java` | Modify | Add atomic increment/decrement queries |
| `PlannerService.java` | Modify | Refactor castVote to use atomic operations |
| `PublicPlannerResponse.java` | Modify | Return "Anonymous" instead of email extraction |
| `PlannerContentValidator.java` | Modify | Replace constants with @Value, add granular error codes |
| `PlannerSseService.java` | Modify | Change log.info → log.debug for cleanup |
| `application.properties` | Modify | Add planner.validation.* properties |
| `application-test.properties` | Modify | Add test values for new properties |

## Testing Guidelines

### Manual Verification

1. **Race Condition Fix**
   - This requires load testing or code review verification
   - Verify castVote uses atomic repository methods, not read-modify-write

2. **Email Exposure Fix**
   - Call `GET /api/planner/md/published`
   - Verify all `authorName` fields show "Anonymous"
   - Verify no email addresses or email prefixes appear

3. **Validation Error Codes**
   - POST invalid planner with missing `title` field
   - Verify response contains `MISSING_REQUIRED_FIELD` error code
   - POST planner with unknown field
   - Verify response contains `UNKNOWN_FIELD` error code
   - POST planner exceeding size limit
   - Verify response contains `SIZE_EXCEEDED` error code

4. **Log Level Change**
   - Run backend with DEBUG logging enabled
   - Verify cleanup messages appear at DEBUG level
   - Run with INFO level only
   - Verify cleanup messages do NOT appear

### Automated Functional Verification

- [ ] Atomic vote increment: PlannerRepository.incrementUpvotes() uses @Modifying + @Query
- [ ] Atomic vote decrement: PlannerRepository.decrementUpvotes() prevents negative values
- [ ] castVote uses atomic: Service method calls repository atomic methods
- [ ] Anonymous author: PublicPlannerResponse always returns "Anonymous"
- [ ] Configurable size limits: MAX_CONTENT_SIZE read from application.properties
- [ ] Granular error codes: At least 5 distinct error codes exist

### Edge Cases

- [ ] Concurrent votes: Two simultaneous UP votes both increment (not lost update)
- [ ] Vote count floor: Decrement stops at 0, never goes negative
- [ ] Null email: User with null email still returns "Anonymous"
- [ ] Empty email: User with empty string email returns "Anonymous"
- [ ] Invalid JSON: Returns appropriate error code, not generic "INVALID_JSON"

### Unit Tests to Add/Modify

- [ ] `PlannerServiceTest`: Add concurrent vote test (use CountDownLatch)
- [ ] `PlannerContentValidatorTest`: Verify distinct error codes for each validation failure type
- [ ] `PublicPlannerResponseTest`: Test fromEntity with null/empty/valid email cases
- [ ] `RateLimitConfigTest`: Existing tests should still pass

## Implementation Order

1. **Phase 1: Repository Layer** - Add atomic increment/decrement methods
2. **Phase 2: Service Layer** - Refactor castVote to use atomic methods
3. **Phase 3: DTO Layer** - Fix PublicPlannerResponse email exposure
4. **Phase 4: Validation Layer** - Externalize constants, add error codes
5. **Phase 5: Logging** - Change SSE cleanup log level
6. **Phase 6: Testing** - Update/add tests for all changes

## Acceptance Criteria

- [ ] No race condition possible in vote counting
- [ ] No PII (email) exposed in public API responses
- [ ] Size limits configurable via application.properties
- [ ] Client can distinguish validation failure types
- [ ] SSE cleanup doesn't pollute INFO-level logs
- [ ] All existing tests pass
- [ ] New tests cover race condition and email scenarios
