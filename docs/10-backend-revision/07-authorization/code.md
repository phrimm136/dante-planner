# Authorization System - Implementation Results

## What Was Done
- Implemented 3-tier role hierarchy (NORMAL < MODERATOR < ADMIN) with explicit rank comparison
- Added role and timeout fields to User entity with V013 migration
- Embedded role in JWT access tokens (not refresh) for fast authorization
- Created AdminService with safeguards: cannot grant higher, cannot demote last admin, cannot modify equal rank
- Created ModerationService for user timeout and planner unpublish
- Added user-level token invalidation in TokenBlacklistService (demotion triggers immediate revoke)
- Integrated timeout check in PlannerService write operations
- Fixed 3 critical issues: TOCTOU race condition, ordinal() fragility, token invalidation persistence

## Files Changed

### New Files
- `entity/UserRole.java` - Enum with explicit rank values
- `controller/AdminController.java` - PUT /api/admin/user/{id}/role
- `controller/ModerationController.java` - Timeout and unpublish endpoints
- `service/AdminService.java` - Role change with safeguards + pessimistic locking
- `service/ModerationService.java` - Timeout and unpublish logic
- `dto/admin/ChangeRoleRequest.java`, `UserRoleResponse.java`
- `dto/moderation/TimeoutRequest.java`, `TimeoutResponse.java`
- `exception/UserTimedOutException.java`
- `db/migration/V013__add_user_authorization.sql`
- `db/migration/V014__add_timeout_index.sql`
- `service/AdminServiceTest.java`, `ModerationServiceTest.java`

### Modified Files
- `entity/User.java` - Added role, timeoutUntil, isTimedOut()
- `repository/UserRepository.java` - countByRole, findWithLockByIdAndDeletedAtIsNull
- `service/token/TokenClaims.java` - Added role field + getEffectiveRole()
- `service/token/TokenGenerator.java` - Added role param to generateAccessToken
- `service/token/JwtTokenService.java` - Embed/extract role in tokens
- `service/token/TokenBlacklistService.java` - User-level invalidation + clearUserInvalidation
- `security/JwtAuthenticationFilter.java` - Role→authority, user-level blacklist check
- `config/SecurityConfig.java` - RoleHierarchy bean, /api/admin/** and /api/moderation/** rules
- `service/PlannerService.java` - Timeout check in writes
- `facade/AuthenticationFacade.java` - Pass role to token, clear invalidation on login
- `exception/GlobalExceptionHandler.java` - UserTimedOutException handler

## Verification Results
- Checkpoint 4 (Data Layer): PASS - Migration applies, compile succeeds
- Checkpoint 8 (Token Layer): PASS - Access token has role, refresh doesn't
- Checkpoint 10 (Security Layer): PASS - Existing endpoints accessible
- Checkpoint 19 (Controller Layer): PASS - 403 for unauthorized
- Build: PASS - `./mvnw compile` succeeds
- Tests: PASS - 465 tests, 0 failures

## Issues & Resolutions
- TOCTOU race condition in changeRole() → Added @Lock(PESSIMISTIC_WRITE) on user fetch
- UserRole.ordinal() fragile for comparison → Added explicit rank field (1,2,3)
- Token invalidation persists after re-login → Added clearUserInvalidation() on auth
- Missing timeout index for queries → Created V014 migration with partial index
- Redundant DB query in PlannerService → Refactored getUserAndCheckNotTimedOut to return User
