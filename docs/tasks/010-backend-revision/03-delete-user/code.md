# Implementation Results: User Account Soft-Delete

## What Was Done

- Implemented two-phase user deletion: soft-delete (immediate auth block) → hard-delete (30-day scheduler)
- Added `deletedAt` and `permanentDeleteScheduledAt` fields to User entity with Instant timestamps
- Created sentinel user (id=0) for reassigning orphaned votes after hard-delete
- Added DELETE /api/user/me endpoint with rate limiting
- Implemented OAuth reactivation flow during grace period with `reactivated` flag
- Created scheduled job for daily hard-delete cleanup (3 AM cron)
- Added 29+ unit tests covering all deletion/reactivation/hard-delete paths

## Files Changed

### New Files
- `backend/src/main/resources/db/migration/V009__add_user_soft_delete.sql`
- `backend/src/main/java/org/danteplanner/backend/exception/AccountDeletedException.java`
- `backend/src/main/java/org/danteplanner/backend/dto/user/UserDeletionResponse.java`
- `backend/src/main/java/org/danteplanner/backend/controller/UserController.java`
- `backend/src/main/java/org/danteplanner/backend/scheduler/UserCleanupScheduler.java`
- `backend/src/test/java/org/danteplanner/backend/service/UserServiceTest.java`
- `backend/src/test/java/org/danteplanner/backend/controller/UserControllerTest.java`
- `backend/src/test/java/org/danteplanner/backend/scheduler/UserCleanupSchedulerTest.java`
- `backend/src/test/java/org/danteplanner/backend/security/JwtAuthenticationFilterTest.java`

### Modified Files
- `backend/src/main/java/org/danteplanner/backend/entity/User.java`
- `backend/src/main/java/org/danteplanner/backend/repository/UserRepository.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java`
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java`
- `backend/src/main/resources/application.properties`
- `backend/src/test/java/org/danteplanner/backend/facade/AuthenticationFacadeTest.java`

## Verification Results

- Build: PASS (`./mvnw compile`)
- Unit Tests: PASS (all 21 soft-delete related tests)
- Code Review: PASS (after fixes applied)
- Manual Verification: PENDING (added to docs/TODO.md as TEST-002)

## Issues & Resolutions

- SQL COMMENT syntax in migration → removed, used standard SQL comments instead
- Scheduler transaction scope wrapped entire loop → moved @Transactional to performHardDelete for per-user isolation
- Missing rate limiting on DELETE endpoint → added RateLimitConfig.checkCrudLimit()
- reactivateAccount() not idempotent → added early return for already-active users
- UserControllerTest missing RateLimitConfig mock → added @Mock for RateLimitConfig
