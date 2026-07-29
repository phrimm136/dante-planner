# Task: User Account Soft-Delete with Two-Phase Deletion

## Description

Implement account deletion functionality that allows users to delete their accounts with a 30-day grace period before permanent deletion. The system should:

### Core Behavior
- User requests account deletion via API endpoint
- Account is immediately "soft-deleted" (blocked from authentication)
- A 30-day grace period begins before permanent hard-delete
- User can reactivate account by re-authenticating during grace period
- After 30 days, a background scheduler permanently deletes the account

### Data Handling on Soft-Delete
- User's published planners remain visible with author shown as "[Deleted User]"
- User cannot log in or perform any authenticated actions
- All existing JWT tokens should be rejected

### Data Handling on Hard-Delete (Day 30)
- User record is permanently deleted from database
- User's planners are CASCADE deleted (existing FK behavior)
- User's votes/bookmarks are reassigned to a "sentinel user" (id=0) to preserve referential integrity
- Vote counts on planners remain accurate (votes are preserved, just re-attributed)

### Post-Hard-Delete Behavior
- If user re-authenticates via OAuth after hard-delete, a **fresh new account is created**
- User starts over with no planners, no history
- This is expected behavior — permanent deletion means complete erasure

### Reactivation Flow (During Grace Period Only)
- If soft-deleted user re-authenticates via OAuth during 30-day grace period
- Account is automatically reactivated (`deleted_at` and `permanent_delete_scheduled_at` cleared)
- User regains full access immediately
- All their planners remain intact and attributed to them

### Sentinel User Pattern
- Create a reserved user record (id=0, email="[deleted]", provider="system")
- This user cannot be authenticated as (guard clause required)
- All orphaned votes/bookmarks reference this sentinel user after hard-delete
- Enables future FK constraints without data integrity issues

## Research

### Existing Patterns to Follow
- Soft-delete pattern in `Planner.java` (lines 110+): `isDeleted()`, `softDelete()` methods
- Soft-delete pattern in `PlannerVote.java` (lines 144-162): includes `reactivate()` method
- Repository query pattern: `findBy...AndDeletedAtIsNull()` used throughout `PlannerRepository`
- JPA auditing: Manual `@PrePersist/@PreUpdate` hooks (not annotation-based)

### Files to Study
- `backend/src/main/java/org/danteplanner/backend/entity/User.java` - current schema
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java` - soft-delete reference
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java` - reactivation pattern
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java` - auth flow
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java` - OAuth handling
- `backend/src/main/resources/db/migration/` - Flyway migration patterns

### Technical Decisions Made
- Use `Instant` for timestamp fields (consistent with Planner pattern)
- Sentinel user ID = 0 (reserved, created in migration)
- Grace period = 30 days (configurable via `@Value` recommended)
- Scheduler runs daily at 3 AM (cron: `0 0 3 * * *`)
- Post-hard-delete re-registration creates fresh account (no tombstone blocking)

## Scope

### Read for Context
- `backend/src/main/java/org/danteplanner/backend/entity/User.java`
- `backend/src/main/java/org/danteplanner/backend/entity/Planner.java`
- `backend/src/main/java/org/danteplanner/backend/entity/PlannerVote.java`
- `backend/src/main/java/org/danteplanner/backend/repository/UserRepository.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java`
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java`

## Target Code Area

### New Files
- `backend/src/main/resources/db/migration/V009__add_user_soft_delete.sql`
- `backend/src/main/java/org/danteplanner/backend/scheduler/UserCleanupScheduler.java`
- `backend/src/main/java/org/danteplanner/backend/controller/UserController.java`
- `backend/src/main/java/org/danteplanner/backend/dto/user/UserDeletionResponse.java`
- `backend/src/main/java/org/danteplanner/backend/exception/AccountDeletedException.java`

### Modified Files
- `backend/src/main/java/org/danteplanner/backend/entity/User.java`
- `backend/src/main/java/org/danteplanner/backend/repository/UserRepository.java`
- `backend/src/main/java/org/danteplanner/backend/repository/PlannerVoteRepository.java`
- `backend/src/main/java/org/danteplanner/backend/service/UserService.java`
- `backend/src/main/java/org/danteplanner/backend/security/JwtAuthenticationFilter.java`
- `backend/src/main/java/org/danteplanner/backend/facade/AuthenticationFacade.java`
- `backend/src/main/java/org/danteplanner/backend/exception/GlobalExceptionHandler.java`

## Testing Guidelines

### Manual API Testing

#### Delete Account Flow
1. Authenticate as a test user via Google OAuth
2. Note the user's access token and user ID
3. Send `DELETE /api/user/me` with the access token
4. Verify response contains `deletedAt` and `permanentDeleteAt` (30 days later)
5. Attempt `GET /api/planner/md` with same token
6. Verify 401 response with "Account deleted" message
7. Attempt to refresh token via `/api/auth/refresh`
8. Verify 401 response (deleted users cannot refresh)

#### Reactivation Flow (During Grace Period)
1. After soft-delete, re-authenticate via Google OAuth
2. Verify new tokens are issued
3. Verify response includes `reactivated: true` flag
4. Verify `GET /api/planner/md` now works
5. Verify user's planners are still present and attributed to them

#### Fresh Account After Hard-Delete
1. Soft-delete a user account
2. Manually set `permanent_delete_scheduled_at` to past date in DB
3. Trigger scheduler (or wait for 3 AM cron)
4. Verify user record is deleted
5. Re-authenticate via Google OAuth with same Google account
6. Verify a NEW user record is created with different ID
7. Verify new user has zero planners (fresh start)

#### Published Planner Display
1. Create a planner and publish it
2. Note the planner ID
3. Delete the user account
4. Access `GET /api/planner/md/published`
5. Verify the planner is still listed
6. Verify author is null or marked for "[Deleted User]" frontend handling

#### Hard-Delete Cascade Behavior
1. Soft-delete a user with planners and votes
2. Manually set `permanent_delete_scheduled_at` to past date in DB
3. Trigger scheduler
4. Verify user record is deleted from `users` table
5. Verify user's planners are deleted (CASCADE)
6. Verify user's votes now have `user_id = 0` (sentinel)
7. Verify planner vote counts on OTHER planners remain unchanged

### Automated Functional Verification

#### Authentication Guards
- [ ] Soft-deleted user cannot use existing access token
- [ ] Soft-deleted user cannot refresh tokens
- [ ] Soft-deleted user can re-authenticate via OAuth (triggers reactivation)
- [ ] Sentinel user (id=0) cannot be authenticated as
- [ ] Hard-deleted user re-authenticating creates fresh account

#### Soft-Delete Behavior
- [ ] `DELETE /api/user/me` sets `deleted_at` to current timestamp
- [ ] `DELETE /api/user/me` sets `permanent_delete_scheduled_at` to +30 days
- [ ] Soft-deleted user's planners remain published
- [ ] Soft-deleted user's planners remain in browse results

#### Reactivation Behavior
- [ ] OAuth login clears `deleted_at` for soft-deleted user
- [ ] OAuth login clears `permanent_delete_scheduled_at`
- [ ] Reactivated user has full access restored
- [ ] Reactivated user's planners still attributed to them

#### Hard-Delete Behavior
- [ ] Scheduler finds users past `permanent_delete_scheduled_at`
- [ ] Votes are reassigned to sentinel user before user deletion
- [ ] User record is permanently deleted
- [ ] Planners are CASCADE deleted with user
- [ ] Vote counts on other planners remain accurate
- [ ] Re-registration after hard-delete creates new user ID

### Edge Cases

- [ ] User deletes then immediately re-authenticates: Reactivated, no data loss
- [ ] User re-authenticates after hard-delete: New account created, fresh start
- [ ] User with 0 planners deleted: Clean deletion, no orphans
- [ ] User with 100+ votes deleted: All votes reassigned to sentinel efficiently
- [ ] Scheduler runs with no expired users: No errors, no-op
- [ ] Sentinel user record missing: Migration must create it, scheduler should error gracefully
- [ ] Double-delete request: Idempotent, returns same response
- [ ] Delete during active SSE connection: Connection should terminate gracefully

### Integration Points

- [ ] JwtAuthenticationFilter: Rejects tokens for deleted users
- [ ] PlannerService: Queries using `findByIdActiveOnly` reject deleted users
- [ ] AuthController: OAuth callback handles reactivation
- [ ] GlobalExceptionHandler: `AccountDeletedException` returns proper 401/403
- [ ] PlannerSseService: Closes connections for deleted users (if applicable)

## API Contracts

### DELETE /api/user/me
```
Authorization: Bearer <token>

Response 200:
{
  "message": "Account scheduled for deletion",
  "deletedAt": "2026-01-03T10:00:00Z",
  "permanentDeleteAt": "2026-02-02T10:00:00Z",
  "gracePeriodDays": 30
}

Response 401 (already deleted):
{
  "error": "Account already deleted"
}
```

### POST /api/auth/callback/google (Modified Response)
```
Response 200 (reactivation case):
{
  "accessToken": "...",
  "refreshToken": "...",
  "reactivated": true,
  "message": "Welcome back! Your account has been reactivated."
}

Response 200 (fresh account after hard-delete):
{
  "accessToken": "...",
  "refreshToken": "...",
  "reactivated": false
}
// Note: Indistinguishable from normal new registration (by design)
```

## Database Schema

### Migration V009
```sql
-- Add soft-delete columns to users table
ALTER TABLE users
  ADD COLUMN deleted_at TIMESTAMP NULL,
  ADD COLUMN permanent_delete_scheduled_at TIMESTAMP NULL;

-- Create sentinel user for orphaned vote attribution
INSERT INTO users (id, email, provider, provider_id, created_at, updated_at)
VALUES (0, '[deleted]', 'system', 'DELETED_USER_SENTINEL', NOW(), NOW());

-- Indexes for query performance
CREATE INDEX idx_users_deleted_at ON users(deleted_at);
CREATE INDEX idx_users_permanent_delete ON users(permanent_delete_scheduled_at);
```

## Constants

```java
// UserService.java or dedicated constants class
public static final Long DELETED_USER_SENTINEL_ID = 0L;
public static final Duration DEFAULT_GRACE_PERIOD = Duration.ofDays(30);
```

## Configuration

```properties
# application.properties
app.user.deletion.grace-period-days=30
app.user.cleanup.cron=0 0 3 * * *
```

## State Diagram

```
                    ┌─────────────┐
                    │   ACTIVE    │
                    └──────┬──────┘
                           │ DELETE /api/user/me
                           ▼
                    ┌─────────────┐
           ┌────────│   DELETED   │────────┐
           │        │ (soft)      │        │
           │        └─────────────┘        │
           │               │               │
    OAuth login      30 days pass     OAuth login
    (grace period)         │          (after hard-delete)
           │               ▼               │
           │        ┌─────────────┐        │
           │        │  HARD-DEL   │        │
           │        │ (permanent) │        │
           │        └─────────────┘        │
           │                               │
           ▼                               ▼
    ┌─────────────┐                 ┌─────────────┐
    │   ACTIVE    │                 │ NEW ACCOUNT │
    │ (same user) │                 │ (fresh ID)  │
    └─────────────┘                 └─────────────┘
```
